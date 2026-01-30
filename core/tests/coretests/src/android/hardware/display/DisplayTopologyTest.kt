/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.hardware.display

import android.graphics.PointF
import android.graphics.RectF
import android.hardware.display.DisplayTopology.POSITION_BOTTOM
import android.hardware.display.DisplayTopology.POSITION_LEFT
import android.hardware.display.DisplayTopology.POSITION_TOP
import android.hardware.display.DisplayTopology.POSITION_RIGHT
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.Display
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import androidx.core.util.size

class DisplayTopologyTest {
    private var topology = DisplayTopology()

    @Test
    fun addOneDisplay() {
        val displayId = 1
        val width = 800
        val height = 600
        val density = 160

        topology.addDisplay(displayId, width, height, density)

        assertThat(topology.primaryDisplayId).isEqualTo(displayId)
        verifyDisplay(topology.root!!, displayId, width, height, density, noOfChildren = 0)

        assertThat(topology.allNodesIdMap())
            .isEqualTo(mapOf(displayId to topology.root!!))
    }

    @Test
    fun addTwoDisplays() {
        val displayId1 = 1
        val width1 = 800
        val height1 = 600

        val displayId2 = 2
        val width2 = 1000
        val height2 = 1500

        val density = 160

        topology.addDisplay(displayId1, width1, height1, density)
        topology.addDisplay(displayId2, width2, height2, density)

        assertThat(topology.primaryDisplayId).isEqualTo(displayId1)

        val display1 = topology.root!!
        val display2 = display1.children[0]
        verifyDisplay(display1, displayId1, width1, height1, density, noOfChildren = 1)
        verifyDisplay(display2, displayId2, width2, height2, density, POSITION_TOP,
            offset = width1 / 2f - width2 / 2f, noOfChildren = 0)

        assertThat(topology.allNodesIdMap())
            .isEqualTo(mapOf(displayId1 to display1, displayId2 to display2))
    }

    @Test
    fun addManyDisplays() {
        val displayId1 = 1
        val width1 = 800
        val height1 = 600

        val displayId2 = 2
        val width2 = 1000
        val height2 = 1500

        val density = 160

        topology.addDisplay(displayId1, width1, height1, density)
        topology.addDisplay(displayId2, width2, height2, density)

        val noOfDisplays = 30
        for (i in 3..noOfDisplays) {
            topology.addDisplay(/* displayId= */ i, width1, height1, density)
        }

        assertThat(topology.primaryDisplayId).isEqualTo(displayId1)

        val display1 = topology.root!!
        verifyDisplay(display1, displayId1, width1, height1, density, noOfChildren = 1)

        val display2 = display1.children[0]
        verifyDisplay(display2, displayId2, width2, height2, density, POSITION_TOP,
            offset = width1 / 2f - width2 / 2f, noOfChildren = 1)

        var display = display2
        for (i in 3..noOfDisplays) {
            display = display.children[0]
            // The last display should have no children
            verifyDisplay(display, id = i, width1, height1, density, POSITION_RIGHT, offset = 0f,
                noOfChildren = if (i < noOfDisplays) 1 else 0)
        }
    }

    @Test
    fun updateDisplay() {
        val displayId = 1
        val width = 800
        val height = 600

        val newWidth = 1000
        val newHeight = 500

        val density = 160

        topology.addDisplay(displayId, width, height, density)
        assertThat(topology.updateDisplay(displayId, newWidth, newHeight, density)).isTrue()

        assertThat(topology.primaryDisplayId).isEqualTo(displayId)
        verifyDisplay(topology.root!!, displayId, newWidth, newHeight, density, noOfChildren = 0)
    }

    @Test
    fun updateDisplay_notUpdated() {
        val displayId = 1
        val width = 800
        val height = 600
        val density = 160

        topology.addDisplay(displayId, width, height, density)

        // Same size
        assertThat(topology.updateDisplay(displayId, width, height, density)).isFalse()

        // Display doesn't exist
        assertThat(topology.updateDisplay(/* displayId= */ 100, width, height, density)).isFalse()

        assertThat(topology.primaryDisplayId).isEqualTo(displayId)
        verifyDisplay(topology.root!!, displayId, width, height, density, noOfChildren = 0)
    }

    @Test
    fun updateDisplayDoesNotAffectDefaultTopology() {
        val width1 = 700
        val height = 600
        val density = 160
        topology.addDisplay(/* displayId= */ 1, width1, height, density)

        val width2 = 800
        val noOfDisplays = 30
        for (i in 2..noOfDisplays) {
            topology.addDisplay(/* displayId= */ i, width2, height, density)
        }

        val displaysToUpdate = arrayOf(3, 7, 18)
        val newWidth = 1000
        val newHeight = 1500
        for (i in displaysToUpdate) {
            assertThat(topology.updateDisplay(/* displayId= */ i, newWidth, newHeight, density))
                    .isTrue()
        }

        assertThat(topology.primaryDisplayId).isEqualTo(1)

        val display1 = topology.root!!
        verifyDisplay(display1, id = 1, width1, height, density, noOfChildren = 1)

        val display2 = display1.children[0]
        verifyDisplay(display2, id = 2, width2, height, density, POSITION_TOP,
            offset = width1 / 2f - width2 / 2f, noOfChildren = 1)

        var display = display2
        for (i in 3..noOfDisplays) {
            display = display.children[0]
            // The last display should have no children
            verifyDisplay(display, id = i, if (i in displaysToUpdate) newWidth else width2,
                if (i in displaysToUpdate) newHeight else height, density, POSITION_RIGHT,
                offset = 0f, noOfChildren = if (i < noOfDisplays) 1 else 0)
        }
    }

    @Test
    fun removeDisplays() {
        val displayId1 = 1
        val width1 = 800
        val height1 = 600

        val displayId2 = 2
        val width2 = 1000
        val height2 = 1500

        val density = 160

        topology.addDisplay(displayId1, width1, height1, density)
        topology.addDisplay(displayId2, width2, height2, density)

        val noOfDisplays = 30
        for (i in 3..noOfDisplays) {
            topology.addDisplay(/* displayId= */ i, width1, height1, density)
        }

        var removedDisplays = arrayOf(20)
        assertThat(topology.removeDisplay(20)).isTrue()

        assertThat(topology.primaryDisplayId).isEqualTo(displayId1)

        var display1 = topology.root!!
        verifyDisplay(display1, displayId1, width1, height1, density, noOfChildren = 1)

        var display2 = display1.children[0]
        verifyDisplay(display2, displayId2, width2, height2, density, POSITION_TOP,
            offset = width1 / 2f - width2 / 2f, noOfChildren = 1)

        var display = display2
        for (i in 3..noOfDisplays) {
            if (i in removedDisplays) {
                continue
            }
            display = display.children[0]
            // The last display should have no children
            verifyDisplay(display, id = i, width1, height1, density, POSITION_RIGHT, offset = 0f,
                noOfChildren = if (i < noOfDisplays) 1 else 0)
        }

        assertThat(topology.removeDisplay(22)).isTrue()
        removedDisplays += 22
        assertThat(topology.removeDisplay(23)).isTrue()
        removedDisplays += 23
        assertThat(topology.removeDisplay(25)).isTrue()
        removedDisplays += 25

        assertThat(topology.primaryDisplayId).isEqualTo(displayId1)

        display1 = topology.root!!
        verifyDisplay(display1, displayId1, width1, height1, density, noOfChildren = 1)

        display2 = display1.children[0]
        verifyDisplay(display2, displayId2, width2, height2, density, POSITION_TOP,
            offset = width1 / 2f - width2 / 2f, noOfChildren = 1)

        display = display2
        for (i in 3..noOfDisplays) {
            if (i in removedDisplays) {
                continue
            }
            display = display.children[0]
            // The last display should have no children
            verifyDisplay(display, id = i, width1, height1, density, POSITION_RIGHT, offset = 0f,
                noOfChildren = if (i < noOfDisplays) 1 else 0)
        }
    }

    @Test
    fun removeAllDisplays() {
        val displayId = 1
        val width = 800
        val height = 600
        val density = 160

        topology.addDisplay(displayId, width, height, density)
        assertThat(topology.removeDisplay(displayId)).isTrue()

        assertThat(topology.primaryDisplayId).isEqualTo(Display.INVALID_DISPLAY)
        assertThat(topology.root).isNull()
    }

    @Test
    fun removeDisplayThatDoesNotExist() {
        val displayId = 1
        val width = 800
        val height = 600
        val density = 160

        topology.addDisplay(displayId, width, height, density)
        assertThat(topology.removeDisplay(3)).isFalse()

        assertThat(topology.primaryDisplayId).isEqualTo(displayId)
        verifyDisplay(topology.root!!, displayId, width, height, density, noOfChildren = 0)
    }

    @Test
    fun removePrimaryDisplay() {
        val displayId1 = 1
        val displayId2 = 2
        val width = 800
        val height = 600
        val density = 160

        topology = DisplayTopology(/* root= */ null, displayId2)
        topology.addDisplay(displayId1, width, height, density)
        topology.addDisplay(displayId2, width, height, density)
        assertThat(topology.removeDisplay(displayId2)).isTrue()

        assertThat(topology.primaryDisplayId).isEqualTo(displayId1)
        verifyDisplay(topology.root!!, displayId1, width, height, density, noOfChildren = 0)
    }

    @Test
    fun normalization_clampsOffsets() {
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, /* position= */ 0,
            /* offset= */ 0f)

        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 800f)
        display1.addChild(display2)

        val primaryDisplayId = 3
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 160, POSITION_LEFT, /* offset= */ -300f)
        display1.addChild(display3)

        val display4 = DisplayTopology.TreeNode(/* displayId= */ 4, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, POSITION_TOP, /* offset= */ 1000f)
        display2.addChild(display4)

        topology = DisplayTopology(display1, primaryDisplayId)
        topology.normalize()

        assertThat(topology.primaryDisplayId).isEqualTo(primaryDisplayId)

        val actualDisplay1 = topology.root!!
        verifyDisplay(actualDisplay1, id = 1, width = 200, height = 600, density = 160,
                noOfChildren = 2)

        val actualDisplay2 = actualDisplay1.children[0]
        verifyDisplay(actualDisplay2, id = 2, width = 600, height = 200, density = 160,
            POSITION_RIGHT, offset = 600f, noOfChildren = 1)

        val actualDisplay3 = actualDisplay1.children[1]
        verifyDisplay(actualDisplay3, id = 3, width = 600, height = 200, density = 160,
            POSITION_LEFT, offset = -200f, noOfChildren = 0)

        val actualDisplay4 = actualDisplay2.children[0]
        verifyDisplay(actualDisplay4, id = 4, width = 200, height = 600, density = 160,
            POSITION_TOP, offset = 600f, noOfChildren = 0)
    }

    @Test
    fun normalization_noOverlaps_leavesTopologyUnchanged() {
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, /* position= */ 0,
            /* offset= */ 0f)

        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 0f)
        display1.addChild(display2)

        val primaryDisplayId = 3
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 400f)
        display1.addChild(display3)

        val display4 = DisplayTopology.TreeNode(/* displayId= */ 4, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 0f)
        display2.addChild(display4)

        topology = DisplayTopology(display1, primaryDisplayId)
        topology.normalize()

        assertThat(topology.primaryDisplayId).isEqualTo(primaryDisplayId)

        val actualDisplay1 = topology.root!!
        verifyDisplay(actualDisplay1, id = 1, width = 200, height = 600, density = 160,
            noOfChildren = 2)

        val actualDisplay2 = actualDisplay1.children[0]
        verifyDisplay(actualDisplay2, id = 2, width = 600, height = 200, density = 160,
            POSITION_RIGHT, offset = 0f, noOfChildren = 1)

        val actualDisplay3 = actualDisplay1.children[1]
        verifyDisplay(actualDisplay3, id = 3, width = 600, height = 200, density = 160,
            POSITION_RIGHT, offset = 400f, noOfChildren = 0)

        val actualDisplay4 = actualDisplay2.children[0]
        verifyDisplay(actualDisplay4, id = 4, width = 200, height = 600, density = 160,
            POSITION_RIGHT, offset = 0f, noOfChildren = 0)
    }

    @Test
    fun normalization_moveDisplayWithoutReparenting() {
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, /* position= */ 0,
            /* offset= */ 0f)

        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 0f)
        display1.addChild(display2)

        val primaryDisplayId = 3
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 10f)
        display1.addChild(display3)

        val display4 = DisplayTopology.TreeNode(/* displayId= */ 4, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 0f)
        display2.addChild(display4)

        topology = DisplayTopology(display1, primaryDisplayId)
        // Display 3 becomes a child of display 2. Display 4 gets moved without changing its parent.
        topology.normalize()

        assertThat(topology.primaryDisplayId).isEqualTo(primaryDisplayId)

        val actualDisplay1 = topology.root!!
        verifyDisplay(actualDisplay1, id = 1, width = 200, height = 600, density = 160,
            noOfChildren = 1)

        val actualDisplay2 = actualDisplay1.children[0]
        verifyDisplay(actualDisplay2, id = 2, width = 200, height = 600, density = 160,
            POSITION_RIGHT, offset = 0f, noOfChildren = 2)

        val actualDisplay3 = actualDisplay2.children[0]
        verifyDisplay(actualDisplay3, id = 3, width = 600, height = 200, density = 160,
            POSITION_RIGHT, offset = 10f, noOfChildren = 0)

        val actualDisplay4 = actualDisplay2.children[1]
        verifyDisplay(actualDisplay4, id = 4, width = 200, height = 600, density = 160,
            POSITION_RIGHT, offset = 210f, noOfChildren = 0)
    }

    @Test
    fun normalization_moveDisplayWithoutReparenting_offsetOutOfBounds() {
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 200,
            /* logicalHeight= */ 50, /* logicalDensity= */ 160, /* position= */ 0, /* offset= */ 0f)

        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 0f)
        display1.addChild(display2)

        val primaryDisplayId = 3
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 10f)
        display1.addChild(display3)

        topology = DisplayTopology(display1, primaryDisplayId)
        // Display 3 gets moved and its left side is still on the same line as the right side
        // of Display 1, but it no longer touches it (the offset is out of bounds), so Display 2
        // becomes its new parent.
        topology.normalize()

        assertThat(topology.primaryDisplayId).isEqualTo(primaryDisplayId)

        val actualDisplay1 = topology.root!!
        verifyDisplay(actualDisplay1, id = 1, width = 200, height = 50, density = 160,
            noOfChildren = 1)

        val actualDisplay2 = actualDisplay1.children[0]
        verifyDisplay(actualDisplay2, id = 2, width = 600, height = 200, density = 160,
            POSITION_RIGHT, offset = 0f, noOfChildren = 1)

        val actualDisplay3 = actualDisplay2.children[0]
        verifyDisplay(actualDisplay3, id = 3, width = 600, height = 200, density = 160,
            POSITION_BOTTOM, offset = 0f, noOfChildren = 0)
    }

    @Test
    fun normalization_moveAndReparentDisplay() {
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, /* position= */ 0,
            /* offset= */ 0f)

        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 0f)
        display1.addChild(display2)

        val primaryDisplayId = 3
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 400f)
        display1.addChild(display3)

        val display4 = DisplayTopology.TreeNode(/* displayId= */ 4, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, POSITION_RIGHT, /* offset= */ 0f)
        display2.addChild(display4)

        topology = DisplayTopology(display1, primaryDisplayId)
        topology.normalize()

        assertThat(topology.primaryDisplayId).isEqualTo(primaryDisplayId)

        val actualDisplay1 = topology.root!!
        verifyDisplay(actualDisplay1, id = 1, width = 200, height = 600, density = 160,
            noOfChildren = 1)

        val actualDisplay2 = actualDisplay1.children[0]
        verifyDisplay(actualDisplay2, id = 2, width = 200, height = 600, density = 160,
            POSITION_RIGHT, offset = 0f, noOfChildren = 1)

        val actualDisplay3 = actualDisplay2.children[0]
        verifyDisplay(actualDisplay3, id = 3, width = 600, height = 200, density = 160,
            POSITION_RIGHT, offset = 400f, noOfChildren = 1)

        val actualDisplay4 = actualDisplay3.children[0]
        verifyDisplay(actualDisplay4, id = 4, width = 200, height = 600, density = 160,
            POSITION_RIGHT, offset = -400f, noOfChildren = 0)
    }

    @Test
    fun rearrange_twoDisplays() {
        val root = rearrangeRects(
            // Arrange in staggered manner, connected vertically.
            DisplayArrangement(150, 100, 160, 100f, 100f),
            DisplayArrangement(150, 100, 160, 150f, 200f),
        )

        verifyDisplay(root, id = 0, width = 150, height = 100, density = 160, noOfChildren = 1)
        val node = root.children[0]
        verifyDisplay(
                node, id = 1, width = 150, height = 100, density = 160, POSITION_BOTTOM,
                offset = 50f, noOfChildren = 0)
    }

    @Test
    fun rearrange_reverseOrderOfSeveralDisplays() {
        val root = rearrangeRects(
            DisplayArrangement(150, 100, 160, 0f, 0f),
            DisplayArrangement(150, 100, 160, -150f, 0f),
            DisplayArrangement(150, 100, 160, -300f, 0f),
            DisplayArrangement(150, 100, 160, -450f, 0f),
        )

        verifyDisplay(root, id = 0, width = 150, height = 100, density = 160, noOfChildren = 1)
        var node = root.children[0]
        verifyDisplay(
                node, id = 1, width = 150, height = 100, density = 160, POSITION_LEFT, offset = 0f,
                noOfChildren = 1)
        node = node.children[0]
        verifyDisplay(
                node, id = 2, width = 150, height = 100, density = 160, POSITION_LEFT, offset = 0f,
                noOfChildren = 1)
        node = node.children[0]
        verifyDisplay(
                node, id = 3, width = 150, height = 100, density = 160, POSITION_LEFT, offset = 0f,
                noOfChildren = 0)
    }

    @Test
    fun rearrange_crossWithRootInCenter() {
        val root = rearrangeRects(
            DisplayArrangement(150, 100, 160, 0f, 0f),
            DisplayArrangement(150, 100, 160, -150f, 0f),
            DisplayArrangement(150, 100, 160, 0f, -100f),
            DisplayArrangement(150, 100, 160, 150f, 0f),
            DisplayArrangement(150, 100, 160, 0f, 100f),
        )

        verifyDisplay(root, id = 0, width = 150, height = 100, density = 160, noOfChildren = 4)
        verifyDisplay(
                root.children[0], id = 1, width = 150, height = 100, density = 160, POSITION_LEFT,
                offset = 0f, noOfChildren = 0)
        verifyDisplay(
                root.children[1], id = 2, width = 150, height = 100, density = 160, POSITION_TOP,
                offset = 0f, noOfChildren = 0)
        verifyDisplay(
                root.children[2], id = 3, width = 150, height = 100, density = 160, POSITION_RIGHT,
                offset = 0f, noOfChildren = 0)
        verifyDisplay(
                root.children[3], id = 4, width = 150, height = 100, density = 160, POSITION_BOTTOM,
                offset = 0f, noOfChildren = 0)
    }

    @Test
    fun rearrange_elbowArrangement1() {
        val root = rearrangeRects(
            //     2
            //     |
            // 0 - 1

            DisplayArrangement(100, 100, 160, 0f, 0f),
            DisplayArrangement(100, 100, 160, 100f, 0f),
            DisplayArrangement(100, 100, 160, 100f, -100f),
        )

        verifyDisplay(root, id = 0, width = 100, height = 100, density = 160, noOfChildren = 2)
        var node = root.children[0]
        verifyDisplay(
                node, id = 1, width = 100, height = 100, density = 160, POSITION_RIGHT, offset = 0f,
                noOfChildren = 0)
        node = root.children[1]
        verifyDisplay(
                node, id = 2, width = 100, height = 100, density = 160, POSITION_RIGHT,
                offset = -100f, noOfChildren = 0)
    }

    @Test
    fun rearrange_elbowArrangement2() {
        val root = rearrangeRects(
            //     0
            //     |
            //     1
            //     |
            // 3 - 2

            DisplayArrangement(100, 100, 160, 0f, 0f),
            DisplayArrangement(100, 100, 160, 0f, 100f),
            DisplayArrangement(100, 100, 160, 0f, 200f),
            DisplayArrangement(100, 100, 160, -100f, 200f),
        )

        verifyDisplay(root, id = 0, width = 100, height = 100, density = 160, noOfChildren = 1)
        var node = root.children[0]
        verifyDisplay(
                node, id = 1, width = 100, height = 100, density = 160, POSITION_BOTTOM,
                offset = 0f, noOfChildren = 2)
        verifyDisplay(
                node.children[0], id = 2, width = 100, height = 100, density = 160, POSITION_BOTTOM,
                offset = 0f, noOfChildren = 0)
        verifyDisplay(
                node.children[1], id = 3, width = 100, height = 100, density = 160, POSITION_LEFT,
                offset = 100f, noOfChildren = 0)
    }

    @Test
    fun rearrange_rootHasFourDirectChildren() {
        val root = rearrangeRects(
            // 444111
            // 444111
            // 444111
            //   000222
            //   000222
            //   000222
            //     333
            //     333
            //     333
            DisplayArrangement(30, 30, 160, 20f, 30f),
            DisplayArrangement(30, 30, 160, 30f, 0f),
            DisplayArrangement(30, 30, 160, 50f, 30f),
            DisplayArrangement(30, 30, 160, 40f, 60f),
            DisplayArrangement(30, 30, 160, 0f, 0f),
        )

        verifyDisplay(root, id = 0, width = 30, height = 30, density = 160, noOfChildren = 4)
        verifyDisplay(
                root.children[0], id = 1, width = 30, height = 30, density = 160, POSITION_TOP,
                offset = 10f, noOfChildren = 0)
        verifyDisplay(
                root.children[1], id = 2, width = 30, height = 30, density = 160,
                POSITION_RIGHT, offset = 0f, noOfChildren = 0)
        verifyDisplay(
                root.children[2], id = 3, width = 30, height = 30, density = 160, POSITION_BOTTOM,
                offset = 20f, noOfChildren = 0)
        verifyDisplay(
                root.children[3], id = 4, width = 30, height = 30, density = 160,
                POSITION_TOP, offset = -20f, noOfChildren = 0)
    }

    @Test
    fun rearrange_closeGaps() {
        val root = rearrangeRects(
            // 000
            // 000 111
            // 000 111
            //     111
            //
            //         222
            //         222
            //         222
            DisplayArrangement(30, 30, 160, 0f, 0f),
            DisplayArrangement(30, 30, 160, 40f, 10f),
            DisplayArrangement(295, 300, 1600, 80.5f, 50f),
        )

        verifyDisplay(root, id = 0, width = 30, height = 30, density = 160, noOfChildren = 1)
        verifyDisplay(
                root.children[0], id = 1, width = 30, height = 30, density = 160, POSITION_RIGHT,
                offset = 10f, noOfChildren = 1)
        verifyDisplay(
                root.children[0].children[0], id = 2, width = 295, height = 300, density = 1600,
                POSITION_RIGHT, offset = 30f, noOfChildren = 0)
    }

    @Test
    fun rearrange_preferLessShiftInOverlapDimension1() {
        val root = rearrangeRects(
            // '*' represents overlap
            // 222
            // 22*00
            // 22*00
            //   0**1
            //    111
            //    111
            DisplayArrangement(30, 30, 160, 20f, 10f),
            DisplayArrangement(30, 30, 160, 30f, 30f),
            DisplayArrangement(30, 30, 160, 0f, 0f),

            // In the main body of DisplayTopology.rearrange, the parent/child relationship will
            // be 0 -> 1 -> 2, and the absolute positions will be:
            // 22*00
            // 22*00
            // 22*00
            //    111
            //    111
            //    111
            // Then normalize() will change this to 0 -> [1, 2] and positioned like this:
            // 222000
            // 222000
            // 222000
            //     111
            //     111
            //     111
        )

        verifyDisplay(root, id = 0, width = 30, height = 30, density = 160, noOfChildren = 2)
        verifyDisplay(
                root.children[0], id = 1, width = 30, height = 30, density = 160, POSITION_BOTTOM,
                offset = 10f, noOfChildren = 0)
        verifyDisplay(
                root.children[1], id = 2, width = 30, height = 30, density = 160, POSITION_LEFT,
                offset = 0f, noOfChildren = 0)
    }

    @Test
    fun rearrange_preferLessShiftInOverlapDimension2() {
        val root = rearrangeRects(
            // '*' represents overlap
            // Clamping requires moving display 2 and 1 slightly to avoid overlap with 0. We should
            // shift the minimal amount to avoid overlap - e.g. display 2 shifts left (10 pixels)
            // rather than up (20 pixels).
            // 222
            // 22*00000
            // 22*00000
            //   0000**1
            //       111
            //       111
            DisplayArrangement(60, 30, 160, 20f, 10f),
            DisplayArrangement(30, 30, 160, 60f, 30f),
            DisplayArrangement(30, 30, 160, 0f, 0f),
        )

        verifyDisplay(root, id = 0, width = 60, height = 30, density = 160, noOfChildren = 2)
        verifyDisplay(
                root.children[0], id = 1, width = 30, height = 30, density = 160, POSITION_BOTTOM,
                offset = 40f, noOfChildren = 0)
        verifyDisplay(
                root.children[1], id = 2, width = 30, height = 30, density = 160, POSITION_LEFT,
                offset = -10f, noOfChildren = 0)
    }

    @Test
    fun rearrange_doNotAttachCornerForShortOverlapOnLongEdgeBottom() {
        val root = rearrangeRects(
            DisplayArrangement(1920, 1080, 160, 0f, 0f),
            DisplayArrangement(1920, 1080, 160, 1850f, 1070f),
        )

        verifyDisplay(root, id = 0, width = 1920, height = 1080, density = 160, noOfChildren = 1)
        verifyDisplay(
                root.children[0], id = 1, width = 1920, height = 1080, density = 160,
                POSITION_BOTTOM, offset = 1850f, noOfChildren = 0)
    }

    @Test
    fun rearrange_doNotAttachCornerForShortOverlapOnLongEdgeLeft() {
        val root = rearrangeRects(
            DisplayArrangement(1080, 1920, 160, 0f, 0f),
            DisplayArrangement(1080, 1920, 160, -1070f, -1880f),
        )

        verifyDisplay(root, id = 0, width = 1080, height = 1920, density = 160, noOfChildren = 1)
        verifyDisplay(
                root.children[0], id = 1, width = 1080, height = 1920, density = 160, POSITION_LEFT,
                offset = -1880f, noOfChildren = 0)
    }

    @Test
    fun rearrange_preferLongHorizontalShiftOverAttachToCorner() {
        // An earlier implementation decided vertical or horizontal clamp direction based on the abs
        // value of the overlap in each dimension, rather than the raw overlap.

        // This horizontal span is twice the height of displays, making abs(xOverlap) > yOverlap,
        // i.e. abs(-60) > 30
        //      |
        //    |----|
        // 000      111
        // 000      111
        // 000      111

        // Before fix:
        // 000
        // 000
        // 000
        //    111
        //    111
        //    111

        // After fix:
        // 000111
        // 000111
        // 000111

        val root = rearrangeRects(
            DisplayArrangement(30, 30, 160, 0f, 0f),
            DisplayArrangement(30, 30, 160, 90f, 0f),
        )

        verifyDisplay(root, id = 0, width = 30, height = 30, density = 160, noOfChildren = 1)
        verifyDisplay(
                root.children[0], id = 1, width = 30, height = 30, density = 160, POSITION_RIGHT,
                offset = 0f, noOfChildren = 0)
    }

    @Test
    fun rearrange_preferLongVerticalShiftOverAttachToCorner() {
        // Before:
        // 111
        // 111
        // 111
        //        |
        //        |- This vertical span is 40dp
        //        |
        //        |
        //   000
        //   000
        //   000

        // After:
        // 111
        // 111
        // 111
        //   000
        //   000
        //   000

        val root = rearrangeRects(
            DisplayArrangement(30, 30, 160, 20f, 70f),
            DisplayArrangement(30, 30, 160, 0f, 0f),
        )

        verifyDisplay(root, id = 0, width = 30, height = 30, density = 160, noOfChildren = 1)
        verifyDisplay(
                root.children[0], id = 1, width = 30, height = 30, density = 160, POSITION_TOP,
                offset = -20f, noOfChildren = 0)
    }

    @Test
    fun rearrange_variousDensities() {
        // 111
        // 111000
        // 111000
        //     22
        //     22
        //     22

        val root = rearrangeRects(
            DisplayArrangement(60, 40, 320, 30f, 10f),
            DisplayArrangement(150, 150, 800, 0f, 0f),
            DisplayArrangement(60, 90, 480, 40f, 30f),
        )

        verifyDisplay(root, id = 0, width = 60, height = 40, density = 320, noOfChildren = 2)
        verifyDisplay(root.children[0], id = 1, width = 150, height = 150, density = 800,
                POSITION_LEFT, offset = -10f, noOfChildren = 0)
        verifyDisplay(root.children[1], id = 2, width = 60, height = 90, density = 480,
                POSITION_BOTTOM, offset = 10f, noOfChildren = 0)

        // Verify sizes in device-independent pixels.
        assertThat(root.width).isEqualTo(30f)
        assertThat(root.height).isEqualTo(20f)
        assertThat(root.children[0].width).isEqualTo(30f)
        assertThat(root.children[0].height).isEqualTo(30f)
        assertThat(root.children[1].width).isEqualTo(20f)
        assertThat(root.children[1].height).isEqualTo(30f)
    }

    @Test
    fun rearrange_useCornerAttachment() {
        // Verify that 0 and 2 do not clamp directly together:
        // 000   222
        // 000   222
        // 000   222
        //    111
        //    111
        //    111
        // (see b/394355269)

        val root = rearrangeRects(
            DisplayArrangement(30, 30, 160, 0f, 0f),
            DisplayArrangement(30, 30, 160, 30f, 30f),
            DisplayArrangement(30, 30, 160, 60f, 0f),
        )

        verifyDisplay(root, id = 0, width = 30, height = 30, density = 160, noOfChildren = 1)
        val dis1 = root.children[0]
        verifyDisplay(dis1, id = 1, width = 30, height = 30, density = 160,
                POSITION_RIGHT, offset = 30f, noOfChildren = 1)
        val dis2 = dis1.children[0]
        verifyDisplay(dis2, id = 2, width = 30, height = 30, density = 160,
                POSITION_RIGHT, offset = -30f, noOfChildren = 0)
    }

    @Test
    fun copy() {
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 160, /* position= */ 0,
            /* offset= */ 0f)

        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 190, POSITION_RIGHT, /* offset= */ 0f)
        display1.addChild(display2)

        val primaryDisplayId = 3
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 600,
            /* logicalHeight= */ 200, /* logicalDensity= */ 300, POSITION_RIGHT, /* offset= */ 400f)
        display1.addChild(display3)

        val display4 = DisplayTopology.TreeNode(/* displayId= */ 4, /* logicalWidth= */ 200,
            /* logicalHeight= */ 600, /* logicalDensity= */ 120, POSITION_RIGHT, /* offset= */ 0f)
        display2.addChild(display4)

        topology = DisplayTopology(display1, primaryDisplayId)
        val copy = topology.copy()

        assertThat(copy.primaryDisplayId).isEqualTo(primaryDisplayId)

        val actualDisplay1 = copy.root!!
        verifyDisplay(actualDisplay1, id = 1, width = 200, height = 600, density = 160,
            noOfChildren = 2)

        val actualDisplay2 = actualDisplay1.children[0]
        verifyDisplay(actualDisplay2, id = 2, width = 600, height = 200, density = 190,
            POSITION_RIGHT, offset = 0f, noOfChildren = 1)

        val actualDisplay3 = actualDisplay1.children[1]
        verifyDisplay(actualDisplay3, id = 3, width = 600, height = 200, density = 300,
            POSITION_RIGHT, offset = 400f, noOfChildren = 0)

        val actualDisplay4 = actualDisplay2.children[0]
        verifyDisplay(actualDisplay4, id = 4, width = 200, height = 600, density = 120,
            POSITION_RIGHT, offset = 0f, noOfChildren = 0)
    }

    @Test
    fun coordinates() {
        // 1122222244
        // 1122222244
        // 11      44
        // 11      44
        // 1133333344
        // 1133333344

        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 300,
            /* logicalHeight= */ 900, /* logicalDensity= */ 240, /* position= */ 0,
            /* offset= */ 0f)

        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 1200,
            /* logicalHeight= */ 400, /* logicalDensity= */ 320, POSITION_RIGHT, /* offset= */ 0f)
        display1.addChild(display2)

        val display3 = DisplayTopology.TreeNode(/* displayId= */ 3, /* logicalWidth= */ 750,
            /* logicalHeight= */ 250, /* logicalDensity= */ 200, POSITION_RIGHT, /* offset= */ 400f)
        display1.addChild(display3)

        val display4 = DisplayTopology.TreeNode(/* displayId= */ 4, /* logicalWidth= */ 150,
            /* logicalHeight= */ 450, /* logicalDensity= */ 120, POSITION_RIGHT, /* offset= */ 0f)
        display2.addChild(display4)

        topology = DisplayTopology(display1, /* primaryDisplayId= */ 1)
        val coords = topology.absoluteBounds

        val expectedCoords = SparseArray<RectF>()
        expectedCoords.append(1, RectF(0f, 0f, 200f, 600f))
        expectedCoords.append(2, RectF(200f, 0f, 800f, 200f))
        expectedCoords.append(3, RectF(200f, 400f, 800f, 600f))
        expectedCoords.append(4, RectF(800f, 0f, 1000f, 600f))
        assertThat(coords.contentEquals(expectedCoords)).isTrue()
    }

    @Test
    fun graph() {
        // 1122222244
        // 1122222244
        // 11      44
        // 11      44
        // 1133333344
        // 1133333344
        //        555
        //        555
        //        555

        val densityPerDisplay = SparseIntArray()

        val density1 = 100
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 125,
            /* logicalHeight= */ 375, density1, /* position= */ 0, /* offset= */ 0f)
        densityPerDisplay.append(1, density1)

        val density2 = 200
        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 750,
            /* logicalHeight= */ 250, density2, POSITION_RIGHT, /* offset= */ 0f)
        display1.addChild(display2)
        densityPerDisplay.append(2, density2)

        val density3 = 1500
        val primaryDisplayId = 3
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 5625,
            /* logicalHeight= */ 1875, density3, POSITION_RIGHT, /* offset= */ 400f)
        display1.addChild(display3)
        densityPerDisplay.append(3, density3)

        val density4 = 300
        val display4 = DisplayTopology.TreeNode(/* displayId= */ 4, /* logicalWidth= */ 375,
            /* logicalHeight= */ 1125, density4, POSITION_RIGHT, /* offset= */ 0f)
        display2.addChild(display4)
        densityPerDisplay.append(4, density4)

        val density5 = 600
        val display5 = DisplayTopology.TreeNode(/* displayId= */ 5, /* logicalWidth= */ 1125,
            /* logicalHeight= */ 1125, density5, POSITION_BOTTOM, /* offset= */ -100f)
        display4.addChild(display5)
        densityPerDisplay.append(5, density5)

        topology = DisplayTopology(display1, primaryDisplayId)
        val graph = topology.getGraph()
        val nodes = graph.displayNodes

        assertThat(graph.primaryDisplayId).isEqualTo(primaryDisplayId)
        assertThat((0 until nodes.size).map { i -> nodes.keyAt(i) }).containsExactly(1, 2, 3, 4, 5)
        for (i in 0 until nodes.size) {
            val node = nodes.valueAt(i)
            assertThat(node.density).isEqualTo(densityPerDisplay.get(node.displayId))
            when (node.displayId) {
                1 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(2), POSITION_RIGHT, /* offsetDp= */ 0f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_RIGHT, /* offsetDp= */ 400f
                    )
                )

                2 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(1), POSITION_LEFT, /* offsetDp= */ 0f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(4), POSITION_RIGHT, /* offsetDp= */ 0f
                    )
                )

                3 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(1), POSITION_LEFT, /* offsetDp= */ -400f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(4), POSITION_RIGHT, /* offsetDp= */ -400f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(5), POSITION_BOTTOM, /* offsetDp= */ 500f
                    )
                )

                4 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(2), POSITION_LEFT, /* offsetDp= */ 0f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_LEFT, /* offsetDp= */ 400f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(5), POSITION_BOTTOM, /* offsetDp= */ -100f
                    )
                )

                5 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_TOP, /* offsetDp= */ -500f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(4), POSITION_TOP, /* offsetDp= */ 100f
                    )
                )
            }
        }
    }

    @Test
    fun graph_corner() {
        // 1122244
        // 1122244
        // 1122244
        //   333
        // 55

        val densityPerDisplay = SparseIntArray()

        val density1 = 1000
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 1250,
            /* logicalHeight= */ 1875, density1, /* position= */ 0, /* offset= */ 0f)
        densityPerDisplay.append(1, density1)

        val density2 = 80
        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 150,
            /* logicalHeight= */ 150, density2, POSITION_RIGHT, /* offset= */ 0f)
        display1.addChild(display2)
        densityPerDisplay.append(2, density2)

        val primaryDisplayId = 3
        val density3 = 600
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 1125,
            /* logicalHeight= */ 375, density3, POSITION_BOTTOM, /* offset= */ 0f)
        display2.addChild(display3)
        densityPerDisplay.append(3, density3)

        val density4 = 600
        val display4 = DisplayTopology.TreeNode(/* displayId= */ 4, /* logicalWidth= */ 750,
            /* logicalHeight= */ 1125, density4, POSITION_RIGHT, /* offset= */ 0f)
        display2.addChild(display4)
        densityPerDisplay.append(4, density4)

        val density5 = 600
        val display5 = DisplayTopology.TreeNode(/* displayId= */ 5, /* logicalWidth= */ 750,
            /* logicalHeight= */ 375, density5, POSITION_BOTTOM, /* offset= */ -200f)
        display3.addChild(display5)
        densityPerDisplay.append(5, density5)

        topology = DisplayTopology(display1, primaryDisplayId)
        val graph = topology.getGraph()
        val nodes = graph.displayNodes

        assertThat(graph.primaryDisplayId).isEqualTo(primaryDisplayId)
        assertThat((0 until nodes.size).map { i -> nodes.keyAt(i) }).containsExactly(1, 2, 3, 4, 5)
        for (i in 0 until nodes.size) {
            val node = nodes.valueAt(i)
            assertThat(node.density).isEqualTo(densityPerDisplay.get(node.displayId))
            when (node.displayId) {
                1 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(2), POSITION_RIGHT, /* offsetDp= */ 0f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_RIGHT, /* offsetDp= */ 300f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_BOTTOM, /* offsetDp= */ 200f
                    )
                )

                2 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(1), POSITION_LEFT, /* offsetDp= */ 0f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_BOTTOM, /* offsetDp= */ 0f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(4), POSITION_RIGHT, /* offsetDp= */ 0f
                    )
                )

                3 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(1), POSITION_LEFT, /* offsetDp= */ -300f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(1), POSITION_TOP, /* offsetDp= */ -200f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(2), POSITION_TOP, /* offsetDp= */ 0f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(4), POSITION_RIGHT, /* offsetDp= */ -300f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(4), POSITION_TOP, /* offsetDp= */ 300f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(5), POSITION_LEFT, /* offsetDp= */ 100f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(5), POSITION_BOTTOM, /* offsetDp= */ -200f
                    )
                )

                4 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(2), POSITION_LEFT, /* offsetDp= */ 0f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_LEFT, /* offsetDp= */ 300f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_BOTTOM, /* offsetDp= */ -300f
                    )
                )

                5 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_TOP, /* offsetDp= */ 200f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_RIGHT, /* offsetDp= */ -100f
                    )
                )
            }
        }
    }

    @Test
    fun graph_smallGap() {
        // 11122
        // 11122
        // 11133
        // 11133

        // There is a gap between displays 2 and 3, small enough for them to still be adjacent.

        val densityPerDisplay = SparseIntArray()

        val density1 = 400
        val display1 = DisplayTopology.TreeNode(/* displayId= */ 1, /* logicalWidth= */ 750,
            /* logicalHeight= */ 1000, density1, /* position= */ 0, /* offset= */ 0f)
        densityPerDisplay.append(1, density1)

        val density2 = 200
        val display2 = DisplayTopology.TreeNode(/* displayId= */ 2, /* logicalWidth= */ 250,
            /* logicalHeight= */ 250, density2, POSITION_RIGHT, /* offset= */ -1f)
        display1.addChild(display2)
        densityPerDisplay.append(2, density2)

        val primaryDisplayId = 3
        val density3 = 120
        val display3 = DisplayTopology.TreeNode(primaryDisplayId, /* logicalWidth= */ 150,
            /* logicalHeight= */ 150, density3, POSITION_RIGHT, /* offset= */ 201f)
        display1.addChild(display3)
        densityPerDisplay.append(3, density3)

        topology = DisplayTopology(display1, primaryDisplayId)
        val graph = topology.getGraph()
        val nodes = graph.displayNodes

        assertThat(graph.primaryDisplayId).isEqualTo(primaryDisplayId)
        assertThat((0 until nodes.size).map { i -> nodes.keyAt(i) }).containsExactly(1, 2, 3)
        for (i in 0 until nodes.size) {
            val node = nodes.valueAt(i)
            assertThat(node.density).isEqualTo(densityPerDisplay.get(node.displayId))
            when (node.displayId) {
                1 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(2), POSITION_RIGHT, /* offsetDp= */ -1f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_RIGHT, /* offsetDp= */ 201f
                    )
                )

                2 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(1), POSITION_LEFT, /* offsetDp= */ 1f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(3), POSITION_BOTTOM, /* offsetDp= */ 0f
                    )
                )

                3 -> assertThat(node.adjacentEdges.toSet()).containsExactly(
                    DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(1), POSITION_LEFT, /* offsetDp= */ -201f
                    ), DisplayTopologyGraph.AdjacentEdge(
                        nodes.get(2), POSITION_TOP, /* offsetDp= */ 0f
                    )
                )
            }
        }
    }

    @Test
    fun allNodesIdMap_nullRoot() {
        assertThat(topology.allNodesIdMap()).isEmpty()
    }

    data class DisplayArrangement(val logicalWidth: Int, val logicalHeight: Int,
            val logicalDensity: Int, val arrangeX: Float, val arrangeY: Float)

    /**
     * Runs the rearrange algorithm and returns the resulting tree as a list of nodes, with the
     * root at index 0. The number of nodes is inferred from the number of positions passed.
     *
     * Returns the root node.
     */
    private fun rearrangeRects(vararg arrangements: DisplayArrangement): DisplayTopology.TreeNode {
        // Generates a linear sequence of nodes in order in the List from root to leaf,
        // left-to-right. IDs are ascending from 0 to count - 1.

        val nodes = arrangements.indices.map {
            DisplayTopology.TreeNode(it, arrangements[it].logicalWidth,
                    arrangements[it].logicalHeight, arrangements[it].logicalDensity, POSITION_RIGHT,
                    /* offset= */ 0f)
        }

        nodes.forEachIndexed { id, node ->
            if (id > 0) {
                nodes[id - 1].addChild(node)
            }
        }

        DisplayTopology(nodes[0], 0).rearrange(arrangements.indices.associateWith {
            PointF(arrangements[it].arrangeX, arrangements[it].arrangeY)
        })

        return nodes[0]
    }

    private fun verifyDisplay(display: DisplayTopology.TreeNode, id: Int, width: Int,
                              height: Int, density: Int,
                              @DisplayTopology.Position position: Int = 0,
                              offset: Float = 0f, noOfChildren: Int) {
        assertThat(display.displayId).isEqualTo(id)
        assertThat(display.logicalWidth).isEqualTo(width)
        assertThat(display.logicalHeight).isEqualTo(height)
        assertThat(display.logicalDensity).isEqualTo(density)
        assertThat(display.position).isEqualTo(position)
        assertThat(display.offset).isEqualTo(offset)
        assertThat(display.children).hasSize(noOfChildren)
    }
}
