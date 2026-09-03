package ir.rasgir.check

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** device-bound offline activation: request code → paste license → unlock */
class ActivationActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Repo.init(this)
        Repo.context = this

        val rootV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rootV.addView(topbar(this, "فعال‌سازی") { finish() })
        val sv = ScrollView(this)
        sv.setBackgroundColor(Pal.BG)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(28))
        }
        sv.addView(col)
        rootV.addView(sv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootV)

        val ok = Lic.isActivated()
        if (ok) {
            val cardOk = card(this)
            cardOk.addView(tv(this, "✅ این دستگاه فعال است.", 17f, Pal.GREEN_DK, bold = true))
            cardOk.addView(tv(this,
                "مجوز روی همین دستگاه معتبر است و در حافظه امن اندروید نگهداری می‌شود.", 13f, Pal.INK2))
            col.addView(cardOk)
            val goB = btn(this, "ورود به برنامه")
            goB.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            col.addView(goB)
            return
        }

        val licErr = Lic.lastError()
        if (licErr != null) {
            col.addView(tv(this, "⚠️ $licErr", 14f, Pal.RED, bold = true))
            val deactB = btn(this, "حذف مجوز نامعتبر", filled = false, small = true)
            deactB.setOnClickListener { Lic.deactivate(); recreate() }
            col.addView(deactB)
        }

        val intro = card(this)
        intro.addView(tv(this, "فعال‌سازی آفلاین وابسته به دستگاه", 15f, Pal.INK, bold = true))
        intro.addView(tv(this,
            "۱) کد درخواست زیر را برای صاحب برنامه بفرستید (هر دستگاهی کد متفاوت دارد).\n" +
                "۲) مجوز امضاشده را در کادر پایین وارد کنید.\n" +
                "۳) کلید خصوصی مجوزساز داخل برنامه نیست و نمی‌توان بدون آن مجوز ساخت.",
            13f, Pal.INK2))
        col.addView(intro)

        val codeCard = card(this)
        codeCard.addView(tv(this, "کد درخواست این دستگاه", 13f, Pal.INK2, bold = true))
        val code = try { Lic.requestCode() } catch (t: Throwable) { "ساخت کد ناموفق بود" }
        val codeTv = tv(this, code, 11.5f, Pal.GREEN_DK)
        codeTv.setTextIsSelectable(true)
        codeCard.addView(codeTv)
        col.addView(codeCard)

        val copyB = btn(this, "کپی کد درخواست", filled = false, small = true, block = false)
        copyB.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("rasgir-request", code))
            toast("کپی شد")
        }
        val sendB = btn(this, "ارسال کد درخواست", filled = false, small = true, block = false)
        sendB.setOnClickListener { shareText("کد فعال‌سازی رأس‌گیر چک", code) }
        val bRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bRow.addView(copyB); bRow.addView(hspace(this, 6f)); bRow.addView(sendB)
        col.addView(bRow)

        val licCard = card(this)
        licCard.addView(tv(this, "متن مجوز دریافتی", 13f, Pal.INK2, bold = true))
        val licEt = EditText(this).apply {
            setSingleLine(false)
            minLines = 6
            setTextColor(Pal.INK)
            setTextSize(13f)
            setHintTextColor(0xFF9AA8A1.toInt())
            hint = "RG-LIC2 …"
        }
        intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { if (it.isNotBlank()) licEt.setText(it) }
        licCard.addView(licEt)
        col.addView(licCard)

        val actB = btn(this, "فعال‌سازی")
        actB.setOnClickListener {
            val txt = licEt.text.toString().trim()
            if (txt.isEmpty()) { toast("متن مجوز را وارد کنید"); return@setOnClickListener }
            val err = Lic.activate(txt)
            if (err == null) {
                toast("✅ فعال شد")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else toast("❌ $err")
        }
        col.addView(actB)
        col.addView(tv(this,
            "مجوز پس از فعال‌سازی فقط روی همین دستگاه کار می‌کند؛ انتقال فایل مجوز به دستگاه دیگر بی‌اثر است.",
            12f, Pal.INK2))
    }
}
