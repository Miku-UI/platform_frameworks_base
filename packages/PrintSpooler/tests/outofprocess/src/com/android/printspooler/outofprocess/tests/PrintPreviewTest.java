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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.print.PrintAttributes;
import android.print.PrintAttributes.MediaSize;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.test.services.FirstPrintService;
import android.print.test.services.PrinterDiscoverySessionCallbacks;
import android.print.test.services.StubbablePrinterDiscoverySession;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObject2Ext;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.printspooler.flags.Flags;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for properties of print preview. */
public class PrintPreviewTest extends PrintSpoolerBaseTest {
    private static final String LOG_TAG = PrintPreviewTest.class.getSimpleName();
    private static final String COLOR_SPINNER_ID = "com.android.printspooler:id/color_spinner";

    /** Add a printer with a given name and supported mediasize to a session */
    private void addPrinter(
            StubbablePrinterDiscoverySession session,
            String name,
            PrintAttributes.MediaSize mediaSize) {
        PrinterId printerId = session.getService().generatePrinterId(name);
        List<PrinterInfo> printers = new ArrayList<>(1);

        PrinterCapabilitiesInfo.Builder builder = new PrinterCapabilitiesInfo.Builder(printerId);

        PrinterInfo printerInfo;
        if (mediaSize != null) {
            builder.setMinMargins(new PrintAttributes.Margins(0, 0, 0, 0))
                    .setColorModes(
                            PrintAttributes.COLOR_MODE_COLOR
                                    | PrintAttributes.COLOR_MODE_MONOCHROME,
                            PrintAttributes.COLOR_MODE_COLOR)
                    .addMediaSize(mediaSize, true)
                    .addResolution(
                            new PrintAttributes.Resolution("300x300", "300x300", 300, 300), true);

            printerInfo =
                    new PrinterInfo.Builder(printerId, name, PrinterInfo.STATUS_IDLE)
                            .setCapabilities(builder.build())
                            .build();
        } else {
            printerInfo =
                    (new PrinterInfo.Builder(printerId, name, PrinterInfo.STATUS_IDLE)).build();
        }

        printers.add(printerInfo);
        session.addPrinters(printers);
    }

    /**
     * Init mock print servic that returns a single printer by default.
     *
     * @param sessionRef Where to store the reference to the session once started
     */
    private void setMockPrintServiceCallbacks(
            StubbablePrinterDiscoverySession[] sessionRef,
            ArrayList<String> trackedPrinters,
            PrintAttributes.MediaSize mediaSize) {
        FirstPrintService.setCallbacks(
                createMockPrintServiceCallbacks(
                        inv ->
                                createMockPrinterDiscoverySessionCallbacks(
                                        inv2 -> {
                                            synchronized (sessionRef) {
                                                sessionRef[0] =
                                                        ((PrinterDiscoverySessionCallbacks)
                                                                        inv2.getMock())
                                                                .getSession();

                                                addPrinter(sessionRef[0], "1st printer", mediaSize);

                                                sessionRef.notifyAll();
                                            }
                                            return null;
                                        },
                                        null,
                                        null,
                                        inv2 -> {
                                            synchronized (trackedPrinters) {
                                                trackedPrinters.add(
                                                        ((PrinterId) inv2.getArguments()[0])
                                                                .getLocalId());
                                                trackedPrinters.notifyAll();
                                            }
                                            return null;
                                        },
                                        null,
                                        inv2 -> {
                                            synchronized (trackedPrinters) {
                                                trackedPrinters.remove(
                                                        ((PrinterId) inv2.getArguments()[0])
                                                                .getLocalId());
                                                trackedPrinters.notifyAll();
                                            }
                                            return null;
                                        },
                                        inv2 -> {
                                            synchronized (sessionRef) {
                                                sessionRef[0] = null;
                                                sessionRef.notifyAll();
                                            }
                                            return null;
                                        }),
                        null,
                        null));
    }

    private void setColorMode(String colorMode) throws UiObjectNotFoundException {
        mPrintHelper.openPrintOptions();
        UiObject2 color_spinner =
                getUiDevice()
                        .wait(
                                Until.findObject(By.res(COLOR_SPINNER_ID)),
                                OPERATION_TIMEOUT_MILLIS);
        assertNotNull(color_spinner);
        selectSpinnerOption(color_spinner, colorMode);
        mPrintHelper.closePrintOptions();
        getUiDevice()
                .wait(
                        Until.gone(By.res(COLOR_SPINNER_ID)),
                        OPERATION_TIMEOUT_MILLIS);
        getUiDevice().waitForIdle();
    }

    @Test
    @LargeTest
    public void previewImageMatchesColorMode() throws Exception {
        final StubbablePrinterDiscoverySession[] session = new StubbablePrinterDiscoverySession[1];
        final PrintAttributes[] printAttributes = new PrintAttributes[1];
        ArrayList<String> trackedPrinters = new ArrayList<>();

        setMockPrintServiceCallbacks(session, trackedPrinters, MediaSize.ISO_A4);
        print(printAttributes);

        // We are now in the PrintActivity
        Log.i(LOG_TAG, "Waiting for session");
        synchronized (session) {
            waitWithTimeout(() -> session[0] == null, session::wait);
        }

        setPrinter("1st printer");
        Log.i(LOG_TAG, "Waiting for 1st printer to be tracked");
        synchronized (trackedPrinters) {
            waitWithTimeout(() -> !trackedPrinters.contains("1st printer"), trackedPrinters::wait);
        }

        Log.i(LOG_TAG, "Waiting for print attributes to change");
        synchronized (printAttributes) {
            waitWithTimeout(
                    () ->
                            printAttributes[0] == null
                                    || !printAttributes[0].getMediaSize().equals(MediaSize.ISO_A4),
                    printAttributes::wait);
        }

        // The requested mode is color, so color preview should always be non-gray.
        Log.i(LOG_TAG, "Checking color preview");
        setColorMode("Color");
        UiObject2 preview =
                getUiDevice()
                        .wait(
                                Until.findObject(
                                        By.res("com.android.printspooler:id/preview_page")),
                                OPERATION_TIMEOUT_MILLIS);
        assertNotNull(preview);
        Bitmap b = UiObject2Ext.takeScreenshot(preview);
        // Test a pixel near the bottom so it won't be covered by the options UI.
        Color testPixel = b.getColor(b.getWidth() / 2, b.getHeight() * 9 / 10);
        int rgb = testPixel.toArgb();
        assertFalse(Color.red(rgb) == Color.blue(rgb) && Color.blue(rgb) == Color.green(rgb));

        // The requested mode is B&W, so preview should be some shade of gray even though the source
        // app still supplies color.
        if (Flags.grayscalePreview()) {
            Log.i(LOG_TAG, "Checking B&W preview");
            setColorMode("Black & White");
            preview =
                    getUiDevice()
                            .wait(
                                    Until.findObject(
                                            By.res("com.android.printspooler:id/preview_page")),
                                    OPERATION_TIMEOUT_MILLIS);
            assertNotNull(preview);
            b = UiObject2Ext.takeScreenshot(preview);
            testPixel = b.getColor(b.getWidth() / 2, b.getHeight() * 9 / 10);
            rgb = testPixel.toArgb();
            assertTrue(Color.red(rgb) == Color.blue(rgb) && Color.blue(rgb) == Color.green(rgb));
        }

        // Close print preview.
        getUiDevice().pressBack();

        // We are back in the test activity
        Log.i(LOG_TAG, "Waiting for session to end");
        synchronized (session) {
            waitWithTimeout(() -> session[0] != null, session::wait);
        }
    }
}
