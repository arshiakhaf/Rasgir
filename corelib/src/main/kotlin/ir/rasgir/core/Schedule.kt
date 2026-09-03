package ir.rasgir.core

import java.math.BigInteger

/**
 * Check scheduling engine.
 *
 * Exact invariants that every produced plan honours (or reports a residual for):
 *
 *   1)  Σ checkAmount + prepayAmount = D                    (exact, toman)
 *   2)  Σ checkAmount·checkDay + prepay·prepayDay = D·T     (exact, toman·days)
 *
 * Amounts are integers in toman and days are integer linear day numbers; both
 * invariants are checked in exact [BigInteger] arithmetic — no floats anywhere.
 * If an exact integer solution does not exist (possible when dates are forced
 * to a calendar grid / locks conflict) the engine keeps invariant 1 exact,
 * minimises the invariant-2 residual and reports it precisely (spec §5/§7).
 *
 * Fit priorities (spec §5): locks > exact Σamount > exact target >
 * least change > spacing ≈ 30 > amounts rounded.
 */
data class Check(
    val amountToman: Long,
    val day: Long,
    val lockAmount: Boolean = false,
    val lockDay: Boolean = false
) {
    fun withAmount(a: Long) = copy(amountToman = a)
    fun withDay(d: Long) = copy(day = d)
}

data class CheckPlan(
    val checks: List<Check>,
    val prepayAmountToman: Long,
    val prepayDay: Long,
    /** residual of invariant 2 in toman·days. 0 = exact. */
    val residualWeighted: BigInteger,
    /** residual as a fraction of a day of "ras" for display. */
    val residualDays: Rational,
    val exact: Boolean,
    val note: String = ""
) {
    val totalChecks: BigInteger
        get() = checks.fold(BigInteger.ZERO) { a, c -> a.add(BigInteger.valueOf(c.amountToman)) }

    val weightedSum: BigInteger
        get() = checks.fold(BigInteger.ZERO) { a, c ->
            a.add(BigInteger.valueOf(c.amountToman).multiply(BigInteger.valueOf(c.day)))
        }

    val center: Rational
        get() {
            val t = totalChecks
            return if (t.signum() == 0) Rational.ZERO else Rational.of(weightedSum, t)
        }

    /** amount of checks that are neither date- nor amount-locked */
    val freeCount: Int get() = checks.count { !it.lockAmount && !it.lockDay }
}

object ScheduleEngine {
    const val DEFAULT_SPACING = 30L
    const val ROUND = 1_000_000L

    class Params(
        val debt: BigInteger,              // D
        val targetWeighted: BigInteger,    // D·T
        val prepayAmountToman: Long,
        val prepayDay: Long,
        val count: Int,
        val minDay: Long,
        val spacing: Long = DEFAULT_SPACING,
        val round: Long = ROUND,
        val keepPastDays: Boolean = false  // if false, days are clamped to minDay
    ) {
        val checksTotal: BigInteger
            get() = debt.subtract(BigInteger.valueOf(prepayAmountToman))
        val checksWeightedRequired: BigInteger
            get() = targetWeighted.subtract(
                BigInteger.valueOf(prepayAmountToman).multiply(BigInteger.valueOf(prepayDay)))
    }

    // ------------------------------------------------------------------
    // Fresh suggestion
    // ------------------------------------------------------------------
    fun suggest(p: Params): CheckPlan {
        require(p.count >= 1)
        val s = p.checksTotal
        require(s.signum() > 0) { "پیش‌پرداخت از کل بدهی بیشتر است" }
        val r = p.checksWeightedRequired
        val n = p.count

        // 1) amounts: n-1 checks rounded to `round`, last check takes the remainder.
        val q = s.divide(BigInteger.valueOf(n.toLong()))
        val qRound = q.divide(BigInteger.valueOf(p.round)).multiply(BigInteger.valueOf(p.round))
        val amounts = ArrayList<Long>(n)
        var sum = BigInteger.ZERO
        for (i in 0 until n - 1) {
            val a = qRound.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
            amounts.add(a)
            sum = sum.add(BigInteger.valueOf(a))
        }
        val last = s.subtract(sum)
        if (last > BigInteger.valueOf(Long.MAX_VALUE)) throw IllegalArgumentException("مبلغ چک بیش از حد بزرگ است")
        amounts.add(last.toLong())

        // 2) anchor first day so the cadence centres on the required target.
        var d1 = Rational.of(r, s)
        for (i in 0 until n) {
            d1 = d1.minus(Rational.of(
                BigInteger.valueOf(amounts[i]).multiply(BigInteger.valueOf(i * p.spacing)), s))
        }
        var d1Int = d1.roundDayUpHalf()
        var cadenceClamped = false
        if (!p.keepPastDays && d1Int < p.minDay) {
            d1Int = p.minDay
            cadenceClamped = true // target date lies before today: plan is late by design
        }
        val days = ArrayList<Long>(n)
        for (i in 0 until n) days.add(d1Int + i * p.spacing)

        return fitInternal(p, amounts, days, { false }, { false }, cadenceClamped)
    }

    // ------------------------------------------------------------------
    // Core fitting
    // ------------------------------------------------------------------

    /** Re-fit an existing plan after any manual change. Locks are respected. */
    fun fitPlan(
        debt: BigInteger,
        targetWeighted: BigInteger,
        plan: CheckPlan,
        minDay: Long = Long.MIN_VALUE
    ): CheckPlan {
        val p = Params(debt, targetWeighted, plan.prepayAmountToman, plan.prepayDay,
            plan.checks.size, minDay, DEFAULT_SPACING, ROUND)
        return fitInternal(
            p,
            plan.checks.map { it.amountToman },
            plan.checks.map { it.day },
            { i -> plan.checks[i].lockAmount },
            { i -> plan.checks[i].lockDay },
            cadenceClamped = false
        )
    }

    fun fitInternal(
        p: Params,
        amountsIn: List<Long>,
        daysIn: List<Long>,
        lockAmount: (Int) -> Boolean,
        lockDay: (Int) -> Boolean,
        cadenceClamped: Boolean = false
    ): CheckPlan {
        val n = amountsIn.size
        val s = p.checksTotal
        val r = p.checksWeightedRequired
        val amounts = amountsIn.toMutableList()
        val days = daysIn.toMutableList()
        val freeMoneyIdx = (0 until n).filter { !lockAmount(it) }
        val freeDayIdx = (0 until n).filter { !lockDay(it) }

        // ---- pass 1: exact amount sum (respects amount locks) ----
        var budget = s
        for (i in 0 until n) if (lockAmount(i)) budget = budget.subtract(BigInteger.valueOf(amounts[i]))
        if (budget.signum() < 0) {
            val over = budget.negate()
            return buildPlan(p, amounts, days, over, "مبلغ چک‌های قفل‌شده از بدهی بیشتر است", lockAmount, lockDay)
        }
        val freeCur = freeMoneyIdx.fold(BigInteger.ZERO) { a, i -> a.add(BigInteger.valueOf(amounts[i])) }
        if (freeCur < budget) {
            val diff = budget.subtract(freeCur)
            if (freeMoneyIdx.isEmpty()) {
                return buildPlan(p, amounts, days, budget.negate(), "جمع چک‌ها با بدهی برابر نیست", lockAmount, lockDay)
            }
            val lastIdx = freeMoneyIdx.last()
            val newVal = BigInteger.valueOf(amounts[lastIdx]).add(diff)
            if (newVal > BigInteger.valueOf(Long.MAX_VALUE)) {
                return buildPlan(p, amounts, days, BigInteger.ZERO.subtract(budget), "مبلغ چک بیش از حد بزرگ است", lockAmount, lockDay)
            }
            amounts[lastIdx] = newVal.toLong()
        } else if (freeCur > budget) {
            var excess = freeCur.subtract(budget)
            for (i in freeMoneyIdx.reversed()) {
                if (excess.signum() == 0) break
                val take = BigInteger.valueOf(amounts[i]).min(excess)
                amounts[i] = amounts[i] - take.toLong()
                excess = excess.subtract(take)
            }
            if (excess.signum() != 0) {
                // theoretically unreachable; defensive
                return buildPlan(p, amounts, days, excess, "خطای داخلی در توزیع مبلغ", lockAmount, lockDay)
            }
        }

        // ---- pass 2: exact target ----
        var e = r.subtract(weighted(amounts, days)) // amount by which weighted sum is short
        if (e == BigInteger.ZERO) return buildPlan(p, amounts, days, BigInteger.ZERO, "", lockAmount, lockDay)
        if (cadenceClamped) {
            // The whole-cadence anchor was pushed to minDay because the target
            // date lies in the past. The plan is intentionally late (its centre
            // is later than T): forcing an exact target would need dates before
            // today, which the engine never produces. Residual is kept exact and
            // reported; the UI drives the operator toward the required prepay.
            return buildPlan(p, amounts, days, e, "تاریخ هدف در گذشته است", lockAmount, lockDay)
        }

        // 2a) whole-cadence shift of every free day
        if (freeDayIdx.isNotEmpty()) {
            val freeSum = freeDayIdx.fold(BigInteger.ZERO) { a, i -> a.add(BigInteger.valueOf(amounts[i])) }
            if (freeSum.signum() > 0) {
                val qr = e.divideAndRemainder(freeSum)
                if (qr[1] == BigInteger.ZERO && qr[0] != BigInteger.ZERO &&
                    (p.keepPastDays || freeDayIdx.all { days[it] + qr[0].toLong() >= p.minDay })
                ) {
                    for (i in freeDayIdx) days[i] = days[i] + qr[0].toLong()
                    return buildPlan(p, amounts, days, BigInteger.ZERO, "", lockAmount, lockDay)
                }
            }
        }

        // 2b) single amount transfer between two free checks (day diff d)
        if (freeMoneyIdx.size >= 2) {
            outer@ for (i in freeMoneyIdx) {
                for (j in freeMoneyIdx) {
                    if (i == j) continue
                    val ddiff = BigInteger.valueOf(days[j] - days[i])
                    if (ddiff == BigInteger.ZERO) continue
                    val qr = e.divideAndRemainder(ddiff)
                    if (qr[1] == BigInteger.ZERO) {
                        val delta = qr[0]
                        if (delta.signum() != 0 &&
                            delta.abs() <= BigInteger.valueOf(amounts[i]) &&
                            BigInteger.valueOf(amounts[j]).add(delta).signum() >= 0
                        ) {
                            amounts[i] = amounts[i] - delta.toLong()
                            amounts[j] = amounts[j] + delta.toLong()
                            return buildPlan(p, amounts, days, BigInteger.ZERO, "", lockAmount, lockDay)
                        }
                    }
                }
            }
        }

        // 2c) "adjuster" search: allow ONE free check (the last free one) to take
        // any integer amount; its day is allowed to drift within ±90 days.
        if (freeMoneyIdx.size >= 2 && freeDayIdx.isNotEmpty()) {
            val adj = freeDayIdx.intersect(freeMoneyIdx.toSet()).maxOrNull()
            if (adj != null) {
                val others = freeMoneyIdx.filter { it != adj }
                val otherAmount = amounts[adj].toBigInteger().let {
                    others.fold(BigInteger.ZERO) { acc, i -> acc.add(BigInteger.valueOf(amounts[i])) }
                }
                // x + otherAmount = s  =>  x fixed; but allow moving one rounded
                // amount between two others (they stay whole millions):
                val xBase = s.subtract(otherAmount)
                val kBase = others.fold(BigInteger.ZERO) { acc, i ->
                    acc.add(BigInteger.valueOf(amounts[i]).multiply(BigInteger.valueOf(days[i])))
                }
                // search small shifts of a "million" between two other checks
                for (sh in -5L..5L) {
                    for (a in others) {
                        for (b in others) {
                            if (a == b) continue
                            val mv = sh * p.round
                            if (BigInteger.valueOf(amounts[a]).add(BigInteger.valueOf(mv)).signum() < 0) continue
                            if (BigInteger.valueOf(amounts[b]).subtract(BigInteger.valueOf(mv)).signum() < 0) continue
                            val x = xBase // unchanged: still sum exact (transfer between others)
                            if (x.signum() <= 0) continue
                            val k = kBase.add(
                                BigInteger.valueOf(mv).multiply(BigInteger.valueOf(days[a] - days[b])))
                            val rem = r.subtract(k)
                            val qr = rem.divideAndRemainder(x)
                            if (qr[1] != BigInteger.ZERO) continue
                            val d = qr[0]
                            val dMin = days[adj] - 90
                            val dMax = days[adj] + 90
                            if (d < BigInteger.valueOf(dMin) || d > BigInteger.valueOf(dMax)) continue
                            if (!p.keepPastDays && d < BigInteger.valueOf(p.minDay)) continue
                            amounts[a] = amounts[a] + mv
                            amounts[b] = amounts[b] - mv
                            amounts[adj] = x.toLong()
                            days[adj] = d.toLong()
                            return buildPlan(p, amounts, days, BigInteger.ZERO, "", lockAmount, lockDay)
                        }
                    }
                }
            }
        }

        // 2b1) small day-jitter + amount transfer: nudge one free day by ±3 days,
        // then absorb whatever weighted residual remains with a pure amount
        // transfer between two free-amount checks. Exact whenever a combination
        // exists; spacing stays within 30±3 which spec ranks below exactness.
        if (!cadenceClamped && freeMoneyIdx.size >= 2 && freeDayIdx.isNotEmpty()) {
            jitter@ for (k in freeDayIdx) {
                for (delta in -3L..3L) {
                    if (delta == 0L) continue
                    val nd = days[k] + delta
                    if (!p.keepPastDays && nd < p.minDay) continue
                    val e2 = e.subtract(BigInteger.valueOf(amounts[k]).multiply(BigInteger.valueOf(delta)))
                    if (e2 == BigInteger.ZERO) {
                        days[k] = nd
                        return buildPlan(p, amounts, days, BigInteger.ZERO, "", lockAmount, lockDay)
                    }
                    for (i in freeMoneyIdx) {
                        for (j in freeMoneyIdx) {
                            if (i == j) continue
                            val ddiff = BigInteger.valueOf(days[j] - days[i])
                            if (ddiff == BigInteger.ZERO) continue
                            val qr = e2.divideAndRemainder(ddiff)
                            if (qr[1] != BigInteger.ZERO) continue
                            val dd = qr[0]
                            if (dd.signum() == 0) continue
                            val ni = BigInteger.valueOf(amounts[i]).subtract(dd)
                            if (ni.signum() < 0) continue
                            val nj = BigInteger.valueOf(amounts[j]).add(dd)
                            if (nj.signum() < 0 || nj > BigInteger.valueOf(Long.MAX_VALUE)) continue
                            amounts[i] = ni.toLong()
                            amounts[j] = nj.toLong()
                            days[k] = nd
                            return buildPlan(p, amounts, days, BigInteger.ZERO, "", lockAmount, lockDay)
                        }
                    }
                }
            }
        }

        // 2c2) two-adjuster exact solve: pick free check k whose day may drift up to
        // ±45 days and free check m that absorbs the amount balance; exact when
        // the divisibility condition holds (very frequent).
        if (freeMoneyIdx.size >= 2 && freeDayIdx.isNotEmpty() && !cadenceClamped) {
            val sFree = s // checks total
            // prefer k = a free check with free day
            for (k in freeDayIdx) {
                if (lockAmount(k)) continue
                for (m in freeMoneyIdx) {
                    if (m == k) continue
                    // fixed others
                    var oSum = BigInteger.ZERO
                    var kO = BigInteger.ZERO
                    for (i in 0 until n) {
                        if (i == k || i == m) continue
                        oSum = oSum.add(BigInteger.valueOf(amounts[i]))
                        kO = kO.add(BigInteger.valueOf(amounts[i]).multiply(BigInteger.valueOf(days[i])))
                    }
                    val freeTotal = sFree.subtract(oSum) // x + y
                    if (freeTotal < BigInteger.TWO) continue
                    val a = r.subtract(kO).subtract(freeTotal.multiply(BigInteger.valueOf(days[m])))
                    val dm = days[m]
                    var best: Triple<Long, Long, Long>? = null // (x,y,d)
                    var bestScore = Long.MAX_VALUE
                    val dLo = maxOf(days[k] - 250, if (p.keepPastDays) Long.MIN_VALUE else p.minDay)
                    val dHi = days[k] + 250
                    for (d in dLo..dHi) {
                        if (d == dm) continue
                        val diff = BigInteger.valueOf(d - dm)
                        val dmag = diff.abs()
                        if (a.mod(dmag).signum() != 0) continue
                        val x = a.divide(diff) // exact; sign of diff handled by BigInteger
                        if (x.signum() <= 0) continue
                        val y = freeTotal.subtract(x)
                        if (y.signum() <= 0) continue
                        if (x > BigInteger.valueOf(Long.MAX_VALUE) ||
                            y > BigInteger.valueOf(Long.MAX_VALUE)) continue
                        val xi = x.toLong()
                        val yi = y.toLong()
                        val score = kotlin.math.abs(xi - amounts[k]) + kotlin.math.abs(yi - amounts[m]) +
                                kotlin.math.abs(d - days[k])
                        if (score < bestScore) {
                            bestScore = score
                            best = Triple(xi, yi, d)
                        }
                    }
                    if (best != null) {
                        amounts[k] = best.first
                        amounts[m] = best.second
                        days[k] = best.third
                        return buildPlan(p, amounts, days, BigInteger.ZERO, "", lockAmount, lockDay)
                    }
                }
            }
        }

        // 2d) small neighbourhood search: move one free day ±k days (k ≤ 3) and/or
        // transfer up to 3×round toman between free checks, choose exact if found.
        val found = neighbourhoodSearch(p, amounts, days, e, freeDayIdx, freeMoneyIdx, r)
        if (found != null) return buildPlan(p, found.first, found.second, BigInteger.ZERO, "", lockAmount, lockDay)

        // 2e) fallback: minimal residual via whole-shift of free days (rounds to best)
        var bestE = e
        var bestDays = days.toList()
        if (freeDayIdx.isNotEmpty()) {
            val freeSum = freeDayIdx.fold(BigInteger.ZERO) { a, i -> a.add(BigInteger.valueOf(amounts[i])) }
            if (freeSum.signum() > 0) {
                val delta = e.divide(freeSum)
                for (dd in longArrayOf(delta.toLong(), delta.toLong() + 1, delta.toLong() - 1)) {
                    if (!p.keepPastDays && freeDayIdx.any { days[it] + dd < p.minDay }) continue
                    val nd = days.toMutableList()
                    for (i in freeDayIdx) nd[i] = nd[i] + dd
                    val ee = r.subtract(weighted(amounts, nd))
                    if (ee.abs() < bestE.abs()) {
                        bestE = ee
                        bestDays = nd
                    }
                }
            }
        }
        val note = if (bestE != BigInteger.ZERO)
            "اختلاف رأس دقیقاً قابل حذف نبود (نزدیک‌ترین حالت ممکن محاسبه شد)"
        else ""
        return buildPlan(p, amounts, bestDays, bestE, note, lockAmount, lockDay)
    }

    private fun neighbourhoodSearch(
        p: Params,
        amounts: List<Long>,
        days: List<Long>,
        e0: BigInteger,
        freeDayIdx: List<Int>,
        freeMoneyIdx: List<Int>,
        r: BigInteger
    ): Pair<List<Long>, List<Long>>? {
        val best = FitState()
        best.init(e0)
        // day-move of a single free check by k ∈ [-3,3]
        for (k in freeDayIdx) {
            for (dk in -3L..3L) {
                if (dk == 0L) continue
                val nd = days.toMutableList()
                nd[k] = nd[k] + dk
                if (!p.keepPastDays && nd[k] < p.minDay) continue
                val ee = r.subtract(weighted(amounts, nd))
                if (ee == BigInteger.ZERO) return amounts to nd
                if (ee.abs() < best.residual.abs()) best.set(amounts, nd, ee)
            }
        }
        // amount transfer δ (in units of 1 toman up to ±2000000) between free checks
        if (freeMoneyIdx.size >= 2) {
            for (i in freeMoneyIdx) {
                for (j in freeMoneyIdx) {
                    if (i == j) continue
                    val ddiff = days[j] - days[i]
                    if (ddiff == 0L) continue
                    for (delta in -2_000_000L..2_000_000L step 100_000L) {
                        if (delta == 0L) continue
                        if (delta > amounts[i] || amounts[j] + delta < 0) continue
                        val na = amounts.toMutableList()
                        na[i] = na[i] - delta
                        na[j] = na[j] + delta
                        val ee = r.subtract(weighted(na, days))
                        if (ee == BigInteger.ZERO) return na to days
                        if (ee.abs() < best.residual.abs()) best.set(na, days, ee)
                    }
                }
            }
        }
        return null
    }

    private class FitState {
        var residual: BigInteger = BigInteger.ZERO
        var amounts: List<Long> = emptyList()
        var days: List<Long> = emptyList()
        var initDone = false

        fun init(e: BigInteger) {
            residual = e
            initDone = true
        }

        fun set(a: List<Long>, d: List<Long>, e: BigInteger) {
            if (!initDone || e.abs() < residual.abs()) {
                residual = e
                amounts = a
                days = d
                initDone = true
            }
        }
    }

    private fun weighted(amounts: List<Long>, days: List<Long>): BigInteger {
        var w = BigInteger.ZERO
        for (i in amounts.indices) w = w.add(BigInteger.valueOf(amounts[i]).multiply(BigInteger.valueOf(days[i])))
        return w
    }

    private fun buildPlan(
        p: Params,
        amounts: List<Long>,
        days: List<Long>,
        residual: BigInteger,
        note: String,
        lockA: ((Int) -> Boolean)? = null,
        lockD: ((Int) -> Boolean)? = null
    ): CheckPlan {
        val checks = amounts.indices.map { i ->
            Check(amounts[i], days[i], lockA?.invoke(i) ?: false, lockD?.invoke(i) ?: false)
        }
        val s = p.checksTotal
        val resDays = if (s.signum() == 0) Rational.ZERO else Rational.of(residual, s)
        return CheckPlan(checks, p.prepayAmountToman, p.prepayDay, residual, resDays, residual == BigInteger.ZERO, note)
    }

    // ------------------------------------------------------------------
    // Operator helpers (used by the UI for the four manual states)
    // ------------------------------------------------------------------

    class Editor(val debt: BigInteger, val targetWeighted: BigInteger, val minDay: Long = Long.MIN_VALUE) {
        private fun params(plan: CheckPlan): Params = Params(
            debt, targetWeighted, plan.prepayAmountToman, plan.prepayDay,
            plan.checks.size, minDay, DEFAULT_SPACING, ROUND
        )

        fun heal(plan: CheckPlan): CheckPlan {
            val p = params(plan)
            return fitInternal(p, plan.checks.map { it.amountToman }, plan.checks.map { it.day },
                { i -> plan.checks[i].lockAmount }, { i -> plan.checks[i].lockDay })
        }

        /** Manual amount/day change. The changed field is treated as locked for
         *  this fit; UI decides afterwards whether to keep it permanently locked. */
        fun manualChange(plan: CheckPlan, index: Int, newAmount: Long? = null, newDay: Long? = null): CheckPlan {
            require(index in plan.checks.indices)
            val p = params(plan)
            val amounts = plan.checks.map { it.amountToman }.toMutableList()
            val days = plan.checks.map { it.day }.toMutableList()
            if (newAmount != null) amounts[index] = newAmount
            if (newDay != null) days[index] = newDay
            return fitInternal(
                p, amounts, days,
                { i -> plan.checks[i].lockAmount || (i == index && newAmount != null) },
                { i -> plan.checks[i].lockDay || (i == index && newDay != null) }
            )
        }

        /** toggle a permanent lock (locks never auto-release) */
        fun setLock(plan: CheckPlan, index: Int, lockAmount: Boolean? = null, lockDay: Boolean? = null): CheckPlan {
            val nl = plan.checks.mapIndexed { i, c ->
                when {
                    i != index -> c
                    else -> c.copy(
                        lockAmount = if (lockAmount != null) lockAmount || c.lockAmount else c.lockAmount,
                        lockDay = if (lockDay != null) lockDay || c.lockDay else c.lockDay
                    )
                }
            }
            val p = params(plan)
            return fitInternal(p, nl.map { it.amountToman }, nl.map { it.day },
                { i -> nl[i].lockAmount }, { i -> nl[i].lockDay })
        }

        /** Mode 1: move every free check by [shiftDays] (unlocked days only).
         *  Applied dates become fixed (day-locked) — exactly what a manual
         *  «move the dates» tool means — then amounts re-heal to the exact
         *  invariants without the engine pulling the dates back. */
        fun shiftFree(plan: CheckPlan, shiftDays: Long): CheckPlan {
            val shifted = plan.checks.map { c ->
                if (c.lockDay) c else c.withDay(c.day + shiftDays).copy(lockDay = true)
            }
            return heal(plan.copy(checks = shifted))
        }

        /** Mode 2 focus: anchor check [index] onto [targetDay] (its date
         *  becomes fixed), then re-heal every other free check so the two
         *  exact invariants still hold around that anchor. */
        fun focusOn(plan: CheckPlan, index: Int, targetDay: Long): CheckPlan {
            val changed = plan.checks.mapIndexed { i, c ->
                if (i == index) c.withDay(targetDay).copy(lockDay = true) else c
            }
            return heal(plan.copy(checks = changed))
        }

        /** Mode 3: make check [index] heavier; free others lighten automatically. */
        fun makeHeavier(plan: CheckPlan, index: Int, deltaToman: Long): CheckPlan =
            manualChange(plan, index, newAmount = plan.checks[index].amountToman + deltaToman)

        /** Change the number of checks then heal. */
        fun setCount(plan: CheckPlan, newCount: Int): CheckPlan {
            require(newCount >= 1)
            val amounts = plan.checks.map { it.amountToman }.toMutableList()
            val days = plan.checks.map { it.day }.toMutableList()
            val la = plan.checks.map { it.lockAmount }.toMutableList()
            val ld = plan.checks.map { it.lockDay }.toMutableList()
            val spacing = if (plan.checks.size >= 2)
                plan.checks[1].day - plan.checks[0].day
            else DEFAULT_SPACING
            while (amounts.size < newCount) {
                val last = days.lastOrNull() ?: plan.prepayDay
                amounts.add(0)
                days.add(last + spacing)
                la.add(false); ld.add(false)
            }
            while (amounts.size > newCount) {
                amounts.removeAt(amounts.lastIndex)
                days.removeAt(days.lastIndex)
                la.removeAt(la.lastIndex); ld.removeAt(ld.lastIndex)
            }
            val p = params(plan)
            return fitInternal(p, amounts, days, { la[it] }, { ld[it] })
        }

        /** Apply prepayment (amount/day) and re-fit all free checks to the exact
         *  invariants (spec: پیش‌پرداخت + جمع چک‌ها = بدهی). */
        fun applyPrepay(plan: CheckPlan, prepayAmount: Long, prepayDay: Long): CheckPlan {
            val newPlan = plan.copy(prepayAmountToman = prepayAmount, prepayDay = prepayDay)
            val p = params(newPlan)
            val n = newPlan.checks.size
            // scale free amounts proportionally to the new checks total
            val oldTotal = newPlan.totalChecks // == old checks total (no prepay included)
            val newTotal = p.checksTotal
            val amounts = newPlan.checks.mapIndexed { i, c ->
                if (c.lockAmount) c.amountToman
                else {
                    val scaled = BigInteger.valueOf(c.amountToman).multiply(newTotal).divide(oldTotal)
                    scaled.toLong()
                }
            }
            val days = newPlan.checks.map { it.day }
            return fitInternal(p, amounts, days,
                { i -> newPlan.checks[i].lockAmount },
                { i -> newPlan.checks[i].lockDay })
        }
    }
}
