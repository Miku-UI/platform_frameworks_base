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

package com.android.systemui.shared.clocks.controller

import android.icu.util.TimeZone
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.android.systemui.customization.clocks.DigitalTimespecHandler
import com.android.systemui.plugins.keyguard.VRect
import com.android.systemui.plugins.keyguard.data.model.AlarmData
import com.android.systemui.plugins.keyguard.data.model.WeatherData
import com.android.systemui.plugins.keyguard.data.model.ZenData
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAnimations
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPositionAnimationArgs
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import com.android.systemui.shared.clocks.FlexClockContext
import com.android.systemui.shared.clocks.view.BitmapClockView
import java.util.Locale

class BitmapClockViewController(
    private val clockCtx: FlexClockContext,
    layerConfig: LayerConfig, // contains time spec
    isLargeClock: Boolean,
) : FlexClockViewController {
    override val view = BitmapClockView(clockCtx.context, null)

    override var onViewBoundsChanged by view::onViewBoundsChanged
    override var onViewMaxSizeChanged by view::onViewMaxSizeChanged

    private val timespec = DigitalTimespecHandler(layerConfig.timespec, layerConfig.timeFormatter!!)

    override val events =
        object : ClockEvents {
            override fun onTimeZoneChanged(timeZone: TimeZone) {}

            override fun onTimeFormatChanged(formatKind: TimeFormatKind) {}

            override fun onLocaleChanged(locale: Locale) {}

            override fun onWeatherDataChanged(data: WeatherData) {}

            override fun onAlarmDataChanged(data: AlarmData) {}

            override fun onZenDataChanged(data: ZenData) {}
        }

    init {
        // To get HOUR_DIGIT_PAIR or MINUTE_DIGIT_PAIR
        view.id = timespec.getViewId()

        view.layoutParams =
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
    }

    fun refreshTime() {
        val text = timespec.getText()
        if (view.text != text) {
            view.text = text
        }
    }

    private fun applyLayout() {
        val lp = view.layoutParams as? RelativeLayout.LayoutParams ?: return
        lp.addRule(RelativeLayout.TEXT_ALIGNMENT_CENTER)
        lp.addRule(RelativeLayout.CENTER_VERTICAL)

        when (view.id) {
            ClockViewIds.HOUR_DIGIT_PAIR -> {
                lp.addRule(RelativeLayout.ALIGN_PARENT_START)
            }
            ClockViewIds.MINUTE_DIGIT_PAIR -> {
                lp.addRule(RelativeLayout.END_OF, ClockViewIds.HOUR_DIGIT_PAIR)
            }
        }
        view.layoutParams = lp
    }

    override val animations =
        object : ClockAnimations {
            override fun enter() {
                applyLayout()
                refreshTime()
            }

            override fun doze(fraction: Float) {
                view.dozeFraction = fraction
            }

            override fun fold(fraction: Float) {
                applyLayout()
            }

            override fun charge() {
                view.animateCharge()
            }

            override fun onPositionAnimated(anim: ClockPositionAnimationArgs) {}

            override fun onPickerCarouselSwiping(swipingFraction: Float) {}

            override fun onFidgetTap(x: Float, y: Float) {}

            override fun onFontAxesChanged(style: ClockAxisStyle) {}
        }

    override val faceEvents =
        object : ClockFaceEvents {
            override fun onTimeTick() {
                refreshTime()

                view.contentDescription = timespec.getContentDescription()
                view.importantForAccessibility =
                    if (view.contentDescription == null) {
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    } else {
                        View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    }
            }

            override fun onThemeChanged(theme: ThemeConfig) {
                refreshTime()
            }

            override fun onFontSettingChanged(fontSizePx: Float) {}

            override fun onTargetRegionChanged(targetRegion: VRect) {}

            override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}
        }

    override val config = ClockFaceConfig()
}
