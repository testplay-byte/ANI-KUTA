package com.confused.anikuta.core.ads

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
)

/**
 * The floating return pill itself — a rounded, theme-colored capsule that
 * floats over the BROWSER while the user completes the smart-link visit
 * (D-443, reshaped by D-448 after the v1.1.3 device round). Classic Views,
 * NOT Compose: the pill is hosted in a TYPE_APPLICATION_OVERLAY window which
 * has no lifecycle owner / saved-state registry — hosting a ComposeView
 * there requires faking both, a fragile pile for one view. A View tree is
 * the honest tool (CORE_RULES §5).
 *
 * # The two states (D-448, the user's v1.1.3 device-round spec)
 *
 * **Counting (compact, passive):** a SMALL capsule — the countdown ring +
 * the "Stay a moment…" label. NO Go-back button, and the pill is NOT
 * clickable: "clicking the pill when the timer has not been completed
 * should not lead the user back." Returning early happens through the
 * browser's own back/UI (the coordinator's Try-again gate still covers it).
 *
 * **Ready (grown, active):** the moment the ring completes, the pill GROWS —
 * the "Go back" chip animates in (a [LayoutTransition] animates the capsule's
 * width change + the chip's appearance), the whole capsule scales up slightly
 * (1.0 → 1.06), the ring swaps to a checkmark, the label becomes "You can go
 * back now", and a gentle pulse invites the tap. ONLY NOW is the pill (the
 * whole capsule — the bigger target) clickable → [onGoBack].
 *
 * # Layout
 *
 *   counting:  [ countdown-ring ]  [ label ]
 *   ready:     [ ✓-ring ]  [ label ]  [ GO BACK chip ]   ← wider + scaled up
 *
 * The window is WRAP_CONTENT, so the capsule's width change re-lays-out the
 * window itself — the grow is visible against the browser content.
 *
 * # Animations (CORE_RULES §22 — buttery, never dead)
 *
 * - Entrance: fade + rise + a slight overshoot scale-in, `FastOutSlowIn`.
 * - Counting: continuous 60fps arc sweep (linear — honest time).
 * - Ready transition: LayoutTransition (chip in + width grow, 300ms) +
 *   the scale-up spring + the looping pulse (1.06 → 1.09 → 1.06).
 * - Press: scale-down on touch, spring back on release (ready state only).
 * - Exit (D-452): the label + chip collapse away, only the checkmark
 *   remains, and it POPS like a bubble — then the window is removed.
 *
 * # Safety
 *
 * `totalMs` <= 0 (a zero-second config) skips straight to the ready state.
 * All animators are cancelled in [onDetachedFromWindow] — a removed overlay
 * must never keep a frame callback alive.
 */
class ReturnPillView(
    context: Context,
    private val pillColors: ReturnPillColors,
    /** The smart-link's min-time-outside threshold — the ring's full length. */
    private val totalMs: Long,
    /** Invoked when the user taps the pill IN THE READY STATE. */
    private val onGoBack: () -> Unit,
) : FrameLayout(context) {

    /** The view-side context (interpolators resolve against it in methods). */
    private val viewContext: Context = context

    /** Wrapper view so the pulse scales the capsule, not the window. */
    private val capsule: LinearLayout

    /** The countdown ring (its own [View] — redraws per frame). */
    private val ring: CountdownRingView

    /** The status label next to the ring. */
    private val label: TextView

    /** The capsule's rounded background. */
    private val capsuleBackground: GradientDrawable

    private var pulseAnimator: ObjectAnimator? = null
    private var entranceAnimator: AnimatorSet? = null

    /** True once the countdown finished (ready state entered). Idempotent gate. */
    private var isReady = false

    /** Shared dp→px helper (init + the ready-state relayout both use it). */
    private val dp: (Int) -> Float = { v: Int ->
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics)
    }

    init {
        // D-452 headroom: the ready-state scale (1.06) + the pulse (up to
        // 1.08) enlarge the capsule BEYOND its laid-out bounds — without
        // padding, the window (sized exactly to the content) CLIPPED the
        // capsule's left/right sides (the v1.1.4 device report). The root's
        // padding gives every scale room to breathe inside the window; the
        // capsule itself is centered within it.
        val padH = dp(16).toInt()
        val padV = dp(8).toInt()
        setPadding(padH, padV, padH, padV)

        capsuleBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(30)
            setColor(pillColors.container)
            setStroke(dp(1).toInt(), withAlpha(pillColors.content, 0x14))
        }

        // LayoutTransition: the chip's later insertion animates (fade+scale in)
        // AND the capsule's bounds change animates — the D-448 "the width of
        // the pill will increase" grow, on the exact views involved.
        val transition = LayoutTransition().apply {
            setDuration(LayoutTransition.APPEARING, 260)
            setDuration(LayoutTransition.CHANGE_APPEARING, 300)
            setDuration(LayoutTransition.CHANGE_DISAPPEARING, 300)
            setInterpolator(LayoutTransition.APPEARING, OvershootInterpolator(1.3f))
            setInterpolator(LayoutTransition.CHANGE_APPEARING,
                AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in))
            setInterpolator(LayoutTransition.CHANGE_DISAPPEARING,
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
            text = "Stay a moment…"
            setPadding(dp(10).toInt(), 0, dp(2).toInt(), 0)
        }

        capsule.addView(
            ring,
            LinearLayout.LayoutParams(dp(34).toInt(), dp(34).toInt()).apply { gravity = Gravity.CENTER_VERTICAL },
        )
        capsule.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(
            capsule,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )

        startEntrance()
        if (totalMs <= 0) {
            enterReadyState()
        } else {
            ring.startCountdown { post { enterReadyState() } }
        }
    }

    private fun startEntrance() {
        alpha = 0f
        translationY = 24f * resources.displayMetrics.density  // rises FROM the bottom
        capsule.scaleX = 0.85f
        capsule.scaleY = 0.85f
        entranceAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@ReturnPillView, ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(this@ReturnPillView, TRANSLATION_Y, translationY, 0f),
                ObjectAnimator.ofFloat(capsule, SCALE_X, 0.85f, 1f),
                ObjectAnimator.ofFloat(capsule, SCALE_Y, 0.85f, 1f),
            )
            duration = 280
            interpolator = AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
            start()
        }
    }

    /**
     * Countdown done → the ready state: the Go-back chip animates in (the
     * capsule grows), the capsule scales up slightly, the ring swaps to a
     * checkmark, and — ONLY now — the pill becomes tappable (D-448: an
     * early tap must NEVER return the user).
     */
    private fun enterReadyState() {
        if (isReady) return
        isReady = true
        Logger.d(TAG) { "return pill countdown finished — ready state (Go back now active)" }

        label.text = "You can go back now"
        label.setPadding(dp(10).toInt(), 0, dp(6).toInt(), 0)
        ring.showCheckmark()

        // The chip — inserted into the transition-armed capsule: the width
        // grow + the chip's entrance animate together (D-448). Kept in a
        // field: the exit animation collapses it back out (D-452).
        val chip = TextView(viewContext).apply {
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
            chip,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        chipView = chip

        // The slight size increase on ready (D-448) — scale, not dpi, so the
        // artwork stays crisp; then the gentle pulse keeps inviting the tap.
        ObjectAnimator.ofFloat(capsule, SCALE_X, 1f, READY_SCALE).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.4f)
            start()
        }
        ObjectAnimator.ofFloat(capsule, SCALE_Y, 1f, READY_SCALE).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.4f)
            start()
        }
        pulseAnimator = ObjectAnimator.ofFloat(capsule, SCALE_X, READY_SCALE, READY_SCALE + 0.02f).apply {
            startDelay = 320
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
            start()
        }
        ObjectAnimator.ofFloat(capsule, SCALE_Y, READY_SCALE, READY_SCALE + 0.02f).apply {
            startDelay = 320
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
            start()
        }.also { readyYAnimator = it }

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

    /** The "Go back" chip (created in the ready state; collapsed on exit). */
    private var chipView: TextView? = null

    /** True while the exit animation runs (the tap + hide paths are one-shot). */
    private var isExiting = false

    /** Shared token for the exit's staged callbacks (cancelable in one call). */
    private val exitToken = Any()

    /** The exit's staged callbacks run through a Handler (View.postDelayed
     * has no token variant; Handler.postDelayed(Runnable, token, delay) is
     * API 24+ — our minSdk). */
    private val exitHandler = Handler(Looper.getMainLooper())

    private var readyYAnimator: Animator? = null

    /**
     * The exit (D-452, the user's v1.1.4 spec): the pill SHRINKS horizontally
     * — the label + the chip collapse away — until ONLY the checkmark ring
     * remains, and the checkmark POPS like a bubble (scales up with an
     * overshoot while fading out). Then [onEnd] lets the controller remove
     * the window. Runs for BOTH exit routes (the Go-back tap + the
     * state-driven hide) — one code path, idempotent.
     */
    fun animateOut(onEnd: () -> Unit) {
        if (isExiting) return
        isExiting = true
        cancelAnimators()
        isClickable = false  // one-shot — no re-entry mid-animation

        // Phase 1 (0–170ms): the label + chip collapse horizontally into the
        // ring (pivot on their left edge — the pill shrinks from its right
        // side inward).
        label.pivotX = 0f
        chipView?.pivotX = 0f
        label.animate().scaleX(0f).alpha(0f).setDuration(170).start()
        chipView?.animate()?.scaleX(0f)?.alpha(0f)?.setDuration(170)?.start()

        // Phase 2 (170ms): remove them — the transition-armed capsule
        // animates the width collapse smoothly.
        exitHandler.postDelayed({
            label.visibility = GONE
            chipView?.visibility = GONE
        }, exitToken, 170L)

        // Phase 3 (~260ms): the checkmark pops like a bubble — scales up
        // with an overshoot while fading out.
        exitHandler.postDelayed({
            ring.pivotX = ring.width / 2f
            ring.pivotY = ring.height / 2f
            ObjectAnimator.ofFloat(ring, SCALE_X, 1f, 1.4f).apply {
                duration = 340
                interpolator = OvershootInterpolator(2f)
                start()
            }
            ObjectAnimator.ofFloat(ring, SCALE_Y, 1f, 1.4f).apply {
                duration = 340
                interpolator = OvershootInterpolator(2f)
                start()
            }
            ring.animate().alpha(0f).setStartDelay(140).setDuration(240).start()
        }, exitToken, 260L)

        // Phase 4 (~640ms): the bubble has popped — hand the window back to
        // the controller for removal.
        exitHandler.postDelayed({ onEnd() }, exitToken, 640L)
    }

    private fun cancelAnimators() {
        pulseAnimator?.cancel()
        readyYAnimator?.cancel()
        entranceAnimator?.cancel()
        ring.cancel()
        exitHandler.removeCallbacksAndMessages(null)
        animate().setListener(null)
    }

    override fun onDetachedFromWindow() {
        cancelAnimators()
        super.onDetachedFromWindow()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        private const val TAG = "Anikuta:Core:Ads:ReturnPill"
        /** The ready-state size bump (D-448: "the size will slightly increase"). */
        private const val READY_SCALE = 1.06f
    }
}

/**
 * The circular countdown ring inside the pill — an arc sweeps from full to
 * empty over `totalMs` with the remaining seconds in the middle. When done,
 * [showCheckmark] swaps the arc for a drawn check.
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
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = colors.accent
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()
    private val checkPath = Path()

    /** 1.0 → 0.0 remaining fraction. */
    private var remainingFraction = 1f
    private var countdownAnimator: ValueAnimator? = null
    private var showCheck = false

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
        showCheck = true
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
        checkPaint.strokeWidth = stroke
        val inset = stroke / 2f + 1f
        arcRect.set(inset, inset, width - inset, height - inset)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        if (showCheck) {
            checkPath.reset()
            val w = width
            val h = height
            checkPath.moveTo(w * 0.30f, h * 0.52f)
            checkPath.lineTo(w * 0.44f, h * 0.66f)
            checkPath.lineTo(w * 0.70f, h * 0.36f)
            canvas.drawPath(checkPath, checkPaint)
        } else {
            canvas.drawArc(arcRect, -90f, 360f * remainingFraction, false, progressPaint)
            val secondsLeft = ceil(remainingFraction * totalMs / 1000.0).toInt().coerceAtLeast(1)
            val label = if (secondsLeft >= 60) "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}" else "$secondsLeft"
            textPaint.textSize = height * 0.38f
            val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, width / 2f, textY, textPaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}
