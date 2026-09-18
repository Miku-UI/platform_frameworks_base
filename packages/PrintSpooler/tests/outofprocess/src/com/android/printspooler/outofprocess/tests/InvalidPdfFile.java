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

package com.android.printspooler.outofprocess.tests;

import static android.print.test.Utils.eventually;
import static android.print.test.Utils.getPrintJob;
import static android.print.test.Utils.getPrintManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.test.services.FirstPrintService;
import android.print.test.services.PrintServiceCallbacks;
import android.print.test.services.PrinterDiscoverySessionCallbacks;
import android.print.test.services.StubbablePrinterDiscoverySession;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileOutputStream;
import java.util.ArrayList;

/** Test attempting to print an invalid PDF file. */
@RunWith(AndroidJUnit4.class)
public class InvalidPdfFile extends PrintSpoolerBaseTest {
    private static final String LOG_TAG = InvalidPdfFile.class.getSimpleName();
    private static final String PRINTER_NAME = "Test printer";
    private static final String PRINT_JOB_NAME = "test-print-job";

    /**
     * Create a mock {@link PrintDocumentAdapter} that provides an invalid PDF.
     *
     * @return The mock adapter
     */
    private @NonNull PrintDocumentAdapter createInvalidPrintDocumentAdapter() {
        return createMockPrintDocumentAdapter(
            invocation -> {
                LayoutResultCallback callback =
                        (LayoutResultCallback) invocation.getArguments()[3];

                callback.onLayoutFinished(new PrintDocumentInfo.Builder("Test")
                        .setPageCount(1)
                        .build(),
                        false);

                onLayoutCalled();
                return null;
            }, invocation -> {
                Object[] args = invocation.getArguments();
                PageRange[] pages = (PageRange[]) args[0];
                ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                WriteResultCallback callback = (WriteResultCallback) args[3];

                FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor());
                fos.write("Invalid PDF file".getBytes());
                fos.close();
                fd.close();

                callback.onWriteFinished(pages);

                onWriteCalled();
                return null;
            }, invocation -> {
                onFinishCalled();
                return null;
            });

    }

    /**
     * Create a mock {@link PrinterDiscoverySessionCallbacks} that discovers a simple test printer.
     *
     * @return The mock session callbacks
     */
    private @NonNull PrinterDiscoverySessionCallbacks createMockPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(
            invocation -> {
                StubbablePrinterDiscoverySession session =
                        ((PrinterDiscoverySessionCallbacks) invocation.getMock()).getSession();

                if (session.getPrinters().isEmpty()) {
                    PrinterId printerId = session.getService().generatePrinterId(PRINTER_NAME);
                    PrinterInfo.Builder printer = new PrinterInfo.Builder(
                            session.getService().generatePrinterId(PRINTER_NAME), PRINTER_NAME,
                            PrinterInfo.STATUS_IDLE);

                    printer.setCapabilities(
                        new PrinterCapabilitiesInfo.Builder(printerId)
                            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                            .addResolution(
                                new PrintAttributes.Resolution("200x200", "200dpi", 200, 200), true)
                            .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                PrintAttributes.COLOR_MODE_COLOR)
                            .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE,
                                PrintAttributes.DUPLEX_MODE_NONE)
                            .setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0))
                            .build());

                    ArrayList<PrinterInfo> printers = new ArrayList<>(1);
                    printers.add(printer.build());

                    session.addPrinters(printers);
                }
                return null;
            }, null, null, null, null, null, null
            );
    }

    @Test
    @LargeTest
    public void printInvalidFile() throws Throwable {
        Log.i(LOG_TAG, "Running invalid PDF file test");

        // The discovery session callbacks that generate a single test printer.
        PrinterDiscoverySessionCallbacks sessionCallbacks =
                createMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the print service.
        PrintServiceCallbacks serviceCallbacks = createMockPrintServiceCallbacks(
                invocation -> sessionCallbacks,
                invocation -> {
                    android.printservice.PrintJob job = (android.printservice.PrintJob) invocation
                                                        .getArguments()[0];

                    // Move the job to the created state.
                    eventually(() -> assertEquals(PrintJobInfo.STATE_CREATED,
                                    job.getInfo().getState()));
                    return null;
                },
                null);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // A mock adapter that generates an invalid PDF.
        PrintDocumentAdapter adapter = createInvalidPrintDocumentAdapter();

        print(adapter, PRINT_JOB_NAME);
        waitForWriteAdapterCallback(1);

        // Since this is an invalid file, the user won't be able to submit the job.
        assertFalse(mPrintHelper.canSubmitJob());

        // Additionally, the job should get set to Cancelled.
        PrintJob job = getPrintJob(getPrintManager(getActivity()), PRINT_JOB_NAME);
        eventually(() -> assertTrue(job.isCancelled()));
    }
}
