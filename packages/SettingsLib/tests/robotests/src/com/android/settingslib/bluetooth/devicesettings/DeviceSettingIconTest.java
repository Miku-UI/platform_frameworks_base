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

package com.android.settingslib.bluetooth.devicesettings;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class DeviceSettingIconTest {
    private static final Bitmap CUSTOMIZED_ICON =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

    @Test
    public void build_withoutDefaultIcon_successfully() {
        DeviceSettingIcon unused =
                new DeviceSettingIcon.Builder()
                        .setCustomizedIcon(CUSTOMIZED_ICON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();
    }

    @Test
    public void build_withoutCustomizedIcon_successfully() {
        DeviceSettingIcon unused =
                new DeviceSettingIcon.Builder()
                        .setDefaultIcon(
                                DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_WARNING)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();
    }

    @Test
    public void build_withoutExtras_successfully() {
        DeviceSettingIcon unused =
                new DeviceSettingIcon.Builder()
                        .setDefaultIcon(
                                DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_WARNING)
                        .setCustomizedIcon(CUSTOMIZED_ICON)
                        .build();
    }

    @Test
    public void build_withAllFields_successfully() {
        DeviceSettingIcon unused =
                new DeviceSettingIcon.Builder()
                        .setDefaultIcon(
                                DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_WARNING)
                        .setCustomizedIcon(CUSTOMIZED_ICON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();
    }

    @Test
    public void getMethods() {
        DeviceSettingIcon icon =
                new DeviceSettingIcon.Builder()
                        .setDefaultIcon(
                                DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_WARNING)
                        .setCustomizedIcon(CUSTOMIZED_ICON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        assertThat(icon.getDefaultIcon())
                .isEqualTo(DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_WARNING);
        assertThat(icon.getCustomizedIcon()).isSameInstanceAs(CUSTOMIZED_ICON);
        assertThat(icon.getExtras().getString("key1")).isEqualTo("value1");
    }

    @Test
    public void parcelOperation() {
        DeviceSettingIcon icon =
                new DeviceSettingIcon.Builder()
                        .setDefaultIcon(
                                DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_WARNING)
                        .setCustomizedIcon(null)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        DeviceSettingIcon fromParcel = writeAndRead(icon);

        assertThat(fromParcel.getDefaultIcon()).isEqualTo(icon.getDefaultIcon());
        assertThat(fromParcel.getCustomizedIcon()).isNull();
        assertThat(fromParcel.getExtras().getString("key1"))
                .isEqualTo(icon.getExtras().getString("key1"));
    }

    private Bundle buildBundle(String key, String value) {
        Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    private DeviceSettingIcon writeAndRead(DeviceSettingIcon state) {
        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return DeviceSettingIcon.CREATOR.createFromParcel(parcel);
    }
}
