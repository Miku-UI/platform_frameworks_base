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

package com.android.server.accessibility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.os.SystemProperties;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.TestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AccessibilityLogUtilTest {

    private static final String TAG = AccessibilityLogUtilTest.class.getSimpleName();
    private static final String LOCAL_TAG = "MyTestTag";
    private static final String GLOBAL_TAG = AccessibilityLogUtil.TAG;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Instrumentation mInstrumentation;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        // Reset log properties to a default state (e.g., INFO) before each test
        setLoggable(LOCAL_TAG, "INFO");
        setLoggable(GLOBAL_TAG, "INFO");
    }

    private void setLoggable(String tag, String level) {
        try {
            mInstrumentation.getUiAutomation().executeShellCommand(
                    "setprop log.tag." + tag + " " + level);
            TestUtils.waitUntil(
                    "Waiting for log.tag." + tag + " to be set to " + level,
                    () -> level.equalsIgnoreCase(SystemProperties.get("log.tag." + tag))
            );
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error trying to apply force invert state. " + e);
            e.printStackTrace();
        }
    }

    @Test
    public void isDebugEnabled_localTagDebuggable_shouldReturnTrue() {
        setLoggable(LOCAL_TAG, "DEBUG");
        setLoggable(GLOBAL_TAG, "INFO");

        assertTrue(AccessibilityLogUtil.isDebugEnabled(LOCAL_TAG));
        assertFalse(AccessibilityLogUtil.isDebugEnabled(GLOBAL_TAG));
    }

    @Test
    public void isDebugEnabled_globalTagDebuggable_shouldReturnTrue() {
        setLoggable(LOCAL_TAG, "INFO");
        setLoggable(GLOBAL_TAG, "DEBUG");

        assertTrue(AccessibilityLogUtil.isDebugEnabled(LOCAL_TAG));
        assertTrue(AccessibilityLogUtil.isDebugEnabled(GLOBAL_TAG));
    }

    @Test
    public void isDebugEnabled_bothTagsDebuggable_shouldReturnTrue() {
        setLoggable(LOCAL_TAG, "DEBUG");
        setLoggable(GLOBAL_TAG, "DEBUG");

        assertTrue(AccessibilityLogUtil.isDebugEnabled(LOCAL_TAG));
        assertTrue(AccessibilityLogUtil.isDebugEnabled(GLOBAL_TAG));
    }

    @Test
    public void isDebugEnabled_neitherTagDebuggable_shouldReturnFalse() {
        setLoggable(LOCAL_TAG, "INFO");
        setLoggable(GLOBAL_TAG, "WARN");

        assertFalse(AccessibilityLogUtil.isDebugEnabled(LOCAL_TAG));
        assertFalse(AccessibilityLogUtil.isDebugEnabled(GLOBAL_TAG));
    }
}
