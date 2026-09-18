/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.SystemProperties;
import android.platform.test.ravenwood.RavenwoodRule;
import android.util.Log;

import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Test to make sure {@link Suite} works with the ravenwood test runner.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        RavenwoodSuiteTest.Test1.class,
        RavenwoodSuiteTest.Test2.class
})
public class RavenwoodSuiteTest {
    public static final String TAG = "RavenwoodSuiteTest";

    private static final String TEST_1_PROP = "a.b.test1";
    private static final String TEST_2_PROP = "a.b.test2";

    private static final CallTracker sCallTracker = new CallTracker();

    @AfterClass
    public static void afterClass() {
        Log.i(TAG, "afterClass called");

        sCallTracker.assertCallsOrDie(
                "test1", 1,
                "test2", 1
        );
    }

    /**
     * Workaround for the issue where tradefed won't think a class is a test class
     * if it has a @RunWith but no @Test methods, even if it is a Suite.
     * TODO(b/466095588) Remove once we have a way to ensure the expected tests are executed.
     */
    @Test
    public void testEmpty() {
    }

    public static class Test1 {
        @Rule
        public RavenwoodRule mRule = new RavenwoodRule.Builder()
                .setSystemPropertyImmutable(TEST_1_PROP, "foo")
                .build();

        @Test
        public void test1() {
            sCallTracker.incrementMethodCallCount();
            if (RavenwoodRule.isOnRavenwood()) {
                assertEquals("foo", SystemProperties.get(TEST_1_PROP));
                assertThrows(IllegalArgumentException.class,
                        () -> SystemProperties.get(TEST_2_PROP));
            }
        }
    }

    public static class Test2 {
        @Rule
        public RavenwoodRule mRule = new RavenwoodRule.Builder()
                .setSystemPropertyImmutable(TEST_2_PROP, "foo")
                .build();

        @Test
        public void test2() {
            sCallTracker.incrementMethodCallCount();
            if (RavenwoodRule.isOnRavenwood()) {
                assertEquals("foo", SystemProperties.get(TEST_2_PROP));
                assertThrows(IllegalArgumentException.class,
                        () -> SystemProperties.get(TEST_1_PROP));
            }
        }
    }
}
