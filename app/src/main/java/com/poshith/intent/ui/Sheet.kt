package com.poshith.intent.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.poshith.intent.data.Store

/** ScrollView that never grows taller than a fraction of the screen. */
class CappedScroll(ctx: Context, private val fraction: Float) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cap = (resources.displayMetrics.heightPixels * fraction).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
    }
}

/** A bottom sheet drawn inside the activity (no dialog window), so it follows our theme and insets. */
class Sheet(private val a: BaseActivity, title: String?, subtitle: String? = null) {
    private val scrim = FrameLayout(a)
    private val card = a.vbox()
    val content: LinearLayout = a.vbox()
    var onDismiss: (() -> Unit)? = null
    private var shown = false
    private val closer: () -> Unit = { dismiss() }
    // The dimmed backdrop is dark at the top; the card decides the bottom.
    private val bars = BaseActivity.BarLayer(statusOnDark = true, navOnDark = P.dark)
    private val barListener: (Bars) -> Unit = { b ->
        card.setPadding(card.paddingLeft, card.paddingTop, card.paddingRight, a.dp(12) + b.bottomOrIme)
    }

    init {
        scrim.setBackgroundColor(P.scrim)
        scrim.isClickable = true
        scrim.setOnClickListener { dismiss() }
        val r = a.dp(26).toFloat()
        card.background = GradientDrawable().apply {
            setColor(P.surface)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        card.isClickable = true
        card.setPadding(0, a.dp(10), 0, a.dp(12))
        val handle = View(a).apply { background = rounded(P.text3, a.dp(2).toFloat()); alpha = 0.5f }
        card.addView(handle, LinearLayout.LayoutParams(a.dp(36), a.dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = a.dp(6) })
        if (title != null) card.addView(a.text(title, 21f, P.text, Fonts.medium(a)).apply { setPadding(a.dp(24), a.dp(14), a.dp(24), a.dp(4)) })
        if (subtitle != null) card.addView(a.paragraph(subtitle))
        val sc = CappedScroll(a, 0.78f).apply { isVerticalScrollBarEnabled = false; addView(content) }
        card.addView(sc, lp())
        scrim.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    fun show(): Sheet {
        if (shown) return this
        shown = true
        // A menu opened from search must not sit under the search keyboard.
        hideKeyboard(a, a.host)
        a.host.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        a.pushBack(closer)
        a.onBars(barListener)
        a.pushBarLayer(bars)
        if (a.animationsOn()) {
            scrim.alpha = 0f
            card.translationY = a.resources.displayMetrics.heightPixels * 0.5f
            scrim.animate().alpha(1f).setDuration(180).start()
            card.animate().translationY(0f).setDuration(260).setInterpolator(DecelerateInterpolator(2f)).start()
        }
        return this
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        a.dropBack(closer)
        a.removeBars(barListener)
        a.dropBarLayer(bars)
        hideKeyboard(a, scrim)
        val done = { (scrim.parent as? ViewGroup)?.removeView(scrim); onDismiss?.invoke(); Unit }
        if (a.animationsOn()) {
            scrim.animate().alpha(0f).setDuration(160).withEndAction(done).start()
            card.animate().translationY(card.height.toFloat()).setDuration(180).start()
        } else done()
    }

    /** A tappable line in the sheet. Closes the sheet first unless keepOpen. */
    fun item(label: String, sub: String? = null, color: Int = P.text, keepOpen: Boolean = false, onClick: () -> Unit): Sheet {
        val row = a.row(label, sub, null) { if (!keepOpen) dismiss(); onClick() }
        ((row.getChildAt(0) as? LinearLayout)?.getChildAt(0) as? TextView)?.setTextColor(color)
        content.addView(row, lp())
        return this
    }

    fun add(v: View, padH: Int = 24): Sheet {
        val wrap = FrameLayout(a).apply { setPadding(a.dp(padH), a.dp(6), a.dp(padH), a.dp(6)); addView(v) }
        content.addView(wrap, lp())
        return this
    }
}

fun hideKeyboard(ctx: Context, v: View) {
    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(v.windowToken, 0)
}

fun showKeyboard(v: View) {
    v.requestFocus()
    v.post {
        (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }
}

/** A calm single-line text field. */
fun Context.field(hint: String, initial: String = "", caps: Boolean = true): EditText = EditText(this).apply {
    setText(initial)
    setSelection(text.length)
    this.hint = hint
    textSize = 18f
    typeface = Fonts.regular(this@field)
    setTextColor(P.text)
    setHintTextColor(P.text3)
    isSingleLine = true
    inputType = InputType.TYPE_CLASS_TEXT or (if (caps) InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else 0)
    background = rounded(P.surface2, dp(14).toFloat())
    setPadding(dp(16), dp(14), dp(16), dp(14))
}

fun EditText.onText(fn: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) { fn(s?.toString() ?: "") }
})

// ------------------------------------------------------------------------------------- friction

/**
 * Making an app EASIER to open waits a few seconds (Settings > Rule changes). Making it harder is
 * instant. This is the commitment-device idea from Allcott et al. (2022): decide while calm.
 */
/**
 * Making a rule looser waits a few seconds (you can still do it, just not on impulse). [onCancel] runs if
 * you keep it as it is, so a switch or chip that already moved can show the real setting again.
 */
fun BaseActivity.confirmLoosen(title: String, message: String, confirmLabel: String = "Yes, change it",
                               onCancel: (() -> Unit)? = null, action: () -> Unit) {
    // Strict mode: no loosening today at all (tomorrow's you decides).
    if (Store.strictActive()) { strictBlocksLoosening(onCancel); return }
    val secs = Store.coolOffSec
    if (secs <= 0) { action(); return }
    val s = Sheet(this, title, message)
    var confirmed = false
    val btn = button("Wait $secs s", Kind.QUIET) { }
    styleButton(btn, Kind.QUIET, enabled = false)
    val h = Handler(Looper.getMainLooper())
    var left = secs
    val tick = object : Runnable {
        override fun run() {
            left--
            if (left <= 0) {
                btn.text = confirmLabel
                styleButton(btn, Kind.PRIMARY, enabled = true)
                btn.setOnClickListener { it.tick(); confirmed = true; s.dismiss(); action() }
            } else {
                btn.text = "Wait $left s"
                h.postDelayed(this, 1000)
            }
        }
    }
    s.onDismiss = { h.removeCallbacks(tick); if (!confirmed) onCancel?.invoke() }
    s.add(paragraph("The pause is part of the rule you set. You can still change it, just not on impulse.", 14f, P.text3), 0)
    s.add(btn)
    s.add(button("Keep it as it is", Kind.PLAIN) { s.dismiss() })
    s.show()
    h.postDelayed(tick, 1000)
}

/** Unlock something you deliberately locked by typing a phrase (stops autopilot, not you). */
fun BaseActivity.confirmPhrase(title: String, message: String, phrase: String, confirmLabel: String, action: () -> Unit) {
    val s = Sheet(this, title, message)
    val f = field("Type: $phrase", caps = false)
    val btn = button(confirmLabel, Kind.PRIMARY) { s.dismiss(); action() }
    styleButton(btn, Kind.PRIMARY, enabled = false)
    f.onText { styleButton(btn, Kind.PRIMARY, enabled = it.trim().equals(phrase, ignoreCase = true)) }
    s.add(text("Type “$phrase”", 14f, P.text2), 24)
    s.add(f)
    s.add(btn)
    s.add(button("Never mind", Kind.PLAIN) { s.dismiss() })
    s.show()
    showKeyboard(f)
}

/** A button you have to hold down. Used to end a focus session early. */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class HoldButton(
    ctx: Context,
    private val label: String,
    private val holdMs: Long,
    pal: Palette = P,
    private val onDone: () -> Unit,
) : TextView(ctx) {
    private var progress = 0f
    private var holding = false
    private var lastTick = 0L
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.accentSoft }
    private val rect = RectF()

    /** Timed on the real clock, not with an animator: turning animations off in Android can't skip the hold. */
    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            val dt = (now - lastTick).coerceAtLeast(0)
            lastTick = now
            progress = if (holding) (progress + dt / holdMs.toFloat()).coerceAtMost(1f)
            else (progress - dt / DRAIN_MS).coerceAtLeast(0f)
            invalidate()
            if (holding && progress >= 1f) {
                holding = false
                progress = 0f
                text = label
                thud()
                onDone()
                invalidate()
                return
            }
            if (holding || progress > 0f) postDelayed(this, FRAME_MS)
        }
    }

    init {
        text = label
        textSize = 15f
        typeface = Fonts.medium(ctx)
        gravity = Gravity.CENTER
        minHeight = ctx.dp(48)
        setPadding(ctx.dp(20), ctx.dp(10), ctx.dp(20), ctx.dp(10))
        setTextColor(pal.text)
        background = rounded(android.graphics.Color.TRANSPARENT, ctx.dp(24).toFloat(), pal.line, ctx.dp(1.2f))
        contentDescription = "$label. Press and hold."
        setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { setHolding(true); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { setHolding(false); true }
                else -> true
            }
        }
        // TalkBack users get a normal double-tap action.
        setOnClickListener { onDone() }
    }

    private fun setHolding(on: Boolean) {
        holding = on
        text = if (on) "Keep holding…" else label
        lastTick = SystemClock.uptimeMillis()
        removeCallbacks(ticker)
        post(ticker)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        holding = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val FRAME_MS = 16L
        const val DRAIN_MS = 200f
    }

    override fun onDraw(canvas: Canvas) {
        if (progress > 0f) {
            val r = height / 2f
            rect.set(0f, 0f, width * progress, height.toFloat())
            canvas.save()
            canvas.clipRect(rect)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, fill)
            canvas.restore()
        }
        super.onDraw(canvas)
    }
}
