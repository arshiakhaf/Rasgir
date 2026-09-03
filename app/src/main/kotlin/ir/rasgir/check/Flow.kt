package ir.rasgir.check

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import ir.rasgir.core.Jalali
import ir.rasgir.core.Money
import ir.rasgir.core.model.WorkProject
import java.math.BigInteger

/** navigation pages of the wizard */
enum class Page { HOME, CUSTOMER, INVOICES, RESULT, CHECKS, PREVIEW }

/** host contract implemented by MainActivity */
interface Host {
    val project: WorkProject
    val today: Long
    fun ctx(): Context
    fun go(p: Page)
    fun back()
    fun save(significant: Boolean = false)
    fun toast(s: String)
    fun rebuild()
    fun openHistory()
    fun openSettings()
    /** open the activation screen when the device has no valid license; returns true if usable */
    fun requireActivation(): Boolean
}

/** builds the view of each page */
object Flow {

    fun build(h: Host, p: Page): View {
        val c = h.ctx()
        return when (p) {
            Page.HOME -> home(h, c)
            Page.CUSTOMER -> customer(h, c)
            Page.INVOICES -> invoices(h, c)
            Page.RESULT -> result(h, c)
            Page.CHECKS -> Checks.build(h, c)
            Page.PREVIEW -> Preview.build(h, c)
        }
    }

    private fun ctxOf(h: Host): Context = h.ctx()

    // ------------------------------------------------------------ home
    private fun home(h: Host, c: Context): View = screen(c) { col ->
        col.addView(tv(c, "رأس‌گیر چک", 28f, Pal.GREEN_DK, bold = true))
        col.addView(tv(c, "محاسبه و تنظیم چک بر پایه رأس وزنی — آفلاین", 13f, Pal.INK2))
        col.addView(vspace(c, 10f))

        val activated = Lic.isActivated()
        val licCard = card(c)
        if (activated) {
            licCard.addView(tv(c, "✅ فعال (وابسته به این دستگاه)", 15f, Pal.GREEN_DK, bold = true))
        } else {
            licCard.addView(tv(c, "❌ فعال‌سازی نشده", 15f, Pal.AMBER, bold = true))
            licCard.addView(tv(c,
                "بدون مجوز معتبر این دستگاه، ساخت کار جدید ممکن نیست. " +
                    "مجوز فقط روی همین دستگاه کار می‌کند و به حافظه امن اندروید قفل است.",
                12.5f, Pal.INK2))
            val goAct = btn(c, "فعال‌سازی", filled = false, small = true)
            goAct.setOnClickListener { h.openSettings() }
            licCard.addView(goAct)
        }
        col.addView(licCard)

        val cardNew = card(c)
        cardNew.addView(tv(c, "شروع کار جدید", 18f, Pal.INK, bold = true))
        cardNew.addView(tv(c, "مشخصات مشتری، فاکتورها، رأس و چک‌ها", 13f, Pal.INK2))
        cardNew.addView(vspace(c, 8f))
        val bNew = btn(c, "مشتری جدید")
        bNew.setOnClickListener {
            if (h.requireActivation()) { h.project.resetCustomer(); h.go(Page.CUSTOMER) }
        }
        cardNew.addView(bNew)
        col.addView(cardNew)

        val cardHis = card(c)
        cardHis.addView(tv(c, "کارهای ذخیره‌شده و تاریخچه", 18f, Pal.INK, bold = true))
        cardHis.addView(tv(c, "جستجو، ویرایش، کپی، اشتراک مجدد، زباله‌دان", 13f, Pal.INK2))
        cardHis.addView(vspace(c, 8f))
        val bHis = btn(c, "تاریخچه کارها")
        bHis.setOnClickListener { h.openHistory() }
        cardHis.addView(bHis)
        col.addView(cardHis)

        val cardSet = card(c)
        cardSet.addView(tv(c, "تنظیمات و پشتیبان", 18f, Pal.INK, bold = true))
        cardSet.addView(vspace(c, 8f))
        val bSet = btn(c, "تنظیمات")
        bSet.setOnClickListener { h.openSettings() }
        cardSet.addView(bSet)
        col.addView(cardSet)
    }

    // ------------------------------------------------------------ customer
    private fun customer(h: Host, c: Context): View {
        val wp = h.project
        return screen(c) { col ->
            col.addView(tv(c, "مشتری", 22f, Pal.GREEN_DK, bold = true))
            val nameWrap = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            nameWrap.addView(tv(c, "نام مشتری (اختیاری)", 13f, Pal.INK2, bold = true))
            val etName = android.widget.EditText(c)
            etName.setText(wp.customerName)
            etName.setTextColor(Pal.INK); etName.setTextSize(18f)
            etName.setPadding(c.dp(10), c.dp(8), c.dp(10), c.dp(8))
            etName.setSingleLine(true)
            nameWrap.addView(etName)
            col.addView(nameWrap)
            col.addView(vspace(c, 6f))

            val titleWrap = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            titleWrap.addView(tv(c, "عنوان روی تصویر تسویه", 13f, Pal.INK2, bold = true))
            val etTitle = android.widget.EditText(c)
            etTitle.setText(wp.imageTitle)
            etTitle.setTextColor(Pal.INK); etTitle.setTextSize(18f)
            etTitle.setPadding(c.dp(10), c.dp(8), c.dp(10), c.dp(8))
            etTitle.setSingleLine(true)
            titleWrap.addView(etTitle)
            col.addView(titleWrap)
            col.addView(vspace(c, 6f))

            val persist = {
                wp.customerName = etName.text.toString()
                wp.imageTitle = etTitle.text.toString()
                h.save()
            }
            etName.setOnFocusChangeListener { _, f -> if (!f) persist() }
            etTitle.setOnFocusChangeListener { _, f -> if (!f) persist() }

            col.addView(tv(c, "رنگ تصویر تسویه", 13f, Pal.INK2, bold = true))
            val chips = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            Pal.SCHEMES.forEachIndexed { i, sch ->
                val ch = chip(c, listOf("سبز", "آبی", "بنفش", "کرم")[i], wp.colorIdx == i)
                ch.setOnClickListener {
                    wp.colorIdx = i
                    h.save(true)
                    h.rebuild()
                }
                chips.addView(ch)
                chips.addView(hspace(c, 6f))
            }
            col.addView(chips)
            col.addView(vspace(c, 14f))
            val next = btn(c, "ادامه ← فاکتورها")
            next.setOnClickListener { persist(); h.go(Page.INVOICES) }
            col.addView(next)
        }
    }

    // ------------------------------------------------------------ invoices
    private fun invoices(h: Host, c: Context): View {
        val wp = h.project
        val holder = screen(c) { col ->
            col.addView(tv(c, "فاکتورها", 22f, Pal.GREEN_DK, bold = true))
            col.addView(tv(c, "مبلغ به تومان، تاریخ خرید (شمسی) و تعداد روز رأس", 13f, Pal.INK2))
            val listBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            col.addView(listBox)
            val totalTxt = tv(c, "", 17f, Pal.INK, bold = true)
            col.addView(vspace(c, 8f))
            col.addView(totalTxt)
            col.addView(vspace(c, 6f))
            val addB = btn(c, "+ افزودن فاکتور", filled = false)
            col.addView(addB)
            col.addView(vspace(c, 10f))
            val nextB = btn(c, "ادامه ← محاسبه رأس")
            col.addView(nextB)

            fun refreshTotal() {
                val d = wp.debt()
                totalTxt.text = if (d.signum() > 0) "کل بدهی: ${Money.formatBig(d)} تومان" else "کل بدهی: ۰ تومان"
                nextB.alpha = if (wp.validInvoices().isNotEmpty()) 1f else 0.45f
            }

            fun drawRows() {
                listBox.removeAllViews()
                val working = ArrayList(wp.invoices)
                working.forEachIndexed { idx, inv ->
                    val row = card(c)
                    row.gravity = android.view.Gravity.CENTER_VERTICAL
                    val cols = LinearLayout(c).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val amtW = MoneyField.build(c, "مبلغ (تومان)", inv.amountToman) { v ->
                        if (v > 0) { wp.invoices[idx] = inv.copy(amountToman = v); refreshTotal(); h.save() }
                    }
                    cols.addView(amtW.wrap)
                    val dateTxt = tv(c, "", 15f, Pal.GREEN_DK, bold = true)
                    fun fmt(d: Long) = if (d > 0) "تاریخ خرید: ${Jalali.format(d)}" else "تاریخ خرید را انتخاب کنید"
                    dateTxt.text = fmt(inv.buyDay)
                    dateTxt.isClickable = true
                    dateTxt.setOnClickListener {
                        JalaliPicker.pick(c, if (inv.buyDay > 0) inv.buyDay else h.today) { d ->
                            wp.invoices[idx] = inv.copy(buyDay = d)
                            dateTxt.text = fmt(d)
                            refreshTotal(); h.save()
                        }
                    }
                    cols.addView(dateTxt)

                    val rasL = tv(c, "روز رأس", 13f, Pal.INK2, bold = true)
                    cols.addView(rasL)
                    val etRas = android.widget.EditText(c)
                    etRas.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                    etRas.setText(if (inv.rasDays > 0) inv.rasDays.toString() else "")
                    etRas.setTextColor(Pal.INK); etRas.setTextSize(17f)
                    etRas.setPadding(c.dp(8), c.dp(4), c.dp(8), c.dp(4))
                    cols.addView(etRas)
                    etRas.setOnFocusChangeListener { _, f ->
                        if (!f) {
                            val r = etRas.text.toString().toLongOrNull()
                            if (r != null && r >= 1 && r != inv.rasDays) {
                                wp.invoices[idx] = inv.copy(rasDays = r)
                                refreshTotal(); h.save()
                            }
                        }
                    }
                    row.addView(cols)
                    val del = btn(c, "حذف", filled = true, accent = Pal.RED, block = false, small = true)
                    del.setOnClickListener {
                        wp.invoices.removeAt(idx)
                        drawRows(); refreshTotal(); h.save(true)
                    }
                    row.addView(del)
                    listBox.addView(row)
                }
            }

            addB.setOnClickListener {
                val last = wp.invoices.lastOrNull()?.buyDay ?: h.today
                wp.invoices.add(WorkProject.DraftInvoice(0L, last, 1L))
                drawRows(); refreshTotal()
            }
            nextB.setOnClickListener {
                if (wp.validInvoices().isNotEmpty()) {
                    wp.sortInvoices()
                    wp.regenerate(h.today)
                    h.save(true)
                    h.go(Page.RESULT)
                } else h.toast("حداقل یک فاکتور معتبر وارد کنید")
            }
            drawRows()
            refreshTotal()
        }
        return holder
    }

    // ------------------------------------------------------------ result (operator view P/Q/T)
    private fun result(h: Host, c: Context): View {
        val wp = h.project
        val dv = wp.compute()
        return screen(c) { col ->
            col.addView(tv(c, "رأس وزنی (مخصوص اپراتور)", 22f, Pal.GREEN_DK, bold = true))
            if (dv == null) {
                col.addView(tv(c, "فاکتور معتبری ثبت نشده است", 16f, Pal.RED))
                return@screen
            }
            fun cardRow(t: String, day: Long, sub: String?) {
                val cd = card(c)
                cd.addView(tv(c, t, 13.5f, Pal.INK2, bold = true))
                cd.addView(tv(c, Jalali.format(day), 22f, Pal.GREEN_DK, bold = true))
                if (sub != null) cd.addView(tv(c, sub, 13f, Pal.INK2))
                col.addView(cd)
            }
            val debtCard = card(c)
            debtCard.addView(tv(c, "بدهی کل", 13.5f, Pal.INK2, bold = true))
            debtCard.addView(tv(c, Money.formatBig(dv.debt) + " تومان", 22f, Pal.INK, bold = true))
            col.addView(debtCard)
            cardRow("P — تاریخ خرید وزنی", dv.displayP, "روز ${dv.displayP}")
            cardRow("Q — سررسید وزنی", dv.displayQ, "روز ${dv.displayQ}")
            cardRow("T — هدف پرداخت (Q−۲)", dv.displayT, "روز ${dv.displayT} — دو روز قبل از رأس")

            col.addView(vspace(c, 8f))
            col.addView(tv(c, "تعداد چک‌ها", 14f, Pal.INK2, bold = true))
            val countRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            val minus = btn(c, "−", filled = false, block = false, small = true)
            val cntTxt = tv(c, wp.checkCount.toString(), 20f, Pal.INK, bold = true)
            val plus = btn(c, "+", filled = false, block = false, small = true)
            cntTxt.setPadding(c.dp(14), 0, c.dp(14), 0)
            countRow.addView(minus); countRow.addView(cntTxt); countRow.addView(plus)
            col.addView(countRow)
            minus.setOnClickListener {
                if (wp.checkCount > 1) {
                    wp.checkCount = wp.checkCount - 1
                    cntTxt.text = wp.checkCount.toString()
                    h.save(true)
                }
            }
            plus.setOnClickListener {
                if (wp.checkCount < 40) {
                    wp.checkCount = wp.checkCount + 1
                    cntTxt.text = wp.checkCount.toString()
                    h.save(true)
                }
            }
            val hint = tv(c, "چک‌ها با فاصله حدود ۳۰ روز و مبالغ یکسان/میلیونی پیشنهاد می‌شوند", 12.5f, Pal.INK2)
            col.addView(hint)
            col.addView(vspace(c, 10f))
            val make = btn(c, "ساخت و تنظیم چک‌ها")
            make.setOnClickListener {
                wp.regenerate(h.today)
                h.save(true)
                h.go(Page.CHECKS)
            }
            col.addView(make)
        }
    }
}
