package com.poshith.intent

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.poshith.intent.core.Catalog
import com.poshith.intent.core.Feed
import com.poshith.intent.core.Mode
import com.poshith.intent.core.Rule
import com.poshith.intent.core.Schedule
import com.poshith.intent.core.Window
import com.poshith.intent.data.AppEntry
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Stats
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage
import com.poshith.intent.guard.BreakQuietService
import com.poshith.intent.guard.Dnd
import com.poshith.intent.guard.Launch
import com.poshith.intent.guard.ReelsGuardService
import com.poshith.intent.guard.Reminder
import com.poshith.intent.ui.AppPicker
import com.poshith.intent.ui.BaseActivity
import com.poshith.intent.ui.Chip
import com.poshith.intent.ui.Flow
import com.poshith.intent.ui.Fonts
import com.poshith.intent.ui.Kind
import com.poshith.intent.ui.askEmergencyCode
import com.poshith.intent.ui.hideKeyboard
import com.poshith.intent.ui.feedsClosedUntil
import com.poshith.intent.ui.Overlay
import com.poshith.intent.ui.P
import com.poshith.intent.ui.Sheet
import com.poshith.intent.ui.button
import com.poshith.intent.ui.confirmLoosen
import com.poshith.intent.ui.divider
import com.poshith.intent.ui.dp
import com.poshith.intent.ui.field
import com.poshith.intent.ui.hbox
import com.poshith.intent.ui.lp
import com.poshith.intent.ui.paragraph
import com.poshith.intent.ui.ripple
import com.poshith.intent.ui.row
import com.poshith.intent.ui.scroller
import com.poshith.intent.ui.sectionTitle
import com.poshith.intent.ui.space
import com.poshith.intent.ui.text
import com.poshith.intent.ui.tick
import com.poshith.intent.ui.toggleRow
import com.poshith.intent.ui.vbox
import java.util.Calendar

/** All settings, as a stack of simple pages inside one screen. Every change is local to the phone. */
class SettingsActivity : BaseActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val GITHUB = "https://github.com/poshithNandyala/intent-launcher"
    }

    private val pages = ArrayList<String>()
    private lateinit var col: LinearLayout
    private var sc: android.widget.ScrollView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = vbox()
        val sc = scroller(col)
        this.sc = sc
        host.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onBars { b -> sc.setPadding(0, b.top, 0, b.bottom); col.setPadding(b.left, dp(20), b.right, dp(28)) }
        pages += intent.getStringExtra(EXTRA_PAGE) ?: "main"
        render()
    }

    override fun onResume() {
        super.onResume()
        if (recreating) return
        render() // permissions may have changed in Android settings
    }

    override fun onBackRoot() {
        if (pages.size > 1) { pages.removeAt(pages.size - 1); render(); sc?.scrollTo(0, 0) } else finish()
    }

    private fun go(page: String) { pages += page; render(); sc?.scrollTo(0, 0) }

    private fun render() {
        if (!::col.isInitialized) return
        col.removeAllViews()
        when (pages.last()) {
            "home" -> pageHome()
            "essentials" -> pagePicker(Mode.ESSENTIAL)
            "mindful" -> pageMindful()
            "checkins" -> pageCheckIns()
            "blocked" -> pagePicker(Mode.BLOCKED)
            "hidden" -> pageHidden()
            "reels" -> pageReels()
            "strict" -> pageStrict()
            "focus" -> pageFocus()
            "gestures" -> pageGestures()
            "appearance" -> pageAppearance()
            "protection" -> pageProtection()
            "notifications" -> pageNotifications()
            "grayscale" -> pageGrayscale()
            "plan" -> pagePlan()
            "privacy" -> pagePrivacy()
            "about" -> pageAbout()
            else -> pageMain()
        }
    }

    private fun header(title: String, sub: String? = null) {
        val back = text(if (pages.size > 1) "‹  Back" else "‹  Done", 16f, P.text2).apply {
            setPadding(dp(24), dp(4), dp(24), dp(4)); minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
            background = ripple(null); isClickable = true; setOnClickListener { it.tick(); handleBack() }
        }
        col.addView(back, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(text(title, 32f, P.text, Fonts.light(this)).apply { setPadding(dp(24), dp(8), dp(24), dp(4)) }, lp())
        if (sub != null) col.addView(paragraph(sub), lp())
    }

    private fun add(v: View) = col.addView(v, lp())

    /** confirmLoosen, and if you keep things as they are, the page shows the real setting again
     *  (a switch or chip that already moved goes back). */
    private fun loosen(title: String, message: String, confirmLabel: String = "Yes, change it", action: () -> Unit) =
        confirmLoosen(title, message, confirmLabel, onCancel = { render() }, action = action)

    /** Today's budget: tighter applies now; looser waits a few seconds, or in Strict mode until tomorrow. */
    private fun pickBudget(v: Int) {
        val cur = Store.budgetMin
        if (com.poshith.intent.core.Limits.budgetLooser(cur, v) && !Store.strictActive())
            loosen("Allow more time?", if (v == 0) "Turns the daily budget off." else "From $cur to $v minutes a day.") { Store.changeBudget(v); render() }
        else { Store.changeBudget(v); render() }
    }

    /** Shorts and Reels: the same rule as the budget. */
    private fun pickReelsAllow(v: Int) {
        val cur = Store.reelsAllow
        if (v > cur && !Store.strictActive())
            loosen("Allow more?", "From ${if (cur == 0) "none" else "$cur"} to $v.") { Store.changeReelsAllow(v); render() }
        else { Store.changeReelsAllow(v); render() }
    }

    /** Under a dial, when a looser value chosen in Strict mode is waiting for tomorrow. */
    private fun pendingNote(key: String, label: (Int) -> String) {
        val next = Store.tomorrowValue(key) ?: return
        add(paragraph("From tomorrow: ${label(next)}. Today stays as it is.", 14f, P.accent))
    }

    private fun chips(values: IntArray, current: Int, label: (Int) -> String, onPick: (Int) -> Unit): View {
        val f = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(10)) }
        val all = ArrayList<Chip>()
        for (v in values) {
            val c = Chip(this, label(v)); c.on = v == current
            c.setOnClickListener { it.tick(); onPick(v) }
            all += c; f.addView(c)
        }
        return f
    }

    private fun timeLabel(min: Int): String {
        val c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, min / 60); c.set(Calendar.MINUTE, min % 60)
        return DateFormat.getTimeFormat(this).format(c.time)
    }

    private fun names(pkgs: Collection<String>, max: Int = 3): String {
        val n = pkgs.mapNotNull { Apps.byPkg(it)?.label }.sorted()
        if (n.isEmpty()) return "None"
        return n.take(max).joinToString(", ") + if (n.size > max) " +${n.size - max}" else ""
    }

    // ---------------------------------------------------------------------------------- main

    private fun pageMain() {
        header("Settings")
        val guardHealth = Launch.guardHealth(this)
        val guard = guardHealth != Launch.Health.OFF
        val home = Launch.isDefaultHome(this)
        if (!home || guardHealth != Launch.Health.ON) {
            val warn = vbox().apply {
                setPadding(dp(20), dp(16), dp(20), dp(16))
                background = com.poshith.intent.ui.rounded(P.surface, dp(18).toFloat())
            }
            warn.addView(text(when {
                !home -> "Intent isn't your home screen yet"
                guardHealth == Launch.Health.STOPPED -> "Focus Guard has stopped"
                else -> "Focus Guard is off"
            }, 17f, P.text, Fonts.medium(this)))
            warn.addView(text(when {
                !home -> "Set it as your home app so your phone opens to calm, chosen apps."
                guardHealth == Launch.Health.STOPPED -> "Its switch is still on, but Android stopped it, so pauses and session ends won't happen. Turning it off and on again fixes it."
                else -> "Without it, Intent only pauses apps you open from here. Turn it on to pause them from notifications and end sessions on time."
            }, 15f, P.text2).apply { setPadding(0, dp(6), 0, dp(10)) })
            warn.addView(button("Fix this", Kind.PRIMARY) { go("protection") }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(warn, lp().apply { leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8) })
        }
        add(sectionTitle("Your apps"))
        add(row("Strict mode", strictSummary(), null) { go("strict") })
        add(row("Home screen apps", "${Store.homeKeys.size} of ${Store.MAX_HOME} · order, names, layout", null) { go("home") })
        add(row("Essentials", "Never paused, even in focus: ${names(Store.packagesWithMode(Mode.ESSENTIAL))}", null) { go("essentials") })
        add(row("Apps I want to use less", "A short pause, then the minutes you choose: ${names(Store.packagesWithMode(Mode.MINDFUL))}", null) { go("mindful") })
        add(row("Check-in times", "Open only at set times: ${names(Store.packagesWithMode(Mode.BATCHED))}", null) { go("checkins") })
        add(row("Blocked", names(Store.packagesWithMode(Mode.BLOCKED)), null) { go("blocked") })
        add(row("Hidden from search", "${Store.hidden.size} hidden", null) { go("hidden") })
        add(row("Shorts and Reels", reelsSummary(), null) { go("reels") })
        add(sectionTitle("Your day"))
        add(row("Focus, wind-down and mornings", "Wind-down " + (if (Store.windDownOn) "${timeLabel(Store.windDownStart)}–${timeLabel(Store.windDownEnd)}" else "off") +
            " · morning quiet " + (if (Store.morningQuietMin > 0) "${Store.morningQuietMin} min" else "off"), null) { go("focus") })
        add(row("Your plan", (Store.urgePlan.ifBlank { null }?.let { "When I feel the pull: $it" } ?: "Not set") +
            if (Store.eveningOn) " · evening look-back on" else "", null) { go("plan") })
        add(sectionTitle("Your phone"))
        add(row("Protection and permissions", when (guardHealth) {
            Launch.Health.ON -> "Focus Guard on"
            Launch.Health.STOPPED -> "Focus Guard stopped: tap to fix"
            Launch.Health.OFF -> "Focus Guard off"
        }, null) { go("protection") })
        add(row("Quiet notifications", "Silence everything that isn't a person", null) { go("notifications") })
        add(row("Grayscale", "Make feeds duller, on purpose", null) { go("grayscale") })
        add(row("Gestures", null, null) { go("gestures") })
        add(row("Appearance", when (Store.theme) { "dark" -> "Dark"; "light" -> "Light"; else -> "Follows system" }, null) { go("appearance") })
        add(row("Rule changes", if (Store.coolOffSec > 0) "Wait ${Store.coolOffSec} s before making apps easier to open" else "Apply instantly", null) { ruleChangesSheet() })
        add(sectionTitle("Intent"))
        add(row("Privacy", "No internet permission. Nothing leaves your phone.", null) { go("privacy") })
        add(row("About and research", null, null) { go("about") })
        add(row("Change home app", "Switch to another launcher any time", null) { Launch.homeSettings(this) })
        add(row("Run setup again", null, null) {
            // Setup can change every rule at once, so it waits like any other loosening (and Strict mode
            // keeps it for tomorrow).
            loosen("Run setup again?", "It can change all your rules at once.") {
                startActivity(Intent(this, OnboardingActivity::class.java).putExtra(OnboardingActivity.EXTRA_RESTART, true))
            }
        })
    }

    private fun ruleChangesSheet() {
        val s = Sheet(this, "Rule changes", "Making an app harder to open always applies instantly. Making it easier can wait a few seconds, so a rule you set while calm isn't undone on impulse.")
        s.add(chips(intArrayOf(0, 10, 20, 30, 60), Store.coolOffSec, { if (it == 0) "Off" else "$it s" }) { v ->
            if (v < Store.coolOffSec) {
                s.dismiss()
                loosen("Shorten the wait?", "From ${Store.coolOffSec} s to ${if (v == 0) "none" else "$v s"}.") { Store.coolOffSec = v; render() }
            } else { Store.coolOffSec = v; s.dismiss(); render() }
        }, 0)
        s.show()
    }

    // ---------------------------------------------------------------------------------- home

    private fun pageHome() {
        header("Home screen", "Your few chosen apps, as words. Up to ${Store.MAX_HOME}; four or five is calmer. Fewer choices, faster decisions.")
        val keys = Store.homeKeys
        if (keys.isEmpty()) add(paragraph("No apps yet.", 15f, P.text3))
        keys.forEachIndexed { i, k ->
            val name = Apps.byKey(k)?.label ?: Store.cachedName(k) ?: k.substringBefore('/')
            val r = hbox().apply { setPadding(dp(24), dp(6), dp(12), dp(6)); minimumHeight = dp(56) }
            r.addView(text(name, 18f, P.text, lines = 1), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            fun mini(label: String, desc: String, enabled: Boolean, f: () -> Unit) = text(label, 18f, if (enabled) P.text2 else P.line).apply {
                gravity = Gravity.CENTER; minWidth = dp(48); minHeight = dp(48); contentDescription = desc
                if (enabled) { background = ripple(null, dp(24).toFloat()); isClickable = true; setOnClickListener { it.tick(); f() } }
            }
            r.addView(mini("↑", "Move $name up", i > 0) { val l = keys.toMutableList(); l.add(i - 1, l.removeAt(i)); Store.homeKeys = l; render() })
            r.addView(mini("↓", "Move $name down", i < keys.size - 1) { val l = keys.toMutableList(); l.add(i + 1, l.removeAt(i)); Store.homeKeys = l; render() })
            r.addView(mini("×", "Remove $name", true) { Store.homeKeys = keys - k; render() })
            add(r)
        }
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(10), dp(24), dp(4))
            addView(button("Choose apps", Kind.QUIET) { homePicker() })
        })
        add(sectionTitle("On the home screen"))
        add(toggleRow("Date", null, Store.showDate) { Store.showDate = it })
        add(toggleRow("Screen time", "Today's total under the date; tap it for the details (needs usage access)", Store.showStats) { Store.showStats = it })
        add(toggleRow("Today's one thing", "What matters today, above your apps. Tap it to plan, change it, or look back on the day.", Store.showOneThing) { Store.showOneThing = it })
        add(toggleRow("Check-in after unlocking", "“What did you pick up your phone for?” Never blocks anything.", Store.checkIn) { Store.checkIn = it })
        add(toggleRow("Lock if I don't answer", "No choice within 15 seconds and the phone locks again. Needs Focus Guard.", Store.lockIfNoAnswer) { Store.lockIfNoAnswer = it })
        add(row("Layout and text size", null, null) { go("appearance") })
    }

    private fun homePicker() {
        lateinit var ov: Overlay
        val picker = AppPicker(this, "Home screen apps", "Tap in the order you want them. Pick the tools you use every day.",
            Store.homeKeys, byPackage = false, max = Store.MAX_HOME, suggested = suggestedTools(keys = true)) { sel ->
            Store.homeKeys = sel; ov.close(); render()
        }
        ov = Overlay(this, picker.build()).show()
    }

    /** Dialer, messages, camera and known tools that are installed. */
    fun suggestedTools(keys: Boolean): Set<String> {
        val out = LinkedHashSet<String>()
        val prot = listOf(
            Launch.resolve(this, Intent(Intent.ACTION_DIAL)),
            Launch.resolve(this, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            Launch.resolve(this, Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).filterNotNull()
        for (p in prot + Catalog.TOOLS) Apps.byPkg(p)?.let { out += if (keys) it.key else it.pkg }
        return out
    }

    // ---------------------------------------------------------------------------------- mode pickers

    private fun protectedReason(e: AppEntry): String? = if (Launch.isProtected(this, e.pkg)) "Always available" else null

    private fun pagePicker(mode: Mode) {
        val (title, sub) = when (mode) {
            Mode.ESSENTIAL -> "Essentials" to "These open instantly, always, even during focus, breaks and wind-down. Think: calls, messages, maps, payments, authenticator, transport."
            Mode.BLOCKED -> "Blocked" to "These never open from Intent or (with Focus Guard) from anywhere else, unless you type a deliberate phrase for 5 minutes."
            else -> "Apps" to ""
        }
        header(title, sub)
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(6), dp(24), dp(6))
            addView(button("Choose apps", Kind.QUIET) { modePicker(mode, title, sub) })
        })
        listMode(mode)
    }

    private fun listMode(mode: Mode) {
        val pkgs = Store.packagesWithMode(mode)
        if (pkgs.isEmpty()) add(paragraph("None yet.", 15f, P.text3))
        for (p in pkgs.sortedBy { Apps.labelFor(this, it).lowercase() }) {
            add(row(Apps.labelFor(this, p), null, null, null))
        }
    }

    private fun modePicker(mode: Mode, title: String, sub: String, suggested: Set<String> = emptySet()) {
        val before = Store.packagesWithMode(mode)
        lateinit var ov: Overlay
        val picker = AppPicker(this, title, sub, before.toList(), byPackage = true, suggested = suggested,
            reasonDisabled = ::protectedReason) { sel ->
            val removed = before - sel.toSet()
            val added = sel.toSet() - before
            fun apply() {
                for (p in added) Store.setRule(p, if (mode == Mode.MINDFUL) Store.mindfulDefaults(Store.rule(p))
                    else Store.rule(p).copy(mode = mode, pauseSec = Store.defaultPause, sessionMin = Store.defaultSession))
                for (p in removed) Store.setMode(p, Mode.NORMAL)
                ov.close(); render()
            }
            // Anything that makes an app easier to open waits: taking it out of a stricter list
            // (back to normal), or adding it here from a stricter one (Blocked to Essential, say).
            val easier = removed.filter { Mode.NORMAL.isLooserThan(mode) } +
                added.filter { mode.isLooserThan(Store.rule(it).mode) }
            if (easier.isNotEmpty()) loosen("Make ${names(easier.toSet(), 2)} easier to open?",
                "They'll open with fewer limits than now.") { apply() }
            else apply()
        }
        ov = Overlay(this, picker.build()).show()
    }

    private fun pageMindful() {
        header("Apps I want to use less", "A short pause, and the minutes you choose. You can still open them, on purpose.")
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(6), dp(24), dp(6))
            addView(button("Choose apps", Kind.QUIET) {
                modePicker(Mode.MINDFUL, "Apps I want to use less", "Apps that pull you in. They'll pause first.", suggestedPull())
            })
        })
        listMode(Mode.MINDFUL)
        add(sectionTitle("Pause length"))
        add(chips(Rule.PAUSE_CHOICES, Store.defaultPause, { "$it s" }) { v -> changeDefaults(pause = v) })
        add(sectionTitle("Usual session"))
        add(chips(Rule.SESSION_CHOICES, Store.defaultSession, { "$it min" }) { v -> changeDefaults(session = v) })
        add(sectionTitle("Daily sessions per app"))
        add(chips(Rule.LIMIT_CHOICES, Store.defaultLimit, { if (it == 0) "No limit" else "$it" }) { v -> changeDefaults(limit = v) })
        add(toggleRow("Pause grows each time", "+2 s for every session after the second one today (max 20 s)", Store.defaultEscalate) { on -> changeDefaults(escalate = on) })
        // Across all of them: one budget, and a short rest after sessions close together.
        add(sectionTitle("One budget for all of them"))
        add(paragraph("Every session takes its minutes from one daily total, so moving from one app to another doesn't start over.", 14.5f, P.text3))
        add(chips(com.poshith.intent.core.Limits.BUDGET_CHOICES, Store.budgetMin, { if (it == 0) "Off" else "$it min" }) { v -> pickBudget(v) })
        pendingNote("budget_min") { if (it == 0) "no daily budget" else "$it min" }
        add(toggleRow("A short rest after 3 sessions in 10 minutes", "They wait 5 minutes, then open as usual", Store.restOn) { on ->
            if (!on) loosen("Turn off the short rest?", "Sessions close together won't get a rest.") { Store.restOn = false; render() }
            else { Store.restOn = true; render() }
        })
        add(toggleRow("Ask how I feel", "Naming the feeling is where the habit loop breaks", Store.askFeeling) { Store.askFeeling = it })
    }

    private fun changeDefaults(pause: Int? = null, session: Int? = null, limit: Int? = null, escalate: Boolean? = null) {
        val pkgs = Store.packagesWithMode(Mode.MINDFUL)
        val cur = Store.mindfulDefaults()
        val looser = (pause != null && pause < Store.defaultPause) ||
            (session != null && session > Store.defaultSession) ||
            (limit != null && (limit == 0 && cur.dailyOpenLimit > 0 || (limit > cur.dailyOpenLimit && cur.dailyOpenLimit > 0))) ||
            (escalate == false && cur.escalate)
        fun apply() {
            // Saved as the defaults too, so apps you make mindful later get the same rule.
            pause?.let { Store.defaultPause = it }
            session?.let { Store.defaultSession = it }
            limit?.let { Store.defaultLimit = it }
            escalate?.let { Store.defaultEscalate = it }
            for (p in pkgs) {
                var r = Store.rule(p)
                pause?.let { r = r.copy(pauseSec = it) }
                session?.let { r = r.copy(sessionMin = it) }
                limit?.let { r = r.copy(dailyOpenLimit = it) }
                escalate?.let { r = r.copy(escalate = it) }
                Store.setRule(p, r)
            }
            render()
        }
        if (looser) loosen("Make these apps easier to open?", "This loosens the rule for every app you want to use less.") { apply() } else apply()
    }

    fun suggestedPull(): Set<String> {
        val out = LinkedHashSet<String>()
        for (a in Apps.list) {
            if (a.pkg in Catalog.PULL || (a.category in Catalog.PULL_CATEGORIES && !Launch.isProtected(this, a.pkg) && a.pkg !in Catalog.TOOLS)) out += a.pkg
        }
        return out
    }

    private fun pageCheckIns() {
        header("Check-in times", "For email, work chat or news: they open only during your check-in windows. Checking email three times a day lowered stress in a trial (Kushlev & Dunn, 2015).")
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(6), dp(24), dp(6))
            addView(button("Choose apps", Kind.QUIET) { modePicker(Mode.BATCHED, "Check-in apps", "They'll open only at your check-in times.") })
        })
        listMode(Mode.BATCHED)
        add(sectionTitle("When"))
        val presets = listOf(
            listOf(12 * 60 + 30, 18 * 60 + 30) to "12:30 and 18:30",
            listOf(9 * 60, 13 * 60, 18 * 60) to "9:00, 13:00 and 18:00",
            listOf(18 * 60) to "Once, at 18:00",
        )
        val f = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(10)) }
        for ((times, label) in presets) {
            val c = Chip(this, times.joinToString(" · ") { timeLabel(it) }); c.on = Store.checkInStarts == times
            c.contentDescription = label
            c.setOnClickListener { it.tick(); changeCheckIns(times, Store.checkInLength) }
            f.addView(c)
        }
        add(f)
        add(sectionTitle("Window length"))
        add(chips(intArrayOf(15, 20, 30), Store.checkInLength, { "$it min" }) { v -> changeCheckIns(Store.checkInStarts, v) })
        if (Store.windDownOn) add(paragraph("Wind-down comes first: a check-in time inside it waits for the morning.", 14f, P.text3))
        add(sectionTitle("Their notifications"))
        val access = BreakQuietService.hasAccess(this)
        add(toggleRow("Hold them until check-in time", (if (access) "" else "Needs Notification access. ") +
            "They arrive in a few batches a day instead of a stream; nothing is deleted. Calls and alarms are never held. In a trial, batching non-urgent notifications three times a day improved well-being (Fitz et al., 2019).",
            Store.batchNotifications && access) { on ->
            if (on && !access) { Store.batchNotifications = true; BreakQuietService.requestAccess(this) }
            else if (!on) loosen("Let their notifications through again?", "They'll arrive as they come.") { Store.batchNotifications = false; render() }
            else { Store.batchNotifications = true; BreakQuietService.sweep(); render() }
        })
    }

    /** New check-in times or length: more (or moved) windows open these apps more, so they wait like any loosening. */
    private fun changeCheckIns(starts: List<Int>, length: Int) {
        fun apply() { Store.checkInStarts = starts; Store.checkInLength = length; render() }
        val looser = Store.packagesWithMode(Mode.BATCHED).isNotEmpty() &&
            Schedule.checkInsLooser(Store.checkIns, Schedule.checkIns(starts, length))
        if (looser) loosen("Open check-in apps more?", "They'd open at times they're closed now.") { apply() } else apply()
    }

    private fun pageHidden() {
        header("Hidden from search", "Out of sight, out of reach. Hidden apps don't show when you type. Uninstalling isn't needed.")
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(6), dp(24), dp(6))
            addView(button("Choose apps", Kind.QUIET) {
                lateinit var ov: Overlay
                val picker = AppPicker(this@SettingsActivity, "Hide from search", "Selected apps won't appear when you search.", Store.hidden.toList(), byPackage = false) { sel ->
                    val unhiding = Store.hidden - sel.toSet()
                    fun apply() { Store.hidden = sel.toSet(); ov.close(); render() }
                    if (unhiding.isNotEmpty()) loosen("Show these again?", "They'll appear in search again.") { apply() } else apply()
                }
                ov = Overlay(this@SettingsActivity, picker.build()).show()
            })
        })
        val hidden = Store.hidden
        if (hidden.isEmpty()) add(paragraph("Nothing hidden.", 15f, P.text3))
        for (k in hidden) add(row(Apps.byKey(k)?.label ?: Store.cachedName(k) ?: k.substringBefore('/'), null, null, null))
    }

    // ---------------------------------------------------------------------------------- day

    private fun pageFocus() {
        header("Focus, wind-down and mornings")
        add(sectionTitle("Morning quiet"))
        add(paragraph("The apps you want to use less rest for the first minutes after your first unlock of the morning, so your first thoughts are your own (Dr. Sahar Yousef's 30/15 rule). In Strict mode, a session that's running when it starts ends then.", 14.5f))
        add(chips(intArrayOf(0, 15, 30, 60), Store.morningQuietMin, { if (it == 0) "Off" else "$it min" }) { v ->
            if (v < Store.morningQuietMin) loosen("Shorten morning quiet?", "From ${Store.morningQuietMin} to ${if (v == 0) "off" else "$v min"}.") { Store.morningQuietMin = v; render() }
            else { Store.morningQuietMin = v; render() }
        })
        add(sectionTitle("Wind-down"))
        add(paragraph("At night, the apps you want to use less rest, and so do check-in apps. Screens late in the evening push sleep later (Chang et al., 2015). Alarms, calls and essentials still work.", 14.5f))
        add(toggleRow("Wind-down", null, Store.windDownOn) { on ->
            if (!on) loosen("Turn off wind-down?", "These apps will open at night again.") { Store.windDownOn = false; render() }
            else { Store.windDownOn = true; render() }
        })
        if (Store.windDownOn) {
            add(row("Starts", null, timeLabel(Store.windDownStart)) { pickTime(Store.windDownStart) { v -> changeWindDown(v, Store.windDownEnd) } })
            add(row("Ends", null, timeLabel(Store.windDownEnd)) { pickTime(Store.windDownEnd) { v -> changeWindDown(Store.windDownStart, v) } })
            add(toggleRow("Firm wind-down", "Normal apps rest too, and a session that's running ends when wind-down starts", Store.windDownStrict) { on ->
                if (!on) loosen("Make wind-down gentler?", "Normal apps will open at night, and a running session can finish.") { Store.windDownStrict = false; render() }
                else { Store.windDownStrict = true; render() }
            })
            add(paragraph(if (Store.windDownStrict || Store.strictActive()) "A session that's running when wind-down starts ends then. One you unlocked on purpose keeps its minutes."
                else "Gentle: a session that's running when wind-down starts can finish its minutes. Firm wind-down (or Strict mode) ends it then.", 14f, P.text3))
        }
        add(sectionTitle("Focus"))
        add(paragraph("Start focus or a screen-free break from the home screen (tap Focus at the bottom). Only essentials open until it ends.", 14.5f))
        add(row("Essentials", names(Store.packagesWithMode(Mode.ESSENTIAL)), null) { go("essentials") })
    }

    /** New wind-down hours: if they leave any minute it covers now uncovered, that's a loosening and it waits. */
    private fun changeWindDown(start: Int, end: Int) {
        fun apply() { Store.windDownStart = start; Store.windDownEnd = end; render() }
        if (Schedule.windDownLooser(Store.windDown, Window(start, end)))
            loosen("Shorten wind-down?", "Apps would open in some of the hours it covers now (${timeLabel(Store.windDownStart)}–${timeLabel(Store.windDownEnd)}).") { apply() }
        else apply()
    }

    private fun pickTime(current: Int, onPick: (Int) -> Unit) {
        val theme = if (P.dark) android.R.style.Theme_Material_Dialog else android.R.style.Theme_Material_Light_Dialog
        TimePickerDialog(this, theme, { _, h, m -> onPick(h * 60 + m) }, current / 60, current % 60, DateFormat.is24HourFormat(this)).show()
    }

    private fun pagePlan() {
        header("Your plan", "An if-then plan for the pull: “When I feel the pull, I'll…”. Planning the response in advance had a medium-to-large effect on reaching goals across 94 studies.")
        val ideas = listOf("take three slow breaths", "drink a glass of water", "stand up and stretch", "read one page of a book",
            "message someone I care about", "do my one thing for two minutes", "step outside for a minute")
        val f = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(10)) }
        for (i in ideas) {
            val c = Chip(this, i); c.on = Store.urgePlan == i
            c.setOnClickListener { it.tick(); Store.urgePlan = i; render() }
            f.addView(c)
        }
        add(f)
        val fld = field("Or write your own", if (Store.urgePlan in ideas) "" else Store.urgePlan)
        add(FrameLayout(this).apply { setPadding(dp(24), dp(6), dp(24), dp(6)); addView(fld) })
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(6), dp(24), dp(6))
            addView(button("Save my own", Kind.QUIET) { if (fld.text.isNotBlank()) { Store.urgePlan = fld.text.toString(); hideKeyboard(this@SettingsActivity, fld); render() } })
        })

        // The daily plan: optional, and only on this phone.
        add(sectionTitle("Your day"))
        add(paragraph("“What matters today?” on the home screen: one thing, the first small step and when, and if you like what might get in the way. Pairing a wish with its obstacle and a plan helps more than the wish alone.", 14.5f))
        add(toggleRow("Ask what matters today", "Until you answer, or say “Not today”", Store.planPrompt) { Store.planPrompt = it })
        add(row("My day starts at", "Today's plan lasts until then. Working nights? Set it to when you wake.", timeLabel(Store.dayStartMin)) {
            pickTime(Store.dayStartMin) { v -> Store.dayStartMin = v; render() }
        })
        add(toggleRow("Evening look-back", "Late in your day: did it happen, what helped, what pulled you away, tomorrow's first step", Store.eveningOn) { Store.eveningOn = it })
        val notes = Store.reflections
        if (notes.isNotEmpty()) {
            add(sectionTitle("Your look-backs"))
            add(paragraph("${notes.size} saved, only on this phone. Nobody else sees them.", 14.5f))
            for (r in notes.takeLast(3).asReversed()) add(row(r.thing.ifBlank { "A day" }, listOf(r.day, when (r.outcome) { "yes" -> "it happened"; "partly" -> "partly"; "no" -> "not today"; else -> "" },
                r.next.takeIf { it.isNotBlank() }?.let { "next: $it" } ?: "").filter { it.isNotBlank() }.joinToString(" · "), null, null))
            add(row("Export as text", "Share them to your notes, email or files", null) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, com.poshith.intent.ui.reflectionsText())
                Launch.tryStart(this, Intent.createChooser(send, "Export look-backs"), "Nothing to share with")
            })
            add(row("Delete them all", null, null) {
                val sh = com.poshith.intent.ui.Sheet(this, "Delete all look-backs?", "They're deleted from this phone for good.")
                sh.add(button("Delete", Kind.PRIMARY) { Store.deleteReflections(); sh.dismiss(); render() })
                sh.add(button("Keep them", Kind.PLAIN) { sh.dismiss() })
                sh.show()
            })
        }
    }

    // ---------------------------------------------------------------------------------- strict mode

    private fun strictSummary(): String = when {
        !Store.strictActive() -> "Off · no extensions, looser only from tomorrow"
        Store.strictOffDay.isNotEmpty() -> "On until midnight"
        Store.locked() && feedsClosedUntil() != null -> "On · Shorts and Reels closed until ${feedsClosedUntil()}"
        else -> "On · ${Store.reelsAllow} Shorts and Reels a day"
    }

    private fun timeLabelAt(ms: Long): String = DateFormat.getTimeFormat(this).format(java.util.Date(ms))

    private fun pageStrict() {
        header("Strict mode", "Limits you set while calm, held while you're craving. A pause with a “few more” button gets waited out; this one has no button to press.")
        val on = Store.strictActive()

        // On: how today is going comes first, where you can see it.
        if (on) {
            add(sectionTitle("Today"))
            val n = Store.stopsToday()
            add(paragraph(when {
                Store.locked() && feedsClosedUntil() != null -> "Shorts and Reels are closed until ${feedsClosedUntil()}. The rest of YouTube and Instagram works."
                n > 0 -> "Today's Shorts and Reels are used. ${com.poshith.intent.core.Strict.nextLockWords(n)}"
                Store.reelsAllowToday() == 0 -> "Shorts and Reels are off today. The rest of YouTube and Instagram works."
                else -> "About ${Stats.shortVideosToday()} of ${Store.reelsAllowToday()} Shorts and Reels so far today."
            }, 15f, P.text))
        }

        // The two dials: stricter applies now, looser from tomorrow.
        add(sectionTitle("Shorts and Reels a day"))
        add(chips(com.poshith.intent.core.Strict.SHORTS_CHOICES, Store.reelsAllow, { if (it == 0) "None" else "$it" }) { v -> pickReelsAllow(v) })
        pendingNote("reels_allow") { if (it == 0) "none" else "$it a day" }
        add(sectionTitle("Minutes a day for all apps you want to use less"))
        add(chips(com.poshith.intent.core.Limits.BUDGET_CHOICES, Store.budgetMin, { if (it == 0) "No limit" else "$it min" }) { v -> pickBudget(v) })
        pendingNote("budget_min") { if (it == 0) "no daily budget" else "$it min" }

        // Then: turn it on, or the ways out (tomorrow, or the emergency code).
        if (on) {
            add(sectionTitle("Strict mode"))
            if (Store.strictOffDay.isEmpty()) {
                add(row("Turn off Strict mode", "It stays on until midnight, so tomorrow's you decides.", null) {
                    Store.strictOffTomorrow(); render()
                })
            } else {
                add(row("Keep Strict mode on", "It's set to switch off at midnight.", null) { Store.strictOffDay = ""; render() })
            }
            if (Store.locked() && Store.strictCodeHash.isNotEmpty()) add(row("${com.poshith.intent.core.Strict.EMERGENCY_MORE} more today", "With your emergency code", null) {
                askEmergencyCode("It ends the lock and gives you ${com.poshith.intent.core.Strict.EMERGENCY_MORE} more today.") {
                    Store.lockUntil = 0; Store.addReelsBonus(com.poshith.intent.core.Strict.EMERGENCY_MORE); render()
                }
            })
            if (Store.strictCodeHash.isNotEmpty()) add(row("Turn it off now", "With your emergency code", null) {
                askEmergencyCode("It turns Strict mode off now.") { Store.strictOn = false; Store.strictOffDay = ""; render() }
            })
        } else {
            add(FrameLayout(this).apply {
                setPadding(dp(24), dp(18), dp(24), dp(6))
                addView(button("Turn on Strict mode", Kind.PRIMARY) { strictCodeSheet { render() } })
            })
            add(paragraph("Choose your numbers above while you're calm. Intent shows your emergency code once.", 14f, P.text3))
        }

        fun point(s: String) = add(text("•  $s", 15f, P.text2).apply { setLineSpacing(0f, 1.25f); setPadding(dp(24), dp(5), dp(24), dp(5)) })
        add(sectionTitle("How it works"))
        point("Shorts and Reels: a number a day (both together), no extensions. At the first swipe it says how many are left; past the last one they close until tomorrow. Only Shorts and Reels: messages, search and long videos keep working. (The count is estimated from scrolling.)")
        point("Go back to them after that and they bounce you out of the feed, for longer each time.")
        point("Nothing to wait out: the daily limit, budget, rest and quiet hours have no unlock buttons.")
        point("Morning quiet and wind-down end a session that's running, instead of letting it finish.")
        point("Rules only get looser from tomorrow. Stricter applies now.")
        point("One way past it: an emergency code, shown once. Keep it where it's hard to reach.")
        point("Calls, messages, maps, payments, Wi-Fi, Bluetooth and hotspot always work.")
    }


    // ---------------------------------------------------------------------------------- phone

    private fun status(ok: Boolean) = if (ok) "On" else "Off"

    private fun pageProtection() {
        header("Protection and permissions", "Each one is optional except the first. Intent works with whatever you allow, and tells you honestly what it can and can't do.")
        val home = Launch.isDefaultHome(this)
        add(row("Home screen", if (home) "Intent is your home app" else "Needed: otherwise your phone opens to your old launcher", if (home) "On" else "Set") {
            if (!home) Launch.requestHome(this) else Launch.homeSettings(this)
        })
        // Each switch says whether it's really working, not just whether it's switched on.
        fun healthRow(title: String, h: Launch.Health, on: String, off: String, open: () -> Unit) {
            val r = row(title, when (h) {
                Launch.Health.ON -> on
                Launch.Health.STOPPED -> "Switched on, but Android stopped it. Tap to fix."
                Launch.Health.OFF -> off
            }, when (h) { Launch.Health.ON -> "On"; Launch.Health.STOPPED -> "Stopped"; Launch.Health.OFF -> "Off" }) { open() }
            if (h == Launch.Health.STOPPED) (r.findViewWithTag<android.widget.TextView>("value"))?.setTextColor(P.warn)
            add(r)
        }
        healthRow("Focus Guard", Launch.guardHealth(this), "Pauses the apps you want to use less from anywhere, and ends sessions on time",
            "Off: pauses only apps opened from Intent; sessions end with a notification") { guardSheet(this) }
        healthRow("Shorts & Reels guard (experimental)", Launch.reelsHealth(this), "Steps in when you start swiping through Shorts or Reels; counts are estimates",
            "Off: Shorts and Reels play without a pause (whole-app sessions still work)") { reelsSheet(this) }
        healthRow("Notification access", Launch.quietHealth(this), "Real breaks hold notifications (calls still ring), and a closed app's video is paused, never your music",
            "Off: notifications come in during a real break, and a video in a small window may keep playing after Intent closes its app") {
            if (!BreakQuietService.hasAccess(this)) BreakQuietService.requestAccess(this)
            else if (!Dnd.hasAccess(this)) Dnd.requestAccess(this)
            else BreakQuietService.requestAccess(this) // stopped: switching it off and on reconnects it
        }
        val usage = Usage.hasAccess(this)
        add(row("Usage access", if (usage) "Honest unlock counts and minutes, computed on the phone" else "Optional: for unlock counts and minutes", status(usage)) { usageSheet() })
        if (Build.VERSION.SDK_INT >= 33) {
            val n = Reminder.canNotify(this)
            add(row("Notifications", "Only “time's up” reminders when Focus Guard is off", status(n)) {
                if (!n) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
                else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            })
        }
        add(sectionTitle("What Focus Guard can and can't do"))
        add(paragraph("It learns only which app is in front (its package name). It can't read your screen, messages or passwords, can't go online (Intent has no internet permission), and you can switch it off any time in Android's accessibility settings. Some phones limit background apps; if pauses stop appearing, allow Intent to run without battery restrictions.", 14.5f))
        add(row("Battery settings for Intent", null, null) { Launch.ownAppInfo(this) })
    }

    // ---------------------------------------------------------------------------------- shorts and reels

    private fun reelsSummary(): String = when {
        !ReelsGuardService.isOn(this) -> "Off · needs one switch in Android settings"
        !Store.reelsOn -> "Paused"
        else -> (if (Store.reelsAllow == 0) "The swipe always stops you" else "Asks at the first swipe, then every ${Store.reelsAllow}") +
            " · about ${Stats.shortVideosToday()} today"
    }

    private fun pageReels() {
        header("Shorts and Reels", "Experimental. The one you open is fine; the endless swipe is the trap. When you start swiping through YouTube Shorts or Instagram Reels, Intent steps in with a question and a big button to close the app. It tells swipes apart by the names of the views that scroll, so counts are estimates and an app update can confuse it. Timed sessions for the whole app (Apps I want to use less) are the dependable part.")
        val on = ReelsGuardService.isOn(this)
        if (!on) {
            val card = vbox().apply {
                setPadding(dp(20), dp(16), dp(20), dp(16))
                background = com.poshith.intent.ui.rounded(P.surface, dp(18).toFloat())
            }
            card.addView(text("One switch in Android settings", 17f, P.text, Fonts.medium(this)))
            card.addView(text("Turn on “Intent · Shorts & Reels” in Accessibility. It's separate from Focus Guard, so you can have either or both.", 15f, P.text2).apply {
                setLineSpacing(0f, 1.2f); setPadding(0, dp(6), 0, dp(10))
            })
            card.addView(button("Turn it on", Kind.PRIMARY) { reelsSheet(this) }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(card, lp().apply { leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8) })
        }
        add(row("Step in when I swipe", if (Store.reelsOn) null else "Paused: Shorts and Reels play as usual", if (Store.reelsOn) "On" else "Off") {
            if (Store.reelsOn) loosen("Pause it?", "Shorts and Reels would play without stopping you.", "Pause it") { Store.reelsOn = false; render() }
            else { Store.reelsOn = true; render() }
        })
        add(sectionTitle("After the first swipe"))
        add(paragraph("The first swipe always asks. If you choose to keep watching, how many before it asks again? “None” means the swipe always stops you.", 14.5f))
        fun label(v: Int) = if (v == 0) "None" else "$v"
        if (Store.strictActive()) {
            add(row("Strict mode is on", "Today's number (${Store.reelsAllow}) and when it changes are on the Strict mode page.", null) { go("strict") })
        } else add(chips(intArrayOf(0, 5, 10, 20), Store.reelsAllow, { label(it) }) { v -> pickReelsAllow(v) })
        add(toggleRow("Blunt lines", "A short, blunt line on the pause (“Close it. You already know.”). Off by default: shame tends to backfire.", Store.bluntLines) { Store.bluntLines = it })
        add(sectionTitle("Today"))
        add(paragraph("About ${Stats.shortVideosToday(Feed.SHORTS)} Shorts · about ${Stats.shortVideosToday(Feed.REELS)} Reels (estimated)", 16f, P.text))
        add(sectionTitle("What it can and can't see"))
        add(paragraph("Android tells it only about scrolling and screen changes inside YouTube and Instagram. Android lets this kind of service look at those two apps' screens; Intent uses that only to read the names of views (like “reel_recycler”) and their size, to tell the Shorts or Reels player from comments or the home feed. It never reads text, can't see other apps, and Intent has no internet permission. Focus Guard is different: it can't look at any screen at all, only which app is in front.", 14.5f))
        if (on) add(row("Turn it off in Android settings", null, null) { reelsSheet(this) })
    }

    private fun usageSheet() {
        val s = Sheet(this, "Usage access", "Lets Intent read Android's own usage events to show today's unlocks and minutes in the apps you want to use less. Computed on your phone, never stored elsewhere or sent anywhere.")
        s.add(button("Open usage access", Kind.PRIMARY) { s.dismiss(); Launch.usageSettings(this) })
        s.show()
    }

    private fun pageNotifications() {
        header("Quiet notifications", "Keep people and urgent tools. Silence the rest. Even an alert you ignore pulls at your attention (Stothart et al., 2015).")
        add(row("Do Not Disturb exceptions", "Let only favourite people and repeat callers through", null) {
            if (!Launch.tryStart(this, Intent("android.settings.ZEN_MODE_PRIORITY_SETTINGS"), null))
                Launch.tryStart(this, Intent(Settings.ACTION_SOUND_SETTINGS), "Open Settings > Sound > Do Not Disturb")
        })
        add(paragraph("Tap an app to open its notification settings, switch off what isn't a person, and come back. Intent doesn't read or filter your notifications; Android does the silencing.", 14.5f, P.text3))
        val reviewed = Store.notifReviewed
        val flagged = (Store.packagesWithMode(Mode.MINDFUL) + Store.packagesWithMode(Mode.BATCHED) + Store.packagesWithMode(Mode.BLOCKED) + suggestedPull())
            .filter { Apps.isInstalled(it) }.distinct()
        add(sectionTitle("Suggested to silence"))
        if (flagged.isEmpty()) add(paragraph("Nothing here yet. Choose the apps you want to use less first.", 15f, P.text3))
        for (p in flagged.sortedBy { Apps.labelFor(this, it).lowercase() }) notifRow(p, p in reviewed)
        add(sectionTitle("Usually worth keeping"))
        add(paragraph("Calls, messages from people, one-time passwords, payments, calendar, navigation.", 14.5f))
        val others = Apps.list.distinctBy { it.pkg }.map { it.pkg }.filter { it !in flagged }
        add(sectionTitle("Everything else"))
        for (p in others.sortedBy { Apps.labelFor(this, it).lowercase() }) notifRow(p, p in reviewed)
    }

    private fun notifRow(pkg: String, done: Boolean) {
        add(row(Apps.labelFor(this, pkg), null, if (done) "Reviewed" else "Open") {
            Store.notifReviewed = Store.notifReviewed + pkg
            Launch.notificationSettings(this, pkg)
        })
    }

    private fun pageGrayscale() {
        header("Grayscale", "Without color, feeds lose some of their pull. In a field experiment, grayscale cut screen time right away (Zimmermann & Sobolev, 2023). Android controls this, so Intent shows you where it is.")
        add(paragraph("Most phones: Settings › Digital Wellbeing › Bedtime mode › Grayscale. Others: Settings › Accessibility › Color correction › Grayscale. Some let you add a Grayscale tile to quick settings.", 15f))
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(10), dp(24), dp(6))
            addView(button("Open Digital Wellbeing", Kind.QUIET) { Launch.digitalWellbeing(this@SettingsActivity) })
        })
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(6), dp(24), dp(6))
            addView(button("Open accessibility settings", Kind.PLAIN) { Launch.accessibilitySettings(this@SettingsActivity) })
        })
        add(paragraph("Intent's own screens are already calm: no badges, one accent color, no red dots.", 14f, P.text3))
    }

    private fun pageGestures() {
        header("Gestures")
        add(toggleRow("Swipe right: Practice", "A one-minute reset, and short lessons from research", Store.swipeRightMind) { Store.swipeRightMind = it })
        val left = Store.swipeLeftKey.takeIf { it.isNotBlank() }?.let { Apps.byKey(it)?.label }
        add(row("Swipe left", "Opens an app (through its rules). Without one, it opens search.", left ?: "Search") {
            lateinit var ov: Overlay
            val picker = AppPicker(this, "Swipe left opens", "Pick one app, or none for search.", listOfNotNull(Store.swipeLeftKey.takeIf { it.isNotBlank() }),
                byPackage = false, max = 1) { sel -> Store.swipeLeftKey = sel.firstOrNull() ?: ""; ov.close(); render() }
            ov = Overlay(this, picker.build()).show()
        })
        add(toggleRow("Double-tap to lock", "Uses Focus Guard (Android 9+)", Store.doubleTapLock) { Store.doubleTapLock = it })
        add(toggleRow("Open single search result", "Typing an exact name opens it right away", Store.autoOpenSingle) { Store.autoOpenSingle = it })
        add(paragraph("Swipe up: search. Swipe down: notifications and quick settings (Wi-Fi, Bluetooth, hotspot, mobile data). Long-press: edit. Tap the clock: alarms. Tap the date: calendar.", 14.5f, P.text3))
    }

    private fun pageAppearance() {
        header("Appearance")
        add(sectionTitle("Home screen background"))
        val bg = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(4)) }
        for ((v, label) in listOf(true to "Your wallpaper", false to "Plain")) {
            val c = Chip(this, label); c.on = Store.wallpaper == v
            c.setOnClickListener { it.tick(); Store.wallpaper = v; render() }
            bg.addView(c)
        }
        add(bg)
        add(paragraph("On your wallpaper, the words turn dark or light to stay easy to read. Change the wallpaper itself in your phone's settings.", 14f, P.text3))
        if (Store.wallpaper) {
            add(text("Dim the wallpaper", 16f, P.text).apply { setPadding(dp(24), dp(12), dp(24), dp(2)) })
            val dim = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(6)) }
            for ((v, label) in listOf(0 to "Off", 35 to "A little", 60 to "More")) {
                val c = Chip(this, label); c.on = Store.wallpaperDim == v
                c.setOnClickListener { it.tick(); Store.wallpaperDim = v; render() }
                dim.addView(c)
            }
            add(dim)
        }
        add(toggleRow("Status bar on the home screen", "Off for a calmer screen. Swipe down still opens notifications.", Store.statusBar) { Store.statusBar = it })
        add(toggleRow("A quote a day", "One line on the home screen, new each morning. Tap it to see where it's from, or to pin it for a week.", Store.showQuote) { Store.showQuote = it; render() })
        if (Store.showQuote && Store.quotePinnedUntil > System.currentTimeMillis()) add(row("Unpin today's quote", "Pinned until ${DateFormat.getDateFormat(this).format(java.util.Date(Store.quotePinnedUntil))}", null) {
            Store.quotePinned = -1; Store.quotePinnedUntil = 0L; render()
        })
        add(sectionTitle("Theme"))
        val themes = listOf("system" to "Follow system", "dark" to "Dark", "light" to "Light")
        val tf = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(10)) }
        for ((k, label) in themes) {
            val c = Chip(this, label); c.on = Store.theme == k
            c.setOnClickListener { it.tick(); Store.theme = k; recreate() }
            tf.addView(c)
        }
        add(tf)
        add(sectionTitle("Font"))
        val ff = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(10)) }
        for ((k, label) in listOf("inter" to "Inter", "system" to "Phone's font")) {
            val c = Chip(this, label); c.on = Store.font == k
            c.setOnClickListener { it.tick(); Store.font = k; recreate() }
            ff.addView(c)
        }
        add(ff)
        add(sectionTitle("Alignment"))
        val af = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(10)) }
        for ((k, label) in listOf("start" to "Left", "center" to "Center", "end" to "Right")) {
            val c = Chip(this, label); c.on = Store.align == k
            c.setOnClickListener { it.tick(); Store.align = k; render() }
            af.addView(c)
        }
        add(af)
        add(sectionTitle("Text size"))
        val sizes = listOf(0.85f to "Smaller", 1f to "Normal", 1.15f to "Larger", 1.3f to "Largest")
        val sf = Flow(this).apply { setPadding(dp(24), dp(4), dp(24), dp(10)) }
        for ((v, label) in sizes) {
            val c = Chip(this, label); c.on = Math.abs(Store.textScale - v) < 0.01f
            c.setOnClickListener { it.tick(); Store.textScale = v; render() }
            sf.addView(c)
        }
        add(sf)
        add(paragraph("Android's own font size and display size settings also apply.", 14f, P.text3))
    }

    // ---------------------------------------------------------------------------------- intent

    private fun pagePrivacy() {
        header("Privacy")
        add(paragraph("Intent has no internet permission. It cannot send anything anywhere, and there are no accounts, ads, analytics or trackers.\n\n" +
            "Everything it knows (your rules, sessions, and simple daily counters like how often you chose “Not now”) stays in a private file on this phone. Uninstalling Intent deletes it.\n\n" +
            "Focus Guard, if you turn it on, sees only which app is in front, by package name. It can't read what's on your screen.\n\n" +
            "Usage access, if you allow it, is read on the phone to count unlocks and minutes. Nothing is stored apart from those counts.\n\n" +
            "The code is open source, so anyone can check all of this.", 16f, P.text))
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(16), dp(24), dp(6))
            addView(button("Read the code on GitHub", Kind.QUIET) { Launch.tryStart(this@SettingsActivity, Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB)), "No browser found") })
        })
        col.addView(divider().apply { (layoutParams as LinearLayout.LayoutParams).setMargins(dp(24), dp(18), dp(24), dp(6)) })
        add(row("Erase everything", "Deletes all of Intent's data on this phone and starts setup again", null) {
            loosen("Erase everything?", "Your rules, home apps, plans and counters will be deleted. This removes every pause too, so it waits like any other loosening.", "Erase") {
                for (pkg in Store.sessions { it.activePackages(android.os.SystemClock.elapsedRealtime()) }) Reminder.cancel(this, pkg)
                com.poshith.intent.guard.Break.end(this) // gives Do Not Disturb back if a break had it
                Store.eraseAll()
                startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        })
    }

    private fun pageAbout() {
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" }
        header("About Intent", "Version $version · open source · made by Poshith Nandyala")
        add(paragraph("Intent is built on what research says actually changes phone habits:\n\n" +
            "• Most pickups are habits you start yourself, not notifications (Heitmayer & Lahlou, 2021), so Intent works at the moment you reach.\n" +
            "• A short pause with an easy way out cut app openings by more than half (Grüning et al., PNAS 2023), so “Not now” is always the biggest button.\n" +
            "• People check about twice as often as they think (Andrews et al., 2015), so the numbers are honest.\n" +
            "• Rules set while calm work better than willpower in the moment (Allcott et al., 2022), so loosening a rule waits.\n" +
            "• Essentials stay instant, because rules that are too strict get abandoned (Castelo et al., 2025).\n\n" +
            "No app can promise a better brain. What Intent can do is make the thing you intended easy, and the thing you didn't intend a little slower.", 15.5f, P.text))
        add(row("Practice", "A one-minute reset and ${com.poshith.intent.core.Lessons.all.size} short lessons, each with its source: swipe right on the home screen", null, null))
        add(row("Code and research notes", GITHUB.removePrefix("https://"), null) {
            Launch.tryStart(this, Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB)), "No browser found")
        })
        add(row("Fonts", "Inter by Rasmus Andersson, SIL Open Font License 1.1", null, null))
        add(row("Inspired by", "Olauncher's text home screen and one sec's pause (ideas only; no code copied)", null, null))
    }
}

/** Focus Guard explanation with Android's "restricted settings" help for apps installed from a file. */
/**
 * Turning Strict mode on: a new emergency code, shown once. Only its hash is kept, so after this
 * sheet nobody (not even Intent) can show it again.
 */
fun BaseActivity.strictCodeSheet(onOn: () -> Unit) {
    val code = com.poshith.intent.core.Strict.newCode(java.security.SecureRandom())
    val s = Sheet(this, "Your emergency code", "The only way past Strict mode today. Write it on paper and keep it in another room, or send it to a friend and delete the message. Intent never shows it again.")
    s.add(text(code, 34f, P.text, Fonts.medium(this)).apply {
        gravity = android.view.Gravity.CENTER; letterSpacing = 0.12f; setPadding(0, dp(10), 0, dp(10))
        setTextIsSelectable(false)
    })
    s.add(button("I wrote it down. Turn on Strict mode", Kind.PRIMARY) {
        Store.strictCodeHash = com.poshith.intent.core.Strict.hash(code)
        Store.strictOffDay = ""
        Store.strictOn = true
        s.dismiss()
        onOn()
    })
    s.add(button("Not now", Kind.PLAIN) { s.dismiss() })
    s.show()
}

/** Shown at the top of a service's sheet when its switch is on but Android stopped it. */
private fun stoppedNote(a: BaseActivity, s: Sheet, name: String) {
    s.add(a.text("$name has stopped. Its switch is still on, but Android stopped it (some phones do after an update, or to save battery). In the list, turn “Intent · $name” off and on again. To keep it running, let Intent run without battery restrictions.", 15f, P.warn).apply { setLineSpacing(0f, 1.25f) }, 24)
    s.add(a.button("Battery settings for Intent", Kind.PLAIN) { Launch.ownAppInfo(a) })
}

fun reelsSheet(a: BaseActivity) {
    val s = Sheet(a, "Shorts & Reels guard", "Steps in when you start swiping through YouTube Shorts or Instagram Reels, and counts them.")
    if (Launch.reelsHealth(a) == Launch.Health.STOPPED) stoppedNote(a, s, "Shorts & Reels")
    s.add(a.text("What it uses: Android's accessibility service, told only about scrolling inside YouTube and Instagram. It looks at view names, like “reel_recycler”, never at text.\n\nWhat it can't do: read your messages or anything you type, see other apps, or go online. Intent has no internet permission.", 15f, P.text2).apply { setLineSpacing(0f, 1.25f) }, 24)
    s.add(a.button("Open accessibility settings", Kind.PRIMARY) { s.dismiss(); Launch.accessibilitySettings(a) })
    s.add(a.text("In the list, choose “Intent · Shorts & Reels” and turn it on.\n\nIf Android says the setting is restricted (it does this for apps installed from a file): open Intent's App info, tap ⋮ at the top right, choose “Allow restricted settings”, then try again.", 14f, P.text3).apply { setLineSpacing(0f, 1.25f) }, 24)
    s.add(a.button("Open Intent's App info", Kind.PLAIN) { Launch.ownAppInfo(a) })
    if (Launch.isXiaomi) {
        s.add(a.text("On Xiaomi, Redmi and POCO phones, also allow “Display pop-up windows while running in the background” in Intent's permissions, or the pause can't appear over YouTube or Instagram.", 14f, P.text3).apply { setLineSpacing(0f, 1.25f) }, 24)
        s.add(a.button("Open Xiaomi permissions", Kind.QUIET) { Launch.xiaomiPermissions(a) })
    }
    s.show()
}

fun guardSheet(a: BaseActivity) {
    val s = Sheet(a, "Focus Guard", "Lets Intent show your pause even when an app you want to use less is opened from a notification or the recents screen, and end the sessions you choose on time.")
    if (Launch.guardHealth(a) == Launch.Health.STOPPED) stoppedNote(a, s, "Focus Guard")
    s.add(a.text("What it uses: Android's accessibility service, only to learn which app is in front (its package name).\n\nWhat it can't do: read your screen, messages or passwords, see notifications, or go online. Intent has no internet permission.", 15f, P.text2).apply { setLineSpacing(0f, 1.25f) }, 24)
    s.add(a.button("Open accessibility settings", Kind.PRIMARY) { s.dismiss(); Launch.accessibilitySettings(a) })
    s.add(a.text("In the list, choose “Intent · Focus Guard” and turn it on.\n\nIf Android says the setting is restricted (it does this for apps installed from a file): open Intent's App info, tap ⋮ at the top right, choose “Allow restricted settings”, then try again.", 14f, P.text3).apply { setLineSpacing(0f, 1.25f) }, 24)
    s.add(a.button("Open Intent's App info", Kind.PLAIN) { Launch.ownAppInfo(a) })
    if (Launch.isXiaomi) {
        s.add(a.text("On Xiaomi, Redmi and POCO phones, one more switch: in Intent's permissions, allow “Display pop-up windows while running in the background”. Without it, the pause can't appear over other apps.", 14f, P.text3).apply { setLineSpacing(0f, 1.25f) }, 24)
        s.add(a.button("Open Xiaomi permissions", Kind.QUIET) { Launch.xiaomiPermissions(a) })
    }
    s.show()
}
