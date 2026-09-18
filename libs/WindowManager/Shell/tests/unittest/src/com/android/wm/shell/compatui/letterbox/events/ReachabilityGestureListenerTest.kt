/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox.events

import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.MotionEvent
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.suppliers.WindowContainerTransactionSupplier
import com.android.wm.shell.compatui.letterbox.animations.LetterboxAnimationHandler
import com.android.wm.shell.compatui.letterbox.asMode
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_MOVE_LETTERBOX_REACHABILITY
import java.util.function.Consumer
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * Tests for [ReachabilityGestureListener].
 *
 * Build/Install/Run: atest WMShellUnitTests:ReachabilityGestureListenerTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class ReachabilityGestureListenerTest : ShellTestCase() {

    @Test
    fun `Use relative coords for bounds check but raw coords for transition when flag enabled`() {
        runTestScenario { r ->
            // Task is offset by 500 in Y. Activity bounds are relative to task.
            // Touching at relative (50, 100), which is INSIDE the activity.
            // Raw coordinates are (50, 600).
            r.updateActivityBounds(Rect(0, 0, 100, 200))
            r.sendMotionEvent(x = 50f, y = 100f, rawX = 50f, rawY = 600f)

            // Should be ignored because the relative touch is inside the bounds.
            r.verifyReachabilityTransitionCreated(expected = false, 50, 600)
            r.verifyReachabilityTransitionStarted(expected = false)
            r.verifyEventIsHandled(expected = false)

            // Touching at relative (50, 300), which is OUTSIDE the activity.
            // Raw coordinates are (50, 800).
            r.sendMotionEvent(x = 50f, y = 300f, rawX = 50f, rawY = 800f)

            // Should be handled, and transition should use RAW coordinates (50, 800).
            r.verifyReachabilityTransitionCreated(expected = true, 50, 800)
            r.verifyReachabilityTransitionStarted(expected = true)
            r.verifyEventIsHandled(expected = true)
            r.verifyLetterboxInputSourceId()
        }
    }

    /** Runs a test scenario providing a Robot. */
    fun runTestScenario(consumer: Consumer<ReachabilityGestureListenerRobotTest>) {
        val robot = ReachabilityGestureListenerRobotTest()
        consumer.accept(robot)
    }

    class ReachabilityGestureListenerRobotTest(
        taskId: Int = TASK_ID,
        token: WindowContainerToken? = TOKEN,
    ) {

        companion object {
            @JvmStatic private val TASK_ID = 1

            @JvmStatic private val TOKEN = mock<WindowContainerToken>()
        }

        private val reachabilityListener: ReachabilityGestureListener
        private val transitions: Transitions
        private val animationHandler: LetterboxAnimationHandler
        private val wctSupplier: WindowContainerTransactionSupplier
        private val wct: WindowContainerTransaction
        private val letterboxState: LetterboxState
        private var eventHandled = false

        init {
            transitions = mock<Transitions>()
            animationHandler = mock<LetterboxAnimationHandler>()
            wctSupplier = mock<WindowContainerTransactionSupplier>()
            wct = mock<WindowContainerTransaction>()
            letterboxState = LetterboxState()
            doReturn(wct).`when`(wctSupplier).get()
            reachabilityListener =
                ReachabilityGestureListener(
                    taskId,
                    token,
                    transitions,
                    animationHandler,
                    wctSupplier,
                    letterboxState,
                )
        }

        fun updateActivityBounds(activityBounds: Rect) {
            reachabilityListener.updateActivityBounds(activityBounds)
        }

        fun sendMotionEvent(x: Float, y: Float, rawX: Float, rawY: Float) {
            val event =
                mock<MotionEvent> {
                    on { getX() } doReturn x
                    on { getY() } doReturn y
                    on { getRawX() } doReturn rawX
                    on { getRawY() } doReturn rawY
                }
            eventHandled = reachabilityListener.onDoubleTap(event)
        }

        fun verifyReachabilityTransitionCreated(
            expected: Boolean,
            x: Int,
            y: Int,
            taskId: Int = TASK_ID,
            token: WindowContainerToken? = TOKEN,
        ) {
            verify(wct, expected.asMode()).setReachabilityOffset(token!!, taskId, x, y)
        }

        fun verifyReachabilityTransitionStarted(expected: Boolean = true) {
            verify(transitions, expected.asMode())
                .startTransition(TRANSIT_MOVE_LETTERBOX_REACHABILITY, wct, animationHandler)
        }

        fun verifyEventIsHandled(expected: Boolean) {
            assertEquals(expected, eventHandled)
        }

        fun verifyLetterboxInputSourceId(expectedInputSourceId: Int = TASK_ID) {
            assertEquals(expectedInputSourceId, letterboxState.lastInputSourceId)
        }
    }
}
