/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.nativeservice.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Intent;
import android.nativeservice.simple.ISimpleNativeService;
import android.os.IBinder;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled({
    com.android.server.am.Flags.FLAG_ENABLE_ACTIVITY_MANAGER_STRUCTURED_SERVICE,
    android.os.Flags.FLAG_NATIVE_FRAMEWORK_PROTOTYPE
})
public class NativeServiceProcessTest {
    private static final String TAG = "NativeServiceProcessTest";
    private static final String TARGET_PACKAGE = "android.nativeservice.test";
    private static final String NATIVE_SERVICE_CLASS = "android.nativeservice.test.NativeService";
    private ISimpleNativeService mService = null;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Before
    public void setup() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(TARGET_PACKAGE, NATIVE_SERVICE_CLASS));
        try {
            IBinder binder = mServiceRule.bindService(intent);
            mService = ISimpleNativeService.Stub.asInterface(binder);
            assertNotNull(mService);
        } catch (Exception e) {
            fail("Failed to bind to service: " + e.getMessage());
        }
    }

    @Test
    public void testSELinuxContext() throws Exception {
        final int pid = mService.getPid();
        String attrOutput = SystemUtil.runShellCommand("cat /proc/" + pid + "/attr/current");
        Log.d(TAG, "attrOutput= " + attrOutput);
        String[] parts = attrOutput.trim().split(":");

        assertTrue("SELinux context format is invalid", parts.length >= 3);
        assertEquals("SELinux context type should be isolated_app", parts[2], "isolated_app");
    }

    @Test
    public void testProcStatus() throws Exception {
        final int pid = mService.getPid();
        String statusOutput = SystemUtil.runShellCommand("cat /proc/" + pid + "/status");
        Log.d(TAG, "statusOutput= " + statusOutput);
        final ProcStatusInfo info = parseProcStatus(statusOutput);

        assertTrue(
                "CapEff should be zero",
                "0000000000000000".equals(info.capEff) || "0".equals(info.capEff));
        assertTrue("Seccomp filter should be applied", "2".equals(info.seccomp));
    }

    private static class ProcStatusInfo {
        public String capEff = null;
        public String seccomp = null;
    }

    private ProcStatusInfo parseProcStatus(String statusOutput) {
        ProcStatusInfo info = new ProcStatusInfo();

        for (String line : statusOutput.split("\n")) {
            if (line.startsWith("CapEff:")) {
                info.capEff = line.split(":")[1].trim();
            } else if (line.startsWith("Seccomp:")) {
                info.seccomp = line.split(":")[1].trim();
            }
        }
        return info;
    }
}
