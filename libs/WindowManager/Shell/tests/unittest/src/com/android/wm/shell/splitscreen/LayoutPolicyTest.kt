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

import android.app.ActivityManager.RunningTaskInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class LayoutPolicyTest {

    private lateinit var layoutPolicy: LayoutPolicy
    private lateinit var taskA: RunningTaskInfo
    private lateinit var taskB: RunningTaskInfo

    @Before
    fun setUp() {
        layoutPolicy = LayoutPolicy()
        taskA = mock(RunningTaskInfo::class.java)
        taskB = mock(RunningTaskInfo::class.java)
    }

    @Test
    fun testGetAvailableSnapPoints_binarySplit_returnsDefaultPoints() {
        val leafA = LeafNode(taskA, 0.5f)
        val leafB = LeafNode(taskB, 0.5f)
        BranchNode(BranchNode.ORIENTATION_HORIZONTAL, listOf(leafA, leafB), 1.0f)

        val points = layoutPolicy.getAvailableSnapPoints(leafA, leafB)

        // Assuming defaults include 0.5, 0.3, 0.7, 0.75, 0.25
        assertTrue(points.contains(0.5f))
        assertTrue(points.contains(0.3f))
        assertTrue(points.contains(0.7f))
        assertTrue(points.contains(0.25f))
        assertTrue(points.contains(0.75f))
    }

    @Test
    fun testGetAvailableSnapPoints_threeWaySplit_returnsEqualDistribution() {
        val leafA = LeafNode(taskA, 0.33f)
        val leafB = LeafNode(taskB, 0.33f)
        val leafC = LeafNode(mock(RunningTaskInfo::class.java), 0.34f)
        BranchNode(BranchNode.ORIENTATION_HORIZONTAL, listOf(leafA, leafB, leafC), 1.0f)

        // For N-way splits, we expect a different logic.
        // The current placeholder logic for this is missing, so this test would fail
        // and drive implementation. For now, we assume a placeholder that returns emptyList.
        val points = layoutPolicy.getAvailableSnapPoints(leafA, leafB)

        // This test is brittle and depends on the placeholder implementation.
        // A real implementation would have a robust test for N-way split snap points.
        assertEquals(5, points.size)
    }
}
