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

import static com.android.server.display.persistence.DisplayState.BRIGHTNESS_CONFIGURATION_KEY;
import static com.android.server.display.persistence.PersistentDataStore.Key;
import static com.android.server.display.persistence.PersistentDataStore.MapKey;
import static com.android.server.display.persistence.PersistentDataStore.XmlProcessor;

import android.util.IndentingPrintWriter;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/**
 * Manages user-specific properties.
 */
class UserState extends GenericStore {
    static final String XML_TAG = "user";
    private static final String XML_ATTR_USER_SERIAL = "user-serial";
    static final Function<UserState, Integer> ID_SUPPLIER = UserState::getUserSerial;
    static final XmlProcessor<UserState> XML_PROCESSOR = new XmlProcessor<>() {
        @Override
        public UserState loadFromXml(TypedXmlPullParser parser)
                throws XmlPullParserException, IOException {
            int userSerial = parser.getAttributeInt(null, XML_ATTR_USER_SERIAL);
            UserState userState = new UserState(userSerial);
            userState.loadFromXml(parser);
            return userState;
        }

        @Override
        public void saveToXml(TypedXmlSerializer serializer, UserState value) throws IOException {
            serializer.attributeInt(null, XML_ATTR_USER_SERIAL, value.getUserSerial());
            value.saveToXml(serializer);
        }
    };

    static final Key<Map<String, DisplayState>> DISPLAY_STATES_KEY = new MapKey<>("display-states",
            DisplayState.XML_TAG, DisplayState.ID_SUPPLIER, DisplayState.XML_PROCESSOR) {
        @Override
        void dump(IndentingPrintWriter ipw, Object value) {
            for (DisplayState displayState : ((Map<String, DisplayState>) value).values()) {
                displayState.dump(ipw);
            }
        }
    };

    private static final Key<?>[] KEYS = new Key<?>[]{
            DISPLAY_STATES_KEY,
            BRIGHTNESS_CONFIGURATION_KEY
    };

    private final int mUserSerial;

    UserState(int userSerial) {
        super(KEYS);
        mUserSerial = userSerial;
    }

    int getUserSerial() {
        return mUserSerial;
    }

    @Override
    void dump(IndentingPrintWriter ipw) {
        ipw.println("UserState serial=" + mUserSerial + ":");
        super.dump(ipw);
    }
}
