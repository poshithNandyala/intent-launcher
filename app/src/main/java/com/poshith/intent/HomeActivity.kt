package com.poshith.intent

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.poshith.intent.core.Mode
import com.poshith.intent.data.AppEntry
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage
import com.poshith.intent.guard.FocusGuardService
import com.poshith.intent.guard.Launch
import com.poshith.intent.ui.BaseActivity
import com.poshith.intent.ui.Chip
import com.poshith.intent.ui.Flow
import com.poshith.intent.ui.Fonts
import com.poshith.intent.ui.HoldButton
import com.poshith.intent.ui.Kind
import com.poshith.intent.ui.MindPanel
import com.poshith.intent.ui.P
import com.poshith.intent.ui.Sheet
import com.poshith.intent.ui.animationsOn
import com.poshith.intent.ui.button
import com.poshith.intent.ui.confirmLoosen
import com.poshith.intent.ui.dp
import com.poshith.intent.ui.field
import com.poshith.intent.ui.hbox
import com.poshith.intent.ui.hideKeyboard
import com.poshith.intent.ui.lp
import com.poshith.intent.ui.onText
import com.poshith.intent.ui.paragraph
import com.poshith.intent.ui.ripple
import com.poshith.intent.ui.rounded
import com.poshith.intent.ui.showKeyboard
import com.poshith.intent.ui.space
import com.poshith.intent.ui.text
import com.poshith.intent.ui.thud
import com.poshith.intent.ui.tick
import com.poshith.intent.ui.vbox
import com.poshith.intent.ui.vflex
import java.util.Date
import kotlin.math.abs

/**
 * The home screen. Time, today's one thing, your few chosen apps as words, and nothing that wants
 * your attention. Swipe up: find any app by typing. Swipe right: the Mind library.
 * Swipe down: notifications and quick settings (Wi-Fi, Bluetooth, hotspot). Long-press: edit.
 */
class HomeActivity : BaseActivity() {

    private lateinit var page: LinearLayout
    private lateinit var statsLine: TextView
    private lateinit var checkIn: LinearLayout
    private lateinit var oneThing: TextView
    private lateinit var status: TextView
    private lateinit var appsBox: LinearLayout
    private lateinit var bottom: LinearLayout
    private var midScroll: android.widget.ScrollView? = null
    private var library: Library? = null
    private var mind: MindPanel? = null

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var flings: GestureDetector
    private lateinit var taps: GestureDetector
    private var flingFired = false
    private var shownCheckInFor = 0L
    private var resumed = false
    private var layoutSig = ""
    private val barsListener: (com.poshith.intent.ui.Bars) -> Unit = { b ->
        if (::page.isInitialized) page.setPadding(dp(30) + b.left, b.top + dp(34), dp(30) + b.right, b.bottom + dp(6))
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k != null && k in REFRESH_KEYS) ui.post { if (resumed) refresh() }
    }
    private val appsListener: () -> Unit = { renderApps() }
    private val unlockListener: () -> Unit = { ui.postDelayed({ if (resumed) maybeCheckIn() }, 250) }
    private val ticker = object : Runnable {
        override fun run() { renderStatus(); ui.postDelayed(this, 20_000) }
    }

    companion object {
        private val REFRESH_KEYS = setOf("home", "labels", "rules", "one_thing", "focus_until", "focus_kind", "wd_on",
            "wd_start", "wd_end", "theme", "font", "align", "text_scale", "show_date", "show_stats", "show_one_thing",
            "morning_until", "hidden")
    }

    // ---------------------------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildHome()
        flings = GestureDetector(this, flingListener)
        taps = GestureDetector(this, tapListener)
        Apps.onChange(appsListener)
        Store.listen(prefsListener)
        Apps.refresh(this)
        if (!Store.onboarded) startActivity(Intent(this, OnboardingActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (recreating) return // the theme changed in Settings: BaseActivity is redrawing this screen
        resumed = true
        if (layoutSig != signature()) buildHome()
        Store.noteUnlock()
        refresh()
        IntentApp.onUnlock(unlockListener)
        ui.removeCallbacks(ticker); ui.postDelayed(ticker, 20_000)
        maybeCheckIn()
        ui.postDelayed({ if (resumed) maybeCheckIn() }, 500)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        IntentApp.removeUnlock(unlockListener)
        ui.removeCallbacks(ticker)
    }

    override fun onStop() {
        super.onStop()
        // Leave home clean for next time.
        hideCheckIn()
        library?.close(animate = false)
    }

    override fun onDestroy() {
        Apps.removeListener(appsListener)
        Store.unlisten(prefsListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button while already home: close whatever is open.
        if (intent.action == Intent.ACTION_MAIN) closeAllLayers()
    }

    /** Back on the home screen with nothing open does nothing. */
    override fun onBackRoot() {}

    // ---------------------------------------------------------------------------------- layout

    private fun scale(sp: Float) = sp * Store.textScale

    private fun alignGravity(): Int = when (Store.align) {
        "center" -> Gravity.CENTER_HORIZONTAL
        "end" -> Gravity.END
        else -> Gravity.START
    }

    private fun signature() = "${Store.font}|${Store.align}|${Store.textScale}|${Store.showDate}"

    private fun buildHome() {
        layoutSig = signature()
        closeAllLayers()
        library = null
        mind = null
        host.removeAllViews()
        page = vbox(alignGravity())
        host.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        removeBars(barsListener)
        onBars(barsListener)

        val g = alignGravity()
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = scale(64f)
            typeface = Fonts.light(this@HomeActivity)
            setTextColor(P.text)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            gravity = g
            contentDescription = "Clock. Opens alarms."
            background = ripple(null, dp(12).toFloat())
            setOnClickListener { it.tick(); Launch.alarms(this@HomeActivity) }
        }
        page.addView(clock, lp().apply { gravity = g })
        if (Store.showDate) {
            val date = TextClock(this).apply {
                format12Hour = "EEEE, d MMMM"
                format24Hour = "EEEE, d MMMM"
                textSize = scale(16f)
                typeface = Fonts.regular(this@HomeActivity)
                setTextColor(P.text2)
                gravity = g
                setPadding(0, dp(6), 0, 0)
                background = ripple(null, dp(8).toFloat())
                setOnClickListener { it.tick(); Launch.calendar(this@HomeActivity) }
            }
            page.addView(date, lp().apply { gravity = g })
        }
        statsLine = text("", scale(13.5f), P.text3).apply { gravity = g; setPadding(0, dp(10), 0, 0); visibility = View.GONE }
        page.addView(statsLine, lp())

        // Everything below the clock sits at the bottom, near your thumb. It only scrolls if it can't
        // fit (seven apps on a small phone, or very large system text), so nothing is ever cut off.
        val mid = vbox(Gravity.BOTTOM or g)
        mid.setOnTouchListener { _, e -> taps.onTouchEvent(e); true }
        midScroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(mid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        page.addView(midScroll, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        checkIn = vbox(g).apply { visibility = View.GONE }
        mid.addView(checkIn, lp().apply { bottomMargin = dp(18); topMargin = dp(12) })

        oneThing = text("", scale(16f), P.text2).apply {
            gravity = g
            setPadding(0, dp(8), 0, dp(8))
            minHeight = dp(44)
            background = ripple(null, dp(10).toFloat())
            setOnClickListener { it.tick(); oneThingSheet() }
        }
        mid.addView(oneThing, lp())

        status = text("", scale(15f), P.accent, Fonts.medium(this)).apply {
            gravity = g
            setPadding(0, dp(4), 0, dp(10))
            minHeight = dp(40)
            visibility = View.GONE
            background = ripple(null, dp(10).toFloat())
            setOnClickListener { it.tick(); statusSheet() }
        }
        mid.addView(status, lp())

        appsBox = vbox(g)
        mid.addView(appsBox, lp())

        bottom = hbox(Gravity.CENTER_VERTICAL)
        bottom.addView(bottomItem("Phone") { Launch.dialer(this) }, lp(0, dp(52), 1f))
        bottom.addView(bottomItem("Focus") { focusSheet() }, lp(0, dp(52), 1f))
        bottom.addView(bottomItem("Camera") { Launch.camera(this) }, lp(0, dp(52), 1f))
        (bottom.getChildAt(0) as TextView).gravity = Gravity.CENTER_VERTICAL or Gravity.START
        (bottom.getChildAt(2) as TextView).gravity = Gravity.CENTER_VERTICAL or Gravity.END
        page.addView(bottom, lp().apply { topMargin = dp(14) })

        // Taps on empty space: double-tap to lock, long-press to edit.
        page.setOnTouchListener { _, e -> taps.onTouchEvent(e); true }
    }

    private fun bottomItem(label: String, onClick: () -> Unit): TextView = text(label, 15f, P.text2).apply {
        gravity = Gravity.CENTER
        background = ripple(null, dp(20).toFloat())
        isClickable = true
        setOnClickListener { it.tick(); onClick() }
    }

    private fun refresh() {
        renderApps()
        renderOneThing()
        renderStatus()
        renderStats()
    }

    // ---------------------------------------------------------------------------------- content

    private fun renderApps() {
        if (!::appsBox.isInitialized) return
        appsBox.removeAllViews()
        val keys = Store.homeKeys
        val g = alignGravity()
        if (keys.isEmpty()) {
            appsBox.addView(text("Long-press anywhere to choose your apps", scale(15f), P.text3).apply { gravity = g; setPadding(0, dp(8), 0, dp(8)) }, lp())
            return
        }
        val stale = ArrayList<String>()
        val moved = LinkedHashMap<String, String>()
        for (k in keys) {
            var key = k
            var entry = Apps.byKey(k)
            if (entry == null && Apps.loaded) {
                val pkg = k.substringBefore('/')
                val serial = k.substringAfterLast('/').toLongOrNull()
                // Same app and profile, but an update renamed its main screen: follow it.
                val same = Apps.list.firstOrNull { it.pkg == pkg && it.serial == serial }
                when {
                    same != null -> { moved[k] = same.key; entry = same; key = same.key }
                    serial != null && serial in Apps.pausedSerials -> continue // work profile paused: keep it for later
                    else -> { stale += k; continue } // uninstalled
                }
            }
            val name = entry?.label ?: Store.customLabel(key) ?: Store.cachedName(key) ?: continue
            val t = text(name, scale(26f), P.text, Fonts.regular(this), lines = 1).apply {
                gravity = g or Gravity.CENTER_VERTICAL
                minHeight = dp(52)
                setPadding(0, dp(6), 0, dp(6))
                background = ripple(null, dp(12).toFloat())
                isClickable = true
                setOnClickListener { v ->
                    v.tick()
                    hideCheckIn()
                    val app = Apps.byKey(key)
                    // Before the app list has loaded, open by package name, still through the rules.
                    if (app != null) Launch.open(this@HomeActivity, app, v)
                    else Launch.openPackage(this@HomeActivity, key.substringBefore('/'))
                }
                setOnLongClickListener { v -> v.thud(); Apps.byKey(key)?.let { appMenu(it) }; true }
            }
            appsBox.addView(t, lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = g })
        }
        if (stale.isNotEmpty() || moved.isNotEmpty()) {
            for ((old, new) in moved) Store.customLabel(old)?.let { Store.setCustomLabel(new, it); Store.setCustomLabel(old, null) }
            Store.homeKeys = keys.filter { it !in stale }.map { moved[it] ?: it }
        }
    }

    private fun renderOneThing() {
        if (!::oneThing.isInitialized) return
        if (!Store.showOneThing) { oneThing.visibility = View.GONE; return }
        oneThing.visibility = View.VISIBLE
        val t = Store.oneThing
        if (t.isBlank()) {
            oneThing.text = "+ Today's one thing"
            oneThing.setTextColor(P.text3)
        } else {
            oneThing.text = "Today · $t"
            oneThing.setTextColor(P.text2)
        }
    }

    private fun renderStatus() {
        if (!::status.isInitialized) return
        val now = System.currentTimeMillis()
        val tf = DateFormat.getTimeFormat(this)
        val s: String? = when {
            Store.basicPhone -> "Basic phone · essentials only"
            Store.focusActive(now) -> {
                val left = ((Store.focusUntil - now + 59_999) / 60_000L).toInt()
                (if (Store.focusIsBreak) "Break" else "Focus") + " · $left min left"
            }
            Store.morningUntil > now -> "Morning quiet until ${tf.format(Date(Store.morningUntil))}"
            Store.windDown?.contains(Store.minuteOfDay(now)) == true ->
                "Wind-down until ${tf.format(Date(Store.windDown!!.endsAfter(now)))}"
            else -> null
        }
        status.visibility = if (s == null) View.GONE else View.VISIBLE
        status.text = s ?: ""
    }

    private fun renderStats() {
        if (!::statsLine.isInitialized) return
        val flash = Store.flash
        if (flash.isNotBlank() && System.currentTimeMillis() - Store.flashAt < 8_000) {
            statsLine.visibility = View.VISIBLE
            statsLine.setTextColor(P.accent)
            statsLine.text = flash
            Store.flash = ""
            ui.postDelayed({ renderStats() }, 4_000)
            return
        }
        statsLine.setTextColor(P.text3)
        if (!Store.showStats) { statsLine.visibility = View.GONE; return }
        com.poshith.intent.ui.Bg.run {
            val usage = Usage.today(this)
            val mindful = Store.packagesWithMode(Mode.MINDFUL)
            val notNow = Stats.notNowToday()
            val parts = ArrayList<String>()
            if (usage != null) {
                if (usage.unlocksKnown) parts += "${usage.unlocks} unlock${if (usage.unlocks == 1) "" else "s"}"
                val ms = usage.msFor(mindful)
                if (mindful.isNotEmpty()) parts += "${Usage.formatMinutes(ms)} in mindful apps"
            }
            if (notNow > 0) parts += "not now ×$notNow"
            val line = parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                statsLine.text = line
                statsLine.visibility = if (line.isBlank()) View.GONE else View.VISIBLE
            }
        }
    }

    // ---------------------------------------------------------------------------------- check-in

    /** Right after you unlock: "What did you pick up your phone for?" Never blocks anything. */
    private fun maybeCheckIn() {
        if (!Store.checkIn || !Store.onboarded || hasBackLayers()) return
        val at = Store.lastUnlockElapsed
        if (at == 0L || at == shownCheckInFor) return
        if (SystemClock.elapsedRealtime() - at > 6_000) return
        shownCheckInFor = at
        checkIn.removeAllViews()
        val g = alignGravity()
        checkIn.addView(text("What did you pick up your phone for?", scale(17f), P.text, Fonts.medium(this)).apply { gravity = g }, lp())
        val flow = Flow(this).apply { setPadding(0, dp(10), 0, 0) }
        fun chip(label: String, action: () -> Unit) = Chip(this, label).apply { setOnClickListener { it.tick(); action() } }
        flow.addView(chip("A call or message") { hideCheckIn() })
        flow.addView(chip("Something specific") { hideCheckIn(); library().open() })
        flow.addView(chip("Just checking") { justChecking() })
        checkIn.addView(flow, lp())
        showCheckIn()
        ui.postDelayed({ if (shownCheckInFor == at) hideCheckIn() }, 12_000)
    }

    private fun justChecking() {
        Stats.justChecking()
        checkIn.removeAllViews()
        val g = alignGravity()
        checkIn.addView(text("Nothing needs you right now.", scale(17f), P.text, Fonts.medium(this)).apply { gravity = g }, lp())
        checkIn.addView(text("Noticing the habit is how it changes.", scale(15f), P.text2).apply { gravity = g; setPadding(0, dp(4), 0, dp(10)) }, lp())
        val row = hbox()
        val lock = button(if (FocusGuardService.running) "Lock the phone" else "Put it down", Kind.QUIET) {
            hideCheckIn()
            if (!FocusGuardService.lockScreen()) Store.flash = "Good call."
            renderStats()
        }
        row.addView(lock, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        checkIn.addView(row, lp().apply { gravity = g })
    }

    private fun showCheckIn() {
        checkIn.visibility = View.VISIBLE
        if (animationsOn()) { checkIn.alpha = 0f; checkIn.translationY = dp(8).toFloat(); checkIn.animate().alpha(1f).translationY(0f).setDuration(260).start() }
    }

    private fun hideCheckIn() {
        if (!::checkIn.isInitialized || checkIn.visibility != View.VISIBLE) return
        checkIn.visibility = View.GONE
        shownCheckInFor = Store.lastUnlockElapsed
    }

    // ---------------------------------------------------------------------------------- gestures

    private val flingListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val start = e1 ?: return false
            val dx = e2.x - start.x
            val dy = e2.y - start.y
            val horizontal = abs(dx) > abs(dy) * 1.3f
            return if (horizontal && abs(dx) > dp(70) && abs(velocityX) > 500) {
                onHorizontal(dx > 0)
            } else if (!horizontal && abs(dy) > dp(60) && abs(velocityY) > 500) {
                onVertical(dy < 0)
            } else false
        }
    }

    private val tapListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!Store.doubleTapLock || hasBackLayers()) return false
            if (!FocusGuardService.lockScreen()) {
                Launch.toast(this@HomeActivity, "Turn on Focus Guard in Intent settings to lock with a double-tap")
            }
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (hasBackLayers()) return
            page.thud()
            homeMenu()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val panelOnly = layerIsPanel()
        if (!hasBackLayers() || panelOnly) {
            flingFired = false
            flings.onTouchEvent(ev)
            if (flingFired && ev.actionMasked == MotionEvent.ACTION_UP) {
                // A swipe that started on an app name must not also open that app.
                val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                flingFired = false
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** True when the only open layer is the library or the Mind page (not a sheet on top). */
    private fun layerIsPanel(): Boolean {
        val lib = library?.isOpen == true
        val m = mind?.isOpen == true
        return (lib || m) && layerCount() == 1
    }

    private fun onHorizontal(right: Boolean): Boolean {
        if (mind?.isOpen == true) {
            if (!right) { mind?.close(); flingFired = true }
            return true
        }
        if (library?.isOpen == true) return false
        if (hasBackLayers()) return false
        flingFired = true
        if (right) {
            if (Store.swipeRightMind) mindPanel().open()
        } else {
            val key = Store.swipeLeftKey
            val app = if (key.isNotBlank()) Apps.byKey(key) else null
            if (app != null) Launch.open(this, app, null) else library().open()
        }
        return true
    }

    private fun onVertical(up: Boolean): Boolean {
        if (library?.isOpen == true) {
            if (!up && library?.atTop() == true) { library?.close(); flingFired = true }
            return true
        }
        if (mind?.isOpen == true || hasBackLayers()) return false
        // On a crowded screen the apps scroll first; the gesture works once they can't.
        if (midScroll?.canScrollVertically(if (up) 1 else -1) == true) return false
        flingFired = true
        hideCheckIn()
        if (up) library().open() else expandNotifications()
        return true
    }

    private fun expandNotifications() {
        if (FocusGuardService.openNotifications()) return
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------------------------- panels

    private fun library(): Library = library ?: Library().also { library = it }
    private fun mindPanel(): MindPanel = mind ?: MindPanel(this).also { mind = it }

    /** Search-first app library: type to find, nothing to browse. */
    inner class Library {
        private val root = FrameLayout(this@HomeActivity)
        private val col = vbox()
        private val results = vbox()
        private val hint = text("Type to find an app.\nTyping, not scrolling, keeps it on purpose.", 15f, P.text3).apply {
            setLineSpacing(0f, 1.25f); setPadding(dp(30), 0, dp(30), dp(18))
        }
        private val input: EditText = field("Type an app name", caps = false)
        var isOpen = false
            private set
        private val closer: () -> Unit = { close() }
        private val barL: (com.poshith.intent.ui.Bars) -> Unit = { b ->
            col.setPadding(b.left, b.top + dp(16), b.right, b.bottomOrIme + dp(14))
        }

        init {
            root.setBackgroundColor(P.bg)
            root.isClickable = true
            col.addView(vflex())
            col.addView(hint, lp())
            col.addView(results, lp())
            input.textSize = 20f
            input.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            input.setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) {
                    (results.getChildAt(results.childCount - 1)?.tag as? (() -> Unit))?.invoke(); true
                } else false
            }
            input.onText { render(it) }
            val fieldWrap = FrameLayout(this@HomeActivity).apply { setPadding(dp(22), dp(6), dp(22), 0); addView(input) }
            col.addView(fieldWrap, lp())
            root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        fun atTop() = true

        fun open() {
            if (isOpen) return
            isOpen = true
            input.setText("")
            render("")
            root.animate().cancel()
            (root.parent as? ViewGroup)?.removeView(root)
            host.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            pushBack(closer)
            onBars(barL)
            if (animationsOn()) {
                root.alpha = 0f; root.translationY = dp(40).toFloat()
                root.animate().alpha(1f).translationY(0f).setDuration(200).start()
            }
            showKeyboard(input)
        }

        fun close(animate: Boolean = true) {
            if (!isOpen) return
            isOpen = false
            dropBack(closer)
            removeBars(barL)
            hideKeyboard(this@HomeActivity, input)
            if (animate && animationsOn()) {
                root.animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(160)
                    .withEndAction { (root.parent as? ViewGroup)?.removeView(root) }.start()
            } else (root.parent as? ViewGroup)?.removeView(root)
        }

        private fun render(q: String) {
            results.removeAllViews()
            if (q.isBlank()) { hint.visibility = View.VISIBLE; return }
            hint.visibility = View.GONE
            val found = Apps.search(q, Store.hidden, limit = 8)
            val own = Apps.score("Intent settings", q) >= 0
            if (found.isEmpty() && !own) {
                results.addView(text("No app called “$q”.", 16f, P.text3).apply { setPadding(dp(30), dp(10), dp(30), dp(10)) }, lp())
                return
            }
            // Best match sits closest to your thumb, right above the keyboard.
            val rows = ArrayList<View>()
            for (a in found) rows += resultRow(a)
            if (own) rows += plainRow("Intent settings", "settings") { close(false); startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
            for (r in rows.asReversed()) results.addView(r, lp())
            if (Store.autoOpenSingle && found.size == 1 && !own && q.trim().length >= 2) (rows[0].tag as? (() -> Unit))?.invoke()
        }

        private fun resultRow(a: AppEntry): View {
            val mode = Store.rule(a.pkg).mode
            val tag = when {
                Launch.isProtected(this@HomeActivity, a.pkg) -> if (a.isWork) "work" else null
                mode == Mode.MINDFUL -> "mindful"
                mode == Mode.BATCHED -> "check-in"
                mode == Mode.BLOCKED -> "blocked"
                a.isWork -> "work"
                else -> null
            }
            val action: () -> Unit = { close(false); Launch.open(this@HomeActivity, a, null) }
            val row = plainRow(a.label, tag, action)
            row.setOnLongClickListener { v -> v.thud(); appMenu(a); true }
            return row
        }

        private fun plainRow(label: String, tag: String?, action: () -> Unit): View {
            val row = hbox().apply {
                minimumHeight = dp(56)
                setPadding(dp(30), dp(8), dp(30), dp(8))
                background = ripple(null)
                isClickable = true
                this.tag = action
                setOnClickListener { it.tick(); action() }
            }
            row.addView(text(label, 21f, P.text, lines = 1), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (tag != null) row.addView(text(tag, 13f, if (tag == "mindful" || tag == "blocked" || tag == "check-in") P.accent else P.text3, Fonts.medium(this@HomeActivity)).apply {
                setPadding(dp(12), 0, 0, 0)
            })
            return row
        }
    }

    // ---------------------------------------------------------------------------------- sheets


    fun appMenu(app: AppEntry) {
        val rule = Store.rule(app.pkg)
        val prot = Launch.isProtected(this, app.pkg)
        val sub = if (prot) "Always available: needed for calls, payments or system tasks."
        else when (rule.mode) {
            Mode.ESSENTIAL -> "Essential: opens instantly, even during focus."
            Mode.NORMAL -> "Opens instantly. Paused during focus."
            Mode.MINDFUL -> "Mindful: ${rule.pauseSec} s pause, ${rule.sessionMin} min sessions."
            Mode.BATCHED -> "Opens only at your check-in times (${checkInText()})."
            Mode.BLOCKED -> "Blocked."
        }
        val s = Sheet(this, app.label, sub)
        val home = Store.homeKeys
        if (app.key in home) s.item("Remove from home") { Store.homeKeys = home - app.key }
        else if (home.size < Store.MAX_HOME) s.item("Add to home", "${home.size} of ${Store.MAX_HOME} · fewer is calmer") { Store.homeKeys = home + app.key }
        s.item("Rename") { renameSheet(app) }
        if (!prot) {
            val options = listOf(
                Mode.ESSENTIAL to "Essential · never paused",
                Mode.NORMAL to "Normal",
                Mode.MINDFUL to "Mindful · pause before opening",
                Mode.BATCHED to "Check-in times · ${checkInText()}",
                Mode.BLOCKED to "Blocked",
            )
            for ((m, label) in options) {
                val current = m == rule.mode
                s.item(if (current) "✓  $label" else "    $label", color = if (current) P.accent else P.text) {
                    if (current) return@item
                    if (m.isLooserThan(rule.mode)) {
                        confirmLoosen("Make ${app.label} easier to open?", "From ${Launch.modeLabel(rule.mode)} to ${Launch.modeLabel(m)}.") {
                            Store.setMode(app.pkg, m)
                        }
                    } else Store.setMode(app.pkg, m)
                }
            }
        }
        val hidden = Store.hidden
        if (app.key in hidden) s.item("Show in search") { Store.hidden = hidden - app.key }
        else s.item("Hide from search", "Out of sight, out of reach") { Store.hidden = hidden + app.key }
        s.item("App info") { Launch.appInfo(this, app) }
        s.item("Uninstall", color = P.warn) { Launch.uninstall(this, app) }
        s.show()
    }

    private fun checkInText(): String {
        val tf = DateFormat.getTimeFormat(this)
        val c = java.util.Calendar.getInstance()
        return Store.checkInStarts.joinToString(" and ") { m ->
            c.set(java.util.Calendar.HOUR_OF_DAY, m / 60); c.set(java.util.Calendar.MINUTE, m % 60)
            tf.format(c.time)
        }.ifBlank { "none set" }
    }

    private fun renameSheet(app: AppEntry) {
        val s = Sheet(this, "Rename", "Give it a name that says what it's for, like “Pay” or “Notes”.")
        val f = field(app.sysLabel, app.label)
        s.add(f)
        s.add(button("Save", Kind.PRIMARY) { Store.setCustomLabel(app.key, f.text.toString().ifBlank { null }); s.dismiss() })
        if (Store.customLabel(app.key) != null) s.add(button("Use the original name", Kind.PLAIN) { Store.setCustomLabel(app.key, null); s.dismiss() })
        s.show()
        showKeyboard(f)
    }

    private fun homeMenu() {
        val s = Sheet(this, null)
        s.item("Edit home apps", "${Store.homeKeys.size} of ${Store.MAX_HOME}") { openSettings("home") }
        s.item("Today's one thing") { oneThingSheet() }
        s.item("Focus or a real break") { focusSheet() }
        s.item("Mind library", "Short, sourced lessons") { mindPanel().open() }
        s.item("Settings") { openSettings(null) }
        if (!Launch.isDefaultHome(this)) s.item("Make Intent your home screen", color = P.accent) { Launch.requestHome(this) }
        s.show()
    }

    private fun openSettings(page: String?) {
        startActivity(Intent(this, SettingsActivity::class.java).apply { if (page != null) putExtra(SettingsActivity.EXTRA_PAGE, page) })
    }

    private fun oneThingSheet() {
        val s = Sheet(this, "Today's one thing", "The single thing that would make today a good day. Attention follows goals, and a goal in view is harder to forget.")
        val f = field("e.g. Finish the API endpoint", Store.oneThing)
        s.add(f)
        s.add(button("Save", Kind.PRIMARY) { Store.oneThing = f.text.toString(); Store.oneThingDay = Store.dayKey(); s.dismiss() })
        if (Store.oneThing.isNotBlank()) s.add(button("Done with it", Kind.PLAIN) { Store.oneThing = ""; s.dismiss() })
        s.show()
        showKeyboard(f)
    }

    private fun statusSheet() {
        val now = System.currentTimeMillis()
        when {
            Store.focusActive(now) -> focusSheet()
            Store.morningUntil > now -> {
                val s = Sheet(this, "Morning quiet", "Mindful apps rest for the first ${Store.morningQuietMin} minutes after your first unlock of the day, so your first thoughts are your own. Change it in Settings.")
                s.add(HoldButton(this, "Hold to end it for today", 2500) { Store.morningUntil = 0; s.dismiss(); renderStatus() })
                s.show()
            }
            else -> {
                val s = Sheet(this, "Wind-down", "Mindful apps rest until morning. Screens in the last hour push sleep later. Change the hours in Settings.")
                s.item("Wind-down settings") { openSettings("focus") }
                s.show()
            }
        }
    }

    fun focusSheet() {
        val now = System.currentTimeMillis()
        if (Store.basicPhone) {
            val s = Sheet(this, "Basic phone is on", "Only your essentials open, like a phone from before feeds. Calls, messages, payments and maps work as always.")
            s.add(HoldButton(this, "Hold to turn basic phone off", 3000) { Store.focusUntil = 0; s.dismiss(); renderStatus() })
            s.show()
            return
        }
        if (Store.focusActive(now)) {
            val until = DateFormat.getTimeFormat(this).format(Date(Store.focusUntil))
            val brk = Store.focusIsBreak
            val s = Sheet(this, if (brk) "On a break" else "Focusing", if (brk) "Until $until. Away from screens is the whole point." else "Until $until. Only your essentials open. Calls always ring.")
            s.add(HoldButton(this, if (brk) "Hold to end the break" else "Hold to end focus early", 3000) {
                Store.focusUntil = 0; s.dismiss(); renderStatus()
            })
            s.show()
            return
        }
        val s = Sheet(this, "Focus or a real break", "Either way, only your essentials open until it ends. Calls always ring through.")
        var focusMin = Store.focusLength
        var breakMin = 15
        s.add(text("Deep work", 16f, P.text, Fonts.medium(this)), 24)
        val ff = Flow(this)
        val fChips = ArrayList<Chip>()
        for (m in intArrayOf(25, 50, 90)) {
            val c = Chip(this, "$m min"); c.on = m == focusMin
            c.setOnClickListener { focusMin = m; fChips.forEach { x -> x.on = x === c } }
            fChips += c; ff.addView(c)
        }
        s.add(ff)
        s.add(button("Start focus", Kind.PRIMARY) { startFocus("focus", focusMin); s.dismiss() })
        s.add(paragraph("Best results: carry the phone to another room. Out of reach, it gets picked up far less.", 14f, P.text3), 0)
        s.add(space(8))
        s.add(text("A real break", 16f, P.text, Fonts.medium(this)), 24)
        s.add(paragraph("Dr. Sahar Yousef's micro break: 10 to 20 minutes fully away from screens and work. Scrolling doesn't count.", 14f, P.text2), 0)
        val bf = Flow(this)
        val bChips = ArrayList<Chip>()
        for (m in intArrayOf(10, 15, 20)) {
            val c = Chip(this, "$m min"); c.on = m == breakMin
            c.setOnClickListener { breakMin = m; bChips.forEach { x -> x.on = x === c } }
            bChips += c; bf.addView(c)
        }
        s.add(bf)
        s.add(button("Start a break", Kind.QUIET) { startFocus("break", breakMin); s.dismiss() })
        s.add(space(8))
        s.add(text("Basic phone", 16f, P.text, Fonts.medium(this)), 24)
        s.add(paragraph("A smartphone that behaves like a simple phone: essentials only, until you turn it off. Some of the most focused people carry exactly that.", 14f, P.text2), 0)
        s.add(button("Turn on basic phone", Kind.OUTLINE) { startFocus("basic", 0); s.dismiss() })
        val essentials = Store.packagesWithMode(Mode.ESSENTIAL).mapNotNull { Apps.byPkg(it)?.label }.sorted()
        s.add(paragraph("Always open: Phone, Messages, Clock" + (if (essentials.isEmpty()) "" else ", " + essentials.joinToString(", ")) + ".", 13.5f, P.text3), 0)
        s.item("Choose essentials", keepOpen = false) { openSettings("essentials") }
        s.show()
    }

    private fun startFocus(kind: String, minutes: Int) {
        val now = System.currentTimeMillis()
        Store.focusKind = kind
        if (kind == "focus") Store.focusLength = minutes
        Store.focusStarted = now
        Store.focusUntil = if (kind == "basic") Store.UNTIL_OFF else now + minutes * 60_000L
        Store.flash = when (kind) {
            "break" -> "Break started. Step away."
            "basic" -> "Basic phone on. Essentials only."
            else -> "Focus started. Park the phone."
        }
        renderStatus(); renderStats()
    }
}
