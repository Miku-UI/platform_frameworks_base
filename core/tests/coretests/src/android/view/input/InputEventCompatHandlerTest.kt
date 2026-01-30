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

package android.view.input

import android.platform.test.annotations.Presubmit
import android.view.InputEventCompatProcessor
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [InputEventCompatHandler].
 */
@SmallTest
@Presubmit
class InputEventCompatHandlerTest {

    @Test
    fun processInputEventReturnsNullIfProcessorReturnsNull() {
        val mockProcessor = mock<InputEventCompatProcessor>()
        whenever(mockProcessor.processInputEventForCompatibility(any())).thenReturn(null)

        val handler = InputEventCompatHandler(mockProcessor, null)
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val result = handler.processInputEvent(event)

        assertThat(result).isNull()
        verify(mockProcessor).processInputEventForCompatibility(event)
    }

    @Test
    fun processInputEventReturnsEmptyListIfProcessorReturnsEmptyList() {
        val mockProcessor = mock<InputEventCompatProcessor>()
        whenever(mockProcessor.processInputEventForCompatibility(any())).thenReturn(emptyList())

        val handler = InputEventCompatHandler(mockProcessor, null)
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val result = handler.processInputEvent(event)

        assertThat(result).isEmpty()
        verify(mockProcessor).processInputEventForCompatibility(event)
    }

    @Test
    fun processInputEventReturnsEventsFromProcessor() {
        val mockProcessor = mock<InputEventCompatProcessor>()

        val event1 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val processedEvents = listOf(event1)
        whenever(mockProcessor.processInputEventForCompatibility(any())).thenReturn(processedEvents)

        val handler = InputEventCompatHandler(mockProcessor, null)
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val result = handler.processInputEvent(event)

        assertThat(result).isEqualTo(processedEvents)
        verify(mockProcessor).processInputEventForCompatibility(event)
    }

    @Test
    fun processInputEventReturnsEventsFromChain() {
        val mockProcessor1 = mock<InputEventCompatProcessor>()
        val mockProcessor2 = mock<InputEventCompatProcessor>()

        // Simulates the following compat rewriting:
        // originalEvent -- mockProcessor1 --> [event1, event2] -- mockProcessor2 --> [[event3], []]

        val event1 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val event2 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 10f, 10f, 0)
        val processedEvents1 = listOf(event1, event2)
        whenever(mockProcessor1.processInputEventForCompatibility(any()))
            .thenReturn(processedEvents1)

        val event3 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 10f, 10f, 0)
        val processedEvents2 = listOf(event3)
        whenever(mockProcessor2.processInputEventForCompatibility(event1))
            .thenReturn(processedEvents2)
        whenever(mockProcessor2.processInputEventForCompatibility(event2))
            .thenReturn(null)

        val chain =
            InputEventCompatHandler(mockProcessor1, InputEventCompatHandler(mockProcessor2, null))
        val originalEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val result = chain.processInputEvent(originalEvent)

        assertThat(result).containsExactly(event3, event2)

        verify(mockProcessor1).processInputEventForCompatibility(originalEvent)

        verify(mockProcessor2).processInputEventForCompatibility(event1)
        verify(mockProcessor2).processInputEventForCompatibility(event2)
    }

    @Test
    fun processInputEventReturnsEventsFromChainWhenNextProcessorDoesNothing() {
        val mockProcessor1 = mock<InputEventCompatProcessor>()
        val mockProcessor2 = mock<InputEventCompatProcessor>()

        // Simulates the following compat rewriting:
        // originalEvent -- mockProcessor1 --> [modifiedEvent] -- mockProcessor2 --> null

        val originalEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val modifiedEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 10f, 10f, 0)
        val processedEvents1 = listOf(modifiedEvent)
        whenever(mockProcessor1.processInputEventForCompatibility(any()))
            .thenReturn(processedEvents1)

        // The second processor returns null, indicating no modification
        whenever(mockProcessor2.processInputEventForCompatibility(any()))
            .thenReturn(null)

        val chain =
            InputEventCompatHandler(mockProcessor1, InputEventCompatHandler(mockProcessor2, null))
        val result = chain.processInputEvent(originalEvent)

        // The result should be the event returned by the first processor
        assertThat(result).isEqualTo(processedEvents1)

        verify(mockProcessor1).processInputEventForCompatibility(originalEvent)

        // The second processor should have been called with the modified event
        verify(mockProcessor2).processInputEventForCompatibility(modifiedEvent)
    }

    @Test
    fun processInputEventBeforeFinishReturnsModifiedEvent() {
        val mockProcessor = mock<InputEventCompatProcessor>()
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val modifiedEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)

        whenever(mockProcessor.processInputEventBeforeFinish(modifiedEvent))
            .thenReturn(event)

        val handler = InputEventCompatHandler(mockProcessor, null)
        val result = handler.processInputEventBeforeFinish(modifiedEvent)

        assertThat(result).isEqualTo(event)
        verify(mockProcessor).processInputEventBeforeFinish(modifiedEvent)
    }

    @Test
    fun processInputEventBeforeFinishReturnsNull() {
        val mockProcessor = mock<InputEventCompatProcessor>()
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        whenever(mockProcessor.processInputEventBeforeFinish(event)).thenReturn(null)

        val handler = InputEventCompatHandler(mockProcessor, null)
        val result = handler.processInputEventBeforeFinish(event)
        assertThat(result).isNull()
        verify(mockProcessor).processInputEventBeforeFinish(event)
    }

    @Test
    fun processInputEventBeforeFinishReturnsOriginalEventInChain() {
        val mockProcessor1 = mock<InputEventCompatProcessor>()
        val mockProcessor2 = mock<InputEventCompatProcessor>()
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        whenever(mockProcessor1.processInputEventBeforeFinish(event))
            .thenReturn(event)
        whenever(mockProcessor2.processInputEventBeforeFinish(event))
            .thenReturn(event)

        val chain =
            InputEventCompatHandler(mockProcessor1, InputEventCompatHandler(mockProcessor2, null))
        val result = chain.processInputEventBeforeFinish(event)

        assertThat(result).isEqualTo(event)
        verify(mockProcessor2).processInputEventBeforeFinish(event)
        verify(mockProcessor1).processInputEventBeforeFinish(event)
    }

    @Test
    fun processInputEventBeforeFinishReturnsModifiedEventInChain() {
        val mockProcessor1 = mock<InputEventCompatProcessor>()
        val mockProcessor2 = mock<InputEventCompatProcessor>()
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val modifiedEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 10f, 10f, 0)
        val furtherModifiedEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 20f, 20f, 0)

        // Simulates the following compat rewriting:
        // originalEvent -- mockProcessor1 --> modifiedEvent -- mockProcessor2 --> furtherModifiedEvent

        whenever(mockProcessor1.processInputEventBeforeFinish(modifiedEvent))
            .thenReturn(event)
        whenever(mockProcessor2.processInputEventBeforeFinish(furtherModifiedEvent))
            .thenReturn(modifiedEvent)

        val chain =
            InputEventCompatHandler(mockProcessor1, InputEventCompatHandler(mockProcessor2, null))
        val result = chain.processInputEventBeforeFinish(furtherModifiedEvent)

        assertThat(result).isEqualTo(event)
        verify(mockProcessor2).processInputEventBeforeFinish(furtherModifiedEvent)
        verify(mockProcessor1).processInputEventBeforeFinish(modifiedEvent)
    }

    @Test
    fun processInputEventBeforeFinishReturnsNullInChain() {
        val mockProcessor1 = mock<InputEventCompatProcessor>()
        val mockProcessor2 = mock<InputEventCompatProcessor>()
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        whenever(mockProcessor1.processInputEventBeforeFinish(any())).thenReturn(event) // Processor 1 does not discard
        whenever(mockProcessor2.processInputEventBeforeFinish(event)).thenReturn(null) // Processor 2 discards the event

        val chain =
            InputEventCompatHandler(mockProcessor1, InputEventCompatHandler(mockProcessor2, null))
        val result = chain.processInputEventBeforeFinish(event)

        assertThat(result).isNull()
        verify(mockProcessor2).processInputEventBeforeFinish(event)
        verify(mockProcessor1, never())
            .processInputEventBeforeFinish(any())
    }
}
