/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.utils;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeTrue;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.platform.test.annotations.Presubmit;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.os.TimeoutRecord;

import org.junit.Ignore;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
@Presubmit
public class AnrTimerTest {

    // A log tag.
    private static final String TAG = "AnrTimerTest";

    // The commonly used message timeout key.
    private static final int MSG_TIMEOUT = 1;

    // The test argument includes a pid and uid, and a tag.  The tag is used to distinguish
    // different message instances.  Additional fields (like "what") capture delivery information
    // that is checked by the test.
    private static class TestArg {
        final int pid;
        final int uid;

        TestArg(int pid, int uid) {
            this.pid = pid;
            this.uid = uid;
        }

        @Override
        public String toString() {
            return String.format("pid=%d uid=%d", pid, uid);
        }
    }

    // A test result: what was delivered on timer expiration.
    private record TestResult(int what, TestArg arg, int token) {

        // Convenience constructor, for when we don't care about 'what'.
        TestResult(TestArg arg, int token) {
            this(MSG_TIMEOUT, arg, token);
        }

        @Override
        public String toString() {
            return String.format("{{%s} what=%d token=%d}", arg, what, token);
        }
    }

    /** The test helper is a self-contained object for a single test. */
    private static class Helper {
        final Object mLock = new Object();

        final Handler mHandler;
        final Semaphore mSem = new Semaphore(0);
        final int mExpect;

        @GuardedBy("mLock")
        final ArrayList<TestResult> mResults = new ArrayList<>();

        @GuardedBy("mLock")
        final ArrayList<TestArg> mMessages = new ArrayList<>();
        @GuardedBy("mLock")
        final ArrayList<Thread> mThreads = new ArrayList<>();

        Helper() {
            this(1);
        }

        Helper(int expect) {
            mHandler = new Handler(Looper.getMainLooper(), this::expirationHandler);
            mExpect = expect;
        }

        /**
         * When a timer expires, the object must be a TestArg.  Update the TestArg with
         * expiration metadata and save it.
         */
        private boolean expirationHandler(Message msg) {
            synchronized (mLock) {
                TestArg arg = (TestArg) msg.obj;
                mMessages.add(arg);
                mThreads.add(Thread.currentThread());
                mResults.add(new TestResult(msg.what, arg, msg.arg1));
                mSem.release();
                return false;
            }
        }

        boolean await(long timeout) throws InterruptedException {
            return await(mExpect, timeout);
        }

        boolean await(int count, long timeout) throws InterruptedException {
            if (count <= 0) {
                throw new IllegalArgumentException("count is non-positive: " + count);
            }
            if (timeout <= 0) {
                throw new IllegalArgumentException("timeout is non-positive: " + count);
            }
            return mSem.tryAcquire(count, timeout, TimeUnit.MILLISECONDS);
        }

        int available() {
            return mSem.availablePermits();
        }

        /**
         * Return the number of messages so far.
         */
        int size() {
            synchronized (mLock) {
                return mResults.size();
            }
        }

        /**
         * Fetch the threads that delivered the messages.
         */
        Thread[] threads() {
            synchronized (mLock) {
                return mThreads.toArray(new Thread[mThreads.size()]);
            }
        }

        TestResult[] results(int want) {
            synchronized (mLock) {
                assertEquals(want, mResults.size());
                return mResults.toArray(new TestResult[mResults.size()]);
            }
        }
    }

    /**
     * An instrumented AnrTimer.
     */
    private class TestAnrTimer extends AnrTimer<TestArg> {
        private TestAnrTimer(Handler h, int key, String tag, boolean enable, boolean testMode) {
            super(h, key, tag, new AnrTimer.Args().enable(enable).testMode(testMode));
        }

        TestAnrTimer(Helper helper, boolean enable, boolean testMode) {
            this(helper.mHandler, MSG_TIMEOUT, caller(), enable, testMode);
        }

        TestAnrTimer(Helper helper, boolean enable) {
            this(helper, enable, false);
        }

        TestAnrTimer(Helper helper, AnrTimer.Args args) {
            super(helper.mHandler, MSG_TIMEOUT, caller(), args);
        }

        @Override
        public int getPid(TestArg arg) {
            return arg.pid;
        }

        @Override
        public int getUid(TestArg arg) {
            return arg.uid;
        }

        // Return the name of method that called the constructor, assuming that this function is
        // called from inside the constructor.  The calling method is used to name the AnrTimer
        // instance so that logs are easier to understand.
        private static String caller() {
            final int n = 4;
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            if (stack.length < n+1) return "test";
            return stack[n].getClassName() + "." + stack[n].getMethodName();
        }
    }

    // Compare two test results.
    void validate(TestResult expected, TestResult actual) {
        assertThat(actual).isEqualTo(expected);
    }

    // Verify that a test result matches the broken-out fields.  The what field is always the
    // timeout constant in these tests.
    void validate(TestArg arg, int token, TestResult actual) {
        assertThat(actual.what).isEqualTo(MSG_TIMEOUT);
        assertThat(actual.arg).isEqualTo(arg);
        assertThat(actual.token).isEqualTo(token);
    }

    // Compare that a test result contains the specified argument.
    void validate(TestArg expected, TestResult actual) {
        assertThat(actual.arg).isEqualTo(expected);
    }

    // Compare two arrays of TestResults.  The array lengths must be the same.
    void validate(TestResult[] expected, TestResult[] actual) {
        assertThat(actual.length).isEqualTo(expected.length);
        for (int i = 0; i < expected.length; i++) {
            if (!actual[i].equals(expected[i])) {
                Log.w(TAG, "mismatch on entry " + i);
            }
            validate(expected[i], actual[i]);
        }
    }

    /**
     * Verify that a simple expiration succeeds.  The timer is started for 10ms.  The test
     * procedure waits 5s for the expiration message, but under correct operation, the test will
     * only take 10ms
     */
    private void testSimpleTimeout(boolean enable) throws Exception {
        Helper helper = new Helper(1);
        try (TestAnrTimer timer = new TestAnrTimer(helper, enable)) {
            // One-time check that the injector is working as expected.
            assertThat(enable).isEqualTo(timer.serviceEnabled());
            TestArg t = new TestArg(1, 1);
            timer.start(t, 10);
            assertThat(helper.await(5000)).isTrue();
            TestResult[] result = helper.results(1);
            validate(t, result[0]);
        }
    }

    @Test
    public void testSimpleTimeoutDisabled() throws Exception {
        testSimpleTimeout(false);
    }

    @Test
    public void testSimpleTimeoutEnabled() throws Exception {
        testSimpleTimeout(true);
    }

    /**
     * Verify that a restarted timer is delivered exactly once.  The initial timer value is very
     * large, to ensure it does not expire before the timer can be restarted.
     */
    private void testTimerRestart(boolean enable) throws Exception {
        Helper helper = new Helper(1);
        try (TestAnrTimer timer = new TestAnrTimer(helper, enable)) {
            TestArg t = new TestArg(1, 1);
            timer.start(t, 10000);
            // Briefly pause.
            assertThat(helper.await(10)).isFalse();
            timer.start(t, 10);

            assertThat(helper.await(5000)).isTrue();
            TestResult[] result = helper.results(1);
            validate(t, result[0]);
        }
    }

    @Test
    public void testTimerRestartDisabled() throws Exception {
        testTimerRestart(false);
    }

    @Test
    public void testTimerRestartEnabled() throws Exception {
        testTimerRestart(true);
    }

    /**
     * Verify that a zero timeout is delivered on a different thread.  Repeat with a negative
     * timeout.  The order in which the timers are delivered is unpredictable (it is based on CPU
     * time during the test), so it is not checked.
     */
    private void testTimerZero(boolean enable) throws Exception {
        Helper helper = new Helper(2);
        try (TestAnrTimer timer = new TestAnrTimer(helper, enable)) {
            TestArg t1 = new TestArg(1, 1);
            timer.start(t1, 0);
            TestArg t2 = new TestArg(1, 2);
            timer.start(t2, -5);

            assertThat(helper.await(5000)).isTrue();
            assertEquals(2, helper.size());
            Thread[] threads = helper.threads();
            Thread me = Thread.currentThread();
            assertNotEquals(me, threads[0]);
            assertNotEquals(me, threads[1]);
        }
    }

    @Test
    public void testTimerZeroDisabled() throws Exception {
        testTimerZero(false);
    }

    @Test
    public void testTimerZeroEnabled() throws Exception {
        testTimerZero(true);
    }

    /**
     * Verify that if three timers are scheduled on a single AnrTimer, they are delivered in time
     * order.
     */
    private void testMultipleTimers(boolean enable) throws Exception {
        // Expect three messages.
        Helper helper = new Helper(3);
        TestArg t1 = new TestArg(1, 1);
        TestArg t2 = new TestArg(1, 2);
        TestArg t3 = new TestArg(1, 3);
        try (TestAnrTimer timer = new TestAnrTimer(helper, enable)) {
            timer.start(t1, 50);
            timer.start(t2, 60);
            timer.start(t3, 40);

            assertThat(helper.await(5000)).isTrue();
            TestResult[] result = helper.results(3);
            validate(t3, result[0]);
            validate(t1, result[1]);
            validate(t2, result[2]);
        }
    }

    @Test
    public void testMultipleTimersDisabled() throws Exception {
        testMultipleTimers(false);
    }

    @Test
    public void testMultipleTimersEnabled() throws Exception {
        testMultipleTimers(true);
    }

    /**
     * Verify that a canceled timer is not delivered.
     */
    private void testCancelTimer(boolean enable) throws Exception {
        // Expect two messages.
        Helper helper = new Helper(2);
        TestArg t1 = new TestArg(1, 1);
        TestArg t2 = new TestArg(1, 2);
        TestArg t3 = new TestArg(1, 3);
        try (TestAnrTimer timer = new TestAnrTimer(helper, enable)) {
            timer.start(t1, 200);
            timer.start(t2, 300);
            timer.start(t3, 100);
            // Briefly pause.
            assertThat(helper.await(10)).isFalse();
            timer.cancel(t1);
            // Delivery is immediate but occurs on a different thread.
            assertThat(helper.await(5000)).isTrue();
            TestResult[] result = helper.results(2);
            validate(t3, result[0]);
            validate(t2, result[1]);
        }
    }

    @Test
    public void testCancelTimerDisabled() throws Exception {
        testCancelTimer(false);
    }

    @Test
    public void testCancelTimerEnabled() throws Exception {
        testCancelTimer(true);
    }

    /**
     * Test the new manual-clock AnrTimer.  This is only tested with the feature enabled.
     */
    @Test
    public void testManualClock() throws Exception {
        assumeTrue(AnrTimer.nativeTimersSupported());

        // Expect two messages.
        Helper helper = new Helper(2);
        TestArg t1 = new TestArg(1, 1);
        TestArg t2 = new TestArg(1, 2);
        TestArg t3 = new TestArg(1, 3);
        try (TestAnrTimer timer = new TestAnrTimer(helper, true, true)) {
            timer.start(t1, 50);
            timer.start(t2, 60);
            timer.start(t3, 40);
            assertEquals(0, helper.size());

            // Briefly pause.
            timer.setTime(10);
            assertEquals(0, helper.size());

            timer.cancel(t1);
            timer.setTime(70);
            assertThat(helper.await(1000)).isTrue();

            TestResult[] result = helper.results(2);
            validate(t3, result[0]);
            validate(t2, result[1]);
        }
    }

    // For convenience, a Timer+Helper record.
    private record Stepper(TestAnrTimer timer, Helper helper) {
        private static final long DEFAULT_TIMEOUT_MS = 100;

        /**
         * A helper function that steps the timer's clock and waits for one response.  The
         * function is meant to track the arrival of new responses, so it starts by verifying that
         * there are no pending responses.  This means that on a good run, the function always
         * takes 100ms (the default timeout).
         */
        void stepAndWait(int clock) throws Exception {
            assertThat(helper.available()).isEqualTo(0);
            timer.setTime(clock);
            assertThat(helper.await(1, DEFAULT_TIMEOUT_MS)).isTrue();
            assertThat(helper.await(1, DEFAULT_TIMEOUT_MS)).isFalse();
        }
    }

    /**
     * Test the split-point feature with a single early notification.
     */
    @Test
    public void testSplitPoint() throws Exception {
        assumeTrue(AnrTimer.nativeTimersSupported());
        AnrTimer.Args args =
                new AnrTimer.Args()
                .enable(true)
                .testMode(true)
                .splitPoint(new AnrTimer.Args.SplitPoint(50, 2));

        // Wait for four events on each of two timer instances.
        Helper helper = new Helper();
        TestArg t1 = new TestArg(1, 1);
        TestArg t2 = new TestArg(1, 2);
        try (TestAnrTimer timer = new TestAnrTimer(helper, args)) {
            Stepper stepper = new Stepper(timer, helper);

            timer.start(t1, 100);
            timer.start(t2, 150);
            assertEquals(0, helper.size());

            timer.setTime(20);
            assertEquals(0, helper.size());

            stepper.stepAndWait(51);
            stepper.stepAndWait(76);
            stepper.stepAndWait(101);
            stepper.stepAndWait(151);

            assertEquals(4, helper.size());

            TestResult[] results = helper.results(4);
            validate(t1, 2, results[0]);
            validate(t2, 2, results[1]);
            validate(t1, 0, results[2]);
            validate(t2, 0, results[3]);
        }
    }

    /**
     * Test the split-point feature with multiple early notifications.
     */
    @Test
    public void testSplitPoint2() throws Exception {
        assumeTrue(AnrTimer.nativeTimersSupported());
        AnrTimer.Args args =
                new AnrTimer.Args()
                .enable(true)
                .testMode(true)
                .splitPoint(new AnrTimer.Args.SplitPoint(25, 1))
                .splitPoint(new AnrTimer.Args.SplitPoint(50, 2))
                .splitPoint(new AnrTimer.Args.SplitPoint(75, 3));

        Helper helper = new Helper();
        TestArg t1 = new TestArg(1, 1);
        TestArg t2 = new TestArg(1, 2);
        try (TestAnrTimer timer = new TestAnrTimer(helper, args)) {
            Stepper stepper = new Stepper(timer, helper);

            timer.start(t1, 100);
            timer.start(t2, 160);

            stepper.stepAndWait(25);
            stepper.stepAndWait(40);
            stepper.stepAndWait(50);
            stepper.stepAndWait(75);
            stepper.stepAndWait(80);
            stepper.stepAndWait(100);
            stepper.stepAndWait(120);
            stepper.stepAndWait(160);

            TestResult[] actual = helper.results(8);
            TestResult[] expected = {
                new TestResult(t1, 1),        // t1,  25ms (25% of 100)
                new TestResult(t2, 1),        // t2,  40ms (25% of 160)
                new TestResult(t1, 2),        // t1,  50ms (50% of 100)
                new TestResult(t1, 3),        // t1,  75ms (75% of 100)
                new TestResult(t2, 2),        // t2,  80ms (50% of 160)
                new TestResult(t1, 0),        // t1, 100ms (100% of 100)
                new TestResult(t2, 3),        // t2, 120ms (75% of 160)
                new TestResult(t2, 0)         // t2, 160ms (100% of 160)
            };
            validate(expected, actual);
        }
    }

    /**
     * Test the ExpiredTimer feature.
     */
    @Test
    public void testExpiredTimer() throws Exception {
        assumeTrue(AnrTimer.nativeTimersSupported());
        AnrTimer.Args args =
                new AnrTimer.Args()
                .enable(true)
                .testMode(true);

        Helper helper = new Helper(1);
        TestArg t1 = new TestArg(1, 1);
        TestArg t2 = new TestArg(2, 2);
        try (TestAnrTimer timer = new TestAnrTimer(helper, args)) {
            Stepper stepper = new Stepper(timer, helper);

            timer.start(t1, 100);
            stepper.stepAndWait(100);
            timer.start(t2, 100);
            stepper.stepAndWait(200);

            assertThat(timer.discard(t1)).isTrue();
            assertThat(timer.discard(t1)).isFalse();
            TimeoutRecord tr1a = TimeoutRecord.forApp("testing purposes");
            timer.accept(t1, tr1a);
            assertThat(tr1a.getExpiredTimer()).isNull();

            TimeoutRecord tr2a = TimeoutRecord.forApp("testing purposes");
            timer.accept(t2, tr2a);
            assertThat(tr2a.getExpiredTimer()).isNotNull();
            assertThat(timer.discard(t2)).isFalse();
            TimeoutRecord tr2b = TimeoutRecord.forApp("testing purposes");
            timer.accept(t2, tr2b);
            assertThat(tr2b.getExpiredTimer()).isNull();
        }
    }

    /**
     * Return the dump string.
     */
    private String getDumpOutput() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        AnrTimer.dump(pw, true);
        pw.close();
        return sw.getBuffer().toString();
    }

    /**
     * Verify the dump output.  This only applies when native timers are supported.
     */
    @Test
    public void testDumpOutput() throws Exception {
        assumeTrue(AnrTimer.nativeTimersSupported());

        // The timers in this class are named "class.method".
        final String timerName = "timer: com.android.server.utils.AnrTimerTest";

        String r1 = getDumpOutput();
        assertThat(r1).doesNotContain(timerName);

        Helper helper = new Helper(2);
        TestArg t1 = new TestArg(1, 1);
        TestArg t2 = new TestArg(1, 2);
        TestArg t3 = new TestArg(1, 3);
        try (TestAnrTimer timer = new TestAnrTimer(helper, true, true)) {
            timer.start(t1, 5000);
            timer.start(t2, 5000);
            timer.start(t3, 5000);

            // Do not advance the clock.

            String r2 = getDumpOutput();
            assertThat(r2).contains(timerName);
        }

        String r3 = getDumpOutput();
        assertThat(r3).doesNotContain(timerName);
    }

    /**
     * Verify that GC works as expected.  This test will almost certainly be flaky, since it
     * relies on the finalizers running, which is a best-effort on the part of the JVM.
     * Therefore, the test is marked @Ignore.  Remove that annotation to run the test locally.
     */
    @Ignore
    @Test
    public void testGarbageCollection() throws Exception {
        String r1 = getDumpOutput();
        assertThat(r1).doesNotContain("timer:");

        Helper helper = new Helper(2);
        TestArg t1 = new TestArg(1, 1);
        TestArg t2 = new TestArg(1, 2);
        TestArg t3 = new TestArg(1, 3);
        // The timer is explicitly not closed.  It is, however, scoped to the next block.
        {
            TestAnrTimer timer = new TestAnrTimer(helper, true);
            timer.start(t1, 5000);
            timer.start(t2, 5000);
            timer.start(t3, 5000);

            String r2 = getDumpOutput();
            assertThat(r2).contains("timer:");
        }

        // Try to make finalizers run.  The timer object above should be a candidate.  Finalizers
        // are run on their own thread, so pause this thread to give that thread some time.
        String r3 = getDumpOutput();
        for (int i = 0; i < 10 && r3.contains("timer:"); i++) {
            Log.i(TAG, "requesting finalization " + i);
            System.gc();
            System.runFinalization();
            Thread.sleep(4 * 1000);
            r3 = getDumpOutput();
        }

        // The timer was not explicitly closed but it should have been implicitly closed by GC.
        assertThat(r3).doesNotContain("timer:");
    }

    // TODO: [b/302724778] Remove manual JNI load
    static {
        System.loadLibrary("servicestestjni");
    }
}
