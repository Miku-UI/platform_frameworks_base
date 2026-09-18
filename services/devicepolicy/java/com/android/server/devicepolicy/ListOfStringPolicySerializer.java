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
import android.app.admin.ListOfStringPolicyValue;
import android.app.admin.PolicyValue;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

public class ListOfStringPolicySerializer extends ListPolicySerializer<String> {
    @Override
    void saveElementToXml(TypedXmlSerializer serializer, @NonNull String value)
            throws IOException {
        serializer.text(value);
    }

    @NonNull
    @Override
    String readElementFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException {
        return parser.nextText();
    }

    @NonNull
    @Override
    PolicyValue<List<String>> toPolicyValue(@NonNull List<String> value) {
        return new ListOfStringPolicyValue(value);
    }
}
