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

package com.android.systemui.util.ui.compose

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.sliderPercentage
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.systemui.biometrics.Utils.toBitmap
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.haptics.slider.SeekableSliderTrackerConfig
import com.android.systemui.haptics.slider.SliderHapticFeedbackConfig
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.util.ui.compose.Dimensions.IconPadding
import com.android.systemui.util.ui.compose.Dimensions.IconSize
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues

/**
 * A slider that has one active and one inactive icon. The inactive icon is justified to the end of
 * the track and when the thumb is swiped to the end of the track, the inactive icon disappears and
 * an active icon appears just before the slider thumb. The active icon moves with the thumb.
 * Similarly, when the slider moves away from the end of the track and has enough space for an icon
 * to the end, the active icon disappears and the inactive icon appears, again at justified at the
 * end of the track.
 *
 * @param levelValue the value of the slider
 * @param valueRange start and end of the above value
 * @param iconResProvider to switch between different icons depending on slider percentage
 * @param imageLoader to load slider icons
 * @param hapticsViewModelFactory synced with value changes
 * @param modifier slider modifier
 * @param onDrag to be called onValueChange
 * @param onStop to be called on ValueChangeFinished
 * @param isEnabled when false the slider grays out
 * @param interactionSource shared between the slider and the thumb
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualIconSlider(
    levelValue: Int,
    valueRange: IntRange,
    iconResProvider: (Float) -> Int,
    imageLoader: suspend (Int, Context) -> Icon.Loaded,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    colors: SliderColors = defaultColors(),
    onDrag: (Int) -> Unit = {},
    onStop: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    isEnabled: Boolean = true,
) {
    var value by remember(levelValue) { mutableIntStateOf(levelValue) }
    val animatedValue by
        animateFloatAsState(targetValue = value.toFloat(), label = "DualIconSliderAnimatedValue")

    val floatValueRange = valueRange.first.toFloat()..valueRange.last.toFloat()

    val hapticsViewModel: SliderHapticsViewModel =
        rememberViewModel(traceName = "SliderHapticsViewModel") {
            hapticsViewModelFactory.create(
                interactionSource,
                floatValueRange,
                Orientation.Horizontal,
                SliderHapticFeedbackConfig(
                    maxVelocityToScale = 1f /* slider progress(from 0 to 1) per sec */
                ),
                SeekableSliderTrackerConfig(),
            )
        }

    // The value state is recreated every time gammaValue changes, so we recreate this derivedState
    // We have to use value as that's the value that changes when the user is dragging (gammaValue
    // is always the starting value: e.g. actual (not temporary) brightness).
    val iconRes by
        remember(levelValue, valueRange) {
            derivedStateOf {
                val percentage =
                    (value - valueRange.first) * 100f / (valueRange.last - valueRange.first)
                iconResProvider(percentage)
            }
        }
    val context = LocalContext.current
    val painter: Painter by
        produceState<Painter>(
            initialValue = ColorPainter(Color.Transparent),
            key1 = iconRes,
            key2 = context,
        ) {
            val icon = imageLoader(iconRes, context)
            // toBitmap is Drawable?.() -> Bitmap? and handles null internally.
            val bitmap = icon.drawable.toBitmap()!!.asImageBitmap()
            this@produceState.value = BitmapPainter(bitmap)
        }

    val activeIconColor = colors.activeTickColor
    val inactiveIconColor = colors.inactiveTickColor
    // Offset from the right
    val trackIcon: DrawScope.(Offset, Color, Float) -> Unit = remember {
        { offset, color, alpha ->
            val rtl = layoutDirection == LayoutDirection.Rtl
            scale(if (rtl) -1f else 1f, 1f) {
                translate(offset.x - IconPadding.toPx() - IconSize.toSize().width, offset.y) {
                    with(painter) {
                        draw(
                            IconSize.toSize(),
                            colorFilter = ColorFilter.tint(color),
                            alpha = alpha,
                        )
                    }
                }
            }
        }
    }

    Slider(
        value = animatedValue,
        valueRange = floatValueRange,
        enabled = isEnabled,
        colors = colors,
        onValueChange = {
            if (isEnabled) {
                hapticsViewModel.onValueChange(it)
                value = it.toInt()
                onDrag(value)
            }
        },
        onValueChangeFinished = {
            if (isEnabled) {
                hapticsViewModel.onValueChangeEnded()
                onStop(value)
            }
        },
        modifier =
            modifier
                .sliderPercentage {
                    (value - valueRange.first).toFloat() / (valueRange.last - valueRange.first)
                }
                .sysuiResTag("slider"),
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                enabled = isEnabled,
                thumbSize = DpSize(Dimensions.ThumbWidth, Dimensions.ThumbHeight),
                colors = colors,
            )
        },
        track = { sliderState ->
            DualIconTrack(sliderState, trackIcon, inactiveIconColor, activeIconColor, colors)
        },
    )
}

private object Dimensions {
    val SliderTrackRoundedCorner = 12.dp
    val IconSize = DpSize(28.dp, 28.dp)
    val IconPadding = 6.dp
    val ThumbTrackGapSize = 6.dp
    val ThumbWidth = 4.dp
    val ThumbHeight = 52.dp
    val TrackInsideCornerSize = 2.dp
    val TrackHeight = 40.dp
}

private object AnimationSpecs {
    val IconAppearSpec = tween<Float>(durationMillis = 100, delayMillis = 33)
    val IconDisappearSpec = tween<Float>(durationMillis = 50)
}

private suspend fun Animatable<Float, AnimationVector1D>.appear() =
    animateTo(targetValue = 1f, animationSpec = AnimationSpecs.IconAppearSpec)

private suspend fun Animatable<Float, AnimationVector1D>.disappear() =
    animateTo(targetValue = 0f, animationSpec = AnimationSpecs.IconDisappearSpec)

@VisibleForTesting
object SliderMotionTestKeys {
    val AnimatingIcon = MotionTestValueKey<Boolean>("animatingIcon")
    val ActiveIconAlpha = MotionTestValueKey<Float>("activeIconAlpha")
    val InactiveIconAlpha = MotionTestValueKey<Float>("inactiveIconAlpha")
}

@Composable
fun defaultColors(): SliderColors {
    return SliderDefaults.colors()
        .copy(
            inactiveTrackColor = LocalAndroidColorScheme.current.surfaceEffect1,
            activeTickColor = MaterialTheme.colorScheme.onPrimary,
            inactiveTickColor = MaterialTheme.colorScheme.onSurface,
        )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
private fun DualIconTrack(
    sliderState: SliderState,
    trackIcon: DrawScope.(Offset, Color, Float) -> Unit,
    inactiveIconColor: Color,
    activeIconColor: Color,
    colors: SliderColors,
) {
    var showIconActive: Boolean? by remember { mutableStateOf(null) }
    val transition = updateTransition(showIconActive, label = "iconAlpha")
    val iconActiveAlpha by transition.animateAlpha(initialValue = 1f, active = true)
    val iconInactiveAlpha by transition.animateAlpha(initialValue = 1f, active = false)

    SliderDefaults.Track(
        sliderState = sliderState,
        modifier =
            Modifier.motionTestValues {
                    transition.isRunning exportAs SliderMotionTestKeys.AnimatingIcon
                    iconActiveAlpha exportAs SliderMotionTestKeys.ActiveIconAlpha
                    iconInactiveAlpha exportAs SliderMotionTestKeys.InactiveIconAlpha
                }
                .height(Dimensions.TrackHeight)
                .drawWithContent {
                    drawContent()

                    val yOffset = size.height / 2 - IconSize.toSize().height / 2
                    val activeTrackStart = 0f
                    val activeTrackEnd =
                        size.width * sliderState.coercedValueAsFraction -
                            Dimensions.ThumbTrackGapSize.toPx()
                    val inactiveTrackStart =
                        activeTrackEnd + Dimensions.ThumbTrackGapSize.toPx() * 2
                    val inactiveTrackEnd = size.width

                    val activeTrackWidth = activeTrackEnd - activeTrackStart
                    val inactiveTrackWidth = inactiveTrackEnd - inactiveTrackStart

                    if (IconSize.toSize().width < inactiveTrackWidth - IconPadding.toPx() * 2) {
                        showIconActive = false
                        trackIcon(
                            Offset(inactiveTrackEnd, yOffset),
                            inactiveIconColor,
                            iconInactiveAlpha,
                        )
                    } else if (
                        IconSize.toSize().width < activeTrackWidth - IconPadding.toPx() * 2
                    ) {
                        showIconActive = true
                        trackIcon(Offset(activeTrackEnd, yOffset), activeIconColor, iconActiveAlpha)
                    }
                },
        trackCornerSize = Dimensions.SliderTrackRoundedCorner,
        trackInsideCornerSize = Dimensions.TrackInsideCornerSize,
        drawStopIndicator = null,
        thumbTrackGapSize = Dimensions.ThumbTrackGapSize,
        colors = colors,
    )
}

@Composable
private fun Transition<Boolean?>.animateAlpha(initialValue: Float, active: Boolean): State<Float> {
    val animatable = remember { Animatable(initialValue) }
    targetState?.let {
        val target = if (active) it else !it
        LaunchedEffect(target) {
            // Snap at first (null) state, then animate
            if (currentState == null) {
                animatable.snapTo(if (target) 1f else 0f)
            } else if (target) {
                animatable.appear()
            } else {
                animatable.disappear()
            }
        }
    }
    return animatable.asState()
}
