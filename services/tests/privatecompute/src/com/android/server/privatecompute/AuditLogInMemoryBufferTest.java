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
import static com.android.server.privatecompute.AuditModeTestUtils.getTestEntry;
import static com.android.server.privatecompute.AuditModeTestUtils.readAuditLogFileFromFile;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.runner.AndroidJUnit4;
import com.android.server.privatecompute.AuditModeContext.Injector;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AuditLogInMemoryBufferTest}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class AuditLogInMemoryBufferTest {

    private static final int MAX_SIZE_KILOBYTES = 10 * 1024; // 10 MB

    @Rule(order = 0)
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Rule(order = 1)
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Mock private Injector mInjector;

    @Before
    public void setUp() {
        when(mInjector.auditModeLogFileMaxSizeKb()).thenReturn(MAX_SIZE_KILOBYTES);
    }

    @Test
    public void testAuditLogInMemoryBuffer_add_success() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogInMemoryBuffer buffer = new AuditLogInMemoryBuffer(file, mInjector);
        byte[] entry = getTestEntry().toByteArray();

        assertThat(buffer.add(entry)).isTrue();
    }

    @Test
    public void testAuditLogInMemoryBuffer_add_bufferFull() throws Exception {
        File file = mTemporaryFolder.newFile();
        byte[] entry = getTestEntry().toByteArray();
        int writtenBytes = 0;
        AuditLogInMemoryBuffer buffer = new AuditLogInMemoryBuffer(file, mInjector);
        while (writtenBytes < 1024 * MAX_SIZE_KILOBYTES - entry.length) {
            assertThat(buffer.add(entry)).isTrue();
            writtenBytes += entry.length;
        }
        assertThat(buffer.add(entry)).isFalse();
    }

    @Test
    public void testAuditLogInMemoryBuffer_writeToFile_writes() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogInMemoryBuffer buffer = new AuditLogInMemoryBuffer(file, mInjector);
        buffer.add(getTestEntry().toByteArray());

        buffer.writeToFile();

        // Verify that the file was written to
        assertThat(file.exists()).isTrue();
        assertThat(file.length()).isGreaterThan(0);
        List<AuditLogEntry> entries = readAuditLogFileFromFile(file);
        assertEquals(1, entries.size());
        assertEquals(entries.get(0).mRealTimeNanos, TEST_REALTIME_NANOS);
        assertEquals(entries.get(0).mCurrentTimeMillis, TEST_CURRENT_TIME_MILLIS);
        assertEquals(entries.get(0).mCallingPackage, TEST_PACKAGE_NAME);
        assertEquals(entries.get(0).mCallingUid, TEST_UID);
        assertEqualsToTestBundle(entries.get(0).mData);
    }

    @Test
    public void testAuditLogInMemoryBuffer_add_cantAddAfterWriteToFile() throws Exception {
        File file = mTemporaryFolder.newFile();
        AuditLogInMemoryBuffer buffer = new AuditLogInMemoryBuffer(file, mInjector);
        buffer.add(getTestEntry().toByteArray());
        buffer.writeToFile();

        assertThat(buffer.add(getTestEntry().toByteArray())).isFalse();
    }
}
