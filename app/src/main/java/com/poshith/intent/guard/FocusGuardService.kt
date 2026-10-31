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
import com.poshith.intent.TimeUpActivity
import com.poshith.intent.core.Decision
import com.poshith.intent.data.Store
import com.poshith.intent.data.Usage

/**
 * Focus Guard (optional, off until you switch it on in Android's accessibility settings).
 *
 * What it does:  notices WHICH app comes to the front (package name only, from window-change events),
 *                asks the same rules as the home screen, shows the pause screen for mindful apps opened
 *                from anywhere (notifications, recents, links), counts a session's minutes only while
 *                that app is in front, and shows "time's up" when they're used.
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
        private const val TIMEUP = "#timeup"
        private const val SELF = "#self"
        private const val OFF = "#off"

        fun lockScreen(): Boolean {
            val s = instance ?: return false
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

        private val notNowAt = HashMap<String, Long>()

        /** "Not now" means not now: if the app pops back up within a few seconds (apps that open through
         *  a splash screen relaunch themselves), it's sent home again without asking twice. */
        fun saidNotNow(pkg: String) { synchronized(notNowAt) { notNowAt[pkg] = SystemClock.elapsedRealtime() } }
        private fun justSaidNotNow(pkg: String): Boolean =
            synchronized(notNowAt) { SystemClock.elapsedRealtime() - (notNowAt[pkg] ?: -NOT_NOW_MS) < NOT_NOW_MS }
        private const val NOT_NOW_MS = 4_000L
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
    private var ignored: Set<String> = setOf("com.android.systemui")
    private val expireRunnable = Runnable { onExpire() }

    /** Rules change at minute boundaries (a check-in window ends, wind-down starts). While you stay
     *  in an app, check again just after each minute turns, so they apply on time. */
    private val recheck = object : Runnable {
        override fun run() {
            val pkg = current
            if (!isApp(pkg)) return
            // A session that ran out without its timer firing still ends here, with "time's up".
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
        out += "com.android.systemui"
        try {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).enabledInputMethodList.forEach { out += it.packageName }
        } catch (_: Exception) {}
        return out
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        val cls = e.className?.toString() ?: ""
        val next = if (pkg == packageName) {
            when (cls) {
                GateActivity::class.java.name -> { gatePending = null; GATE }
                TimeUpActivity::class.java.name -> TIMEUP
                HomeActivity::class.java.name -> HOME
                else -> if (cls.startsWith("com.poshith.intent.")) SELF else return
            }
        } else {
            if (pkg in ignored) return
            pkg
        }
        if (next == current) {
            // The same app put up another screen. If it should be paused but the pause isn't in front
            // (a splash screen opened the real app over it), pause it again.
            if (isApp(next) && Launch.decide(this, next) !is Decision.Open) showGate(next)
            return
        }
        moveTo(next)
    }

    private fun isApp(s: String) = !s.startsWith("#")

    private fun moveTo(next: String) {
        if (next == current) return
        val prev = current
        current = next
        lastFront = next
        val now = SystemClock.elapsedRealtime()
        if (isApp(prev)) {
            Store.sessions { it.stop(prev, now) }
            if (timing == prev) { h.removeCallbacks(expireRunnable); timing = null }
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
            else -> if (justSaidNotNow(pkg)) performGlobalAction(GLOBAL_ACTION_HOME) else showGate(pkg)
        }
    }

    private fun showGate(pkg: String) {
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
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        } catch (_: Exception) {}
    }

    private fun scheduleExpiry(pkg: String) {
        val rem = Store.sessions { it.remainingMs(pkg, SystemClock.elapsedRealtime()) }
        timing = pkg
        h.removeCallbacks(expireRunnable)
        h.postDelayed(expireRunnable, rem.coerceAtLeast(500))
    }

    private fun onExpire() {
        val pkg = timing ?: return
        if (current != pkg) return
        val now = SystemClock.elapsedRealtime()
        val rem = Store.sessions { it.remainingMs(pkg, now) }
        if (rem > 1_000) { scheduleExpiry(pkg); return }
        Store.sessions { it.stop(pkg, now) }
        Store.markEnded(pkg)
        timing = null
        try {
            startActivity(TimeUpActivity.intent(this, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
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
                current == GATE || current == TIMEUP || current == SELF -> moveTo(pkg)
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
        if (isApp(current)) Store.sessions { it.stop(current, now) }
        h.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screen) } catch (_: Exception) {}
    }
}
