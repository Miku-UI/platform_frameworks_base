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
package android.app;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.privatecompute.flags.Flags;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Process;
import android.permission.IPermissionManager;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Rule;
import org.junit.Test;

public class ActivityManagerTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(AppGlobals.class)
            .build();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testCheckComponentPermission() throws Exception {
        IPackageManager packageManager = mock(IPackageManager.class);
        IPermissionManager permissionManager = mock(IPermissionManager.class);

        doReturn(packageManager).when(() -> AppGlobals.getPackageManager());
        doReturn(permissionManager).when(() -> AppGlobals.getPermissionManager());

        final int appUid1 = 10001;
        final int pccUid1 = Process.FIRST_PCC_UID;
        final int appUid2 = 10002;
        final String permission = "android.permission.INTERNET";

        when(packageManager.getAppUidForPrivateComputeCoreUid(pccUid1)).thenReturn(appUid1);

        // --- System and Root UID ---
        assertThat(ActivityManager.checkComponentPermission(permission, Process.SYSTEM_UID, -1, -1,
                true)).isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(ActivityManager.checkComponentPermission(permission, Process.ROOT_UID, -1, -1,
                true)).isEqualTo(PackageManager.PERMISSION_GRANTED);

        // --- Caller is Owner ---
        assertThat(ActivityManager.checkComponentPermission(null, appUid1, -1, appUid1,
                false)).isEqualTo(PackageManager.PERMISSION_GRANTED);

        // --- PCC and non-PCC UIDs (same app) ---
        // Caller is PCC, owner is not
        assertThat(ActivityManager.checkComponentPermission(null, pccUid1, -1, appUid1,
                false)).isEqualTo(PackageManager.PERMISSION_GRANTED);
        // Caller is not PCC, owner is
        assertThat(ActivityManager.checkComponentPermission(null, appUid1, -1, pccUid1,
                false)).isEqualTo(PackageManager.PERMISSION_GRANTED);


        // --- PCC and non-PCC UIDs (different apps) ---
        // Caller is PCC, owner is not
        assertThat(ActivityManager.checkComponentPermission(null, pccUid1, -1, appUid2,
                false)).isEqualTo(PackageManager.PERMISSION_DENIED);
        // Caller is not PCC, owner is
        assertThat(ActivityManager.checkComponentPermission(null, appUid2, -1, pccUid1,
                false)).isEqualTo(PackageManager.PERMISSION_DENIED);

        // --- Exported component ---
        assertThat(ActivityManager.checkComponentPermission(null, appUid2, -1, appUid1,
                true)).isEqualTo(PackageManager.PERMISSION_GRANTED);

        // --- Non-exported component, no permission required ---
        assertThat(ActivityManager.checkComponentPermission(null, appUid2, -1, appUid1,
                false)).isEqualTo(PackageManager.PERMISSION_DENIED);

        // --- Permission granted ---
        when(permissionManager.checkUidPermission(eq(appUid2), eq(permission), eq(-1)))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        assertThat(ActivityManager.checkComponentPermission(permission, appUid2, -1, appUid1,
                true)).isEqualTo(PackageManager.PERMISSION_GRANTED);

        // --- Permission denied ---
        when(permissionManager.checkUidPermission(eq(appUid2), eq(permission), eq(-1)))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertThat(ActivityManager.checkComponentPermission(permission, appUid2, -1, appUid1,
                true)).isEqualTo(PackageManager.PERMISSION_DENIED);
    }
}
