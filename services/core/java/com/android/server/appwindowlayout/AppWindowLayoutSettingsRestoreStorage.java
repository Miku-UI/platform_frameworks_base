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
package com.android.server.appwindowlayout;

import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_UNSET;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;

/**
 * Storage for restored app window layout settings, for apps that are not installed yet.
 *
 * <p>This class uses SharedPreferences to preserve data. Only non-committed data is held in the
 * storage. Any stored data waiting for apps to be installed will be deleted after 7 days.
 *
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class AppWindowLayoutSettingsRestoreStorage {
    private static final String TAG = "AppWinLayoutSetStorage";

    // TODO(b/414361710): Consider joining this class implementation with the Settings app
    //  UserAspectRatioRestoreStorage, or removing the Settings class implementation if it is not
    //  needed after restoreUserAspectRatioSettingsUsingService flag is launched.
    @VisibleForTesting
    static final String ASPECT_RATIO_STAGED_DATA_PREFS = "AspectRatioStagedDataPrefs.xml";
    // Storage for commit time, to be able to clean up after expiry date.
    @VisibleForTesting
    static final String RESTORE_TIME_STAGED_DATA_PREFS = "RestoreTimeStagedDataPrefs.xml";
    @VisibleForTesting
    static final String KEY_STAGED_DATA_TIME = "staged_data_time";
    // 7 days after completing restore, the staged data will be deleted automatically. This will
    // erase restored user aspect ratios for apps that haven't been installed since.
    private static final Duration EXPIRY_DURATION = Duration.ofDays(7);

    @NonNull
    private final Context mContext;
    @UserIdInt
    private final int mUserId;
    @NonNull
    private final InstantSource mInstantSource;

    // SharedPreferences to store restored user aspect ratios for not-yet-installed packages.
    @NonNull
    private final SharedPreferences mUserAspectRatioSharedPreferences;
    // SharedPreferences to store restore time, for the purposes of cleanup.
    @NonNull
    private final SharedPreferences mRestoreTimeSharedPreferences;

    AppWindowLayoutSettingsRestoreStorage(@NonNull Context context, @UserIdInt int userId,
            @NonNull InstantSource instantSource) {
        mContext = context;
        mUserId = userId;
        mInstantSource = instantSource;

        mUserAspectRatioSharedPreferences = createSharedPrefs(mContext.getPackageName()
                + "." + ASPECT_RATIO_STAGED_DATA_PREFS);
        mRestoreTimeSharedPreferences = createSharedPrefs(mContext.getPackageName()
                + "." + RESTORE_TIME_STAGED_DATA_PREFS);
    }

    void setStagedRestoreTime() {
        // Store restore time, for the purpose of cleanup after expiry date.
        mRestoreTimeSharedPreferences.edit().putLong(KEY_STAGED_DATA_TIME,
                mInstantSource.millis()).commit();
    }

    /**
     * Stores package name and user aspect ratio that is restored, but cannot be committed at the
     * moment, for example because given package has not been installed yet.
     */
    void storePackageAndUserAspectRatio(@NonNull String packageName,
            @PackageManager.UserMinAspectRatio int userAspectRatio) {
        mUserAspectRatioSharedPreferences.edit().putInt(packageName, userAspectRatio).commit();
        setStagedRestoreTime();
    }

    int getAndRemoveUserAspectRatioForPackage(@NonNull String packageName) {
        removeExpiredDataIfNeeded();
        final int aspectRatio = mUserAspectRatioSharedPreferences.getInt(packageName,
                USER_MIN_ASPECT_RATIO_UNSET);
        mUserAspectRatioSharedPreferences.edit().remove(packageName).commit();
        return aspectRatio;
    }

    @NonNull
    private SharedPreferences createSharedPrefs(@NonNull String fileName) {
        final File prefsFile = new File(Environment.getDataSystemDeDirectory(mUserId), fileName);
        return mContext.createDeviceProtectedStorageContext().getSharedPreferences(prefsFile,
                Context.MODE_PRIVATE);
    }

    private void removeExpiredDataIfNeeded() {
        if (!mRestoreTimeSharedPreferences.contains(KEY_STAGED_DATA_TIME)) {
            // Restore not yet completed (too early to clean up), or already cleaned up.
            return;
        }

        final Instant restoreTime = Instant.ofEpochMilli(mRestoreTimeSharedPreferences.getLong(
                KEY_STAGED_DATA_TIME, 0));
        if (Duration.between(restoreTime, mInstantSource.instant()).compareTo(EXPIRY_DURATION)
                >= 0) {
            Slog.d(TAG, "Restore time expired. Deleting packages: "
                    + mUserAspectRatioSharedPreferences.getAll());
            // Remove the restore time and all data to restore.
            mUserAspectRatioSharedPreferences.edit().clear().commit();
            mRestoreTimeSharedPreferences.edit().clear().commit();
        }
    }

    boolean hasDataStored() {
        return !mUserAspectRatioSharedPreferences.getAll().isEmpty();
    }
}
