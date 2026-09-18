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

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.view.View
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.protolog.ProtoLog
import com.android.testing.wm.util.MockToken
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_ANYTHING
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyEnterBubbleTransaction
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewController
import com.android.wm.shell.taskview.TaskViewTaskController
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.io.PrintWriter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [BubbleExpandedView].
 *
 * Build/Install/Run:
 * - atest WMShellRobolectricTests:BubbleExpandedViewTest (on host)
 * - atest WMShellMultivalentTestsOnDevice:BubbleExpandedViewTest (on device)
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleExpandedViewTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val componentName = ComponentName(context, "TestClass")

    private val taskOrganizer = mock<ShellTaskOrganizer>()
    private val taskViewTaskToken: WindowContainerToken = MockToken.token()
    private var taskViewController = mock<TaskViewController>()
    private val taskViewTaskController =
        mock<TaskViewTaskController> {
            on { taskOrganizer } doReturn taskOrganizer
            on { taskToken } doReturn taskViewTaskToken
        }
    private val bubbleHelper = mock<BubbleHelper>()
    private val bubbleController =
        mock<BubbleController> { on { bubbleHelper } doReturn bubbleHelper }
    private val bubbleExpandedViewManager =
        mock<BubbleExpandedViewManager> { on { getBubbleHelper() } doReturn bubbleHelper }
    private lateinit var taskView: TaskView
    private lateinit var bubbleTaskView: BubbleTaskView
    private lateinit var expandedView: BubbleExpandedView
    private lateinit var pointerView: View

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()

        taskView = TaskView(context, taskViewController, taskViewTaskController)
        bubbleTaskView = BubbleTaskView(taskView, directExecutor(), bubbleController)

        // The view must be properly inflated for tests to pass.
        val view = TestableBubbleExpandedView(context)
        // onFinishInflate needs a pointer_view to be present.
        pointerView = createPointerView()
        view.addView(pointerView)
        view.onFinishInflate()

        expandedView = view
        expandedView.initialize(
            bubbleExpandedViewManager,
            mock<BubbleStackView>(),
            mock<BubblePositioner>(),
            false /* isOverflow */,
            bubbleTaskView,
        )
        // Default to animating state for existing tests.
        expandedView.setAnimating(true)
    }

    @Test
    fun getTaskId_onTaskCreated_returnsCorrectTaskId() {
        bubbleTaskView.listener.onTaskCreated(123 /* taskId */, componentName)

        assertThat(expandedView.taskId).isEqualTo(123)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_ANYTHING)
    fun onTaskCreated_appliesWctToEnterBubble() {
        bubbleTaskView.listener.onTaskCreated(123 /* taskId */, componentName)

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(taskOrganizer).applyTransaction(wctCaptor.capture())
        val wct = wctCaptor.lastValue
        verifyEnterBubbleTransaction(wct, taskViewTaskToken.asBinder(), isAppBubble = false)
    }

    @Test
    fun setContentVisibility_true_notAnimating_setsAlphaToOne() {
        // Pre-condition: not animating, and alpha is 0
        expandedView.setAnimating(false)
        taskView.alpha = 0f
        pointerView.alpha = 0f

        // Show the content
        expandedView.setContentVisibility(true)

        // Verify visibility is true and alpha is 1
        assertThat(expandedView.contentVisibility).isTrue()
        assertThat(taskView.alpha).isEqualTo(1f)
        assertThat(pointerView.alpha).isEqualTo(1f)
    }

    @Test
    fun setContentVisibility_false_notAnimating_setsAlphaToZero() {
        // Pre-condition: not animating, and alpha is 1
        expandedView.setAnimating(false)
        taskView.alpha = 1f
        pointerView.alpha = 1f

        // Hide the content
        expandedView.setContentVisibility(false)

        // Verify visibility is false and alpha is 0
        assertThat(expandedView.contentVisibility).isFalse()
        assertThat(taskView.alpha).isEqualTo(0f)
        assertThat(pointerView.alpha).isEqualTo(0f)
    }

    @Test
    fun setContentVisibility_true_isAnimating_alphaUnchanged() {
        // Pre-condition: is animating, and alpha is 0
        expandedView.setAnimating(true)
        taskView.alpha = 0f
        pointerView.alpha = 0f

        // Try to show the content
        expandedView.setContentVisibility(true)

        // Verify visibility is true, but alpha is unchanged because of animation
        assertThat(expandedView.contentVisibility).isTrue()
        assertThat(taskView.alpha).isEqualTo(0f)
        assertThat(pointerView.alpha).isEqualTo(0f)
    }

    @Test
    fun setContentVisibility_false_isAnimating_alphaUnchanged() {
        // Pre-condition: is animating, and alpha is 1
        expandedView.setAnimating(true)
        taskView.alpha = 1f
        pointerView.alpha = 1f

        // Try to hide the content
        expandedView.setContentVisibility(false)

        // Verify visibility is false, but alpha is unchanged because of animation
        assertThat(expandedView.contentVisibility).isFalse()
        assertThat(taskView.alpha).isEqualTo(1f)
        assertThat(pointerView.alpha).isEqualTo(1f)
    }

    @Test
    fun initialize_taskViewGone_taskViewSetVisible() {
        val localTaskView = TaskView(context, taskViewController, taskViewTaskController)
        localTaskView.visibility = View.GONE
        val localBubbleTv = BubbleTaskView(localTaskView, directExecutor(), bubbleController)

        // The view must be properly inflated for tests to pass.
        val localExpandedView = TestableBubbleExpandedView(context)
        localExpandedView.addView(createPointerView())
        localExpandedView.onFinishInflate()

        localExpandedView.initialize(
            bubbleExpandedViewManager,
            mock<BubbleStackView>(),
            mock<BubblePositioner>(),
            false /* isOverflow */,
            localBubbleTv,
        )

        assertThat(localTaskView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun initialize_taskViewHasClipBounds_clipBoundsSetToNull() {
        val localTaskView = TaskView(context, taskViewController, taskViewTaskController)
        localTaskView.clipBounds = Rect(0, 0, 100, 100)
        val localBubbleTv = BubbleTaskView(localTaskView, directExecutor(), bubbleController)

        val localExpandedView = TestableBubbleExpandedView(context)
        localExpandedView.addView(createPointerView())
        localExpandedView.onFinishInflate()

        localExpandedView.initialize(
            bubbleExpandedViewManager,
            mock<BubbleStackView>(),
            mock<BubblePositioner>(),
            false /* isOverflow */,
            localBubbleTv,
        )

        assertThat(localTaskView.clipBounds).isNull()
    }

    @Test
    fun cleanUpExpandedState_taskViewNotNull_taskViewVisibilityGoneAndNull() {
        assertThat(expandedView.taskView).isNotNull()
        assertThat(taskView.visibility).isEqualTo(View.VISIBLE)

        expandedView.cleanUpExpandedState()

        assertThat(expandedView.taskView).isNull()
        assertThat(taskView.visibility).isEqualTo(View.GONE)
    }

    private fun createPointerView(): View = View(context).apply { id = R.id.pointer_view }

    /** Testable subclass of [BubbleExpandedView] to expose protected methods for testing. */
    private class TestableBubbleExpandedView(context: Context) : BubbleExpandedView(context) {
        // Make onFinishInflate public so we can call it in the test.
        public override fun onFinishInflate() {
            super.onFinishInflate()
        }
    }
}
