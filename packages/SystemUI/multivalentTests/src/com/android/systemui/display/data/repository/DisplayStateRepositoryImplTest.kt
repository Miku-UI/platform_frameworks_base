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

package com.android.systemui.display.data.repository

import android.content.res.Configuration
import android.content.testableContext
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.util.DisplayMetrics
import android.util.Size
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.DisplayInfo
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.display.data.repository.DeviceStateRepository.DeviceState
import com.android.systemui.display.shared.model.DisplayRotation
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplayStateRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val display = mock<Display>()
    private val configuration = Configuration()
    private val context = kosmos.testableContext

    private val underTest by lazy { kosmos.realDisplayStateRepository }

    @Before
    fun setUp() {
        context.display = display
        context.orCreateTestableResources.apply {
            addOverride(com.android.internal.R.bool.config_reverseDefaultRotation, false)
            overrideConfiguration(configuration)
        }

        // Set densityDpi such that pixels and DP are the same; Makes it easier to read and write
        // tests.
        configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT
    }

    @Test
    fun updatesIsInRearDisplayMode_whenRearDisplayStateChanges() =
        kosmos.runTest {
            val isInRearDisplayMode by collectLastValue(underTest.isInRearDisplayMode)

            fakeDeviceStateRepository.emit(DeviceState.FOLDED)
            assertThat(isInRearDisplayMode).isFalse()

            fakeDeviceStateRepository.emit(DeviceState.REAR_DISPLAY)
            assertThat(isInRearDisplayMode).isTrue()
        }

    @Test
    fun updatesCurrentRotation_whenDisplayStateChanges() =
        kosmos.runTest {
            val currentRotation by collectLastValue(underTest.currentRotation)

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_90
                return@then true
            }

            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentRotation).isEqualTo(DisplayRotation.ROTATION_90)

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_180
                return@then true
            }

            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentRotation).isEqualTo(DisplayRotation.ROTATION_180)
        }

    @Test
    fun updatesCurrentSize_whenDisplayStateChanges() =
        kosmos.runTest {
            val currentSize by collectLastValue(underTest.currentDisplaySize)

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_0
                info.logicalWidth = 100
                info.logicalHeight = 200
                return@then true
            }
            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentSize).isEqualTo(Size(100, 200))

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_90
                info.logicalWidth = 100
                info.logicalHeight = 200
                return@then true
            }
            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentSize).isEqualTo(Size(200, 100))
        }

    @Test
    @DisableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun updatesIsLargeScreen_whenDisplayStateChanges() =
        kosmos.runTest {
            val isLargeScreen by collectLastValue(underTest.isLargeScreen)

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_0
                info.logicalWidth = 100
                info.logicalHeight = 700
                return@then true
            }
            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(isLargeScreen).isFalse()

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_0
                info.logicalWidth = 800
                info.logicalHeight = 700
                return@then true
            }
            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(isLargeScreen).isTrue()
        }

    @Test
    @DisableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun updatesIsWideScreen_whenDisplayStateChanges() =
        kosmos.runTest {
            val isWideScreen by collectLastValue(underTest.isWideScreen)

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_0
                info.logicalWidth = 200
                info.logicalHeight = 700
                return@then true
            }
            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(isWideScreen).isFalse()

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_90
                info.logicalWidth = 700
                info.logicalHeight = 200
                return@then true
            }
            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(isWideScreen).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun currentRotation_anotherDisplaychanged_noChange() =
        kosmos.runTest {
            val currentRotation by collectLastValue(underTest.currentRotation)

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_90
                return@then true
            }

            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY)
            assertThat(currentRotation).isEqualTo(DisplayRotation.ROTATION_90)

            whenever(display.getDisplayInfo(any())).then {
                val info = it.getArgument<DisplayInfo>(0)
                info.rotation = Surface.ROTATION_180
                return@then true
            }

            displayRepository.emitDisplayChangeEvent(DEFAULT_DISPLAY + 1)
            // Still the previous one!
            assertThat(currentRotation).isEqualTo(DisplayRotation.ROTATION_90)
        }

    @Test
    @EnableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun isWideScreen_fromConfiguration() =
        kosmos.runTest {
            val isWideScreen by collectLastValue(underTest.isWideScreen)

            val smallScreenConfig = Configuration().apply { screenWidthDp = SMALL_SCREEN_WIDTH_DP }
            kosmos.fakeConfigurationRepository.onConfigurationChange(smallScreenConfig)

            assertThat(isWideScreen).isFalse()

            val wideScreenConfig = Configuration().apply { screenWidthDp = LARGE_SCREEN_WIDTH_DP }
            kosmos.fakeConfigurationRepository.onConfigurationChange(wideScreenConfig)

            assertThat(isWideScreen).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun isLargeScreen_fromConfiguration() =
        kosmos.runTest {
            val isLargeScreen by collectLastValue(underTest.isLargeScreen)

            val smallScreenConfig =
                Configuration().apply { smallestScreenWidthDp = SMALL_SCREEN_WIDTH_DP }
            kosmos.fakeConfigurationRepository.onConfigurationChange(smallScreenConfig)

            assertThat(isLargeScreen).isFalse()

            val wideScreenConfig =
                Configuration().apply { smallestScreenWidthDp = LARGE_SCREEN_WIDTH_DP }
            kosmos.fakeConfigurationRepository.onConfigurationChange(wideScreenConfig)

            assertThat(isLargeScreen).isTrue()
        }

    private companion object {
        const val SMALL_SCREEN_WIDTH_DP = 1
        const val LARGE_SCREEN_WIDTH_DP = 1000000
    }
}
