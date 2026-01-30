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

package com.android.server.accessibility;

import android.os.SystemClock;

/**
 * Provides a source for obtaining uptime in milliseconds.
 * This interface is used for dependency injection, allowing different implementations
 * for production code (using {@link SystemClock#uptimeMillis()}) and test code
 * (using a controllable, mockable clock). This ensures that time-dependent logic
 * can be tested deterministically.
 */
public interface TimeSource {
    /**
     * Returns the number of milliseconds since the device was last booted.
     * This time does not include time spent in deep sleep. It is typically
     * used for measuring durations or scheduling events that should be robust
     * to changes in wall-clock time (e.g., user changing the date/time).
     * In production, this should typically return the value of
     * {@link SystemClock#uptimeMillis()}. In tests, it might return a value
     * from a controlled, mockable clock.
     *
     * @return The number of milliseconds since device boot, not including deep sleep.
     */
    long uptimeMillis();
}
