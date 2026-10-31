package com.poshith.intent.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import com.poshith.intent.data.Store

/* ------------------------------------------------------------------------------------------------
 * Intent's tiny design system. Calm on purpose: one accent, warm neutrals, no badges, generous space.
 * Every color pair used for text meets WCAG AA contrast on its background.
 * ---------------------------------------------------------------------------------------------- */

class Palette(
    val dark: Boolean,
    val bg: Int,
    val surface: Int,
    val surface2: Int,
    val text: Int,
    val text2: Int,
    val text3: Int,
    val line: Int,
    val accent: Int,
    val onAccent: Int,
    val accentSoft: Int,
    val warn: Int,
    val ripple: Int,
    val scrim: Int,
)

val DARK = Palette(
    dark = true,
    bg = 0xFF0C0C0D.toInt(), surface = 0xFF161618.toInt(), surface2 = 0xFF1F1F22.toInt(),
    text = 0xFFEDEAE4.toInt(), text2 = 0xFF9C9891.toInt(), text3 = 0xFF6E6A64.toInt(),
    line = 0x1FFFFFFF, accent = 0xFFA9C5A0.toInt(), onAccent = 0xFF0F1A10.toInt(),
    accentSoft = 0x26A9C5A0, warn = 0xFFE3B07A.toInt(), ripple = 0x22FFFFFF, scrim = 0xB3000000.toInt(),
)

val LIGHT = Palette(
    dark = false,
    bg = 0xFFF6F3ED.toInt(), surface = 0xFFFFFFFF.toInt(), surface2 = 0xFFECE8E0.toInt(),
    text = 0xFF1B1A18.toInt(), text2 = 0xFF66635D.toInt(), text3 = 0xFF8E8A83.toInt(),
    line = 0x1F000000, accent = 0xFF3F6B46.toInt(), onAccent = 0xFFFFFFFF.toInt(),
    accentSoft = 0x1F3F6B46, warn = 0xFF8A5212.toInt(), ripple = 0x1A000000, scrim = 0x80000000.toInt(),
)

object Theme {
    var p: Palette = DARK
        private set

    fun resolve(ctx: Context): Palette = when (Store.theme) {
        "dark" -> DARK
        "light" -> LIGHT
        else -> if ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
    }

    /** Edge-to-edge window with transparent bars and the right icon colors. */
    fun apply(a: Activity) {
        p = resolve(a)
        val w = a.window
        w.setBackgroundDrawable(ColorDrawable(p.bg))
        legacyBars(w)
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (p.dark) 0 else light, light)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyBars(w: android.view.Window) {
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT < 30) {
            var f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (!p.dark) f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            w.decorView.systemUiVisibility = f
        }
    }
}

/** One shared background thread for small reads (usage stats), never shut down. */
object Bg {
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "intent-bg").apply { isDaemon = true } }
    fun run(block: () -> Unit) { try { io.execute(block) } catch (_: Exception) {} }
}

/** Shorthand for the current palette. */
val P: Palette get() = Theme.p

object Fonts {
    private var cache: Array<Typeface>? = null
    private var cachedFor = ""

    private fun load(ctx: Context): Array<Typeface> {
        val want = Store.font
        cache?.let { if (cachedFor == want) return it }
        val sys = arrayOf(
            Typeface.create("sans-serif-light", Typeface.NORMAL),
            Typeface.create("sans-serif", Typeface.NORMAL),
            Typeface.create("sans-serif-medium", Typeface.NORMAL),
        )
        val set = if (want == "system") sys else try {
            val am = ctx.applicationContext.assets
            arrayOf(
                Typeface.createFromAsset(am, "fonts/inter_light.ttf"),
                Typeface.createFromAsset(am, "fonts/inter_regular.ttf"),
                Typeface.createFromAsset(am, "fonts/inter_medium.ttf"),
            )
        } catch (_: Exception) { sys }
        cache = set; cachedFor = want
        return set
    }

    fun light(ctx: Context) = load(ctx)[0]
    fun regular(ctx: Context) = load(ctx)[1]
    fun medium(ctx: Context) = load(ctx)[2]
    val serif: Typeface get() = Typeface.SERIF
}

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun Context.animationsOn(): Boolean =
    try { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f } catch (_: Exception) { true }

fun View.tick() { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
fun View.thud() { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

fun ripple(content: Drawable?, radius: Float = 0f): RippleDrawable {
    val mask = rounded(Color.WHITE, radius)
    return RippleDrawable(ColorStateList.valueOf(P.ripple), content, mask)
}

// ---------------------------------------------------------------------------------------- builders

fun Context.text(
    s: CharSequence,
    size: Float = 16f,
    color: Int = P.text,
    face: Typeface = Fonts.regular(this),
    lines: Int = 0,
): TextView = TextView(this).apply {
    text = s
    textSize = size
    setTextColor(color)
    typeface = face
    includeFontPadding = true
    if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
}

fun Context.vbox(gravity: Int = Gravity.START): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    this.gravity = gravity
}

fun Context.hbox(gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    this.gravity = gravity
}

fun Context.space(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
fun Context.flex(): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
fun Context.vflex(): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) }

fun Context.divider(): View = View(this).apply {
    setBackgroundColor(P.line)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, dp(0.6f)))
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight)

enum class Kind { PRIMARY, QUIET, OUTLINE, PLAIN }

/** Big, calm button. Minimum 52 dp tall (touch targets stay above 48 dp). */
fun Context.button(label: String, kind: Kind = Kind.PRIMARY, onClick: (View) -> Unit): TextView = TextView(this).apply {
    text = label
    textSize = 16f
    typeface = Fonts.medium(this@button)
    gravity = Gravity.CENTER
    minHeight = dp(52)
    setPadding(dp(20), dp(12), dp(20), dp(12))
    isClickable = true
    isFocusable = true
    styleButton(this, kind, enabled = true)
    setOnClickListener { if (isEnabled) { it.tick(); onClick(it) } }
}

fun styleButton(b: TextView, kind: Kind, enabled: Boolean) {
    val r = b.context.dp(26).toFloat()
    b.isEnabled = enabled
    val alpha = if (enabled) 1f else 0.38f
    when (kind) {
        Kind.PRIMARY -> { b.background = ripple(rounded(P.accent, r), r); b.setTextColor(P.onAccent) }
        Kind.QUIET -> { b.background = ripple(rounded(P.surface2, r), r); b.setTextColor(P.text) }
        Kind.OUTLINE -> { b.background = ripple(rounded(Color.TRANSPARENT, r, P.line, b.context.dp(1.2f)), r); b.setTextColor(P.text) }
        Kind.PLAIN -> { b.background = ripple(null, r); b.setTextColor(P.text2) }
    }
    b.alpha = alpha
}

/** A selectable pill. */
class Chip(ctx: Context, label: String) : TextView(ctx) {
    var on = false
        set(v) { field = v; restyle() }

    init {
        text = label
        textSize = 15f
        typeface = Fonts.regular(ctx)
        gravity = Gravity.CENTER
        minHeight = ctx.dp(44)
        minWidth = ctx.dp(44)
        setPadding(ctx.dp(16), ctx.dp(9), ctx.dp(16), ctx.dp(9))
        isClickable = true
        restyle()
    }

    private fun restyle() {
        val r = context.dp(22).toFloat()
        background = if (on) ripple(rounded(P.accentSoft, r, P.accent, context.dp(1.2f)), r)
        else ripple(rounded(Color.TRANSPARENT, r, P.line, context.dp(1f)), r)
        setTextColor(if (on) P.text else P.text2)
    }
}

/** A row of chips that wraps onto new lines. */
class Flow(ctx: Context, private val centered: Boolean = false) : ViewGroup(ctx) {
    private val gap = ctx.dp(8)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += rowH + gap; rowH = 0 }
            x += c.measuredWidth + gap
            rowH = maxOf(rowH, c.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        // Split into rows first, so each row can be centered.
        val rows = ArrayList<ArrayList<View>>()
        var cur = ArrayList<View>(); var x = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            if (x > 0 && x + c.measuredWidth > maxW) { rows += cur; cur = ArrayList(); x = 0 }
            cur += c; x += c.measuredWidth + gap
        }
        if (cur.isNotEmpty()) rows += cur
        var y = paddingTop
        for (row in rows) {
            val rowW = row.sumOf { it.measuredWidth } + gap * (row.size - 1)
            val offset = if (centered) ((maxW - rowW) / 2).coerceAtLeast(0) else 0
            var cx = offset
            var rowH = 0
            for (c in row) {
                val left = if (rtl) (r - l) - paddingRight - cx - c.measuredWidth else paddingLeft + cx
                c.layout(left, y, left + c.measuredWidth, y + c.measuredHeight)
                cx += c.measuredWidth + gap
                rowH = maxOf(rowH, c.measuredHeight)
            }
            y += rowH + gap
        }
    }
}

/** Settings-style row: title, optional subtitle, optional value on the right. */
fun Context.row(title: String, subtitle: String? = null, value: String? = null, onClick: ((View) -> Unit)?): LinearLayout {
    val row = hbox().apply {
        minimumHeight = dp(60)
        setPadding(dp(24), dp(12), dp(24), dp(12))
        if (onClick != null) {
            background = ripple(null)
            isClickable = true
            setOnClickListener { it.tick(); onClick(it) }
        }
    }
    val col = vbox()
    col.addView(text(title, 17f, P.text))
    if (!subtitle.isNullOrBlank()) col.addView(text(subtitle, 14f, P.text2).apply { setPadding(0, dp(2), 0, 0) })
    row.addView(col, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    if (value != null) row.addView(text(value, 15f, P.text2).apply { setPadding(dp(16), 0, 0, 0); tag = "value" })
    return row
}

fun Context.toggleRow(title: String, subtitle: String?, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
    var state = initial
    lateinit var pill: TextView
    val row = row(title, subtitle, null) { v -> state = !state; paintToggle(pill, state); onChange(state) }
    pill = text(if (state) "On" else "Off", 14f, P.text, Fonts.medium(this)).apply {
        gravity = Gravity.CENTER
        minWidth = dp(56)
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }
    paintToggle(pill, state)
    row.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(16) })
    return row
}

private fun paintToggle(t: TextView, on: Boolean) {
    val r = t.context.dp(16).toFloat()
    t.text = if (on) "On" else "Off"
    t.background = if (on) rounded(P.accent, r) else rounded(Color.TRANSPARENT, r, P.line, t.context.dp(1.2f))
    t.setTextColor(if (on) P.onAccent else P.text2)
}

fun Context.sectionTitle(s: String): TextView = text(s.uppercase(), 12.5f, P.text3, Fonts.medium(this)).apply {
    letterSpacing = 0.08f
    setPadding(dp(24), dp(28), dp(24), dp(8))
}

fun Context.paragraph(s: CharSequence, size: Float = 15.5f, color: Int = P.text2): TextView = text(s, size, color).apply {
    setLineSpacing(0f, 1.25f)
    setPadding(dp(24), dp(4), dp(24), dp(4))
}

fun Context.scroller(child: View): ScrollView = ScrollView(this).apply {
    isFillViewport = true
    isVerticalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    addView(child, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}

// ------------------------------------------------------------------------------------------ insets

class Bars(val top: Int, val bottom: Int, val left: Int, val right: Int, val ime: Int) {
    val bottomOrIme: Int get() = maxOf(bottom, ime)
}

@Suppress("DEPRECATION")
fun readBars(ins: WindowInsets): Bars =
    if (Build.VERSION.SDK_INT >= 30) {
        val sys = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val ime = ins.getInsets(WindowInsets.Type.ime())
        Bars(sys.top, sys.bottom, sys.left, sys.right, ime.bottom)
    } else {
        Bars(ins.systemWindowInsetTop, ins.systemWindowInsetBottom, ins.systemWindowInsetLeft, ins.systemWindowInsetRight, 0)
    }

// ------------------------------------------------------------------------------------ base activity

/**
 * Every screen: themed edge-to-edge window, one FrameLayout host, and a back stack for sheets and
 * panels that works with Android 13+ predictive back as well as older phones.
 */
abstract class BaseActivity : Activity() {
    lateinit var host: FrameLayout
        private set
    var bars = Bars(0, 0, 0, 0, 0)
        private set
    private val barListeners = ArrayList<(Bars) -> Unit>()
    private val backStack = ArrayList<() -> Unit>()
    private var backCallback: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Store.init(this)
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        host = FrameLayout(this)
        host.setBackgroundColor(P.bg)
        host.setOnApplyWindowInsetsListener { _, ins ->
            bars = readBars(ins)
            barListeners.toList().forEach { it(bars) }
            ins
        }
        setContentView(host)
        if (Build.VERSION.SDK_INT >= 33) {
            val cb = OnBackInvokedCallback { handleBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backCallback = cb
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) (backCallback as? OnBackInvokedCallback)?.let {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        super.onDestroy()
    }

    fun onBars(l: (Bars) -> Unit) { barListeners += l; l(bars); host.requestApplyInsets() }
    fun removeBars(l: (Bars) -> Unit) { barListeners -= l }

    fun pushBack(closer: () -> Unit) { backStack += closer }
    fun dropBack(closer: () -> Unit) { backStack.remove(closer) }
    fun hasBackLayers() = backStack.isNotEmpty()
    fun layerCount() = backStack.size
    fun closeAllLayers() { while (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1).invoke() }

    /** Back with nothing open. Default: close this screen. */
    open fun onBackRoot() = finish()

    fun handleBack() {
        if (backStack.isNotEmpty()) backStack.removeAt(backStack.size - 1).invoke() else onBackRoot()
    }

    @Deprecated("Android 12 and older")
    override fun onBackPressed() { handleBack() }
}
