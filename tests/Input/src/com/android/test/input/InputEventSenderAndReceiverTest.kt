/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.view.InputChannel
import android.view.InputEvent
import android.view.InputEventReceiver
import android.view.KeyEvent
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private fun assertKeyEvent(expected: KeyEvent, received: KeyEvent) {
    assertEquals(expected.action, received.action)
    assertEquals(expected.deviceId, received.deviceId)
    assertEquals(expected.downTime, received.downTime)
    assertEquals(expected.eventTime, received.eventTime)
    assertEquals(expected.keyCode, received.keyCode)
    assertEquals(expected.scanCode, received.scanCode)
    assertEquals(expected.repeatCount, received.repeatCount)
    assertEquals(expected.metaState, received.metaState)
    assertEquals(expected.flags, received.flags)
    assertEquals(expected.source, received.source)
    assertEquals(expected.displayId, received.displayId)
}

private fun getTestKeyEvent(): KeyEvent {
    return KeyEvent(
        1 /*downTime*/,
        1 /*eventTime*/,
        KeyEvent.ACTION_DOWN,
        KeyEvent.KEYCODE_A,
        0, /*repeat*/
    )
}

private class CrashingInputEventReceiver(channel: InputChannel, looper: Looper) :
    InputEventReceiver(channel, looper) {
    override fun onInputEvent(event: InputEvent) {
        try {
            throw IllegalArgumentException("This receiver crashes when it receives input event")
        } finally {
            finishInputEvent(event, true /*handled*/)
        }
    }
}

/**
 * When this input receiver gets an input event, it will call "dispose" on itself. This could happen
 * when a window wants to close itself when the user presses Esc or clicks the "close" button, for
 * example.
 */
private class DisposingInputEventReceiver(channel: InputChannel, looper: Looper) :
    SpyInputEventReceiver(channel, looper) {
    override fun onInputEvent(event: InputEvent) {
        super.onInputEvent(event)
        dispose()
    }
}

class InputEventSenderAndReceiverTest {
    companion object {
        private const val TAG = "InputEventSenderAndReceiverTest"
    }

    private val mHandlerThread = HandlerThread("Process input events")
    private lateinit var mReceiver: SpyInputEventReceiver
    private lateinit var mSender: SpyInputEventSender

    @Before
    fun setUp() {
        val channels = InputChannel.openInputChannelPair("TestChannel")
        mHandlerThread.start()

        val looper = mHandlerThread.getLooper()
        mSender = SpyInputEventSender(channels[0], looper)
        mReceiver = SpyInputEventReceiver(channels[1], looper)
    }

    @After
    fun tearDown() {
        mHandlerThread.quitSafely()
    }

    @Test
    fun testSendAndReceiveKey() {
        val key = getTestKeyEvent()
        val seq = 10
        mSender.sendInputEvent(seq, key)

        // Check receiver
        mReceiver.assertReceivedKey(withKeyAction(key.getAction()))

        // Check sender
        mSender.assertReceivedFinishedSignal(seq, handled = true)
    }

    // The timeline case is slightly unusual because it goes from InputConsumer to InputPublisher.
    @Test
    fun testSendAndReceiveTimeline() {
        val sent =
            SpyInputEventSender.Timeline(inputEventId = 1, gpuCompletedTime = 2, presentTime = 3)
        mReceiver.reportTimeline(sent.inputEventId, sent.gpuCompletedTime, sent.presentTime)
        val received = mSender.getTimeline()
        assertEquals(sent, received)
    }

    // If an invalid timeline is sent, the channel should not get closed. We simply discard it and
    // move on.
    @Test
    fun testSendAndReceiveInvalidTimeline() {
        val sent =
            SpyInputEventSender.Timeline(inputEventId = 1, gpuCompletedTime = 3, presentTime = 2)
        mReceiver.reportTimeline(sent.inputEventId, sent.gpuCompletedTime, sent.presentTime)
        // This bad timeline will not be reported, but subsequent valid ones will be.
        val sent2 =
            SpyInputEventSender.Timeline(inputEventId = 2, gpuCompletedTime = 3, presentTime = 4)
        mReceiver.reportTimeline(sent2.inputEventId, sent2.gpuCompletedTime, sent2.presentTime)
        val received = mSender.getTimeline()
        assertEquals(sent2, received)
        mSender.assertNoEvents()
    }

    /**
     * If a receiver throws an exception during 'onInputEvent' execution, the 'finally' block still
     * completes, and therefore, finishInputEvent is called. Make sure that there's no crash in the
     * native layer in these circumstances. In this test, we are reusing the 'mHandlerThread', but
     * we are creating new sender and receiver.
     */
    @Test
    fun testCrashingReceiverDoesNotCrash() {
        val channels = InputChannel.openInputChannelPair("TestChannel2")
        val sender = SpyInputEventSender(channels[0], mHandlerThread.looper)

        // Need a separate thread for the receiver so that the sender can still get the response
        // after the receiver crashes
        val receiverThread = HandlerThread("Crash when input event comes in")
        receiverThread.start()
        val crashingReceiver = CrashingInputEventReceiver(channels[1], receiverThread.looper)
        receiverThread.setUncaughtExceptionHandler { thread, exception ->
            if (thread == receiverThread && exception is IllegalArgumentException) {
                // do nothing - this is the exception that we need to ignore
            } else {
                throw exception
            }
        }

        val key = getTestKeyEvent()
        val seq = 11
        sender.sendInputEvent(seq, key)
        sender.assertReceivedFinishedSignal(seq, handled = true)

        // Clean up
        sender.dispose()
        receiverThread.quitSafely()
    }

    /**
     * If a receiver calls "dispose", it should not receive any more events.
     *
     * In this test, we are reusing the 'mHandlerThread', but we are creating new sender and
     * receiver.
     *
     * The receiver calls "dispose" after it receives the first event. So if the sender sends more
     * events, the receiver shouldn't get any more because it will have already called "dispose"
     * after the first one.
     *
     * To reduce the flakiness in the test (so that it doesn't falsely pass), we only create the
     * receiver after we are done writing the events to the socket. This ensures that there's a
     * second event available for consumption after the first one is processed.
     */
    @Test
    fun testNoEventsAfterDispose() {
        val channels = InputChannel.openInputChannelPair("TestChannel2")
        val sender = SpyInputEventSender(channels[0], mHandlerThread.looper)
        val key = getTestKeyEvent()
        val seq = 11
        sender.sendInputEvent(seq, key)
        sender.sendInputEvent(seq + 1, key)
        sender.sendInputEvent(seq + 2, key)

        // Need a separate thread for the receiver so that the events can be processed in parallel
        val receiverThread = HandlerThread("Dispose when input event comes in")
        receiverThread.start()
        val disposingReceiver = DisposingInputEventReceiver(channels[1], receiverThread.looper)

        disposingReceiver.assertReceivedKey(withKeyCode(key.keyCode))
        // We can't safely check for the arrival of the "Finished" signal here. Since the receiver
        // closed the input channel, the finished event may or may not arrive.
        // See InputChannel_test for an explanation, and a smaller unit test that illustrates this.
        // No more events should be delivered because the receiver has disposed itself after the
        // first one.
        disposingReceiver.assertNoEvents()

        // Clean up
        sender.dispose()
        receiverThread.quitSafely()
    }

    /**
     * If a receiver calls "dispose" while processing an input event, this should not cause a crash.
     *
     * In this test, we are reusing the 'mHandlerThread', but we are creating new sender and
     * receiver.
     */
    @Test
    fun testDisposingReceiverDoesNotCrash() {
        val channels = InputChannel.openInputChannelPair("TestChannel2")
        val sender = SpyInputEventSender(channels[0], mHandlerThread.getLooper())

        // Need a separate thread for the receiver so that the sender can still get the response
        // after the receiver crashes
        val receiverThread = HandlerThread("Dispose when input event comes in")
        receiverThread.start()
        val disposingReceiver = DisposingInputEventReceiver(channels[1], receiverThread.getLooper())

        val key = getTestKeyEvent()
        val seq = 11
        sender.sendInputEvent(seq, key)
        // We can't check for sure whether the 'finish' event was received by the sender because
        // 'dispose' could have been processed sooner than the 'finish' call was completed, but the
        // key should definitely have been received.
        disposingReceiver.assertReceivedKey(withKeyCode(key.keyCode))

        // Clean up
        sender.dispose()
        receiverThread.quitSafely()
    }

    /** After 'dispose' is called, the receiver should not get any more events. */
    @Test
    fun testSendEventAfterDispose() {
        val key = getTestKeyEvent()
        val seq = 10
        mSender.sendInputEvent(seq, key)
        // Check receiver
        mReceiver.assertReceivedKey(withKeyCode(key.keyCode))
        mSender.assertReceivedFinishedSignal(seq, handled = true)

        // After the receiver is disposed, there should not be any more events.
        // Invoke the "dispose" call on the looper thread.
        val handler = Handler(mHandlerThread.looper)
        handler.runWithScissors(mReceiver::dispose, 0)

        val key2 = getTestKeyEvent()
        val seq2 = 11
        mSender.sendInputEvent(seq2, key2)
        mReceiver.assertNoEvents()
        mSender.assertNoEvents()
    }
}
