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

package com.android.commands.bmgr.outputparser;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Presubmit
public class BmgrOutputParserTest {
    @Test
    public void testParseBmgrErrors_successfulBackup_returnsNoError() throws IOException {
        // Output for a successful backup.
        String output =
                "Running non-incremental backup for 2 requested packages.\n"
                        + "Package @pm@ with result: Success\n"
                        + "Package com.example.backuprestore with progress: 512/1024\n"
                        + "Package com.example.backuprestore with progress: 2048/1024\n"
                        + "Package com.example.backuprestore with progress: 3072/1024\n"
                        + "=> Event{AGENT / AGENT_LOGGING_RESULTS : package = com.example.backuprestore(v1), results = []}\n"
                        + "Package com.example.backuprestore with result: Success\n"
                        + "Backup finished with result: Success";
        List<BmgrError> errors = BmgrOutputParser.parseBmgrErrors(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void testParseBmgrErrors_noDataToBackup_returnsCorrectError() throws IOException {
        // Output for a Full backup package, when the package has no data to backup (or backup size
        // is above quota)
        String output =
                "Running non-incremental backup for 2 requested packages.\n"
                        + "Package @pm@ with result: Success\n"
                        + "=> Event{BACKUP_MANAGER_POLICY / ERROR_PREFLIGHT : package = com.example.backuprestore(v1), other keys = android.app.backup.extra.LOG_PREFLIGHT_ERROR}\n"
                        + "Package com.example.backuprestore with result: Transport rejected package because it wasn't able to process it at the time\n"
                        + "Backup finished with result: Success";

        List<BmgrError> errors = BmgrOutputParser.parseBmgrErrors(output);
        assertEquals(2, errors.size());
        assertEquals("ERROR_PREFLIGHT", errors.get(0).getErrorCode());
    }

    @Test
    public void testParseBmgrErrors_noBackupDataForPackage_returnsCorrectError()
            throws IOException {
        // Output for a KV backup package, when the package has no data to backup.
        String output =
                "Running non-incremental backup for 2 requested packages.\n"
                        + "=> Event{TRANSPORT / UNKNOWN_ID : package = @pm@(v0)}\n"
                        + "Package @pm@ with result: Success\n"
                        + "=> Event{AGENT / AGENT_LOGGING_RESULTS : package = com.example.backuprestore(v1), results = []}\n"
                        + "=> Event{BACKUP_MANAGER_POLICY / NO_DATA_TO_SEND : package = com.example.backuprestore(v1), other keys = some.random.key}\n"
                        + "Package com.example.backuprestore with result: Success\n"
                        + "Backup finished with result: Success";

        List<BmgrError> errors = BmgrOutputParser.parseBmgrErrors(output);
        assertEquals(1, errors.size());
        assertEquals("NO_DATA_TO_SEND", errors.get(0).getErrorCode());
    }

    @Test
    public void testParseBmgrErrors_allowBackupFalse_returnsCorrectError() throws IOException {
        // Output for a KV backup package, when the package has no data to backup.
        String output =
                "Running non-incremental backup for 2 requested packages.\n"
                        + "Package com.example.backuprestore with result: Backup is not allowed\n"
                        + "Package @pm@ with result: Success\n"
                        + "Backup finished with result: Success";

        List<BmgrError> errors = BmgrOutputParser.parseBmgrErrors(output);
        assertEquals(1, errors.size());
        assertEquals("Backup is not allowed", errors.get(0).getErrorCode());
    }

    @Test
    public void testParseBmgrErrors_invalidPackageBackup_returnsCorrectError() throws IOException {
        // Output for backup of a non existent package.
        String output =
                "Running non-incremental backup for 2 requested packages.\n"
                        + "Package com.example.backuprestore with result: Package not found\n"
                        + "Package @pm@ with result: Transport rejected package because it wasn't able to process it at the time\n"
                        + "Backup finished with result: Transport error";

        List<BmgrError> errors = BmgrOutputParser.parseBmgrErrors(output);
        assertEquals(3, errors.size());
        assertEquals("Package not found", errors.get(0).getErrorCode());
    }

    @Test
    public void testParseBmgrErrors_stoppedStatePackageBackup_returnsCorrectError()
            throws IOException {
        // Output for backup of a package, which is in stopped state.
        String output =
                "Running non-incremental backup for 2 requested packages.\n"
                        + "=> Event{BACKUP_MANAGER_POLICY / PACKAGE_STOPPED : package = com.example.backuprestore(v1)}\n"
                        + "Package com.example.backuprestore with result: Backup is not allowed\n"
                        + "Package @pm@ with result: Transport rejected package because it wasn't able to process it at the time\n"
                        + "Backup finished with result: Transport error";

        List<BmgrError> errors = BmgrOutputParser.parseBmgrErrors(output);
        assertEquals(4, errors.size());
        assertEquals("PACKAGE_STOPPED", errors.get(0).getErrorCode());
    }

    @Test
    public void testParseBmgrErrors_successfulRestore_returnsNoError() throws IOException {
        // Output for a successful restore.
        String output =
                "Scheduling restore: D2D\n"
                        + "restoreStarting: 1 packages\n"
                        + "=> Event{BACKUP_MANAGER_POLICY / START_RESTORE_AT_INSTALL}\n"
                        + "onUpdate: 1 = com.example.backuprestore\n"
                        + "=> Event{BACKUP_MANAGER_POLICY / START_PACKAGE_RESTORE : package = com.example.backuprestore(v1)}\n"
                        + "=> Event{BACKUP_MANAGER_POLICY / FULL_RESTORE : package = com.example.backuprestore(v1)}\n"
                        + "=> Event{BACKUP_MANAGER_POLICY / VERSIONS_MATCH : package = com.example.backuprestore(v1)}\n"
                        + "=> Event{BACKUP_MANAGER_POLICY / PACKAGE_RESTORE_FINISHED : package = com.example.backuprestore(v1)}\n"
                        + "=> Event{AGENT / AGENT_LOGGING_RESULTS : package = com.example.backuprestore(v1), results = []}\n"
                        + "restoreFinished: 0\n"
                        + "done";

        List<BmgrError> errors = BmgrOutputParser.parseBmgrErrors(output);
        assertEquals(0, errors.size());
    }
}
