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
import com.poshith.intent.core.Mode
import com.poshith.intent.core.Rule
import com.poshith.intent.data.AppEntry
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage
import com.poshith.intent.guard.Launch
import com.poshith.intent.guard.Reminder
import com.poshith.intent.ui.AppPicker
import com.poshith.intent.ui.BaseActivity
import com.poshith.intent.ui.Chip
import com.poshith.intent.ui.Flow
import com.poshith.intent.ui.Fonts
import com.poshith.intent.ui.Kind
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = vbox()
        val sc = scroller(col)
        host.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onBars { b -> col.setPadding(b.left, b.top + dp(20), b.right, b.bottom + dp(28)) }
        pages += intent.getStringExtra(EXTRA_PAGE) ?: "main"
        render()
    }

    override fun onResume() {
        super.onResume()
        if (recreating) return
        render() // permissions may have changed in Android settings
    }

    override fun onBackRoot() {
        if (pages.size > 1) { pages.removeAt(pages.size - 1); render() } else finish()
    }

    private fun go(page: String) { pages += page; render() }

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
            setPadding(dp(24), dp(4), dp(24), dp(4)); minHeight = dp(44); gravity = Gravity.CENTER_VERTICAL
            background = ripple(null); isClickable = true; setOnClickListener { it.tick(); handleBack() }
        }
        col.addView(back, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(text(title, 32f, P.text, Fonts.light(this)).apply { setPadding(dp(24), dp(8), dp(24), dp(4)) }, lp())
        if (sub != null) col.addView(paragraph(sub), lp())
    }

    private fun add(v: View) = col.addView(v, lp())

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
        val guard = Launch.isGuardOn(this)
        if (!Launch.isDefaultHome(this) || !guard) {
            val warn = vbox().apply {
                setPadding(dp(20), dp(16), dp(20), dp(16))
                background = com.poshith.intent.ui.rounded(P.surface, dp(18).toFloat())
            }
            warn.addView(text(if (!Launch.isDefaultHome(this)) "Intent isn't your home screen yet" else "Focus Guard is off", 17f, P.text, Fonts.medium(this)))
            warn.addView(text(if (!Launch.isDefaultHome(this)) "Set it as your home app so your phone opens to calm, chosen apps."
                else "Without it, Intent only pauses apps you open from here. Turn it on to pause them from notifications and end sessions on time.", 15f, P.text2).apply { setPadding(0, dp(6), 0, dp(10)) })
            warn.addView(button("Fix this", Kind.PRIMARY) { go("protection") }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
            col.addView(warn, lp().apply { leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8) })
        }
        add(sectionTitle("Your apps"))
        add(row("Home screen apps", "${Store.homeKeys.size} of ${Store.MAX_HOME} · order, names, layout", null) { go("home") })
        add(row("Essentials", "Never paused, even in focus: ${names(Store.packagesWithMode(Mode.ESSENTIAL))}", null) { go("essentials") })
        add(row("Mindful apps", "Pause first, then a session you choose: ${names(Store.packagesWithMode(Mode.MINDFUL))}", null) { go("mindful") })
        add(row("Check-in times", "Open only at set times: ${names(Store.packagesWithMode(Mode.BATCHED))}", null) { go("checkins") })
        add(row("Blocked", names(Store.packagesWithMode(Mode.BLOCKED)), null) { go("blocked") })
        add(row("Hidden from search", "${Store.hidden.size} hidden", null) { go("hidden") })
        add(sectionTitle("Your day"))
        add(row("Focus, wind-down and mornings", "Wind-down " + (if (Store.windDownOn) "${timeLabel(Store.windDownStart)}–${timeLabel(Store.windDownEnd)}" else "off") +
            " · morning quiet " + (if (Store.morningQuietMin > 0) "${Store.morningQuietMin} min" else "off"), null) { go("focus") })
        add(row("Your plan for the pull", Store.urgePlan.ifBlank { "Not set" }, null) { go("plan") })
        add(sectionTitle("Your phone"))
        add(row("Protection and permissions", if (guard) "Focus Guard on" else "Focus Guard off", null) { go("protection") })
        add(row("Quiet notifications", "Silence everything that isn't a person", null) { go("notifications") })
        add(row("Grayscale", "Make feeds duller, on purpose", null) { go("grayscale") })
        add(row("Gestures", null, null) { go("gestures") })
        add(row("Appearance", when (Store.theme) { "dark" -> "Dark"; "light" -> "Light"; else -> "Follows system" }, null) { go("appearance") })
        add(row("Rule changes", if (Store.coolOffSec > 0) "Wait ${Store.coolOffSec} s before making apps easier to open" else "Apply instantly", null) { ruleChangesSheet() })
        add(sectionTitle("Intent"))
        add(row("Privacy", "No internet permission. Nothing leaves your phone.", null) { go("privacy") })
        add(row("About and research", null, null) { go("about") })
        add(row("Change home app", "Switch to another launcher any time", null) { Launch.homeSettings(this) })
        add(row("Run setup again", null, null) { startActivity(Intent(this, OnboardingActivity::class.java)) })
    }

    private fun ruleChangesSheet() {
        val s = Sheet(this, "Rule changes", "Making an app harder to open always applies instantly. Making it easier can wait a few seconds, so a rule you set while calm isn't undone on impulse.")
        s.add(chips(intArrayOf(0, 10, 20, 30, 60), Store.coolOffSec, { if (it == 0) "Off" else "$it s" }) { v ->
            if (v < Store.coolOffSec) {
                s.dismiss()
                confirmLoosen("Shorten the wait?", "From ${Store.coolOffSec} s to ${if (v == 0) "none" else "$v s"}.") { Store.coolOffSec = v; render() }
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
        add(toggleRow("Today's numbers", "Unlocks and time in mindful apps (needs usage access)", Store.showStats) { Store.showStats = it })
        add(toggleRow("Today's one thing", "One goal in view above your apps", Store.showOneThing) { Store.showOneThing = it })
        add(toggleRow("Check-in after unlocking", "“What did you pick up your phone for?” Never blocks anything.", Store.checkIn) { Store.checkIn = it })
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
                for (p in added) Store.setRule(p, Store.rule(p).copy(mode = mode, pauseSec = Store.defaultPause, sessionMin = Store.defaultSession))
                for (p in removed) Store.setMode(p, Mode.NORMAL)
                ov.close(); render()
            }
            // Anything that makes an app easier to open waits: taking it out of a stricter list
            // (back to normal), or adding it here from a stricter one (Blocked to Essential, say).
            val easier = removed.filter { Mode.NORMAL.isLooserThan(mode) } +
                added.filter { mode.isLooserThan(Store.rule(it).mode) }
            if (easier.isNotEmpty()) confirmLoosen("Make ${names(easier.toSet(), 2)} easier to open?",
                "They'll open with fewer limits than now.") { apply() }
            else apply()
        }
        ov = Overlay(this, picker.build()).show()
    }

    private fun pageMindful() {
        header("Mindful apps", "A short breath, a question, and a session length you choose. You can still open them, on purpose.")
        add(FrameLayout(this).apply {
            setPadding(dp(24), dp(6), dp(24), dp(6))
            addView(button("Choose mindful apps", Kind.QUIET) {
                modePicker(Mode.MINDFUL, "Mindful apps", "Apps that pull you in. They'll pause first.", suggestedPull())
            })
        })
        listMode(Mode.MINDFUL)
        add(sectionTitle("Pause length"))
        add(chips(Rule.PAUSE_CHOICES, Store.defaultPause, { "$it s" }) { v -> changeDefaults(pause = v) })
        add(sectionTitle("Usual session"))
        add(chips(Rule.SESSION_CHOICES, Store.defaultSession, { "$it min" }) { v -> changeDefaults(session = v) })
        add(sectionTitle("Daily sessions per app"))
        val limit = Store.packagesWithMode(Mode.MINDFUL).firstOrNull()?.let { Store.rule(it).dailyOpenLimit } ?: 0
        add(chips(Rule.LIMIT_CHOICES, limit, { if (it == 0) "No limit" else "$it" }) { v -> changeDefaults(limit = v) })
        val esc = Store.packagesWithMode(Mode.MINDFUL).firstOrNull()?.let { Store.rule(it).escalate } ?: true
        add(toggleRow("Pause grows each time", "+2 s for every session after the second one today (max 20 s)", esc) { on -> changeDefaults(escalate = on) })
        add(toggleRow("Ask how I feel", "Naming the feeling is where the habit loop breaks", Store.askFeeling) { Store.askFeeling = it })
    }

    private fun changeDefaults(pause: Int? = null, session: Int? = null, limit: Int? = null, escalate: Boolean? = null) {
        val pkgs = Store.packagesWithMode(Mode.MINDFUL)
        val cur = pkgs.firstOrNull()?.let { Store.rule(it) } ?: Rule(Mode.MINDFUL, Store.defaultPause, Store.defaultSession)
        val looser = (pause != null && pause < Store.defaultPause) ||
            (session != null && session > Store.defaultSession) ||
            (limit != null && (limit == 0 && cur.dailyOpenLimit > 0 || (limit > cur.dailyOpenLimit && cur.dailyOpenLimit > 0))) ||
            (escalate == false && cur.escalate)
        fun apply() {
            pause?.let { Store.defaultPause = it }
            session?.let { Store.defaultSession = it }
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
        if (looser) confirmLoosen("Make mindful apps easier to open?", "This loosens the rule for every mindful app.") { apply() } else apply()
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
            c.setOnClickListener { it.tick(); Store.checkInStarts = times; render() }
            f.addView(c)
        }
        add(f)
        add(sectionTitle("Window length"))
        add(chips(intArrayOf(15, 20, 30), Store.checkInLength, { "$it min" }) { v -> Store.checkInLength = v; render() })
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
                    if (unhiding.isNotEmpty()) confirmLoosen("Show these again?", "They'll appear in search again.") { apply() } else apply()
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
        add(paragraph("Mindful apps rest for the first minutes after your first unlock of the morning, so your first thoughts are your own (Dr. Sahar Yousef's 30/15 rule).", 14.5f))
        add(chips(intArrayOf(0, 15, 30, 60), Store.morningQuietMin, { if (it == 0) "Off" else "$it min" }) { v ->
            if (v < Store.morningQuietMin) confirmLoosen("Shorten morning quiet?", "From ${Store.morningQuietMin} to ${if (v == 0) "off" else "$v min"}.") { Store.morningQuietMin = v; render() }
            else { Store.morningQuietMin = v; render() }
        })
        add(sectionTitle("Wind-down"))
        add(paragraph("At night, mindful apps rest. Screens late in the evening push sleep later (Chang et al., 2015). Alarms, calls and essentials still work.", 14.5f))
        add(toggleRow("Wind-down", null, Store.windDownOn) { on ->
            if (!on) confirmLoosen("Turn off wind-down?", "Mindful apps will open at night again.") { Store.windDownOn = false; render() }
            else { Store.windDownOn = true; render() }
        })
        if (Store.windDownOn) {
            add(row("Starts", null, timeLabel(Store.windDownStart)) { pickTime(Store.windDownStart) { Store.windDownStart = it; render() } })
            add(row("Ends", null, timeLabel(Store.windDownEnd)) { pickTime(Store.windDownEnd) { Store.windDownEnd = it; render() } })
            add(toggleRow("Strict", "Also pause normal apps, not just mindful ones", Store.windDownStrict) { on ->
                if (!on) confirmLoosen("Make wind-down gentler?", "Normal apps will open at night.") { Store.windDownStrict = false; render() }
                else { Store.windDownStrict = true; render() }
            })
        }
        add(sectionTitle("Focus"))
        add(paragraph("Start focus or a screen-free break from the home screen (tap Focus at the bottom). Only essentials open until it ends.", 14.5f))
        add(row("Essentials", names(Store.packagesWithMode(Mode.ESSENTIAL)), null) { go("essentials") })
    }

    private fun pickTime(current: Int, onPick: (Int) -> Unit) {
        val theme = if (P.dark) android.R.style.Theme_Material_Dialog else android.R.style.Theme_Material_Light_Dialog
        TimePickerDialog(this, theme, { _, h, m -> onPick(h * 60 + m) }, current / 60, current % 60, DateFormat.is24HourFormat(this)).show()
    }

    private fun pagePlan() {
        header("Your plan for the pull", "An if-then plan: “When I feel the pull, I'll…”. Planning the response in advance had a medium-to-large effect on reaching goals across 94 studies.")
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
            addView(button("Save my own", Kind.QUIET) { if (fld.text.isNotBlank()) { Store.urgePlan = fld.text.toString(); render() } })
        })
    }

    // ---------------------------------------------------------------------------------- phone

    private fun status(ok: Boolean) = if (ok) "On" else "Off"

    private fun pageProtection() {
        header("Protection and permissions", "Each one is optional except the first. Intent works with whatever you allow, and tells you honestly what it can and can't do.")
        val home = Launch.isDefaultHome(this)
        add(row("Home screen", if (home) "Intent is your home app" else "Needed: otherwise your phone opens to your old launcher", if (home) "On" else "Set") {
            if (!home) Launch.requestHome(this) else Launch.homeSettings(this)
        })
        val guard = Launch.isGuardOn(this)
        add(row("Focus Guard", if (guard) "Pauses mindful apps from anywhere and ends sessions on time"
            else "Off: pauses only apps opened from Intent; sessions end with a notification", status(guard)) { guardSheet(this) })
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

    private fun usageSheet() {
        val s = Sheet(this, "Usage access", "Lets Intent read Android's own usage events to show today's unlocks and minutes in mindful apps. Computed on your phone, never stored elsewhere or sent anywhere.")
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
        if (flagged.isEmpty()) add(paragraph("Nothing flagged. Mark apps as mindful first.", 15f, P.text3))
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
        add(toggleRow("Swipe right: Mind library", null, Store.swipeRightMind) { Store.swipeRightMind = it })
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
        add(toggleRow("Status bar on the home screen", "Off for a calmer screen. Swipe down still opens notifications.", Store.statusBar) { Store.statusBar = it })
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
            confirmLoosen("Erase everything?", "Your rules, home apps, plans and counters will be deleted. This removes every pause too, so it waits like any other loosening.", "Erase") {
                for (pkg in Store.sessions { it.activePackages(android.os.SystemClock.elapsedRealtime()) }) Reminder.cancel(this, pkg)
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
        add(row("The Mind library", "${com.poshith.intent.core.Lessons.all.size} sourced lessons: swipe right on the home screen", null, null))
        add(row("Code and research notes", GITHUB.removePrefix("https://"), null) {
            Launch.tryStart(this, Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB)), "No browser found")
        })
        add(row("Fonts", "Inter by Rasmus Andersson, SIL Open Font License 1.1", null, null))
        add(row("Inspired by", "Olauncher's text home screen and one sec's pause (ideas only; no code copied)", null, null))
    }
}

/** Focus Guard explanation with Android's "restricted settings" help for apps installed from a file. */
fun guardSheet(a: BaseActivity) {
    val s = Sheet(a, "Focus Guard", "Lets Intent show your pause even when a mindful app is opened from a notification or the recents screen, and end the sessions you choose on time.")
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
