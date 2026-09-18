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
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class LayoutAnimatorTest {

    private lateinit var animatable: Animatable<Any>
    private lateinit var layoutAnimator: LayoutAnimator<Any>

    private lateinit var taskA: RunningTaskInfo
    private lateinit var taskB: RunningTaskInfo
    private lateinit var taskC: RunningTaskInfo

    private lateinit var targetA: Any
    private lateinit var targetB: Any

    @Before
    fun setUp() {
        animatable = mock()
        layoutAnimator = LayoutAnimator(animatable)

        taskA = RunningTaskInfo().apply { taskId = 1 }
        taskB = RunningTaskInfo().apply { taskId = 2 }
        taskC = RunningTaskInfo().apply { taskId = 3 }

        targetA = Any()
        targetB = Any()
    }

    @Test
    fun createTransition_resizingNode_createsBoundsAnimator() {
        val boundsA1 = Rect(0, 0, 500, 1000)
        val boundsB1 = Rect(500, 0, 1000, 1000)
        val leafA1 = LeafNode(taskA, 0.5f).apply { setBounds(boundsA1) }
        val leafB1 = LeafNode(taskB, 0.5f).apply { setBounds(boundsB1) }
        val beforeTree = BranchNode(BranchNode.ORIENTATION_HORIZONTAL, listOf(leafA1, leafB1))

        val boundsA2 = Rect(0, 0, 300, 1000)
        val boundsB2 = Rect(300, 0, 1000, 1000)
        val leafA2 = LeafNode(taskA, 0.3f).apply { setBounds(boundsA2) }
        val leafB2 = LeafNode(taskB, 0.7f).apply { setBounds(boundsB2) }
        val afterTree = BranchNode(BranchNode.ORIENTATION_HORIZONTAL, listOf(leafA2, leafB2))

        val targetMap = mapOf(taskA.taskId to targetA, taskB.taskId to targetB)
        layoutAnimator.createTransition(beforeTree, afterTree, targetMap)

        verify(animatable).createBoundsAnimator(targetA, boundsA1, boundsA2)
        verify(animatable).createBoundsAnimator(targetB, boundsB1, boundsB2)
        verify(animatable, never()).createFadeOutAnimator(any())
    }

    @Test
    fun createTransition_exitingNode_createsFadeOutAnimator() {
        val boundsA1 = Rect(0, 0, 500, 1000)
        val boundsB1 = Rect(500, 0, 1000, 1000)
        val leafA1 = LeafNode(taskA, 0.5f).apply { setBounds(boundsA1) }
        val leafB1 = LeafNode(taskB, 0.5f).apply { setBounds(boundsB1) }
        val beforeTree = BranchNode(BranchNode.ORIENTATION_HORIZONTAL, listOf(leafA1, leafB1))

        val boundsB2 = Rect(0, 0, 1000, 1000)
        val afterTree = LeafNode(taskB, 1.0f).apply { setBounds(boundsB2) }

        val targetMap = mapOf(taskA.taskId to targetA, taskB.taskId to targetB)
        layoutAnimator.createTransition(beforeTree, afterTree, targetMap)

        verify(animatable).createFadeOutAnimator(targetA)
        verify(animatable).createBoundsAnimator(targetB, boundsB1, boundsB2)
    }

    @Test
    fun createTransition_noChange_createsNoAnimators() {
        val boundsA = Rect(0, 0, 500, 1000)
        val boundsB = Rect(500, 0, 1000, 1000)
        val leafA = LeafNode(taskA, 0.5f).apply { setBounds(boundsA) }
        val leafB = LeafNode(taskB, 0.5f).apply { setBounds(boundsB) }
        val tree = BranchNode(BranchNode.ORIENTATION_HORIZONTAL, listOf(leafA, leafB))

        val targetMap = mapOf(taskA.taskId to targetA, taskB.taskId to targetB)
        layoutAnimator.createTransition(tree, tree, targetMap)

        verify(animatable, never()).createBoundsAnimator(any(), any(), any())
        verify(animatable, never()).createFadeOutAnimator(any())
    }
}
