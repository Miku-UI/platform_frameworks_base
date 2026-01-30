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

import android.annotation.UserIdInt
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.print.PrintAttributes
import android.print.PrintDocumentInfo
import android.print.PrintJobInfo
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * @hide
 */
object StatsAsyncLogger {
    private val TAG = StatsAsyncLogger::class.java.simpleName
    private val DEBUG = false

    @VisibleForTesting val EVENT_REPORTED_MIN_INTERVAL: Duration = 10.milliseconds
    @VisibleForTesting val MAX_EVENT_QUEUE = 150

    private var semaphore = Semaphore(MAX_EVENT_QUEUE)
    private lateinit var handlerThread: HandlerThread
    private lateinit var eventHandler: Handler
    private var nextAvailableTimeMillis = SystemClock.uptimeMillis()
    private var statsLogWrapper = StatsLogWrapper()
    private var logging = false

    @VisibleForTesting
    fun testSetStatsLogWrapper(wrapper: StatsLogWrapper) {
        statsLogWrapper = wrapper
    }

    @VisibleForTesting
    fun testSetSemaphore(s: Semaphore) {
        semaphore = s
    }

    @VisibleForTesting
    fun testSetHandler(handler: Handler) {
        eventHandler = handler
    }

    private fun getNextAvailableTimeMillis(): Long {
        return max(
            // Handles back to back records
            nextAvailableTimeMillis + EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds,
            // Updates next time to more recent value if it wasn't recently
            SystemClock.uptimeMillis() + EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds,
        )
    }

    // Initializes Async Logger for logging events. Returns true if
    // started logging and False if logging was already started.
    fun startLogging(): Boolean {
        if (logging) {
            return false
        }
        logging = true
        if (DEBUG) {
            Log.d(TAG, "Logging started")
        }
        semaphore = Semaphore(MAX_EVENT_QUEUE)
        handlerThread = HandlerThread("StatsEventLoggerWrapper").also { it.start() }
        eventHandler = Handler(handlerThread.getLooper())
        nextAvailableTimeMillis = SystemClock.uptimeMillis()
        return true
    }

    // Returns true if logging was started and the logger successfully
    // logged all pending events while ending. Returns false
    // otherwise.
    fun stopLogging(): Boolean {
        if (!logging) {
            return false
        }
        logging = false
        if (DEBUG) {
            Log.d(TAG, "Begin flushing events")
        }
        val acquired =
            semaphore.tryAcquire(
                MAX_EVENT_QUEUE,
                MAX_EVENT_QUEUE * EVENT_REPORTED_MIN_INTERVAL.inWholeMilliseconds,
                TimeUnit.MILLISECONDS,
            )
        if (!acquired) {
            Log.w(TAG, "Time exceeded awaiting stats events")
        }
        if (DEBUG) {
            Log.d(TAG, "End flushing events")
        }
        handlerThread.quit()
        return acquired
    }

    fun AdvancedOptionsUiLaunched(@UserIdInt printServiceId: Int): Boolean {
        if (!logging) {
            return false
        }
        if (DEBUG) {
            Log.d(TAG, "Logging AdvancedOptionsUiLaunched event")
        }
        synchronized(semaphore) {
            if (!semaphore.tryAcquire()) {
                Log.w(TAG, "Logging too many events, dropping AdvancedOptionsUiLaunched event")
                return false
            }
            val result =
                eventHandler.postAtTime(
                    Runnable {
                        synchronized(semaphore) {
                            if (DEBUG) {
                                Log.d(TAG, "Async logging AdvancedOptionsUiLaunched event")
                            }
                            statsLogWrapper.internalAdvancedOptionsUiLaunched(printServiceId)
                            semaphore.release()
                        }
                    },
                    nextAvailableTimeMillis,
                )
            if (!result) {
                Log.e(TAG, "Could not log AdvancedOptionsUiLaunched event")
                semaphore.release()
                return false
            }
            nextAvailableTimeMillis = getNextAvailableTimeMillis()
        }
        return true
    }

    fun MainPrintUiLaunched(@UserIdInt printServiceIds: Set<Int>, printerCount: Int): Boolean {
        if (!logging) {
            return false
        }
        if (DEBUG) {
            Log.d(TAG, "Logging MainPrintUiLaunched event")
        }
        synchronized(semaphore) {
            if (!semaphore.tryAcquire()) {
                Log.w(TAG, "Logging too many events, dropping MainPrintUiLaunched event")
                return false
            }
            val result =
                eventHandler.postAtTime(
                    Runnable {
                        synchronized(semaphore) {
                            if (DEBUG) {
                                Log.d(TAG, "Async logging MainPrintUiLaunched event")
                            }
                            statsLogWrapper.internalMainPrintUiLaunched(
                                printServiceIds,
                                printerCount,
                            )
                            semaphore.release()
                        }
                    },
                    nextAvailableTimeMillis,
                )
            if (!result) {
                Log.e(TAG, "Could not log MainPrintUiLaunched event")
                semaphore.release()
                return false
            }
            nextAvailableTimeMillis = getNextAvailableTimeMillis()
        }
        return true
    }

    fun PrinterDiscovery(
        @UserIdInt printServiceId: Int,
        colorModeMask: Int,
        duplexModeMask: Int,
        supportedMediaSizes: Set<PrintAttributes.MediaSize>,
    ): Boolean {
        if (!logging) {
            return false
        }
        if (DEBUG) {
            Log.d(TAG, "Logging PrinterDiscovery event")
        }

        val colors =
            InternalColorModePrinterDiscoveryEvent.values()
                .filter { color -> (color.colorMode ?: 0) and colorModeMask != 0 }
                .toSet()
        val sizes =
            supportedMediaSizes
                .map { InternalMediaSizePrinterDiscoveryEvent.from(it.getId()) }
                .toSet()
        val duplexModes =
            InternalDuplexModePrinterDiscoveryEvent.values()
                .filter { mode -> (mode.duplexMode ?: 0) and duplexModeMask != 0 }
                .toSet()

        synchronized(semaphore) {
            if (!semaphore.tryAcquire()) {
                Log.w(TAG, "Logging too many events, dropping PrinterDiscovery event")
                return false
            }
            val result =
                eventHandler.postAtTime(
                    Runnable {
                        synchronized(semaphore) {
                            if (DEBUG) {
                                Log.d(TAG, "Async logging PrinterDiscovery event")
                            }
                            statsLogWrapper.internalPrinterDiscovery(
                                printServiceId,
                                colors,
                                sizes,
                                duplexModes,
                            )
                            semaphore.release()
                        }
                    },
                    nextAvailableTimeMillis,
                )
            if (!result) {
                Log.e(TAG, "Could not log PrinterDiscovery event")
                semaphore.release()
                return false
            }
            nextAvailableTimeMillis = getNextAvailableTimeMillis()
        }
        return true
    }

    fun PrintJob(
        @UserIdInt printServiceId: Int,
        finalJobState: Int,
        colorModeMask: Int,
        mediaSize: PrintAttributes.MediaSize?,
        resolution: PrintAttributes.Resolution?,
        duplexModeMask: Int,
        docType: Int,
        savedPdf: Boolean,
        pageCount: Int,
    ): Boolean {
        if (!logging) {
            return false
        }
        if (DEBUG) {
            Log.d(TAG, "Logging PrintJob event")
        }

        val finalState = InternalFinalStatePrintJobEvent.from(finalJobState)
        val colorMode = InternalColorModePrintJobEvent.from(colorModeMask)
        val size = InternalMediaSizePrintJobEvent.from(mediaSize?.getId() ?: null)
        val duplexMode = InternalDuplexModePrintJobEvent.from(duplexModeMask)
        val documentType = InternalDocumentTypePrintJobEvent.from(docType)
        val horizontalDpi = resolution?.getHorizontalDpi() ?: 0
        val verticalDpi = resolution?.getVerticalDpi() ?: 0
        val orientation =
            when (mediaSize) {
                null -> InternalOrientationPrintJobEvent.UNSPECIFIED
                else ->
                    if (mediaSize.isPortrait()) InternalOrientationPrintJobEvent.PORTRAIT
                    else InternalOrientationPrintJobEvent.LANDSCAPE
            }

        synchronized(semaphore) {
            if (!semaphore.tryAcquire()) {
                Log.w(TAG, "Logging too many events, dropping PrintJob event")
                return false
            }
            val result =
                eventHandler.postAtTime(
                    Runnable {
                        synchronized(semaphore) {
                            if (DEBUG) {
                                Log.d(TAG, "Async logging PrintJob event")
                            }
                            statsLogWrapper.internalPrintJob(
                                printServiceId,
                                finalState,
                                colorMode,
                                duplexMode,
                                size,
                                documentType,
                                orientation,
                                horizontalDpi,
                                verticalDpi,
                                savedPdf,
                                pageCount,
                            )
                            semaphore.release()
                        }
                    },
                    nextAvailableTimeMillis,
                )
            if (!result) {
                Log.e(TAG, "Could not log PrintJob event")
                semaphore.release()
                return false
            }
            nextAvailableTimeMillis = getNextAvailableTimeMillis()
        }
        return true
    }

    // Mappings for internal values to associated proto values.

    // Each Enum class has a suffix that corresponds to an event
    // proto, e.g. InternalColorMode"PrinterDiscoveryEvent" to be used for
    // proto values for PrinterDiscovery() recorded above.
    // These are internal to the package so are prefixed with
    // Internal

    enum class InternalDocumentTypePrintJobEvent(val documentType: Int, val rawValue: Int) {
        DOCUMENT(
            PrintDocumentInfo.CONTENT_TYPE_DOCUMENT,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINT_JOB__DOCUMENT_TYPE__FRAMEWORK_DOCUMENT_TYPE_DOCUMENT,
        ),
        PHOTO(
            PrintDocumentInfo.CONTENT_TYPE_PHOTO,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__DOCUMENT_TYPE__FRAMEWORK_DOCUMENT_TYPE_PHOTO,
        ),
        UNSPECIFIED(
            PrintDocumentInfo.CONTENT_TYPE_UNKNOWN,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINT_JOB__DOCUMENT_TYPE__FRAMEWORK_DOCUMENT_TYPE_UNSPECIFIED,
        );

        companion object {
            private val map = entries.associateBy(InternalDocumentTypePrintJobEvent::documentType)

            fun from(documentType: Int?): InternalDocumentTypePrintJobEvent {
                return map.getOrDefault(documentType, InternalDocumentTypePrintJobEvent.UNSPECIFIED)
            }
        }
    }

    enum class InternalDuplexModePrintJobEvent(val duplexMode: Int, val rawValue: Int) {
        NONE(
            PrintAttributes.DUPLEX_MODE_NONE,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__DUPLEX_MODE__FRAMEWORK_DUPLEX_MODE_NONE,
        ),
        LONG_EDGE(
            PrintAttributes.DUPLEX_MODE_LONG_EDGE,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__DUPLEX_MODE__FRAMEWORK_DUPLEX_MODE_LONG_EDGE,
        ),
        SHORT_EDGE(
            PrintAttributes.DUPLEX_MODE_SHORT_EDGE,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__DUPLEX_MODE__FRAMEWORK_DUPLEX_MODE_SHORT_EDGE,
        ),
        UNSPECIFIED(
            0,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__DUPLEX_MODE__FRAMEWORK_DUPLEX_MODE_UNSPECIFIED,
        );

        companion object {
            private val map = entries.associateBy(InternalDuplexModePrintJobEvent::duplexMode)

            fun from(duplexMode: Int): InternalDuplexModePrintJobEvent {
                return map.getOrDefault(duplexMode, InternalDuplexModePrintJobEvent.UNSPECIFIED)
            }
        }
    }

    enum class InternalOrientationPrintJobEvent(val rawValue: Int) {
        PORTRAIT(
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__ORIENTATION__FRAMEWORK_ORIENTATION_PORTRAIT
        ),
        LANDSCAPE(
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__ORIENTATION__FRAMEWORK_ORIENTATION_LANDSCAPE
        ),
        UNSPECIFIED(
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__ORIENTATION__FRAMEWORK_ORIENTATION_UNSPECIFIED
        ),
    }

    enum class InternalMediaSizePrintJobEvent(val mediaSizeId: String?, val rawValue: Int) {
        UNKNOWN_PORTRAIT(
            PrintAttributes.MediaSize.UNKNOWN_PORTRAIT.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_UNKNOWN_PORTRAIT,
        ),
        UNKNOWN_LANDSCAPE(
            PrintAttributes.MediaSize.UNKNOWN_LANDSCAPE.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_UNKNOWN_LANDSCAPE,
        ),
        ISO_A0(
            PrintAttributes.MediaSize.ISO_A0.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A0,
        ),
        ISO_A1(
            PrintAttributes.MediaSize.ISO_A1.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A1,
        ),
        ISO_A2(
            PrintAttributes.MediaSize.ISO_A2.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A2,
        ),
        ISO_A3(
            PrintAttributes.MediaSize.ISO_A3.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A3,
        ),
        ISO_A4(
            PrintAttributes.MediaSize.ISO_A4.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A4,
        ),
        ISO_A5(
            PrintAttributes.MediaSize.ISO_A5.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A5,
        ),
        ISO_A6(
            PrintAttributes.MediaSize.ISO_A6.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A6,
        ),
        ISO_A7(
            PrintAttributes.MediaSize.ISO_A7.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A7,
        ),
        ISO_A8(
            PrintAttributes.MediaSize.ISO_A8.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A8,
        ),
        ISO_A9(
            PrintAttributes.MediaSize.ISO_A9.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A9,
        ),
        ISO_A10(
            PrintAttributes.MediaSize.ISO_A10.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_A10,
        ),
        ISO_B0(
            PrintAttributes.MediaSize.ISO_B0.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B0,
        ),
        ISO_B1(
            PrintAttributes.MediaSize.ISO_B1.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B1,
        ),
        ISO_B2(
            PrintAttributes.MediaSize.ISO_B2.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B2,
        ),
        ISO_B3(
            PrintAttributes.MediaSize.ISO_B3.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B3,
        ),
        ISO_B4(
            PrintAttributes.MediaSize.ISO_B4.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B4,
        ),
        ISO_B5(
            PrintAttributes.MediaSize.ISO_B5.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B5,
        ),
        ISO_B6(
            PrintAttributes.MediaSize.ISO_B6.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B6,
        ),
        ISO_B7(
            PrintAttributes.MediaSize.ISO_B7.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B7,
        ),
        ISO_B8(
            PrintAttributes.MediaSize.ISO_B8.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B8,
        ),
        ISO_B9(
            PrintAttributes.MediaSize.ISO_B9.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B9,
        ),
        ISO_B10(
            PrintAttributes.MediaSize.ISO_B10.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_B10,
        ),
        ISO_C0(
            PrintAttributes.MediaSize.ISO_C0.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C0,
        ),
        ISO_C1(
            PrintAttributes.MediaSize.ISO_C1.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C1,
        ),
        ISO_C2(
            PrintAttributes.MediaSize.ISO_C2.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C2,
        ),
        ISO_C3(
            PrintAttributes.MediaSize.ISO_C3.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C3,
        ),
        ISO_C4(
            PrintAttributes.MediaSize.ISO_C4.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C4,
        ),
        ISO_C5(
            PrintAttributes.MediaSize.ISO_C5.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C5,
        ),
        ISO_C6(
            PrintAttributes.MediaSize.ISO_C6.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C6,
        ),
        ISO_C7(
            PrintAttributes.MediaSize.ISO_C7.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C7,
        ),
        ISO_C8(
            PrintAttributes.MediaSize.ISO_C8.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C8,
        ),
        ISO_C9(
            PrintAttributes.MediaSize.ISO_C9.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C9,
        ),
        ISO_C10(
            PrintAttributes.MediaSize.ISO_C10.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ISO_C10,
        ),
        NA_LETTER(
            PrintAttributes.MediaSize.NA_LETTER.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_LETTER,
        ),
        NA_GOVT_LETTER(
            PrintAttributes.MediaSize.NA_GOVT_LETTER.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_GOVT_LETTER,
        ),
        NA_LEGAL(
            PrintAttributes.MediaSize.NA_LEGAL.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_LEGAL,
        ),
        NA_JUNIOR_LEGAL(
            PrintAttributes.MediaSize.NA_JUNIOR_LEGAL.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_JUNIOR_LEGAL,
        ),
        NA_LEDGER(
            PrintAttributes.MediaSize.NA_LEDGER.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_LEDGER,
        ),
        NA_TABLOID(
            PrintAttributes.MediaSize.NA_TABLOID.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_TABLOID,
        ),
        NA_INDEX_3X5(
            PrintAttributes.MediaSize.NA_INDEX_3X5.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_INDEX_3X5,
        ),
        NA_INDEX_4X6(
            PrintAttributes.MediaSize.NA_INDEX_4X6.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_INDEX_4X6,
        ),
        NA_INDEX_5X8(
            PrintAttributes.MediaSize.NA_INDEX_5X8.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_INDEX_5X8,
        ),
        NA_MONARCH(
            PrintAttributes.MediaSize.NA_MONARCH.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_MONARCH,
        ),
        NA_QUARTO(
            PrintAttributes.MediaSize.NA_QUARTO.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_QUARTO,
        ),
        NA_FOOLSCAP(
            PrintAttributes.MediaSize.NA_FOOLSCAP.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_FOOLSCAP,
        ),
        ANSI_C(
            PrintAttributes.MediaSize.ANSI_C.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ANSI_C,
        ),
        ANSI_D(
            PrintAttributes.MediaSize.ANSI_D.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ANSI_D,
        ),
        ANSI_E(
            PrintAttributes.MediaSize.ANSI_E.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ANSI_E,
        ),
        ANSI_F(
            PrintAttributes.MediaSize.ANSI_F.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ANSI_F,
        ),
        NA_ARCH_A(
            PrintAttributes.MediaSize.NA_ARCH_A.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_ARCH_A,
        ),
        NA_ARCH_B(
            PrintAttributes.MediaSize.NA_ARCH_B.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_ARCH_B,
        ),
        NA_ARCH_C(
            PrintAttributes.MediaSize.NA_ARCH_C.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_ARCH_C,
        ),
        NA_ARCH_D(
            PrintAttributes.MediaSize.NA_ARCH_D.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_ARCH_D,
        ),
        NA_ARCH_E(
            PrintAttributes.MediaSize.NA_ARCH_E.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_ARCH_E,
        ),
        NA_ARCH_E1(
            PrintAttributes.MediaSize.NA_ARCH_E1.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_ARCH_E1,
        ),
        NA_SUPER_B(
            PrintAttributes.MediaSize.NA_SUPER_B.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_NA_SUPER_B,
        ),
        ROC_8K(
            PrintAttributes.MediaSize.ROC_8K.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ROC_8K,
        ),
        ROC_16K(
            PrintAttributes.MediaSize.ROC_16K.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_ROC_16K,
        ),
        PRC_1(
            PrintAttributes.MediaSize.PRC_1.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_1,
        ),
        PRC_2(
            PrintAttributes.MediaSize.PRC_2.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_2,
        ),
        PRC_3(
            PrintAttributes.MediaSize.PRC_3.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_3,
        ),
        PRC_4(
            PrintAttributes.MediaSize.PRC_4.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_4,
        ),
        PRC_5(
            PrintAttributes.MediaSize.PRC_5.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_5,
        ),
        PRC_6(
            PrintAttributes.MediaSize.PRC_6.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_6,
        ),
        PRC_7(
            PrintAttributes.MediaSize.PRC_7.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_7,
        ),
        PRC_8(
            PrintAttributes.MediaSize.PRC_8.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_8,
        ),
        PRC_9(
            PrintAttributes.MediaSize.PRC_9.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_9,
        ),
        PRC_10(
            PrintAttributes.MediaSize.PRC_10.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_10,
        ),
        PRC_16K(
            PrintAttributes.MediaSize.PRC_16K.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_PRC_16K,
        ),
        OM_PA_KAI(
            PrintAttributes.MediaSize.OM_PA_KAI.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_OM_PA_KAI,
        ),
        OM_DAI_PA_KAI(
            PrintAttributes.MediaSize.OM_DAI_PA_KAI.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_OM_DAI_PA_KAI,
        ),
        OM_JUURO_KU_KAI(
            PrintAttributes.MediaSize.OM_JUURO_KU_KAI.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_OM_JUURO_KU_KAI,
        ),
        JIS_B10(
            PrintAttributes.MediaSize.JIS_B10.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B10,
        ),
        JIS_B9(
            PrintAttributes.MediaSize.JIS_B9.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B9,
        ),
        JIS_B8(
            PrintAttributes.MediaSize.JIS_B8.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B8,
        ),
        JIS_B7(
            PrintAttributes.MediaSize.JIS_B7.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B7,
        ),
        JIS_B6(
            PrintAttributes.MediaSize.JIS_B6.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B6,
        ),
        JIS_B5(
            PrintAttributes.MediaSize.JIS_B5.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B5,
        ),
        JIS_B4(
            PrintAttributes.MediaSize.JIS_B4.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B4,
        ),
        JIS_B3(
            PrintAttributes.MediaSize.JIS_B3.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B3,
        ),
        JIS_B2(
            PrintAttributes.MediaSize.JIS_B2.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B2,
        ),
        JIS_B1(
            PrintAttributes.MediaSize.JIS_B1.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B1,
        ),
        JIS_B0(
            PrintAttributes.MediaSize.JIS_B0.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_B0,
        ),
        JIS_EXEC(
            PrintAttributes.MediaSize.JIS_EXEC.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JIS_EXEC,
        ),
        JPN_CHOU4(
            PrintAttributes.MediaSize.JPN_CHOU4.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_CHOU4,
        ),
        JPN_CHOU3(
            PrintAttributes.MediaSize.JPN_CHOU3.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_CHOU3,
        ),
        JPN_CHOU2(
            PrintAttributes.MediaSize.JPN_CHOU2.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_CHOU2,
        ),
        JPN_HAGAKI(
            PrintAttributes.MediaSize.JPN_HAGAKI.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_HAGAKI,
        ),
        JPN_OUFUKU(
            PrintAttributes.MediaSize.JPN_OUFUKU.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_OUFUKU,
        ),
        JPN_KAHU(
            PrintAttributes.MediaSize.JPN_KAHU.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_KAHU,
        ),
        JPN_KAKU2(
            PrintAttributes.MediaSize.JPN_KAKU2.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_KAKU2,
        ),
        JPN_YOU4(
            PrintAttributes.MediaSize.JPN_YOU4.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_YOU4,
        ),
        JPN_OE_PHOTO_L(
            PrintAttributes.MediaSize.JPN_OE_PHOTO_L.getId(),
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_JPN_OE_PHOTO_L,
        ),
        UNSPECIFIED(
            null,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__SIZE__FRAMEWORK_MEDIA_SIZE_UNSPECIFIED,
        );

        companion object {
            private val map = entries.associateBy(InternalMediaSizePrintJobEvent::mediaSizeId)

            fun from(mediaSizeId: String?): InternalMediaSizePrintJobEvent {
                return when (mediaSizeId) {
                    null -> InternalMediaSizePrintJobEvent.UNSPECIFIED
                    else -> {
                        map.getOrDefault(mediaSizeId, InternalMediaSizePrintJobEvent.UNSPECIFIED)
                    }
                }
            }
        }
    }

    enum class InternalColorModePrintJobEvent(val colorMode: Int, val rawValue: Int) {
        COLOR(
            PrintAttributes.COLOR_MODE_COLOR,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__COLOR__FRAMEWORK_COLOR_MODE_COLOR,
        ),
        MONOCHROME(
            PrintAttributes.COLOR_MODE_MONOCHROME,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__COLOR__FRAMEWORK_COLOR_MODE_MONOCRHOME,
        ),
        UNSPECIFIED(
            0,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__COLOR__FRAMEWORK_COLOR_MODE_UNSPECIFIED,
        );

        companion object {
            private val map = entries.associateBy(InternalColorModePrintJobEvent::colorMode)

            fun from(colorMode: Int): InternalColorModePrintJobEvent {
                return map.getOrDefault(colorMode, InternalColorModePrintJobEvent.UNSPECIFIED)
            }
        }
    }

    enum class InternalFinalStatePrintJobEvent(val state: Int?, val rawValue: Int) {
        COMPLETED(
            PrintJobInfo.STATE_COMPLETED,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINT_JOB__FINAL_STATE__FRAMEWORK_PRINT_JOB_RESULT_COMPLETED,
        ),
        CANCELLED(
            PrintJobInfo.STATE_CANCELED,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINT_JOB__FINAL_STATE__FRAMEWORK_PRINT_JOB_RESULT_CANCELLED,
        ),
        FAILED(
            PrintJobInfo.STATE_FAILED,
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB__FINAL_STATE__FRAMEWORK_PRINT_JOB_RESULT_FAILED,
        ),
        UNSPECIFIED(
            null,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINT_JOB__FINAL_STATE__FRAMEWORK_PRINT_JOB_RESULT_UNSPECIFIED,
        );

        companion object {
            private val map = entries.associateBy(InternalFinalStatePrintJobEvent::state)

            fun from(state: Int): InternalFinalStatePrintJobEvent {
                return map.getOrDefault(state, InternalFinalStatePrintJobEvent.UNSPECIFIED)
            }
        }
    }

    enum class InternalColorModePrinterDiscoveryEvent(val colorMode: Int, val rawValue: Int) {
        COLOR(
            PrintAttributes.COLOR_MODE_COLOR,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_COLORS__FRAMEWORK_COLOR_MODE_COLOR,
        ),
        MONOCHROME(
            PrintAttributes.COLOR_MODE_MONOCHROME,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_COLORS__FRAMEWORK_COLOR_MODE_MONOCRHOME,
        ),
        UNSPECIFIED(
            0,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_COLORS__FRAMEWORK_COLOR_MODE_UNSPECIFIED,
        ),
    }

    enum class InternalDuplexModePrinterDiscoveryEvent(val duplexMode: Int, val rawValue: Int) {
        LONG_EDGE(
            PrintAttributes.DUPLEX_MODE_LONG_EDGE,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_DUPLEX_MODES__FRAMEWORK_DUPLEX_MODE_LONG_EDGE,
        ),
        SHORT_EDGE(
            PrintAttributes.DUPLEX_MODE_SHORT_EDGE,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_DUPLEX_MODES__FRAMEWORK_DUPLEX_MODE_SHORT_EDGE,
        ),
        NONE(
            PrintAttributes.DUPLEX_MODE_NONE,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_DUPLEX_MODES__FRAMEWORK_DUPLEX_MODE_NONE,
        ),
        UNSPECIFIED(
            0,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_DUPLEX_MODES__FRAMEWORK_DUPLEX_MODE_UNSPECIFIED,
        ),
    }

    enum class InternalMediaSizePrinterDiscoveryEvent(val mediaSizeId: String?, val rawValue: Int) {
        UNKNOWN_PORTRAIT(
            PrintAttributes.MediaSize.UNKNOWN_PORTRAIT.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_UNKNOWN_PORTRAIT,
        ),
        UNKNOWN_LANDSCAPE(
            PrintAttributes.MediaSize.UNKNOWN_LANDSCAPE.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_UNKNOWN_LANDSCAPE,
        ),
        ISO_A0(
            PrintAttributes.MediaSize.ISO_A0.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A0,
        ),
        ISO_A1(
            PrintAttributes.MediaSize.ISO_A1.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A1,
        ),
        ISO_A2(
            PrintAttributes.MediaSize.ISO_A2.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A2,
        ),
        ISO_A3(
            PrintAttributes.MediaSize.ISO_A3.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A3,
        ),
        ISO_A4(
            PrintAttributes.MediaSize.ISO_A4.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A4,
        ),
        ISO_A5(
            PrintAttributes.MediaSize.ISO_A5.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A5,
        ),
        ISO_A6(
            PrintAttributes.MediaSize.ISO_A6.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A6,
        ),
        ISO_A7(
            PrintAttributes.MediaSize.ISO_A7.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A7,
        ),
        ISO_A8(
            PrintAttributes.MediaSize.ISO_A8.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A8,
        ),
        ISO_A9(
            PrintAttributes.MediaSize.ISO_A9.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A9,
        ),
        ISO_A10(
            PrintAttributes.MediaSize.ISO_A10.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_A10,
        ),
        ISO_B0(
            PrintAttributes.MediaSize.ISO_B0.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B0,
        ),
        ISO_B1(
            PrintAttributes.MediaSize.ISO_B1.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B1,
        ),
        ISO_B2(
            PrintAttributes.MediaSize.ISO_B2.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B2,
        ),
        ISO_B3(
            PrintAttributes.MediaSize.ISO_B3.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B3,
        ),
        ISO_B4(
            PrintAttributes.MediaSize.ISO_B4.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B4,
        ),
        ISO_B5(
            PrintAttributes.MediaSize.ISO_B5.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B5,
        ),
        ISO_B6(
            PrintAttributes.MediaSize.ISO_B6.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B6,
        ),
        ISO_B7(
            PrintAttributes.MediaSize.ISO_B7.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B7,
        ),
        ISO_B8(
            PrintAttributes.MediaSize.ISO_B8.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B8,
        ),
        ISO_B9(
            PrintAttributes.MediaSize.ISO_B9.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B9,
        ),
        ISO_B10(
            PrintAttributes.MediaSize.ISO_B10.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_B10,
        ),
        ISO_C0(
            PrintAttributes.MediaSize.ISO_C0.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C0,
        ),
        ISO_C1(
            PrintAttributes.MediaSize.ISO_C1.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C1,
        ),
        ISO_C2(
            PrintAttributes.MediaSize.ISO_C2.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C2,
        ),
        ISO_C3(
            PrintAttributes.MediaSize.ISO_C3.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C3,
        ),
        ISO_C4(
            PrintAttributes.MediaSize.ISO_C4.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C4,
        ),
        ISO_C5(
            PrintAttributes.MediaSize.ISO_C5.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C5,
        ),
        ISO_C6(
            PrintAttributes.MediaSize.ISO_C6.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C6,
        ),
        ISO_C7(
            PrintAttributes.MediaSize.ISO_C7.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C7,
        ),
        ISO_C8(
            PrintAttributes.MediaSize.ISO_C8.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C8,
        ),
        ISO_C9(
            PrintAttributes.MediaSize.ISO_C9.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C9,
        ),
        ISO_C10(
            PrintAttributes.MediaSize.ISO_C10.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ISO_C10,
        ),
        NA_LETTER(
            PrintAttributes.MediaSize.NA_LETTER.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_LETTER,
        ),
        NA_GOVT_LETTER(
            PrintAttributes.MediaSize.NA_GOVT_LETTER.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_GOVT_LETTER,
        ),
        NA_LEGAL(
            PrintAttributes.MediaSize.NA_LEGAL.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_LEGAL,
        ),
        NA_JUNIOR_LEGAL(
            PrintAttributes.MediaSize.NA_JUNIOR_LEGAL.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_JUNIOR_LEGAL,
        ),
        NA_LEDGER(
            PrintAttributes.MediaSize.NA_LEDGER.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_LEDGER,
        ),
        NA_TABLOID(
            PrintAttributes.MediaSize.NA_TABLOID.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_TABLOID,
        ),
        NA_INDEX_3X5(
            PrintAttributes.MediaSize.NA_INDEX_3X5.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_INDEX_3X5,
        ),
        NA_INDEX_4X6(
            PrintAttributes.MediaSize.NA_INDEX_4X6.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_INDEX_4X6,
        ),
        NA_INDEX_5X8(
            PrintAttributes.MediaSize.NA_INDEX_5X8.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_INDEX_5X8,
        ),
        NA_MONARCH(
            PrintAttributes.MediaSize.NA_MONARCH.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_MONARCH,
        ),
        NA_QUARTO(
            PrintAttributes.MediaSize.NA_QUARTO.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_QUARTO,
        ),
        NA_FOOLSCAP(
            PrintAttributes.MediaSize.NA_FOOLSCAP.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_FOOLSCAP,
        ),
        ANSI_C(
            PrintAttributes.MediaSize.ANSI_C.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ANSI_C,
        ),
        ANSI_D(
            PrintAttributes.MediaSize.ANSI_D.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ANSI_D,
        ),
        ANSI_E(
            PrintAttributes.MediaSize.ANSI_E.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ANSI_E,
        ),
        ANSI_F(
            PrintAttributes.MediaSize.ANSI_F.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ANSI_F,
        ),
        NA_ARCH_A(
            PrintAttributes.MediaSize.NA_ARCH_A.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_ARCH_A,
        ),
        NA_ARCH_B(
            PrintAttributes.MediaSize.NA_ARCH_B.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_ARCH_B,
        ),
        NA_ARCH_C(
            PrintAttributes.MediaSize.NA_ARCH_C.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_ARCH_C,
        ),
        NA_ARCH_D(
            PrintAttributes.MediaSize.NA_ARCH_D.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_ARCH_D,
        ),
        NA_ARCH_E(
            PrintAttributes.MediaSize.NA_ARCH_E.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_ARCH_E,
        ),
        NA_ARCH_E1(
            PrintAttributes.MediaSize.NA_ARCH_E1.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_ARCH_E1,
        ),
        NA_SUPER_B(
            PrintAttributes.MediaSize.NA_SUPER_B.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_NA_SUPER_B,
        ),
        ROC_8K(
            PrintAttributes.MediaSize.ROC_8K.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ROC_8K,
        ),
        ROC_16K(
            PrintAttributes.MediaSize.ROC_16K.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_ROC_16K,
        ),
        PRC_1(
            PrintAttributes.MediaSize.PRC_1.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_1,
        ),
        PRC_2(
            PrintAttributes.MediaSize.PRC_2.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_2,
        ),
        PRC_3(
            PrintAttributes.MediaSize.PRC_3.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_3,
        ),
        PRC_4(
            PrintAttributes.MediaSize.PRC_4.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_4,
        ),
        PRC_5(
            PrintAttributes.MediaSize.PRC_5.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_5,
        ),
        PRC_6(
            PrintAttributes.MediaSize.PRC_6.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_6,
        ),
        PRC_7(
            PrintAttributes.MediaSize.PRC_7.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_7,
        ),
        PRC_8(
            PrintAttributes.MediaSize.PRC_8.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_8,
        ),
        PRC_9(
            PrintAttributes.MediaSize.PRC_9.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_9,
        ),
        PRC_10(
            PrintAttributes.MediaSize.PRC_10.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_10,
        ),
        PRC_16K(
            PrintAttributes.MediaSize.PRC_16K.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_PRC_16K,
        ),
        OM_PA_KAI(
            PrintAttributes.MediaSize.OM_PA_KAI.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_OM_PA_KAI,
        ),
        OM_DAI_PA_KAI(
            PrintAttributes.MediaSize.OM_DAI_PA_KAI.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_OM_DAI_PA_KAI,
        ),
        OM_JUURO_KU_KAI(
            PrintAttributes.MediaSize.OM_JUURO_KU_KAI.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_OM_JUURO_KU_KAI,
        ),
        JIS_B10(
            PrintAttributes.MediaSize.JIS_B10.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B10,
        ),
        JIS_B9(
            PrintAttributes.MediaSize.JIS_B9.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B9,
        ),
        JIS_B8(
            PrintAttributes.MediaSize.JIS_B8.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B8,
        ),
        JIS_B7(
            PrintAttributes.MediaSize.JIS_B7.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B7,
        ),
        JIS_B6(
            PrintAttributes.MediaSize.JIS_B6.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B6,
        ),
        JIS_B5(
            PrintAttributes.MediaSize.JIS_B5.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B5,
        ),
        JIS_B4(
            PrintAttributes.MediaSize.JIS_B4.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B4,
        ),
        JIS_B3(
            PrintAttributes.MediaSize.JIS_B3.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B3,
        ),
        JIS_B2(
            PrintAttributes.MediaSize.JIS_B2.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B2,
        ),
        JIS_B1(
            PrintAttributes.MediaSize.JIS_B1.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B1,
        ),
        JIS_B0(
            PrintAttributes.MediaSize.JIS_B0.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_B0,
        ),
        JIS_EXEC(
            PrintAttributes.MediaSize.JIS_EXEC.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JIS_EXEC,
        ),
        JPN_CHOU4(
            PrintAttributes.MediaSize.JPN_CHOU4.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JPN_CHOU4,
        ),
        JPN_CHOU3(
            PrintAttributes.MediaSize.JPN_CHOU3.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JPN_CHOU3,
        ),
        JPN_CHOU2(
            PrintAttributes.MediaSize.JPN_CHOU2.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JPN_CHOU2,
        ),
        JPN_HAGAKI(
            PrintAttributes.MediaSize.JPN_HAGAKI.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JPN_HAGAKI,
        ),
        JPN_OUFUKU(
            PrintAttributes.MediaSize.JPN_OUFUKU.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JPN_OUFUKU,
        ),
        JPN_KAHU(
            PrintAttributes.MediaSize.JPN_KAHU.getId(),
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_JPN_KAHU,
        ),
        UNSPECIFIED(
            null,
            PrintSpoolerStatsLog
                .FRAMEWORK_PRINTER_DISCOVERY__SUPPORTED_SIZES__FRAMEWORK_MEDIA_SIZE_UNSPECIFIED,
        );

        companion object {
            private val map =
                entries.associateBy(InternalMediaSizePrinterDiscoveryEvent::mediaSizeId)

            fun from(mediaSizeId: String?): InternalMediaSizePrinterDiscoveryEvent {
                return when (mediaSizeId) {
                    null -> InternalMediaSizePrinterDiscoveryEvent.UNSPECIFIED
                    else -> {
                        map.getOrDefault(
                            mediaSizeId,
                            InternalMediaSizePrinterDiscoveryEvent.UNSPECIFIED,
                        )
                    }
                }
            }
        }
    }
}
