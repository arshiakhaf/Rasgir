package ir.rasgir.check

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import android.widget.LinearLayout
import ir.rasgir.core.model.WorkProject

/** single-activity wizard host; renders Flow pages under a top bar */
class MainActivity : Activity(), Host {

    override var project = WorkProject("cur")
    override val today: Long get() = Repo.todayDay()
    private var recId: Long = -1L
    private val stack = ArrayDeque<Page>()
    private var current = Page.HOME
    private val handler = Handler(Looper.getMainLooper())
    private var saveQueued = false
    private var lastAutoBackup = 0L
    private lateinit var content: FrameLayout
    private var bar: LinearLayout? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Repo.init(this)
        Repo.context = this
        Repo.purgeTrashOlderThan(30)
        lastAutoBackup = Repo.getSetting("lastAutoBackup").toLongOrNull() ?: 0L

        val id = intent?.getLongExtra("id", -1L) ?: -1L
        if (id > 0) {
            Repo.loadProject(id)?.let {
                project = it
                recId = id
                Repo.setCurrent(id)
            }
        } else if (Repo.currentId() > 0) {
            Repo.loadProject(Repo.currentId())?.let {
                project = it
                recId = Repo.currentId()
            }
        }

        root()
        current = resumePage(intent?.getStringExtra("page") ?: "")
        rebuild()
    }

    /** where to land when (re)opened from history / share */
    private fun resumePage(want: String): Page {
        val hasChecks = project.plan?.checks?.isNotEmpty() == true
        return when {
            want == "checks" && hasChecks -> Page.CHECKS
            want == "invoices" -> Page.INVOICES
            hasChecks -> Page.CHECKS
            project.hasDebt() -> Page.INVOICES
            project.invoices.isNotEmpty() || project.customerName.isNotBlank() -> Page.CUSTOMER
            else -> Page.HOME
        }
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) rebuild()
    }

    private fun root() {
        val rootV = LinearLayout(this)
        rootV.orientation = LinearLayout.VERTICAL
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Pal.GREEN_DK)
        }
        rootV.addView(bar)
        content = FrameLayout(this)
        content.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        rootV.addView(content)
        setContentView(rootV)
    }

    override fun ctx(): Context = this

    override fun toast(s: String) =
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()

    override fun go(p: Page) {
        if (current == p) { rebuild(); return }
        stack.addLast(current)
        current = p
        rebuild()
    }

    override fun back() {
        if (stack.isEmpty()) { finish(); return }
        current = stack.removeLast()
        rebuild()
    }

    override fun onBackPressed() = back()

    override fun rebuild() {
        if (!::content.isInitialized) return
        val titles = mapOf(
            Page.HOME to "رأس‌گیر چک",
            Page.CUSTOMER to "مشتری",
            Page.INVOICES to "فاکتورها",
            Page.RESULT to "رأس وزنی",
            Page.CHECKS to "چک‌ها",
            Page.PREVIEW to "تصویر تسویه"
        )
        val b = bar ?: return
        b.removeAllViews()
        if (current != Page.HOME) {
            val backTv = tv(this, "→", 24f, 0xFFFFFFFF.toInt(), bold = true, gravity = android.view.Gravity.CENTER)
            backTv.setPadding(dp(12), 0, dp(12), 0)
            backTv.isClickable = true
            backTv.setOnClickListener { back() }
            b.addView(backTv)
        }
        b.addView(tv(this, titles[current] ?: "", 17f, 0xFFFFFFFF.toInt(), bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        content.removeAllViews()
        if (!Lic.isActivated()) {
            content.addView(licGateView(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            return
        }
        content.addView(Flow.build(this, current), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    /** full licence gate: without a valid device-bound licence the operator
     *  screens are not reachable (spec §10) */
    private fun licGateView(): android.view.View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(26), dp(20), dp(20))
        col.setBackgroundColor(Pal.BG)
        col.addView(tv(this, "رأس‌گیر چک", 24f, Pal.GREEN_DK, bold = true))
        col.addView(vspace(this, 6f))
        col.addView(tv(this,
            "این برنامه فقط با مجوز معتبر همین دستگاه باز می‌شود. مجوز به کلید امن اندروید قفل است و روی دستگاه دیگر کار نمی‌کند.",
            14f, Pal.INK2))
        col.addView(vspace(this, 14f))
        val err = Lic.lastError()
        if (err != null) col.addView(tv(this, "⚠️ $err", 13.5f, Pal.RED, bold = true))
        val go = btn(this, "رفتن به فعال‌سازی")
        go.setOnClickListener { startActivity(Intent(this, ActivationActivity::class.java)) }
        col.addView(go)
        val note = tv(this,
            "کد درخواست دستگاه را برای صاحب برنامه بفرستید و مجوز دریافتی را وارد کنید.",
            12.5f, Pal.INK2)
        col.addView(note)
        return col
    }

    // ---------- activation gate ----------
    override fun requireActivation(): Boolean {
        if (Lic.isActivated()) return true
        startActivity(Intent(this, ActivationActivity::class.java))
        return false
    }

    // ---------- persistence ----------
    override fun save(significant: Boolean) {
        fun persist() {
            if (recId < 0) {
                recId = Repo.insertProject(project, project.customerName.ifBlank { "بدون نام" })
                Repo.setCurrent(recId)
            } else {
                Repo.updateProject(recId, project, project.customerName.ifBlank { "بدون نام" })
                Repo.setCurrent(recId)
            }
        }
        if (significant) {
            saveQueued = false
            handler.removeCallbacksAndMessages(null)
            persist()
            maybeAutoBackup()
        } else if (!saveQueued) {
            saveQueued = true
            handler.postDelayed({
                saveQueued = false
                if (recId >= 0) persist()
                else if (project.invoices.isNotEmpty() || project.customerName.isNotBlank()) persist()
            }, 1200)
        }
    }

    private fun maybeAutoBackup() {
        val now = System.currentTimeMillis()
        if (now - lastAutoBackup > 2 * 60 * 1000L) {
            lastAutoBackup = now
            Repo.setSetting("lastAutoBackup", now.toString())
            try { Repo.autoBackup() } catch (t: Throwable) { /* storage may be unavailable; manual backup exists */ }
        }
    }

    override fun openHistory() {
        startActivity(Intent(this, HistoryActivity::class.java))
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
}
