package com.poshith.intent

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
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
import android.view.animation.DecelerateInterpolator
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.poshith.intent.core.AfterAsk
import com.poshith.intent.core.Catalog
import com.poshith.intent.core.Decision
import com.poshith.intent.core.Mode
import com.poshith.intent.core.Pulls
import com.poshith.intent.data.AppEntry
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Noticed
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage
import com.poshith.intent.guard.Break
import com.poshith.intent.guard.BreakQuietService
import com.poshith.intent.guard.Dnd
import com.poshith.intent.guard.FocusGuardService
import com.poshith.intent.guard.Launch
import com.poshith.intent.ui.BaseActivity
import com.poshith.intent.ui.BreakScreen
import com.poshith.intent.ui.DARK
import com.poshith.intent.ui.LIGHT
import com.poshith.intent.ui.Palette
import com.poshith.intent.ui.Theme
import com.poshith.intent.ui.Wallpaper
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
import com.poshith.intent.ui.row
import com.poshith.intent.ui.rounded
import com.poshith.intent.ui.showKeyboard
import com.poshith.intent.ui.space
import com.poshith.intent.ui.text
import com.poshith.intent.ui.thud
import com.poshith.intent.ui.tick
import com.poshith.intent.ui.toggleRow
import com.poshith.intent.ui.vbox
import com.poshith.intent.ui.vflex
import com.poshith.intent.ui.feedsClosedUntil
import com.poshith.intent.ui.emergencyLink
import com.poshith.intent.ui.planSheet
import com.poshith.intent.ui.rolloverSheet
import com.poshith.intent.ui.intentionSheet
import com.poshith.intent.ui.reflectSheet
import java.util.Date
import kotlin.math.abs

/**
 * The home screen. Time, today's one thing, your few chosen apps as words, and nothing that wants
 * your attention. Swipe up: find any app by typing. Swipe right: Practice (a one-minute reset, lessons).
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
    private var ink: Palette? = null
    /** The real-break screen, shown instead of the home screen while a break runs. */
    private var breakScreen: BreakScreen? = null
    private var library: Library? = null
    private var mind: MindPanel? = null

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var flings: GestureDetector
    private lateinit var taps: GestureDetector
    private var flingFired = false
    private var shownCheckInFor = 0L
    private var resumed = false
    private var away = true // home was fully hidden (another app, screen off): settle in on return
    private var reopenQuiet = false // came back from a switch the real break needed: show its sheet again
    private var quoteBox: LinearLayout? = null
    private var quoteText: TextView? = null
    private var quoteBy: TextView? = null
    private var quoteNow = com.poshith.intent.core.Quotes.all.first()
    /** At your midnight, tomorrow's line (if the home screen is in front then). */
    private val quoteTicker = object : Runnable {
        override fun run() {
            showQuote(quoteForNow(), animate = true)
            ui.postDelayed(this, untilNextQuote())
        }
    }

    /** One line a day, or the one you pinned for the week. Local time: it turns over at your midnight. */
    private fun quoteForNow(): com.poshith.intent.core.Quote = Store.todaysQuote()
    private fun untilNextQuote(): Long = Store.endOfDay() - System.currentTimeMillis() + 1_000
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
        private const val AUTO_LOCK_MS = 15_000L
        private val REFRESH_KEYS = setOf("home", "labels", "rules", "one_thing", "one_thing_day", "plan_skipped", "plan_prompt",
            "evening_on", "reflections", "day_start", "focus_until", "focus_kind", "wd_on",
            "wd_start", "wd_end", "theme", "font", "align", "text_scale", "show_date", "show_stats", "show_one_thing",
            "morning_until", "hidden", "show_quote", "quote_pin", "quote_pin_until")
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
    }

    override fun onResume() {
        super.onResume()
        if (recreating) return // the theme changed in Settings: BaseActivity is redrawing this screen
        // Setup not finished: back to it, on the page you were on. It lives in its own task, so the home
        // screen coming forward (choosing Intent as your home app does that) doesn't wipe it.
        if (!Store.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        resumed = true
        Break.checkEnded(this)
        if (layoutSig != signature()) buildHome()
        breakScreen?.let { it.start(); return }
        Store.noteUnlock()
        refresh()
        IntentApp.onUnlock(unlockListener)
        ui.removeCallbacks(ticker); ui.postDelayed(ticker, 20_000)
        if (quoteText != null) {
            // Back home: today's line (it changes at midnight, not while you look at it).
            showQuote(quoteForNow(), animate = false)
            ui.removeCallbacks(quoteTicker); ui.postDelayed(quoteTicker, untilNextQuote())
        }
        maybeCheckIn()
        ui.postDelayed({ if (resumed) maybeCheckIn() }, 500)
        // Back from a session: how did it feel? (after the unlock question has had its chance)
        ui.postDelayed({ if (resumed) maybeAfterCheck() }, 900)
        if (away) { away = false; settle() }
        if (reopenQuiet && breakScreen == null) {
            reopenQuiet = false
            ui.postDelayed({ if (resumed && !hasBackLayers() && breakScreen == null) focusSheet() }, 350)
        }
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        cancelAutoLock()
        breakScreen?.stop()
        IntentApp.removeUnlock(unlockListener)
        ui.removeCallbacks(ticker)
        ui.removeCallbacks(quoteTicker)
    }

    override fun onStop() {
        super.onStop()
        // Leave home clean for next time.
        away = true
        afterToken = null
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

    // ---------------------------------------------------------------------------------- wallpaper

    /** Colors for the home screen itself. On your wallpaper: dark text on a light wallpaper, light text
     *  on a dark one. Sheets and panels keep the app's own theme. */
    private fun ink(): Palette = ink ?: when {
        !Store.wallpaper -> P
        Store.wallpaperDim >= 60 -> onWallpaper(DARK) // dimmed this much, light words always read best
        Wallpaper.isLight(this) -> onWallpaper(LIGHT)
        else -> onWallpaper(DARK)
    }.also { ink = it }

    /** On a photo, grey secondary text disappears: secondary lines use the main color, slightly see-through. */
    private fun onWallpaper(p: Palette) = Palette(p.dark, p.bg, p.surface, p.surface2, p.text,
        (p.text and 0x00FFFFFF) or 0xDB000000.toInt(), (p.text and 0x00FFFFFF) or 0xB3000000.toInt(),
        p.line, p.accent, p.onAccent, p.accentSoft, p.warn, p.ripple, p.scrim)

    /** A gentle shade at the top and bottom edges (as phone launchers do), so words over a bright
     *  part of the wallpaper stay readable. The middle of your wallpaper is untouched. */
    private fun edgeShade() = android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
        if (ink().dark) intArrayOf(0x59000000, 0, 0x59000000) else intArrayOf(0x66FFFFFF, 0, 0x66FFFFFF))

    /** On your wallpaper the window is clear (the dimming, if any, is drawn once, by the screen itself). */
    override fun windowColor(): Int = when {
        Store.breakActive() -> BreakScreen.BG
        Store.wallpaper -> Color.TRANSPARENT
        else -> P.bg
    }
    private fun screenColor(): Int = when {
        Store.breakActive() -> BreakScreen.BG
        Store.wallpaper -> Color.argb(Store.wallpaperDim * 255 / 100, 0, 0, 0)
        else -> P.bg
    }
    override fun barsOnDark(): Boolean = Store.breakActive() || ink().dark

    /** A tight shadow (like icon labels on Android's own home screen) keeps each letter's edge visible
     *  on any wallpaper, without a smudge behind the line. */
    private fun <T : TextView> T.legible(): T {
        if (Store.wallpaper) setShadowLayer(dp(2.5f).toFloat(), 0f, dp(0.5f).toFloat(), if (ink().dark) 0xA6000000.toInt() else 0x99FFFFFF.toInt())
        return this
    }

    private fun applyWindow() {
        val flag = WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        // No wallpaper during a real break: just the timer.
        if (Store.wallpaper && !Store.breakActive()) window.addFlags(flag) else window.clearFlags(flag)
        Theme.apply(this)
        host.setBackgroundColor(screenColor())
        applyStatusBar()
    }

    /** Hide the status bar on the home screen if you chose to (swipe down from the top still shows it). */
    private fun applyStatusBar() {
        val show = Store.statusBar && !Store.breakActive()
        if (Build.VERSION.SDK_INT >= 30) {
            val c = window.decorView.windowInsetsController ?: return
            if (show) c.show(WindowInsets.Type.statusBars())
            else {
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsets.Type.statusBars())
            }
        } else {
            @Suppress("DEPRECATION")
            val f = WindowManager.LayoutParams.FLAG_FULLSCREEN
            if (show) window.clearFlags(f) else window.addFlags(f)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyStatusBar()
    }

    /** A live wallpaper reacts to taps on empty space, as it does on Android's own home screen. */
    private fun wallpaperTap(e: MotionEvent) {
        if (!Store.wallpaper || breakScreen != null) return
        val token = window.decorView.windowToken ?: return
        try {
            android.app.WallpaperManager.getInstance(this)
                .sendWallpaperCommand(token, android.app.WallpaperManager.COMMAND_TAP, e.rawX.toInt(), e.rawY.toInt(), 0, null)
        } catch (_: Exception) {}
    }

    /** A short line under the date ("Basic phone is on…"), gone after a few seconds. */
    private fun hint(msg: String) {
        Store.flash = msg
        renderStats()
    }

    /** Swipe up / search. Basic phone means essentials only: no search at all. */
    private fun openLibrary() {
        if (Store.basicPhone) { hint("Basic phone is on: just your essentials."); return }
        library().open()
    }

    private fun alignGravity(): Int = when (Store.align) {
        "center" -> Gravity.CENTER_HORIZONTAL
        "end" -> Gravity.END
        else -> Gravity.START
    }

    private fun signature() = "${Store.font}|${Store.align}|${Store.textScale}|${Store.showDate}|${Store.wallpaper}|" +
        "${Store.wallpaper && Wallpaper.isLight(this)}|${Store.wallpaperDim}|${Store.statusBar}|${Store.breakActive()}|${Store.showQuote}"

    private fun buildHome() {
        layoutSig = signature()
        closeAllLayers()
        library = null
        mind = null
        ink = null
        breakScreen?.dispose()
        breakScreen = null
        applyWindow()
        if (Store.breakActive()) { buildBreak(); return }
        val k = ink()
        host.removeAllViews()
        page = vbox(alignGravity())
        if (Store.wallpaper && Store.wallpaperDim < 60) page.background = edgeShade()
        host.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        removeBars(barsListener)
        onBars(barsListener)

        val g = alignGravity()
        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = scale(64f)
            typeface = Fonts.light(this@HomeActivity)
            setTextColor(k.text)
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            gravity = g
            contentDescription = "Clock. Opens alarms."
            background = ripple(null, dp(12).toFloat())
            setOnClickListener { it.tick(); Launch.alarms(this@HomeActivity) }
        }.legible()
        page.addView(clock, lp().apply { gravity = g })
        if (Store.showDate) {
            val date = TextClock(this).apply {
                format12Hour = "EEEE, d MMMM"
                format24Hour = "EEEE, d MMMM"
                textSize = scale(16f)
                typeface = Fonts.regular(this@HomeActivity)
                setTextColor(k.text2)
                gravity = g
                setPadding(0, dp(6), 0, 0)
                background = ripple(null, dp(8).toFloat())
                setOnClickListener { it.tick(); Launch.calendar(this@HomeActivity) }
            }.legible()
            page.addView(date, lp().apply { gravity = g })
        }
        statsLine = text("", scale(14f), k.text2).apply {
            gravity = g; setPadding(0, dp(10), 0, dp(4)); visibility = View.GONE
            setLineSpacing(0f, 1.25f)
            background = ripple(null, dp(8).toFloat())
            setOnClickListener { it.tick(); if (breakScreen == null) todaySheet() }
        }.legible()
        page.addView(statsLine, lp())

        // A line worth stopping for: one a day. Tap it for where it's from (and to pin it or turn quotes
        // off). On a wallpaper it sits on a soft pane in full-strength text, so it reads on any picture.
        quoteBox = null; quoteText = null; quoteBy = null
        if (Store.showQuote) {
            val onWall = Store.wallpaper
            val quote = text("", scale(16f), if (onWall) k.text else k.text2, if (onWall) Fonts.regular(this) else Fonts.light(this)).apply {
                gravity = g; setLineSpacing(0f, 1.28f)
                breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED // even lines, no lonely last word
            }.legible()
            val by = text("", scale(11.5f), if (onWall) k.text2 else k.text3, Fonts.medium(this)).apply {
                gravity = g; letterSpacing = 0.08f; setPadding(0, dp(8), 0, 0)
            }.legible()
            val box = vbox(g).apply {
                if (onWall) {
                    val r = dp(20).toFloat()
                    background = ripple(rounded(if (k.dark) 0x52000000 else 0x73FFFFFF, r), r)
                    setPadding(dp(18), dp(14), dp(18), dp(15))
                } else background = ripple(null, dp(12).toFloat())
                setOnClickListener { it.tick(); quoteSheet(quoteNow) }
                addView(quote, lp()); addView(by, lp())
            }
            // The pane reaches a little into the margin, so its words line up with the clock and apps.
            val bleed = if (onWall) dp(18) else 0
            page.addView(box, lp().apply { topMargin = dp(if (onWall) 18 else 24); leftMargin = -bleed; rightMargin = -bleed })
            page.clipToPadding = false
            quoteBox = box; quoteText = quote; quoteBy = by
            showQuote(quoteForNow(), animate = false)
        }

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

        checkIn = vbox(g).apply {
            visibility = View.GONE
            // On a wallpaper the question sits on a soft card, so the choices are easy to read.
            if (Store.wallpaper) {
                background = rounded((k.surface and 0x00FFFFFF) or 0xE6000000.toInt(), dp(22).toFloat())
                setPadding(dp(18), dp(16), dp(18), dp(14))
            }
        }
        mid.addView(checkIn, lp().apply { bottomMargin = dp(18); topMargin = dp(12) })

        oneThing = text("", scale(16f), k.text2).apply {
            gravity = g
            setPadding(0, dp(8), 0, dp(8))
            minHeight = dp(48)
            background = ripple(null, dp(10).toFloat())
            setOnClickListener { it.tick(); oneThingTap() }
        }.legible()
        mid.addView(oneThing, lp())

        status = text("", scale(15f), k.accent, Fonts.medium(this)).apply {
            gravity = g
            setPadding(0, dp(4), 0, dp(10))
            minHeight = dp(48)
            visibility = View.GONE
            background = ripple(null, dp(10).toFloat())
            setOnClickListener { it.tick(); statusSheet() }
        }.legible()
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

    /** Where today's line is from, and what research it leans on. Pin it for a week, or turn quotes off. */
    private fun quoteSheet(q: com.poshith.intent.core.Quote) {
        val sh = com.poshith.intent.ui.Sheet(this, "About this line")
        sh.add(text("“${q.text}”", 17f, P.text).apply { setLineSpacing(0f, 1.25f) })
        sh.add(text(q.by.ifBlank { "Written for Intent" }, 14.5f, P.text2, Fonts.medium(this)))
        if (q.note.isNotBlank()) sh.add(text(q.note, 14.5f, P.text2).apply { setLineSpacing(0f, 1.25f) })
        val idx = com.poshith.intent.core.Quotes.all.indexOf(q)
        val now = System.currentTimeMillis()
        val pinned = Store.quotePinned == idx && Store.quotePinnedUntil > now
        sh.item(if (pinned) "Unpin" else "Pin it for a week", if (pinned) "Back to a new line each day" else "The same line every day until then") {
            if (pinned) { Store.quotePinned = -1; Store.quotePinnedUntil = 0L }
            else { Store.quotePinned = idx; Store.quotePinnedUntil = now + com.poshith.intent.core.Quotes.PIN_MS }
            showQuote(quoteForNow(), animate = true)
        }
        sh.item("Turn quotes off", "You can bring them back in Settings › Home screen") { Store.showQuote = false }
        sh.show()
    }

    /** Shows [q] in the quote pane: a soft cross-fade of the words, the pane itself stays put. */
    private fun showQuote(q: com.poshith.intent.core.Quote, animate: Boolean) {
        val t = quoteText ?: return
        val by = quoteBy ?: return
        if (q == quoteNow && t.text.isNotEmpty()) return
        quoteNow = q
        fun put() {
            t.text = "“${q.text}”"
            by.text = q.by.uppercase()
            by.visibility = if (q.by.isBlank()) View.GONE else View.VISIBLE
        }
        if (!animate || !animationsOn() || !resumed) { put(); return }
        by.animate().cancel(); by.animate().alpha(0f).setDuration(200).start()
        t.animate().cancel()
        t.animate().alpha(0f).setDuration(200).withEndAction {
            put()
            t.animate().alpha(1f).setDuration(420).start()
            by.animate().alpha(1f).setDuration(420).start()
        }.start()
    }

    /**
     * Coming back home: the clock, then each line, rises a few pixels into place. Short and soft,
     * so it reads as calm rather than busy, and skipped when animations are off.
     */
    private fun settle() {
        if (!animationsOn() || breakScreen != null || !::page.isInitialized) return
        val views = ArrayList<View>()
        for (i in 0 until page.childCount) {
            val v = page.getChildAt(i)
            if (v.visibility != View.VISIBLE) continue
            if (v === midScroll) {
                if (oneThing.visibility == View.VISIBLE) views += oneThing
                if (status.visibility == View.VISIBLE) views += status
                for (j in 0 until appsBox.childCount) appsBox.getChildAt(j).takeIf { it.visibility == View.VISIBLE }?.let { views += it }
            } else views += v
        }
        val rise = dp(10).toFloat()
        val ease = DecelerateInterpolator(2.2f)
        views.forEachIndexed { n, v ->
            v.animate().cancel()
            v.alpha = 0f
            v.translationY = rise
            v.animate().alpha(1f).translationY(0f).setStartDelay(minOf(n, 9) * 16L).setDuration(300)
                .setInterpolator(ease).start()
        }
    }

    // ---------------------------------------------------------------------------------- real break

    private fun buildBreak() {
        host.removeAllViews()
        val b = BreakScreen(this) { breakOver() }
        breakScreen = b
        host.addView(b.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (resumed) b.start()
    }

    /** The timer ran out, or you held "end the break". Do Not Disturb goes back to how it was. */
    private fun breakOver() {
        val early = Store.breakActive()
        Break.end(this)
        Store.flash = if (early) "Break ended." else "Break done. Welcome back."
        buildHome()
        refresh()
    }

    private fun bottomItem(label: String, onClick: () -> Unit): TextView = text(label, 15f, ink().text2).legible().apply {
        gravity = Gravity.CENTER
        background = ripple(null, dp(20).toFloat())
        isClickable = true
        setOnClickListener { it.tick(); onClick() }
    }

    private fun refresh() {
        if (layoutSig != signature()) { buildHome(); if (resumed) breakScreen?.start() }
        if (breakScreen != null) return
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
            appsBox.addView(text("Long-press anywhere to choose your apps", scale(15f), ink().text3).apply { gravity = g; setPadding(0, dp(8), 0, dp(8)) }.legible(), lp())
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
            // Basic phone: only what can open is shown. The rest isn't even there.
            if (Store.basicPhone && Launch.decide(this, key.substringBefore('/')) !is Decision.Open) continue
            val name = entry?.label ?: Store.customLabel(key) ?: Store.cachedName(key) ?: continue
            val t = text(name, scale(26f), ink().text, Fonts.regular(this), lines = 1).legible().apply {
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

    /**
     * Today's one thing, above your apps. Yesterday's isn't passed off as today's: it says "Yesterday"
     * and a tap offers to keep, change or clear it. With nothing set yet: "What matters today?" (until you
     * answer or say "Not today"); late in your day, if you turned the look-back on: "How did today go?".
     */
    private fun renderOneThing() {
        if (!::oneThing.isInitialized) return
        val k = ink()
        val today = Store.todayIntention()
        val stale = Store.staleIntention()
        val line: Pair<String, Int>? = when {
            !Store.showOneThing -> null
            com.poshith.intent.ui.lookBackDue() -> "How did today go?" to k.accent
            today != null -> "Today · $today" to k.text2
            stale != null -> "Yesterday · $stale" to k.text2
            com.poshith.intent.ui.planDue() -> "What matters today?" to k.accent
            else -> null
        }
        oneThing.visibility = if (line == null) View.GONE else View.VISIBLE
        if (line != null) { oneThing.text = line.first; oneThing.setTextColor(line.second) }
    }

    private fun oneThingTap() {
        val done = { renderOneThing() }
        when {
            com.poshith.intent.ui.lookBackDue() -> reflectSheet(done)
            Store.todayIntention() != null -> intentionSheet(done)
            Store.staleIntention() != null -> rolloverSheet(done)
            else -> planSheet(done)
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
            Store.locked(now) && feedsClosedUntil(now) != null -> "Shorts and Reels closed until ${feedsClosedUntil(now)}"
            Store.morningUntil > now -> "Morning quiet until ${tf.format(Date(Store.morningUntil))}"
            Store.windDown?.contains(Store.minuteOfDay(now)) == true ->
                "Wind-down until ${tf.format(Date(Store.windDown!!.endsAfter(now)))}"
            // Nothing else to say: push for the protections that are still off (until they're on, or
            // "remind me next week").
            now >= Store.protectNudgeAfter -> Launch.missingProtections(this).let { m ->
                when (m.size) { 0 -> null; 1 -> "${m[0].name} is off"; else -> "${m[0].name} and ${m[1].name} are off" }
            }
            else -> null
        }
        status.visibility = if (s == null) View.GONE else View.VISIBLE
        status.text = s ?: ""
    }

    /** The protections that are off, one tap each, and a way to be reminded later. */
    private fun protectSheet(missing: List<Launch.Missing>) {
        val s = Sheet(this, "Switched off", "The rules you chose depend on these. One switch each in Android's settings.")
        for (m in missing) s.item(m.name, null, P.accent, keepOpen = false) { m.turnOn(this) }
        s.add(button("Remind me next week", Kind.PLAIN) {
            Store.protectNudgeAfter = System.currentTimeMillis() + 7 * 86_400_000L
            s.dismiss(); renderStatus()
        })
        s.show()
    }

    private fun renderStats() {
        if (!::statsLine.isInitialized) return
        val flash = Store.flash
        if (flash.isNotBlank() && System.currentTimeMillis() - Store.flashAt < 8_000) {
            statsLine.visibility = View.VISIBLE
            statsLine.setTextColor(ink().accent)
            statsLine.text = flash
            Store.flash = ""
            ui.postDelayed({ renderStats() }, 4_000)
            return
        }
        statsLine.setTextColor(ink().text2)
        if (!Store.showStats) { statsLine.visibility = View.GONE; return }
        com.poshith.intent.ui.Bg.run {
            val usage = Usage.today(this)
            runOnUiThread {
                if (isDestroyed || breakScreen != null) return@runOnUiThread
                statsLine.text = if (usage == null) "" else "${Usage.formatMinutes(usage.totalMs(packageName))} today"
                statsLine.visibility = if (statsLine.text.isBlank()) View.GONE else View.VISIBLE
            }
        }
    }

    /** Today in numbers, only when you ask (tap the screen time). The home screen stays quiet. */
    private fun todaySheet() {
        val s = Sheet(this, "Today", null)
        val body = vbox()
        s.add(body)
        body.addView(text("…", 15f, P.text3), lp())
        s.show()
        com.poshith.intent.ui.Bg.run {
            val usage = Usage.today(this)
            val mindful = Store.packagesWithMode(Mode.MINDFUL)
            val shorts = Stats.shortVideosToday(com.poshith.intent.core.Feed.SHORTS)
            val reels = Stats.shortVideosToday(com.poshith.intent.core.Feed.REELS)
            val notNow = Stats.notNowToday()
            val top = usage?.foregroundMs?.filterKeys { it in mindful }?.entries?.sortedByDescending { it.value }
                ?.filter { it.value >= 60_000 }?.take(4).orEmpty()
            val pull = usage?.msFor(mindful) ?: 0L
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                body.removeAllViews()
                fun row(label: String, value: String) {
                    val r = hbox(Gravity.CENTER_VERTICAL).apply { minimumHeight = dp(48) }
                    r.addView(text(label, 16f, P.text2), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    r.addView(text(value, 16f, P.text, Fonts.medium(this@HomeActivity)))
                    body.addView(r, lp())
                }
                if (usage == null) {
                    body.addView(paragraph("Allow usage access to see your screen time. It's counted on the phone and never leaves it.", 15f, P.text2), lp())
                    body.addView(button("Allow usage access", Kind.QUIET) { s.dismiss(); Launch.usageSettings(this) }, lp())
                } else {
                    row("Screen time", Usage.formatMinutes(usage.totalMs(packageName)))
                    if (usage.unlocksKnown) row("Unlocks", usage.unlocks.toString())
                    row("In apps you want to use less", Usage.formatMinutes(pull))
                    for ((pkg, ms) in top) row("   " + Apps.labelFor(this, pkg), Usage.formatMinutes(ms))
                }
                val left = com.poshith.intent.core.Limits.budgetLeft(Store.budgetMin, Stats.day().plannedMin)
                if (left != null) row("Budget left", "$left of ${Store.budgetMin} min")
                // Counted from scrolling, so estimates.
                if (shorts > 0) row("Shorts (about)", shorts.toString())
                if (reels > 0) row("Reels (about)", reels.toString())
                if (notNow > 0) row("Said “Not now”", "$notNow×")
                // The long view, only here, where you came to look: what a year of days like today adds up to.
                val pullMin = (pull / 60_000L).toInt()
                if (usage != null && pullMin >= 10) body.addView(paragraph("A year of days like today: about ${com.poshith.intent.core.Strict.wakingDaysPerYear(pullMin)} waking days in these apps.", 14.5f, P.text2).apply {
                    setPadding(0, dp(10), 0, 0)
                }, lp())
                body.addView(space(8))
                body.addView(button("Your week", Kind.PLAIN) { s.dismiss(); mindPanel().open() }, lp())
            }
        }
    }

    // ---------------------------------------------------------------------------------- check-in

    /** Right after you unlock: "What did you pick up your phone for?" Never blocks anything. */
    private fun maybeCheckIn() {
        if (!Store.checkIn || !Store.onboarded || hasBackLayers() || breakScreen != null) return
        if (com.poshith.intent.guard.Calls.active(this)) return // never between you and a call
        val at = Store.lastUnlockElapsed
        if (at == 0L || at == shownCheckInFor) return
        if (SystemClock.elapsedRealtime() - at > 6_000) return
        shownCheckInFor = at
        afterToken = null // the unlock question takes the place of a "how did it feel?"
        checkIn.removeAllViews()
        val g = alignGravity()
        val k = ink()
        checkIn.addView(text("What did you pick up your phone for?", scale(17f), k.text, Fonts.medium(this)).apply { gravity = g }, lp())
        val flow = Flow(this).apply { setPadding(0, dp(12), 0, 0) }
        fun chip(label: String, action: () -> Unit) = Chip(this, label, k).apply { setOnClickListener { it.tick(); action() } }
        flow.addView(chip("A call or message") { callOrMessage() })
        if (!Store.basicPhone) flow.addView(chip("Something specific") { cancelAutoLock(); hideCheckIn(); openLibrary() })
        flow.addView(chip("Just checking") { justChecking() })
        checkIn.addView(flow, lp())
        if (Store.lockIfNoAnswer && FocusGuardService.running) startAutoLock(at)
        else ui.postDelayed({ if (shownCheckInFor == at && autoLock == null) hideCheckIn() }, 12_000)
        showCheckIn()
    }

    // No answer, no reason: the phone locks again. A thin line shows the time left; any touch stops it.
    private var autoLock: Runnable? = null

    private fun startAutoLock(at: Long) {
        val k = ink()
        val g = alignGravity()
        checkIn.addView(text("No answer locks the phone.", 13f, k.text3).apply { gravity = g; setPadding(0, dp(14), 0, dp(8)) }, lp())
        val line = com.poshith.intent.ui.Countdown(this, k.accent, AUTO_LOCK_MS, g)
        checkIn.addView(line, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)))
        line.start()
        val r = object : Runnable {
            override fun run() {
                if (autoLock !== this || shownCheckInFor != at) return
                autoLock = null
                hideCheckIn()
                if (!com.poshith.intent.guard.Calls.active(this@HomeActivity)) FocusGuardService.lockScreen()
            }
        }
        autoLock = r
        ui.postDelayed(r, AUTO_LOCK_MS)
    }

    private fun cancelAutoLock() {
        val r = autoLock ?: return
        autoLock = null
        ui.removeCallbacks(r)
        // The countdown is gone; the question stays until you answer or leave.
        if (::checkIn.isInitialized && checkIn.childCount >= 4) {
            checkIn.removeViewAt(checkIn.childCount - 1)
            checkIn.removeViewAt(checkIn.childCount - 1)
        }
    }

    /** "A call or message": the ways to reach someone, right here. */
    private fun callOrMessage() {
        cancelAutoLock()
        val k = ink()
        val g = alignGravity()
        checkIn.removeAllViews()
        checkIn.addView(text("Who do you want to reach?", scale(17f), k.text, Fonts.medium(this)).apply { gravity = g }, lp())
        val flow = Flow(this).apply { setPadding(0, dp(12), 0, 0) }
        fun chip(label: String, action: () -> Unit) = Chip(this, label, k).apply { setOnClickListener { it.tick(); hideCheckIn(); action() } }
        flow.addView(chip("Phone") { Launch.dialer(this) })
        val sms = Launch.resolve(this, Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:")))
        if (sms != null) flow.addView(chip(Apps.labelFor(this, sms)) { Launch.openPackage(this, sms) })
        for (wa in Catalog.WHATSAPP) Apps.byPkg(wa)?.let { app -> flow.addView(chip(app.label) { Launch.open(this, app, null) }) }
        checkIn.addView(flow, lp())
    }

    private fun justChecking() {
        cancelAutoLock()
        Stats.justChecking()
        val k = ink()
        val g = alignGravity()
        checkIn.removeAllViews()
        checkIn.addView(text("Nothing needs you right now.", scale(17f), k.text, Fonts.medium(this)).apply { gravity = g }, lp())
        if (FocusGuardService.running) {
            // Say it, then do it: the phone locks on its own.
            checkIn.addView(text("Locking the phone…", scale(15f), k.text2).apply { gravity = g; setPadding(0, dp(6), 0, 0) }, lp())
            val at = shownCheckInFor
            ui.postDelayed({ if (shownCheckInFor == at && resumed) { hideCheckIn(); FocusGuardService.lockScreen() } }, 1_300)
        } else {
            checkIn.addView(text("Put it down. Turn on Focus Guard and Intent locks it for you.", scale(15f), k.text2).apply {
                gravity = g; setLineSpacing(0f, 1.2f); setPadding(0, dp(6), 0, dp(10))
            }, lp())
            checkIn.addView(Chip(this, "Turn on Focus Guard", k).apply { setOnClickListener { hideCheckIn(); guardSheet(this@HomeActivity) } },
                lp(ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = g })
            val at = shownCheckInFor
            ui.postDelayed({ if (shownCheckInFor == at) hideCheckIn() }, 8_000)
        }
    }

    // ---------------------------------------------------------------------------------- after a session

    /** The "how did it feel?" on screen, so its own timers don't close anything else. */
    private var afterToken: Any? = null

    /**
     * Back home after a session: "After YouTube, how do you feel?" Better, about the same, or worse. Once
     * per session, only after a minute or more in the app, at most three times a day, and never in the
     * way: no answer is fine, and it goes by itself after a little while. Paying attention to what a habit actually
     * gives you is how its pull gets re-learned (Ludwig, Brown & Brewer, 2020); the answers stay on the phone.
     */
    private fun maybeAfterCheck() {
        if (!Store.askAfter || !Store.onboarded || hasBackLayers() || breakScreen != null) return
        if (!::checkIn.isInitialized || checkIn.visibility == View.VISIBLE) return
        if (com.poshith.intent.guard.Calls.active(this)) return
        val (pkg, started) = Noticed.pendingAfter() ?: return
        when (Pulls.afterAsk(started, System.currentTimeMillis(), Noticed.asksToday())) {
            AfterAsk.WAIT -> return
            AfterAsk.DROP -> { Noticed.clearPendingAfter(); return }
            AfterAsk.ASK -> {}
        }
        // With Focus Guard counting the minutes actually spent in the app: barely used yet, ask another time.
        val used = Store.sessions { b -> b[pkg]?.let { b.usedMs(pkg, SystemClock.elapsedRealtime()) } }
        if (FocusGuardService.running && used != null && used < 45_000) return
        Noticed.clearPendingAfter()
        Noticed.noteAsked()
        val k = ink()
        val g = alignGravity()
        val token = Any()
        afterToken = token
        checkIn.removeAllViews()
        checkIn.addView(text("After ${Apps.labelFor(this, pkg)}, how do you feel?", scale(17f), k.text, Fonts.medium(this)).apply { gravity = g }, lp())
        val flow = Flow(this).apply { setPadding(0, dp(12), 0, 0) }
        fun chip(label: String, feel: Int) = Chip(this, label, k).apply { setOnClickListener { it.tick(); afterAnswered(pkg, feel, token) } }
        flow.addView(chip("Better", 1))
        flow.addView(chip("About the same", 0))
        flow.addView(chip("Worse", -1))
        checkIn.addView(flow, lp())
        ui.postDelayed({ if (afterToken === token) { afterToken = null; hideCheckIn() } }, 20_000)
        showCheckIn()
    }

    private fun afterAnswered(pkg: String, feel: Int, token: Any) {
        if (afterToken !== token) return
        Noticed.noteAfter(pkg, feel)
        val k = ink()
        val g = alignGravity()
        checkIn.removeAllViews()
        checkIn.addView(text(when (feel) {
            1 -> "Good. That's what on purpose feels like."
            0 -> "Noted. Worth knowing."
            else -> "Noticing that is how a habit changes. Remember it next time the pull comes."
        }, scale(16f), k.text, Fonts.medium(this)).apply { gravity = g; setLineSpacing(0f, 1.2f) }, lp())
        ui.postDelayed({ if (afterToken === token) { afterToken = null; hideCheckIn() } }, 3_500)
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
        override fun onSingleTapUp(e: MotionEvent): Boolean { wallpaperTap(e); return false }
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
        if (breakScreen != null) return super.dispatchTouchEvent(ev) // a break: no swipes, no menus
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) cancelAutoLock()
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
            if (Store.swipeRightMind) { page.tick(); mindPanel().open() }
        } else {
            val key = Store.swipeLeftKey
            val app = if (key.isNotBlank()) Apps.byKey(key) else null
            if (app != null) Launch.open(this, app, null) else openLibrary()
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
        if (up) { if (!Store.basicPhone) page.tick(); openLibrary() } else expandNotifications()
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
        /** Choosing an app (Replace) instead of opening one. */
        private var picking: ((AppEntry) -> Unit)? = null
        private val defaultHint = "Type to find an app.\nTyping, not scrolling, keeps it on purpose."
        private val closer: () -> Unit = { close() }
        private val bars = BarLayer(P.dark, P.dark)
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

        /** Opens the search to choose an app: tapping a result hands it to [onPick] instead of opening it. */
        fun pick(title: String, onPick: (AppEntry) -> Unit) {
            if (isOpen) close(false)
            picking = onPick
            hint.text = "$title\nType the app's name."
            hint.setTextColor(P.text2)
            open()
        }

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
            pushBarLayer(bars)
            if (animationsOn()) {
                root.alpha = 0f; root.translationY = dp(40).toFloat()
                root.animate().alpha(1f).translationY(0f).setDuration(200).start()
            }
            showKeyboard(input)
        }

        fun close(animate: Boolean = true) {
            if (!isOpen) return
            isOpen = false
            picking = null
            hint.text = defaultHint
            hint.setTextColor(P.text3)
            dropBack(closer)
            removeBars(barL)
            dropBarLayer(bars)
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
            val own = picking == null && Apps.score("Intent settings", q) >= 0
            if (found.isEmpty() && !own) {
                results.addView(text("No app called “$q”.", 16f, P.text3).apply { setPadding(dp(30), dp(10), dp(30), dp(10)) }, lp())
                return
            }
            // Best match sits closest to your thumb, right above the keyboard.
            val rows = ArrayList<View>()
            for (a in found) rows += resultRow(a)
            if (own) rows += plainRow("Intent settings", "settings") { close(false); startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
            for (r in rows.asReversed()) results.addView(r, lp())
            if (picking == null && Store.autoOpenSingle && found.size == 1 && !own && q.trim().length >= 2) (rows[0].tag as? (() -> Unit))?.invoke()
        }

        private fun resultRow(a: AppEntry): View {
            val mode = Store.rule(a.pkg).mode
            val tag = when {
                Launch.isProtected(this@HomeActivity, a.pkg) -> if (a.isWork) "work" else null
                mode == Mode.MINDFUL -> "use less"
                mode == Mode.BATCHED -> "check-in"
                mode == Mode.BLOCKED -> "blocked"
                a.isWork -> "work"
                else -> null
            }
            val pick = picking
            val action: () -> Unit = if (pick != null) {
                { close(false); pick(a); Launch.toast(this@HomeActivity, "${a.label} is on your home screen") }
            } else {
                { close(false); Launch.open(this@HomeActivity, a, null) }
            }
            val row = plainRow(a.label, tag, action)
            if (pick == null) row.setOnLongClickListener { v -> v.thud(); appMenu(a); true }
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
            if (tag != null) row.addView(text(tag, 13f, if (tag == "use less" || tag == "blocked" || tag == "check-in") P.accent else P.text2, Fonts.medium(this@HomeActivity)).apply {
                setPadding(dp(12), 0, 0, 0)
            })
            return row
        }
    }

    // ---------------------------------------------------------------------------------- sheets


    fun appMenu(app: AppEntry) {
        val rule = Store.rule(app.pkg)
        val prot = Launch.isProtected(this, app.pkg)
        val s = Sheet(this, app.label, if (prot) "Always opens: it's needed for calls, payments or the system." else modeLine(rule))

        // Quick actions in one row: nothing hidden below the fold, no keyboard.
        val quick = Flow(this)
        val home = Store.homeKeys
        val onHome = app.key in home
        if (onHome) quick.addView(Chip(this, "Replace").apply {
            setOnClickListener {
                it.tick(); s.dismiss()
                library().pick("Replace ${app.label} with…") { chosen ->
                    val now = Store.homeKeys
                    Store.homeKeys = when {
                        chosen.key == app.key -> now
                        chosen.key in now -> now.map { k -> if (k == app.key) chosen.key else if (k == chosen.key) app.key else k } // swap places
                        else -> now.map { k -> if (k == app.key) chosen.key else k }
                    }
                }
            }
        })
        quick.addView(Chip(this, if (onHome) "Remove from home" else "Add to home").apply {
            setOnClickListener {
                it.tick(); s.dismiss()
                when {
                    onHome -> Store.homeKeys = home - app.key
                    home.size < Store.MAX_HOME -> Store.homeKeys = home + app.key
                    else -> Launch.toast(this@HomeActivity, "Your home screen holds ${Store.MAX_HOME} apps. Fewer is calmer.")
                }
            }
        })
        quick.addView(Chip(this, "Rename").apply { setOnClickListener { it.tick(); s.dismiss(); renameSheet(app) } })
        val hidden = Store.hidden
        quick.addView(Chip(this, if (app.key in hidden) "Show in search" else "Hide from search").apply {
            setOnClickListener { it.tick(); s.dismiss(); Store.hidden = if (app.key in hidden) hidden - app.key else hidden + app.key }
        })
        s.add(quick)

        // How it opens: one choice, the current one marked.
        if (!prot) {
            s.add(text("HOW IT OPENS", 12f, P.text3, Fonts.medium(this)).apply { letterSpacing = 0.1f; setPadding(0, dp(14), 0, dp(2)) })
            val modes = Flow(this)
            for ((m, label) in listOf(Mode.ESSENTIAL to "Essential", Mode.NORMAL to "Normal", Mode.MINDFUL to "Use less",
                Mode.BATCHED to "Check-in", Mode.BLOCKED to "Blocked")) {
                val c = Chip(this, label); c.on = m == rule.mode
                c.setOnClickListener {
                    it.tick()
                    if (m == rule.mode) return@setOnClickListener
                    s.dismiss()
                    if (m.isLooserThan(rule.mode)) {
                        confirmLoosen("Make ${app.label} easier to open?", "From ${Launch.modeLabel(rule.mode)} to ${Launch.modeLabel(m)}.") { Store.setMode(app.pkg, m) }
                    } else Store.setMode(app.pkg, m)
                }
                modes.addView(c)
            }
            s.add(modes)
        }
        s.add(space(6))
        s.item("App info") { Launch.appInfo(this, app) }
        s.item("Uninstall", color = P.warn) { Launch.uninstall(this, app) }
        s.show()
    }

    private fun modeLine(rule: com.poshith.intent.core.Rule): String = when (rule.mode) {
        Mode.ESSENTIAL -> "Essential: opens instantly, even during focus."
        Mode.NORMAL -> "Opens instantly. Waits during focus and breaks."
        Mode.MINDFUL -> "Use less: a ${rule.pauseSec} s pause first, then the minutes you choose."
        Mode.BATCHED -> "Opens only at your check-in times (${checkInText()})."
        Mode.BLOCKED -> "Blocked. You can still unlock it once by typing a phrase."
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
        s.item("Today's one thing") { oneThingTap() }
        s.item("Focus or a real break") { focusSheet() }
        s.item("Practice", "A one-minute reset, and short lessons from research") { mindPanel().open() }
        s.item("Settings") { openSettings(null) }
        if (!Launch.isDefaultHome(this)) s.item("Make Intent your home screen", color = P.accent) { Launch.requestHome(this) }
        s.show()
    }

    private fun openSettings(page: String?) {
        startActivity(Intent(this, SettingsActivity::class.java).apply { if (page != null) putExtra(SettingsActivity.EXTRA_PAGE, page) })
    }

    private fun statusSheet() {
        val now = System.currentTimeMillis()
        when {
            Store.focusActive(now) -> focusSheet()
            Store.locked(now) && feedsClosedUntil(now) != null -> {
                val s = Sheet(this, "Shorts and Reels closed until ${feedsClosedUntil(now)}", "Strict mode, as you chose. The rest of YouTube and Instagram works as usual.")
                com.poshith.intent.ui.nextThing()?.let { s.add(paragraph(it, 16f, P.text), 0) }
                s.item("Strict mode settings") { openSettings("strict") }
                s.show()
            }
            Store.morningUntil > now -> {
                val s = Sheet(this, "Morning quiet", "The apps you want to use less rest for the first ${Store.morningQuietMin} minutes after your first unlock of the day, so your first thoughts are your own. Change it in Settings.")
                // Strict mode: quiet hours have no "end it" button (the emergency code is the one way past).
                if (!Store.strictActive()) s.add(HoldButton(this, "Hold to end it for today", 2500) { Store.morningUntil = 0; s.dismiss(); renderStatus() })
                else emergencyLink("It ends morning quiet for today.") { Store.morningUntil = 0; s.dismiss(); renderStatus() }?.let { s.add(it) }
                s.show()
            }
            Store.windDown?.contains(Store.minuteOfDay(now)) != true -> {
                val missing = Launch.missingProtections(this)
                if (missing.isNotEmpty()) protectSheet(missing)
            }
            else -> {
                val s = Sheet(this, "Wind-down", "The apps you want to use less rest until morning, and check-in apps too. Calls, alarms and essentials still work. Change the hours in Settings.")
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
            val s = Sheet(this, if (brk) "On a break" else "Focusing", if (brk) "Away from screens is the whole point." else "Until $until. Only your essentials open. Calls always ring.")
            s.add(HoldButton(this, if (brk) "Hold to end the break" else "Hold to end focus early", 3000) {
                if (brk) { s.dismiss(); breakOver() } else { Store.focusUntil = 0; s.dismiss(); renderStatus() }
            })
            s.show()
            return
        }
        val s = Sheet(this, "Make some quiet", null)
        fun render(tab: String) {
            s.content.removeAllViews()
            // Three ways, one at a time: nothing hidden below the fold.
            val tabs = Flow(this)
            for ((key, label) in listOf("break" to "Real break", "focus" to "Focus", "basic" to "Basic phone")) {
                val c = Chip(this, label); c.on = key == tab
                c.setOnClickListener { it.tick(); if (key != tab) render(key) }
                tabs.addView(c)
            }
            s.add(tabs)
            when (tab) {
                "break" -> breakTab(s)
                "focus" -> focusTab(s)
                else -> basicTab(s)
            }
        }
        render("break")
        s.show()
    }

    private fun breakTab(s: Sheet) {
        s.add(paragraph("Only calls get through. Every other app and every notification waits. The screen shows how long is left, and the phone tells you when it's over.", 15f, P.text2), 0)
        var breakMin = Store.breakLength
        val presets = intArrayOf(15, 30, 45, 60, 90)
        val bf = Flow(this)
        val bChips = ArrayList<Chip>()
        val custom = field("How many minutes?", if (breakMin in presets) "" else breakMin.toString(), caps = false).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            visibility = if (breakMin in presets) View.GONE else View.VISIBLE
        }
        custom.onText { t -> t.toIntOrNull()?.let { breakMin = it.coerceIn(1, Break.MAX_MIN) } }
        for (m in presets) {
            val c = Chip(this, if (m < 60) "$m min" else if (m == 60) "1 hour" else "1 h 30"); c.on = m == breakMin
            c.setOnClickListener {
                it.tick(); breakMin = m; bChips.forEach { x -> x.on = x === c }
                custom.visibility = View.GONE; hideKeyboard(this, custom)
            }
            bChips += c; bf.addView(c)
        }
        val cc = Chip(this, "Your time"); cc.on = breakMin !in presets
        cc.setOnClickListener {
            it.tick(); bChips.forEach { x -> x.on = x === cc }
            custom.visibility = View.VISIBLE; custom.requestFocus(); showKeyboard(custom)
        }
        bChips += cc; bf.addView(cc)
        s.add(bf)
        s.add(custom)
        // What makes it a real break: two one-time switches in Android's settings (after either, this
        // sheet comes back by itself). Notification access also gives Do Not Disturb access, so the
        // first is usually one switch, not two.
        fun silenced() = BreakQuietService.hasAccess(this) && Dnd.hasAccess(this)
        fun silence() { reopenQuiet = true; if (!BreakQuietService.hasAccess(this)) BreakQuietService.requestAccess(this) else Dnd.requestAccess(this) }
        fun guardOn() { reopenQuiet = true; guardSheet(this) }
        s.add(button("Start the break", Kind.PRIMARY) {
            hideKeyboard(this, custom)
            val go = {
                Break.start(this, breakMin)
                s.dismiss()
                buildHome()
                if (resumed) breakScreen?.start()
            }
            val quiet = silenced()
            val guarded = (Launch.guardHealth(this) == Launch.Health.ON)
            if (quiet && guarded) go()
            else {
                // Honest about what a half-set-up break can't do, and one tap from fixing it.
                val c = Sheet(this, "Not quite a real break yet", when {
                    !quiet && !guarded -> "Right now, notifications still come in, and the notification shade and other apps stay open."
                    !quiet -> "Right now, notifications still come in."
                    else -> "Right now, the notification shade and other apps stay open."
                })
                if (!quiet) c.add(button("Silence notifications", Kind.PRIMARY) { c.dismiss(); silence() })
                else c.add(button("Turn on Focus Guard", Kind.PRIMARY) { c.dismiss(); s.dismiss(); guardOn() })
                c.add(button("Start anyway", Kind.PLAIN) { c.dismiss(); go() })
                c.show()
            }
        })
        // Below the button (it stays in reach on small screens): what's set, or the one tap that's missing.
        val quiet = silenced()
        val guarded = (Launch.guardHealth(this) == Launch.Health.ON)
        if (quiet && guarded) {
            s.add(paragraph("Notifications silenced and hidden, the shade kept closed. Calls still ring.", 13.5f, P.text3), 0)
        } else {
            fun need(title: String, sub: String, action: () -> Unit) {
                val r = row(title, sub, "Turn on", { _: View -> action() })
                (r.findViewWithTag<TextView>("value"))?.setTextColor(P.accent)
                s.content.addView(r, lp())
            }
            if (!quiet) need("Silence every notification", if (BreakQuietService.hasAccess(this)) "Also allow “Do Not Disturb access”" else "Allow “Notification access” once. Calls still ring.") { silence() }
            if (!guarded) need("Keep the shade and other apps closed", "Turn on Focus Guard once") { s.dismiss(); guardOn() }
        }
    }

    private fun focusTab(s: Sheet) {
        s.add(paragraph("Deep work: your essentials open, everything else waits. Calls always ring.", 15f, P.text2), 0)
        var focusMin = Store.focusLength
        val ff = Flow(this)
        val fChips = ArrayList<Chip>()
        for (m in intArrayOf(25, 50, 90)) {
            val c = Chip(this, "$m min"); c.on = m == focusMin
            c.setOnClickListener { it.tick(); focusMin = m; fChips.forEach { x -> x.on = x === c } }
            fChips += c; ff.addView(c)
        }
        s.add(ff)
        s.add(button("Start focus", Kind.PRIMARY) { startFocus("focus", focusMin); s.dismiss() })
        s.add(paragraph("Best results: carry the phone to another room. Out of reach, it gets picked up far less.", 14f, P.text3), 0)
    }

    private fun basicTab(s: Sheet) {
        s.add(paragraph("A smartphone that behaves like a simple phone: only your essentials, and no search, until you turn it off.", 15f, P.text2), 0)
        val essentials = Store.packagesWithMode(Mode.ESSENTIAL).mapNotNull { Apps.byPkg(it)?.label }.sorted()
        s.add(paragraph("Always open: Phone, Messages, Clock" + (if (essentials.isEmpty()) "" else ", " + essentials.joinToString(", ")) + ".", 13.5f, P.text3), 0)
        s.add(button("Turn on basic phone", Kind.PRIMARY) { startFocus("basic", 0); s.dismiss() })
        s.item("Choose essentials") { openSettings("essentials") }
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
