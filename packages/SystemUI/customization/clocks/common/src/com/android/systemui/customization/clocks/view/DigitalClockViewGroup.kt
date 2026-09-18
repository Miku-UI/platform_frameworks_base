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

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.core.graphics.withSave
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.DigitTranslateAnimator
import com.android.systemui.customization.clocks.R
import com.android.systemui.customization.clocks.utils.CanvasUtils.translate
import com.android.systemui.customization.clocks.utils.ViewUtils.layout
import com.android.systemui.customization.clocks.utils.ViewUtils.measuredSize
import com.android.systemui.customization.clocks.utils.ViewUtils.measuredSizeAndState
import com.android.systemui.customization.clocks.utils.ViewUtils.position
import com.android.systemui.customization.clocks.utils.ViewUtils.setLeftTopRightBottom
import com.android.systemui.plugins.keyguard.VMeasurePoint
import com.android.systemui.plugins.keyguard.VMeasureSpec
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VPointF.Companion.max
import com.android.systemui.plugins.keyguard.VRectF
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds.HOUR_DIGIT_PAIR
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds.HOUR_FIRST_DIGIT
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds.HOUR_SECOND_DIGIT
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds.MINUTE_DIGIT_PAIR
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds.MINUTE_FIRST_DIGIT
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds.MINUTE_SECOND_DIGIT
import java.util.Locale
import kotlin.math.ceil

interface IDigitalClockViewGroup : IDigitalClockView {
    fun animateFidget(pt: VPointF, enforceBounds: Boolean): Boolean

    fun updateMeasuredSize()

    fun updateLocation()

    fun onLocaleChanged(locale: Locale)

    fun animateDoze(isDozing: Boolean, isAnimated: Boolean)

    fun updateAxes(axes: ClockAxisStyle, isAnimated: Boolean)

    fun animateCharge()

    fun onFontSettingChanged(fontSizePx: Float)

    fun updateColor(lockscreenColor: Int, aodColor: Int = Color.WHITE)
}

@SuppressLint("ViewConstructor")
abstract class DigitalClockViewGroup<TChild>(clockCtx: ClockContext) :
    ViewGroup(clockCtx.context), IDigitalClockViewGroup
    where TChild : IDigitalClockTextView, TChild : View {
    val logger = ClockLogger(this, clockCtx.messageBuffer, this::class.simpleName!!)
        get() = field ?: ClockLogger.INIT_LOGGER

    val isAnimationEnabled: Boolean = clockCtx.isAnimationEnabled

    override var dozeFraction: Float = 0F
        set(value) {
            field = value
            children.forEach { view -> view.dozeFraction = field }
        }

    abstract val children: Sequence<TChild>

    protected fun getChild(id: Int): TChild? {
        return children.firstOrNull { child -> child.id == id }
    }

    protected val hasLeadingDigit: Boolean
        get() = getChild(HOUR_FIRST_DIGIT)?.text?.isNotBlank() ?: false

    protected var maxChildSize = VPointF(-1f)
        private set

    init {
        layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
    }

    final override var maxSize = VPointF(-1f)
        protected set

    override var onViewBoundsChanged: ((VRectF) -> Unit)? = null
    override var onViewMaxSizeChanged: ((VPointF) -> Unit)? = null

    override fun onViewAdded(child: View?) {
        if (child == null) return
        logger.onViewAdded(child)
        super.onViewAdded(child)
        (child as? DigitalClockTextView)?.apply {
            digitTranslateAnimator = DigitTranslateAnimator { invalidate() }
            onViewMaxSizeChanged = { recomputeMaxTextSize() }
        }
        child.setWillNotDraw(!willNotDraw())
    }

    abstract fun refreshTime()

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
        updateMeasuredSize(measuredSizeAndState, shouldMeasureChildren = false)
    }

    private fun updateMeasuredSize(measureSpec: VMeasurePoint, shouldMeasureChildren: Boolean) {
        maxChildSize = VPointF(-1f)
        children.forEach { textView ->
            if (shouldMeasureChildren) {
                textView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            }
            maxChildSize = max(maxChildSize, textView.measuredSize)
        }

        val size = calculateSize(measureSpec)
        setMeasuredDimension(
            VMeasureSpec.exactly(ceil(size.x).toInt()).spec,
            VMeasureSpec.exactly(ceil(size.y).toInt()).spec,
        )
    }

    abstract fun calculateSize(measureSpec: VMeasurePoint): VPointF

    private fun recomputeMaxTextSize() {
        var maxSize = VPointF(-1f)
        children.forEach { child ->
            maxSize =
                max(
                    maxSize,
                    // This will over-measure if some child views may be larger than others at their
                    // maximal size, however all child views should be equivalently configured which
                    // means that we should not need a more complex approach here.
                    when (child.id) {
                        // Digit pairs should only need to be duplicated vertically
                        HOUR_DIGIT_PAIR,
                        MINUTE_DIGIT_PAIR -> child.maxSize * VPointF(1f, 2f)
                        // Single digit views are duplicated in both the x & y direction
                        HOUR_FIRST_DIGIT,
                        HOUR_SECOND_DIGIT,
                        MINUTE_FIRST_DIGIT,
                        MINUTE_SECOND_DIGIT -> child.maxSize * 2f
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
        val bounds = VRectF.fromCenter(layoutBounds.center, this.measuredSize)
        setLeftTopRightBottom(bounds)
        updateChildFrames(isLayout = false)
        onViewBoundsChanged?.let { it(bounds) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measureSpec = VMeasurePoint.fromSpecs(widthMeasureSpec, heightMeasureSpec)
        logger.onMeasure(measureSpec)
        updateMeasuredSize(measureSpec, shouldMeasureChildren = true)
    }

    private var layoutBounds = VRectF.ZERO

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        logger.onLayout(changed, left, top, right, bottom)
        layoutBounds = VRectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        updateChildFrames(isLayout = true)
    }

    abstract fun getChildFrame(child: TChild): VRectF

    protected fun updateChildFrames(isLayout: Boolean) {
        for (child in children) {
            if (child.parent != this) continue
            val rect = getChildFrame(child)
            if (isLayout) {
                child.layout(rect)
            } else {
                child.setLeftTopRightBottom(rect)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        logger.onDraw()
        children.forEach { child ->
            canvas.withSave {
                translate(child.position)
                child.draw(this)
            }
        }
    }

    override fun animateFidget(pt: VPointF, enforceBounds: Boolean): Boolean = false
}
