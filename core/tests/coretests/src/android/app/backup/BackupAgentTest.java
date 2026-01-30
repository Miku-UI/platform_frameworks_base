/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.backup;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.IBackupAgent;
import android.app.backup.BackupAgent.IncludeExcludeRules;
import android.app.backup.BackupAnnotations.BackupDestination;
import android.app.backup.BackupAnnotations.OperationType;
import android.app.backup.FullBackup.BackupScheme.PathWithRequiredFlags;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.backup.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class BackupAgentTest {
    // An arbitrary user.
    private static final UserHandle USER_HANDLE = new UserHandle(15);
    private static final String DATA_TYPE_BACKED_UP = "test data type";

    @Mock IBackupManager mIBackupManager;
    @Mock FullBackup.BackupScheme mBackupScheme;

    private Context mContext;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testGetIncludeExcludeRules_isNotMigration_returnsRules() throws Exception {
        PathWithRequiredFlags path = new PathWithRequiredFlags("path", /* requiredFlags= */ 0);
        Map<String, Set<PathWithRequiredFlags>> includePaths =
                Collections.singletonMap("test", Collections.singleton(path));
        ArraySet<PathWithRequiredFlags> excludePaths = new ArraySet<>();
        excludePaths.add(path);
        IncludeExcludeRules expectedRules = new IncludeExcludeRules(includePaths, excludePaths);

        BackupAgent backupAgent = getAgentForBackupDestination(BackupDestination.CLOUD);
        when(mBackupScheme.maybeParseAndGetCanonicalExcludePaths()).thenReturn(excludePaths);
        when(mBackupScheme.maybeParseAndGetCanonicalIncludePaths()).thenReturn(includePaths);

        IncludeExcludeRules rules = backupAgent.getIncludeExcludeRules(mBackupScheme);
        assertThat(rules).isEqualTo(expectedRules);
    }

    @Test
    public void getBackupRestoreEventLogger_beforeOnCreate_isNull() {
        BackupAgent agent = new TestFullBackupAgent();

        assertThat(agent.getBackupRestoreEventLogger()).isNull();
    }

    @Test
    public void getBackupRestoreEventLogger_afterOnCreateForBackup_initializedForBackup() {
        BackupAgent agent = new TestFullBackupAgent();
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.BACKUP);

        assertThat(agent.getBackupRestoreEventLogger().getOperationType())
                .isEqualTo(OperationType.BACKUP);
    }

    @Test
    public void getBackupRestoreEventLogger_afterOnCreateForRestore_initializedForRestore() {
        BackupAgent agent = new TestFullBackupAgent();
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.RESTORE);

        assertThat(agent.getBackupRestoreEventLogger().getOperationType())
                .isEqualTo(OperationType.RESTORE);
    }

    @Test
    public void getBackupRestoreEventLogger_afterBackup_containsLogsLoggedByAgent()
            throws Exception {
        BackupAgent agent = new TestFullBackupAgent();
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.BACKUP);

        // TestFullBackupAgent logs DATA_TYPE_BACKED_UP when onFullBackup is called.
        agent.onFullBackup(new FullBackupDataOutput(/* quota= */ 0));

        assertThat(agent.getBackupRestoreEventLogger().getLoggingResults().get(0).getDataType())
                .isEqualTo(DATA_TYPE_BACKED_UP);
    }

    @Test
    public void testClearLogger_clearsPendingLogs() throws Exception {
        BackupAgent agent = new TestFullBackupAgent();
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.BACKUP);

        agent.onFullBackup(new FullBackupDataOutput(/* quota= */ 0));
        agent.clearBackupRestoreEventLogger();

        assertThat(agent.getBackupRestoreEventLogger().getLoggingResults().size()).isEqualTo(0);
    }

    @Test
    public void testClearLoggerBetweenBackups_restartsSuccessCount() throws Exception {
        BackupAgent agent = new TestFullBackupAgent();
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.BACKUP);

        agent.onFullBackup(new FullBackupDataOutput(/* quota= */ 0));
        agent.clearBackupRestoreEventLogger();
        agent.onFullBackup(new FullBackupDataOutput(/* quota= */ 0));

        assertThat(agent.getBackupRestoreEventLogger().getLoggingResults().get(0).getSuccessCount())
                .isEqualTo(1);
    }

    @Test
    public void doRestoreFile_agentOverrideIgnoresFile_consumesAllBytesInBuffer() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_CLEAR_PIPE_AFTER_RESTORE_FILE);
        BackupAgent agent = new TestRestoreIgnoringFullBackupAgent();
        agent.attach(mContext);
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.RESTORE);
        IBackupAgent agentBinder = (IBackupAgent) agent.onBind();

        ParcelFileDescriptor[] pipes = ParcelFileDescriptor.createPipe();
        FileOutputStream writeSide = new FileOutputStream(pipes[1].getFileDescriptor());
        writeSide.write("Hello".getBytes(StandardCharsets.UTF_8));

        agentBinder.doRestoreFile(
                pipes[0],
                /* length= */ 5,
                BackupAgent.TYPE_FILE,
                FullBackup.FILES_TREE_TOKEN,
                /* path= */ "hello_file",
                /* mode= */ 0666,
                /* mtime= */ 12345,
                /* token= */ 6789,
                mIBackupManager,
                /* appVersionCode= */ 0,
                /* transportFlags= */ 0,
                /* contentVersion= */ "");

        try (FileInputStream in = new FileInputStream(pipes[0].getFileDescriptor())) {
            assertThat(in.available()).isEqualTo(0);
        } finally {
            pipes[0].close();
            pipes[1].close();
        }
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER})
    public void doRestoreFile_flagOff_doesNotSetExtraParams() throws Exception {
        TestWhichOnRestoreFileCalledBackupAgent agent =
                new TestWhichOnRestoreFileCalledBackupAgent();
        agent.attach(mContext);
        agent.onCreate(USER_HANDLE, BackupDestination.DEVICE_TRANSFER, OperationType.RESTORE);
        IBackupAgent agentBinder = (IBackupAgent) agent.onBind();
        long appVersionCode = 1;
        int transportFlags = BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER;
        String contentVersion = "1.0";

        ParcelFileDescriptor[] pipes = ParcelFileDescriptor.createPipe();
        FileOutputStream writeSide = new FileOutputStream(pipes[1].getFileDescriptor());
        writeSide.write("Hello".getBytes(StandardCharsets.UTF_8));

        agentBinder.doRestoreFile(
                pipes[0],
                /* length= */ 5,
                BackupAgent.TYPE_FILE,
                FullBackup.FILES_TREE_TOKEN,
                /* path= */ "hello_file",
                /* mode= */ 0666,
                /* mtime= */ 12345,
                /* token= */ 6789,
                mIBackupManager,
                appVersionCode,
                transportFlags,
                contentVersion);

        assertThat(agent.mOldOnRestoreFileCalled).isTrue();
        assertThat(agent.mNewOnRestoreFileCalled).isFalse();
        assertThat(agent.mAppVersionCodeReceived).isEqualTo(0);
        assertThat(agent.mTransportFlagsReceived).isEqualTo(0);
        assertThat(agent.mContentVersionReceived).isEqualTo("");
        try (FileInputStream in = new FileInputStream(pipes[0].getFileDescriptor())) {
            assertThat(in.available()).isEqualTo(0);
        } finally {
            pipes[0].close();
            pipes[1].close();
        }
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER})
    public void doRestoreFile_flagOn_setsExtraParams() throws Exception {
        TestWhichOnRestoreFileCalledBackupAgent agent =
                new TestWhichOnRestoreFileCalledBackupAgent();
        agent.attach(mContext);
        agent.onCreate(USER_HANDLE, BackupDestination.DEVICE_TRANSFER, OperationType.RESTORE);
        IBackupAgent agentBinder = (IBackupAgent) agent.onBind();
        long appVersionCode = 1;
        int transportFlags = BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER;
        String contentVersion = "1.0";

        ParcelFileDescriptor[] pipes = ParcelFileDescriptor.createPipe();
        FileOutputStream writeSide = new FileOutputStream(pipes[1].getFileDescriptor());
        writeSide.write("Hello".getBytes(StandardCharsets.UTF_8));

        agentBinder.doRestoreFile(
                pipes[0],
                /* length= */ 5,
                BackupAgent.TYPE_FILE,
                FullBackup.FILES_TREE_TOKEN,
                /* path= */ "hello_file",
                /* mode= */ 0666,
                /* mtime= */ 12345,
                /* token= */ 6789,
                mIBackupManager,
                appVersionCode,
                transportFlags,
                contentVersion);

        assertThat(agent.mOldOnRestoreFileCalled).isFalse();
        assertThat(agent.mNewOnRestoreFileCalled).isTrue();
        assertThat(agent.mAppVersionCodeReceived).isEqualTo(appVersionCode);
        assertThat(agent.mTransportFlagsReceived).isEqualTo(transportFlags);
        assertThat(agent.mContentVersionReceived).isEqualTo(contentVersion);
        try (FileInputStream in = new FileInputStream(pipes[0].getFileDescriptor())) {
            assertThat(in.available()).isEqualTo(0);
        } finally {
            pipes[0].close();
            pipes[1].close();
        }
    }

    @Test
    @DisableFlags({Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER})
    public void doMeasureFullBackup_flagOff_callOnFullBackup() throws Exception {
        TestMeasureSizeBackupAgent agent = new TestMeasureSizeBackupAgent(1024);
        agent.attach(mContext);
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.BACKUP);
        IBackupAgent agentBinder = (IBackupAgent) agent.onBind();

        agentBinder.doMeasureFullBackup(
                /* quotaBytes= */ 2048, /* token= */ 0, mIBackupManager, /* transportFlags= */ 0);

        assertThat(agent.mOnMeasureFullBackupCalled).isFalse();
        assertThat(agent.mOnFullBackupCalled).isTrue();
        verify(mIBackupManager).opCompleteForUser(USER_HANDLE.getIdentifier(), 0, 0);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_CROSS_PLATFORM_TRANSFER})
    public void doMeasureFullBackup_flagOn_callsOnMeasureFullBackup() throws Exception {
        TestMeasureSizeBackupAgent agent = new TestMeasureSizeBackupAgent(1024);
        agent.attach(mContext);
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.BACKUP);
        IBackupAgent agentBinder = (IBackupAgent) agent.onBind();

        agentBinder.doMeasureFullBackup(
                /* quotaBytes= */ 2048, /* token= */ 0, mIBackupManager, /* transportFlags= */ 0);

        assertThat(agent.mOnMeasureFullBackupCalled).isTrue();
        assertThat(agent.mOnFullBackupCalled).isFalse();
        verify(mIBackupManager).opCompleteForUser(USER_HANDLE.getIdentifier(), 0, 1024);
    }

    @Test
    public void doMeasureFullBackup_flagOn_negativeSize_callsOnFullBackup() throws Exception {
        TestMeasureSizeBackupAgent agent = new TestMeasureSizeBackupAgent(-1);
        agent.attach(mContext);
        agent.onCreate(USER_HANDLE, BackupDestination.CLOUD, OperationType.BACKUP);
        IBackupAgent agentBinder = (IBackupAgent) agent.onBind();

        agentBinder.doMeasureFullBackup(
                /* quotaBytes= */ 2048, /* token= */ 0, mIBackupManager, /* transportFlags= */ 0);

        assertThat(agent.mOnMeasureFullBackupCalled).isTrue();
        assertThat(agent.mOnFullBackupCalled).isTrue();
        verify(mIBackupManager).opCompleteForUser(USER_HANDLE.getIdentifier(), 0, 0);
    }

    private BackupAgent getAgentForBackupDestination(@BackupDestination int backupDestination) {
        BackupAgent agent = new TestFullBackupAgent();
        agent.onCreate(USER_HANDLE, backupDestination);
        return agent;
    }

    private static class TestFullBackupAgent extends BackupAgent {
        @Override
        public void onBackup(
                ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState)
                throws IOException {
            // Left empty as this is a full backup agent.
        }

        @Override
        public void onFullBackup(FullBackupDataOutput data) {
            getBackupRestoreEventLogger().logItemsBackedUp(DATA_TYPE_BACKED_UP, 1);
        }

        @Override
        public void onRestore(
                BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
                throws IOException {
            // Left empty as this is a full backup agent.
        }
    }

    private static class TestRestoreIgnoringFullBackupAgent extends TestFullBackupAgent {
        @Override
        protected void onRestoreFile(
                ParcelFileDescriptor data,
                long size,
                int type,
                String domain,
                String path,
                long mode,
                long mtime,
                long appVersionCode,
                int transportFlags,
                String contentVersion)
                throws IOException {
            // Ignore the file and don't consume any data.
        }
    }

    private static class TestWhichOnRestoreFileCalledBackupAgent extends TestFullBackupAgent {
        boolean mOldOnRestoreFileCalled = false;
        boolean mNewOnRestoreFileCalled = false;
        long mAppVersionCodeReceived = 0;
        int mTransportFlagsReceived = 0;
        String mContentVersionReceived = "";

        @Override
        public void onRestoreFile(
                ParcelFileDescriptor data,
                long size,
                File destination,
                int type,
                long mode,
                long mtime)
                throws IOException {
            mOldOnRestoreFileCalled = true;
            FullBackup.restoreFile(data, size, type, mode, mtime, null);
        }

        @Override
        public void onRestoreFile(@NonNull FullRestoreDataInput data) throws IOException {
            mNewOnRestoreFileCalled = true;
            mAppVersionCodeReceived = data.getAppVersionCode();
            mTransportFlagsReceived = data.getTransportFlags();
            mContentVersionReceived = data.getContentVersion();
            FullBackup.restoreFile(
                    data.getData(),
                    data.getSize(),
                    data.getType(),
                    data.getMode(),
                    data.getModificationTimeSeconds(),
                    null);
        }
    }

    private static class TestMeasureSizeBackupAgent extends BackupAgent {
        private final long mSize;
        private boolean mOnFullBackupCalled;
        private boolean mOnMeasureFullBackupCalled;

        TestMeasureSizeBackupAgent(long size) {
            this.mSize = size;
        }

        @Override
        public void onBackup(
                ParcelFileDescriptor oldState,
                BackupDataOutput data,
                ParcelFileDescriptor newState) {
            // Left empty as this is a full backup agent.
        }

        @Override
        public void onRestore(
                BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) {
            // Left empty as this is a full backup agent.
        }

        @Override
        public void onFullBackup(FullBackupDataOutput data) {
            mOnFullBackupCalled = true;
        }

        @Override
        public long onEstimateFullBackupBytes(long quotaBytes, int transportFlags) {
            mOnMeasureFullBackupCalled = true;
            return mSize;
        }
    }
}
