package ir.rasgir.core

import java.math.BigInteger

/**
 * Prepayment (پیش‌پرداخت) engine.
 *
 * Simple-state formula from the specification:
 *
 *      X = D · (A − T) / (A − d0)
 *
 *   X  = required prepayment (toman)
 *   D  = debt (toman)
 *   A  = current weighted day of the checks (as planned)
 *   T  = target payment day (= Q − 2)
 *   d0 = prepayment day
 *
 * Everything is computed exactly with BigInteger rationals; the suggested X is
 * the smallest whole toman ≥ the exact value (rounding up), because any smaller
 * prepayment would leave the weighted center later than T.
 *
 * Reference test (spec): D=150,000,000, A=108, T=90, d0=0  =>  X=25,000,000.
 */
object Prepay {

    fun requiredExact(debt: BigInteger, aDay: Rational, tDay: Rational, d0: Long): Rational {
        val num = debt.toRational().times(aDay.minus(tDay))
        val den = aDay.minus(Rational.of(d0))
        if (den.compareTo(Rational.ZERO) <= 0) return Rational.ZERO // no correction possible
        return num.div(den)
    }

    /** smallest whole toman that satisfies the constraint (ceil) */
    fun requiredToman(debt: BigInteger, aDay: Rational, tDay: Rational, d0: Long): Long {
        val x = requiredExact(debt, aDay, tDay, d0)
        if (x.compareTo(Rational.ZERO) <= 0) return 0
        val ceil = x.ceilDay()
        return if (ceil > Long.MAX_VALUE) Long.MAX_VALUE else ceil
    }

    /**
     * Effect description when prepaying MORE than suggested: the effective
     * weighted day of the whole payment plan moves earlier by
     * X_extra/(D) days; the remaining checks (D−X) keep the same dates so the
     * schedule stays exact (D·T). Operator benefit is visualised in the UI from
     * [lateDays] / [effectiveDay].
     */
    fun effectiveDay(debt: BigInteger, xApplied: Long, aDay: Rational, d0: Long): Rational {
        val d = debt.toRational()
        val x = Rational.of(xApplied.toLong())
        // P_eff = ((D − X)·A + X·d0) / D
        return d.minus(x).times(aDay).plus(x.times(Rational.of(d0))).div(d)
    }

    /** difference of the effective day vs target: positive means still late. */
    fun lateDays(debt: BigInteger, xApplied: Long, aDay: Rational, tDay: Rational, d0: Long): Rational =
        effectiveDay(debt, xApplied, aDay, d0).minus(tDay)
}

private fun BigInteger.toRational(): Rational = Rational.of(this, BigInteger.ONE)
