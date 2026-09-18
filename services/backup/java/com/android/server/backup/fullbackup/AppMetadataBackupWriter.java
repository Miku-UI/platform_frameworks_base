package com.android.server.backup.fullbackup;

import static com.android.server.backup.BackupManagerService.DEBUG;
import static com.android.server.backup.BackupManagerService.TAG;
import static com.android.server.backup.UserBackupManagerService.BACKUP_MANIFEST_FILENAME;
import static com.android.server.backup.UserBackupManagerService.BACKUP_METADATA_FILENAME;
import static com.android.server.backup.UserBackupManagerService.BACKUP_MANIFEST_VERSION;
import static com.android.server.backup.UserBackupManagerService.BACKUP_METADATA_VERSION;
import static com.android.server.backup.UserBackupManagerService.BACKUP_WIDGET_METADATA_TOKEN;
import static com.android.server.backup.UserBackupManagerService.CROSS_PLATFORM_MANIFEST_FILENAME;
import static com.android.server.backup.crossplatform.PlatformConfigParser.PLATFORM_IOS;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.backup.FullBackup;
import android.app.backup.FullBackupDataOutput;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.util.StringBuilderPrinter;

import com.android.internal.util.Preconditions;
import com.android.server.backup.crossplatform.CrossPlatformManifest;
import com.android.server.backup.utils.BackupEligibilityRules;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Writes the backup of app-specific metadata to {@link FullBackupDataOutput}. This data is not
 * backed up by the app's backup agent and is written before the agent writes its own data. This
 * includes the app's manifest, widget data and {@link CrossPlatformManifest}.
 */
public class AppMetadataBackupWriter {
    private final FullBackupDataOutput mOutput;
    private final PackageManager mPackageManager;
    private final PackageInfo mPackageInfo;
    private final File mFilesDir;

    /** The destination of the backup is specified by {@code output}. */
    public AppMetadataBackupWriter(FullBackupDataOutput output, PackageManager packageManager,
            PackageInfo packageInfo, File filesDir) {
        mOutput = output;
        mPackageManager = packageManager;
        mFilesDir = filesDir;
        mPackageInfo = packageInfo;
    }

    /**
     * Back up the app's manifest.
     *
     * <ol>
     *   <li>Create a temporary file {@code manifestFile} in the specified directory {@code
     *       filesDir}.
     *   <li>Write the app's manifest data to the specified temporary file {@code manifestFile}.
     *   <li>Backup the file in TAR format to the backup destination {@link #mOutput}.
     *   <li>Delete the temporary file.
     * </ol>
     *
     */
    // TODO(b/113806991): Look into streaming the backup data directly.
    public void backupManifest() throws IOException {
        File manifestFile = new File(mFilesDir, BACKUP_MANIFEST_FILENAME);

        byte[] manifestBytes = getManifestBytes(mPackageInfo);
        FileOutputStream outputStream = new FileOutputStream(manifestFile);
        outputStream.write(manifestBytes);
        outputStream.close();

        backupTempFileAndDeleteFile(manifestFile);
    }

    /**
     * Gets the app's manifest as a byte array. All data are strings ending in LF.
     *
     * <p>The manifest format is:
     *
     * <pre>
     *     BACKUP_MANIFEST_VERSION
     *     package name
     *     package version code
     *     platform version code
     *     empty installer package name
     *     0 (boolean withApk)
     *     # of signatures N
     *     N* (signature byte array in ascii format per Signature.toCharsString())
     * </pre>
     */
    private byte[] getManifestBytes(PackageInfo packageInfo) {
        String packageName = packageInfo.packageName;
        StringBuilder builder = new StringBuilder(4096);
        StringBuilderPrinter printer = new StringBuilderPrinter(builder);

        printer.println(Integer.toString(BACKUP_MANIFEST_VERSION));
        printer.println(packageName);
        printer.println(Long.toString(packageInfo.getLongVersionCode()));
        printer.println(Integer.toString(Build.VERSION.SDK_INT));

        // In the deprecated adb backup functionality APKs can be backed up and restored in the TAR
        // file but transport based restore does not support that. We still need to include these
        // lines because our TAR format requires them. The next two lines are placeholders for
        // the APK data which is no longer backed up.

        // Transport-based backup does not require the installer package name.
        printer.println("");
        // withApk is always false for transport-based backup.
        printer.println("0");

        // Write the signature block.
        SigningInfo signingInfo = packageInfo.signingInfo;
        if (signingInfo == null) {
            printer.println("0");
        } else {
            // Retrieve the newest signatures to write.
            // TODO (b/73988180) use entire signing history in case of rollbacks.
            Signature[] signatures = signingInfo.getApkContentsSigners();
            printer.println(Integer.toString(signatures.length));
            for (Signature sig : signatures) {
                printer.println(sig.toCharsString());
            }
        }
        return builder.toString().getBytes();
    }

    /**
     * Backup specified widget data. The widget data is prefaced by a metadata header.
     *
     * <ol>
     *   <li>Create a temporary file {@code metadataFile} in the specified directory {@code
     *       filesDir}.
     *   <li>Write a metadata header to the specified temporary file {@code metadataFile}.
     *   <li>Write widget data bytes to the same file.
     *   <li>Backup the file in TAR format to the backup destination {@link #mOutput}.
     *   <li>Delete the temporary file.
     * </ol>
     *
     * @throws IllegalArgumentException if the widget data provided is empty.
     */
    // TODO(b/113806991): Look into streaming the backup data directly.
    public void backupWidget(byte[] widgetData)
            throws IOException {
        File metadataFile = new File(mFilesDir, BACKUP_METADATA_FILENAME);

        Preconditions.checkArgument(widgetData.length > 0, "Can't backup widget with no data.");

        String packageName = mPackageInfo.packageName;
        FileOutputStream fileOutputStream = new FileOutputStream(metadataFile);
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);

        byte[] metadata = getMetadataBytes(packageName);
        bufferedOutputStream.write(metadata); // bypassing DataOutputStream
        writeWidgetData(dataOutputStream, widgetData);
        bufferedOutputStream.flush();
        dataOutputStream.close();

        backupTempFileAndDeleteFile(metadataFile);
    }

    /**
     * Back up the app's cross platform manifest.
     *
     * <ol>
     *   <li>Create a temporary file {@code manifestFile} in the specified directory {@code
     *       filesDir}.
     *   <li>Write the app's cross platform manifest data to the specified temporary file {@code
     *       manifestFile}.
     *   <li>Backup the file in TAR format to the backup destination {@link #mOutput}.
     *   <li>Delete the temporary file.
     * </ol>
     *
     */
    public void backupCrossPlatformManifest(BackupEligibilityRules backupEligibilityRules)
            throws IOException {
        CrossPlatformManifest manifest =
                CrossPlatformManifest.create(
                        mPackageInfo,
                        PLATFORM_IOS,
                        backupEligibilityRules.getPlatformSpecificParams(
                                mPackageInfo.applicationInfo, PLATFORM_IOS));
        File manifestFile = new File(mFilesDir, CROSS_PLATFORM_MANIFEST_FILENAME);
        try (FileOutputStream out = new FileOutputStream(manifestFile)) {
            out.write(manifest.toByteArray());
        }

        backupTempFileAndDeleteFile(manifestFile);
    }

    /**
     * Back up the specified temp file to {@link #mOutput} and delete the file.
     */
    private void backupTempFileAndDeleteFile(File tempFile) {
        // We want the manifest block in the archive stream to be constant each time we generate
        // a backup stream for the app. However, the underlying TAR mechanism sees it as a file
        // and will propagate its last modified time. We pin the last modified time to zero to
        // prevent the TAR header from varying.
        tempFile.setLastModified(0);

        FullBackup.backupToTar(
                mPackageInfo.packageName,
                /* domain= */ null,
                /* linkDomain= */ null,
                mFilesDir.getAbsolutePath(),
                tempFile.getAbsolutePath(),
                mOutput);

        tempFile.delete();
    }

    /**
     * Gets the app's metadata as a byte array. All entries are strings ending in LF.
     *
     * <p>The metadata format is:
     *
     * <pre>
     *     BACKUP_METADATA_VERSION
     *     package name
     * </pre>
     */
    private byte[] getMetadataBytes(String packageName) {
        StringBuilder builder = new StringBuilder(512);
        StringBuilderPrinter printer = new StringBuilderPrinter(builder);
        printer.println(Integer.toString(BACKUP_METADATA_VERSION));
        printer.println(packageName);
        return builder.toString().getBytes();
    }

    /**
     * Write a byte array of widget data to the specified output stream. All integers are binary in
     * network byte order.
     *
     * <p>The widget data format:
     *
     * <pre>
     *     4 : Integer token identifying the widget data blob.
     *     4 : Integer size of the widget data.
     *     N : Raw bytes of the widget data.
     * </pre>
     */
    private void writeWidgetData(DataOutputStream out, byte[] widgetData) throws IOException {
        out.writeInt(BACKUP_WIDGET_METADATA_TOKEN);
        out.writeInt(widgetData.length);
        out.write(widgetData);
    }
}
