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

package com.android.systemui.qs.tiles.impl.modes.ui.mapper

import android.content.res.Resources
import android.icu.text.MessageFormat
import android.widget.Button
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.ui.model.QSTileDataToStateMapper
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesTileModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import java.util.Locale
import javax.inject.Inject

class ModesTileMapper
@Inject
constructor(@ShadeDisplayAware private val resources: Resources, val theme: Resources.Theme) :
    QSTileDataToStateMapper<ModesTileModel> {
    override fun map(config: QSTileConfig, data: ModesTileModel): QSTileState =
        QSTileState.build(resources, theme, config.uiConfig) {
            icon = data.icon
            activationState =
                if (data.isActivated) {
                    QSTileState.ActivationState.ACTIVE
                } else {
                    QSTileState.ActivationState.INACTIVE
                }
            if (android.app.Flags.modesUiTileReactivatesLast()) {
                label = getLabel(data, resources)
                secondaryLabel = getSecondaryLabel(data, resources)
            } else {
                // label is fixed, set by QSTileState.build() from uiConfig
                secondaryLabel = legacyGetModesStatus(data, resources)
            }
            contentDescription = "$label. $secondaryLabel"
            supportedActions =
                setOf(
                    QSTileState.UserAction.CLICK,
                    QSTileState.UserAction.LONG_CLICK,
                    QSTileState.UserAction.TOGGLE_CLICK,
                )
            sideViewIcon = QSTileState.SideViewIcon.Chevron
            expandedAccessibilityClass = Button::class
        }

    private fun getLabel(data: ModesTileModel, resources: Resources): String {
        return if (data.activeModes.size >= 2)
            resources.getString(R.string.zen_modes_multiple_on_title, data.activeModes.size)
        else if (data.activeModes.size == 1) data.activeModes.first().name
        else resources.getString(R.string.quick_settings_modes_label)
    }

    private fun getSecondaryLabel(data: ModesTileModel, resources: Resources): String {
        return if (data.activeModes.size >= 2)
            resources.getString(R.string.zen_modes_multiple_on_status)
        else if (data.activeModes.size == 1) resources.getString(R.string.zen_mode_on) else ""
    }

    private fun legacyGetModesStatus(data: ModesTileModel, resources: Resources): String {
        val msgFormat =
            MessageFormat(resources.getString(R.string.zen_mode_active_modes), Locale.getDefault())
        val count = data.activeModes.count()
        val args: MutableMap<String, Any> = HashMap()
        args["count"] = count
        if (count >= 1) {
            args["mode"] = data.activeModes[0].name
        }
        return msgFormat.format(args)
    }
}
