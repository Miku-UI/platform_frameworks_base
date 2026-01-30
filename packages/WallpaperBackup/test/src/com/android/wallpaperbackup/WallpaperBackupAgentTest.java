/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.wallpaperbackup;

import static android.app.Flags.fixWallpaperCropsOnRestore;
import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

import static com.android.wallpaperbackup.WallpaperBackupAgent.LOCK_WALLPAPER_STAGE;
import static com.android.wallpaperbackup.WallpaperBackupAgent.SYSTEM_WALLPAPER_STAGE;
import static com.android.wallpaperbackup.WallpaperBackupAgent.WALLPAPER_BACKUP_DEVICE_INFO_STAGE;
import static com.android.wallpaperbackup.WallpaperBackupAgent.WALLPAPER_INFO_STAGE;
import static com.android.wallpaperbackup.WallpaperEventLogger.ERROR_INELIGIBLE;
import static com.android.wallpaperbackup.WallpaperEventLogger.ERROR_NO_METADATA;
import static com.android.wallpaperbackup.WallpaperEventLogger.ERROR_NO_WALLPAPER;
import static com.android.wallpaperbackup.WallpaperEventLogger.ERROR_QUOTA_EXCEEDED;
import static com.android.wallpaperbackup.WallpaperEventLogger.WALLPAPER_DESCRIPTION_LOCK;
import static com.android.wallpaperbackup.WallpaperEventLogger.WALLPAPER_DESCRIPTION_SYSTEM;
import static com.android.wallpaperbackup.WallpaperEventLogger.WALLPAPER_IMG_LOCK;
import static com.android.wallpaperbackup.WallpaperEventLogger.WALLPAPER_IMG_SYSTEM;
import static com.android.wallpaperbackup.WallpaperEventLogger.WALLPAPER_LIVE_LOCK;
import static com.android.wallpaperbackup.WallpaperEventLogger.WALLPAPER_LIVE_SYSTEM;
import static com.android.window.flags.Flags.multiCrop;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.app.Flags;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.app.backup.BackupAnnotations;
import android.app.backup.BackupManager;
import android.app.backup.BackupRestoreEventLogger;
import android.app.backup.BackupRestoreEventLogger.DataTypeResult;
import android.app.backup.FullBackupDataOutput;
import android.app.wallpaper.WallpaperDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.wallpaper.WallpaperService;
import android.util.Pair;
import android.util.SparseArray;
import android.util.Xml;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.content.PackageMonitor;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.wallpaperbackup.WallpaperBackupAgent.WallpaperDisplayInfo;
import com.android.wallpaperbackup.utils.ContextWithServiceOverrides;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RunWith(AndroidJUnit4.class)
public class WallpaperBackupAgentTest {
    private static final String TEST_WALLPAPER_PACKAGE = "wallpaper_package";

    private static final int TEST_SYSTEM_WALLPAPER_ID = 1;
    private static final int TEST_LOCK_WALLPAPER_ID = 2;
    private static final int NO_LOCK_WALLPAPER_ID = -1;
    // An arbitrary user.
    private static final UserHandle USER_HANDLE = new UserHandle(15);

    @Mock
    private FullBackupDataOutput mOutput;

    @Mock
    private WallpaperManager mWallpaperManager;
    @Mock
    private Context mMockContext;
    @Mock
    private BackupManager mBackupManager;

    private boolean mShouldMockDisplays = false;
    private final List<WallpaperDisplayInfo> mMockWallpaperDisplayInfos = new ArrayList<>();
    private boolean mShouldMockBitmapSize = false;
    private Point mMockBitmapSize = null;

    private ContextWithServiceOverrides mContext;
    private IsolatedWallpaperBackupAgent mWallpaperBackupAgent;
    private ComponentName mWallpaperComponent;
    private WallpaperDescription mWallpaperDescription;
    private Locale mOriginalLocale;

    private final TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    @Captor
    private ArgumentCaptor<SparseArray<Rect>> mCropHintsCaptor;

    @Rule
    public RuleChain mRuleChain = RuleChain.outerRule(new SetFlagsRule()).around(mTemporaryFolder);

    @Before
    public void setUp() {

        MockitoAnnotations.initMocks(this);
        when(mWallpaperManager.isWallpaperBackupEligible(eq(FLAG_SYSTEM))).thenReturn(true);
        when(mWallpaperManager.isWallpaperBackupEligible(eq(FLAG_LOCK))).thenReturn(true);

        mContext = new ContextWithServiceOverrides(ApplicationProvider.getApplicationContext());
        mContext.injectSystemService(WallpaperManager.class, mWallpaperManager);

        mWallpaperBackupAgent = new IsolatedWallpaperBackupAgent();
        mWallpaperBackupAgent.attach(mContext);
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.BACKUP);

        mWallpaperComponent = new ComponentName(TEST_WALLPAPER_PACKAGE, "");
        mWallpaperDescription = new WallpaperDescription.Builder().setComponent(
                mWallpaperComponent).setId("id").build();

        mOriginalLocale = Locale.getDefault();
    }

    @After
    public void tearDown() {
        FileUtils.deleteContents(mContext.getFilesDir());
        Locale.setDefault(mOriginalLocale);
        mShouldMockDisplays = false;
        mShouldMockBitmapSize = false;
    }

    @Test
    public void testOnFullBackup_backsUpEmptyFile() throws IOException {
        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertThat(getBackedUpFileOptional("empty").isPresent()).isTrue();
    }

    @Test
    public void testOnFullBackup_noExistingInfoStage_backsUpInfoFile() throws Exception {
        mockWallpaperInfoFileWithContents("fake info file");

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(WALLPAPER_INFO_STAGE).get(),
                "fake info file");
    }

    @Test
    public void testOnFullBackup_existingInfoStage_noChange_backsUpAlreadyStagedInfoFile()
            throws Exception {
        // Do a backup first so the info file is staged.
        mockWallpaperInfoFileWithContents("old info file");
        // Provide system and lock wallpapers but don't change them in between backups.
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockLockWallpaperFileWithContents("lock wallpaper");
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // This new wallpaper should be ignored since the ID of neither wallpaper changed.
        mockWallpaperInfoFileWithContents("new info file");

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(WALLPAPER_INFO_STAGE).get(),
                "old info file");
    }

    @Test
    public void testOnFullBackup_existingInfoStage_sysChanged_backsUpNewInfoFile()
            throws Exception {
        // Do a backup first so the backed up system wallpaper ID is persisted to disk.
        mockWallpaperInfoFileWithContents("old info file");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // Mock that the user changed the system wallpaper.
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID + 1, TEST_LOCK_WALLPAPER_ID);
        mockWallpaperInfoFileWithContents("new info file");

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(WALLPAPER_INFO_STAGE).get(),
                "new info file");
    }

    @Test
    public void testOnFullBackup_existingInfoStage_lockChanged_backsUpNewInfoFile()
            throws Exception {
        // Do a backup first so the backed up lock wallpaper ID is persisted to disk.
        mockWallpaperInfoFileWithContents("old info file");
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // Mock that the user changed the system wallpaper.
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID + 1);
        mockWallpaperInfoFileWithContents("new info file");

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(WALLPAPER_INFO_STAGE).get(),
                "new info file");
    }

    @Test
    public void testOnFullBackup_systemWallpaperNotEligible_doesNotBackUpSystemWallpaper()
            throws Exception {
        when(mWallpaperManager.isWallpaperBackupEligible(eq(FLAG_SYSTEM))).thenReturn(false);
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, NO_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertThat(getBackedUpFileOptional(SYSTEM_WALLPAPER_STAGE).isPresent()).isFalse();
    }

    @Test
    public void testOnFullBackup_existingSystemStage_noSysChange_backsUpAlreadyStagedFile()
            throws Exception {
        // Do a backup first so that a stage file is created.
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, NO_LOCK_WALLPAPER_ID);
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // This new file should be ignored since the ID of the wallpaper did not change.
        mockSystemWallpaperFileWithContents("new system wallpaper");

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(SYSTEM_WALLPAPER_STAGE).get(),
                "system wallpaper");
    }

    @Test
    public void testOnFullBackup_existingSystemStage_sysChanged_backsUpNewSystemWallpaper()
            throws Exception {
        // Do a backup first so that a stage file is created.
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, NO_LOCK_WALLPAPER_ID);
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // Mock that the system wallpaper was changed by the user.
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID + 1, NO_LOCK_WALLPAPER_ID);
        mockSystemWallpaperFileWithContents("new system wallpaper");

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(SYSTEM_WALLPAPER_STAGE).get(),
                "new system wallpaper");
    }

    @Test
    public void testOnFullBackup_noExistingSystemStage_backsUpSystemWallpaper()
            throws Exception {
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, NO_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(SYSTEM_WALLPAPER_STAGE).get(),
                "system wallpaper");
    }

    @Test
    public void testOnFullBackup_lockWallpaperNotEligible_doesNotBackUpLockWallpaper()
            throws Exception {
        when(mWallpaperManager.isWallpaperBackupEligible(eq(FLAG_LOCK))).thenReturn(false);
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertThat(getBackedUpFileOptional(LOCK_WALLPAPER_STAGE).isPresent()).isFalse();
    }

    @Test
    public void testOnFullBackup_existingLockStage_lockWallpaperRemovedByUser_NotBackUpOldStage()
            throws Exception {
        // Do a backup first so that a stage file is created.
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // Mock the ID of the lock wallpaper to indicate it's not set.
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, NO_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertThat(getBackedUpFileOptional(LOCK_WALLPAPER_STAGE).isPresent()).isFalse();
    }

    @Test
    public void testOnFullBackup_existingLockStage_lockWallpaperRemovedByUser_deletesExistingStage()
            throws Exception {
        // Do a backup first so that a stage file is created.
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // Mock the ID of the lock wallpaper to indicate it's not set.
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, NO_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertThat(new File(mContext.getFilesDir(), LOCK_WALLPAPER_STAGE).exists()).isFalse();
    }

    @Test
    public void testOnFullBackup_existingLockStage_noLockChange_backsUpAlreadyStagedFile()
            throws Exception {
        // Do a backup first so that a stage file is created.
        mockLockWallpaperFileWithContents("old lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // This new file should be ignored since the ID of the wallpaper did not change.
        mockLockWallpaperFileWithContents("new lock wallpaper");

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(LOCK_WALLPAPER_STAGE).get(),
                "old lock wallpaper");
    }

    @Test
    public void testOnFullBackup_existingLockStage_lockChanged_backsUpNewLockWallpaper()
            throws Exception {
        // Do a backup first so that a stage file is created.
        mockLockWallpaperFileWithContents("old lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);
        mWallpaperBackupAgent.onFullBackup(mOutput);
        mWallpaperBackupAgent.mBackedUpFiles.clear();
        // Mock that the lock wallpaper was changed by the user.
        mockLockWallpaperFileWithContents("new lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID + 1);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(LOCK_WALLPAPER_STAGE).get(),
                "new lock wallpaper");
    }

    @Test
    public void testOnFullBackup_noExistingLockStage_backsUpLockWallpaper()
            throws Exception {
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        assertFileContentEquals(getBackedUpFileOptional(LOCK_WALLPAPER_STAGE).get(),
                "lock wallpaper");
    }

    @Test
    public void testUpdateWallpaperComponent_immediate_systemAndLock() throws IOException {
        mWallpaperBackupAgent.mPackageExists = true;

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);

        assertThat(mWallpaperBackupAgent.mGetPackageMonitorCallCount).isEqualTo(0);
        verify(mWallpaperManager, times(1))
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_LOCK | FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_LOCK);
        verify(mWallpaperManager, never()).clear(anyInt());
    }

    @Test
    public void testUpdateWallpaperComponent_immediate_systemOnly()
            throws IOException {
        mWallpaperBackupAgent.mPackageExists = true;

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_SYSTEM);

        assertThat(mWallpaperBackupAgent.mGetPackageMonitorCallCount).isEqualTo(0);
        verify(mWallpaperManager, times(1))
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_LOCK);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_LOCK | FLAG_SYSTEM);
        verify(mWallpaperManager, never()).clear(anyInt());
    }

    @Test
    public void testUpdateWallpaperDescription_immediate_systemAndLock()
            throws IOException {
        mWallpaperBackupAgent.mPackageExists = true;

        mWallpaperBackupAgent.updateWallpaperComponent(
                new Pair<>(mWallpaperComponent, mWallpaperDescription), /* which */
                FLAG_LOCK | FLAG_SYSTEM);

        verify(mWallpaperManager, times(1))
                .setWallpaperComponentWithDescription(mWallpaperDescription,
                        FLAG_LOCK | FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithDescription(mWallpaperDescription, FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithDescription(mWallpaperDescription, FLAG_LOCK);
        verify(mWallpaperManager, never()).clear(anyInt());
    }

    @Test
    public void testUpdateWallpaperDescription_immediate_systemOnly() throws IOException {
        mWallpaperBackupAgent.mPackageExists = true;

        mWallpaperBackupAgent.updateWallpaperComponent(
                new Pair<>(mWallpaperComponent, mWallpaperDescription), /* which */ FLAG_SYSTEM);

        verify(mWallpaperManager, times(1))
                .setWallpaperComponentWithDescription(mWallpaperDescription, FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithDescription(mWallpaperDescription, FLAG_LOCK);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithDescription(mWallpaperDescription,
                        FLAG_LOCK | FLAG_SYSTEM);
        verify(mWallpaperManager, never()).clear(anyInt());
    }

    @Test
    public void testUpdateWallpaperDescription_delayed_systemAndLock()
            throws IOException {
        mWallpaperBackupAgent.mIsDeviceInRestore = true;
        mWallpaperBackupAgent.updateWallpaperComponent(
                new Pair<>(mWallpaperComponent, mWallpaperDescription), /* which */
                FLAG_LOCK | FLAG_SYSTEM);

        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);
        verify(mWallpaperManager, times(1))
                .setWallpaperComponentWithDescription(mWallpaperDescription,
                        FLAG_LOCK | FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithDescription(mWallpaperDescription, FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithDescription(mWallpaperDescription, FLAG_LOCK);
        verify(mWallpaperManager, never()).clear(anyInt());
    }

    @Test
    public void testUpdateWallpaperDescription_delayed_systemOnly() throws IOException {
        mWallpaperBackupAgent.mIsDeviceInRestore = true;

        mWallpaperBackupAgent.updateWallpaperComponent(
                new Pair<>(mWallpaperComponent, mWallpaperDescription), /* which */ FLAG_SYSTEM);

        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);

        verify(mWallpaperManager, times(1))
                .setWallpaperComponentWithDescription(mWallpaperDescription, FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithDescription(mWallpaperDescription, FLAG_LOCK);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithDescription(mWallpaperDescription,
                        FLAG_LOCK | FLAG_SYSTEM);
        verify(mWallpaperManager, never()).clear(anyInt());
    }

    @Test
    public void testUpdateWallpaperComponent_delayed_systemAndLock() throws IOException {
        mWallpaperBackupAgent.mIsDeviceInRestore = true;

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);
        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);
        verify(mWallpaperManager, times(1))
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_LOCK | FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_LOCK);
        verify(mWallpaperManager, never()).clear(anyInt());
    }

    @Test
    public void testUpdateWallpaperComponent_delayed_systemOnly()
            throws IOException {
        mWallpaperBackupAgent.mIsDeviceInRestore = true;

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_SYSTEM);
        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);

        verify(mWallpaperManager, times(1))
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_SYSTEM);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_LOCK);
        verify(mWallpaperManager, never())
                .setWallpaperComponentWithFlags(mWallpaperComponent, FLAG_LOCK | FLAG_SYSTEM);
        verify(mWallpaperManager, never()).clear(anyInt());
    }

    @Test
    public void testUpdateWallpaperComponent_delayed_deviceNotInRestore_doesNotApply()
            throws IOException {
        mWallpaperBackupAgent.mIsDeviceInRestore = false;

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);

        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);

        verify(mWallpaperManager, never()).setWallpaperComponent(mWallpaperComponent);
        verify(mWallpaperManager, never()).clear(eq(FLAG_LOCK));
    }

    @Test
    public void testUpdateWallpaperComponent_delayed_differentPackageInstalled_doesNotApply()
            throws IOException {
        mWallpaperBackupAgent.mIsDeviceInRestore = false;

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);

        // Imitate "wrong" wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(/* packageName */"",
                /* uid */0);

        verify(mWallpaperManager, never()).setWallpaperComponent(mWallpaperComponent);
        verify(mWallpaperManager, never()).clear(eq(FLAG_LOCK));
    }

    @Test
    public void testOnFullBackup_systemWallpaperImgSuccess_logsSuccess() throws Exception {
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, NO_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    public void testOnFullBackup_systemWallpaperImgIneligible_logsFailure() throws Exception {
        when(mWallpaperManager.isWallpaperBackupEligible(eq(FLAG_SYSTEM))).thenReturn(false);
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getErrors()).containsKey(ERROR_INELIGIBLE);
    }

    @Test
    public void testOnFullBackup_systemWallpaperImgMissing_logsFailure() throws Exception {
        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getErrors()).containsKey(ERROR_NO_WALLPAPER);
    }

    @Test
    public void testOnFullBackup_systemWallpaperImgMissingButHasLiveComponent_logsLiveSuccess()
            throws Exception {
        mockWallpaperInfoFileWithContents("info file");
        when(mWallpaperManager.getWallpaperInfo(anyInt())).thenReturn(getFakeWallpaperInfo());

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_LIVE_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getMetadataHash()).isNotNull();
    }

    @Test
    public void testOnFullBackup_systemWallpaperImgMissingButHasLiveComponent_logsNothingForImg()
            throws Exception {
        mockWallpaperInfoFileWithContents("info file");
        when(mWallpaperManager.getWallpaperInfo(anyInt())).thenReturn(getFakeWallpaperInfo());

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNull();
    }

    @Test
    public void testOnFullBackup_lockWallpaperImgSuccess_logsSuccess() throws Exception {
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    public void testOnFullBackup_lockWallpaperImgIneligible_logsFailure() throws Exception {
        when(mWallpaperManager.isWallpaperBackupEligible(eq(FLAG_LOCK))).thenReturn(false);
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getErrors()).containsKey(ERROR_INELIGIBLE);
    }

    @Test
    public void testOnFullBackup_lockWallpaperImgMissing_logsFailure() throws Exception {
        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getErrors()).containsKey(ERROR_NO_WALLPAPER);
    }

    @Test
    public void testOnFullBackup_lockWallpaperImgMissingButHasLiveComponent_logsLiveSuccess()
            throws Exception {
        mockWallpaperInfoFileWithContents("info file");
        when(mWallpaperManager.getWallpaperInfo(anyInt())).thenReturn(getFakeWallpaperInfo());

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_LIVE_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getMetadataHash()).isNotNull();
    }

    @Test
    public void testOnFullBackup_lockWallpaperImgMissingButHasLiveComponent_logsNothingForImg()
            throws Exception {
        mockWallpaperInfoFileWithContents("info file");
        when(mWallpaperManager.getWallpaperInfo(anyInt())).thenReturn(getFakeWallpaperInfo());

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNull();
    }


    @Test
    public void testOnFullBackup_exceptionThrown_logsException() throws Exception {
        when(mWallpaperManager.isWallpaperBackupEligible(anyInt())).thenThrow(
                new RuntimeException());
        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getErrors()).containsKey(RuntimeException.class.getName());
    }

    @Test
    public void testOnFullBackup_lastBackupOverQuota_logsLockFailure() throws Exception {
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);
        markAgentAsOverQuota();

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getErrors()).containsKey(ERROR_QUOTA_EXCEEDED);
    }

    @Test
    public void testOnFullBackup_lastBackupOverQuota_logsSystemSuccess() throws Exception {
        mockSystemWallpaperFileWithContents("system wallpaper");
        mockLockWallpaperFileWithContents("lock wallpaper");
        mockCurrentWallpaperIds(TEST_SYSTEM_WALLPAPER_ID, TEST_LOCK_WALLPAPER_ID);
        markAgentAsOverQuota();

        mWallpaperBackupAgent.onFullBackup(mOutput);

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    public void testOnRestore_wallpaperImgSuccess_logsSuccess() throws Exception {
        mockStagedWallpaperFile(WALLPAPER_INFO_STAGE);
        mockStagedWallpaperFile(SYSTEM_WALLPAPER_STAGE);
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.RESTORE);

        mWallpaperBackupAgent.onRestoreFinished();

        // wallpaper will be applied to home & lock screen, a success for both screens is expected
        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);

        result = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    public void testOnRestore_lockWallpaperImgSuccess_logsSuccess() throws Exception {
        mockStagedWallpaperFile(WALLPAPER_INFO_STAGE);
        mockStagedWallpaperFile(LOCK_WALLPAPER_STAGE);
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.RESTORE);

        mWallpaperBackupAgent.onRestoreFinished();

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
    }

    @Test
    public void testOnRestore_systemWallpaperImgMissingAndNoLive_logsFailure() throws Exception {
        mockStagedWallpaperFile(WALLPAPER_INFO_STAGE);
        mockStagedWallpaperFile(LOCK_WALLPAPER_STAGE);
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.RESTORE);

        mWallpaperBackupAgent.onRestoreFinished();

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getErrors()).containsKey(ERROR_NO_WALLPAPER);

    }

    @Test
    public void testOnRestore_wallpaperImgMissingAndNoLive_logsFailure() throws Exception {
        mockStagedWallpaperFile(WALLPAPER_INFO_STAGE);
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.RESTORE);

        mWallpaperBackupAgent.onRestoreFinished();

        for (String wallpaper: List.of(WALLPAPER_IMG_LOCK, WALLPAPER_IMG_SYSTEM)) {
            DataTypeResult result = getLoggingResult(wallpaper,
                    mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
            assertThat(result).isNotNull();
            assertThat(result.getFailCount()).isEqualTo(1);
            assertThat(result.getErrors()).containsKey(ERROR_NO_WALLPAPER);
        }
    }

    @Test
    public void testOnRestore_wallpaperInfoMissing_logsFailure() throws Exception {
        mockStagedWallpaperFile(SYSTEM_WALLPAPER_STAGE);
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.RESTORE);

        mWallpaperBackupAgent.onRestoreFinished();

        DataTypeResult result = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(result).isNotNull();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getErrors()).containsKey(ERROR_NO_METADATA);
    }

    @Test
    public void testOnRestore_imgMissingButWallpaperInfoHasLive_doesNotLogImg() throws Exception {
        mockRestoredLiveWallpaperFile();
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.RESTORE);

        mWallpaperBackupAgent.onRestoreFinished();

        DataTypeResult system = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        DataTypeResult lock = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(system).isNull();
        assertThat(lock).isNull();
    }

    @Test
    public void testOnRestore_throwsException_logsErrors() throws Exception {
        if (!multiCrop()) {
            when(mWallpaperManager.setStream(any(), any(), anyBoolean(), anyInt()))
                    .thenThrow(new RuntimeException());
        } else {
            when(mWallpaperManager.setStreamWithCrops(any(), any(SparseArray.class), anyBoolean(),
                    anyInt())).thenThrow(new RuntimeException());
        }
        mockStagedWallpaperFile(SYSTEM_WALLPAPER_STAGE);
        mockStagedWallpaperFile(WALLPAPER_INFO_STAGE);
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.RESTORE);

        mWallpaperBackupAgent.onRestoreFinished();

        DataTypeResult system = getLoggingResult(WALLPAPER_IMG_SYSTEM,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        DataTypeResult lock = getLoggingResult(WALLPAPER_IMG_LOCK,
                mWallpaperBackupAgent.getBackupRestoreEventLogger().getLoggingResults());
        assertThat(system).isNotNull();
        assertThat(system.getFailCount()).isEqualTo(1);
        assertThat(system.getErrors()).containsKey(RuntimeException.class.getName());
        assertThat(lock).isNotNull();
        assertThat(lock.getFailCount()).isEqualTo(1);
        assertThat(lock.getErrors()).containsKey(RuntimeException.class.getName());
    }

    @Test
    public void testUpdateWallpaperComponent_delayed_succeeds_logsSuccess() throws Exception {
        mWallpaperBackupAgent.mIsDeviceInRestore = true;
        when(mWallpaperManager.setWallpaperComponentWithFlags(any(), eq(FLAG_LOCK | FLAG_SYSTEM)))
                .thenReturn(true);
        BackupRestoreEventLogger logger = new BackupRestoreEventLogger(
                BackupAnnotations.OperationType.RESTORE);
        when(mBackupManager.getDelayedRestoreLogger()).thenReturn(logger);
        mWallpaperBackupAgent.setBackupManagerForTesting(mBackupManager);

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);
        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);

        DataTypeResult system = getLoggingResult(WALLPAPER_LIVE_SYSTEM, logger.getLoggingResults());
        DataTypeResult lock = getLoggingResult(WALLPAPER_LIVE_LOCK, logger.getLoggingResults());
        assertThat(system).isNotNull();
        assertThat(system.getSuccessCount()).isEqualTo(1);
        assertThat(lock).isNotNull();
        assertThat(lock.getSuccessCount()).isEqualTo(1);
    }


    @Test
    public void testUpdateWallpaperDescription_delayed_succeeds_logsSuccess() throws Exception {
        mWallpaperBackupAgent.mIsDeviceInRestore = true;
        when(mWallpaperManager.setWallpaperComponentWithDescription(any(),
                eq(FLAG_LOCK | FLAG_SYSTEM))).thenReturn(true);
        BackupRestoreEventLogger logger = new BackupRestoreEventLogger(
                BackupAnnotations.OperationType.RESTORE);
        when(mBackupManager.getDelayedRestoreLogger()).thenReturn(logger);
        mWallpaperBackupAgent.setBackupManagerForTesting(mBackupManager);

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(null, mWallpaperDescription),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);
        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);

        DataTypeResult system = getLoggingResult(WALLPAPER_DESCRIPTION_SYSTEM,
                logger.getLoggingResults());
        DataTypeResult lock = getLoggingResult(WALLPAPER_DESCRIPTION_LOCK,
                logger.getLoggingResults());
        assertThat(system).isNotNull();
        assertThat(system.getSuccessCount()).isEqualTo(1);
        assertThat(lock).isNotNull();
        assertThat(lock.getSuccessCount()).isEqualTo(1);
    }

    @Test
    public void testUpdateWallpaperComponent_delayed_fails_logsFailure() throws Exception {
        mWallpaperBackupAgent.mIsDeviceInRestore = true;
        BackupRestoreEventLogger logger = new BackupRestoreEventLogger(
                BackupAnnotations.OperationType.RESTORE);
        when(mBackupManager.getDelayedRestoreLogger()).thenReturn(logger);
        mWallpaperBackupAgent.setBackupManagerForTesting(mBackupManager);

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);
        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);

        DataTypeResult system = getLoggingResult(WALLPAPER_LIVE_SYSTEM, logger.getLoggingResults());
        assertThat(system).isNotNull();
        assertThat(system.getFailCount()).isEqualTo(1);
        assertThat(system.getErrors()).containsKey(
                WallpaperEventLogger.ERROR_SET_COMPONENT_EXCEPTION);
    }

    @Test
    public void testUpdateWallpaperDescription_delayed_fails_logsFailure() throws Exception {
        mWallpaperBackupAgent.mIsDeviceInRestore = true;
        BackupRestoreEventLogger logger = new BackupRestoreEventLogger(
                BackupAnnotations.OperationType.RESTORE);
        when(mBackupManager.getDelayedRestoreLogger()).thenReturn(logger);
        mWallpaperBackupAgent.setBackupManagerForTesting(mBackupManager);

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(null, mWallpaperDescription),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);
        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);

        DataTypeResult system = getLoggingResult(WALLPAPER_LIVE_SYSTEM, logger.getLoggingResults());
        assertThat(system).isNotNull();
        assertThat(system.getFailCount()).isEqualTo(1);
        assertThat(system.getErrors()).containsKey(
                WallpaperEventLogger.ERROR_SET_DESCRIPTION_EXCEPTION);
    }

    @Test
    public void testUpdateWallpaperComponent_delayed_packageNotInstalled_logsFailure()
            throws Exception {
        mWallpaperBackupAgent.mIsDeviceInRestore = false;
        BackupRestoreEventLogger logger = new BackupRestoreEventLogger(
                BackupAnnotations.OperationType.RESTORE);
        when(mBackupManager.getDelayedRestoreLogger()).thenReturn(logger);
        mWallpaperBackupAgent.setBackupManagerForTesting(mBackupManager);

        mWallpaperBackupAgent.updateWallpaperComponent(new Pair<>(mWallpaperComponent, null),
                /* which */ FLAG_LOCK | FLAG_SYSTEM);

        // Imitate wallpaper component installation.
        mWallpaperBackupAgent.mWallpaperPackageMonitor.onPackageAdded(TEST_WALLPAPER_PACKAGE,
                /* uid */0);

        DataTypeResult system = getLoggingResult(WALLPAPER_LIVE_SYSTEM, logger.getLoggingResults());
        DataTypeResult lock = getLoggingResult(WALLPAPER_LIVE_LOCK, logger.getLoggingResults());
        assertThat(system).isNotNull();
        assertThat(system.getFailCount()).isEqualTo(1);
        assertThat(system.getErrors()).containsKey(
                WallpaperEventLogger.ERROR_LIVE_PACKAGE_NOT_INSTALLED);
        assertThat(lock).isNotNull();
        assertThat(lock.getFailCount()).isEqualTo(1);
        assertThat(lock.getErrors()).containsKey(
                WallpaperEventLogger.ERROR_LIVE_PACKAGE_NOT_INSTALLED);
    }

    @Test
    public void testOnRestore_noCropHints() throws Exception {
        testParseCropHints(new SparseArray<>(), new SparseArray<>());
    }

    @Test
    @DisableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHint() throws Exception {
        SparseArray<Rect> testCropHints = new SparseArray<>();
        testCropHints.put(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(1, 2, 3, 4));
        testParseCropHints(new SparseArray<>(), null);
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHintPortrait_sameDimensions() throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(2000, 2000),
                /* sourceDeviceDimensions */ new Point(1000, 2000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(1000, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(350, 80, 1850, 1950)),

                // When both devices have the same dimensions, the crop hints should not change.
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(350, 80, 1850, 1950))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHintPortrait_widerTargetDevice_noParallax()
            throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(2000, 2000),
                /* sourceDeviceDimensions */ new Point(800, 2000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(1000, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(600, 0, 1400, 2000)),

                // Here we should be adding 100px on both sides to match the new aspect ratio.
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(500, 0, 1500, 2000))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHintPortrait_widerTargetDevice_ltr() throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(3000, 3000),
                /* sourceDeviceDimensions */ new Point(800, 2000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(1000, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(600, 0, 1800, 2000)),

                // The crop without parallax of the source device is (600, 0, 1400, 2000), and there
                // is 400px (50% of the width) for parallax. The crop without parallax of the target
                // device should be (500, 0, 1500, 2000), to preserve the same center and match the
                // new aspect ratio. Then we need to add 500px (50% of the width) to the right
                // (since LTR layout) leading to the expected crop (500, 0, 2000, 2000).
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(500, 0, 2000, 2000))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHintPortrait_widerTargetDevice_rtl() throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(2000, 2000),
                /* sourceDeviceDimensions */ new Point(800, 2000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(1000, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ true,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(600, 0, 1800, 2000)),

                // Similar to the case singleCropHintPortrait_widerTargetDevice_ltr, but since the
                // layout is RTL, we add 100px to the right and 200px to the left, not the opposite.
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(400, 0, 1900, 2000))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHintPortrait_narrowerTargetDevice_noParallax()
            throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(1000, 1000),
                /* sourceDeviceDimensions */ new Point(1200, 2000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(1000, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(200, 50, 680, 850)),

                // Here we need to enlarge the crop vertically to match the new aspect ratio. We
                // need to add 160px of height in total. Since there are only 50px remaining at the
                // top, we add them, and 110px at the bottom.
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(200, 0, 680, 960))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHintPortrait_narrowerTargetDevice_ltr() throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(1500, 1000),
                /* sourceDeviceDimensions */ new Point(1000, 2000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(800, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(100, 0, 850, 1000)),

                // Here we have no room to enlarge the crop vertically, so we need to shrink it
                // horizontally to match the new aspect ratio. Since the layout is LTR, the crop
                // without parallax is (100, 0, 600, 1000), and the parallax amount is 50%. The new
                // crop without parallax is (150, 0, 550, 1000) in order to keep the same center and
                // match the new aspect ratio. Then we add 50% of the width for parallax to the
                // right (since LTR), which leads us to the expected crop (150, 0, 750, 1000).
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(150, 0, 750, 1000))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHintPortrait_narrowerTargetDevice_rtl() throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(1000, 1000),
                /* sourceDeviceDimensions */ new Point(900, 1800),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(800, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ true,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(0, 0, 900, 900)),

                // With RTL layout the initial crop without parallax is (450, 0, 900, 900), and the
                // parallax amount is 100%. We first add the 100px of height we have available to
                // try to match the new aspect ratio. Our crop becomes 450x1000 which is still too
                // wide: we need to remove 50px of width. To keep the same center our new crop
                // without parallax becomes (475, 0, 875, 100). After adding back the 100% (400px)
                // of parallax to the left, we get the expected crop (75, 0, 875, 1000).
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(75, 0, 875, 1000))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_singleCropHintPortrait_doesNotAddTinyParallax() throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(601, 1000),
                /* sourceDeviceDimensions */ new Point(800, 2000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(1200, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(100, 0, 600, 1000)),

                // On the original device, crop without parallax is (100, 0, 500, 1000) and there
                // is 100px of width for parallax. To match the new device aspect ratio, add 100px
                // of width to both sides of the crop without parallax, leading to a new crop
                // without parallax of (0, 0, 600, 1000). Then, we would like to add back the
                // parallax but have only 1px of width remaining. Since 1px is too small and we
                // do not want to have tiny amounts of parallax on our wallpaper, we should not add
                // any parallax at all, and the expected crop should remain (0, 0, 600, 1000).
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(0, 0, 600, 1000))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_smallDevice_landscapeCropHintNotRestored() throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(1000, 1000),
                /* sourceDeviceDimensions */ new Point(1800, 900),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(800, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ true,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_LANDSCAPE, new Rect(0, 0, 900, 900)),

                // The landscape crop should be discarded if a device doesn't have a large screen.
                /* expectedCropHints */
                Map.of()
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_largeScreen_landscapeCropHintRestored() throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(2000, 2000),
                /* sourceDeviceDimensions */ new Point(1000, 2000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(2000, 1000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ true,

                /* sourceCropHints */
                Map.of(WallpaperManager.ORIENTATION_LANDSCAPE, new Rect(0, 500, 2000, 1500)),

                // The landscape crop should be kept if a device has a large screen.
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_LANDSCAPE, new Rect(0, 500, 2000, 1500))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_portraitAndLandscapeCrops_smallScreen_restoresPortraitOnly()
            throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(2000, 2000),
                /* sourceDeviceDimensions */ new Point(2000, 1000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(1000, 2000),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(
                        WallpaperManager.ORIENTATION_PORTRAIT, new Rect(0, 0, 1500, 2000),
                        WallpaperManager.ORIENTATION_LANDSCAPE, new Rect(500, 200, 1500, 700)),

                // The landscape crop should be discarded since the target device doesn't have a
                // large screen. The portrait crop should be unchanged since the source and target
                // devices have the same dimensions.
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(0, 0, 1500, 2000))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_portraitAndLandscapeCrops_largeScreen_restoresBothCrops()
            throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(600, 500),
                /* sourceDeviceDimensions */ new Point(500, 1000),
                /* sourceDeviceSecondaryDimensions */ null,
                /* targetDeviceDimensions */ new Point(1000, 2500),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ true,

                /* sourceCropHints */
                Map.of(
                        WallpaperManager.ORIENTATION_PORTRAIT, new Rect(0, 0, 360, 480),
                        WallpaperManager.ORIENTATION_LANDSCAPE, new Rect(30, 190, 470, 410)),

                // Since the target device has a large screen, no crops should be discarded, and
                // each crop should be adjusted if needed.
                /* expectedCropHints */
                Map.of(
                        // The source crop without parallax is 240x480, with a 50% parallax amount.
                        // We first add the 20px of height we have to try to match the new aspect
                        // ratio. Our crop becomes 240x500 which is still too wide: we need to
                        // remove 40px of width. To keep the same center our new crop without
                        // parallax becomes (20, 0, 220, 500). After adding back the 50% (100px) of
                        // parallax to the left, we get the expected crop (20, 0, 320, 500).
                        WallpaperManager.ORIENTATION_PORTRAIT, new Rect(20, 0, 320, 500),

                        // The source crop is 440x220 and has no parallax. Similarly to the portrait
                        // case just above (but in rotated), we first add all the width we can: 30px
                        // on both sides, since 30px is the max we can add to the left. It's not
                        // enough, so we remove height (10px on both sides) to match the new aspect
                        // ratio, leading us to the crop (0, 200, 500, 400).
                        WallpaperManager.ORIENTATION_LANDSCAPE, new Rect(0, 200, 500, 400))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_foldableToFoldable_portraitAndSquarePortraitCrops_noParallax()
            throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(1000, 1000),
                /* sourceDeviceDimensions */ new Point(500, 1000),
                /* sourceDeviceSecondaryDimensions */ new Point(900, 1000),
                /* targetDeviceDimensions */ new Point(1000, 2500),
                /* targetDeviceSecondaryDimensions */ new Point(2000, 2500),
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(
                        WallpaperManager.ORIENTATION_PORTRAIT, new Rect(250, 0, 750, 1000),
                        WallpaperManager.ORIENTATION_SQUARE_PORTRAIT, new Rect(0, 0, 400, 450)),

                // Since the target device has both portrait and square orientations, both crops
                // should be kept and adjusted.
                /* expectedCropHints */
                Map.of(
                        // Since there is no room to add height to the crop, remove width on both
                        // sides of the crop to match the new device aspect ratio.
                        WallpaperManager.ORIENTATION_PORTRAIT, new Rect(300, 0, 700, 1000),

                        // Add height to the crop (to the bottom since there is no room to the top)
                        // to match the new device aspect ratio.
                        WallpaperManager.ORIENTATION_SQUARE_PORTRAIT, new Rect(0, 0, 400, 500))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_foldableToHandheld_portraitAndSquarePortraitCrops_rtl()
            throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(100, 100),
                /* sourceDeviceDimensions */ new Point(500, 1000),
                /* sourceDeviceSecondaryDimensions */ new Point(900, 1000),
                /* targetDeviceDimensions */ new Point(1000, 2500),
                /* targetDeviceSecondaryDimensions */ null,
                /* isTargetDeviceRtl */ true,
                /* isTargetDeviceLargeScreen */ false,

                /* sourceCropHints */
                Map.of(
                        WallpaperManager.ORIENTATION_PORTRAIT, new Rect(20, 0, 95, 100),
                        WallpaperManager.ORIENTATION_SQUARE_PORTRAIT, new Rect(10, 10, 90, 90)),

                // Only keep the portrait orientation since the target device has no square screen.
                // The source portrait crop without parallax is (45, 0, 95, 100) with a 50% parallax
                // amount. The new crop without parallax should be 40x100 to match the target aspect
                // ratio. There is no room to add height to the crop, so remove 5px on each side of
                // the crop horizontally. The new crop without parallax becomes (50, 0, 90, 100).
                // After adding back the 50% parallax, we get the expected crop (30, 0, 90, 100).
                /* expectedCropHints */
                Map.of(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(30, 0, 90, 100))
        );
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_foldableToFoldable_portraitAndSquareLandscapeCrops_ltr()
            throws Exception {
        testRestoredCrops(
                /* bitmapDimensions */ new Point(1000, 1000),
                /* sourceDeviceDimensions */ new Point(500, 1000),
                /* sourceDeviceSecondaryDimensions */ new Point(1100, 1000),
                /* targetDeviceDimensions */ new Point(1000, 2500),
                /* targetDeviceSecondaryDimensions */ new Point(2000, 2500),
                /* isTargetDeviceRtl */ false,
                /* isTargetDeviceLargeScreen */ true,

                /* sourceCropHints */
                Map.of(
                        WallpaperManager.ORIENTATION_PORTRAIT, new Rect(200, 0, 950, 1000),
                        WallpaperManager.ORIENTATION_SQUARE_LANDSCAPE, new Rect(
                                100, 300, 980, 700)),

                // No crop should be discarded since the target device has both portrait and square
                // dimensions.
                /* expectedCropHints */
                Map.of(
                        // The source portrait crop without parallax is (200, 0, 700, 1000) with a
                        // 50% parallax amount. There is no room to add height to the crop, so
                        // remove 50px on each side of the crop horizontally to match the new aspect
                        // ratio. The new crop without parallax is (250, 0, 650, 1000). After adding
                        // back the 50% parallax, we get the expected crop (250, 0, 850, 1000).
                        WallpaperManager.ORIENTATION_PORTRAIT, new Rect(250, 0, 850, 1000),

                        // The source square landscape crop without parallax is (100, 300, 540, 700)
                        // with a 100% parallax amount. The target device has square landscape
                        // dimensions of 2500x2000 which is wider than the source device. To match
                        // the new aspect ratio, we add 30px to both sides of the crop, and get a
                        // new crop without parallax of (70, 300, 570, 700). There is not enough
                        // width to add back all the 100% of parallax, but there is enough to add a
                        // decent amount of parallax (86%). Use all the remaining width to add these
                        // 86% of parallax.
                        WallpaperManager.ORIENTATION_SQUARE_LANDSCAPE, new Rect(70, 300, 1000, 700))
        );
    }

    @Test
    @DisableFlags(Flags.FLAG_FIX_WALLPAPER_CROPS_ON_RESTORE)
    public void testOnRestore_multipleCropHints() throws Exception {
        SparseArray<Rect> testCropHints = new SparseArray<>();
        testCropHints.put(WallpaperManager.ORIENTATION_PORTRAIT, new Rect(1, 2, 3, 4));
        testCropHints.put(WallpaperManager.ORIENTATION_SQUARE_PORTRAIT, new Rect(5, 6, 7, 8));
        testCropHints.put(WallpaperManager.ORIENTATION_SQUARE_LANDSCAPE, new Rect(9, 10, 11, 12));
        testParseCropHints(testCropHints, null);
    }

    private void testRestoredCrops(
            Point bitmapDimensions,
            Point sourceDeviceDimensions,
            @Nullable Point sourceDeviceSecondaryDimensions,
            Point targetDeviceDimensions,
            @Nullable Point targetDeviceSecondaryDimensions,
            boolean isTargetDeviceRtl,
            boolean isTargetDeviceLargeScreen,
            Map<Integer, Rect> sourceCropHints,
            Map<Integer, Rect> expectedCropHints
    ) throws Exception {
        setupFakeBitmapSize(bitmapDimensions);
        setupMockDisplays(
                targetDeviceDimensions,
                targetDeviceSecondaryDimensions,
                isTargetDeviceLargeScreen);
        setupRtl(isTargetDeviceRtl);
        mockWallpaperBackupDeviceInfoStage(sourceDeviceDimensions, sourceDeviceSecondaryDimensions);

        SparseArray<Rect> source = new SparseArray<>();
        sourceCropHints.forEach(source::put);

        SparseArray<Rect> expected = new SparseArray<>();
        expectedCropHints.forEach(expected::put);

        testParseCropHints(source, expected);
    }

    private void testParseCropHints(
            SparseArray<Rect> testCropHints,
            SparseArray<Rect> expectedCropHints
    ) throws Exception {
        assumeTrue(multiCrop());
        mockRestoredStaticWallpaperFile(testCropHints);
        mockStagedWallpaperFile(SYSTEM_WALLPAPER_STAGE);
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.RESTORE);

        mWallpaperBackupAgent.onRestoreFinished();

        verify(mWallpaperManager).setStreamWithCrops(
                any(InputStream.class), mCropHintsCaptor.capture(), eq(true), anyInt());
        SparseArray<Rect> capturedCropHints = mCropHintsCaptor.getValue();

        if (!fixWallpaperCropsOnRestore()) {
            assertThat(testCropHints.contentEquals(capturedCropHints)).isTrue();
            return;
        }

        assertWithMessage("Received unexpected crop hints. "
                + "Expected: " + expectedCropHints + ". Actual: " + capturedCropHints)
                .that(capturedCropHints.contentEquals(expectedCropHints))
                .isTrue();
    }

    private void mockCurrentWallpaperIds(int systemWallpaperId, int lockWallpaperId) {
        when(mWallpaperManager.getWallpaperId(eq(FLAG_SYSTEM))).thenReturn(systemWallpaperId);
        when(mWallpaperManager.getWallpaperId(eq(FLAG_LOCK))).thenReturn(lockWallpaperId);
    }

    private File createTemporaryFileWithContentString(String contents) throws Exception {
        File file = mTemporaryFolder.newFile();
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(contents.getBytes());
        }
        return file;
    }

    private void assertFileContentEquals(File file, String expected) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            assertThat(new String(inputStream.readAllBytes())).isEqualTo(expected);
        }
    }

    private Optional<File> getBackedUpFileOptional(String fileName) {
        return mWallpaperBackupAgent.mBackedUpFiles.stream().filter(
                file -> file.getName().equals(fileName)).findFirst();
    }

    private void mockWallpaperInfoFileWithContents(String contents) throws Exception {
        File fakeInfoFile = createTemporaryFileWithContentString(contents);
        when(mWallpaperManager.getWallpaperInfoFile()).thenReturn(
                ParcelFileDescriptor.open(fakeInfoFile, MODE_READ_ONLY));
    }

    private void mockSystemWallpaperFileWithContents(String contents) throws Exception {
        File fakeSystemWallpaperFile = createTemporaryFileWithContentString(contents);
        when(mWallpaperManager.getWallpaperFile(eq(FLAG_SYSTEM), /* cropped = */
                eq(false))).thenReturn(
                ParcelFileDescriptor.open(fakeSystemWallpaperFile, MODE_READ_ONLY));
    }

    private void mockLockWallpaperFileWithContents(String contents) throws Exception {
        File fakeLockWallpaperFile = createTemporaryFileWithContentString(contents);
        when(mWallpaperManager.getWallpaperFile(eq(FLAG_LOCK), /* cropped = */
                eq(false))).thenReturn(
                ParcelFileDescriptor.open(fakeLockWallpaperFile, MODE_READ_ONLY));
    }

    private void mockStagedWallpaperFile(String location) throws Exception {
        File wallpaperFile = new File(mContext.getFilesDir(), location);
        wallpaperFile.createNewFile();
    }

    private void mockWallpaperBackupDeviceInfoStage(Point dimensions, Point secondaryDimensions)
            throws Exception {
        File infoFile = new File(mContext.getFilesDir(), WALLPAPER_BACKUP_DEVICE_INFO_STAGE);
        infoFile.createNewFile();
        mWallpaperBackupAgent.writeDeviceInfoToFile(infoFile, dimensions, secondaryDimensions);
    }

    private void mockRestoredLiveWallpaperFile() throws Exception {
        File wallpaperFile = new File(mContext.getFilesDir(), WALLPAPER_INFO_STAGE);
        wallpaperFile.createNewFile();
        FileOutputStream fstream = new FileOutputStream(wallpaperFile, false);
        TypedXmlSerializer out = Xml.resolveSerializer(fstream);
        out.startDocument(null, true);
        out.startTag(null, "wp");
        out.attribute(null, "component",
                getFakeWallpaperInfo().getComponent().flattenToShortString());
        out.endTag(null, "wp");
        out.endDocument();
        fstream.flush();
        FileUtils.sync(fstream);
        fstream.close();
    }

    private void mockRestoredStaticWallpaperFile(SparseArray<Rect> crops) throws Exception {
        File wallpaperFile = new File(mContext.getFilesDir(), WALLPAPER_INFO_STAGE);
        wallpaperFile.createNewFile();
        FileOutputStream fstream = new FileOutputStream(wallpaperFile, false);
        TypedXmlSerializer out = Xml.resolveSerializer(fstream);
        out.startDocument(null, true);
        out.startTag(null, "wp");
        for (int i = 0; i < crops.size(); i++) {
            String orientation = switch (crops.keyAt(i)) {
                case WallpaperManager.ORIENTATION_PORTRAIT -> "Portrait";
                case WallpaperManager.ORIENTATION_LANDSCAPE -> "Landscape";
                case WallpaperManager.ORIENTATION_SQUARE_PORTRAIT -> "SquarePortrait";
                case WallpaperManager.ORIENTATION_SQUARE_LANDSCAPE -> "SquareLandscape";
                default -> throw new IllegalArgumentException("Invalid orientation");
            };
            Rect rect = crops.valueAt(i);
            out.attributeInt(null, "cropLeft" + orientation, rect.left);
            out.attributeInt(null, "cropTop" + orientation, rect.top);
            out.attributeInt(null, "cropRight" + orientation, rect.right);
            out.attributeInt(null, "cropBottom" + orientation, rect.bottom);
        }
        out.endTag(null, "wp");
        out.endDocument();
        fstream.flush();
        FileUtils.sync(fstream);
        fstream.close();
    }

    private void setupMockDisplays(
            Point displaySize,
            Point secondaryDisplaySize,
            boolean isLargeScreen
    ) {
        mMockWallpaperDisplayInfos.clear();
        mMockWallpaperDisplayInfos.add(new WallpaperDisplayInfo(displaySize, isLargeScreen));
        if (secondaryDisplaySize != null) {
            mMockWallpaperDisplayInfos.add(
                    new WallpaperDisplayInfo(secondaryDisplaySize, isLargeScreen));
        }
        mShouldMockDisplays = true;
    }

    private void setupRtl(boolean rtl) {
        Locale.setDefault(rtl ? Locale.of("ar") : Locale.US);
    }

    private void setupFakeBitmapSize(Point fakeBitmapSize) {
        mMockBitmapSize = fakeBitmapSize;
        mShouldMockBitmapSize = true;
    }

    private WallpaperInfo getFakeWallpaperInfo() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        Intent intent = new Intent(WallpaperService.SERVICE_INTERFACE);
        intent.setPackage("com.android.wallpaperbackup.tests");
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> result = pm.queryIntentServices(intent, PackageManager.GET_META_DATA);
        assertEquals(1, result.size());
        ResolveInfo info = result.get(0);
        return new WallpaperInfo(context, info);
    }

    private void markAgentAsOverQuota() throws Exception {
        // Create over quota file to indicate the last backup was over quota
        File quotaFile = new File(mContext.getFilesDir(), WallpaperBackupAgent.QUOTA_SENTINEL);
        quotaFile.createNewFile();

        // Now redo the setup of the agent to pick up the over quota
        mWallpaperBackupAgent.onCreate(USER_HANDLE, BackupAnnotations.BackupDestination.CLOUD,
                BackupAnnotations.OperationType.BACKUP);
    }

    private static DataTypeResult getLoggingResult(String dataType, List<DataTypeResult> results) {
        for (DataTypeResult result : results) {
            if ((result.getDataType()).equals(dataType)) {
                return result;
            }
        }
        return null;
    }

    private class IsolatedWallpaperBackupAgent extends WallpaperBackupAgent {
        List<File> mBackedUpFiles = new ArrayList<>();
        PackageMonitor mWallpaperPackageMonitor;
        boolean mIsDeviceInRestore = false;
        boolean mPackageExists = false;
        int mGetPackageMonitorCallCount = 0;

        @Override
        protected void backupFile(File file, FullBackupDataOutput data) {
            mBackedUpFiles.add(file);
        }

        @Override
        boolean servicePackageExists(ComponentName comp) {
            return mPackageExists;
        }

        @Override
        boolean isDeviceInRestore() {
            return mIsDeviceInRestore;
        }

        @Override
        PackageMonitor getWallpaperPackageMonitor(ComponentName componentName,
                WallpaperDescription description, int which) {
            mGetPackageMonitorCallCount++;
            mWallpaperPackageMonitor = super.getWallpaperPackageMonitor(componentName, description,
                    which);
            return mWallpaperPackageMonitor;
        }

        @Override
        public Context getBaseContext() {
            return mMockContext;
        }

        @Override List<WallpaperDisplayInfo> getInternalDisplays() {
            return mShouldMockDisplays ? mMockWallpaperDisplayInfos : super.getInternalDisplays();
        }

        @Override Point getBitmapSize(File stage) throws IOException {
            return mShouldMockBitmapSize ? mMockBitmapSize : super.getBitmapSize(stage);
        }
    }
}
