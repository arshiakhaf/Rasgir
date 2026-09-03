package ir.rasgir.test

import ir.rasgir.core.Rational
import ir.rasgir.core.model.WorkProject
import java.math.BigInteger

object ModelTest {
    fun run() {
        Harness.group("تست مدل پروژه (پیشنهاد → پیش‌پرداخت → دقیق)")
        scenarioExactPrepay()
        scenarioTooLateToday()
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
}
