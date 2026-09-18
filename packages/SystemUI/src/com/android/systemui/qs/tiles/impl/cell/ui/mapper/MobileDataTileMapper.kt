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

package com.android.systemui.qs.tiles.impl.cell.ui.mapper

import android.content.res.Resources
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.ui.model.QSTileDataToStateMapper
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

class MobileDataTileMapper
@Inject
constructor(
    @ShadeDisplayAware private val resources: Resources,
    private val theme: Resources.Theme,
) : QSTileDataToStateMapper<MobileDataTileModel> {

    override fun map(config: QSTileConfig, data: MobileDataTileModel): QSTileState =
        QSTileState.build(resources, theme, config.uiConfig) {
            val label = resources.getString(R.string.quick_settings_cellular_detail_title)
            contentDescription = label

            val iconResId: Int
            if (data.isAirplaneModeEnabled) {
                activationState = QSTileState.ActivationState.INACTIVE
                secondaryLabel = resources.getString(R.string.status_bar_airplane)
                iconResId = R.drawable.ic_cell_off
            } else if (data.isSimActive) {
                if (data.isEnabled) {
                    activationState = QSTileState.ActivationState.ACTIVE
                    secondaryLabel = data.secondaryLabel
                    iconResId = R.drawable.ic_cell_on
                } else {
                    activationState = QSTileState.ActivationState.INACTIVE
                    secondaryLabel = null
                    iconResId = R.drawable.ic_cell_off
                }
            } else {
                activationState = QSTileState.ActivationState.UNAVAILABLE
                secondaryLabel = null
                iconResId = R.drawable.ic_cell_off
            }

            icon =
                Icon.Loaded(
                    resources.getDrawable(iconResId, theme),
                    contentDescription = null,
                    iconResId,
                )
            supportedActions = buildSet {
                add(QSTileState.UserAction.CLICK)
                add(QSTileState.UserAction.LONG_CLICK)
                if (data.isSimActive && !data.isAirplaneModeEnabled) {
                    add(QSTileState.UserAction.TOGGLE_CLICK)
                }
            }
        }
}
