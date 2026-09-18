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

package com.android.server.uri;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.NULL_DEFAULT;

import static com.android.server.uri.UriGrantsMockContext.PKG_PCC;
import static com.android.server.uri.UriGrantsMockContext.PKG_PCC_TRUSTED;
import static com.android.server.uri.UriGrantsMockContext.UID_PRIMARY_CAMERA;
import static com.android.server.uri.UriGrantsMockContext.UID_PRIMARY_PCC;
import static com.android.server.uri.UriGrantsMockContext.UID_PRIMARY_PCC_TRUSTED;
import static com.android.server.uri.UriGrantsMockContext.UID_PRIMARY_SOCIAL;
import static com.android.server.uri.UriGrantsMockContext.URI_PCC;
import static com.android.server.uri.UriGrantsMockContext.URI_PCC_TRUSTED;
import static com.android.server.uri.UriGrantsMockContext.URI_PHOTO_1;
import static com.android.server.uri.UriGrantsMockContext.URI_PUBLIC;
import static com.android.server.uri.UriGrantsMockContext.USER_PRIMARY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.server.LocalServices;
import com.android.server.privatecompute.PccSandboxManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.List;

@RunWith(Parameterized.class)
@RequiresFlagsEnabled(android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class UriGrantsManagerServicePccTest {
    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getFlags() {
        return FlagsParameterization.allCombinationsOf(
                android.security.Flags.FLAG_CONTENT_URI_PERMISSION_APIS);
    }

    public UriGrantsManagerServicePccTest(FlagsParameterization flags) {
        mSetFlagsRule = new SetFlagsRule(NULL_DEFAULT, flags);
    }

    @Rule
    public final SetFlagsRule mSetFlagsRule;

    private UriGrantsMockContext mContext;
    private UriGrantsManagerInternal mService;
    private PccSandboxManagerInternal mPccSandboxManager;

    @Before
    public void setUp() throws Exception {
        mContext = new UriGrantsMockContext();

        mPccSandboxManager = mock(PccSandboxManagerInternal.class);
        when(mPccSandboxManager.isPccTrustedSystemComponent(
                eq(UID_PRIMARY_PCC_TRUSTED), eq(PKG_PCC_TRUSTED))).thenReturn(true);
        LocalServices.removeServiceForTest(PccSandboxManagerInternal.class);
        LocalServices.addService(PccSandboxManagerInternal.class, mPccSandboxManager);

        mService = createForTest(mContext.getFilesDir()).getLocalService();
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(PccSandboxManagerInternal.class);
    }

    private UriGrantsManagerService createForTest(File systemDir) {
        final UriGrantsManagerService service = new UriGrantsManagerService(systemDir, null) {
            @Override
            protected int checkUidPermission(String permission, int uid) {
                // Grant READ_EXTERNAL_STORAGE to UID_PRIMARY_SOCIAL
                if (uid == UID_PRIMARY_SOCIAL
                        && android.Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission)) {
                    return PackageManager.PERMISSION_GRANTED;
                }
                return PackageManager.PERMISSION_DENIED;
            }

            @Override
            protected int getAppUidForPrivateComputeCoreUid(int pccUid) {
                int appId = UserHandle.getAppId(pccUid);
                if (appId >= Process.FIRST_PCC_UID && appId <= Process.LAST_PCC_UID) {
                    int hostAppId = appId - (Process.FIRST_PCC_UID - Process.FIRST_APPLICATION_UID);
                    return UserHandle.getUid(UserHandle.getUserId(pccUid), hostAppId);
                }
                return -1;
            }
        };
        service.mAmInternal = mContext.mAmInternal;
        service.mPmInternal = mContext.mPmInternal;
        return service;
    }

    @Test
    public void testPccWriteToPublic_ShouldFail() {
        // PCC trying to write to Public provider (Non-PCC)
        // Public provider allows everyone to write (in this mock context, if not restricted)

        GrantUri grantUri = new GrantUri(USER_PRIMARY, URI_PUBLIC, FLAG_GRANT_WRITE_URI_PERMISSION);
        boolean allowed = mService.checkUriPermission(
                grantUri, UID_PRIMARY_PCC, FLAG_GRANT_WRITE_URI_PERMISSION, true);

        // Should be false because PCC cannot write to non-PCC.
        assertFalse("PCC should NOT be allowed to write to Non-PCC provider", allowed);
    }

    @Test
    public void testPccReadDelegation_ShouldPass() {
        // PCC trying to read from Camera provider (Non-PCC)
        // Camera provider requires READ_EXTERNAL_STORAGE
        // PCC does NOT have this permission.
        // Host App (Social) HAS permission (via checkUidPermission override).
        GrantUri grantUri = new GrantUri(USER_PRIMARY, URI_PHOTO_1, FLAG_GRANT_READ_URI_PERMISSION);
        boolean allowed = mService.checkUriPermission(
                grantUri, UID_PRIMARY_PCC, FLAG_GRANT_READ_URI_PERMISSION, true);

        // Should be true via delegation.
        assertTrue("PCC should be allowed to read if Host App has permission", allowed);
    }

    @Test
    public void testPccReadDelegation_HostDoesNotHavePermission_ShouldFail() {
        int uidPccSecondary = UserHandle.getUid(mContext.USER_SECONDARY, 39998);
        GrantUri grantUri = new GrantUri(
                mContext.USER_SECONDARY, URI_PHOTO_1, FLAG_GRANT_READ_URI_PERMISSION);
        boolean allowed = mService.checkUriPermission(
                grantUri, uidPccSecondary, FLAG_GRANT_READ_URI_PERMISSION, true);

        assertFalse("PCC delegation should fail if Host App doesn't have permission", allowed);
    }

    @Test
    public void testGrantWriteToPcc_ShouldFail() {
        // Camera App tries to grant WRITE permission to PCC.
        final Intent intent = new Intent(Intent.ACTION_VIEW, URI_PHOTO_1)
                .addFlags(FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            mService.checkGrantUriPermissionFromIntent(
                    intent, UID_PRIMARY_CAMERA, PKG_PCC, USER_PRIMARY);
            fail("Should have thrown SecurityException when granting WRITE to PCC");
        } catch (SecurityException e) {
            // Expected
        }
    }

    @Test
    public void testPccWriteToPcc_ShouldPass() {
        // PCC writing to PCC provider.
        // Should be allowed.
        GrantUri grantUri = new GrantUri(USER_PRIMARY, URI_PCC, FLAG_GRANT_WRITE_URI_PERMISSION);
        boolean allowed = mService.checkUriPermission(
                grantUri, UID_PRIMARY_PCC, FLAG_GRANT_WRITE_URI_PERMISSION, true);

        assertTrue("PCC should be allowed to write to PCC provider", allowed);
    }

    @Test
    public void testPccWriteToPccTrusted_ShouldPass() {
        // PCC writing to PCC Trusted provider.
        // Should be allowed.
        GrantUri grantUri = new GrantUri(
                USER_PRIMARY, URI_PCC_TRUSTED, FLAG_GRANT_WRITE_URI_PERMISSION);
        boolean allowed = mService.checkUriPermission(
                grantUri, UID_PRIMARY_PCC, FLAG_GRANT_WRITE_URI_PERMISSION, true);

        assertTrue("PCC should be allowed to write to PCC Trusted provider", allowed);
    }

    @Test
    public void testGrantWriteToPccTrusted_ShouldPass() {
        // Camera App tries to grant WRITE permission for PCC Trusted provider to PCC.
        final Intent intent = new Intent(Intent.ACTION_VIEW, URI_PCC_TRUSTED)
                .addFlags(FLAG_GRANT_WRITE_URI_PERMISSION);

        // Expected to succeed regardless of flag state since provider allows grants.
        mService.checkGrantUriPermissionFromIntent(
                intent, UID_PRIMARY_CAMERA, PKG_PCC, USER_PRIMARY);
    }

    @Test
    public void testPccGrantWriteToPccTrusted_ShouldPass() {
        // PCC App tries to grant WRITE permission for its own provider to a PCC Trusted app.
        final Intent intent = new Intent(Intent.ACTION_VIEW, URI_PCC)
                .addFlags(FLAG_GRANT_WRITE_URI_PERMISSION);

        // Expected to succeed regardless of flag state since provider allows grants.
        mService.checkGrantUriPermissionFromIntent(
                intent, UID_PRIMARY_PCC, PKG_PCC_TRUSTED, USER_PRIMARY);
    }
}

