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
import android.graphics.Point
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class RecursiveSplitLayoutTest {

    private lateinit var recursiveSplitLayout: RecursiveSplitLayout
    private lateinit var taskA: RunningTaskInfo
    private lateinit var taskB: RunningTaskInfo

    @Before
    fun setUp() {
        recursiveSplitLayout = RecursiveSplitLayout()
        taskA = mock(RunningTaskInfo::class.java)
        taskB = mock(RunningTaskInfo::class.java)
    }

    @Test
    fun setLayout_updatesCurrentTree() {
        val leafA = LeafNode(taskA, 1.0f)
        recursiveSplitLayout.setLayout(leafA)
        // This is a white-box test, but it's a simple way to verify state.
        // A better test would mock the LayoutEngine and verify it's called with the correct tree.
        assertNotNull(recursiveSplitLayout.calculateLayout(Rect(0, 0, 100, 100)))
    }

    @Test
    fun onDividerReleased_updatesTreeAndNotifiesListener() {
        val leafA = LeafNode(taskA, 0.5f)
        val leafB = LeafNode(taskB, 0.5f)
        val root =
            BranchNode(
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                children = listOf(leafA, leafB),
                weight = 1.0f,
                dividerSize = 10,
            )
        // Set initial bounds required for the release calculation. In a real scenario,
        // a full layout pass would have set all of these.
        leafA.setBounds(Rect(0, 0, 500, 1000))
        leafB.setBounds(Rect(510, 0, 1010, 1000))
        root.setBounds(Rect(0, 0, 1010, 1000))
        recursiveSplitLayout.setLayout(root)

        var receivedResult: CalculatedBounds? = null
        recursiveSplitLayout.listener =
            object : OnLayoutChangeListener {
                override fun onLayoutChanged(result: CalculatedBounds) {
                    receivedResult = result
                }
            }

        // Simulate releasing the divider at the 30% mark of the combined space
        recursiveSplitLayout.onDividerReleased(leafA, leafB, Point(303, 500))

        assertNotNull(receivedResult)

        // After cloning, the node instances will be different. We need to find the new
        // nodes in the result based on the task info they contain.
        val newLeafA = receivedResult!!.leafNodeBounds.keys.find { it.taskInfo == taskA }
        val newLeafB = receivedResult!!.leafNodeBounds.keys.find { it.taskInfo == taskB }

        assertNotNull(newLeafA)
        assertNotNull(newLeafB)

        // Verify the new weights were applied and calculated correctly.
        // Total width 1010, divider 10, available 1000. 30% is 300.
        assertEquals(Rect(0, 0, 300, 1000), receivedResult!!.leafNodeBounds[newLeafA])
        assertEquals(Rect(310, 0, 1010, 1000), receivedResult!!.leafNodeBounds[newLeafB])
    }
}
