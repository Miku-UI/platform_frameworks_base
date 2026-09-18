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

package com.android.server.backup;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.IBackupAgent;
import android.app.backup.BackupManagerMonitor;
import android.app.backup.IBackupManager;
import android.app.backup.IBackupManagerMonitor;
import android.app.backup.BackupAnnotations.BackupDestination;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import static com.android.server.backup.internal.BackupHandler.MSG_BACKUP_RESTORE_STEP;
import static com.android.server.backup.internal.BackupHandler.MSG_RESTORE_OPERATION_TIMEOUT;

import android.os.Message;

import com.android.server.backup.OperationStorage.OpType;
import com.android.server.backup.internal.BackupHandler;

import com.android.server.backup.restore.DelayedRestoreJournal;
import com.android.server.backup.transport.BackupTransportClient;
import com.android.server.backup.transport.TransportConnection;
import com.android.server.backup.utils.BackupEligibilityRules;

import java.util.Queue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class DelayedRestoreCleanupTaskTest {
    private static final String TEST_PACKAGE = "test.package";
    private static final int TEST_USER_ID = 0;
    private static final int TEST_TOKEN = 12345;

    @Mock private UserBackupManagerService mUserBackupManagerService;
    @Mock private PackageManager mPackageManager;
    @Mock private BackupAgentConnectionManager mBackupAgentConnectionManager;
    @Mock private DelayedRestoreJournal mDelayedRestoreJournal;
    @Mock private IBackupAgent mBackupAgent;
    @Mock private IBackupManager mBackupManagerBinder;
    @Mock private Queue<BackupRestoreTask> mPendingRestores;
    @Mock private BackupHandler mBackupHandler;
    @Mock private BackupRestoreTask mNextTask;
    @Mock private OperationStorage mOperationStorage;
    @Mock private BackupAgentTimeoutParameters mAgentTimeoutParameters;
    @Mock private BackupEligibilityRules mBackupEligibilityRules;
    @Mock private TransportConnection mTransportConnection;
    @Mock private BackupTransportClient mTransport;
    @Mock private IBackupManagerMonitor mMonitor;
    @Mock private TransportManager mTransportManager;

    private ApplicationInfo mApplicationInfo;
    private PackageInfo mPackageInfo;
    private DelayedRestoreCleanupTask mTask;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mApplicationInfo = new ApplicationInfo();
        mApplicationInfo.uid = 1000;
        mApplicationInfo.packageName = TEST_PACKAGE;
        mPackageInfo = new PackageInfo();
        mPackageInfo.packageName = TEST_PACKAGE;
        mPackageInfo.applicationInfo = mApplicationInfo;

        when(mUserBackupManagerService.getPackageManager()).thenReturn(mPackageManager);
        when(mUserBackupManagerService.getUserId()).thenReturn(TEST_USER_ID);
        when(mUserBackupManagerService.generateRandomIntegerToken()).thenReturn(TEST_TOKEN);
        when(mUserBackupManagerService.getDelayedRestoreJournal())
                .thenReturn(mDelayedRestoreJournal);
        when(mUserBackupManagerService.getBackupAgentConnectionManager())
                .thenReturn(mBackupAgentConnectionManager);
        when(mUserBackupManagerService.getBackupManagerBinder()).thenReturn(mBackupManagerBinder);
        when(mUserBackupManagerService.getPendingRestores()).thenReturn(mPendingRestores);
        when(mUserBackupManagerService.getBackupHandler()).thenReturn(mBackupHandler);
        when(mUserBackupManagerService.getAgentTimeoutParameters())
                .thenReturn(mAgentTimeoutParameters);
        when(mUserBackupManagerService.getEligibilityRulesForOperation(BackupDestination.CLOUD))
                .thenReturn(mBackupEligibilityRules);
        when(mUserBackupManagerService.getTransportManager()).thenReturn(mTransportManager);

        when(mPackageManager.getApplicationInfoAsUser(TEST_PACKAGE, 0, TEST_USER_ID))
                .thenReturn(mApplicationInfo);
        when(mPackageManager.getPackageInfoAsUser(TEST_PACKAGE, 0, TEST_USER_ID))
                .thenReturn(mPackageInfo);

        when(mTransportConnection.connectOrThrow(anyString())).thenReturn(mTransport);
        when(mTransport.getBackupManagerMonitor()).thenReturn(mMonitor);
        when(mTransportManager.getCurrentTransportClient(anyString()))
                .thenReturn(mTransportConnection);
    }

    @Test
    public void testExecute_success() throws Exception {
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);

        when(mBackupAgentConnectionManager.bindToAgentSynchronous(
                        eq(mApplicationInfo), anyInt(), eq(BackupDestination.CLOUD)))
                .thenReturn(mBackupAgent);

        mTask.execute();

        verify(mDelayedRestoreJournal).clearAllRequestsForPackage(TEST_PACKAGE);
        verify(mUserBackupManagerService)
                .prepareOperationTimeout(
                        eq(TEST_TOKEN), anyLong(), eq(mTask), eq(OpType.RESTORE_WAIT));
        verify(mBackupAgent).doDelayedRestoreCachedDataExpired(TEST_TOKEN, mBackupManagerBinder);

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mMonitor).onEvent(bundleCaptor.capture());
        assertThat(bundleCaptor.getValue().getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID))
                .isEqualTo(BackupManagerMonitor.LOG_EVENT_ID_START_DELAYED_RESTORE_CLEANUP);
    }

    @Test
    public void testExecute_packageNotFound_doesNothing() throws Exception {
        when(mPackageManager.getPackageInfoAsUser(TEST_PACKAGE, 0, TEST_USER_ID))
                .thenThrow(new PackageManager.NameNotFoundException());

        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);
        mTask.execute();

        verify(mDelayedRestoreJournal, never()).clearAllRequestsForPackage(any());
        verify(mBackupAgentConnectionManager, never())
                .bindToAgentSynchronous(any(), anyInt(), anyInt());
    }

    @Test
    public void testExecute_bindFailed_doesNotCallAgent() throws Exception {
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);

        when(mBackupAgentConnectionManager.bindToAgentSynchronous(
                        eq(mApplicationInfo), anyInt(), eq(BackupDestination.CLOUD)))
                .thenReturn(null);

        mTask.execute();

        verify(mDelayedRestoreJournal).clearAllRequestsForPackage(TEST_PACKAGE);
        verify(mBackupAgent, never()).doDelayedRestoreCachedDataExpired(anyInt(), any());
    }

    @Test
    public void testExecute_agentRemoteException_handlesException() throws Exception {
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);

        when(mBackupAgentConnectionManager.bindToAgentSynchronous(
                        eq(mApplicationInfo), anyInt(), eq(BackupDestination.CLOUD)))
                .thenReturn(mBackupAgent);

        doThrow(new RemoteException())
                .when(mBackupAgent)
                .doDelayedRestoreCachedDataExpired(anyInt(), any());

        // Should not throw exception
        mTask.execute();

        verify(mDelayedRestoreJournal).clearAllRequestsForPackage(TEST_PACKAGE);
        verify(mBackupAgent).doDelayedRestoreCachedDataExpired(TEST_TOKEN, mBackupManagerBinder);
    }

    @Test
    public void testOperationComplete_unbindsAgent() throws Exception {
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);

        when(mBackupAgentConnectionManager.bindToAgentSynchronous(
                        eq(mApplicationInfo), anyInt(), anyInt()))
                .thenReturn(mBackupAgent);

        mTask.execute();
        mTask.operationComplete(0);

        verify(mBackupAgentConnectionManager).unbindAgent(mApplicationInfo, false);
        verify(mOperationStorage).removeOperation(TEST_TOKEN);
        verify(mBackupHandler).removeMessages(MSG_RESTORE_OPERATION_TIMEOUT, mTask);
        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mMonitor, atLeast(1)).onEvent(bundleCaptor.capture());
        assertThat(bundleCaptor.getValue().getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID))
                .isEqualTo(BackupManagerMonitor.LOG_EVENT_ID_DELAYED_RESTORE_CLEANUP_FINISHED);
    }

    @Test
    public void testHandleCancel_unbindsAgent() throws Exception {
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);

        when(mBackupAgentConnectionManager.bindToAgentSynchronous(
                        eq(mApplicationInfo), anyInt(), anyInt()))
                .thenReturn(mBackupAgent);

        mTask.execute();
        mTask.handleCancel(0);

        verify(mBackupAgentConnectionManager).unbindAgent(mApplicationInfo, false);
        verify(mOperationStorage).removeOperation(TEST_TOKEN);
        verify(mBackupHandler).removeMessages(MSG_RESTORE_OPERATION_TIMEOUT, mTask);
        verify(mTransportManager).disposeOfTransportClient(eq(mTransportConnection), anyString());
    }

    @Test
    public void testOperationComplete_packageNotFound_doesNotCrash() throws Exception {
        when(mPackageManager.getPackageInfoAsUser(TEST_PACKAGE, 0, TEST_USER_ID))
                .thenThrow(new PackageManager.NameNotFoundException());
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);

        mTask.operationComplete(0);

        verify(mBackupAgentConnectionManager, never()).unbindAgent(any(), anyInt() == 1);
    }

    @Test
    public void testHandleCancel_packageNotFound_doesNotCrash() throws Exception {
        when(mPackageManager.getPackageInfoAsUser(TEST_PACKAGE, 0, TEST_USER_ID))
                .thenThrow(new PackageManager.NameNotFoundException());
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);

        mTask.handleCancel(0);

        verify(mBackupAgentConnectionManager, never()).unbindAgent(any(), anyInt() == 1);
    }

    @Test
    public void testOperationComplete_pendingRestoresEmpty_setsRestoreInProgressFalse()
            throws Exception {
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);
        when(mPendingRestores.size()).thenReturn(0);

        mTask.operationComplete(0);

        verify(mUserBackupManagerService).setRestoreInProgress(false);
        verify(mBackupHandler, never()).sendMessage(any());
    }

    @Test
    public void testOperationComplete_pendingRestoresNotEmpty_sendsMessage() throws Exception {
        mTask =
                new DelayedRestoreCleanupTask(
                        mUserBackupManagerService, TEST_PACKAGE, mOperationStorage);
        when(mPendingRestores.size()).thenReturn(1);
        when(mPendingRestores.remove()).thenReturn(mNextTask);
        Message msg = Message.obtain();
        when(mBackupHandler.obtainMessage(eq(MSG_BACKUP_RESTORE_STEP), eq(mNextTask)))
                .thenReturn(msg);

        mTask.operationComplete(0);

        verify(mBackupHandler).sendMessage(msg);
        verify(mUserBackupManagerService, never()).setRestoreInProgress(false);
    }
}
