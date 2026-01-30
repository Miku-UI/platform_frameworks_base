/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.row

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import com.android.systemui.res.R
import com.android.wm.shell.shared.animation.Interpolators

class AnimatedActionBackgroundDrawable(
    context: Context,
    onAnimationStarted: () -> Unit,
    onAnimationEnded: () -> Unit,
    onAnimationCancelled: () -> Unit,
) :
    RippleDrawable(
        ContextCompat.getColorStateList(context, R.color.notification_ripple_untinted_color)
            ?: ColorStateList.valueOf(Color.TRANSPARENT),
        createBaseDrawable(context, onAnimationStarted, onAnimationEnded, onAnimationCancelled),
        null,
    ) {
    companion object {
        private fun createBaseDrawable(
            context: Context,
            onAnimationStarted: () -> Unit,
            onAnimationEnded: () -> Unit,
            onAnimationCancelled: () -> Unit,
        ): Drawable {
            return BaseBackgroundDrawable(
                context,
                onAnimationStarted,
                onAnimationEnded,
                onAnimationCancelled,
            )
        }
    }
}

class BaseBackgroundDrawable(
    private val context: Context,
    onAnimationStarted: () -> Unit,
    onAnimationEnded: () -> Unit,
    onAnimationCancelled: () -> Unit,
) : Drawable() {

    private val cornerRadius =
        context.resources
            .getDimensionPixelSize(R.dimen.animated_action_button_corner_radius)
            .toFloat()

    // Stroke is clipped in draw() callback, so doubled in width here.
    private val outlineStrokeWidth =
        2 *
            context.resources
                .getDimensionPixelSize(R.dimen.animated_action_button_outline_stroke_width)
                .toFloat()
    private val emphasizedOutlineStrokeWidth =
        2 *
            context.resources
                .getDimensionPixelSize(
                    R.dimen.animated_action_button_outline_stroke_width_emphasized
                )
                .toFloat()
    private val insetVertical =
        context.resources
            .getDimensionPixelSize(R.dimen.animated_action_button_inset_vertical)
            .toFloat()
    private val innerGlow =
        RenderNode("innerGlow").apply {
            val radiusResource =
                if (isNightMode(context)) {
                    R.dimen.animated_action_button_glow_radius_emphasized
                } else {
                    R.dimen.animated_action_button_glow_radius
                }
            val blurRadius = context.resources.getDimensionPixelSize(radiusResource).toFloat()
            val blurEffect =
                RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.MIRROR)
            setRenderEffect(blurEffect)
        }

    private val buttonShape = Path()
    // Color and style
    private val outlineStaticColor = context.getColor(R.color.animated_action_button_stroke_color)
    private val outlineGradientPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outlineStaticColor
            style = Paint.Style.STROKE
            strokeWidth = outlineStrokeWidth
        }

    private val outlineStartColor =
        boostChroma(context.getColor(com.android.internal.R.color.materialColorTertiaryContainer))
    private val outlineMiddleColor =
        boostChroma(context.getColor(com.android.internal.R.color.materialColorPrimaryFixedDim))
    private val outlineEndColor =
        boostChroma(context.getColor(com.android.internal.R.color.materialColorPrimary))

    // Animation
    private var gradientAnimator: ValueAnimator
    private val rotationStart = 35f // Start rotation at 35 degrees
    private var rotationAngle = rotationStart
    private var fadeAnimator: ValueAnimator? = null
    private var innerGlowAlpha = 255 // Fading out gradient

    init {
        gradientAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 5000
                startDelay = 1000
                interpolator = Interpolators.LINEAR
                repeatCount = 0
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            onAnimationEnded()
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            onAnimationCancelled()
                        }
                    }
                )
                addUpdateListener { animator ->
                    val animatedValue = animator.animatedValue as Float
                    rotationAngle = rotationStart + animatedValue * 720f // Rotate in a spiral
                    invalidateSelf()
                }
                start()
            }
        fadeAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 500
                startDelay = 5000
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    innerGlowAlpha = ((1 - progress) * 255).toInt() // Fade out inner glow
                    invalidateSelf()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            onAnimationStarted()
                        }
                    }
                )
                start()
            }
    }

    override fun draw(canvas: Canvas) {
        val boundsF = RectF(bounds)
        boundsF.inset(0f, insetVertical)
        innerGlow.setPosition(0, 0, bounds.width(), bounds.height())
        val glowCanvas = innerGlow.beginRecording(bounds.width(), bounds.height())
        try {
            val strokeWidth =
                if (isNightMode(context)) {
                    emphasizedOutlineStrokeWidth
                } else {
                    outlineStrokeWidth
                }
            drawAnimatedOutline(glowCanvas, boundsF, innerGlowAlpha, strokeWidth)
        } finally {
            innerGlow.endRecording()
        }

        canvas.withClip(buttonShape) {
            drawAnimatedOutline(canvas, boundsF, 255, outlineStrokeWidth)
            // Software rendering doesn't support drawRenderNode
            if (canvas.isHardwareAccelerated()) {
                canvas.drawRenderNode(innerGlow)
            }
        }
    }

    private fun drawAnimatedOutline(
        canvas: Canvas,
        boundsF: RectF,
        alpha: Int,
        strokeWidth: Float,
    ) {
        buttonShape.reset()
        buttonShape.addRoundRect(boundsF, cornerRadius, cornerRadius, Path.Direction.CW)

        // Set up outline gradient
        val gradientShader =
            LinearGradient(
                boundsF.left,
                boundsF.top,
                boundsF.right,
                boundsF.bottom,
                intArrayOf(outlineStartColor, outlineMiddleColor, outlineEndColor),
                floatArrayOf(0.2f, 0.5f, 0.8f),
                Shader.TileMode.CLAMP,
            )
        // Create a rotation matrix for the spiral effect
        val matrix = Matrix()
        matrix.setRotate(rotationAngle, boundsF.centerX(), boundsF.centerY())
        gradientShader.setLocalMatrix(matrix)

        // Apply gradient to outline
        outlineGradientPaint.shader = gradientShader
        outlineGradientPaint.alpha = alpha
        outlineGradientPaint.strokeWidth = strokeWidth
        canvas.drawPath(buttonShape, outlineGradientPaint)
    }

    private fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf() // Redraw when size changes
    }

    override fun setAlpha(alpha: Int) {
        outlineGradientPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        outlineGradientPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun boostChroma(color: Int): Int {
        val hctColor = FloatArray(3)
        ColorUtils.colorToM3HCT(color, hctColor)
        val chroma = hctColor[1]
        return if (chroma < 5) {
            color
        } else {
            ColorUtils.M3HCTToColor(hctColor[0], 70f, hctColor[2])
        }
    }
}
