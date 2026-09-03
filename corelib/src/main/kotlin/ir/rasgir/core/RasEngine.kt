package ir.rasgir.core

import java.math.BigInteger

/**
 * Weighted-date engine of "رأس‌گیر چک".
 *
 * Domain rules (from the specification, kept exact):
 *
 *  - Each invoice: amount a_i (toman), purchase day p_i (linear day number),
 *    ras (float-days count) r_i.
 *  - Maturity day of an invoice:  q_i = p_i + r_i - 1   (buy day counts as day 1,
 *    so an invoice with r_i = 1 matures exactly on its buy day).
 *  - Debt total D = Σ a_i
 *  - Weighted purchase base  P = Σ(a_i·p_i) / D
 *  - Weighted debt maturity Q = Σ(a_i·q_i) / D
 *  - Payment target         T = Q - 2   (always pay two days before the ras)
 *
 * All internal values are exact rationals; the customer image never contains
 * P/Q/T (operator-only information).
 */
data class Invoice(
    val amountToman: Long,   // a_i
    val buyDay: Long,        // p_i (linear day number of the Jalali purchase date)
    val rasDays: Long        // r_i
) {
    val maturityDay: Long get() = buyDay + rasDays - 1

    init {
        require(amountToman > 0) { "invoice amount must be positive" }
        require(rasDays >= 1) { "ras days must be >= 1" }
    }
}

class DebtResult(
    val invoices: List<Invoice>,
    val debt: BigInteger,            // D (toman, exact)
    val p: Rational,                 // weighted purchase base day
    val q: Rational,                 // weighted maturity day
    val t: Rational,                 // target = q - 2
    /** D·T — integer, exact; equals Σ a·q − 2D */
    val targetWeighted: BigInteger
) {
    /** operator-only display values (rounded half-up to a day) */
    val displayP: Long get() = p.roundDayUpHalf()
    val displayQ: Long get() = q.roundDayUpHalf()
    val displayT: Long get() = t.roundDayUpHalf()
}

object RasEngine {

    fun debtOf(invoices: List<Invoice>): BigInteger =
        invoices.fold(BigInteger.ZERO) { acc, inv -> acc.add(BigInteger.valueOf(inv.amountToman)) }

    fun compute(invoices: List<Invoice>): DebtResult {
        require(invoices.isNotEmpty()) { "no invoices" }
        val d = debtOf(invoices)
        val numP = invoices.fold(BigInteger.ZERO) { acc, inv ->
            acc.add(BigInteger.valueOf(inv.amountToman).multiply(BigInteger.valueOf(inv.buyDay)))
        }
        val numQ = invoices.fold(BigInteger.ZERO) { acc, inv ->
            acc.add(BigInteger.valueOf(inv.amountToman).multiply(BigInteger.valueOf(inv.maturityDay)))
        }
        val p = Rational.of(numP, d)
        val q = Rational.of(numQ, d)
        val t = q.minus(Rational.TWO)
        val targetWeighted = numQ.subtract(d.multiply(BigInteger.TWO)) // = D·(Q-2)
        return DebtResult(invoices, d, p, q, t, targetWeighted)
    }

    /** Weighted center (rational day) of a set of (amount, day) pairs. */
    fun weightedCenter(amounts: List<Long>, days: List<Long>): Rational {
        require(amounts.size == days.size && amounts.isNotEmpty())
        var num = BigInteger.ZERO
        var den = BigInteger.ZERO
        for (i in amounts.indices) {
            num = num.add(BigInteger.valueOf(amounts[i]).multiply(BigInteger.valueOf(days[i])))
            den = den.add(BigInteger.valueOf(amounts[i]))
        }
        return Rational.of(num, den)
    }

    /** Check center weighted with an optional prepayment included. */
    fun checksCenter(amounts: List<Long>, days: List<Long>, prepayAmount: Long = 0, prepayDay: Long = 0): Rational {
        val total = amounts.fold(BigInteger.ZERO) { a, b -> a.add(BigInteger.valueOf(b)) }
            .add(BigInteger.valueOf(prepayAmount))
        var num = BigInteger.ZERO
        for (i in amounts.indices) {
            num = num.add(BigInteger.valueOf(amounts[i]).multiply(BigInteger.valueOf(days[i])))
        }
        num = num.add(BigInteger.valueOf(prepayAmount).multiply(BigInteger.valueOf(prepayDay)))
        return Rational.of(num, total)
    }
}
