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

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Interface for managing a specific setting.
 *
 * @param <T> The type of the setting.
 */
interface SettingController<T> {
    /**
     * Reads the current value of a setting at the time secure lock device is enabled and stores it
     * using {@link SettingState#setOriginalValue(Object)}.
     *
     * @param state  The state object containing the setting information.
     * @param userId The user ID to store the original value for.
     */
    void storeOriginalValue(@NonNull SettingState<T> state, int userId) throws Exception;

    /**
     * Retrieves and applies the expected setting value from {@link
     * SettingState#getSecureLockDeviceValue()}.
     *
     * @param state  The state object containing the setting information.
     * @param userId The user ID to apply the secure lock device value to.
     */
    void applySecureLockDeviceValue(@NonNull SettingState<T> state, int userId) throws Exception;

    /**
     * Retrieves the original value of the setting {@link SettingState#getOriginalValue()} and
     * restores the setting to this state.
     *
     * @param state  The state object containing the setting information.
     * @param userId The user ID to restore the original value for.
     */
    void restoreFromOriginalValue(@NonNull SettingState<T> state, int userId) throws Exception;

    /**
     * Serializes the original value of the setting {@link SettingState#getOriginalValue()}
     *
     * @param settingKey    The string identifier for the setting.
     * @param originalValue The original value of the setting.
     * @param serializer    The serializer to serialize the original value to.
     */
    void serializeOriginalValue(@NonNull String settingKey, @NonNull T originalValue,
            @NonNull TypedXmlSerializer serializer) throws IOException, XmlPullParserException;

    /**
     * Deserializes the original value of the setting from the XML.
     * @param parser The XML parser, positioned at the start of the
     *               {@code <setting-original-value>} tag.
     * @param settingKey The key of the setting being deserialized.
     * @return The deserialized original value.
     */
    T deserializeOriginalValue(@NonNull TypedXmlPullParser parser,
            @NonNull String settingKey) throws IOException, XmlPullParserException;
}
