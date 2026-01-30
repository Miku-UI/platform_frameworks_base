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

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import android.graphics.Rect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ChipsMaxWidthTest : SysuiTestCase() {
    @Test
    fun chipsMaxWidth_ltr_noAppHandles_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = emptyList(),
                startSideContainerBounds = rect(left = 10, right = 100, top = 0, bottom = 50),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // 30 -> 100
        assertThat(result).isEqualTo(70.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_withDate_usesDateAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = emptyList(),
                startSideContainerBounds = rect(left = 10, right = 100, top = 0, bottom = 50),
                dateBounds = rect(left = 30, right = 60, top = 0, bottom = 50),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // 60 -> 100
        assertThat(result).isEqualTo(40.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_noAppHandles_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = emptyList(),
                startSideContainerBounds = rect(left = 1000, right = 1400, top = 0, bottom = 50),
                dateBounds = Rect(),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // 1000 <- 1350
        assertThat(result).isEqualTo(350.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_withDates_noAppHandles_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = emptyList(),
                startSideContainerBounds = rect(left = 1000, right = 1400, top = 0, bottom = 50),
                dateBounds = rect(left = 1200, right = 1350, top = 0, bottom = 50),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // 1000 <- 1200
        assertThat(result).isEqualTo(200.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_oneAppHandle_yOutside_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 100, bottom = 150, left = 80, right = 120)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 10, right = 100),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // app handle's left=80 doesn't matter because it's not overlapping with the status bar,
        // so still use 30 -> 100
        assertThat(result).isEqualTo(70.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_oneAppHandle_yOutside_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 100, bottom = 150, left = 800, right = 1200)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 1000, right = 1400),
                dateBounds = Rect(),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // app handle's right=1200 doesn't matter because it's not overlapping with the status bar,
        // so still use 1000 <- 1350
        assertThat(result).isEqualTo(350.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_oneAppHandle_furtherThanStartSideEnd_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 10, bottom = 40, left = 120, right = 200)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 10, right = 100),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // app handle's left=120 doesn't matter because it's further than the container anyway,
        // so still use 30 -> 100
        assertThat(result).isEqualTo(70.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_oneAppHandle_furtherThanStartSideEnd_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 10, bottom = 40, left = 600, right = 800)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 1000, right = 1400),
                dateBounds = Rect(),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // app handle's right=800 doesn't matter because it's further than the container anyway,
        // so still use 1000 <- 1350
        assertThat(result).isEqualTo(350.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_oneAppHandle_isEmptyRect_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 0, bottom = 0, left = 0, right = 0)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 10, right = 100),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // app handle doesn't matter because it's empty, so still use 10 -> 80
        assertThat(result).isEqualTo(70.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_oneAppHandle_isEmptyRect_usesClockAndStartSideContainerBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 1400, bottom = 1400, left = 1400, right = 1400)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 1000, right = 1400),
                dateBounds = Rect(),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // app handle doesn't matter because it's empty so still use 1000 <- 1350
        assertThat(result).isEqualTo(350.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_oneAppHandle_closerThanStartSideEnd_usesAppHandleBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 10, bottom = 40, left = 80, right = 120)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 10, right = 100),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // 30 -> 80, 80 from app handle
        assertThat(result).isEqualTo(50.dp)
    }

    @Test
    fun chipsMaxWidth_withDateBounds_ltr_oneAppHandle_closerThanStartSideEnd_usesAppHandleBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 10, bottom = 40, left = 80, right = 120)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 10, right = 100),
                dateBounds = rect(left = 30, right = 45, top = 0, bottom = 50),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // 45 -> 80, 80 from app handle
        assertThat(result).isEqualTo(35.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_oneAppHandle_closerThanStartSideEnd_usesAppHandleBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(left = 800, right = 1100, top = 10, bottom = 40)),
                startSideContainerBounds = rect(left = 1000, right = 1400, top = 0, bottom = 50),
                dateBounds = Rect(),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // 1100 <- 1350, 1100 from app handle
        assertThat(result).isEqualTo(250.dp)
    }

    @Test
    fun chipsMaxWidth_withDateBounds_rtl_oneAppHandle_closerThanStartSideEnd_usesAppHandleBounds() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(left = 800, right = 1100, top = 10, bottom = 40)),
                startSideContainerBounds = rect(left = 1000, right = 1400, top = 0, bottom = 50),
                dateBounds = rect(left = 1200, right = 1350, top = 0, bottom = 50),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // 1100 <- 1200, 1100 from app handle
        assertThat(result).isEqualTo(100.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_oneAppHandle_yOverlapsWithTopOnly_usesAppHandleBounds() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // top is within status bar area, bottom is not
                        rect(top = 40, bottom = 80, left = 80, right = 120)
                    ),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 10, right = 100),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // 30 -> 80, 80 from app handle
        assertThat(result).isEqualTo(50.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_oneAppHandle_yOverlapsWithTopOnly_usesAppHandleBounds() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // top is within status bar area, bottom is not
                        rect(top = 40, bottom = 80, left = 800, right = 1100)
                    ),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 1000, right = 1400),
                dateBounds = Rect(),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // 1100 <- 1350, 1100 from app handle
        assertThat(result).isEqualTo(250.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_oneAppHandle_yOverlapsWithBottomOnly_usesAppHandleBounds() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // bottom is within status bar area, bottom is not
                        rect(top = 0, bottom = 40, left = 80, right = 120)
                    ),
                startSideContainerBounds = rect(top = 10, bottom = 50, left = 10, right = 100),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // 30 -> 80, 80 from app handle
        assertThat(result).isEqualTo(50.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_oneAppHandle_yOverlapsWithBottomOnly_usesAppHandleBounds() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // bottom is within status bar area, top is not
                        rect(top = 0, bottom = 40, left = 800, right = 1100)
                    ),
                startSideContainerBounds = rect(top = 10, bottom = 50, left = 1000, right = 1400),
                dateBounds = Rect(),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // 1100 <- 1350, 1100 from app handle
        assertThat(result).isEqualTo(250.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_twoAppHandles_usesClosestOne() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // Closer
                        rect(top = 10, bottom = 40, left = 40, right = 80),
                        // Further
                        rect(top = 10, bottom = 40, left = 90, right = 120),
                    ),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 10, right = 100),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // 30 -> 40
        assertThat(result).isEqualTo(10.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_twoAppHandles_usesClosestOne() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // Further
                        rect(top = 10, bottom = 40, left = 800, right = 1100),
                        // Closer
                        rect(top = 10, bottom = 40, left = 1300, right = 1450),
                    ),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 1000, right = 1600),
                dateBounds = Rect(),
                clockBounds = rect(left = 1550, right = 1600, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // 1450 <- 1550
        assertThat(result).isEqualTo(100.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_multipleAppHandles_onlySomeInYRange_usesYRange() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // Closer, but outside y range
                        rect(top = 100, bottom = 200, left = 40, right = 60),
                        // Closer, inside y range
                        rect(top = 10, bottom = 40, left = 70, right = 80),
                        // Further, inside y range
                        rect(top = 10, bottom = 40, left = 90, right = 120),
                    ),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 10, right = 100),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // 30 -> 70
        assertThat(result).isEqualTo(40.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_multipleAppHandles_onlySomeInYRange_usesYRange() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // Further
                        rect(top = 10, bottom = 40, left = 800, right = 1200),
                        // Closer, inside y range
                        rect(top = 10, bottom = 40, left = 1210, right = 1310),
                        // Closer, but outside y range
                        rect(top = 100, bottom = 200, left = 1300, right = 1450),
                    ),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 1000, right = 1600),
                dateBounds = Rect(),
                clockBounds = rect(top = 0, bottom = 50, left = 1550, right = 1600),
                isRtl = true,
                density = 1f,
            )

        // 1310 <- 1550
        assertThat(result).isEqualTo(240.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_handleStartsBeforeEndOfClock_limitedToZero() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // Handle starts at 80
                        rect(left = 80, right = 120, top = 10, bottom = 40)
                    ),
                // Clock ends at 100
                dateBounds = Rect(),
                clockBounds = rect(left = 0, right = 100, top = 0, bottom = 50),
                startSideContainerBounds = rect(left = 0, right = 200, top = 0, bottom = 50),
                isRtl = false,
                density = 1f,
            )

        // Theoretically would be 80 - 100 = -20, but it's capped at 0
        assertThat(result).isEqualTo(0.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_handleEndsBeforeStartOfClock_limitedToZero() {
        val result =
            chipsMaxWidth(
                appHandles =
                    listOf(
                        // Handle ends at 1250
                        rect(left = 1000, right = 1250, top = 10, bottom = 40)
                    ),
                startSideContainerBounds = rect(left = 800, right = 1300, top = 0, bottom = 50),
                // Clock starts at 1200
                dateBounds = Rect(),
                clockBounds = rect(left = 1200, right = 1300, top = 0, bottom = 50),
                isRtl = true,
                density = 1f,
            )

        // Theoretically would be 1200 - 1250 = -50, but it's capped at 0
        assertThat(result).isEqualTo(0.dp)
    }

    @Test
    fun chipsMaxWidth_ltr_usesDensity() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(left = 80, right = 120, top = 10, bottom = 40)),
                startSideContainerBounds = rect(left = 10, right = 100, top = 0, bottom = 50),
                dateBounds = Rect(),
                clockBounds = rect(left = 10, right = 30, top = 0, bottom = 50),
                isRtl = false,
                density = 5f,
            )

        // (30 -> 80) / 5
        assertThat(result).isEqualTo(10.dp)
    }

    @Test
    fun chipsMaxWidth_rtl_usesDensity() {
        val result =
            chipsMaxWidth(
                appHandles = listOf(rect(top = 10, bottom = 40, left = 800, right = 1100)),
                startSideContainerBounds = rect(top = 0, bottom = 50, left = 1000, right = 1400),
                dateBounds = Rect(),
                clockBounds = rect(left = 1350, right = 1400, top = 0, bottom = 50),
                isRtl = true,
                density = 10f,
            )

        // (1100 <- 1350) / 10
        assertThat(result).isEqualTo(25.dp)
    }

    /** Helper function so we can have named parameters for Rect and switch the param order. */
    private fun rect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect(left, top, right, bottom)
    }
}
