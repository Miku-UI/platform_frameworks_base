/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pipeline.shared.ui.composable

import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

private const val TEST_TAG = "testBox"
private const val CHILD_TAG = "childBox"

@SmallTest
@RunWith(AndroidJUnit4::class)
class ForwardDragAndSwipeToShadeRootViewTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    private lateinit var legacyView: View
    private lateinit var onDownCallback: (Offset, IntSize, Boolean) -> Unit
    private lateinit var motionEventCaptor: ArgumentCaptor<MotionEvent>

    private var touchSlop: Float = 5f

    @Before
    fun setUp() {
        legacyView = spy(View(context))
        onDownCallback = mock()
        motionEventCaptor = ArgumentCaptor.forClass(MotionEvent::class.java)
    }

    @Test
    fun swipeDown_eventNotConsumed_passesFalseToCallback() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .size(100.dp)
                        .forwardDragAndSwipeToShadeRootView(
                            view = legacyView,
                            touchSlop = touchSlop,
                            onDown = onDownCallback,
                        )
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            up()
        }
        composeTestRule.waitForIdle()

        // Verify that isConsumed is accurately reported as false
        verify(onDownCallback).invoke(any(), any(), eq(false))
    }

    @Test
    fun swipeDown_eventConsumedByChild_passesTrueToCallback() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .size(100.dp)
                        .forwardDragAndSwipeToShadeRootView(
                            view = legacyView,
                            touchSlop = touchSlop,
                            onDown = onDownCallback,
                        )
            ) {
                // A child that consumes the click event
                Box(
                    modifier =
                        Modifier.testTag(CHILD_TAG).size(50.dp).clickable { /* Consumes the event */
                        }
                )
            }
        }

        // Perform the touch on the child box
        composeTestRule.onNodeWithTag(CHILD_TAG).performTouchInput {
            down(center)
            up()
        }
        composeTestRule.waitForIdle()

        // Verify that isConsumed is accurately reported as true
        verify(onDownCallback).invoke(any(), any(), eq(true))
    }

    @Test
    fun click_doesNotDispatchToLegacyView() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .size(100.dp)
                        .forwardDragAndSwipeToShadeRootView(
                            view = legacyView,
                            touchSlop = touchSlop,
                            onDown = onDownCallback,
                        )
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            up()
        }
        composeTestRule.waitForIdle()

        verify(onDownCallback).invoke(any(), any(), eq(false))
        verify(legacyView, never()).dispatchTouchEvent(any())
    }

    @Test
    fun drag_lessThanSlop_doesNotDispatchToLegacyView() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .size(100.dp)
                        .forwardDragAndSwipeToShadeRootView(
                            view = legacyView,
                            touchSlop = touchSlop,
                            onDown = onDownCallback,
                        )
            )
        }
        val dragDistance = touchSlop / 2f

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, dragDistance))
            up()
        }
        composeTestRule.waitForIdle()

        verify(onDownCallback).invoke(any(), any(), eq(false))
        verify(legacyView, never()).dispatchTouchEvent(any())
    }

    @Test
    fun drag_exceedsSlop_dispatchesAllEventsToLegacyView() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.testTag("testBox")
                        .size(100.dp)
                        .forwardDragAndSwipeToShadeRootView(legacyView, touchSlop, onDownCallback)
            )
        }

        composeTestRule.onNodeWithTag("testBox").performTouchInput {
            down(center)
            moveBy(Offset(0f, touchSlop * 2f))
            moveBy(Offset(0f, 10f))
            up()
        }

        verify(onDownCallback).invoke(any(), any(), eq(false))
        verify(legacyView, atLeastOnce()).dispatchTouchEvent(motionEventCaptor.capture())

        val capturedEvents = motionEventCaptor.allValues

        assertThat(capturedEvents.size).isAtLeast(3)
        assertThat(capturedEvents.first().action).isEqualTo(MotionEvent.ACTION_DOWN)
        assertThat(capturedEvents.last().action).isEqualTo(MotionEvent.ACTION_UP)
        // Verify that a move actually happened between down and up.
        assertThat(capturedEvents.any { it.action == MotionEvent.ACTION_MOVE }).isTrue()
    }

    @Test
    fun drag_exceedsSlopWithMultipleSmallMoves_dispatchesAllEventsToLegacyView() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .size(100.dp)
                        .forwardDragAndSwipeToShadeRootView(
                            view = legacyView,
                            touchSlop = touchSlop,
                            onDown = onDownCallback,
                        )
            )
        }

        // Drag consists of 1 DOWN + 5 MOVE + 1 UP = 7 total events.
        val dragMoveSteps = 5
        val expectedEventCount = 1 + dragMoveSteps + 1
        val smallDragStep = touchSlop / 4f

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            down(center)
            // Move by small steps until we exceed touchSlop. The 5th move will cross the
            // threshold.
            repeat(dragMoveSteps) { moveBy(delta = Offset(0f, smallDragStep), delayMillis = 10) }
            up()
        }

        verify(onDownCallback).invoke(any(), any(), eq(false))
        // VERIFY that the exact number of events was dispatched. No more, no less.
        verify(legacyView, times(expectedEventCount))
            .dispatchTouchEvent(motionEventCaptor.capture())

        val capturedEvents = motionEventCaptor.allValues
        val capturedActions = capturedEvents.map { it.action }

        // ASSERT the entire sequence of actions is exactly what we expect.
        val expectedActions =
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
            )

        assertThat(capturedActions).isEqualTo(expectedActions)
    }

    @Test
    fun drag_multiTouch_doesNotStopWhenPrimaryPointerLifts() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .size(100.dp)
                        .forwardDragAndSwipeToShadeRootView(
                            view = legacyView,
                            touchSlop = touchSlop,
                            onDown = onDownCallback,
                        )
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG).performTouchInput {
            // 1. Put both fingers down.
            down(0, center)
            down(1, center + Offset(10f, 0f))

            // 2. Drag both fingers to exceed the touch slop.
            moveBy(0, Offset(0f, touchSlop * 2f))
            moveBy(1, Offset(0f, touchSlop * 2f))

            // 3. Lift the FIRST (primary) finger.
            up(0)

            // 4. Move the SECOND finger a bit more to prove we are still tracking.
            moveBy(1, Offset(0f, 10f))

            // 5. Lift the SECOND finger.
            up(1)
        }

        verify(legacyView, atLeastOnce()).dispatchTouchEvent(motionEventCaptor.capture())

        val capturedEvents = motionEventCaptor.allValues

        // We check actionMasked because multi-touch events use bitmasks
        // (e.g., ACTION_POINTER_DOWN, ACTION_POINTER_UP)
        val capturedActionsMasked = capturedEvents.map { it.actionMasked }

        // Assert that the gesture completed successfully.
        // Before the fix, the loop would break when pointer1 lifted,
        // meaning the final ACTION_UP for the gesture would never be dispatched.
        assertThat(capturedActionsMasked.last()).isEqualTo(MotionEvent.ACTION_UP)

        // Verify that a pointer was lifted mid-gesture, proving our multi-touch
        // scenario actually executed as expected.
        assertThat(capturedActionsMasked).contains(MotionEvent.ACTION_POINTER_UP)
    }
}
