package ir.rasgir.check

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ir.rasgir.core.License
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Device-locked activation (spec §10): the first run creates an EC P-256 key
 * inside the Android Keystore. The public key (DER) identifies THIS device and
 * can never be copied to another phone (Keystore private keys are not
 * exportable). Licenses signed for this device-hash fail on any other device.
 * minSdk 26 ⇒ KeyGenParameterSpec is always available.
 */
object Lic {

    private const val ALIAS = "rasgir-device-key"
    private const val KEYSTORE = "AndroidKeyStore"

    private fun store(): KeyStore =
        KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun createKeyIfNeeded() {
        val ks = store()
        if (ks.containsAlias(ALIAS)) return
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setKeySize(256)
            .build()
        gen.initialize(spec)
        gen.generateKeyPair()
    }

    fun publicKey(): PublicKey {
        createKeyIfNeeded()
        return store().getCertificate(ALIAS).publicKey
    }

    fun deviceDer(): ByteArray = publicKey().encoded

    fun deviceHash(): String = License.deviceHash(deviceDer())

    fun requestCode(): String =
        License.buildRequestCode(IssuerKey.APP_ID, IssuerKey.APP_VERSION, deviceDer())

    fun issuerPublic(): PublicKey =
        License.publicKeyFromDer(Base64.decode(IssuerKey.PUBLIC_DER_B64, Base64.DEFAULT))

    fun activatedLicense(): String = Repo.getSetting("lic")

    fun isActivated(): Boolean = activatedLicense().isNotBlank() && lastError() == null

    fun lastError(): String? = try {
        License.validateLicense(activatedLicense(), issuerPublic(), deviceDer())
    } catch (t: Throwable) {
        "خطا در بررسی مجوز"
    }

    /** try to activate with pasted license text; returns error string or null */
    fun activate(text: String): String? {
        val err = License.validateLicense(text, issuerPublic(), deviceDer())
        if (err != null) return err
        Repo.setSetting("lic", text.trim())
        return null
    }

    fun deactivate() = Repo.setSetting("lic", "")
}
