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

package com.android.test.ipcrendering;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IPCVriJUnitTest {
    @Rule
    public ActivityTestRule<IPCVriTest> mActivityRule =
            new ActivityTestRule<>(IPCVriTest.class, false, false);

    @Test
    public void testIPCVriRendering() throws Throwable {
        // Enable IPC rendering via system property
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "setprop viewroot.ipc_rendering_packages com.android.test.ipcrendering");

        // Launch the activity
        IPCVriTest activity = mActivityRule.launchActivity(null);

        try {
            // Wait a bit to ensure drawing happens and animation plays
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
