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

import android.content.res.Resources
import android.icu.util.TimeZone
import com.android.systemui.animation.GSFAxes
import com.android.systemui.customization.R
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.utils.FontUtils.put
import com.android.systemui.customization.clocks.utils.FontUtils.set
import com.android.systemui.customization.clocks.utils.FontUtils.toClockAxis
import com.android.systemui.plugins.keyguard.data.model.AlarmData
import com.android.systemui.plugins.keyguard.data.model.WeatherData
import com.android.systemui.plugins.keyguard.data.model.ZenData
import com.android.systemui.plugins.keyguard.ui.clocks.AxisPresetConfig
import com.android.systemui.plugins.keyguard.ui.clocks.AxisType
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEventListeners
import com.android.systemui.plugins.keyguard.ui.clocks.ClockEvents
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFontAxis
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFontAxis.Companion.merge
import com.android.systemui.plugins.keyguard.ui.clocks.ClockMessageBuffers
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import com.android.systemui.shared.clocks.view.FlexClockViewGroup
import java.io.PrintWriter
import java.util.Locale

/** Controller for the default flex clock */
class FlexClockController(
    private val clockCtx: ClockContext,
    private val messageBuffers: ClockMessageBuffers,
) : ClockController {
    override val smallClock =
        FlexClockFaceController(
            clockCtx.copy(messageBuffer = messageBuffers.smallClockMessageBuffer),
            isLargeClock = false,
        )

    override val largeClock =
        FlexClockFaceController(
            clockCtx.copy(messageBuffer = messageBuffers.largeClockMessageBuffer),
            isLargeClock = true,
        )

    override val config: ClockConfig by lazy {
        ClockConfig(
            DEFAULT_CLOCK_ID,
            clockCtx.resources.getString(R.string.clock_default_name),
            clockCtx.resources.getString(R.string.clock_default_description),
        )
    }

    override val events =
        object : ClockEvents {
            override var isReactiveTouchInteractionEnabled = false
                set(value) {
                    field = value
                    val view = largeClock.view as FlexClockViewGroup
                    view.isReactiveTouchInteractionEnabled = value
                }

            override fun onTimeZoneChanged(timeZone: TimeZone) {
                smallClock.events.onTimeZoneChanged(timeZone)
                largeClock.events.onTimeZoneChanged(timeZone)
            }

            override fun onTimeFormatChanged(formatKind: TimeFormatKind) {
                smallClock.events.onTimeFormatChanged(formatKind)
                largeClock.events.onTimeFormatChanged(formatKind)
            }

            override fun onLocaleChanged(locale: Locale) {
                smallClock.events.onLocaleChanged(locale)
                largeClock.events.onLocaleChanged(locale)
            }

            override fun onWeatherDataChanged(data: WeatherData) {
                smallClock.events.onWeatherDataChanged(data)
                largeClock.events.onWeatherDataChanged(data)
            }

            override fun onAlarmDataChanged(data: AlarmData) {
                smallClock.events.onAlarmDataChanged(data)
                largeClock.events.onAlarmDataChanged(data)
            }

            override fun onZenDataChanged(data: ZenData) {
                smallClock.events.onZenDataChanged(data)
                largeClock.events.onZenDataChanged(data)
            }
        }

    override val eventListeners = ClockEventListeners()

    override fun initialize(isDarkTheme: Boolean, dozeFraction: Float, foldFraction: Float) {
        smallClock.run {
            layerController.onViewBoundsChanged = {
                eventListeners.fire { onBoundsChanged(it, isLargeClock = false) }
            }
            layerController.onViewMaxSizeChanged = {
                eventListeners.fire { onMaxSizeChanged(it, isLargeClock = false) }
            }
            events.onThemeChanged(theme.copy(isDarkTheme = isDarkTheme))
            animations.onFontAxesChanged(clockCtx.settings.axes)
            animations.doze(dozeFraction)
            animations.fold(foldFraction)
            events.onTimeTick()
        }

        largeClock.run {
            layerController.onViewBoundsChanged = {
                eventListeners.fire { onBoundsChanged(it, isLargeClock = true) }
            }
            layerController.onViewMaxSizeChanged = {
                eventListeners.fire { onMaxSizeChanged(it, isLargeClock = true) }
            }
            events.onThemeChanged(theme.copy(isDarkTheme = isDarkTheme))
            animations.onFontAxesChanged(clockCtx.settings.axes)
            animations.doze(dozeFraction)
            animations.fold(foldFraction)
            events.onTimeTick()
        }
    }

    override fun dump(pw: PrintWriter) {}

    companion object {
        fun getDefaultAxes(settings: ClockSettings): List<ClockFontAxis> {
            return if (settings.clockId == FLEX_CLOCK_ID) {
                FONT_AXES.merge(LEGACY_FLEX_SETTINGS)
            } else FONT_AXES
        }

        private val FONT_AXES =
            listOf(
                GSFAxes.WEIGHT.toClockAxis(
                    type = AxisType.Float,
                    currentValue = 500f,
                    name = "Weight",
                    description = "Glyph Weight",
                ),
                GSFAxes.WIDTH.toClockAxis(
                    type = AxisType.Float,
                    currentValue = 100f,
                    name = "Width",
                    description = "Glyph Width",
                ),
                GSFAxes.ROUND.toClockAxis(
                    type = AxisType.Boolean,
                    currentValue = 100f,
                    name = "Round",
                    description = "Glyph Roundness",
                ),
                GSFAxes.SLANT.toClockAxis(
                    type = AxisType.Boolean,
                    currentValue = 0f,
                    name = "Slant",
                    description = "Glyph Slant",
                ),
            )

        private val LEGACY_FLEX_SETTINGS = ClockAxisStyle {
            put(GSFAxes.WEIGHT, 600f)
            put(GSFAxes.WIDTH, 100f)
            put(GSFAxes.ROUND, 100f)
            put(GSFAxes.SLANT, 0f)
        }

        val BASE_PRESETS: List<ClockAxisStyle> =
            listOf(
                    ClockAxisStyle {
                        put(GSFAxes.WEIGHT, 800f)
                        put(GSFAxes.WIDTH, 30f)
                    },
                    ClockAxisStyle {
                        put(GSFAxes.WEIGHT, 700f)
                        put(GSFAxes.WIDTH, 55f)
                    },
                    ClockAxisStyle {
                        put(GSFAxes.WEIGHT, 600f)
                        put(GSFAxes.WIDTH, 80f)
                    },
                    ClockAxisStyle {
                        put(GSFAxes.WEIGHT, 500f)
                        put(GSFAxes.WIDTH, 100f)
                    },
                    ClockAxisStyle {
                        put(GSFAxes.WEIGHT, 400f)
                        put(GSFAxes.WIDTH, 108f)
                    },
                    ClockAxisStyle {
                        put(GSFAxes.WEIGHT, 300f)
                        put(GSFAxes.WIDTH, 116f)
                    },
                    ClockAxisStyle {
                        put(GSFAxes.WEIGHT, 200f)
                        put(GSFAxes.WIDTH, 120f)
                    },
                )
                .map {
                    it.put(GSFAxes.SLANT, 0f)
                    it.put(GSFAxes.ROUND, 0f)
                    it
                }

        fun buildPresetGroup(resources: Resources, isRound: Boolean): AxisPresetConfig.Group {
            val round = if (isRound) GSFAxes.ROUND.maxValue else GSFAxes.ROUND.minValue
            return AxisPresetConfig.Group(
                presets = BASE_PRESETS.map { it.copy { put(GSFAxes.ROUND, round) } },
                // TODO(b/395647577): Placeholder Icon; Replace or remove
                icon = resources.getDrawable(R.drawable.clock_default_thumbnail, null),
            )
        }
    }
}
