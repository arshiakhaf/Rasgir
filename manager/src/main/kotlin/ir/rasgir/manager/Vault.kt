package ir.rasgir.manager

import ir.rasgir.core.License
import java.security.PrivateKey

/**
 * Issuer vault. The private key is delivered wrapped (AES-256-GCM + PBKDF2,
 * see License.wrapPrivateKey) as a text bundle; it is imported once and the
 * same bundle is reused on every unlock with the passphrase. The public key
 * never changes while the app is deployed — changing it would invalidate
 * every installed copy of rasgir-check (its public key is baked in).
 */
object Vault {

    var key: PrivateKey? = null
        private set

    val hasKey: Boolean get() = key != null
    val isLocked: Boolean get() = key == null

    fun storedBundle(): String = Ms.get("bundle")

    /** unlock with the passphrase; returns error text or null on success */
    fun unlock(pass: String): String? {
        val bundle = storedBundle()
        if (bundle.isBlank()) return "ابتدا کلید صادرکننده باید وارد شود"
        val priv = License.unwrapPrivateKey(pass.toCharArray(), bundle)
            ?: return "رمز عبور اشتباه است یا بسته کلید آسیب دیده"
        key = priv
        return null
    }

    /** import a delivered issuer bundle (RGKEY1…) and unlock it */
    fun importAndUnlock(pass: String, bundle: String): String? {
        val clean = bundle.trim()
        if (!clean.startsWith("RGKEY" + License.VERSION + ":")) return "فرمت بسته کلید درست نیست (RGKEY…)"
        val priv = License.unwrapPrivateKey(pass.toCharArray(), clean) ?: return "رمز عبور اشتباه است"
        Ms.put("bundle", clean)
        key = priv
        return null
    }

    /** re-wrap the same key with a new passphrase */
    fun changePass(oldPass: String, newPass: String): String? {
        if (unlock(oldPass) != null) return "رمز فعلی اشتباه است"
        val priv = key ?: return "کلید در دسترس نیست"
        val b = License.wrapPrivateKey(newPass.toCharArray(), License.privateKeyDer(priv))
        Ms.put("bundle", b)
        return null
    }

    fun lock() {
        key = null
    }
}
