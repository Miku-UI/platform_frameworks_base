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

package com.android.server.vibrator;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.IndentingPrintWriter;

import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByNative;

/** Handles interactions with a vibrator manager HAL. */
interface HalVibratorManager {

    /** Callbacks from the vibrator manager HAL. */
    @UsedByNative(
            description = "Called from JNI in jni/VibratorManagerService.cpp",
            kind = KeepItemKind.CLASS_AND_MEMBERS)
    interface Callbacks {
        /** Callback triggered when synced vibration is complete. */
        void onSyncedVibrationComplete(long vibrationId);

        /** Callback triggered when vibration session is complete. */
        void onVibrationSessionComplete(long sessionId);
    }

    /** Initializes the HAL and set callback instances for future interactions. */
    void init(@NonNull Callbacks callbacks, @NonNull HalVibrator.Callbacks vibratorCallbacks);

    /** Notifies the boot phase system ready. This might load some HAL static data. */
    void onSystemReady();

    /** Return the vibrator manager capabilities. */
    long getCapabilities();

    /** Return true if vibrator manager has required capability, false otherwise. */
    default boolean hasCapability(long capability) {
        return (getCapabilities() & capability) == capability;
    }

    /** Return the IDs of the vibrators controlled by this manager. */
    @NonNull int[] getVibratorIds();

    /** Return the vibrator with given ID controlled by this manager. */
    @Nullable
    HalVibrator getVibrator(int id);

    /** Prepare vibrators for triggering vibrations in sync. */
    boolean prepareSynced(@NonNull int[] vibratorIds);

    /** Trigger prepared synced vibration. */
    boolean triggerSynced(long vibrationId);

    /** Cancel prepared synced vibration. */
    boolean cancelSynced();

    /** Start vibration session. */
    boolean startSession(long sessionId, @NonNull int[] vibratorIds);

    /** End vibration session. */
    boolean endSession(long sessionId, boolean shouldAbort);

    /** Writes information into dumpsys. */
    void dump(IndentingPrintWriter pw);
}
