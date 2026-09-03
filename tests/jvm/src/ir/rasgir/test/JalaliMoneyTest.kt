package ir.rasgir.test

import ir.rasgir.core.Jalali
import ir.rasgir.core.Money
import java.math.BigInteger
import kotlin.random.Random

object JalaliMoneyTest {
    fun run() {
        Harness.group("تست تقویم شمسی")
        // anchors
        val n1404 = Jalali.jalaliToDay(1404, 1, 1)
        Harness.eq(n1404, Jalali.gregorianToDay(2025, 3, 21), "نوروز ۱۴۰۴ = ۲۰۲۵-۰۳-۲۱")
        Harness.eq(Jalali.jalaliToDay(1357, 11, 22), Jalali.gregorianToDay(1979, 2, 11), "۲۲ بهمن ۱۳۵۷")

        val (y, m, d) = Jalali.dayToJalali(n1404)
        Harness.check(y == 1404 && m == 1 && d == 1, "round-trip 1404/1/1 got $y/$m/$d")

        // month/year crossing
        val e29 = Jalali.jalaliToDay(1403, 12, 29)
        val e30 = Jalali.jalaliToDay(1403, 12, 30)
        val n14041 = Jalali.jalaliToDay(1404, 1, 1)
        Harness.check(Jalali.isLeap(1403), "سال ۱۴۰۳ کبیسه است")
        Harness.eq(e30 - e29, 1, "فاصله ۲۹ و ۳۰ اسفند")
        Harness.eq(n14041 - e30, 1, "عبور از سال: ۳۰ اسفند → ۱ فروردین")

        Harness.check(!Jalali.isLeap(1404), "سال ۱۴۰۴ کبیسه نیست")
        Harness.eq(Jalali.daysInMonth(1404, 12).toLong(), 29, "اسفند ۱۴۰۴ بیست و نه روزه")
        Harness.eq(Jalali.daysInMonth(1403, 12).toLong(), 30, "اسفند ۱۴۰۳ سی روزه")
        Harness.eq(Jalali.daysInMonth(1404, 6).toLong(), 31, "شهریور ۳۱ روز")
        Harness.eq(Jalali.daysInMonth(1404, 7).toLong(), 30, "مهر ۳۰ روز")

        // fuzz round-trip over a wide range
        var ok = true
        var checked = 0L
        val rnd = Random(42)
        for (i in 0 until 4000) {
            val jy = 1300 + rnd.nextInt(300)
            val jm = 1 + rnd.nextInt(12)
            val jd = 1 + rnd.nextInt(Jalali.daysInMonth(jy, jm))
            val day = Jalali.jalaliToDay(jy, jm, jd)
            val (ry, rm, rd) = Jalali.dayToJalali(day)
            if (!(ry == jy && rm == jm && rd == jd)) {
                ok = false
                break
            }
            // gregorian round trip
            val (gy, gm, gd) = Jalali.dayToGregorian(day)
            if (Jalali.gregorianToDay(gy, gm, gd) != day) {
                ok = false
                break
            }
            checked++
        }
        Harness.check(ok, "round-trip fuzz (checked=$checked)")
        Harness.eq(Jalali.format(n1404), "1404/01/01", "format")

        // weekday: 2025-03-21 was جمعه (index 6)
        Harness.eq(Jalali.weekdayIndex(n1404).toLong(), 6, "۱ فروردین ۱۴۰۴ جمعه")
        // 2025-03-22 = شنبه index 0
        Harness.eq(Jalali.weekdayIndex(n1404 + 1).toLong(), 0, "۲ فروردین ۱۴۰۴ شنبه")

        // month names
        Harness.check(Jalali.MONTH_NAMES[0] == "فروردین", "نام ماه")

        Harness.group("تست پول (تومان/ریال/حروف)")
        Harness.eq(Money.formatToman(15_000_000), "15,000,000", "جداکننده سه‌رقمی")
        Harness.eq(Money.parseToman("15,000,000")!!, 15_000_000L, "parse با ویرگول")
        Harness.eq(Money.parseToman("۱۵٬۰۰۰٬۰۰۰")!!, 15_000_000L, "parse ارقام فارسی")
        Harness.check(Money.parseToman("12.5") == null, "اعشار مجاز نیست")
        Harness.eq(Money.rialLabel(250_000_000L), "2,500,000,000 ریال", "تبدیل تومان به ریال")
        Harness.eq(Money.formatBig(BigInteger("2500000000")), "2,500,000,000", "formatBig")

        Harness.eq(Money.toWords(BigInteger("250000000")), "دویست و پنجاه میلیون", "حروف ۲۵۰ میلیون")
        Harness.eq(Money.toWords(BigInteger("1000000000000")), "یک بیلیون", "حروف بیلیون")
        Harness.eq(Money.toWords(BigInteger("0")), "صفر", "حروف صفر")
        Harness.eq(Money.toWords(BigInteger("1000000")), "یک میلیون", "حروف یک میلیون")
        Harness.eq(Money.toWords(BigInteger("123456789")), "صد و بیست و سه میلیون و چهارصد و پنجاه و شش هزار و هفتصد و هشتاد و نه", "حروف ۱۲۳۴۵۶۷۸۹")
        Harness.eq(Money.toWords(BigInteger("110")), "صد و ده", "حروف ۱۱۰")
        Harness.eq(Money.toWords(BigInteger("11")), "یازده", "حروف ۱۱")
        Harness.eq(Money.toWords(BigInteger("21")), "بیست و یک", "حروف ۲۱")
        Harness.eq(Money.toWords(BigInteger("505")), "پانصد و پنج", "حروف ۵۰۵")
        Harness.eq(Money.toWords(BigInteger("1000")), "یک هزار", "حروف ۱۰۰۰")
    }
}
