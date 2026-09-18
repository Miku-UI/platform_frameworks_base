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
package com.android.ravenwoodtest.bivalenttest.ravenizer;

import android.os.SystemProperties;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.RavenwoodTestRunnerInitializing;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

public class RavenwoodJUnit3Test extends TestCase implements RavenwoodRule.Provider {
    public static final String TAG = "RavenwoodJunit3Test";

    private static final CallTracker sCallTracker = new CallTracker();

    @Override
    public RavenwoodRule getRavenwoodRule() {
        return new RavenwoodRule.Builder()
                .setSystemPropertyImmutable("a.b.c", "foo")
                .build();
    }

    private static int getExpectedRavenwoodRunnerInitializingNumCalls() {
        return RavenwoodRule.isOnRavenwood() ? 1 : 0;
    }

    @RavenwoodTestRunnerInitializing
    public static void ravenwoodRunnerInitializing() {
        // No other calls should have been made.
        sCallTracker.assertCalls();

        sCallTracker.incrementMethodCallCount();
    }

    public RavenwoodJUnit3Test() {
        // Make sure the environment is already initialized when the constructor is called
        assertNotNull(InstrumentationRegistry.getInstrumentation());
    }

    public void test1() {
        sCallTracker.assertCalls(
                "ravenwoodRunnerInitializing",
                getExpectedRavenwoodRunnerInitializingNumCalls()
        );
    }

    @DisabledOnRavenwood
    public void testDeviceOnly() {
        assertFalse(RavenwoodRule.isOnRavenwood());
        sCallTracker.assertCalls(
                "ravenwoodRunnerInitializing",
                getExpectedRavenwoodRunnerInitializingNumCalls()
        );
    }

    public void testRavenwoodRule() {
        sCallTracker.assertCalls(
                "ravenwoodRunnerInitializing",
                getExpectedRavenwoodRunnerInitializingNumCalls()
        );
        if (RavenwoodRule.isOnRavenwood()) {
            assertEquals("foo", SystemProperties.get("a.b.c"));
        }
    }
}
