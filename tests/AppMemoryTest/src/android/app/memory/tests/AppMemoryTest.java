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

package android.app.memory.tests;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@RunWith(Parameterized.class)
public class AppMemoryTest {

    private static final String TAG = "AppMemoryTest";

    // The well-known test directory.
    private final String mRootPath = "/data/local/tmp/appmemorytest/";

    // The helper application APK, located in the test directory.
    private static final String HELPER_APK = "AppMemoryTest_Helper.apk";

    // The application that boots and then generates its own heap dump.
    private static final String HELPER = "android.app.memory.testhelper";

    private static String runShellCommandWithResult(String cmd) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(cmd);
        try (var result = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            byte[] response = new byte[100];
            int len = result.read(response);
            if (len <= 0) {
                return "";
            }
            return new String(response, 0, len);
        } catch (IOException e) {
            // Ignore the exception.
        }
        return null;
    }

    private static void uninstallHelper() {
        // The result string is not important but the pm executable will fail if the result is not
        // consumed.
        String r = runShellCommandWithResult("pm uninstall " + HELPER);
        Log.i(TAG, "uninstalled " + HELPER + ": " + r);
    }

    @BeforeClass
    public static void setUpForTest() {
        // Ensure that the helper app is uninstalled before the first test runs.  Note that the
        // app will normally not be installed, so the command will log an error into logcat;
        // ignore the logcat - it is expected.
        uninstallHelper();
    }

    @Before
    public void setUp() {
        runShellCommandWithResult("setprop debug.allocTracker.stackDepth 64");
        final String apk = mRootPath + HELPER_APK;
        File apkFile = new File(apk);
        assertTrue("apk not found", apkFile.exists());
        // The result string is not important but the pm executable will fail if the result is not
        // consumed.
        String r = runShellCommandWithResult("pm install -t " + apk);
        Log.i(TAG, "installed " + apk + ": " + r);
    }

    @After
    public void tearDown() {
        runShellCommandWithResult("setprop debug.allocTracker.stackDepth 16");
        runShellCommandWithResult("am force-stop " + HELPER);
        uninstallHelper();
    }

    // How long the process should wait before triggering a heap dump.  This delay should be long
    // enough that all allocations have completed.
    private static final long DELAY_MS = 10 * 1000;

    // Set the variable true to enable a calibration run, which repeats the test with a series of
    // ever larger allocations in the app.  The results should reflect the extra allocations quite
    // closely.
    private static boolean sCalibration = false;

    // The interesting parameter is the "extra-size"
    @Parameter(0)
    public int mExtraSize;

    /**
     * Return the parameter list for the run.  The result depends on sCalibration: if it is false,
     * run a baseline test (no extra memory).  If it is true, then run a sequence of tests with
     * increasing extra allocations.
     */
    @Parameters(name = "extra-size: {0}")
    public static Iterable<? extends Object> data() {
        if (sCalibration) {
            return Arrays.asList(new Object[][]{ {0}, {1}, {256}, {512}, {1024}, {4096} });
        } else {
            return Arrays.asList(new Object[][]{ {0} });
        }
    }

    // Launch the test app with the specified parameters and create one heap profile.  Metrics are
    // extracted and reported.
    private void generateOneProfile(long delay, int extra) throws Exception {
        // Compute the suffix for this particular test.  The suffix is just the amount of extra
        // memory divided by 1024.
        final String suffix = String.format("-%04d", extra / 1024);
        final String path = mRootPath + "jheap" + suffix;
        final String profilePath = mRootPath + "jheap" + suffix + ".hprof";
        final File profileFile = new File(profilePath);

        // Ensure clean state
        profileFile.delete();

        // -S stops any existing activity before starting new, ensuring cold start
        // -W waits until activity has drawn its first frame
        String cmd = new String("am start-activity -S -W --track-allocation ")
                     + String.format("-n %s/.EmptyActivity ", HELPER);

        Log.i(TAG, "starting helper activity");
        Log.i(TAG, cmd);
        runShellCommandWithResult(cmd);

        // without this sleep, pidof errors out, meaning maybe when we wait with -W
        // perhaps when the first frame is drawn the pid is still not assigned?
        // also serves as a soak delay
        Thread.sleep(15 * 1000);

        // get PID of the helper app
        String pid = runShellCommandWithResult("pidof " + HELPER).trim();
        assertNotNull("Could not get PID for package " + HELPER, pid);
        Log.i(TAG, "Got PID for " + HELPER + ": " + pid);
        assertTrue("PID is not a valid number: " + pid, pid.matches("\\d+"));
        Log.i(TAG, "Got PID for " + HELPER + ": " + pid);

        // trigger the heap dump
        cmd = String.format("am dumpheap -b png %s %s", pid, profilePath);
        Log.i(TAG, "Executing heap dump command: " + cmd);
        runShellCommandWithResult(cmd);

        // make file readable
        runShellCommandWithResult("chmod 666 " + profilePath);

        // verify dump exists and readable
        assertTrue("Heap profile does not exist: " + profilePath, profileFile.exists());
        assertTrue("Heap profile is not readable: " + profilePath, profileFile.canRead());
        Log.i(TAG, "Heap dump successfully created at: " + profilePath);

        // without this sleep, am dumpheap creates a file but it is still writing to it.
        // if Profile() reads it too early, then readVersion() triggers an overflow error.
        Thread.sleep(15 * 1000);


        // Extract metrics and report them
        Profile p = new Profile(profileFile);
        final long p_allocated = p.size();

        // Log the parsed size for debugging purposes.
        Log.i(TAG, "Profile-parsed heap size (PSize): " + p_allocated);

        // Send metrics to the automation system.
        Bundle stats = new Bundle();
        String key = "PSize" + suffix;
        stats.putLong(key, p_allocated);
        stats.putString(Instrumentation.REPORT_KEY_STREAMRESULT, key + ": " + p_allocated);
        InstrumentationRegistry.getInstrumentation().sendStatus(Activity.RESULT_OK, stats);
    }

    // Test the basic app with the specified extra memory.
    @Test
    public void testApp() throws Exception {
        generateOneProfile(DELAY_MS, mExtraSize * 1024);
    }
}
