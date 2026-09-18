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

package com.android.server.display

import android.util.SparseArray
import android.view.Display
import android.view.SurfaceControl
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

private const val NO_FLAGS = 0
private const val ANISOTROPY_FLAGS =
    Display.Mode.FLAG_ANISOTROPY_CORRECTION or Display.Mode.FLAG_SIZE_OVERRIDE

@SmallTest
@RunWith(TestParameterInjector::class)
class DisplayModeFactoryTest {

    @Test
    fun testCreateMode_resolutionAndRefreshRate() {
        val width = 150
        val height = 250
        val refreshRate = 60f
        val result = DisplayModeFactory.createMode(width, height, refreshRate)

        assertModesEqual(result, createDisplayMode(width = width, height = height,
            peakRefreshRate = refreshRate, vsyncRate = refreshRate))
    }

    @Test
    fun testCreateMode_fromSurfaceControlMode(@TestParameter testCase: SFModeTestCase) {
        val displayMode = createSfDisplayMode(peakRefreshRate = testCase.inputRefreshRate,
            vsyncRate = 240f)
        val alternativeRefreshRates = floatArrayOf(10f, 20f)
        val expectedMode = createDisplayMode(flags = testCase.expectedFlags,
            width = displayMode.width, height = displayMode.height,
            peakRefreshRate = displayMode.peakRefreshRate, vsyncRate = displayMode.vsyncRate,
            alternativeRefreshRates = alternativeRefreshRates,
            supportedHdrTypes = displayMode.supportedHdrTypes)

        val result = DisplayModeFactory.createMode(displayMode, alternativeRefreshRates,
            testCase.hasArrSupport, testCase.sizeOverrideEnabled)

        assertModesEqual(result, expectedMode)
    }

    enum class SFModeTestCase(
        val inputRefreshRate: Float,
        val hasArrSupport: Boolean,
        val sizeOverrideEnabled: Boolean,
        val expectedFlags: Int
    ) {
        ARR_60HZ_MODE(60f, true, false, Display.Mode.FLAG_ARR_RENDER_RATE),
        ARR_HIGH_REFRESH_RATE_MODE(120f, true, false, NO_FLAGS),
        NON_ARR_60HZ_MODE(60f, false, false, NO_FLAGS),
        SIZE_OVERRIDE_ENABLED_MODE(60f, false, true, Display.Mode.FLAG_SIZE_OVERRIDE),
    }

    @Test
    fun testCreateArrSyntheticModes() {
        val modeForArr = createDisplayMode(peakRefreshRate = 120f)
        val recordForArr = LocalDisplayAdapter.DisplayModeRecord(modeForArr)
        val expectedMode = createDisplayMode(parentId = modeForArr.modeId,
            peakRefreshRate = 60f, vsyncRate = 60f, flags = Display.Mode.FLAG_ARR_RENDER_RATE)

        val result = DisplayModeFactory.createArrSyntheticModes(listOf(recordForArr), true)

        assertThat(result).hasSize(1)
        assertModesEqual(result.get(0).mMode, expectedMode)
    }

    @Test
    fun testCreateArrSyntheticModes_sameSizeAndHdrModes() {
        val modeForArr1 = createDisplayMode(peakRefreshRate = 120f)
        val modeForArr2 = createDisplayMode(peakRefreshRate = 90f)
        val recordForArr1 = LocalDisplayAdapter.DisplayModeRecord(modeForArr1)
        val recordForArr2 = LocalDisplayAdapter.DisplayModeRecord(modeForArr2)
        val expectedMode = createDisplayMode(parentId = modeForArr1.modeId,
            peakRefreshRate = 60f, vsyncRate = 60f, flags = Display.Mode.FLAG_ARR_RENDER_RATE)

        val result = DisplayModeFactory.createArrSyntheticModes(
            listOf(recordForArr1, recordForArr2), true)

        assertThat(result).hasSize(1)
        assertModesEqual(result.get(0).mMode, expectedMode)
    }

    @Test
    fun testCreateArrSyntheticModes_FLAG_ARR_RENDER_RATE() {
        val recordForArr = LocalDisplayAdapter.DisplayModeRecord(
            createDisplayMode(peakRefreshRate = 120f, flags = Display.Mode.FLAG_ARR_RENDER_RATE),
        )

        val result = DisplayModeFactory.createArrSyntheticModes(listOf(recordForArr), true)
        assertWithMessage("Result should be empty for FLAG_ARR_RENDER_RATE mode")
            .that(result).isEmpty()
    }

    @Test
    fun testCreateArrSyntheticModes_noArrSupport() {
        val recordForArr = LocalDisplayAdapter.DisplayModeRecord(
            createDisplayMode(peakRefreshRate = 120f),
        )

        val result = DisplayModeFactory.createArrSyntheticModes(listOf(recordForArr), false)
        assertWithMessage("Result should be empty for ARR not supported")
            .that(result).isEmpty()
    }

    enum class AnisotropicModesTestCase(
        val inputModes: List<Display.Mode>,
        val inputSfModes: SparseArray<SurfaceControl.DisplayMode>,
        val expectedModes: List<Display.Mode>
    ) {
        SINGLE_ISOTROPIC(
            listOf(createDisplayMode(id = 1)),
            sparseArrayOf(1 to createSfDisplayMode()),
            listOf()
        ),
        NO_CORRESPONDING_SF_MODE(
            listOf(createDisplayMode(id = 1)),
            sparseArrayOf(2 to createSfDisplayMode(xDpi = 2f, yDpi = 1f)),
            listOf()
        ),
        NO_XDPI(
            listOf(createDisplayMode(id = 1)),
            sparseArrayOf(1 to createSfDisplayMode(xDpi = 0f, yDpi = 1f)),
            listOf()
        ),
        NO_YDPI(
            listOf(createDisplayMode(id = 1)),
            sparseArrayOf(1 to createSfDisplayMode(xDpi = 0f, yDpi = 1f)),
            listOf()
        ),
        SINGLE_ANISOTROPIC_TALL(
            listOf(createDisplayMode(id = 1, width = 100, height = 100)),
            sparseArrayOf(1 to createSfDisplayMode(xDpi = 2f, yDpi = 1f)),
            listOf(createDisplayMode(parentId = 1, width = 100, height = 200, flags = ANISOTROPY_FLAGS))
        ),
        SINGLE_ANISOTROPIC_WIDE(
            listOf(createDisplayMode(id = 1, width = 100, height = 100)),
            sparseArrayOf(1 to createSfDisplayMode(xDpi = 1f, yDpi = 2f)),
            listOf(createDisplayMode(parentId = 1, width = 200, height = 100, flags = ANISOTROPY_FLAGS))
        ),
        ANISOTROPIC_TALL_AND_MATCHING_WIDTH_ISOTROPIC(
            listOf(
                createDisplayMode(id = 1, width = 100, height = 100),
                createDisplayMode(id = 2, width = 102, height = 204),
            ),
            sparseArrayOf(
                1 to createSfDisplayMode(xDpi = 2f, yDpi = 1f),
                2 to createSfDisplayMode(xDpi = 1f, yDpi = 1f),
            ),
            listOf()
        ),
        ANISOTROPIC_TALL_AND_MATCHING_HEIGHT_ISOTROPIC(
            listOf(
                createDisplayMode(id = 1, width = 100, height = 100),
                createDisplayMode(id = 2, width = 51, height = 102),
            ),
            sparseArrayOf(
                1 to createSfDisplayMode(xDpi = 2f, yDpi = 1f),
                2 to createSfDisplayMode(xDpi = 1f, yDpi = 1f),
            ),
            listOf()
        ),
        ANISOTROPIC_TALL_AND_NOT_MATCHING_ISOTROPIC(
            listOf(
                createDisplayMode(id = 1, width = 100, height = 100),
                createDisplayMode(id = 2, width = 110, height = 210),
                createDisplayMode(id = 3, width = 55, height = 110),
            ),
            sparseArrayOf(
                1 to createSfDisplayMode(xDpi = 2f, yDpi = 1f),
                2 to createSfDisplayMode(xDpi = 1f, yDpi = 1f),
                3 to createSfDisplayMode(xDpi = 1f, yDpi = 1f),
            ),
            listOf(createDisplayMode(parentId = 1, width = 100, height = 200, flags = ANISOTROPY_FLAGS))
        ),
        ANISOTROPIC_WIDE_AND_MATCHING_WIDTH_ISOTROPIC(
            listOf(
                createDisplayMode(id = 1, width = 100, height = 100),
                createDisplayMode(id = 2, width = 104, height = 52),
            ),
            sparseArrayOf(
                1 to createSfDisplayMode(xDpi = 1f, yDpi = 2f),
                2 to createSfDisplayMode(xDpi = 1f, yDpi = 1f),
            ),
            listOf()
        ),
        ANISOTROPIC_WIDE_AND_MATCHING_HEIGHT_ISOTROPIC(
            listOf(
                createDisplayMode(id = 1, width = 100, height = 100),
                createDisplayMode(id = 2, width = 202, height = 101),
            ),
            sparseArrayOf(
                1 to createSfDisplayMode(xDpi = 1f, yDpi = 2f),
                2 to createSfDisplayMode(xDpi = 1f, yDpi = 1f),
            ),
            listOf()
        ),
        ANISOTROPIC_WIDE_AND_NOT_MATCHING_ISOTROPIC(
            listOf(
                createDisplayMode(id = 1, width = 100, height = 100),
                createDisplayMode(id = 2, width = 210, height = 110),
                createDisplayMode(id = 3, width = 110, height = 55),
            ),
            sparseArrayOf(
                1 to createSfDisplayMode(xDpi = 1f, yDpi = 2f),
                2 to createSfDisplayMode(xDpi = 1f, yDpi = 1f),
                3 to createSfDisplayMode(xDpi = 1f, yDpi = 1f),
            ),
            listOf(createDisplayMode(parentId = 1, width = 200, height = 100, flags = ANISOTROPY_FLAGS))
        ),
    }

    @Test
    fun testAnisotropy(@TestParameter testCase: AnisotropicModesTestCase) {
        val inputRecords =
            testCase.inputModes.stream()
                .map { mode -> LocalDisplayAdapter.DisplayModeRecord(mode) }
                .toList()
        val result =
            DisplayModeFactory.createAnisotropyCorrectedModes(inputRecords, testCase.inputSfModes, true)
        assertThat(result).hasSize(testCase.expectedModes.size)
        result.forEachIndexed { index, record ->
            assertModesEqual(record.mMode, testCase.expectedModes[index])
        }
    }

    private fun assertModesEqual(result: Display.Mode, expected: Display.Mode) {
        assertWithMessage("parentModeId is different")
            .that(result.parentModeId).isEqualTo(expected.parentModeId)
        assertWithMessage("flags are different")
            .that(result.flags).isEqualTo(expected.flags)
        assertWithMessage("width is different")
            .that(result.physicalWidth).isEqualTo(expected.physicalWidth)
        assertWithMessage("height is different")
            .that(result.physicalHeight).isEqualTo(expected.physicalHeight)
        assertWithMessage("peakRefreshRate is different")
            .that(result.refreshRate).isWithin(0.001f).of(expected.refreshRate)
        assertWithMessage("vsyncRate is different")
            .that(result.vsyncRate).isWithin(0.001f).of(expected.vsyncRate)
    }
}

private fun <V>sparseArrayOf(vararg pairs: Pair<Int, V>): SparseArray<V> {
    return SparseArray<V>(pairs.size).also { array ->
        pairs.forEach { pair ->
            array.put(pair.first, pair.second)
        }
    }
}
