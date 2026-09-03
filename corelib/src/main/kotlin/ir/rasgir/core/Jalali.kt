package ir.rasgir.core

import kotlin.math.floor

/**
 * Jalali (Solar Hijri / Shamsi) calendar.
 *
 * Internal dates are represented as a linear integer "day number" so that all
 * weighted-date arithmetic in the app (which crosses month/year boundaries) is
 * plain integer math. Conversion follows the well-known 2820-year-cycle
 * algorithm (as popularized by "jalaali-js"), which is valid and tested for
 * years ~1178..3177 (Jalali), i.e. the full practical range of this app.
 *
 * The linear day number used here is the same value produced by
 * g2d()/toEpoch-ish gregorian conversion anchored so that 1404/01/01 == 2025-03-21.
 */
object Jalali {

    /** Day number of the Gregorian date (proleptic). */
    fun gregorianToDay(gy: Int, gm: Int, gd: Int): Long {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461L) / 4 +
                (153L * ((gm + 9) % 12) + 2) / 5 + gd - 34840408L
        d = d - ((gy + 100100L + (gm - 8) / 6) / 100 * 3) / 4 + 752L
        return d
    }

    /** Gregorian date from a linear day number. */
    fun dayToGregorian(jdn: Long): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631L
        j = j + ((4 * jdn + 183187720L) / 146097 * 3) / 4 * 4 - 3908L
        val i = ((j % 1461) / 4) * 5 + 308L
        val gd = (i % 153) / 5 + 1
        val gm = (i / 153 % 12) + 1
        val gy = j / 1461 - 100100 + (8 - gm) / 6
        return Triple(gy.toInt(), gm.toInt(), gd.toInt())
    }

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private class JalCal(val leap: Int, val gy: Int, val march: Int)

    // JS "~~(a/b)" truncating integer division (Kotlin Long '/' already
    // truncates toward zero) and JS "%"-remainder with dividend sign.
    private fun jdiv(a: Long, b: Long): Long = a / b
    private fun jmod(a: Long, b: Long): Long = a - (a / b) * b

    private fun jalCal(jy: Int): JalCal {
        // Faithful port of jalaali-js v2 jalCalCore + leapFromCycle.
        // NB: on the first break element the JS loop assigns jump = jm - jp
        // BEFORE the jy < jm test, so `jump` is the gap to the *next* break.
        val gy = jy + 621L
        var leapJ = -14L
        var jp = BREAKS[0].toLong()
        var jump = 0L
        var i = 1
        while (i < BREAKS.size) {
            val jm = BREAKS[i].toLong()
            jump = jm - jp
            if (jy.toLong() < jm) break
            leapJ += jdiv(jump, 33) * 8 + jdiv(jmod(jump, 33), 4)
            jp = jm
            i++
        }
        val n = jy - jp
        leapJ += jdiv(n, 33) * 8 + jdiv(jmod(n, 33) + 3, 4)
        if (jmod(jump, 33) == 4L && jump - n == 4L) leapJ += 1
        val leapG = jdiv(gy, 4) - jdiv((jdiv(gy, 100) + 1) * 3, 4) - 150L
        val march = 20 + leapJ - leapG
        var adjusted = n
        if (jump - n < 6) adjusted = n - jump + jdiv(jump + 4, 33) * 33
        var leap = jmod(jmod(adjusted + 1, 33) - 1, 4)
        if (leap == -1L) leap = 4L
        return JalCal(leap.toInt(), gy.toInt(), march.toInt())
    }

    /** Linear day number of a Jalali date. */
    fun jalaliToDay(jy: Int, jm: Int, jd: Int): Long {
        val r = jalCal(jy)
        return gregorianToDay(r.gy, 3, r.march) +
                (jm - 1) * 31L - (jm / 7) * (jm - 7) + jd - 1
    }

    /** Jalali date from a linear day number. */
    fun dayToJalali(jdn: Long): Triple<Int, Int, Int> {
        val (gy, _, _) = dayToGregorian(jdn)
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = gregorianToDay(r.gy, 3, r.march)
        var k = jdn - jdn1f
        var jm: Int
        var jd: Int
        if (k >= 0) {
            if (k <= 185) {
                jm = 1 + (k / 31).toInt()
                jd = (k % 31).toInt() + 1
                return Triple(jy, jm, jd)
            } else {
                k -= 186
            }
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        jm = 7 + (k / 30).toInt()
        jd = (k % 30).toInt() + 1
        return Triple(jy, jm, jd)
    }

    fun isLeap(jy: Int): Boolean = jalCal(jy).leap == 0 // jalaali-js: leap==0 means the year is leap

    fun daysInMonth(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm < 12 -> 30
        else -> if (isLeap(jy)) 30 else 29
    }

    const val MONTHS = 12

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    val WEEKDAY_NAMES = listOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"
    )

    /** Weekday index for a linear day number: 0 = شنبه. (1 = Monday, 7 = Sunday) */
    fun weekdayIndex(day: Long): Int {
        val (_, _, gd) = dayToGregorian(day)
        // Zeller-free: compute from a known anchor using java-like math.
        // Anchor: 2025-03-21 (day 0 anchor of 1404/1/1) was a Friday (جمعه, index 6).
        val anchorFriday = gregorianToDay(2025, 3, 21)
        val diff = (day - anchorFriday).mod(7L)
        return ((6 + diff) % 7).toInt()
    }

    /** Format as latin-digits string e.g. 1404/05/12 */
    fun format(day: Long): String {
        val (y, m, d) = dayToJalali(day)
        return "%04d/%02d/%02d".format(y, m, d)
    }

    /** today's linear day number in UTC-less local reckoning from epoch day */
    fun fromEpochDay(epochDay: Long): Long = epochDay + gregorianToDay(1970, 1, 1)

    fun toEpochDay(day: Long): Long = day - gregorianToDay(1970, 1, 1)

    /** Validate a Jalali (y,m,d); returns false if impossible. */
    fun valid(jy: Int, jm: Int, jd: Int): Boolean =
        jm in 1..12 && jd in 1..daysInMonth(jy, jm)

    /** round a fractional day to nearest day; halves round up (spec). */
    fun roundDay(x: Double): Long {
        val f = floor(x)
        val frac = x - f
        return if (frac >= 0.5) (f + 1).toLong() else f.toLong()
    }

    /** Add months keeping day clamped to month end. */
    fun addMonthsClamped(jy0: Int, jm0: Int, jd0: Int, months: Int): Triple<Int, Int, Int> {
        var total = jy0 * 12 + (jm0 - 1) + months
        var jy = Math.floorDiv(total, 12)
        var jm = Math.floorMod(total, 12) + 1
        var jd = jd0
        if (jd > daysInMonth(jy, jm)) jd = daysInMonth(jy, jm)
        return Triple(jy, jm, jd)
    }
}
