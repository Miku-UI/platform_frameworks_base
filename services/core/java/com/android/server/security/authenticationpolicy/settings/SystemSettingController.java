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

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Map;

/** Settings controller for {@link Settings.System} settings. */
class SystemSettingController implements SettingController<Integer> {
    @NonNull private final ContentResolver mContentResolver;
    /**
     * Default values to use as original value if not found in secure settings at the time secure
     * lock device is enabled.
     */
    private final Map<String, Integer> mSystemSettingsDefaultValues;


    SystemSettingController(@NonNull Context context) {
        mSystemSettingsDefaultValues = initializeDefaultValues();
        mContentResolver = context.getContentResolver();
    }

    @VisibleForTesting
    Map<String, Integer> initializeDefaultValues() {
        return Map.of(
                Settings.System.BLUETOOTH_DISCOVERABILITY, 1,
                Settings.System.LOCK_TO_APP_ENABLED, 0
        );
    }

    @Override
    public void storeOriginalValue(@NonNull SettingState<Integer> state, int userId)
            throws Exception {
        int defaultValue = mSystemSettingsDefaultValues.get(state.getSettingKey());
        int originalValue = Settings.System.getIntForUser(mContentResolver, state.getSettingKey(),
                defaultValue, userId);
        state.setOriginalValue(originalValue);
    }

    @Override
    public void applySecureLockDeviceValue(@NonNull SettingState<Integer> state, int userId)
            throws Exception {
        int secureLockDeviceValue = state.getSecureLockDeviceValue();
        Settings.System.putIntForUser(mContentResolver, state.getSettingKey(),
                secureLockDeviceValue, userId);
    }

    @Override
    public void restoreFromOriginalValue(@NonNull SettingState<Integer> state, int userId)
            throws Exception {
        Integer originalValue = state.getOriginalValue();
        if (originalValue != null) {
            Settings.System.putIntForUser(mContentResolver, state.getSettingKey(), originalValue,
                    userId);
        }
    }

    @Override
    public void serializeOriginalValue(@NonNull String settingKey, @NonNull Integer originalValue,
            @NonNull TypedXmlSerializer serializer) throws IOException {
        serializer.text(Integer.toString(originalValue));
    }

    @Override
    public Integer deserializeOriginalValue(@NonNull TypedXmlPullParser parser,
            @NonNull String settingKey) throws IOException, XmlPullParserException {
        return Integer.parseInt(parser.nextText());
    }
}
