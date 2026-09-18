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

package com.android.server.backup.restore;

import static com.android.server.backup.internal.BackupHandler.MSG_RESTORE_OPERATION_TIMEOUT;
import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

import android.app.ApplicationThreadConstants;
import android.app.IBackupAgent;
import android.app.backup.BackupAnnotations.BackupDestination;
import android.app.backup.DelayedRestoreRequest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.Presubmit;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.backup.BackupAgentConnectionManager;
import com.android.server.backup.BackupAgentTimeoutParameters;
import com.android.server.backup.BackupRestoreTask;
import com.android.server.backup.OperationStorage;
import com.android.server.backup.OperationStorage.OpType;
import com.android.server.backup.PackageManagerBackupAgent;
import com.android.server.backup.UserBackupManagerService;
import com.android.server.backup.internal.BackupHandler;
import com.android.server.backup.utils.BackupEligibilityRules;
import com.android.server.backup.transport.BackupTransportClient;
import com.android.server.backup.transport.TransportConnection;
import com.android.server.backup.TransportManager;

import android.app.backup.BackupManagerMonitor;
import android.app.backup.IBackupManagerMonitor;
import android.content.ComponentName;
import android.os.Bundle;
import org.mockito.ArgumentCaptor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class DelayedRestoreTaskTest {
    private static final String PACKAGE_NAME = "com.example.package";
    private static final int USER_ID = 10;
    private static final int OP_TOKEN = 12345;
    private static final String TRANSPORT_DIR_NAME = "transport_dir";
    private static final long AGENT_TIMEOUT_MILLIS = 1000L;

    @Mock private UserBackupManagerService mBackupManagerService;
    @Mock private PackageManager mPackageManager;
    @Mock private OperationStorage mOperationStorage;
    @Mock private BackupAgentConnectionManager mBackupAgentConnectionManager;
    @Mock private BackupAgentTimeoutParameters mAgentTimeoutParameters;
    @Mock private PackageManagerBackupAgent mPmAgent;
    @Mock private IBackupAgent mBackupAgent;
    @Mock private android.app.backup.IBackupManager mBackupManagerBinder;
    @Mock private DelayedRestoreJournal mDelayedRestoreJournal;
    @Mock private BackupHandler mBackupHandler;
    @Mock private BackupEligibilityRules mBackupEligibilityRules;
    @Mock private Queue<BackupRestoreTask> mPendingRestores;
    @Mock private TransportConnection mTransportConnection;
    @Mock private BackupTransportClient mTransport;
    @Mock private IBackupManagerMonitor mMonitor;
    @Mock private TransportManager mTransportManager;

    private DelayedRestoreTask mTask;
    private DelayedRestoreRequest mRequest;
    private Queue<String> mRequesterPackageNames;
    private Context mContext;
    private File mBaseStateDir;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = InstrumentationRegistry.getContext();
        mBaseStateDir = mContext.getFilesDir();

        mRequest =
                new DelayedRestoreRequest.Builder(DelayedRestoreRequest.TYPE_APP_INSTALL)
                        .setPackageName(PACKAGE_NAME)
                        .build();
        mRequesterPackageNames = new ArrayDeque();
        mRequesterPackageNames.add(PACKAGE_NAME);

        when(mBackupManagerService.generateRandomIntegerToken()).thenReturn(OP_TOKEN);
        when(mBackupManagerService.makeMetadataAgent(null)).thenReturn(mPmAgent);
        when(mBackupManagerService.getBackupAgentConnectionManager())
                .thenReturn(mBackupAgentConnectionManager);
        when(mBackupManagerService.getPackageManager()).thenReturn(mPackageManager);
        when(mBackupManagerService.getUserId()).thenReturn(USER_ID);
        when(mBackupManagerService.getAgentTimeoutParameters()).thenReturn(mAgentTimeoutParameters);
        when(mAgentTimeoutParameters.getRestoreAgentTimeoutMillis(anyInt()))
                .thenReturn(AGENT_TIMEOUT_MILLIS);
        when(mBackupManagerService.getBackupManagerBinder()).thenReturn(mBackupManagerBinder);
        when(mBackupManagerService.getBaseStateDir()).thenReturn(mBaseStateDir);
        when(mBackupManagerService.getDelayedRestoreJournal()).thenReturn(mDelayedRestoreJournal);
        when(mBackupManagerService.getBackupHandler()).thenReturn(mBackupHandler);
        when(mBackupManagerService.getEligibilityRulesForOperation(BackupDestination.CLOUD))
                .thenReturn(mBackupEligibilityRules);
        when(mBackupEligibilityRules.getBackupDestination()).thenReturn(BackupDestination.CLOUD);
        when(mBackupManagerService.getPendingRestores()).thenReturn(mPendingRestores);

        when(mTransportConnection.connectOrThrow(any())).thenReturn(mTransport);
        when(mTransport.getBackupManagerMonitor()).thenReturn(mMonitor);
        when(mTransportConnection.getTransportComponent())
                .thenReturn(new ComponentName(PACKAGE_NAME, "Transport"));
        when(mBackupManagerService.getTransportManager()).thenReturn(mTransportManager);
        when(mTransportManager.getTransportDirName(any(ComponentName.class)))
                .thenReturn(TRANSPORT_DIR_NAME);
        when(mTransportManager.getCurrentTransportClient(anyString()))
                .thenReturn(mTransportConnection);

        File transportDir = new File(mBaseStateDir, TRANSPORT_DIR_NAME);
        transportDir.mkdirs();
    }

    @Test
    public void testExecute_kvRestore_callsDoDelayedRestore() throws Exception {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.uid = 1000;
        packageInfo.packageName = PACKAGE_NAME;
        packageInfo.applicationInfo.longVersionCode = 100L;
        when(mPackageManager.getPackageInfoAsUser(PACKAGE_NAME, 0, USER_ID))
                .thenReturn(packageInfo);

        when(mBackupAgentConnectionManager.bindToAgentSynchronous(
                        eq(packageInfo.applicationInfo),
                        eq(ApplicationThreadConstants.BACKUP_MODE_RESTORE),
                        eq(BackupDestination.CLOUD)))
                .thenReturn(mBackupAgent);

        mTask =
                new DelayedRestoreTask(
                        mRequest, mBackupManagerService, mRequesterPackageNames, mOperationStorage);

        mTask.execute();

        verify(mBackupManagerService)
                .prepareOperationTimeout(
                        eq(OP_TOKEN), eq(AGENT_TIMEOUT_MILLIS), eq(mTask), eq(OpType.RESTORE_WAIT));
        verify(mBackupAgent)
                .doDelayedRestore(
                        eq(mRequest),
                        any(ParcelFileDescriptor.class),
                        eq(mBackupManagerBinder),
                        eq(100L),
                        eq(OP_TOKEN));

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mMonitor, atLeastOnce()).onEvent(bundleCaptor.capture());
        Bundle bundle = bundleCaptor.getValue();
        // Verify KV restore event
        assertThat(bundle.getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID))
                .isEqualTo(BackupManagerMonitor.LOG_EVENT_ID_KV_RESTORE);
    }

    @Test
    public void testExecute_fullRestore_callsDoDelayedFullRestore() throws Exception {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.uid = 1000;
        packageInfo.packageName = PACKAGE_NAME;
        when(mPackageManager.getPackageInfoAsUser(PACKAGE_NAME, 0, USER_ID))
                .thenReturn(packageInfo);

        when(mBackupEligibilityRules.appGetsFullBackup(packageInfo)).thenReturn(true);

        when(mBackupAgentConnectionManager.bindToAgentSynchronous(
                        eq(packageInfo.applicationInfo),
                        eq(ApplicationThreadConstants.BACKUP_MODE_RESTORE),
                        eq(BackupDestination.CLOUD)))
                .thenReturn(mBackupAgent);

        mTask =
                new DelayedRestoreTask(
                        mRequest, mBackupManagerService, mRequesterPackageNames, mOperationStorage);

        mTask.execute();

        verify(mBackupManagerService)
                .prepareOperationTimeout(
                        eq(OP_TOKEN), eq(AGENT_TIMEOUT_MILLIS), eq(mTask), eq(OpType.RESTORE_WAIT));
        verify(mBackupAgent)
                .doDelayedFullRestore(eq(mRequest), eq(mBackupManagerBinder), eq(OP_TOKEN));

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mMonitor, atLeastOnce()).onEvent(bundleCaptor.capture());
        Bundle bundle = bundleCaptor.getValue();
        // Verify full restore event
        assertThat(bundle.getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID))
                .isEqualTo(BackupManagerMonitor.LOG_EVENT_ID_FULL_RESTORE);
    }

    @Test
    public void testExecute_packageNotFound_doesNothing() throws Exception {
        when(mPackageManager.getPackageInfoAsUser(PACKAGE_NAME, 0, USER_ID))
                .thenThrow(new PackageManager.NameNotFoundException());

        mTask =
                new DelayedRestoreTask(
                        mRequest, mBackupManagerService, mRequesterPackageNames, mOperationStorage);

        mTask.execute();

        verify(mBackupManagerService, never())
                .prepareOperationTimeout(anyInt(), anyLong(), any(), anyInt());
        verify(mBackupAgentConnectionManager, never())
                .bindToAgentSynchronous(any(), anyInt(), anyInt());

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mMonitor, atLeastOnce()).onEvent(bundleCaptor.capture());
        Bundle bundle = bundleCaptor.getValue();
        assertThat(bundle.getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID))
                .isEqualTo(BackupManagerMonitor.LOG_EVENT_ID_PACKAGE_NOT_PRESENT);
    }

    @Test
    public void testOperationComplete_cleansUp() throws Exception {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.packageName = PACKAGE_NAME;
        when(mPackageManager.getPackageInfoAsUser(PACKAGE_NAME, 0, USER_ID))
                .thenReturn(packageInfo);

        mTask =
                new DelayedRestoreTask(
                        mRequest, mBackupManagerService, mRequesterPackageNames, mOperationStorage);
        // Ensure state file is set up
        when(mBackupAgentConnectionManager.bindToAgentSynchronous(any(), anyInt(), anyInt()))
                .thenReturn(mBackupAgent);
        mTask.execute();

        mTask.operationComplete(0);

        verify(mBackupManagerService).getEligibilityRulesForOperation(BackupDestination.CLOUD);
        verify(mDelayedRestoreJournal).removeRequest(mRequest, PACKAGE_NAME);
        verify(mOperationStorage).removeOperation(OP_TOKEN);
        verify(mBackupAgentConnectionManager)
                .unbindAgent(eq(packageInfo.applicationInfo), eq(false));
        verify(mBackupHandler).removeMessages(eq(MSG_RESTORE_OPERATION_TIMEOUT), eq(mTask));

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mMonitor, atLeastOnce()).onEvent(bundleCaptor.capture());
        Bundle bundle = bundleCaptor.getValue();
        assertThat(bundle.getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID))
                .isEqualTo(BackupManagerMonitor.LOG_EVENT_ID_PACKAGE_RESTORE_FINISHED);
    }

    @Test
    public void testHandleCancel_cleansUp() throws Exception {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.packageName = PACKAGE_NAME;
        when(mPackageManager.getPackageInfoAsUser(PACKAGE_NAME, 0, USER_ID))
                .thenReturn(packageInfo);

        // Ensure state file is set up by executing (which sets up mCurrentPackageAppInfo needed for
        // unbindAgent)
        when(mBackupAgentConnectionManager.bindToAgentSynchronous(any(), anyInt(), anyInt()))
                .thenReturn(mBackupAgent);

        mTask =
                new DelayedRestoreTask(
                        mRequest, mBackupManagerService, mRequesterPackageNames, mOperationStorage);

        // Execute to populate mCurrentPackageAppInfo
        mTask.execute();

        mTask.handleCancel(BackupRestoreTask.CancellationReason.TIMEOUT);

        verify(mOperationStorage).removeOperation(OP_TOKEN);
        verify(mBackupAgentConnectionManager)
                .unbindAgent(eq(packageInfo.applicationInfo), eq(false));
        verify(mBackupHandler).removeMessages(eq(MSG_RESTORE_OPERATION_TIMEOUT), eq(mTask));

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mMonitor, atLeastOnce()).onEvent(bundleCaptor.capture());
        Bundle bundle = bundleCaptor.getValue();
        assertThat(bundle.getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID))
                .isEqualTo(BackupManagerMonitor.LOG_EVENT_ID_KEY_VALUE_RESTORE_TIMEOUT);
    }

    @Test
    public void testHandleCancel_agentDisconnected_logsRestoreCancelled() throws Exception {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.packageName = PACKAGE_NAME;
        when(mPackageManager.getPackageInfoAsUser(PACKAGE_NAME, 0, USER_ID))
                .thenReturn(packageInfo);

        // Ensure state file is set up by executing (which sets up mCurrentPackageAppInfo needed for
        // unbindAgent)
        when(mBackupAgentConnectionManager.bindToAgentSynchronous(any(), anyInt(), anyInt()))
                .thenReturn(mBackupAgent);

        mTask =
                new DelayedRestoreTask(
                        mRequest, mBackupManagerService, mRequesterPackageNames, mOperationStorage);

        // Execute to populate mCurrentPackageAppInfo
        mTask.execute();

        mTask.handleCancel(BackupRestoreTask.CancellationReason.AGENT_DISCONNECTED);

        verify(mOperationStorage).removeOperation(OP_TOKEN);
        verify(mBackupAgentConnectionManager)
                .unbindAgent(eq(packageInfo.applicationInfo), eq(false));
        verify(mBackupHandler).removeMessages(eq(MSG_RESTORE_OPERATION_TIMEOUT), eq(mTask));

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mMonitor, atLeastOnce()).onEvent(bundleCaptor.capture());
        Bundle bundle = bundleCaptor.getValue();
        assertThat(bundle.getInt(BackupManagerMonitor.EXTRA_LOG_EVENT_ID))
                .isEqualTo(BackupManagerMonitor.LOG_EVENT_ID_RESTORE_CANCELLED);
    }
}
