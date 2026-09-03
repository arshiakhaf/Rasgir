package ir.rasgir.core

import java.math.BigInteger
import java.math.RoundingMode

/**
 * Exact rational arithmetic (BigInteger based). Used for weighted dates so
 * that no fractional day information is ever lost to floating point, per the
 * project rule: "محاسبات دقیق و کسری" — floats/doubles are forbidden for
 * financial/date computations.
 */
class Rational private constructor(
    numIn: BigInteger,
    denIn: BigInteger
) : Comparable<Rational> {

    val num: BigInteger
    val den: BigInteger

    init {
        require(denIn.signum() != 0) { "denominator must not be zero" }
        var n = numIn
        var d = denIn
        if (d.signum() < 0) {
            n = n.negate()
            d = d.negate()
        }
        val g = n.gcd(d)
        if (g.signum() > 0) {
            n = n.divide(g)
            d = d.divide(g)
        }
        num = n
        den = d
    }

    fun isInteger(): Boolean = den == BigInteger.ONE

    val signum: Int get() = num.signum()

    fun toBigIntegerOrNull(): BigInteger? = if (isInteger()) num else null

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    /** Nearest day: fractions >= 0.5 round up (spec); truncation semantics exact. */
    fun roundDayUpHalf(): Long {
        // floor(num/den) then check remainder*2 >= den
        val q = num.divide(den) // truncates toward zero; num>=0 in our usage
        val r = num.remainder(den).abs()
        return if (r.multiply(BigInteger.TWO) >= den) q.add(BigInteger.ONE).toLong() else q.toLong()
    }

    fun floorDay(): Long = num.divide(den).toLong()

    fun ceilDay(): Long {
        val q = num.divide(den)
        val r = num.remainder(den)
        return if (r.signum() > 0) q.add(BigInteger.ONE).toLong() else q.toLong()
    }

    operator fun plus(o: Rational): Rational = Rational(num.multiply(o.den).add(o.num.multiply(den)), den.multiply(o.den))
    operator fun minus(o: Rational): Rational = plus(o.negate())
    operator fun times(o: Rational): Rational = Rational(num.multiply(o.num), den.multiply(o.den))
    operator fun div(o: Rational): Rational = Rational(num.multiply(o.den), den.multiply(o.num))

    fun timesBig(i: BigInteger): Rational = Rational(num.multiply(i), den)
    fun timesLong(l: Long): Rational = timesBig(BigInteger.valueOf(l))

    fun negate(): Rational = Rational(num.negate(), den)

    fun abs(): Rational = if (num.signum() < 0) negate() else this

    /** exact decimal for rationals whose denominator divides 10^k, else null */
    fun asFiniteDecimalOrNull(): BigInteger? {
        var d = den
        while (d.mod(BigInteger.TEN) == BigInteger.ZERO) d = d.divide(BigInteger.TEN)
        return if (d == BigInteger.ONE) num else null
    }

    override fun compareTo(other: Rational): Int =
        num.multiply(other.den).compareTo(other.num.multiply(den))

    override fun equals(other: Any?): Boolean =
        other is Rational && num == other.num && den == other.den

    override fun hashCode(): Int = num.hashCode() * 31 + den.hashCode()

    override fun toString(): String = if (isInteger()) num.toString() else "$num/$den"

    companion object {
        val ZERO = of(0)
        val ONE = of(1)
        val TWO = of(2)

        fun of(n: Long, d: Long = 1): Rational = of(BigInteger.valueOf(n), BigInteger.valueOf(d))
        fun of(n: Long, d: BigInteger): Rational = of(BigInteger.valueOf(n), d)
        fun of(n: BigInteger, d: Long): Rational = of(n, BigInteger.valueOf(d))

        fun of(n: BigInteger, d: BigInteger): Rational {
            require(d.signum() != 0)
            var nn = n
            var dd = d
            if (dd.signum() < 0) {
                nn = nn.negate(); dd = dd.negate()
            }
            val g = nn.gcd(dd)
            if (g.signum() > 0) {
                nn = nn.divide(g); dd = dd.divide(g)
            }
            return if (dd == BigInteger.ONE) Rational(nn, BigInteger.ONE) else Rational(nn, dd)
        }

        /** From a decimal fraction string or integer (exact). */
        fun parse(s: String): Rational {
            if (s.contains('.')) {
                val parts = s.split('.')
                val whole = parts[0].toBigIntegerOrNull() ?: BigInteger.ZERO
                val frac = parts[1]
                val scale = BigInteger.TEN.pow(frac.length)
                val f = if (frac.isEmpty()) BigInteger.ZERO else frac.toBigInteger()
                return of(whole.multiply(scale).add(f), scale)
            }
            return of(s.toBigInteger(), BigInteger.ONE)
        }

        /** Rounding used for display of check deviations: round half up on positive. */
        fun ofDaysRoundedHalfUp(r: Rational): Long = r.roundDayUpHalf()
    }
}
