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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser of the output of BackupManager command line utility (bmgr).
 *
 * <p>Current version of parser only supports parsing of the output for following commands:
 *
 * <ul>
 *   <li>'bmgr backupnow' (with --monitor-verbose flag)
 *   <li>'bmgr restore' (with --monitor-verbose flag)
 * </ul>
 *
 * Other commands (like 'bmgr backup', 'bmgr fullbackup', 'bmgr autorestore') are not supported..
 */
public class BmgrOutputParser {
    private static final Pattern BACKUP_START_PATTERN =
            Pattern.compile("Running (.*?) backup for (.*?) packages.");
    private static final Pattern EVENT_PATTERN =
            Pattern.compile("=> Event\\{(.*?)/(.*?) : package = (.*?)\\(v(.*?)\\).*\\}");
    private static final Pattern PACKAGE_RESULT_PATTERN =
            Pattern.compile("Package (.*?) with result: (.*)");
    private static final Pattern BACKUP_FINISHED_PATTERN =
            Pattern.compile("Backup finished with result: (.*)");
    private static final Pattern RESTORE_FINISHED_PATTERN =
            Pattern.compile("restoreFinished: (.*)");

    private static final Map<String, String> sErrorEventInfoMap = new HashMap<>();

    static {
        // -----------------------------------------------------------------------------------------
        // Common misconfiguration errors. Errors that developers can encounter when configuring
        // backup for their app.

        // Caveat of device to device transfers: allowBackup is ignored (for API level >= 31)
        sErrorEventInfoMap.put(
                "PACKAGE_INELIGIBLE",
                "App either has android:allowBackup set to false, or is disabled, or is installed"
                        + " as an instant app.");
        sErrorEventInfoMap.put(
                "APP_HAS_NO_AGENT",
                "Key-value backup apps must specify BackupAgent in their manifest.");
        sErrorEventInfoMap.put(
                "SIGNATURE_MISMATCH",
                "Signature of the app for which restore is called doesn't match the signature of "
                        + "the app corresponding to the backup.");
        sErrorEventInfoMap.put(
                "FULL_RESTORE_SIGNATURE_MISMATCH",
                "Signature of the app for which restore is called doesn't match the signature of "
                        + "the app corresponding to the backup.");
        // Caveat of device to device transfers: allowBackup is ignored (for API level >= 31)
        sErrorEventInfoMap.put(
                "FULL_RESTORE_ALLOW_BACKUP_FALSE",
                "App either has android:allowBackup set to false, or is disabled, or is installed"
                        + " as an instant app.");

        // -----------------------------------------------------------------------------------------
        // Uncommon misconfiguration errors. Errors that developers should infrequently encounter
        // when configuring backup for their app.

        // The illegal key is logged in BMM event extras.
        sErrorEventInfoMap.put(
                "ILLEGAL_KEY",
                "One of the K/V pairs that the app is trying to backup is not allowed (reserved "
                        + "for system).");
        // Exception trace is logged in BMM event extras.
        sErrorEventInfoMap.put("EXCEPTION_FULL_BACKUP", "Exception thrown during full backup.");
        sErrorEventInfoMap.put(
                "UNABLE_TO_CREATE_AGENT_FOR_RESTORE",
                "BackupManager could not establish a binder connection with the BackupAgent of the "
                        + "app.");
        sErrorEventInfoMap.put(
                "CANT_FIND_AGENT",
                "Possible reasons: Binding to backup agent took too long (timed out), Binding to "
                        + "backup agent failed,  BackupAgent died right after successful binder "
                        + "connection.");
        sErrorEventInfoMap.put(
                "VERSION_OF_BACKUP_OLDER",
                "Backup data is coming from a newer version of the app. The default behavior in "
                        + "this case is not restoring the backup data. This behavior can be "
                        + "changed by specifying android:restoreAnyVersion attribute to true in "
                        + "app manifest.");
        sErrorEventInfoMap.put(
                "KEY_VALUE_RESTORE_TIMEOUT", "Timeout when restoring key-value backup.");
        sErrorEventInfoMap.put("FULL_RESTORE_TIMEOUT", "Timeout when restoring app.");
        sErrorEventInfoMap.put(
                "KV_AGENT_ERROR", "Exception occurred when calling app's BackupAgent.");
        sErrorEventInfoMap.put(
                "FULL_AGENT_ERROR", "IO error during data streaming to the BackupAgent.");
        sErrorEventInfoMap.put(
                "AGENT_FAILURE", "Error occurred when BackupAgent.doRestoreFinished() was called.");
        sErrorEventInfoMap.put(
                "AGENT_CRASHED_BEFORE_RESTORE_DATA_IS_SENT",
                "BackupAgent crashed after Backup service binds to it, but before any backup data"
                        + " is streamed.");
        sErrorEventInfoMap.put(
                "FAILED_TO_SEND_DATA_TO_AGENT_DURING_RESTORE",
                "BackupAgent crashed after Backup service binds to it, and has started receiving "
                        + "data from it.");
        sErrorEventInfoMap.put(
                "AGENT_FAILURE_DURING_RESTORE",
                "Either BackupAgent crashed during restore, or it reported failure.");

        // -----------------------------------------------------------------------------------------
        // Backup/Restore pre-condition failures. Device or package state is not correct.

        sErrorEventInfoMap.put(
                "PACKAGE_STOPPED",
                "App is in stopped state. It may happen when the app hasn't been opened yet since "
                        + "install/restore.");
        sErrorEventInfoMap.put(
                "PACKAGE_NOT_FOUND",
                "App needs to be installed on the device/emulator for backup/restore to work.");
        // Enabled state can be checked by running: bmgr enabled --user <user-id>
        // Backup can be enabled by running: bmgr enable true --user <user-id>
        sErrorEventInfoMap.put("BACKUP_DISABLED", "Backup is disabled for the current user.");
        sErrorEventInfoMap.put(
                "DEVICE_NOT_PROVISIONED",
                "Device hasn't been provisioned yet (doesn't have an assigned AndroidID). This "
                        + "could happen when device setup is not complete.");
        sErrorEventInfoMap.put(
                "PACKAGE_NOT_PRESENT",
                "Package for which restore is requested, is not installed on the device.");

        // -----------------------------------------------------------------------------------------
        // Errors due to the size of backup

        // This isn’t treated as an error by BackupTransport. Here it is reported as an 'error' to
        // let the user decide whether this is intented or not.
        sErrorEventInfoMap.put("NO_DATA_TO_SEND", "App did not provide any backup data.");
        sErrorEventInfoMap.put(
                "ERROR_PREFLIGHT",
                "Backup Preflight check failed. Either the estimated backup size is over quota. "
                        + "Please ensure that backup size is within allowed quota (25MB for Cloud "
                        + "backups, 2GB for device-to-device backups).");
        sErrorEventInfoMap.put(
                "QUOTA_HIT_PREFLIGHT",
                "App hit quota limit for backup. Estimated backup size > quota (25MB for Cloud "
                        + "backups, 2GB for device-to-device backups).");
        // -----------------------------------------------------------------------------------------
        // Backup infrastructure errors

        sErrorEventInfoMap.put(
                "FULL_BACKUP_CANCEL",
                "Backup was cancelled by either the user or backup service lifecycle.");
        sErrorEventInfoMap.put(
                "PACKAGE_KEY_VALUE_PARTICIPANT",
                "Auto Backup is requested for a key-value backup app. App that specify "
                        + "BackupAgent need to set android:fullBackupOnly attribute to 'true' in "
                        + "their app manifest.");
        sErrorEventInfoMap.put(
                "PACKAGE_TRANSPORT_NOT_PRESENT",
                "Could not establish a binder connection to BackupTransport.");
        sErrorEventInfoMap.put(
                "KEY_VALUE_BACKUP_CANCEL",
                "Either Backup operation timed out, or the thread on which BackupAgent was "
                        + "running was interrupted, or the BackupAgent failed with "
                        + "'FAILED_CANCELLED' result.");
        sErrorEventInfoMap.put(
                "NO_RESTORE_METADATA_AVAILABLE", "Unexpected behavior of BackupTransport.");
        sErrorEventInfoMap.put(
                "NO_PM_METADATA_RECEIVED",
                "@pm@ has not been restored. All restore sessions must restore @pm@ first, before"
                        + " any other package.");
        sErrorEventInfoMap.put(
                "PM_AGENT_HAS_NO_METADATA",
                "The restored data of @pm@ does not contain the metadata required for "
                        + "signature/version verification.");
        sErrorEventInfoMap.put(
                "LOST_TRANSPORT",
                "BackupManager service lost connection to BackupTransport during restore. This "
                        + "may be a transient error, e.g. such as during GMSCore updates.");
        sErrorEventInfoMap.put("APK_NOT_INSTALLED", "App is not installed on the device.");
        sErrorEventInfoMap.put("CANNOT_RESTORE_WITHOUT_APK", "Unexpected error.");
        sErrorEventInfoMap.put(
                "MISSING_SIGNATURE", "Signature missing from backup data provided for restore.");
        sErrorEventInfoMap.put(
                "EXPECTED_DIFFERENT_PACKAGE",
                "The backup data being used for restore belongs to a different app.");
        sErrorEventInfoMap.put(
                "UNKNOWN_VERSION", "Backup manifest version in backup data is unknown.");
        sErrorEventInfoMap.put(
                "CORRUPT_MANIFEST", "Backup manifest is not of the expected format.");
        sErrorEventInfoMap.put("WIDGET_METADATA_MISMATCH", "Unexpected error.");
        sErrorEventInfoMap.put("WIDGET_UNKNOWN_VERSION", "Unexpected error.");
        sErrorEventInfoMap.put("NO_PACKAGES", "Backup requested for 0 packages.");
        sErrorEventInfoMap.put(
                "TRANSPORT_IS_NULL",
                "The selected BackupTransport is either invalid (not valid component name) or not "
                        + "registered (valid component, but not allow-listed).");
        sErrorEventInfoMap.put(
                "TRANSPORT_ERROR_DURING_START_RESTORE",
                "Could not start restore due to Transport error.");
        sErrorEventInfoMap.put(
                "CANNOT_GET_NEXT_PKG_NAME",
                "BackupTransport failure during restore session. Failure in getting the next "
                        + "package name for restore.");
        sErrorEventInfoMap.put(
                "NO_NEXT_RESTORE_TARGET",
                "Call to BackupTransport failed when getting next restore package.");
        sErrorEventInfoMap.put(
                "TRANSPORT_ERROR_KV_RESTORE",
                "Failed to fetch backup data from BackupTransport for restore.");
        sErrorEventInfoMap.put(
                "NO_FEEDER_THREAD",
                "IO error when creating pipes for streaming data to BackupAgent.");
        sErrorEventInfoMap.put(
                "TRANSPORT_ERROR_FULL_RESTORE",
                "BackupTransport error when reading backup data for restore.");
        sErrorEventInfoMap.put(
                "RESTORE_DATA_DOES_NOT_BELONG_TO_PACKAGE",
                "The backup data being used for restore belongs to a different app.");
        sErrorEventInfoMap.put(
                "FAILED_TO_READ_DATA_FROM_TRANSPORT",
                "IO error when reading data from BackupTransport.");
    }

    /**
     * Parses the output of BackupManager command line utility (bmgr) and returns a list of the
     * errors present in the output.
     *
     * @param reader The BufferedReader to read the output from.
     * @return A list of BmgrError objects, one for each error found.
     * @throws IOException If there is an error reading from the BufferedReader.
     */
    public static List<BmgrError> parseBmgrErrors(BufferedReader reader) throws IOException {
        List<BmgrError> errors = new ArrayList<>();
        boolean isBackup = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (BACKUP_START_PATTERN.matcher(line).find()) {
                isBackup = true;
                continue;
            }

            Matcher eventMatcher = EVENT_PATTERN.matcher(line);
            Matcher packageResultMatcher = PACKAGE_RESULT_PATTERN.matcher(line);
            Matcher backupFinishedMatcher = BACKUP_FINISHED_PATTERN.matcher(line);
            Matcher restoreFinishedMatcher = RESTORE_FINISHED_PATTERN.matcher(line);

            if (eventMatcher.find()) {
                String eventId = eventMatcher.group(2).trim();
                if (sErrorEventInfoMap.containsKey(eventId)) {
                    errors.add(new BmgrError(eventId, sErrorEventInfoMap.get(eventId)));
                }
            } else if (isBackup && packageResultMatcher.find()) {
                String packageName = packageResultMatcher.group(1).trim();
                String result = packageResultMatcher.group(2).trim();
                if (!"Success".equalsIgnoreCase(result)) {
                    errors.add(new BmgrError(result, "Backup failed for package: " + packageName));
                }
            } else if (isBackup && backupFinishedMatcher.find()) {
                String result = backupFinishedMatcher.group(1).trim();
                if (!"Success".equalsIgnoreCase(result)) {
                    errors.add(new BmgrError(result, "Backup operation failed."));
                }
            } else if (!isBackup && restoreFinishedMatcher.find()) {
                String result = restoreFinishedMatcher.group(1).trim();
                if (!"0".equalsIgnoreCase(result)) {
                    errors.add(new BmgrError(result, "Restore operation failed"));
                }
            }
        }

        reader.close();
        return errors;
    }

    /**
     * Parses the output of BackupManager command line utility (bmgr) and returns a list of the
     * errors present in the output.
     *
     * @param bmgrOutput The output of the bmgr command.
     * @return A list of BmgrError objects, one for each error found.
     * @throws IOException If there is an error reading from the StringReader.
     */
    public static List<BmgrError> parseBmgrErrors(String bmgrOutput) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(bmgrOutput));
        return parseBmgrErrors(reader);
    }
}
