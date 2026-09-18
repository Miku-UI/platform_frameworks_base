/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.server.companion.datatransfer.crossdevicesync.notification;

import static android.app.Notification.VISIBILITY_PUBLIC;

import android.app.Notification.Action;
import android.app.Notification.BigTextStyle;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.android.internal.R;
import com.android.server.companion.datatransfer.crossdevicesync.common.DeviceUtils;

import java.time.Period;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/** Configuration for notifications. */
public class NotificationConfig {
    private static final String APM_SYNC_NOTIFICATION_CHANNEL_ID = "apm_sync_channel";
    private static final String DND_SYNC_NOTIFICATION_CHANNEL_ID = "dnd_sync_channel";

    private final Context mContext;
    private final DeviceUtils mDeviceUtils;

    final Map<String, Notification> mNotificationMap;

    public NotificationConfig(Context context, DeviceUtils deviceUtils) {
        mContext = context;
        mDeviceUtils = deviceUtils;

        mNotificationMap =
                Map.of(
                        NotificationReason.AIRPLANE_MODE_SYNCED,
                        new Notification(
                                1,
                                this::createApmSyncedNotificationChannel,
                                this::createApmSyncedAndroidNotification,
                                Arrays.asList(
                                        new RateLimiter(Period.ZERO, 1),
                                        new RateLimiter(Period.ofWeeks(1), 1),
                                        new RateLimiter(Period.ofMonths(6), RateLimiter.FOREVER))),
                        NotificationReason.DO_NOT_DISTURB_SYNCED,
                        new Notification(
                                2,
                                this::createDndSyncedNotificationChannel,
                                this::createDndSyncedAndroidNotification,
                                Collections.singletonList(
                                        new RateLimiter(Period.ZERO, RateLimiter.FOREVER))));
    }

    private Context getResContext() {
        try {
            return mContext.createPackageContext(
                    "com.android.server.companion.datatransfer.crossdevicesync.res", 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return mContext;
        }
    }

    private NotificationChannel createApmSyncedNotificationChannel() {
        Context resContext = getResContext();
        return new NotificationChannel(
                APM_SYNC_NOTIFICATION_CHANNEL_ID,
                resContext.getString(R.string.airplane_mode_sync_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
    }

    private android.app.Notification createApmSyncedAndroidNotification() {
        Context resContext = getResContext();
        String message = resContext.getString(R.string.airplane_mode_sync_notification_content);
        android.app.Notification.Builder builder =
                new android.app.Notification.Builder(resContext, APM_SYNC_NOTIFICATION_CHANNEL_ID)
                        .setContentTitle(
                                resContext.getString(
                                        R.string.airplane_mode_sync_notification_title))
                        .setSmallIcon(R.drawable.ic_cross_device_sync_airplane)
                        .setContentText(message)
                        .setStyle(new BigTextStyle().bigText(message))
                        .setVisibility(VISIBILITY_PUBLIC)
                        .setAutoCancel(true)
                        .setLocalOnly(true);
        PendingIntent intent =
                PendingIntent.getActivity(
                        mContext,
                        PendingIntent.FLAG_UPDATE_CURRENT,
                        new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
                        PendingIntent.FLAG_IMMUTABLE);
        // On Watch, tapping on the notification expands it. So show this as an action instead.
        if (mDeviceUtils.isWatch()) {
            String actionLabel =
                    resContext.getString(R.string.apm_sync_notification_settings_action);
            builder.setActions(new Action.Builder(/* icon= */ null, actionLabel, intent).build());
        } else {
            builder.setContentIntent(intent);
        }
        return builder.build();
    }

    private NotificationChannel createDndSyncedNotificationChannel() {
        Context resContext = getResContext();
        return new NotificationChannel(
                DND_SYNC_NOTIFICATION_CHANNEL_ID,
                resContext.getString(R.string.do_not_disturb_sync_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
    }

    private android.app.Notification createDndSyncedAndroidNotification() {
        Context resContext = getResContext();
        return new android.app.Notification.Builder(resContext, DND_SYNC_NOTIFICATION_CHANNEL_ID)
                .setContentTitle(
                        resContext.getString(R.string.do_not_disturb_sync_notification_title))
                .setContentText(
                        resContext.getString(R.string.do_not_disturb_sync_notification_content))
                .setSmallIcon(com.android.internal.R.drawable.ic_settings)
                .setVisibility(VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .build();
    }
}
