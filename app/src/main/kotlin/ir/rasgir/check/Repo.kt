package ir.rasgir.check

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import ir.rasgir.core.model.WorkProject
import java.io.File
import java.time.LocalDate

/**
 * Room is not used by design (fully offline, no androidx): persistence is a
 * plain SQLiteOpenHelper + a repository object with the same responsibilities a
 * DAO/repository would have, so it can later be swapped 1:1 for Room in the
 * Compose variant.
 */
object Repo {
    const val DB = "rasgir.db"
    private var h: Helper? = null

    class Helper(c: Context) : SQLiteOpenHelper(c, DB, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE projects(
                   recId INTEGER PRIMARY KEY AUTOINCREMENT,
                   createdMs INTEGER NOT NULL,
                   updatedMs INTEGER NOT NULL,
                   deleted INTEGER NOT NULL DEFAULT 0,
                   deletedMs INTEGER NOT NULL DEFAULT 0,
                   name TEXT NOT NULL DEFAULT '',
                   debt TEXT NOT NULL DEFAULT '0',
                   checks INTEGER NOT NULL DEFAULT 0,
                   data TEXT NOT NULL DEFAULT '')""")
            db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    fun init(c: Context) {
        if (h == null) h = Helper(c.applicationContext)
    }

    fun db(): SQLiteDatabase = h!!.writableDatabase

    // ---------------- settings ----------------
    fun setSetting(k: String, v: String) {
        val cv = ContentValues().apply { put("k", k); put("v", v) }
        db().insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSetting(k: String, dflt: String = ""): String {
        val c = db().rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(k))
        val r = if (c.moveToFirst()) c.getString(0) else dflt
        c.close()
        return r
    }

    // ---------------- current session ----------------
    fun currentId(): Long = getSetting("currentId").toLongOrNull() ?: -1L
    fun setCurrent(id: Long) = setSetting("currentId", id.toString())

    fun insertProject(wp: WorkProject, name: String): Long {
        val id = db().insert("projects", null, ContentValues().apply {
            put("createdMs", System.currentTimeMillis())
            put("updatedMs", System.currentTimeMillis())
            put("name", name)
            put("debt", wp.debt().toString())
            put("checks", (wp.plan?.checks?.size ?: wp.checkCount).toString())
            put("data", encode(wp))
        })
        setCurrent(id)
        return id
    }

    fun updateProject(id: Long, wp: WorkProject, name: String) {
        db().update("projects", ContentValues().apply {
            put("updatedMs", System.currentTimeMillis())
            put("name", name)
            put("debt", wp.debt().toString())
            put("checks", (wp.plan?.checks?.size ?: wp.checkCount).toString())
            put("data", encode(wp))
        }, "recId=?", arrayOf(id.toString()))
    }

    fun copyProject(id: Long): Long? {
        val c = db().rawQuery("SELECT data,name FROM projects WHERE recId=? AND deleted=0", arrayOf(id.toString()))
        if (!c.moveToFirst()) { c.close(); return null }
        val data = c.getString(0)
        val name = c.getString(1) + " (کپی)"
        c.close()
        val wp = decode(data)
        val nid = insertProject(wp, name)
        return nid
    }

    fun loadProject(id: Long): WorkProject? {
        val c = db().rawQuery("SELECT data FROM projects WHERE recId=? AND deleted=0", arrayOf(id.toString()))
        if (!c.moveToFirst()) { c.close(); return null }
        val d = c.getString(0)
        c.close()
        return decode(d)
    }

    fun loadName(id: Long): String {
        val c = db().rawQuery("SELECT name FROM projects WHERE recId=?", arrayOf(id.toString()))
        val r = if (c.moveToFirst()) c.getString(0) else ""
        c.close()
        return r
    }

    // ---------------- listing / search / trash ----------------
    data class RowInfo(
        val id: Long, val name: String, val createdMs: Long, val debt: String,
        val checks: Int, val deletedMs: Long
    )

    fun listActive(query: String = ""): List<RowInfo> {
        val q = query.trim()
        val sql = if (q.isEmpty())
            "SELECT recId,name,createdMs,debt,checks,deletedMs FROM projects WHERE deleted=0 ORDER BY updatedMs DESC"
        else
            "SELECT recId,name,createdMs,debt,checks,deletedMs FROM projects WHERE deleted=0 AND " +
                "(name LIKE ? OR debt LIKE ? OR CAST(checks AS TEXT)=? OR updatedMs LIKE ?) ORDER BY updatedMs DESC"
        val args = if (q.isEmpty()) emptyArray() else arrayOf("%$q%", "%$q%", q, "%$q%")
        val c = db().rawQuery(sql, args)
        val out = ArrayList<RowInfo>()
        while (c.moveToNext())
            out.add(RowInfo(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getInt(4), c.getLong(5)))
        c.close()
        return out
    }

    fun listTrash(): List<RowInfo> {
        val c = db().rawQuery(
            "SELECT recId,name,createdMs,debt,checks,deletedMs FROM projects WHERE deleted=1 ORDER BY deletedMs DESC", null)
        val out = ArrayList<RowInfo>()
        while (c.moveToNext())
            out.add(RowInfo(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getInt(4), c.getLong(5)))
        c.close()
        return out
    }

    fun trash(id: Long) {
        db().execSQL("UPDATE projects SET deleted=1,deletedMs=? WHERE recId=?",
            arrayOf(System.currentTimeMillis().toString(), id.toString()))
        if (currentId() == id) setSetting("currentId", "")
    }

    fun restoreFromTrash(id: Long) {
        db().execSQL("UPDATE projects SET deleted=0,deletedMs=0 WHERE recId=?", arrayOf(id.toString()))
    }

    fun purgeTrashOlderThan(days: Long) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        db().execSQL("DELETE FROM projects WHERE deleted=1 AND deletedMs<?", arrayOf(cutoff.toString()))
    }

    fun hardDelete(id: Long) {
        db().execSQL("DELETE FROM projects WHERE recId=?", arrayOf(id.toString()))
    }

    // ---------------- project encoding ----------------
    fun encode(wp: WorkProject): String {
        val sb = StringBuilder("v1")
        fun line(k: String, vararg v: Any) {
            sb.append('\n').append(k).append('|')
            sb.append(v.joinToString("|"))
        }
        line("title", wp.imageTitle)
        line("color", wp.colorIdx)
        line("count", wp.checkCount)
        if (wp.prepayToman > 0 || wp.prepayDay > 0)
            line("prepay", wp.prepayToman, wp.prepayDay)
        wp.invoices.forEach { line("inv", it.amountToman, it.buyDay, it.rasDays) }
        wp.plan?.checks?.forEach {
            line("chk", it.amountToman, it.day, if (it.lockAmount) 1 else 0, if (it.lockDay) 1 else 0)
        }
        return sb.toString()
    }

    fun decode(blob: String): WorkProject {
        val wp = WorkProject("rec-import")
        var hasPlan = false
        for (raw in blob.split('\n')) {
            val parts = raw.split('|')
            if (parts.size < 2) continue
            when (parts[0]) {
                "v1" -> {}
                "title" -> wp.imageTitle = parts[1]
                "color" -> wp.colorIdx = parts.getOrNull(1)?.toIntOrNull() ?: 0
                "count" -> parts.getOrNull(1)?.toIntOrNull()?.let { wp.checkCount = it }
                "prepay" -> {
                    wp.restorePrepay(
                        parts.getOrNull(1)?.toLongOrNull() ?: 0L,
                        parts.getOrNull(2)?.toLongOrNull() ?: 0L)
                }
                "inv" -> parts.getOrNull(1)?.toLongOrNull()?.let { a ->
                    val b = parts.getOrNull(2)?.toLongOrNull() ?: return@let
                    val r = parts.getOrNull(3)?.toLongOrNull() ?: return@let
                    if (a > 0 && r >= 1 && b > 0) wp.invoices.add(WorkProject.DraftInvoice(a, b, r))
                }
                "chk" -> { hasPlan = true }
            }
        }
        wp.regenerate(todayDay())
        if (hasPlan) restoreChecksFromBlob(wp, blob)
        return wp
    }

    private fun restoreChecksFromBlob(wp: WorkProject, blob: String) {
        val list = blob.split('\n')
            .mapNotNull { it.split('|').takeIf { p -> p[0] == "chk" && p.size >= 5 } }
            .mapNotNull {
                val a = it[1].toLongOrNull() ?: return@mapNotNull null
                val d = it[2].toLongOrNull() ?: return@mapNotNull null
                val la = it[3] == "1"; val ld = it[4] == "1"
                ir.rasgir.core.Check(a, d, la, ld)
            }
        if (list.isEmpty()) return
        wp.replaceChecks(todayDay(), list)
    }

    /** today as linear day (JVM/android shared semantics) */
    fun todayDay(): Long {
        val e = LocalDate.now().toEpochDay()
        return ir.rasgir.core.Jalali.fromEpochDay(e)
    }

    // ---------------- backups (Documents/Rasgir, legacy path or MediaStore) ----------------
    private fun baseDir26(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Rasgir")

    fun backupLocationText(): String =
        if (Build.VERSION.SDK_INT >= 29) "Downloads/Rasgir" else baseDir26().absolutePath

    fun backupFiles(): List<String> = if (Build.VERSION.SDK_INT >= 29)
        listMediaStoreBackups() else listLegacyBackups()

    private fun listLegacyBackups(): List<String> {
        val d = baseDir26()
        if (!d.exists()) return emptyList()
        return d.listFiles()?.filter { it.name.endsWith(".rgbk") }?.sortedByDescending { it.lastModified() }?.map { it.absolutePath } ?: emptyList()
    }

    private fun listMediaStoreBackups(): List<String> {
        val c = context?.contentResolver?.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.RELATIVE_PATH}=?", arrayOf("Download/Rasgir/"),
            "${MediaStore.Downloads.DATE_ADDED} DESC") ?: return emptyList()
        val out = ArrayList<String>()
        while (c.moveToNext()) out.add(c.getLong(0).toString() + "|" + c.getString(1))
        c.close()
        return out
    }

    var context: Context? = null

    fun exportBackupToStorage(tag: String): Boolean {
        val ctx = context ?: return false
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val body = dumpAll()
        return if (Build.VERSION.SDK_INT >= 29) {
            try {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Rasgir_${tag}_$stamp.rgbk")
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Rasgir/")
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return false
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) } ?: return false
                trimMediaStore(tag)
                true
            } catch (t: Throwable) { false }
        } else {
            try {
                val dir = baseDir26()
                if (!dir.exists() && !dir.mkdirs()) return false
                val f = File(dir, "Rasgir_${tag}_$stamp.rgbk")
                f.writeText(body)
                trimLegacy(tag)
                true
            } catch (t: Throwable) { false }
        }
    }

    private fun trimMediaStore(tag: String) {
        val all = listMediaStoreBackups().filter { it.contains("Rasgir_${tag}_") }
        if (all.size <= 30) return
        val ctx = context ?: return
        all.drop(30).forEach { idname ->
            val id = idname.substringBefore('|')
            ctx.contentResolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, "_id=?", arrayOf(id))
        }
    }

    private fun trimLegacy(tag: String) {
        val all = listLegacyBackups().filter { it.contains("Rasgir_${tag}_") }
        if (all.size <= 30) return
        all.drop(30).forEach { File(it).delete() }
    }

    fun dumpAll(): String {
        val sb = StringBuilder("RGBK1\n")
        val c = db().rawQuery("SELECT recId,name,debt,checks,data,deleted FROM projects", null)
        while (c.moveToNext()) {
            sb.append("P|").append(c.getLong(0)).append('|').append(c.getString(1)).append('|')
                .append(c.getString(2)).append('|').append(c.getInt(3)).append('|')
                .append(c.getInt(5)).append('|')
                .append(c.getString(4).replace("\n", "\\n")).append('\n')
        }
        c.close()
        val s = db().rawQuery("SELECT k,v FROM settings", null)
        while (s.moveToNext()) sb.append("S|").append(s.getString(0)).append('|').append(s.getString(1)).append('\n')
        s.close()
        return sb.toString()
    }

    /** merge=true: upsert by recId keeping both sides unique; replace=false path used by wipe+insert */
    fun restoreFromText(body: String, replaceAll: Boolean) {
        db().beginTransaction()
        try {
            if (replaceAll) {
                db().execSQL("DELETE FROM projects")
                db().execSQL("DELETE FROM settings")
            }
            val projects = ArrayList<List<String>>()
            val settings = ArrayList<List<String>>()
            var sawHeader = false
            for (line in body.split('\n')) {
                if (line == "RGBK1") { sawHeader = true; continue }
                if (!sawHeader) continue
                val p = line.split('|')
                if (p.isEmpty() || p[0] == "") continue
                when (p[0]) {
                    "P" -> if (p.size >= 7) projects.add(p)
                    "S" -> if (p.size >= 3) settings.add(p)
                }
            }
            if (!replaceAll) {
                // merge without duplicates: skip existing recIds
                val have = HashSet<Long>()
                val c = db().rawQuery("SELECT recId FROM projects", null)
                while (c.moveToNext()) have.add(c.getLong(0))
                c.close()
                projects.removeAll { have.contains(it[1].toLongOrNull() ?: -1L) }
            }
            for (p in projects) {
                val data = p[6].replace("\\n", "\n")
                val cv = ContentValues().apply {
                    put("recId", p[1].toLongOrNull() ?: 0L)
                    put("name", p[2]); put("debt", p[3]); put("checks", p[4].toInt())
                    put("deleted", p[5].toInt()); put("deletedMs", 0L)
                    put("createdMs", System.currentTimeMillis()); put("updatedMs", System.currentTimeMillis())
                    put("data", data)
                }
                db().insertWithOnConflict("projects", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            if (replaceAll) for (s in settings) {
                val cv = ContentValues().apply { put("k", s[1]); put("v", if (s.size > 2) s[2] else "") }
                db().insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db().setTransactionSuccessful()
        } finally {
            db().endTransaction()
        }
    }

    /** keep the last [keep] automatic backups per tag (called on significant changes) */
    fun autoBackup() {
        exportBackupToStorage("auto")
    }
}
