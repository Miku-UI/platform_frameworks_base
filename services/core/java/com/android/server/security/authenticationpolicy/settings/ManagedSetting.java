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

import android.util.Log;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Stores the expected state of a setting during secure lock device, as well as the original state
 * of the setting at the time secure lock device is enabled, in order to restore to this state upon
 * secure lock device being disabled.
 *
 * @param <T> The type of the setting.
 */
public class ManagedSetting<T> {
    private static final String TAG = "ManagedSetting";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final String XML_TAG_SETTING_ORIGINAL_VALUE = "setting-original-value";
    private final SettingState<T> mState;
    private final SettingController<T> mController;

    ManagedSetting(@NonNull SettingState<T> state, @NonNull SettingController<T> controller) {
        mState = state;
        mController = controller;
    }

    /**
     * Stores the original value of the setting in [SettingState.originalValue]
     * @param userId The user id to store the setting value for.
     */
    public void storeOriginalValue(int userId) {
        try {
            mController.storeOriginalValue(mState, userId);
            if (DEBUG) {
                Slog.d(TAG, "Stored original value to SettingState " + mState + " for user "
                        + userId);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to store original value to SettingState " + mState + " for user "
                    + userId, e);
        }
    }

    /**
     * Applies the expected setting value from [SettingState.secureLockDeviceValue]
     *
     * @param userId The user id to apply the setting value to.
     */
    public void applySecureLockDeviceValue(int userId) {
        try {
            mController.applySecureLockDeviceValue(mState, userId);
            if (DEBUG) {
                Slog.d(TAG, "Applied secure lock device value to SettingState " + mState
                        + " for user " + userId);
            }
        } catch (Exception e) {
            Slog.d(TAG, "Failed to apply secure lock device value to SettingState " + mState
                    + " for user " + userId, e);
        }
    }

    /**
     * Retrieves the original value of the setting with [SettingState.originalValue] and restores
     * the
     * setting to this state.
     *
     * @param userId The user id to apply the setting value to.
     */
    public void restoreFromOriginalValue(int userId) {
        try {
            mController.restoreFromOriginalValue(mState, userId);
            if (DEBUG) {
                Slog.d(TAG, "SettingState " + mState + " restored to original value before "
                        + "secure lock device was enabled by user " + userId);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to restore original value " + mState.getOriginalValue()
                    + " on setting with key " + mState.getSettingKey() + " using controller "
                    + mController.getClass().getSimpleName(), e);
        }
    }

    /**
     * Serializes original value using {@code serializer}.
     *
     * @param serializer The serializer used to serialize the original value.
     */
    public void serializeOriginalValue(@NonNull TypedXmlSerializer serializer) {
        String settingKey = mState.getSettingKey();
        T originalValue = mState.getOriginalValue();
        if (originalValue == null) {
            Slog.w(TAG, "Original value is null for setting with key " + settingKey);
            return;
        }
        Slog.w(TAG, "Serializing original value for setting with key " + settingKey);
        try {
            serializer.startTag(null, XML_TAG_SETTING_ORIGINAL_VALUE);
            mController.serializeOriginalValue(settingKey, originalValue, serializer);
            serializer.endTag(null, XML_TAG_SETTING_ORIGINAL_VALUE);
        } catch (IOException | XmlPullParserException e) {
            Slog.e(TAG, "Error serializing original value for setting with key: "
                    + settingKey, e);
        }
    }

    /**
     * Deserializes the original value from the XML and sets {@code SettingState.originalValue} to
     * the retrieved value.
     * @param parser The XML parser.
     * @param key The key of the setting being deserialized.
     */
    public void deserializeAndRestoreOriginalValueFromXml(TypedXmlPullParser parser, String key) {
        try {
            T originalValue = mController.deserializeOriginalValue(parser, key);
            mState.setOriginalValue(originalValue);
            if (DEBUG) {
                Slog.d(TAG, "Deserialized original value for setting " + this);
            }
        } catch (IOException | XmlPullParserException e) {
            Slog.e(TAG, "Error deserializing original value for setting " + this, e);
        }
    }

    /** String identifier for the [SettingState] */
    @NonNull
    public String getSettingKey() {
        return mState.getSettingKey();
    }

    /** [SettingState.SettingType] of the [SettingState] */
    @NonNull
    public SettingState.SettingType getSettingType() {
        return mState.getSettingType();
    }

    @Override
    public String toString() {
        return "[Key " + mState.getSettingKey() + ", Type " + mState.getSettingType()
                + ", Original value " + mState.getOriginalValue() + ", Secure lock device value "
                + mState.getSecureLockDeviceValue() + "]";
    }
}
