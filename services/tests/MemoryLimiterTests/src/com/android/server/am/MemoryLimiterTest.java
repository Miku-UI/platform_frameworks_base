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

package com.android.server.am;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.ActivityManager.ProcessState;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.DeviceConfig;
import android.util.ArrayMap;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.am.MemoryLimiter.Configuration;
import com.android.server.am.MemoryLimiter.ControllerEnabled;
import com.android.server.am.MemoryLimiter.Limits;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build/Install/Run:
 *  atest MemoryLimiterTest
 */
@LargeTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class MemoryLimiterTest {

    private static final String TAG = "MemoryLimiterTest";

    @Rule
    public final SetFlagsRule mFlagRule = new SetFlagsRule();

    // Our context, used for sending broadcasts.
    private Context mContext = ApplicationProvider.getApplicationContext();

    // The test application package and activity name.
    // LINT.IfChange(testapp)
    private static final String HELPER =
            "com.android.memorylimitertests.apps.memorylimitertestapp";
    private static final String ACTIVITY = "TestActivity";
    // LINT.ThenChange(
    // /services/tests/MemoryLimiterTests/test-app/MemoryLimiterTestApp/AndroidManifest.xml:testapp
    // )

    // The location of data files on the device.
    private static final String DATA_DIR = "/data/local/tmp/cts/memorylimiter/";

    // Return the path to a data file.
    private static String dataFile(String file) {
        return DATA_DIR + file;
    }

    // The UID of the test application.  This can change every time the package is installed but
    // should not change for the duration of the test.
    private int mUid;

    private static String shellCommand(String cmd) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(cmd);
        try (var result = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            return new String(result.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Error running command: '" + cmd + "'", e);
        }
        return null;
    }

    // Two convenience constants, 1MB and 1GB, and a function to express GB as MB.
    static final long MB = 1024L * 1024;
    static final long GB = 1024L * MB;

    // The mapping between process states and sizes is arbitrary.  Constants are declared to
    // make it more obvious in the test routine just what the memory limit will be.
    @ProcessState
    private static final int PROCESS_STATE_FG = ActivityManager.PROCESS_STATE_SERVICE;
    @ProcessState
    private static final int PROCESS_STATE_BG = ActivityManager.PROCESS_STATE_TRANSIENT_BACKGROUND;
    @ProcessState
    private static final int PROCESS_STATE_MAX = ActivityManager.PROCESS_STATE_TOP;

    // High limits, in MB.
    private static final int LIMIT_MEM = 100;
    private static final int LIMIT_SWAP = 50;

    // The memory assigned to a process in PROCESS_STATE_FG.
    private static final long sProcessMemoryFG = LIMIT_MEM * MB;

    // The memory assigned to a process in PROCESS_STATE_BG.
    private static final long sProcessMemoryBG = (LIMIT_MEM / 2) * MB;

    // The memory assigned to a process in PROCESS_STATE_FG.
    private static final long sProcessSwapFG = LIMIT_SWAP * MB;

    // The memory assigned to a process in PROCESS_STATE_FG.
    private static final long sProcessSwapBG = LIMIT_SWAP * MB;

    // The memory assigned to a process in PROCESS_STATE_MAX.  Any negative value turns into
    // maximum memory in the native handlers, but the test uses -1 for consistency.
    private static final long sProcessMemoryMax = (long) (-1);

    // The memory that will make the process exceed mem-high.  Units are MB.
    private static final int sProcessSizeOverHigh = LIMIT_MEM + 10;

    // The memory that will make the process exceed mem-high + swap-max.  Units are MB.
    private static final int sProcessSizeOverAnon = LIMIT_MEM + LIMIT_SWAP + 10;

    // The memory margin granted to a process that exceeds memory.high.  The margin does not
    // affect the anon+swap limit but it gives the process extra headroom to run and possibly shed
    // memory.
    // TODO: This value is defined in the native layer and is not exposed to the Java layer except
    // right here, for these tests.  Expose the value formally or find a way to avoid relying on
    // it in tests.
    private static final long MEM_MARGIN = 100 * MB;

    // The limits for FG and BG processes, and the limits for an unlimited process.
    private static final Limits limitsFG = new Limits(LIMIT_MEM * MB, LIMIT_SWAP * MB);
    private static final Limits limitsBG = new Limits(LIMIT_MEM / 2 * MB, LIMIT_SWAP * MB);
    private static final Limits limitsMax = new Limits(sProcessMemoryMax, sProcessMemoryMax);

    // Return true if MemoryLimiter is running in system_server.
    private static boolean isMemoryLimiterRunning() {
        String response = shellCommand("am memory-limiter status");
        return response.contains("enabled");
    }

    // An Injector for testing.
    private class TestInjector extends MemoryLimiter.Injector {

        private final String mConfigFile;

        private final boolean mLimitMode;

        TestInjector(String config, boolean limitMode) {
            super(MemoryLimiterTest.this.mContext);
            mConfigFile = (config != null) ? dataFile(config) : super.configFile();
            mLimitMode = limitMode;
        }

        TestInjector(String config) {
            this(config, true);
        }

        TestInjector() {
            this("config-testing.xml");
        }

        @Override
        boolean isMonitoringEnabled() {
            return true;
        }

        @Override
        boolean isSwapMonitoringEnabled() {
            return true;
        }

        @Override
        boolean limitMode() {
            return mLimitMode;
        }

        @Override
        String configFile() {
            return mConfigFile;
        }

        @Override
        String getPackageNameForPid(int pid) {
            // By default return the helper package name, regardless of the pid.
            return HELPER;
        }
    }

    // Capture over-limit events.
    private class EventCounter extends MemoryLimiter.ControllerEnabled {
        // A lock that ensures the countdown latch and the event count are consistent.
        private final Object mLock = new Object();

        // A countdown latch that tests can wait on.  This is atomic so that changes to attribute
        // made in the test thread are visible in the callback thread.
        private CountDownLatch mLatch;

        // A single over-limit event.  The limit is configured limit in bytes.  The percent is the
        // percentage of available memory represented by the limit.
        record Event(int pid, int uid, int limit, long memHigh, long swapHigh) {}

        // True if the getStateLimit() should return the production limits.  The default is false,
        // which means getStateLimit() will return small, well-known limits.
        private boolean mUseRawLimits = false;

        // The events received by this counter.
        final ArrayList<Event> mEvents = new ArrayList<>();

        // The instance is created with the expected number of events.
        EventCounter(int expected) {
            this(expected, true);
        }

        // The instance is created with the expected number of events and the limit mode.
        EventCounter(int expected, boolean limitMode) {
            super(new TestInjector("config-testing.xml", limitMode));
            mLatch = new CountDownLatch(expected);
        }

        void reset(int expected) {
            synchronized (mLock) {
                mLatch = new CountDownLatch(expected);
                mEvents.clear();
            }
        }

        void useRawLimits(boolean useRaw) {
            mUseRawLimits = useRaw;
        }

        // Wait for the counter to go to zero within timeout seconds.  This cannot take the lock!
        boolean await(long timeout) {
            try {
                return mLatch.await(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "exception while waiting for event", e);
                return false;
            }
        }

        // Return the total number of events ever received by this object.
        int eventCount() {
            synchronized (mLock) {
                return mEvents.size();
            }
        }

        // Match the n'th event.  Fail the test if there is no match.
        void expect(int index, Helper helper, int limit, Limits expected) {
            final Event event = mEvents.get(index);
            assertThat(event.pid).isEqualTo(helper.getPid());
            assertThat(event.uid).isEqualTo(helper.getUid());
            assertThat(event.limit).isEqualTo(limit);
            assertThat(event.memHigh).isEqualTo(expected.memHigh());
            assertThat(event.swapHigh).isEqualTo(expected.swapHigh());
        }

        // Get the memory limit for the process state.  Use one of the process states above.
        @Override
        public Limits getStateLimit(@ProcessState int newState) {
            if (mUseRawLimits) {
                return super.getStateLimit(newState);
            }
            return switch (newState) {
                case PROCESS_STATE_BG -> limitsBG;
                case PROCESS_STATE_FG -> limitsFG;
                case PROCESS_STATE_MAX -> limitsMax;
                default -> {
                    fail("invalid state for testing: " + newState);
                    yield null;
                }
            };
        }

        // Capture an actual event.
        @Override
        public void onLimitExceeded(int pid, int uid, int type, long memHigh, long swapHigh,
                String pkg) {
            synchronized (mLock) {
                mLatch.countDown();
                mEvents.add(new Event(pid, uid, type, memHigh, swapHigh));
            }
        }

        // Complete initialization. This method launches the initialization routine and then waits
        // for initialization to complete.
        @Override
        public void onSystemReady() {
            super.onSystemReady();
            waitUntil(()-> mInitialized, 2000);
        }

        // If true, exempt the helper package from limiting.
        boolean mExemptHelper = false;

        void setHelperExempt(boolean exempt) {
            mExemptHelper = exempt;
        }

        // Possibly override the isExempt() method.  If mExemptHelper is true, then the helper
        // package is exempt, along with anything else in the exempt list.
        @Override
        public boolean isExempt(String pkg) {
            if (super.isExempt(pkg)) return true;
            return mExemptHelper && HELPER.equals(pkg);
        }

        /**
         * Fetch the status from a MemoryLimiter and return the native statistics as a hash.
         * Values that are not long are discarded.
         */
        ArrayMap<String, Long> stats() {
            // Fetch the native string.
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            dump(pw);
            pw.close();
            String statsString = sw.getBuffer().toString();

            ArrayMap<String, Long> stats = new ArrayMap<>();

            // Now dice it for the hashmap
            for (String word: statsString.split("\\s+")) {
                String[] kv = word.split("=");
                if (kv.length != 2) {
                    continue;
                }
                try {
                    stats.put(kv[0], Long.parseLong(kv[1]));
                } catch (NumberFormatException e) {
                    // Do nothing.  This is not a native statistic.
                }
            }
            return stats;
        }

        // Fetch the named statistics from the native layer.  On any error, this returns zero.
        // That simplifies the test code below.
        long stat(String key) {
            return stats().get(key);
        }
    }

    @BeforeClass
    public static void setUpEarly() throws Exception {
        shellCommand("am force-stop " + HELPER);
    }

    @Before
    public void setUp() throws Exception {
        mUid = getHelperUid();
        blockSystemLimiter(mUid, true);
    }

    @After
    public void tearDown() throws Exception {
        shellCommand("am force-stop " + HELPER);
        blockSystemLimiter(mUid, false);
    }

    // Fetch the uid of the helper application.
    private int getHelperUid() throws PackageManager.NameNotFoundException {
        PackageManager pm = mContext.getPackageManager();
        return pm.getApplicationInfo(HELPER, 0).uid;
    }

    // A single instance of a helper application.
    private class Helper {

        // The helper pid.  The uid is inherited from the parent.
        final int mPid;

        Helper() throws Exception {
            final String args = "-S -W --dismiss-keyguard";
            final String cmd = String.format("am start-activity %s -n %s/.%s",
                    args, HELPER, ACTIVITY);
            final String ans = shellCommand(cmd);
            if (!ans.contains("LaunchState: COLD")) {
                // If the app was not launched cold then it did not initialize as expected and the
                // test cannot run.  This seems to be an issue that is not related to the feature,
                // so the test is skipped with assumption-failed rather than test-failed.  The
                // results of launching the application are logged for debugging.
                Log.i(TAG, "app failed to launch: " + ans);
                assumeTrue(ans.contains("LaunchState: COLD"));
            }

            mPid = findPid();
            if (mPid < 0) {
                throw new IllegalStateException("helper process not found");
            }
            Log.i(TAG, String.format("helper at pid=%d uid=%d", mPid, mUid));
            prepareCgroup();
        }

        // Fetch the pid of the helper application.
        int findPid() {
            String pid = shellCommand("pidof " + HELPER).trim();
            if (pid == null || pid.equals("")) {
                return -1;
            }
            try {
                return Integer.parseInt(pid);
            } catch (NumberFormatException e) {
                Log.i(TAG, "helper pid not a number: " + pid);
                return -1;
            }
        }

        int getPid() {
            return mPid;
        }

        int getUid() {
            return mUid;
        }

        // Attach the Helper to the Limiter
        void attach(MemoryLimiter.Limiter limiter) {
            limiter.setUid(mUid, HELPER);
            limiter.setPid(mPid);
        }

        // Return true if the process exists.  The process must have existed for the constructor
        // to succeed, but it might no longer exist.
        boolean exists() {
            return findPid() > 0;
        }

        // Prepare the helper group by allowing world access to the cgroup tree for the helper
        // UID.  The UID is specific to the helper package, so there is no impact to any other
        // functions on the system.  This must be done every time the helper app is started because
        // the cgroup path disappears if there are no processes with the UID.
        void prepareCgroup() throws Exception {
            String path = String.format("/sys/fs/cgroup/apps/uid_%d", mUid);
            String r = shellCommand("chmod -R a+rw " + path);
            if (r != null && !r.trim().equals("")) {
                Log.i(TAG, "unprotecting " + path + ": \"" + r + "\"");
                fail("failed to prepare cgroup");
            }
        }

        // Return the path to the memcg file.  A null file returns the directory.
        String cgroupFile(String file) {
            if (file == null) {
                return String.format("/sys/fs/cgroup/apps/uid_%d/pid_%d", mUid, mPid);
            } else {
                return String.format("/sys/fs/cgroup/apps/uid_%d/pid_%d/%s", mUid, mPid, file);
            }
        }

        // Return true if the cgroup directory exists.
        boolean cgroupExists() throws Exception {
            String path = cgroupFile(null);
            return Files.exists(Paths.get(path));
        }

        // Return the contents of a cgroup file, as a string.
        String cgroupData(String which) throws Exception {
            // The system resets the protections of the memory.events file every time it
            // changes.  Rerun the prepare operation to ensure the data can be read.
            prepareCgroup();
            String path = cgroupFile(which);
            Path filePath = Paths.get(path);

            // Always specify the Charset, e.g., UTF_8.  This may throw: allow the test to fail.
            return Files.readString(filePath, StandardCharsets.UTF_8).trim();
        }

        // Return the value from a cgroup file.  As a special case, this converts the string "max"
        // to sProcessMemoryMax (aka, -1).
        private long cgroupValue(String which) {
            try {
                String value = cgroupData(which);

                // The value can be the string "max"; if so, return -1.  Otherwise, the string
                // must be a valid integer.
                if (value.equals("max")) {
                    return sProcessMemoryMax;
                } else {
                    return Long.parseLong(value);
                }
            } catch (Exception e) {
                fail(e.toString());
            }
            // Satisfy Java that the function actually returns a value.
            return -1;
        }

        // Return the current memory of the helper app, as reported by memcg.
        long currentMemory() throws Exception {
            return cgroupValue("memory.current");
        }

        // Return the current memory.high limit of the helper app, as reported by memcg.
        private long currentMemHigh() {
            return cgroupValue("memory.high");
        }

        // Wait for memory.high to meet some limit.  Return true if it meets the limit.
        boolean waitMemoryHigh(long want) {
            return waitUntil(() -> currentMemHigh() == want, 5000);
        }

        // Return the current memory.swap.max limit of the helper app, as reported by memcg.
        private long currentSwapMax() {
            return cgroupValue("memory.swap.max");
        }

        // Wait for memory.swap.max to meet some limit.  Return true if it meets the limit.
        boolean waitSwapMax(long want) {
            return waitUntil(() -> currentSwapMax() == want, 5000);
        }

        // Wait for the helper to be not-alive.
        boolean waitExited(long maxWaitMs) {
            return waitUntil(() -> !exists(), maxWaitMs);
        }

        // Return the current value for high events.
        private long currentEvents() throws Exception {
            final String value = cgroupData("memory.events");

            Matcher high = Pattern.compile("high (\\d+)").matcher(value);
            if (high.find()) {
                return Integer.parseInt(high.group(1));
            } else {
                throw new IllegalArgumentException("bad memory.events");
            }
        }

        // Send a request to the application to change its memory.  The size has unit MB.
        void resize(int size, int delay) {
            final Intent intent = new Intent(); // SendActivity.this, SendActivity.class);
            intent.setAction(HELPER + ".MEMORY");
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            intent.putExtra("size", size);
            intent.putExtra("delay", delay);
            mContext.sendBroadcast(intent);
        }

        void resize(int size) {
            resize(size, 0);
        }

        // Send a request to the application to exit.
        private void exit() {
            final Intent intent = new Intent(); // SendActivity.this, SendActivity.class);
            intent.setAction(HELPER + ".EXIT");
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            mContext.sendBroadcast(intent);
        }
    }

    // Disable the system MemoryLimiter for the UID under test.
    private static void blockSystemLimiter(int uid, boolean blocked) throws Exception {
        shellCommand("am memory-limiter ignore " + ((blocked) ? Integer.toString(uid) : "none"));
    }

    // Spin until the predicate returns true or <timeout> milliseconds have elapsed.  Return true
    // if the predicate returned true and false on timeout.  This polls every 10ms.  The function
    // throws if the sleep() throws.
    private static boolean waitUntil(BooleanSupplier f, long timeoutMs) {
        final long start = SystemClock.uptimeMillis();
        while (true) {
            if (f.getAsBoolean()) {
                return true;
            }
            final long now = SystemClock.uptimeMillis();
            if (now - start > timeoutMs) {
                return false;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // An early wake-up is not a problem.  Continue with the loop.
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiter() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_FG);
                helper.resize(sProcessSizeOverHigh);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, helper, MemoryLimiter.LIMIT_TYPE_MEMORY, limitsFG);

                // Verify that limitMode is honored.
                assertThat(helper.currentMemHigh()).isEqualTo(sProcessMemoryFG + MEM_MARGIN);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiterAnonSwap() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_FG);
                helper.resize(sProcessSizeOverHigh);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, helper, MemoryLimiter.LIMIT_TYPE_MEMORY, limitsFG);

                assertThat(helper.currentMemHigh()).isEqualTo(sProcessMemoryFG + MEM_MARGIN);
                assertThat(helper.currentSwapMax()).isEqualTo(sProcessSwapFG);

                // Grow the helper another 10M.  This should fill swap and then should exceed
                // anon+rss.
                counter.reset(1);
                helper.resize(sProcessSizeOverAnon);
                assertTrue(counter.await(300));

                // There should be one event in the counter: AnonRss
                assertThat(counter.eventCount()).isEqualTo(1);
                // The anon+swap limit.
                counter.expect(0, helper, MemoryLimiter.LIMIT_TYPE_ANON_SWAP, limitsFG);

                // The memory.high and memory.swap.high limits have been released.
                assertThat(helper.currentMemHigh()).isEqualTo(sProcessMemoryMax);
                assertThat(helper.currentSwapMax()).isEqualTo(sProcessMemoryMax);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiterSenseMode() throws Exception {
        try (EventCounter counter = new EventCounter(1, false)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_FG);
                helper.resize(sProcessSizeOverHigh);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, helper, MemoryLimiter.LIMIT_TYPE_MEMORY, limitsFG);

                // Verify that limitMode is honored.
                assertThat(helper.currentMemHigh()).isEqualTo(sProcessMemoryMax);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiterAfter() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Grow the app by 10M, set the limit, and wait for the over-limit event.
                helper.resize(sProcessSizeOverHigh);
                limiter.onProcStateUpdated(PROCESS_STATE_FG);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, helper, MemoryLimiter.LIMIT_TYPE_MEMORY, limitsFG);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testLimiterNoLimits() throws Exception {
        try (EventCounter counter = new EventCounter(0)) {
            counter.useRawLimits(true);
            Limits fg = counter.getStateLimit(PROCESS_STATE_FG);
            Limits bg = counter.getStateLimit(PROCESS_STATE_BG);

            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Update the process state and verify that memory.high is correct.
                limiter.onProcStateUpdated(PROCESS_STATE_FG);
                assertTrue(helper.waitMemoryHigh(fg.memHigh()));

                limiter.onProcStateUpdated(PROCESS_STATE_BG);
                assertTrue(helper.waitMemoryHigh(bg.memHigh()));

                // Disable limits and verify that memory.high is set to max.
                setRuntimeFlags(counter, true, null);
                assertThat(counter.mDisableLimits).isEqualTo(true);
                limiter.onProcStateUpdated(PROCESS_STATE_FG);
                assertTrue(helper.waitMemoryHigh(sProcessMemoryMax));
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testExemptList() throws Exception {
        try (EventCounter counter = new EventCounter(0)) {
            counter.useRawLimits(true);
            Limits fg = counter.getStateLimit(PROCESS_STATE_FG);
            counter.setHelperExempt(true);

            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);
                assertTrue(helper.waitMemoryHigh(sProcessMemoryMax));

                // Update the process state and verify that memory.high is correct.
                limiter.onProcStateUpdated(PROCESS_STATE_FG);
                // Unfortunately, we must wait for the process state to NOT be changed.  The
                // cgroup files are updated asynchronously, so all we can do here is wait a bit.
                assertFalse(helper.waitMemoryHigh(fg.memHigh()));
                assertTrue(helper.waitMemoryHigh(sProcessMemoryMax));
            }
        }
    }

    // Compare the two fields of a Configuration to the inputs.
    private static void testConfig(Configuration cfg, long visible, long notVisible) {
        assertThat(cfg.memVisible()).isEqualTo(visible);
        assertThat(cfg.memNotVisible()).isEqualTo(notVisible);
    }

    private static void testConfig(Configuration cfg, Configuration ref) {
        testConfig(cfg, ref.memVisible(), ref.memNotVisible());
    }

    /**
     * Test that process exit is captured.
     */
    @Test
    public void testProcessExit() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                assertThat(counter.stat("started")).isEqualTo(0);
                assertThat(counter.stat("processes")).isEqualTo(0);

                Helper helper = new Helper();
                helper.attach(limiter);
                // This call causes the native layer to start monitoring the process.  The process
                // is not monitored until the first time a limit is configured.
                limiter.onProcStateUpdated(PROCESS_STATE_MAX);

                BooleanSupplier one = () -> counter.stat("processes") == 1;
                assertThat(waitUntil(one, 4000)).isTrue();
                assertThat(counter.stat("started")).isEqualTo(1);
                assertThat(counter.stat("process-hwm")).isEqualTo(1);
                assertThat(counter.stat("watched")).isEqualTo(1);

                helper.exit();
                BooleanSupplier exit = () -> !helper.exists();
                waitUntil(exit, 4000);

                BooleanSupplier zero = () -> counter.stat("processes") == 0;
                assertThat(waitUntil(zero, 4000)).isTrue();

                var stats = counter.stats();
                Log.i(TAG, counter.stats().toString());
                assertThat(stats.get("started")).isEqualTo(1);
                assertThat(stats.get("process-hwm")).isEqualTo(1);
                assertThat(stats.get("watched")).isEqualTo(1);
                assertThat(stats.get("processes")).isEqualTo(0);
            }
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testXmlConfig() throws Exception {
        Configuration cfg;

        // The default case.
        cfg = MemoryLimiter.getConfiguration(null, 0);
        testConfig(cfg, MemoryLimiter.sDefaultConfig);

        // A valid configuration file that specifies the defaults.
        cfg = MemoryLimiter.getConfiguration(dataFile("config-default.xml"), 10 * GB);
        testConfig(cfg, 6 * GB, 3 * GB);
        cfg = MemoryLimiter.getConfiguration(dataFile("config-default.xml"), 14 * GB);
        testConfig(cfg, 8 * GB, 4 * GB);
        cfg = MemoryLimiter.getConfiguration(dataFile("config-default.xml"), 8 * GB);
        assertThat(cfg).isNull();

        // Parse an invalid XML file.  There must be an error.
        try {
            cfg = MemoryLimiter.getConfiguration(dataFile("config-error.xml"), 10 * GB);
            fail("failed to detect XML parse error");
        } catch (IllegalArgumentException e) {
            // Success.
        }
    }

    /**
     * This test starts the test application but instead of taking control from ActivityManager,
     * it verifies that the application's limits are set as expected. The test assumes that the
     * test app is not moved out of a foreground (visible) procstate before the memory limit is
     * checked.
     */
    @RequiresFlagsEnabled({Flags.FLAG_MEMORY_LIMITER_ENABLE,
            Flags.FLAG_MEMORY_LIMITER_DEFAULT_APP_LIMITS})
    @Test
    public void testOperation() throws Exception {
        assumeTrue(isMemoryLimiterRunning());

        // Use the default "enabled" controller to fetch the limit.  There is no need for a full
        // MemoryLimiter.
        final Limits expectedLimit;
        final TestInjector inj = new TestInjector();
        try (MemoryLimiter.Controller controller = new MemoryLimiter.ControllerEnabled(inj)) {
            expectedLimit = controller.getStateLimit(ActivityManager.PROCESS_STATE_TOP);
        }

        // Start by enabling system_server control over the app.
        blockSystemLimiter(mUid, false);

        Helper helper = new Helper();

        // Poll until the limit is set by system_server.
        assertTrue(helper.waitMemoryHigh(expectedLimit.memHigh()));
        assertTrue(helper.waitSwapMax(expectedLimit.swapHigh()));

        // Now test the manual shell command.  This goes through system_server so it cannot be done
        // locally.  Set the limit to 975MB, which is unlikely to be confused with a valid from the
        // currently running configuration file.
        int limitInMB = 975;
        shellCommand(String.format("am memory-limiter manual %d %d", helper.getPid(), limitInMB));
        long expected = limitInMB * MB;
        assertTrue(helper.waitMemoryHigh(expected));
        assertTrue(helper.waitSwapMax(expected));

        // Now try to exceed the limits.  Set a lower limit (40MB), otherwise the test will time
        // out as the app tries to grow.  Then grow the app and wait for the app to exit.  The
        // test completes as soon as the process exits, with a maximum wait of 120s.
        shellCommand(String.format("am memory-limiter manual %d %d", helper.getPid(), 40));
        helper.resize(100);
        assertTrue(helper.waitExited(120_000));
    }

    /**
     * This test starts the test application with system_server in control.  As soon as
     * system_server has configured a limit (the app should be TOP at this point), the test takes
     * control back, configures a much lower limit, and forces the app over-limit.  system_server
     * will generate a statsd atom in response.
     */
    @RequiresFlagsEnabled({Flags.FLAG_MEMORY_LIMITER_ENABLE,
            Flags.FLAG_MEMORY_LIMITER_DEFAULT_APP_LIMITS})
    @Test
    public void testStatsdAtom() throws Exception {
        assumeTrue(isMemoryLimiterRunning());

        // Start by enabling system_server control over the app.
        blockSystemLimiter(mUid, false);

        try (EventCounter counter = new EventCounter(1)) {
            try (MemoryLimiter controller = new MemoryLimiter(counter)) {
                MemoryLimiter.Limiter limiter = controller.newLimiter();

                Helper helper = new Helper();
                helper.attach(limiter);

                // Wait for the system server to set its limit.  Any limit that is not "max" is
                // okay.  This test only runs if default app limits are configured, and none of
                // thse are "max".
                for (int i = 0; i < 100 && helper.currentMemHigh() != sProcessMemoryMax; i++) {
                    Thread.sleep(100); // Wait a bit before polling again.
                }
                assertThat(helper.currentMemHigh()).isNotEqualTo(sProcessMemoryMax);

                if (Flags.memoryLimiterSwap()) {
                    for (int i = 0; i < 100 && helper.currentSwapMax() != sProcessMemoryMax; i++) {
                        Thread.sleep(100); // Wait a bit before polling again.
                    }
                    assertThat(helper.currentSwapMax()).isNotEqualTo(sProcessMemoryMax);
                } else {
                    // The flag is off, so the swap limit should always be "max".  Wait 200ms ,
                    // because limit are set asynchronously, and then verify that the limit is still
                    // max.
                    Thread.sleep(200);
                    assertThat(helper.currentSwapMax()).isEqualTo(sProcessMemoryMax);
                }

                // Now make system server stop watching the UID.  The native layer may still be
                // watching the pid; that's fine.
                blockSystemLimiter(mUid, true);

                // Set the limit, grow the app, and wait for the over-limit event.
                limiter.onProcStateUpdated(PROCESS_STATE_FG);
                helper.resize(sProcessSizeOverHigh);
                assertTrue(counter.await(10));

                // There should be exactly one event in the counter.
                assertThat(counter.eventCount()).isEqualTo(1);
                counter.expect(0, helper, MemoryLimiter.LIMIT_TYPE_MEMORY, limitsFG);
            }
        }
    }

    /**
     * Test the statsd rate limiter.  Care is taken always to use the
     */
    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void testStatsdAtomRateLimiter() throws Exception {
        try (EventCounter counter = new EventCounter(1)) {
            long clock = 0;

            // Start by verifying that the system allows MAX_TOKEN events at the beginning of
            // time.
            int total;
            for (total = 0; total < 100 && counter.shouldLogAtom(clock); total++) {
                // Nothing to do - this is just counting the number of permitted tokens.
            }
            assertThat(total).isEqualTo(EventCounter.MAX_TOKENS);

            // Advance the clock to 1.5 periods.  Verify that a single event is permitted.
            clock = (long) (EventCounter.TOKEN_PERIOD_MS * 1.5);
            assertThat(counter.shouldLogAtom(clock)).isTrue();
            assertThat(counter.shouldLogAtom(clock)).isFalse();

            // Advance the clock to 1.75 periods.  Verify that no event is permitted.
            clock = (long) (EventCounter.TOKEN_PERIOD_MS * 1.75);
            assertThat(counter.shouldLogAtom(clock)).isFalse();

            // Advance the clock to 2.5 periods.  Verify that a single event is permitted.
            clock = (long) (EventCounter.TOKEN_PERIOD_MS * 2.5);
            assertThat(counter.shouldLogAtom(clock)).isTrue();
            assertThat(counter.shouldLogAtom(clock)).isFalse();

            // Advance the clock to 50 periods.  Verify that MAX_TOKEN events are permitted.
            clock = EventCounter.TOKEN_PERIOD_MS * 100;
            for (total = 0; total < 100 && counter.shouldLogAtom(clock); total++) {
                // Nothing to do - this is just counting the number of permitted tokens.
            }
            assertThat(total).isEqualTo(EventCounter.MAX_TOKENS);
        }
    }

    /**
     * Verifies that setting a manual limit is a no-op when the controller is disabled.
     */
    @RequiresFlagsEnabled(Flags.FLAG_MEMORY_LIMITER_ENABLE)
    @Test
    public void setManualLimit_isNoOpWhenControllerIsDisabled() throws Exception {
        try (MemoryLimiter.ControllerDisabled controllerDisabled =
                new MemoryLimiter.ControllerDisabled()) {
            try (MemoryLimiter controller = new MemoryLimiter(controllerDisabled)) {
                Helper helper = new Helper();
                // The limit should be "max" initially because the test setup blocks the system
                // limiter from acting on the test UID.
                assertThat(helper.currentMemHigh()).isEqualTo(sProcessMemoryMax);

                // Attempt to set a manual limit. Since the controller is disabled, this should be
                // a no-op.
                controller.setManualLimit(helper.getPid(), helper.getUid(), 20);

                // The call is a synchronous no-op, so the limit should be unchanged immediately.
                assertThat(helper.currentMemHigh()).isEqualTo(sProcessMemoryMax);
            }
        }
    }

    /**
     * A small helper function to cgroup data parsing for a helper.
     */
    private long[] testParsing(Helper helper, String which) throws Exception {
        String data = helper.cgroupData(which);
        long[] expected = MemoryLimiter.testParseCgroup(which, data);
        return expected;
    }

    /**
     * Verify the native cgroup file parsing routines.  The first set of tests verifies that
     * fields are processed in order and return the expected values.
     */
    @Test
    public void testCgroupParsing() throws Exception {
        final String memEvents = """
                                 low 1
                                 high 3
                                 max 5
                                 oom 7
                                 oom_kill 9
                                 oom_group_kill 11
                                 """;
        {
            long[] expected = {1, 3, 5, 7, 9, 11};
            long[] parsed = MemoryLimiter.testParseCgroup("memory.events", memEvents);
            assertThat(Arrays.equals(expected, parsed)).isTrue();
        }

        // This is the leading fragment of a memory.stat file.  It includes all fields that the code
        // parses but not the entire file.  It includes some lines following what is parsed, just in
        // case those can cause errors.
        final String memStats = """
                                anon 110718976
                                file 1063903232
                                kernel 0
                                kernel_stack 0
                                pagetables 0
                                sec_pagetables 0
                                percpu 0
                                sock 0
                                vmalloc 0
                                shmem 2269184
                                file_mapped 671019008
                                file_dirty 0
                                file_writeback 0
                                swapcached 0
                                anon_thp 0
                                file_thp 0
                                shmem_thp 0
                                inactive_anon 85643264
                                active_anon 26238976
                                inactive_file 689344512
                                active_file 107540480
                                unevictable 265846784
                                """;
        {
            // "anon" and "shmem" are the extracted fields.
            long[] expected = {110718976, 2269184};
            long[] parsed = MemoryLimiter.testParseCgroup("memory.stat", memStats);
            assertThat(Arrays.equals(expected, parsed)).isTrue();
        }

        // Now, a negative test.  Verify that out-of-order fields are not parsed correctly.
        // Look closely: "max" and "high" have changed position.
        final String badEvents = """
                                 low 1
                                 max 5
                                 high 3
                                 oom 7
                                 oom_kill 9
                                 oom_group_kill 11
                                 """;
        {
            long[] parsed = MemoryLimiter.testParseCgroup("memory.events", badEvents);
            assertThat(parsed).isNull();
        }

        // Real-time tests: verify that the cgroup files returned from a running helper can be
        // successfully parsed.
        Helper helper = new Helper();
        assertThat(testParsing(helper, "memory.events")).isNotNull();
        assertThat(testParsing(helper, "memory.stat")).isNotNull();
    }

    // Set the runtime flags.  The inputs are Booleans; a null value is not pushed to the
    // controller.
    private void setRuntimeFlags(ControllerEnabled controller, Boolean limits, Boolean kill) {
        final String limitsKey = ControllerEnabled.DISABLE_LIMITS_KEY;
        final String killKey = ControllerEnabled.DISABLE_KILL_KEY;

        DeviceConfig.Properties.Builder updated =
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER);
        if (limits != null) {
            updated.setBoolean(limitsKey, limits);
        }
        if (kill != null) {
            updated.setBoolean(killKey, kill);
        }
        controller.updateRuntimeFlags(updated.build());
    }

    private void validateRuntimeFlags(ControllerEnabled controller) {
        final String limitsKey = ControllerEnabled.DISABLE_LIMITS_KEY;
        final String killKey = ControllerEnabled.DISABLE_KILL_KEY;
        DeviceConfig.Properties actual =
                DeviceConfig.getProperties(DeviceConfig.NAMESPACE_ACTIVITY_MANAGER,
                        limitsKey, killKey);
        for (String key : actual.getKeyset()) {
            if (limitsKey.equals(key)) {
                boolean val = actual.getBoolean(key, false);
                assertThat(controller.mDisableLimits).isEqualTo(val);
            } else if (killKey.equals(key)) {
                boolean val = actual.getBoolean(key, false);
                assertThat(controller.mDisableKill).isEqualTo(val);
            }
        }
    }

    @Test
    public void testRuntimeFlags() {
        try (EventCounter counter = new EventCounter(0)) {
            assertThat(counter.mDisableKill).isEqualTo(false);
            assertThat(counter.mDisableLimits).isEqualTo(false);

            counter.onSystemReady();
            assertThat(counter.mInitialized).isTrue();

            // Verify that the values loaded reflect the current operational state of the system,
            // whatever that might be.
            validateRuntimeFlags(counter);

            // Toggle the flags and verify that they change as expected.
            setRuntimeFlags(counter, true, false);
            assertThat(counter.mDisableKill).isEqualTo(false);
            assertThat(counter.mDisableLimits).isEqualTo(true);
            setRuntimeFlags(counter, false, true);
            assertThat(counter.mDisableKill).isEqualTo(true);
            assertThat(counter.mDisableLimits).isEqualTo(false);
        }
    }

    static {
        System.loadLibrary("servicestestjni");
    }
}
