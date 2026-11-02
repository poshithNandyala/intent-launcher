package com.poshith.intent

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.poshith.intent.core.Catalog
import com.poshith.intent.core.Mode
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
import com.poshith.intent.ui.P
import com.poshith.intent.ui.animationsOn
import com.poshith.intent.ui.button
import com.poshith.intent.ui.dp
import com.poshith.intent.ui.field
import com.poshith.intent.ui.hbox
import com.poshith.intent.ui.lp
import com.poshith.intent.ui.onText
import com.poshith.intent.ui.paragraph
import com.poshith.intent.ui.ripple
import com.poshith.intent.ui.rounded
import com.poshith.intent.ui.tick
import com.poshith.intent.ui.row
import com.poshith.intent.ui.scroller
import com.poshith.intent.ui.space
import com.poshith.intent.ui.text
import com.poshith.intent.ui.vbox
import com.poshith.intent.ui.vflex

/** First run, about two minutes: what Intent is, your essentials, the apps that pull you, a plan, and permissions. */
class OnboardingActivity : BaseActivity() {

    companion object {
        /** Start over from the first page ("Run setup again" in Settings). */
        const val EXTRA_RESTART = "restart"
    }

    private var step = 0
    private val steps = listOf("welcome", "essentials", "pull", "plan", "strict", "protect", "quiet", "done")
    /** "gentle" or "strict", once chosen on the strict page. */
    private var strictChoice: String? = null
    private var essentials: List<String> = emptyList()
    private var pull: List<String> = emptyList()
    private lateinit var frame: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        frame = FrameLayout(this)
        host.addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onBars { b -> frame.setPadding(b.left, b.top + dp(18), b.right, b.bottomOrIme + dp(14)) }
        Apps.onChange(appsListener)
        Apps.refresh(this)
        // Pick up on the page you were on: Android can restart setup (choosing a home app does on some phones).
        step = if (intent.getBooleanExtra(EXTRA_RESTART, false)) 0 else Store.setupStep.coerceIn(0, steps.size - 1)
        show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_RESTART, false)) { step = 0; show() }
    }

    private val appsListener: () -> Unit = { if (steps[step] in setOf("essentials", "pull") && essentials.isEmpty() && pull.isEmpty()) show() }

    override fun onDestroy() {
        Apps.removeListener(appsListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (recreating) return
        if (steps[step] == "protect") show() // statuses change after returning from Android settings
    }

    override fun onBackRoot() {
        if (step > 0) { step--; show(); return }
        // Leaving setup on its first page: back to the home app you use now. (If that's already Intent,
        // there's nothing to go back to until setup is done.)
        if (!Launch.isDefaultHome(this)) Launch.goHome(this)
    }

    private fun next() { if (step < steps.size - 1) { step++; show() } }

    private fun show() {
        Store.setupStep = step
        frame.removeAllViews()
        val v: View = when (steps[step]) {
            "welcome" -> welcome()
            "essentials" -> essentialsPage()
            "pull" -> pullPage()
            "plan" -> planPage()
            "strict" -> strictPage()
            "protect" -> protectPage()
            "quiet" -> quietPage()
            else -> donePage()
        }
        frame.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (animationsOn()) { v.alpha = 0f; v.animate().alpha(1f).setDuration(220).start() }
    }

    private fun dots(): View {
        val row = hbox(Gravity.CENTER)
        for (i in 1 until steps.size - 1) {
            val d = View(this).apply { background = rounded(if (i <= step) P.accent else P.line, dp(3).toFloat()) }
            row.addView(d, LinearLayout.LayoutParams(dp(if (i == step) 18 else 6), dp(6)).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        return row
    }

    private fun page(title: String, sub: String?, body: View?, primary: String, onPrimary: () -> Unit, secondary: String? = null, onSecondary: (() -> Unit)? = null, scroll: Boolean = true): View {
        val root = vbox()
        root.addView(dots(), lp().apply { bottomMargin = dp(18) })
        val inner = vbox()
        inner.addView(text(title, 30f, P.text, Fonts.medium(this)).apply { setPadding(dp(24), 0, dp(24), 0); setLineSpacing(0f, 1.1f) }, lp())
        if (sub != null) inner.addView(paragraph(sub), lp().apply { topMargin = dp(6) })
        if (body != null) inner.addView(body, lp())
        if (scroll) root.addView(scroller(inner), lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        else { root.addView(inner, lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)) }
        val actions = vbox().apply { setPadding(dp(24), dp(10), dp(24), 0) }
        actions.addView(button(primary, Kind.PRIMARY) { onPrimary() }, lp())
        if (secondary != null && onSecondary != null) actions.addView(button(secondary, Kind.PLAIN) { onSecondary() }, lp().apply { topMargin = dp(6) })
        root.addView(actions, lp())
        return root
    }

    // ---------------------------------------------------------------------------------- pages

    /** Scrolling content with the buttons pinned below it, so they're reachable on any screen and font size. */
    private fun pinned(content: LinearLayout, actions: View): View {
        val root = vbox()
        root.addView(scroller(content), lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(actions, lp().apply { topMargin = dp(10) })
        return root
    }

    private fun welcome(): View {
        val content = vbox()
        content.addView(vflex())
        content.addView(text("Intent", 52f, P.text, Fonts.light(this)).apply { setPadding(dp(28), dp(8), dp(28), 0) })
        content.addView(text("Open your phone on purpose.", 22f, P.text2, Fonts.regular(this)).apply { setPadding(dp(28), dp(4), dp(28), dp(26)) })
        fun fact(s: String) = content.addView(text(s, 16f, P.text2).apply { setLineSpacing(0f, 1.28f); setPadding(dp(28), dp(8), dp(28), dp(8)) }, lp())
        fact("Most of the time we pick up our phones out of habit, not because something needs us.")
        fact("We check about twice as often as we think.")
        fact("A short pause, with an easy way out, cut app openings by more than half in a large study.")
        content.addView(text("Intent keeps your tools one tap away and puts that pause in front of the apps that pull you. Calls, messages, payments, maps, Wi-Fi, Bluetooth and hotspot keep working exactly as before.", 15f, P.text3).apply {
            setLineSpacing(0f, 1.28f); setPadding(dp(28), dp(14), dp(28), dp(8))
        }, lp())
        content.addView(vflex())
        val actions = vbox().apply { setPadding(dp(24), 0, dp(24), 0) }
        actions.addView(button("Begin", Kind.PRIMARY) { next() }, lp())
        actions.addView(text("About two minutes. Nothing leaves your phone: Intent has no internet access.", 13.5f, P.text3).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        }, lp())
        return pinned(content, actions)
    }

    private fun defaultTools(): List<String> {
        val out = ArrayList<String>()
        // Phone and Camera are already at the bottom of the home screen, so they aren't picked twice.
        val pkgs = listOf(
            Launch.resolve(this, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            "com.whatsapp",
            "com.google.android.apps.maps",
        ).filterNotNull() + Catalog.TOOLS.filter { it in Catalog.ESSENTIAL_HINT }
        for (p in pkgs) {
            val k = Apps.byPkg(p)?.key ?: continue
            if (k !in out) out += k
            if (out.size >= Store.SUGGESTED_HOME) break
        }
        return out
    }

    private fun essentialsPage(): View {
        if (essentials.isEmpty()) essentials = Store.homeKeys.ifEmpty { defaultTools() }
        val suggested = LinkedHashSet<String>()
        listOf(Launch.resolve(this, Intent(Intent.ACTION_DIAL)), Launch.resolve(this, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            Launch.resolve(this, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))).filterNotNull()
            .forEach { p -> Apps.byPkg(p)?.let { suggested += it.key } }
        Catalog.TOOLS.forEach { p -> Apps.byPkg(p)?.let { suggested += it.key } }
        val picker = AppPicker(this, "Your essentials",
            "The few tools you use every day: they become your home screen and always open instantly, even during focus. Up to ${Store.MAX_HOME}; four or five is calmest. Phone and Camera are always at the bottom.",
            essentials, byPackage = false, max = Store.MAX_HOME, suggested = suggested, showDone = false,
            onChange = { essentials = it }) { }
        val root = vbox()
        root.addView(dots(), lp().apply { bottomMargin = dp(18) })
        root.addView(picker.build(), lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val actions = vbox().apply { setPadding(dp(24), dp(10), dp(24), 0) }
        actions.addView(button("Next", Kind.PRIMARY) {
            essentials = picker.selected.toList()
            Store.homeKeys = essentials
            for (k in essentials) {
                val pkg = k.substringBefore('/')
                if (pkg !in Catalog.PULL && Store.rule(pkg).mode == Mode.NORMAL) Store.setMode(pkg, Mode.ESSENTIAL)
            }
            next()
        }, lp())
        root.addView(actions, lp())
        return root
    }

    /**
     * Gentle or Strict, chosen on purpose (a goal you pick yourself holds better than one you're handed).
     * Strict: no extensions in the moment, locks if you go back, looser only from tomorrow.
     */
    private fun strictPage(): View {
        if (strictChoice == null && Store.strictActive()) strictChoice = "strict"
        val body = vbox().apply { setPadding(dp(16), dp(8), dp(16), 0) }
        fun card(key: String, title: String, badge: String?, sub: String) {
            val chosen = strictChoice == key
            val c = vbox().apply {
                setPadding(dp(20), dp(16), dp(20), dp(16))
                background = ripple(rounded(if (chosen) P.accentSoft else P.surface, dp(18).toFloat(), if (chosen) P.accent else 0, dp(2)), dp(18).toFloat())
                isClickable = true
                setOnClickListener { it.tick(); strictChoice = key; show() }
            }
            val head = hbox(Gravity.CENTER_VERTICAL)
            head.addView(text(title, 18f, P.text, Fonts.medium(this)), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (chosen) head.addView(text("✓", 18f, P.accent, Fonts.medium(this)))
            c.addView(head, lp())
            if (badge != null) c.addView(text(badge, 12.5f, P.accent, Fonts.medium(this)).apply { setPadding(0, dp(2), 0, 0) }, lp())
            c.addView(text(sub, 14.5f, P.text2).apply { setLineSpacing(0f, 1.22f); setPadding(0, dp(6), 0, 0) }, lp())
            body.addView(c, lp().apply { bottomMargin = dp(12) })
        }
        card("gentle", "Gentle", null, "A short pause before your pull apps, and a question at the first Short or Reel. You decide each time.")
        card("strict", "Strict", "If pauses haven't worked for you",
            "No “a few more” in the moment. A number of Shorts and Reels a day, then they close; go back and they lock (the rest of YouTube and Instagram still works). Rules only get looser from tomorrow, and an emergency code is the only way past it.")
        if (strictChoice == "strict") {
            body.addView(text("Shorts and Reels a day", 16f, P.text, Fonts.medium(this)).apply { setPadding(dp(8), dp(10), dp(8), dp(4)) }, lp())
            val f = Flow(this).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) }
            for (v in com.poshith.intent.core.Strict.SHORTS_CHOICES) {
                val c = Chip(this, if (v == 0) "None" else "$v"); c.on = v == Store.reelsAllow
                c.setOnClickListener { it.tick(); Store.reelsAllow = v; show() }
                f.addView(c)
            }
            body.addView(f, lp())
            body.addView(text("Minutes a day for all pull apps", 16f, P.text, Fonts.medium(this)).apply { setPadding(dp(8), dp(12), dp(8), dp(4)) }, lp())
            val g = Flow(this).apply { setPadding(dp(4), dp(4), dp(4), dp(4)) }
            for (v in com.poshith.intent.core.Limits.BUDGET_CHOICES) {
                val c = Chip(this, if (v == 0) "No limit" else "$v min"); c.on = v == Store.budgetMin
                c.setOnClickListener { it.tick(); Store.budgetMin = v; show() }
                g.addView(c)
            }
            body.addView(g, lp())
        }
        return page("How strict should Intent be?", "Choose while you're calm. You can change it later.", body,
            when (strictChoice) { null -> "Choose one"; "strict" -> if (Store.strictActive()) "Next" else "Next: your emergency code"; else -> "Next" },
            {
                when (strictChoice) {
                    null -> {}
                    "strict" -> if (Store.strictActive()) next() else strictCodeSheet { next() }
                    else -> next()
                }
            })
    }

    private val INSTAGRAM = setOf("com.instagram.android", "com.instagram.lite")

    private fun suggestedPull(): Set<String> {
        val out = LinkedHashSet<String>()
        for (a in Apps.list) if (a.pkg in Catalog.PULL ||
            (a.category in Catalog.PULL_CATEGORIES && a.pkg !in Catalog.TOOLS && !Launch.isProtected(this, a.pkg))) out += a.pkg
        return out
    }

    private fun pullPage(): View {
        val sug = suggestedPull()
        // Preselected: Instagram only. Every other suggestion is listed for you to choose.
        if (pull.isEmpty()) pull = (Store.packagesWithMode(Mode.MINDFUL).ifEmpty { sug.filter { it in INSTAGRAM }.toSet() }).toList()
        val picker = AppPicker(this, "Which apps pull you in?",
            "Feeds, short videos, endless browsing. They'll get a short pause and the minutes you choose. You can still open them, on purpose.",
            pull, byPackage = true, suggested = sug, showDone = false,
            reasonDisabled = { e -> if (Launch.isProtected(this, e.pkg)) "Always available" else null },
            onChange = { pull = it }) { }
        val root = vbox()
        root.addView(dots(), lp().apply { bottomMargin = dp(18) })
        root.addView(picker.build(), lp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val actions = vbox().apply { setPadding(dp(24), dp(10), dp(24), 0) }
        actions.addView(button("Next", Kind.PRIMARY) {
            pull = picker.selected.toList()
            for (p in pull) if (Store.rule(p).mode != Mode.MINDFUL) Store.setRule(p, Store.mindfulDefaults(Store.rule(p)))
            // Unticked here (running setup again): no longer mindful.
            for (p in Store.packagesWithMode(Mode.MINDFUL)) if (p !in pull) Store.setMode(p, Mode.NORMAL)
            next()
        }, lp())
        root.addView(actions, lp())
        return root
    }

    private fun planPage(): View {
        val body = vbox()
        val ideas = listOf("take three slow breaths", "drink a glass of water", "stand up and stretch", "read one page of a book",
            "message someone I care about", "do my one thing for two minutes", "step outside for a minute")
        val flow = Flow(this).apply { setPadding(dp(24), dp(12), dp(24), dp(8)) }
        val chips = ArrayList<Chip>()
        for (i in ideas) {
            val c = Chip(this, i); c.on = Store.urgePlan == i
            c.setOnClickListener { Store.urgePlan = i; chips.forEach { x -> x.on = x === c } }
            chips += c; flow.addView(c)
        }
        body.addView(flow, lp())
        val f = field("Or write your own", if (Store.urgePlan in ideas) "" else Store.urgePlan)
        f.onText { if (it.isNotBlank()) { Store.urgePlan = it; chips.forEach { x -> x.on = false } } }
        body.addView(FrameLayout(this).apply { setPadding(dp(24), dp(8), dp(24), dp(8)); addView(f) }, lp())
        body.addView(text("Today's one thing (optional)", 16f, P.text, Fonts.medium(this)).apply { setPadding(dp(24), dp(22), dp(24), dp(6)) }, lp())
        val one = field("e.g. Finish the API endpoint", Store.oneThing)
        one.onText { Store.oneThing = it; Store.oneThingDay = Store.dayKey() }
        body.addView(FrameLayout(this).apply { setPadding(dp(24), dp(4), dp(24), dp(8)); addView(one) }, lp())
        return page("When you feel the pull, you'll…",
            "An if-then plan decided now does the work later, when you're tired. Across 94 studies this kind of plan had a medium-to-large effect on reaching goals.",
            body, "Next", { next() }, "Skip", { next() })
    }

    /**
     * The switches that make Intent work. All optional, but this page pushes for every one: what each
     * does in one line, how many are on, and one big button that turns on the next.
     */
    private fun protectPage(): View {
        class Switch(val title: String, val sub: String, val on: Boolean, val action: String, val needed: Boolean = false, val turnOn: () -> Unit)
        val list = ArrayList<Switch>()
        list += Switch("Make Intent your home screen", "So your phone opens to your essentials, not to an icon grid built to pull you in.",
            Launch.isDefaultHome(this), "Set Intent as home", needed = true) { Launch.requestHome(this) }
        list += Switch("Focus Guard", "Pauses your pull apps even from a notification or recents, and closes them when your minutes are up. It sees only which app is in front.",
            Launch.guardHealth(this) == Launch.Health.ON, "Turn on Focus Guard") { guardSheet(this) }
        list += Switch("Shorts & Reels guard", "Stops the endless swipe: the Short you open plays, the next swipe gets a pause and a big button to close the app.",
            Launch.reelsHealth(this) == Launch.Health.ON, "Turn on the Shorts & Reels guard") { reelsSheet(this) }
        list += Switch("No notifications in a real break", "Notification access: during a break only calls get through, and everything else waits for you.",
            com.poshith.intent.guard.BreakQuietService.hasAccess(this) && com.poshith.intent.guard.Dnd.hasAccess(this), "Allow notification access") {
            if (!com.poshith.intent.guard.BreakQuietService.hasAccess(this)) com.poshith.intent.guard.BreakQuietService.requestAccess(this)
            else com.poshith.intent.guard.Dnd.requestAccess(this)
        }
        list += Switch("Usage access", "Honest numbers: today's screen time, unlocks and minutes in pull apps, counted on the phone.",
            Usage.hasAccess(this), "Allow usage access") { Launch.usageSettings(this) }
        if (Build.VERSION.SDK_INT >= 33) list += Switch("Reminders", "A “time's up” note if Focus Guard ever isn't running.",
            Reminder.canNotify(this), "Allow reminders") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7) }

        val body = vbox().apply { setPadding(dp(16), dp(4), dp(16), 0) }
        val onCount = list.count { it.on }
        // How far along: a bar and a count, so "almost there" pulls you to finish.
        val prog = hbox(Gravity.CENTER_VERTICAL).apply { setPadding(dp(8), dp(6), dp(8), dp(14)) }
        val bar = hbox().apply { background = rounded(P.surface2, dp(3).toFloat()); clipToOutline = true }
        if (onCount > 0) bar.addView(View(this).apply { background = rounded(P.accent, dp(3).toFloat()) }, LinearLayout.LayoutParams(0, dp(6), onCount.toFloat()))
        if (onCount < list.size) bar.addView(View(this), LinearLayout.LayoutParams(0, dp(6), (list.size - onCount).toFloat()))
        prog.addView(bar, LinearLayout.LayoutParams(0, dp(6), 1f))
        prog.addView(text("$onCount of ${list.size} on", 13.5f, if (onCount == list.size) P.accent else P.text2, Fonts.medium(this)).apply { setPadding(dp(12), 0, 0, 0) })
        body.addView(prog, lp())
        for (sw in list) {
            val c = vbox().apply {
                setPadding(dp(20), dp(14), dp(20), dp(14))
                background = rounded(P.surface, dp(18).toFloat())
            }
            val head = hbox(Gravity.CENTER_VERTICAL)
            head.addView(text(sw.title, 16.5f, P.text, Fonts.medium(this)), lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(text(if (sw.on) "✓ On" else if (sw.needed) "Needed" else "Recommended", 12.5f,
                if (sw.on) P.accent else if (sw.needed) P.warn else P.text2, Fonts.medium(this)))
            c.addView(head, lp())
            c.addView(text(sw.sub, 14f, P.text2).apply { setLineSpacing(0f, 1.22f); setPadding(0, dp(5), 0, if (sw.on) 0 else dp(10)) }, lp())
            if (!sw.on) c.addView(button("Turn on", Kind.QUIET) { sw.turnOn() }, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
            body.addView(c, lp().apply { bottomMargin = dp(10) })
        }
        val nextOff = list.firstOrNull { !it.on }
        return if (nextOff == null) page("All on. Intent can do its job.", "You can turn any of these off later in Settings › Protection and permissions.", body, "Next", { next() })
        else page("Turn on your protection", "Intent works best with every one of these on. Each is one switch in Android's settings, and comes back here.",
            body, nextOff.action, { nextOff.turnOn() }, "Skip for now", { next() })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42) finish() else if (steps[step] == "protect") show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (steps[step] == "protect") show()
    }

    private fun quietPage(): View {
        val body = vbox()
        val list = pull.ifEmpty { Store.packagesWithMode(Mode.MINDFUL).toList() }
        if (list.isEmpty()) body.addView(paragraph("You didn't mark any apps as mindful. You can do it later from Settings.", 15f, P.text3), lp())
        for (p in list.sortedBy { Apps.labelFor(this, it).lowercase() }) {
            body.addView(row(Apps.labelFor(this, p), null, if (p in Store.notifReviewed) "Done" else "Silence") {
                Store.notifReviewed = Store.notifReviewed + p
                Launch.notificationSettings(this, p)
            }, lp())
        }
        body.addView(text("Keep: calls, messages from people, one-time passwords, payments.", 14.5f, P.text3).apply {
            setPadding(dp(24), dp(16), dp(24), 0); setLineSpacing(0f, 1.2f)
        }, lp())
        return page("Quiet the pull apps", "Their alerts are designed to bring you back. Tap each one, switch off its notifications in Android, and come back here. Even an alert you ignore costs attention.",
            body, "Next", { next() }, "Later", { next() })
    }

    private fun donePage(): View {
        val content = vbox()
        content.addView(vflex())
        content.addView(text("You're set.", 40f, P.text, Fonts.light(this)).apply { setPadding(dp(28), dp(8), dp(28), dp(18)) })
        fun how(a: String, b: String) {
            val r = hbox(Gravity.TOP).apply { setPadding(dp(28), dp(8), dp(28), dp(8)) }
            r.addView(text(a, 15f, P.text, Fonts.medium(this)), lp(dp(118)))
            r.addView(text(b, 15f, P.text2).apply { setLineSpacing(0f, 1.2f) }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            content.addView(r, lp())
        }
        how("Swipe up", "Find any app by typing its name")
        how("Swipe right", "The Mind library: short, sourced lessons")
        how("Swipe down", "Notifications and quick settings")
        how("Long-press", "Edit apps, rules, focus and settings")
        how("Double-tap", "Lock the phone (with Focus Guard)")
        how("Focus", "Bottom of the screen: deep work or a real break")
        content.addView(vflex())
        val actions = vbox().apply { setPadding(dp(24), 0, dp(24), 0) }
        actions.addView(button("Start using Intent", Kind.PRIMARY) {
            Store.onboarded = true
            Store.setupStep = 0
            if (Launch.isDefaultHome(this) || !Launch.requestHome(this, 42)) finish()
        }, lp())
        return pinned(content, actions)
    }
}
