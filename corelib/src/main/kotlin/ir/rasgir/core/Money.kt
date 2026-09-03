package ir.rasgir.core

import java.math.BigInteger

/**
 * Money helpers. The app works in TOMAN (unit of input/display inside the
 * app, per requirements). All money is integer and exact: amounts are stored
 * as [Long] toman (up to ±9.2e18 toman) and every derived arithmetic that can
 * grow large (weighted sums, ×10 rial conversion, ...) uses [BigInteger] so
 * nothing can overflow. Floating point is never used for money or dates.
 */
object Money {

    /** Parse a typed amount into Toman. Accepts 0-9 and Persian ۰-۹ digits,
     *  thousands separators (، , ٬ and space) are ignored. Returns null when invalid. */
    fun parseToman(raw: String): Long? {
        val sb = StringBuilder()
        for (ch in raw.trim()) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                '،', ',', '٬', ' ', '\u200C' -> { /* separator */ }
                else -> return null
            }
        }
        if (sb.isEmpty()) return null
        return try {
            sb.toString().toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Format Toman with Latin digits and comma grouping, e.g. 15,000,000 */
    fun formatToman(v: Long): String {
        val neg = v < 0
        val s = if (neg) (-v).toString() else v.toString()
        val out = StringBuilder()
        val first = s.length % 3
        for (i in s.indices) {
            if (i != 0 && (i - first) % 3 == 0) out.append(',')
            out.append(s[i])
        }
        return (if (neg) "-" else "") + out.toString()
    }

    fun formatBig(v: BigInteger): String {
        val neg = v.signum() < 0
        val s = v.abs().toString()
        val out = StringBuilder()
        val first = s.length % 3
        for (i in s.indices) {
            if (i != 0 && (i - first) % 3 == 0) out.append(',')
            out.append(s[i])
        }
        return (if (neg) "-" else "") + out.toString()
    }

    /** Toman -> Rial exact (×10). */
    fun tomanToRial(toman: Long): BigInteger = BigInteger.valueOf(toman).multiply(BigInteger.TEN)

    /** Toman -> Rial exact (BigInteger input allowed). */
    fun tomanToRialBig(toman: BigInteger): BigInteger = toman.multiply(BigInteger.TEN)

    private val UNITS = listOf(
        "", "هزار", "میلیون", "میلیارد", "بیلیون", "تریلیون",
        "کادریلیون", "کوینتیلیون", "سکستیلیون"
    )

    private val YEKAN = arrayOf(
        "", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه"
    )
    private val DAH = arrayOf(
        "ده", "یازده", "دوازده", "سیزده", "چهارده",
        "پانزده", "شانزده", "هفده", "هجده", "نوزده"
    )
    private val DAHAN = arrayOf(
        "", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"
    )
    private val SADGAN = arrayOf(
        "", "صد", "دویست", "سیصد", "چهارصد", "پانصد",
        "ششصد", "هفتصد", "هشتصد", "نهصد"
    )

    private fun threeDigits(n: Int): String {
        if (n == 0) return ""
        val sad = n / 100
        val baghi = n % 100
        val parts = ArrayList<String>()
        if (sad > 0) parts.add(SADGAN[sad])
        when {
            baghi in 1..9 -> parts.add(YEKAN[baghi])
            baghi in 10..19 -> parts.add(DAH[baghi - 10])
            baghi >= 20 -> {
                val d = baghi / 10
                val y = baghi % 10
                if (d > 0) {
                    val s = if (y > 0) DAHAN[d] + " و " + YEKAN[y] else DAHAN[d]
                    parts.add(s)
                }
            }
        }
        return parts.joinToString(" و ")
    }

    /**
     * Convert a non-negative integer to Persian words.
     * Example: 250_000_000 -> "دویست و پنجاه میلیون"
     */
    fun toWords(v: BigInteger): String {
        if (v.signum() < 0) return "منفی " + toWords(v.negate())
        if (v == BigInteger.ZERO) return "صفر"
        var n = v
        var group = 0
        val parts = ArrayList<String>()
        while (n.signum() > 0) {
            val rem = n.mod(BigInteger.valueOf(1000)).toInt()
            if (rem != 0) {
                val g = threeDigits(rem)
                val unit = if (group < UNITS.size) UNITS[group] else "گروه" + group
                parts.add(if (unit.isEmpty()) g else "$g $unit")
            }
            n = n.divide(BigInteger.valueOf(1000))
            group++
        }
        return parts.reversed().joinToString(" و ")
    }

    fun toWords(toman: Long): String = toWords(BigInteger.valueOf(toman))

    /** Amount in Rial as number and words (used inside the customer image). */
    fun rialLabel(toman: Long): String {
        val r = tomanToRial(toman)
        return "${formatBig(r)} ریال"
    }

    fun rialWords(toman: Long): String {
        val r = tomanToRial(toman)
        return "${toWords(r)} ریال"
    }
}
