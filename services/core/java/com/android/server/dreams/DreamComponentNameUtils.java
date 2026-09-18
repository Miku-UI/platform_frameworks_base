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

package com.android.server.dreams;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Utility class for serializing and deserializing Dream ComponentNames. */
public final class DreamComponentNameUtils {

    private DreamComponentNameUtils() {}

    /** Flattens an array of components into a comma-separated string. */
    @NonNull
    public static String toCommaSeparatedString(@Nullable ComponentName[] componentNames) {
        if (componentNames == null) {
            return "";
        }
        final StringBuilder names = new StringBuilder();
        for (ComponentName componentName : componentNames) {
            if (componentName == null) {
                continue;
            }
            if (names.length() > 0) {
                names.append(',');
            }
            names.append(componentName.flattenToString());
        }
        return names.toString();
    }

    /** Parses a comma-separated string into a ComponentName[]. */
    @NonNull
    public static ComponentName[] fromCommaSeparatedString(@Nullable String names) {
        if (TextUtils.isEmpty(names)) {
            return new ComponentName[0];
        }
        final List<ComponentName> validNames = new ArrayList<>();
        final TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
        splitter.setString(names);
        while (splitter.hasNext()) {
            final String name = splitter.next();
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            final ComponentName cn = ComponentName.unflattenFromString(name);
            if (cn != null) {
                validNames.add(cn);
            }
        }
        return validNames.toArray(new ComponentName[0]);
    }
}
