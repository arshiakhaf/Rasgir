package ir.rasgir.core

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Device-bound offline licensing (spec §10).
 *
 *  - The app creates an EC P-256 key inside the Android Keystore (never
 *    exportable). Only the public part can be read.
 *  - First run shows a REQUEST code containing the device public key. The
 *    operator pastes/feeds it into the separate License Manager tool, which
 *    signs a license with the issuer private key (ECDSA P-256 / SHA-256).
 *  - The app verifies the license with the embedded issuer public key AND
 *    checks that the license was issued for *this* device's public key hash.
 *    Because the private key never leaves the Keystore (uninstall/factory
 *    reset wipes it), a copied APK or a copied license cannot activate on a
 *    different phone.
 *
 * The issuer key is never embedded in the APK. The manager keeps it in ITS
 * Keystore and can export a password-encrypted (AES-256-GCM + PBKDF2) bundle
 * for backup/restore.
 */
object License {

    const val VERSION = "1"
    private const val CURVE = "secp256r1"
    private const val SIG = "SHA256withECDSA"
    const val PBKDF2_ITER = 150_000

    // ------------------------------------------------------------------ keys
    fun generateEcKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(CURVE))
        return kpg.generateKeyPair()
    }

    fun publicKeyDer(pub: PublicKey): ByteArray = pub.encoded
    fun privateKeyDer(priv: PrivateKey): ByteArray = priv.encoded

    fun publicKeyFromDer(der: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))

    fun privateKeyFromDer(der: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** short stable fingerprint of a device public key (16 bytes hex) */
    fun deviceHash(publicKeyDer: ByteArray): String =
        sha256(publicKeyDer).copyOfRange(0, 16).joinToString("") { "%02x".format(it) }

    fun b64u(data: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    fun b64uDecode(s: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(s)
    } catch (e: IllegalArgumentException) {
        null
    }

    fun b64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)
    fun b64Decode(s: String): ByteArray? = try {
        Base64.getDecoder().decode(s)
    } catch (e: IllegalArgumentException) {
        null
    }

    fun sign(priv: PrivateKey, payload: ByteArray): ByteArray {
        val s = Signature.getInstance(SIG)
        s.initSign(priv)
        s.update(payload)
        return s.sign()
    }

    fun verify(pub: PublicKey, payload: ByteArray, sig: ByteArray): Boolean = try {
        val s = Signature.getInstance(SIG)
        s.initVerify(pub)
        s.update(payload)
        s.verify(sig)
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------- request
    fun buildRequestCode(appId: String, appVersion: String, devicePublicKeyDer: ByteArray): String {
        val pub = b64(devicePublicKeyDer)
        val hash = deviceHash(devicePublicKeyDer)
        return "RGREQ$VERSION:$appId:$appVersion:$hash:$pub"
    }

    class Request(val appId: String, val version: String, val deviceHash: String, val pubB64: String) {
        fun publicKey(): PublicKey? = b64Decode(pubB64)?.let { runCatching { publicKeyFromDer(it) }.getOrNull() }
    }

    fun parseRequest(text: String): Request? {
        val t = text.trim()
        if (!t.startsWith("RGREQ$VERSION:")) return null
        val parts = t.split(':')
        if (parts.size < 5) return null
        val appId = parts[1]
        val ver = parts[2]
        val hash = parts[3]
        val pub = parts.subList(4, parts.size).joinToString(":")
        return Request(appId, ver, hash, pub)
    }

    // ------------------------------------------------------------- license
    /** canonical, order-stable payload that gets signed */
    fun canonical(appId: String, deviceHash: String, issuedDay: Long, expiryDay: Long, customer: String): String {
        val name = b64u(customer.toByteArray(Charsets.UTF_8))
        return "RG-LIC$VERSION\n" +
            "app=$appId\n" +
            "dev=$deviceHash\n" +
            "iss=$issuedDay\n" +
            "exp=$expiryDay\n" +   // 0 = permanent
            "who=$name"
    }

    fun issueLicense(
        issuerKey: PrivateKey,
        appId: String,
        request: Request,
        issuedDay: Long,
        expiryDay: Long, // 0 = permanent
        customer: String = ""
    ): String {
        val payload = canonical(appId, request.deviceHash, issuedDay, expiryDay, customer)
        val sig = b64(sign(issuerKey, payload.toByteArray(Charsets.UTF_8)))
        return payload + "\nsig=$sig"
    }

    class LicenseInfo(
        val appId: String,
        val deviceHash: String,
        val issuedDay: Long,
        val expiryDay: Long, // 0 = permanent
        val customer: String
    ) {
        val permanent: Boolean get() = expiryDay == 0L
        val expiredToday: Boolean get() = !permanent && expiryDay < todayEpochDay()
    }

    /** epoch day of "today" (injectable clock for tests) */
    fun todayEpochDay(): Long = System.currentTimeMillis() / 86_400_000L

    fun parseLicense(text: String): Pair<LicenseInfo, ByteArray>? { // (info, signature)
        val lines = text.trim().split('\n').map { it.trim() }
        if (lines.isEmpty() || lines[0] != "RG-LIC$VERSION") return null
        val map = HashMap<String, String>()
        var sig = ""
        for (i in 1 until lines.size) {
            val kv = lines[i].split('=', limit = 2)
            if (kv.size == 2) {
                if (kv[0] == "sig") sig = kv[1] else map[kv[0]] = kv[1]
            }
        }
        val app = map["app"] ?: return null
        val dev = map["dev"] ?: return null
        val iss = map["iss"]?.toLongOrNull() ?: return null
        val exp = map["exp"]?.toLongOrNull() ?: return null
        val who = map["who"]?.let { b64uDecode(it) }?.toString(Charsets.UTF_8) ?: ""
        if (sig.isEmpty()) return null
        val payload = canonical(app, dev, iss, exp, who)
        val sigBytes = b64Decode(sig) ?: return null
        return LicenseInfo(app, dev, iss, exp, who) to sigBytes
    }

    /** returns error string or null when valid for this device */
    fun validateLicense(
        licenseText: String,
        issuerPublic: PublicKey,
        devicePublicKeyDer: ByteArray
    ): String? {
        val parsed = parseLicense(licenseText) ?: return "فرمت مجوز صحیح نیست"
        val (info, sig) = parsed
        val devHash = deviceHash(devicePublicKeyDer)
        if (info.deviceHash != devHash) return "مجوز متعلق به این دستگاه نیست"
        if (info.appId.isNotEmpty() && info.appId != APP_ANY) {
            // app-id match is checked by caller (manager may sign for any app)
        }
        val payload = canonical(info.appId, info.deviceHash, info.issuedDay, info.expiryDay, info.customer)
        if (!verify(issuerPublic, payload.toByteArray(Charsets.UTF_8), sig)) return "امضای مجوز معتبر نیست"
        if (!info.permanent && info.expiryDay < todayEpochDay()) return "مجوز منقضی شده است"
        return null
    }

    const val APP_ANY = "*"
    const val APP_CHECK = "rasgir-check"
    const val APP_MANAGER = "rasgir-manager"

    // --------------------------------------------- encrypted issuer-key bundle
    /** wrap private key DER with a password:  "RGKEY1:" + b64(salt||iv||ct) */
    fun wrapPrivateKey(password: CharArray, privateDer: ByteArray): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val key = deriveKey(password, salt)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = c.doFinal(privateDer)
        val blob = salt + iv + ct
        return "RGKEY$VERSION:" + b64(blob)
    }

    fun unwrapPrivateKey(password: CharArray, bundle: String): PrivateKey? {
        if (!bundle.startsWith("RGKEY$VERSION:")) return null
        val raw = b64Decode(bundle.substringAfter(':')) ?: return null
        if (raw.size < 28) return null
        val salt = raw.copyOfRange(0, 16)
        val iv = raw.copyOfRange(16, 28)
        val ct = raw.copyOfRange(28, raw.size)
        return try {
            val key = deriveKey(password, salt)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            privateKeyFromDer(c.doFinal(ct))
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITER, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(f.generateSecret(spec).encoded, "AES")
    }
}
