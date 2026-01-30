/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.doze;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.TestCase.assertEquals;

import android.hardware.display.AmbientDisplayConfiguration;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.display.feature.flags.Flags;
import com.android.systemui.SysuiTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DozeConfigurationTest extends SysuiTestCase {

    private AmbientDisplayConfiguration mDozeConfig;

    @Before
    public void setup() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_pulseOnNotificationsAvailable, true);
        mContext.getOrCreateTestableResources().addOverride(R.string.config_dozeComponent,
                "FakeDozeComponent");
        mContext.getOrCreateTestableResources().addOverride(
                R.array.config_dozeTapSensorPostureMapping, new String[]{"posture1", "posture2"});
        mContext.getOrCreateTestableResources().addOverride(R.string.config_dozeDoubleTapSensorType,
                "FakeDoubleTapSensorType");
        mDozeConfig = new AmbientDisplayConfiguration(mContext);
    }

    @Test
    public void alwaysOn_followsConfigByDefault() {
        if (!mDozeConfig.alwaysOnAvailable()) {
            return;
        }

        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.DOZE_ALWAYS_ON, null, UserHandle.USER_CURRENT);
        boolean defaultValue = mContext.getResources()
                .getBoolean(com.android.internal.R.bool.config_dozeAlwaysOnEnabled);
        assertEquals(defaultValue, mDozeConfig.alwaysOnEnabled(UserHandle.USER_CURRENT));
    }

    @Test
    @EnableFlags(Flags.FLAG_CONFIGURABLE_DEFAULT_DOZE_VALUES)
    public void pulseOnNotificationEnabledByDefault() {
        mContext.getOrCreateTestableResources().addOverride(R.bool.config_dozeEnabled, true);
        mDozeConfig = new AmbientDisplayConfiguration(mContext);
        assertThat(mDozeConfig.pulseOnNotificationEnabled(UserHandle.USER_CURRENT)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_CONFIGURABLE_DEFAULT_DOZE_VALUES)
    public void pulseOnNotificationDisabledByDefault() {
        mContext.getOrCreateTestableResources().addOverride(R.bool.config_dozeEnabled, false);
        mDozeConfig = new AmbientDisplayConfiguration(mContext);
        assertThat(mDozeConfig.pulseOnNotificationEnabled(UserHandle.USER_CURRENT)).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_CONFIGURABLE_DEFAULT_DOZE_VALUES)
    public void tapGestureEnabledByDefault() {
        mContext.getOrCreateTestableResources().addOverride(R.bool.config_dozeTapGestureEnabled,
                true);
        mDozeConfig = new AmbientDisplayConfiguration(mContext);
        assertThat(mDozeConfig.tapGestureEnabled(UserHandle.USER_CURRENT)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_CONFIGURABLE_DEFAULT_DOZE_VALUES)
    public void tapGestureDisabledByDefault() {
        mContext.getOrCreateTestableResources().addOverride(R.bool.config_dozeTapGestureEnabled,
                false);
        mDozeConfig = new AmbientDisplayConfiguration(mContext);
        assertThat(mDozeConfig.tapGestureEnabled(UserHandle.USER_CURRENT)).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_CONFIGURABLE_DEFAULT_DOZE_VALUES)
    public void doubleTapGestureEnabledByDefault() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_dozeDoubleTapGestureEnabled, true);
        mDozeConfig = new AmbientDisplayConfiguration(mContext);
        assertThat(mDozeConfig.doubleTapGestureEnabled(UserHandle.USER_CURRENT)).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_CONFIGURABLE_DEFAULT_DOZE_VALUES)
    public void doubleTapGestureDisabledByDefault() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_dozeDoubleTapGestureEnabled, false);
        mDozeConfig = new AmbientDisplayConfiguration(mContext);
        assertThat(mDozeConfig.doubleTapGestureEnabled(UserHandle.USER_CURRENT)).isFalse();
    }
}
