/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.customization.clocks.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.VibrationEffect
import android.text.Layout
import android.text.TextPaint
import android.util.Log
import android.util.MathUtils.lerp
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.core.graphics.withSave
import com.android.app.animation.Interpolators
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.animation.AxisDefinition
import com.android.systemui.animation.GSFAxes
import com.android.systemui.animation.TextAnimator
import com.android.systemui.animation.TextAnimatorListener
import com.android.systemui.animation.TypefaceVariantCache
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.DigitTranslateAnimator
import com.android.systemui.customization.clocks.FontTextStyle
import com.android.systemui.customization.clocks.FontTextStyleImpl
import com.android.systemui.customization.clocks.TextStyle
import com.android.systemui.customization.clocks.utils.CanvasUtils.translate
import com.android.systemui.customization.clocks.utils.PaintUtils.getTextBounds
import com.android.systemui.customization.clocks.utils.ViewUtils.measuredSize
import com.android.systemui.customization.clocks.utils.ViewUtils.measuredSizeAndState
import com.android.systemui.customization.clocks.utils.ViewUtils.setLeftTopRightBottom
import com.android.systemui.plugins.keyguard.VMeasurePoint
import com.android.systemui.plugins.keyguard.VMeasureSpec
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VPointF.Companion.max
import com.android.systemui.plugins.keyguard.VRectF
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

interface IDigitalClockView {
    val maxSize: VPointF
    var dozeFraction: Float

    var onViewBoundsChanged: ((VRectF) -> Unit)?
    var onViewMaxSizeChanged: ((VPointF) -> Unit)?

    fun invalidate()

    fun requestLayout()
}

interface IDigitalClockTextView : IDigitalClockView {
    var text: String

    fun refreshText()

    fun animateCharge()
}

abstract class DigitalClockTextView(private val clockCtx: ClockContext) :
    TextView(clockCtx.context), IDigitalClockTextView {
    val lockscreenPaint = TextPaint()
    private var textStyle: FontTextStyle = FontTextStyleImpl()
    private var aodStyle: FontTextStyle = FontTextStyleImpl()

    data class FontVariations(
        val lockscreen: String,
        val doze: String,
        val chargeLockscreen: String,
        val chargeDoze: String,
        val fidget: String,
    ) {
        constructor(fVar: String) : this(fVar, fVar)

        constructor(
            lockscreen: String,
            doze: String,
        ) : this(
            lockscreen = lockscreen,
            doze = doze,
            chargeLockscreen = doze,
            chargeDoze = lockscreen,
            fidget = lockscreen,
        )

        fun getStandard(isDozing: Boolean): String = if (isDozing) doze else lockscreen

        fun getCharge(isDozing: Boolean): String = if (isDozing) chargeDoze else chargeLockscreen
    }

    abstract var fontVariations: FontVariations

    abstract fun updateFontVariations(lsAxes: ClockAxisStyle): FontVariations

    override var text: String
        get() = "${super.getText()}"
        set(value) = super.setText(value)

    override var onViewBoundsChanged: ((VRectF) -> Unit)? = null
    override var onViewMaxSizeChanged: ((VPointF) -> Unit)? = null
    var digitTranslateAnimator: DigitTranslateAnimator? = null
    private var aodFontSizePx = -1f
    var isVertical: Boolean = false

    var maxSingleDigitSize = VPointF(-1f)
        private set

    // Store the font size when there's no height constraint as a reference when adjusting font size
    private var lastUnconstrainedTextSize = Float.MAX_VALUE
    // Calculated by height of styled text view / text size
    // Used as a factor to calculate a smaller font size when text height is constrained
    @VisibleForTesting var fontSizeAdjustFactor = 1f

    private val initThread = Thread.currentThread()

    // Maximum size this view will be at any time, but in its current rendering configuration
    final override var maxSize = VPointF(-1f)
        private set

    // textBounds is the size of text in LS, which only measures current text in lockscreen style
    var textBounds = VRectF.ZERO
    // prevTextBounds and targetTextBounds are to deal with dozing animation between LS and AOD
    // especially for the textView which has different bounds during the animation
    // prevTextBounds holds the state we are transitioning from
    private var prevTextBounds = VRectF.ZERO
    // targetTextBounds holds the state we are interpolating to
    private var targetTextBounds = VRectF.ZERO
    protected val logger = ClockLogger(this, clockCtx.messageBuffer, TAG)
        get() = field ?: ClockLogger.INIT_LOGGER

    private var aodDozingInterpolator: Interpolator = Interpolators.LINEAR

    lateinit var textAnimator: TextAnimator
    abstract val typefaceCache: TypefaceVariantCache

    open fun initializeAnimators(layout: Layout) {
        textAnimator = TextAnimator(layout, typefaceCache, animatorListener)
    }

    open fun updateAnimators(layout: Layout) {
        textAnimator.updateLayout(layout)
    }

    var verticalAlignment: VerticalAlignment = VerticalAlignment.BASELINE
    var horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER

    private val xAlignment: XAlignment
        get() = horizontalAlignment.resolveXAlignment((parent as? View) ?: this)

    val isAnimationEnabled: Boolean
        get() = clockCtx.isAnimationEnabled

    override var dozeFraction: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    val isDozing: Boolean
        get() = dozeFraction > 0.5f

    private var measuredBaseline = 0

    var lockscreenColor = Color.WHITE
        private set

    var aodColor = Color.WHITE
        private set

    init {
        maxLines = 1
        layoutParams = ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    }

    override fun onTouchEvent(evt: MotionEvent): Boolean {
        if (super.onTouchEvent(evt)) return true
        if (isDozing) return false

        if (evt.action == MotionEvent.ACTION_DOWN) {
            val pt = VPointF(evt.x, evt.y)
            return (parent as? IDigitalClockViewGroup)?.animateFidget(pt, enforceBounds = false)
                ?: animateFidget(pt, enforceBounds = false)
        }

        return false
    }

    private val animatorListener =
        object : TextAnimatorListener {
            override fun onInvalidate() = invalidate()

            override fun onRebased(progress: Float) {
                updateAnimationTextBounds()
            }

            override fun onPaintModified(paint: Paint) {
                updateAnimationTextBounds()
            }
        }

    fun updateColor(lockscreenColor: Int, aodColor: Int = Color.WHITE) {
        this.lockscreenColor = lockscreenColor
        this.aodColor = aodColor
        lockscreenPaint.color = lockscreenColor

        textAnimator.setTextStyle(
            TextAnimator.Style(color = if (dozeFraction < 0.5f) lockscreenColor else aodColor)
        )
        invalidate()
    }

    fun updateAxes(lsAxes: ClockAxisStyle, isAnimated: Boolean) {
        fontVariations = updateFontVariations(lsAxes)
        logger.updateAxes(fontVariations.lockscreen, fontVariations.doze, isAnimated)

        lockscreenPaint.typeface = typefaceCache.getTypefaceForVariant(fontVariations.lockscreen)
        typeface = lockscreenPaint.typeface

        updateTextBounds()

        textAnimator.setTextStyle(
            TextAnimator.Style(fVar = fontVariations.getStandard(isDozing)),
            TextAnimator.Animation(
                animate = isAnimated && isAnimationEnabled,
                duration = AXIS_CHANGE_ANIMATION_DURATION,
                interpolator = AXIS_CHANGE_INTERPOLATOR,
            ),
        )

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        recomputeMaxTextSize()
        requestLayout()
        invalidate()
    }

    data class AxisAnimation(val axisKey: String, val distance: Float, val midpoint: Float) {
        constructor(
            axis: AxisDefinition,
            distance: Float,
            midpoint: Float = (axis.maxValue + axis.minValue) / 2f,
        ) : this(axis.tag, distance, midpoint)

        fun mutateValue(value: Float): Float {
            return value + distance * (if (value > midpoint) -1 else 1)
        }
    }

    fun buildAnimationTargetVariation(
        axes: ClockAxisStyle,
        params: List<AxisAnimation>,
    ): ClockAxisStyle {
        return ClockAxisStyle(
            axes.items.associate { (key, value) ->
                val axis = params.firstOrNull { it.axisKey == key }
                key to (axis?.mutateValue(value) ?: value)
            }
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measureSpec = VMeasurePoint.fromSpecs(widthMeasureSpec, heightMeasureSpec)
        logger.onMeasure(measureSpec)
        super.onMeasure(measureSpec.width.spec, measureSpec.height.spec)

        val layout = this.layout
        if (layout != null) {
            if (!this::textAnimator.isInitialized) {
                initializeAnimators(layout)
                setInterpolatorPaint()
            } else {
                updateAnimators(layout)
            }
            measuredBaseline = layout.getLineBaseline(0)
        } else {
            val currentThread = Thread.currentThread()
            Log.wtf(
                TAG,
                "TextView.getLayout() is null after measure! " +
                    "currentThread=$currentThread; initThread=$initThread",
            )
        }

        val bounds = getInterpolatedTextBounds()
        val size = computeMeasuredSize(bounds, measureSpec)
        setInterpolatedSize(size, measureSpec)
    }

    private var drawnProgress: Float? = null

    override fun onDraw(canvas: Canvas) {
        logger.onDraw(textAnimator.textInterpolator.shapedText)

        val interpProgress = textAnimator.progress
        val interpBounds = getInterpolatedTextBounds(interpProgress)
        if (interpProgress != drawnProgress) {
            drawnProgress = interpProgress
            val measureSize = computeMeasuredSize(interpBounds)
            setInterpolatedSize(measureSize)
            (parent as? IDigitalClockViewGroup)?.run {
                updateMeasuredSize()
                updateLocation()
            } ?: setInterpolatedLocation(measureSize)
        }

        canvas.withSave {
            if (layout != null) {
                val layoutRect = layout.computeDrawingBoundingBox()
                // TODO(b/450350391): This correction is about right, but very weird
                if (layoutRect.left > 10000f) {
                    val offset = if (isVertical) interpBounds.top else interpBounds.left
                    translate(layoutRect.left - offset, 0f)
                }
            }

            digitTranslateAnimator?.apply { translate(currentTranslation) }
            if (isVertical) {
                translate(0f, measuredHeight.toFloat())
                rotate(-90f)
            }

            val drawTranslation = getDrawTranslation(interpBounds)
            translate(drawTranslation)
            drawAnimators(this, drawTranslation)
        }
    }

    protected open fun drawAnimators(canvas: Canvas, drawTranslation: VPointF) {
        textAnimator.draw(canvas)
    }

    override fun setVisibility(visibility: Int) {
        logger.setVisibility(visibility)
        super.setVisibility(visibility)
    }

    override fun setAlpha(alpha: Float) {
        logger.setAlpha(alpha)
        super.setAlpha(alpha)
    }

    private var layoutBounds = VRectF.ZERO

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        logger.onLayout(changed, left, top, right, bottom)
        layoutBounds = VRectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    override fun invalidate() {
        logger.invalidate()
        super.invalidate()
        (parent as? IDigitalClockViewGroup)?.invalidate()
    }

    fun refreshTime() {
        logger.refreshTime()
        refreshText()
    }

    protected open fun onAnimateDoze(
        isDozing: Boolean,
        style: TextAnimator.Style,
        animation: TextAnimator.Animation,
    ) {
        textAnimator.setTextStyle(style, animation)
    }

    fun animateDoze(isDozing: Boolean, isAnimated: Boolean) {
        if (!this::textAnimator.isInitialized) return
        logger.animateDoze(isDozing, isAnimated)
        val isAnimated = isAnimated && isAnimationEnabled
        onAnimateDoze(
            isDozing,
            TextAnimator.Style(
                fVar = fontVariations.getStandard(isDozing),
                color = if (isDozing) aodColor else lockscreenColor,
                textSize = if (isDozing) aodFontSizePx else lockscreenPaint.textSize,
            ),
            TextAnimator.Animation(
                animate = isAnimated,
                duration = aodStyle.transitionDuration,
                interpolator = aodDozingInterpolator,
            ),
        )

        if (!isAnimated) {
            requestLayout()
            (parent as? IDigitalClockViewGroup)?.requestLayout()
        }
    }

    protected open fun onAnimateTransient(
        targetStyle: TextAnimator.Style,
        returnStyle: TextAnimator.Style,
        animation: TextAnimator.Animation,
    ) {
        textAnimator.setTransientTextStyle(targetStyle, returnStyle, animation)
    }

    override fun animateCharge() {
        if (!this::textAnimator.isInitialized || textAnimator.isRunning) {
            // Skip charge animation if dozing animation is already playing.
            return
        }
        logger.animateCharge()

        onAnimateTransient(
            TextAnimator.Style(fVar = fontVariations.getCharge(dozeFraction != 0f)),
            TextAnimator.Style(fVar = fontVariations.getStandard(dozeFraction != 0f)),
            TextAnimator.Animation(
                animate = isAnimationEnabled,
                duration = CHARGE_ANIMATION_DURATION,
                interpolator = CHARGE_INTERPOLATOR,
            ),
        )
    }

    fun animateFidget(pt: VPointF, enforceBounds: Boolean): Boolean {
        if (!this::textAnimator.isInitialized || textAnimator.isRunning) {
            // Skip fidget animation if other animation is already playing.
            return false
        }

        if (enforceBounds) {
            if (visibility != View.VISIBLE) {
                logger.animateFidget(pt, isSuppressed = true)
                return false
            }

            val bounds = getInterpolatedTextBounds()
            if (!bounds.contains(pt)) {
                logger.animateFidget(pt, isSuppressed = true)
                return false
            }
        }

        logger.animateFidget(pt, isSuppressed = false)
        clockCtx.vibrator?.vibrate(FIDGET_HAPTICS)

        onAnimateTransient(
            TextAnimator.Style(fVar = fontVariations.fidget),
            TextAnimator.Style(fVar = fontVariations.getStandard(isDozing)),
            TextAnimator.Animation(
                animate = isAnimationEnabled,
                duration = FIDGET_ANIMATION_DURATION,
                interpolator = FIDGET_INTERPOLATOR,
            ),
        )
        return true
    }

    override fun refreshText() {
        updateTextBounds()

        if (layout == null) {
            requestLayout()
        } else {
            updateAnimators(layout)
        }
    }

    /** Returns the interpolated text bounding rect based on interpolation progress */
    fun getInterpolatedTextBounds(progress: Float = textAnimator.progress): VRectF {
        if (progress <= 0f) {
            return prevTextBounds
        } else if (!textAnimator.isRunning || progress >= 1f) {
            return targetTextBounds
        }

        return VRectF(
            left = lerp(prevTextBounds.left, targetTextBounds.left, progress),
            right = lerp(prevTextBounds.right, targetTextBounds.right, progress),
            top = lerp(prevTextBounds.top, targetTextBounds.top, progress),
            bottom = lerp(prevTextBounds.bottom, targetTextBounds.bottom, progress),
        )
    }

    private fun computeMeasuredSize(
        interpBounds: VRectF,
        spec: VMeasurePoint = measuredSizeAndState,
    ): VPointF {
        return VPointF(
                when {
                    spec.width.mode == VMeasureSpec.Mode.EXACTLY -> spec.width.size.toFloat()
                    else -> interpBounds.width + 2 * lockscreenPaint.strokeWidth
                },
                when {
                    spec.height.mode == VMeasureSpec.Mode.EXACTLY -> spec.height.size.toFloat()
                    else -> interpBounds.height + 2 * lockscreenPaint.strokeWidth
                },
            )
            .let { result -> if (isVertical) result.swap() else result }
    }

    /** Set the measured size of the view to match the interpolated text bounds */
    private fun setInterpolatedSize(
        measureBounds: VPointF,
        measureSpec: VMeasurePoint = measuredSizeAndState,
    ) {
        val mode = if (isVertical) measureSpec.mode.swap() else measureSpec.mode
        val targetSpec =
            VMeasurePoint(
                VMeasureSpec(ceil(measureBounds.x).toInt(), mode.x),
                VMeasureSpec(ceil(measureBounds.y).toInt(), mode.y),
            )

        setMeasuredDimension(targetSpec.width.spec, targetSpec.height.spec)

        logger.d({ "setInterpolatedSize(${VMeasurePoint.fromLong(long1)})" }) {
            long1 = targetSpec.toLong()
        }
    }

    /** Set the location of the view to match the interpolated text bounds */
    private fun setInterpolatedLocation(measureSize: VPointF): VRectF {
        val pos =
            VPointF(
                when (xAlignment) {
                    XAlignment.LEFT -> layoutBounds.left
                    XAlignment.CENTER -> layoutBounds.center.x - measureSize.x / 2f
                    XAlignment.RIGHT -> layoutBounds.right - measureSize.x
                },
                when (verticalAlignment) {
                    VerticalAlignment.TOP -> layoutBounds.top
                    VerticalAlignment.CENTER -> layoutBounds.center.y - measureSize.y / 2f
                    VerticalAlignment.BOTTOM -> layoutBounds.bottom - measureSize.y
                    VerticalAlignment.BASELINE -> layoutBounds.center.y - measureSize.y / 2f
                },
            )

        val targetRect = VRectF.fromTopLeft(pos, measureSize)
        setLeftTopRightBottom(targetRect)
        onViewBoundsChanged?.let { it(targetRect) }
        logger.d({ "setInterpolatedLocation(${VRectF.fromLong(long1)})" }) {
            long1 = targetRect.toLong()
        }
        return targetRect
    }

    fun getDrawTranslation(interpBounds: VRectF): VPointF {
        val measuredSize = if (isVertical) measuredSize.swap() else measuredSize
        val sizeDiff = measuredSize - interpBounds.size
        val alignment =
            VPointF(
                when (xAlignment) {
                    XAlignment.LEFT -> 0f
                    XAlignment.CENTER -> 0.5f
                    XAlignment.RIGHT -> 1f
                },
                when (verticalAlignment) {
                    VerticalAlignment.TOP -> 0f
                    VerticalAlignment.CENTER -> 0.5f
                    VerticalAlignment.BASELINE -> 0.5f
                    VerticalAlignment.BOTTOM -> 1f
                },
            )

        val renderCorrection =
            VPointF(
                x = -interpBounds.left,
                y = -interpBounds.top - (if (baseline != -1) baseline else measuredBaseline),
            )

        return sizeDiff * alignment + renderCorrection
    }

    open fun applyStyles(textStyle: TextStyle, aodStyle: TextStyle?) {
        if (textStyle is FontTextStyle && aodStyle is FontTextStyle?) {
            this.textStyle = textStyle
            lockscreenPaint.strokeJoin = Paint.Join.ROUND
            lockscreenPaint.typeface =
                typefaceCache.getTypefaceForVariant(fontVariations.lockscreen)
            lockscreenPaint.fontFeatureSettings = textStyle.fontFeatureSettings
            typeface = lockscreenPaint.typeface
            textStyle.lineHeight?.let { lineHeight = it.roundToInt() }

            this.aodStyle = aodStyle ?: textStyle
            aodDozingInterpolator = this.aodStyle.transitionInterpolator ?: Interpolators.LINEAR
        }

        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        setInterpolatorPaint()
        recomputeMaxTextSize()
        invalidate()
    }

    /** When constrainedByHeight is on, targetFontSizePx is the constrained height of textView */
    fun applyTextSize(targetFontSizePx: Float?, constrainedByHeight: Boolean) {
        val adjustedFontSizePx = adjustFontSize(targetFontSizePx, constrainedByHeight)
        val fontSizePx = adjustedFontSizePx * textStyle.fontSizeScale
        aodFontSizePx = adjustedFontSizePx * aodStyle.fontSizeScale
        if (fontSizePx > 0) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx)
            lockscreenPaint.textSize = textSize
            updateTextBounds()
        }

        if (!constrainedByHeight) {
            val lastUnconstrainedHeight = textBounds.height + lockscreenPaint.strokeWidth * 2
            fontSizeAdjustFactor = lastUnconstrainedHeight / lastUnconstrainedTextSize
        }

        recomputeMaxTextSize()
        setInterpolatorPaint()
    }

    /** Measures a maximal piece of text so that layout decisions can be consistent. */
    private fun recomputeMaxTextSize() {
        maxSingleDigitSize = VPointF(-1)

        for (i in 0..9) {
            val digitBounds = lockscreenPaint.getTextBounds("$i")
            maxSingleDigitSize = max(maxSingleDigitSize, digitBounds.size)
        }
        maxSingleDigitSize += 2 * lockscreenPaint.strokeWidth

        maxSize =
            when (id) {
                // Single digit values have already been computed
                ClockViewIds.HOUR_FIRST_DIGIT,
                ClockViewIds.HOUR_SECOND_DIGIT,
                ClockViewIds.MINUTE_FIRST_DIGIT,
                ClockViewIds.MINUTE_SECOND_DIGIT -> maxSingleDigitSize
                // Digit pairs should measure 00 as 88 is not a valid hour or minute pair
                ClockViewIds.HOUR_DIGIT_PAIR,
                ClockViewIds.MINUTE_DIGIT_PAIR -> lockscreenPaint.getTextBounds("00").size
                // Full format includes the colon. This overmeasures a bit for 12hr configurations.
                ClockViewIds.TIME_FULL_FORMAT -> lockscreenPaint.getTextBounds("00:00").size
                // DATE_FORMAT is difficult, and shouldn't be necessary
                else -> VPointF(-1)
            }

        onViewMaxSizeChanged?.let { it(maxSize) }
    }

    /** Called without animation, can be used to set the initial state of animator */
    protected fun setInterpolatorPaint() {
        if (!this::textAnimator.isInitialized) {
            return
        }

        setInterpolatorPaint(
            isDozing,
            TextAnimator.Style(
                fVar = fontVariations.getStandard(isDozing),
                color = if (isDozing) aodColor else lockscreenColor,
                textSize = if (isDozing) aodFontSizePx else lockscreenPaint.textSize,
            ),
        )
    }

    protected open fun setInterpolatorPaint(isDozing: Boolean, textStyle: TextAnimator.Style) {
        textAnimator.textInterpolator.targetPaint.set(lockscreenPaint)
        textAnimator.textInterpolator.onTargetPaintModified()
        textAnimator.setTextStyle(textStyle)
    }

    /** Updates both the lockscreen text bounds and animation text bounds */
    private fun updateTextBounds() {
        textBounds = lockscreenPaint.getTextBounds(text)
        updateAnimationTextBounds()
    }

    /**
     * Called after textAnimator.setTextStyle textAnimator.setTextStyle will update targetPaint, and
     * rebase if previous animator is canceled so basePaint will store the state we transition from
     * and targetPaint will store the state we transition to
     */
    private fun updateAnimationTextBounds() {
        drawnProgress = null
        if (this::textAnimator.isInitialized) {
            prevTextBounds = textAnimator.textInterpolator.basePaint.getTextBounds(text)
            targetTextBounds = textAnimator.textInterpolator.targetPaint.getTextBounds(text)
        } else {
            prevTextBounds = textBounds
            targetTextBounds = textBounds
        }
    }

    /**
     * Adjust text size to adapt to large display / font size where the text view will be
     * constrained by height
     */
    private fun adjustFontSize(targetFontSizePx: Float?, constrainedByHeight: Boolean): Float {
        return if (constrainedByHeight) {
            min((targetFontSizePx ?: 0F) / fontSizeAdjustFactor, lastUnconstrainedTextSize)
        } else {
            lastUnconstrainedTextSize = targetFontSizePx ?: 1F
            lastUnconstrainedTextSize
        }
    }

    companion object {
        private val TAG = DigitalClockTextView::class.simpleName!!

        val FIDGET_HAPTICS: VibrationEffect =
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0)
                .compose()

        const val CHARGE_ANIMATION_DURATION = 400L
        val CHARGE_INTERPOLATOR = PathInterpolator(0.26873f, 0f, 0.45042f, 1f)
        val CHARGE_DISTS =
            listOf(
                AxisAnimation(GSFAxes.WEIGHT, 200f),
                AxisAnimation(GSFAxes.WIDTH, 0f),
                AxisAnimation(GSFAxes.ROUND, 0f),
                AxisAnimation(GSFAxes.SLANT, 0f),
            )

        const val AXIS_CHANGE_ANIMATION_DURATION = 400L
        val AXIS_CHANGE_INTERPOLATOR: Interpolator = Interpolators.EMPHASIZED

        const val FIDGET_ANIMATION_DURATION = 250L
        val FIDGET_INTERPOLATOR = PathInterpolator(0.26873f, 0f, 0.45042f, 1f)
        val FIDGET_DISTS =
            listOf(
                AxisAnimation(GSFAxes.WEIGHT, 200f),
                AxisAnimation(GSFAxes.WIDTH, 10f),
                AxisAnimation(GSFAxes.ROUND, 0f),
                AxisAnimation(GSFAxes.SLANT, 0f),
            )

        fun TextAnimator.setTransientTextStyle(
            targetStyle: TextAnimator.Style,
            returnStyle: TextAnimator.Style,
            animation: TextAnimator.Animation,
        ) {
            setTextStyle(
                targetStyle,
                animation.copy(onAnimationEnd = { setTextStyle(returnStyle, animation) }),
            )
        }
    }
}
