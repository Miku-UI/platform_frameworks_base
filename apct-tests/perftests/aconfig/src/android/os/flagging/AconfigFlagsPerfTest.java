/*
 * Copyright 2025 The Android Open Source Project
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

package android.os.flagging;

import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class AconfigFlagsPerfTest {

    @Rule public PerfStatusReporter mPerfStatusReporter = new PerfStatusReporter();

    // Note that we've manually unrolled the loop into 20 calls per iteration, to isolate
    // marginal performance, smooth out jitter, and avoid timing integer truncation issues.
    @Test
    public void timePlatformAconfigreadWriteFlagForTest() {
        com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
            com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
        }
    }

    @Test
    public void timeAconfigreadWriteFlagForTest() {
        com.android.server.deviceconfig.Flags.readWriteFlagForTest();
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
            com.android.server.deviceconfig.Flags.readWriteFlagForTest();
        }
    }

    @Test
    public void timePlatformAconfigreadOnlyFlagForTest() {
        com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
            com.android.aconfig_new_storage.Flags.readOnlyFlagForTest();
        }
    }

    @Test
    public void timeAconfigreadOnlyFlagForTest() {
        com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
            com.android.server.deviceconfig.Flags.readOnlyFlagForTest();
        }
    }

    @Test
    public void timeNonAconfigreadOnlyFlagForTestBaseline() {
        NonAconfigFlags.readOnlyFlagForTest();
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
            NonAconfigFlags.readOnlyFlagForTest();
        }
    }

    @Test
    public void timeNonAconfigreadWriteFlagForTestBaseline() {
        NonAconfigFlags.readWriteFlagForTest();
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        while (state.keepRunning()) {
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
            NonAconfigFlags.readWriteFlagForTest();
        }
    }
}
