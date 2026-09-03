package ir.rasgir.test

import ir.rasgir.core.Rational
import ir.rasgir.core.model.WorkProject
import java.math.BigInteger

object ModelTest {
    fun run() {
        Harness.group("تست مدل پروژه (پیشنهاد → پیش‌پرداخت → دقیق)")
        scenarioExactPrepay()
        scenarioTooLateToday()
        scenarioManualTools()
        scenarioPrepayLimits()
    }

    /** T is still in the future but the cadence anchor falls before today, so
     *  the plan is a little late → X is offered → applying X makes E = 0. */
    private fun scenarioExactPrepay() {
        val wp = WorkProject("exact")
        wp.invoices.add(WorkProject.DraftInvoice(150_000_000L, 19_900L, 30L))
        val today = 19_900L
        val dv = wp.compute()!!
        Harness.check(dv.debt == BigInteger.valueOf(150_000_000L), "بدهی = ۱۵۰ میلیون")
        // q = 19900+30−1 = 19929 → T = 19927
        Harness.check(dv.displayT == 19_927L, "هدف T = ۱۹۹۲۷ (got ${dv.displayT})")
        wp.ensurePlan(today)
        val plan0 = wp.plan!!
        Harness.check(plan0.checks.all { it.day >= today }, "چک‌ها از امروز به بعد")

        val adv = wp.prepayAdvisor(today)!!
        Harness.check(adv.needsPrepay, "پیش‌پرداخت لازم است (A > T)")
        Harness.check(adv.requiredToman == 15_000_000L, "X پیشنهادی = ۱۵ میلیون (got ${adv.requiredToman})")
        Harness.check(!adv.tooLateToday, "امروز هنوز دیر نیست")

        // apply via explicit button semantics
        wp.applyPrepay(today, adv.requiredToman, today)
        val p1 = wp.plan!!
        Harness.check(
            p1.totalChecks.add(BigInteger.valueOf(wp.prepayToman)) == dv.debt,
            "پیش‌پرداخت + جمع چک‌ها = بدهی")
        val e = wp.invariantResidual(today)!!
        Harness.check(e == BigInteger.ZERO, "پس از اعمال X: E = 0 دقیق (got $e)")
        Harness.check(wp.prepayAdvisor(today)!!.needsPrepay == false, "دیگر نیازی به پیش‌پرداخت نیست")
        Harness.check(wp.customerWarnings(today).isEmpty(), "هشدار تصویر پس از اعمال X خالی است")

        // overpaying keeps E ≈ 0 (sub-milli-day) and never unlocks/loses locks
        wp.applyPrepay(today, adv.requiredToman + 5_000_000L, today)
        val e2 = wp.invariantResidual(today)!!
        val rd = Rational.of(e2, dv.debt)
        Harness.check(rd.abs() < Rational.of(1, 1000), "باقیمانده پس از پرداخت بیش از پیشنهاد زیر هزارم روز (got ${rd.toDouble()})")
    }

    /** T long past: even paying the whole debt today is late → explicit note,
     *  no bogus X suggestion. */
    private fun scenarioTooLateToday() {
        val wp = WorkProject("late")
        wp.invoices.add(WorkProject.DraftInvoice(150_000_000L, 14_000L, 30L))
        val today = 20_000L
        val dv = wp.compute()!!
        Harness.check(dv.displayT < today, "هدف قبل از امروز است")
        wp.ensurePlan(today)
        val adv = wp.prepayAdvisor(today)!!
        Harness.check(adv.tooLateToday, "وضعیت «حتی پرداخت کامل امروز هم دیر است» تشخیص داده می‌شود")
        Harness.check(!adv.needsPrepay, "پیشنهاد X بی‌معنی داده نمی‌شود")
        val w = wp.customerWarnings(today)
        Harness.check(w.any { it.contains("پرداخت کامل امروز هم دیر است") },
            "در تصویر صراحتاً نوشته می‌شود که پرداخت کامل امروز هم دیر است")
        Harness.check(w.none { it.contains("پیش‌پرداخت لازم") },
            "مبلغ پیش‌پرداخت ناممکن نشان داده نمی‌شود")
    }
    /** the four adjustment modes keep Σ=D and (when no locks conflict) exactness */
    private fun scenarioManualTools() {
        val today = 24_000L

        // ---- mode 1: shift every free check by +10 days (dates commit) ----
        val wp1 = WorkProject("tools-shift")
        wp1.invoices.add(WorkProject.DraftInvoice(90_000_000L, today, 120L))
        wp1.ensurePlan(today)
        Harness.check(wp1.plan!!.exact, "طرح اولیه (ras=۱۲۰) دقیق است")
        wp1.shiftFreeDays(today, 10)
        val p1 = wp1.plan!!
        Harness.check(p1.checks.all { it.lockDay }, "تاریخ چک‌ها پس از جابه‌جایی قفل می‌شوند")
        Harness.check(p1.exact, "پس از جابه‌جایی ۱۰ روزه دقیق می‌ماند (residual=${p1.residualWeighted})")
        Harness.check(p1.totalChecks == wp1.debt(), "جمع چک‌ها پس از جابه‌جایی = بدهی")

        // ---- mode 2: anchor the ras onto a chosen check/date ----
        val anchor = p1.checks[1].day
        wp1.focusCheckOnDay(today, 1, anchor - 3)
        val p2 = wp1.plan!!
        Harness.check(p2.checks[1].day == anchor - 3 && p2.checks[1].lockDay,
            "چک انتخاب‌شده روی تاریخ خواسته‌شده می‌ماند")
        Harness.check(p2.exact, "پس از نشاندن رأس روی چک، طرح دقیق است (residual=${p2.residualWeighted})")

        // ---- mode 3: make check #0 heavier — Σ must stay = D ----
        wp1.heavierCheck(today, 0, 2_000_000L)
        Harness.check(wp1.plan!!.totalChecks == wp1.debt(), "جمع چک‌ها با وجود سنگین‌کردن = بدهی")

        // ---- mode 4: change the number of checks ----
        wp1.resizeChecks(today, 7)
        Harness.check(wp1.plan!!.checks.size == 7, "تعداد چک‌ها ۷ شد")
        Harness.check(wp1.plan!!.totalChecks == wp1.debt(), "جمع چک‌ها پس از تغییر تعداد = بدهی")
        wp1.resizeChecks(today, 2)
        Harness.check(wp1.plan!!.checks.size == 2, "تعداد چک‌ها ۲ شد")

        // persisted restore path used by Repo.decode: exact source plan restored
        val src = WorkProject("restore-src")
        src.invoices.add(WorkProject.DraftInvoice(90_000_000L, today, 120L))
        src.regenerate(today)
        Harness.check(src.plan!!.exact, "طرح مبدأ برای بازیابی دقیق است")
        val restore = WorkProject("restore")
        restore.invoices.add(WorkProject.DraftInvoice(90_000_000L, today, 120L))
        restore.regenerate(today)
        restore.replaceChecks(today, src.plan!!.checks)
        Harness.check(restore.plan!!.exact, "بازیابی چک‌ها از بلاب ذخیره‌شده دوباره دقیق می‌شود")
        Harness.check(restore.plan!!.totalChecks == restore.debt(), "جمع چک‌های بازیابی‌شده = بدهی")
    }

    /** prepay clamps to [1, D-1] and clear restores exactness */
    private fun scenarioPrepayLimits() {
        val wp = WorkProject("prepay-limits")
        wp.invoices.add(WorkProject.DraftInvoice(50_000_000L, 24_500L, 20L))
        val today = 24_500L
        wp.applyPrepay(today, 999_999_999_999L, today) // far above D → clamp to D-1
        Harness.check(wp.prepayToman < 50_000_000L,
            "پیش‌پرداخت بیش از بدهی به D−۱ محدود می‌شود (got ${wp.prepayToman})")
        Harness.check(
            wp.plan!!.totalChecks.add(BigInteger.valueOf(wp.prepayToman)) == wp.debt(),
            "جمع چک‌ها + پیش‌پرداخت = بدهی پس از محدودسازی")
        wp.clearPrepay(today)
        Harness.check(wp.prepayToman == 0L, "پاک‌کردن پیش‌پرداخت مقدار را صفر می‌کند")
        Harness.check(
            wp.plan!!.totalChecks == wp.debt(),
            "جمع چک‌ها پس از پاک‌کردن پیش‌پرداخت = بدهی")
    }
}
