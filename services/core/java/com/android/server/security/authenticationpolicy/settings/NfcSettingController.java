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

import android.content.Context;
import android.nfc.NfcAdapter;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/** Setting controller for whether NFC is enabled. */
class NfcSettingController implements SettingController<Boolean> {
    private static final String TAG = "NfcSettingController";

    private final Context mContext;
    private boolean mSkipSecurityFeaturesForTest = false;

    NfcSettingController(Context context) {
        mContext = context;
    }

    @Nullable
    private NfcAdapter getNfcAdapter() {
        return NfcAdapter.getDefaultAdapter(mContext);
    }

    @Override
    public void storeOriginalValue(@NonNull SettingState<Boolean> state, int userId)
            throws Exception {
        NfcAdapter nfcAdapter = getNfcAdapter();
        if (nfcAdapter == null) {
            Slog.w(TAG, "NFC adapter is not available, cannot store original value.");
        } else {
            state.setOriginalValue(nfcAdapter.isEnabled());
        }
    }

    @Override
    public void applySecureLockDeviceValue(@NonNull SettingState<Boolean> state, int userId)
            throws Exception {
        if (mSkipSecurityFeaturesForTest) {
            Slog.d(TAG, "Skipping NFC disable for test.");
            return;
        }
        NfcAdapter nfcAdapter = getNfcAdapter();
        if (nfcAdapter == null) {
            Slog.w(TAG, "NFC adapter is not available, cannot apply secure lock device value.");
        } else {
            nfcAdapter.disable();
        }
    }

    @Override
    public void restoreFromOriginalValue(@NonNull SettingState<Boolean> state, int userId)
            throws Exception {
        NfcAdapter nfcAdapter = getNfcAdapter();
        if (nfcAdapter == null) {
            Slog.w(TAG, "NFC adapter is not available, cannot restore original value.");
        } else {
            Boolean originalValue = state.getOriginalValue();
            if (originalValue != null && originalValue) {
                nfcAdapter.enable();
            } else {
                Slog.w(TAG, "NFC was disabled prior to secure lock device, leave unchanged.");
            }
        }
    }

    @Override
    public void serializeOriginalValue(@NonNull String settingKey, @NonNull Boolean originalValue,
            @NonNull TypedXmlSerializer serializer) throws IOException {
        serializer.text(Boolean.toString(originalValue));
    }

    @Override
    public Boolean deserializeOriginalValue(@NonNull TypedXmlPullParser parser,
            @NonNull String settingKey) throws IOException, XmlPullParserException {
        return Boolean.parseBoolean(parser.nextText());
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
