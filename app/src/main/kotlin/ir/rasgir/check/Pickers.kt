package ir.rasgir.check

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import ir.rasgir.core.Jalali
import ir.rasgir.core.Money

/**
 * Jalali date picker (pure framework widgets). Returns a linear day number.
 */
object JalaliPicker {

    private fun yearRange(): IntArray {
        val now = Jalali.dayToJalali(Repo.todayDay())
        val from = now.first - 40
        val to = now.first + 30
        return IntArray(to - from + 1) { from + it }
    }

    fun pick(
        c: Context, initialDay: Long, title: String = "انتخاب تاریخ (شمسی)",
        onPicked: (Long) -> Unit
    ) {
        val (iy, im, id) = try {
            Jalali.dayToJalali(initialDay)
        } catch (t: Throwable) {
            Jalali.dayToJalali(Repo.todayDay())
        }
        val years = yearRange()
        val root = LinearLayout(c)
        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER
        root.setPadding(24, 8, 24, 8)
        val ys = Spinner(c)
        val ms = Spinner(c)
        val ds = Spinner(c)
        val names = listOf("سال", "ماه", "روز")
        listOf(ys, ms, ds).forEachIndexed { i, s ->
            val w = LinearLayout(c)
            w.orientation = LinearLayout.VERTICAL
            s.prompt = names[i]
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(6, 0, 6, 0)
            s.layoutParams = lp
            root.addView(s)
        }
        fun months(): List<String> = Jalali.MONTH_NAMES
        fun daysOf(y: Int, m: Int): Int = Jalali.daysInMonth(y, m)

        fun fillDays() {
            val y = years[ys.selectedItemPosition]
            val m = ms.selectedItemPosition + 1
            val max = daysOf(y, m)
            val cur = (ds.selectedItemPosition + 1).coerceIn(1, max)
            val items = (1..max).map { it.toString() }
            ds.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_item, items).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            ds.setSelection(cur - 1)
        }
        ms.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_item, months()).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        ys.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_item, years.map { it.toString() }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        ys.setSelection(years.indexOf(iy).coerceAtLeast(0))
        ms.setSelection(im - 1)
        fillDays()
        ds.onItemSelectedListener = null // avoid recursion noise
        ms.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, idL: Long) = fillDays()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        ys.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, idL: Long) = fillDays()
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        AlertDialog.Builder(c)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("تأیید") { _: DialogInterface?, _: Int ->
                val y = years[ys.selectedItemPosition]
                val m = ms.selectedItemPosition + 1
                val d = ds.selectedItemPosition + 1
                if (Jalali.valid(y, m, d)) onPicked(Jalali.jalaliToDay(y, m, d))
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}

/**
 * Amount input with live three-digit grouping (Tooman) while typing.
 * The parsed value is committed once on focus-loss / IME-Done so rebuilds only
 * happen when the user finishes an edit. Latin digits only, per spec.
 */
object MoneyField {

    class Built(val wrap: LinearLayout, val commit: () -> Long)

    fun build(c: Context, label: String, initial: Long? = null, onCommit: ((Long) -> Unit)? = null): Built {
        val wrap = LinearLayout(c)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.addView(tv(c, label, 13f, Pal.INK2, bold = true))
        val et = EditText(c)
        et.setTextColor(Pal.INK)
        et.setTextSize(17f)
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.imeOptions = if (onCommit != null) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
        et.setSingleLine(true)
        val g = android.graphics.drawable.GradientDrawable()
        g.cornerRadius = c.dp(10).toFloat()
        g.setColor(Pal.CARD)
        g.setStroke(1, Pal.LINE)
        et.background = g
        et.setPadding(c.dp(12), c.dp(10), c.dp(12), c.dp(10))
        wrap.addView(et)
        var self = false

        fun commitNow() {
            val v = ir.rasgir.core.Money.parseToman(et.text.toString()) ?: 0L
            if (onCommit != null) onCommit(v)
            else if (v > 0) et.setText(Money.formatToman(v))
        }

        et.setOnFocusChangeListener { _, has ->
            if (!has) commitNow()
        }
        et.setOnEditorActionListener { _, _, _ ->
            commitNow()
            true
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (self || s == null) return
                val digits = s.toString().filter { it.isDigit() }
                if (digits.isEmpty()) return
                val v = digits.toLongOrNull() ?: return
                val pretty = Money.formatToman(v)
                if (pretty != s.toString()) {
                    self = true
                    et.setText(pretty)
                    et.setSelection(et.text.length)
                    self = false
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c0: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c0: Int) {}
        })
        if (initial != null && initial > 0) et.setText(Money.formatToman(initial))
        return Built(wrap) { Money.parseToman(et.text.toString()) ?: 0L }
    }
}
