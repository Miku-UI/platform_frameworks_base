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

package com.android.internal.util;

import static android.media.Utils.VIBRATION_URI_PARAM;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;
import android.testing.TestableContext;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.notification.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@SmallTest
@DisabledOnRavenwood(blockedBy = VibrationStatsWriterTest.class)
@EnableFlags({Flags.FLAG_NOTIFICATION_VIBRATION_IN_SOUND_URI,
        android.media.audio.Flags.FLAG_ENABLE_RINGTONE_HAPTICS_CUSTOMIZATION})
@RunWith(AndroidJUnit4.class)
public class VibrationStatsWriterTest {
    public final Context mContext = spy(new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext().getApplicationContext()));
    private static final Uri DEFAULT_RINGTONE_URI =
            Uri.parse("content://media/internal/audio/media/10?title=DefaultRingtone&canonical=1");
    private static final String VIBRATION_FILE_PATH = "product/media/vibration";
    private static final String RINGTONE_URI_PATH = "ringtone";
    private VibrationStatsWriter mVibrationStatsWriter;

    @Before
    public void setUp() {
        Resources mockResource = Mockito.mock(Resources.class);
        when(mContext.getResources()).thenReturn(mockResource);
        when(mockResource.getBoolean(
                com.android.internal.R.bool.config_ringtoneVibrationSettingsSupported))
                .thenReturn(true);
        String[] ringtonePatternToIdMap = new String[]{"Bumps.xml,102"};
        String[] notificationPatternToIdMap = new String[]{"Taps.xml,202"};
        doReturn(ringtonePatternToIdMap).when(mockResource).getStringArray(
                R.array.config_ringtoneVibrationPatternToMetricIdMapping);
        doReturn(notificationPatternToIdMap).when(mockResource).getStringArray(
                R.array.config_notificationVibrationPatternToMetricIdMapping);
        VibrationStatsWriter.setInstance(null);
        mVibrationStatsWriter = spy(VibrationStatsWriter.getInstance(mContext));
    }

    @Test
    public void testNoLogWhenRingtoneVibrationSettingsNotSupported() {
        Resources mockResource = Mockito.mock(Resources.class);
        Context context = spy(new TestableContext(
                InstrumentationRegistry.getInstrumentation().getContext()));
        when(context.getResources()).thenReturn(mockResource);
        String[] ringtonePatternToIdMap = new String[]{"Bumps.xml,102"};
        String[] notificationPatternToIdMap = new String[]{"Taps.xml,202"};
        doReturn(ringtonePatternToIdMap).when(mockResource).getStringArray(
                R.array.config_ringtoneVibrationPatternToMetricIdMapping);
        doReturn(notificationPatternToIdMap).when(mockResource).getStringArray(
                R.array.config_notificationVibrationPatternToMetricIdMapping);
        // Intentionally set config_ringtoneVibrationSettingsSupported as false (not supported)
        when(mockResource.getBoolean(
                com.android.internal.R.bool.config_ringtoneVibrationSettingsSupported))
                .thenReturn(false);

        // No crash when getInstance(context) called
        VibrationStatsWriter.setInstance(null);
        VibrationStatsWriter vibrationStatsWriter = spy(VibrationStatsWriter.getInstance(context));
        vibrationStatsWriter.logCustomVibrationPatternEventIfNeeded(
                VibrationStatsWriter.VIBRATION_PATTERN_PLAYED, RingtoneManager.TYPE_NOTIFICATION,
                Settings.System.DEFAULT_NOTIFICATION_URI);

        // Never log when config_ringtoneVibrationSettingsSupported is false
        verify(vibrationStatsWriter, never()).logCustomVibrationPatternEvent(
                eq(VibrationStatsWriter.VIBRATION_PATTERN_PLAYED),
                eq(RingtoneManager.TYPE_NOTIFICATION), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void testInvalidVibrationPatternsMapping_noCrash_neverLog() throws Exception {
        Resources mockResource = Mockito.mock(Resources.class);
        Context context = spy(new TestableContext(
                InstrumentationRegistry.getInstrumentation().getContext()));
        when(context.getResources()).thenReturn(mockResource);
        // Invalid (empty) pattern to Id mappings
        String[] ringtonePatternToIdMap = new String[]{""};
        String[] notificationPatternToIdMap = new String[]{""};
        doReturn(ringtonePatternToIdMap).when(mockResource).getStringArray(
                R.array.config_ringtoneVibrationPatternToMetricIdMapping);
        doReturn(notificationPatternToIdMap).when(mockResource).getStringArray(
                R.array.config_notificationVibrationPatternToMetricIdMapping);

        // No crash when getInstance(context) called
        VibrationStatsWriter.setInstance(null);
        VibrationStatsWriter vibrationStatsWriter = spy(VibrationStatsWriter.getInstance(context));

        vibrationStatsWriter.logCustomVibrationPatternEventIfNeeded(
                VibrationStatsWriter.VIBRATION_PATTERN_PLAYED, RingtoneManager.TYPE_NOTIFICATION,
                Settings.System.DEFAULT_NOTIFICATION_URI);

        // Never log when no valid pattern to Id mappings
        verify(vibrationStatsWriter, never()).logCustomVibrationPatternEvent(
                eq(VibrationStatsWriter.VIBRATION_PATTERN_PLAYED),
                eq(RingtoneManager.TYPE_NOTIFICATION), anyInt(), anyInt(), anyBoolean());

        // No crash when getInstance(context) called
        VibrationStatsWriter.setInstance(null);
        vibrationStatsWriter = spy(VibrationStatsWriter.getInstance(context));

        vibrationStatsWriter.logCustomVibrationPatternEventIfNeeded(
                VibrationStatsWriter.VIBRATION_PATTERN_PLAYED, RingtoneManager.TYPE_RINGTONE,
                Settings.System.DEFAULT_RINGTONE_URI);

        // Never log when no valid pattern to Id mappings
        verify(vibrationStatsWriter, never()).logCustomVibrationPatternEvent(
                eq(VibrationStatsWriter.VIBRATION_PATTERN_PLAYED),
                eq(RingtoneManager.TYPE_RINGTONE), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    public void testVibrationStatsWriter_logWhenRingtonePlayed() {
        final Ringtone ringtone = spy(new Ringtone(mContext, false));
        // The vibration uri specifies a custom vibration
        final String testVibrationFileName = "Bumps.xml";
        Uri vibrationUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_FILE)
                .appendPath(VIBRATION_FILE_PATH)
                .appendPath(RINGTONE_URI_PATH)
                .appendPath(testVibrationFileName)
                .build();
        Uri testRingtoneUri = DEFAULT_RINGTONE_URI.buildUpon().appendQueryParameter(
                VIBRATION_URI_PARAM, vibrationUri.toString()).build();
        ringtone.setUri(testRingtoneUri);
        doReturn(mVibrationStatsWriter).when(ringtone).getVibrationStatsWriter();

        ringtone.play();
        verify(mVibrationStatsWriter).logCustomVibrationPatternEvent(
                eq(VibrationStatsWriter.VIBRATION_PATTERN_PLAYED),
                eq(RingtoneManager.TYPE_RINGTONE), eq(102), anyInt(), eq(false));
    }

    @Test
    public void testVibrationStatsWriter_logDefaultNotificationUri() {
        final String testVibrationFileName = "Taps.xml";
        Uri vibrationUri = new Uri.Builder()
                .scheme(ContentResolver.SCHEME_FILE)
                .appendPath(VIBRATION_FILE_PATH)
                .appendPath(RINGTONE_URI_PATH)
                .appendPath(testVibrationFileName)
                .build();
        // Set a custom vibration uri into the actual default ringtone uri for notification
        Uri testRingtoneUri = DEFAULT_RINGTONE_URI.buildUpon().appendQueryParameter(
                VIBRATION_URI_PARAM, vibrationUri.toString()).build();
        Settings.System.putString(mContext.getContentResolver(),
                Settings.System.NOTIFICATION_SOUND, testRingtoneUri.toString());

        // Simulate logging symbolic default notification uri when the vibration played
        mVibrationStatsWriter.logCustomVibrationPatternEventIfNeeded(
                VibrationStatsWriter.VIBRATION_PATTERN_PLAYED, RingtoneManager.TYPE_NOTIFICATION,
                Settings.System.DEFAULT_NOTIFICATION_URI);

        // Verify the writer will log the corresponding custom vibration ID through the actual uri.
        verify(mVibrationStatsWriter).logCustomVibrationPatternEvent(
                eq(VibrationStatsWriter.VIBRATION_PATTERN_PLAYED),
                eq(RingtoneManager.TYPE_NOTIFICATION), eq(202), anyInt(), eq(false));
    }
}
