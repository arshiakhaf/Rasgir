package ir.rasgir.check

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/** PNG export, gallery save and system share-sheet helpers. */
object Share {

    /** writes pages as PNGs into cache; returns uris */
    fun cachePngs(c: Context, pages: List<Bitmap>, baseName: String): List<Uri> {
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        val uris = ArrayList<Uri>()
        pages.forEachIndexed { i, bmp ->
            val multi = pages.size > 1
            val f = File(dir, "${baseName}${if (multi) "_${i + 1}" else ""}.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            uris.add(Uri.parse("content://ir.rasgir.check.files/${Uri.encode(f.name)}"))
        }
        return uris
    }

    /** writes a text body into the share cache; returns its uri */
    fun cacheText(c: Context, name: String, body: String): Uri {
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, name)
        f.writeText(body)
        return Uri.parse("content://ir.rasgir.check.files/${Uri.encode(f.name)}")
    }

    fun share(c: Context, uris: List<Uri>, text: String) {
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        c.startActivity(Intent.createChooser(i, "اشتراک تصویر تسویه"))
    }

    /** gallery save; returns display message */
    fun saveToGallery(c: Context, pages: List<Bitmap>, name: String): String {
        var saved = 0
        pages.forEachIndexed { i, bmp ->
            val multi = pages.size > 1
            val fname = "${name}${if (multi) "_${i + 1}" else ""}.png"
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fname)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Rasgir")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) {
                    c.contentResolver.openOutputStream(uri)?.use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    val upd = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                    c.contentResolver.update(uri, upd, null, null)
                    saved++
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Rasgir")
                if (dir.exists() || dir.mkdirs()) {
                    val f = File(dir, fname)
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    c.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)))
                    saved++
                }
            }
        }
        return if (saved == pages.size) "در گالری ذخیره شد" else "ذخیره در گالری ناموفق بود"
    }
}
