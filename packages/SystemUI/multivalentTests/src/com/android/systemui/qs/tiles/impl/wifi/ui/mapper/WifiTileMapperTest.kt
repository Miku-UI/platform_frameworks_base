/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs.tiles.impl.wifi.ui.mapper

import android.widget.Switch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.base.ui.model.QSTileStateSubject
import com.android.systemui.qs.tiles.impl.wifi.domain.model.WifiTileModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.connectivity.WifiIcons
import com.android.systemui.statusbar.pipeline.wifi.ui.model.WifiTileIconModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class WifiTileMapperTest : SysuiTestCase() {

    private val config =
        QSTileConfigTestBuilder.build {
            uiConfig =
                QSTileUIConfig.Resource(
                    iconRes = WifiIcons.WIFI_NO_SIGNAL,
                    labelRes = R.string.quick_settings_internet_label,
                )
        }
    private var underTest: WifiTileMapper =
        WifiTileMapper(context.resources, context.theme, context)

    @Test
    fun map_inactive_isInactive() {
        val model =
            WifiTileModel.Inactive(
                icon = WifiTileIconModel(WifiIcons.WIFI_NO_SIGNAL),
                secondaryLabel = null,
            )

        val outputState = underTest.map(config, model)

        val expectedState =
            createWifiTileState(
                QSTileState.ActivationState.INACTIVE,
                Icon.Loaded(
                    context.getDrawable(WifiIcons.WIFI_NO_SIGNAL)!!,
                    null,
                    WifiIcons.WIFI_NO_SIGNAL,
                ),
                secondaryLabel = null,
            )
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)
    }

    @Test
    fun map_active_isActive() {
        val iconRes = WifiIcons.WIFI_FULL_ICONS[4]
        val model =
            WifiTileModel.Active(icon = WifiTileIconModel(iconRes), secondaryLabel = "Test SSID")

        val outputState = underTest.map(config, model)

        val expectedState =
            createWifiTileState(
                QSTileState.ActivationState.ACTIVE,
                Icon.Loaded(context.getDrawable(iconRes)!!, null, iconRes),
                secondaryLabel = "Test SSID",
            )
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)
    }

    @Test
    fun map_activeWithSecondaryLabel_showsSecondaryLabel() {
        val secondaryLabel = "My Awesome Wifi"
        val model =
            WifiTileModel.Active(
                icon = WifiTileIconModel(WifiIcons.WIFI_FULL_ICONS[4]),
                secondaryLabel = secondaryLabel,
            )

        val outputState = underTest.map(config, model)

        assertThat(outputState.secondaryLabel).isEqualTo(secondaryLabel)
        assertThat(outputState.contentDescription)
            .isEqualTo(
                "${context.getString(R.string.quick_settings_internet_label)},$secondaryLabel"
            )
        assertThat(outputState.stateDescription).isEqualTo(secondaryLabel)
    }

    private fun createWifiTileState(
        activationState: QSTileState.ActivationState,
        icon: Icon?,
        secondaryLabel: CharSequence?,
    ): QSTileState {
        val label = context.getString(R.string.quick_settings_internet_label)
        val supportedActions =
            setOf(
                QSTileState.UserAction.CLICK,
                QSTileState.UserAction.LONG_CLICK,
                QSTileState.UserAction.TOGGLE_CLICK,
            )

        return QSTileState(
            icon = icon,
            label = label,
            activationState = activationState,
            secondaryLabel = secondaryLabel,
            supportedActions = supportedActions,
            contentDescription = "$label,$secondaryLabel",
            stateDescription = secondaryLabel,
            sideViewIcon = QSTileState.SideViewIcon.None,
            enabledState = QSTileState.EnabledState.ENABLED,
            expandedAccessibilityClassName = Switch::class.qualifiedName,
        )
    }
}
