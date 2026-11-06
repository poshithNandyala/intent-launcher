package com.poshith.intent.guard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.poshith.intent.GateActivity
import com.poshith.intent.HomeActivity
import com.poshith.intent.core.Decision
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage

/**
 * Focus Guard (optional, off until you switch it on in Android's accessibility settings).
 *
 * What it does:  notices WHICH app comes to the front (package name only, from window-change events),
 *                asks the same rules as the home screen, shows the pause screen for mindful apps opened
 *                from anywhere (notifications, recents, links), counts a session's minutes only while
 *                that app is in front, and closes the app when they're used (no picture-in-picture,
 *                nothing left playing). During a real break it keeps the notification shade closed.
 * What it can't: read the screen (canRetrieveWindowContent=false), see notifications or messages,
 *                go online (Intent has no internet permission), or stop you from turning it off.
 */
class FocusGuardService : AccessibilityService() {

    companion object {
        @Volatile var running = false
            private set
        @Volatile private var instance: FocusGuardService? = null

        private const val HOME = "#home"
        private const val GATE = "#gate"
        private const val SELF = "#self"
        private const val OFF = "#off"
        /** A call is ringing or going on: nothing is paused, and no app's minutes count. */
        private const val CALL = "#call"

        fun lockScreen(): Boolean {
            val s = instance ?: return false
            if (Calls.active(s)) return false // never lock the screen on a call
            return Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun openNotifications(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /** The pause screen let a session start: if the app doesn't report itself, assume it's in front. */
        fun sessionStarted(pkg: String) {
            val s = instance
            if (s != null) s.expect(pkg) else { pendingExpect = pkg; pendingExpectAt = SystemClock.elapsedRealtime() }
        }

        // Kept across a restart of the service (Android can unbind and rebind it at any time):
        // what was in front, and when the service went away, so timing picks up where it left off.
        @Volatile private var lastFront = HOME
        @Volatile private var unboundAt = 0L
        @Volatile private var pendingExpect: String? = null
        @Volatile private var pendingExpectAt = 0L

        private val sentHomeAt = HashMap<String, Long>()

        /** "Not now" means not now, and time's up means closed: if the app pops back up within a few
         *  seconds (apps that open through a splash screen relaunch themselves, and an app on its way out
         *  can report one more screen), it's sent home again without asking twice. */
        fun saidNotNow(pkg: String) = sentHome(pkg)
        private fun sentHome(pkg: String) { synchronized(sentHomeAt) { sentHomeAt[pkg] = SystemClock.elapsedRealtime() } }
        private fun justSentHome(pkg: String): Boolean =
            synchronized(sentHomeAt) { SystemClock.elapsedRealtime() - (sentHomeAt[pkg] ?: -SENT_HOME_MS) < SENT_HOME_MS }
        private const val SENT_HOME_MS = 4_000L
        /** The heads-up comes this long before a session ends. */
        private const val WARN_MS = 60_000L

        /** The status bar, notification shade and quick settings (Xiaomi draws its control centre in a plugin). */
        private val SYSTEM_UI = setOf("com.android.systemui", "miui.systemui.plugin")
    }

    private val h = Handler(Looper.getMainLooper())
    private var current = HOME
    private var beforeOff = HOME
    private var timing: String? = null
    /** The app whose pause screen is on its way (started, not yet in front), and when. */
    private var gatePending: String? = null
    private var gatePendingAt = 0L
    /** Recent pause starts per app, so an app that keeps reopening itself can't start a loop. */
    private val gateStarts = HashMap<String, ArrayDeque<Long>>()
    private var ignored: Set<String> = SYSTEM_UI
    private val expireRunnable = Runnable { onExpire() }

    /** Rules change at minute boundaries (a check-in window ends, wind-down starts). While you stay
     *  in an app, check again just after each minute turns, so they apply on time. */
    private val recheck = object : Runnable {
        override fun run() {
            val pkg = current
            if (!isApp(pkg)) return
            // A session that ran out without its timer firing still ends here: the app closes.
            val s = Store.sessions { it[pkg] }
            if (s != null && Store.sessions { it.remainingMs(pkg, SystemClock.elapsedRealtime()) } <= 1_000) {
                timing = pkg; onExpire(); return
            }
            if (Launch.decide(this@FocusGuardService, pkg) !is Decision.Open) showGate(pkg)
            else h.postDelayed(this, msToNextMinute())
        }
    }

    private fun msToNextMinute(): Long = 60_000L - System.currentTimeMillis() % 60_000L + 1_000L

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { beforeOff = current; moveTo(OFF) }
                Intent.ACTION_USER_PRESENT -> onUnlock()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        instance = this
        running = true
        ignored = computeIgnored()
        val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
        com.poshith.intent.IntentApp.listen(this, screen, f)
        // Back after a restart: carry on with whatever was in front (its session timer included).
        val now = SystemClock.elapsedRealtime()
        val front = lastFront
        if (unboundAt > 0 && now - unboundAt < 60_000 && front != HOME && front != OFF) h.post { if (current == HOME) moveTo(front) }
        pendingExpect?.let { p -> if (now - pendingExpectAt < 10_000) expect(p) }
        pendingExpect = null
    }

    private fun computeIgnored(): Set<String> {
        val out = HashSet<String>()
        out += SYSTEM_UI
        try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach { out += it.packageName }
        } catch (_: Exception) {}
        return out
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        // A real break means no notifications at all: the shade (and quick settings) close as they open.
        if (pkg in SYSTEM_UI) { if (Store.breakActive() && !Calls.active(this)) closeShade(); return }
        val cls = e.className?.toString() ?: ""
        val next = if (pkg == packageName) {
            when (cls) {
                GateActivity::class.java.name -> { gatePending = null; GATE }
                HomeActivity::class.java.name -> HOME
                else -> if (cls.startsWith("com.poshith.intent.")) SELF else return
            }
        } else {
            if (pkg in ignored) return
            // A call (ringing, or a call screen: phone, WhatsApp, Telegram, Meet...) always shows, in every
            // mode and every break: Intent never stands between you and a call. And a call doesn't use up
            // an app's minutes: its session waits, and picks up again when you're back in the app.
            val callScreen = isCallScreen(cls)
            if (callScreen || Calls.active(this)) {
                callFront = if (callScreen) null else pkg
                moveTo(CALL)
                h.removeCallbacks(callCheck); h.postDelayed(callCheck, 2_000)
                return
            }
            pkg
        }
        if (next == current) {
            // The same app put up another screen. If it should be paused but the pause isn't in front
            // (a splash screen opened the real app over it), pause it again. If you just said "Not now"
            // to it, it doesn't get to ask twice: straight home instead.
            if (isApp(next) && Launch.decide(this, next) !is Decision.Open) {
                if (justSentHome(next)) sendHome() else showGate(next)
            }
            return
        }
        moveTo(next)
    }

    private fun isApp(s: String) = !s.startsWith("#")

    /** During a call: the last app that came to the front (not the call screen), to pick up after the call. */
    private var callFront: String? = null
    private val callCheck = object : Runnable {
        override fun run() {
            if (current != CALL) return
            if (Calls.active(this@FocusGuardService)) { h.postDelayed(this, 2_000); return }
            // The call is over. If an app came to the front during it, that's where you are now: its
            // session picks up (or it gets the pause, like any other open).
            val back = callFront
            callFront = null
            if (back != null) moveTo(back)
        }
    }

    private fun isCallScreen(cls: String): Boolean {
        val c = cls.substringAfterLast('.').lowercase()
        return "voip" in c || "incall" in c || "incomingcall" in c || ("call" in c && "activity" in c && "log" !in c && "history" !in c)
    }

    private val shadeCloser = Runnable {
        if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        else try {
            // Older Android: the status bar service collapses its panels (EXPAND_STATUS_BAR permission).
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("collapsePanels").invoke(sb)
        } catch (_: Exception) {}
    }

    private fun closeShade() {
        h.removeCallbacks(shadeCloser)
        h.postDelayed(shadeCloser, 40)
        // Some phones open it in stages (notifications, then quick settings): close again a moment later.
        h.postDelayed({ if (Store.breakActive() && !Calls.active(this)) shadeCloser.run() }, 450)
    }

    private fun moveTo(next: String) {
        if (next == current) return
        val prev = current
        current = next
        lastFront = next
        val now = SystemClock.elapsedRealtime()
        if (isApp(prev)) {
            val hadSession = Store.sessions { it.isActive(prev, now) }
            Store.sessions { it.stop(prev, now) }
            if (timing == prev) { h.removeCallbacks(expireRunnable); h.removeCallbacks(warnRunnable); timing = null }
            // Its minutes stop counting while you're away, so its video shouldn't keep playing either
            // (in a little picture-in-picture window, say). Intent's own screens don't count as away.
            if (hadSession && next != GATE && next != SELF && next != CALL && !Calls.active(this) &&
                Store.rule(prev).mode == com.poshith.intent.core.Mode.MINDFUL) Closer.silence(this, prev)
        }
        h.removeCallbacks(recheck)
        if (isApp(next)) {
            enter(next)
            h.postDelayed(recheck, msToNextMinute())
        }
    }

    private fun enter(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        when (Launch.decide(this, pkg)) {
            is Decision.Open -> {
                if (Store.sessions { it.isActive(pkg, now) }) {
                    Store.sessions { it.start(pkg, now) }
                    scheduleExpiry(pkg)
                }
            }
            else -> if (justSentHome(pkg)) sendHome() else showGate(pkg)
        }
    }

    /** Home in front, quietly (no picture-in-picture), and counted as in front from now on: the
     *  launcher may not report itself again if it already was. */
    private fun sendHome() {
        Closer.toHome(this)
        moveTo(HOME)
    }

    private fun showGate(pkg: String) {
        // A call ringing or going on: nothing goes over it. The app is checked again once you're back.
        if (Calls.active(this)) return
        val now = SystemClock.elapsedRealtime()
        // Skip only if this exact pause was started a moment ago. An app that comes back in front after
        // that (a splash screen opening the real app) gets the pause again, at most 5 times in 10 s.
        if (gatePending == pkg && now - gatePendingAt < 400) return
        val starts = gateStarts.getOrPut(pkg) { ArrayDeque() }
        while (starts.isNotEmpty() && now - starts.first() > 10_000) starts.removeFirst()
        if (starts.size >= 5) return
        starts.addLast(now)
        gatePending = pkg
        gatePendingAt = now
        try {
            startActivity(GateActivity.intent(this, pkg, null, fromLauncher = false)
                .addFlags(Closer.OVER_APP or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (_: Exception) {}
    }

    private fun scheduleExpiry(pkg: String) {
        // Focus Guard is timing it now: the backup reminder (scheduled if it wasn't running) isn't needed.
        Reminder.cancel(this, pkg)
        val rem = Store.sessions { it.remainingMs(pkg, SystemClock.elapsedRealtime()) }
        timing = pkg
        h.removeCallbacks(expireRunnable)
        h.postDelayed(expireRunnable, rem.coerceAtLeast(500))
        // A quiet heads-up a minute before the end, so time's up never comes out of nowhere.
        h.removeCallbacks(warnRunnable)
        if (rem > WARN_MS + 30_000) h.postDelayed(warnRunnable, rem - WARN_MS)
    }

    private val warnRunnable = Runnable {
        val pkg = timing ?: return@Runnable
        if (current != pkg || Calls.active(this)) return@Runnable
        val rem = Store.sessions { it.remainingMs(pkg, SystemClock.elapsedRealtime()) }
        if (rem !in 20_000..(WARN_MS + 5_000)) return@Runnable
        val label = com.poshith.intent.data.Apps.labelFor(this, pkg)
        try { android.widget.Toast.makeText(this, "1 minute left on $label", android.widget.Toast.LENGTH_LONG).show() } catch (_: Exception) {}
    }

    private fun onExpire() {
        val pkg = timing ?: return
        if (current != pkg) return
        // No session any more (its rule changed, or it was ended elsewhere): nothing to end, nothing to close.
        if (Store.sessions { it[pkg] } == null) { timing = null; h.removeCallbacks(warnRunnable); return }
        // On a call in that app (WhatsApp, Meet...): time's up waits until the call is over.
        if (Calls.active(this)) { h.removeCallbacks(expireRunnable); h.postDelayed(expireRunnable, 15_000); return }
        val now = SystemClock.elapsedRealtime()
        val rem = Store.sessions { it.remainingMs(pkg, now) }
        if (rem > 1_000) { scheduleExpiry(pkg); return }
        val minutes = ((Store.sessions { it[pkg] }?.allowanceMs ?: 0L) / 60_000L).toInt()
        Store.sessions { it.close(pkg) }
        Store.markEnded(pkg)
        timing = null
        // Time's up: the app closes. No screen asking to stay; a line on the home screen says why.
        val label = com.poshith.intent.data.Apps.labelFor(this, pkg)
        Store.flash = (if (minutes > 0) "Your $minutes minutes on $label ended, as you planned." else "Your time on $label ended, as you planned.") +
            ((Store.todayStep() ?: Store.todayIntention())?.let { " Next: $it" } ?: "")
        // Closed means closed: a last screen it reports on its way out (or reopening it within a few
        // seconds) sends it home again, with no pause screen popping up.
        sentHome(pkg)
        Closer.close(this, pkg)
        moveTo(HOME)
    }

    private fun onUnlock() {
        Store.noteUnlock()
        Store.lastUnlockElapsed = SystemClock.elapsedRealtime()
        // After unlocking, Android may simply resume the app you were in. If no window event arrives,
        // work out what's in front (from usage events if allowed, else what was there before) and
        // check it, so a mindful app you left open hours ago still gets its pause.
        h.postDelayed({
            if (current == OFF) {
                val fg = Usage.lastForeground(this)?.let { if (it == packageName) HOME else it } ?: beforeOff
                current = HOME
                moveTo(if (fg == OFF) HOME else fg)
            }
        }, 700)
    }

    private fun expect(pkg: String) {
        h.postDelayed({
            // The app should be in front by now. If no window event said so, assume it is.
            when {
                // Still on one of Intent's own screens (the pause just closed): the app is coming up.
                // Not if you're on the home screen: then you really are home.
                current == GATE || current == SELF -> moveTo(pkg)
                current == pkg && timing != pkg && Store.sessions { it.isActive(pkg, SystemClock.elapsedRealtime()) } -> scheduleExpiry(pkg)
            }
        }, 700)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (!running) return
        running = false
        instance = null
        unboundAt = SystemClock.elapsedRealtime()
        val now = SystemClock.elapsedRealtime()
        // Going away in the middle of a session (switched off, or stopped by Android): nobody will see
        // you leave the app now, so its minutes keep running on the clock and the backup reminder ends
        // it on time. If Focus Guard comes back, it picks the timing up again and cancels the reminder.
        val pkg = current
        if (isApp(pkg)) {
            val left = Store.sessions { b ->
                if (!b.isActive(pkg, now)) null else { b.start(pkg, now); b.remainingMs(pkg, now) to (b[pkg]?.allowanceMs ?: 0L) }
            }
            if (left != null) Reminder.scheduleMs(this, pkg, left.first, (left.second / 60_000L).toInt())
        }
        h.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screen) } catch (_: Exception) {}
    }
}
