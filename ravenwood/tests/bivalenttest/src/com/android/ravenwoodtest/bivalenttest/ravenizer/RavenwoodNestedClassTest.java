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

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.JUnitCore;

public class RavenwoodNestedClassTest {

    private static final CallTracker sCallTracker = new CallTracker();

    @Before
    public void setup() {
        sCallTracker.reset();
    }

    @Test
    public void test1() {
        assertEquals(0, JUnitCore.runClasses(Test1.class).getFailureCount());
        sCallTracker.assertCalls("test1", 1);
    }

    public static class Test1 {
        @Test
        public void test1() {
            sCallTracker.incrementMethodCallCount();
        }
    }

    @Test
    public void test2() {
        assertEquals(0, JUnitCore.runClasses(Test2.class).getFailureCount());
        sCallTracker.assertCalls("test2", 1);
    }

    public static class Test2 {
        @Test
        public void test2() {
            sCallTracker.incrementMethodCallCount();
        }
    }
}
