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
import android.provider.Settings;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** Setting controller for {@link Settings.Global} settings. */
class GlobalSettingController implements SettingController<Integer> {
    private static final String TAG = "GlobalSettingController";

    private final ContentResolver mContentResolver;
    private boolean mSkipSecurityFeaturesForTest = false;

    GlobalSettingController(@NonNull ContentResolver contentResolver) {
        mContentResolver = contentResolver;
    }

    @Override
    public void storeOriginalValue(@NonNull SettingState<Integer> state, int userId)
            throws Exception {
        if (mSkipSecurityFeaturesForTest) {
            Slog.d(TAG, "Skipping storing global settings for test.");
            return;
        }
        int originalValue = Settings.Global.getInt(mContentResolver, state.getSettingKey());
        state.setOriginalValue(originalValue);
    }

    @Override
    public void applySecureLockDeviceValue(@NonNull SettingState<Integer> state, int userId)
            throws Exception {
        if (mSkipSecurityFeaturesForTest) {
            Slog.d(TAG, "Skipping applying global settings for test.");
            return;
        }
        int secureLockDeviceValue = state.getSecureLockDeviceValue();
        Settings.Global.putInt(mContentResolver, state.getSettingKey(), secureLockDeviceValue);
    }

    @Override
    public void restoreFromOriginalValue(@NonNull SettingState<Integer> state, int userId)
            throws Exception {
        if (mSkipSecurityFeaturesForTest) {
            Slog.d(TAG, "Skipping restoring global settings for test.");
            return;
        }
        Integer originalValue = state.getOriginalValue();
        if (originalValue != null) {
            Settings.Global.putInt(mContentResolver, state.getSettingKey(), originalValue);
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

    /**
     * Sets whether to skip security features for test.
     *
     * @param skipSecurityFeaturesForTest Whether to skip security features for test.
     */
    void setSkipSecurityFeaturesForTest(boolean skipSecurityFeaturesForTest) {
        mSkipSecurityFeaturesForTest = skipSecurityFeaturesForTest;
    }
}
