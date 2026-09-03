package ir.rasgir.manager

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import ir.rasgir.core.Jalali
import ir.rasgir.core.License

object Screens {

    private fun epochToday(): Long = System.currentTimeMillis() / 86_400_000L

    // ------------------------------------------------------------- lock
    fun lock(h: MH): View {
        val a = h.act
        return screenOf(a) { col ->
            col.addView(tv(a, "گاوصندوق قفل است", 20f, Pal.BLUE_DK, bold = true))
            col.addView(tv(a,
                "برای صدور یا مدیریت مجوزها، رمز بسته کلید صادرکننده را وارد کنید.", 13.5f, Pal.INK2))
            col.addView(vspace(a, 10f))
            val f = field(a, "رمز عبور", hint = "••••••••")
            f.edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            col.addView(f.view)
            val errTxt = tv(a, "", 13.5f, Pal.RED, bold = true)
            col.addView(errTxt)
            val openB = btn(a, "باز کردن")
            openB.setOnClickListener {
                val err = Vault.unlock(f.edit.text.toString())
                if (err != null) errTxt.text = err
                else { errTxt.text = ""; f.edit.text.clear(); h.show(Scr.HOME) }
            }
            col.addView(openB)
            val alt = btn(a, "وارد کردن بسته کلید (بازیابی از پشتیبان)", filled = false, small = true)
            alt.setOnClickListener { h.show(Scr.IMPORT) }
            col.addView(alt)
        }
    }

    // ------------------------------------------------------------- import
    fun importK(h: MH): View {
        val a = h.act
        return screenOf(a) { col ->
            col.addView(tv(a, "کلید خصوصی صادرکننده", 20f, Pal.BLUE_DK, bold = true))
            col.addView(tv(a,
                "فایل issuer-private.bundle (متن RGKEY1…) را که در تحویل کلیدها دریافت کرده‌اید همراه با رمز آن وارد کنید. " +
                    "این کلید برای همیشه روی این مدیر می‌ماند و هر بار با رمز باز می‌شود.", 13.5f, Pal.INK2))
            col.addView(vspace(a, 8f))
            val bundleF = field(a, "بسته کلید (RGKEY1…)", hint = "RGKEY1:…", multiline = true, mono = true)
            col.addView(bundleF.view)
            val passF = field(a, "رمز بسته کلید", hint = "••••••••")
            passF.edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            col.addView(passF.view)
            val errTxt = tv(a, "", 13.5f, Pal.RED, bold = true)
            col.addView(errTxt)
            val importB = btn(a, "وارد کردن و باز کردن")
            importB.setOnClickListener {
                val err = Vault.importAndUnlock(passF.edit.text.toString(), bundleF.edit.text.toString())
                if (err != null) errTxt.text = err else h.show(Scr.HOME)
            }
            col.addView(importB)
            val pasteB = btn(a, "چسباندن بسته کلید از کلیپ‌بورد", filled = false, small = true)
            pasteB.setOnClickListener {
                val txt = clipboardText(a)
                if (txt != null && txt.startsWith("RGKEY")) bundleF.edit.setText(txt.trim())
                else h.toast("کلیپ‌بورد حاوی بسته کلید نیست")
            }
            col.addView(pasteB)
        }
    }

    private fun clipboardText(a: Activity): String? {
        val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(a)?.toString()
    }

    // ------------------------------------------------------------- home
    fun home(h: MH): View {
        val a = h.act
        return screenOf(a) { col ->
            col.addView(tv(a, "مدیر مجوز رأس‌گیر چک", 24f, Pal.BLUE_DK, bold = true))
            col.addView(tv(a, "ابزار صدور مجوز برای «رأس‌گیر چک» — کاملاً آفلاین", 12.5f, Pal.INK2))
            col.addView(vspace(a, 8f))

            val st = card(a)
            st.addView(tv(a, "کلید صادرکننده", 13f, Pal.INK2, bold = true))
            st.addView(tv(a, "✓ موجود و باز است", 15f, Pal.GREEN, bold = true))
            st.addView(tv(a,
                "کلید عمومی داخل برنامه رأس‌گیر چک تعبیه شده و نباید تغییر کند.", 12.5f, Pal.INK2))
            col.addView(st)

            val q = card(a)
            q.addView(tv(a, "صدور مجوز برای یک دستگاه", 13f, Pal.INK2, bold = true))
            q.addView(tv(a,
                "کد درخواست (RGREQ) را از صفحه «فعال‌سازی» برنامه رأس‌گیر چک بگیرید — با اشتراک، پیام یا کپی/چسباندن.", 12.5f, Pal.INK2))
            val qB = btn(a, "صدور مجوز جدید")
            qB.setOnClickListener { h.renewRow = null; h.show(Scr.ISSUE) }
            q.addView(qB)
            col.addView(q)

            val l = card(a)
            l.addView(tv(a, "مجوزهای صادرشده", 13f, Pal.INK2, bold = true))
            val n = Ms.countLic()
            l.addView(tv(a, if (n == 0L) "هنوز مجوزی صادر نشده." else "تعداد: $n", 13.5f, Pal.INK))
            val lB = btn(a, "فهرست و تمدید", filled = false)
            lB.setOnClickListener { h.show(Scr.LIST) }
            l.addView(lB)
            col.addView(l)

            val s = card(a)
            val sB = btn(a, "تنظیمات و پشتیبان", filled = false)
            sB.setOnClickListener { h.show(Scr.SETTINGS) }
            s.addView(sB)
            col.addView(s)

            val lockB = btn(a, "قفل کردن گاوصندوق", filled = false, small = true, accent = Pal.RED)
            lockB.setOnClickListener { Vault.lock(); h.show(Scr.LOCK) }
            col.addView(lockB)
        }
    }

    // ------------------------------------------------------------- issue
    fun issue(h: MH): View {
        val a = h.act
        val pre = h.renewRow
        return screenOf(a) { col ->
            col.addView(tv(a, "صدور مجوز", 20f, Pal.BLUE_DK, bold = true))
            col.addView(tv(a,
                "اطلاعات دستگاه را وارد کنید؛ مجوز فقط روی همان دستگاه معتبر خواهد بود.", 12.5f, Pal.INK2))

            val prefill = pre?.request ?: h.pendingRequest
            val reqF = field(a, "کد درخواست دستگاه (RGREQ…)", value = prefill,
                hint = "RGREQ1 …", multiline = true, mono = true)
            col.addView(reqF.view)
            if (pre == null) {
                val pasteB = btn(a, "خواندن از کلیپ‌بورد", filled = false, small = true)
                pasteB.setOnClickListener {
                    clipboardText(a)?.let { txt -> reqF.edit.setText(txt.trim()) }
                }
                col.addView(pasteB)
            }

            val infoTxt = tv(a, "", 13.5f, Pal.INK)
            col.addView(infoTxt)

            val custF = field(a, "نام مشتری (اختیاری)", value = pre?.customer ?: "")
            col.addView(custF.view)

            col.addView(tv(a, "اعتبار", 13f, Pal.INK2, bold = true))
            val chips = LinearLayout(a)
            chips.orientation = LinearLayout.HORIZONTAL
            val permChip = chip(a, "دائمی")
            val untilChip = chip(a, "تا تاریخ…")
            chips.addView(permChip); chips.addView(hspace(a, 6f)); chips.addView(untilChip)
            col.addView(chips)
            val dayTxt = tv(a, "", 14f, Pal.INK, bold = true)
            col.addView(dayTxt)
            var permanent = pre == null || pre.expiryDay == 0L
            var untilDay = if (pre != null && pre.expiryDay > 0L) Jalali.fromEpochDay(pre.expiryDay)
            else managerTodayDay()

            fun repaint() {
                chipPaint(a, permChip, permanent)
                chipPaint(a, untilChip, !permanent)
                dayTxt.visibility = if (permanent) View.GONE else View.VISIBLE
                if (!permanent)
                    dayTxt.text = "پایان اعتبار: ${Jalali.format(untilDay)}  (برای تغییر بزنید)"
            }
            permChip.setOnClickListener { permanent = true; repaint() }
            untilChip.setOnClickListener {
                permanent = false
                pickJalaliDay(a, untilDay, "پایان اعتبار") { d -> untilDay = d; repaint() }
            }
            dayTxt.setOnClickListener {
                pickJalaliDay(a, untilDay, "پایان اعتبار") { d -> untilDay = d; repaint() }
            }
            repaint()

            fun refreshInfo() {
                val req = License.parseRequest(reqF.edit.text.toString().trim())
                infoTxt.text = when {
                    req == null -> "کد درخواست معتبر نیست (باید با RGREQ1 شروع شود)"
                    req.appId != License.APP_CHECK ->
                        "این کد برای «${req.appId}» است؛ این مدیر فقط «${License.APP_CHECK}» را پشتیبانی می‌کند."
                    else -> "برنامه: ${req.appId} — دستگاه: ${req.deviceHash.take(12)}…"
                }
            }
            reqF.edit.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = refreshInfo()
                override fun beforeTextChanged(s: CharSequence?, a0: Int, b0: Int, c0: Int) {}
                override fun onTextChanged(s: CharSequence?, a0: Int, b0: Int, c0: Int) {}
            })
            refreshInfo()

            val goB = btn(a, "صدور مجوز و نمایش")
            goB.setOnClickListener {
                val key = Vault.key
                if (key == null) { h.show(Scr.LOCK); return@setOnClickListener }
                val raw = reqF.edit.text.toString().trim()
                val req = License.parseRequest(raw)
                if (req == null) { h.toast("کد درخواست معتبر نیست"); return@setOnClickListener }
                if (req.appId != License.APP_CHECK) {
                    h.toast("این مدیر فقط مجوز «رأس‌گیر چک» را صادر می‌کند"); return@setOnClickListener
                }
                val expiry = if (permanent) 0L else Jalali.toEpochDay(untilDay)
                if (!permanent && expiry <= epochToday()) { h.toast("تاریخ پایان باید در آینده باشد"); return@setOnClickListener }
                val customer = custF.edit.text.toString().trim()
                val lic = License.issueLicense(key, req.appId, req, epochToday(), expiry, customer)
                val id = Ms.addLic(Ms.Lic(0L, customer, req.appId, req.deviceHash, raw,
                    epochToday(), expiry, lic, System.currentTimeMillis()))
                h.viewRowId = id
                h.renewRow = null
                h.pendingRequest = ""
                hideKeyboard(a)
                h.show(Scr.VIEW)
            }
            col.addView(goB)
            val cancelB = btn(a, "انصراف", filled = false, small = true)
            cancelB.setOnClickListener { h.renewRow = null; h.show(Scr.HOME) }
            col.addView(cancelB)
        }
    }

    private fun hideKeyboard(a: Activity) {
        (a.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(a.currentFocus?.windowToken, 0)
    }

    // ------------------------------------------------------------- list
    fun list(h: MH): View {
        val a = h.act
        return screenOf(a) { col ->
            col.addView(tv(a, "مجوزهای صادرشده", 20f, Pal.BLUE_DK, bold = true))
            val rows = Ms.listLic(200)
            if (rows.isEmpty()) {
                col.addView(tv(a, "هنوز مجوزی صادر نشده است.", 14f, Pal.INK2))
            }
            rows.forEach { r ->
                val cd = card(a)
                cd.addView(tv(a, r.customer.ifBlank { "(بدون نام)" }, 16f, Pal.INK, bold = true))
                val exp = if (r.expiryDay == 0L) "دائمی" else
                    Jalali.format(Jalali.fromEpochDay(r.expiryDay)) +
                        (if (r.expiryDay < epochToday()) " — منقضی شده" else "")
                cd.addView(tv(a, "دستگاه: ${r.devHash.take(12)}… — پایان اعتبار: $exp", 12.5f, Pal.INK2))
                val row2 = LinearLayout(a)
                row2.orientation = LinearLayout.HORIZONTAL
                val viewB = btn(a, "متن مجوز", block = false, small = true)
                viewB.setOnClickListener { h.viewRowId = r.id; h.show(Scr.VIEW) }
                val renewB = btn(a, "تمدید", block = false, small = true, filled = false)
                renewB.setOnClickListener { h.renewRow = r; h.show(Scr.ISSUE) }
                val delB = btn(a, "حذف", block = false, small = true, filled = true, accent = Pal.RED)
                delB.setOnClickListener {
                    a.ask("حذف مجوز",
                        "این مجوز از فهرست حذف شود؟ (مجوزهای صادرشده روی دستگاه‌ها معتبر می‌مانند.)",
                        "حذف") { Ms.delLic(r.id); h.show(Scr.LIST) }
                }
                row2.addView(viewB); row2.addView(hspace(a, 4f))
                row2.addView(renewB); row2.addView(hspace(a, 4f))
                row2.addView(delB)
                cd.addView(row2)
                col.addView(cd)
            }
        }
    }

    // ------------------------------------------------------------- view
    fun view(h: MH): View {
        val a = h.act
        val row = Ms.getLic(h.viewRowId)
        if (row == null) {
            return screenOf(a) { col ->
                col.addView(tv(a, "ردیف پیدا نشد", 16f, Pal.RED))
                val b = btn(a, "بازگشت")
                b.setOnClickListener { h.show(Scr.LIST) }
                col.addView(b)
            }
        }
        return screenOf(a) { col ->
            col.addView(tv(a, "مجوز صادر شد", 20f, Pal.GREEN, bold = true))
            col.addView(tv(a,
                "این متن را برای همان دستگاه بفرستید و در برنامه «فعال‌سازی» → «وارد کردن مجوز» بچسبانید.", 12.5f, Pal.INK2))
            val meta = card(a)
            meta.addView(rowPair(a, "مشتری", row.customer.ifBlank { "—" }))
            meta.addView(rowPair(a, "دستگاه", row.devHash))
            meta.addView(rowPair(a, "اعتبار",
                if (row.expiryDay == 0L) "دائمی" else Jalali.format(Jalali.fromEpochDay(row.expiryDay))))
            col.addView(meta)

            val licCard = card(a)
            val tLic = tv(a, row.licenseText, 12f, Pal.INK)
            tLic.setTextIsSelectable(true)
            tLic.typeface = Typeface.MONOSPACE
            licCard.addView(tLic)
            col.addView(licCard)

            val actRow = LinearLayout(a)
            actRow.orientation = LinearLayout.HORIZONTAL
            val copyB = btn(a, "کپی", block = false)
            copyB.setOnClickListener {
                val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("rasgir-license", row.licenseText))
                h.toast("کپی شد")
            }
            val shareB = btn(a, "اشتراک‌گذاری", block = false, filled = false)
            shareB.setOnClickListener {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, row.licenseText)
                }
                a.startActivity(Intent.createChooser(i, "مجوز رأس‌گیر چک"))
            }
            actRow.addView(copyB); actRow.addView(hspace(a, 6f)); actRow.addView(shareB)
            col.addView(actRow)
            val nextB = btn(a, "صدور مجوز برای دستگاه دیگر", filled = false, small = true)
            nextB.setOnClickListener { h.renewRow = null; h.show(Scr.ISSUE) }
            col.addView(nextB)
            val okB = btn(a, "انجام شد")
            okB.setOnClickListener { h.show(Scr.HOME) }
            col.addView(okB)
        }
    }

    // ------------------------------------------------------------- settings
    fun settings(h: MH): View {
        val a = h.act
        return screenOf(a) { col ->
            col.addView(tv(a, "تنظیمات و پشتیبان", 20f, Pal.BLUE_DK, bold = true))
            col.addView(tv(a, "مدیر روی همین دستگاه است؛ پشتیبان‌ها را در جایی امن نگه دارید.", 12.5f, Pal.INK2))

            val passC = card(a)
            passC.addView(tv(a, "تغییر رمز گاوصندوق", 13f, Pal.INK2, bold = true))
            passC.addView(tv(a, "کلید صادرکننده همان می‌ماند؛ فقط رمز باز شدن عوض می‌شود.", 12.5f, Pal.INK2))
            val pB = btn(a, "تغییر رمز", filled = false, small = true)
            pB.setOnClickListener { changePasswordDialog(h) }
            passC.addView(pB)
            col.addView(passC)

            val bkC = card(a)
            bkC.addView(tv(a, "پشتیبان کامل (بسته کلید + فهرست مجوزها)", 13f, Pal.INK2, bold = true))
            val exB = btn(a, "ارسال پشتیبان (فایل)", small = true, filled = false)
            exB.setOnClickListener {
                val bundle = Ms.get("bundle")
                if (bundle.isBlank()) h.toast("کلیدی وجود ندارد")
                else shareFileOfText(a, "RasgirManager_vault.rgbk", Ms.exportText(bundle))
            }
            bkC.addView(exB)
            val imB = btn(a, "بازیابی از فایل پشتیبان", small = true, filled = false)
            imB.setOnClickListener { pickBackup(h) }
            bkC.addView(imB)
            col.addView(bkC)

            val wipeC = card(a)
            wipeC.addView(tv(a, "حذف همه", 13f, Pal.RED, bold = true))
            val wB = btn(a, "پاک کردن کلید و فهرست (خطرناک)", small = true, filled = true, accent = Pal.RED)
            wB.setOnClickListener {
                a.ask("حذف همه",
                    "کلید صادرکننده و همه مجوزهای فهرست از این دستگاه پاک می‌شود. ادامه؟", "پاک کن") {
                    Ms.clearAll()
                    Vault.lock()
                    h.show(Scr.IMPORT)
                }
            }
            wipeC.addView(wB)
            col.addView(wipeC)
        }
    }

    private fun changePasswordDialog(h: MH) {
        val a = h.act
        val root = LinearLayout(a)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(a.dp(24), a.dp(8), a.dp(24), 0)
        val cur = field(a, "رمز فعلی", hint = "••••••••")
        val neu = field(a, "رمز جدید (حداقل ۸ کاراکتر)", hint = "••••••••")
        val con = field(a, "تکرار رمز جدید", hint = "••••••••")
        listOf(cur, neu, con).forEach {
            it.edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            root.addView(it.view)
        }
        AlertDialog.Builder(a).setTitle("تغییر رمز").setView(root)
            .setPositiveButton("ذخیره") { _, _ ->
                val c = cur.edit.text.toString()
                val n1 = neu.edit.text.toString()
                val n2 = con.edit.text.toString()
                when {
                    n1.length < 8 -> h.toast("رمز جدید کوتاه است")
                    n1 != n2 -> h.toast("تکرار رمز یکسان نیست")
                    else -> {
                        val err = Vault.changePass(c, n1)
                        if (err != null) h.toast(err) else h.toast("رمز تغییر کرد")
                    }
                }
            }
            .setNegativeButton("انصراف", null).show()
    }

    private fun pickBackup(h: MH) {
        val a = h.act
        h.resultHandler = { data ->
            val uri = data?.data
            if (uri != null) doRestore(h, uri)
        }
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        a.startActivityForResult(Intent.createChooser(i, "انتخاب پشتیبان"), 3001)
    }

    private fun doRestore(h: MH, uri: android.net.Uri) {
        val a = h.act
        val body = try {
            a.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (t: Throwable) { "" }
        if (!body.startsWith("RGVAULT1")) { h.toast("فایل پشتیبان معتبر نیست"); return }
        val choices = arrayOf("جایگزینی کامل", "ادغام فهرست", "انصراف")
        AlertDialog.Builder(a).setTitle("نحوه بازیابی")
            .setItems(choices) { _, w ->
                when (w) {
                    0 -> {
                        val bundle = Ms.bundleOf(body)
                        if (bundle.isNullOrBlank()) { h.toast("پشتیبان کلید ندارد"); return@setItems }
                        val root = LinearLayout(a)
                        root.orientation = LinearLayout.VERTICAL
                        root.setPadding(a.dp(24), a.dp(8), a.dp(24), 0)
                        val f = field(a, "رمز بسته کلید این پشتیبان", hint = "••••••••")
                        f.edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        root.addView(f.view)
                        AlertDialog.Builder(a).setTitle("رمز پشتیبان").setView(root)
                            .setPositiveButton("بازیابی") { _, _ ->
                                val err = Vault.importAndUnlock(f.edit.text.toString(), bundle)
                                if (err != null) h.toast(err)
                                else {
                                    Ms.importText(body, true)
                                    h.toast("بازیابی شد")
                                    h.show(Scr.HOME)
                                }
                            }
                            .setNegativeButton("انصراف", null).show()
                    }
                    1 -> {
                        val bundle = Ms.bundleOf(body)
                        if (bundle.isNullOrBlank()) h.toast("پشتیبان کلید ندارد")
                        else if (bundle != Ms.get("bundle"))
                            h.toast("کلید این پشتیبان با کلید فعلی فرق دارد؛ «جایگزینی کامل» را انتخاب کنید")
                        else { Ms.importText(body, false); h.toast("فهرست ادغام شد") }
                    }
                }
            }.show()
    }

    // ----- chips -----
    private fun chip(a: Activity, text: String): TextView {
        val t = tv(a, text, 13.5f, Pal.INK2, gravity = Gravity.CENTER)
        t.setPadding(a.dp(14), a.dp(7), a.dp(14), a.dp(7))
        t.isClickable = true
        t.isFocusable = true
        return t
    }

    private fun chipPaint(a: Activity, t: TextView, on: Boolean) {
        val g = android.graphics.drawable.GradientDrawable()
        g.cornerRadius = a.dp(18).toFloat()
        g.setColor(if (on) 0xFFDCEBFA.toInt() else Pal.CARD)
        g.setStroke(1, if (on) Pal.BLUE else Pal.LINE)
        t.background = g
        t.setTextColor(if (on) Pal.BLUE_DK else Pal.INK2)
        t.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
    }
}
