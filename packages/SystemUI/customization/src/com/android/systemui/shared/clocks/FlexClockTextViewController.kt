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
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.android.systemui.customization.clocks.ClockContext
import com.android.systemui.customization.clocks.ClockLogger
import com.android.systemui.customization.clocks.DigitalTimeFormatter
import com.android.systemui.customization.clocks.DigitalTimespec
import com.android.systemui.customization.clocks.DigitalTimespecHandler
import com.android.systemui.customization.clocks.FontTextStyle
import com.android.systemui.customization.clocks.view.DigitalAlignment
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
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import com.android.systemui.plugins.keyguard.ui.clocks.TimeFormatKind
import com.android.systemui.shared.clocks.view.FlexClockTextView
import java.util.Locale

data class LayerConfig(
    val style: FontTextStyle,
    val aodStyle: FontTextStyle,
    val alignment: DigitalAlignment,
    val timespec: DigitalTimespec,
    val timeFormatter: DigitalTimeFormatter?,
)

open class FlexClockTextViewController(
    private val clockCtx: ClockContext,
    private val layerCfg: LayerConfig,
    isLargeClock: Boolean,
) : FlexClockViewController {
    override val view = FlexClockTextView(clockCtx, isLargeClock)
    private val logger = ClockLogger(null, clockCtx.messageBuffer, TAG)
    private val timespec = DigitalTimespecHandler(layerCfg.timespec, layerCfg.timeFormatter!!)
    override var onViewBoundsChanged by view::onViewBoundsChanged
    override var onViewMaxSizeChanged by view::onViewMaxSizeChanged

    override val config = ClockFaceConfig()
    var dozeState: DefaultClockController.AnimationState? = null

    init {
        view.layoutParams =
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        layerCfg.alignment.verticalAlignment?.let { view.verticalAlignment = it }
        layerCfg.alignment.horizontalAlignment?.let { view.horizontalAlignment = it }
        view.applyStyles(layerCfg.style, layerCfg.aodStyle)
        view.id = timespec.getViewId()
    }

    fun refreshTime() {
        val text = timespec.getText()
        if (view.text != text) {
            view.text = text
            view.refreshTime()
            logger.d({ "refreshTime: new text=$str1" }) { str1 = text }
        }
    }

    private fun applyLayout() {
        // TODO: Remove NO-OP
        if (view.layoutParams is RelativeLayout.LayoutParams) {
            val lp = view.layoutParams as RelativeLayout.LayoutParams
            lp.addRule(RelativeLayout.TEXT_ALIGNMENT_CENTER)
            when (view.id) {
                ClockViewIds.HOUR_DIGIT_PAIR -> {
                    lp.addRule(RelativeLayout.CENTER_VERTICAL)
                    lp.addRule(RelativeLayout.ALIGN_PARENT_START)
                }
                ClockViewIds.MINUTE_DIGIT_PAIR -> {
                    lp.addRule(RelativeLayout.CENTER_VERTICAL)
                    lp.addRule(RelativeLayout.END_OF, ClockViewIds.HOUR_DIGIT_PAIR)
                }
                else -> {
                    throw Exception("cannot apply two pairs layout to view ${view.id}")
                }
            }
            view.layoutParams = lp
        }
    }

    override val events =
        object : ClockEvents {
            override var isReactiveTouchInteractionEnabled = false

            override fun onLocaleChanged(locale: Locale) {
                timespec.formatter.locale = locale
                refreshTime()
            }

            override fun onTimeFormatChanged(formatKind: TimeFormatKind) {
                timespec.formatter.formatKind = formatKind
                refreshTime()
            }

            override fun onTimeZoneChanged(timeZone: TimeZone) {
                timespec.formatter.timeKeeper.timeZone = timeZone
                refreshTime()
            }

            override fun onWeatherDataChanged(data: WeatherData) {}

            override fun onAlarmDataChanged(data: AlarmData) {}

            override fun onZenDataChanged(data: ZenData) {}
        }

    override val animations =
        object : ClockAnimations {
            override fun enter() {
                applyLayout()
                refreshTime()
            }

            override fun doze(fraction: Float) {
                if (dozeState == null) {
                    dozeState = DefaultClockController.AnimationState(fraction)
                    view.animateDoze(dozeState!!.isActive, false)
                } else {
                    val (hasChanged, hasJumped) = dozeState!!.update(fraction)
                    if (hasChanged) view.animateDoze(dozeState!!.isActive, !hasJumped)
                }
                view.dozeFraction = fraction
            }

            private var hasFontAxes = false

            override fun onFontAxesChanged(style: ClockAxisStyle) {
                view.updateAxes(style, isAnimated = hasFontAxes)
                hasFontAxes = true
            }

            override fun fold(fraction: Float) {
                applyLayout()
                refreshTime()
            }

            override fun charge() {
                view.animateCharge()
            }

            override fun onPickerCarouselSwiping(swipingFraction: Float) {}

            override fun onPositionAnimated(args: ClockPositionAnimationArgs) {}

            override fun onFidgetTap(x: Float, y: Float) {
                view.animateFidget(VPointF(x, y), enforceBounds = true)
            }
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

            override fun onFontSettingChanged(fontSizePx: Float) {
                view.applyTextSize(fontSizePx)
            }

            override fun onThemeChanged(theme: ThemeConfig) {
                view.updateColor(
                    lockscreenColor = theme.getDefaultColor(clockCtx.context),
                    aodColor = theme.getAodColor(clockCtx.context),
                )
                refreshTime()
            }

            override fun onTargetRegionChanged(targetRegion: Rect?) {}

            override fun onSecondaryDisplayChanged(onSecondaryDisplay: Boolean) {}
        }

    companion object {
        private val TAG = FlexClockTextViewController::class.simpleName!!
    }
}
