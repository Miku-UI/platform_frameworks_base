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
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class AppMemoryTest {

    private static final String TAG = "AppMemoryTest";

    // The well-known test directory.
    private final String mRootPath = "/data/local/tmp/appmemorytest/";

    // The helper application APK, located in the test directory.
    private static final String HELPER_APK = "AppMemoryTest_Helper.apk";

    // The application that boots and then generates its own heap dump.
    private static final String HELPER = "android.app.memory.testhelper";

    private static final String STACK_DEPTH_PROP = "debug.allocTracker.stackDepth";

    private static String runShellCommandWithResult(String cmd) {
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(cmd);
        try (var result = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            return new String(result.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            Log.e(TAG, "Error running command: '" + cmd + "'", e);
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
        runShellCommandWithResult("setprop " + STACK_DEPTH_PROP + " 64");
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
        // TODO: b/445722486 - runShellCommand doesn't work with empty string
        // Set to 0 for now, but ideally want a true reset
        runShellCommandWithResult("setprop " + STACK_DEPTH_PROP + " 0");
    }

    @AfterClass
    public static void tearDownClass() {
        runShellCommandWithResult("am force-stop " + HELPER);
        uninstallHelper();
    }

    // Launch the test app with the specified parameters and create one heap profile.  Metrics are
    // extracted and reported.
    private void generateOneProfile() throws Exception {
        final String profilePath = mRootPath + "jheap.hprof";
        final File profileFile = new File(profilePath);

        // Ensure clean state
        profileFile.delete();

        // -S stops any existing activity before starting new, ensuring cold start
        // -W waits until activity has drawn its first frame
        String cmd = new String("am start-activity -S -W --track-allocation ")
                     + String.format("-n %s/.EmptyActivity ", HELPER);

        Log.i(TAG, "starting helper activity");
        String r = runShellCommandWithResult(cmd);
        Log.i(TAG, "started activity with result: " + r);

        // get PID of the helper app
        String pid = runShellCommandWithResult("pidof " + HELPER).trim();
        assertNotNull("Could not get PID for package " + HELPER, pid);
        Log.i(TAG, "Got PID for " + HELPER + ": " + pid);
        assertTrue("PID is not a valid number: " + pid, pid.matches("\\d+"));
        Log.i(TAG, "Got PID for " + HELPER + ": " + pid);

        // before java app heap dump, trigger native heap dump by perfetto
        cmd = "killall -USR1 heapprofd";
        r = runShellCommandWithResult(cmd);

        // trigger the heap dump
        cmd = String.format("am dumpheap -b png %s %s", pid, profilePath);
        Log.i(TAG, "Executing heap dump command: " + cmd);
        r = runShellCommandWithResult(cmd);
        Log.i(TAG, "heap dump command executed with result:  " + r);

        // make file readable
        r = runShellCommandWithResult("chmod 666 " + profilePath);
        Log.i(TAG, "chmod command executed with result:  " + r);

        // verify dump exists and readable
        assertTrue("Heap profile does not exist: " + profilePath, profileFile.exists());
        assertTrue("Heap profile is not readable: " + profilePath, profileFile.canRead());
        Log.i(TAG, "Heap dump successfully created at: " + profilePath);

        // Extract metrics and report them
        Profile profile = new Profile(profileFile);
        final long profileAllocated = profile.size();
        final long profileCount = profile.count();

        // Log the parsed size for debugging purposes.
        Log.i(TAG, "Profile-parsed heap size (PSize): " + profileAllocated);
        Log.i(TAG, "Profile-parsed object count (PCount): " + profileCount);

        // Send metrics to the automation system.
        Bundle stats = new Bundle();
        String key = "appmemorytest_app_heap_size_bytes";
        stats.putLong(key, profileAllocated);
        stats.putString(Instrumentation.REPORT_KEY_STREAMRESULT, key + ": " + profileAllocated);
        key = "appmemorytest_app_heap_count";
        stats.putLong(key, profileCount);
        stats.putString(Instrumentation.REPORT_KEY_STREAMRESULT, key + ": " + profileCount);
        InstrumentationRegistry.getInstrumentation().sendStatus(Activity.RESULT_OK, stats);
    }

    // Test the basic app
    @Test
    public void testApp() throws Exception {
        generateOneProfile();
    }
}
