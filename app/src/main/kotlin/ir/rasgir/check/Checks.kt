package ir.rasgir.check

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import ir.rasgir.core.Jalali
import ir.rasgir.core.Money
import ir.rasgir.core.model.WorkProject
import java.math.BigInteger

/** Check-plan page: rows with independent date/amount locks, the four manual
 *  modes, exactness summary and the prepayment advisor (apply is always
 *  explicit). */
object Checks {

    fun build(h: Host, c: Context): View {
        val wp = h.project
        if (!wp.hasDebt()) {
            return screen(c) { col ->
                col.addView(tv(c, "چک‌ها و پیش‌پرداخت", 22f, Pal.GREEN_DK, bold = true))
                col.addView(tv(c, "هنوز فاکتور معتبری ثبت نشده است.", 15f, Pal.RED))
                val backB = btn(c, "بازگشت به فاکتورها")
                backB.setOnClickListener { h.go(Page.INVOICES) }
                col.addView(backB)
            }
        }
        wp.ensurePlan(h.today)
        var ppAmount = wp.prepayToman
        var ppDay = wp.prepayDay

        return screen(c) { col ->
            col.addView(tv(c, "چک‌ها و پیش‌پرداخت", 22f, Pal.GREEN_DK, bold = true))
            val statusTxt = tv(c, "", 14f, Pal.INK)
            col.addView(statusTxt)
            val listBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            col.addView(listBox)

            fun refreshStatus() {
                val p = wp.plan
                if (p == null) { statusTxt.text = "هنوز طرحی ساخته نشده"; return }
                val dv = wp.compute()
                val sb = StringBuilder()
                if (dv != null)
                    sb.append("بدهی: ").append(Money.formatBig(dv.debt)).append(" تومان — ")
                sb.append("جمع چک‌ها: ").append(Money.formatBig(p.totalChecks)).append(" تومان")
                if (wp.prepayToman > 0)
                    sb.append(" — پیش‌پرداخت: ").append(Money.formatBig(BigInteger.valueOf(wp.prepayToman))).append(" تومان")
                    if (p.exact) sb.append("\nوضعیت: دقیق — رأس وزنی روی هدف است")
                    else {
                        val rw = p.residualWeighted
                        sb.append(if (rw.signum() < 0) "\nوضعیت: رأس از هدف دیرتر است (غیردقیق)"
                            else "\nوضعیت: نزدیک‌ترین حالت ممکن")
                        sb.append(" — اختلاف: ").append(Money.formatBig(rw.abs())).append(" تومان-روز")
                        if (p.note.isNotBlank()) sb.append(" (").append(p.note).append(")")
                    }
                    statusTxt.text = sb.toString()
                }

            fun rebuildRows() {
                listBox.removeAllViews()
                val p = wp.plan ?: return
                p.checks.forEachIndexed { idx, chk ->
                    val row = card(c)
                    val head = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
                    head.gravity = android.view.Gravity.CENTER_VERTICAL
                    head.addView(tv(c, "چک ${idx + 1}", 17f, Pal.INK, bold = true).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val lockA = chip(c, if (chk.lockAmount) "قفل مبلغ 🔒" else "مبلغ آزاد", chk.lockAmount)
                    val lockD = chip(c, if (chk.lockDay) "قفل تاریخ 🔒" else "تاریخ آزاد", chk.lockDay)
                    head.addView(lockA)
                    head.addView(hspace(c, 4f))
                    head.addView(lockD)
                    row.addView(head)

                    val amtW = MoneyField.build(c, "مبلغ (تومان)", chk.amountToman) { v ->
                        if (v > 0 && v != (wp.plan?.checks?.getOrNull(idx)?.amountToman ?: 0L)) {
                            val locked = wp.plan?.checks?.get(idx)?.lockAmount == true
                            if (locked) {
                                val act = c as? android.app.Activity
                                if (act != null) {
                                    act.ask("این مبلغ قفل است", "ویرایش انجام و قفل حفظ شود؟",
                                        "بله، قفل بماند", "قفل برداشته شود") {
                                        wp.manualAmount(h.today, idx, v)
                                        refreshStatus(); rebuildRows(); h.save()
                                    }
                                    return@build
                                }
                            }
                            wp.manualAmount(h.today, idx, v)
                            refreshStatus(); rebuildRows(); h.save()
                        }
                    }
                    row.addView(amtW)
                    val dTxt = tv(c, Jalali.format(chk.day), 16f, Pal.GREEN_DK, bold = true)
                    dTxt.isClickable = true
                    dTxt.text = "تاریخ: ${Jalali.format(chk.day)}"
                    dTxt.setOnClickListener {
                        JalaliPicker.pick(c, chk.day, "تاریخ چک ${idx + 1}") { d ->
                            if (d != (wp.plan?.checks?.getOrNull(idx)?.day ?: 0L)) {
                                val locked = wp.plan?.checks?.get(idx)?.lockDay == true
                                if (locked) {
                                    val act = c as? android.app.Activity
                                    if (act != null) {
                                        act.ask("این تاریخ قفل است", "ویرایش انجام و قفل حفظ شود؟",
                                            "بله، قفل بماند", "قفل برداشته شود") {
                                            wp.manualDay(h.today, idx, d)
                                            rebuildRows(); refreshStatus(); h.save()
                                        }
                                        return@pick
                                    }
                                }
                                wp.manualDay(h.today, idx, d)
                                rebuildRows(); refreshStatus(); h.save()
                            }
                        }
                    }
                    row.addView(dTxt)
                    lockA.setOnClickListener {
                        wp.toggleLock(!(wp.plan?.checks?.get(idx)?.lockAmount ?: false), null, idx)
                        rebuildRows(); refreshStatus(); h.save()
                    }
                    lockD.setOnClickListener {
                        wp.toggleLock(null, !(wp.plan?.checks?.get(idx)?.lockDay ?: false), idx)
                        rebuildRows(); refreshStatus(); h.save()
                    }
                    listBox.addView(row)
                }
            }

            rebuildRows()
            refreshStatus()
            col.addView(rule(c))

            // ---- mode 1: shift all free checks ----
            col.addView(tv(c, "ابزارهای تنظیم (حالت‌های دستی)", 16f, Pal.GREEN_DK, bold = true))
            val toolCard = card(c)
            toolCard.addView(tv(c, "۱) جابه‌جایی همه چک‌های آزاد", 13.5f, Pal.INK2, bold = true))
            val shiftSeek = SeekBar(c)
            shiftSeek.max = 240
            shiftSeek.progress = 120
            val shiftTxt = tv(c, "تغییر: ۰ روز", 13f, Pal.INK)
            shiftSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                    val d = v - 120
                    shiftTxt.text = if (d >= 0) "تغییر: عقب‌انداختن $d روز" else "تغییر: جلوآوردن ${-d} روز"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            toolCard.addView(shiftTxt)
            toolCard.addView(shiftSeek)
            val shiftB = btn(c, "اعمال جابه‌جایی", small = true)
            shiftB.setOnClickListener {
                val delta = shiftSeek.progress - 120
                if (delta != 0) {
                    wp.shiftFreeDays(h.today, delta.toLong())
                    rebuildRows(); refreshStatus(); h.save(true)
                }
            }
            toolCard.addView(shiftB)
            col.addView(toolCard)

            // ---- mode 2: weighted ras onto one check's date ----
            val focusCard = card(c)
            focusCard.addView(tv(c, "۲) نشاندن رأس وزنی روی تاریخ یک چک", 13.5f, Pal.INK2, bold = true))
            val pickIdxTxt = tv(c, "چک: ۱", 14f, Pal.INK, bold = true)
            val idxSeek = SeekBar(c).apply {
                max = (wp.plan?.checks?.size ?: 1).coerceAtLeast(1) - 1
                progress = 0
            }
            idxSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { pickIdxTxt.text = "چک: ${v + 1}" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            val dSel = tv(c, "", 15f, Pal.GREEN_DK, bold = true)
            dSel.isClickable = true
            var selDay = h.today
            dSel.text = "تاریخ: ${Jalali.format(selDay)} (برای تغییر بزنید)"
            dSel.setOnClickListener {
                JalaliPicker.pick(c, selDay) { d -> selDay = d; dSel.text = "تاریخ: ${Jalali.format(d)}" }
            }
            focusCard.addView(pickIdxTxt)
            focusCard.addView(idxSeek)
            focusCard.addView(dSel)
            val focusB = btn(c, "اعمال", small = true)
            focusB.setOnClickListener {
                wp.focusCheckOnDay(h.today, idxSeek.progress, selDay)
                rebuildRows(); refreshStatus(); h.save(true)
            }
            focusCard.addView(focusB)
            col.addView(focusCard)

            // ---- mode 3: heavier one check, lighter others ----
            val heavyCard = card(c)
            heavyCard.addView(tv(c, "۳) سنگین‌تر کردن یک چک", 13.5f, Pal.INK2, bold = true))
            var heavyIdx = 0
            val hIdxTxt = tv(c, "چک: ۱", 14f, Pal.INK, bold = true)
            val hSeek = SeekBar(c).apply {
                max = (wp.plan?.checks?.size ?: 1).coerceAtLeast(1) - 1
            }
            hSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { hIdxTxt.text = "چک: ${v + 1}"; heavyIdx = v }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            val heavyWrap = MoneyField.build(c, "افزایش مبلغ (تومان)", null) { v -> heavyAmount = v }
            heavyCard.addView(hIdxTxt)
            heavyCard.addView(hSeek)
            heavyCard.addView(heavyWrap)
            val heavyB = btn(c, "اعمال", small = true)
            heavyB.setOnClickListener {
                val dv = wp.compute() ?: return@setOnClickListener
                val inc = heavyAmount
                if (inc > 0 && BigInteger.valueOf(inc) < dv.debt) {
                    wp.heavierCheck(h.today, heavyIdx, inc)
                    rebuildRows(); refreshStatus(); h.save(true)
                } else h.toast("مبلغ نامعتبر")
            }
            heavyCard.addView(heavyB)
            col.addView(heavyCard)

            // ---- count ----
            val cntCard = card(c)
            cntCard.addView(tv(c, "۴) تغییر تعداد چک‌ها (دستی و آزاد)", 13.5f, Pal.INK2, bold = true))
            val cr = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            val minus = btn(c, "−", filled = false, block = false, small = true)
            val cntT = tv(c, (wp.plan?.checks?.size ?: wp.checkCount).toString(), 18f, Pal.INK, bold = true).apply {
                setPadding(c.dp(16), 0, c.dp(16), 0)
            }
            val plus = btn(c, "+", filled = false, block = false, small = true)
            cr.addView(minus); cr.addView(cntT); cr.addView(plus)
            cntCard.addView(cr)
            minus.setOnClickListener {
                val n = wp.plan?.checks?.size ?: return@setOnClickListener
                if (n > 1) { wp.resizeChecks(h.today, n - 1); cntT.text = (n - 1).toString(); rebuildRows(); refreshStatus(); h.save(true) }
            }
            plus.setOnClickListener {
                val n = wp.plan?.checks?.size ?: return@setOnClickListener
                if (n < 40) { wp.resizeChecks(h.today, n + 1); cntT.text = (n + 1).toString(); rebuildRows(); refreshStatus(); h.save(true) }
            }
            col.addView(cntCard)

            col.addView(rule(c))

            // ---- prepayment ----
            col.addView(tv(c, "پیش‌پرداخت", 16f, Pal.GREEN_DK, bold = true))
            val ppCard = card(c)
            val advTxt = tv(c, "", 14f, Pal.INK)
            ppCard.addView(advTxt)

            fun refreshAdvisor() {
                val adv = wp.prepayAdvisor(h.today)
                val sb = StringBuilder()
                val arr = wp.arrangementCenter(h.today)
                val tDay = wp.compute()?.t?.roundDayUpHalf()
                if (arr != null) sb.append("میانگین وزنی پرداخت‌ها: روز ").append(arr.roundDayUpHalf())
                if (tDay != null) sb.append(" — هدف T: روز ").append(tDay)
                if (adv != null) {
                    when {
                        adv.tooLateToday -> sb.append("\n⚠️ حتی پرداخت کامل امروز هم دیر است؛ رأس از موعد گذشته.")
                        adv.needsPrepay -> sb.append("\nپیشنهاد دقیق (فقط با دکمه اعمال می‌شود): ")
                            .append(Money.formatBig(BigInteger.valueOf(adv.requiredToman))).append(" تومان")
                        wp.plan?.exact == false ->
                            sb.append("\nپس از اعمال، با ابزارهای بالا رأس را دقیق کنید.")
                        wp.prepayToman > 0 -> sb.append("\n✓ با پیش‌پرداخت اعمال‌شده، رأس روی هدف است.")
                        else -> sb.append("\nرأس دقیق است — پیش‌پرداخت لازم نیست.")
                    }
                }
                advTxt.text = sb.toString()
            }
            refreshAdvisor()

            val ppWrap = MoneyField.build(c, "مبلغ پیش‌پرداخت (تومان)",
                if (wp.prepayToman > 0) wp.prepayToman else null) { v -> ppAmount = v }
            ppCard.addView(ppWrap)
            val dateTxt = tv(c, "", 15f, Pal.GREEN_DK, bold = true)
            fun refreshDateText() {
                val d = if (ppDay > 0) ppDay else wp.effectivePrepayDay(h.today)
                dateTxt.text = "تاریخ پیش‌پرداخت: ${Jalali.format(d)}  (برای تغییر بزنید)"
            }
            refreshDateText()
            dateTxt.isClickable = true
            dateTxt.setOnClickListener {
                JalaliPicker.pick(c, if (ppDay > 0) ppDay else wp.effectivePrepayDay(h.today), "تاریخ پیش‌پرداخت") { d ->
                    ppDay = d; refreshDateText()
                }
            }
            ppCard.addView(dateTxt)
            val applyB = btn(c, "اعمال پیش‌پرداخت")
            applyB.setOnClickListener {
                val v = if (ppAmount > 0) ppAmount else wp.prepayToman
                val debtMax = wp.debt()
                if (v <= 0) { h.toast("مبلغ پیش‌پرداخت را وارد کنید"); return@setOnClickListener }
                if (BigInteger.valueOf(v) >= debtMax && debtMax.signum() > 0) {
                    h.toast("پیش‌پرداخت باید کمتر از کل بدهی باشد"); return@setOnClickListener
                }
                wp.applyPrepay(h.today, v, if (ppDay > 0) ppDay else h.today)
                ppAmount = wp.prepayToman
                ppDay = wp.prepayDay
                rebuildRows(); refreshStatus(); refreshAdvisor(); h.save(true)
                h.toast("پیش‌پرداخت اعمال شد — چک‌ها بازتنظیم شدند")
            }
            ppCard.addView(applyB)
            val clearB = btn(c, "حذف پیش‌پرداخت", filled = false, small = true)
            clearB.setOnClickListener {
                wp.clearPrepay(h.today)
                ppAmount = 0; ppDay = 0
                rebuildRows(); refreshStatus(); refreshAdvisor(); h.save(true)
            }
            ppCard.addView(clearB)
            col.addView(ppCard)

            col.addView(vspace(c, 6f))
            val preview = btn(c, "مشاهده تصویر تسویه حساب ←")
            preview.setOnClickListener { h.go(Page.PREVIEW) }
            col.addView(preview)
        }
    }
}

// heavy-mode amount captured via a shared holder
private var heavyAmount: Long = 0
