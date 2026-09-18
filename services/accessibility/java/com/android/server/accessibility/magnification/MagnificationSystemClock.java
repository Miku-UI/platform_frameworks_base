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

package com.android.server.accessibility.magnification;

/**
 * Helper class to provide time. Tests may extend this interface to "control time".
 */
public class MagnificationSystemClock {
    public MagnificationSystemClock() {}

    /**
     * Returns current time in milliseconds since boot, not counting time spent in deep sleep. This
     * implementation is for the real system clock for use in production.
     * @return the real uptimeMillis from SystemClock.
     */
    public long uptimeMillis() {
        return android.os.SystemClock.uptimeMillis();
    }
}
