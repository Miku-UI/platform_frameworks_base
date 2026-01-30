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
package com.android.ravenwoodtest.coretest;

import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Tests related to uncaught exception from BG threads.
 *
 * Unfortunately, we can't programmatically verify the results, so all tests are disabled.
 *
 * In order to run the test, remove @Ignore and run.
 */
public class RavenwoodExceptionTest {
    // If this test is executed after a crashing test, this would inherit the failure and fail too.
    @Test
    public void test01Pass() {
    }

    // If this test is executed after a crashing test, this would inherit the failure and fail too.
    @Test
    public void test10Pass() {
    }

    /**
     * For manual testing. We can't use "(expected = Exception.class)" because the exception is
     * thrown by an "outer" runner.
     *
     * This test should fail with something like this, but it shouldn't affect any subsequent tests.
     *
STACKTRACE:
java.lang.Exception: Exception detected on thread Thread-0:  *** Continuing the test because it's recoverable ***
at android.platform.test.ravenwood.RavenwoodDriver.makeRecoverableExceptionInstance(RavenwoodDriver.java:588)
at android.platform.test.ravenwood.RavenwoodDriver.onUncaughtException(RavenwoodDriver.java:763)
at java.base/java.lang.ThreadGroup.uncaughtException(ThreadGroup.java:695)
at java.base/java.lang.ThreadGroup.uncaughtException(ThreadGroup.java:690)
at java.base/java.lang.Thread.dispatchUncaughtException(Thread.java:2901)
Caused by: java.lang.AssertionError: Expected Exception
at org.junit.Assert.fail(Assert.java:89)
at com.android.ravenwoodtest.coretest.RavenwoodExceptionTest.lambda$testBgCrashBenign$0(RavenwoodExceptionTest.java:39)
at java.base/java.lang.Thread.run(Thread.java:1583)
     */
    @org.junit.Ignore
    @Test
    public void testBgCrashBenign() throws Exception {
        new Thread(() -> {
            fail("Expected Exception");
        }).start();
        Thread.sleep(1000);
    }

    /**
     * For manual testing. We can't use "(expected = Exception.class)" because the exception is
     * thrown by an "outer" runner.
     *
     * This test and all sbusequent tests should fail with:

 STACKTRACE:
 java.lang.Exception: Uncaught exception detected on thread Thread[#111,Thread-0,5,main], test=testBgCrash(com.android.ravenwoodtest.coretest.RavenwoodExceptionTest): java.lang.RuntimeException: Expected Exception
 at com.android.ravenwoodtest.coretest.RavenwoodExceptionTest.lambda$testBgCrash$1(RavenwoodExceptionTest.java:68)
 at java.base/java.lang.Thread.run(Thread.java:1583)
 ; Failing all subsequent tests
 at android.platform.test.ravenwood.RavenwoodDriver.onUncaughtException(RavenwoodDriver.java:771)
 at java.base/java.lang.ThreadGroup.uncaughtException(ThreadGroup.java:695)
 at java.base/java.lang.ThreadGroup.uncaughtException(ThreadGroup.java:690)
 at java.base/java.lang.Thread.dispatchUncaughtException(Thread.java:2901)
 Caused by: java.lang.RuntimeException: Expected Exception
 at com.android.ravenwoodtest.coretest.RavenwoodExceptionTest.lambda$testBgCrash$1(RavenwoodExceptionTest.java:68)
 at java.base/java.lang.Thread.run(Thread.java:1583)

     */
    @org.junit.Ignore
    @Test
    public void testBgCrash() throws Exception {
        new Thread(() -> {
            throw new RuntimeException("Expected Exception");
        }).start();
        Thread.sleep(1000);
    }
}
