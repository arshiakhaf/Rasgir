package ir.rasgir.test

import ir.rasgir.core.Invoice
import ir.rasgir.core.Jalali
import ir.rasgir.core.Prepay
import ir.rasgir.core.RasEngine
import ir.rasgir.core.Rational
import ir.rasgir.core.ScheduleEngine
import java.math.BigInteger
import kotlin.random.Random

object EngineTest {
    private fun inv(amount: Long, day: Long, ras: Long = 1) = Invoice(amount, day, ras)

    fun run() {
        Harness.group("تست رأس وزنی")
        // spec example: equal purchases on days 1 and 20
        val invs = listOf(inv(100, 1), inv(100, 20))
        val r1 = RasEngine.compute(invs)
        Harness.close(r1.p.toDouble(), 10.5, "P = 10.5")
        Harness.eq(r1.displayP, 11L, "نمایش P: نیم‌روز به بالا = ۱۱")
        // day 1 counts as first day: ras=1 => maturity == buy day
        Harness.check(invs[0].maturityDay == 1L, "سررسید فاکتور یک‌روزه همان روز خرید")

        // example from spec: invoice 10 Mehr with ras 1 day => maturity 10 Mehr
        val day10Mehr = Jalali.jalaliToDay(1404, 7, 10)
        Harness.check(inv(100, day10Mehr).maturityDay == day10Mehr, "فاکتور ۱۰ مهر رأس یک روز")

        // Q = weighted maturity; T = Q-2
        val r2 = RasEngine.compute(listOf(inv(150_000_000, 100, 10)))
        // single invoice: P=p=100; q=109; T=107
        Harness.close(r2.p.toDouble(), 100.0, "P تک فاکتور")
        Harness.close(r2.q.toDouble(), 109.0, "Q تک فاکتور")
        Harness.close(r2.t.toDouble(), 107.0, "T = Q-2")
        Harness.eq(r2.targetWeighted, BigInteger.valueOf(150_000_000L * 107), "D·T عدد صحیح")

        // multiple different weights
        val r3 = RasEngine.compute(listOf(inv(30_000_000, 5), inv(70_000_000, 15)))
        Harness.close(r3.p.toDouble(), 12.0, "P وزنی ۳۰/۷۰")

        Harness.group("تست پیش‌پرداخت (فرمول قطعی)")
        // spec fixed test: D=150M, A=108, T=90, d0=0 => X=25,000,000
        val debt = BigInteger.valueOf(150_000_000L)
        val x = Prepay.requiredExact(debt, Rational.of(108), Rational.of(90), 0)
        Harness.close(x.toDouble(), 25_000_000.0, "X دقیق = ۲۵ میلیون")
        Harness.eq(Prepay.requiredToman(debt, Rational.of(108), Rational.of(90), 0), 25_000_000L, "X تومان")
        // A <= T => 0
        Harness.eq(Prepay.requiredToman(debt, Rational.of(80), Rational.of(90), 0), 0L, "بدون نیاز به پیش‌پرداخت")
        // ceil behaviour
        val x2 = Prepay.requiredToman(debt, Rational.of(1085, 10), Rational.of(900, 10), 0)
        Harness.check(x2 >= 25_000_000, "پیش‌پرداخت پیشنهادی گرد بالا")

        Harness.group("تست پیشنهاد چک (جمع و رأس دقیق)")
        var exactCount = 0
        var lateCount = 0
        var nearCount = 0
        var total = 0
        val rnd = Random(7)
        var residualDaysMax = Rational.ZERO
        for (trial in 0 until 160) {
            val nInv = 1 + rnd.nextInt(4)
            val list = ArrayList<Invoice>()
            var base = 20000L
            for (i in 0 until nInv) {
                val amt = (1 + rnd.nextInt(20)) * 5_000_000L
                val buy = base + rnd.nextInt(400)
                val ras = (1 + rnd.nextInt(90)).toLong()
                list.add(inv(amt, buy, ras))
                base += rnd.nextInt(50)
            }
            val res = RasEngine.compute(list)
            val d = res.debt
            val today = 20200L + rnd.nextInt(150)
            val count = 1 + rnd.nextInt(6)
            val params = ScheduleEngine.Params(d, res.targetWeighted, 0, 0, count, today)
            val plan = ScheduleEngine.suggest(params)
            total++
            Harness.check(plan.totalChecks == d, "جمع چک‌ها == بدهی (trial $trial): ${plan.totalChecks} vs $d")
            Harness.check(plan.checks.all { it.day >= today }, "هیچ چکی در گذشته نیست (trial $trial)")
            val late = plan.residualWeighted.signum() < 0 // center later than target
            when {
                late -> lateCount++
                plan.exact -> exactCount++
                else -> {
                    nearCount++
                    // Exact integer fit can be impossible on an integer-day grid
                    // with rounded amounts; spec §5 then requires the *closest*
                    // feasible plan with the precise residual shown. These cases
                    // must be truly closest — well under half a day of ra's.
                    Harness.check(plan.residualDays.abs() < Rational.of(1, 2),
                        "باقیمانده رأس در طرح‌های بدون تأخیر زیر نیم روز است (trial $trial residual=${plan.residualDays.toDouble()} روز)")
                }
            }
            if (!plan.exact && plan.residualDays.abs() > residualDaysMax) residualDaysMax = plan.residualDays.abs()
        }
        println("  [stats: total=$total exact=$exactCount late=$lateCount nearButInexact=$nearCount maxResidualDays=${residualDaysMax.toDouble()}]")
        Harness.check(total == exactCount + lateCount + nearCount, "شمارش کامل طرح‌ها")
        Harness.check(nearCount <= 10, "موارد غیردقیق نزدیک کم‌شمارند (near=$nearCount)")

        // deterministic example: debt 150M, checks 3, target center: choose invoices
        // producing T exactly; check suggestion sum=150M and center matches target if exact
        val ex = listOf(inv(150_000_000, 1000, 1))
        val re = RasEngine.compute(ex)
        // single invoice ras=1: P=Q=1000, T=998
        val pe = ScheduleEngine.Params(re.debt, re.targetWeighted, 0, 0, 3, 0)
        val planE = ScheduleEngine.suggest(pe)
        Harness.eq(planE.totalChecks, re.debt, "جمع ۳ چک = بدهی")
        Harness.check(planE.checks.all { c -> c.day >= 0 }, "تاریخ‌ها معتبر")
        if (planE.exact) {
            val center = planE.center
            Harness.close(center.toDouble(), re.t.toDouble(), "مرکز چک‌ها = T")
        } else {
            println("  [note: deterministic example non-exact, residual=${planE.residualDays.toDouble()} days]")
        }
    }
}
