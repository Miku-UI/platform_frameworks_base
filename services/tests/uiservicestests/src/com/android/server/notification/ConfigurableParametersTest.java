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
package com.android.server.notification;

import static com.android.server.notification.NotificationManagerService.ConfigurableParameters.DEFAULT_NLS_COMPLETION_DURATION_MS;
import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.NLS_COMPLETION_DURATION_MS;

import static com.google.common.truth.Truth.assertThat;

import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConfigurableParametersTest {

    private NotificationManagerService.ConfigurableParameters mConfigurableParameters =
            new NotificationManagerService.ConfigurableParameters();

    @Test
    public void defaultValues() {
        assertThat(mConfigurableParameters.mNlsCompletionDurationMs).isEqualTo(
                DEFAULT_NLS_COMPLETION_DURATION_MS);
    }

    @Test
    public void testOnPropertiesChanged() {
        final long testValue = 12345L;
        Properties properties = new Properties.Builder(DeviceConfig.NAMESPACE_SYSTEMUI)
                .setLong(NLS_COMPLETION_DURATION_MS, testValue)
                .build();

        mConfigurableParameters.onPropertiesChanged(properties);

        assertThat(mConfigurableParameters.mNlsCompletionDurationMs).isEqualTo(testValue);
    }

    @Test
    public void testOnPropertiesChanged_emptyProperties() {
        Properties properties = new Properties.Builder(DeviceConfig.NAMESPACE_SYSTEMUI).build();
        mConfigurableParameters.onPropertiesChanged(properties);

        assertThat(mConfigurableParameters.mNlsCompletionDurationMs).isEqualTo(
                DEFAULT_NLS_COMPLETION_DURATION_MS);
    }
}
