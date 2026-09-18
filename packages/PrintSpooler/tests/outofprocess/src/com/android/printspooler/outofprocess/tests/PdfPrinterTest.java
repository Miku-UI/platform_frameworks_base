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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.print.PrintAttributes;
import android.print.PrintJob;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests that verify properties of the "Save as PDF" printer. */
@RunWith(AndroidJUnit4.class)
public class PdfPrinterTest extends PrintSpoolerBaseTest {
    private static final String LOG_TAG = PdfPrinterTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @LargeTest
    public void duplexNotSupported() throws Throwable {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        PrintJob job = print(printAttributes);
        selectPdfPrinter();

        mPrintHelper.openPrintOptions();
        getUiDevice().waitForIdle();

        // If the duplex mode spinner has a child named None and it isn't enabled,
        // then duplexing must not be available.
        UiObject2 spinner =
                getUiDevice()
                        .wait(
                                Until.findObject(
                                        By.res("com.android.printspooler:id/duplex_spinner")),
                                OPERATION_TIMEOUT_MILLIS);
        assertNotNull(spinner);
        assertEquals(1, spinner.getChildCount());
        assertNotNull(spinner.findObject(By.text("None")));
        assertFalse(spinner.isEnabled());

        closePrintOptions();

        // Close the print dialog.
        getUiDevice().pressBack();
        eventually(() -> assertTrue(job.isCancelled()));
    }

    @Test
    @LargeTest
    public void commonSizesAvailable() throws Throwable {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];
        PrintJob job = print(printAttributes);
        selectPdfPrinter();

        mPrintHelper.openPrintOptions();
        getUiDevice().waitForIdle();

        UiObject2 spinner =
                getUiDevice()
                        .wait(
                                Until.findObject(
                                        By.res("com.android.printspooler:id/paper_size_spinner")),
                                OPERATION_TIMEOUT_MILLIS);
        assertNotNull(spinner);
        for (String size : new String[] {"ISO A4", "Letter"}) {
            try {
                Log.i(LOG_TAG, "Checking for paper size " + size);
                synchronized (printAttributes) {
                    PrintAttributes old = printAttributes[0];
                    selectSpinnerOption(spinner, size);
                    getUiDevice().waitForIdle();
                    Log.d(LOG_TAG, "Waiting for print attributes to change");
                    waitWithTimeout(
                            () -> printAttributes[0] == null || printAttributes[0] == old,
                            printAttributes::wait);
                }
            } catch (UiObjectNotFoundException e) {
                assertFalse("Size " + size + " is missing", true);
            }
        }

        closePrintOptions();

        // Close the print dialog.
        getUiDevice().pressBack();
        eventually(() -> assertTrue(job.isCancelled()));
    }

    @Test
    @LargeTest
    @RequiresFlagsEnabled(com.android.printspooler.flags.Flags.FLAG_GRAYSCALE_PREVIEW)
    public void pdfOnlyOffersColor() throws Throwable {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        PrintJob job = print(printAttributes);
        selectPdfPrinter();

        mPrintHelper.openPrintOptions();
        getUiDevice().waitForIdle();

        // If the color mode spinner has a child named Color and it isn't enabled,
        // then B&W must not be available.
        UiObject2 spinner =
                getUiDevice()
                        .wait(
                                Until.findObject(
                                        By.res("com.android.printspooler:id/color_spinner")),
                                OPERATION_TIMEOUT_MILLIS);
        assertNotNull(spinner);
        assertEquals(1, spinner.getChildCount());
        assertNotNull(spinner.findObject(By.text("Color")));
        assertFalse(spinner.isEnabled());

        closePrintOptions();

        // Close the print dialog.
        getUiDevice().pressBack();
        eventually(() -> assertTrue(job.isCancelled()));
    }
}
