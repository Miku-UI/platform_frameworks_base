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
public final class ButtonInfoTest {

    @Test
    public void build_withoutLabel_fail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    ButtonInfo unused =
                            new ButtonInfo.Builder()
                                    .setAction(DeviceSettingAction.EMPTY_ACTION)
                                    .setExtras(buildBundle("key1", "value1"))
                                    .build();
                });
    }

    @Test
    public void build_withoutExtra_successfully() {
        ButtonInfo unused =
                new ButtonInfo.Builder()
                        .setLabel("label")
                        .setAction(DeviceSettingAction.EMPTY_ACTION)
                        .build();
    }

    @Test
    public void build_withoutAction_successfully() {
        ButtonInfo unused =
                new ButtonInfo.Builder()
                        .setLabel("label")
                        .setExtras(buildBundle("key1", "value1"))
                        .build();
    }

    @Test
    public void build_withAllFields_successfully() {
        ButtonInfo unused =
                new ButtonInfo.Builder()
                        .setLabel("label")
                        .setAction(DeviceSettingAction.EMPTY_ACTION)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();
    }

    @Test
    public void getMethods() {
        DeviceSettingAction action = DeviceSettingAction.EMPTY_ACTION;
        ButtonInfo info =
                new ButtonInfo.Builder()
                        .setLabel("label")
                        .setAction(action)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        assertThat(info.getLabel()).isEqualTo("label");
        assertThat(info.getAction()).isSameInstanceAs(action);
        assertThat(info.getExtras().getString("key1")).isEqualTo("value1");
    }

    @Test
    public void parcelOperation() {
        DeviceSettingAction action = DeviceSettingAction.EMPTY_ACTION;
        ButtonInfo info =
                new ButtonInfo.Builder()
                        .setLabel("label")
                        .setAction(action)
                        .setExtras(buildBundle("key1", "value1"))
                        .build();

        ButtonInfo fromParcel = writeAndRead(info);

        assertThat(fromParcel.getLabel()).isEqualTo(info.getLabel());
        assertThat(fromParcel.getAction()).isEqualTo(info.getAction());
        assertThat(fromParcel.getExtras().getString("key1"))
                .isEqualTo(info.getExtras().getString("key1"));
    }

    private Bundle buildBundle(String key, String value) {
        Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    private ButtonInfo writeAndRead(ButtonInfo state) {
        Parcel parcel = Parcel.obtain();
        state.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return ButtonInfo.CREATOR.createFromParcel(parcel);
    }
}
