package com.confused.anikuta.core.ads

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.confused.anikuta.core.common.Logger
import kotlin.math.ceil
import kotlin.math.min

/**
 * The theme colors for the floating return pill. Plain ARGB ints (not
 * Compose `Color`s) because the pill lives in a system overlay WINDOW
 * rendered by classic Views — there is no Compose theme there.
 *
 * The interstitial captures these from `MaterialTheme.colorScheme` at the
 * moment the smart link opens, so the pill always matches the user's active
 * theme palette (their chosen colors, dark/light, accent variants).
 */
data class ReturnPillColors(
    /** Pill background (theme surface). */
    val container: Int,
    /** Pill text + ring track (theme onSurface). */
    val content: Int,
    /** Ring progress + the "Go back" chip background (theme primary). */
    val accent: Int,
    /** "Go back" chip text (theme onPrimary). */
    val onAccent: Int,
    /** The early-return X (theme error) — D-454. */
    val error: Int,
)

/**
 * The floating return pill itself — a rounded, theme-colored capsule that
 * floats over the BROWSER while the user completes the smart-link visit
 * (D-443 → D-448 → rebuilt in D-454 after the v1.1.5 device round).
 * Classic Views, NOT Compose: the pill is hosted in a TYPE_APPLICATION_OVERLAY
 * window which has no lifecycle owner / saved-state registry.
 *
 * # THE JITTER FIX (D-454 — the v1.1.5 "stuttering / glitchy" report)
 *
 * The v1.1.3-45 pill resized its WINDOW twice per cycle (grow + exit). A
 * WindowManager surface resize is asynchronous to the animations driving it —
 * the frames in between render against the old surface size, which read as
 * stutter. The rebuilt pill resizes its window EXACTLY ZERO times:
 *
 * 1. The view tree is built in the READY shape (label reading the longer
 *    "You can go back now" + the chip present) and MEASURED before the
 *    window is added — the controller sizes the window to that measured
 *    size once, and the surface never changes again.
 * 2. The counting presentation is pure transforms inside the fixed window:
 *    the chip is GONE and the capsule is scaled to [COUNT_SCALE]. The empty
 *    window margin around the smaller capsule is non-interactive while
 *    counting (FLAG_NOT_TOUCHABLE — everything passes through to the
 *    browser; the pill is not tappable while counting anyway).
 * 3. The grow swaps to the ready presentation with ONE in-surface relayout,
 *    visually compensated in the same frame (the capsule's scale is set to
 *    keep its on-screen width identical the frame the layout widens), then
 *    a pure property animation springs the capsule to [READY_SCALE]. No
 *    surface resize, no clipped frames — the overshoot can only grow INTO
 *    the padding headroom the window already has.
 * 4. The exit is pure property animation too: the label + chip fade/collapse,
 *    the capsule's background dissolves, and the ring (check = success,
 *    X = returned-too-early) pops like a bubble. No relayout, no resize.
 *
 * # Countdown sync (D-454 — the "returned early but saw success" report)
 *
 * The coordinator's outside-clock starts at the app's ON_STOP (the browser
 * actually covering us), while v1.1.5's ring started at the Continue tap —
 * up to ~0.5s AHEAD, so the checkmark could appear while the coordinator
 * would still judge the return as too-early. The rebuilt ring does NOT
 * count on its own: [startCountdown] is invoked by the controller on the
 * app's ON_STOP — the same signal the coordinator measures with — so the
 * checkmark can never precede a passing return. And when a return IS
 * judged too early, [beginExit] renders the RED X (theme error) instead of
 * the green check (D-454).
 */
class ReturnPillView(
    context: Context,
    private val pillColors: ReturnPillColors,
    /** The smart-link's min-time-outside threshold — the ring's full length. */
    private val totalMs: Long,
    /** Invoked when the user taps the pill IN THE READY STATE. */
    private val onGoBack: () -> Unit,
    /** Invoked when the countdown completes (the controller clears FLAG_NOT_TOUCHABLE). */
    private val onReady: () -> Unit,
) : FrameLayout(context) {

    /** The view-side context (interpolators resolve against it in methods). */
    private val viewContext: Context = context

    private val capsule: LinearLayout
    private val ring: CountdownRingView
    private val label: TextView
    private var chipView: TextView? = null
    private val capsuleBackground: GradientDrawable

    private val mainHandler = Handler(Looper.getMainLooper())

    private var pulseAnimator: ObjectAnimator? = null
    private var pulseYAnimator: ObjectAnimator? = null
    private var entranceAnimator: AnimatorSet? = null
    private var growAnimator: AnimatorSet? = null
    private var isReady = false
    private var isExiting = false

    /** The laid-out capsule width in the counting presentation (for the grow's
     * width-compensation — the visual width must not jump when the layout widens). */
    private var countingWidth = 0

    /** Shared dp→px helper. */
    private val dp: (Int) -> Float = { v: Int ->
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics)
    }

    init {
        // D-452/D-454 headroom: the grow overshoot + pulse enlarge the capsule
        // BEYOND its laid-out bounds; the (fixed) window carries this padding
        // so every scale stays fully inside the surface.
        val padH = dp(20).toInt()
        val padV = dp(8).toInt()
        setPadding(padH, padV, padH, padV)

        capsuleBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(30)
            setColor(pillColors.container)
            setStroke(dp(1).toInt(), withAlpha(pillColors.content, 0x14))
        }

        // LayoutTransition animates the ONE in-surface relayout (chip
        // GONE→VISIBLE at the grow). No window resize is ever involved.
        val transition = LayoutTransition().apply {
            setDuration(LayoutTransition.APPEARING, 240)
            setDuration(LayoutTransition.CHANGE_APPEARING, 260)
            setInterpolator(LayoutTransition.APPEARING, OvershootInterpolator(1.2f))
            setInterpolator(LayoutTransition.CHANGE_APPEARING,
                AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in))
        }

        capsule = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = capsuleBackground
            setPadding(dp(12).toInt(), dp(8).toInt(), dp(12).toInt(), dp(8).toInt())
            layoutTransition = transition
        }

        ring = CountdownRingView(context, pillColors, totalMs)

        label = TextView(context).apply {
            setTextColor(pillColors.content)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            text = COUNTDOWN_LABEL
            setPadding(dp(10).toInt(), 0, dp(2).toInt(), 0)
        }

        capsule.addView(
            ring,
            LinearLayout.LayoutParams(dp(34).toInt(), dp(34).toInt()).apply { gravity = Gravity.CENTER_VERTICAL },
        )
        capsule.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // The chip EXISTS from construction (it sizes the ready layout used
        // for the window's fixed size) but starts GONE — the counting capsule
        // lays out narrow and is centered inside the fixed window.
        chipView = TextView(context).apply {
            text = "Go back"
            setTextColor(pillColors.onAccent)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22)
                setColor(pillColors.accent)
            }
            setPadding(dp(14).toInt(), dp(8).toInt(), dp(14).toInt(), dp(8).toInt())
        }
        capsule.addView(
            chipView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )

        addView(
            capsule,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )

        // MEASURE the ready shape (the widest this pill ever lays out) so the
        // controller can size the window to it ONCE — the surface then never
        // resizes for the pill's whole life (the D-454 jitter fix).
        label.text = READY_LABEL
        chipView?.visibility = VISIBLE
        measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        // Switch to the counting presentation (pure layout state inside the
        // fixed window — no surface change).
        label.text = COUNTDOWN_LABEL
        chipView?.visibility = GONE
        capsule.scaleX = COUNT_SCALE
        capsule.scaleY = COUNT_SCALE

        startEntrance()
    }

    private fun startEntrance() {
        alpha = 0f
        translationY = 24f * resources.displayMetrics.density  // rises FROM the bottom
        capsule.scaleX = 0.85f * COUNT_SCALE
        capsule.scaleY = 0.85f * COUNT_SCALE
        entranceAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@ReturnPillView, ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(this@ReturnPillView, TRANSLATION_Y, translationY, 0f),
                ObjectAnimator.ofFloat(capsule, SCALE_X, 0.85f * COUNT_SCALE, COUNT_SCALE),
                ObjectAnimator.ofFloat(capsule, SCALE_Y, 0.85f * COUNT_SCALE, COUNT_SCALE),
            )
            duration = 280
            interpolator = AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
            start()
        }
    }

    /**
     * Starts the ring's countdown. Called by the controller on the app's
     * ON_STOP (the same signal the coordinator measures — D-454's sync fix),
     * NOT at the Continue tap.
     */
    fun startCountdown() {
        if (isReady || isExiting) return
        Logger.d(TAG) { "countdown started (app actually backgrounded)" }
        ring.startCountdown {
            post { enterReadyState() }
        }
    }

    /**
     * Countdown done → the ready state: ONE compensated in-surface relayout
     * (chip in + the longer label), then a pure property spring to
     * [READY_SCALE]. The window never resizes; the overshoot can only grow
     * into the padding headroom. ONLY now is the pill tappable (D-448).
     */
    private fun enterReadyState() {
        if (isReady || isExiting) return
        isReady = true
        Logger.d(TAG) { "countdown finished — ready state (Go back now active)" }

        countingWidth = capsule.width
        label.text = READY_LABEL
        label.setPadding(dp(10).toInt(), 0, dp(6).toInt(), 0)
        ring.showCheckmark()
        chipView?.visibility = VISIBLE
        // The relayout lands on the next layout pass; compensate THIS frame
        // so the capsule's on-screen width is continuous, then spring up.
        post {
            val readyWidth = capsule.width.coerceAtLeast(1)
            val compensation = COUNT_SCALE * (countingWidth.toFloat() / readyWidth)
            capsule.scaleX = compensation
            capsule.scaleY = COUNT_SCALE
            growAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(capsule, SCALE_X, compensation, READY_SCALE),
                    ObjectAnimator.ofFloat(capsule, SCALE_Y, COUNT_SCALE, READY_SCALE),
                )
                duration = 320
                interpolator = OvershootInterpolator(1.1f)
                start()
            }
            startPulse()
        }

        onReady()

        // ONLY in the ready state is the pill tappable (D-448). The whole
        // capsule is the target — bigger than the chip alone.
        isClickable = true
        isFocusable = true
        setOnClickListener {
            Logger.i(TAG) { "return pill tapped (ready) — bringing ANI-KUTA back to the foreground" }
            onGoBack()
        }
        // Press feedback: scale-down on press, spring back on release (§22).
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.96f * READY_SCALE).scaleY(0.96f * READY_SCALE).setDuration(90)
                        .setInterpolator(AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in))
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(READY_SCALE).scaleY(READY_SCALE).setDuration(160)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .start()
                }
            }
            v.onTouchEvent(event)
        }
    }

    private fun startPulse() {
        pulseAnimator = ObjectAnimator.ofFloat(capsule, SCALE_X, READY_SCALE, READY_SCALE + 0.02f).apply {
            startDelay = 340
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
            start()
        }
        pulseYAnimator = ObjectAnimator.ofFloat(capsule, SCALE_Y, READY_SCALE, READY_SCALE + 0.02f).apply {
            startDelay = 340
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
            start()
        }
    }

    /**
     * The exit (D-448 spec, rebuilt D-454 to be pure property animation —
     * no relayout, no surface resize, no stutter): the label + chip fade and
     * collapse, the capsule's background dissolves, and the ring pops like a
     * bubble — a GREEN CHECK for success, a RED X when the user returned
     * before the timer passed. Then [onEnd] lets the controller remove the
     * window.
     */
    fun beginExit(returnedTooEarly: Boolean, onEnd: () -> Unit) {
        if (isExiting) return
        isExiting = true
        cancelAnimators()
        isClickable = false  // one-shot — no re-entry mid-animation

        // The ring's final mark: green check (success) / red X (too early).
        ring.showResult(success = !returnedTooEarly)

        // The label + chip fade + collapse horizontally into the ring.
        label.pivotX = 0f
        chipView?.pivotX = 0f
        label.animate().scaleX(0.4f).alpha(0f).setDuration(160).start()
        chipView?.animate()?.scaleX(0.4f)?.alpha(0f)?.setDuration(160)?.start()

        // The capsule's background dissolves (no empty rounded rect remains).
        ObjectAnimator.ofFloat(capsuleBackground, "alpha", 1, 0).apply {
            duration = 200
            start()
        }

        // The bubble pop — the ring swells past its stroke and fades.
        mainHandler.postDelayed({
            ring.pivotX = ring.width / 2f
            ring.pivotY = ring.height / 2f
            ObjectAnimator.ofFloat(ring, SCALE_X, 1f, 1.45f).apply {
                duration = 360
                interpolator = OvershootInterpolator(2f)
                start()
            }
            ObjectAnimator.ofFloat(ring, SCALE_Y, 1f, 1.45f).apply {
                duration = 360
                interpolator = OvershootInterpolator(2f)
                start()
            }
            ring.animate().alpha(0f).setStartDelay(160).setDuration(240).start()
        }, 240)

        mainHandler.postDelayed({ onEnd() }, 700)
    }

    private fun cancelAnimators() {
        pulseAnimator?.cancel()
        pulseYAnimator?.cancel()
        entranceAnimator?.cancel()
        growAnimator?.cancel()
        ring.cancel()
    }

    override fun onDetachedFromWindow() {
        cancelAnimators()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        private const val TAG = "Anikuta:Core:Ads:ReturnPill"
        private const val COUNTDOWN_LABEL = "Stay a moment…"
        private const val READY_LABEL = "You can go back now"
        /** The counting presentation's scale (visibly smaller than ready — D-448). */
        private const val COUNT_SCALE = 0.92f
        /** The ready-state size bump (D-448: "the size will slightly increase"). */
        private const val READY_SCALE = 1.06f
    }
}

/**
 * The circular countdown ring inside the pill — an arc sweeps from full to
 * empty over `totalMs` with the remaining seconds in the middle. When done,
 * [showCheckmark] swaps the arc for a drawn check. [showResult] freezes the
 * ring into its exit mark: the check (success, accent color) or the RED X
 * (returned too early, error color) — D-454.
 */
private class CountdownRingView(
    context: Context,
    private val colors: ReturnPillColors,
    private val totalMs: Long,
) : View(context) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = withAlpha(colors.content, 0x26)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = colors.accent
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.content
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = colors.accent
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()
    private val markPath = Path()

    /** 1.0 → 0.0 remaining fraction. */
    private var remainingFraction = 1f
    private var countdownAnimator: ValueAnimator? = null

    /** null = still counting (draw the arc); true = check; false = red X. */
    private var result: Boolean? = null

    fun startCountdown(onComplete: () -> Unit) {
        countdownAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = totalMs.coerceAtLeast(1L)
            interpolator = null  // linear — the ring must be honest time
            addUpdateListener {
                remainingFraction = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onComplete()
            })
            start()
        }
    }

    fun showCheckmark() {
        result = true
        countdownAnimator?.cancel()
        invalidate()
    }

    /** The exit mark: the check (success) or the RED X (returned too early). */
    fun showResult(success: Boolean) {
        result = success
        if (success) markPaint.color = colors.accent else markPaint.color = colors.error
        countdownAnimator?.cancel()
        invalidate()
    }

    fun cancel() {
        countdownAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = min(width, height) * 0.14f
        trackPaint.strokeWidth = stroke
        progressPaint.strokeWidth = stroke
        markPaint.strokeWidth = stroke
        val inset = stroke / 2f + 1f
        arcRect.set(inset, inset, width - inset, height - inset)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        when (result) {
            null -> {
                canvas.drawArc(arcRect, -90f, 360f * remainingFraction, false, progressPaint)
                val secondsLeft = ceil(remainingFraction * totalMs / 1000.0).toInt().coerceAtLeast(1)
                val label = if (secondsLeft >= 60) "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}" else "$secondsLeft"
                textPaint.textSize = height * 0.38f
                val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, width / 2f, textY, textPaint)
            }
            true -> drawMark(check = true, canvas)
            false -> drawMark(check = false, canvas)
        }
    }

    private fun drawMark(check: Boolean, canvas: Canvas) {
        val w = width
        val h = height
        markPath.reset()
        if (check) {
            markPath.moveTo(w * 0.30f, h * 0.52f)
            markPath.lineTo(w * 0.44f, h * 0.66f)
            markPath.lineTo(w * 0.70f, h * 0.36f)
        } else {
            markPath.moveTo(w * 0.32f, h * 0.32f)
            markPath.lineTo(w * 0.68f, h * 0.68f)
            markPath.moveTo(w * 0.68f, h * 0.32f)
            markPath.lineTo(w * 0.32f, h * 0.68f)
        }
        canvas.drawPath(markPath, markPaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
