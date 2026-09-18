/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.backup;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.backup.internal.BackupHandler.MSG_RUN_DELAYED_RESTORE;
import static com.android.server.backup.internal.BackupHandler.MSG_RUN_DELAYED_RESTORE_CLEANUP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.IActivityManager;
import android.app.backup.BackupAgent;
import android.app.backup.BackupAnnotations.BackupDestination;
import android.app.backup.BackupAnnotations.OperationType;
import android.app.backup.BackupRestoreEventLogger.DataTypeResult;
import android.app.backup.DelayedRestoreRequest;
import android.app.backup.IBackupManagerMonitor;
import android.app.backup.IBackupObserver;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.testing.TestableContext;
import android.util.FeatureFlagUtils;
import android.util.KeyValueListParser;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.backup.internal.BackupHandler;
import com.android.server.backup.internal.LifecycleOperationStorage;
import com.android.server.backup.internal.OnTaskFinishedListener;
import com.android.server.backup.params.BackupParams;
import com.android.server.backup.params.DelayedRestoreParams;
import com.android.server.backup.restore.DelayedRestoreJournal;
import com.android.server.backup.transport.BackupTransportClient;
import com.android.server.backup.transport.TransportConnection;
import com.android.server.backup.utils.BackupEligibilityRules;
import com.android.server.backup.utils.BackupManagerMonitorEventSender;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class UserBackupManagerServiceTest {
    private static final String TEST_PACKAGE = "package1";
    private static final String[] TEST_PACKAGES = new String[] {TEST_PACKAGE};
    private static final String TEST_TRANSPORT = "transport";

    @Rule public TestRule compatChangeRule = new PlatformCompatChangeRule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock IBackupManagerMonitor mBackupManagerMonitor;
    @Mock IBackupObserver mBackupObserver;
    @Mock PackageManager mPackageManager;
    @Mock TransportConnection mTransportConnection;
    @Mock TransportManager mTransportManager;
    @Mock BackupTransportClient mBackupTransport;
    @Mock BackupEligibilityRules mBackupEligibilityRules;
    @Mock LifecycleOperationStorage mOperationStorage;
    @Mock JobScheduler mJobScheduler;
    @Mock BackupHandler mBackupHandler;
    @Mock BackupManagerMonitorEventSender mBackupManagerMonitorEventSender;
    @Mock IActivityManager mActivityManager;
    @Mock ActivityManagerInternal mActivityManagerInternal;
    @Mock DelayedRestoreJournal mDelayedRestoreJournal;

    @UserIdInt private int mUserId;
    private TestableContext mContext;
    private MockitoSession mSession;
    private TestBackupService mService;
    private ApplicationInfo mTestPackageApplicationInfo;

    @Before
    public void setUp() throws Exception {
        mSession =
                mockitoSession()
                        .initMocks(this)
                        .mockStatic(BackupManagerMonitorEventSender.class)
                        .mockStatic(FeatureFlagUtils.class)
                        // TODO(b/263239775): Remove unnecessary stubbing.
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        MockitoAnnotations.initMocks(this);

        mUserId = ActivityManager.getCurrentUser();

        mContext = new TestableContext(ApplicationProvider.getApplicationContext());
        mContext.addMockSystemService(JobScheduler.class, mJobScheduler);
        mContext.getTestablePermissions()
                .setPermission(
                        android.Manifest.permission.BACKUP, PackageManager.PERMISSION_GRANTED);

        mService = new TestBackupService();
        mService.setEnabled(true);
        mService.setSetupComplete(true);
        mService.enqueueFullBackup("com.test.backup.app", /* lastBackedUp= */ 0);

        mTestPackageApplicationInfo = new ApplicationInfo();
        mTestPackageApplicationInfo.packageName = TEST_PACKAGE;
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testSetFrameworkSchedulingEnabled_enablesAndSchedulesBackups() throws Exception {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.BACKUP_SCHEDULING_ENABLED,
                0,
                mUserId);

        mService.setFrameworkSchedulingEnabled(true);

        assertThat(mService.isFrameworkSchedulingEnabled()).isTrue();
        verify(mJobScheduler)
                .schedule(matchesJobWithId(KeyValueBackupJob.getJobIdForUserId(mUserId)));
        verify(mJobScheduler).schedule(matchesJobWithId(FullBackupJob.getJobIdForUserId(mUserId)));
    }

    private static JobInfo matchesJobWithId(int id) {
        return argThat((jobInfo) -> jobInfo.getId() == id);
    }

    @Test
    public void testSetFrameworkSchedulingEnabled_disablesAndCancelBackups() throws Exception {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.BACKUP_SCHEDULING_ENABLED,
                1,
                mUserId);

        mService.setFrameworkSchedulingEnabled(false);

        assertThat(mService.isFrameworkSchedulingEnabled()).isFalse();
        verify(mJobScheduler).cancel(FullBackupJob.getJobIdForUserId(mUserId));
        verify(mJobScheduler).cancel(KeyValueBackupJob.getJobIdForUserId(mUserId));
    }

    @Test
    public void initializeBackupEnableState_doesntWriteStateToDisk() {
        mService.initializeBackupEnableState();

        assertThat(mService.isEnabledStatePersisted).isFalse();
    }

    @Test
    public void updateBackupEnableState_writesStateToDisk() {
        mService.setBackupEnabled(true);

        assertThat(mService.isEnabledStatePersisted).isTrue();
    }

    @Test
    public void getRequestBackupParams_appIsEligibleForFullBackup() throws Exception {
        when(mPackageManager.getPackageInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenReturn(getPackageInfo(TEST_PACKAGE));
        when(mBackupEligibilityRules.appIsEligibleForBackup(any())).thenReturn(true);
        when(mBackupEligibilityRules.appGetsFullBackup(any())).thenReturn(true);

        BackupParams params =
                mService.getRequestBackupParams(
                        TEST_PACKAGES,
                        mBackupObserver,
                        mBackupManagerMonitor, /* flags */
                        0,
                        mBackupEligibilityRules,
                        mTransportConnection, /* transportDirName */
                        "",
                        OnTaskFinishedListener.NOP);

        assertThat(params.kvPackages).isEmpty();
        assertThat(params.fullPackages).contains(TEST_PACKAGE);
        assertThat(params.mBackupEligibilityRules).isEqualTo(mBackupEligibilityRules);
    }

    @Test
    public void getRequestBackupParams_appIsEligibleForKeyValueBackup() throws Exception {
        when(mPackageManager.getPackageInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenReturn(getPackageInfo(TEST_PACKAGE));
        when(mBackupEligibilityRules.appIsEligibleForBackup(any())).thenReturn(true);
        when(mBackupEligibilityRules.appGetsFullBackup(any())).thenReturn(false);

        BackupParams params =
                mService.getRequestBackupParams(
                        TEST_PACKAGES,
                        mBackupObserver,
                        mBackupManagerMonitor, /* flags */
                        0,
                        mBackupEligibilityRules,
                        mTransportConnection, /* transportDirName */
                        "",
                        OnTaskFinishedListener.NOP);

        assertThat(params.kvPackages).contains(TEST_PACKAGE);
        assertThat(params.fullPackages).isEmpty();
        assertThat(params.mBackupEligibilityRules).isEqualTo(mBackupEligibilityRules);
    }

    @Test
    public void getRequestBackupParams_appIsNotEligibleForBackup() throws Exception {
        when(mPackageManager.getPackageInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenReturn(getPackageInfo(TEST_PACKAGE));
        when(mBackupEligibilityRules.appIsEligibleForBackup(any())).thenReturn(false);
        when(mBackupEligibilityRules.appGetsFullBackup(any())).thenReturn(false);

        BackupParams params =
                mService.getRequestBackupParams(
                        TEST_PACKAGES,
                        mBackupObserver,
                        mBackupManagerMonitor, /* flags */
                        0,
                        mBackupEligibilityRules,
                        mTransportConnection, /* transportDirName */
                        "",
                        OnTaskFinishedListener.NOP);

        assertThat(params.kvPackages).isEmpty();
        assertThat(params.fullPackages).isEmpty();
        assertThat(params.mBackupEligibilityRules).isEqualTo(mBackupEligibilityRules);
    }

    @Test
    public void testGetBackupDestinationFromTransport_returnsCloudByDefault() throws Exception {
        when(mTransportConnection.connectOrThrow(any())).thenReturn(mBackupTransport);
        when(mBackupTransport.getTransportFlags()).thenReturn(0);

        int backupDestination = mService.getBackupDestinationFromTransport(mTransportConnection);

        assertThat(backupDestination).isEqualTo(BackupDestination.CLOUD);
    }

    @Test
    public void testGetBackupDestinationFromTransport_returnsDeviceTransferForD2dTransport()
            throws Exception {
        when(mTransportConnection.connectOrThrow(any())).thenReturn(mBackupTransport);
        when(mBackupTransport.getTransportFlags())
                .thenReturn(BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER);

        int backupDestination = mService.getBackupDestinationFromTransport(mTransportConnection);

        assertThat(backupDestination).isEqualTo(BackupDestination.DEVICE_TRANSFER);
    }

    @Test
    public void testReportDelayedRestoreResult_sendsLogsToMonitor() throws Exception {
        PackageInfo packageInfo = getPackageInfo(TEST_PACKAGE);
        when(mPackageManager.getPackageInfoAsUser(
                        anyString(), any(PackageManager.PackageInfoFlags.class), anyInt()))
                .thenReturn(packageInfo);
        when(mTransportManager.getCurrentTransportName()).thenReturn(TEST_TRANSPORT);
        when(mTransportManager.getTransportClientOrThrow(eq(TEST_TRANSPORT), anyString()))
                .thenReturn(mTransportConnection);
        when(mTransportConnection.connectOrThrow(any())).thenReturn(mBackupTransport);
        when(mBackupTransport.getBackupManagerMonitor()).thenReturn(mBackupManagerMonitor);

        List<DataTypeResult> results =
                Arrays.asList(
                        new DataTypeResult(/* dataType */ "type_1"),
                        new DataTypeResult(/* dataType */ "type_2"));
        mService.reportDelayedRestoreResult(TEST_PACKAGE, results);

        verify(mBackupManagerMonitorEventSender)
                .sendAgentLoggingResults(eq(packageInfo), eq(results), eq(OperationType.RESTORE));
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER})
    public void testGetEligibilityRulesForRestoreAtInstall_flagOff_defaultEligibility()
            throws Exception {
        long restoreToken = 1;
        mService.setAncestralToken(restoreToken);
        mService.setAncestralBackupDestination(BackupDestination.CROSS_PLATFORM_TRANSFER);

        BackupEligibilityRules eligibilityRules =
                mService.getEligibilityRulesForRestoreAtInstall(restoreToken);

        assertThat(eligibilityRules.getBackupDestination()).isEqualTo(BackupDestination.CLOUD);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER})
    public void testGetEligibilityRulesForRestoreAtInstall_flagOn_correctEligibilityRules()
            throws Exception {
        long restoreToken = 1;
        mService.setAncestralToken(restoreToken);
        mService.setAncestralBackupDestination(BackupDestination.CROSS_PLATFORM_TRANSFER);

        BackupEligibilityRules eligibilityRules =
                mService.getEligibilityRulesForRestoreAtInstall(restoreToken);

        assertThat(eligibilityRules.getBackupDestination())
                .isEqualTo(BackupDestination.CROSS_PLATFORM_TRANSFER);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER})
    public void testGetEligibilityRulesForRestoreAtInstall_noAncestralToken_defaultEligibility()
            throws Exception {
        BackupEligibilityRules eligibilityRules =
                mService.getEligibilityRulesForRestoreAtInstall(1);

        assertThat(eligibilityRules.getBackupDestination()).isEqualTo(BackupDestination.CLOUD);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testOnDelayedRestoreConditionMet_flagOn_schedulesDelayedRestoreParams()
            throws Exception {
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName(TEST_PACKAGE)
                        .build();
        Set<String> requesters = Collections.singleton(TEST_PACKAGE);

        when(mTransportManager.getCurrentTransportClient(anyString()))
                .thenReturn(mTransportConnection);
        when(mTransportConnection.getTransportComponent())
                .thenReturn(new ComponentName("pkg", "cls"));
        when(mTransportManager.getTransportDirName(any(ComponentName.class)))
                .thenReturn(TEST_TRANSPORT);
        when(mDelayedRestoreJournal.getPackagesForRequest(eq(request))).thenReturn(requesters);

        Message msg = mock(Message.class);
        when(mBackupHandler.obtainMessage(eq(MSG_RUN_DELAYED_RESTORE), any()))
                .thenReturn(msg);

        mService.onDelayedRestoreConditionMet(request);

        verify(mBackupHandler).sendMessage(msg);
        verify(mBackupHandler)
                .obtainMessage(
                        eq(MSG_RUN_DELAYED_RESTORE),
                        argThat(
                                params ->
                                        params instanceof DelayedRestoreParams
                                                && Objects.equals(
                                                        ((DelayedRestoreParams) params).request,
                                                        request)
                                                && ((DelayedRestoreParams) params)
                                                        .requesterPackageNames.contains(
                                                                TEST_PACKAGE)));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testOnDelayedRestoreConditionMet_flagOff_doesNothing() throws Exception {
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName(TEST_PACKAGE)
                        .build();

        mService.onDelayedRestoreConditionMet(request);

        verify(mTransportManager, never()).getCurrentTransportClient(anyString());
        verify(mDelayedRestoreJournal, never()).getPackagesForRequest(any());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testOnDelayedRestoreCachedDataExpired_flagOn_sendsMessage() {
        Message msg = mock(Message.class);
        when(mBackupHandler.obtainMessage(
                        eq(MSG_RUN_DELAYED_RESTORE_CLEANUP), eq(TEST_PACKAGE)))
                .thenReturn(msg);

        mService.onDelayedRestoreCachedDataExpired(TEST_PACKAGE);

        verify(mBackupHandler).sendMessage(msg);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testOnDelayedRestoreCachedDataExpired_flagOff_doesNothing() {
        mService.onDelayedRestoreCachedDataExpired(TEST_PACKAGE);

        verify(mBackupHandler, never()).sendMessage(any());
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testScheduleDelayedRestore_flagOff_returnsFalse() {
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName(TEST_PACKAGE)
                        .build();

        boolean result = mService.scheduleDelayedRestore(request);

        assertThat(result).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testScheduleDelayedRestore_noRestoreInProgress_returnsFalse() {
        mService.setRestoreInProgress(false);
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName(TEST_PACKAGE)
                        .build();

        boolean result = mService.scheduleDelayedRestore(request);

        assertThat(result).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testScheduleDelayedRestore_uidMismatch_returnsFalse() throws Exception {
        mService.setRestoreInProgress(true);
        mService.setRestoreInProgressPackageName(TEST_PACKAGE);
        // Calling uid != package uid
        when(mPackageManager.getPackageUidAsUser(eq(TEST_PACKAGE), anyInt(), anyInt()))
                .thenReturn(android.os.Binder.getCallingUid() + 1);

        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName(TEST_PACKAGE)
                        .build();

        boolean result = mService.scheduleDelayedRestore(request);

        assertThat(result).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testScheduleDelayedRestore_addRequestFails_returnsFalse() throws Exception {
        mService.setRestoreInProgress(true);
        mService.setRestoreInProgressPackageName(TEST_PACKAGE);
        when(mPackageManager.getPackageUidAsUser(eq(TEST_PACKAGE), anyInt(), anyInt()))
                .thenReturn(android.os.Binder.getCallingUid());

        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName(TEST_PACKAGE)
                        .build();

        when(mDelayedRestoreJournal.addRequest(eq(request), eq(TEST_PACKAGE))).thenReturn(false);

        boolean result = mService.scheduleDelayedRestore(request);

        assertThat(result).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testScheduleDelayedRestore_success_conditionNotMet() throws Exception {
        mService.setRestoreInProgress(true);
        mService.setRestoreInProgressPackageName(TEST_PACKAGE);
        when(mPackageManager.getPackageUidAsUser(eq(TEST_PACKAGE), anyInt(), anyInt()))
                .thenReturn(android.os.Binder.getCallingUid());

        // Use TYPE_APP_UPDATE which returns false for isDelayedRestoreRequestAlreadyMet
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_UPDATE)
                        .setPackageName(TEST_PACKAGE)
                        .build();

        when(mDelayedRestoreJournal.addRequest(eq(request), eq(TEST_PACKAGE))).thenReturn(true);

        boolean result = mService.scheduleDelayedRestore(request);

        assertThat(result).isTrue();
        // Verify no message sent for running delayed restore
        verify(mBackupHandler, never()).sendMessage(any());
        verify(mBackupHandler, never()).obtainMessage(eq(MSG_RUN_DELAYED_RESTORE), any());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DELAYED_RESTORE_API)
    public void testScheduleDelayedRestore_success_conditionMet() throws Exception {
        mService.setRestoreInProgress(true);
        mService.setRestoreInProgressPackageName(TEST_PACKAGE);
        when(mPackageManager.getPackageUidAsUser(eq(TEST_PACKAGE), anyInt(), anyInt()))
                .thenReturn(android.os.Binder.getCallingUid());

        // TYPE_APP_INSTALL checks if package exists.
        DelayedRestoreRequest request =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName(TEST_PACKAGE)
                        .build();

        when(mDelayedRestoreJournal.addRequest(eq(request), eq(TEST_PACKAGE))).thenReturn(true);
        // Package exists
        when(mPackageManager.getPackageInfoAsUser(eq(TEST_PACKAGE), anyInt(), anyInt()))
                .thenReturn(new PackageInfo());

        when(mDelayedRestoreJournal.getPackagesForRequest(eq(request)))
                .thenReturn(Collections.singleton(TEST_PACKAGE));

        // Transport setup
        when(mTransportManager.getCurrentTransportClient(anyString()))
                .thenReturn(mTransportConnection);
        when(mTransportConnection.connectOrThrow(any())).thenReturn(mBackupTransport);
        when(mTransportConnection.getTransportComponent())
                .thenReturn(new ComponentName("pkg", "cls"));
        when(mTransportManager.getTransportDirName(any(ComponentName.class)))
                .thenReturn(TEST_TRANSPORT);

        // Handler message setup
        Message msg = mock(Message.class);
        when(mBackupHandler.obtainMessage(eq(MSG_RUN_DELAYED_RESTORE), any())).thenReturn(msg);

        boolean result = mService.scheduleDelayedRestore(request);

        assertThat(result).isTrue();
        verify(mBackupHandler).sendMessage(msg);
    }

    private static PackageInfo getPackageInfo(String packageName) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.packageName = packageName;
        return packageInfo;
    }

    private class TestBackupService extends UserBackupManagerService {
        boolean isEnabledStatePersisted = false;

        TestBackupService() {
            super(
                    mUserId,
                    mContext,
                    mPackageManager,
                    mOperationStorage,
                    mTransportManager,
                    mBackupHandler,
                    createConstants(mContext),
                    mActivityManager,
                    mActivityManagerInternal,
                    mDelayedRestoreJournal);
        }

        private static BackupManagerConstants createConstants(Context context) {
            BackupManagerConstants constants =
                    new BackupManagerConstants(Handler.getMain(), context.getContentResolver());
            // This will trigger constants default values to be set thus preventing invalid values
            // being used in tests.
            constants.update(new KeyValueListParser(','));
            return constants;
        }

        @Override
        void writeEnabledState(boolean enable) {
            isEnabledStatePersisted = true;
        }

        @Override
        boolean readEnabledState() {
            return false;
        }

        @Override
        void updateStateOnBackupEnabled(boolean wasEnabled, boolean enable) {}

        @Override
        BackupManagerMonitorEventSender getBMMEventSender(IBackupManagerMonitor monitor) {
            return mBackupManagerMonitorEventSender;
        }
    }
}
