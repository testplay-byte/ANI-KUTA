package com.confused.anikuta.core.ads

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
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
 * (D-443). Classic Views, NOT Compose: the pill is hosted in a
 * TYPE_APPLICATION_OVERLAY window which has no lifecycle owner / saved-state
 * registry — hosting a ComposeView there requires faking both, a fragile
 * pile for one view. A View tree is the honest tool (CORE_RULES §5).
 *
 * # Layout (one horizontal capsule, self-sizing)
 *
 *   [ countdown-ring ]  [ label text ]  [ GO BACK chip ]
 *
 * - The ring counts down the smart-link's `minTimeOutsideMs` (a 5s config
 *   today — the same threshold the coordinator judges the return against).
 *   While it runs the label reads "Stay a moment…".
 * - When the ring completes, the pill crossfades into its "ready" state:
 *   label "You can go back now", the ring swaps to a checkmark, the chip
 *   brightens + the whole pill starts a gentle pulse. The user taps it →
 *   [onGoBack] (the controller brings ANI-KUTA back to the foreground →
 *   the coordinator's return-gate completes the ad).
 * - "Go back" is tappable AT ANY TIME (also mid-countdown). Returning early
 *   is safe: the coordinator shows the existing "Try again" state — the
 *   system was never intrusive-first.
 *
 * # Animations (CORE_RULES §22 — buttery, never dead)
 *
 * - Entrance: fade + rise + a slight overshoot scale-in, `FastOutSlowIn`.
 * - Press: the pill scales down to 0.96 on touch-down, springs back on up.
 * - Countdown ring: continuous 60fps arc sweep (linear — honest time).
 * - Ready state: background-color crossfade + looping pulse (1.0→1.04).
 * - Exit is owned by the controller (a quick fade-out before removal).
 *
 * # Safety
 *
 * `totalMs` <= 0 (a zero-second config) skips straight to the ready state.
 * All animators are cancelled in [onDetachedFromWindow] — a removed overlay
 * must never keep a frame callback alive.
 */
class ReturnPillView(
    context: Context,
    private val colors: ReturnPillColors,
    /** The smart-link's min-time-outside threshold — the ring's full length. */
    totalMs: Long,
    /** Invoked when the user taps the pill / the Go-back chip. */
    private val onGoBack: () -> Unit,
) : FrameLayout(context) {

    /** The view-side context (interpolators resolve against it in methods). */
    private val viewContext: Context = context

    /** Wrapper view so the pulse scales the capsule, not the window. */
    private val capsule: LinearLayout

    /** The countdown ring (its own [View] — redraws per frame). */
    private val ring: CountdownRingView

    /** The status label between the ring and the chip. */
    private val label: TextView

    /** The "Go back" chip. */
    private val chip: TextView

    /** The capsule's rounded background (animated to the ready tint). */
    private val capsuleBackground: GradientDrawable

    private var pulseAnimator: ObjectAnimator? = null
    private var entranceAnimator: AnimatorSet? = null

    /** True once the countdown finished (ready state entered). */
    private var isReady = false

    init {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics) }

        capsuleBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(30)
            setColor(colors.container)
            setStroke(dp(1).toInt(), withAlpha(colors.content, 0x14))
        }

        capsule = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = capsuleBackground
            setPadding(dp(12).toInt(), dp(8).toInt(), dp(8).toInt(), dp(8).toInt())
        }

        ring = CountdownRingView(context, colors, totalMs)

        label = TextView(context).apply {
            setTextColor(colors.content)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            text = "Stay a moment…"
            setPadding(dp(10).toInt(), 0, dp(6).toInt(), 0)
        }

        chip = TextView(context).apply {
            text = "Go back"
            setTextColor(colors.onAccent)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22)
                setColor(colors.accent)
            }
            setPadding(dp(14).toInt(), dp(8).toInt(), dp(14).toInt(), dp(8).toInt())
        }

        capsule.addView(
            ring,
            LinearLayout.LayoutParams(dp(34).toInt(), dp(34).toInt()).apply { gravity = Gravity.CENTER_VERTICAL },
        )
        capsule.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        capsule.addView(chip, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(capsule)

        // The WHOLE pill is the tap target (bigger than the chip alone) —
        // per the spec the pill offers "the option to go back". The touch
        // listener lives on `this` (not the capsule) so the click flows
        // through View.onTouchEvent normally.
        isClickable = true
        isFocusable = true
        setOnClickListener {
            Logger.i(TAG) { "return pill tapped — bringing ANI-KUTA back to the foreground" }
            onGoBack()
        }
        // Press feedback: scale-down on press, spring back on release (§22).
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).setInterpolator(
                        AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
                    ).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(
                        OvershootInterpolator(1.4f)
                    ).start()
                }
            }
            v.onTouchEvent(event)
        }

        startEntrance()
        if (totalMs <= 0) {
            enterReadyState()
        } else {
            ring.startCountdown { post { enterReadyState() } }
        }
    }

    private fun startEntrance() {
        alpha = 0f
        translationY = -24f * resources.displayMetrics.density
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

    /** Countdown done → the "you can go back now" state. Idempotent. */
    private fun enterReadyState() {
        if (isReady) return
        isReady = true
        Logger.d(TAG) { "return pill countdown finished — ready state" }

        label.text = "You can go back now"
        ring.showCheckmark()
        chip.animate().scaleX(1.06f).scaleY(1.06f).setDuration(200).start()

        // Gentle loop pulse — invites the tap without being obnoxious.
        pulseAnimator = ObjectAnimator.ofFloat(capsule, SCALE_X, 1f, 1.04f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
            start()
        }
        ObjectAnimator.ofFloat(capsule, SCALE_Y, 1f, 1.04f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AnimationUtils.loadInterpolator(viewContext, android.R.interpolator.fast_out_slow_in)
            start()
        }.also { readyYAnimator = it }
    }

    private var readyYAnimator: Animator? = null

    /** Fade-out for the controller's orderly removal. Cancels everything. */
    fun animateOut(onEnd: () -> Unit) {
        cancelAnimators()
        animate()
            .alpha(0f)
            .translationY(translationY - 12f * resources.displayMetrics.density)
            .setDuration(180)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            .start()
    }

    private fun cancelAnimators() {
        pulseAnimator?.cancel()
        readyYAnimator?.cancel()
        entranceAnimator?.cancel()
        ring.cancel()
        animate().setListener(null)
    }

    override fun onDetachedFromWindow() {
        cancelAnimators()
        super.onDetachedFromWindow()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        private const val TAG = "Anikuta:Core:Ads:ReturnPill"
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
