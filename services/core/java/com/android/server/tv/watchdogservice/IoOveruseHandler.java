/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.tv.watchdogservice;

import android.annotation.UserIdInt;
import android.text.TextUtils;
import android.util.ArraySet;

import java.util.StringJoiner;

/**
 * This is a temporary stub class to allow the main service to compile. It will be replaced with the
 * real IoOveruseHandler generated via genrules from CarWatchdog.
 */
public final class IoOveruseHandler {

    /** Parses a semicolon-separated string of package names. */
    public static ArraySet<String> extractPackages(String settingsString) {
        ArraySet<String> packages = new ArraySet<>();
        if (TextUtils.isEmpty(settingsString)) {
            return packages;
        }
        for (String pkg : settingsString.split(";")) {
            if (!TextUtils.isEmpty(pkg)) {
                packages.add(pkg);
            }
        }
        return packages;
    }

    /** Constructs a semicolon-separated string from a set of packages. */
    public static String constructSettingsString(ArraySet<String> packages) {
        StringJoiner joiner = new StringJoiner(";");
        for (String pkg : packages) {
            joiner.add(pkg);
        }
        return joiner.toString();
    }

    /** Creates a unique identifier for a user-package combination. */
    public static String getUserPackageUniqueId(@UserIdInt int userId, String packageName) {
        return userId + ":" + packageName;
    }
}
