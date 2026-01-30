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

package com.android.server.backup.restore;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.app.backup.BackupAgent;
import android.app.backup.BackupAnnotations.BackupDestination;
import android.app.backup.RestoreSet;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.LocalServices;
import com.android.server.backup.Flags;
import com.android.server.backup.TransportManager;
import com.android.server.backup.UserBackupManagerService;
import com.android.server.backup.utils.BackupEligibilityRules;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActiveRestoreSessionTest {
    private static final String TEST_APP_NAME = "test_app";

    private ActiveRestoreSession mRestoreSession;
    private ApplicationInfo mTestApp;

    @Mock private UserBackupManagerService mBackupManagerService;
    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private PackageManagerInternal mPackageManagerInternal;
    @Mock private TransportManager mTransportManager;
    @Mock private UserManager mUserManager;

    @Rule public final SetFlagsRule mFlagsRule = new SetFlagsRule();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(/* testClassInstance */ this)
                    .mockStatic(LocalServices.class)
                    .afterSessionFinished(
                            () -> LocalServices.removeServiceForTest(PackageManagerInternal.class))
                    .build();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass */ this);
        when(mBackupManagerService.getTransportManager()).thenReturn(mTransportManager);
        when(mBackupManagerService.getContext()).thenReturn(mContext);
        when(mBackupManagerService.getEligibilityRulesForOperation(anyInt()))
                .thenAnswer(
                        invocation -> {
                            return new BackupEligibilityRules(
                                    mPackageManager,
                                    mPackageManagerInternal,
                                    0,
                                    mContext,
                                    invocation.getArgument(0));
                        });
        when(mContext.getSystemService(eq(UserManager.class))).thenReturn(mUserManager);
        when(LocalServices.getService(PackageManagerInternal.class))
                .thenReturn(mPackageManagerInternal);

        mRestoreSession =
                new ActiveRestoreSession(
                        mBackupManagerService,
                        /* packageName */ null,
                        /* transportName */ "",
                        new BackupEligibilityRules(
                                mPackageManager,
                                mPackageManagerInternal,
                                0,
                                mContext,
                                BackupDestination.CLOUD));
        mTestApp = new ApplicationInfo();
        mTestApp.packageName = TEST_APP_NAME;
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_SKIPPING_RESTORE_LAUNCHED_APPS})
    public void testGetBackupEligibilityRules_skipRestoreFlagOn_skipsLaunchedAppRestore() {
        RestoreSet restoreSet =
                new RestoreSet(
                        /* name */ null,
                        /* device */ null,
                        /* token */ 0,
                        /* backupTransportFlags */ BackupAgent.FLAG_SKIP_RESTORE_FOR_LAUNCHED_APPS);
        when(mPackageManagerInternal.wasPackageEverLaunched(eq(TEST_APP_NAME), anyInt()))
                .thenReturn(true);

        BackupEligibilityRules eligibilityRules =
                mRestoreSession.getBackupEligibilityRules(restoreSet);

        assertThat(eligibilityRules.isAppEligibleForRestore(mTestApp)).isFalse();
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_SKIPPING_RESTORE_LAUNCHED_APPS})
    public void testGetBackupEligibilityRules_skipRestoreFlagOff_allowsAppRestore() {
        RestoreSet restoreSet =
                new RestoreSet(
                        /* name */ null,
                        /* device */ null,
                        /* token */ 0,
                        /* backupTransportFlags */ BackupAgent.FLAG_SKIP_RESTORE_FOR_LAUNCHED_APPS);
        when(mPackageManagerInternal.wasPackageEverLaunched(eq(TEST_APP_NAME), anyInt()))
                .thenReturn(true);

        BackupEligibilityRules eligibilityRules =
                mRestoreSession.getBackupEligibilityRules(restoreSet);

        assertThat(eligibilityRules.isAppEligibleForRestore(mTestApp)).isTrue();
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER})
    public void testGetBackupEligibilityRules_crossPlatformTransfer_hasCorrectDestination() {
        RestoreSet restoreSet =
                new RestoreSet(
                        /* name= */ null,
                        /* device= */ "D2D",
                        /* token= */ 1,
                        BackupAgent.FLAG_CROSS_PLATFORM_DATA_TRANSFER_IOS);

        BackupEligibilityRules eligibilityRules =
                mRestoreSession.getBackupEligibilityRules(restoreSet);

        assertThat(eligibilityRules.getBackupDestination())
                .isEqualTo(BackupDestination.CROSS_PLATFORM_TRANSFER);
    }
}
