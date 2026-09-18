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
package com.android.wm.shell.back

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.IRemoteAnimationFinishedCallback
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.BackEvent
import android.window.BackMotionEvent
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags.FLAG_FIX_CROSS_ACTIVITY_BACK_ANIMATION_IN_BUBBLES
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.BubbleController
import java.util.Optional
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DefaultCrossActivityBackAnimationTest : ShellTestCase() {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Mock private lateinit var backAnimationBackground: BackAnimationBackground
    @Mock private lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var transaction: Transaction
    @Mock private lateinit var handler: Handler
    @Mock private lateinit var bubbleController: BubbleController
    @Mock private lateinit var iRemoteAnimationFinishedCallback: IRemoteAnimationFinishedCallback

    private lateinit var defaultCrossActivityBackAnimation: DefaultCrossActivityBackAnimation

    @Before
    @Throws(Exception::class)
    fun setUp() {
        whenever(transaction.setColor(any(), any())).thenReturn(transaction)
        whenever(transaction.setAlpha(any(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setCrop(any(), any())).thenReturn(transaction)
        whenever(transaction.setRelativeLayer(any(), any(), anyInt())).thenReturn(transaction)
        whenever(transaction.setCornerRadius(any(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setMatrix(any(), any(), any())).thenReturn(transaction)

        defaultCrossActivityBackAnimation =
            DefaultCrossActivityBackAnimation(
                context,
                backAnimationBackground,
                rootTaskDisplayAreaOrganizer,
                handler,
                Optional.of(bubbleController),
                transaction,
            )
        spy(defaultCrossActivityBackAnimation)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FIX_CROSS_ACTIVITY_BACK_ANIMATION_IN_BUBBLES)
    fun testBubbleCornerRadius() {
        val taskId = 123
        val bubbleCornerRadius = 24.5f
        whenever(bubbleController.hasStableBubbleForTask(taskId)).thenReturn(true)
        whenever(bubbleController.getBubbleCornerRadius(taskId)).thenReturn(bubbleCornerRadius)

        val closingTarget = createAnimationTarget(false, taskId)
        val enteringTarget = createAnimationTarget(true, taskId + 1)

        startAnimation(arrayOf(closingTarget, enteringTarget))

        verify(transaction, org.mockito.Mockito.times(2))
            .setCornerRadius(any(), eq(bubbleCornerRadius))
    }

    @Test
    @RequiresFlagsEnabled(FLAG_FIX_CROSS_ACTIVITY_BACK_ANIMATION_IN_BUBBLES)
    fun testFreeformCornerRadius() {
        val taskId = 123
        val freeformCornerRadius = 24.5f
        // Mock the resource value
        val freeformCornerRadiusInt = freeformCornerRadius.toInt()
        mContext
            .getOrCreateTestableResources()
            .addOverride(
                com.android.wm.shell.shared.R.dimen
                    .desktop_windowing_freeform_rounded_corner_radius,
                freeformCornerRadiusInt,
            )

        val closingTarget = createAnimationTarget(false, taskId, isFreeform = true)
        val enteringTarget = createAnimationTarget(true, taskId + 1)

        startAnimation(arrayOf(closingTarget, enteringTarget))

        verify(transaction, org.mockito.Mockito.times(2))
            .setCornerRadius(any(), eq(freeformCornerRadiusInt.toFloat()))
    }

    private fun startAnimation(targets: Array<RemoteAnimationTarget>) {
        defaultCrossActivityBackAnimation.prepareNextAnimation(null, 0)
        defaultCrossActivityBackAnimation.runner.runner.onAnimationStart(
            0,
            targets,
            null,
            null,
            iRemoteAnimationFinishedCallback,
        )
        defaultCrossActivityBackAnimation.runner.callback.onBackStarted(backMotionEventFrom(0f, 0f))
    }

    private fun backMotionEventFrom(touchX: Float, progress: Float) =
        BackMotionEvent(
            /* touchX = */ touchX,
            /* touchY = */ 0f,
            /* frameTime = */ 0,
            /* progress = */ progress,
            /* triggerBack = */ false,
            /* swipeEdge = */ BackEvent.EDGE_LEFT,
        )

    private fun createAnimationTarget(
        open: Boolean,
        taskId: Int,
        isFreeform: Boolean = false,
    ): RemoteAnimationTarget {
        val topWindowLeash = SurfaceControl.Builder().setName("FakeLeash").build()
        val taskInfo = RunningTaskInfo()
        taskInfo.taskId = taskId
        taskInfo.taskDescription = ActivityManager.TaskDescription()
        if (isFreeform) {
            taskInfo.configuration.windowConfiguration.windowingMode =
                WindowConfiguration.WINDOWING_MODE_FREEFORM
        }
        return RemoteAnimationTarget(
            taskId,
            if (open) RemoteAnimationTarget.MODE_OPENING else RemoteAnimationTarget.MODE_CLOSING,
            topWindowLeash,
            false,
            Rect(),
            Rect(),
            -1,
            Point(0, 0),
            Rect(0, 0, BOUND_SIZE, BOUND_SIZE),
            Rect(),
            WindowConfiguration(),
            true,
            null,
            null,
            taskInfo,
            false,
            -1,
        )
    }

    companion object {
        private const val BOUND_SIZE = 100
    }
}
