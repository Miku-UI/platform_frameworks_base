/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.qs.tiles.impl.flashlight.ui.mapper

import android.content.res.Resources
import android.content.res.Resources.Theme
import android.widget.Button
import android.widget.Switch
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.flashlight.flags.FlashlightStrength
import com.android.systemui.flashlight.shared.logger.FlashlightLogger
import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.ui.model.QSTileDataToStateMapper
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import java.text.NumberFormat
import javax.inject.Inject

/** Maps [FlashlightModel] to [QSTileState]. */
class FlashlightTileMapper
@Inject
constructor(
    @ShadeDisplayAware private val res: Resources,
    private val theme: Theme,
    private val logger: FlashlightLogger,
) : QSTileDataToStateMapper<FlashlightModel> {

    override fun map(config: QSTileConfig, data: FlashlightModel): QSTileState =
        QSTileState.build(res, theme, config.uiConfig) {
            if (FlashlightStrength.isEnabled) {
                when (data) {
                    is FlashlightModel.Available.Binary -> buildBinaryState(data)

                    is FlashlightModel.Available.Level -> buildLevelState(data)

                    is FlashlightModel.Unavailable.Temporarily.Loading,
                    FlashlightModel.Unavailable.Temporarily.CameraInUse,
                    FlashlightModel.Unavailable.Temporarily.NotFound,
                    FlashlightModel.Unavailable.Permanently.NotSupported ->
                        buildUnavailableState(data as FlashlightModel.Unavailable)
                }

                contentDescription = label
                stateDescription = secondaryLabel
            } else { // flag off, preserving as much of the old code as possible

                val iconRes =
                    if (data is FlashlightModel.Available && data.enabled) {
                        R.drawable.qs_flashlight_icon_on
                    } else {
                        R.drawable.qs_flashlight_icon_off
                    }

                icon = Icon.Loaded(res.getDrawable(iconRes, theme), null, iconRes)

                contentDescription = label

                if (data is FlashlightModel.Unavailable) {
                    activationState = QSTileState.ActivationState.UNAVAILABLE
                    secondaryLabel = // only this label was possible in old code when unavailable
                        res.getString(R.string.quick_settings_flashlight_camera_in_use)
                    supportedActions = setOf()
                    return@build
                } else if (data is FlashlightModel.Available && data.enabled) {
                    activationState = QSTileState.ActivationState.ACTIVE
                } else { // available but inactive
                    activationState = QSTileState.ActivationState.INACTIVE
                }
                stateDescription = secondaryLabel
                supportedActions = setOf(QSTileState.UserAction.CLICK)
            }
        }

    private fun QSTileState.Builder.buildBinaryState(data: FlashlightModel.Available.Binary) {
        if (data.enabled) {
            activationState = QSTileState.ActivationState.ACTIVE
            icon =
                Icon.Loaded(
                    res.getDrawable(R.drawable.qs_flashlight_icon_on, theme),
                    null,
                    R.drawable.qs_flashlight_icon_on,
                )
        } else {
            activationState = QSTileState.ActivationState.INACTIVE
            icon =
                Icon.Loaded(
                    res.getDrawable(R.drawable.qs_flashlight_icon_off, theme),
                    null,
                    R.drawable.qs_flashlight_icon_off,
                )
        }
        supportedActions = setOf(QSTileState.UserAction.CLICK)
        expandedAccessibilityClass = Switch::class
    }

    private fun QSTileState.Builder.buildLevelState(data: FlashlightModel.Available.Level) {
        val percentage = calculatePercentage(data)
        if (percentage == null) {
            activationState = QSTileState.ActivationState.UNAVAILABLE
            icon =
                Icon.Loaded(
                    res.getDrawable(R.drawable.qs_flashlight_icon_off, theme),
                    null,
                    R.drawable.qs_flashlight_icon_off,
                )
            supportedActions = setOf()
            expandedAccessibilityClass = Switch::class
        } else if (data.enabled) {
            activationState = QSTileState.ActivationState.ACTIVE
            icon =
                Icon.Loaded(
                    res.getDrawable(R.drawable.qs_flashlight_icon_on, theme),
                    null,
                    R.drawable.qs_flashlight_icon_on,
                )

            val percentInstance =
                res.configuration.locales.get(0)?.let { NumberFormat.getPercentInstance(it) }
                    ?: NumberFormat.getPercentInstance()

            secondaryLabel = percentInstance.format(percentage)
            supportedActions =
                setOf(QSTileState.UserAction.CLICK, QSTileState.UserAction.TOGGLE_CLICK)
            expandedAccessibilityClass = Button::class
        } else {
            activationState = QSTileState.ActivationState.INACTIVE
            icon =
                Icon.Loaded(
                    res.getDrawable(R.drawable.qs_flashlight_icon_off, theme),
                    null,
                    R.drawable.qs_flashlight_icon_off,
                )
            supportedActions =
                setOf(QSTileState.UserAction.CLICK, QSTileState.UserAction.TOGGLE_CLICK)
            expandedAccessibilityClass = Button::class
        }
    }

    private fun QSTileState.Builder.buildUnavailableState(data: FlashlightModel.Unavailable) {
        if (data is FlashlightModel.Unavailable.Permanently.NotSupported) {
            logger.w("FlashlightMapper should not have received permanently unavailable state.")
        } else if (data is FlashlightModel.Unavailable.Temporarily.CameraInUse) {
            secondaryLabel = res.getString(R.string.quick_settings_flashlight_camera_in_use)
        }

        activationState = QSTileState.ActivationState.UNAVAILABLE
        icon =
            Icon.Loaded(
                res.getDrawable(R.drawable.qs_flashlight_icon_off, theme),
                null,
                R.drawable.qs_flashlight_icon_off,
            )
        supportedActions = setOf()
        expandedAccessibilityClass = Switch::class
    }

    private fun calculatePercentage(data: FlashlightModel.Available.Level): Float? {
        if (data.level < BASE_LEVEL || data.level > data.max) {
            logger.wtf(
                "FlashlightMapper: invalid Level data. level:${data.level}, max:${data.max}."
            )
            return null
        }

        return data.level.toFloat() / data.max
    }

    private companion object {
        const val BASE_LEVEL = 1
    }
}
