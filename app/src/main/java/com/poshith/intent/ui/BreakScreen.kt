package com.poshith.intent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.poshith.intent.data.Store
import com.poshith.intent.guard.Launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * The real-break screen, shown instead of the home screen: lights down, no timer, and a way to call.
 * Nothing else to tap, nothing to scroll. Always dark, whatever the theme: a break is lights-out.
 *
 * Layout: the breathing circle gets only the height left above the quote and the buttons and sizes
 * itself to fit, so on a short screen or with large text it shrinks instead of covering them.
 */
class BreakScreen(private val a: BaseActivity, private val onOver: () -> Unit) {
    private val k = DARK
    val view = FrameLayout(a)
    // No timer: watching the minutes is still watching the phone. You'll know when it's over.
    private val words = a.text("Be here.", 40f, k.text, Fonts.light(a)).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
    }
    private val ring = PulseRing(a, k.accent)
    private val face = Face()
    private lateinit var barsL: (Bars) -> Unit
    private val h = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (update()) h.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L + 5)
        }
    }

    /** The space above the buttons: the circle, as big as fits (at most 320 dp), the words inside it. */
    private inner class Face : FrameLayout(a) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val w = MeasureSpec.getSize(widthSpec)
            val h = MeasureSpec.getSize(heightSpec) - paddingTop - paddingBottom
            val side = minOf(w - a.dp(64), h - a.dp(20), a.dp(320)).coerceAtLeast(a.dp(150))
            val lp = ring.layoutParams as FrameLayout.LayoutParams
            if (lp.width != side) { lp.width = side; lp.height = side }
            // The words are part of the drawing: they scale with the circle, not with the font size.
            val px = side * 0.14f
            if (abs(words.textSize - px) > 0.5f) words.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
            super.onMeasure(widthSpec, heightSpec)
        }
    }

    init {
        view.setBackgroundColor(BG)
        val page = a.vbox()
        view.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Top: the breathing circle, centred in the space that's left.
        face.addView(ring, FrameLayout.LayoutParams(a.dp(296), a.dp(296), Gravity.CENTER))
        val col = a.vbox(Gravity.CENTER_HORIZONTAL)
        col.addView(a.text("REAL BREAK", 12.5f, k.text3, Fonts.medium(a)).apply { letterSpacing = 0.16f; gravity = Gravity.CENTER }, lp())
        col.addView(words, lp().apply { topMargin = a.dp(12) })
        col.addView(a.text("The phone will tell you when it's over.", 14f, k.text3).apply {
            gravity = Gravity.CENTER; setPadding(a.dp(8), a.dp(10), a.dp(8), 0)
        }, lp())
        face.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        page.addView(face, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Below it, never under it: one line to sit with, then calling and the way out (held).
        val bottom = a.vbox(Gravity.CENTER_HORIZONTAL)
        val q = com.poshith.intent.core.Quotes.forMoment(System.currentTimeMillis())
        bottom.addView(a.text("“${q.text}”", 15f, k.text2, Fonts.light(a)).apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f); setPadding(a.dp(8), 0, a.dp(8), 0); maxLines = 4
            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
        }, lp())
        if (q.by.isNotBlank()) bottom.addView(a.text(q.by.uppercase(), 11f, k.text3, Fonts.medium(a)).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.1f; setPadding(0, a.dp(8), 0, 0)
        }, lp())
        bottom.addView(View(a), LinearLayout.LayoutParams(1, a.dp(28)))
        // One thing to tap: calling. Everything else waits.
        bottom.addView(pill("Call") { Launch.dialer(a) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(56)))
        bottom.addView(a.text("Only calls get through. Everything else waits for you.", 13.5f, k.text3).apply {
            gravity = Gravity.CENTER; setPadding(0, a.dp(14), 0, a.dp(2))
        }, lp())
        bottom.addView(HoldButton(a, "Hold to end the break", 6000, k) { onOver() }.apply {
            background = null
            setTextColor(k.text3)
        }, lp())
        page.addView(bottom, lp())
        barsL = { b ->
            face.setPadding(0, b.top + a.dp(12), 0, a.dp(8))
            bottom.setPadding(a.dp(28) + b.left, 0, a.dp(28) + b.right, b.bottom + a.dp(12))
        }
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
        return true
    }

    companion object {
        const val BG = 0xFF0A0A0B.toInt()
    }
}

/**
 * A slow breathing circle (8 seconds in and out): a soft glow and a fine outline that swell and settle
 * together. Still, if animations are off.
 */
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
        val r = min(width, height) / 2f * (0.82f + 0.16f * s)
        fill.color = withAlpha(color, 0.035f + 0.05f * s)
        stroke.color = withAlpha(color, 0.16f + 0.2f * s)
        c.drawCircle(width / 2f, height / 2f, r, fill)
        c.drawCircle(width / 2f, height / 2f, r - stroke.strokeWidth, stroke)
        if (running && moving) postInvalidateDelayed(33)
    }

    private fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
