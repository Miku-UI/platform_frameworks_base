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

// Thin wrapper around the generated atom logger for dependency
// injection and testability

// Kotlin does not offer package-private visibility modifier.
// Clients outside of the package should use StatsAsyncLogger instead.

// Not intended to be subclassed, left "open" for mocking.  internal
// modifier isn't used as it doesn't play nice with @VisibleForTesting
// annotation within package clients.

/**
 * @hide
 */
open class StatsLogWrapper {
    open fun internalAdvancedOptionsUiLaunched(@UserIdInt printServiceId: Int) {
        PrintSpoolerStatsLog.write(
            PrintSpoolerStatsLog.FRAMEWORK_ADVANCED_UI_LAUNCHED,
            printServiceId,
        )
    }

    open fun internalMainPrintUiLaunched(
        @UserIdInt printServiceIds: Set<Int>,
        printServiceCount: Int,
    ) {
        PrintSpoolerStatsLog.write(
            PrintSpoolerStatsLog.FRAMEWORK_MAIN_PRINT_UI_LAUNCHED,
            printServiceIds.toIntArray(),
            printServiceCount,
        )
    }

    open fun internalPrinterDiscovery(
        @UserIdInt printServiceId: Int,
        supportedColors: Set<StatsAsyncLogger.InternalColorModePrinterDiscoveryEvent>,
        supportedSizes: Set<StatsAsyncLogger.InternalMediaSizePrinterDiscoveryEvent>,
        supportedDuplexModes: Set<StatsAsyncLogger.InternalDuplexModePrinterDiscoveryEvent>,
    ) {
        val colorBits = supportedColors.map { it.rawValue }.toIntArray()
        val mediaSizes = supportedSizes.map { it.rawValue }.toIntArray()
        val duplexModes = supportedDuplexModes.map { it.rawValue }.toIntArray()

        PrintSpoolerStatsLog.write(
            PrintSpoolerStatsLog.FRAMEWORK_PRINTER_DISCOVERY,
            printServiceId,
            colorBits,
            mediaSizes,
            duplexModes,
        )
    }

    open fun internalPrintJob(
        @UserIdInt printServiceId: Int,
        finalState: StatsAsyncLogger.InternalFinalStatePrintJobEvent,
        colorMode: StatsAsyncLogger.InternalColorModePrintJobEvent,
        duplexMode: StatsAsyncLogger.InternalDuplexModePrintJobEvent,
        mediaSize: StatsAsyncLogger.InternalMediaSizePrintJobEvent,
        docType: StatsAsyncLogger.InternalDocumentTypePrintJobEvent,
        orientation: StatsAsyncLogger.InternalOrientationPrintJobEvent,
        horizontalDpi: Int,
        verticalDpi: Int,
        savedPdf: Boolean,
        pageCount: Int,
    ) {
        PrintSpoolerStatsLog.write(
            PrintSpoolerStatsLog.FRAMEWORK_PRINT_JOB,
            finalState.rawValue,
            colorMode.rawValue,
            printServiceId,
            mediaSize.rawValue,
            horizontalDpi,
            verticalDpi,
            orientation.rawValue,
            duplexMode.rawValue,
            docType.rawValue,
            savedPdf,
            pageCount,
        )
    }
}
