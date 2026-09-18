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

import static com.android.server.display.persistence.PersistentDataStore.Key;

import android.util.IndentingPrintWriter;

import androidx.annotation.Nullable;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class GenericStore {
    // Map from key names to keys
    protected final Map<String, Key<?>> mKeys;

    // Map from key names to values
    protected final Map<String, Object> mStore = new HashMap<>();

    GenericStore(Key<?>[] keys) {
        mKeys = new HashMap<>();
        for (Key<?> key : keys) {
            if (mKeys.containsKey(key.mName)) {
                throw new IllegalArgumentException("Found a duplicate key: " + key.mName);
            }
            mKeys.put(key.mName, key);
        }
    }

    @Nullable
    <T> T get(Key<T> key) {
        if (!mKeys.containsKey(key.mName)) {
            throw new IllegalArgumentException("Unrecognized key: " + key.mName);
        }
        return (T) mStore.get(key.mName);
    }

    /**
     * @param key   The key to identify the property
     * @param value The value to store. If null, the property is removed.
     * @return True if the value provided is not equal to one stored previously (or it was not
     * stored before).
     */
    <T> boolean put(Key<T> key, T value) {
        if (!mKeys.containsKey(key.mName)) {
            throw new IllegalArgumentException("Unrecognized key: " + key.mName);
        }
        if (Objects.equals(mStore.get(key.mName), value)) {
            return false;
        }
        if (value != null) {
            mStore.put(key.mName, value);
        } else {
            mStore.remove(key.mName);
        }
        return true;
    }

    void loadFromXml(TypedXmlPullParser parser) throws XmlPullParserException, IOException {
        final int outerDepth = parser.getDepth();
        while (XmlUtils.nextElementWithin(parser, outerDepth)) {
            String keyName = parser.getName();
            if (!mKeys.containsKey(keyName)) {
                throw new XmlPullParserException("Unrecognized key: " + keyName);
            }
            if (mStore.containsKey(keyName)) {
                throw new XmlPullParserException("Found a duplicate key: " + keyName);
            }
            mStore.put(keyName, mKeys.get(keyName).loadFromXml(parser));
        }
    }

    void saveToXml(TypedXmlSerializer serializer) throws IOException {
        for (Map.Entry<String, Object> entry : mStore.entrySet()) {
            String keyName = entry.getKey();
            serializer.startTag(null, keyName);
            mKeys.get(keyName).saveToXml(serializer, entry.getValue());
            serializer.endTag(null, keyName);
        }
    }

    void dump(IndentingPrintWriter ipw) {
        ipw.increaseIndent();
        for (Map.Entry<String, Object> entry : mStore.entrySet()) {
            mKeys.get(entry.getKey()).dump(ipw, entry.getValue());
        }
        ipw.decreaseIndent();
    }
}
