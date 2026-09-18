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

package com.android.server.display.persistence;

import static com.android.server.display.persistence.PersistentDataStore.ComplexKey;
import static com.android.server.display.persistence.PersistentDataStore.FloatKey;
import static com.android.server.display.persistence.PersistentDataStore.IntegerKey;
import static com.android.server.display.persistence.PersistentDataStore.Key;
import static com.android.server.display.persistence.PersistentDataStore.XmlProcessor;

import android.util.IndentingPrintWriter;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.function.Function;

/**
 * Manages display-specific properties.
 */
public class DisplayState extends GenericStore {
    static final String XML_TAG = "display";
    private static final String XML_ATTR_UNIQUE_ID = "unique-id";
    static final Function<DisplayState, String> ID_SUPPLIER = DisplayState::getUniqueId;
    static final XmlProcessor<DisplayState> XML_PROCESSOR = new XmlProcessor<>() {
        @Override
        public DisplayState loadFromXml(TypedXmlPullParser parser)
                throws XmlPullParserException, IOException {
            String uniqueId = parser.getAttributeValue(null, XML_ATTR_UNIQUE_ID);
            if (uniqueId == null) {
                throw new XmlPullParserException(
                        "Missing unique-id attribute on display.");
            }
            DisplayState displayState = new DisplayState(uniqueId);
            displayState.loadFromXml(parser);
            return displayState;
        }

        @Override
        public void saveToXml(TypedXmlSerializer serializer, DisplayState value)
                throws IOException {
            serializer.attribute(null, XML_ATTR_UNIQUE_ID, value.getUniqueId());
            value.saveToXml(serializer);
        }
    };

    public static final Key<Integer> COLOR_MODE_KEY = new IntegerKey("color-mode");
    public static final Key<Float> BRIGHTNESS_KEY = new FloatKey("brightness");
    public static final Key<BrightnessConfiguration> BRIGHTNESS_CONFIGURATION_KEY =
            new ComplexKey<>(BrightnessConfiguration.XML_TAG,
                    BrightnessConfiguration.XML_PROCESSOR) {};
    public static final Key<DisplayMode> DISPLAY_MODE_KEY = new ComplexKey<>("display-mode",
            DisplayMode.XML_PROCESSOR) {};
    public static final Key<Integer> CONNECTION_PREFERENCE_KEY = new IntegerKey(
            "connection-preference");
    public static final Key<Integer> HDR_PREFERENCE_KEY = new IntegerKey("hdr-preference");

    private static final Key<?>[] KEYS = new Key<?>[]{
            COLOR_MODE_KEY,
            BRIGHTNESS_KEY,
            BRIGHTNESS_CONFIGURATION_KEY,
            DISPLAY_MODE_KEY,
            CONNECTION_PREFERENCE_KEY,
            HDR_PREFERENCE_KEY
    };

    private final String mUniqueId;

    DisplayState(String uniqueId) {
        super(KEYS);
        mUniqueId = uniqueId;
    }

    String getUniqueId() {
        return mUniqueId;
    }

    @Override
    void dump(IndentingPrintWriter ipw) {
        ipw.println("DisplayState uniqueId=" + mUniqueId + ":");
        super.dump(ipw);
    }
}
