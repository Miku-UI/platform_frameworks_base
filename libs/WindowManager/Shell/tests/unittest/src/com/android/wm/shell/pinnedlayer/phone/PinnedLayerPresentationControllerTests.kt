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

package com.android.wm.shell.pinnedlayer.phone

import android.app.TaskInfo
import android.app.WindowConfiguration
import android.content.res.Resources
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import android.util.DisplayMetrics
import android.view.Display
import android.window.TransitionRequestInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.`when` as whenever

/**
 * Unit tests against [PinnedLayerPresentationController]
 *
 * Build/Install/Run: atest WMShellUnitTests:PinnedLayerPresentationControllerTests
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE)
@RunWith(AndroidJUnit4::class)
class PinnedLayerPresentationControllerTests : ShellTestCase() {
    @Mock private lateinit var displayController: DisplayController
    @Mock private lateinit var displayLayout: DisplayLayout
    @Mock private lateinit var desktopState: DesktopState
    private lateinit var resources: Resources
    private lateinit var displayMetrics: DisplayMetrics

    private lateinit var controller: PinnedLayerPresentationController

    @Before
    fun setUp() {
        // Default display setup: 1080x1920, density 2.0, insets top=100 bottom=50
        // 10px padding for pinned window.
        // The center of available display area (excl. insets) is (540, 910).
        val testableResources = mContext.getOrCreateTestableResources()
        testableResources.addOverride(R.dimen.pinned_window_init_padding, 10)
        displayMetrics = DisplayMetrics()
        displayMetrics.density = 2.0f
        whenever(testableResources.resources.displayMetrics).thenReturn(displayMetrics)
        whenever(displayController.getDisplayLayout(Display.DEFAULT_DISPLAY))
            .thenReturn(displayLayout)
        whenever(displayController.getDisplayContext(Display.DEFAULT_DISPLAY)).thenReturn(context)
        whenever(displayLayout.width()).thenReturn(1080)
        whenever(displayLayout.height()).thenReturn(1920)
        whenever(displayLayout.stableInsets()).thenReturn(Rect(0, 100, 0, 50))
        whenever(displayLayout.getStableBounds(any())).thenAnswer {
            val outRect = it.getArgument<Rect>(0)
            outRect.set(0, 100, 1080, 1870) // incl. insets
        }
        whenever(desktopState.isDesktopModeSupportedOnDisplay(anyInt())).thenReturn(true)

        controller = PinnedLayerPresentationController(context, displayController, desktopState)
    }

    @Test
    fun getPinEntryDestinationBounds_taskWithinLimits_noScaling() {
        // minSize = 220dp * 2.0 = 440px
        // maxWidth = 1080 * 0.7 = 756px
        // maxHeight = 1920 * 0.7 = 1344px
        // Task size (500x600) is within these limits.
        val task = createTaskInfo(Rect(0, 0, 500, 600))

        val bounds = controller.getPinEntryDestinationBounds(task)

        // Expected position: bottom-right corner considering stable insets
        // right = 1080 - 0 (inset) - 10 (padding) = 1070
        // bottom = 1920 - 50 (inset) - 10 (padding) = 1860
        // left = 1070 - 500 = 570
        // top = 1860 - 600 = 1260
        val expected = Rect(570, 1260, 1070, 1860)
        assertThat(bounds).isEqualTo(expected)
    }

    @Test
    fun getPinEntryDestinationBounds_taskTooSmall_scalesUp() {
        // minSize = 220dp * 2.0 = 440px
        // Task size (200x300) is smaller than minSize.
        val task = createTaskInfo(Rect(0, 0, 200, 300))

        val bounds = controller.getPinEntryDestinationBounds(task)

        // w0=200, h0=300. minScale = max(440/200, 440/300) = 2.2
        // finalWidth = 200 * 2.2 = 440
        // finalHeight = 300 * 2.2 = 660
        // right = 1070, bottom = 1860
        // left = 1070 - 440 = 630
        // top = 1860 - 660 = 1200
        val expected = Rect(630, 1200, 1070, 1860)
        assertThat(bounds).isEqualTo(expected)
    }

    @Test
    fun getPinEntryDestinationBounds_taskTooLarge_scalesDown() {
        // maxWidth = 1080 * 0.7 = 756px
        // maxHeight = 1920 * 0.7 = 1344px
        // Task size (1000x1500) is larger than max size.
        val task = createTaskInfo(Rect(0, 0, 1000, 1500))

        val bounds = controller.getPinEntryDestinationBounds(task)

        // w0=1000, h0=1500. maxScale = min(756/1000, 1344/1500) = 0.756
        // finalWidth = 1000 * 0.756 = 756
        // finalHeight = 1500 * 0.756 = 1134
        // right = 1070, bottom = 1860
        // left = 1070 - 756 = 314
        // top = 1860 - 1134 = 726
        val expected = Rect(314, 726, 1070, 1860)
        assertThat(bounds).isEqualTo(expected)
    }

    @Test
    fun getPinEntryDestinationBounds_conflictingConstraints_prioritizesMaxSize() {
        // Setup a smaller display where min size in px is larger than max size in px
        whenever(displayLayout.width()).thenReturn(500)
        whenever(displayLayout.height()).thenReturn(800)
        whenever(displayLayout.stableInsets()).thenReturn(Rect()) // No insets

        // minSize = 220dp * 2.0 = 440px
        // maxWidth = 500 * 0.7 = 350px -> Conflict!
        // maxHeight = 800 * 0.7 = 560px
        val task = createTaskInfo(Rect(0, 0, 300, 400))

        val bounds = controller.getPinEntryDestinationBounds(task)

        // w0=300, h0=400
        // minScale = max(440/300, 440/400) = 1.466
        // maxScale = min(350/300, 560/400) = 1.166
        // minScale > maxScale, so scale should be maxScale.
        // finalWidth = 300 * 1.166... = 350
        // finalHeight = 400 * 1.166... = 466
        // right = 500 - 0 (inset) - 10 (padding) = 490
        // bottom = 800 - 0 (inset) - 10 (padding) = 790
        // left = 490 - 350 = 140
        // top = 790 - 466 = 324
        val expected = Rect(140, 324, 490, 790)
        assertThat(bounds).isEqualTo(expected)
    }

    @Test
    fun getPinEntryDestinationBounds_noDisplayLayout_returnsNull() {
        whenever(displayController.getDisplayLayout(Display.DEFAULT_DISPLAY)).thenReturn(null)
        val task = createTaskInfo(Rect(0, 0, 500, 600))

        val bounds = controller.getPinEntryDestinationBounds(task)

        assertThat(bounds).isNull()
    }

    @Test
    fun getPinEntryDestinationBounds_noContext_returnsNull() {
        whenever(displayController.getDisplayContext(Display.DEFAULT_DISPLAY)).thenReturn(null)
        val task = createTaskInfo(Rect(0, 0, 500, 600))

        val bounds = controller.getPinEntryDestinationBounds(task)

        assertThat(bounds).isNull()
    }

    @Test
    fun getPinEntryDestinationBounds_invalidTaskBounds_returnsNull() {
        val task = createTaskInfo(Rect(0, 0, 0, 100)) // Zero width

        val bounds = controller.getPinEntryDestinationBounds(task)

        assertThat(bounds).isNull()
    }

    @Test
    fun isTaskSupportedForPinning_supported() {
        val task =
            TestRunningTaskInfoBuilder()
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM)
                .build()

        assertThat(controller.isTaskSupportedForPinning(task)).isTrue()
    }

    @Test
    fun isTaskSupportedForPinning_notFreeform_returnsFalse() {
        val task =
            TestRunningTaskInfoBuilder()
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
                .build()

        assertThat(controller.isTaskSupportedForPinning(task)).isFalse()
    }

    @Test
    fun isTaskSupportedForPinning_desktopModeNotSupported_returnsFalse() {
        whenever(desktopState.isDesktopModeSupportedOnDisplay(anyInt())).thenReturn(false)
        val task =
            TestRunningTaskInfoBuilder()
                .setWindowingMode(WindowConfiguration.WINDOWING_MODE_FREEFORM)
                .build()

        assertThat(controller.isTaskSupportedForPinning(task)).isFalse()
    }

    @Test
    fun calculateNewTaskBounds_resize_fromAllQuadrants() {
        // Deliberately not using junit parameterized testing, for readability.
        data class TestCase(
            val name: String,
            val initialBounds: Rect,
            val requestedSize: Rect,
            val expectedBounds: Rect,
        )

        val testCases =
            listOf(
                // Shrinking cases
                TestCase(
                    name = "shrink, top-left quadrant",
                    // Shrink, closer edges (right, bottom) move.
                    initialBounds = Rect(0, 100, 500, 700),
                    requestedSize = Rect(0, 0, 450, 550),
                    expectedBounds = Rect(0, 100, 450, 650),
                ),
                TestCase(
                    name = "shrink, top-right quadrant",
                    // Shrink, closer edges (left, bottom) move.
                    initialBounds = Rect(580, 100, 1080, 700),
                    requestedSize = Rect(0, 0, 450, 550),
                    expectedBounds = Rect(630, 100, 1080, 650),
                ),
                TestCase(
                    name = "shrink, bottom-left quadrant",
                    // Shrink, closer edges (right, top) move.
                    initialBounds = Rect(0, 1000, 500, 1600),
                    requestedSize = Rect(0, 0, 450, 550),
                    expectedBounds = Rect(0, 1050, 450, 1600),
                ),
                TestCase(
                    name = "shrink, bottom-right quadrant",
                    // Shrink, closer edges (left, top) move.
                    initialBounds = Rect(580, 1000, 1080, 1600),
                    requestedSize = Rect(0, 0, 450, 550),
                    expectedBounds = Rect(630, 1050, 1080, 1600),
                ),
                // Expanding cases
                TestCase(
                    name = "expand, top-left quadrant",
                    // Expand, farther edges (left, top) move.
                    initialBounds = Rect(0, 100, 500, 700),
                    requestedSize = Rect(0, 0, 600, 700),
                    expectedBounds = Rect(0, 100, 600, 800),
                ),
                TestCase(
                    name = "expand, top-right quadrant",
                    // Expand, farther edges (right, top) move.
                    initialBounds = Rect(580, 100, 1080, 700),
                    requestedSize = Rect(0, 0, 600, 700),
                    expectedBounds = Rect(480, 100, 1080, 800),
                ),
                TestCase(
                    name = "expand, bottom-left quadrant",
                    // Expand, farther edges (left, bottom) move.
                    initialBounds = Rect(0, 1000, 500, 1600),
                    requestedSize = Rect(0, 0, 600, 700),
                    expectedBounds = Rect(0, 1000, 600, 1700),
                ),
                TestCase(
                    name = "expand, bottom-right quadrant",
                    // Expand, farther edges (right, bottom) move.
                    initialBounds = Rect(580, 1000, 1080, 1600),
                    requestedSize = Rect(0, 0, 600, 700),
                    expectedBounds = Rect(480, 1000, 1080, 1700),
                ),
            )

        testCases.forEachIndexed { i, testCase ->
            val task = createTaskInfo(testCase.initialBounds, taskId = i)
            val requestedLocation = requestLocation(testCase.requestedSize)

            val newBounds = controller.calculateNewTaskBounds(task, requestedLocation)

            assertWithMessage(testCase.name).that(newBounds).isEqualTo(testCase.expectedBounds)
        }
    }

    @Test
    fun calculateNewTaskBounds_resizeTooSmall_scalesUp() {
        val task = createTaskInfo(Rect(100, 200, 600, 800)) // 500x600
        val requestedBounds = Rect(0, 0, 200, 300)
        val requestedLocation = requestLocation(requestedBounds)

        val newBounds = controller.calculateNewTaskBounds(task, requestedLocation)

        // minSize is 440px. scale = max(440/200, 440/300) = 2.2 -> new size: 440x660.
        // Width shrinks (closer edge, right, moves), height expands (farther edge, top, moves).
        val expected = Rect(100, 140, 540, 800)
        assertThat(newBounds).isEqualTo(expected)
    }

    @Test
    fun calculateNewTaskBounds_resizeTooLarge_scalesDown() {
        val task = createTaskInfo(Rect(100, 200, 600, 800)) // 500x600
        val requestedBounds = Rect(0, 0, 1000, 1500)
        val requestedLocation = requestLocation(requestedBounds)

        val newBounds = controller.calculateNewTaskBounds(task, requestedLocation)

        // maxWidth=756, maxHeight=1344. scale=min(756/1000, 1344/1500) = 0.756 -> 756x1134.
        // Width and height expand, so farther edges (left, top) move. Then ensureNotOffscreen.
        val expected = Rect(0, 100, 756, 1234)
        assertThat(newBounds).isEqualTo(expected)
    }

    @Test
    fun calculateNewTaskBounds_resizeCausesOffscreen_movesOnscreen() {
        data class TestCase(
            val name: String,
            val initialBounds: Rect,
            val requestedSize: Rect,
            val expectedBounds: Rect,
        )
        val testCases =
            listOf(
                TestCase(
                    "resize wider, goes offscreen right",
                    Rect(500, 200, 1000, 800),
                    Rect(0, 0, 600, 600), // right edge would be 1100
                    Rect(480, 200, 1080, 800), // 1080 - 0 (inset) = 1080
                ),
                TestCase(
                    "resize taller, goes offscreen bottom",
                    Rect(100, 1300, 600, 1800),
                    Rect(0, 0, 500, 600), // bottom edge would be 1900
                    Rect(100, 1270, 600, 1870), // 1920 - 50 (inset) = 1870
                ),
                TestCase(
                    "resize wider, goes offscreen left",
                    Rect(50, 200, 550, 800),
                    Rect(0, 0, 600, 600), // left edge would be -50
                    Rect(0, 200, 600, 800),
                ),
                TestCase(
                    "resize taller, goes offscreen top",
                    Rect(100, 150, 600, 750),
                    Rect(0, 0, 500, 700), // top edge would be 50
                    Rect(100, 100, 600, 800), // top inset = 100
                ),
            )

        testCases.forEachIndexed { i, testCase ->
            val task = createTaskInfo(testCase.initialBounds, taskId = i)
            val requestedLocation = requestLocation(testCase.requestedSize)

            val newBounds = controller.calculateNewTaskBounds(task, requestedLocation)

            assertWithMessage(testCase.name).that(newBounds).isEqualTo(testCase.expectedBounds)
        }
    }

    @Test
    fun calculateNewTaskBounds_multipleResizes_maintainsOriginalBoundsForResize() {
        val task = createTaskInfo(Rect(100, 200, 600, 800)) // 500x600

        // First resize
        val requestedLocation1 = requestLocation(Rect(0, 0, 450, 550))
        val newBounds1 = controller.calculateNewTaskBounds(task, requestedLocation1)!!
        val expected1 = Rect(100, 200, 550, 750)
        assertThat(newBounds1).isEqualTo(expected1)

        // Create a new task info with the updated bounds for the second call
        val taskAfterResize = createTaskInfo(newBounds1, taskId = task.taskId)

        // Second resize. The resize should still be based on the original bounds.
        val requestedLocation2 = requestLocation(Rect(0, 0, 650, 850))
        val newBounds2 = controller.calculateNewTaskBounds(taskAfterResize, requestedLocation2)
        val expected2 = Rect(0, 100, 650, 950)
        assertThat(newBounds2).isEqualTo(expected2)
    }

    @Test
    fun calculateNewTaskBounds_multipleResizes_resizesBackToOriginalSize() {
        val task = createTaskInfo(Rect(100, 200, 600, 800)) // 500x600

        // First resize - expand offscreen
        val requestedLocation1 = requestLocation(Rect(0, 0, 650, 800))
        val newBounds1 = controller.calculateNewTaskBounds(task, requestedLocation1)!!
        val expected1 = Rect(0, 100, 650, 900)
        assertThat(newBounds1).isEqualTo(expected1)

        // Create a new task info with the updated bounds for the second call
        val taskAfterResize = createTaskInfo(newBounds1, taskId = task.taskId)

        // Second resize. The resize should still be based on the original bounds.
        val requestedLocation2 = requestLocation(Rect(0, 0, 500, 600))
        val newBounds2 = controller.calculateNewTaskBounds(taskAfterResize, requestedLocation2)
        val expected2 = Rect(100, 200, 600, 800)
        assertThat(newBounds2).isEqualTo(expected2)
    }

    @Test
    fun calculateNewTaskBounds_externalBoundsChange_resetsAnchor() {
        val task = createTaskInfo(Rect(100, 200, 600, 800))

        // First resize
        val requestedLocation1 = requestLocation(Rect(0, 0, 450, 550))
        controller.calculateNewTaskBounds(task, requestedLocation1)

        // Now, simulate an external change (e.g., user moved the window)
        val externallyMovedBounds = Rect(300, 400, 750, 950) // 450x550
        val taskAfterMove = createTaskInfo(externallyMovedBounds, taskId = task.taskId)

        // Second resize. Should be based on the new position.
        val requestedLocation2 = requestLocation(Rect(0, 0, 500, 700))
        val newBounds = controller.calculateNewTaskBounds(taskAfterMove, requestedLocation2)

        // Expanding, farther edges (left, top) should move; dx=50, dy=150
        val expected = Rect(250, 250, 750, 950)
        assertThat(newBounds).isEqualTo(expected)
    }

    @Test
    fun calculateNewTaskBounds_differentDisplay_returnsCurrentBounds() {
        val task = createTaskInfo(Rect(100, 200, 600, 800))
        val requestedBounds = Rect(0, 0, 450, 550)
        val requestedLocation =
            requestLocation(requestedBounds, displayId = Display.DEFAULT_DISPLAY + 1)

        val newBounds = controller.calculateNewTaskBounds(task, requestedLocation)

        assertThat(newBounds).isEqualTo(task.configuration.windowConfiguration.bounds)
    }

    private fun createTaskInfo(bounds: Rect, taskId: Int = 44): TaskInfo {
        return TestRunningTaskInfoBuilder()
            .setTaskId(taskId)
            .setDisplayId(Display.DEFAULT_DISPLAY)
            .setBounds(bounds)
            .build()
    }

    private fun requestLocation(bounds: Rect, displayId: Int = Display.DEFAULT_DISPLAY) =
        TransitionRequestInfo.RequestedLocation(displayId, bounds)
}
