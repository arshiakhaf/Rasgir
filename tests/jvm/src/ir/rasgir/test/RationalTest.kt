package ir.rasgir.test

import ir.rasgir.core.Rational
import java.math.BigInteger

object RationalTest {
    fun run() {
        Harness.group("تست اعداد گویا و گردکردن")
        Harness.eq(Rational.of(21, 2).roundDayUpHalf(), 11L, "10.5 → 11")
        Harness.eq(Rational.of(10, 1).roundDayUpHalf(), 10L, "10 → 10")
        Harness.eq(Rational.of(41, 4).roundDayUpHalf(), 10L, "10.25 → 10")
        Harness.check(Rational.of(6, 4) == Rational.of(3, 2), "ساده‌سازی کسر")
        Harness.check(Rational.of(1, 3).times(Rational.of(3)) == Rational.ONE, "ضرب دقیق کسر")
        Harness.check(Rational.of(1, 3).plus(Rational.of(1, 6)) == Rational.of(1, 2), "جمع دقیق کسر")

        val big = BigInteger.TEN.pow(30)
        Harness.check(Rational.of(big, big.multiply(BigInteger.TWO)) == Rational.of(1, 2), "کسر با اعداد بزرگ")
    }
}
