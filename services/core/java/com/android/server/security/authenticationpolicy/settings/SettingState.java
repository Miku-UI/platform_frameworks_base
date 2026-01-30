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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Stores the expected state of a setting during secure lock device, as well as the original state
 * of the setting at the time secure lock device is enabled, in order to restore to this state upon
 * secure lock device being disabled.
 *
 * @param <T> The type of the setting.
 */
public class SettingState<T> {
    @NonNull private final String mSettingKey;
    @NonNull private final SettingType mSettingType;
    @NonNull private final T mSecureLockDeviceValue;
    @Nullable private T mOriginalValue;

    SettingState(@NonNull String settingKey, @NonNull SettingType settingType,
            @NonNull T secureLockDeviceValue) {
        mSettingKey = settingKey;
        mSettingType = settingType;
        mSecureLockDeviceValue = secureLockDeviceValue;
    }

    @NonNull
    public String getSettingKey() {
        return mSettingKey;
    }

    @NonNull
    public SettingType getSettingType() {
        return mSettingType;
    }

    @Nullable
    public T getOriginalValue() {
        return mOriginalValue;
    }

    /**
     * Sets the original value of this setting.
     * @param originalValue Value to store as original value of this setting.
     */
    public void setOriginalValue(@Nullable T originalValue) {
        mOriginalValue = originalValue;
    }

    @NonNull
    public T getSecureLockDeviceValue() {
        return mSecureLockDeviceValue;
    }

    @Override
    public String toString() {
        return "[Key " + mSettingKey + ", Type " + mSettingType + ", Secure lock device value "
                + mSecureLockDeviceValue + ", Original value " + mOriginalValue + "]";
    }

    public enum SettingType {
        BOOLEAN,
        STRING,
        INTEGER,
        INTEGER_PAIR,
        STRING_SET,
        USB_PORT_MAP
    }
}
