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

package com.android.wm.shell.compatui.api.events

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [CompatUIEventService].
 *
 * Build/Install/Run: atest WMShellUnitTests:CompatUIEventServiceTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class CompatUIEventServiceTest : ShellTestCase() {

    private lateinit var mainExecutor: ShellExecutor

    private lateinit var backgroundExecutor: ShellExecutor

    private lateinit var consumerA: CompatUIEventConsumer<TestEventA>

    private lateinit var consumerB: CompatUIEventConsumer<TestEventB>

    private lateinit var consumerAAlternate: CompatUIEventConsumer<TestEventA>

    private lateinit var callbackConsumer: CompatUICallbackEventConsumer<TestEventA, String>

    private lateinit var callbackLambda: (String) -> Unit

    private val runnableCaptor = argumentCaptor<Runnable>()

    // --- SUT ---
    private lateinit var service: CompatUIEventService

    // --- Test Events ---
    private data class TestEventA(val id: Int) : CompatUIBaseEvent

    private data class TestEventB(val value: String) : CompatUIBaseEvent

    @Before
    fun setUp() {
        mainExecutor = mock<ShellExecutor>()
        backgroundExecutor = mock<ShellExecutor>()
        consumerA =
            mock<CompatUIEventConsumer<TestEventA>> {
                on { compatUIExecutionContext } doReturn CompatUIExecutionContext.MAIN_THREAD
            }
        consumerB =
            mock<CompatUIEventConsumer<TestEventB>> {
                on { compatUIExecutionContext } doReturn CompatUIExecutionContext.MAIN_THREAD
            }
        consumerAAlternate =
            mock<CompatUIEventConsumer<TestEventA>> {
                on { compatUIExecutionContext } doReturn CompatUIExecutionContext.MAIN_THREAD
            }
        callbackConsumer =
            mock<CompatUICallbackEventConsumer<TestEventA, String>> {
                on { compatUIExecutionContext } doReturn CompatUIExecutionContext.BACKGROUND
            }
        callbackLambda = mock<(String) -> Unit>()

        // Create the Service Under Test (SUT) with the mocked executors
        service = CompatUIEventService(mainExecutor, backgroundExecutor)
    }

    @Test
    fun postEvent_routesToMainExecutor_forMainThreadContext() {
        // Arrange
        val event = TestEventA(1)
        service.subscribe(TestEventA::class.java, consumerA)

        // Act
        service.postEvent(event)

        // Assert
        // Verify the main executor was called and the background one was not
        verify(mainExecutor).execute(any())
        verify(backgroundExecutor, never()).execute(any())
    }

    @Test
    fun postEvent_routesToBackgroundExecutor_forBackgroundContext() {
        // Arrange
        val event = TestEventA(1)
        whenever(consumerA.compatUIExecutionContext).thenReturn(CompatUIExecutionContext.BACKGROUND)
        service.subscribe(TestEventA::class.java, consumerA)

        // Act
        service.postEvent(event)

        // Assert
        // Verify the background executor was called and the main one was not
        verify(backgroundExecutor).execute(any())
        verify(mainExecutor, never()).execute(any())
    }

    @Test
    fun postEvent_consumerIsCalled_withCorrectEvent() {
        // Arrange
        val event = TestEventA(123)
        service.subscribe(TestEventA::class.java, consumerA)

        service.postEvent(event)

        // Assert
        // Capture the runnable that was passed to the executor
        verify(mainExecutor).execute(runnableCaptor.capture())
        // Manually run the captured runnable to simulate execution
        runnableCaptor.lastValue.run()

        // Verify the consumer's process method was called with the exact event object
        verify(consumerA).process(eq(event))
    }

    @Test
    fun postEvent_onlyCallsCorrectConsumer() {
        // Arrange
        val eventA = TestEventA(1)
        TestEventB("test")
        service.subscribe(TestEventA::class.java, consumerA)
        service.subscribe(TestEventB::class.java, consumerB)

        // Act
        service.postEvent(eventA)

        // Assert
        // Capture and run the runnable
        verify(mainExecutor).execute(runnableCaptor.capture())
        runnableCaptor.lastValue.run()

        // Verify consumerA was called, but consumerB was not
        verify(consumerA).process(eq(eventA))
        verify(consumerB, never()).process(any())
    }

    // Verifies subscription-replacement logic. We use MAIN_THREAD here,
    // as routing to both contexts is already verified in other tests.
    @Test
    fun subscribe_replacesExistingConsumer() {
        // Arrange
        val event = TestEventA(1)
        service.subscribe(TestEventA::class.java, consumerA)

        // Act
        // Subscribe a *different* consumer for the *same* event
        service.subscribe(TestEventA::class.java, consumerAAlternate)
        service.postEvent(event)

        // Assert
        // Capture and run the runnable
        verify(mainExecutor).execute(runnableCaptor.capture())
        runnableCaptor.lastValue.run()

        // Verify the *new* consumer was called, and the old one was not
        verify(consumerAAlternate).process(eq(event))
        verify(consumerA, never()).process(any())
    }

    @Test
    fun unsubscribe_removesConsumer() {
        // Arrange
        val event = TestEventA(1)
        service.subscribe(TestEventA::class.java, consumerA)

        // Act
        service.unsubscribe(TestEventA::class.java)
        service.postEvent(event)

        // Assert
        // Verify that *no* executor was called, because the consumer was removed
        verify(mainExecutor, never()).execute(any())
        verify(backgroundExecutor, never()).execute(any())
        // Verify the consumer was never called
        verify(consumerA, never()).process(any())
    }

    @Test
    fun postEvent_withNoConsumer_doesNotCrash() {
        // Arrange
        val event = TestEventA(1)
        // Note: We do *not* subscribe a consumer for TestEventA

        // Act
        // This should log a warning but not throw an exception
        service.postEvent(event)

        // Assert
        // Verify no executors were called
        verify(mainExecutor, never()).execute(any())
        verify(backgroundExecutor, never()).execute(any())
    }

    @Test
    fun postEvent_withCallback_executesAndReturnsResultOnMainThread() {
        // Arrange
        val event = TestEventA(999)
        val expectedResult = "Processed 999"

        // Setup the mock consumer to return a result
        whenever(callbackConsumer.process(event)).thenReturn(expectedResult)

        // Register the callback consumer
        service.subscribe(TestEventA::class.java, callbackConsumer)

        // Act
        // Post to BACKGROUND with a callback
        service.postEvent(event, callbackLambda)

        // Assert - Step 1: Verify background execution
        verify(backgroundExecutor).execute(runnableCaptor.capture())

        // Simulate background thread running
        runnableCaptor.lastValue.run()

        // Verify consumer was called
        verify(callbackConsumer).process(eq(event))

        // Assert - Step 2: Verify callback posted to Main Executor
        // Note: We capture again to get the NEW runnable posted to mainExecutor
        verify(mainExecutor).execute(runnableCaptor.capture())

        // Simulate main thread running
        runnableCaptor.lastValue.run()

        // Verify the callback lambda was invoked with the correct result
        verify(callbackLambda).invoke(expectedResult)
    }
}
