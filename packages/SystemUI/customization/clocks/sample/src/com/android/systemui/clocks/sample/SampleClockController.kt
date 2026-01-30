/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.clocks.sample

import android.content.Context
import android.icu.util.TimeZone
import com.android.systemui.customization.clocks.DigitalTimeFormatter
import com.android.systemui.customization.clocks.TimeKeeper
import com.android.systemui.plugins.keyguard.data.model.AlarmData
import com.android.systemui.plugins.keyguard.data.model.WeatherData
import com.android.systemui.plugins.keyguard.data.model.ZenData
import com.android.systemui.plugins.keyguard.ui.clocks.ClockConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEventListeners
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import java.io.PrintWriter
import java.util.Locale

class SampleClockController(
    private val hostCtx: Context,
    private val pluginCtx: Context,
    private val settings: ClockSettings,
    private val messageBuffers: ClockMessageBuffers,
    private val timeKeeper: TimeKeeper,
) : ClockController {
    val timeFormatter = DigitalTimeFormatter("hh:mm", timeKeeper)

    // Small clock above notifications
    override val smallClock =
        SampleClockFaceController(
            hostCtx,
            pluginCtx,
            settings,
            timeFormatter,
            messageBuffers.smallClockMessageBuffer,
            isLargeClock = false,
        )

    // Full page clock when no notifications
    override val largeClock =
        SampleClockFaceController(
            hostCtx,
            pluginCtx,
            settings,
            timeFormatter,
            messageBuffers.largeClockMessageBuffer,
            isLargeClock = true,
        )

    override val config =
        ClockConfig(
            SampleClockProvider.SAMPLE_CLOCK_ID,
            pluginCtx.resources.getString(R.string.sample_clock_name),
            pluginCtx.resources.getString(R.string.sample_clock_description),
        )

    override val eventListeners = ClockEventListeners()

    override val events =
        object : ClockEvents {
            override var isReactiveTouchInteractionEnabled = false

            override fun onTimeZoneChanged(timeZone: TimeZone) {
                timeFormatter.timeKeeper.timeZone = timeZone

                smallClock.events.onTimeTick()
                largeClock.events.onTimeTick()
            }

            override fun onTimeFormatChanged(formatKind: TimeFormatKind) {
                timeFormatter.formatKind = formatKind

                smallClock.events.onTimeTick()
                largeClock.events.onTimeTick()
            }

            override fun onLocaleChanged(locale: Locale) {
                timeFormatter.locale = locale

                smallClock.events.onTimeTick()
                largeClock.events.onTimeTick()
            }

            override fun onWeatherDataChanged(data: WeatherData) {}

            override fun onAlarmDataChanged(data: AlarmData) {}

            override fun onZenDataChanged(data: ZenData) {}
        }

    override fun initialize(isDarkTheme: Boolean, dozeFraction: Float, foldFraction: Float) {
        smallClock.run {
            events.onThemeChanged(theme.copy(isDarkTheme = isDarkTheme))
            animations.doze(dozeFraction)
            animations.fold(foldFraction)
            events.onTimeTick()
        }

        largeClock.run {
            events.onThemeChanged(theme.copy(isDarkTheme = isDarkTheme))
            animations.doze(dozeFraction)
            animations.fold(foldFraction)
            events.onTimeTick()
        }
    }

    override fun dump(pw: PrintWriter) {}
}
