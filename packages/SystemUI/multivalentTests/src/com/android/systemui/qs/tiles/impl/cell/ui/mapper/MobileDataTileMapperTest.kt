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

package com.android.systemui.qs.tiles.impl.cell.ui.mapper

import android.widget.Switch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.base.ui.model.QSTileStateSubject
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileModel
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MobileDataTileMapperTest : SysuiTestCase() {

    private val config =
        QSTileConfigTestBuilder.build {
            uiConfig =
                QSTileUIConfig.Resource(
                    iconRes = R.drawable.ic_cell_on,
                    labelRes = R.string.quick_settings_cellular_detail_title,
                )
        }
    private var underTest: MobileDataTileMapper =
        MobileDataTileMapper(context.resources, context.theme)

    @Test
    fun map_noActiveSim_isUnavailable() {
        val iconRes = R.drawable.ic_cell_off
        val model =
            MobileDataTileModel(
                isSimActive = false,
                isEnabled = false,
                isAirplaneModeEnabled = false,
            )

        val outputState = underTest.map(config, model)

        val expectedState =
            createMobileDataTileState(
                QSTileState.ActivationState.UNAVAILABLE,
                null,
                Icon.Loaded(context.getDrawable(iconRes)!!, null, iconRes),
                setOf(QSTileState.UserAction.CLICK, QSTileState.UserAction.LONG_CLICK),
            )
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)
    }

    @Test
    fun map_activeSim_dataDisabled_isInactive() {
        val iconRes = R.drawable.ic_cell_off
        val model =
            MobileDataTileModel(
                isSimActive = true,
                isEnabled = false,
                isAirplaneModeEnabled = false,
            )

        val outputState = underTest.map(config, model)

        val expectedState =
            createMobileDataTileState(
                QSTileState.ActivationState.INACTIVE,
                null,
                Icon.Loaded(context.getDrawable(iconRes)!!, null, iconRes),
                setOf(
                    QSTileState.UserAction.CLICK,
                    QSTileState.UserAction.LONG_CLICK,
                    QSTileState.UserAction.TOGGLE_CLICK
                ),
            )
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)
    }

    @Test
    fun map_activeSim_dataEnabled_isActive() {
        val model =
            MobileDataTileModel(
                isSimActive = true,
                isEnabled = true,
                isAirplaneModeEnabled = false,
            )

        val outputState = underTest.map(config, model)

        val expectedState =
            createMobileDataTileState(
                QSTileState.ActivationState.ACTIVE,
                null,
                outputState.icon,
                setOf(
                    QSTileState.UserAction.CLICK,
                    QSTileState.UserAction.LONG_CLICK,
                    QSTileState.UserAction.TOGGLE_CLICK
                ),
            )
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)
    }

    @Test
    fun map_activeSim_dataEnabled_withSecondaryLabel_usesSecondaryLabel() {
        val secondaryLabel = "Test Label"
        val model =
            MobileDataTileModel(
                isSimActive = true,
                isEnabled = true,
                isAirplaneModeEnabled = false,
                secondaryLabel = secondaryLabel,
            )

        val outputState = underTest.map(config, model)

        assertThat(outputState.secondaryLabel).isEqualTo(secondaryLabel)
    }

    @Test
    fun map_airplaneModeEnabled_isInactive() {
        val iconRes = R.drawable.ic_cell_off
        val model =
            MobileDataTileModel(
                isSimActive = true,
                isEnabled = true,
                isAirplaneModeEnabled = true,
            )

        val outputState = underTest.map(config, model)

        val expectedState =
            createMobileDataTileState(
                QSTileState.ActivationState.INACTIVE,
                context.getString(R.string.status_bar_airplane),
                Icon.Loaded(context.getDrawable(iconRes)!!, null, iconRes),
                setOf(QSTileState.UserAction.CLICK, QSTileState.UserAction.LONG_CLICK),
            )
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)
    }

    private fun createMobileDataTileState(
        activationState: QSTileState.ActivationState,
        secondaryLabel: CharSequence?,
        icon: Icon?,
        supportedActions: Set<QSTileState.UserAction>,
        enabledState: QSTileState.EnabledState = QSTileState.EnabledState.ENABLED,
    ): QSTileState {
        val label = context.getString(R.string.quick_settings_cellular_detail_title)

        return QSTileState(
            icon = icon,
            label = label,
            activationState = activationState,
            secondaryLabel,
            supportedActions = supportedActions,
            contentDescription = label,
            stateDescription = null,
            sideViewIcon = QSTileState.SideViewIcon.None,
            enabledState = enabledState,
            Switch::class.qualifiedName,
        )
    }
}
