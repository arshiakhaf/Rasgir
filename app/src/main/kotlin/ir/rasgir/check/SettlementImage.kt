package ir.rasgir.check

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import ir.rasgir.core.Jalali
import ir.rasgir.core.Money
import ir.rasgir.core.model.WorkProject
import java.math.BigInteger

/**
 * Customer settlement image («شرایط تسویه حساب»).
 *
 * Rule (spec §8): the image shows ONLY – title, optional customer name, total
 * debt, prepayment if any, check table (amount + date), and warnings only when
 * a problem exists. It must NEVER contain the weighted P/Q/T days, base dates,
 * formulas, invoice list or other operator internals.
 *
 * Amounts are rendered in Rial — numeric with separators and as Persian words.
 * Too-tall content is split into several numbered images automatically.
 */
object SettlementImage {

    const val W = 1240
    private const val M = 90            // margin
    private const val CW = W - 2 * M    // content width
    private const val MAX_TALL = 3100   // taller ⇒ split into numbered pages
    private const val PER_PAGE = 8      // checks per page when split

    private val INK2 = 0xFF56625C.toInt()
    private val RED = Color.RED

    private fun scheme(project: WorkProject): IntArray =
        Pal.SCHEMES[project.colorIdx.coerceIn(0, Pal.SCHEMES.size - 1)]

    private fun paint(textSize: Float, color: Int, bold: Boolean = false, alignRight: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.color = color
            this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
        }

    /** width-limited text laid out flush-right (ALIGN_OPPOSITE in LTR layout);
     *  bidi still renders the Persian runs correctly inside each line */
    private fun sl(text: String, tp: TextPaint, width: Float, center: Boolean = false): StaticLayout =
        StaticLayout(text, tp, width.toInt(),
            if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_OPPOSITE,
            1.0f, 0f, false)

    private class P(
        val text: String,
        val size: Float,
        val color: Int,
        val bold: Boolean = false,
        val gapBefore: Float = 0f,
        val center: Boolean = false
    )

    fun render(project: WorkProject, todayDay: Long, warnings: List<String>): List<Bitmap> {
        val s = scheme(project)
        val ink = s[1]
        val accent = s[3]
        val dv = project.compute()
        val debtRial = if (dv != null) Money.tomanToRialBig(dv.debt) else BigInteger.ZERO
        val checks = project.plan?.checks.orEmpty()
        val debtWords = if (debtRial.signum() != 0) Money.toWords(debtRial) + " ریال" else ""

        val blocks = ArrayList<P>()
        blocks.add(P(project.imageTitle.ifBlank { "شرایط تسویه حساب" }, 54f, ink, bold = true, center = true))
        blocks.add(P("●", 34f, accent, center = true))

        if (project.customerName.isNotBlank())
            blocks.add(P("به نام: ${project.customerName}", 33f, ink, gapBefore = 16f))

        blocks.add(P("کل بدهی", 29f, INK2, gapBefore = 24f))
        blocks.add(P(Money.formatBig(debtRial) + " ریال", 45f, ink, bold = true, gapBefore = 4f))
        if (debtWords.isNotBlank()) blocks.add(P(debtWords, 28f, accent, gapBefore = 4f))

        if (project.prepayToman > 0) {
            val pr = Money.tomanToRialBig(BigInteger.valueOf(project.prepayToman))
            blocks.add(P("پیش‌پرداخت", 29f, INK2, gapBefore = 20f))
            blocks.add(P(Money.formatBig(pr) + " ریال", 36f, accent, bold = true, gapBefore = 4f))
            blocks.add(P(
                "تاریخ: ${Jalali.format(project.effectivePrepayDay(todayDay))}",
                26f, ink, gapBefore = 2f))
        }

        if (checks.isNotEmpty()) {
            blocks.add(P("چک‌ها", 29f, INK2, gapBefore = 22f))
            for ((i, chk) in checks.withIndex()) {
                val r = Money.tomanToRialBig(BigInteger.valueOf(chk.amountToman))
                val amt = Money.formatBig(r)
                val d = Jalali.format(chk.day)
                blocks.add(P("چک ${i + 1}   |   $amt ریال   |   $d", 31f, ink, gapBefore = 7f))
                val words = Money.toWords(r) + " ریال"
                blocks.add(P("        = $words", 24f, INK2, gapBefore = 1f))
            }
        }

        if (warnings.isNotEmpty()) {
            blocks.add(P("توجه", 31f, RED, bold = true, gapBefore = 18f))
            warnings.forEach { blocks.add(P("• $it", 27f, RED, gapBefore = 5f)) }
        }

        val singleHeight = measure(blocks) + 120
        return if (singleHeight <= MAX_TALL) {
            listOf(draw(blocks, s, 1, 1))
        } else {
            val chunks = splitChunks(blocks, project)
            chunks.mapIndexed { i, c -> draw(c, s, i + 1, chunks.size) }
        }
    }

    private fun isCheck(p: P) = p.text.startsWith("چک ")
    private fun isWarnStart(p: P) = p.text == "توجه"
    private fun indexOfFirstCheck(blocks: List<P>): Int = blocks.indexOfFirst { isCheck(it) }

    /** splits into numbered pages: header on page 1, then chunks with up to
     *  PER_PAGE checks (or one large warnings block); continuation pages get a
     *  compact repeated title */
    private fun splitChunks(blocks: List<P>, project: WorkProject): List<List<P>> {
        val out = ArrayList<List<P>>()
        val first = indexOfFirstCheck(blocks).let { if (it < 0) blocks.size else it }
        val header = ArrayList(blocks.subList(0, first))
        out.add(header)
        var page = ArrayList<P>()
        var checksOnPage = 0
        var i = first
        while (i < blocks.size) {
            val b = blocks[i]
            val isC = isCheck(b)
            if (isWarnStart(b)) {
                if (page.isNotEmpty()) { out.add(page); page = ArrayList(); checksOnPage = 0 }
                page.add(b)
            } else if (isC && checksOnPage >= PER_PAGE && page.isNotEmpty()) {
                out.add(page); page = ArrayList(); checksOnPage = 0
                page.add(b); checksOnPage = 1
            } else {
                if (isC) checksOnPage++
                page.add(b)
            }
            i++
        }
        if (page.isNotEmpty()) out.add(page)
        val title = project.imageTitle.ifBlank { "شرایط تسویه حساب" }
        val s = scheme(project)
        val withTitle = ArrayList<List<P>>()
        out.forEachIndexed { idx, pageBlocks ->
            if (idx == 0) withTitle.add(pageBlocks)
            else {
                val cont = ArrayList<P>()
                cont.add(P("$title (ادامه)", 40f, s[1], bold = true, center = true))
                cont.addAll(pageBlocks)
                withTitle.add(cont)
            }
        }
        return withTitle
    }

    private fun measure(blocks: List<P>): Int {
        var y = 130f
        for (b in blocks) {
            if (b.text.isEmpty()) { y += 26f; continue }
            val tp = paint(b.size, b.color, b.bold)
            val s = sl(b.text, tp, CW.toFloat(), b.center)
            y += b.gapBefore + s.height
        }
        return y.toInt() + 40
    }

    private fun draw(blocks: List<P>, s: IntArray, pageNo: Int, totalPages: Int): Bitmap {
        val h = measure(blocks)
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(s[0])
        var y = 130f
        for (b in blocks) {
            if (b.text.isEmpty()) { y += 26f; continue }
            val tp = paint(b.size, b.color, b.bold)
            val tl = sl(b.text, tp, CW.toFloat(), b.center)
            y += b.gapBefore
            cv.save()
            cv.translate(M.toFloat(), y)
            tl.draw(cv)
            cv.restore()
            y += tl.height
        }
        if (totalPages > 1) {
            val tp = paint(22f, INK2, alignRight = true)
            cv.drawText("صفحه $pageNo از $totalPages", (W - M).toFloat(), (h - 24f), tp)
        }
        return bmp
    }
}
