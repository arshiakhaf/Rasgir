package ir.rasgir.core.model

import ir.rasgir.core.Check
import ir.rasgir.core.CheckPlan
import ir.rasgir.core.Invoice
import ir.rasgir.core.Jalali
import ir.rasgir.core.Money
import ir.rasgir.core.Prepay
import ir.rasgir.core.RasEngine
import ir.rasgir.core.Rational
import ir.rasgir.core.ScheduleEngine
import java.math.BigInteger

/**
 * Pure-JVM project model shared by the Android app, the Compose variant and the
 * JVM test harness. Holds the whole working state for one customer and derives
 * every plan through the exact core engine — no Android types, no floats.
 *
 * A project flows: invoices → DebtView (P/Q/T operator view) → CheckPlan.
 * Plans are rebuilt from scratch only on structural changes (invoice list,
 * check count). Prepayment is always applied through
 * [ScheduleEngine.Editor.applyPrepay] so existing check dates / locks are kept
 * and the two exact invariants are re-healed:
 *
 *    prepay + Σchecks = D            (toman, exact)
 *    prepay·date + Σc·date = D·T     (toman·day, exact when plan.exact)
 */
class WorkProject(val id: String) {

    var customerName: String = ""
    var imageTitle: String = "شرایط تسویه حساب"
    var colorIdx: Int = 0
    var createdAtDay: Long = 0

    /** start a fresh customer while keeping the same object identity */
    fun resetCustomer() {
        customerName = ""
        imageTitle = "شرایط تسویه حساب"
        colorIdx = 0
        invoices.clear()
        checkCount = 3
        prepayToman = 0L
        prepayDay = 0L
        plan = null
    }

    data class DraftInvoice(val amountToman: Long, val buyDay: Long, val rasDays: Long) {
        fun toCore(): Invoice = Invoice(amountToman, buyDay, rasDays)
    }

    val invoices = ArrayList<DraftInvoice>()

    fun sortInvoices() {
        invoices.sortWith(compareBy({ it.buyDay }, { it.amountToman }))
    }

    fun validInvoices(): List<DraftInvoice> =
        invoices.filter { it.amountToman > 0 && it.rasDays >= 1 && it.buyDay > 0 }

    fun debt(): BigInteger =
        validInvoices().takeIf { it.isNotEmpty() }
            ?.let { RasEngine.debtOf(it.map { d -> d.toCore() }) }
            ?: BigInteger.ZERO

    class DebtView(
        val debt: BigInteger,
        val p: Rational,
        val q: Rational,
        val t: Rational,
        val targetWeighted: BigInteger,
        val displayP: Long,
        val displayQ: Long,
        val displayT: Long,
        val invoiceCount: Int
    )

    fun compute(): DebtView? {
        val v = validInvoices()
        if (v.isEmpty()) return null
        val res = RasEngine.compute(v.map { it.toCore() })
        return DebtView(
            res.debt, res.p, res.q, res.t, res.targetWeighted,
            res.displayP, res.displayQ, res.displayT, v.size
        )
    }

    var checkCount: Int = 3

    /** applied prepayment — never set silently; only [applyPrepay] changes it */
    var prepayToman: Long = 0L
        private set
    var prepayDay: Long = 0L
        private set

    var plan: CheckPlan? = null
        private set

    fun hasDebt(): Boolean = compute() != null

    fun effectivePrepayDay(todayDay: Long): Long = if (prepayDay > 0) prepayDay else todayDay

    /** raw restore used only by persistence code (no auto-apply semantics) */
    fun restorePrepay(amountToman: Long, day: Long) {
        prepayToman = amountToman
        prepayDay = day
        plan = null
    }

    /** restore an exact persisted plan (amounts/days/locks) and re-heal it */
    fun replaceChecks(todayDay: Long, checks: List<Check>) {
        if (checks.isEmpty()) return
        val p = ensurePlan(todayDay) ?: return
        val ed = editor(todayDay) ?: return
        val base = p.copy(checks = checks)
        plan = ed.heal(base)
        checkCount = plan?.checks?.size ?: checkCount
    }

    /** structural regeneration (invoice set / check count / fresh project) */
    fun regenerate(todayDay: Long) {
        val dv = compute()
        if (dv == null) { plan = null; return }
        val params = ScheduleEngine.Params(
            debt = dv.debt,
            targetWeighted = dv.targetWeighted,
            prepayAmountToman = prepayToman,
            prepayDay = effectivePrepayDay(todayDay),
            count = checkCount,
            minDay = todayDay
        )
        plan = ScheduleEngine.suggest(params)
    }

    fun ensurePlan(todayDay: Long): CheckPlan? {
        if (plan == null) regenerate(todayDay)
        return plan
    }

    private fun editor(todayDay: Long): ScheduleEngine.Editor? {
        val dv = compute() ?: return null
        return ScheduleEngine.Editor(dv.debt, dv.targetWeighted, todayDay)
    }

    /** manual amount/day edit: the edited dimension becomes (temporarily) fixed
     *  for the exact refit so the operator's number is honoured; the caller then
     *  decides whether to keep it permanently locked (spec §5: manual edits of a
     *  locked part ask whether to lock). */
    fun manualAmount(todayDay: Long, index: Int, newAmount: Long) {
        val p = plan ?: return
        val ed = editor(todayDay) ?: return
        plan = ed.manualChange(p, index, newAmount = newAmount)
    }

    fun manualDay(todayDay: Long, index: Int, newDay: Long) {
        val p = plan ?: return
        val ed = editor(todayDay) ?: return
        plan = ed.manualChange(p, index, newDay = newDay)
    }

    /** tool mode 1 — shift every free (unlocked-day) check by [deltaDays] */
    fun shiftFreeDays(todayDay: Long, deltaDays: Long) {
        val p = plan ?: return
        val ed = editor(todayDay) ?: return
        plan = ed.shiftFree(p, deltaDays)
    }

    /** tool mode 2 — put the selected check on [targetDay] (its date heals freely) */
    fun focusCheckOnDay(todayDay: Long, index: Int, targetDay: Long) {
        val p = plan ?: return
        val ed = editor(todayDay) ?: return
        plan = ed.focusOn(p, index, targetDay)
    }

    /** tool mode 3 — make check [index] heavier by [deltaToman]; rest lighten */
    fun heavierCheck(todayDay: Long, index: Int, deltaToman: Long) {
        val p = plan ?: return
        val ed = editor(todayDay) ?: return
        plan = ed.makeHeavier(p, index, deltaToman)
    }

    /** change the number of checks, re-healing the exact invariants */
    fun resizeChecks(todayDay: Long, newCount: Int) {
        val p = plan ?: return
        val ed = editor(todayDay) ?: return
        val n = newCount.coerceIn(1, 40)
        plan = ed.setCount(p, n)
        checkCount = n
    }

    fun editPlan(todayDay: Long, transform: (CheckPlan) -> CheckPlan) {
        val p = plan ?: return
        val ed = editor(todayDay) ?: return
        plan = ed.heal(transform(p))
    }

    fun toggleLock(lockAmount: Boolean?, lockDay: Boolean?, index: Int) {
        val p = plan ?: return
        val nl = p.checks.mapIndexed { i, c ->
            if (i != index) c else c.copy(
                lockAmount = if (lockAmount == true) true else if (lockAmount == false) false else c.lockAmount,
                lockDay = if (lockDay == true) true else if (lockDay == false) false else c.lockDay
            )
        }
        plan = p.copy(checks = nl)
    }

    /** explicit «اعمال پیش‌پرداخت»: keeps dates & locks, heals exact invariants */
    fun applyPrepay(todayDay: Long, amountToman: Long, payDay: Long): CheckPlan? {
        val p = plan ?: ensurePlan(todayDay) ?: return null
        val dv = compute() ?: return null
        val clampedAmount = amountToman.coerceIn(0L, dv.debt.subtract(BigInteger.ONE).longOrMax())
        val ed = editor(todayDay) ?: return null
        val newPlan = ed.applyPrepay(p, clampedAmount, payDay)
        prepayToman = clampedAmount
        prepayDay = payDay
        plan = newPlan
        return newPlan
    }

    /** remove an applied prepayment (keeps dates/locks; re-heals) */
    fun clearPrepay(todayDay: Long): CheckPlan? {
        if (prepayToman == 0L && prepayDay == 0L) return plan
        return applyPrepay(todayDay, 0L, todayDay)
    }

    private fun BigInteger.longOrMax(): Long =
        if (this > BigInteger.valueOf(Long.MAX_VALUE)) Long.MAX_VALUE else toLong()

    /**
     * Full arrangement (checks + prepay) weighted day — exact rational.
     * A = S/D where S = prepay·d0 + Σ checkAmount·checkDay.
     */
    fun arrangementCenter(todayDay: Long): Rational? {
        val dv = compute() ?: return null
        val p = plan ?: return null
        val d0 = effectivePrepayDay(todayDay)
        val w = p.weightedSum.add(BigInteger.valueOf(prepayToman).multiply(BigInteger.valueOf(d0)))
        return Rational.of(w, dv.debt)
    }

    /** exact invariant residual E = D·T − S (toman·day); 0 ⇒ exact */
    fun invariantResidual(todayDay: Long): BigInteger? {
        val dv = compute() ?: return null
        val p = plan ?: return null
        val d0 = effectivePrepayDay(todayDay)
        return dv.targetWeighted.subtract(
            p.weightedSum.add(BigInteger.valueOf(prepayToman).multiply(BigInteger.valueOf(d0))))
    }

    /**
     * Prepayment advisor (spec §6/§7): when the actual weighted day of the whole
     * arrangement A is later than the target T, the extra prepayment that
     * restores exactness is X = D·(A−T)/(A−d0), whole toman rounded up.
     * Offered to the operator — never applied automatically.
     */
    class Advisor(
        val debt: BigInteger,
        val aDay: Rational,
        val tDay: Rational,
        val d0: Long,
        val requiredToman: Long,
        val lateDays: Rational,
        val needsPrepay: Boolean,
        val tooLateToday: Boolean
    )

    /** operator display of an exact weighted day (half-up rounding, spec §2) */
    fun roundDay(r: Rational): Long = r.roundDayUpHalf()

    fun prepayAdvisor(todayDay: Long): Advisor? {
        val dv = compute() ?: return null
        val a = arrangementCenter(todayDay) ?: return null
        val t = dv.t
        // "A > T" is judged at operator display granularity (whole days),
        // the X formula itself stays exact on the underlying rationals.
        val lateAtAll = a.roundDayUpHalf() > t.roundDayUpHalf()
        // X = D·(A−T)/(A−d0) only makes sense when payment can still be made
        // before T; once d0 ≥ T even a full payment today cannot catch up.
        val tooLate = Rational.of(todayDay) > t
        val x = if (lateAtAll && !tooLate)
            Prepay.requiredToman(dv.debt, a, t, effectivePrepayDay(todayDay)) else 0L
        return Advisor(dv.debt, a, t, effectivePrepayDay(todayDay), x,
            if (lateAtAll) a.minus(t) else Rational.ZERO, lateAtAll && !tooLate, tooLate)
    }

    /** residual in days of debt-ra's = E / D, exact */
    fun residualDays(todayDay: Long): Rational? {
        val e = invariantResidual(todayDay) ?: return null
        val d = debt()
        return if (d.signum() == 0) Rational.ZERO else Rational.of(e, d)
    }

    /**
     * Warnings that may appear INSIDE the customer image (spec §7). Only shown
     * when a problem exists; never contains P/Q/T or operator internals.
     */
    fun customerWarnings(todayDay: Long): List<String> {
        val dv = compute() ?: return emptyList()
        val p = plan ?: return emptyList()
        val out = ArrayList<String>()
        val d0 = effectivePrepayDay(todayDay)
        val d = dv.debt
        val e = invariantResidual(todayDay) ?: return out
        val dayRes = Rational.of(e, d)
        val a = arrangementCenter(todayDay)!!
        val aR = a.roundDayUpHalf()
        val tR = dv.t.roundDayUpHalf()
        // sub-half-day imbalances are invisible at whole-day granularity: for the
        // customer image they are treated as exact (operator screens still show
        // the exact residual).
        val visibleImbalance = dayRes.abs() >= Rational.of(1, 2)
        if (aR > tR) {
            out.add("زمان وزنی پرداخت‌ها از موعد تعیین‌شده دیرتر است")
            if (visibleImbalance) {
                out.add("اختلاف دقیق: " + formatDaySigned(dayRes) + " (${e.abs()} تومان-روز)")
            }
            if (Rational.of(todayDay) > dv.t) {
                out.add("حتی پرداخت کامل امروز هم دیر است")
            } else {
                val x = Prepay.requiredToman(d, a, dv.t, d0)
                if (x > 0) {
                    out.add("پیش‌پرداخت لازم: ${Money.rialWords(x)} در تاریخ ${Jalali.format(d0)}")
                    out.add("این مبلغ باید از جمع چک‌ها کسر شود")
                }
            }
        } else if (aR < tR && visibleImbalance) {
            out.add("مجموع پرداخت‌ها با بدهی تراز نیست")
            out.add("اختلاف دقیق: " + formatDaySigned(dayRes) + " (${e.abs()} تومان-روز)")
        }
        return out
    }

    /** checks-only weighted day (used for «انتقال رأس» displays) */
    fun checksCenter(): Rational? {
        val p = plan ?: return null
        if (p.totalChecks.signum() <= 0) return null
        return Rational.of(p.weightedSum, p.totalChecks)
    }

    private fun formatDaySigned(r: Rational): String {
        if (r.signum == 0) return "صفر"
        val sign = if (r.num.signum() < 0) "منفی " else ""
        return sign + formatDay(r.abs())
    }

    /** exact human rendering of a non-negative rational day */
    private fun formatDay(r: Rational): String {
        if (r.isInteger()) return "${r.num} روز"
        val dec = r.asFiniteDecimalOrNull()
        if (dec != null) {
            var k = 0
            var den = r.den
            while (den > BigInteger.ONE) { den = den.divide(BigInteger.TEN); k++ }
            val s = dec.toString()
            val whole: String
            val frac: String
            if (s.length <= k) {
                whole = "0"
                frac = "0".repeat(k - s.length) + s
            } else {
                whole = s.substring(0, s.length - k)
                frac = s.substring(s.length - k)
            }
            return "$whole,${frac.trimEnd('0').ifEmpty { "0" }} روز"
        }
        return "${r.num}/${r.den} روز"
    }
}
