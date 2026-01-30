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

package com.android.server.theming;


import static android.content.PermissionChecker.PERMISSION_GRANTED;

import static com.google.common.truth.Truth.assertThat;

import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeSettingsPreset;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.Binder;
import android.os.UserHandle;
import android.provider.Settings;
import android.testing.TestableContext;
import android.testing.TestablePermissions;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ThemeBinderServiceTests {
    private final IThemeSettingsCallback mCallback = new IThemeSettingsCallback.Stub() {
        @Override
        public void onSettingsChanged(ThemeSettings oldSettings, ThemeSettings newSettings) {
        }
    };

    private int mUserId;
    private ThemeBinderService mUnderTest;
    private ThemeManagerInternal mInternal;
    private ThemeSettings mDefaultSettings = ThemeSettings
            .builder(0, ThemeStyle.TONAL_SPOT)
            .buildFromPreset(Color.valueOf(Color.GRAY), Color.valueOf(Color.GRAY));

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        TestableContext context = new TestableContext(InstrumentationRegistry.getTargetContext(),
                null);

        mUserId = UserHandle.getUserId(Binder.getCallingUid());

        Settings.Secure.putStringForUser(context.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, null, mUserId);

        TestablePermissions perms = context.getTestablePermissions();
        perms.setPermission(android.Manifest.permission.INTERACT_ACROSS_USERS, PERMISSION_GRANTED);

        ThemeSettingsManager themeSettingsManager = new ThemeSettingsManager();
        mInternal = new ThemeManagerInternal(context, themeSettingsManager, mDefaultSettings);
        mUnderTest = new ThemeBinderService(context, mInternal);
    }

    @Test
    public void testRegisterThemeSettingsCallback_success() {
        boolean result = mUnderTest.registerThemeSettingsCallback(mCallback);
        assertThat(result).isTrue();
    }

    @Test
    public void testRegisterThemeSettingsCallback_nullCallback_returnsFalse() {
        boolean result = mUnderTest.registerThemeSettingsCallback(null);
        assertThat(result).isFalse();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_success() {
        mUnderTest.registerThemeSettingsCallback(mCallback);
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mCallback);
        assertThat(result).isTrue();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_callbackNotRegistered_returnsFalse() {
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mCallback);
        assertThat(result).isFalse();
    }


    @Test
    public void testCallback_receivesNewValue() {
        final Color testColor = Color.valueOf(Color.parseColor("#FF0000"));
        final ThemeSettingsPreset newPayload = ThemeSettings.builder(2,
                ThemeStyle.VIBRANT).buildFromPreset(testColor, testColor);
        final ThemeSettings[] returnedOldSettings = {null};
        final ThemeSettings[] returnedNewSettings = {null};

        mUnderTest.registerThemeSettingsCallback(new IThemeSettingsCallback.Stub() {
            @Override
            public void onSettingsChanged(ThemeSettings oldSettings, ThemeSettings newSettings) {
                returnedOldSettings[0] = oldSettings;
                returnedNewSettings[0] = newSettings;
            }
        });

        // When no theme is set, oldSettings should be null.
        mInternal.notifySettingsChange(mUserId, newPayload);
        assertThat(returnedOldSettings[0]).isNull();
        assertThat(returnedNewSettings[0]).isEqualTo(newPayload);
    }

    @Test
    public void testCallback_receivesOldAndNewValue() {
        final Color oldColor = Color.valueOf(Color.BLUE);
        final ThemeSettingsPreset oldPayload = ThemeSettings.builder(1,
                ThemeStyle.TONAL_SPOT).buildFromPreset(oldColor, oldColor);

        final Color newColor = Color.valueOf(Color.RED);
        final ThemeSettingsPreset newPayload = ThemeSettings.builder(2,
                ThemeStyle.VIBRANT).buildFromPreset(newColor, newColor);

        final ThemeSettings[] returnedOldSettings = {null};
        final ThemeSettings[] returnedNewSettings = {null};

        // Set an initial theme setting.
        mInternal.updateThemeSettings(mUserId, oldPayload);

        mUnderTest.registerThemeSettingsCallback(new IThemeSettingsCallback.Stub() {
            @Override
            public void onSettingsChanged(ThemeSettings oldSettings, ThemeSettings newSettings) {
                returnedOldSettings[0] = oldSettings;
                returnedNewSettings[0] = newSettings;
            }
        });

        // Notify with the new settings.
        mInternal.notifySettingsChange(mUserId, newPayload);

        // The callback should receive the new settings correctly.
        assertThat(returnedNewSettings[0]).isEqualTo(newPayload);

        // Verify the old payload field-by-field due to timestamp precision loss on save/load.
        ThemeSettingsPreset returnedOldPreset = (ThemeSettingsPreset) returnedOldSettings[0];
        assertThat(returnedOldPreset).isNotNull();
        assertThat(returnedOldPreset.timeStamp().toEpochMilli()).isEqualTo(
                oldPayload.timeStamp().toEpochMilli());
        assertThat(returnedOldPreset.colorIndex()).isEqualTo(oldPayload.colorIndex());
        assertThat(returnedOldPreset.themeStyle()).isEqualTo(oldPayload.themeStyle());
        assertThat(returnedOldPreset.systemPalette()).isEqualTo(oldPayload.systemPalette());
        assertThat(returnedOldPreset.accentColor()).isEqualTo(oldPayload.accentColor());
    }

    @Test
    public void getThemeSettings_noSetting_returnsNull() {
        ThemeSettings settings = mUnderTest.getThemeSettings();
        assertThat(settings).isNull();
    }

    @Test
    public void getThemeSettings_withSetting_returnsStored() {
        final Color testColor = Color.valueOf(Color.RED);
        final ThemeSettingsPreset storedSettings = ThemeSettings.builder(2,
                ThemeStyle.VIBRANT).buildFromPreset(testColor, testColor);
        mInternal.updateThemeSettings(mUserId, storedSettings);

        ThemeSettings settings = mUnderTest.getThemeSettings();

        // Verify the loaded payload field-by-field due to timestamp precision loss on save/load.
        assertThat(settings).isInstanceOf(ThemeSettingsPreset.class);
        ThemeSettingsPreset returnedPreset = (ThemeSettingsPreset) settings;
        assertThat(returnedPreset).isNotNull();
        assertThat(returnedPreset.timeStamp().toEpochMilli()).isEqualTo(
                storedSettings.timeStamp().toEpochMilli());
        assertThat(returnedPreset.colorIndex()).isEqualTo(storedSettings.colorIndex());
        assertThat(returnedPreset.themeStyle()).isEqualTo(storedSettings.themeStyle());
        assertThat(returnedPreset.systemPalette()).isEqualTo(storedSettings.systemPalette());
        assertThat(returnedPreset.accentColor()).isEqualTo(storedSettings.accentColor());
    }

    @Test
    public void getThemeSettingsOrDefault_noSetting_returnsDefault() {
        ThemeSettings settings = mUnderTest.getThemeSettingsOrDefault();
        assertThat(settings).isEqualTo(mDefaultSettings);
    }

    @Test
    public void getThemeSettingsOrDefault_withSetting_returnsStored() {
        final Color testColor = Color.valueOf(Color.RED);
        final ThemeSettingsPreset storedSettings = ThemeSettings.builder(2,
                ThemeStyle.VIBRANT).buildFromPreset(testColor, testColor);
        mInternal.updateThemeSettings(mUserId, storedSettings);

        ThemeSettings settings = mUnderTest.getThemeSettingsOrDefault();

        // Verify the loaded payload field-by-field due to timestamp precision loss on save/load.
        assertThat(settings).isInstanceOf(ThemeSettingsPreset.class);
        ThemeSettingsPreset returnedPreset = (ThemeSettingsPreset) settings;
        assertThat(returnedPreset).isNotNull();
        assertThat(returnedPreset.timeStamp().toEpochMilli()).isEqualTo(
                storedSettings.timeStamp().toEpochMilli());
        assertThat(returnedPreset.colorIndex()).isEqualTo(storedSettings.colorIndex());
        assertThat(returnedPreset.themeStyle()).isEqualTo(storedSettings.themeStyle());
        assertThat(returnedPreset.systemPalette()).isEqualTo(storedSettings.systemPalette());
        assertThat(returnedPreset.accentColor()).isEqualTo(storedSettings.accentColor());
    }
}
