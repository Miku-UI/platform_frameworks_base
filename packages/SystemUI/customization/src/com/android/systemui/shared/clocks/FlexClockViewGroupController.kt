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

package com.android.systemui.shared.clocks

import android.graphics.Rect
import android.icu.util.TimeZone
import com.android.app.animation.Interpolators
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.DigitalTimeFormatter
import com.android.systemui.customization.clocks.DigitalTimespec
import com.android.systemui.customization.clocks.FontTextStyle
import com.android.systemui.customization.clocks.view.DigitalAlignment
import com.android.systemui.customization.clocks.view.HorizontalAlignment
import com.android.systemui.customization.clocks.view.VerticalAlignment
import com.android.systemui.plugins.keyguard.VPointF
import com.android.systemui.plugins.keyguard.data.model.AlarmData
import com.android.systemui.plugins.keyguard.data.model.WeatherData
import com.android.systemui.plugins.keyguard.data.model.ZenData
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAnimations
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import com.android.systemui.shared.clocks.view.FlexClockViewGroup
import java.util.Locale

class FlexClockViewGroupController(private val clockCtx: ClockContext) : FlexClockViewController {
    private val logger =
        ClockLogger(null, clockCtx.messageBuffer, FlexClockViewGroupController::class.simpleName!!)

    val layerControllers = mutableListOf<FlexClockViewController>()
    val dozeState = DefaultClockController.AnimationState(1F)

    override val view = FlexClockViewGroup(clockCtx)
    override var onViewBoundsChanged by view::onViewBoundsChanged
    override var onViewMaxSizeChanged by view::onViewMaxSizeChanged

    init {
        fun createController(cfg: LayerConfig) {
            val controller = FlexClockTextViewController(clockCtx, cfg, isLargeClock = true)
            view.addView(controller.view)
            layerControllers.add(controller)
        }

        val layerCfg =
            LayerConfig(
                style = FontTextStyle(lineHeight = 147.25f),
                alignment = DigitalAlignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER),
                aodStyle =
                    FontTextStyle(
                        transitionInterpolator = Interpolators.EMPHASIZED,
                        transitionDuration = FlexClockViewGroup.AOD_TRANSITION_DURATION,
                    ),

                // Placeholder Timespec Values
                timespec = DigitalTimespec.DIGIT_PAIR,
                timeFormatter = null,
            )

        DigitalTimeFormatter("hh", clockCtx.timeKeeper).also { timeFormatter ->
            createController(
                layerCfg.copy(timespec = DigitalTimespec.FIRST_DIGIT, timeFormatter = timeFormatter)
            )
            createController(
                layerCfg.copy(
                    timespec = DigitalTimespec.SECOND_DIGIT,
                    timeFormatter = timeFormatter,
                )
            )
        }

        DigitalTimeFormatter("mm", clockCtx.timeKeeper).also { timeFormatter ->
            createController(
                layerCfg.copy(timespec = DigitalTimespec.FIRST_DIGIT, timeFormatter = timeFormatter)
            )
            createController(
                layerCfg.copy(
                    timespec = DigitalTimespec.SECOND_DIGIT,
                    timeFormatter = timeFormatter,
                )
            )
        }
    }

    private fun refreshTime() {
        layerControllers.forEach { it.faceEvents.onTimeTick() }
        view.refreshTime()
    }

    override val events =
        object : ClockEvents {
            override fun onTimeZoneChanged(timeZone: TimeZone) {
                layerControllers.forEach { it.events.onTimeZoneChanged(timeZone) }
                refreshTime()
            }

            override fun onTimeFormatChanged(formatKind: TimeFormatKind) {
                layerControllers.forEach { it.events.onTimeFormatChanged(formatKind) }
                refreshTime()
            }

            override fun onLocaleChanged(locale: Locale) {
                layerControllers.forEach { it.events.onLocaleChanged(locale) }
                view.onLocaleChanged(locale)
                refreshTime()
            }

            override fun onWeatherDataChanged(data: WeatherData) {}

            override fun onAlarmDataChanged(data: AlarmData) {}

            override fun onZenDataChanged(data: ZenData) {}

            override var isReactiveTouchInteractionEnabled
                get() = view.isReactiveTouchInteractionEnabled
                set(value) {
                    view.isReactiveTouchInteractionEnabled = value
                }
        }

    override val animations =
        object : ClockAnimations {
            override fun enter() {
                refreshTime()
            }

            override fun doze(fraction: Float) {
                val (hasChanged, hasJumped) = dozeState.update(fraction)
                if (hasChanged) view.animateDoze(dozeState.isActive, !hasJumped)
                view.dozeFraction = fraction
                view.invalidate()
            }

            override fun fold(fraction: Float) {
                refreshTime()
            }

            override fun charge() {
                view.animateCharge()
            }

            override fun onPositionAnimated(args: ClockPositionAnimationArgs) {}

            override fun onPickerCarouselSwiping(swipingFraction: Float) {}

            override fun onFidgetTap(x: Float, y: Float) {
                view.animateFidget(VPointF(x, y), enforceBounds = true)
            }

            private var hasFontAxes = false

            override fun onFontAxesChanged(style: ClockAxisStyle) {
                view.updateAxes(style, isAnimated = hasFontAxes)
                hasFontAxes = true
            }
        }

    override val faceEvents =
        object : ClockFaceEvents {
            override fun onTimeTick() {
                refreshTime()
            }

            override fun onThemeChanged(theme: ThemeConfig) {
                view.updateColor(
                    lockscreenColor = theme.getDefaultColor(clockCtx.context),
                    aodColor = theme.getAodColor(clockCtx.context),
                )
            }

            override fun onFontSettingChanged(fontSizePx: Float) {
                view.onFontSettingChanged(fontSizePx)
            }

            override fun onTargetRegionChanged(targetRegion: Rect?) {}

            override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}
        }

    override val config =
        ClockFaceConfig(
            hasCustomWeatherDataDisplay = false,
            hasCustomPositionUpdatedAnimation = true,
        )
}
