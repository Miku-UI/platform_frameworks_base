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

package com.android.server.theming;

import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * An interface that abstracts the reading of system properties, primarily to facilitate testing.
 * <p>
 * This interface serves as a wrapper around {@link android.os.SystemProperties}, allowing
 * dependencies that rely on system properties to be unit-tested by injecting a mock
 * implementation.
 * <p>
 * In the context of theming, this is used to read device-specific properties like
 * {@code ro.boot.hardware.color}, which can define the default theme for a device out of the box.
 */
public interface SystemPropertiesReader {
    /**
     * Get the String value for the given {@code key}.
     *
     * @param key the key to lookup
     * @param def the default value in case the property is not set or empty
     * @return if the {@code key} isn't found, return {@code def} if it isn't null, or an empty
     * string otherwise
     */
    @NonNull
    String get(@NonNull String key, @Nullable String def);
}
