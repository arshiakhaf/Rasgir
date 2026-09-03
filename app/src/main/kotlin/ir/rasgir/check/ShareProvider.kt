package ir.rasgir.check

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** tiny file provider (androidx FileProvider is unavailable offline) */
class ShareProvider : ContentProvider() {
    private val root get() = File(context!!.cacheDir, "share")
    override fun onCreate(): Boolean { root.mkdirs(); return true }
    override fun query(u: Uri, p: Array<String?>?, s: String?, a: Array<String?>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String = "image/png"
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<String?>?): Int = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String?>?): Int = 0
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = File(root, uri.lastPathSegment ?: return null)
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
