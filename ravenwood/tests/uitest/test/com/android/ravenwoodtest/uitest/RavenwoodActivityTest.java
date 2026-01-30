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
package com.android.ravenwoodtest.uitest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.platform.test.ravenwood.RavenwoodExperimentalApiChecker;
import android.platform.test.ravenwood.RavenwoodRule;
import android.platform.test.ravenwood.RavenwoodUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.WindowUtil;

import org.junit.Test;

/**
 * Simple tests with {@link Activity}.
 */
public class RavenwoodActivityTest {
    public static void assumeExperimentalApiAvailable() {
        if (RavenwoodRule.isOnRavenwood()) {
            assumeTrue(RavenwoodExperimentalApiChecker.isExperimentalApiEnabled());
        }
    }

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private static final Context sContext = sInstrumentation.getContext();

    @Test
    public void testResourcesAvailable() {
        assertEquals("Test string", sContext.getString(R.string.test_string));
    }

    @Test
    public void testExperimentalApi() {
        assumeExperimentalApiAvailable();

        // This API is an experimental API.
        sInstrumentation.getTargetContext().isUiContext();
    }

    @Test
    public void testStartActivity() {
        assumeExperimentalApiAvailable();

        Intent intent = new Intent(sContext, TestActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Activity act = sInstrumentation.startActivitySync(intent);
        assertThat(act).isNotNull();
        try {
            // Check some basic states...
            assertThat(act).isInstanceOf(TestActivity.class);
            assertThat(act.getPackageName()).isEqualTo(sContext.getPackageName());
            assertThat(act.isResumed()).isTrue();

            sInstrumentation.waitForIdleSync();

            WindowUtil.waitForFocus(act);

        } finally {
            RavenwoodUtils.runOnMainThreadSync(act::finish);
        }
    }

    @Test
    public void testWithContentView() {
        assumeExperimentalApiAvailable();

        Intent intent = new Intent(sContext, TestViewActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Activity act = sInstrumentation.startActivitySync(intent);
        assertThat(act).isNotNull();
        try {
            assertThat(act).isInstanceOf(TestViewActivity.class);
            assertThat(act.getPackageName()).isEqualTo(sContext.getPackageName());
            assertThat(act.isResumed()).isTrue();

            sInstrumentation.waitForIdleSync();
        } finally {
            RavenwoodUtils.runOnMainThreadSync(act::finish);
        }
    }
}
