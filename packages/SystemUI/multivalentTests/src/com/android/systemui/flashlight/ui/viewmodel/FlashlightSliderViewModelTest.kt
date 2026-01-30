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

package com.android.systemui.flashlight.ui.viewmodel

import android.hardware.camera2.CameraManager.TorchCallback
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.camera.cameraManager
import com.android.systemui.flashlight.data.repository.startFlashlightRepository
import com.android.systemui.flashlight.domain.interactor.flashlightInteractor
import com.android.systemui.flashlight.shared.logger.FlashlightUiEvent
import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.verify

@SmallTest
@EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
@RunWith(AndroidJUnit4::class)
class FlashlightSliderViewModelTest : SysuiTestCase() {

    val kosmos = testKosmos()
    val underTest = kosmos.flashlightSlicerViewModelFactory.create()

    @Before
    fun setUp() {
        kosmos.startFlashlightRepository(true)
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun doNothing_initiallyNullAndThenLoadsInitialState() =
        kosmos.runTest {
            assertThat(underTest.currentFlashlightLevel).isNull()
            runCurrent()
            assertThat(underTest.currentFlashlightLevel)
                .isEqualTo(FlashlightModel.Available.Level(false, DEFAULT_LEVEL, MAX_LEVEL))
        }

    @Test
    fun setLevel_updatesStateAndLogsUiEvents() =
        kosmos.runTest {
            runCurrent()
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(DEFAULT_LEVEL)

            underTest.setFlashlightLevel(1)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel).isNotNull()
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(1)

            assertThat(uiEventLoggerFake.logs.size).isEqualTo(1)
            assertThat(uiEventLoggerFake.eventId(0))
                .isEqualTo(FlashlightUiEvent.FLASHLIGHT_SLIDER_SET_LEVEL.id)
            assertThat(uiEventLoggerFake.logs[0].position).isEqualTo(1) // value 1 logged

            underTest.setFlashlightLevel(2)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(2)

            assertThat(uiEventLoggerFake.logs.size).isEqualTo(2)
            assertThat(uiEventLoggerFake.eventId(1))
                .isEqualTo(FlashlightUiEvent.FLASHLIGHT_SLIDER_SET_LEVEL.id)
            assertThat(uiEventLoggerFake.logs[1].position).isEqualTo(2)
        }

    @Test
    fun setLevel0_stateDisablesAtDefaultLevel() =
        kosmos.runTest {
            underTest.setFlashlightLevel(0)
            runCurrent()

            val actualLevel = underTest.currentFlashlightLevel!!.level
            val enabled = underTest.currentFlashlightLevel!!.enabled

            assertThat(actualLevel).isEqualTo(DEFAULT_LEVEL)
            assertThat(enabled).isEqualTo(false)
        }

    @Test
    fun setLevelBelowZero_stateUnchanged() {
        assertThrows(IllegalArgumentException::class.java) {
            kosmos.runTest {
                runCurrent()

                val originalState = underTest.currentFlashlightLevel!!

                underTest.setFlashlightLevel(-1)
                runCurrent()
            }
        }
    }

    @Test
    fun setLevelMax_stateMax() =
        kosmos.runTest {
            runCurrent()

            underTest.setFlashlightLevel(MAX_LEVEL)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(MAX_LEVEL)
        }

    @Test
    fun setLevel_whenCameraInUse_levelRemainsUnchanged() =
        kosmos.runTest {
            val torchCallbackCaptor = ArgumentCaptor.forClass(TorchCallback::class.java)
            runCurrent()
            verify(cameraManager).registerTorchCallback(any(), torchCallbackCaptor.capture())

            underTest.setFlashlightLevel(1)
            runCurrent()
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(1)

            torchCallbackCaptor.value.onTorchModeUnavailable(DEFAULT_ID)
            runCurrent()

            underTest.setFlashlightLevel(2)
            runCurrent()
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(1)
        }

    @Test
    fun setLevelAboveMax_stateUnchanged() {
        assertThrows(IllegalArgumentException::class.java) {
            kosmos.runTest {
                runCurrent()
                val originalState = underTest.currentFlashlightLevel!!

                underTest.setFlashlightLevel(MAX_LEVEL + 1)
                runCurrent()
            }
        }
    }

    @Test
    fun updateInteractor_updatesLevel() =
        kosmos.runTest {
            flashlightInteractor.setEnabled(true)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(true)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(DEFAULT_LEVEL)
            assertThat(underTest.currentFlashlightLevel!!.max).isEqualTo(MAX_LEVEL)

            flashlightInteractor.setLevel(1)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(true)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(1)

            flashlightInteractor.setLevel(2)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(true)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(2)

            // instead it can disable the flashlight
            flashlightInteractor.setEnabled(false)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(false)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(2)

            // can set level at max
            flashlightInteractor.setLevel(MAX_LEVEL)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(true)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(MAX_LEVEL)
        }

    @Test
    fun updateInteractor_temporaryUpdatesAreForgottenAndPermanentOnesRemembered() =
        kosmos.runTest {
            flashlightInteractor.setEnabled(true)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(true)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(DEFAULT_LEVEL)
            assertThat(underTest.currentFlashlightLevel!!.max).isEqualTo(MAX_LEVEL)

            underTest.setFlashlightLevel(1)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(true)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(1)

            underTest.setFlashlightLevelTemporary(2)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(true)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(2)

            // instead it can disable the flashlight
            flashlightInteractor.setEnabled(false)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(false)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(1)

            // can set level at max
            flashlightInteractor.setEnabled(true)
            runCurrent()

            assertThat(underTest.currentFlashlightLevel!!.enabled).isEqualTo(true)
            assertThat(underTest.currentFlashlightLevel!!.level).isEqualTo(1)
        }

    @Test
    fun flashlightIsAdjustable_turnsTrueAfterInitialization() =
        kosmos.runTest {
            assertThat(underTest.isFlashlightAdjustable).isFalse()

            runCurrent()

            assertThat(underTest.isFlashlightAdjustable).isTrue()
        }

    @Test
    fun testCorrectFlashlightIconForDifferentPercentages() =
        kosmos.runTest {
            assertThat(FlashlightSliderViewModel.getIconForPercentage(0f))
                .isEqualTo(R.drawable.vd_flashlight_off)

            assertThat(FlashlightSliderViewModel.getIconForPercentage(1f))
                .isEqualTo(R.drawable.vd_flashlight_on)

            assertThat(FlashlightSliderViewModel.getIconForPercentage(99f))
                .isEqualTo(R.drawable.vd_flashlight_on)

            assertThat(FlashlightSliderViewModel.getIconForPercentage(100f))
                .isEqualTo(R.drawable.vd_flashlight_on)
        }

    private companion object {
        const val MAX_LEVEL = 45
        const val DEFAULT_LEVEL = 21
        const val DEFAULT_ID = "ID"
    }
}
