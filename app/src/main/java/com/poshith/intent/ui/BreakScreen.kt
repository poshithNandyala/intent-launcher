package com.poshith.intent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.poshith.intent.core.Catalog
import com.poshith.intent.data.Apps
import com.poshith.intent.data.Store
import com.poshith.intent.guard.Launch
import java.util.Date
import kotlin.math.cos
import kotlin.math.min

/**
 * The real-break screen, shown instead of the home screen: lights down, a timer, and a way to call.
 * Nothing else to tap, nothing to scroll. Always dark, whatever the theme: a break is lights-out.
 */
class BreakScreen(private val a: BaseActivity, private val onOver: () -> Unit) {
    private val k = DARK
    val view = FrameLayout(a)
    private val time = a.text("", 76f, k.text, Fonts.light(a)).apply {
        fontFeatureSettings = "tnum"
        gravity = Gravity.CENTER
        includeFontPadding = false
    }
    private val ring = PulseRing(a, k.accent)
    private lateinit var barsL: (Bars) -> Unit
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (update()) h.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L + 5)
        }
    }

    init {
        view.setBackgroundColor(BG)
        // Centre: the breathing ring with the time inside it.
        val centre = FrameLayout(a)
        val size = a.dp(296)
        centre.addView(ring, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        val col = a.vbox(Gravity.CENTER_HORIZONTAL)
        col.addView(a.text("REAL BREAK", 12.5f, k.text3, Fonts.medium(a)).apply { letterSpacing = 0.16f; gravity = Gravity.CENTER }, lp())
        col.addView(time, lp().apply { topMargin = a.dp(10) })
        val back = DateFormat.getTimeFormat(a).format(Date(Store.focusUntil))
        col.addView(a.text("Back at $back", 15f, k.text2).apply { gravity = Gravity.CENTER; setPadding(0, a.dp(8), 0, 0) }, lp())

        centre.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        view.addView(centre, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Bottom: calling, WhatsApp if you kept it, and the way out (held, so it's a decision).
        val bottom = a.vbox(Gravity.CENTER_HORIZONTAL)
        // Something to sit with, not to scroll: one line, changing every ten minutes.
        val q = com.poshith.intent.core.Quotes.forMoment(System.currentTimeMillis())
        bottom.addView(a.text("“${q.text}”", 15.5f, k.text2, Fonts.light(a)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f); setPadding(a.dp(12), 0, a.dp(12), 0)
        }, lp())
        if (q.by.isNotBlank()) bottom.addView(a.text(q.by.uppercase(), 11.5f, k.text3, Fonts.medium(a)).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.08f; setPadding(0, a.dp(8), 0, 0)
        }, lp())
        bottom.addView(View(a), LinearLayout.LayoutParams(1, a.dp(34)))
        val row = a.hbox(Gravity.CENTER)
        row.addView(pill("Call") { Launch.dialer(a) }, LinearLayout.LayoutParams(0, a.dp(56), 1f))
        val wa = if (Store.breakKeepWhatsapp) Catalog.WHATSAPP.firstNotNullOfOrNull { Apps.byPkg(it) } else null
        if (wa != null) {
            row.addView(View(a), LinearLayout.LayoutParams(a.dp(12), 1))
            row.addView(pill(wa.label) { Launch.start(a, wa) }, LinearLayout.LayoutParams(0, a.dp(56), 1f))
        }
        bottom.addView(row, lp())
        bottom.addView(a.text("Only calls ring. Everything else waits for you.", 13.5f, k.text3).apply {
            gravity = Gravity.CENTER; setPadding(0, a.dp(16), 0, a.dp(6))
        }, lp())
        bottom.addView(HoldButton(a, "Hold to end the break", 3000, k) { onOver() }.apply {
            background = null
            setTextColor(k.text3)
        }, lp())
        view.addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        barsL = { b -> bottom.setPadding(a.dp(28) + b.left, 0, a.dp(28) + b.right, b.bottom + a.dp(18)) }
        a.onBars(barsL)
    }

    /** The break screen is going away (the break ended, or the home screen is rebuilt). */
    fun dispose() { stop(); a.removeBars(barsL) }

    private fun pill(label: String, onClick: () -> Unit): TextView = a.text(label, 17f, k.text, Fonts.medium(a)).apply {
        gravity = Gravity.CENTER
        background = ripple(rounded(k.surface2, a.dp(28).toFloat()), a.dp(28).toFloat())
        isClickable = true
        setOnClickListener { it.tick(); onClick() }
    }

    fun start() { h.removeCallbacks(tick); h.post(tick); ring.running = true }
    fun stop() { h.removeCallbacks(tick); ring.running = false }

    /** False once the break is over. */
    private fun update(): Boolean {
        val left = Store.focusUntil - System.currentTimeMillis()
        if (!Store.breakActive() || left <= 0) { stop(); onOver(); return false }
        val s = (left + 999) / 1000
        val hrs = s / 3600; val mins = (s % 3600) / 60; val secs = s % 60
        time.text = if (hrs > 0) String.format("%d:%02d:%02d", hrs, mins, secs) else String.format("%02d:%02d", mins, secs)
        time.contentDescription = "${if (hrs > 0) "$hrs hours " else ""}$mins minutes left"
        return true
    }

    companion object {
        const val BG = 0xFF0A0A0B.toInt()
    }
}

/** A slow breathing ring (8 seconds in and out). Still, if you turned animations off. */
class PulseRing(ctx: Context, private val color: Int) : View(ctx) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(1.5f).toFloat() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = SystemClock.uptimeMillis()
    private val moving = ctx.animationsOn()
    var running = false
        set(v) { field = v; invalidate() }

    override fun onDraw(c: Canvas) {
        val t = if (moving) ((SystemClock.uptimeMillis() - start) % 8000L) / 8000f else 0.5f
        val s = ((1 - cos(t * 2 * Math.PI)) / 2).toFloat()
        val r = min(width, height) / 2f * (0.80f + 0.18f * s)
        fill.color = withAlpha(color, 0.04f + 0.05f * s)
        stroke.color = withAlpha(color, 0.18f + 0.22f * s)
        c.drawCircle(width / 2f, height / 2f, r, fill)
        c.drawCircle(width / 2f, height / 2f, r - stroke.strokeWidth, stroke)
        if (running && moving) postInvalidateDelayed(33)
    }

    private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
