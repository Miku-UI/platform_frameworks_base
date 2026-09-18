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

package com.android.systemui.shared.clocks.controller

import android.icu.util.TimeZone
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import com.android.app.animation.Interpolators
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.animation.GSFAxes
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.DigitalTimeFormatter
import com.android.systemui.customization.clocks.DigitalTimespec
import com.android.systemui.customization.clocks.FontTextStyleImpl
import com.android.systemui.customization.clocks.R
import com.android.systemui.customization.clocks.utils.FontUtils.get
import com.android.systemui.customization.clocks.utils.FontUtils.set
import com.android.systemui.customization.clocks.utils.ViewUtils.computeLayoutDiff
import com.android.systemui.customization.clocks.utils.ViewUtils.translation
import com.android.systemui.customization.clocks.view.DefaultClockFaceLayout
import com.android.systemui.customization.clocks.view.DigitalAlignment
import com.android.systemui.customization.clocks.view.HorizontalAlignment
import com.android.systemui.customization.clocks.view.VerticalAlignment
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.VRect
import com.android.systemui.plugins.keyguard.VRectF
import com.android.systemui.plugins.keyguard.data.model.AlarmData
import com.android.systemui.plugins.keyguard.data.model.WeatherData
import com.android.systemui.plugins.keyguard.data.model.ZenData
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAnimations
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceLayout
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFontAxis.Companion.merge
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Clock
import com.android.systemui.shared.clocks.FlexClockContext
import com.android.systemui.shared.clocks.controller.FlexClockController.Companion.getDefaultAxes
import com.android.systemui.shared.clocks.view.FlexClockViewGroup
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

interface FlexClockViewController {
    val view: View
    val events: ClockEvents
    val animations: ClockAnimations
    val faceEvents: ClockFaceEvents
    val config: ClockFaceConfig

    var onViewBoundsChanged: ((VRectF) -> Unit)?
    var onViewMaxSizeChanged: ((VPointF) -> Unit)?
}

class FlexClockFaceController(
    private val clockCtx: FlexClockContext,
    private val isLargeClock: Boolean,
) : ClockFaceController {
    override val view: View
        get() = layerController.view

    private val logger =
        ClockLogger(null, clockCtx.messageBuffer, FlexClockFaceController::class.simpleName!!)

    override val config = ClockFaceConfig(hasCustomPositionUpdatedAnimation = true)

    override var theme = ThemeConfig(true, clockCtx.settings.seedColor)

    private val keyguardLargeClockTopMargin =
        clockCtx.resources.getDimensionPixelSize(R.dimen.keyguard_large_clock_top_margin)
    private val timeFormatter =
        DigitalTimeFormatter("h:mm", clockCtx.timeKeeper, enableContentDescription = true)
    val layerController: FlexClockViewController =
        if (isLargeClock) {
            FlexClockViewGroupController(clockCtx)
        } else {
            val cfg = SMALL_LAYER_CONFIG.copy(timeFormatter = timeFormatter)
            FlexClockTextViewController(clockCtx, cfg, isLargeClock)
        }

    init {
        layerController.view.layoutParams =
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply { gravity = Gravity.CENTER }
    }

    override val layout: ClockFaceLayout =
        DefaultClockFaceLayout(view).apply {
            (view as? FlexClockViewGroup)?.let { view ->
                largeClockModifier = {
                    Modifier.onGloballyPositioned {
                        when (val state = layoutState.transitionState) {
                            is TransitionState.Transition -> {
                                val start = Clock.Large.targetOffset(state.fromContent)
                                val end = Clock.Large.targetOffset(state.toContent)
                                if (start != null && end != null) {
                                    view.offsetGlyphsForStepClockAnimation(
                                        startX = start.x,
                                        endX = end.x,
                                        progress = state.progress,
                                    )
                                } else {
                                    logger.e({
                                        "Failed to execute clock-stepping animation: " +
                                            "progress=$double1; start=$str1; end=$str2"
                                    }) {
                                        double1 = state.progress.toDouble()
                                        str1 = "$start"
                                        str2 = "$end"
                                    }
                                }
                            }
                            else -> view.resetGlyphsOffsets()
                        }
                    }
                }
            }
            view.id =
                if (isLargeClock) ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE
                else ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL
        }

    override val events = FlexClockFaceEvents()

    inner class FlexClockFaceEvents : ClockEvents, ClockFaceEvents {
        override fun onTimeTick() {
            clockCtx.timeKeeper.updateTime()
            view.contentDescription = timeFormatter.getContentDescription()
            layerController.faceEvents.onTimeTick()
        }

        override fun onTimeZoneChanged(timeZone: TimeZone) {
            logger.onTimeZoneChanged(timeZone)
            clockCtx.timeKeeper.timeZone = timeZone
            layerController.events.onTimeZoneChanged(timeZone)
        }

        override fun onTimeFormatChanged(formatKind: TimeFormatKind) {
            logger.onTimeFormatChanged(formatKind)
            timeFormatter.formatKind = formatKind
            layerController.events.onTimeFormatChanged(formatKind)
        }

        override fun onLocaleChanged(locale: Locale) {
            logger.onLocaleChanged(locale)
            timeFormatter.locale = locale
            layerController.events.onLocaleChanged(locale)
        }

        override fun onFontSettingChanged(fontSizePx: Float) {
            layerController.faceEvents.onFontSettingChanged(fontSizePx)
            view.requestLayout()
        }

        override fun onThemeChanged(theme: ThemeConfig) {
            this@FlexClockFaceController.theme = theme
            layerController.faceEvents.onThemeChanged(theme)
        }

        /**
         * targetRegion passed to all customized clock applies counter translationY of Keyguard and
         * keyguard_large_clock_top_margin from default clock
         */
        override fun onTargetRegionChanged(targetRegion: VRect) {
            var maxWidth = 0f
            var maxHeight = 0f

            layerController.faceEvents.onTargetRegionChanged(targetRegion)
            maxWidth = max(maxWidth, view.layoutParams.width.toFloat())
            maxHeight = max(maxHeight, view.layoutParams.height.toFloat())

            val lp =
                if (maxHeight <= 0 || maxWidth <= 0) {
                    // No specified width/height. Just match parent size.
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                } else {
                    // Scale to fit in targetRegion based on largest child elements.
                    val ratio = maxWidth / maxHeight
                    val targetRatio = targetRegion.width / targetRegion.height
                    val scale =
                        if (ratio > targetRatio) targetRegion.width / maxWidth
                        else targetRegion.height / maxHeight

                    FrameLayout.LayoutParams(
                        ceil(maxWidth * scale).toInt(),
                        ceil(maxHeight * scale).toInt(),
                    )
                }

            lp.gravity = Gravity.CENTER
            view.layoutParams = lp
            view.translation = view.computeLayoutDiff(targetRegion, isLargeClock)
        }

        override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}

        override fun onWeatherDataChanged(data: WeatherData) {
            layerController.events.onWeatherDataChanged(data)
        }

        override fun onAlarmDataChanged(data: AlarmData) {
            layerController.events.onAlarmDataChanged(data)
        }

        override fun onZenDataChanged(data: ZenData) {
            layerController.events.onZenDataChanged(data)
        }
    }

    override val animations =
        object : ClockAnimations {
            override fun enter() {
                layerController.animations.enter()
            }

            override fun doze(fraction: Float) {
                layerController.animations.doze(fraction)
            }

            override fun fold(fraction: Float) {
                layerController.animations.fold(fraction)
            }

            override fun charge() {
                layerController.animations.charge()
            }

            override fun onPickerCarouselSwiping(swipingFraction: Float) {
                if (isLargeClock) {
                    view.translationY = keyguardLargeClockTopMargin / 2F * swipingFraction
                }
                layerController.animations.onPickerCarouselSwiping(swipingFraction)
                view.invalidate()
            }

            override fun onPositionAnimated(anim: ClockPositionAnimationArgs) {
                layerController.animations.onPositionAnimated(anim)
                if (isLargeClock) {
                    (view as? FlexClockViewGroup)?.offsetGlyphsForStepClockAnimation(anim)
                }
            }

            override fun onFidgetTap(x: Float, y: Float) {
                layerController.animations.onFidgetTap(x, y)
            }

            override fun onFontAxesChanged(style: ClockAxisStyle) {
                val axes = ClockAxisStyle(getDefaultAxes(clockCtx.settings).merge(style))
                if (!isLargeClock && axes[GSFAxes.WIDTH] > SMALL_CLOCK_MAX_WDTH) {
                    axes[GSFAxes.WIDTH] = SMALL_CLOCK_MAX_WDTH
                }

                layerController.animations.onFontAxesChanged(axes)
            }
        }

    companion object {
        const val SMALL_CLOCK_MAX_WDTH = 120f

        val SMALL_LAYER_CONFIG =
            LayerConfig(
                style = FontTextStyleImpl(fontSizeScale = 0.98f, fontFeatureSettings = "pnum"),
                aodStyle =
                    FontTextStyleImpl(
                        transitionInterpolator = Interpolators.EMPHASIZED,
                        transitionDuration = FlexClockViewGroup.AOD_TRANSITION_DURATION,
                        fontFeatureSettings = "pnum",
                    ),
                alignment = DigitalAlignment(HorizontalAlignment.START, VerticalAlignment.CENTER),
                timespec = DigitalTimespec.TIME_FULL_FORMAT,
                timeFormatter = null, // Placeholder
            )
    }
}
