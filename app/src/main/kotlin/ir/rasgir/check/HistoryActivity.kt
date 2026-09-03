package ir.rasgir.check

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** history: search, edit, copy, re-share, trash (30-day restore window) */
class HistoryActivity : Activity() {

    private lateinit var search: EditText
    private lateinit var listBox: LinearLayout
    private lateinit var countTxt: android.widget.TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Repo.init(this)
        Repo.context = this
        Repo.purgeTrashOlderThan(30)

        val rootV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rootV.addView(topbar(this, "تاریخچه کارها") { finish() })
        val sv = ScrollView(this)
        sv.setBackgroundColor(Pal.BG)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(24))
        }
        sv.addView(col)
        rootV.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootV)

        search = EditText(this).apply {
            hint = "جستجو: نام، بدهی، تعداد چک…"
            setTextColor(Pal.INK)
            setTextSize(15f)
            setSingleLine(true)
        }
        col.addView(search)
        countTxt = tv(this, "", 12.5f, Pal.INK2)
        col.addView(countTxt)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(listBox)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { redraw(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        val trashBtn = btn(this, "زباله‌دان (۳۰ روز)", filled = false, block = false, small = true)
        trashBtn.setOnClickListener { showTrash() }
        col.addView(vspace(this, 8f))
        col.addView(trashBtn)

        redraw()
    }

    private fun redraw(q: String = search.text.toString()) {
        val rows = Repo.listActive(q)
        listBox.removeAllViews()
        countTxt.text = if (rows.isEmpty()) "موردی پیدا نشد" else "${rows.size} کار ذخیره‌شده"
        for (r in rows) drawRow(r)
    }

    private fun drawRow(r: Repo.RowInfo) {
        val cd = card(this)
        cd.addView(tv(this, r.name, 16f, Pal.INK, bold = true))
        val whenS = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(r.createdMs))
        val debt = r.debt.toLongOrNull()?.let { BigInteger.valueOf(it) } ?: BigInteger.ZERO
        cd.addView(tv(this,
            "تاریخ: $whenS — بدهی: ${ir.rasgir.core.Money.formatBig(debt)} تومان — ${r.checks} چک",
            12.5f, Pal.INK2))

        val openB = btn(this, "ویرایش", block = false, small = true)
        openB.setOnClickListener {
            if (requireActivated()) {
                startActivity(Intent(this, MainActivity::class.java)
                    .putExtra("id", r.id).putExtra("page", "checks"))
            }
        }
        val copyB = btn(this, "کپی", block = false, small = true, filled = false)
        copyB.setOnClickListener {
            if (requireActivated()) {
                val nid = Repo.copyProject(r.id)
                if (nid == null) toast("کپی ممکن نشد")
                else startActivity(Intent(this, MainActivity::class.java)
                    .putExtra("id", nid).putExtra("page", "checks"))
            }
        }
        val shareB = btn(this, "تصویر", block = false, small = true, filled = false)
        shareB.setOnClickListener {
            val wp = Repo.loadProject(r.id)
            val pg = wp?.plan?.checks
            if (wp == null || pg.isNullOrEmpty()) { toast("این کار هنوز چک ندارد"); return@setOnClickListener }
            val today = Repo.todayDay()
            val pages = SettlementImage.render(wp, today, wp.customerWarnings(today))
            val name = "Rasgir_${wp.customerName.ifBlank { r.name }}".replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
            Share.share(this, Share.cachePngs(this, pages, name), "شرایط تسویه حساب — رأس‌گیر چک")
        }
        val delB = btn(this, "حذف", block = false, small = true, accent = Pal.RED)
        delB.setOnClickListener {
            ask("حذف «${r.name}»", "این کار به زباله‌دان می‌رود و تا ۳۰ روز قابل بازیابی است.",
                "انتقال به زباله‌دان") {
                Repo.trash(r.id)
                redraw()
            }
        }
        val acts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        acts.addView(openB); acts.addView(hspace(this, 4f))
        acts.addView(copyB); acts.addView(hspace(this, 4f))
        acts.addView(shareB); acts.addView(hspace(this, 4f))
        acts.addView(delB)
        cd.addView(acts)
        listBox.addView(cd)
    }

    private fun requireActivated(): Boolean {
        if (Lic.isActivated()) return true
        startActivity(Intent(this, ActivationActivity::class.java))
        return false
    }

    /** single two-step trash dialog: pick row → بازیابی / حذف دائمی */
    private fun showTrash() {
        val rows = Repo.listTrash()
        if (rows.isEmpty()) { toast("زباله‌دان خالی است"); return }
        AlertDialog.Builder(this)
            .setTitle("زباله‌دان")
            .setItems(rows.mapIndexed { i, r -> "${i + 1}. ${r.name}" }.toTypedArray()) { _, which ->
                val r = rows[which]
                AlertDialog.Builder(this)
                    .setTitle(r.name)
                    .setItems(arrayOf("بازیابی", "حذف دائمی", "انصراف")) { _, o ->
                        when (o) {
                            0 -> { Repo.restoreFromTrash(r.id); toast("بازیابی شد"); redraw() }
                            1 -> ask("حذف دائمی", "این کار برای همیشه پاک می‌شود و قابل بازگشت نیست.",
                                "حذف کن") { Repo.hardDelete(r.id); toast("حذف شد"); redraw() }
                        }
                    }.show()
            }.show()
    }
}
