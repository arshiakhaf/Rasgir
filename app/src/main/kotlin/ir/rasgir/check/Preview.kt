package ir.rasgir.check

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import ir.rasgir.core.model.WorkProject

/** Customer-image preview + share / save-to-gallery page. */
object Preview {

    fun build(h: Host, c: Context): View {
        val wp = h.project
        val holder = screen(c) { col ->
            col.addView(tv(c, "تصویر تسویه حساب مشتری", 22f, Pal.GREEN_DK, bold = true))
            col.addView(tv(c, "فقط اطلاعات مجاز مشتری نمایش داده می‌شود", 12.5f, Pal.INK2))

            val pages = ArrayList<Bitmap>()
            val thumbBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            col.addView(thumbBox)
            val warnTxt = tv(c, "", 13f, Pal.AMBER)
            col.addView(warnTxt)

            fun renderAndDraw() {
                thumbBox.removeAllViews()
                pages.clear()
                val warnings = wp.customerWarnings(h.today)
                val rendered = SettlementImage.render(wp, h.today, warnings)
                pages.addAll(rendered)
                val multi = rendered.size > 1
                warnTxt.text = if (warnings.isNotEmpty())
                    "⚠️ این تصویر شامل هشدار است (فقط چون مشکل وجود دارد)."
                else ""
                val info = tv(c,
                    if (multi) "${rendered.size} تصویر شماره‌دار تولید شد" else "یک تصویر بلند تولید شد",
                    13f, Pal.GREEN_DK)
                thumbBox.addView(info)
                rendered.forEachIndexed { i, bmp ->
                    val iv = ImageView(c)
                    iv.setImageBitmap(bmp)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dp(360))
                    lp.setMargins(0, c.dp(8), 0, c.dp(8))
                    iv.layoutParams = lp
                    iv.adjustViewBounds = true
                    iv.scaleType = ImageView.ScaleType.FIT_CENTER
                    thumbBox.addView(iv)
                }
            }

            val shareRow = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
            val shareB = btn(c, "اشتراک‌گذاری", block = false)
            val saveB = btn(c, "ذخیره در گالری", block = false, filled = false)
            shareRow.addView(shareB)
            shareRow.addView(hspace(c, 8f))
            shareRow.addView(saveB)
            col.addView(shareRow)

            shareB.setOnClickListener {
                if (pages.isEmpty()) return@setOnClickListener
                val name = "Rasgir_${wp.customerName.ifBlank { "check" }}".replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
                val uris = Share.cachePngs(c, pages, name)
                Share.share(c, uris, "شرایط تسویه حساب — رأس‌گیر چک")
            }
            saveB.setOnClickListener {
                if (pages.isEmpty()) return@setOnClickListener
                val name = "Rasgir_${wp.customerName.ifBlank { "check" }}".replace(Regex("[^\\p{L}\\p{N}_-]"), "_")
                h.toast(Share.saveToGallery(c, pages, name))
            }

            col.addView(vspace(c, 4f))
            val editB = btn(c, "بازگشت به تنظیم چک‌ها", filled = false)
            editB.setOnClickListener { h.back() }
            col.addView(editB)

            renderAndDraw()
        }
        return holder
    }
}
