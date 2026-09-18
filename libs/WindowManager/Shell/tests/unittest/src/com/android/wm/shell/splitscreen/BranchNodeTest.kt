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
class BranchNodeTest {

    private lateinit var taskA: RunningTaskInfo
    private lateinit var taskB: RunningTaskInfo

    @Before
    fun setUp() {
        taskA = mock(RunningTaskInfo::class.java)
        taskB = mock(RunningTaskInfo::class.java)
    }

    @Test
    fun testConstructor_setsParentOnChildren() {
        val leafA = LeafNode(taskA, 0.5f)
        val leafB = LeafNode(taskB, 0.5f)
        val branch = BranchNode(BranchNode.ORIENTATION_HORIZONTAL, listOf(leafA, leafB), 1.0f)

        assertEquals(2, branch.children.size)
        assertEquals(branch, leafA.parent)
        assertEquals(branch, leafB.parent)
    }

    @Test
    fun testAddChild_setsParent() {
        val branch = BranchNode(BranchNode.ORIENTATION_HORIZONTAL, emptyList(), 1.0f)
        val leafA = LeafNode(taskA, 1.0f)

        branch.addChild(leafA, 0)

        assertEquals(1, branch.children.size)
        assertEquals(branch, leafA.parent)
    }

    @Test
    fun testRemoveChild() {
        val leafA = LeafNode(taskA, 0.5f)
        val leafB = LeafNode(taskB, 0.5f)
        val branch = BranchNode(BranchNode.ORIENTATION_HORIZONTAL, listOf(leafA, leafB), 1.0f)

        assertTrue(branch.removeChild(leafA))
        assertEquals(1, branch.children.size)
        assertEquals(leafB, branch.children[0])
    }

    @Test
    fun testProperties_areSetCorrectly() {
        val branch =
            BranchNode(
                orientation = BranchNode.ORIENTATION_VERTICAL,
                children = emptyList(),
                weight = 1.0f,
                dividerSize = 20,
                isOffscreen = true,
                mainChildIndex = 1,
            )
        assertEquals(20, branch.dividerSize)
        assertTrue(branch.isOffscreen)
        assertEquals(1, branch.mainChildIndex)
    }
}
