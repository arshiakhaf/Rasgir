package ir.rasgir.manager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** manager storage: settings + issued-license history */
object Ms {
    private lateinit var h: H

    private class H(ctx: Context) : SQLiteOpenHelper(ctx, "manager.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT NOT NULL)")
            db.execSQL("""CREATE TABLE licenses(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer TEXT NOT NULL,
                app_id TEXT NOT NULL,
                dev_hash TEXT NOT NULL,
                request TEXT NOT NULL,
                issued_day INTEGER NOT NULL,
                expiry_day INTEGER NOT NULL,
                license_text TEXT NOT NULL,
                created_ms INTEGER NOT NULL)""")
        }
        override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
    }

    fun init(c: Context) {
        if (!::h.isInitialized) h = H(c.applicationContext)
    }

    private fun db(): SQLiteDatabase = h.writableDatabase

    fun put(k: String, v: String) {
        db().insertWithOnConflict("settings", null,
            ContentValues().apply { put("k", k); put("v", v) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun get(k: String, d: String = ""): String =
        db().rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(k))
            .use { c -> if (c.moveToFirst()) c.getString(0) else d }

    data class Lic(
        val id: Long, val customer: String, val appId: String, val devHash: String,
        val request: String, val issuedDay: Long, val expiryDay: Long,
        val licenseText: String, val createdMs: Long
    )

    fun addLic(l: Lic): Long =
        db().insert("licenses", null, ContentValues().apply {
            put("customer", l.customer); put("app_id", l.appId)
            put("dev_hash", l.devHash); put("request", l.request)
            put("issued_day", l.issuedDay); put("expiry_day", l.expiryDay)
            put("license_text", l.licenseText); put("created_ms", l.createdMs)
        })

    fun countLic(): Long =
        db().rawQuery("SELECT COUNT(*) FROM licenses", null)
            .use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    fun clearAll() {
        db().delete("licenses", null, null)
        put("bundle", "")
    }

    fun bundleOf(body: String): String? {
        for (line in body.split('\n')) if (line.startsWith("bundle=")) return line.removePrefix("bundle=")
        return null
    }

    fun listLic(limit: Int = 200): List<Lic> {
        val out = ArrayList<Lic>()
        db().rawQuery("SELECT * FROM licenses ORDER BY created_ms DESC LIMIT $limit", null)
            .use { c ->
                while (c.moveToNext()) out.add(Lic(
                    c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                    c.getString(4), c.getLong(5), c.getLong(6), c.getString(7), c.getLong(8)))
            }
        return out
    }

    fun getLic(id: Long): Lic? =
        db().rawQuery("SELECT * FROM licenses WHERE id=?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) Lic(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                c.getString(4), c.getLong(5), c.getLong(6), c.getString(7), c.getLong(8)) else null }

    fun delLic(id: Long) = db().delete("licenses", "id=?", arrayOf(id.toString()))

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace("|", "\\p")

    private fun unesc(s: String): String =
        s.replace("\\p", "|").replace("\\n", "\n").replace("\\\\", "\\")

    /** textual vault export (bundle + history) */
    fun exportText(bundle: String): String {
        val sb = StringBuilder("RGVAULT1\nbundle=$bundle\n")
        for (l in listLic(10000)) {
            sb.append("L|").append(l.id).append('|').append(esc(l.customer)).append('|').append(esc(l.appId))
                .append('|').append(esc(l.devHash)).append('|').append(esc(l.request)).append('|')
                .append(l.issuedDay).append('|').append(l.expiryDay).append('|')
                .append(l.createdMs).append('|').append(esc(l.licenseText)).append('\n')
        }
        return sb.toString()
    }

    /** import vault text; replaceAll=false merges unique dev_hash+customer rows */
    fun importText(body: String, replaceAll: Boolean): String? { // returns bundle or null
        if (!body.startsWith("RGVAULT1")) return null
        var bundle = ""
        val rows = ArrayList<List<String>>()
        for (line in body.split('\n')) {
            when {
                line.startsWith("bundle=") -> bundle = line.removePrefix("bundle=")
                line.startsWith("L|") -> rows.add(line.split('|'))
            }
        }
        if (bundle.isBlank()) return null
        if (replaceAll) {
            db().delete("licenses", null, null)
            put("bundle", bundle)
        }
        val have = HashSet<String>()
        if (!replaceAll) {
            db().rawQuery("SELECT dev_hash||'|'||customer FROM licenses", null)
                .use { c -> while (c.moveToNext()) have.add(c.getString(0)) }
        }
        for (r in rows) {
            if (r.size < 10) continue
            val customer = unesc(r[2])
            val key = r[4] + "|" + customer
            if (!replaceAll && have.contains(key)) continue
            addLic(Lic(r[1].toLongOrNull() ?: 0L, customer, unesc(r[3]), unesc(r[4]), unesc(r[5]),
                r[6].toLongOrNull() ?: 0L, r[7].toLongOrNull() ?: 0L,
                unesc(r[9]), r[8].toLongOrNull() ?: System.currentTimeMillis()))
        }
        return bundle
    }
}
