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

package com.android.systemui.qs.tiles.impl.screenrecord.domain.ui.mapper

import android.content.res.Resources
import android.text.TextUtils
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.ui.model.QSTileDataToStateMapper
import com.android.systemui.qs.tiles.impl.screenrecord.domain.model.ScreenRecordTileModel
import com.android.systemui.res.R
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject

/** Maps [ScreenRecordTileModel] to [QSTileState]. */
class ScreenRecordTileMapper
@Inject
constructor(
    @ShadeDisplayAware private val resources: Resources,
    private val theme: Resources.Theme,
) : QSTileDataToStateMapper<ScreenRecordTileModel> {
    override fun map(config: QSTileConfig, data: ScreenRecordTileModel): QSTileState =
        QSTileState.build(resources, theme, config.uiConfig) {
            label = getLabel(data.isLargeScreenRecordingEnabled)
            supportedActions = setOf(QSTileState.UserAction.CLICK)
            val iconRes: Int
            when (val state = data.screenRecordModel) {
                is ScreenRecordModel.Recording -> {
                    activationState = QSTileState.ActivationState.ACTIVE
                    iconRes = R.drawable.qs_screen_record_icon_on
                    sideViewIcon = QSTileState.SideViewIcon.None
                    secondaryLabel = resources.getString(R.string.quick_settings_screen_record_stop)
                }
                is ScreenRecordModel.Starting -> {
                    activationState = QSTileState.ActivationState.ACTIVE
                    iconRes = R.drawable.qs_screen_record_icon_on
                    val countDown = state.countdownSeconds
                    sideViewIcon = QSTileState.SideViewIcon.None
                    secondaryLabel = String.format("%d...", countDown)
                }
                is ScreenRecordModel.DoingNothing -> {
                    activationState = QSTileState.ActivationState.INACTIVE
                    iconRes = R.drawable.qs_screen_record_icon_off
                    sideViewIcon = QSTileState.SideViewIcon.Chevron // tapping will open dialog
                    // Omit the "Start" sub-label for large screen capture
                    secondaryLabel =
                        resources
                            .getString(R.string.quick_settings_screen_record_start)
                            .takeUnless { data.isLargeScreenRecordingEnabled }
                }
            }
            icon = Icon.Loaded(resources.getDrawable(iconRes, theme), null, iconRes)

            contentDescription =
                if (TextUtils.isEmpty(secondaryLabel)) label
                else TextUtils.concat(label, ", ", secondaryLabel)
        }

    private fun getLabel(isLargeScreenRecordingEnabled: Boolean): String {
        if (isLargeScreenRecordingEnabled) {
            return resources.getString(R.string.quick_settings_screen_capture_label)
        }
        return resources.getString(R.string.quick_settings_screen_record_label)
    }
}
