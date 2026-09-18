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

package com.android.server.devicepolicy;

import android.annotation.NonNull;
import android.app.admin.PolicyValue;
import android.util.Log;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Encode a list of elements, where the encoding of each element is implemented in a
 * type-specific subtype. Encoded, the XML looks like:
 * {@code
 * <elements>
 * <element>first value</element>
 * <element>second value</element>
 * </elements>
 * }
 *
 * @param <T> The type of the list element.
 */
public abstract class ListPolicySerializer<T> extends PolicySerializer<List<T>> {
    private static final String TAG = "ListPolicySerializer";

    private static final String TAG_LIST = "elements";
    private static final String TAG_ELEMENT = "element";

    abstract void saveElementToXml(TypedXmlSerializer serializer, @NonNull T value)
            throws IOException;

    @NonNull
    abstract T readElementFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException;

    @NonNull
    abstract PolicyValue<List<T>> toPolicyValue(@NonNull List<T> value);

    @Override
    void saveToXml(TypedXmlSerializer serializer, @NonNull List<T> value) throws IOException {
        Objects.requireNonNull(value);
        serializer.startTag(null, TAG_LIST);

        for (var element : value) {
            Objects.requireNonNull(element);
            serializer.startTag(null, TAG_ELEMENT);
            saveElementToXml(serializer, element);
            serializer.endTag(null, TAG_ELEMENT);
        }

        serializer.endTag(null, TAG_LIST);
    }

    @Override
    PolicyValue<List<T>> readFromXml(TypedXmlPullParser parser) {
        List<T> result = new ArrayList<>();

        try {
            parser.next(); // Skip the current tag.
            parser.require(XmlPullParser.START_TAG, null, TAG_LIST);
            parser.next();

            while (parser.getEventType()
                    == XmlPullParser.START_TAG && parser.getName().equals(TAG_ELEMENT)) {
                T value = readElementFromXml(parser);
                result.add(value);
                parser.require(XmlPullParser.END_TAG, null, TAG_ELEMENT);
                parser.next();
            }

            parser.require(XmlPullParser.END_TAG, null, TAG_LIST);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Error parsing List policy.", e);

            throw new RuntimeException(e);
        }

        return toPolicyValue(result);
    }
}
