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
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_PACKAGE_NAME;
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_CURRENT_TIME_MILLIS;
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_PACKAGE_NAME;
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_REALTIME_NANOS;
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_UID;
import static com.android.server.privatecompute.AuditModeTestUtils.assertEqualsToTestBundle;
import static com.android.server.privatecompute.AuditModeTestUtils.getTestBundle;
import static com.android.server.privatecompute.AuditModeTestUtils.getTestEntry;
import static com.android.server.privatecompute.AuditModeTestUtils.readAuditLogFileFromFile;
import static com.android.server.privatecompute.AuditModeTestUtils.readAuditLogFileFromStream;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.os.PersistableBundle;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.runner.AndroidJUnit4;
import com.android.server.privatecompute.AuditModeContext.Injector;
import com.google.common.collect.ImmutableList;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AuditModeContext}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class AuditModeContextTest {

    private static final String TEST_PACKAGE_NAME = "test_package";
    private static final int MAX_FILES = 3;
    private static final int MAX_SIZE_KILOBYTES = 1 * 1024; // 1 MB, otherwise the test is slow.

    private AuditModeContext mAuditModeContext;

    @Rule(order = 0)
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule(order = 1)
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Mock private Injector mInjector;

    @Before
    public void setUp() {
        when(mInjector.auditModeLogFileMaxSizeKb()).thenReturn(MAX_SIZE_KILOBYTES);
        when(mInjector.auditModeMaxLogFiles()).thenReturn(MAX_FILES);
        mAuditModeContext =
                new AuditModeContext(
                        UserHandle.getUserId(TEST_UID),
                        newDirectExecutorService(),
                        newDirectExecutorService(),
                        mTemporaryFolder.getRoot(),
                        mInjector);
    }

    @Test
    public void testWriteToAuditLog_oneBundle_getsWrittenToDiskAndCanBeParsedBack()
            throws Exception {
        // Currently this test's implementation is identical to testStopAuditing_writesPendingData,
        // but in the future we might change how we write. These are two different behaviors that
        // need to be tested.
        mAuditModeContext.writeToAuditLog(getTestBundle(), TEST_PACKAGE_NAME, TEST_UID);

        mAuditModeContext.stopAuditing(); // Triggers a write with pending data

        File file = mAuditModeContext.getCurrentAuditLogFile();
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEqualsToTestBundle(entries.get(0).mData);
    }

    @Test
    public void testWriteToAuditLog_bufferFull_getsWrittenToDisk() throws Exception {
        PersistableBundle testBundle = getTestBundle();
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, 234000L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);
        }
        File file = mAuditModeContext.getCurrentAuditLogFile();
        assertThat(file.length()).isEqualTo(0); // no write yet

        // buffer full, triggers a write
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);

        assertThat(file.length()).isNotEqualTo(0); // write triggered
    }

    @Test
    public void testWriteToAuditLog_whenBufferFull_createsNewFile() throws Exception {
        PersistableBundle testBundle = getTestBundle();
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, 234000L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);
        }
        File file = mAuditModeContext.getCurrentAuditLogFile();

        // buffer full, triggers a write
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);

        File newFile = mAuditModeContext.getCurrentAuditLogFile();
        assertThat(newFile.getName()).isNotEqualTo(file.getName());
    }

    @Test
    public void testWriteToAuditLog_differentUser_throwsSecurityException() {
        AuditModeContext auditModeContext =
                new AuditModeContext(
                        UserHandle.getUserId(TEST_UID) + 1,
                        newDirectExecutorService(),
                        newDirectExecutorService(),
                        mTemporaryFolder.getRoot(),
                        mInjector);
        assertThrows(
                SecurityException.class,
                () ->
                        auditModeContext.writeToAuditLog(
                                getTestBundle(), TEST_PACKAGE_NAME, TEST_UID));
    }

    @Test
    public void testWriteToAuditLog_doesNotCreateMoreThanMaxFiles() throws Exception {
        // Arrange
        File newFolder = mTemporaryFolder.newFolder();
        AuditModeContext auditModeContext =
                new AuditModeContext(
                        UserHandle.getUserId(TEST_UID),
                        newDirectExecutorService(),
                        newDirectExecutorService(),
                        newFolder,
                        mInjector);
        PersistableBundle testBundle = getTestBundle();
        File file0 = auditModeContext.getCurrentAuditLogFile();
        // This creates MAX_FILES files.
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, 234000L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < MAX_FILES * nEntriesToWrite; i++) {
            auditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);
        }
        PersistableBundle testBundle2 = testBundle.deepCopy();
        testBundle2.putInt("test_key2", 123);

        // Sanity check: the first file should be full, and contain only testBundles.
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file0);
        assertEquals(entries.size(), nEntriesToWrite);
        for (int i = 0; i < nEntriesToWrite; i++) {
            assertEqualsToTestBundle(entries.get(i).mData);
        }

        // Act: write one more bundle to the ringbuffer, then trigger a write.
        auditModeContext.writeToAuditLog(testBundle2, TEST_PACKAGE_NAME, TEST_UID);
        auditModeContext.stopAuditing();

        // Assert: Should behave like a ringbuffer, and overwrite the first file.
        // First file only contains testBundle2.
        assertThat(newFolder.listFiles()).hasLength(MAX_FILES);
        File newFile = auditModeContext.getCurrentAuditLogFile();
        assertThat(newFile.getName()).isEqualTo("audit_log.0.bin");
        List<AuditLogEntry> entries2 = readAuditLogFileFromFile(newFile);
        assertEquals(entries2.size(), 1);
        assertEquals(entries2.get(0).mData.getInt("test_key2"), 123);
    }

    @Test
    public void testWriteToAuditLog_overwritesFileDoesNotAppend() throws Exception {
        File file = mAuditModeContext.getCurrentAuditLogFile();
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(new byte[] {1, 2, 3}); // left-over file with garbage data
            fileOutputStream.flush();
        }
        PersistableBundle testBundle = getTestBundle();
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, 234000L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);
        }

        // buffer full, triggers a write
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);

        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        List<AuditLogEntry> entries = readAuditLogFileFromStream(stream);
        assertEquals(nEntriesToWrite, entries.size()); // should already be sufficient
        assertEqualsToTestBundle(entries.get(0).mData); // sanity-check: first bundle is correct
    }

    @Test
    public void testWriteToAuditLog_correctlySetsPackageName() throws Exception {
        String packageName = "package_name";
        PersistableBundle testBundle = getTestBundle();
        File file = mAuditModeContext.getCurrentAuditLogFile();

        mAuditModeContext.writeToAuditLog(testBundle, packageName, TEST_UID);

        mAuditModeContext.stopAuditing(); // flushes 1 log entry to disk
        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        List<AuditLogEntry> entries = readAuditLogFileFromStream(stream);
        assertEquals(1, entries.size());
        assertEquals(entries.get(0).mCallingPackage, packageName);
    }

    @Test
    public void testStopAuditing_writesPendingData() throws Exception {
        mAuditModeContext.writeToAuditLog(getTestBundle(), TEST_PACKAGE_NAME, TEST_UID);

        mAuditModeContext.stopAuditing();

        File file = mAuditModeContext.getCurrentAuditLogFile();
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEqualsToTestBundle(entries.get(0).mData);
    }

    @Test
    public void testStopAuditing_whenStopped_noMoreDataIsWritten() throws Exception {
        PersistableBundle testBundle = getTestBundle();
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);
        mAuditModeContext.stopAuditing(); // flushes 1 log entry to disk
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, 234000L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);

        // nEntriesToWrite writes, would trigger a write if not for the stopAuditing()
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);
        }

        File file = mAuditModeContext.getCurrentAuditLogFile();
        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        List<AuditLogEntry> entries = readAuditLogFileFromStream(stream);
        assertEquals(1, entries.size());
    }

    @Test
    public void testWriteToAuditLog_realExecutors_bundleGetsWrittenToDiskAndCanBeParsedBack()
            throws Exception {
        // Real executors.
        ExecutorService serializerExecutor = AuditModeContext.getBundleSerializerExecutorService();
        ExecutorService writerExecutor = AuditModeContext.getDiskWriterExecutorService();
        mAuditModeContext =
                new AuditModeContext(
                        UserHandle.getUserId(TEST_UID),
                        serializerExecutor,
                        writerExecutor,
                        mTemporaryFolder.getRoot(),
                        mInjector);
        mAuditModeContext.writeToAuditLog(getTestBundle(), TEST_PACKAGE_NAME, TEST_UID);
        serializerExecutor.awaitTermination(10, TimeUnit.SECONDS); // Wait for the pending tasks.
        writerExecutor.awaitTermination(10, TimeUnit.SECONDS); // Wait for the pending write.

        mAuditModeContext.stopAuditing(); // Triggers a write with pending data

        File file = mAuditModeContext.getCurrentAuditLogFile();
        assertThat(file.toPath().toString()).contains("audit_log.0.bin");
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEqualsToTestBundle(entries.get(0).mData);
        serializerExecutor.shutdown();
        serializerExecutor.awaitTermination(1, TimeUnit.SECONDS);
        writerExecutor.shutdown();
        writerExecutor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testWriteToAuditLog_realExecutors_writeIsAsync() throws Exception {
        // Real executors.
        ExecutorService serializerExecutor = AuditModeContext.getBundleSerializerExecutorService();
        ExecutorService writerExecutor = AuditModeContext.getDiskWriterExecutorService();
        mAuditModeContext =
                new AuditModeContext(
                        UserHandle.getUserId(TEST_UID),
                        serializerExecutor,
                        writerExecutor,
                        mTemporaryFolder.getRoot(),
                        mInjector);
        CountDownLatch slowTaskStarted = new CountDownLatch(1);
        CountDownLatch allowSlowTaskToFinish = new CountDownLatch(1);
        serializerExecutor.execute( // Add a slow task to the serializerExecutor
                () -> {
                    slowTaskStarted.countDown(); // Signal that we have occupied the thread
                    try {
                        // Wait until the test says we can finish.
                        boolean ignored = allowSlowTaskToFinish.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
        slowTaskStarted.await(1, TimeUnit.SECONDS); // Wait until the slow task has started
        PersistableBundle testBundle = getTestBundle();
        AuditLogEntry entry = new AuditLogEntry(testBundle, 234L, 234000L, TEST_PACKAGE_NAME, 123);
        int entrySize = entry.toByteArray().length;
        int nEntriesToWrite = (int) Math.floor(1024 * MAX_SIZE_KILOBYTES / (double) entrySize);
        for (int i = 0; i < nEntriesToWrite; i++) {
            mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);
        }

        // Act: Triggers a write by the serializerExecutor, who is busy with the slow task.
        long startTime = System.currentTimeMillis();
        mAuditModeContext.writeToAuditLog(testBundle, TEST_PACKAGE_NAME, TEST_UID);
        long endTime = System.currentTimeMillis();

        // Assert: The write should be fast, even though the executor is busy with the slow task.
        assertThat(endTime - startTime).isLessThan(50L);

        // Cleanup: Let the background thread finish so we don't leak threads.
        allowSlowTaskToFinish.countDown();
        serializerExecutor.shutdown();
        serializerExecutor.awaitTermination(1, TimeUnit.SECONDS);
        writerExecutor.shutdown();
        writerExecutor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testSerializeAndWrite_invalidBundle_throwsSecurityExceptionAndDoesNotWrite()
            throws Exception {
        PersistableBundle invalidBundle = getDeepNestedBundle101();
        AuditLogEntry entry =
                new AuditLogEntry(invalidBundle, 234L, 234000L, TEST_PACKAGE_NAME, 123);

        assertThrows(SecurityException.class, () -> mAuditModeContext.serializeAndWrite(entry));

        mAuditModeContext.stopAuditing();

        File file = mAuditModeContext.getCurrentAuditLogFile();
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 0);
    }

    @Test
    public void testDeleteAuditLogFiles_deletesFolder() throws Exception {
        File folder = mTemporaryFolder.newFolder();
        new File(folder, "audit_log.0.bin").createNewFile();
        new File(folder, "audit_log.1.bin").createNewFile();
        assertThat(folder.exists()).isTrue();

        AuditModeContext.deleteAuditLogFiles(folder);

        assertThat(folder.exists()).isFalse();
    }

    @Test
    public void testDeleteAuditLogFiles_nonExistentFolder_doesNotThrow() {
        File folder = new File(mTemporaryFolder.getRoot(), "non_existent");
        assertThat(folder.exists()).isFalse();

        AuditModeContext.deleteAuditLogFiles(folder);

        // Assert: no exception thrown.
    }

    @Test
    public void testReadAuditLogs_noFiles_returnsEmptyList() throws Exception {
        File folder = mTemporaryFolder.newFolder();

        List<AuditLogEntry> entries = AuditModeContext.readAuditLogs(folder);

        assertThat(entries).isEmpty();
    }

    @Test
    public void testReadAuditLogs_oneFile_canReadEntries() throws Exception {
        File folder = mTemporaryFolder.newFolder();
        File file = new File(folder, "audit_log.0.bin");
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        AuditLogEntry entry1 = getTestEntry();
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key", 123);
        AuditLogEntry entry2 =
                new AuditLogEntry(
                        bundle2,
                        TEST_REALTIME_NANOS + 1L,
                        TEST_CURRENT_TIME_MILLIS + 1000L,
                        "other_package",
                        23);
        writer.writeEntries(ImmutableList.of(entry1.toByteArray(), entry2.toByteArray()));
        assertThat(folder.listFiles()).hasLength(1);

        List<AuditLogEntry> entries = AuditModeContext.readAuditLogs(folder);

        assertThat(entries).hasSize(2);
        assertEquals(entries.get(0).mCallingPackage, TEST_PACKAGE_NAME);
        assertEquals(entries.get(0).mCallingUid, TEST_UID);
        assertEquals(entries.get(0).mRealTimeNanos, TEST_REALTIME_NANOS);
        assertEquals(entries.get(0).mCurrentTimeMillis, TEST_CURRENT_TIME_MILLIS);
        assertEqualsToTestBundle(entries.get(0).mData);
        assertThat(entries.get(1).mCallingPackage).isEqualTo("other_package");
        assertThat(entries.get(1).mCallingUid).isEqualTo(23);
        assertEquals(entries.get(1).mRealTimeNanos, TEST_REALTIME_NANOS + 1L);
        assertEquals(entries.get(1).mCurrentTimeMillis, TEST_CURRENT_TIME_MILLIS + 1000L);
        assertEquals(entries.get(1).mData.getInt("test_key"), 123);
    }

    @Test
    public void testReadAuditLogs_twoFiles_canReadEntries() throws Exception {
        File folder = mTemporaryFolder.newFolder();
        File file1 = new File(folder, "audit_log.0.bin");
        File file2 = new File(folder, "audit_log.2.bin");
        AuditLogFileWriter writer = new AuditLogFileWriter(file1);
        AuditLogEntry entry1 = getTestEntry();
        writer.writeEntries(ImmutableList.of(entry1.toByteArray()));
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key", 123);
        AuditLogFileWriter writer2 = new AuditLogFileWriter(file2);
        AuditLogEntry entry2 =
                new AuditLogEntry(
                        bundle2,
                        TEST_REALTIME_NANOS + 2L,
                        TEST_CURRENT_TIME_MILLIS + 2000L,
                        "other_package",
                        23);
        writer2.writeEntries(ImmutableList.of(entry2.toByteArray()));
        assertThat(folder.listFiles()).hasLength(2);

        List<AuditLogEntry> entries = AuditModeContext.readAuditLogs(folder);

        assertThat(entries).hasSize(2);
        assertEquals(entries.get(0).mCallingPackage, TEST_PACKAGE_NAME);
        assertEquals(entries.get(0).mCallingUid, TEST_UID);
        assertEquals(entries.get(0).mRealTimeNanos, TEST_REALTIME_NANOS);
        assertEquals(entries.get(0).mCurrentTimeMillis, TEST_CURRENT_TIME_MILLIS);
        assertEqualsToTestBundle(entries.get(0).mData);
        assertThat(entries.get(1).mCallingPackage).isEqualTo("other_package");
        assertThat(entries.get(1).mCallingUid).isEqualTo(23);
        assertEquals(entries.get(1).mRealTimeNanos, TEST_REALTIME_NANOS + 2L);
        assertEquals(entries.get(1).mCurrentTimeMillis, TEST_CURRENT_TIME_MILLIS + 2000L);
        assertEquals(entries.get(1).mData.getInt("test_key"), 123);
    }

    @Test
    public void readAuditLogs_userIdButNoLogs_returnsEmptyList() throws Exception {
        int userId = 10;
        File folder = mTemporaryFolder.newFolder();

        List<AuditLogEntry> entries = AuditModeContext.readAuditLogs(folder, userId);

        assertThat(entries).hasSize(0);
    }

    @Test
    public void readAuditLogs_oneLogWrongUserId_returnsEmptyList() throws Exception {
        int userId = 9;
        int logUid = UserHandle.PER_USER_RANGE * 10 + 1;
        assertThat(UserHandle.getUserId(logUid)).isNotEqualTo(userId);
        File folder = mTemporaryFolder.newFolder();
        AuditLogEntry entry =
                new AuditLogEntry(
                        getTestBundle(),
                        TEST_REALTIME_NANOS,
                        TEST_CURRENT_TIME_MILLIS,
                        TEST_PACKAGE_NAME,
                        logUid);
        AuditLogFileWriter writer = new AuditLogFileWriter(folder);
        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        List<AuditLogEntry> entries = AuditModeContext.readAuditLogs(folder, userId);

        assertThat(entries).hasSize(0);
    }

    @Test
    public void readAuditLogs_oneLogCorrectUserId_returnsLog() throws Exception {
        int userId = 10;
        int logUid = UserHandle.PER_USER_RANGE * userId + 1;
        assertThat(UserHandle.getUserId(logUid)).isEqualTo(userId);
        File folder = mTemporaryFolder.newFolder();
        File file = new File(folder, "audit_log.0.bin");
        AuditLogEntry entry =
                new AuditLogEntry(
                        getTestBundle(),
                        TEST_REALTIME_NANOS,
                        TEST_CURRENT_TIME_MILLIS,
                        TEST_PACKAGE_NAME,
                        logUid);
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        List<AuditLogEntry> entries = AuditModeContext.readAuditLogs(folder, userId);

        assertThat(entries).hasSize(1);
        assertEquals(entries.get(0).mCallingPackage, TEST_PACKAGE_NAME);
        assertEquals(entries.get(0).mCallingUid, logUid);
        assertEquals(entries.get(0).mRealTimeNanos, TEST_REALTIME_NANOS);
        assertEquals(entries.get(0).mCurrentTimeMillis, TEST_CURRENT_TIME_MILLIS);
        assertEqualsToTestBundle(entries.get(0).mData);
    }

    @Test
    public void readAuditLogs_twoLogs_returnsCorrectUid() throws Exception {
        int userId1 = 0;
        int userId2 = 10;
        int logUid1 = UserHandle.PER_USER_RANGE * userId1 + 1;
        int logUid2 = UserHandle.PER_USER_RANGE * userId2 + 1;
        assertThat(UserHandle.getUserId(logUid1)).isEqualTo(userId1);
        assertThat(UserHandle.getUserId(logUid2)).isEqualTo(userId2);
        File folder = mTemporaryFolder.newFolder();
        File file = new File(folder, "audit_log.1.bin");
        AuditLogEntry entry1 =
                new AuditLogEntry(
                        getTestBundle(),
                        TEST_REALTIME_NANOS,
                        TEST_CURRENT_TIME_MILLIS,
                        TEST_PACKAGE_NAME,
                        logUid1);
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, 234000L, "other_package", logUid2);
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        writer.writeEntries(ImmutableList.of(entry1.toByteArray(), entry2.toByteArray()));

        List<AuditLogEntry> entries = AuditModeContext.readAuditLogs(folder, userId1);

        assertThat(entries).hasSize(1);
        assertEquals(entries.get(0).mCallingPackage, TEST_PACKAGE_NAME);
        assertEquals(entries.get(0).mCallingUid, logUid1);
        assertEquals(entries.get(0).mRealTimeNanos, TEST_REALTIME_NANOS);
        assertEquals(entries.get(0).mCurrentTimeMillis, TEST_CURRENT_TIME_MILLIS);
        assertEqualsToTestBundle(entries.get(0).mData);
    }

    /** Create a nested bundle of depth 101. */
    private static PersistableBundle getDeepNestedBundle101() {
        PersistableBundle rootBundle = new PersistableBundle();

        PersistableBundle currentBundle = rootBundle;
        for (int i = 0; i < 100; i++) {
            PersistableBundle newInnerBundle = new PersistableBundle();
            currentBundle.putPersistableBundle("NESTED_BUNDLE_KEY", newInnerBundle);
            currentBundle = newInnerBundle; // Move down one level
        }

        currentBundle.putPersistableBundle("NESTED_BUNDLE_KEY", new PersistableBundle());

        return rootBundle;
    }
}
