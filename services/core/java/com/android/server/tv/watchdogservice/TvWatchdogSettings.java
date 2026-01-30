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

import android.provider.Settings;

/**
 * TV-specific settings, stored as name-value pairs.
 *
 * @hide
 */
public final class TvWatchdogSettings {
    /** Secure settings, containing system preferences that applications can read. */
    public static final class Secure extends Settings.NameValueTable {
        /**
         * A semicolon-separated list of packages disabled due to resource overuse.
         *
         * <p>Example: {@code "com.example.app1;com.vendor.service2"}
         */
        public static final String KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE =
                "android.tv.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE";
    }
}
