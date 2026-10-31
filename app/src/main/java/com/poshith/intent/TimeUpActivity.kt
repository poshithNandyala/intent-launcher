package com.poshith.intent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.poshith.intent.core.SessionBook
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Store
import com.poshith.intent.guard.FocusGuardService
import com.poshith.intent.guard.Launch
import com.poshith.intent.ui.BaseActivity
import com.poshith.intent.ui.Fonts
import com.poshith.intent.ui.Kind
import com.poshith.intent.ui.P
import com.poshith.intent.ui.button
import com.poshith.intent.ui.dp
import com.poshith.intent.ui.lp
import com.poshith.intent.ui.space
import com.poshith.intent.ui.styleButton
import com.poshith.intent.ui.text
import com.poshith.intent.ui.vbox
import com.poshith.intent.ui.vflex

/** "Your 5 minutes are up." Shown by Focus Guard at the end of a session you chose. */
class TimeUpActivity : BaseActivity() {

    companion object {
        private const val EXTRA_PKG = "pkg"
        fun intent(ctx: Context, pkg: String): Intent = Intent(ctx, TimeUpActivity::class.java).putExtra(EXTRA_PKG, pkg)
    }

    private var pkg = ""
    private var leaving = false
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: pkg
        leaving = false
        build()
    }

    override fun onStop() {
        super.onStop()
        if (!leaving && !isFinishing && !isChangingConfigurations) finish()
    }

    override fun onBackRoot() = done()

    private fun build() {
        host.removeAllViews()
        h.removeCallbacksAndMessages(null)
        if (pkg.isEmpty()) { finish(); return }
        val label = Apps.labelFor(this, pkg)
        val s = Store.sessions { it[pkg] }
        val minutes = ((s?.allowanceMs ?: 0L) / 60_000L).toInt()
        val extended = s?.extended == true

        val col = vbox(Gravity.CENTER_HORIZONTAL)
        host.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        onBars { b -> col.setPadding(dp(28), b.top + dp(32), dp(28), b.bottom + dp(24)) }

        col.addView(vflex())
        col.addView(text("TIME'S UP", 14f, P.text3, Fonts.medium(this)).apply { letterSpacing = 0.06f; gravity = Gravity.CENTER })
        val head = if (minutes > 0) "Your $minutes minutes on $label are done" else "Your time on $label is done"
        col.addView(text(head, 30f, P.text, Fonts.medium(this)).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }, lp())
        val why = s?.intention.orEmpty()
        if (why.isNotBlank()) col.addView(sub("You came to: ${why.lowercase()}."), lp())
        if (Store.oneThing.isNotBlank()) col.addView(sub("Next: ${Store.oneThing}", P.accent), lp())
        val q = com.poshith.intent.core.Quotes.forMoment(System.currentTimeMillis(), 60_000L)
        col.addView(text("“${q.text}”", 16f, P.text2, Fonts.light(this)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f); setPadding(0, dp(30), 0, 0)
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        if (q.by.isNotBlank()) col.addView(text(q.by.uppercase(), 11.5f, P.text3, Fonts.medium(this)).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.08f; setPadding(0, dp(8), 0, 0)
        }, lp())
        col.addView(vflex())

        col.addView(button("I'm done", Kind.PRIMARY) { done() }, lp())
        col.addView(space(10))
        if (!extended) {
            val more = button("Wait…", Kind.OUTLINE) { extend() }
            styleButton(more, Kind.OUTLINE, enabled = false)
            col.addView(more, lp())
            var left = 5
            val tick = object : Runnable {
                override fun run() {
                    if (left <= 0) {
                        more.text = "2 more minutes, then I stop"
                        styleButton(more, Kind.OUTLINE, enabled = true)
                    } else {
                        more.text = "2 more minutes in $left…"
                        left--
                        h.postDelayed(this, 1000)
                    }
                }
            }
            h.post(tick)
        } else {
            col.addView(sub("You've already used your extra minutes.", P.text3), lp())
        }
    }

    private fun sub(s: String, color: Int = P.text2): TextView =
        text(s, 16f, color).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f); setPadding(0, dp(12), 0, 0) }

    private fun done() {
        if (leaving) return
        leaving = true
        Store.sessions { it.close(pkg) }
        Store.markEnded(pkg)
        Launch.goHome(this)
        finish()
    }

    private fun extend() {
        if (leaving) return
        leaving = true
        val ok = Store.sessions { it.extend(pkg, SessionBook.EXTENSION_MS, SystemClock.elapsedRealtime()) }
        if (ok) FocusGuardService.sessionStarted(pkg) else Launch.goHome(this)
        finish()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
