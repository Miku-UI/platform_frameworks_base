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

package com.android.server.privatecompute;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static com.android.server.privatecompute.PccSandboxManagerServiceImpl.AUDIT_LOG_CLEANUP_INTERVAL_MS;

import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.privatecompute.DataMigrationToPccService;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.privatecompute.IDataMigrationToPccService;
import android.app.privatecompute.IMigrationRequestResultReceiver;
import android.app.privatecompute.IMigrationRequestResultSender;
import android.app.privatecompute.MigrationException;
import android.app.privatecompute.MigrationRequestResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.privatecompute.PccSandboxManagerServiceImpl.Injector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link PccSandboxManagerServiceImpl}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class PccSandboxManagerServiceImplTest {
    private static final int TEST_UID = 10123;
    private static final String TEST_PACKAGE_NAME = "com.example.foo";
    private static final String ANOTHER_PACKAGE_NAME = "com.example.bar";
    private static final String TEST_SERVICE_CLASS = "com.example.foo.MigrationService";
    private static final long TEST_ELAPSED_REALTIME = 1000L;

    @Rule(order= 0)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1) public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule(order = 2) public TemporaryFolder mLogFolderUser1 = new TemporaryFolder();
    @Rule(order = 3) public TemporaryFolder mLogFolderUser2 = new TemporaryFolder();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;

    @Mock private PackageManagerInternal mPackageManagerInternal;
    @Mock
    private Handler mHandler;
    @Mock
    private IMigrationRequestResultReceiver mCallback;
    @Mock private AlarmManager mAlarmManager;
    @Mock private Looper mBackgroundLooper;

    @Mock private Injector mInjector;
    @Mock private PccSandboxManagerInternal mInternal;

    private File mAuditLogDirUser1;
    private File mAuditLogDirUser2;
    private PccSandboxManagerServiceImpl mService;

    @Before
    public void setUp() throws Exception {
        mAuditLogDirUser1 = mLogFolderUser1.newFolder();
        mAuditLogDirUser2 = mLogFolderUser2.newFolder();
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);
        when(mInjector.getCallingUid()).thenReturn(TEST_UID);
        when(mInjector.getHandler(any())).thenReturn(mHandler);
        when(mInjector.getAlarmManager(any())).thenReturn(mAlarmManager);
        when(mInjector.getBackgroundLooper()).thenReturn(mBackgroundLooper);
        when(mInjector.getElapsedRealtime()).thenReturn(TEST_ELAPSED_REALTIME);
        when(mInjector.getExecutorService()).thenReturn(newDirectExecutorService());
        when(mInjector.getAuditLogFilesDirectory(anyInt())).thenReturn(mAuditLogDirUser1);
        when(mInjector.getAuditLogFilesDirectory(eq(10))).thenReturn(mAuditLogDirUser2);
        doCallRealMethod().when(mInjector).deleteAuditLogFiles(anyInt());
        mService = new PccSandboxManagerServiceImpl(mContext, mInjector);
        mService.setPccSandboxManagerInternal(mInternal);
    }


    @Test
    public void testWhenUserUnlocked_folderIsDeleted() throws Exception {
        mAuditLogDirUser1.mkdirs();
        mAuditLogDirUser2.mkdirs();
        assertTrue(mAuditLogDirUser1.exists());
        assertTrue(mAuditLogDirUser2.exists());
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiverForAllUsers(
                receiverCaptor.capture(), any(IntentFilter.class), any(), any(Handler.class));
        BroadcastReceiver receiver = receiverCaptor.getValue();

        // Act: User 10 is unlocked.
        Intent intent = new Intent(Intent.ACTION_USER_UNLOCKED);
        intent.putExtra(Intent.EXTRA_USER_HANDLE, 10);
        receiver.onReceive(mContext, intent);
        mService.getExecutorService().shutdown();
        assertTrue(mService.getExecutorService().awaitTermination(5, TimeUnit.SECONDS));

        verify(mInjector).deleteAuditLogFiles(10);
        assertTrue(mAuditLogDirUser1.exists());  // User 0's folder is not deleted
        assertFalse(mAuditLogDirUser2.exists()); // User 10's folder is deleted
    }

    @Test
    public void testEnableTrustInstrumentedClients_shellCommand() {
        when(mInjector.getCallingUid()).thenReturn(Process.SHELL_UID);
        mService.onShellCommand(FileDescriptor.in, FileDescriptor.out, FileDescriptor.err,
                new String[]{"enable-trust-instrumented-clients"}, null, null);
        verify(mInternal).setTrustInstrumentedClients(true);
    }

    @Test
    public void testDisableTrustInstrumentedClients_shellCommand() {
        when(mInjector.getCallingUid()).thenReturn(Process.SHELL_UID);
        mService.onShellCommand(FileDescriptor.in, FileDescriptor.out, FileDescriptor.err,
                new String[]{"disable-trust-instrumented-clients"}, null, null);
        verify(mInternal).setTrustInstrumentedClients(false);
    }

    @Test
    public void testEnableTrustInstrumentedClients_notRootOrShell_denied() {
        when(mInjector.getCallingUid()).thenReturn(TEST_UID);
        mService.onShellCommand(FileDescriptor.in, FileDescriptor.out, FileDescriptor.err,
                new String[]{"enable-trust-instrumented-clients"}, null, null);
        verify(mInternal, never()).setTrustInstrumentedClients(anyBoolean());
    }

    @Test
    public void testAlarmListener_runsCleanupTaskAndReschedules() {
        when(mInjector.auditModeEnabled()).thenReturn(true);
        when(mPackageManagerInternal.isSameApp(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(mInternal.isPccTrustedSystemComponent(anyInt(), anyString())).thenReturn(true);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());
        // Trigger the first write, which schedules the cleanup task.
        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);
        // Capture the listener.
        ArgumentCaptor<AlarmManager.OnAlarmListener> listenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        // It was called once in writeToAuditLogInternal.
        verify(mAlarmManager, times(1))
                .set(anyInt(), anyLong(), anyString(), listenerCaptor.capture(), any());
        AlarmManager.OnAlarmListener listener = listenerCaptor.getValue();

        // Clear invocations to verify the next run.
        clearInvocations(mAlarmManager);

        // Trigger the alarm.
        listener.onAlarm();

        // Verify alarm is rescheduled.
        verify(mAlarmManager).cancel(any(AlarmManager.OnAlarmListener.class));
        verify(mAlarmManager)
                .set(
                        eq(AlarmManager.ELAPSED_REALTIME),
                        anyLong(),
                        anyString(),
                        eq(listener),
                        any());
    }

    @Test
    public void testIsPrivateComputeServicesUid_uidInPccRange_returnsFalse() throws Exception {
        assertFalse(mService.isPrivateComputeServicesUid(Process.FIRST_PCC_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_uidOutsideApplicationUidRange_returnsFalse()
            throws Exception {
        assertFalse(mService.isPrivateComputeServicesUid(Process.FIRST_APPLICATION_UID - 1));
        assertFalse(mService.isPrivateComputeServicesUid(Process.LAST_APPLICATION_UID + 1));
    }

    @Test
    public void testIsPrivateComputeServicesUid_packageDoesNotHavePermission_returnsFalse() {
        when(mPackageManager.getPackagesForUid(TEST_UID))
                .thenReturn(new String[] {TEST_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertFalse(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_noPackagesForUid_returnsFalse() throws Exception {
        when(mPackageManager.getPackagesForUid(TEST_UID)).thenReturn(null);

        assertFalse(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_packageHasPermission_returnsTrue() {
        when(mPackageManager.getPackagesForUid(TEST_UID))
                .thenReturn(new String[] {TEST_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        assertTrue(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_multiplePackages_oneHasPermission_returnsTrue() {
        when(mPackageManager.getPackagesForUid(TEST_UID))
                .thenReturn(new String[] {TEST_PACKAGE_NAME, ANOTHER_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        ANOTHER_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_GRANTED);

        assertTrue(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testIsPrivateComputeServicesUid_multiplePackages_noneHavePermission_returnsFalse() {
        when(mPackageManager.getPackagesForUid(TEST_UID))
                .thenReturn(new String[] {TEST_PACKAGE_NAME, ANOTHER_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        ANOTHER_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertFalse(mService.isPrivateComputeServicesUid(TEST_UID));
    }

    @Test
    public void testWriteToAuditLogInternal_packageNameDoesNotMatchUid_throwsSecurityException() {
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(true);
        when(mPackageManagerInternal.isSameApp(
                        ANOTHER_PACKAGE_NAME, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(false);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        assertThrows(
                SecurityException.class,
                () ->
                        mService.writeToAuditLogInternal(data, ANOTHER_PACKAGE_NAME));
    }

    @Test
    public void testWriteToAuditLogInternal_packageNameMatchesUid_doesNotThrowSecurityException() {
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(true);
        when(mPackageManagerInternal.isSameApp(
                        ANOTHER_PACKAGE_NAME, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(false);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);

        // No exception thrown.
    }

    @Test
    public void testWriteToAuditLogInternal_nonPccNonPcsUid_returnsFalse() {
        int nonPccNonPcsUid = Process.FIRST_APPLICATION_UID;
        when(mInjector.getCallingUid()).thenReturn(nonPccNonPcsUid);
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, nonPccNonPcsUid, UserHandle.getUserId(nonPccNonPcsUid)))
                .thenReturn(true);
        when(mInternal.isPccTrustedSystemComponent(nonPccNonPcsUid, TEST_PACKAGE_NAME))
                .thenReturn(false);

        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        assertFalse(mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME));
    }

    @Test
    public void testWriteToAuditLogInternal_explicitNonPcsUid_returnsFalse() {
        when(mInjector.auditModeEnabled()).thenReturn(true);
        int nonPccUid = Process.FIRST_APPLICATION_UID + 1;
        when(mInjector.getCallingUid()).thenReturn(nonPccUid);
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, nonPccUid, UserHandle.getUserId(nonPccUid)))
                .thenReturn(true);
        // Explicitly mock that it is NOT a PCC UID
        when(mInternal.isPccTrustedSystemComponent(nonPccUid, TEST_PACKAGE_NAME))
                .thenReturn(false);
        // Explicitly mock that it is NOT a PCS UID
        when(mPackageManager.getPackagesForUid(nonPccUid))
                .thenReturn(new String[] {TEST_PACKAGE_NAME});
        when(mPackageManager.checkPermission(
                        android.Manifest.permission.PROVIDE_PRIVATE_COMPUTE_SERVICES,
                        TEST_PACKAGE_NAME))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        assertFalse(mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME));
    }

    @Test
    public void testWriteToAuditLogInternal_sysPropDisabled_returnsFalse() {
        when(mInjector.auditModeEnabled()).thenReturn(false);
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(true);
        when(mInternal.isPccTrustedSystemComponent(TEST_UID, TEST_PACKAGE_NAME))
                .thenReturn(true);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        assertFalse(mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME));
    }

    @Test
    public void testWriteToAuditLogInternal_sysPropEnabled_returnsTrue() {
        when(mInjector.auditModeEnabled()).thenReturn(true);
        when(mPackageManagerInternal.isSameApp(
                        TEST_PACKAGE_NAME, TEST_UID, UserHandle.getUserId(TEST_UID)))
                .thenReturn(true);
        when(mInternal.isPccTrustedSystemComponent(TEST_UID, TEST_PACKAGE_NAME))
                .thenReturn(true);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        assertTrue(mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME));
    }

    @Test
    public void testWriteToAuditLogInternal_multipleUsers_cleansUpOnlyOnce() {
        when(mInjector.auditModeEnabled()).thenReturn(true);
        when(mPackageManagerInternal.isSameApp(
                        eq(TEST_PACKAGE_NAME), anyInt(), anyInt()))
                .thenReturn(true);
        when(mInternal.isPccTrustedSystemComponent(anyInt(), anyString())).thenReturn(true);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());

        // Call for user 0
        when(mInjector.getCallingUid()).thenReturn(0);
        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);

        // Call for user 10
        when(mInjector.getCallingUid()).thenReturn(UserHandle.PER_USER_RANGE * 10);
        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);

        // Verify this is called only once for the first user.
        verify(mInjector, times(1)).deleteAuditLogFilesAllUsers();
    }

    @Test
    public void testWriteToAuditLogInternal_auditModeToggledOff_clearsAllPreviousLogs() {
        // This tests that when auditing is on for two users, then turned off, the following
        // enable clears all previous logs, for all users.
        when(mInjector.auditModeEnabled()).thenReturn(true);
        when(mPackageManagerInternal.isSameApp(
                        eq(TEST_PACKAGE_NAME), anyInt(), anyInt()))
                .thenReturn(true);
        when(mInternal.isPccTrustedSystemComponent(anyInt(), anyString())).thenReturn(true);
        List<PersistableBundle> data = new ArrayList<>(1);
        data.add(new PersistableBundle());
        when(mInjector.getCallingUid()).thenReturn(0);
        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);
        when(mInjector.getCallingUid()).thenReturn(UserHandle.PER_USER_RANGE * 10);
        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);
        // deleteAuditLogFilesAllUsers is called once so far (when creating the audit mode context
        // for the first user).
        verify(mInjector, times(1)).deleteAuditLogFilesAllUsers();
        // Disable audit mode - Logs are not cleared, so they can be read.
        when(mInjector.auditModeEnabled()).thenReturn(false);
        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);

        // Enable audit mode (and trigger the write call again): This should clear all past logs.
        when(mInjector.auditModeEnabled()).thenReturn(true);
        mService.writeToAuditLogInternal(data, TEST_PACKAGE_NAME);

        verify(mInjector, times(3)).deleteAuditLogFilesAllUsers();
    }

    @Test
    public void testStartNonPccProcessForDataMigration_success() throws Exception {
        setupMigrationService(true);

        // Mock the binder interface
        IDataMigrationToPccService.Stub mockBinder = mock(IDataMigrationToPccService.Stub.class);
        IDataMigrationToPccService mockInterface = mock(IDataMigrationToPccService.class);
        when(mockBinder.queryLocalInterface(any())).thenReturn(mockInterface);

        // Capture the ServiceConnection
        ArgumentCaptor<ServiceConnection> connectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        when(mContext.bindServiceAsUser(any(), connectionCaptor.capture(), anyInt(), any()))
                .thenReturn(true);

        mService.startNonPccProcessForDataMigration(mCallback);

        // Verify bind
        verify(mContext).bindServiceAsUser(any(), any(), eq(Context.BIND_AUTO_CREATE),
                eq(UserHandle.getUserHandleForUid(TEST_UID)));

        // Simulate connection
        connectionCaptor.getValue().onServiceConnected(new ComponentName(TEST_PACKAGE_NAME,
                TEST_SERVICE_CLASS), mockBinder);

        // Capture the completion callback passed to the service
        ArgumentCaptor<IMigrationRequestResultSender> completionCallbackCaptor =
                ArgumentCaptor.forClass(IMigrationRequestResultSender.class);
        verify(mockInterface).onMigrationRequested(completionCallbackCaptor.capture());

        // Simulate completion
        MigrationRequestResult result = new MigrationRequestResult(
                MigrationRequestResult.MIGRATION_REQUEST_ACCEPTED, PersistableBundle.EMPTY);
        completionCallbackCaptor.getValue().sendResult(result);

        // Verify callback called
        verify(mCallback).onResult(result);

        // Verify unbind
        verify(mContext).unbindService(connectionCaptor.getValue());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_noPackageForUid() throws Exception {
        when(mPackageManager.getPackagesForUid(TEST_UID)).thenReturn(null);

        mService.startNonPccProcessForDataMigration(mCallback);

        verify(mCallback).onError(eq(MigrationException.ERROR_INVOCATION_FAILED),
                anyString());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_noService() throws Exception {
        when(mPackageManager.getPackagesForUid(TEST_UID)).thenReturn(
                new String[]{TEST_PACKAGE_NAME});
        when(mPackageManager.resolveService(any(), anyInt())).thenReturn(null);

        mService.startNonPccProcessForDataMigration(mCallback);

        verify(mCallback).onError(eq(MigrationException.ERROR_INVOCATION_FAILED),
                anyString());
        verify(mContext, never()).bindServiceAsUser(any(), any(), anyInt(), any());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_missingPermission() throws Exception {
        setupMigrationService(false);

        mService.startNonPccProcessForDataMigration(mCallback);

        verify(mCallback).onError(eq(MigrationException.ERROR_INVOCATION_FAILED),
                anyString());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_timeout() throws Exception {
        setupMigrationService(true);

        // Mock the binder interface
        IDataMigrationToPccService.Stub mockBinder = mock(IDataMigrationToPccService.Stub.class);
        IDataMigrationToPccService mockInterface = mock(IDataMigrationToPccService.class);
        when(mockBinder.queryLocalInterface(any())).thenReturn(mockInterface);

        // Capture the ServiceConnection
        ArgumentCaptor<ServiceConnection> connectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        when(mContext.bindServiceAsUser(any(), connectionCaptor.capture(), anyInt(), any()))
                .thenReturn(true);

        // Capture the runnable passed to postDelayed
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(mHandler.postDelayed(runnableCaptor.capture(),
                eq(DataMigrationToPccService.MIGRATION_TIMEOUT_MS))).thenReturn(true);

        mService.startNonPccProcessForDataMigration(mCallback);

        // Simulate connection
        connectionCaptor.getValue().onServiceConnected(new ComponentName(TEST_PACKAGE_NAME,
                TEST_SERVICE_CLASS), mockBinder);

        // Run the timeout runnable
        runnableCaptor.getValue().run();

        // Verify onError called
        verify(mCallback).onError(eq(MigrationException.ERROR_TIMEOUT), anyString());

        // Verify unbind
        verify(mContext).unbindService(connectionCaptor.getValue());
    }

    @Test
    public void testStartNonPccProcessForDataMigration_invalidBundle() throws Exception {
        setupMigrationService(true);

        IDataMigrationToPccService.Stub mockBinder = mock(IDataMigrationToPccService.Stub.class);
        IDataMigrationToPccService mockInterface = mock(IDataMigrationToPccService.class);
        when(mockBinder.queryLocalInterface(any())).thenReturn(mockInterface);

        ArgumentCaptor<ServiceConnection> connectionCaptor =
                ArgumentCaptor.forClass(ServiceConnection.class);
        when(mContext.bindServiceAsUser(any(), connectionCaptor.capture(), anyInt(), any()))
                .thenReturn(true);

        mService.startNonPccProcessForDataMigration(mCallback);

        verify(mContext).bindServiceAsUser(any(), any(), eq(Context.BIND_AUTO_CREATE),
                eq(UserHandle.getUserHandleForUid(TEST_UID)));

        connectionCaptor.getValue().onServiceConnected(new ComponentName(TEST_PACKAGE_NAME,
                TEST_SERVICE_CLASS), mockBinder);

        ArgumentCaptor<IMigrationRequestResultSender> completionCallbackCaptor =
                ArgumentCaptor.forClass(IMigrationRequestResultSender.class);
        verify(mockInterface).onMigrationRequested(completionCallbackCaptor.capture());

        PersistableBundle outerBundle = new PersistableBundle();
        PersistableBundle currentBundle = outerBundle;
        for (int i = 0; i < 101; i++) {
            PersistableBundle nextBundle = new PersistableBundle();
            currentBundle.putPersistableBundle("key", nextBundle);
            currentBundle = nextBundle;
        }

        MigrationRequestResult result = new MigrationRequestResult(
                MigrationRequestResult.MIGRATION_REQUEST_ACCEPTED, outerBundle);
        completionCallbackCaptor.getValue().sendResult(result);

        verify(mCallback).onError(eq(MigrationException.ERROR_INVOCATION_FAILED),
                anyString());

        verify(mContext).unbindService(connectionCaptor.getValue());
    }

    private void setupMigrationService(boolean hasPermission) {
        when(mPackageManager.getPackagesForUid(TEST_UID)).thenReturn(
                new String[]{TEST_PACKAGE_NAME});

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.packageName = TEST_PACKAGE_NAME;
        resolveInfo.serviceInfo.name = TEST_SERVICE_CLASS;
        if (hasPermission) {
            resolveInfo.serviceInfo.permission =
                    "android.permission.BIND_DATA_MIGRATION_FOR_PRIVATECOMPUTE";
        }

        when(mPackageManager.resolveServiceAsUser(any(), anyInt(), anyInt())).thenReturn(
                resolveInfo);
    }
}
