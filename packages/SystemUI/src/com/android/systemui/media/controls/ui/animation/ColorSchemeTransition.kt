/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.media.controls.ui.animation

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import com.android.internal.R
import com.android.internal.annotations.VisibleForTesting
import com.android.systemui.Flags.enableSuggestedDeviceUi
import com.android.systemui.media.controls.ui.view.MediaViewHolder
import com.android.systemui.monet.ColorScheme
import com.android.systemui.surfaceeffects.loadingeffect.LoadingEffect
import com.android.systemui.surfaceeffects.ripple.MultiRippleController
import com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseController

/**
 * A [ColorTransition] is an object that updates the colors of views each time [updateColorScheme]
 * is triggered.
 */
interface ColorTransition {
    fun updateColorScheme(scheme: ColorScheme?): Boolean
}

/**
 * A [ColorTransition] that animates between two specific colors. It uses a ValueAnimator to execute
 * the animation and interpolate between the source color and the target color.
 *
 * Selection of the target color from the scheme, and application of the interpolated color are
 * delegated to callbacks.
 */
open class AnimatingColorTransition(
    private val defaultColor: Int,
    private val extractColor: (ColorScheme) -> Int,
    private val applyColor: (Int) -> Unit,
) : AnimatorUpdateListener, ColorTransition {

    private val argbEvaluator = ArgbEvaluator()
    private val valueAnimator = buildAnimator()
    var sourceColor: Int = defaultColor
    var currentColor: Int = defaultColor
    var targetColor: Int = defaultColor

    override fun onAnimationUpdate(animation: ValueAnimator) {
        currentColor =
            argbEvaluator.evaluate(animation.animatedFraction, sourceColor, targetColor) as Int
        applyColor(currentColor)
    }

    override fun updateColorScheme(scheme: ColorScheme?): Boolean {
        val newTargetColor = if (scheme == null) defaultColor else extractColor(scheme)
        if (newTargetColor != targetColor) {
            sourceColor = currentColor
            targetColor = newTargetColor
            valueAnimator.cancel()
            valueAnimator.start()
            return true
        }
        return false
    }

    init {
        applyColor(defaultColor)
    }

    @VisibleForTesting
    open fun buildAnimator(): ValueAnimator {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 333
        animator.addUpdateListener(this)
        return animator
    }
}

typealias AnimatingColorTransitionFactory =
    (Int, (ColorScheme) -> Int, (Int) -> Unit) -> AnimatingColorTransition

/**
 * ColorSchemeTransition constructs a ColorTransition for each color in the scheme that needs to be
 * transitioned when changed. It also sets up the assignment functions for sending the sending the
 * interpolated colors to the appropriate views.
 */
class ColorSchemeTransition
internal constructor(
    private val context: Context,
    private val mediaViewHolder: MediaViewHolder,
    private val multiRippleController: MultiRippleController,
    private val turbulenceNoiseController: TurbulenceNoiseController,
    animatingColorTransitionFactory: AnimatingColorTransitionFactory,
) {
    constructor(
        context: Context,
        mediaViewHolder: MediaViewHolder,
        multiRippleController: MultiRippleController,
        turbulenceNoiseController: TurbulenceNoiseController,
    ) : this(
        context,
        mediaViewHolder,
        multiRippleController,
        turbulenceNoiseController,
        ::AnimatingColorTransition,
    )

    var loadingEffect: LoadingEffect? = null

    private val buttonStrokeWidth =
        context.resources.getDimensionPixelSize(
            com.android.systemui.res.R.dimen.qs_media_button_stroke_width
        )

    // Defaults may be briefly visible before loading a new player's colors
    private val backgroundDefault = context.getColor(R.color.system_on_surface_light)
    private val primaryDefault = context.getColor(R.color.system_primary_dark)
    private val onPrimaryDefault = context.getColor(R.color.system_on_primary_dark)
    private val outlineDefault = context.getColor(R.color.system_outline_dark)

    private val backgroundColor: AnimatingColorTransition by lazy {
        animatingColorTransitionFactory(backgroundDefault, ::backgroundFromScheme) { color ->
            mediaViewHolder.albumView.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private val primaryColor: AnimatingColorTransition by lazy {
        animatingColorTransitionFactory(primaryDefault, ::primaryFromScheme) { primaryColor ->
            val primaryColorList = ColorStateList.valueOf(primaryColor)
            mediaViewHolder.actionPlayPause.backgroundTintList = primaryColorList
            mediaViewHolder.seamlessButton.backgroundTintList = primaryColorList
            (mediaViewHolder.seamlessButton.background as? RippleDrawable)?.let {
                it.setColor(primaryColorList)
                it.effectColor = primaryColorList
            }
            mediaViewHolder.seekBar.progressBackgroundTintList = primaryColorList
        }
    }

    private val onPrimaryColor: AnimatingColorTransition by lazy {
        animatingColorTransitionFactory(onPrimaryDefault, ::onPrimaryFromScheme) { onPrimaryColor ->
            val onPrimaryColorList = ColorStateList.valueOf(onPrimaryColor)
            mediaViewHolder.actionPlayPause.imageTintList = onPrimaryColorList
            mediaViewHolder.seamlessText.setTextColor(onPrimaryColor)
            mediaViewHolder.seamlessIcon.imageTintList = onPrimaryColorList
        }
    }

    private val outlineColor: AnimatingColorTransition by lazy {
        animatingColorTransitionFactory(outlineDefault, ::outlineFromScheme) { outlineColor ->
            if (enableSuggestedDeviceUi()) {
                (mediaViewHolder.deviceSuggestionButton.background as? RippleDrawable)?.let {
                    val shape = it.findDrawableByLayerId(R.id.background)
                    if (shape is GradientDrawable) {
                        shape.setStroke(buttonStrokeWidth, outlineColor)
                    }
                }
            }
        }
    }

    fun getDeviceIconColor(): Int {
        return onPrimaryColor.targetColor
    }

    fun getAppIconColor(): Int {
        return primaryColor.targetColor
    }

    fun getSurfaceEffectColor(): Int {
        return primaryColor.targetColor
    }

    fun getGutsTextColor(): Int {
        return context.getColor(com.android.systemui.res.R.color.media_on_background)
    }

    private fun getColorTransitions(): Array<AnimatingColorTransition> {
        return arrayOf(backgroundColor, primaryColor, onPrimaryColor, outlineColor)
    }

    fun updateColorScheme(colorScheme: ColorScheme?): Boolean {
        var anyChanged = false
        getColorTransitions().forEach {
            val isChanged = it.updateColorScheme(colorScheme)
            anyChanged = isChanged || anyChanged
        }
        getSurfaceEffectColor().let {
            multiRippleController.updateColor(it)
            turbulenceNoiseController.updateNoiseColor(it)
            loadingEffect?.updateColor(it)
        }
        mediaViewHolder.gutsViewHolder.setTextColor(getGutsTextColor())
        colorScheme?.let { mediaViewHolder.gutsViewHolder.setColors(it) }
        return anyChanged
    }
}
