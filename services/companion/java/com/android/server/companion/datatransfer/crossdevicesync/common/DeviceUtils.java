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

package com.android.server.companion.datatransfer.crossdevicesync.common;

/** Utility to check if the current device is a watch and its specific type. */
public interface DeviceUtils {
    /** Returns true if the device is a watch. */
    boolean isWatch();

    /** Returns true if the device is a kids watch. */
    boolean isKidsWatch();

    /** Registers a listener to be notified when the kids watch status changes. */
    void registerKidsWatchChangeListener(Listener listener);

    /** Unregisters a previously registered listener. */
    void unregisterKidsWatchChangeListener(Listener listener);

    /** Listener for kids watch status changes. */
    interface Listener {
        /** Called when the kids watch status changes. */
        void onKidsWatchChanged(boolean isKidsWatch);
    }
}
