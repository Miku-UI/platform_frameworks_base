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

package com.android.systemui.qs.tiles.impl.modes.ui.mapper

import android.graphics.drawable.TestStubDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.notification.modes.TestModeBuilder
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigTestBuilder
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.qs.tiles.impl.modes.domain.model.ModesTileModel
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ModesTileMapperTest : SysuiTestCase() {
    val config =
        QSTileConfigTestBuilder.build {
            uiConfig =
                QSTileUIConfig.Resource(
                    iconRes = R.drawable.qs_dnd_icon_off,
                    labelRes = R.string.quick_settings_modes_label,
                )
        }

    val underTest =
        ModesTileMapper(
            context.orCreateTestableResources
                .apply {
                    addOverride(R.drawable.qs_dnd_icon_on, TestStubDrawable())
                    addOverride(R.drawable.qs_dnd_icon_off, TestStubDrawable())
                }
                .resources,
            context.theme,
        )

    @Test
    fun inactiveState() {
        val icon = TestStubDrawable("res123").asIcon()
        val model =
            ModesTileModel(
                isActivated = false,
                activeModes = emptyList(),
                icon = icon,
                quickMode = TestModeBuilder.MANUAL_DND,
            )

        val state = underTest.map(config, model)

        assertThat(state.activationState).isEqualTo(QSTileState.ActivationState.INACTIVE)
        assertThat(state.icon).isEqualTo(icon)
        assertThat(state.label).isEqualTo("Modes")
        assertThat(state.secondaryLabel).isEqualTo("")
    }

    @Test
    fun activeState_oneMode() {
        val icon = TestStubDrawable("res123").asIcon()
        val model =
            ModesTileModel(
                isActivated = true,
                activeModes = activeModesList("DND"),
                icon = icon,
                quickMode = TestModeBuilder.MANUAL_DND,
            )

        val state = underTest.map(config, model)

        assertThat(state.activationState).isEqualTo(QSTileState.ActivationState.ACTIVE)
        assertThat(state.icon).isEqualTo(icon)
        assertThat(state.label).isEqualTo("DND")
        assertThat(state.secondaryLabel).isEqualTo("On")
    }

    @Test
    fun activeState_multipleModes() {
        val icon = TestStubDrawable("res123").asIcon()
        val model =
            ModesTileModel(
                isActivated = true,
                activeModes = activeModesList("Mode 1", "Mode 2", "Mode 3"),
                icon = icon,
                quickMode = TestModeBuilder.MANUAL_DND,
            )

        val state = underTest.map(config, model)

        assertThat(state.activationState).isEqualTo(QSTileState.ActivationState.ACTIVE)
        assertThat(state.icon).isEqualTo(icon)
        assertThat(state.label).isEqualTo("3 Modes")
        assertThat(state.secondaryLabel).isEqualTo("On")
    }

    @Test
    fun state_modelHasIconResId_includesIconResId() {
        val icon = TestStubDrawable("res123").asIcon(resId = 123)
        val model =
            ModesTileModel(
                isActivated = false,
                activeModes = emptyList(),
                icon = icon,
                quickMode = TestModeBuilder.MANUAL_DND,
            )

        val state = underTest.map(config, model)

        assertThat(state.icon).isEqualTo(icon)
    }

    private fun activeModesList(vararg modeIdsAndNames: String): List<ModesTileModel.ActiveMode> {
        return modeIdsAndNames.map {
            // For testing purposes, we use the same value for id and name.
            ModesTileModel.ActiveMode(it, it)
        }
    }
}
