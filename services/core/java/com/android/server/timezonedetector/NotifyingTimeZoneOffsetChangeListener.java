/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.timezonedetector;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.Context.RECEIVER_NOT_EXPORTED;
import static android.content.Intent.ACTION_TIMEZONE_OFFSET_CHANGED;
import static android.content.Intent.EXTRA_NEW_TIMEZONE_OFFSET;
import static android.content.Intent.EXTRA_OLD_TIMEZONE_OFFSET;
import static android.provider.Settings.ACTION_DATE_SETTINGS;

import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.icu.text.DateFormat;
import android.icu.text.MeasureFormat;
import android.icu.text.SimpleDateFormat;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.icu.util.TimeZone;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.IndentingPrintWriter;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.notification.SystemNotificationChannels;
import com.android.server.timezonedetector.TimeZoneOffsetChangeListener.TimeZoneOffsetChangeEvent;

import java.time.Duration;
import java.util.Objects;

/** An implementation of {@link TimeZoneOffsetChangeListener} that fires notifications. */
public class NotifyingTimeZoneOffsetChangeListener implements TimeZoneOffsetChangeListener {
    private static final String TAG = "TimeZoneOffsetChangeNotificationTracker";
    private static final String NOTIFICATION_TAG = "TimeZoneOffsetChangeNotification";
    private static final int TIME_ZONE_OFFSET_CHANGE_NOTIFICATION_ID = 1002;

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final ServiceConfigAccessor mServiceConfigAccessor;
    private final Resources mRes = Resources.getSystem();

    private final Object mConfigurationLock = new Object();

    @GuardedBy("mConfigurationLock")
    private ConfigurationInternal mConfigurationInternal;

    @GuardedBy("mConfigurationLock")
    private boolean mIsRegistered;

    private final BroadcastReceiver mTimeZoneOffsetChangeChangeReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_TIMEZONE_OFFSET_CHANGED.equals(intent.getAction())) {
                        final int oldOffsetSeconds =
                                intent.getIntExtra(EXTRA_OLD_TIMEZONE_OFFSET, 0);
                        final int newOffsetSeconds =
                                intent.getIntExtra(EXTRA_NEW_TIMEZONE_OFFSET, 0);
                        TimeZoneOffsetChangeEvent event =
                                new TimeZoneOffsetChangeEvent(
                                        SystemClock.elapsedRealtime(),
                                        System.currentTimeMillis(),
                                        oldOffsetSeconds,
                                        newOffsetSeconds);

                        process(event);
                    }
                }
            };

    /** Create and initialise a new {@code NotifyingTimeZoneOffsetChangeStatusChangeListener} */
    @RequiresPermission("android.permission.INTERACT_ACROSS_USERS_FULL")
    public static NotifyingTimeZoneOffsetChangeListener create(
            Context context, ServiceConfigAccessor serviceConfigAccessor) {
        if (!ExperimentHelper.isTimeZoneOffsetChangeNotificationEnabled()) {
            return null;
        }

        NotifyingTimeZoneOffsetChangeListener manager =
                new NotifyingTimeZoneOffsetChangeListener(
                        context,
                        context.getSystemService(NotificationManager.class),
                        context.getSystemService(AlarmManager.class),
                        serviceConfigAccessor);

        // Pretend there was an update to initialize configuration.
        manager.handleConfigurationUpdate();

        return manager;
    }

    @VisibleForTesting
    NotifyingTimeZoneOffsetChangeListener(
            Context context,
            NotificationManager notificationManager,
            AlarmManager alarmManager,
            ServiceConfigAccessor serviceConfigAccessor) {
        mContext = Objects.requireNonNull(context);
        mNotificationManager = notificationManager;
        mServiceConfigAccessor = Objects.requireNonNull(serviceConfigAccessor);
        mServiceConfigAccessor.addConfigurationInternalChangeListener(
                this::handleConfigurationUpdate);
    }

    private void handleConfigurationUpdate() {
        synchronized (mConfigurationLock) {
            ConfigurationInternal oldConfigurationInternal = mConfigurationInternal;
            mConfigurationInternal = mServiceConfigAccessor.getCurrentUserConfigurationInternal();

            if (areNotificationsEnabled()) {
                if (!mIsRegistered) {
                    mContext.registerReceiverForAllUsers(
                            mTimeZoneOffsetChangeChangeReceiver,
                            new IntentFilter(ACTION_TIMEZONE_OFFSET_CHANGED),
                            /* broadcastPermission= */ null,
                            /* scheduler= */ null,
                            RECEIVER_NOT_EXPORTED);
                    mIsRegistered = true;
                }
            } else if (mIsRegistered) {
                mContext.unregisterReceiver(mTimeZoneOffsetChangeChangeReceiver);
                mIsRegistered = false;
            }

            if (oldConfigurationInternal != null) {
                boolean userChanged =
                        oldConfigurationInternal.getUserId() != mConfigurationInternal.getUserId();

                if (!areNotificationsEnabled() || userChanged) {
                    // Clear any notifications that are no longer needed.
                    clearNotificationForUser(oldConfigurationInternal.getUserId());
                }
            }
        }
    }

    @Override
    public void process(TimeZoneOffsetChangeEvent event) {
        synchronized (mConfigurationLock) {
            if (areNotificationsEnabled()) {
                notifyOfTimeZoneOffsetChange(mConfigurationInternal.getUserId(), event);
            }
        }
    }

    private void notifyOfTimeZoneOffsetChange(
            @UserIdInt int userId, TimeZoneOffsetChangeEvent event) {
        long unixEpochTimeMillis = event.getUnixEpochTimeMillis();
        int oldOffsetSeconds = event.getOldOffsetSeconds();
        int newOffsetSeconds = event.getNewOffsetSeconds();

        final CharSequence title =
                mRes.getString(R.string.time_zone_offset_change_notification_title);
        final CharSequence body =
                getNotificationBody(oldOffsetSeconds, newOffsetSeconds, unixEpochTimeMillis);

        final Intent clickNotificationIntent =
                new Intent(ACTION_DATE_SETTINGS)
                        .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Notification notification =
                new Notification.Builder(mContext, SystemNotificationChannels.TIME)
                        .setSmallIcon(R.drawable.btn_clock_material)
                        .setStyle(new Notification.BigTextStyle().bigText(body))
                        .setOnlyAlertOnce(true)
                        .setColor(mContext.getColor(R.color.system_notification_accent_color))
                        .setTicker(title)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setContentIntent(
                                PendingIntent.getActivityAsUser(
                                        mContext,
                                        /* requestCode= */ 0,
                                        clickNotificationIntent,
                                        /* flags= */ PendingIntent.FLAG_CANCEL_CURRENT
                                                | FLAG_IMMUTABLE,
                                        /* options= */ null,
                                        UserHandle.of(userId)))
                        .setAutoCancel(true) // auto-clear notification on selection
                        .build();

        mNotificationManager.notifyAsUser(
                NOTIFICATION_TAG,
                TIME_ZONE_OFFSET_CHANGE_NOTIFICATION_ID,
                notification,
                UserHandle.of(userId));
    }

    private CharSequence getNotificationBody(
            int oldOffsetSeconds, int newOffsetSeconds, long unixEpochTimeMillis) {
        Duration absDuration = Duration.ofSeconds(Math.abs(newOffsetSeconds - oldOffsetSeconds));
        long hours = absDuration.toHours();
        long minutes = absDuration.toMinutes() % 60;

        MeasureFormat mf =
                MeasureFormat.getInstance(
                        mContext.getResources().getConfiguration().getLocales().get(0),
                        MeasureFormat.FormatWidth.WIDE);

        final Measure[] measures;
        if (hours > 0 && minutes > 0) {
            measures =
                    new Measure[] {
                        new Measure(hours, MeasureUnit.HOUR),
                        new Measure(minutes, MeasureUnit.MINUTE)
                    };
        } else if (hours > 0) {
            measures = new Measure[] {new Measure(hours, MeasureUnit.HOUR)};
        } else {
            measures = new Measure[] {new Measure(minutes, MeasureUnit.MINUTE)};
        }
        String duration = mf.formatMeasures(measures);
        int resId;

        if (newOffsetSeconds - oldOffsetSeconds > 0) {
            resId = R.string.time_zone_offset_change_notification_body_forward;
        } else {
            resId = R.string.time_zone_offset_change_notification_body_backward;
        }

        // Show the time zone display name post change
        TimeZone tz = TimeZone.getDefault();
        DateFormat timeFormat = SimpleDateFormat.getInstanceForSkeleton("zzzz");
        DateFormat offsetFormat = SimpleDateFormat.getInstanceForSkeleton("ZZZZ");
        String newTime = formatInZone(timeFormat, tz, unixEpochTimeMillis);
        String newOffset = formatInZone(offsetFormat, tz, unixEpochTimeMillis);

        return mRes.getString(resId, duration, newTime, newOffset);
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        synchronized (mConfigurationLock) {
            pw.println("currentUserId=" + mConfigurationInternal.getUserId());
            pw.println(
                    "timeZoneOffsetChangeNotificationsEnabledSetting="
                            + mConfigurationInternal
                                    .getTimeZoneOffsetChangeNotificationsEnabledBehavior());
        }
    }

    private void clearNotificationForUser(@UserIdInt int userId) {
        mNotificationManager.cancelAsUser(
                NOTIFICATION_TAG, TIME_ZONE_OFFSET_CHANGE_NOTIFICATION_ID, UserHandle.of(userId));
    }

    private static String formatInZone(
            DateFormat timeFormat, TimeZone timeZone, long unixEpochTimeMillis) {
        timeFormat.setTimeZone(timeZone);
        return timeFormat.format(unixEpochTimeMillis);
    }

    private boolean areNotificationsEnabled() {
        synchronized (mConfigurationLock) {
            return mConfigurationInternal.getTimeZoneOffsetChangeNotificationsEnabledBehavior();
        }
    }
}
