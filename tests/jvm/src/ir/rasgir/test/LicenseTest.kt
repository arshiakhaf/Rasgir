package ir.rasgir.test

import ir.rasgir.core.License

object LicenseTest {
    fun run() {
        Harness.group("تست فعال‌سازی وابسته به دستگاه (ECDSA P-256)")
        val issuer = License.generateEcKeyPair()
        val dev1 = License.generateEcKeyPair()
        val dev2 = License.generateEcKeyPair()

        val pub1 = License.publicKeyDer(dev1.public)
        val req1 = License.buildRequestCode(License.APP_CHECK, "1.0.0", pub1)
        val parsed = License.parseRequest(req1)
        Harness.check(parsed != null, "کد درخواست قابل پارس است")
        if (parsed != null) {
            Harness.check(parsed.publicKey() != null, "کلید عمومی از کد درخواست")
            Harness.eq(parsed.deviceHash, License.deviceHash(pub1), "هش دستگاه در کد")
        }

        val issued = 19000L
        val lic = License.issueLicense(issuer.private, License.APP_CHECK, parsed!!, issued, 0, "مشتری نمونه")
        Harness.check(lic.startsWith("RG-LIC1"), "قالب مجوز")
        val err1 = License.validateLicense(lic, issuer.public, pub1)
        Harness.check(err1 == null, "مجوز معتبر برای دستگاه ۱ (err=$err1)")

        // copied license on a second device must fail
        val err2 = License.validateLicense(lic, issuer.public, License.publicKeyDer(dev2.public))
        Harness.check(err2 != null && err2.contains("دستگاه"), "مجوز کپی‌شده روی دستگاه دوم رد می‌شود")

        // tampered license must fail
        val tampered = lic.replace("dev=" + parsed.deviceHash, "dev=00" + parsed.deviceHash.drop(2))
        val err3 = License.validateLicense(tampered, issuer.public, pub1)
        Harness.check(err3 != null, "مجوز دستکاری‌شده رد می‌شود")

        // expired license must fail (valid when exp < today)
        val licExp = License.issueLicense(issuer.private, License.APP_CHECK, parsed, issued, 5)
        val err4 = License.validateLicense(licExp, issuer.public, pub1)
        Harness.check(err4 != null && err4.contains("منقضی"), "مجوز تاریخ‌دار منقضی رد می‌شود")

        // a wrong issuer must fail
        val evilIssuer = License.generateEcKeyPair()
        val err5 = License.validateLicense(lic, evilIssuer.public, pub1)
        Harness.check(err5 != null, "امضای غیرمجاز رد می‌شود")

        // manager backup/restore of the issuer key
        val pw = "s3cret!".toCharArray()
        val bundle = License.wrapPrivateKey(pw, License.privateKeyDer(issuer.private))
        Harness.check(bundle.startsWith("RGKEY1:"), "بسته رمزنگاری‌شده ساخته شد")
        val restored = License.unwrapPrivateKey(pw, bundle)
        Harness.check(restored != null, "بازیابی کلید با رمز درست")
        if (restored != null) {
            val lic2 = License.issueLicense(restored, License.APP_CHECK, parsed, issued, 0)
            val e = License.validateLicense(lic2, issuer.public, pub1)
            Harness.check(e == null, "مجوز با کلید بازیابی‌شده معتبر است")
        }
        val bad = License.unwrapPrivateKey("wrong".toCharArray(), bundle)
        Harness.check(bad == null, "رمز غلط → بازیابی ناموفق")
    }
}
