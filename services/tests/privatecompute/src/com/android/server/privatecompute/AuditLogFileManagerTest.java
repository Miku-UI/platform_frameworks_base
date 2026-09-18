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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.RequiresFlagsEnabled;
import androidx.test.runner.AndroidJUnit4;
import com.android.server.privatecompute.AuditModeContext.Injector;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AuditLogFileManager}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
public class AuditLogFileManagerTest {

    private static final int MAX_FILES = 10;

    @Rule(order = 0)
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Rule(order = 1)
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Mock private Injector mInjector;

    @Before
    public void setUp() {
        when(mInjector.auditModeMaxLogFiles()).thenReturn(MAX_FILES);
    }

    @Test
    public void testAuditLogFileManager_rotateAndReturnNewFile_createFiles() throws Exception {
        File folder = mTemporaryFolder.newFolder();
        AuditLogFileManager fileManager = new AuditLogFileManager(folder, mInjector);

        File file1 = fileManager.rotateAndReturnNewFile();
        assertEquals("audit_log.0.bin", file1.getName());
        assertEquals(folder, file1.getParentFile());

        File file2 = fileManager.rotateAndReturnNewFile();
        assertEquals("audit_log.1.bin", file2.getName());
        assertEquals(folder, file2.getParentFile());
    }

    @Test
    public void testAuditLogFileManager_rotateAndReturnNewFile_alwaysStartsAtZero()
            throws Exception {
        File folder = mTemporaryFolder.newFolder();
        File file4 = new File(folder, "audit_log.4.bin");
        file4.createNewFile();
        File file5 = new File(folder, "audit_log.5.bin");
        file5.createNewFile();
        AuditLogFileManager fileManager = new AuditLogFileManager(folder, mInjector);

        // The counter is an in-memory variable, it's not read from disk. Thus numbering
        // always starts at 0.
        File file1 = fileManager.rotateAndReturnNewFile();
        assertEquals("audit_log.0.bin", file1.getName());
        assertEquals(folder, file1.getParentFile());

        File file2 = fileManager.rotateAndReturnNewFile();
        assertEquals("audit_log.1.bin", file2.getName());
        assertEquals(folder, file2.getParentFile());
    }

    @Test
    public void testAuditLogFileManager_rotateAndReturnNewFile_wrapsAround() throws Exception {
        int numFiles = 3;
        when(mInjector.auditModeMaxLogFiles()).thenReturn(numFiles);
        File folder = mTemporaryFolder.newFolder();
        AuditLogFileManager fileManager = new AuditLogFileManager(folder, mInjector);
        File file0 = fileManager.rotateAndReturnNewFile();
        assertEquals("audit_log.0.bin", file0.getName());
        for (int i = 1; i < numFiles; i++) {
            File file = fileManager.rotateAndReturnNewFile();
            assertEquals("audit_log." + i + ".bin", file.getName());
        }

        // After numFiles, it should loop back to 0.
        File nextFile = fileManager.rotateAndReturnNewFile();
        assertEquals("audit_log.0.bin", nextFile.getName());
    }
}
