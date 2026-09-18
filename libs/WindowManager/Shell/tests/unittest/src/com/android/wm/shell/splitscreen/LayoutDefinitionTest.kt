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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class LayoutDefinitionTest {

    private lateinit var taskA: RunningTaskInfo
    private lateinit var taskB: RunningTaskInfo

    @Before
    fun setUp() {
        taskA = mock(RunningTaskInfo::class.java)
        taskB = mock(RunningTaskInfo::class.java)
    }

    @Test
    fun testBuilder_addTask() {
        val layout =
            LayoutDefinition.Builder(BranchNode.ORIENTATION_HORIZONTAL).addTask(0.5f, taskA).build()

        assertFalse(layout.isLeaf)
        assertEquals(1, layout.children?.size)
        assertTrue(layout.children?.get(0)?.isLeaf ?: false)
        assertEquals(0.5f, layout.children?.get(0)?.weight)
        assertEquals(taskA, layout.children?.get(0)?.taskInfo)
    }

    @Test
    fun testBuilder_addNode() {
        val innerLayout =
            LayoutDefinition.Builder(BranchNode.ORIENTATION_VERTICAL).addTask(1.0f, taskB).build()

        val outerLayout =
            LayoutDefinition.Builder(BranchNode.ORIENTATION_HORIZONTAL)
                .addTask(0.3f, taskA)
                .addNode(0.7f, innerLayout)
                .build()

        assertFalse(outerLayout.isLeaf)
        assertEquals(2, outerLayout.children?.size)
        assertEquals(0.7f, outerLayout.children?.get(1)?.weight)
        assertEquals(innerLayout, outerLayout.children?.get(1))
    }

    @Test
    fun testBuilder_normalizesWeights() {
        // Weights add up to 2.0f, should be normalized to 0.25f and 0.75f
        val layout =
            LayoutDefinition.Builder(BranchNode.ORIENTATION_HORIZONTAL)
                .addTask(0.5f, taskA)
                .addTask(1.5f, taskB)
                .build()

        assertEquals(2, layout.children?.size)
        assertEquals(0.25f, layout.children?.get(0)?.weight)
        assertEquals(0.75f, layout.children?.get(1)?.weight)
    }

    @Test
    fun testIsLeaf() {
        val leaf = LayoutDefinition(taskInfo = taskA)
        val branch = LayoutDefinition.Builder(BranchNode.ORIENTATION_HORIZONTAL).build()

        assertTrue(leaf.isLeaf)
        assertFalse(branch.isLeaf)
    }
}
