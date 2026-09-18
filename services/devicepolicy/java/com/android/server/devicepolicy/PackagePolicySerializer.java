/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.annotation.Nullable;
import android.app.admin.PackageIdentifier;
import android.app.admin.PackagePolicyValue;
import android.util.Log;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Objects;

public final class PackagePolicySerializer extends PolicySerializer<PackageIdentifier> {

    private static final String TAG = "PackagePolicySerializer";

    private static final String ATTR_PACKAGE_NAME = "package_name";

    @Override
    void saveToXml(
            TypedXmlSerializer serializer,
            @NonNull PackageIdentifier value) throws IOException {
        Objects.requireNonNull(value);
        serializer.attribute(/* namespace= */ null, ATTR_PACKAGE_NAME, value.getPackageName());
    }

    @Nullable
    @Override
    PackagePolicyValue readFromXml(TypedXmlPullParser parser) {
        String packageName = parser.getAttributeValue(/* namespace= */ null, ATTR_PACKAGE_NAME);
        if (packageName == null) {
            Log.e(TAG, "Error parsing Package policy value.");
            return null;
        }
        return new PackagePolicyValue(new PackageIdentifier(packageName));
    }
}
