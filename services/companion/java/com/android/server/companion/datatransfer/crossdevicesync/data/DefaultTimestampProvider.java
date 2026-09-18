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
package com.android.server.companion.datatransfer.crossdevicesync.data;

import android.os.SystemClock;
import android.util.Log;

import com.google.android.submerge.TimestampProvider;

import java.time.DateTimeException;

/** Default implementation of {@link TimestampProvider}. */
public class DefaultTimestampProvider implements TimestampProvider {
    private static final String TAG = "DefaultTimestampProvider";

    public DefaultTimestampProvider() {}

    @Override
    public long now() {
        try {
            return SystemClock.currentNetworkTimeMillis();
        } catch (DateTimeException e) {
            // Fall through
            Log.w(TAG, "Unable to get network timestamp!", e);
        }
        return System.currentTimeMillis();
    }
}
