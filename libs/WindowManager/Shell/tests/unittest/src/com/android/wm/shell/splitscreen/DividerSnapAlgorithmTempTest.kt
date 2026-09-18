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

package com.android.wm.shell.splitscreen

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DividerSnapAlgorithmTempTest {

    private lateinit var snapAlgorithm: DividerSnapAlgorithmTemp

    @Before
    fun setUp() {
        snapAlgorithm = DividerSnapAlgorithmTemp()
    }

    @Test
    fun testFindClosestSnapTarget_horizontal_snapsToClosest() {
        val bounds = Rect(0, 0, 1000, 500)
        val availablePoints = listOf(0.25f, 0.5f, 0.75f) // Targets at 250, 500, 750

        // Test snapping to the left
        var target =
            snapAlgorithm.findClosestSnapTarget(
                260,
                availablePoints,
                bounds,
                BranchNode.ORIENTATION_HORIZONTAL,
            )
        assertEquals(250, target.position)
        assertEquals(0.25f, target.proportion)

        // Test snapping to the middle
        target =
            snapAlgorithm.findClosestSnapTarget(
                400,
                availablePoints,
                bounds,
                BranchNode.ORIENTATION_HORIZONTAL,
            )
        assertEquals(500, target.position)
        assertEquals(0.5f, target.proportion)

        // Test snapping to the right
        target =
            snapAlgorithm.findClosestSnapTarget(
                900,
                availablePoints,
                bounds,
                BranchNode.ORIENTATION_HORIZONTAL,
            )
        assertEquals(750, target.position)
        assertEquals(0.75f, target.proportion)
    }

    @Test
    fun testFindClosestSnapTarget_vertical_snapsToClosest() {
        val bounds = Rect(0, 0, 500, 2000)
        val availablePoints = listOf(0.2f, 0.8f) // Targets at 400, 1600

        // Test snapping to the top
        var target =
            snapAlgorithm.findClosestSnapTarget(
                300,
                availablePoints,
                bounds,
                BranchNode.ORIENTATION_VERTICAL,
            )
        assertEquals(400, target.position)
        assertEquals(0.2f, target.proportion)

        // Test snapping to the bottom
        target =
            snapAlgorithm.findClosestSnapTarget(
                1500,
                availablePoints,
                bounds,
                BranchNode.ORIENTATION_VERTICAL,
            )
        assertEquals(1600, target.position)
        assertEquals(0.8f, target.proportion)
    }

    @Test
    fun testFindClosestSnapTarget_emptyList_returnsDefault() {
        val bounds = Rect(0, 0, 1000, 500)
        val target =
            snapAlgorithm.findClosestSnapTarget(
                300,
                emptyList(),
                bounds,
                BranchNode.ORIENTATION_HORIZONTAL,
            )

        // Should default to the middle
        assertEquals(500, target.position)
        assertEquals(0.5f, target.proportion)
    }
}
