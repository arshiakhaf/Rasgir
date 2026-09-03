package ir.rasgir.check

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File

/** settings: license status, backups (manual / share / restore) */
class SettingsActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Repo.init(this)
        Repo.context = this

        val rootV = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rootV.addView(topbar(this, "تنظیمات و پشتیبان") { finish() })
        val sv = ScrollView(this)
        sv.setBackgroundColor(Pal.BG)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(28))
        }
        sv.addView(col)
        rootV.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootV)

        // ---------------- license ----------------
        val licCard = card(this)
        val activated = Lic.isActivated()
        licCard.addView(tv(this,
            if (activated) "✅ مجوز: فعال — این دستگاه" else "❌ مجوز: فعال نشده",
            16f, if (activated) Pal.GREEN_DK else Pal.AMBER, bold = true))
        licCard.addView(tv(this,
            "مجوز با کلید داخلی دستگاه (Android Keystore) قفل است و روی دستگاه دیگر کار نمی‌کند.",
            12.5f, Pal.INK2))
        val actB = btn(this, if (activated) "مشاهده / غیرفعال‌سازی" else "فعال‌سازی", filled = false, small = true)
        actB.setOnClickListener {
            if (activated) {
                ask("غیرفعال‌سازی", "مجوز فعلی از این دستگاه حذف شود؟", "حذف") { Lic.deactivate(); recreate() }
            } else startActivity(Intent(this, ActivationActivity::class.java))
        }
        licCard.addView(actB)
        col.addView(licCard)

        // ---------------- backups ----------------
        val bkCard = card(this)
        bkCard.addView(tv(this, "پشتیبان‌گیری", 16f, Pal.INK, bold = true))
        bkCard.addView(tv(this,
            "محل ذخیره: ${Repo.backupLocationText()} — حداکثر ۳۰ نسخه خودکار هر برچسب نگهداری می‌شود.",
            12.5f, Pal.INK2))
        val mkB = btn(this, "پشتیبان‌گیری دستی همین حالا", small = true)
        mkB.setOnClickListener { manualBackup() }
        bkCard.addView(mkB)
        val shareB = btn(this, "ارسال نسخه پشتیبان (فایل)", small = true, filled = false)
        shareB.setOnClickListener {
            val body = Repo.dumpAll()
            val uri = Share.cacheText(this, "Rasgir_backup.rgbk", body)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "پشتیبان رأس‌گیر چک — فایل RGBK1")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(i, "ارسال پشتیبان"))
        }
        bkCard.addView(shareB)
        col.addView(bkCard)

        // ---------------- restore ----------------
        val rsCard = card(this)
        rsCard.addView(tv(this, "بازیابی", 16f, Pal.INK, bold = true))
        rsCard.addView(tv(this,
            "جایگزینی کامل: داده‌های فعلی پاک و با پشتیبان عوض می‌شوند. ادغام: فقط کارهای جدید افزوده می‌شوند.",
            12.5f, Pal.INK2))
        val localB = btn(this, "بازیابی از پشتیبان‌های ذخیره‌شده", small = true, filled = false)
        localB.setOnClickListener { pickLocalBackup() }
        rsCard.addView(localB)
        val fileB = btn(this, "بازیابی از فایل (انتخاب فایل)", small = true, filled = false)
        fileB.setOnClickListener {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(i, 11)
        }
        rsCard.addView(fileB)
        col.addView(rsCard)

        // ---------------- maintenance ----------------
        val mCard = card(this)
        mCard.addView(tv(this, "نگهداری", 16f, Pal.INK, bold = true))
        val purB = btn(this, "حذف دائمی زباله‌دان", small = true, filled = false)
        purB.setOnClickListener {
            ask("حذف دائمی زباله‌دان", "همه موارد زباله‌دان برای همیشه پاک می‌شوند.", "پاک کن") {
                Repo.purgeTrashOlderThan(0); toast("زباله‌دان خالی شد")
            }
        }
        mCard.addView(purB)
        col.addView(mCard)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 11 && res == RESULT_OK && data?.data != null) {
            val text = try {
                contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (t: Throwable) { "" }
            if (text.isBlank()) toast("خواندن فایل ممکن نشد")
            else restoreAsk { mode -> Repo.restoreFromText(text, mode == 0); toast("بازیابی انجام شد") }
        }
    }

    private fun manualBackup() {
        if (Build.VERSION.SDK_INT < 29 &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 7)
            return
        }
        toast(if (Repo.exportBackupToStorage("manual"))
            "پشتیبان ساخته شد (${Repo.backupLocationText()})" else "ساخت پشتیبان ناموفق بود")
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 7) manualBackup()
    }

    private fun pickLocalBackup() {
        val files = Repo.backupFiles()
        if (files.isEmpty()) { toast("هیچ پشتیبان محلی پیدا نشد"); return }
        val names = files.map { it.substringAfter('|') }.toTypedArray()
        AlertDialog.Builder(this).setTitle("انتخاب پشتیبان")
            .setItems(names) { _, i -> restoreAsk { mode -> restoreLocal(files[i], mode == 0) } }
            .show()
    }

    private fun restoreLocal(ref: String, replaceAll: Boolean) {
        val body = readLocal(ref)
        if (body.isBlank()) { toast("خواندن پشتیبان ممکن نشد"); return }
        Repo.restoreFromText(body, replaceAll)
        toast("بازیابی انجام شد")
    }

    private fun readLocal(ref: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val id = ref.substringBefore('|').toLongOrNull() ?: return ""
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            return try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (t: Throwable) { "" }
        }
        val f = File(ref)
        return if (f.exists()) try { f.readText() } catch (t: Throwable) { "" } else ""
    }

    private fun restoreAsk(onMode: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("نحوه بازیابی")
            .setItems(arrayOf("جایگزینی کامل", "ادغام (بدون تکراری)", "انصراف")) { _, w ->
                if (w < 2) onMode(w)
            }.show()
    }
}
