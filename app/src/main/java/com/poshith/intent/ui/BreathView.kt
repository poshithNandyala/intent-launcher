package com.poshith.intent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * One slow breath: the circle grows for 4 s (in) and shrinks for 4 s (out). The thin outer arc fills
 * over the pause, so you can see when "Open" becomes available. The pause itself is timed in real
 * time (not by animation), so it lasts exactly as long as your rule says even if you changed
 * Android's animation speed. With animations turned off, the circle stays still.
 */
class BreathView(ctx: Context) : View(ctx) {
    var onPhase: ((inhale: Boolean) -> Unit)? = null
    var onDone: (() -> Unit)? = null

    private val cycleMs = 8_000L
    private var breath = 0.6f
    private var progress = 0f
    private var startedAt = 0L
    private var pauseMs = 1L
    private var lastInhale: Boolean? = null
    private var breathAnim: ValueAnimator? = null
    private val rect = RectF()

    private val done = Runnable { progress = 1f; invalidate(); onDone?.invoke() }
    private val ticker = object : Runnable {
        override fun run() {
            progress = ((SystemClock.uptimeMillis() - startedAt).toFloat() / pauseMs).coerceIn(0f, 1f)
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

    fun start(pauseMs: Long) {
        stop()
        this.pauseMs = pauseMs.coerceAtLeast(1)
        startedAt = SystemClock.uptimeMillis()
        progress = 0f
        if (context.animationsOn()) {
            breathAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = cycleMs
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    breath = 0.5f - 0.5f * cos(2 * PI * f).toFloat()
                    val inhale = f < 0.5f
                    if (inhale != lastInhale) { lastInhale = inhale; onPhase?.invoke(inhale) }
                    invalidate()
                }
                start()
            }
        } else {
            breath = 0.7f
            onPhase?.invoke(true)
        }
        post(ticker)
        postDelayed(done, this.pauseMs)
    }

    fun stop() {
        breathAnim?.cancel(); breathAnim = null
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
        val r = outer * (0.42f + 0.46f * breath)
        canvas.drawCircle(cx, cy, r, glow)
        canvas.drawCircle(cx, cy, context.dp(4).toFloat(), core)
    }
}
