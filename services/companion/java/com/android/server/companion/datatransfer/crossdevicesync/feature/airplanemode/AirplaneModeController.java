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

package com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode;

import android.annotation.NonNull;

/** Manages and provides airplane mode state. */
public interface AirplaneModeController {
    /** Returns the current airplane mode state. */
    boolean isAirplaneModeEnabled();

    /** Returns the current airplane mode sync enabled state. */
    boolean isAirplaneModeSyncEnabled();

    /** Updates the current airplane mode state. */
    void updateAirplaneModeState(boolean enabled);

    /** Registers a listener for airplane mode state changes. */
    void registerAirplaneModeChangedListener(@NonNull Listener listener);

    /** Unregisters a listener for airplane mode state changes. */
    void unregisterAirplaneModeChangedListener(@NonNull Listener listener);

    /** Listener for airplane mode state changes. */
    interface Listener {
        /** Called when the airplane mode state changes. */
        void onAirplaneModeChanged(boolean enabled);

        /** Called when the airplane mode sync enabled state changes. */
        void onAirplaneModeSyncEnabledStateChanged(boolean enabled);
    }
}
