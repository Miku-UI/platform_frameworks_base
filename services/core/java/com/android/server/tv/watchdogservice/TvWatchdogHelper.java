/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.tv.watchdogservice;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.app.ActivityManagerInternal;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.StatsEvent;

import com.android.internal.R;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.LocalServices;
import com.android.server.utils.Slogf;

import java.util.ArrayList;
import java.util.List;

// TODO: Add "implements IoOveruseHandler.IoOveruseHelper" once IoOveruseHandler is created.
public class TvWatchdogHelper {
    /** Tag for logging. */
    private static final String TAG = "TvWatchdogHelper";

    /** The ID for the notification channel used for resource overuse alerts. */
    static final String NOTIFICATION_CHANNEL_ID = "tv_watchdog_channel";

    /** Broadcast action sent when a resource overuse notification is dismissed. */
    static final String ACTION_NOTIFICATION_DISMISSED =
            "com.android.server.tv.watchdogservice.ACTION_NOTIFICATION_DISMISSED";

    /** Intent extra key for the ID of the dismissed notification. */
    static final String EXTRA_NOTIFICATION_ID =
            "com.android.server.tv.watchdogservice.EXTRA_NOTIFICATION_ID";

    /** An interface defining the callbacks this helper needs from its owner (TvWatchdogService). */
    public interface Callback {
        /**
         * Returns whether the device is in idle mode.
         *
         * @return {@code true} if the device is idle, {@code false} otherwise.
         */
        boolean isDeviceIdle();

        /**
         * Atomically checks for and reserves a unique notification ID for a given package.
         *
         * @return The notification ID to use, or -1 if a notification already exists.
         */
        int reserveNotificationSlot(String userPackageUniqueId);

        /** Cancels a previously reserved notification slot if posting the notification failed. */
        void cancelNotificationSlot(String userPackageUniqueId, int notificationId);

        /** Cleans up state for a notification that was dismissed by the user. */
        void onNotificationDismissed(int notificationId);
    }

    private final Context mContext;
    private final Callback mCallback;
    private final NotificationManager mNotificationManager;
    private final boolean mDebug;
    private final Object mSettingsLock = new Object();
    private final ActivityManagerInternal mActivityManagerInternal;

    public TvWatchdogHelper(Context context, Callback callback, boolean debug) {
        this(context, callback, debug, LocalServices.getService(ActivityManagerInternal.class));
    }

    TvWatchdogHelper(
            Context context,
            Callback callback,
            boolean debug,
            ActivityManagerInternal activityManagerInternal) {
        mContext = context;
        mCallback = callback;
        mNotificationManager = context.getSystemService(NotificationManager.class);
        mDebug = debug;
        mActivityManagerInternal = activityManagerInternal;
    }

    // --- IoOveruseHelper Implementation ---

    /**
     * Returns whether the device is in idle mode.
     *
     * @return {@code true} if the device is idle, {@code false} otherwise.
     */
    public boolean isInIdleMode() {
        return mCallback.isDeviceIdle();
    }

    /**
     * Records a kill event to the system log for debugging purposes.
     *
     * @param packageName The package name of the killed application.
     * @param userId The user ID of the killed application.
     * @param totalTimesKilled Total times this package has been killed for overuse.
     * @param isPackageDisabled Whether the package was disabled as a result of the kill.
     */
    public void writeKillEventLog(
            String packageName,
            int userId,
            long foregroundBytes,
            long backgroundBytes,
            long garageModeBytes,
            long thresholdForegroundBytes,
            long thresholdBackgroundBytes,
            long thresholdGarageModeBytes,
            int totalTimesKilled,
            boolean isPackageDisabled) {
        Slogf.i(
                TAG,
                TextUtils.formatSimple(
                        "Kill Event: pkg=%s, user=%d, disabled=%b, totalKills=%d",
                        packageName, userId, isPackageDisabled, totalTimesKilled));
    }

    /** Called when a package previously disabled for overuse is re-enabled by the user. */
    public void onPackageEnabledLocked(String packageName, int userId) {
        updateSecureSettingString(packageName, userId, false);
    }

    /** Called when a package is disabled for resource overuse. */
    public void onPackageDisabledLocked(String packageName, int userId) {
        updateSecureSettingString(packageName, userId, true);
    }

    /** Helper method to modify the semicolon-separated list of disabled packages. */
    private void updateSecureSettingString(String packageName, int userId, boolean shouldAdd) {
        synchronized (mSettingsLock) {
            final ContentResolver contentResolver;
            try {
                contentResolver =
                        mContext.createPackageContextAsUser("android", 0, UserHandle.of(userId))
                                .getContentResolver();
            } catch (NameNotFoundException e) {
                Slogf.e(TAG, "Failed to create package context for user " + userId, e);
                return;
            }

            String currentSetting =
                    getSecureStringSettingForUser(
                            contentResolver,
                            TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
                            userId);
            ArraySet<String> packages = IoOveruseHandler.extractPackages(currentSetting);

            boolean changed = shouldAdd ? packages.add(packageName) : packages.remove(packageName);
            if (changed) {
                String newSetting = IoOveruseHandler.constructSettingsString(packages);
                putSecureStringSetting(
                        contentResolver,
                        TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE,
                        newSetting);
                if (mDebug) {
                    Slogf.d(
                            TAG,
                            (shouldAdd ? "Appended " : "Removed ")
                                    + packageName
                                    + " for user "
                                    + userId);
                }
            }
        }
    }

    /** Logs I/O overuse stats metrics with {@link FrameworkStatsLog}. */
    public void logIoOveruseStatsReported(int uid, byte[] ioOveruseStats) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.CAR_WATCHDOG_IO_OVERUSE_STATS_REPORTED, uid, ioOveruseStats);
    }

    /** Logs I/O overuse kill stats metrics with {@link FrameworkStatsLog}. */
    public void logKillStatsReported(
            int uid,
            int uidState,
            int systemState,
            int killReason,
            byte[] processStats,
            byte[] ioOveruseStats) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.CAR_WATCHDOG_KILL_STATS_REPORTED,
                uid,
                uidState,
                systemState,
                killReason,
                processStats,
                ioOveruseStats);
    }

    /** Builds a system-wide I/O usage summary stats event for statsd to pull. */
    public StatsEvent buildSystemIoUsageSummaryStatsEvent(byte[] summary, long startTime) {
        return FrameworkStatsLog.buildStatsEvent(
                FrameworkStatsLog.CAR_WATCHDOG_SYSTEM_IO_USAGE_SUMMARY, summary, startTime);
    }

    /** Builds a per-UID I/O usage summary stats event for statsd to pull. */
    public StatsEvent buildUidIoUsageSummaryStatsEvent(int uid, byte[] summary, long startTime) {
        return FrameworkStatsLog.buildStatsEvent(
                FrameworkStatsLog.CAR_WATCHDOG_UID_IO_USAGE_SUMMARY, uid, summary, startTime);
    }

    /** Gets the current enabled setting for an application for a specific user. */
    public int getApplicationEnabledSettingForUser(
            @NonNull String packageName, @UserIdInt int userId) throws RemoteException {
        try {
            Context userContext =
                    mContext.createPackageContextAsUser(
                            mContext.getPackageName(), 0, UserHandle.of(userId));
            return userContext.getPackageManager().getApplicationEnabledSetting(packageName);
        } catch (NameNotFoundException | IllegalArgumentException e) {
            return PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        }
    }

    /** Sets the enabled setting for an application for a specific user. */
    @RequiresPermission(android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE)
    public void setApplicationEnabledSettingForUser(
            @NonNull String packageName,
            int newState,
            int flags,
            @UserIdInt int userId,
            @NonNull String callingPackage)
            throws RemoteException {
        try {
            Context userContext =
                    mContext.createPackageContextAsUser(
                            mContext.getPackageName(), 0, UserHandle.of(userId));
            userContext
                    .getPackageManager()
                    .setApplicationEnabledSetting(packageName, newState, flags);
        } catch (NameNotFoundException e) {
            Slogf.e(TAG, "Failed to create user context for setting enabled state", e);
        }
    }

    /**
     * Sends notifications to the user about packages that have overused resources.
     *
     * @param userId The user to notify. This must be the current foreground user.
     * @param packages A list of package names to send notifications for.
     * @return A list of packages for which notifications were successfully sent.
     */
    @Nullable
    public List<String> sendUserNotifications(@UserIdInt int userId, List<String> packages) {
        if (mActivityManagerInternal == null
                || mActivityManagerInternal.getCurrentUserId() != userId) {
            Slogf.w(TAG, "Cannot send notification for non-current user " + userId);
            return null;
        }

        List<String> notifiedPackages = new ArrayList<>();
        for (String packageName : packages) {
            String userPackageUniqueId =
                    IoOveruseHandler.getUserPackageUniqueId(userId, packageName);
            int notificationId = mCallback.reserveNotificationSlot(userPackageUniqueId);

            if (notificationId == -1) {
                Slogf.w(TAG, "Dropping duplicate notification for " + userPackageUniqueId);
                continue;
            }

            if (postNotification(notificationId, packageName, UserHandle.of(userId))) {
                notifiedPackages.add(packageName);
            } else {
                Slogf.e(
                        TAG,
                        "Failed to post notification, canceling slot for " + userPackageUniqueId);
                mCallback.cancelNotificationSlot(userPackageUniqueId, notificationId);
            }
        }
        return notifiedPackages;
    }

    /**
     * Creates and displays a resource overuse notification.
     *
     * @param notificationId The unique integer ID for this notification.
     * @param packageName The package the notification is for.
     * @param userHandle The user who should see the notification.
     * @return {@code true} if the notification was posted successfully, {@code false} otherwise.
     */
    protected boolean postNotification(
            int notificationId, String packageName, UserHandle userHandle) {
        final String channelName =
                mContext.getString(R.string.tv_watchdog_notification_channel_name);
        mNotificationManager.createNotificationChannel(
                new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        channelName,
                        NotificationManager.IMPORTANCE_DEFAULT));

        String title = mContext.getString(R.string.tv_watchdog_notification_title);
        String content = mContext.getString(R.string.tv_watchdog_notification_content, packageName);

        Intent deleteIntent = new Intent(ACTION_NOTIFICATION_DISMISSED);
        deleteIntent.setPackage(mContext.getPackageName());
        deleteIntent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        PendingIntent deletePendingIntent =
                PendingIntent.getBroadcast(
                        mContext,
                        notificationId,
                        deleteIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setSmallIcon(R.drawable.stat_sys_warning)
                        .setDeleteIntent(deletePendingIntent)
                        .build();
        try {
            mNotificationManager.notifyAsUser(
                    packageName, notificationId, notification, userHandle);
            if (mDebug) {
                Slogf.d(TAG, "Posted notification id " + notificationId + " for " + packageName);
            }
            return true;
        } catch (RuntimeException e) {
            Slogf.e(TAG, "Failed to post notification for " + packageName, e);
            return false;
        }
    }

    protected String getSecureStringSettingForUser(
            ContentResolver contentResolver, String name, @UserIdInt int userId) {
        return Settings.Secure.getStringForUser(contentResolver, name, userId);
    }

    protected void putSecureStringSetting(
            ContentResolver contentResolver, String name, String value) {
        Settings.Secure.putString(contentResolver, name, value);
    }
}
