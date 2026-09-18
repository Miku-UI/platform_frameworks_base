/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.settingslib.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.OneShotPreDrawListener
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * A helper class to handle spring-based expand and collapse animations for banner widgets.
 * This class manages height transitions, alpha changes, and scale effects for a smooth UI feel.
 */
class BannerAnimationHelper(
    private val context: Context,
    private val bannerView: View,
    private val contentView: View
) {

    private var springAnim: SpringAnimation? = null

    // Layout metrics captured for animation calculations
    private var measuredFullHeight: Int = 0
    private var targetWidth: Int = 0
    private var initialTopMargin: Int = 0
    private var initialBottomMargin: Int = 0

    // State tracking
    private var isExpanding: Boolean = false

    /**
     * Returns the current animation progress from 0.0 (collapsed) to 1.0 (expanded).
     */
    var currentProgress: Float = 1f
        private set

    /**
     * Custom property to animate the view height via the SpringAnimation framework.
     */
    private val heightProperty = object : FloatPropertyCompat<View>("viewHeight") {
        override fun getValue(view: View): Float {
            return view.layoutParams.height.toFloat()
        }

        override fun setValue(view: View, value: Float) {
            updateFrameLogic(value.toInt())
        }
    }

    /**
     * Triggers the expand or collapse animation.
     *
     * @param expand True to expand the banner, false to collapse it.
     * @param onEnd Callback invoked when the animation completes or is cancelled.
     */
    fun animate(expand: Boolean, onEnd: () -> Unit) {
        // Cancel any ongoing animations to prevent state conflicts
        if (springAnim?.isRunning == true) {
            springAnim?.cancel()
        }

        bannerView.setHasTransientState(true)
        bannerView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        restoreState()

        val parent = bannerView.parent as? View
        if (parent == null) {
            bannerView.visibility = if (expand) View.VISIBLE else View.GONE
            onEnd()
            return
        }

        // Ensure the parent is laid out before calculating dimensions
        if (parent.width == 0 && bannerView.isAttachedToWindow) {
            OneShotPreDrawListener.add(parent) {
                animate(expand, onEnd)
            }
            return
        }

        val grandParent = parent.parent as? View

        targetWidth = if (grandParent != null && grandParent.width > 0) {
            grandParent.width
        } else if (parent.width > 0) {
            parent.width
        } else {
            context.resources.displayMetrics.widthPixels
        }

        targetWidth -= (parent.paddingStart + parent.paddingEnd)

        // Calculate actual measurement width excluding margins
        val lp = bannerView.layoutParams as? ViewGroup.MarginLayoutParams
        val horizontalMargins = if (lp != null) lp.leftMargin + lp.rightMargin else 0
        val measureWidth = (targetWidth - horizontalMargins).coerceAtLeast(0)

        // Measure the view to find its natural "full" height
        val widthSpec = View.MeasureSpec.makeMeasureSpec(measureWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        bannerView.measure(widthSpec, heightSpec)
        measuredFullHeight = bannerView.measuredHeight

        if (measuredFullHeight == 0) {
            onEnd()
            return
        }

        val targetValue: Float
        val startValue: Float

        if (expand) {
            isExpanding = true
            targetValue = measuredFullHeight.toFloat()
            startValue = 0f

            if (lp != null) {
                initialTopMargin = lp.topMargin
                initialBottomMargin = lp.bottomMargin
                lp.height = 0
                lp.topMargin = 0
                lp.bottomMargin = 0
                bannerView.layoutParams = lp
            }
            bannerView.visibility = View.VISIBLE
            contentView.alpha = 0f
            contentView.translationY = -(measuredFullHeight / 8f)
        } else {
            isExpanding = false
            targetValue = 0f

            if (bannerView.height > 0) {
                startValue = bannerView.height.toFloat()
                if (lp != null && initialTopMargin == 0) {
                    initialTopMargin = lp.topMargin
                    initialBottomMargin = lp.bottomMargin
                }
            } else {
                startValue = measuredFullHeight.toFloat()
                bannerView.layoutParams.height = measuredFullHeight
                bannerView.requestLayout()

                if (lp != null) {
                    initialTopMargin = lp.topMargin
                    initialBottomMargin = lp.bottomMargin
                }
            }
        }

        // Ensure content view height matches measured height during transition
        val contentParams = contentView.layoutParams
        contentParams.height = measuredFullHeight
        contentView.layoutParams = contentParams

        updateFrameLogic(startValue.toInt())

        val invisibleThresholdPixels = measuredFullHeight * 0.02f

        // Initialize and start the spring animation
        springAnim = SpringAnimation(bannerView, heightProperty).apply {
            spring = SpringForce(targetValue).apply {
                stiffness = 350f
                dampingRatio = if (expand) 0.8f else SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            setStartValue(startValue)
            setMinValue(0f)
            minimumVisibleChange = invisibleThresholdPixels.coerceAtLeast(1f)
            addEndListener { _, _, _, _ ->
                bannerView.setLayerType(View.LAYER_TYPE_NONE, null)
                finishAnimation(expand, onEnd)
            }
        }

        springAnim?.start()
    }

    /**
     * Updates the UI elements (alpha, scale, margins) based on the current height during animation.
     */
    private fun updateFrameLogic(currentH: Int) {
        val progress =
            if (measuredFullHeight == 0) 0f else currentH.toFloat() / measuredFullHeight.toFloat()
        currentProgress = progress

        // Handle banner container visibility
        bannerView.alpha = if (progress <= 0.05f) 0f else 1f

        val params = bannerView.layoutParams as ViewGroup.MarginLayoutParams
        params.height = currentH

        // Scale margins relative to progress
        if (initialTopMargin > 0) {
            params.topMargin = (initialTopMargin * progress).toInt().coerceAtLeast(0)
        }
        if (initialBottomMargin > 0) {
            params.bottomMargin = (initialBottomMargin * progress).toInt().coerceAtLeast(0)
        }

        // Enforce full width during animation
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.leftMargin = 0
        params.rightMargin = 0

        // Handle horizontal scale effect when height is near zero
        val density = context.resources.displayMetrics.density
        val cornerRadius = 28f * density
        val triggerHeight = cornerRadius * 2

        if (currentH <= triggerHeight && triggerHeight > 0) {
            val ratio = currentH.toFloat() / triggerHeight
            // Map height ratio to width scale (0.9 to 1.0)
            val targetScale = 0.90f + (0.10f * ratio)
            bannerView.scaleX = targetScale
        } else {
            bannerView.scaleX = 1f
        }

        bannerView.layoutParams = params

        // Update content alpha (staggered start at 25% progress)
        contentView.alpha = if (progress < 0.25f) {
            0f
        } else {
            ((progress - 0.25f) / (0.95f - 0.25f)).coerceIn(0f, 1f)
        }

        // Update content translation (slide-up effect)
        val totalMove = measuredFullHeight / 8f
        contentView.translationY = if (progress < 0.25f) {
            -totalMove
        } else {
            val moveProgress = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
            -totalMove + (totalMove * moveProgress)
        }
    }

    /**
     * Finalizes view properties once the animation has ended.
     */
    private fun finishAnimation(expand: Boolean, onEnd: () -> Unit) {
        if (!expand) {
            bannerView.visibility = View.GONE
            bannerView.alpha = 1f
        } else {
            restoreState()
            bannerView.layoutParams.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            contentView.alpha = 1f
            contentView.translationY = 0f
            bannerView.alpha = 1f
        }
        onEnd()
    }

    /**
     * Restores layout parameters to their original measured states.
     */
    private fun restoreState() {
        val params = bannerView.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null && (initialTopMargin != 0 || initialBottomMargin != 0)) {
            params.topMargin = initialTopMargin
            params.bottomMargin = initialBottomMargin
            params.leftMargin = 0
            params.rightMargin = 0
            bannerView.layoutParams = params
        }
        contentView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        contentView.requestLayout()
    }

    /**
     * Immediately cancels the animation and restores the banner state.
     */
    fun cancel() {
        if (springAnim?.isRunning == true) {
            springAnim?.cancel()
        }
        restoreState()
    }
}