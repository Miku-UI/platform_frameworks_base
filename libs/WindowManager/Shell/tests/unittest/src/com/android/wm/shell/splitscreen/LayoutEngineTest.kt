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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
class LayoutEngineTest {

    private lateinit var layoutEngine: LayoutEngine
    private lateinit var taskA: RunningTaskInfo
    private lateinit var taskB: RunningTaskInfo

    @Before
    fun setUp() {
        layoutEngine = LayoutEngine()
        taskA = mock(RunningTaskInfo::class.java)
        taskB = mock(RunningTaskInfo::class.java)
    }

    @Test
    fun deeplyNestedFlexibleSplit_mainChildIndex0_shouldCalculateCorrectBounds() {
        // ARRANGE: Set up the complex, nested tree structure and input values.
        val dividerSize = 10
        val taskC = mock(RunningTaskInfo::class.java)

        // Create nodes from the inside out for clarity.
        val leaf1 = LeafNode(taskC, debugName = "L1")
        val leaf2 = LeafNode(taskC, debugName = "L2")
        val leaf3 = LeafNode(taskC, debugName = "L3")
        val leaf4 = LeafNode(taskC, debugName = "L4")

        val b3 =
            BranchNode(
                debugName = "B3",
                children = listOf(leaf3, leaf4),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = true,
                mainChildIndex = 0,
                dividerSize = dividerSize,
            )

        val b2 =
            BranchNode(
                debugName = "B2",
                children = listOf(leaf2, b3),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = true,
                mainChildIndex = 0,
                dividerSize = dividerSize,
            )

        val rootNode =
            BranchNode(
                debugName = "B1",
                children = listOf(leaf1, b2),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = true,
                mainChildIndex = 0,
                dividerSize = dividerSize,
            )

        val screenBounds = Rect(0, 0, 100, 100)
        val layoutEngine = LayoutEngine()

        // These are the expected results based on our previous walkthrough,
        // applying .roundToInt() where necessary.
        val expectedLeaf1Bounds = Rect(0, 0, 100, 81)
        val expectedLeaf2Bounds = Rect(0, 91, 81, 172)
        val expectedLeaf3Bounds = Rect(91, 91, 172, 155) // 91 + round(71 * 0.9) = 91 + 64
        val expectedLeaf4Bounds = Rect(91, 165, 172, 229) // 155 + 10 + 64

        val expectedDivider1Bounds = Rect(0, 81, 100, 91)
        val expectedDivider2Bounds = Rect(81, 91, 91, 172)
        val expectedDivider3Bounds = Rect(91, 155, 172, 165)

        // ACT: Execute the algorithm.
        val result = layoutEngine.calculateLayout(rootNode, screenBounds)

        // ASSERT: Verify that the calculated bounds match the expected values.
        assertEquals("There should be 4 leaf node bounds.", 4, result.leafNodeBounds.size)
        assertEquals("There should be 3 divider bounds.", 3, result.dividerBounds.size)

        // Assert leaf bounds
        assertEquals(expectedLeaf1Bounds, result.leafNodeBounds[leaf1])
        assertEquals(expectedLeaf2Bounds, result.leafNodeBounds[leaf2])
        assertEquals(expectedLeaf3Bounds, result.leafNodeBounds[leaf3])
        assertEquals(expectedLeaf4Bounds, result.leafNodeBounds[leaf4])

        // Assert divider bounds
        assertEquals(expectedDivider1Bounds, result.dividerBounds[Pair(leaf1, b2)])
        assertEquals(expectedDivider2Bounds, result.dividerBounds[Pair(leaf2, b3)])
        assertEquals(expectedDivider3Bounds, result.dividerBounds[Pair(leaf3, leaf4)])
    }

    @Test
    fun simpleHorizontalStandardSplit_shouldCalculateCorrectBounds() {
        // ARRANGE
        val dividerSize = 10
        val leaf1 = LeafNode(taskA, debugName = "L1")
        val leaf2 = LeafNode(taskA, debugName = "L2")
        val root =
            BranchNode(
                debugName = "B1",
                children = listOf(leaf1, leaf2),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = false,
                dividerSize = dividerSize,
            )
        val screenBounds = Rect(0, 0, 100, 100)
        val engine = LayoutEngine()

        // ACT
        val result = engine.calculateLayout(root, screenBounds)

        // ASSERT
        assertEquals(2, result.leafNodeBounds.size)
        assertEquals(1, result.dividerBounds.size)
        assertEquals(Rect(0, 0, 45, 100), result.leafNodeBounds[leaf1])
        assertEquals(Rect(55, 0, 100, 100), result.leafNodeBounds[leaf2])
        assertEquals(Rect(45, 0, 55, 100), result.dividerBounds[Pair(leaf1, leaf2)])
    }

    @Test
    fun verticalStandardSplit_withUnequalWeights_shouldCalculateProportionalBounds() {
        // ARRANGE
        val dividerSize = 10
        val leaf1 = LeafNode(taskA, debugName = "L1", weight = 1f)
        val leaf2 = LeafNode(taskA, debugName = "L2", weight = 3f)
        val root =
            BranchNode(
                debugName = "B1",
                children = listOf(leaf1, leaf2),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = false,
                dividerSize = dividerSize,
            )
        val screenBounds = Rect(0, 0, 100, 100)
        val engine = LayoutEngine()

        // ACT
        val result = engine.calculateLayout(root, screenBounds)

        // ASSERT
        assertEquals(Rect(0, 0, 100, 23), result.leafNodeBounds[leaf1])
        assertEquals(Rect(0, 33, 100, 100), result.leafNodeBounds[leaf2])
        assertEquals(Rect(0, 23, 100, 33), result.dividerBounds[Pair(leaf1, leaf2)])
    }

    @Test
    fun horizontalFlexibleSplit_withSecondChildDominant_shouldPositionCorrectly() {
        // ARRANGE
        val dividerSize = 10
        val leaf1 = LeafNode(taskB, debugName = "L1")
        val leaf2 = LeafNode(taskA, debugName = "L2")
        val root =
            BranchNode(
                debugName = "B1",
                children = listOf(leaf1, leaf2),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = true,
                mainChildIndex = 1, // Second child is dominant
                dividerSize = dividerSize,
            )
        val screenBounds = Rect(0, 0, 100, 100)
        val engine = LayoutEngine()

        // ACT
        val result = engine.calculateLayout(root, screenBounds)

        // ASSERT
        assertEquals(Rect(-72, 0, 9, 100), result.leafNodeBounds[leaf1])
        assertEquals(Rect(19, 0, 100, 100), result.leafNodeBounds[leaf2])
        assertEquals(Rect(9, 0, 19, 100), result.dividerBounds[Pair(leaf1, leaf2)])
    }

    @Test
    fun mixedLayout_standardThenFlexible_shouldCalculateCorrectly() {
        // ARRANGE
        val dividerSize = 10
        val leaf1 = LeafNode(taskB, debugName = "L1")
        val leaf2 = LeafNode(taskB, debugName = "L2")
        val leaf3 = LeafNode(taskB, debugName = "L3")
        val b2 =
            BranchNode(
                debugName = "B2",
                children = listOf(leaf2, leaf3),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = true,
                mainChildIndex = 1,
                dividerSize = dividerSize,
            )
        val root =
            BranchNode(
                debugName = "B1",
                children = listOf(leaf1, b2),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = false,
                dividerSize = dividerSize,
            )
        val screenBounds = Rect(0, 0, 100, 100)
        val engine = LayoutEngine()

        // ACT
        val result = engine.calculateLayout(root, screenBounds)

        // ASSERT
        assertEquals(Rect(0, 0, 100, 45), result.leafNodeBounds[leaf1])
        assertEquals(Rect(-72, 55, 9, 100), result.leafNodeBounds[leaf2])
        assertEquals(Rect(19, 55, 100, 100), result.leafNodeBounds[leaf3])
    }

    @Test
    fun edgeCase_singleChild_shouldOccupyFullBounds() {
        // ARRANGE
        val dividerSize = 10
        val leaf1 = LeafNode(taskB, debugName = "L1")
        val root =
            BranchNode(
                debugName = "B1",
                children = listOf(leaf1),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                dividerSize = dividerSize,
            )
        val screenBounds = Rect(0, 0, 100, 100)
        val engine = LayoutEngine()

        // ACT
        val result = engine.calculateLayout(root, screenBounds)

        // ASSERT
        assertEquals(1, result.leafNodeBounds.size)
        assertTrue(result.dividerBounds.isEmpty())
        assertEquals(Rect(0, 0, 100, 100), result.leafNodeBounds[leaf1])
    }

    @Test
    fun edgeCase_noChildren_shouldNotCrashAndReturnEmpty() {
        // ARRANGE
        val dividerSize = 10
        val root =
            BranchNode(
                debugName = "B1",
                children = emptyList(),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                dividerSize = dividerSize,
            )
        val screenBounds = Rect(0, 0, 100, 100)
        val engine = LayoutEngine()

        // ACT
        val result = engine.calculateLayout(root, screenBounds)

        // ASSERT
        assertTrue(result.leafNodeBounds.isEmpty())
        assertTrue(result.dividerBounds.isEmpty())
    }

    @Test
    fun sideBySideFlexibleSplits_withMixedDominantChildren_shouldCalculateCorrectly() {
        // ARRANGE
        // This test features a root horizontal split (B1), where each child is another
        // vertical flexible split (B2 and B3), each with different dominant children.
        val dividerSize = 10

        // Children for the left-side branch (B2)
        val leaf1 = LeafNode(taskA, debugName = "L1")
        val leaf2 = LeafNode(taskA, debugName = "L2")

        // Children for the right-side branch (B3)
        val leaf3 = LeafNode(taskA, debugName = "L3")
        val leaf4 = LeafNode(taskA, debugName = "L4")

        // B2 is the left-side branch, its FIRST child (Leaf1) is dominant.
        val b2 =
            BranchNode(
                debugName = "B2",
                children = listOf(leaf1, leaf2),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = true,
                mainChildIndex = 0,
                dividerSize = dividerSize,
            )

        // B3 is the right-side branch, its SECOND child (Leaf4) is dominant.
        val b3 =
            BranchNode(
                debugName = "B3",
                children = listOf(leaf3, leaf4),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = true,
                mainChildIndex = 1,
                dividerSize = dividerSize,
            )

        // B1 is the root, its FIRST child (B2) is dominant.
        val rootNode =
            BranchNode(
                debugName = "B1",
                children = listOf(b2, b3),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = true,
                mainChildIndex = 0,
                dividerSize = dividerSize,
            )

        val screenBounds = Rect(0, 0, 100, 100)
        val engine = LayoutEngine()

        // ACT
        val result = engine.calculateLayout(rootNode, screenBounds)

        // ASSERT
        // Step 1: B1 (Horizontal Flex, B2 dominant)
        // Available width = 90. Dominant width = 81.
        // B2 gets Rect(0, 0, 81, 100). B3 gets Rect(91, 0, 172, 100).

        // Step 2: B2 (Vertical Flex, Leaf1 dominant) within Rect(0, 0, 81, 100)
        // Available height = 90. Dominant height = 81.
        // Leaf1 gets Rect(0, 0, 81, 81). Leaf2 gets Rect(0, 91, 81, 172).
        assertEquals(Rect(0, 0, 81, 81), result.leafNodeBounds[leaf1])
        assertEquals(Rect(0, 91, 81, 172), result.leafNodeBounds[leaf2])

        // Step 3: B3 (Vertical Flex, Leaf4 dominant) within Rect(91, 0, 172, 100)
        // Available height = 90. Dominant height = 81. Non-dominant visible = 9.
        // Leaf3 gets Rect(91, -72, 172, 9). Leaf4 gets Rect(91, 19, 172, 100).
        assertEquals(Rect(91, -72, 172, 9), result.leafNodeBounds[leaf3])
        assertEquals(Rect(91, 19, 172, 100), result.leafNodeBounds[leaf4])

        // Assert divider count
        assertEquals(3, result.dividerBounds.size)
    }

    @Test
    fun nestedFlexLayout_withDominantFlexChild_mainChildIndex1() {
        // ARRANGE
        // This is a variant where the SECOND child of each branch is dominant.
        val dividerSize = 10

        val leaf1 = LeafNode(taskA, debugName = "L1")
        val leaf2 = LeafNode(taskA, debugName = "L2")
        val leaf3 = LeafNode(taskA, debugName = "L3")

        // B2's second child (Leaf2) is dominant.
        val b2 =
            BranchNode(
                debugName = "B2",
                children = listOf(leaf1, leaf2),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = true,
                mainChildIndex = 1,
                dividerSize = dividerSize,
            )

        // rootNode's second child (Leaf3) is dominant, making B2 non-dominant.
        val rootNode =
            BranchNode(
                debugName = "B1",
                children = listOf(b2, leaf3),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = true,
                mainChildIndex = 1,
                dividerSize = dividerSize,
            )

        val screenBounds = Rect(0, 0, 100, 100)

        // ACT
        val result = layoutEngine.calculateLayout(rootNode, screenBounds)

        // ASSERT
        // Step 1: Root (Vertical, Leaf3 dominant)
        // B2 is non-dominant, gets top sliver: Rect(0, -72, 100, 9)
        // Leaf3 is dominant, gets bottom area: Rect(0, 19, 100, 100)
        assertEquals(Rect(0, 19, 100, 100), result.leafNodeBounds[leaf3])

        // Step 2: B2 (Horizontal, Leaf2 dominant) within Rect(0, -72, 100, 9)
        // Leaf1 is non-dominant, gets left sliver: Rect(-72, -72, 9, 9)
        // Leaf2 is dominant, gets right area: Rect(19, -72, 100, 9)
        assertEquals(Rect(-72, -72, 9, 9), result.leafNodeBounds[leaf1])
        assertEquals(Rect(19, -72, 100, 9), result.leafNodeBounds[leaf2])
    }

    @Test
    fun nestedFlexLayout_withNonDominantFlexChild_mainChildIndex1() {
        // ARRANGE
        // This is a variant where the SECOND child of each branch is dominant.
        val dividerSize = 10

        val leaf1 = LeafNode(taskA, debugName = "L1")
        val leaf2 = LeafNode(taskA, debugName = "L2")
        val leaf3 = LeafNode(taskA, debugName = "L3")

        // B2's second child (Leaf3) is dominant.
        val b2 =
            BranchNode(
                debugName = "B2",
                children = listOf(leaf2, leaf3),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = true,
                mainChildIndex = 1,
                dividerSize = dividerSize,
            )

        // rootNode's second child (B2) is dominant.
        val rootNode =
            BranchNode(
                debugName = "B1",
                children = listOf(leaf1, b2),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = true,
                mainChildIndex = 1,
                dividerSize = dividerSize,
            )

        val screenBounds = Rect(0, 0, 100, 100)

        // ACT
        val result = layoutEngine.calculateLayout(rootNode, screenBounds)

        // ASSERT
        // Step 1: Root (Vertical, B2 dominant)
        // Leaf1 is non-dominant, gets top sliver: Rect(0, -72, 100, 9)
        // B2 is dominant, gets bottom area: Rect(0, 19, 100, 100)
        assertEquals(Rect(0, -72, 100, 9), result.leafNodeBounds[leaf1])

        // Step 2: B2 (Horizontal, Leaf3 dominant) within Rect(0, 19, 100, 100)
        // Leaf2 is non-dominant, gets left sliver: Rect(-72, 19, 9, 100)
        // Leaf3 is dominant, gets right area: Rect(19, 19, 100, 100)
        assertEquals(Rect(-72, 19, 9, 100), result.leafNodeBounds[leaf2])
        assertEquals(Rect(19, 19, 100, 100), result.leafNodeBounds[leaf3])
    }

    @Test
    fun nestedFlexLayout_withDominantFlexChild_shouldCalculateCorrectly() {
        // ARRANGE
        // This tests a 2-level flex layout where the dominant child of the root (B2)
        // is also a flexible split.
        val dividerSize = 10

        val leaf1 = LeafNode(taskA, debugName = "L1")
        val leaf2 = LeafNode(taskA, debugName = "L2")
        val leaf3 = LeafNode(taskA, debugName = "L3")

        // B2 is a horizontal flexible split.
        val b2 =
            BranchNode(
                debugName = "B2",
                children = listOf(leaf1, leaf2),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = true,
                mainChildIndex = 0, // Leaf1 is dominant in this sub-group
                dividerSize = dividerSize,
            )

        // rootNode's first child (B2) is dominant.
        val rootNode =
            BranchNode(
                debugName = "B1",
                children = listOf(b2, leaf3),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = true,
                mainChildIndex = 0,
                dividerSize = dividerSize,
            )

        val screenBounds = Rect(0, 0, 100, 100)

        // ACT
        val result = layoutEngine.calculateLayout(rootNode, screenBounds)

        // ASSERT
        // Step 1: Root (Vertical, B2 dominant)
        // B2 gets the top dominant space: Rect(0, 0, 100, 81)
        // Leaf3 gets the bottom non-dominant space: Rect(0, 91, 100, 172)
        assertEquals(Rect(0, 91, 100, 172), result.leafNodeBounds[leaf3])

        // Step 2: B2 (Horizontal, Leaf1 dominant) within Rect(0, 0, 100, 81)
        // Leaf1 gets the left dominant space: Rect(0, 0, 81, 81)
        // Leaf2 gets the right non-dominant space: Rect(91, 0, 172, 81)
        assertEquals(Rect(0, 0, 81, 81), result.leafNodeBounds[leaf1])
        assertEquals(Rect(91, 0, 172, 81), result.leafNodeBounds[leaf2])
    }

    @Test
    fun nestedFlexLayout_withNonDominantFlexChild_shouldCalculateCorrectly() {
        // ARRANGE
        // This tests a 2-level flex layout where the non-dominant child of the root (B2)
        // is also a flexible split.
        val dividerSize = 10

        val leaf1 = LeafNode(taskA, debugName = "L1")
        val leaf2 = LeafNode(taskA, debugName = "L2")
        val leaf3 = LeafNode(taskA, debugName = "L3")

        // B2 is a horizontal flexible split.
        val b2 =
            BranchNode(
                debugName = "B2",
                children = listOf(leaf2, leaf3),
                orientation = BranchNode.ORIENTATION_HORIZONTAL,
                isOffscreen = true,
                mainChildIndex = 0, // Leaf2 is dominant in this sub-group
                dividerSize = dividerSize,
            )

        // rootNode's first child (Leaf1) is dominant, making B2 non-dominant.
        val rootNode =
            BranchNode(
                debugName = "B1",
                children = listOf(leaf1, b2),
                orientation = BranchNode.ORIENTATION_VERTICAL,
                isOffscreen = true,
                mainChildIndex = 0,
                dividerSize = dividerSize,
            )

        val screenBounds = Rect(0, 0, 100, 100)

        // ACT
        val result = layoutEngine.calculateLayout(rootNode, screenBounds)

        // ASSERT
        // Step 1: Root (Vertical, Leaf1 dominant)
        // Leaf1 gets the top dominant space: Rect(0, 0, 100, 81)
        // B2 gets the bottom non-dominant space: Rect(0, 91, 100, 172)
        assertEquals(Rect(0, 0, 100, 81), result.leafNodeBounds[leaf1])

        // Step 2: B2 (Horizontal, Leaf2 dominant) within Rect(0, 91, 100, 172)
        // Leaf2 gets the left dominant space: Rect(0, 91, 81, 172)
        // Leaf3 gets the right non-dominant space: Rect(91, 91, 172, 172)
        assertEquals(Rect(0, 91, 81, 172), result.leafNodeBounds[leaf2])
        assertEquals(Rect(91, 91, 172, 172), result.leafNodeBounds[leaf3])
    }
}
