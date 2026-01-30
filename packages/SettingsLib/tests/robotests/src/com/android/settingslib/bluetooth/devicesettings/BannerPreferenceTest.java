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

import static org.junit.Assert.assertThrows;

import android.os.Bundle;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class BannerPreferenceTest {
    private static final DeviceSettingIcon ICON =
            new DeviceSettingIcon(
                    DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_WARNING,
                    null,
                    Bundle.EMPTY);
    private static final DeviceSettingAction ACTION = DeviceSettingAction.EMPTY_ACTION;
    private static final ButtonInfo POSITIVE_BUTTON =
            new ButtonInfo.Builder().setLabel("positive").setAction(ACTION).build();
    private static final ButtonInfo NEGATIVE_BUTTON =
            new ButtonInfo.Builder().setLabel("negative").setAction(ACTION).build();

    @Test
    public void build_withoutTitle_fail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    BannerPreference unused =
                            new BannerPreference.Builder()
                                    .setMessage("message")
                                    .setIcon(ICON)
                                    .setPositiveButtonInfo(POSITIVE_BUTTON)
                                    .setNegativeButtonInfo(NEGATIVE_BUTTON)
                                    .setExtras(buildBundle("key1", "value1"))
                                    .build();
                });
    }

    @Test
    public void build_withoutMessage_fail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    BannerPreference unused =
                            new BannerPreference.Builder()
                                    .setTitle("title")
                                    .setIcon(ICON)
                                    .setPositiveButtonInfo(POSITIVE_BUTTON)
                                    .setNegativeButtonInfo(NEGATIVE_BUTTON)
                                    .setExtras(buildBundle("key1", "value1"))
                                    .build();
                });
    }

    @Test
    public void build_withoutIcon_successfully() {
        BannerPreference unused =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setPositiveButtonInfo(POSITIVE_BUTTON)
                        .setNegativeButtonInfo(NEGATIVE_BUTTON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();
    }

    @Test
    public void build_withoutButtons_successfully() {
        BannerPreference unused =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setIcon(ICON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();
    }

    @Test
    public void build_withoutExtra_successfully() {
        BannerPreference unused =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setIcon(ICON)
                        .setPositiveButtonInfo(POSITIVE_BUTTON)
                        .setNegativeButtonInfo(NEGATIVE_BUTTON)
                        .build();
    }

    @Test
    public void build_withAllFields_successfully() {
        BannerPreference unused =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setIcon(ICON)
                        .setPositiveButtonInfo(POSITIVE_BUTTON)
                        .setNegativeButtonInfo(NEGATIVE_BUTTON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();
    }

    @Test
    public void getMethods() {
        BannerPreference preference =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setIcon(ICON)
                        .setPositiveButtonInfo(POSITIVE_BUTTON)
                        .setNegativeButtonInfo(NEGATIVE_BUTTON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        assertThat(preference.getTitle()).isEqualTo("title");
        assertThat(preference.getMessage()).isEqualTo("message");
        assertThat(preference.getIcon()).isEqualTo(ICON);
        assertThat(preference.getPositiveButtonInfo()).isEqualTo(POSITIVE_BUTTON);
        assertThat(preference.getNegativeButtonInfo()).isEqualTo(NEGATIVE_BUTTON);
        assertThat(preference.getExtras().getString("key1")).isEqualTo("value1");
    }

    @Test
    public void parcelOperation_withAllFields() {
        BannerPreference preference =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setIcon(ICON)
                        .setPositiveButtonInfo(POSITIVE_BUTTON)
                        .setNegativeButtonInfo(NEGATIVE_BUTTON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        BannerPreference fromParcel = writeAndRead(preference);

        assertThat(fromParcel.getTitle()).isEqualTo(preference.getTitle());
        assertThat(fromParcel.getMessage()).isEqualTo(preference.getMessage());
        assertThat(fromParcel.getIcon()).isEqualTo(preference.getIcon());
        // ButtonInfo does not implement equals(), so we compare its properties.
        assertThat(fromParcel.getPositiveButtonInfo().getLabel())
                .isEqualTo(preference.getPositiveButtonInfo().getLabel());
        assertThat(fromParcel.getNegativeButtonInfo().getLabel())
                .isEqualTo(preference.getNegativeButtonInfo().getLabel());
        assertThat(fromParcel.getExtras().getString("key1"))
                .isEqualTo(preference.getExtras().getString("key1"));
    }

    @Test
    public void parcelOperation_withNullButtons() {
        BannerPreference preference =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setIcon(ICON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        BannerPreference fromParcel = writeAndRead(preference);

        assertThat(fromParcel.getTitle()).isEqualTo(preference.getTitle());
        assertThat(fromParcel.getMessage()).isEqualTo(preference.getMessage());
        assertThat(fromParcel.getIcon()).isEqualTo(preference.getIcon());
        assertThat(fromParcel.getPositiveButtonInfo()).isNull();
        assertThat(fromParcel.getNegativeButtonInfo()).isNull();
        assertThat(fromParcel.getExtras().getString("key1"))
                .isEqualTo(preference.getExtras().getString("key1"));
    }

    @Test
    public void parcelOperation_withOnlyPositiveButton() {
        BannerPreference preference =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setIcon(ICON)
                        .setPositiveButtonInfo(POSITIVE_BUTTON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        BannerPreference fromParcel = writeAndRead(preference);

        assertThat(fromParcel.getTitle()).isEqualTo(preference.getTitle());
        assertThat(fromParcel.getMessage()).isEqualTo(preference.getMessage());
        assertThat(fromParcel.getIcon()).isEqualTo(preference.getIcon());
        assertThat(fromParcel.getPositiveButtonInfo().getLabel())
                .isEqualTo(preference.getPositiveButtonInfo().getLabel());
        assertThat(fromParcel.getNegativeButtonInfo()).isNull();
        assertThat(fromParcel.getExtras().getString("key1"))
                .isEqualTo(preference.getExtras().getString("key1"));
    }

    @Test
    public void parcelOperation_withOnlyNegativeButton() {
        BannerPreference preference =
                new BannerPreference.Builder()
                        .setTitle("title")
                        .setMessage("message")
                        .setIcon(ICON)
                        .setNegativeButtonInfo(NEGATIVE_BUTTON)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        BannerPreference fromParcel = writeAndRead(preference);

        assertThat(fromParcel.getTitle()).isEqualTo(preference.getTitle());
        assertThat(fromParcel.getMessage()).isEqualTo(preference.getMessage());
        assertThat(fromParcel.getIcon()).isEqualTo(preference.getIcon());
        assertThat(fromParcel.getPositiveButtonInfo()).isNull();
        assertThat(fromParcel.getNegativeButtonInfo().getLabel())
                .isEqualTo(preference.getNegativeButtonInfo().getLabel());
        assertThat(fromParcel.getExtras().getString("key1"))
                .isEqualTo(preference.getExtras().getString("key1"));
    }

    private Bundle buildBundle(String key, String value) {
        Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    private BannerPreference writeAndRead(BannerPreference preference) {
        Parcel parcel = Parcel.obtain();
        preference.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return BannerPreference.CREATOR.createFromParcel(parcel);
    }
}
