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

package com.android.server.security.authenticationpolicy.settings;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashMap;
import java.util.Map;

public class SystemSettingControllerTest {
    @Rule public MockitoRule mockito = MockitoJUnit.rule();

    private static final Map<String, Integer> SYSTEM_SETTINGS_SECURE_LOCK_DEVICE_VALUES =
            Map.of(Settings.System.BLUETOOTH_DISCOVERABILITY, 0);
    private final Map<String, Integer> mSystemSettingsOriginalOrDefaultValues =
            new HashMap<>();
    private Map<String, Integer> mSystemSettingsDefaultValues;


    private ContentResolver mContentResolver;
    private SystemSettingController mSystemSettingController;
    private UserManager mUserManager;
    private UserInfo mTestUser;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        // Creates test user for applying settings
        mTestUser = mUserManager.createUser("TestUser1", UserInfo.FLAG_GUEST);
        mContentResolver = context.getContentResolver();
        mSystemSettingController = new SystemSettingController(context);
        mSystemSettingsDefaultValues = mSystemSettingController.initializeDefaultValues();
        SYSTEM_SETTINGS_SECURE_LOCK_DEVICE_VALUES.keySet().forEach(settingKey -> {
            int originalValue = Settings.System.getIntForUser(mContentResolver, settingKey,
                    mSystemSettingsDefaultValues.get(settingKey), mTestUser.id);
            mSystemSettingsOriginalOrDefaultValues.put(settingKey, originalValue);
        });
    }

    @After
    public void tearDown() {
        mUserManager.removeUser(mTestUser.id);
    }


    @Test
    public void testStoreOriginalValue_successfullyStoresOriginalOrDefaultValue() {
        SYSTEM_SETTINGS_SECURE_LOCK_DEVICE_VALUES.forEach((settingKey, secureLockDeviceValue) -> {
            try {
                SettingState<Integer> settingState = new SettingState<>(settingKey,
                        SettingState.SettingType.INTEGER, secureLockDeviceValue);
                int originalOrDefaultValue = mSystemSettingsOriginalOrDefaultValues.get(
                        settingKey);

                mSystemSettingController.storeOriginalValue(settingState, mTestUser.id);

                assertThat(settingState.getOriginalValue()).isEqualTo(originalOrDefaultValue);
            } catch (Exception e) {
                throw new RuntimeException("Failed to store original value for " + settingKey, e);
            }
        });
    }

    @Test
    public void testApplySecureLockDeviceValue_successfullyAppliesSecureLockDeviceValue() {
        SYSTEM_SETTINGS_SECURE_LOCK_DEVICE_VALUES.forEach((settingKey, secureLockDeviceValue) -> {
            SettingState<Integer> settingState = new SettingState<>(settingKey,
                    SettingState.SettingType.INTEGER, secureLockDeviceValue);

            try {
                mSystemSettingController.applySecureLockDeviceValue(settingState, mTestUser.id);

                assertThat(Settings.System.getIntForUser(mContentResolver, settingKey,
                        mTestUser.id)).isEqualTo(secureLockDeviceValue);
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply secure lock device value for "
                        + settingKey, e);
            }
        });
    }

    @Test
    public void testRestoreFromOriginalValue_successfullyRestoresOriginalOrDefaultValue() {
        SYSTEM_SETTINGS_SECURE_LOCK_DEVICE_VALUES.forEach((settingKey, secureLockDeviceValue) -> {
            int originalOrDefaultValue = mSystemSettingsOriginalOrDefaultValues.get(settingKey);
            SettingState<Integer> settingState = new SettingState<>(settingKey,
                    SettingState.SettingType.INTEGER, secureLockDeviceValue);

            try {
                mSystemSettingController.storeOriginalValue(settingState, mTestUser.id);
                mSystemSettingController.applySecureLockDeviceValue(settingState, mTestUser.id);
                mSystemSettingController.restoreFromOriginalValue(settingState, mTestUser.id);

                assertThat(Settings.System.getIntForUser(mContentResolver, settingKey,
                        mTestUser.id)).isEqualTo(originalOrDefaultValue);
            } catch (Exception e) {
                throw new RuntimeException("Failed to restore original or default value for "
                        + settingKey, e);
            }
        });
    }
}
