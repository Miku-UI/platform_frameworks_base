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
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_REALTIME_NANOS;
import static com.android.server.privatecompute.AuditModeTestUtils.TEST_UID;
import static com.android.server.privatecompute.AuditModeTestUtils.assertEqualsToTestBundle;
import static com.android.server.privatecompute.AuditModeTestUtils.getTestBundle;
import static com.android.server.privatecompute.AuditModeTestUtils.getTestEntry;
import static com.android.server.privatecompute.AuditModeTestUtils.readAuditLogFileFromFile;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.runner.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Unit tests for {@link AuditLogFileWriter}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class AuditLogFileWriterTest {

    // Open file for writing, creating it if it doesn't exist, or truncating it if it does.
    private static final StandardOpenOption[] OPEN_OPTIONS =
            new StandardOpenOption[] {
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            };

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Test
    public void writeEntries_prependsTheVersionNumber() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);

        writer.writeEntries(new ArrayList<>());

        DataInputStream stream = new DataInputStream(new FileInputStream(file));
        assertEquals(stream.readInt(), 0); // version
    }

    @Test
    public void writeEntries_oneEntry_canBeParsedBack() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        long realTimeNanos = 1764409688L; // 2025-11-29 09:48:08 GMT
        long currentTimeMillis = 1764409688000L;
        String packageName = "test_package";
        int uid = 12;
        AuditLogEntry entry =
                new AuditLogEntry(
                        getTestBundle(), realTimeNanos, currentTimeMillis, packageName, uid);

        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEqualsToTestBundle(entries.get(0).mData);
        assertEquals(entries.get(0).mRealTimeNanos, realTimeNanos);
        assertEquals(entries.get(0).mCurrentTimeMillis, currentTimeMillis);
        assertEquals(entries.get(0).mCallingPackage, packageName);
        assertEquals(entries.get(0).mCallingUid, uid);
    }

    @Test
    public void writeEntries_twoEntries_canBeParsedBack() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        AuditLogEntry entry1 = getTestEntry();
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, 234000L, "other_package", 23);

        writer.writeEntries(ImmutableList.of(entry1.toByteArray(), entry2.toByteArray()));

        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 2);
        assertEquals(entries.get(0).mRealTimeNanos, TEST_REALTIME_NANOS);
        assertEquals(entries.get(0).mCurrentTimeMillis, TEST_CURRENT_TIME_MILLIS);
        assertEquals(entries.get(0).mCallingPackage, TEST_PACKAGE_NAME);
        assertEquals(entries.get(0).mCallingUid, TEST_UID);
        assertEqualsToTestBundle(entries.get(0).mData);
        assertEquals(entries.get(1).mRealTimeNanos, 234L);
        assertEquals(entries.get(1).mCurrentTimeMillis, 234000L);
        assertEquals(entries.get(1).mCallingPackage, "other_package");
        assertEquals(entries.get(1).mCallingUid, 23);
        assertThat(entries.get(1).mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void writeEntries_secondWritesOverwritesFile_sameInstance() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogEntry entry = getTestEntry();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, 234000L, "other_package", 23);
        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        writer.writeEntries(ImmutableList.of(entry2.toByteArray())); // 2nd write to same instance

        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEquals(entries.get(0).mRealTimeNanos, 234L);
        assertEquals(entries.get(0).mCurrentTimeMillis, 234000L);
        assertEquals(entries.get(0).mCallingPackage, "other_package");
        assertEquals(entries.get(0).mCallingUid, 23);
        assertThat(entries.get(0).mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void writeEntries_secondWritesOverwritesFile_newInstance() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogEntry entry = getTestEntry();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, 234000L, "other_package", 23);
        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        AuditLogFileWriter writer2 = new AuditLogFileWriter(file); // new instance, same file
        writer2.writeEntries(ImmutableList.of(entry2.toByteArray()));

        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(entries.size(), 1);
        assertEquals(entries.get(0).mRealTimeNanos, 234L);
        assertEquals(entries.get(0).mCurrentTimeMillis, 234000L);
        assertEquals(entries.get(0).mCallingPackage, "other_package");
        assertEquals(entries.get(0).mCallingUid, 23);
        assertThat(entries.get(0).mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void writeEntries_createsDirectoryIfItDoesNotExist() throws Exception {
        File newDir = new File(mTemporaryFolder.getRoot(), "new_dir");
        File file = new File(newDir, "test_log");
        assertThat(newDir.exists()).isFalse();

        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        AuditLogEntry entry = getTestEntry();

        writer.writeEntries(ImmutableList.of(entry.toByteArray()));

        assertThat(newDir.exists()).isTrue();
        assertThat(file.exists()).isTrue();
    }

    @Test
    public void readFromStream_emptyStream_throws() throws Exception {
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(new byte[0]));

        assertThrows(IOException.class, () -> AuditLogEntry.readFromStream(stream));
    }

    @Test
    public void readFromStream_invalidStream_throws() throws Exception {
        byte[] bytes = {1, 2, 3};
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes));

        assertThrows(IOException.class, () -> AuditLogEntry.readFromStream(stream));
    }

    @Test
    public void readFromStream_canReadEntry() throws Exception {
        AuditLogEntry originalEntry = getTestEntry();
        byte[] bytes = originalEntry.toByteArray();
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes));

        AuditLogEntry parsedEntry = AuditLogEntry.readFromStream(stream);

        assertEquals(originalEntry.mRealTimeNanos, parsedEntry.mRealTimeNanos);
        assertEquals(originalEntry.mCurrentTimeMillis, parsedEntry.mCurrentTimeMillis);
        assertEquals(originalEntry.mCallingPackage, parsedEntry.mCallingPackage);
        assertEquals(originalEntry.mCallingUid, parsedEntry.mCallingUid);
        assertEqualsToTestBundle(parsedEntry.mData);
    }

    @Test
    public void readFromStream_canReadMultipleEntries() throws Exception {
        AuditLogEntry entry1 = getTestEntry();
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, 234000L, "other_package", 23);
        byte[] entry1Bytes = entry1.toByteArray();
        byte[] entry2Bytes = entry2.toByteArray();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(entry1Bytes);
        output.write(entry2Bytes);
        byte[] bytes = output.toByteArray();
        DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes));

        AuditLogEntry parsedEntry1 = AuditLogEntry.readFromStream(stream);
        AuditLogEntry parsedEntry2 = AuditLogEntry.readFromStream(stream);

        assertEquals(entry1.mRealTimeNanos, parsedEntry1.mRealTimeNanos);
        assertEquals(entry1.mCurrentTimeMillis, parsedEntry1.mCurrentTimeMillis);
        assertEquals(entry1.mCallingPackage, parsedEntry1.mCallingPackage);
        assertEquals(entry1.mCallingUid, parsedEntry1.mCallingUid);
        assertEqualsToTestBundle(parsedEntry1.mData);
        assertEquals(entry2.mRealTimeNanos, parsedEntry2.mRealTimeNanos);
        assertEquals(entry2.mCurrentTimeMillis, parsedEntry2.mCurrentTimeMillis);
        assertEquals(entry2.mCallingPackage, parsedEntry2.mCallingPackage);
        assertEquals(entry2.mCallingUid, parsedEntry2.mCallingUid);
        assertThat(parsedEntry2.mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void readAuditLogFile_incorrectVersion_returnsEmpty() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        writer.writeEntries(new ArrayList<>());
        // Write a stream with an invalid version number.
        DataOutputStream outputStream =
                new DataOutputStream(
                        new BufferedOutputStream(
                                Files.newOutputStream(file.toPath(), OPEN_OPTIONS)));
        outputStream.writeInt(12345);

        List<AuditLogEntry> entries = AuditLogFileWriter.readEntries(file);

        // Assert that it does not throw, but simply skips unreadable files.
        assertThat(entries).isEmpty();
    }

    @Test
    public void readAuditLogFile_canReadEntries() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogFileWriter writer = new AuditLogFileWriter(file);
        AuditLogEntry entry1 = getTestEntry();
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, 234000L, "other_package", 23);
        writer.writeEntries(ImmutableList.of(entry1.toByteArray(), entry2.toByteArray()));

        List<AuditLogEntry> entries = AuditLogFileWriter.readEntries(file);

        assertEquals(2, entries.size());
        assertEquals(TEST_REALTIME_NANOS, entries.get(0).mRealTimeNanos);
        assertEquals(TEST_CURRENT_TIME_MILLIS, entries.get(0).mCurrentTimeMillis);
        assertEquals(TEST_PACKAGE_NAME, entries.get(0).mCallingPackage);
        assertEquals(TEST_UID, entries.get(0).mCallingUid);
        assertEqualsToTestBundle(entries.get(0).mData);
        assertEquals(234L, entries.get(1).mRealTimeNanos);
        assertEquals(234000L, entries.get(1).mCurrentTimeMillis);
        assertEquals("other_package", entries.get(1).mCallingPackage);
        assertEquals(23, entries.get(1).mCallingUid);
        assertThat(entries.get(1).mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void readAuditLogFiles_canReadEntries() throws Exception {
        File file1 = mTemporaryFolder.newFile();
        File file2 = mTemporaryFolder.newFile();
        AuditLogFileWriter writer1 = new AuditLogFileWriter(file1);
        AuditLogFileWriter writer2 = new AuditLogFileWriter(file2);
        AuditLogEntry entry1 = getTestEntry();
        PersistableBundle bundle2 = new PersistableBundle();
        bundle2.putInt("test_key2", 123);
        AuditLogEntry entry2 = new AuditLogEntry(bundle2, 234L, 234000L, "other_package", 23);
        writer1.writeEntries(ImmutableList.of(entry1.toByteArray()));
        writer2.writeEntries(ImmutableList.of(entry2.toByteArray()));

        List<AuditLogEntry> entries =
                AuditLogFileWriter.readEntriesForFiles(ImmutableList.of(file1, file2));

        assertEquals(2, entries.size());
        assertEquals(TEST_REALTIME_NANOS, entries.get(0).mRealTimeNanos);
        assertEquals(TEST_CURRENT_TIME_MILLIS, entries.get(0).mCurrentTimeMillis);
        assertEquals(TEST_PACKAGE_NAME, entries.get(0).mCallingPackage);
        assertEquals(TEST_UID, entries.get(0).mCallingUid);
        assertEqualsToTestBundle(entries.get(0).mData);
        assertEquals(234L, entries.get(1).mRealTimeNanos);
        assertEquals(234000L, entries.get(1).mCurrentTimeMillis);
        assertEquals("other_package", entries.get(1).mCallingPackage);
        assertEquals(23, entries.get(1).mCallingUid);
        assertThat(entries.get(1).mData.getInt("test_key2")).isEqualTo(123);
    }

    @Test
    public void readAuditLogFiles_skipsUnreadableFiles() throws Exception {
        File file1 = mTemporaryFolder.newFile();
        File file2 = mTemporaryFolder.newFile();
        AuditLogFileWriter writer1 = new AuditLogFileWriter(file1);
        AuditLogEntry entry1 = getTestEntry();
        writer1.writeEntries(ImmutableList.of(entry1.toByteArray()));
        // Garbled file 2
        DataOutputStream outputStream =
                new DataOutputStream(
                        new BufferedOutputStream(
                                Files.newOutputStream(file2.toPath(), OPEN_OPTIONS)));
        outputStream.writeInt(12345);

        List<AuditLogEntry> entries =
                AuditLogFileWriter.readEntriesForFiles(ImmutableList.of(file1, file2));

        assertEquals(1, entries.size());
        assertEquals(TEST_REALTIME_NANOS, entries.get(0).mRealTimeNanos);
        assertEquals(TEST_CURRENT_TIME_MILLIS, entries.get(0).mCurrentTimeMillis);
        assertEquals(TEST_PACKAGE_NAME, entries.get(0).mCallingPackage);
        assertEquals(TEST_UID, entries.get(0).mCallingUid);
        assertEqualsToTestBundle(entries.get(0).mData);
    }
}
