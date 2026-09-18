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

package com.android.wm.shell.bubbles.util

import android.app.ActivityManager
import android.graphics.Rect
import android.os.Binder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleHelper
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyEnterBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyExitBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleUtils.getEnterBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleUtils.getExitBubbleTransaction
import com.android.wm.shell.bubbles.util.BubbleUtils.getTaskTokenForBoundsUpdate
import com.android.wm.shell.taskview.TaskView
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Unit tests for [BubbleUtils].
 *
 * Build/Install/Run: atest WMShellUnitTests:BubbleUtilsTest
 */
@SmallTest
@RunWith(TestParameterInjector::class)
class BubbleUtilsTest : ShellTestCase() {

    private val binder = Binder()
    private val token = mock<WindowContainerToken> { on { asBinder() } doReturn binder }
    private val rootTaskToken = mock<WindowContainerToken> { on { asBinder() } doReturn binder }
    private val bubbleHelper =
        mock<BubbleHelper> { on { getAppBubbleRootTaskToken() } doReturn rootTaskToken }
    private val bounds = Rect(0, 0, 100, 100)
    private val captionInsetsOwner = Binder()

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @DisableFlags(FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun testGetEnterBubbleTransaction(@TestParameter isAppBubble: Boolean) {
        bubbleHelper.stub { on { getAppBubbleRootTaskToken() } doReturn null }
        val wctWithLaunchNextToBubble =
            getEnterBubbleTransaction(bubbleHelper, token, bounds, isAppBubble)

        verifyEnterBubbleTransaction(wctWithLaunchNextToBubble, token.asBinder(), isAppBubble)
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @DisableFlags(FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun testGetEnterBubbleTransaction_reparentToTda(@TestParameter isAppBubble: Boolean) {
        bubbleHelper.stub { on { getAppBubbleRootTaskToken() } doReturn null }
        val wctWithLaunchNextToBubble =
            getEnterBubbleTransaction(
                bubbleHelper,
                token,
                bounds,
                isAppBubble,
                reparentToTda = true,
            )

        verifyEnterBubbleTransaction(
            wctWithLaunchNextToBubble,
            token.asBinder(),
            isAppBubble,
            reparentToTda = true,
        )
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ENABLE_BUBBLE_ROOT_TASK)
    @Test
    fun testRootBubbleGetEnterBubbleTransaction() {
        val isAppBubble = true
        val wct = getEnterBubbleTransaction(bubbleHelper, token, bounds, isAppBubble)

        verifyEnterBubbleTransaction(
            wct,
            token.asBinder(),
            isAppBubble,
            rootTaskToken = rootTaskToken.asBinder(),
        )
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun testGetExitBubbleTransaction() {
        val wct = getExitBubbleTransaction(bubbleHelper, token, captionInsetsOwner)

        verifyExitBubbleTransaction(wct, token.asBinder(), captionInsetsOwner)
    }

    @Test
    fun getTaskTokenForBoundsUpdate_nullTaskView_shouldReturnNull() {
        val bubble = mock<Bubble> { on { taskView } doReturn null }
        assertThat(bubble.getTaskTokenForBoundsUpdate(bubbleHelper)).isNull()
    }

    @Test
    fun getTaskTokenForBoundsUpdate_nullTaskInfo_shouldReturnNull() {
        val tv = mock<TaskView> { on { taskInfo } doReturn null }
        val bubble = mock<Bubble> { on { taskView } doReturn tv }
        assertThat(bubble.getTaskTokenForBoundsUpdate(bubbleHelper)).isNull()
    }

    @Test
    fun getTaskTokenForBoundsUpdate_appBubble_shouldReturnRootTaskToken() {
        val info = ActivityManager.RunningTaskInfo()
        val tv = mock<TaskView> { on { taskInfo } doReturn info }
        val bubble = mock<Bubble> { on { taskView } doReturn tv }
        bubbleHelper.stub { on { isAppBubbleTask(info) } doReturn true }

        assertThat(bubble.getTaskTokenForBoundsUpdate(bubbleHelper)).isEqualTo(rootTaskToken)
    }

    @Test
    fun getTaskTokenForBoundsUpdate_appBubble_rootTaskNull_shouldReturnTaskToken() {
        val info = ActivityManager.RunningTaskInfo().apply { token = MockToken().token() }
        val tv = mock<TaskView> { on { taskInfo } doReturn info }
        val bubble = mock<Bubble> { on { taskView } doReturn tv }
        bubbleHelper.stub {
            on { isAppBubbleTask(info) } doReturn true
            on { getAppBubbleRootTaskToken() } doReturn null
        }

        assertThat(bubble.getTaskTokenForBoundsUpdate(bubbleHelper)).isEqualTo(info.token)
    }

    @Test
    fun getTaskTokenForBoundsUpdate_chatBubble_shouldReturnTaskToken() {
        val info = ActivityManager.RunningTaskInfo().apply { token = MockToken().token() }
        val tv = mock<TaskView> { on { taskInfo } doReturn info }
        val bubble = mock<Bubble> { on { taskView } doReturn tv }
        bubbleHelper.stub { on { isAppBubbleTask(info) } doReturn false }

        assertThat(bubble.getTaskTokenForBoundsUpdate(bubbleHelper)).isEqualTo(info.token)
    }
}
