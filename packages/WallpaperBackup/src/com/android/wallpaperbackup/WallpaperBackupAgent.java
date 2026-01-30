/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static android.app.WallpaperManager.ORIENTATION_LANDSCAPE;
import static android.app.WallpaperManager.ORIENTATION_UNKNOWN;
import static android.app.WallpaperManager.getOrientation;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

import static com.android.wallpaperbackup.WallpaperEventLogger.ERROR_INELIGIBLE;
import static com.android.wallpaperbackup.WallpaperEventLogger.ERROR_NO_METADATA;
import static com.android.wallpaperbackup.WallpaperEventLogger.ERROR_NO_WALLPAPER;
import static com.android.wallpaperbackup.WallpaperEventLogger.ERROR_QUOTA_EXCEEDED;
import static com.android.window.flags.Flags.multiCrop;

import android.annotation.Nullable;
import android.app.AppGlobals;
import android.app.WallpaperManager;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.app.backup.BackupRestoreEventLogger.BackupRestoreError;
import android.app.backup.FullBackupDataOutput;
import android.app.wallpaper.WallpaperDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.View;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Backs up and restores wallpaper and metadata related to it.
 *
 * This agent has its own package because it does full backup as opposed to SystemBackupAgent
 * which does key/value backup.
 *
 * This class stages wallpaper files for backup by copying them into its own directory because of
 * the following reasons:
 *
 * <ul>
 *     <li>Non-system users don't have permission to read the directory that the system stores
 *     the wallpaper files in</li>
 *     <li>{@link BackupAgent} enforces that backed up files must live inside the package's
 *     {@link Context#getFilesDir()}</li>
 * </ul>
 *
 * There are 3 files to back up:
 * <ul>
 *     <li>The "wallpaper info"  file which contains metadata like the crop applied to the
 *     wallpaper or the live wallpaper component name.</li>
 *     <li>The "system" wallpaper file.</li>
 *     <li>An optional "lock" wallpaper, which is shown on the lockscreen instead of the system
 *     wallpaper if set.</li>
 * </ul>
 *
 * On restore, the metadata file is parsed and {@link WallpaperManager} APIs are used to set the
 * wallpaper. Note that if there's a live wallpaper, the live wallpaper package name will be
 * part of the metadata file and the wallpaper will be applied when the package it's installed.
 */
public class WallpaperBackupAgent extends BackupAgent {
    private static final String TAG = "WallpaperBackup";
    private static final boolean DEBUG = false;

    // Names of our local-data stage files
    @VisibleForTesting
    static final String SYSTEM_WALLPAPER_STAGE = "wallpaper-stage";
    @VisibleForTesting
    static final String LOCK_WALLPAPER_STAGE = "wallpaper-lock-stage";
    @VisibleForTesting
    static final String WALLPAPER_INFO_STAGE = "wallpaper-info-stage";
    @VisibleForTesting
    static final String WALLPAPER_BACKUP_DEVICE_INFO_STAGE = "wallpaper-backup-device-info-stage";
    static final String EMPTY_SENTINEL = "empty";
    static final String QUOTA_SENTINEL = "quota";
    // Shared preferences constants.
    static final String PREFS_NAME = "wbprefs.xml";
    static final String SYSTEM_GENERATION = "system_gen";
    static final String LOCK_GENERATION = "lock_gen";

    static final String DEVICE_CONFIG_WIDTH = "device_config_width";

    static final String DEVICE_CONFIG_HEIGHT = "device_config_height";

    static final String DEVICE_CONFIG_SECONDARY_WIDTH = "device_config_secondary_width";

    static final String DEVICE_CONFIG_SECONDARY_HEIGHT = "device_config_secondary_height";

    static final float DEFAULT_ACCEPTABLE_PARALLAX = 0.2f;

    // If this file exists, it means we exceeded our quota last time
    private File mQuotaFile;
    private boolean mQuotaExceeded;

    private WallpaperManager mWallpaperManager;
    private WallpaperEventLogger mEventLogger;
    private BackupManager mBackupManager;

    private boolean mSystemHasLiveComponent;
    private boolean mLockHasLiveComponent;

    private DisplayManager mDisplayManager;

    @Override
    public void onCreate() {
        if (DEBUG) {
            Slog.v(TAG, "onCreate()");
        }

        mWallpaperManager = getSystemService(WallpaperManager.class);

        mQuotaFile = new File(getFilesDir(), QUOTA_SENTINEL);
        mQuotaExceeded = mQuotaFile.exists();
        if (DEBUG) {
            Slog.v(TAG, "quota file " + mQuotaFile.getPath() + " exists=" + mQuotaExceeded);
        }

        mBackupManager = new BackupManager(getBaseContext());
        mEventLogger = new WallpaperEventLogger(mBackupManager, /* wallpaperAgent */ this);

        mDisplayManager = getSystemService(DisplayManager.class);
    }

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        try {
            // We always back up this 'empty' file to ensure that the absence of
            // storable wallpaper imagery still produces a non-empty backup data
            // stream, otherwise it'd simply be ignored in preflight.
            final File empty = new File(getFilesDir(), EMPTY_SENTINEL);
            if (!empty.exists()) {
                FileOutputStream touch = new FileOutputStream(empty);
                touch.close();
            }
            backupFile(empty, data);

            SharedPreferences sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            // Check the IDs of the wallpapers that we backed up last time. If they haven't
            // changed, we won't re-stage them for backup and use the old staged versions to avoid
            // disk churn.
            final int lastSysGeneration = sharedPrefs.getInt(SYSTEM_GENERATION, /* defValue= */ -1);
            final int lastLockGeneration = sharedPrefs.getInt(LOCK_GENERATION, /* defValue= */ -1);

            final int deviceConfigWidth = sharedPrefs.getInt(
                    DEVICE_CONFIG_WIDTH, /* defValue= */ -1);
            final int deviceConfigHeight = sharedPrefs.getInt(
                    DEVICE_CONFIG_HEIGHT, /* defValue= */ -1);
            final int deviceConfigSecondaryWidth = sharedPrefs.getInt(
                    DEVICE_CONFIG_SECONDARY_WIDTH, /* defValue= */ -1);
            final int deviceConfigSecondaryHeight = sharedPrefs.getInt(
                    DEVICE_CONFIG_SECONDARY_HEIGHT, /* defValue= */ -1);

            final int sysGeneration = mWallpaperManager.getWallpaperId(FLAG_SYSTEM);
            final int lockGeneration = mWallpaperManager.getWallpaperId(FLAG_LOCK);
            final boolean sysChanged = (sysGeneration != lastSysGeneration);
            final boolean lockChanged = (lockGeneration != lastLockGeneration);

            if (DEBUG) {
                Slog.v(TAG, "sysGen=" + sysGeneration + " : sysChanged=" + sysChanged);
                Slog.v(TAG, "lockGen=" + lockGeneration + " : lockChanged=" + lockChanged);
            }

            // Due to the way image vs live wallpaper backup logic is intermingled, for logging
            // purposes first check if we have live components for each wallpaper to avoid
            // over-reporting errors.
            mSystemHasLiveComponent = mWallpaperManager.getWallpaperInfo(FLAG_SYSTEM) != null;
            mLockHasLiveComponent = mWallpaperManager.getWallpaperInfo(FLAG_LOCK) != null;

            // performing backup of each file based on order of importance
            backupWallpaperInfoFile(/* sysOrLockChanged= */ sysChanged || lockChanged, data);
            backupSystemWallpaperFile(sharedPrefs, sysChanged, sysGeneration, data);
            backupLockWallpaperFileIfItExists(sharedPrefs, lockChanged, lockGeneration, data);

            final boolean isDeviceConfigChanged = isDeviceConfigChanged(deviceConfigWidth,
                    deviceConfigHeight, deviceConfigSecondaryWidth, deviceConfigSecondaryHeight);

            backupDeviceInfoFile(sharedPrefs, isDeviceConfigChanged, data);
        } catch (Exception e) {
            Slog.e(TAG, "Unable to back up wallpaper", e);
            mEventLogger.onBackupException(e);
        } finally {
            // Even if this time we had to back off on attempting to store the lock image
            // due to exceeding the data quota, try again next time.  This will alternate
            // between "try both" and "only store the primary image" until either there
            // is no lock image to store, or the quota is raised, or both fit under the
            // quota.
            mQuotaFile.delete();
        }
    }

    private boolean isDeviceConfigChanged(int width, int height, int secondaryWidth,
            int secondaryHeight) {
        Point currentDimensions = getScreenDimensions();
        Point smallerDisplay = getSmallerDisplaySizeIfExists();
        Point currentSecondaryDimensions = smallerDisplay != null ? smallerDisplay :
                new Point(0, 0);

        return (currentDimensions.x != width
                || currentDimensions.y != height
                || currentSecondaryDimensions.x != secondaryWidth
                || currentSecondaryDimensions.y != secondaryHeight);
    }

    /**
     * This method backs up the device dimension information. The device data will always get
     * overwritten when triggering a backup
     */
    private void backupDeviceInfoFile(SharedPreferences sharedPrefs, boolean isDeviceConfigChanged,
            FullBackupDataOutput data)
            throws IOException {
        final File deviceInfoStage = new File(getFilesDir(), WALLPAPER_BACKUP_DEVICE_INFO_STAGE);

        if (isDeviceConfigChanged || (fixWallpaperCropsOnRestore() && !deviceInfoStage.exists())) {
            deviceInfoStage.createNewFile();

            // save the dimensions of the device with xml formatting
            Point dimensions = getScreenDimensions();
            Point smallerDisplay = getSmallerDisplaySizeIfExists();
            writeDeviceInfoToFile(deviceInfoStage, dimensions, smallerDisplay);

            Point secondaryDimensions = smallerDisplay != null ? smallerDisplay : new Point(0, 0);

            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putInt(DEVICE_CONFIG_WIDTH, dimensions.x);
            editor.putInt(DEVICE_CONFIG_HEIGHT, dimensions.y);
            editor.putInt(DEVICE_CONFIG_SECONDARY_WIDTH, secondaryDimensions.x);
            editor.putInt(DEVICE_CONFIG_SECONDARY_HEIGHT, secondaryDimensions.y);
            editor.apply();
        }
        if (DEBUG) Slog.v(TAG, "Storing device dimension data");
        backupFile(deviceInfoStage, data);
    }

    /**
     * Write device info to file.
     * @param smallerDisplay Always null if there is only one display. If there are two displays,
     *                       dimensions of the smallest one (in terms of area in pixels).
     */
    @VisibleForTesting void writeDeviceInfoToFile(
            File deviceInfoStage,
            Point dimensions,
            @Nullable Point smallerDisplay
    ) throws IOException {
        FileOutputStream fstream = new FileOutputStream(deviceInfoStage, false);
        TypedXmlSerializer out = Xml.resolveSerializer(fstream);
        out.startDocument(null, true);
        out.startTag(null, "dimensions");

        out.startTag(null, "width");
        out.text(String.valueOf(dimensions.x));
        out.endTag(null, "width");

        out.startTag(null, "height");
        out.text(String.valueOf(dimensions.y));
        out.endTag(null, "height");

        if (smallerDisplay != null) {
            out.startTag(null, "secondarywidth");
            out.text(String.valueOf(smallerDisplay.x));
            out.endTag(null, "secondarywidth");

            out.startTag(null, "secondaryheight");
            out.text(String.valueOf(smallerDisplay.y));
            out.endTag(null, "secondaryheight");
        }

        out.endTag(null, "dimensions");
        out.endDocument();
        fstream.flush();
        FileUtils.sync(fstream);
        fstream.close();

    }

    private void backupWallpaperInfoFile(boolean sysOrLockChanged, FullBackupDataOutput data)
            throws IOException {
        final ParcelFileDescriptor wallpaperInfoFd = mWallpaperManager.getWallpaperInfoFile();

        if (wallpaperInfoFd == null) {
            Slog.w(TAG, "Wallpaper metadata file doesn't exist");
            // If we have live components, getting the file to back up somehow failed, so log it
            // as an error.
            if (mSystemHasLiveComponent) {
                mEventLogger.onSystemLiveWallpaperBackupFailed(ERROR_NO_METADATA);
            }
            if (mLockHasLiveComponent) {
                mEventLogger.onLockLiveWallpaperBackupFailed(ERROR_NO_METADATA);
            }
            return;
        }

        final File infoStage = new File(getFilesDir(), WALLPAPER_INFO_STAGE);

        if (sysOrLockChanged || !infoStage.exists()) {
            if (DEBUG) Slog.v(TAG, "New wallpaper configuration; copying");
            copyFromPfdToFileAndClosePfd(wallpaperInfoFd, infoStage);
        }

        if (DEBUG) Slog.v(TAG, "Storing wallpaper metadata");
        backupFile(infoStage, data);

        // We've backed up the info file which contains the live component, so log it as success
        if (mSystemHasLiveComponent) {
            mEventLogger.onSystemLiveWallpaperBackedUp(
                    mWallpaperManager.getWallpaperInfo(FLAG_SYSTEM));
        }
        if (mLockHasLiveComponent) {
            mEventLogger.onLockLiveWallpaperBackedUp(mWallpaperManager.getWallpaperInfo(FLAG_LOCK));
        }
    }

    private void backupSystemWallpaperFile(SharedPreferences sharedPrefs,
            boolean sysChanged, int sysGeneration, FullBackupDataOutput data) throws IOException {
        if (!mWallpaperManager.isWallpaperBackupEligible(FLAG_SYSTEM)) {
            Slog.d(TAG, "System wallpaper ineligible for backup");
            logSystemImageErrorIfNoLiveComponent(ERROR_INELIGIBLE);
            return;
        }

        final ParcelFileDescriptor systemWallpaperImageFd = mWallpaperManager.getWallpaperFile(
                FLAG_SYSTEM,
                /* getCropped= */ false);

        if (systemWallpaperImageFd == null) {
            Slog.w(TAG, "System wallpaper doesn't exist");
            logSystemImageErrorIfNoLiveComponent(ERROR_NO_WALLPAPER);
            return;
        }

        final File imageStage = new File(getFilesDir(), SYSTEM_WALLPAPER_STAGE);

        if (sysChanged || !imageStage.exists()) {
            if (DEBUG) Slog.v(TAG, "New system wallpaper; copying");
            copyFromPfdToFileAndClosePfd(systemWallpaperImageFd, imageStage);
        }

        if (DEBUG) Slog.v(TAG, "Storing system wallpaper image");
        backupFile(imageStage, data);
        sharedPrefs.edit().putInt(SYSTEM_GENERATION, sysGeneration).apply();
        mEventLogger.onSystemImageWallpaperBackedUp();
    }

    private void logSystemImageErrorIfNoLiveComponent(@BackupRestoreError String error) {
        if (mSystemHasLiveComponent) {
            return;
        }
        mEventLogger.onSystemImageWallpaperBackupFailed(error);
    }

    private void backupLockWallpaperFileIfItExists(SharedPreferences sharedPrefs,
            boolean lockChanged, int lockGeneration, FullBackupDataOutput data) throws IOException {
        final File lockImageStage = new File(getFilesDir(), LOCK_WALLPAPER_STAGE);

        // This means there's no lock wallpaper set by the user.
        if (lockGeneration == -1) {
            if (lockChanged && lockImageStage.exists()) {
                if (DEBUG) Slog.v(TAG, "Removed lock wallpaper; deleting");
                lockImageStage.delete();
            }
            Slog.d(TAG, "No lockscreen wallpaper set, add nothing to backup");
            sharedPrefs.edit().putInt(LOCK_GENERATION, lockGeneration).apply();
            logLockImageErrorIfNoLiveComponent(ERROR_NO_WALLPAPER);
            return;
        }

        if (!mWallpaperManager.isWallpaperBackupEligible(FLAG_LOCK)) {
            Slog.d(TAG, "Lock screen wallpaper ineligible for backup");
            logLockImageErrorIfNoLiveComponent(ERROR_INELIGIBLE);
            return;
        }

        final ParcelFileDescriptor lockWallpaperFd = mWallpaperManager.getWallpaperFile(
                FLAG_LOCK, /* getCropped= */ false);

        // If we get to this point, that means lockGeneration != -1 so there's a lock wallpaper
        // set, but we can't find it.
        if (lockWallpaperFd == null) {
            Slog.w(TAG, "Lock wallpaper doesn't exist");
            logLockImageErrorIfNoLiveComponent(ERROR_NO_WALLPAPER);
            return;
        }

        if (mQuotaExceeded) {
            Slog.w(TAG, "Not backing up lock screen wallpaper. Quota was exceeded last time");
            logLockImageErrorIfNoLiveComponent(ERROR_QUOTA_EXCEEDED);
            return;
        }

        if (lockChanged || !lockImageStage.exists()) {
            if (DEBUG) Slog.v(TAG, "New lock wallpaper; copying");
            copyFromPfdToFileAndClosePfd(lockWallpaperFd, lockImageStage);
        }

        if (DEBUG) Slog.v(TAG, "Storing lock wallpaper image");
        backupFile(lockImageStage, data);
        sharedPrefs.edit().putInt(LOCK_GENERATION, lockGeneration).apply();
        mEventLogger.onLockImageWallpaperBackedUp();
    }

    private void logLockImageErrorIfNoLiveComponent(@BackupRestoreError String error) {
        if (mLockHasLiveComponent) {
            return;
        }
        mEventLogger.onLockImageWallpaperBackupFailed(error);
    }

    /**
     * Copies the contents of the given {@code pfd} to the given {@code file}.
     *
     * All resources used in the process including the {@code pfd} will be closed.
     */
    private static void copyFromPfdToFileAndClosePfd(ParcelFileDescriptor pfd, File file)
            throws IOException {
        try (ParcelFileDescriptor.AutoCloseInputStream inputStream =
                     new ParcelFileDescriptor.AutoCloseInputStream(pfd);
             FileOutputStream outputStream = new FileOutputStream(file)
        ) {
            FileUtils.copy(inputStream, outputStream);
        }
    }

    private static String readText(TypedXmlPullParser parser)
            throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    @VisibleForTesting
    // fullBackupFile is final, so we intercept backups here in tests.
    protected void backupFile(File file, FullBackupDataOutput data) {
        fullBackupFile(file, data);
    }

    @Override
    public void onQuotaExceeded(long backupDataBytes, long quotaBytes) {
        Slog.i(TAG, "Quota exceeded (" + backupDataBytes + " vs " + quotaBytes + ')');
        try (FileOutputStream f = new FileOutputStream(mQuotaFile)) {
            f.write(0);
        } catch (Exception e) {
            Slog.w(TAG, "Unable to record quota-exceeded: " + e.getMessage());
        }
    }

    // We use the default onRestoreFile() implementation that will recreate our stage files,
    // then post-process in onRestoreFinished() to apply the new wallpaper.
    @Override
    public void onRestoreFinished() {
        Slog.v(TAG, "onRestoreFinished()");
        final File filesDir = getFilesDir();
        final File infoStage = new File(filesDir, WALLPAPER_INFO_STAGE);
        final File imageStage = new File(filesDir, SYSTEM_WALLPAPER_STAGE);
        final File lockImageStage = new File(filesDir, LOCK_WALLPAPER_STAGE);
        final File deviceDimensionsStage = new File(filesDir, WALLPAPER_BACKUP_DEVICE_INFO_STAGE);
        boolean lockImageStageExists = lockImageStage.exists();

        try {
            // Parse the device dimensions of the source device
            Pair<Point, Point> sourceDeviceDimensions = parseDeviceDimensions(
                    deviceDimensionsStage);

            // First parse the live component name so that we know for logging if we care about
            // logging errors with the image restore.
            Pair<ComponentName, WallpaperDescription> wpService = parseWallpaperComponent(infoStage,
                    "wp");
            mSystemHasLiveComponent = wpService.first != null;

            Pair<ComponentName, WallpaperDescription> kwpService = parseWallpaperComponent(
                    infoStage, "kwp");
            mLockHasLiveComponent = kwpService.first != null;
            boolean separateLockWallpaper = mLockHasLiveComponent || lockImageStage.exists();

            // if there's no separate lock wallpaper, apply the system wallpaper to both screens.
            final int sysWhich = separateLockWallpaper ? FLAG_SYSTEM : FLAG_SYSTEM | FLAG_LOCK;

            // It is valid for the imagery to be absent; it means that we were not permitted
            // to back up the original image on the source device, or there was no user-supplied
            // wallpaper image present.
            if (lockImageStageExists) {
                restoreFromStage(lockImageStage, infoStage, "kwp", FLAG_LOCK,
                        sourceDeviceDimensions);
            }
            restoreFromStage(imageStage, infoStage, "wp", sysWhich, sourceDeviceDimensions);

            // And reset to the wallpaper service we should be using
            if (mLockHasLiveComponent) {
                updateWallpaperComponent(kwpService, FLAG_LOCK);
            }
            updateWallpaperComponent(wpService, sysWhich);
        } catch (Exception e) {
            Slog.e(TAG, "Unable to restore wallpaper: " + e.getMessage());
            mEventLogger.onRestoreException(e);
        } finally {
            Slog.v(TAG, "Restore finished; clearing backup bookkeeping");
            infoStage.delete();
            imageStage.delete();
            lockImageStage.delete();
            deviceDimensionsStage.delete();

            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putInt(SYSTEM_GENERATION, -1)
                    .putInt(LOCK_GENERATION, -1)
                    .commit();
        }
    }

    /**
     * This method parses the given file for the backed up device dimensions
     *
     * @param deviceDimensions the file which holds the device dimensions
     * @return the backed up device dimensions
     */
    private Pair<Point, Point> parseDeviceDimensions(File deviceDimensions) {
        int width = 0, height = 0, secondaryHeight = 0, secondaryWidth = 0;
        try {
            TypedXmlPullParser parser = Xml.resolvePullParser(
                    new FileInputStream(deviceDimensions));

            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }

                String name = parser.getName();

                switch (name) {
                    case "width":
                        String widthText = readText(parser);
                        width = Integer.valueOf(widthText);
                        break;

                    case "height":
                        String textHeight = readText(parser);
                        height = Integer.valueOf(textHeight);
                        break;

                    case "secondarywidth":
                        String secondaryWidthText = readText(parser);
                        secondaryWidth = Integer.valueOf(secondaryWidthText);
                        break;

                    case "secondaryheight":
                        String secondaryHeightText = readText(parser);
                        secondaryHeight = Integer.valueOf(secondaryHeightText);
                        break;
                    default:
                        break;
                }
            }
            return new Pair<>(new Point(width, height), new Point(secondaryWidth, secondaryHeight));

        } catch (Exception e) {
            return null;
        }
    }

    @VisibleForTesting
    void updateWallpaperComponent(Pair<ComponentName, WallpaperDescription> wpService, int which)
            throws IOException {
        WallpaperDescription description = wpService.second;
        boolean hasDescription = description != null;
        ComponentName component = hasDescription ? description.getComponent() : wpService.first;
        if (servicePackageExists(component)) {
            if (hasDescription) {
                Slog.i(TAG, "Using wallpaper description " + description);
                mWallpaperManager.setWallpaperComponentWithDescription(description, which);
                if ((which & FLAG_LOCK) != 0) {
                    mEventLogger.onLockLiveWallpaperRestoredWithDescription(description);
                }
                if ((which & FLAG_SYSTEM) != 0) {
                    mEventLogger.onSystemLiveWallpaperRestoredWithDescription(description);
                }
            } else {
                Slog.i(TAG, "Using wallpaper service " + component);
                mWallpaperManager.setWallpaperComponentWithFlags(component, which);
                if ((which & FLAG_LOCK) != 0) {
                    mEventLogger.onLockLiveWallpaperRestored(component);
                }
                if ((which & FLAG_SYSTEM) != 0) {
                    mEventLogger.onSystemLiveWallpaperRestored(component);
                }
            }
        } else {
            // If we've restored a live wallpaper, but the component doesn't exist,
            // we should log it as an error so we can easily identify the problem
            // in reports from users
            if (component != null) {
                // TODO(b/268471749): Handle delayed case
                applyComponentAtInstall(component, description, which);
                Slog.w(TAG, "Wallpaper service " + component + " isn't available. "
                        + " Will try to apply later");
            }
        }
    }

    private void restoreFromStage(File stage, File info, String hintTag, int which,
            Pair<Point, Point> sourceDeviceDimensions)
            throws IOException {
        if (stage.exists()) {
            if (multiCrop()) {
                SparseArray<Rect> cropHints = parseCropHints(info, hintTag);
                SparseArray<Rect> newCropHints = null;
                if (fixWallpaperCropsOnRestore()) {
                    Point bitmapSize = getBitmapSize(stage);
                    newCropHints = adjustCropHints(cropHints, bitmapSize, sourceDeviceDimensions);
                }
                cropHints = fixWallpaperCropsOnRestore() && newCropHints != null
                        ? newCropHints : cropHints;
                if (cropHints != null) {
                    Slog.i(TAG, "Got restored wallpaper; applying which=" + which
                            + "; cropHints = " + cropHints);
                    try (FileInputStream in = new FileInputStream(stage)) {
                        mWallpaperManager.setStreamWithCrops(in, cropHints, true, which);
                    }
                    // And log the success
                    if ((which & FLAG_SYSTEM) > 0) {
                        mEventLogger.onSystemImageWallpaperRestored();
                    }
                    if ((which & FLAG_LOCK) > 0) {
                        mEventLogger.onLockImageWallpaperRestored();
                    }
                } else {
                    logRestoreError(which, ERROR_NO_METADATA);
                }
                return;
            }
            // Parse the restored info file to find the crop hint.  Note that this currently
            // relies on a priori knowledge of the wallpaper info file schema.
            Rect cropHint = parseCropHint(info, hintTag);
            if (cropHint != null) {
                Slog.i(TAG, "Got restored wallpaper; applying which=" + which
                        + "; cropHint = " + cropHint);
                try (FileInputStream in = new FileInputStream(stage)) {

                    if (sourceDeviceDimensions != null && sourceDeviceDimensions.first != null) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        ParcelFileDescriptor pdf = ParcelFileDescriptor.open(stage, MODE_READ_ONLY);
                        BitmapFactory.decodeFileDescriptor(pdf.getFileDescriptor(),
                                null, options);
                        Point bitmapSize = new Point(options.outWidth, options.outHeight);
                        Point sourceDeviceSize = new Point(sourceDeviceDimensions.first.x,
                                sourceDeviceDimensions.first.y);
                        Point targetDeviceDimensions = getScreenDimensions();

                        // TODO: for now we handle only the case where the target device has smaller
                        // aspect ratio than the source device i.e. the target device is more narrow
                        // than the source device
                        if (isTargetMoreNarrowThanSource(targetDeviceDimensions,
                                sourceDeviceSize)) {
                            Rect adjustedCrop = findNewCropfromOldCrop(cropHint,
                                    sourceDeviceDimensions.first, targetDeviceDimensions,
                                    bitmapSize, true);

                            cropHint.set(adjustedCrop);
                        }
                    }

                    mWallpaperManager.setStream(in, cropHint.isEmpty() ? null : cropHint,
                            true, which);

                    // And log the success
                    if ((which & FLAG_SYSTEM) > 0) {
                        mEventLogger.onSystemImageWallpaperRestored();
                    }
                    if ((which & FLAG_LOCK) > 0) {
                        mEventLogger.onLockImageWallpaperRestored();
                    }
                }
            } else {
                logRestoreError(which, ERROR_NO_METADATA);
            }
        } else {
            Slog.d(TAG, "Restore data doesn't exist for file " + stage.getPath());
            logRestoreErrorIfNoLiveComponent(which, ERROR_NO_WALLPAPER);
        }
    }

    @VisibleForTesting Point getBitmapSize(File stage) throws IOException {
        Point bitmapSize = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (ParcelFileDescriptor pdf = ParcelFileDescriptor.open(stage,
                MODE_READ_ONLY)) {
            if (pdf != null) {
                BitmapFactory.decodeFileDescriptor(pdf.getFileDescriptor(),
                        null, options);
                bitmapSize = new Point(options.outWidth, options.outHeight);
            }
        }
        return bitmapSize;
    }

    /**
     * Adjust some cropHints restored from the previous device so that they better match the new
     * device dimensions. Overall, the goal is to preserve the same center of the image for the
     * left-most launcher page (or right-most if the device is RTL). If possible, this adjustment is
     * made by enlarging the crop, not reducing it, and parallax is preserved. Refer to
     * {@link #findNewCropfromOldCrop} for the details. Return null if one of the supplied argument
     * is null.
     *
     * @param cropHints for the previous device, map from {@link WallpaperManager.ScreenOrientation}
     *                  to a sub-region of the image to display for that screen orientation
     * @param bitmapSize the dimensions of the restored bitmap
     * @param sourceDeviceDimensions the device dimensions of the source device as per
     *                  {@link #parseDeviceDimensions}
     */
    private SparseArray<Rect> adjustCropHints(SparseArray<Rect> cropHints, Point bitmapSize,
            Pair<Point, Point> sourceDeviceDimensions) {
        if (cropHints == null || bitmapSize == null || sourceDeviceDimensions == null) {
            return null;
        }

        boolean rtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())
                == View.LAYOUT_DIRECTION_RTL;

        // If the map contains ORIENTATION_UNKNOWN, the map is a singleton and only an overall crop
        // is specified. Adjust this overall crop based on the largest screens of both devices.
        Rect totalCropHint = cropHints.get(ORIENTATION_UNKNOWN);
        if (totalCropHint != null) {
            SparseArray<Rect> result = new SparseArray<>();
            Rect adjustedTotalCropHint = findNewCropfromOldCrop(totalCropHint,
                    sourceDeviceDimensions.first, getScreenDimensions(), bitmapSize, rtl);
            result.put(ORIENTATION_UNKNOWN, adjustedTotalCropHint);
            return result;
        }

        boolean hasLargeScreen = false;
        SparseArray<Point> allCurrentDimensions = new SparseArray<>();
        for (WallpaperDisplayInfo displayInfo: getInternalDisplays()) {
            hasLargeScreen |= displayInfo.mIsLargeScreen;
            Point size = displayInfo.mDisplaySize;
            allCurrentDimensions.put(getOrientation(size), size);
            Point rotated = new Point(size.y, size.x);
            allCurrentDimensions.put(getOrientation(rotated), rotated);
        }

        SparseArray<Point> allSourceDimensions = new SparseArray<>();
        for (Point size: List.of(sourceDeviceDimensions.first, sourceDeviceDimensions.second)) {
            if (size == null || size.x == 0 || size.y == 0) {
                continue;
            }
            allSourceDimensions.put(getOrientation(size), size);
            Point rotated = new Point(size.y, size.x);
            allSourceDimensions.put(getOrientation(rotated), rotated);
        }

        SparseArray<Rect> adjustedCropHints = new SparseArray<>();
        for (int i = 0; i < cropHints.size(); i++) {
            int orientation = cropHints.keyAt(i);

            // Drop the LANDSCAPE crop hint unless the device has a large screen.
            if (!hasLargeScreen && orientation == ORIENTATION_LANDSCAPE) {
                continue;
            }
            Point currentDimensions = allCurrentDimensions.get(orientation);
            Point sourceDimensions = allSourceDimensions.get(orientation);
            if (currentDimensions == null || sourceDimensions == null) {
                continue;
            }
            Rect oldCrop = cropHints.valueAt(i);
            if (oldCrop.isEmpty() || oldCrop.left < 0 || oldCrop.top < 0
                    || oldCrop.right > bitmapSize.x
                    || oldCrop.bottom > bitmapSize.y) {
                Slog.w(TAG, "Skipping invalid crop " + oldCrop + " for orientation"
                        + orientation + " and bitmap size " + bitmapSize + ".");
            }
            Rect newCrop = findNewCropfromOldCrop(oldCrop, sourceDimensions,
                    currentDimensions, bitmapSize, rtl);
            adjustedCropHints.put(orientation, newCrop);
        }
        return adjustedCropHints;
    }

    /**
     * This method computes the crop of the stored wallpaper to preserve its center point as the
     * user had set it in the previous device.
     *
     * The algorithm involves first computing the original crop of the user (without parallax). Then
     * manually adjusting the user's original crop to respect the current device's aspect ratio
     * (thereby preserving the center point). Then finally, adding any leftover image real-estate
     * (i.e. space left over on the horizontal axis) to add parallax effect. Parallax is only added
     * if was present in the old device's settings.
     */
    private Rect findNewCropfromOldCrop(Rect oldCrop, Point oldDisplaySize,
            Point newDisplaySize, Point bitmapSize, boolean newRtl) {
        Rect cropWithoutParallax = withoutParallax(oldCrop, oldDisplaySize, newRtl, bitmapSize);
        // TODO (b/281648899) this is never empty when multi_crop is true
        oldCrop = oldCrop.isEmpty() ? new Rect(0, 0, bitmapSize.x, bitmapSize.y) : oldCrop;
        float oldParallaxAmount = ((float) oldCrop.width() / cropWithoutParallax.width()) - 1;

        Rect newCropWithSameCenterWithoutParallax = sameCenter(newDisplaySize, bitmapSize,
                cropWithoutParallax);

        Rect newCrop = newCropWithSameCenterWithoutParallax;

        // calculate the amount of left-over space there is in the image after adjusting the crop
        // from the above operation i.e. in a rtl configuration, this is the remaining space in the
        // image after subtracting the new crop's right edge coordinate from the image itself, and
        // for ltr, its just the new crop's left edge coordinate (as it's the distance from the
        // beginning of the image)
        int widthAvailableForParallaxOnTheNewDevice =
                (newRtl) ? newCrop.left : bitmapSize.x - newCrop.right;

        // calculate relatively how much this available space is as a fraction of the total cropped
        // image
        float availableParallaxAmount =
                (float) widthAvailableForParallaxOnTheNewDevice / newCrop.width();

        float minAcceptableParallax = Math.min(DEFAULT_ACCEPTABLE_PARALLAX, oldParallaxAmount);

        if (DEBUG) {
            Slog.d(TAG, "- cropWithoutParallax: " + cropWithoutParallax);
            Slog.d(TAG, "- oldParallaxAmount: " + oldParallaxAmount);
            Slog.d(TAG, "- newCropWithSameCenterWithoutParallax: "
                    + newCropWithSameCenterWithoutParallax);
            Slog.d(TAG, "- widthAvailableForParallaxOnTheNewDevice: "
                    + widthAvailableForParallaxOnTheNewDevice);
            Slog.d(TAG, "- availableParallaxAmount: " + availableParallaxAmount);
            Slog.d(TAG, "- minAcceptableParallax: " + minAcceptableParallax);
            Slog.d(TAG, "- oldCrop: " + oldCrop);
            Slog.d(TAG, "- oldDisplaySize: " + oldDisplaySize);
            Slog.d(TAG, "- newDisplaySize: " + newDisplaySize);
            Slog.d(TAG, "- bitmapSize: " + bitmapSize);
            Slog.d(TAG, "- newRtl: " + newRtl);
        }
        if (availableParallaxAmount >= minAcceptableParallax) {
            // but in any case, don't put more parallax than the amount of the old device
            float parallaxToAdd = Math.min(availableParallaxAmount, oldParallaxAmount);

            int widthToAddForParallax = (int) (newCrop.width() * parallaxToAdd);
            if (DEBUG) {
                Slog.d(TAG, "- parallaxToAdd: " + parallaxToAdd);
                Slog.d(TAG, "- widthToAddForParallax: " + widthToAddForParallax);
            }
            if (newRtl) {
                newCrop.left -= widthToAddForParallax;
            } else {
                newCrop.right += widthToAddForParallax;
            }
        }
        return newCrop;
    }

    /**
     * This method computes the original crop of the user without parallax.
     *
     * NOTE: When the user sets the wallpaper with a specific crop, there may additional image added
     * to the crop to support parallax. In order to determine the user's actual crop the parallax
     * must be removed if it exists.
     */
    Rect withoutParallax(Rect crop, Point displaySize, boolean rtl, Point bitmapSize) {
        // in the case an image's crop is not set, we assume the image itself is cropped
        // TODO (b/281648899) this is never empty when multi_crop is true
        if (crop.isEmpty()) {
            crop = new Rect(0, 0, bitmapSize.x, bitmapSize.y);
        }

        if (DEBUG) {
            Slog.w(TAG, "- crop: " + crop);
        }

        Rect adjustedCrop = new Rect(crop);
        float suggestedDisplayRatio = (float) displaySize.x / displaySize.y;

        // here we calculate the width of the wallpaper image such that it has the same aspect ratio
        // as the given display i.e. the width of the image on a single page of the device without
        // parallax (i.e. displaySize will correspond to the display the crop was originally set on)
        int wallpaperWidthWithoutParallax = (int) (0.5f + (float) displaySize.x * crop.height()
                / displaySize.y);
        // subtracting wallpaperWidthWithoutParallax from the wallpaper crop gives the amount of
        // parallax added
        int widthToRemove = Math.max(0, crop.width() - wallpaperWidthWithoutParallax);

        if (DEBUG) {
            Slog.d(TAG, "- adjustedCrop: " + adjustedCrop);
            Slog.d(TAG, "- suggestedDisplayRatio: " + suggestedDisplayRatio);
            Slog.d(TAG, "- wallpaperWidthWithoutParallax: " + wallpaperWidthWithoutParallax);
            Slog.d(TAG, "- widthToRemove: " + widthToRemove);
        }
        if (rtl) {
            adjustedCrop.left += widthToRemove;
        } else {
            adjustedCrop.right -= widthToRemove;
        }

        if (DEBUG) {
            Slog.d(TAG, "- adjustedCrop: " + crop);
        }
        return adjustedCrop;
    }

    /**
     * This method adjusts a given crop to match the aspect ratio of a new displaySize. The rules
     * are, in order of priority:
     * <ul>
     *   <li> Preserve the same horizontal center: if the crop needs to be enlarged horizontally,
     *   always add the same amount of width on both sides of the crop.</li>
     *   <li> Do not remove content: when possible, adjust by making the crop wider or taller, not
     *   shorter. Only make the crop shorter when it reaches the border of the image.
     *   <li> Preserve the same vertical center: if the crop needs to be enlarged vertically, add
     *   the same amount of height on both sides when possible.
     * </ul>
     */
    Rect sameCenter(Point displaySize, Point bitmapSize, Rect crop) {

        // in the case an image's crop is not set, we assume the image itself is cropped
        // TODO (b/281648899) this is never empty if multi_crop is true
        if (crop.isEmpty()) {
            crop = new Rect(0, 0, bitmapSize.x, bitmapSize.y);
        }

        float screenRatio = (float) displaySize.x / displaySize.y;
        float cropRatio = (float) crop.width() / crop.height();

        Rect adjustedCrop = new Rect(crop);

        if (screenRatio <= cropRatio) {
            // the screen is more narrow than the image, and as such, the image will need to be
            // zoomed in till it fits in the vertical axis. Due to this, we need to manually adjust
            // the image's crop in order for it to fit into the screen without having the framework
            // do it (since the framework left aligns the image after zooming)

            // Calculate the height of the adjusted wallpaper crop so it respects the aspect ratio
            // of the device. To calculate the height, we will use the width of the current crop.
            // This is so we find the largest height possible which also respects the device aspect
            // ratio.
            int heightToAdd = (int) (0.5f + crop.width() / screenRatio - crop.height());

            if (fixWallpaperCropsOnRestore()) {
                int availableHeight = bitmapSize.y - crop.height();
                if (availableHeight >= heightToAdd) {
                    // If there is enough height available to match the new aspect ratio, add that
                    // height to the crop, if possible on both sides of the crop.
                    int heightToAddTop = heightToAdd / 2;
                    int heightToAddBottom = heightToAdd / 2 + heightToAdd % 2;

                    if (crop.top < heightToAddTop) {
                        heightToAddBottom += (heightToAddTop - crop.top);
                        heightToAddTop = crop.top;
                    } else if (bitmapSize.y - crop.bottom < heightToAddBottom) {
                        heightToAddTop += (heightToAddBottom - (bitmapSize.y - crop.bottom));
                        heightToAddBottom = bitmapSize.y - crop.bottom;
                    }
                    adjustedCrop.top -= heightToAddTop;
                    adjustedCrop.bottom += heightToAddBottom;
                } else {
                    // Otherwise, make the crop use the whole bitmap height.
                    adjustedCrop.top = 0;
                    adjustedCrop.bottom = bitmapSize.y;
                }
            } else {
                // Calculate how much extra image space available that can be used to adjust
                // the crop. If this amount is less than heightToAdd, from above, then that means we
                // can't use heightToAdd. Instead we will need to use the maximum possible height,
                // which is the height of the original bitmap. NOTE: the bitmap height may be
                // different than the crop.
                // since there is no guarantee to have height available on both sides
                // (e.g. the available height might be fully at the bottom), grab the minimum
                int availableHeight = 2 * Math.min(crop.top, bitmapSize.y - crop.bottom);
                int actualHeightToAdd = Math.min(heightToAdd, availableHeight);

                // half of the additional height is added to the top and bottom of the crop
                adjustedCrop.top -= actualHeightToAdd / 2 + actualHeightToAdd % 2;
                adjustedCrop.bottom += actualHeightToAdd / 2;
            }

            // Calculate the width of the adjusted crop. Initially we used the fixed width of the
            // crop to calculate the heightToAdd, but since this height may be invalid (based on
            // the calculation above) we calculate the width again instead of using the fixed width,
            // using the adjustedCrop's updated height.
            int widthToRemove = (int) (0.5f + crop.width() - adjustedCrop.height() * screenRatio);

            // half of the additional width is subtracted from the left and right side of the crop
            int widthToRemoveLeft = widthToRemove / 2;
            int widthToRemoveRight = widthToRemove / 2 + widthToRemove % 2;

            adjustedCrop.left += widthToRemoveLeft;
            adjustedCrop.right -= widthToRemoveRight;

            if (DEBUG) {
                Slog.d(TAG, "cropRatio: " + cropRatio);
                Slog.d(TAG, "screenRatio: " + screenRatio);
                Slog.d(TAG, "heightToAdd: " + heightToAdd);
                Slog.d(TAG, "widthToRemove: " + widthToRemove);
                Slog.d(TAG, "adjustedCrop: " + adjustedCrop);
            }
        } else if (fixWallpaperCropsOnRestore()) {
            // Similar to the case above; but we always to add the same amount of width on both
            // sides to make sure we preserve the center horizontally.
            int widthToAdd = (int) (crop.height() * screenRatio - crop.width());

            // In this case, the available width is twice the width available on the shorter side
            int availableWidth = 2 * Math.min(crop.left, bitmapSize.x - crop.right);
            int actualWidthToAdd = Math.min(widthToAdd, availableWidth);
            adjustedCrop.left -= actualWidthToAdd / 2 + actualWidthToAdd % 2;
            adjustedCrop.right += actualWidthToAdd / 2;

            // If we couldn't add enough width to match the new aspect ratio, remove height
            int heightToRemove = (int) (0.5f + crop.height() - adjustedCrop.width() / screenRatio);

            int heightToRemoveTop = heightToRemove / 2;
            int heightToRemoveBottom = heightToRemove / 2 + heightToRemove % 2;

            adjustedCrop.top += heightToRemoveTop;
            adjustedCrop.bottom -= heightToRemoveBottom;
        }
        return adjustedCrop;
    }

    private boolean isTargetMoreNarrowThanSource(Point targetDisplaySize, Point srcDisplaySize) {
        float targetScreenRatio = (float) targetDisplaySize.x / targetDisplaySize.y;
        float srcScreenRatio = (float) srcDisplaySize.x / srcDisplaySize.y;

        return (targetScreenRatio < srcScreenRatio);
    }

    private void logRestoreErrorIfNoLiveComponent(int which, String error) {
        if (mSystemHasLiveComponent) {
            return;
        }
        logRestoreError(which, error);
    }

    private void logRestoreError(int which, String error) {
        if ((which & FLAG_SYSTEM) == FLAG_SYSTEM) {
            mEventLogger.onSystemImageWallpaperRestoreFailed(error);
        }
        if ((which & FLAG_LOCK) == FLAG_LOCK) {
            mEventLogger.onLockImageWallpaperRestoreFailed(error);
        }
    }

    private Rect parseCropHint(File wallpaperInfo, String sectionTag) {
        Rect cropHint = new Rect();
        try (FileInputStream stream = new FileInputStream(wallpaperInfo)) {
            XmlPullParser parser = Xml.resolvePullParser(stream);

            int type;
            do {
                type = parser.next();
                if (type == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (sectionTag.equals(tag)) {
                        cropHint.left = getAttributeInt(parser, "cropLeft", 0);
                        cropHint.top = getAttributeInt(parser, "cropTop", 0);
                        cropHint.right = getAttributeInt(parser, "cropRight", 0);
                        cropHint.bottom = getAttributeInt(parser, "cropBottom", 0);
                    }
                }
            } while (type != XmlPullParser.END_DOCUMENT);
        } catch (Exception e) {
            // Whoops; can't process the info file at all.  Report failure.
            Slog.w(TAG, "Failed to parse restored crop: " + e.getMessage());
            return null;
        }

        return cropHint;
    }

    private SparseArray<Rect> parseCropHints(File wallpaperInfo, String sectionTag) {
        SparseArray<Rect> cropHints = new SparseArray<>();
        try (FileInputStream stream = new FileInputStream(wallpaperInfo)) {
            XmlPullParser parser = Xml.resolvePullParser(stream);
            int type;
            do {
                type = parser.next();
                if (type != XmlPullParser.START_TAG) continue;
                String tag = parser.getName();
                if (!sectionTag.equals(tag)) continue;
                for (Pair<Integer, String> pair : List.of(
                        new Pair<>(WallpaperManager.ORIENTATION_PORTRAIT, "Portrait"),
                        new Pair<>(WallpaperManager.ORIENTATION_LANDSCAPE, "Landscape"),
                        new Pair<>(WallpaperManager.ORIENTATION_SQUARE_PORTRAIT, "SquarePortrait"),
                        new Pair<>(WallpaperManager.ORIENTATION_SQUARE_LANDSCAPE,
                                "SquareLandscape"))) {
                    Rect cropHint = new Rect(
                            getAttributeInt(parser, "cropLeft" + pair.second, 0),
                            getAttributeInt(parser, "cropTop" + pair.second, 0),
                            getAttributeInt(parser, "cropRight" + pair.second, 0),
                            getAttributeInt(parser, "cropBottom" + pair.second, 0));
                    if (!cropHint.isEmpty()) cropHints.put(pair.first, cropHint);
                }
                if (cropHints.size() == 0) {

                    Rect cropHint = null;
                    // It's possible to have a total crop but no crop hints per screen orientation,
                    // for example with overloads of setBitmap taking a single Rect as parameter.
                    if (fixWallpaperCropsOnRestore()) {
                        cropHint = new Rect(
                                getAttributeInt(parser, "totalCropLeft", 0),
                                getAttributeInt(parser, "totalCropTop", 0),
                                getAttributeInt(parser, "totalCropRight", 0),
                                getAttributeInt(parser, "totalCropBottom", 0));
                    }

                    // Migration case: the crops per orientation and total crop are not specified.
                    // Use the old attributes to restore the crop for one screen orientation.
                    if (cropHint == null) {
                        cropHint = new Rect(
                                getAttributeInt(parser, "cropLeft", 0),
                                getAttributeInt(parser, "cropTop", 0),
                                getAttributeInt(parser, "cropRight", 0),
                                getAttributeInt(parser, "cropBottom", 0));
                    }
                    // Note: When ORIENTATION_UNKNOWN is used, cropHint is treated as a total crop.
                    // The system will first crop the image, and for all screen orientations, the
                    // part of the image that is displayed will be within the crop.
                    if (!cropHint.isEmpty()) cropHints.put(ORIENTATION_UNKNOWN, cropHint);
                }
            } while (type != XmlPullParser.END_DOCUMENT);
        } catch (Exception e) {
            // Whoops; can't process the info file at all.  Report failure.
            Slog.w(TAG, "Failed to parse restored crops: " + e.getMessage());
            return null;
        }
        return cropHints;
    }

    private Pair<ComponentName, WallpaperDescription> parseWallpaperComponent(File wallpaperInfo,
            String sectionTag) {
        ComponentName name = null;
        WallpaperDescription description = null;
        try (FileInputStream stream = new FileInputStream(wallpaperInfo)) {
            final TypedXmlPullParser parser = Xml.resolvePullParser(stream);

            int type;
            do {
                type = parser.next();
                if (type == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (sectionTag.equals(tag)) {
                        final String parsedName = parser.getAttributeValue(null, "component");
                        name = (parsedName != null)
                                ? ComponentName.unflattenFromString(parsedName)
                                : null;
                        description = parseWallpaperDescription(parser, name);
                        break;
                    }
                }
            } while (type != XmlPullParser.END_DOCUMENT);
        } catch (Exception e) {
            // Whoops; can't process the info file at all.  Report failure.
            Slog.w(TAG, "Failed to parse restored component: " + e.getMessage());
            return new Pair<>(null, null);
        }
        return new Pair<>(name, description);
    }

    // Copied from com.android.server.wallpaper.WallpaperDataParser
    private WallpaperDescription parseWallpaperDescription(TypedXmlPullParser parser,
            ComponentName component) throws XmlPullParserException, IOException {

        WallpaperDescription description = null;
        int type = parser.next();
        if (type == XmlPullParser.START_TAG && "description".equals(parser.getName())) {
            // Always read the description if it's there - there may be one from a previous save
            // with content handling enabled even if it's enabled now
            description = WallpaperDescription.restoreFromXml(parser);
            // null component means that wallpaper was last saved without content handling, so
            // populate description from saved component
            if (description.getComponent() == null) {
                description = description.toBuilder().setComponent(component).build();
            }
        }
        return description;
    }

    private int getAttributeInt(XmlPullParser parser, String name, int defValue) {
        final String value = parser.getAttributeValue(null, name);
        return (value == null) ? defValue : Integer.parseInt(value);
    }

    @VisibleForTesting
    boolean servicePackageExists(ComponentName comp) {
        try {
            if (comp != null) {
                final IPackageManager pm = AppGlobals.getPackageManager();
                final PackageInfo info = pm.getPackageInfo(comp.getPackageName(),
                        0, getUserId());
                return (info != null);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Unable to contact package manager");
        }
        return false;
    }

    /** Unused Key/Value API. */
    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        // Intentionally blank
    }

    /** Unused Key/Value API. */
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        // Intentionally blank
    }

    private void applyComponentAtInstall(ComponentName componentName,
            @Nullable WallpaperDescription description, int which) {
        PackageMonitor packageMonitor = getWallpaperPackageMonitor(componentName, description,
                which);
        packageMonitor.register(getBaseContext(), null, true);
    }

    @VisibleForTesting
    PackageMonitor getWallpaperPackageMonitor(ComponentName componentName,
            @Nullable WallpaperDescription description, int which) {
        return new PackageMonitor() {
            @Override
            public void onPackageAdded(String packageName, int uid) {
                if (!isDeviceInRestore()) {
                    // We don't want to reapply the wallpaper outside a restore.
                    unregister();

                    // We have finished restore and not succeeded, so let's log that as an error.
                    WallpaperEventLogger logger = new WallpaperEventLogger(
                            mBackupManager.getDelayedRestoreLogger());
                    if ((which & FLAG_SYSTEM) != 0) {
                        logger.onSystemLiveWallpaperRestoreFailed(
                                WallpaperEventLogger.ERROR_LIVE_PACKAGE_NOT_INSTALLED);
                    }
                    if ((which & FLAG_LOCK) != 0) {
                        logger.onLockLiveWallpaperRestoreFailed(
                                WallpaperEventLogger.ERROR_LIVE_PACKAGE_NOT_INSTALLED);
                    }
                    mBackupManager.reportDelayedRestoreResult(logger.getBackupRestoreLogger());

                    return;
                }

                boolean useDescription =
                        (description != null && description.getComponent() != null);
                if (useDescription && description.getComponent().getPackageName().equals(
                        packageName)) {
                    Slog.d(TAG, "Applying description " + description);
                    boolean success = mWallpaperManager.setWallpaperComponentWithDescription(
                            description, which);
                    WallpaperEventLogger logger = new WallpaperEventLogger(
                            mBackupManager.getDelayedRestoreLogger());
                    if (success) {
                        if ((which & FLAG_SYSTEM) != 0) {
                            logger.onSystemLiveWallpaperRestoredWithDescription(description);
                        }
                        if ((which & FLAG_LOCK) != 0) {
                            logger.onLockLiveWallpaperRestoredWithDescription(description);
                        }
                    } else {
                        if ((which & FLAG_SYSTEM) != 0) {
                            logger.onSystemLiveWallpaperRestoreFailed(
                                    WallpaperEventLogger.ERROR_SET_DESCRIPTION_EXCEPTION);
                        }
                        if ((which & FLAG_LOCK) != 0) {
                            logger.onLockLiveWallpaperRestoreFailed(
                                    WallpaperEventLogger.ERROR_SET_DESCRIPTION_EXCEPTION);
                        }
                    }
                    // We're only expecting to restore the wallpaper component once.
                    unregister();
                    mBackupManager.reportDelayedRestoreResult(logger.getBackupRestoreLogger());
                } else if (componentName.getPackageName().equals(packageName)) {
                    Slog.d(TAG, "Applying component " + componentName);
                    boolean success = mWallpaperManager.setWallpaperComponentWithFlags(
                            componentName, which);
                    WallpaperEventLogger logger = new WallpaperEventLogger(
                            mBackupManager.getDelayedRestoreLogger());
                    if (success) {
                        if ((which & FLAG_SYSTEM) != 0) {
                            logger.onSystemLiveWallpaperRestored(componentName);
                        }
                        if ((which & FLAG_LOCK) != 0) {
                            logger.onLockLiveWallpaperRestored(componentName);
                        }
                    } else {
                        if ((which & FLAG_SYSTEM) != 0) {
                            logger.onSystemLiveWallpaperRestoreFailed(
                                    WallpaperEventLogger.ERROR_SET_COMPONENT_EXCEPTION);
                        }
                        if ((which & FLAG_LOCK) != 0) {
                            logger.onLockLiveWallpaperRestoreFailed(
                                    WallpaperEventLogger.ERROR_SET_COMPONENT_EXCEPTION);
                        }
                    }
                    // We're only expecting to restore the wallpaper component once.
                    unregister();
                    mBackupManager.reportDelayedRestoreResult(logger.getBackupRestoreLogger());
                }
            }
        };
    }

    /**
     * This method retrieves the dimensions of the largest display of the device
     *
     * @return a @{Point} object that contains the dimensions of the largest display on the device
     */
    private Point getScreenDimensions() {
        Point largestDimensions = null;
        int maxArea = 0;

        for (WallpaperDisplayInfo displayInfo : getInternalDisplays()) {
            Point displaySize = displayInfo.mDisplaySize;

            int width = displaySize.x;
            int height = displaySize.y;
            int area = width * height;

            if (area > maxArea) {
                maxArea = area;
                largestDimensions = displaySize;
            }
        }
        return largestDimensions;
    }

    /**
     * This method returns the smaller display on a multi-display device
     *
     * @return Display that corresponds to the smaller display on a device or null if there is only
     * one Display on a device
     */
    @Nullable
    private Point getSmallerDisplaySizeIfExists() {
        List<WallpaperDisplayInfo> internalDisplays = getInternalDisplays();
        Point largestDisplaySize = getScreenDimensions();

        // Find the first non-matching internal display
        for (WallpaperDisplayInfo displayInfo : internalDisplays) {
            Point displaySize = displayInfo.mDisplaySize;
            if (displaySize.x != largestDisplaySize.x || displaySize.y != largestDisplaySize.y) {
                return displaySize;
            }
        }

        // If no smaller display found, return null, as there is only a single display
        return null;
    }

    static class WallpaperDisplayInfo {
        Point mDisplaySize;
        boolean mIsLargeScreen;

        WallpaperDisplayInfo(int width, int height, int dpi) {
            mDisplaySize = new Point(width, height);
            float densityScaleFactor = (float) DisplayMetrics.DENSITY_DEFAULT / dpi;
            mIsLargeScreen = Math.min(mDisplaySize.x, mDisplaySize.y) * densityScaleFactor >= 600;
        }

        @VisibleForTesting WallpaperDisplayInfo(Point displaySize, boolean isLargeScreen) {
            mDisplaySize = displaySize;
            mIsLargeScreen = isLargeScreen;
        }
    }

    /**
     * This method retrieves the collection of Display objects available in the device.
     * i.e. non-external displays are ignored
     *
     * @return list of displays corresponding to each display in the device
     */
    @VisibleForTesting List<WallpaperDisplayInfo> getInternalDisplays() {
        Display[] allDisplays = mDisplayManager.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED);

        List<WallpaperDisplayInfo> internalDisplays = new ArrayList<>();
        DisplayInfo outDisplayInfo = new DisplayInfo();
        for (Display display : allDisplays) {
            if (display.getType() == Display.TYPE_INTERNAL) {
                display.getDisplayInfo(outDisplayInfo);
            }
            internalDisplays.add(new WallpaperDisplayInfo(
                    outDisplayInfo.logicalWidth,
                    outDisplayInfo.logicalHeight,
                    outDisplayInfo.logicalDensityDpi));
        }
        return internalDisplays;
    }

    @VisibleForTesting
    boolean isDeviceInRestore() {
        try {
            boolean isInSetup = Settings.Secure.getInt(getBaseContext().getContentResolver(),
                    Settings.Secure.USER_SETUP_COMPLETE) == 0;
            boolean isInDeferredSetup = Settings.Secure.getInt(getBaseContext()
                            .getContentResolver(),
                    Settings.Secure.USER_SETUP_PERSONALIZATION_STATE) ==
                    Settings.Secure.USER_SETUP_PERSONALIZATION_STARTED;
            return isInSetup || isInDeferredSetup;
        } catch (Settings.SettingNotFoundException e) {
            Slog.w(TAG, "Failed to check if the user is in restore: " + e);
            return false;
        }
    }

    @VisibleForTesting
    void setBackupManagerForTesting(BackupManager backupManager) {
        mBackupManager = backupManager;
    }
}
