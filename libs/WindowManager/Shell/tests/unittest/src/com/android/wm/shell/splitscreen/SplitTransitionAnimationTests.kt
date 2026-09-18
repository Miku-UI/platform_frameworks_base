/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.splitscreen

import android.graphics.Rect
import androidx.test.filters.SmallTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for [SplitTransitionAnimations].
 *
 * Usage: atest WMShellUnitTests:SplitTransitionAnimationTests
 */
@SmallTest
@RunWith(JUnit4::class)
class SplitTransitionAnimationTests {
    private lateinit var splitTransitionAnimations: SplitTransitionAnimations

    @Before
    fun setUp() {
        splitTransitionAnimations = SplitTransitionAnimations()
    }

    @Test
    fun testCalculateExitBounds() {
        // Screen: 200x200
        val leftStage = Rect(0, 0, 90, 200)
        val rightStage = Rect(110, 0, 200, 200)
        val topStage = Rect(0, 0, 200, 90)
        val bottomStage = Rect(0, 110, 200, 200)
        val fullScreen = Rect(0, 0, 200, 200)

        data class Scenario(val closing: Rect, val expanding: Rect, val expected: Rect)

        val scenarios =
            listOf(
                Scenario(leftStage, rightStage, Rect(-20, 0, -20, 200)),
                Scenario(rightStage, leftStage, Rect(220, 0, 220, 200)),
                Scenario(topStage, bottomStage, Rect(0, -20, 200, -20)),
                Scenario(bottomStage, topStage, Rect(0, 220, 200, 220)),
            )

        scenarios.forEach { scenario ->
            val result =
                splitTransitionAnimations.calculateExitBounds(
                    scenario.closing,
                    scenario.expanding,
                    fullScreen,
                )

            assertThat(result, equalTo(scenario.expected))
        }
    }
}
