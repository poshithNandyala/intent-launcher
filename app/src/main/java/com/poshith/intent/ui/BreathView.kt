package com.poshith.intent.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * One slow breath: the circle grows for 4 s (in) and shrinks for 4 s (out). The thin outer arc fills
 * over the pause, so you can see when "Open" becomes available.
 *
 * Everything runs on real time, not on Android's animation speed: the pause lasts exactly as long as
 * your rule says, and the words ("Breathe in", "Breathe out") follow the clock too. With animations
 * turned off (reduce motion) the circle stays still and the words don't pace you: "Breathe naturally".
 * When the pause is over the circle settles and stays still.
 */
class BreathView(ctx: Context) : View(ctx) {

    enum class Phase(val words: String) { IN("Breathe in"), OUT("Breathe out"), NATURAL("Breathe naturally") }

    var onPhase: ((Phase) -> Unit)? = null
    var onDone: (() -> Unit)? = null

    private val cycleMs = 8_000L
    private var progress = 0f
    private var startedAt = 0L
    private var pauseMs = 1L
    private var moving = true
    private var lastPhase: Phase? = null
    private val rect = RectF()

    /** The pause is over: the circle settles and stays still (no motion that never ends). */
    private val done = Runnable { progress = 1f; settled = true; removeCallbacks(ticker); invalidate(); onDone?.invoke() }
    private var settled = false

    /** The progress arc and the words, from the clock (20 times a second is plenty for both). */
    private val ticker = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            progress = (elapsed.toFloat() / pauseMs).coerceIn(0f, 1f)
            phase(if (!moving) Phase.NATURAL else if (elapsed % cycleMs < cycleMs / 2) Phase.IN else Phase.OUT)
            invalidate()
            if (progress < 1f) postDelayed(this, 50)
        }
    }

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P.accentSoft; style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = P.line; style = Paint.Style.STROKE; strokeWidth = ctx.dp(1.5f).toFloat()
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = P.accent; style = Paint.Style.STROKE; strokeWidth = ctx.dp(2.5f).toFloat(); strokeCap = Paint.Cap.ROUND
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = P.accent; style = Paint.Style.FILL }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun phase(p: Phase) {
        if (p == lastPhase) return
        lastPhase = p
        onPhase?.invoke(p)
    }

    fun start(pauseMs: Long) {
        stop()
        this.pauseMs = pauseMs.coerceAtLeast(1)
        startedAt = SystemClock.uptimeMillis()
        progress = 0f
        lastPhase = null
        settled = false
        moving = context.animationsOn()
        post(ticker)
        postDelayed(done, this.pauseMs)
    }

    /** Stops the breathing (the words stay as they are); the pause screen calls it once it's done. */
    fun stop() {
        removeCallbacks(ticker)
        removeCallbacks(done)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f - arc.strokeWidth
        if (outer <= 0f) return
        canvas.drawCircle(cx, cy, outer, ring)
        rect.set(cx - outer, cy - outer, cx + outer, cy + outer)
        if (progress > 0f) canvas.drawArc(rect, -90f, 360f * progress, false, arc)
        val breathing = moving && startedAt > 0 && !settled
        val breath = if (breathing) {
            val f = ((SystemClock.uptimeMillis() - startedAt) % cycleMs).toFloat() / cycleMs
            0.5f - 0.5f * cos(2 * PI * f).toFloat()
        } else 0.7f
        canvas.drawCircle(cx, cy, outer * (0.42f + 0.46f * breath), glow)
        canvas.drawCircle(cx, cy, context.dp(4).toFloat(), core)
        if (breathing && isAttachedToWindow) postInvalidateOnAnimation()
    }
}
