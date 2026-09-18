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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

import android.app.Notification;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.timezone.flags.Flags;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.timezonedetector.TimeZoneOffsetChangeListener.TimeZoneOffsetChangeEvent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.InstantSource;
import java.util.TimeZone;

/** White-box unit tests for {@link NotifyingTimeZoneOffsetChangeStatusChangeListener}. */
@RunWith(JUnit4.class)
@EnableFlags({
    Flags.FLAG_ENABLE_TIME_ZONE_OFFSET_CHANGE_BROADCAST,
    Flags.FLAG_TIME_ZONE_OFFSET_CHANGE_NOTIFICATIONS
})
public class NotifyingTimeZoneOffsetChangeListenerTest {

    private static final String INTERACT_ACROSS_USERS_FULL_PERMISSION =
            "android.permission.INTERACT_ACROSS_USERS_FULL";

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mContext;
    private UiAutomation mUiAutomation;
    private FakeNotificationManager mNotificationManager;
    private FakeServiceConfigAccessor mServiceConfigAccessor;
    private int mUid;
    private NotifyingTimeZoneOffsetChangeListener mListener;

    @Before
    public void setUp() {
        mUid = Process.myUid();

        ConfigurationInternal config = new ConfigurationInternal.Builder().setUserId(mUid).build();

        mServiceConfigAccessor = spy(new FakeServiceConfigAccessor());
        mServiceConfigAccessor.initializeCurrentUserConfiguration(config);

        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(INTERACT_ACROSS_USERS_FULL_PERMISSION);

        mNotificationManager = new FakeNotificationManager(mContext, InstantSource.system());

        mListener =
                new NotifyingTimeZoneOffsetChangeListener(
                        mContext,
                        mNotificationManager,
                        null /* alarmManager */,
                        mServiceConfigAccessor);
    }

    @Test
    public void testProcess_notificationsDisabled() {
        setNotificationsEnabled(false);

        TimeZoneOffsetChangeEvent event =
                new TimeZoneOffsetChangeEvent(
                        SystemClock.elapsedRealtime(), System.currentTimeMillis(), 0, 3600);
        mListener.process(event);

        assertEquals(0, mNotificationManager.getNotifications().size());
    }

    @Test
    public void testProcess_notificationsEnabled_sendsNotification() {
        setNotificationsEnabled(true);

        TimeZoneOffsetChangeEvent event =
                new TimeZoneOffsetChangeEvent(
                        SystemClock.elapsedRealtime(), System.currentTimeMillis(), 0, 3600);
        mListener.process(event);

        assertEquals(1, mNotificationManager.getNotifications().size());
    }

    @Test
    public void testNotificationContent_forwardChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        setNotificationsEnabled(true);

        // From GMT to GMT+1
        TimeZoneOffsetChangeEvent event =
                new TimeZoneOffsetChangeEvent(
                        SystemClock.elapsedRealtime(), System.currentTimeMillis(), 0, 3600);
        mListener.process(event);

        assertEquals(1, mNotificationManager.getNotifications().size());
        Notification notification = mNotificationManager.getNotifications().get(0);

        String title =
                mContext.getString(
                        com.android.internal.R.string.time_zone_offset_change_notification_title);
        assertEquals(title, notification.extras.getString(Notification.EXTRA_TITLE));

        String body = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        // Exact string is localized. Check for components.
        assertTrue(body.contains("1 hour"));
        assertTrue(body.contains("forward"));
    }

    @Test
    public void testNotificationContent_backwardChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        setNotificationsEnabled(true);

        // From GMT+1 to GMT
        TimeZoneOffsetChangeEvent event =
                new TimeZoneOffsetChangeEvent(
                        SystemClock.elapsedRealtime(), System.currentTimeMillis(), 3600, 0);
        mListener.process(event);

        assertEquals(1, mNotificationManager.getNotifications().size());
        Notification notification = mNotificationManager.getNotifications().get(0);

        String title =
                mContext.getString(
                        com.android.internal.R.string.time_zone_offset_change_notification_title);
        assertEquals(title, notification.extras.getString(Notification.EXTRA_TITLE));

        String body = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        assertTrue(body.contains("1 hour"));
        assertTrue(body.contains("back"));
    }

    @Test
    public void testNotificationContent_30minuteChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        setNotificationsEnabled(true);

        TimeZoneOffsetChangeEvent event =
                new TimeZoneOffsetChangeEvent(
                        SystemClock.elapsedRealtime(),
                        System.currentTimeMillis(),
                        0,
                        1800); // +30 mins
        mListener.process(event);

        assertEquals(1, mNotificationManager.getNotifications().size());
        Notification notification = mNotificationManager.getNotifications().get(0);
        String body = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        assertTrue(body.contains("30 minutes"));
    }

    @Test
    public void testNotificationContent_90minuteChange() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        setNotificationsEnabled(true);

        TimeZoneOffsetChangeEvent event =
                new TimeZoneOffsetChangeEvent(
                        SystemClock.elapsedRealtime(),
                        System.currentTimeMillis(),
                        0,
                        5400); // +90 mins
        mListener.process(event);

        assertEquals(1, mNotificationManager.getNotifications().size());
        Notification notification = mNotificationManager.getNotifications().get(0);
        String body = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
        assertTrue(body.contains("1 hour"));
        assertTrue(body.contains("30 minutes"));
    }

    private void setNotificationsEnabled(boolean enabled) {
        ConfigurationInternal oldConfig =
                mServiceConfigAccessor.getCurrentUserConfigurationInternal();
        ConfigurationInternal newConfig =
                new ConfigurationInternal.Builder(oldConfig)
                        .setTimeZoneOffsetChangeNotificationsEnabledSetting(enabled)
                        .setTimeZoneOffsetChangeNotificationsSupported(true)
                        .build();
        mServiceConfigAccessor.simulateCurrentUserConfigurationInternalChange(newConfig);
    }
}
