package ir.rasgir.check

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** colour palette — the app is green-on-white by default with few alternatives */
object Pal {
    const val BG = 0xFFFDFDF9.toInt()
    const val CARD = 0xFFFFFFFF.toInt()
    const val INK = 0xFF10231A.toInt()
    const val INK2 = 0xFF4A5A52.toInt()
    const val GREEN = 0xFF18794E.toInt()
    const val GREEN_DK = 0xFF0E5A38.toInt()
    const val LINE = 0xFFE2E8E4.toInt()
    const val RED = 0xFFB42318.toInt()
    const val AMBER = 0xFF9A6700.toInt()
    const val GOLD = 0xFFC8A24B.toInt()

    /** selectable accent colours for the settlement image (bg, ink, line, accent) */
    val SCHEMES = listOf(
        intArrayOf(Color.WHITE, 0xFF114D2A.toInt(), 0xFFDFE9E2.toInt(), 0xFF18794E.toInt()), // سبز
        intArrayOf(Color.WHITE, 0xFF12355B.toInt(), 0xFFDCE7F3.toInt(), 0xFF1B5FAA.toInt()), // آبی
        intArrayOf(Color.WHITE, 0xFF3F2D63.toInt(), 0xFFE7DFF3.toInt(), 0xFF6A3FB5.toInt()), // بنفش
        intArrayOf(0xFFFCF7EE.toInt(), 0xFF5C3B0F.toInt(), 0xFFEFE2C8.toInt(), 0xFFB7791F.toInt()) // کرم/طلایی
    )
}

/** number ↔ dp, views helpers */
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

fun rule(c: Context, margin: Float = 4f): View {
    val v = View(c)
    v.setBackgroundColor(Pal.LINE)
    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
    lp.setMargins(c.dp(margin), c.dp(6f), c.dp(margin), c.dp(6f))
    v.layoutParams = lp
    return v
}

fun card(c: Context): LinearLayout {
    val l = LinearLayout(c)
    l.orientation = LinearLayout.VERTICAL
    l.setBackgroundColor(Pal.CARD)
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
    accent: Int = Pal.GREEN, block: Boolean = true, small: Boolean = false
): TextView {
    val b = tv(c, text, if (small) 13f else 15f, if (filled) Color.WHITE else accent, bold = true, gravity = Gravity.CENTER)
    b.isClickable = true
    b.isFocusable = true
    val g = GradientDrawable()
    g.cornerRadius = if (small) c.dp(9).toFloat() else c.dp(13).toFloat()
    if (filled) g.setColor(accent) else {
        g.setColor(Color.TRANSPARENT)
        g.setStroke(c.dp(1.4f), accent)
    }
    b.background = g
    val padY = if (small) c.dp(7) else c.dp(12)
    val padX = if (small) c.dp(12) else c.dp(16)
    b.setPadding(padX, padY, padX, padY)
    b.gravity = Gravity.CENTER
    if (block) {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(c.dp(2), c.dp(5), c.dp(2), c.dp(5))
        b.layoutParams = lp
    }
    return b
}

fun chip(c: Context, text: CharSequence, selected: Boolean = false): TextView {
    val b = tv(c, text, 13.5f, if (selected) Pal.GREEN_DK else Pal.INK2, bold = selected, gravity = Gravity.CENTER)
    val g = GradientDrawable()
    g.cornerRadius = c.dp(18).toFloat()
    g.setColor(if (selected) 0xFFE2F0E9.toInt() else Pal.CARD)
    g.setStroke(1, if (selected) Pal.GREEN else Pal.LINE)
    b.background = g
    b.setPadding(c.dp(12), c.dp(6), c.dp(12), c.dp(6))
    b.isClickable = true
    b.isFocusable = true
    return b
}

/** input field with a label; supports live toman grouping via [toman] */
class Field(
    val label: String, val view: EditText, hint: String = ""
) {
    companion object {
        fun text(
            c: Context, label: String, value: String = "", hint: String = "",
            singleLine: Boolean = true, numeric: Boolean = false,
            onDone: ((String) -> Unit)? = null
        ): Field {
            val wrap = LinearLayout(c)
            wrap.orientation = LinearLayout.VERTICAL
            wrap.addView(tv(c, label, 13f, Pal.INK2, bold = true))
            val et = EditText(c)
            et.setTextColor(Pal.INK)
            et.setTextSize(17f)
            et.setHintTextColor(0xFF9AA8A1.toInt())
            et.hint = hint
            et.setBackgroundColor(Pal.CARD)
            val g = GradientDrawable()
            g.cornerRadius = c.dp(10).toFloat()
            g.setColor(Pal.CARD)
            g.setStroke(1, Pal.LINE)
            et.background = g
            et.setPadding(c.dp(12), c.dp(10), c.dp(12), c.dp(10))
            if (numeric) et.inputType = InputType.TYPE_CLASS_NUMBER
            if (singleLine) {
                et.imeOptions = if (onDone != null) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
                et.maxLines = 1
            }
            et.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (onDone != null) et.setOnEditorActionListener { _, _, _ ->
                onDone(et.text.toString()); true
            }
            et.setText(value)
            wrap.addView(et)
            return Field(label, et, hint)
        }
    }
}

/** simple two-line list row */
fun row(c: Context, title: String, sub: String? = null, onTap: (() -> Unit)? = null): LinearLayout {
    val r = LinearLayout(c)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.setPadding(c.dp(2), c.dp(10), c.dp(2), c.dp(10))
    val txt = LinearLayout(c)
    txt.orientation = LinearLayout.VERTICAL
    txt.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    txt.addView(tv(c, title, 16f, Pal.INK, bold = true))
    if (sub != null) txt.addView(tv(c, sub, 12.5f, Pal.INK2))
    r.addView(txt)
    if (onTap != null) {
        val arrow = tv(c, "‹", 22f, Pal.GREEN)
        r.addView(arrow)
        r.isClickable = true
        r.isFocusable = true
        r.setOnClickListener { onTap() }
    }
    return r
}

/** vertical content in a scroll */
fun screen(c: Context, content: (LinearLayout) -> Unit): ScrollView {
    val sv = ScrollView(c)
    sv.setBackgroundColor(Pal.BG)
    sv.isFillViewport = true
    val col = LinearLayout(c)
    col.orientation = LinearLayout.VERTICAL
    col.setPadding(c.dp(16), c.dp(12), c.dp(16), c.dp(28))
    sv.addView(col)
    content(col)
    return sv
}

/** toolbar with back */
fun topbar(c: Context, title: String, onBack: (() -> Unit)?): LinearLayout {
    val bar = LinearLayout(c)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setBackgroundColor(Pal.GREEN_DK)
    bar.setPadding(c.dp(6), c.dp(10), c.dp(10), c.dp(10))
    if (onBack != null) {
        val back = tv(c, "→", 24f, Color.WHITE, bold = true, gravity = Gravity.CENTER)
        back.setPadding(c.dp(10), 0, c.dp(10), 0)
        back.isClickable = true
        bar.addView(back)
        back.setOnClickListener { onBack() }
    }
    val t = tv(c, title, 17f, Color.WHITE, bold = true)
    t.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    bar.addView(t)
    return bar
}

fun Activity.ask(title: String, msg: String, ok: String, cancel: String = "انصراف", onOk: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(msg)
        .setPositiveButton(ok) { _, _ -> onOk() }
        .setNegativeButton(cancel, null)
        .show()
}

fun Activity.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

/** share plain text via the system share sheet */
fun Context.shareText(subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(i, subject))
}
