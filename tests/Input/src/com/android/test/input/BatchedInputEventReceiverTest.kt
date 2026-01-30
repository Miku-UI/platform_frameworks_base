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

package com.android.test.input

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.BatchedInputEventReceiver
import android.view.InputChannel
import android.view.InputDevice
import android.view.InputDevice.SOURCE_MOUSE
import android.view.InputEvent
import android.view.MotionEvent
import com.android.cts.input.BlockingQueueEventVerifier
import com.android.cts.input.MotionEventBuilder
import com.android.cts.input.PointerBuilder
import com.android.cts.input.inputeventmatchers.withMotionAction
import java.util.concurrent.LinkedBlockingQueue
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Test

private fun getTestMouseMotionEvent(action: Int, eventTime: Long): MotionEvent {
    return MotionEventBuilder(action, InputDevice.SOURCE_MOUSE)
        .downTime(eventTime)
        .eventTime(eventTime)
        .pointer(PointerBuilder(/* id= */ 0, MotionEvent.TOOL_TYPE_MOUSE).x(0f).y(0f))
        .build()
}

private class TestBatchedInputEventReceiver(
    channel: InputChannel,
    looper: Looper,
    scheduler: BatchedInputEventReceiver.BatchedInputScheduler,
) : BatchedInputEventReceiver(channel, looper, scheduler) {
    // If set, event receiver will disable, and then immediately enable batching after handling
    // next pending batched input event - i.e. after scheduling batched input, but before the
    // scheduled batched input is actually consumed.
    var resetBatchingAfterNextPendingEvent = false

    // Keeps track of received input events.
    private val inputEvents = LinkedBlockingQueue<InputEvent>()
    private val verifier = BlockingQueueEventVerifier(inputEvents)

    override fun onBatchedInputEventPending(source: Int) {
        super.onBatchedInputEventPending(source)

        if (resetBatchingAfterNextPendingEvent) {
            resetBatchingAfterNextPendingEvent = false
            setBatchingEnabled(false)
            setBatchingEnabled(true)
        }
    }

    override fun onInputEvent(event: InputEvent) {
        when (event) {
            is MotionEvent -> inputEvents.put(MotionEvent.obtain(event))
            else -> throw Exception("Received $event is not  a motion")
        }
        finishInputEvent(event, true /*handled*/)
    }

    fun assertReceivedMotion(matcher: Matcher<MotionEvent>) {
        verifier.assertReceivedMotion(matcher)
    }
}

/**
 * Test implementation for interface used to schedule tasks to consume batched input events used by
 * `BatchedInputEventReceiver`. The implementation posts the task asynchronously to the provided
 * looper.
 */
private class TestBatchedInputScheduler(
    looper: Looper,
    var frameTimeMs: Long,
    val frameTimeIncrementMs: Long,
) : BatchedInputEventReceiver.BatchedInputScheduler {
    val handler = Handler(looper)

    override fun postCallback(callback: Runnable) {
        handler.post(callback)
    }

    override fun removeCallbacks(callback: Runnable) {
        handler.removeCallbacks(callback)
    }

    override fun getFrameTimeNanos(): Long {
        val frameTime = frameTimeMs * 1000000L
        frameTimeMs += frameTimeIncrementMs
        return frameTime
    }
}

class BatchedInputEventReceiverTest {
    companion object {
        private const val TAG = "BatchedInputEventReceiverTest"
    }

    private val channels = InputChannel.openInputChannelPair("TestChannel")
    private val handlerThread = HandlerThread("Process input events")
    private lateinit var sender: SpyInputEventSender
    private lateinit var batchedReceiver: TestBatchedInputEventReceiver

    @Before
    fun setUp() {
        handlerThread.start()

        val looper = handlerThread.getLooper()
        sender = SpyInputEventSender(channels[0], looper)
        batchedReceiver =
            TestBatchedInputEventReceiver(
                channels[1],
                looper,
                TestBatchedInputScheduler(looper, 1000L, 50L),
            )
    }

    @After
    fun tearDown() {
        handlerThread.quitSafely()
    }

    // Consumption of batched input events should continue if batching is disabled, and then enabled
    // again before running the task to consume pending events in response to batching getting
    // disabled.
    @Test
    fun testKeepConsumingEventsAfterBatchingRestart() {
        val seq = 12
        // Send ACTION_DOWN, and verify it gets handled by the receiver without batching.
        sender.sendInputEvent(seq, getTestMouseMotionEvent(MotionEvent.ACTION_DOWN, 900L))
        batchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))
        sender.assertReceivedFinishedSignal(seq, handled = true)

        batchedReceiver.resetBatchingAfterNextPendingEvent = true

        // Send hover move events, which will get batched.
        // The reveiver resets batching after receiving batched input notification for this event.
        sender.sendInputEvent(seq + 1, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 930L))
        sender.sendInputEvent(seq + 2, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 1010L))

        // Verify that the receiver consumed batched hover move event, even though batching was
        // restarted upon receiving first pending batched input events notification.
        batchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))
        sender.assertReceivedFinishedSignal(seq + 1, handled = true)
        batchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))
        sender.assertReceivedFinishedSignal(seq + 2, handled = true)

        // Send another hover move event, and verify it gets consumed in case where batching is not
        // restarted.
        sender.sendInputEvent(seq + 3, getTestMouseMotionEvent(MotionEvent.ACTION_MOVE, 1060L))
        batchedReceiver.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_MOVE))

        sender.assertReceivedFinishedSignal(seq + 3, handled = true)

        sender.dispose()
    }
}
