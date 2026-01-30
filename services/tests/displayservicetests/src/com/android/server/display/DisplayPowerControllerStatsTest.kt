/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.display

import androidx.test.filters.SmallTest
import com.android.internal.util.FrameworkStatsLog
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(TestParameterInjector::class)
class DisplayPowerControllerStatsTest {

    @Test
    fun testMapLuxToProtoEnumBucket(@TestParameter testCase: LuxBucketTestCase) {
        val result = DisplayPowerController.mapLuxToProtoEnumBucket(testCase.luxValue)
        assertThat(result).isEqualTo(testCase.expectedBucket)
    }

    @Test
    fun testGetBrightnessDirection(@TestParameter testCase: BrightnessDirectionTestCase) {
        val result = DisplayPowerController.getBrightnessAdjustmentDirection(
            testCase.currentBrightnessInNits,
            testCase.lastReportedBrightnessInNits,
        )
        assertThat(result).isEqualTo(testCase.expectedDirection)
    }

    enum class LuxBucketTestCase(
        val luxValue: Float,
        val expectedBucket: Int
    ) {
        // [0, 0.1)
        LUX_0_0_TO_0_1_LOWER_BOUND(0.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_0_01),
        LUX_0_0_TO_0_1_MID_RANGE(0.05f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_0_01),
        LUX_0_0_TO_0_1_UPPER_BOUND(0.0999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_0_01),

        // [0.1, 0.3)
        LUX_0_1_TO_0_3_LOWER_BOUND(0.1f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_01_03),
        LUX_0_1_TO_0_3_MID_RANGE(0.2f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_01_03),
        LUX_0_1_TO_0_3_UPPER_BOUND(0.2999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_01_03),

        // [0.3, 1)
        LUX_0_3_TO_1_LOWER_BOUND(0.3f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_03_1),
        LUX_0_3_TO_1_MID_RANGE(0.5f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_03_1),
        LUX_0_3_TO_1_UPPER_BOUND(0.9999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_03_1),

        // [1, 3)
        LUX_1_TO_3_LOWER_BOUND(1.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_1_3),
        LUX_1_TO_3_MID_RANGE(2.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_1_3),
        LUX_1_TO_3_UPPER_BOUND(2.9999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_1_3),

        // [3, 10)
        LUX_3_TO_10_LOWER_BOUND(3.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_3_10),
        LUX_3_TO_10_MID_RANGE(5.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_3_10),
        LUX_3_TO_10_UPPER_BOUND(9.999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_3_10),

        // [10, 30)
        LUX_10_TO_30_LOWER_BOUND(10.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_10_30),
        LUX_10_TO_30_MID_RANGE(20.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_10_30),
        LUX_10_TO_30_UPPER_BOUND(29.999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_10_30),

        // [30, 100)
        LUX_30_TO_100_LOWER_BOUND(30.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_30_100),
        LUX_30_TO_100_MID_RANGE(50.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_30_100),
        LUX_30_TO_100_UPPER_BOUND(99.999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_30_100),

        // [100, 300)
        LUX_100_TO_300_LOWER_BOUND(100.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_100_300),
        LUX_100_TO_300_MID_RANGE(200.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_100_300),
        LUX_100_TO_300_UPPER_BOUND(299.999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_100_300),

        // [300, 1000)
        LUX_300_TO_1000_LOWER_BOUND(300.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_300_1000),
        LUX_300_TO_1000_MID_RANGE(500.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_300_1000),
        LUX_300_TO_1000_UPPER_BOUND(999.999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_300_1000),

        // [1000, 3000)
        LUX_1000_TO_3000_LOWER_BOUND(1000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_1000_3000),
        LUX_1000_TO_3000_MID_RANGE(2000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_1000_3000),
        LUX_1000_TO_3000_UPPER_BOUND(2999.999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_1000_3000),

        // [3000, 10000)
        LUX_3000_TO_10000_LOWER_BOUND(3000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_3000_10000),
        LUX_3000_TO_10000_MID_RANGE(5000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_3000_10000),
        LUX_3000_TO_10000_UPPER_BOUND(9999.999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_3000_10000),

        // [10000, 30000)
        LUX_10000_TO_30000_LOWER_BOUND(10000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_10000_30000),
        LUX_10000_TO_30000_MID_RANGE(20000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_10000_30000),
        LUX_10000_TO_30000_UPPER_BOUND(29999.999f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_10000_30000),

        // [30000, 100000)
        LUX_30000_TO_100000_LOWER_BOUND(30000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_30000_100000),
        LUX_30000_TO_100000_MID_RANGE(50000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_30000_100000),
        LUX_30000_TO_100000_UPPER_BOUND(99999.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_30000_100000),

        // >= 100000
        LUX_100000_TO_INF_LOWER_BOUND(100000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_100000_INF),
        LUX_100000_TO_INF_HIGH_VALUE(1000000.0f, FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_100000_INF),
    }

    enum class BrightnessDirectionTestCase(
        val currentBrightnessInNits: Float,
        val lastReportedBrightnessInNits: Float,
        val expectedDirection: Int
    ) {
        // --- Cases for DIRECTION_UNKNOWN ---
        // No change
        NO_CHANGE_EXACT(
            currentBrightnessInNits = 50f,
            lastReportedBrightnessInNits = 50f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_UNKNOWN
        ),

        // --- Cases for DIRECTION_INCREASE ---
        INCREASE_NORMAL(
            currentBrightnessInNits = 120f,
            lastReportedBrightnessInNits = 100f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_INCREASE
        ),
        INCREASE_VERY_SMALL_INCREMENT(
            currentBrightnessInNits = 50.0001f,
            lastReportedBrightnessInNits = 50.0000f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_INCREASE
        ),
        INCREASE_SMALL_INCREMENT(
            currentBrightnessInNits = 100.1f,
            lastReportedBrightnessInNits = 100.0f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_INCREASE
        ),
        INCREASE_FROM_MIN(
            currentBrightnessInNits = 0.5f,
            lastReportedBrightnessInNits = 0.0f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_INCREASE
        ),
        INCREASE_VERY_LARGE(
            currentBrightnessInNits = 5000f,
            lastReportedBrightnessInNits = 100f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_INCREASE
        ),

        // --- Cases for DIRECTION_DECREASE ---
        DECREASE_NORMAL(
            currentBrightnessInNits = 80f,
            lastReportedBrightnessInNits = 100f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_DECREASE
        ),
        DECREASE_VERY_SMALL_DECREMENT(
            currentBrightnessInNits = 50.0000f,
            lastReportedBrightnessInNits = 50.0001f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_DECREASE
        ),
        DECREASE_SMALL_DECREMENT(
            currentBrightnessInNits = 99.9f,
            lastReportedBrightnessInNits = 100.0f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_DECREASE
        ),
        DECREASE_TO_MIN(
            currentBrightnessInNits = 0.0f,
            lastReportedBrightnessInNits = 0.5f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_DECREASE
        ),
        DECREASE_VERY_LARGE(
            currentBrightnessInNits = 50f,
            lastReportedBrightnessInNits = 1000f,
            expectedDirection = FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_DECREASE
        ),
    }
}
