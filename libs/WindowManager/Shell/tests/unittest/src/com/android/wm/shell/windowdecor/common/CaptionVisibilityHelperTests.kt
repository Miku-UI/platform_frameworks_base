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

package com.android.wm.shell.windowdecor.common

import android.app.WindowConfiguration
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableContext
import android.view.Display
import android.view.WindowManager
import androidx.test.filters.SmallTest
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.BubbleHelper
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.LockTaskChangeListener
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTaskWithBaseActivity
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createPinnedTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createSplitScreenTask
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import java.util.Optional
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * Tests for [CaptionVisibilityHelperTests].
 *
 * Build/Install/Run: atest WMShellUnitTests:CaptionVisibilityHelperTests
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class CaptionVisibilityHelperTests : ShellTestCase() {
    private val mockDisplayController = mock<DisplayController>()
    private val mockBubbleHelper = mock<BubbleHelper>()
    private val mockBubbleController =
        mock<BubbleController> { on { bubbleHelper } doReturn mockBubbleHelper }
    private val mockDisplay = mock<Display>()
    private val mockSplitScreenController = mock<SplitScreenController>()
    private val mockLockTaskChangeListener = mock<LockTaskChangeListener>()
    private val mockDesktopModeCompatPolicy = mock<DesktopModeCompatPolicy>()

    private val testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val testScope = TestScope(testDispatcher)
    private val desktopState = FakeDesktopState()

    private lateinit var captionVisibilityHelper: CaptionVisibilityHelper
    private lateinit var spyContext: TestableContext

    @Before
    fun setup() {
        whenever(mockDisplay.type).thenReturn(Display.TYPE_INTERNAL)
        whenever(mockDisplay.displayId).thenReturn(DEFAULT_DISPLAY_ID)
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY_ID] = true
        desktopState.canEnterDesktopMode = true
        whenever(mockDisplayController.getDisplay(anyInt())).thenReturn(mockDisplay)
        whenever(mockLockTaskChangeListener.isTaskLocked).thenReturn(false)
        whenever(mockDesktopModeCompatPolicy.shouldDisableDesktopEntryPoints(any()))
            .thenReturn(false)
        spyContext = spy(mContext)

        captionVisibilityHelper =
            CaptionVisibilityHelper(
                    displayController = mockDisplayController,
                    desktopModeCompatPolicy = mockDesktopModeCompatPolicy,
                    desktopState = desktopState,
                    bubbleController = Optional.of(mockBubbleController),
                    lockTaskChangeListener = mockLockTaskChangeListener,
                )
                .apply { splitScreenController = mockSplitScreenController }
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun shouldCreateCaption_lockTask_returnsFalse() {
        val task = createFullscreenTaskWithBaseActivity(DEFAULT_DISPLAY_ID)
        whenever(mockLockTaskChangeListener.isTaskLocked).thenReturn(true)

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun shouldCreateCaption_onKeyguardVisAndOccluded_returnsFalse() {
        val task = createFullscreenTaskWithBaseActivity(DEFAULT_DISPLAY_ID)

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = true,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun shouldCreateCaption_taskRootOrStage_returnsFalse() {
        val task = createSplitScreenTask()
        whenever(mockSplitScreenController.isTaskRootOrStageRoot(task.taskId)).thenReturn(true)

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    fun shouldCreateCaption_freeformTask_returnsTrue() {
        val task = createFreeformTask()

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertTrue(shouldCreateCaption)
    }

    @Test
    fun shouldCreateCaption_disableEntryPoints_returnsFalse() {
        val task = createFullscreenTaskWithBaseActivity(DEFAULT_DISPLAY_ID)
        whenever(mockDesktopModeCompatPolicy.shouldDisableDesktopEntryPoints(any()))
            .thenReturn(true)

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    fun shouldCreateCaption_smallScreens_returnsFalse() {
        // Set display to smaller than min display width to be considered a large display
        whenever(mockDisplay.minSizeDimensionDp)
            .thenReturn(WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP - 10f)
        val task = createFullscreenTaskWithBaseActivity(DEFAULT_DISPLAY_ID)
        desktopState.canEnterDesktopMode = false
        desktopState.overridesShowAppHandle = true

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    fun shouldCreateCaption_cannotEnterDesktopModeOrShowAppHandle_returnsFalse() {
        val task = createFullscreenTaskWithBaseActivity(DEFAULT_DISPLAY_ID)
        desktopState.overrideDesktopModeSupportPerDisplay[task.displayId] = false
        desktopState.overridesShowAppHandle = false

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun shouldCreateCaption_isWallpaperTask_returnsFalse() {
        val task =
            createFullscreenTaskWithBaseActivity(DEFAULT_DISPLAY_ID).apply {
                baseIntent =
                    Intent().apply {
                        component = DesktopWallpaperActivity.wallpaperActivityComponent
                    }
            }

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_ADD_WINDOW_DECORATION_TO_ALL_TASKS)
    fun shouldCreateCaption_isNonStandardActivity_returnsFalse() {
        val task =
            createFullscreenTaskWithBaseActivity(DEFAULT_DISPLAY_ID).apply {
                configuration.windowConfiguration.activityType =
                    WindowConfiguration.ACTIVITY_TYPE_UNDEFINED
            }

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    fun shouldCreateCaption_isPipTask_returnsFalse() {
        val task = createPinnedTask()

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    fun shouldCreateCaption_nonDefaultAndNonInternalDisplay_returnsFalse() {
        val task = createFreeformTask(DEFAULT_DISPLAY_ID)
        whenever(mockDisplay.type).thenReturn(Display.TYPE_OVERLAY)
        whenever(mockDisplayController.isDisplayInTopology(DEFAULT_DISPLAY_ID)).thenReturn(false)

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    @Test
    fun shouldCreateCaption_desktopModeNotSupportedOnDisplay_returnsFalse() {
        val task = createFreeformTask(DEFAULT_DISPLAY_ID)
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY_ID] = false

        val shouldCreateCaption =
            captionVisibilityHelper.shouldCreateCaption(
                taskInfo = task,
                isKeyguardVisAndOccluded = DEFAULT_KEYGUARD_VIS_AND_OCCLUDED,
            )

        assertFalse(shouldCreateCaption)
    }

    companion object {
        private const val DEFAULT_DISPLAY_ID: Int = 0
        private const val DEFAULT_KEYGUARD_VIS_AND_OCCLUDED = false
    }
}
