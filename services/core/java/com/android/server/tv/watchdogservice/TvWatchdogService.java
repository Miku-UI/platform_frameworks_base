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

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.utils.Slogf;

/**
 * TV Watchdog Service.
 *
 * <p>This service runs in the System Server to monitor system health, focusing on I/O overuse, and
 * interacts with a native watchdog daemon. It replaces CarWatchdogService for the TV platform.
 */
public final class TvWatchdogService implements TvWatchdogHelper.Callback {
    /** Tag for logging. */
    static final String TAG = "TvWatchdogService";

    /** Flag to enable/disable debug logs. */
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** The starting ID for resource overuse notifications. */
    static final int RESOURCE_OVERUSE_NOTIFICATION_BASE_ID = 1100000;

    /** The number of unique notification IDs to cycle through. */
    static final int RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET = 1000;

    private final Context mContext;
    private final Object mLock = new Object();
    // The TvWatchdogHelper is initialized here but will be used in subsequent changes.
    @SuppressWarnings("unused")
    private final TvWatchdogHelper mHelper;
    private final NotificationManager mNotificationManager;

    /** Tracks whether the device is currently in idle mode. */
    @GuardedBy("mLock")
    private boolean mIsDeviceIdle = false;

    /**
     * Maps a posted notification's ID to the unique user-package identifier it represents. Used to
     * manage active notifications.
     */
    @GuardedBy("mLock")
    private final SparseArray<String> mActiveUserNotificationsByNotificationId =
            new SparseArray<>();

    /**
     * Set of unique user-package identifiers that currently have an active notification posted.
     * Used to prevent duplicate notifications.
     */
    @GuardedBy("mLock")
    private final ArraySet<String> mActiveUserNotifications = new ArraySet<>();

    /**
     * A cycling counter used to generate unique notification IDs. Incremented after each
     * notification is posted.
     */
    @GuardedBy("mLock")
    private int mCurrentOveruseNotificationIdOffset;

    /** Listens for explicit user dismissal of our notifications. */
    private final BroadcastReceiver mNotificationDismissalReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (TvWatchdogHelper.ACTION_NOTIFICATION_DISMISSED.equals(intent.getAction())) {
                        int notificationId =
                                intent.getIntExtra(TvWatchdogHelper.EXTRA_NOTIFICATION_ID, -1);
                        if (notificationId != -1) {
                            onNotificationDismissed(notificationId);
                        }
                    }
                }
            };

    /** Listens for package uninstallations to clean up notification state. */
    private final BroadcastReceiver mPackageRemovalReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                        final String packageName = intent.getData().getSchemeSpecificPart();
                        if (!TextUtils.isEmpty(packageName)) {
                            onPackageRemoved(packageName);
                        }
                    }
                }
            };

    /**
     * Initializes the TvWatchdogService.
     *
     * @param context The system context.
     */
    public TvWatchdogService(Context context) {
        mContext = context;
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mHelper = new TvWatchdogHelper(mContext, this, DEBUG);

        IntentFilter dismissFilter =
                new IntentFilter(TvWatchdogHelper.ACTION_NOTIFICATION_DISMISSED);
        mContext.registerReceiver(
                mNotificationDismissalReceiver, dismissFilter, Context.RECEIVER_NOT_EXPORTED);

        IntentFilter packageFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addDataScheme("package");
        mContext.registerReceiver(
                mPackageRemovalReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    /** Cleans up resources. This should be called when the service is destroyed. */
    public void shutdown() {
        mContext.unregisterReceiver(mNotificationDismissalReceiver);
        mContext.unregisterReceiver(mPackageRemovalReceiver);
    }

    // --- TvWatchdogHelper.Callback Implementation ---

    @Override
    public boolean isDeviceIdle() {
        synchronized (mLock) {
            return mIsDeviceIdle;
        }
    }

    @Override
    public int reserveNotificationSlot(String userPackageUniqueId) {
        synchronized (mLock) {
            if (mActiveUserNotifications.contains(userPackageUniqueId)) {
                Slogf.w(TAG, "Dropping duplicate notification request for " + userPackageUniqueId);
                return -1; // Slot already reserved for this package.
            }

            final int initialOffset = mCurrentOveruseNotificationIdOffset;
            while (true) {
                final int notificationId =
                        RESOURCE_OVERUSE_NOTIFICATION_BASE_ID + mCurrentOveruseNotificationIdOffset;
                // Check if this notification ID is already in use.
                if (mActiveUserNotificationsByNotificationId.get(notificationId) == null) {
                    // This ID is free. Reserve it.
                    mActiveUserNotifications.add(userPackageUniqueId);
                    mActiveUserNotificationsByNotificationId.put(
                            notificationId, userPackageUniqueId);
                    mCurrentOveruseNotificationIdOffset =
                            (mCurrentOveruseNotificationIdOffset + 1)
                                    % RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET;
                    return notificationId; // Success
                }

                // ID is in use, try the next one.
                mCurrentOveruseNotificationIdOffset =
                        (mCurrentOveruseNotificationIdOffset + 1)
                                % RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET;
                // If we have checked all 1000 slots and all are full, we cannot proceed.
                if (mCurrentOveruseNotificationIdOffset == initialOffset) {
                    Slogf.e(
                            TAG,
                            "All "
                                    + RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET
                                    + " notification slots are in use. Cannot post for "
                                    + userPackageUniqueId);
                    return -1; // All slots are full.
                }
            }
        }
    }

    @Override
    public void cancelNotificationSlot(String userPackageUniqueId, int notificationId) {
        synchronized (mLock) {
            mActiveUserNotifications.remove(userPackageUniqueId);
            mActiveUserNotificationsByNotificationId.remove(notificationId);
        }
    }

    @Override
    public void onNotificationDismissed(int notificationId) {
        synchronized (mLock) {
            String userPackageUniqueId =
                    mActiveUserNotificationsByNotificationId.get(notificationId);
            if (userPackageUniqueId != null) {
                mActiveUserNotificationsByNotificationId.remove(notificationId);
                mActiveUserNotifications.remove(userPackageUniqueId);
                if (DEBUG) {
                    Slogf.d(TAG, "Cleared dismissed notification state for " + userPackageUniqueId);
                }
            }
        }
    }

    /** Cleans up any active notification state for a package that was just uninstalled. */
    private void onPackageRemoved(String packageName) {
        synchronized (mLock) {
            for (int i = mActiveUserNotificationsByNotificationId.size() - 1; i >= 0; i--) {
                String userPackageUniqueId = mActiveUserNotificationsByNotificationId.valueAt(i);
                if (userPackageUniqueId != null
                        && userPackageUniqueId.endsWith(":" + packageName)) {
                    int notificationId = mActiveUserNotificationsByNotificationId.keyAt(i);
                    mNotificationManager.cancel(notificationId);
                    mActiveUserNotificationsByNotificationId.removeAt(i);
                    mActiveUserNotifications.remove(userPackageUniqueId);
                    if (DEBUG) {
                        Slogf.d(
                                TAG,
                                "Cleaned up notification (ID: "
                                        + notificationId
                                        + ") for uninstalled package: "
                                        + userPackageUniqueId);
                    }
                }
            }
        }
    }
}
