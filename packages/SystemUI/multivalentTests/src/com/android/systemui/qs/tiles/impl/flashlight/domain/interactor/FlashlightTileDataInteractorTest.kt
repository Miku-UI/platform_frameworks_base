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

package com.android.systemui.qs.tiles.impl.flashlight.domain.interactor

import android.content.packageManager
import android.content.pm.PackageManager
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.flashlight.data.repository.startFlashlightRepository
import com.android.systemui.flashlight.domain.interactor.flashlightInteractor
import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.qs.tiles.base.domain.model.DataUpdateTrigger
import com.android.systemui.statusbar.policy.fakeFlashlightController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class FlashlightTileDataInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val controller = kosmos.fakeFlashlightController
    private val underTest = kosmos.flashlightTileDataInteractor

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOff_availabilityOnMatchesController() = runTest {
        controller.hasFlashlight = true

        runCurrent()
        val availability by collectLastValue(underTest.availability(TEST_USER))

        assertThat(availability).isTrue()
    }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOn_availabilityOnMatchesInteractor() =
        kosmos.runTest {
            startFlashlightRepository(true)

            runCurrent()
            val availability by collectLastValue(underTest.availability(TEST_USER))

            assertThat(availability).isTrue()
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOn_availabilityOnMatchesInteractorDeviceSupportsFlashlight() =
        kosmos.runTest {
            startFlashlightRepository(true)

            runCurrent()
            val availability by collectLastValue(underTest.availability(TEST_USER))

            assertThat(availability).isEqualTo(flashlightInteractor.deviceSupportsFlashlight)
        }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOff_availabilityOffMatchesController() = runTest {
        controller.hasFlashlight = false

        runCurrent()
        val availability by collectLastValue(underTest.availability(TEST_USER))

        assertThat(availability).isFalse()
    }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOn_availabilityOffMatchesInteractor() =
        kosmos.runTest {
            startFlashlightRepository(false)

            runCurrent()
            verify(packageManager, times(1))
                .hasSystemFeature(eq(PackageManager.FEATURE_CAMERA_FLASH))

            val flowValues: List<FlashlightModel> by
                collectValues(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            flowValues.forEach {
                Log.d(
                    "FLASHLIGHT_DATA",
                    "withFlagOn_availabilityOffMatchesInteractorDeviceSupportsFlashlight: $it",
                )
            }

            val availability by collectLastValue(underTest.availability(TEST_USER))
            runCurrent()

            assertThat(availability).isNotNull()
            assertThat(availability).isFalse()
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOn_availabilityOffMatchesInteractorDeviceSupportsFlashlight() =
        kosmos.runTest {
            startFlashlightRepository(false)

            runCurrent()
            val availability by collectLastValue(underTest.availability(TEST_USER))

            assertThat(availability).isEqualTo(flashlightInteractor.deviceSupportsFlashlight)
        }

    @DisableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOff_isEnabledDataMatchesControllerWhenAvailable() = runTest {
        val flowValues: List<FlashlightModel> by
            collectValues(underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest)))

        runCurrent()
        controller.setFlashlight(true)
        runCurrent()
        controller.setFlashlight(false)
        runCurrent()

        assertThat(flowValues.size).isEqualTo(4) // 2 from setup(), 2 from this test
        assertThat(flowValues.filterIsInstance<FlashlightModel.Available>().map { it.enabled })
            .containsExactly(false, false, true, false)
            .inOrder()
    }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOn_isEnabledDataMatchesInteractorWhenAvailable() =
        kosmos.runTest {
            val flowValues: List<FlashlightModel> by
                collectValues(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )

            startFlashlightRepository(true)

            runCurrent()
            flashlightInteractor.setEnabled(true)
            runCurrent()
            flashlightInteractor.setEnabled(false)
            runCurrent()

            assertThat(flowValues.size).isEqualTo(4) // 2 from setup(), 2 from this test
            assertThat(flowValues.filterIsInstance<FlashlightModel.Available>().map { it.enabled })
                .containsExactly(false, true, false)
                .inOrder()
        }

    @EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
    @Test
    fun withFlagOn_dataMatchesInteractorLevel() =
        kosmos.runTest {
            val flowValues: List<FlashlightModel> by
                collectValues(
                    underTest.tileData(TEST_USER, flowOf(DataUpdateTrigger.InitialRequest))
                )
            startFlashlightRepository(true)

            runCurrent()
            flashlightInteractor.setLevel(1)
            runCurrent()
            flashlightInteractor.setLevel(2)
            runCurrent()

            flowValues.forEach {
                Log.d("FlashlightDataInteractorTest", "dataMatchesInteractorLevel: $it")
            }

            assertThat(flowValues.size).isEqualTo(4) // loading, 0, 1, 2
            assertThat(flowValues.map { it is FlashlightModel.Available.Level })
                .containsExactly(false, true, true, true)
                .inOrder()
            assertThat(
                    flowValues.filterIsInstance<FlashlightModel.Available.Level>().map { it.level }
                )
                .containsExactly(DEFAULT_LEVEL, 1, 2)
                .inOrder()
        }

    private companion object {
        val TEST_USER = UserHandle.of(1)!!
        const val DEFAULT_LEVEL = 21
    }
}
