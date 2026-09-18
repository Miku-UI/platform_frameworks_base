package com.android.server.backup.fullbackup;

import static com.android.server.backup.UserBackupManagerService.BACKUP_MANIFEST_FILENAME;
import static com.android.server.backup.UserBackupManagerService.BACKUP_MANIFEST_VERSION;
import static com.android.server.backup.UserBackupManagerService.BACKUP_METADATA_FILENAME;
import static com.android.server.backup.UserBackupManagerService.BACKUP_METADATA_VERSION;
import static com.android.server.backup.UserBackupManagerService.BACKUP_WIDGET_METADATA_TOKEN;
import static com.android.server.backup.UserBackupManagerService.CROSS_PLATFORM_MANIFEST_FILENAME;
import static com.android.server.backup.crossplatform.PlatformConfigParser.PLATFORM_IOS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;
import static org.testng.Assert.expectThrows;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Application;
import android.app.backup.BackupDataInput;
import android.app.backup.FullBackupDataOutput;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningDetails;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;

import com.android.server.backup.crossplatform.CrossPlatformManifest;
import com.android.server.backup.utils.BackupEligibilityRules;
import com.android.server.testing.shadows.ShadowBackupDataInput;
import com.android.server.testing.shadows.ShadowBackupDataOutput;
import com.android.server.testing.shadows.ShadowFullBackup;

import org.junit.After;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowApplicationPackageManager;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(
        shadows = {
            ShadowBackupDataInput.class,
            ShadowBackupDataOutput.class,
            ShadowEnvironment.class,
            ShadowFullBackup.class,
            ShadowSigningInfo.class,
        })
@LooperMode(LooperMode.Mode.PAUSED)
public class AppMetadataBackupWriterTest {
    private static final String TEST_PACKAGE = "com.test.package";
    private static final Long TEST_PACKAGE_VERSION_CODE = 100L;

    private @UserIdInt int mUserId;
    private PackageManager mPackageManager;
    private ShadowApplicationPackageManager mShadowPackageManager;
    private File mFilesDir;
    private File mBackupDataOutputFile;
    private FullBackupDataOutput mOutput;
    private AppMetadataBackupWriter mBackupWriter;
    @Mock private BackupEligibilityRules mBackupEligibilityRules;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Application application = RuntimeEnvironment.application;

        mUserId = UserHandle.USER_SYSTEM;
        mPackageManager = application.getPackageManager();
        mShadowPackageManager = (ShadowApplicationPackageManager) shadowOf(mPackageManager);

        mFilesDir = RuntimeEnvironment.application.getFilesDir();
        mBackupDataOutputFile = new File(mFilesDir, "output");
        mBackupDataOutputFile.createNewFile();
        ParcelFileDescriptor pfd =
                ParcelFileDescriptor.open(
                        mBackupDataOutputFile, ParcelFileDescriptor.MODE_READ_WRITE);
        mOutput = new FullBackupDataOutput(pfd, /* quota */ -1, /* transportFlags */ 0);
    }

    @After
    public void tearDown() throws Exception {
        mBackupDataOutputFile.delete();
    }

    /**
     * The manifest format is:
     *
     * <pre>
     *     BACKUP_MANIFEST_VERSION
     *     package name
     *     package version code
     *     platform version code
     *     empty line (installer package name)
     *     0 (boolean withApk)
     *     # of signatures N
     *     N* (signature byte array in ascii format per Signature.toCharsString())
     * </pre>
     */
    @Test
    public void testBackupManifest_withoutSignatures_writesCorrectData() throws Exception {
        PackageInfo packageInfo =
                createPackageInfo(TEST_PACKAGE, TEST_PACKAGE_VERSION_CODE);
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);

        mBackupWriter.backupManifest();

        byte[] manifestBytes = getWrittenBytes(mBackupDataOutputFile, /* includeTarHeader */ false);
        String[] manifest = new String(manifestBytes, StandardCharsets.UTF_8).split("\n");
        assertThat(manifest.length).isEqualTo(7);
        assertThat(manifest[0]).isEqualTo(Integer.toString(BACKUP_MANIFEST_VERSION));
        assertThat(manifest[1]).isEqualTo(TEST_PACKAGE);
        assertThat(manifest[2]).isEqualTo(Long.toString(TEST_PACKAGE_VERSION_CODE));
        assertThat(manifest[3]).isEqualTo(Integer.toString(Build.VERSION.SDK_INT));
        assertThat(manifest[4]).isEqualTo("");
        assertThat(manifest[5]).isEqualTo("0"); // withApk
        assertThat(manifest[6]).isEqualTo("0"); // signatures
    }

    /**
     * The manifest format is:
     *
     * <pre>
     *     BACKUP_MANIFEST_VERSION
     *     package name
     *     package version code
     *     platform version code
     *     empty line (installer package name)
     *     0 (boolean withApk)
     *     # of signatures N
     *     N* (signature byte array in ascii format per Signature.toCharsString())
     * </pre>
     */
    @Test
    public void testBackupManifest_withSignatures_writesCorrectSignatures() throws Exception {
        PackageInfo packageInfo =
                createPackageInfo(TEST_PACKAGE, TEST_PACKAGE_VERSION_CODE);
        packageInfo.signingInfo =
                new SigningInfo(
                        new SigningDetails(
                                new Signature[] {new Signature("1234"), new Signature("5678")},
                                SigningDetails.SignatureSchemeVersion.SIGNING_BLOCK_V3,
                                null,
                                null));
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);

        mBackupWriter.backupManifest();

        byte[] manifestBytes = getWrittenBytes(mBackupDataOutputFile, /* includeTarHeader */ false);
        String[] manifest = new String(manifestBytes, StandardCharsets.UTF_8).split("\n");
        assertThat(manifest.length).isEqualTo(9);
        assertThat(manifest[6]).isEqualTo("2"); // # of signatures
        assertThat(manifest[7]).isEqualTo("1234"); // first signature
        assertThat(manifest[8]).isEqualTo("5678"); // second signature
    }

    @Test
    public void testBackupManifest_whenRunPreviouslyWithSameData_producesSameBytesOnSecondRun()
            throws Exception {
        PackageInfo packageInfo =
                createPackageInfo(TEST_PACKAGE, TEST_PACKAGE_VERSION_CODE);
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);
        mBackupWriter.backupManifest();
        byte[] firstRunBytes = getWrittenBytes(mBackupDataOutputFile, /* includeTarHeader */ true);
        mBackupDataOutputFile.delete();
        mBackupDataOutputFile.createNewFile();
        ParcelFileDescriptor pfd =
                ParcelFileDescriptor.open(
                        mBackupDataOutputFile, ParcelFileDescriptor.MODE_READ_WRITE);
        mOutput = new FullBackupDataOutput(pfd, /* quota */ -1, /* transportFlags */ 0);
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);

        mBackupWriter.backupManifest();

        byte[] secondRunBytes = getWrittenBytes(mBackupDataOutputFile, /* includeTarHeader */ true);
        assertThat(firstRunBytes).isEqualTo(secondRunBytes);
    }

    /**
     * The widget data format with metadata is:
     *
     * <pre>
     *     BACKUP_METADATA_VERSION
     *     package name
     *     4 : Integer token identifying the widget data blob.
     *     4 : Integer size of the widget data.
     *     N : Raw bytes of the widget data.
     * </pre>
     */
    @Test
    public void testBackupWidget_writesCorrectData() throws Exception {
        PackageInfo packageInfo =
                createPackageInfo(TEST_PACKAGE, TEST_PACKAGE_VERSION_CODE);
        byte[] widgetBytes = "widget".getBytes();
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);

        mBackupWriter.backupWidget(widgetBytes);

        byte[] writtenBytes = getWrittenBytes(mBackupDataOutputFile, /* includeTarHeader */ false);
        String[] widgetData = new String(writtenBytes, StandardCharsets.UTF_8).split("\n");
        assertThat(widgetData.length).isEqualTo(3);
        // Metadata header
        assertThat(widgetData[0]).isEqualTo(Integer.toString(BACKUP_METADATA_VERSION));
        assertThat(widgetData[1]).isEqualTo(packageInfo.packageName);
        // Widget data
        ByteArrayOutputStream expectedBytes = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(expectedBytes);
        stream.writeInt(BACKUP_WIDGET_METADATA_TOKEN);
        stream.writeInt(widgetBytes.length);
        stream.write(widgetBytes);
        stream.flush();
        assertThat(widgetData[2]).isEqualTo(expectedBytes.toString());
    }

    @Test
    public void testBackupWidget_withNullWidgetData_throwsNullPointerException() throws Exception {
        PackageInfo packageInfo =
                createPackageInfo(TEST_PACKAGE, TEST_PACKAGE_VERSION_CODE);
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);

        expectThrows(NullPointerException.class, () -> mBackupWriter.backupWidget(null));
    }

    @Test
    public void testBackupWidget_withEmptyWidgetData_throwsIllegalArgumentException()
            throws Exception {
        PackageInfo packageInfo =
                createPackageInfo(TEST_PACKAGE, TEST_PACKAGE_VERSION_CODE);
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);

        expectThrows(IllegalArgumentException.class, () -> mBackupWriter.backupWidget(new byte[0]));
    }

    @Test
    public void testBackupWidget_whenRunPreviouslyWithSameData_producesSameBytesOnSecondRun()
            throws Exception {
        PackageInfo packageInfo =
                createPackageInfo(TEST_PACKAGE, TEST_PACKAGE_VERSION_CODE);
        byte[] widgetBytes = "widget".getBytes();
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);
        mBackupWriter.backupWidget(widgetBytes);
        byte[] firstRunBytes = getWrittenBytes(mBackupDataOutputFile, /* includeTarHeader */ true);
        mBackupDataOutputFile.delete();
        mBackupDataOutputFile.createNewFile();
        ParcelFileDescriptor pfd =
                ParcelFileDescriptor.open(
                        mBackupDataOutputFile, ParcelFileDescriptor.MODE_READ_WRITE);
        mOutput = new FullBackupDataOutput(pfd, /* quota */ -1, /* transportFlags */ 0);
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);

        mBackupWriter.backupWidget(widgetBytes);

        byte[] secondRunBytes = getWrittenBytes(mBackupDataOutputFile, /* includeTarHeader */ true);
        assertThat(firstRunBytes).isEqualTo(secondRunBytes);
    }

    @Test
    public void backupCrossPlatformManifest_writesCorrectData() throws Exception {
        PackageInfo packageInfo =
                createPackageInfo(TEST_PACKAGE, TEST_PACKAGE_VERSION_CODE);
        packageInfo.signingInfo =
                new SigningInfo(
                        new SigningDetails(
                                new Signature[] {new Signature("1234")},
                                SigningDetails.SignatureSchemeVersion.SIGNING_BLOCK_V3,
                                null,
                                null));
        mBackupWriter = new AppMetadataBackupWriter(
                mOutput, mPackageManager, packageInfo, mFilesDir);
        when(mBackupEligibilityRules.getPlatformSpecificParams(
                        packageInfo.applicationInfo, PLATFORM_IOS))
                .thenReturn(Collections.emptyList());

        mBackupWriter.backupCrossPlatformManifest(mBackupEligibilityRules);

        byte[] writtenBytes = getWrittenBytes(mBackupDataOutputFile, /* includeTarHeader */ false);
        CrossPlatformManifest manifest = CrossPlatformManifest.parseFrom(writtenBytes);
        assertThat(manifest.getPackageName()).isEqualTo(TEST_PACKAGE);
    }

    /**
     * Creates a test package and registers it with the package manager.
     */
    private PackageInfo createPackageInfo(String packageName, long versionCode) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.packageName = packageName;
        packageInfo.setLongVersionCode(versionCode);
        mShadowPackageManager.addPackage(packageInfo);
        return packageInfo;
    }

    /**
     * Reads backup data written to the {@code file} by {@link ShadowBackupDataOutput}. Uses {@link
     * ShadowBackupDataInput} to parse the data. Follows the format used by {@link
     * ShadowFullBackup#backupToTar(String, String, String, String, String, FullBackupDataOutput)}.
     *
     * @param includeTarHeader If {@code true}, returns the TAR header and data bytes combined.
     *     Otherwise, only returns the data bytes.
     */
    private byte[] getWrittenBytes(File file, boolean includeTarHeader) throws IOException {
        BackupDataInput input = new BackupDataInput(new FileInputStream(file).getFD());
        input.readNextHeader();
        int dataSize = input.getDataSize();

        byte[] bytes;
        if (includeTarHeader) {
            bytes = new byte[dataSize + 512];
            input.readEntityData(bytes, 0, dataSize + 512);
        } else {
            input.readEntityData(new byte[512], 0, 512); // skip TAR header
            bytes = new byte[dataSize];
            input.readEntityData(bytes, 0, dataSize);
        }

        return bytes;
    }

    private File createApkFileAndWrite(byte[] data) throws IOException {
        File apkFile = new File(mFilesDir, "apk");
        apkFile.createNewFile();
        Files.write(apkFile.toPath(), data);
        return apkFile;
    }
}
