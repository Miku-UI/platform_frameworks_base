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
package com.android.server.accessibility.gestures

import android.content.Context
import android.os.SystemClock
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_UP
import android.view.accessibility.AccessibilityEvent
import com.android.server.accessibility.AccessibilityManagerService
import com.android.server.accessibility.EventStreamTransformation
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(AndroidTestingRunner::class)
class EventDispatcherTest {

    private val ams: AccessibilityManagerService = mock<AccessibilityManagerService>()
    private val receiver: EventStreamTransformation = mock<EventStreamTransformation>()

    private lateinit var state: TouchState
    private lateinit var dispatcher: EventDispatcher

    @get:Rule
    val mSetFlagsRule = SetFlagsRule()

    @Before
    fun setUp() {
        state = TouchState(DEFAULT_DISPLAY, ams)
        dispatcher = EventDispatcher(
            mock<Context>(),
            DEFAULT_DISPLAY,
            ams,
            receiver,
            state
        )
    }

    @Test
    fun populateAccessibilityEvent_returnsEventWithCorrectProperties() {
        val windowId = 1
        val displayId = 2
        val type = AccessibilityEvent.TYPE_TOUCH_INTERACTION_END

        ams.stub { on { activeWindowId } doReturn windowId }
        dispatcher.mDisplayId = displayId
        val event = dispatcher.populateAccessibilityEvent(type)

        assertThat(event.windowId).isEqualTo(windowId)
        assertThat(event.displayId).isEqualTo(displayId)
        assertThat(event.eventType).isEqualTo(type)
    }

    @Test
    fun sendMotionEvent_withDifferentAction_dispatchesEventWithNewAction() {
        val downTime = SystemClock.uptimeMillis()
        val downEvent = createMotionEvent(ACTION_DOWN, downTime, downTime)
        sendMotionEvent(downEvent, ACTION_HOVER_ENTER)

        val captor = argumentCaptor<MotionEvent>()
        verify(receiver).onMotionEvent(captor.capture(), isA<MotionEvent>(), anyInt())
        val captured: MotionEvent = captor.firstValue
        assertThat(captured.action).isEqualTo(ACTION_HOVER_ENTER)
    }

    @Test
    fun sendMotionEvent_withDownAction_preservesOriginalDownTime() {
        val downTime = SystemClock.uptimeMillis()
        val downEvent = createMotionEvent(ACTION_DOWN, downTime, downTime)
        sendMotionEvent(downEvent, ACTION_DOWN)

        val captor = argumentCaptor<MotionEvent>()
        verify(receiver).onMotionEvent(captor.capture(), isA<MotionEvent>(), anyInt())
        val captured: MotionEvent = captor.firstValue
        assertThat(captured.action).isEqualTo(ACTION_DOWN)
        assertThat(captured.downTime).isEqualTo(downTime)
    }

    @Test
    fun sendMotionEvent_forHoverEventAfterTouch_reusesPreviousDownTime() {
        // cache a downtime
        val downTime = SystemClock.uptimeMillis()
        val downEvent = createMotionEvent(ACTION_DOWN, downTime, downTime)
        val upEvent = createMotionEvent(ACTION_UP, downTime, downTime + 10)
        sendMotionEvent(downEvent, ACTION_DOWN)
        sendMotionEvent(upEvent, ACTION_UP)

        // use a different downtime for hover event
        val hoverEvent = createMotionEvent(ACTION_HOVER_ENTER, downTime + 20, downTime + 20)
        sendMotionEvent(hoverEvent, ACTION_HOVER_ENTER)

        val captor = argumentCaptor<MotionEvent>()
        verify(receiver, times(3)).onMotionEvent(captor.capture(), isA<MotionEvent>(), anyInt())
        val captured: MotionEvent = captor.lastValue
        assertThat(captured.action).isEqualTo(ACTION_HOVER_ENTER)
        assertThat(captured.downTime).isEqualTo(downTime)
    }

    @Test
    fun sendMotionEvent_forHoverEventWithNoPriorTouch_setsDownTimeToEventTime() {
        val downTime = SystemClock.uptimeMillis()
        val eventTime = downTime + 10
        val hoverEvent = createMotionEvent(ACTION_HOVER_ENTER, downTime, eventTime)
        sendMotionEvent(hoverEvent, ACTION_HOVER_ENTER)

        val captor = argumentCaptor<MotionEvent>()
        verify(receiver).onMotionEvent(captor.capture(), isA<MotionEvent>(), anyInt())
        val captured: MotionEvent = captor.lastValue
        assertThat(captured.action).isEqualTo(ACTION_HOVER_ENTER)
        assertThat(captured.downTime).isEqualTo(eventTime)
    }

    @Test
    fun sendUpForInjectedDownPointers_stateMismatch_sendsCorrectActionUp() {
        // [Scenario]: Braille Keyboard / TalkBack Drag Bug
        // Situation: The user has 2 fingers on screen (Prototype), but TalkBack
        // has filtered this down to 1 finger dragging (mState).

        // Setup mState: Simulate TalkBack injecting ONLY pointer 0 (The Drag)
        val downTime = SystemClock.uptimeMillis()
        val injectedEvent = createMultiPointerEvent(
            ACTION_DOWN, downTime, downTime, intArrayOf(0)
        )
        state.onInjectedMotionEvent(injectedEvent)

        // Setup prototype: Represents real hardware state (stuck pointer 1 + valid pointer 0)
        val prototype = createMultiPointerEvent(
            MotionEvent.ACTION_MOVE, downTime, downTime + 10, intArrayOf(0, 1)
        )
        state.onReceivedMotionEvent(prototype, prototype, 0)

        // Execute
        // When this runs, it will iterate through the Prototype (0, 1).
        // It should SKIP pointer 1 (not in state).
        // It should SEND pointer 0.
        // Crucially, it must force the event to be ACTION_UP with count=1.
        dispatcher.sendUpForInjectedDownPointers(prototype, 0)

        // Verify
        val captor = argumentCaptor<MotionEvent>()
        verify(receiver).onMotionEvent(captor.capture(), any(), anyInt())
        val sentEvent = captor.lastValue
        assertThat(sentEvent.actionMasked).isEqualTo(ACTION_UP)
        assertThat(sentEvent.pointerCount).isEqualTo(1)
        assertThat(sentEvent.getPointerId(0)).isEqualTo(0)
    }

    @Test
    fun sendUpForInjectedDownPointers_withFourPointers_sendsCorrectSequence() {
        // [Scenario]: Normal multi-touch lift
        // Verifies the loop behavior: 4 pointers down -> 3x ACTION_POINTER_UP -> 1x ACTION_UP

        val downTime = SystemClock.uptimeMillis()

        // Setup state: Inject pointers 0, 1, 2, 3
        var currentEvent = createMultiPointerEvent(ACTION_DOWN, downTime, downTime, intArrayOf(0))
        state.onInjectedMotionEvent(currentEvent)

        var action =
            (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) or MotionEvent.ACTION_POINTER_DOWN
        currentEvent = createMultiPointerEvent(action, downTime, downTime, intArrayOf(0, 1))
        state.onInjectedMotionEvent(currentEvent)

        action = (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) or MotionEvent.ACTION_POINTER_DOWN
        currentEvent = createMultiPointerEvent(action, downTime, downTime, intArrayOf(0, 1, 2))
        state.onInjectedMotionEvent(currentEvent)

        action = (3 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) or MotionEvent.ACTION_POINTER_DOWN
        currentEvent = createMultiPointerEvent(action, downTime, downTime, intArrayOf(0, 1, 2, 3))
        state.onInjectedMotionEvent(currentEvent)

        // Setup Prototype: All 4 pointers present
        val prototype = createMultiPointerEvent(
            MotionEvent.ACTION_MOVE, downTime, downTime + 100, intArrayOf(0, 1, 2, 3)
        )
        state.onReceivedMotionEvent(prototype, prototype, 0)

        val capturedEvents = mutableListOf<MotionEvent>()
        org.mockito.Mockito.doAnswer { invocation ->
            val event = invocation.getArgument(0) as MotionEvent
            capturedEvents.add(MotionEvent.obtain(event)) // Make a copy!
            null
        }.`when`(receiver).onMotionEvent(any(), any(), anyInt())

        // Execute
        dispatcher.sendUpForInjectedDownPointers(prototype, 0)

        // Verify
        assertThat(capturedEvents).hasSize(4)

        assertThat(capturedEvents[0].actionMasked).isEqualTo(MotionEvent.ACTION_POINTER_UP)
        assertThat(capturedEvents[0].pointerCount).isEqualTo(4)
        assertThat(capturedEvents[0].actionIndex).isEqualTo(0)

        assertThat(capturedEvents[1].actionMasked).isEqualTo(MotionEvent.ACTION_POINTER_UP)
        assertThat(capturedEvents[1].pointerCount).isEqualTo(3)
        assertThat(capturedEvents[1].getPointerId(0)).isEqualTo(1)

        assertThat(capturedEvents[2].actionMasked).isEqualTo(MotionEvent.ACTION_POINTER_UP)
        assertThat(capturedEvents[2].pointerCount).isEqualTo(2)
        assertThat(capturedEvents[2].getPointerId(0)).isEqualTo(2)

        assertThat(capturedEvents[3].actionMasked).isEqualTo(ACTION_UP)
        assertThat(capturedEvents[3].pointerCount).isEqualTo(1)
        assertThat(capturedEvents[3].getPointerId(0)).isEqualTo(3)

        capturedEvents.forEach { it.recycle() }
    }

    private fun createMultiPointerEvent(
        action: Int, downTime: Long, eventTime: Long, pointerIds: IntArray
    ): MotionEvent {
        val count = pointerIds.size
        val props = Array(count) { i ->
            MotionEvent.PointerProperties().apply {
                id = pointerIds[i]
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(count) { i ->
            MotionEvent.PointerCoords().apply {
                x = i * 100f + 100f
                y = i * 100f + 100f
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(
            downTime, eventTime, action, count, props, coords,
            0, 0, 1f, 1f, 0, 0, SOURCE_TOUCHSCREEN, DEFAULT_DISPLAY
        )
    }

    // Only work for 1 pointer events
    private fun sendMotionEvent(event: MotionEvent, action: Int) {
        val pointerId = event.getPointerId(event.actionIndex)
        val pointerIdBits = (1 shl pointerId)
        dispatcher.sendMotionEvent(event, action, event, pointerIdBits, 0)
    }

    private fun createMotionEvent(action: Int, downTime: Long, eventTime: Long): MotionEvent {
        val x = 1f
        val y = 2f
        val pressure = 3f
        val size = 1f
        val metaState = 0
        val xPrecision = 0f
        val yPrecision = 0f
        val edgeFlags = 0
        val deviceId = 0
        val source = SOURCE_TOUCHSCREEN
        val displayId = DEFAULT_DISPLAY
        return MotionEvent.obtain(
            downTime, eventTime, action, x, y, pressure, size, metaState,
            xPrecision, yPrecision, deviceId, edgeFlags, source, displayId
        )
    }
}
