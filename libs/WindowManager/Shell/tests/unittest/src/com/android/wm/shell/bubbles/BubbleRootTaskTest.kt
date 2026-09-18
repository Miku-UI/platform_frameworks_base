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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.SurfaceControl
import android.window.TaskAppearedInfo
import android.window.TaskCreationParams
import android.window.TaskPropertiesRequest.REQUEST_NONE
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.shared.bubbles.FakeBubbleFeatureConfig
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * Unit tests for [BubbleRootTask].
 *
 * Build/Install/Run: atest WMShellUnitTests:BubbleRootTaskTest
 */
@SmallTest
class BubbleRootTaskTest : ShellTestCase() {

    private val shellInit = mock<ShellInit>()
    private val taskOrganizer = mock<ShellTaskOrganizer>()
    private val bubbleFeatureConfig = FakeBubbleFeatureConfig()
    private lateinit var bubbleRootTask: BubbleRootTask

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @DisableFlags(FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun init_flagDisabled_doNothing() {
        bubbleRootTask = BubbleRootTask(mContext, shellInit, taskOrganizer, bubbleFeatureConfig)
        verify(shellInit, never()).addInitCallback<BubbleRootTask>(any(), any())
        verify(taskOrganizer, never()).createTask(any())
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun init_createRootTask() {
        bubbleRootTask = BubbleRootTask(mContext, shellInit, taskOrganizer, bubbleFeatureConfig)
        val initCallback =
            argumentCaptor<Runnable>().let { initCallbackCaptor ->
                verify(shellInit)
                    .addInitCallback<BubbleRootTask>(initCallbackCaptor.capture(), any())
                initCallbackCaptor.firstValue
            }
        verify(taskOrganizer, never()).createTask(any())

        initCallback.run()

        val rootTaskParams =
            argumentCaptor<TaskCreationParams>().let { paramsCaptor ->
                verify(taskOrganizer).createTask(paramsCaptor.capture(), eq(bubbleRootTask))
                paramsCaptor.firstValue
            }

        assertThat(rootTaskParams.windowingMode).isEqualTo(WINDOWING_MODE_MULTI_WINDOW)
        val rootTaskProperties = rootTaskParams.taskPropertiesRequest
        assertThat(rootTaskProperties.isIgnoreInsets).isTrue()
        assertThat(rootTaskProperties.isDisableAppCompatRoundedCorners).isTrue()
        assertThat(rootTaskProperties.isReparentLeafTaskIfRelaunchFromHome).isTrue()
        assertThat(rootTaskProperties.isDisallowOverrideWindowingModeForChildren).isTrue()
        assertThat(rootTaskProperties.isPreserveLeafTaskIfRelaunch).isTrue()
        assertThat(rootTaskProperties.isInterceptBackPressedOnTaskRoot).isTrue()
        assertThat(rootTaskProperties.isTaskForceExcludedFromRecents).isTrue()
        assertThat(rootTaskProperties.isDisablePip).isTrue()
        assertThat(rootTaskProperties.isDisableLaunchAdjacent).isTrue()
        assertThat(rootTaskProperties.isForceTranslucent).isTrue()
        assertThat(rootTaskProperties.isForceLeafTasksNonOccluding).isTrue()

        val token = MockToken.token()
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123, bubbleRootToken = token)

        verify(taskOrganizer, never()).applyTransaction(any())
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun onTaskInfoChanged_updatesRootTask() {
        bubbleRootTask = BubbleRootTask(mContext, shellInit, taskOrganizer, bubbleFeatureConfig)
        val initCallbackCaptor = argumentCaptor<Runnable>()
        verify(shellInit).addInitCallback<BubbleRootTask>(initCallbackCaptor.capture(), any())
        initCallbackCaptor.firstValue.run()

        val token = MockToken.token()
        bubbleRootTask.prepareRootTaskForTest(bubbleRootTaskId = 123, bubbleRootToken = token)

        assertThat(bubbleRootTask.taskId).isEqualTo(123)

        val updatedTaskInfo =
            ActivityManager.RunningTaskInfo().apply {
                taskId = 456
                this.token = token
            }
        bubbleRootTask.onTaskInfoChanged(updatedTaskInfo)

        assertThat(bubbleRootTask.taskId).isEqualTo(456)
    }

    @Test
    fun onTaskAppeared_addVisibilityBarrier() {
        bubbleRootTask = BubbleRootTask(mContext, shellInit, taskOrganizer, bubbleFeatureConfig)
        val rootTaskToken = MockToken.token()
        val visibilityBarrierToken = MockToken.token()
        val visibilityBarrierTask =
            ActivityManager.RunningTaskInfo().apply {
                taskId = 11
                token = visibilityBarrierToken
            }
        taskOrganizer.stub {
            on { createTask(any(), any()) } doReturn TaskAppearedInfo(visibilityBarrierTask, mock())
        }
        bubbleRootTask.prepareRootTaskForTest(
            bubbleRootTaskId = 123,
            bubbleRootToken = rootTaskToken,
        )

        assertThat(bubbleRootTask.visibilityBarrierToken).isEqualTo(visibilityBarrierToken)
        val visibilityBarrierParams =
            argumentCaptor<TaskCreationParams>().let { paramsCaptor ->
                verify(taskOrganizer).createTask(paramsCaptor.capture(), eq(bubbleRootTask))
                paramsCaptor.firstValue
            }
        assertThat(visibilityBarrierParams.parentContainer).isEqualTo(rootTaskToken)
        assertThat(visibilityBarrierParams.isVisibilityBarrier).isTrue()
        assertThat(visibilityBarrierParams.taskPropertiesRequest.requestMask)
            .isEqualTo(REQUEST_NONE)
    }

    @Test
    fun visibilityBarrier_onTaskAppearedOrChanged_doesNotAffectRootTask() {
        bubbleRootTask = BubbleRootTask(mContext, shellInit, taskOrganizer, bubbleFeatureConfig)
        val rootTaskToken = MockToken.token()
        val visibilityBarrierToken = MockToken.token()
        val visibilityBarrierTaskInfo =
            ActivityManager.RunningTaskInfo().apply {
                taskId = 456
                token = visibilityBarrierToken
            }
        taskOrganizer.stub {
            on { createTask(any(), any()) } doReturn
                TaskAppearedInfo(visibilityBarrierTaskInfo, mock())
        }
        bubbleRootTask.prepareRootTaskForTest(
            bubbleRootTaskId = 123,
            bubbleRootToken = rootTaskToken,
        )

        bubbleRootTask.onTaskAppeared(visibilityBarrierTaskInfo, mock<SurfaceControl>())

        assertThat(bubbleRootTask.windowContainerToken).isEqualTo(rootTaskToken)
        assertThat(bubbleRootTask.taskId).isEqualTo(123)

        bubbleRootTask.onTaskInfoChanged(visibilityBarrierTaskInfo)

        assertThat(bubbleRootTask.windowContainerToken).isEqualTo(rootTaskToken)
        assertThat(bubbleRootTask.taskId).isEqualTo(123)
    }

    @Test
    fun appBubblesUnsupported_shouldNotCreateRootTask() {
        bubbleFeatureConfig.areAppBubblesSupported = false
        bubbleRootTask = BubbleRootTask(mContext, shellInit, taskOrganizer, bubbleFeatureConfig)
        verify(shellInit, never()).addInitCallback<BubbleRootTask>(any(), any())
        verify(taskOrganizer, never()).createTask(any())
    }

    companion object {
        /**
         * Test helper to prepare the [BubbleRootTask] instance for testing.
         *
         * @param bubbleRootTaskId the task ID to assign to the mock root task.
         * @param bubbleRootToken the [WindowContainerToken] to assign to the mock root task,
         *   defaults to a new [MockToken] if not provided.
         */
        fun BubbleRootTask.prepareRootTaskForTest(
            bubbleRootTaskId: Int,
            bubbleRootToken: WindowContainerToken = MockToken.token(),
        ) {
            val bubbleRootTaskInfo =
                ActivityManager.RunningTaskInfo().apply {
                    taskId = bubbleRootTaskId
                    token = bubbleRootToken
                }
            onTaskAppeared(bubbleRootTaskInfo, mock<SurfaceControl>())
        }
    }
}
