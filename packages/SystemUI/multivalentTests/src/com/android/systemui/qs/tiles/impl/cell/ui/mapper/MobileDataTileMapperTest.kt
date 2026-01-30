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

import android.os.Handler
import android.widget.Switch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.graph.SignalDrawable
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.base.ui.model.QSTileStateSubject
import com.android.systemui.qs.tiles.impl.cell.domain.model.MobileDataTileIcon
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
                    iconRes = com.android.settingslib.R.drawable.ic_mobile_4_4_bar,
                    labelRes = R.string.quick_settings_cellular_detail_title,
                )
        }
    private var underTest: MobileDataTileMapper =
        MobileDataTileMapper(context.resources, context.theme, context, Handler(context.mainLooper))

    @Test
    fun map_noActiveSim_isUnavailable() {
        val iconRes = com.android.settingslib.R.drawable.ic_mobile_4_4_bar
        val model =
            MobileDataTileModel(
                isSimActive = false,
                isEnabled = false,
                icon = MobileDataTileIcon.ResourceIcon(Icon.Resource(iconRes, null)),
            )

        val outputState = underTest.map(config, model)

        val expectedState =
            createMobileDataTileState(
                QSTileState.ActivationState.UNAVAILABLE,
                Icon.Loaded(context.getDrawable(iconRes)!!, null, iconRes),
            )
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)
    }

    @Test
    fun map_activeSim_dataDisabled_isInactive() {
        val iconRes = R.drawable.ic_signal_mobile_data_off
        val model =
            MobileDataTileModel(
                isSimActive = true,
                isEnabled = false,
                icon = MobileDataTileIcon.ResourceIcon(Icon.Resource(iconRes, null)),
            )

        val outputState = underTest.map(config, model)

        val expectedState =
            createMobileDataTileState(
                QSTileState.ActivationState.INACTIVE,
                Icon.Loaded(context.getDrawable(iconRes)!!, null, iconRes),
            )
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)
    }

    @Test
    fun map_activeSim_dataEnabled_isActive() {
        val model =
            MobileDataTileModel(
                isSimActive = true,
                isEnabled = true,
                icon = MobileDataTileIcon.SignalIcon(level = 4),
            )

        val outputState = underTest.map(config, model)

        // SignalDrawable is created inside the mapper, so we can't create an identical one for the
        // expected state. We copy the icon from the output and verify the rest of the state.
        val expectedState =
            createMobileDataTileState(QSTileState.ActivationState.ACTIVE, outputState.icon)
        QSTileStateSubject.assertThat(outputState).isEqualTo(expectedState)

        // Then, we can verify the icon specifics separately.
        val icon = outputState.icon as Icon.Loaded
        val drawable = icon.drawable
        assertThat(drawable).isInstanceOf(SignalDrawable::class.java)
        assertThat((drawable as SignalDrawable).level).isEqualTo(4)
    }

    private fun createMobileDataTileState(
        activationState: QSTileState.ActivationState,
        icon: Icon?,
    ): QSTileState {
        val label = context.getString(R.string.quick_settings_cellular_detail_title)
        val enabledState = QSTileState.EnabledState.ENABLED
        val supportedActions =
            setOf(QSTileState.UserAction.CLICK, QSTileState.UserAction.LONG_CLICK)

        return QSTileState(
            icon = icon,
            label = label,
            activationState = activationState,
            secondaryLabel = null,
            supportedActions = supportedActions,
            contentDescription = label,
            stateDescription = null,
            sideViewIcon = QSTileState.SideViewIcon.None,
            enabledState = enabledState,
            Switch::class.qualifiedName,
        )
    }
}
