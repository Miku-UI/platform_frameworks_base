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

package com.android.systemui.shared.clocks.view

import android.graphics.Canvas
import android.graphics.Color
import android.icu.text.NumberFormat
import android.util.MathUtils.constrainedMap
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.annotation.VisibleForTesting
import androidx.core.view.children
import com.android.app.animation.Interpolators
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.DigitTranslateAnimator
import com.android.systemui.customization.clocks.R
import com.android.systemui.customization.clocks.utils.CanvasUtils.translate
import com.android.systemui.customization.clocks.utils.CanvasUtils.use
import com.android.systemui.customization.clocks.utils.ViewUtils.measuredSize
import com.android.systemui.customization.clocks.view.DigitalClockTextViewParent
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VPointF.Companion.max
import com.android.systemui.plugins.keyguard.VPointF.Companion.times
import com.android.systemui.plugins.keyguard.VRectF
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import java.util.Locale
import kotlin.collections.filterNotNull
import kotlin.collections.map
import kotlin.collections.max
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun clamp(value: Float, minVal: Float, maxVal: Float): Float = max(min(value, maxVal), minVal)

class FlexClockViewGroup(clockCtx: ClockContext) :
    ViewGroup(clockCtx.context), DigitalClockTextViewParent {
    protected val logger = ClockLogger(this, clockCtx.messageBuffer, this::class.simpleName!!)
        get() = field ?: ClockLogger.INIT_LOGGER

    @VisibleForTesting
    var isAnimationEnabled = true
        set(value) {
            field = value
            childViews.forEach { view -> view.isAnimationEnabled = value }
        }

    var dozeFraction: Float = 0F
        set(value) {
            field = value
            childViews.forEach { view -> view.dozeFraction = field }
        }

    var isReactiveTouchInteractionEnabled = false
        set(value) {
            field = value
        }

    var _childViews: List<FlexClockTextView>? = null
    val childViews: List<FlexClockTextView>
        get() {
            return _childViews
                ?: this.children
                    .map { child -> child as? FlexClockTextView }
                    .filterNotNull()
                    .toList()
                    .also { _childViews = it }
        }

    private var maxChildSize = VPointF(-1f)
    private val lockscreenTranslate = VPointF.ZERO
    private var aodTranslate = VPointF.ZERO

    private var onAnimateDoze: (() -> Unit)? = null
    private var isDozeReadyToAnimate = false

    // Does the current language have mono vertical size when displaying numerals
    private var isMonoVerticalNumericLineSpacing = true

    init {
        setWillNotDraw(false)
        layoutParams =
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        updateLocale(Locale.getDefault())
    }

    var maxSize = VPointF(-1f)
        private set

    var onViewBoundsChanged: ((VRectF) -> Unit)? = null
    var onViewMaxSizeChanged: ((VPointF) -> Unit)? = null
    private val digitOffsets = mutableMapOf<Int, Float>()

    protected fun calculateSize(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        shouldMeasureChildren: Boolean,
    ): VPointF {
        maxChildSize = VPointF(-1f)
        childViews.forEach { textView ->
            if (shouldMeasureChildren) {
                textView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            }
            maxChildSize = max(maxChildSize, textView.measuredSize)
        }
        aodTranslate = VPointF.ZERO
        // TODO(b/364680879): Cleanup
        /*
        aodTranslate = VPointF(
            maxChildSize.x * AOD_HORIZONTAL_TRANSLATE_RATIO,
            maxChildSize.y * AOD_VERTICAL_TRANSLATE_RATIO
        )
        */

        val xScale = if (childViews.size < 4) 1f else 2f
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)
        return (maxChildSize + aodTranslate.abs()) * VPointF(xScale, 2f) + VPointF(0f, yBuffer)
    }

    override fun onViewAdded(child: View?) {
        if (child == null) return
        logger.onViewAdded(child)
        super.onViewAdded(child)
        (child as? FlexClockTextView)?.let {
            it.digitTranslateAnimator = DigitTranslateAnimator { invalidate() }
            it.onViewMaxSizeChanged = { recomputeMaxTextSize() }
        }
        child.setWillNotDraw(true)
        _childViews = null
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        _childViews = null
    }

    fun refreshTime() {
        logger.refreshTime()
        childViews.forEach { textView -> textView.refreshText() }
    }

    override fun setVisibility(visibility: Int) {
        logger.setVisibility(visibility)
        super.setVisibility(visibility)
    }

    override fun setAlpha(alpha: Float) {
        logger.setAlpha(alpha)
        super.setAlpha(alpha)
    }

    override fun invalidate() {
        logger.invalidate()
        super.invalidate()
    }

    override fun requestLayout() {
        logger.requestLayout()
        super.requestLayout()
    }

    override fun updateMeasuredSize() {
        updateMeasuredSize(
            measuredWidthAndState,
            measuredHeightAndState,
            shouldMeasureChildren = false,
        )
    }

    private fun updateMeasuredSize(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        shouldMeasureChildren: Boolean,
    ) {
        val size = calculateSize(widthMeasureSpec, heightMeasureSpec, shouldMeasureChildren)
        setMeasuredDimension(size.x.roundToInt(), size.y.roundToInt())
    }

    private fun recomputeMaxTextSize() {
        var maxSize = VPointF(-1f)
        childViews.forEach { child ->
            maxSize =
                max(
                    maxSize,
                    // This wil overmeasure if some child views may be larger than others at their
                    // maximal size, however all child views should be equivalently configured which
                    // means that we should not need a more complex approach here.
                    when (child.id) {
                        // Digit pairs should only need to be duplicated vertically
                        ClockViewIds.HOUR_DIGIT_PAIR,
                        ClockViewIds.MINUTE_DIGIT_PAIR -> child.maxSize * VPointF(1f, 2f)
                        // Single digit views are duplicated in both the x & y direction
                        ClockViewIds.HOUR_FIRST_DIGIT,
                        ClockViewIds.HOUR_SECOND_DIGIT,
                        ClockViewIds.MINUTE_FIRST_DIGIT,
                        ClockViewIds.MINUTE_SECOND_DIGIT -> child.maxSize * 2f
                        // Other clock view ids are not valid children
                        else -> VPointF(-1)
                    },
                )
        }
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)
        this.maxSize = maxSize + VPointF(0f, yBuffer)
        onViewMaxSizeChanged?.let { it(this.maxSize) }
    }

    override fun updateLocation() {
        val layoutBounds = this.layoutBounds ?: return
        val bounds = VRectF.fromCenter(layoutBounds.center, this.measuredSize)
        setFrame(
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.right.roundToInt(),
            bounds.bottom.roundToInt(),
        )
        updateChildFrames(isLayout = false)
        onViewBoundsChanged?.let { it(bounds) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        logger.onMeasure(widthMeasureSpec, heightMeasureSpec)
        updateMeasuredSize(widthMeasureSpec, heightMeasureSpec, shouldMeasureChildren = true)

        isDozeReadyToAnimate = true
        onAnimateDoze?.invoke()
        onAnimateDoze = null
    }

    private var layoutBounds = VRectF.ZERO

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        logger.onLayout(changed, left, top, right, bottom)
        layoutBounds = VRectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        updateChildFrames(isLayout = true)
    }

    private fun updateChildFrames(isLayout: Boolean) {
        val yBuffer = context.resources.getDimensionPixelSize(R.dimen.clock_vertical_digit_buffer)
        childViews.forEach { child ->
            var offset =
                maxChildSize.run {
                    when (child.id) {
                        ClockViewIds.HOUR_DIGIT_PAIR -> VPointF.ZERO
                        ClockViewIds.HOUR_FIRST_DIGIT -> VPointF.ZERO
                        ClockViewIds.HOUR_SECOND_DIGIT -> VPointF(x, 0f)
                        // Add a small vertical buffer for second line views
                        ClockViewIds.MINUTE_DIGIT_PAIR -> VPointF(0f, y + yBuffer)
                        ClockViewIds.MINUTE_FIRST_DIGIT -> VPointF(0f, y + yBuffer)
                        ClockViewIds.MINUTE_SECOND_DIGIT -> VPointF(x, y + yBuffer)
                        else -> VPointF.ZERO
                    }
                }

            val childSize = child.measuredSize
            offset += aodTranslate.abs()

            // Horizontal offset to center each view in the available space
            val midX = if (childViews.size < 4) measuredWidth / 2f else measuredWidth / 4f
            offset += VPointF(midX - childSize.x / 2f, 0f)

            val setPos = if (isLayout) child::layout else child::setLeftTopRightBottom
            setPos(
                offset.x.roundToInt(),
                offset.y.roundToInt(),
                (offset.x + childSize.x).roundToInt(),
                (offset.y + childSize.y).roundToInt(),
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        logger.onDraw()
        childViews.forEach { child ->
            canvas.use { canvas ->
                canvas.translate(digitOffsets.getOrDefault(child.id, 0f), 0f)
                canvas.translate(child.left.toFloat(), child.top.toFloat())
                child.draw(canvas)
            }
        }
    }

    fun onLocaleChanged(locale: Locale) {
        updateLocale(locale)
        requestLayout()
    }

    fun updateColor(lockscreenColor: Int, aodColor: Int = Color.WHITE) {
        childViews.forEach { view -> view.updateColor(lockscreenColor, aodColor) }
        invalidate()
    }

    fun updateAxes(axes: ClockAxisStyle, isAnimated: Boolean) {
        childViews.forEach { view -> view.updateAxes(axes, isAnimated) }
        requestLayout()
    }

    fun onFontSettingChanged(fontSizePx: Float) {
        childViews.forEach { view -> view.applyTextSize(fontSizePx) }
    }

    fun animateDoze(isDozing: Boolean, isAnimated: Boolean) {
        fun executeDozeAnimation() {
            childViews.forEach { view -> view.animateDoze(isDozing, isAnimated) }
            if (maxChildSize.x < 0 || maxChildSize.y < 0) {
                measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            }
            childViews.forEach { textView ->
                textView.digitTranslateAnimator?.let {
                    if (!isDozing) {
                        it.animatePosition(
                            animate = isAnimated && isAnimationEnabled,
                            interpolator = Interpolators.EMPHASIZED,
                            duration = AOD_TRANSITION_DURATION,
                            targetTranslation =
                                updateDirectionalTargetTranslate(id, lockscreenTranslate),
                        )
                    } else {
                        it.animatePosition(
                            animate = isAnimated && isAnimationEnabled,
                            interpolator = Interpolators.EMPHASIZED,
                            duration = AOD_TRANSITION_DURATION,
                            onAnimationEnd = null,
                            targetTranslation = updateDirectionalTargetTranslate(id, aodTranslate),
                        )
                    }
                }
            }
        }

        if (isDozeReadyToAnimate) executeDozeAnimation()
        else onAnimateDoze = { executeDozeAnimation() }
    }

    fun animateCharge() {
        childViews.forEach { view -> view.animateCharge() }
        childViews.forEach { textView ->
            textView.digitTranslateAnimator?.let {
                it.animatePosition(
                    animate = isAnimationEnabled,
                    interpolator = Interpolators.EMPHASIZED,
                    duration = CHARGING_TRANSITION_DURATION,
                    onAnimationEnd = {
                        it.animatePosition(
                            animate = isAnimationEnabled,
                            interpolator = Interpolators.EMPHASIZED,
                            duration = CHARGING_TRANSITION_DURATION,
                            targetTranslation =
                                updateDirectionalTargetTranslate(
                                    textView.id,
                                    if (dozeFraction == 1F) aodTranslate else lockscreenTranslate,
                                ),
                        )
                    },
                    targetTranslation =
                        updateDirectionalTargetTranslate(
                            textView.id,
                            if (dozeFraction == 1F) lockscreenTranslate else aodTranslate,
                        ),
                )
            }
        }
    }

    override fun animateFidget(pt: VPointF, enforceBounds: Boolean): Boolean {
        if (enforceBounds) {
            if (visibility != View.VISIBLE) {
                logger.animateFidget(pt, isSuppressed = true)
                return false
            }

            val bounds = VRectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            if (!bounds.contains(pt)) {
                logger.animateFidget(pt, isSuppressed = true)
                return false
            }
        }

        childViews.forEach { it.animateFidget(pt, enforceBounds = false) }
        return true
    }

    private fun updateLocale(locale: Locale) {
        isMonoVerticalNumericLineSpacing =
            !NON_MONO_VERTICAL_NUMERIC_LINE_SPACING_LANGUAGES.any {
                val newLocaleNumberFormat =
                    NumberFormat.getInstance(locale).format(FORMAT_NUMBER.toLong())
                val nonMonoVerticalNumericLineSpaceNumberFormat =
                    NumberFormat.getInstance(Locale.forLanguageTag(it))
                        .format(FORMAT_NUMBER.toLong())
                newLocaleNumberFormat == nonMonoVerticalNumericLineSpaceNumberFormat
            }
    }

    /** Offsets the textViews of the clock for the step clock animation. */
    fun offsetGlyphsForStepClockAnimation(args: ClockPositionAnimationArgs) {
        val translation = left - args.fromLeft
        val isCentering = isLayoutRtl == (translation < 0)
        // A map of the delays for a given digit, keyed by digit index. Inverted for rtl motion.
        val delays = if (isLayoutRtl == isCentering) STEP_LEFT_DELAYS else STEP_RIGHT_DELAYS
        childViews.forEachIndexed { index, child ->
            val digitFraction =
                STEP_INTERPOLATOR.getInterpolation(
                    constrainedMap(
                        /* rangeMin= */ 0.0f,
                        /* rangeMax= */ 1.0f,
                        /* valueMin= */ delays[index],
                        /* valueMax= */ delays[index] + STEP_ANIMATION_TIME,
                        /* value= */ args.fraction,
                    )
                )
            digitOffsets[child.id] = translation * (digitFraction - 1)
        }
        invalidate()
    }

    fun resetGlyphsOffsets() {
        if (digitOffsets.size <= 0) return
        digitOffsets.clear()
        invalidate()
    }

    /** Offsets the textViews of the clock for the compose version of the step clock animation. */
    fun offsetGlyphsForStepClockAnimation(
        startX: Float,
        currentX: Float,
        endX: Float,
        progress: Float,
    ) {
        if (progress <= 0f || progress >= 1f) {
            resetGlyphsOffsets()
            return
        }

        val translation = endX - startX
        // A map of the delays for a given digit, keyed by digit index
        val delays = if (translation > 0) STEP_RIGHT_DELAYS else STEP_LEFT_DELAYS
        childViews.forEachIndexed { index, child ->
            val digitFraction =
                constrainedMap(
                    /* rangeMin= */ 0.0f,
                    /* rangeMax= */ 1.0f,
                    /* valueMin= */ delays[index],
                    /* valueMax= */ delays[index] + STEP_ANIMATION_TIME,
                    /* value= */ progress,
                )

            val digitX = translation * digitFraction + startX
            digitOffsets[child.id] = (digitX - currentX)
        }
        invalidate()
    }

    companion object {
        val FORMAT_NUMBER = 1234567890
        val AOD_TRANSITION_DURATION = 800L
        val CHARGING_TRANSITION_DURATION = 300L

        val AOD_HORIZONTAL_TRANSLATE_RATIO = -0.15F
        val AOD_VERTICAL_TRANSLATE_RATIO = 0.075F

        private val STEP_INTERPOLATOR = Interpolators.EMPHASIZED
        private val STEP_DIGIT_DELAY = 0.033f // Measured as fraction of total animation duration
        private val STEP_LEFT_DELAYS = listOf(0, 1, 2, 3).map { it * STEP_DIGIT_DELAY }
        private val STEP_RIGHT_DELAYS = listOf(1, 0, 3, 2).map { it * STEP_DIGIT_DELAY }
        private val STEP_ANIMATION_TIME = 1.0f - STEP_LEFT_DELAYS.max()

        /** Languages that do not have vertically mono spaced numerals */
        private val NON_MONO_VERTICAL_NUMERIC_LINE_SPACING_LANGUAGES = setOf("my" /* Burmese */)

        /** Use the sign of targetTranslation to control the direction of digit translation */
        fun updateDirectionalTargetTranslate(id: Int, targetTranslation: VPointF): VPointF {
            return targetTranslation *
                when (id) {
                    ClockViewIds.HOUR_FIRST_DIGIT -> VPointF(-1, -1)
                    ClockViewIds.HOUR_SECOND_DIGIT -> VPointF(1, -1)
                    ClockViewIds.MINUTE_FIRST_DIGIT -> VPointF(-1, 1)
                    ClockViewIds.MINUTE_SECOND_DIGIT -> VPointF(1, 1)
                    ClockViewIds.HOUR_DIGIT_PAIR -> VPointF(-1, -1)
                    ClockViewIds.MINUTE_DIGIT_PAIR -> VPointF(-1, 1)
                    else -> VPointF(1, 1)
                }
        }
    }
}
