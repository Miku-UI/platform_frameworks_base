/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.printspooler.stats

import android.os.Handler
import android.print.PrintAttributes
import android.print.PrintDocumentInfo
import android.print.PrintJobInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Semaphore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

// These are clear-box tests that primarily validate basic and concurrent interactions.

@RunWith(AndroidJUnit4::class)
open class StatsAsyncLoggerTest {
    val mStatsLogWrapper = mock<StatsLogWrapper>()
    val mHandler = mock<Handler>()
    val mSemaphore = mock<Semaphore>()

    @Before
    fun setup() {
        reset(mStatsLogWrapper)
        reset(mHandler)
        reset(mSemaphore)

        // Mocks should succeed by default
        whenever(mHandler.postAtTime(any(), any())).thenReturn(true)
        whenever(mSemaphore.tryAcquire()).thenReturn(true)

        // Don't add Stats.AsyncLogger.startLogging() here so we can test it.
    }

    @After
    fun teardown() {
        StatsAsyncLogger.stopLogging()
    }

    @Test
    fun printJobSuccessfullyLoggedTest() {
        val logWrapperInOrder = inOrder(mStatsLogWrapper)
        val handlerInOrder = inOrder(mHandler)
        val semaphoreInOrder = inOrder(mSemaphore)
        val timeCaptor = argumentCaptor<Long>()
        val runnableCaptor = argumentCaptor<Runnable>()

        StatsAsyncLogger.startLogging()
        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        StatsAsyncLogger.testSetHandler(mHandler)
        StatsAsyncLogger.testSetStatsLogWrapper(mStatsLogWrapper)

        // "foo" printer service: Generally arbitrary arguments focusing more on creating non-empty
        // lists.
        val printServiceFoo = 42
        val colorsMaskFoo = PrintAttributes.COLOR_MODE_COLOR
        val sizeFoo = PrintAttributes.MediaSize.NA_LETTER
        val duplexModeMaskFoo = PrintAttributes.DUPLEX_MODE_LONG_EDGE
        val resolutionFoo = PrintAttributes.Resolution("hello", "resolution", 123, 321)
        val docTypeFoo = PrintDocumentInfo.CONTENT_TYPE_DOCUMENT
        val savedPdfFoo = true
        val pageCount = 52
        val finalState = PrintJobInfo.STATE_COMPLETED
        assertThat(
                StatsAsyncLogger.PrintJob(
                    printServiceFoo,
                    finalState,
                    colorsMaskFoo,
                    sizeFoo,
                    resolutionFoo,
                    duplexModeMaskFoo,
                    docTypeFoo,
                    savedPdfFoo,
                    pageCount,
                )
            )
            .isTrue()

        // "bar" printer service: Generally arbitrary arguments focusing more on empty/default
        // values.
        val printServiceBar = 1337
        assertThat(
                StatsAsyncLogger.PrintJob(
                    printServiceBar,
                    PrintJobInfo.STATE_FAILED,
                    0,
                    null,
                    null,
                    0,
                    PrintDocumentInfo.CONTENT_TYPE_UNKNOWN,
                    false,
                    PrintDocumentInfo.PAGE_COUNT_UNKNOWN,
                )
            )
            .isTrue()

        handlerInOrder
            .verify(mHandler, times(2))
            .postAtTime(runnableCaptor.capture(), timeCaptor.capture())
        handlerInOrder.verifyNoMoreInteractions()

        // Validate delay args
        val firstTime = timeCaptor.firstValue
        val secondTime = timeCaptor.secondValue
        assertThat(secondTime - firstTime)
            .isAtLeast(StatsAsyncLogger.EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds)
        assertThat(secondTime - firstTime)
            .isAtMost(2 * StatsAsyncLogger.EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds)

        // Validate Runnable logic
        runnableCaptor.firstValue.run()
        runnableCaptor.secondValue.run()
        logWrapperInOrder
            .verify(mStatsLogWrapper)
            .internalPrintJob(
                printServiceFoo,
                StatsAsyncLogger.InternalFinalStatePrintJobEvent.COMPLETED,
                StatsAsyncLogger.InternalColorModePrintJobEvent.COLOR,
                StatsAsyncLogger.InternalDuplexModePrintJobEvent.LONG_EDGE,
                StatsAsyncLogger.InternalMediaSizePrintJobEvent.NA_LETTER,
                StatsAsyncLogger.InternalDocumentTypePrintJobEvent.DOCUMENT,
                StatsAsyncLogger.InternalOrientationPrintJobEvent.PORTRAIT,
                resolutionFoo.getHorizontalDpi(),
                resolutionFoo.getVerticalDpi(),
                savedPdfFoo,
                pageCount,
            )
        logWrapperInOrder
            .verify(mStatsLogWrapper)
            .internalPrintJob(
                printServiceBar,
                StatsAsyncLogger.InternalFinalStatePrintJobEvent.FAILED,
                StatsAsyncLogger.InternalColorModePrintJobEvent.UNSPECIFIED,
                StatsAsyncLogger.InternalDuplexModePrintJobEvent.UNSPECIFIED,
                StatsAsyncLogger.InternalMediaSizePrintJobEvent.UNSPECIFIED,
                StatsAsyncLogger.InternalDocumentTypePrintJobEvent.UNSPECIFIED,
                StatsAsyncLogger.InternalOrientationPrintJobEvent.UNSPECIFIED,
                0,
                0,
                false,
                PrintDocumentInfo.PAGE_COUNT_UNKNOWN,
            )
        logWrapperInOrder.verifyNoMoreInteractions()

        // Validate Semaphore logic
        semaphoreInOrder.verify(mSemaphore, times(2)).tryAcquire()
        semaphoreInOrder.verify(mSemaphore, times(2)).release()
    }

    @Test
    fun printerDiscoverySuccessfullyLoggedTest() {
        val logWrapperInOrder = inOrder(mStatsLogWrapper)
        val handlerInOrder = inOrder(mHandler)
        val semaphoreInOrder = inOrder(mSemaphore)
        val timeCaptor = argumentCaptor<Long>()
        val runnableCaptor = argumentCaptor<Runnable>()

        StatsAsyncLogger.startLogging()
        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        StatsAsyncLogger.testSetHandler(mHandler)
        StatsAsyncLogger.testSetStatsLogWrapper(mStatsLogWrapper)

        // "foo" printer service: Generally arbitrary arguments focusing more on creating non-empty
        // lists.
        val printServiceFoo = 42
        val colorsMaskFoo =
            (PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME)
        val supportedMediaSizesFoo =
            setOf<PrintAttributes.MediaSize>(
                PrintAttributes.MediaSize.NA_LETTER,
                PrintAttributes.MediaSize.JPN_HAGAKI,
            )
        val duplexModeMaskFoo =
            (PrintAttributes.DUPLEX_MODE_LONG_EDGE or PrintAttributes.DUPLEX_MODE_SHORT_EDGE)

        assertThat(
                StatsAsyncLogger.PrinterDiscovery(
                    printServiceFoo,
                    colorsMaskFoo,
                    duplexModeMaskFoo,
                    supportedMediaSizesFoo,
                )
            )
            .isTrue()

        // "bar" printer service: Generally arbitrary arguments focusing more on empty/default
        // values.
        val printServiceBar = 1337
        assertThat(StatsAsyncLogger.PrinterDiscovery(printServiceBar, 0, 0, setOf())).isTrue()

        handlerInOrder
            .verify(mHandler, times(2))
            .postAtTime(runnableCaptor.capture(), timeCaptor.capture())
        handlerInOrder.verifyNoMoreInteractions()

        // Validate delay args
        val firstTime = timeCaptor.firstValue
        val secondTime = timeCaptor.secondValue
        assertThat(secondTime - firstTime)
            .isAtLeast(StatsAsyncLogger.EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds)
        assertThat(secondTime - firstTime)
            .isAtMost(2 * StatsAsyncLogger.EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds)

        // Validate Runnable logic
        runnableCaptor.firstValue.run()
        runnableCaptor.secondValue.run()
        logWrapperInOrder
            .verify(mStatsLogWrapper)
            .internalPrinterDiscovery(
                printServiceFoo,
                setOf(
                    StatsAsyncLogger.InternalColorModePrinterDiscoveryEvent.COLOR,
                    StatsAsyncLogger.InternalColorModePrinterDiscoveryEvent.MONOCHROME,
                ),
                setOf(
                    StatsAsyncLogger.InternalMediaSizePrinterDiscoveryEvent.NA_LETTER,
                    StatsAsyncLogger.InternalMediaSizePrinterDiscoveryEvent.JPN_HAGAKI,
                ),
                setOf(
                    StatsAsyncLogger.InternalDuplexModePrinterDiscoveryEvent.LONG_EDGE,
                    StatsAsyncLogger.InternalDuplexModePrinterDiscoveryEvent.SHORT_EDGE,
                ),
            )
        logWrapperInOrder
            .verify(mStatsLogWrapper)
            .internalPrinterDiscovery(printServiceBar, setOf(), setOf(), setOf())
        logWrapperInOrder.verifyNoMoreInteractions()

        // Validate Semaphore logic
        semaphoreInOrder.verify(mSemaphore, times(2)).tryAcquire()
        semaphoreInOrder.verify(mSemaphore, times(2)).release()
    }

    @Test
    fun mainPrintUiLaunchedSuccessfullyLoggedTest() {
        val logWrapperInOrder = inOrder(mStatsLogWrapper)
        val handlerInOrder = inOrder(mHandler)
        val semaphoreInOrder = inOrder(mSemaphore)
        val timeCaptor = argumentCaptor<Long>()
        val runnableCaptor = argumentCaptor<Runnable>()

        StatsAsyncLogger.startLogging()
        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        StatsAsyncLogger.testSetHandler(mHandler)
        StatsAsyncLogger.testSetStatsLogWrapper(mStatsLogWrapper)

        // Arbitrary arguments
        assertThat(StatsAsyncLogger.MainPrintUiLaunched(setOf(1, 2, 3), 42)).isTrue()
        assertThat(StatsAsyncLogger.MainPrintUiLaunched(setOf(4, 5, 6), 1337)).isTrue()

        handlerInOrder
            .verify(mHandler, times(2))
            .postAtTime(runnableCaptor.capture(), timeCaptor.capture())
        handlerInOrder.verifyNoMoreInteractions()

        // Validate delay args
        val firstTime = timeCaptor.firstValue
        val secondTime = timeCaptor.secondValue
        assertThat(secondTime - firstTime)
            .isAtLeast(StatsAsyncLogger.EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds)
        assertThat(secondTime - firstTime)
            .isAtMost(2 * StatsAsyncLogger.EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds)

        // Validate Runnable logic
        runnableCaptor.firstValue.run()
        runnableCaptor.secondValue.run()
        logWrapperInOrder.verify(mStatsLogWrapper).internalMainPrintUiLaunched(setOf(1, 2, 3), 42)
        logWrapperInOrder.verify(mStatsLogWrapper).internalMainPrintUiLaunched(setOf(4, 5, 6), 1337)
        logWrapperInOrder.verifyNoMoreInteractions()

        // Validate Semaphore logic
        semaphoreInOrder.verify(mSemaphore, times(2)).tryAcquire()
        semaphoreInOrder.verify(mSemaphore, times(2)).release()
    }

    @Test
    fun advancedPrintUiLaunchedSuccessfullyLoggedTest() {
        val logWrapperInOrder = inOrder(mStatsLogWrapper)
        val handlerInOrder = inOrder(mHandler)
        val semaphoreInOrder = inOrder(mSemaphore)
        val timeCaptor = argumentCaptor<Long>()
        val runnableCaptor = argumentCaptor<Runnable>()

        StatsAsyncLogger.startLogging()
        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        StatsAsyncLogger.testSetHandler(mHandler)
        StatsAsyncLogger.testSetStatsLogWrapper(mStatsLogWrapper)

        // Arbitrary arguments
        assertThat(StatsAsyncLogger.AdvancedOptionsUiLaunched(42)).isTrue()
        assertThat(StatsAsyncLogger.AdvancedOptionsUiLaunched(1337)).isTrue()

        handlerInOrder
            .verify(mHandler, times(2))
            .postAtTime(runnableCaptor.capture(), timeCaptor.capture())
        handlerInOrder.verifyNoMoreInteractions()

        // Validate delay args
        val firstTime = timeCaptor.firstValue
        val secondTime = timeCaptor.secondValue
        assertThat(secondTime - firstTime)
            .isAtLeast(StatsAsyncLogger.EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds)
        assertThat(secondTime - firstTime)
            .isAtMost(2 * StatsAsyncLogger.EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds)

        // Validate Runnable logic
        runnableCaptor.firstValue.run()
        runnableCaptor.secondValue.run()
        logWrapperInOrder.verify(mStatsLogWrapper).internalAdvancedOptionsUiLaunched(42)
        logWrapperInOrder.verify(mStatsLogWrapper).internalAdvancedOptionsUiLaunched(1337)
        logWrapperInOrder.verifyNoMoreInteractions()

        // Validate Semaphore logic
        semaphoreInOrder.verify(mSemaphore, times(2)).tryAcquire()
        semaphoreInOrder.verify(mSemaphore, times(2)).release()
    }

    @Test
    fun failureToAcquireSemaphoreTicketNeverSchedulesEvent() {
        StatsAsyncLogger.startLogging()

        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        StatsAsyncLogger.testSetHandler(mHandler)
        whenever(mSemaphore.tryAcquire()).thenReturn(false)
        // Arbitrary Arguments
        assertThat(StatsAsyncLogger.AdvancedOptionsUiLaunched(42)).isFalse()
        assertThat(StatsAsyncLogger.MainPrintUiLaunched(setOf(1, 2, 3), 42)).isFalse()
        assertThat(StatsAsyncLogger.PrinterDiscovery(1337, 0, 0, setOf())).isFalse()
        verifyNoInteractions(mHandler)
    }

    @Test
    fun failureToScheduleReleasesSemaphoreTicket() {
        StatsAsyncLogger.startLogging()

        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        StatsAsyncLogger.testSetHandler(mHandler)
        whenever(mHandler.postAtTime(any(), any())).thenReturn(false)
        // Arbitrary Arguments
        assertThat(StatsAsyncLogger.AdvancedOptionsUiLaunched(42)).isFalse()
        assertThat(StatsAsyncLogger.MainPrintUiLaunched(setOf(1, 2, 3), 42)).isFalse()
        assertThat(StatsAsyncLogger.PrinterDiscovery(1337, 0, 0, setOf())).isFalse()
        assertThat(
                StatsAsyncLogger.PrintJob(
                    42,
                    PrintJobInfo.STATE_FAILED,
                    0,
                    null,
                    null,
                    0,
                    PrintDocumentInfo.CONTENT_TYPE_UNKNOWN,
                    false,
                    PrintDocumentInfo.PAGE_COUNT_UNKNOWN,
                )
            )
            .isFalse()
        verify(mSemaphore, times(4)).release()
    }

    @Test
    fun stopLoggingSucceedsAwaitingEvents() {
        StatsAsyncLogger.startLogging()

        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        whenever(mSemaphore.tryAcquire(any(), any(), any())).thenReturn(true)
        assertThat(StatsAsyncLogger.stopLogging()).isTrue()
    }

    @Test
    fun stopLoggingFailsAwaitingEvents() {
        StatsAsyncLogger.startLogging()

        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        whenever(mSemaphore.tryAcquire(any(), any(), any())).thenReturn(false)
        assertThat(StatsAsyncLogger.stopLogging()).isFalse()
    }

    @Test
    fun stopLoggingFailsToLog() {
        StatsAsyncLogger.startLogging()
        StatsAsyncLogger.stopLogging()

        StatsAsyncLogger.testSetSemaphore(mSemaphore)
        StatsAsyncLogger.testSetHandler(mHandler)
        StatsAsyncLogger.testSetStatsLogWrapper(mStatsLogWrapper)

        // Arbitrary Arguments
        assertThat(StatsAsyncLogger.AdvancedOptionsUiLaunched(42)).isFalse()
        assertThat(StatsAsyncLogger.MainPrintUiLaunched(setOf(1, 2, 3), 42)).isFalse()
        assertThat(StatsAsyncLogger.PrinterDiscovery(1337, 0, 0, setOf())).isFalse()
        assertThat(
                StatsAsyncLogger.PrintJob(
                    42,
                    PrintJobInfo.STATE_FAILED,
                    0,
                    null,
                    null,
                    0,
                    PrintDocumentInfo.CONTENT_TYPE_UNKNOWN,
                    false,
                    PrintDocumentInfo.PAGE_COUNT_UNKNOWN,
                )
            )
            .isFalse()
        verifyNoInteractions(mHandler)
        verifyNoInteractions(mSemaphore)
        verifyNoInteractions(mStatsLogWrapper)
    }

    @Test
    fun successiveStartLogging() {
        assertThat(StatsAsyncLogger.startLogging()).isTrue()
        assertThat(StatsAsyncLogger.startLogging()).isFalse()
    }

    @Test
    fun successiveStopLogging() {
        assertThat(StatsAsyncLogger.startLogging()).isTrue()

        assertThat(StatsAsyncLogger.stopLogging()).isTrue()
        assertThat(StatsAsyncLogger.stopLogging()).isFalse()
    }
}
