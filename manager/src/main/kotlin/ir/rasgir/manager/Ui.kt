package ir.rasgir.manager

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** manager palette + tiny view toolkit (native, no androidx) */
object Pal {
    const val BG = 0xFFF7F9FC.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val INK = 0xFF16233A.toInt()
    const val INK2 = 0xFF4A5A72.toInt()
    const val BLUE = 0xFF1B5FAA.toInt()
    const val BLUE_DK = 0xFF123E72.toInt()
    const val LINE = 0xFFDCE4EE.toInt()
    const val RED = 0xFFB42318.toInt()
    const val AMBER = 0xFF9A6700.toInt()
    const val GREEN = 0xFF18794E.toInt()
}

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()

fun tv(
    c: Context, text: CharSequence, sizeSp: Float = 15f, color: Int = Pal.INK,
    bold: Boolean = false, gravity: Int = Gravity.START
): TextView {
    val t = TextView(c)
    t.text = text
    t.setTextSize(sizeSp)
    t.setTextColor(color)
    t.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    t.gravity = gravity
    t.includeFontPadding = false
    return t
}

fun vspace(c: Context, h: Float): View {
    val v = View(c)
    v.layoutParams = LinearLayout.LayoutParams(0, c.dp(h))
    return v
}

fun hspace(c: Context, w: Float): View {
    val v = View(c)
    v.layoutParams = LinearLayout.LayoutParams(c.dp(w), 0)
    return v
}

fun card(c: Context): LinearLayout {
    val l = LinearLayout(c)
    l.orientation = LinearLayout.VERTICAL
    val g = GradientDrawable()
    g.setColor(Pal.CARD)
    g.cornerRadius = c.dp(14).toFloat()
    g.setStroke(1, Pal.LINE)
    l.background = g
    l.setPadding(c.dp(14), c.dp(12), c.dp(14), c.dp(12))
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    lp.setMargins(c.dp(2), c.dp(6), c.dp(2), c.dp(6))
    l.layoutParams = lp
    return l
}

fun btn(
    c: Context, text: CharSequence, filled: Boolean = true,
    accent: Int = Pal.BLUE, block: Boolean = true, small: Boolean = false
): TextView {
    val b = tv(c, text, if (small) 13f else 15f, if (filled) Color.WHITE else accent,
        bold = true, gravity = Gravity.CENTER)
    b.isClickable = true
    b.isFocusable = true
    val g = GradientDrawable()
    g.cornerRadius = if (small) c.dp(9).toFloat() else c.dp(13).toFloat()
    if (filled) g.setColor(accent) else {
        g.setColor(Color.TRANSPARENT)
        g.setStroke(c.dp(1.4f), accent)
    }
    b.background = g
    b.setPadding(if (small) c.dp(12) else c.dp(16), if (small) c.dp(7) else c.dp(12),
        if (small) c.dp(12) else c.dp(16), if (small) c.dp(7) else c.dp(12))
    b.gravity = Gravity.CENTER
    if (block) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(c.dp(2), c.dp(5), c.dp(2), c.dp(5))
        b.layoutParams = lp
    }
    return b
}

/** labelled input: [view] is the whole column (add to layout), [edit] the field */
class F(val view: LinearLayout, val edit: EditText)

fun field(
    c: Context, label: String, value: String = "", hint: String = "",
    multiline: Boolean = false, mono: Boolean = false
): F {
    val wrap = LinearLayout(c)
    wrap.orientation = LinearLayout.VERTICAL
    wrap.addView(tv(c, label, 13f, Pal.INK2, bold = true))
    val et = EditText(c)
    et.setTextColor(Pal.INK)
    et.setTextSize(15f)
    if (mono) et.typeface = Typeface.MONOSPACE
    et.setHintTextColor(0xFF8A97A8.toInt())
    et.hint = hint
    val g = GradientDrawable()
    g.cornerRadius = c.dp(10).toFloat()
    g.setColor(Pal.CARD)
    g.setStroke(1, Pal.LINE)
    et.background = g
    et.setPadding(c.dp(12), c.dp(10), c.dp(12), c.dp(10))
    if (multiline) et.setSingleLine(false) else et.setSingleLine(true)
    et.setText(value)
    wrap.addView(et)
    return F(wrap, et)
}

fun topbar(c: Context, title: String, onBack: (() -> Unit)?): LinearLayout {
    val bar = LinearLayout(c)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setBackgroundColor(Pal.BLUE_DK)
    bar.setPadding(c.dp(6), c.dp(10), c.dp(10), c.dp(10))
    if (onBack != null) {
        val back = tv(c, "→", 24f, Color.WHITE, bold = true, gravity = Gravity.CENTER)
        back.setPadding(c.dp(10), 0, c.dp(10), 0)
        back.isClickable = true
        back.setOnClickListener { onBack() }
        bar.addView(back)
    }
    val t = tv(c, title, 17f, Color.WHITE, bold = true)
    t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    bar.addView(t)
    return bar
}

fun Activity.ask(title: String, msg: String, ok: String, cancel: String = "انصراف", onOk: () -> Unit) {
    AlertDialog.Builder(this).setTitle(title).setMessage(msg)
        .setPositiveButton(ok) { _, _ -> onOk() }
        .setNegativeButton(cancel, null)
        .show()
}

fun Activity.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

fun Context.shareText2(subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(i, subject))
}

/** label+value rows used on dialogs/screens */
fun rowPair(c: Context, label: String, value: String): LinearLayout {
    val r = LinearLayout(c)
    r.orientation = LinearLayout.VERTICAL
    r.addView(tv(c, label, 12.5f, Pal.INK2, bold = true))
    r.addView(tv(c, value, 15f, Pal.INK))
    return r
}
