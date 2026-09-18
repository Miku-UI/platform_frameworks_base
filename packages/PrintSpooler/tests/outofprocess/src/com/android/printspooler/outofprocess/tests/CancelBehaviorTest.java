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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.print.PrintAttributes;
import android.print.PrintJob;
import android.view.KeyEvent;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests that verify ways to cancel printing. */
@RunWith(AndroidJUnit4.class)
public class CancelBehaviorTest extends PrintSpoolerBaseTest {
    private static final String LOG_TAG = CancelBehaviorTest.class.getSimpleName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @LargeTest
    public void backCancelsJob() throws Throwable {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];
        PrintJob job = print(printAttributes);
        selectPdfPrinter();

        getUiDevice().waitForIdle();
        getUiDevice().pressBack();

        eventually(() -> assertTrue(job.isCancelled()));
    }

    @Test
    @LargeTest
    public void escapeCancelsJob() throws Throwable {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];
        PrintJob job = print(printAttributes);
        selectPdfPrinter();

        getUiDevice().waitForIdle();
        getUiDevice().pressKeyCode(KeyEvent.KEYCODE_ESCAPE);

        eventually(() -> assertTrue(job.isCancelled()));
    }

    @Test
    @LargeTest
    @RequiresFlagsEnabled(com.android.printspooler.flags.Flags.FLAG_UPDATED_BUTTON_LAYOUT)
    public void cancelButtonCancelsJob() throws Throwable {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        PrintJob job = print(printAttributes);
        selectPdfPrinter();

        getUiDevice().waitForIdle();

        UiObject2 button =
                getUiDevice()
                        .wait(
                                Until.findObject(
                                        By.res("com.android.printspooler:id/cancel_button")),
                                OPERATION_TIMEOUT_MILLIS);
        assertNotNull(button);
        button.click();

        eventually(() -> assertTrue(job.isCancelled()));
    }
}
