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

package com.android.systemui.qs.tiles.impl.flashlight.ui.mapper

import android.content.res.Configuration
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.widget.Button
import android.widget.Switch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.kosmos.runTest
import com.android.systemui.qs.tiles.base.shared.model.QSTileState
import com.android.systemui.qs.tiles.impl.flashlight.qsFlashlightTileConfig
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class FlashlightTileMapperTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val qsTileConfig = kosmos.qsFlashlightTileConfig
    private val underTest = kosmos.flashlightTileMapper

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun mapsDisabledDataToInactiveState() {
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Available.Binary(false))

        val actualActivationState = tileState.activationState

        assertEquals(QSTileState.ActivationState.INACTIVE, actualActivationState)
    }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun mapsEnabledDataToActiveState() {
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Available.Binary(true))

        val actualActivationState = tileState.activationState
        assertEquals(QSTileState.ActivationState.ACTIVE, actualActivationState)
    }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun mapsEnabledDataToOnIconState() {
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Available.Binary(true))

        val expectedIcon =
            Icon.Loaded(
                context.getDrawable(R.drawable.qs_flashlight_icon_on)!!,
                null,
                R.drawable.qs_flashlight_icon_on,
            )
        val actualIcon = tileState.icon
        assertThat(actualIcon).isEqualTo(expectedIcon)
    }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun mapsDisabledDataToOffIconState() {
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Available.Binary(false))

        val expectedIcon =
            Icon.Loaded(
                context.getDrawable(R.drawable.qs_flashlight_icon_off)!!,
                null,
                R.drawable.qs_flashlight_icon_off,
            )
        val actualIcon = tileState.icon
        assertThat(actualIcon).isEqualTo(expectedIcon)
    }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun mapsCameraInUseUnavailableDataToOffIconState() {
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Unavailable.Temporarily.CameraInUse)

        val expectedIcon =
            Icon.Loaded(
                context.getDrawable(R.drawable.qs_flashlight_icon_off)!!,
                null,
                R.drawable.qs_flashlight_icon_off,
            )
        val actualIcon = tileState.icon
        assertThat(actualIcon).isEqualTo(expectedIcon)
    }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun mapsCameraNotFoundUnavailableDataToOffIconState() {
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Unavailable.Temporarily.NotFound)

        val expectedIcon =
            Icon.Loaded(
                context.getDrawable(R.drawable.qs_flashlight_icon_off)!!,
                null,
                R.drawable.qs_flashlight_icon_off,
            )
        val actualIcon = tileState.icon
        assertThat(actualIcon).isEqualTo(expectedIcon)
    }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun supportClickActionWhenAvailable() {
        val dontCare = true
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Available.Binary(dontCare))

        val supportedActions = tileState.supportedActions
        assertThat(supportedActions).containsExactly(QSTileState.UserAction.CLICK)
    }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun doesNotSupportClickActionWhenUnavailableCameraInUse() {
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Unavailable.Temporarily.CameraInUse)

        val supportedActions = tileState.supportedActions
        assertThat(supportedActions).isEmpty()
    }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun doesNotSupportClickActionWhenUnavailableFlashlightNotFound() {
        val tileState: QSTileState =
            underTest.map(qsTileConfig, FlashlightModel.Unavailable.Temporarily.NotFound)

        val supportedActions = tileState.supportedActions
        assertThat(supportedActions).isEmpty()
    }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun permanentlyUnavailableNotSupported() =
        kosmos.runTest {
            val actual =
                underTest.map(qsTileConfig, FlashlightModel.Unavailable.Permanently.NotSupported)

            assertThat(actual)
                .isEqualTo(createFlashlightState(QSTileState.ActivationState.UNAVAILABLE))
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun temporarilyUnavailableLoading() =
        kosmos.runTest {
            val actual =
                underTest.map(qsTileConfig, FlashlightModel.Unavailable.Temporarily.Loading)

            assertThat(actual)
                .isEqualTo(createFlashlightState(QSTileState.ActivationState.UNAVAILABLE))
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun temporarilyUnavailableNotFound() =
        kosmos.runTest {
            val actual =
                underTest.map(qsTileConfig, FlashlightModel.Unavailable.Temporarily.NotFound)

            assertThat(actual)
                .isEqualTo(createFlashlightState(QSTileState.ActivationState.UNAVAILABLE))
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun temporarilyUnavailableCameraInUse() =
        kosmos.runTest {
            val actual =
                underTest.map(qsTileConfig, FlashlightModel.Unavailable.Temporarily.CameraInUse)

            assertThat(actual)
                .isEqualTo(
                    createFlashlightState(
                        activationState = QSTileState.ActivationState.UNAVAILABLE,
                        secondaryLabel =
                            context.getString(R.string.quick_settings_flashlight_camera_in_use),
                    )
                )
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableBinaryDisabled() =
        kosmos.runTest {
            val actual = underTest.map(qsTileConfig, FlashlightModel.Available.Binary(false))

            assertThat(actual)
                .isEqualTo(createFlashlightState(QSTileState.ActivationState.INACTIVE))
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableBinaryEnabled() =
        kosmos.runTest {
            val actual = underTest.map(qsTileConfig, FlashlightModel.Available.Binary(true))

            assertThat(actual).isEqualTo(createFlashlightState(QSTileState.ActivationState.ACTIVE))
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableLevelDisabled() =
        kosmos.runTest {
            val actual =
                underTest.map(
                    qsTileConfig,
                    FlashlightModel.Available.Level(false, DEFAULT_LEVEL, MAX_LEVEL),
                )

            assertThat(actual)
                .isEqualTo(
                    createFlashlightState(
                        activationState = QSTileState.ActivationState.INACTIVE,
                        toggleable = true,
                    )
                )
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableLevelEnabled_whenDataInCorrectRange() =
        kosmos.runTest {
            context.orCreateTestableResources.overrideConfiguration(
                Configuration(context.resources.configuration).apply { setLocale(Locale.US) }
            )
            val actual =
                underTest.map(
                    qsTileConfig,
                    FlashlightModel.Available.Level(true, DEFAULT_LEVEL, MAX_LEVEL),
                )

            assertThat(actual)
                .isEqualTo(
                    createFlashlightState(
                        activationState = QSTileState.ActivationState.ACTIVE,
                        secondaryLabel = "47%",
                        toggleable = true,
                    )
                )
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableLevelEnabled_whenDataInCorrectRange_withFrenchLocale() =
        kosmos.runTest {
            context.orCreateTestableResources.overrideConfiguration(
                Configuration(context.resources.configuration).apply { setLocale(Locale.FRANCE) }
            )

            val actual =
                underTest.map(
                    qsTileConfig,
                    FlashlightModel.Available.Level(true, DEFAULT_LEVEL, MAX_LEVEL),
                )

            assertThat(actual)
                .isEqualTo(
                    createFlashlightState(
                        activationState = QSTileState.ActivationState.ACTIVE,
                        secondaryLabel = "47 %", // extra NBSP
                        toggleable = true,
                    )
                )
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableLevelEnabled_whenMaxIsLessThanCurrentLevel() =
        kosmos.runTest {
            val actual =
                underTest.map(
                    qsTileConfig,
                    FlashlightModel.Available.Level(true, MAX_LEVEL + 1, MAX_LEVEL),
                )

            assertThat(actual)
                .isEqualTo(
                    createFlashlightState(activationState = QSTileState.ActivationState.UNAVAILABLE)
                )
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableLevelEnabled_whenLevelIsLessThanBase() =
        kosmos.runTest {
            val actual =
                underTest.map(
                    qsTileConfig,
                    FlashlightModel.Available.Level(true, BASE_LEVEL - 1, MAX_LEVEL),
                )

            assertThat(actual)
                .isEqualTo(
                    createFlashlightState(activationState = QSTileState.ActivationState.UNAVAILABLE)
                )
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableLevelEnabled_whenMaxIsLessThanBase() =
        kosmos.runTest {
            val actual =
                underTest.map(
                    qsTileConfig,
                    FlashlightModel.Available.Level(true, BASE_LEVEL, BASE_LEVEL - 1),
                )

            assertThat(actual)
                .isEqualTo(
                    createFlashlightState(activationState = QSTileState.ActivationState.UNAVAILABLE)
                )
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun availableLevelEnabled_whenMaxIs0_noSecondaryLabel() =
        kosmos.runTest {
            val actual =
                underTest.map(qsTileConfig, FlashlightModel.Available.Level(true, DEFAULT_LEVEL, 0))

            assertThat(actual)
                .isEqualTo(
                    createFlashlightState(activationState = QSTileState.ActivationState.UNAVAILABLE)
                )
        }

    private fun createFlashlightState(
        activationState: QSTileState.ActivationState,
        secondaryLabel: CharSequence? = null,
        toggleable: Boolean = false,
    ): QSTileState {
        val label = context.getString(R.string.quick_settings_flashlight_label)
        val iconRes =
            if (activationState == QSTileState.ActivationState.ACTIVE)
                R.drawable.qs_flashlight_icon_on
            else R.drawable.qs_flashlight_icon_off
        return QSTileState(
            Icon.Loaded(context.getDrawable(iconRes)!!, null, iconRes),
            label,
            activationState,
            secondaryLabel,
            when (activationState) {
                QSTileState.ActivationState.UNAVAILABLE -> setOf()
                QSTileState.ActivationState.ACTIVE,
                QSTileState.ActivationState.INACTIVE ->
                    if (toggleable)
                        setOf(QSTileState.UserAction.CLICK, QSTileState.UserAction.TOGGLE_CLICK)
                    else setOf(QSTileState.UserAction.CLICK)
            },
            label,
            secondaryLabel,
            QSTileState.SideViewIcon.None,
            QSTileState.EnabledState.ENABLED,
            if (toggleable) Button::class.qualifiedName else Switch::class.qualifiedName,
        )
    }

    private companion object {
        const val BASE_LEVEL = 1
        const val DEFAULT_LEVEL = 21
        const val MAX_LEVEL = 45
    }
}
