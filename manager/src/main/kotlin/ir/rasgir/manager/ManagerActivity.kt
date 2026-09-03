package ir.rasgir.manager

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File

/** states of the single-activity manager */
enum class Scr { LOCK, IMPORT, HOME, ISSUE, LIST, VIEW, SETTINGS }

/** host shared by all screens */
interface MH {
    val act: Activity
    fun show(s: Scr)
    fun toast(s: String)
    var pendingRequest: String
    var viewRowId: Long
    var renewRow: Ms.Lic?
    /** SAF file-picker continuation set by screens */
    var resultHandler: ((android.content.Intent?) -> Unit)?
}

class MainActivity : Activity(), MH {

    override val act: Activity get() = this
    private var screen = Scr.HOME
    private lateinit var content: FrameLayout
    private var bar: LinearLayout? = null

    /** pending shared request code (ACTION_SEND) */
    override var pendingRequest: String = ""
    /** row currently shown in VIEW / being renewed */
    override var viewRowId: Long = -1L
    override var renewRow: Ms.Lic? = null
    override var resultHandler: ((android.content.Intent?) -> Unit)? = null

    override fun onActivityResult(req: Int, res: Int, data: android.content.Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 3001 && res == RESULT_OK) {
            val cb = resultHandler
            resultHandler = null
            cb?.invoke(data)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Ms.init(this)
        intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.let {
            if (it.isNotEmpty() && it.startsWith("RGREQ")) pendingRequest = it
        }

        val rootV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Pal.BLUE_DK)
        }
        rootV.addView(bar)
        content = FrameLayout(this)
        content.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        rootV.addView(content)
        setContentView(rootV)

        screen = if (Vault.hasKey) Scr.HOME
        else if (Ms.get("bundle").isBlank()) Scr.IMPORT else Scr.LOCK
        render()
    }

    override fun show(s: Scr) {
        screen = s
        render()
    }

    private fun titles() = mapOf(
        Scr.LOCK to "باز کردن گاوصندوق",
        Scr.IMPORT to "وارد کردن کلید صادرکننده",
        Scr.HOME to "مدیر مجوز رأس‌گیر چک",
        Scr.ISSUE to "صدور مجوز",
        Scr.LIST to "مجوزهای صادرشده",
        Scr.VIEW to "مجوز",
        Scr.SETTINGS to "تنظیمات و پشتیبان"
    )

    private fun render() {
        val b = bar ?: return
        b.removeAllViews()
        val t = titles()[screen] ?: ""
        if (screen != Scr.HOME && screen != Scr.LOCK && screen != Scr.IMPORT) {
            val back = tv(this, "→", 24f, 0xFFFFFFFF.toInt(), bold = true, gravity = android.view.Gravity.CENTER)
            back.setPadding(dp(10), 0, dp(10), 0)
            back.isClickable = true
            back.setOnClickListener { show(Scr.HOME) }
            b.addView(back)
        }
        b.addView(tv(this, t, 17f, 0xFFFFFFFF.toInt(), bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        content.removeAllViews()
        val v = when (screen) {
            Scr.LOCK -> Screens.lock(this)
            Scr.IMPORT -> Screens.importK(this)
            Scr.HOME -> Screens.home(this)
            Scr.ISSUE -> Screens.issue(this)
            Scr.LIST -> Screens.list(this)
            Scr.VIEW -> Screens.view(this)
            Scr.SETTINGS -> Screens.settings(this)
        }
        content.addView(v, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    override fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()
}

fun Activity.copyText(label: String, text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareFileOfText(a: Activity, name: String, body: String) {
    val dir = File(a.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, name)
    f.writeText(body)
    val uri = android.net.Uri.parse("content://ir.rasgir.manager.files/${android.net.Uri.encode(f.name)}")
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "پشتیبان مدیر مجوز رأس‌گیر چک")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    a.startActivity(Intent.createChooser(i, "ارسال پشتیبان"))
}

fun makeLabeled(c: Context, text: String, bold: Boolean = false, color: Int = Pal.INK2): android.widget.TextView =
    tv(c, text, 13f, color, bold = bold)

fun EditText.monoStyle(c: Context) {
    setTextColor(Pal.INK)
    setTextSize(13f)
    typeface = android.graphics.Typeface.MONOSPACE
}

/** full-width vertical screen with padding */
fun screenOf(a: Activity, content: (LinearLayout) -> Unit): ScrollView {
    val sv = ScrollView(a)
    sv.setBackgroundColor(Pal.BG)
    sv.isFillViewport = true
    val col = LinearLayout(a)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(24))
    sv.addView(col)
    content(col)
    return sv
}
