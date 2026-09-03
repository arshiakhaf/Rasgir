package ir.rasgir.manager

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import ir.rasgir.core.Jalali

fun managerTodayDay(): Long {
    val epoch = System.currentTimeMillis() / 86_400_000L
    return Jalali.fromEpochDay(epoch)
}

/** Jalali 3-spinner picker returning a linear day */
fun pickJalaliDay(
    c: Context, initialDay: Long, title: String,
    onPicked: (Long) -> Unit
) {
    val (iy0, im0, id0) = try {
        Jalali.dayToJalali(initialDay)
    } catch (t: Throwable) {
        Jalali.dayToJalali(managerTodayDay())
    }
    val now = Jalali.dayToJalali(managerTodayDay())
    val from = now.first - 60
    val to = now.first + 60
    val years = IntArray(to - from + 1) { from + it }

    val ys = Spinner(c); val ms = Spinner(c); val ds = Spinner(c)
    val root = LinearLayout(c)
    root.orientation = LinearLayout.HORIZONTAL
    root.gravity = Gravity.CENTER
    root.setPadding(16, 8, 16, 8)
    listOf(ys, ms, ds).forEach { s ->
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(6, 0, 6, 0)
        s.layoutParams = lp
        root.addView(s)
    }

    fun fillDays() {
        val y = years[ys.selectedItemPosition]
        val m = ms.selectedItemPosition + 1
        val max = Jalali.daysInMonth(y, m)
        val cur = (ds.selectedItemPosition + 1).coerceIn(1, max)
        ds.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_item, (1..max).map { it.toString() })
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        ds.setSelection(cur - 1)
    }

    ms.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_item, Jalali.MONTH_NAMES)
        .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    ys.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_item, years.map { it.toString() })
        .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    ys.setSelection(years.indexOf(iy0).coerceAtLeast(0))
    ms.setSelection(im0 - 1)
    ms.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, idL: Long) = fillDays()
        override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
    }
    ys.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, idL: Long) = fillDays()
        override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
    }
    fillDays()

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
