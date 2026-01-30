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
import android.hardware.vibrator.CompositeEffect;
import android.hardware.vibrator.CompositePwleV2;
import android.hardware.vibrator.IVibrationSession;
import android.hardware.vibrator.IVibrator;
import android.hardware.vibrator.IVibratorManager;
import android.hardware.vibrator.PrimitivePwle;
import android.hardware.vibrator.VendorEffect;

/** Handles interactions with vibrator HAL services through native. */
interface HalNativeHandler {

    /** Initializes the callback instance for future interactions. */
    void init(@NonNull HalVibratorManager.Callbacks managerCallback,
            @NonNull HalVibrator.Callbacks vibratorCallback);

    /**
     * Call {@link IVibratorManager#triggerSynced} using given vibration id for callbacks from HAL.
     *
     * <p>This should only be called if HAL has {@link IVibratorManager#CAP_TRIGGER_CALLBACK}. The
     * HAL might fail the request otherwise.
     *
     * @return true if successful, false otherwise.
     */
    boolean triggerSyncedWithCallback(long vibrationId);

    /**
     * Call {@link IVibratorManager#startSession} using given session id for callbacks from HAL.
     *
     * <p>This should only be called if HAL has {@link IVibratorManager#CAP_START_SESSIONS}. The
     * HAL might fail the request otherwise.
     *
     * @return the session binder token if successful, null otherwise.
     */
    @Nullable
    IVibrationSession startSessionWithCallback(long sessionId, int[] vibratorIds);

    /**
     * Call {@link IVibrator#on} on single vibrator using vibration id for callbacks from HAL.
     *
     * <p>This should only be called if HAL has {@link IVibrator#CAP_ON_CALLBACK}. The HAL might
     * fail the request otherwise.
     *
     * @return durationMs if successful, zero if unsupported or negative if failed.
     */
    int vibrateWithCallback(int vibratorId, long vibrationId, long stepId, int durationMs);

    /**
     * Call {@link IVibrator#performVendorEffect} on single vibrator using vibration id for
     * callbacks from HAL.
     *
     * <p>This should only be called if HAL has {@link IVibrator#CAP_PERFORM_VENDOR_EFFECTS}.
     * The HAL might fail the request otherwise.
     *
     * @return max int value if successful, zero if unsupported or negative if failed.
     */
    int vibrateWithCallback(int vibratorId, long vibrationId, long stepId, VendorEffect effect);

    /**
     * Call {@link IVibrator#perform} on single vibrator using vibration id for callbacks from HAL.
     *
     * <p>This should only be called if HAL has {@link IVibrator#CAP_PERFORM_CALLBACK}. The HAL
     * might fail the request otherwise.
     *
     * @return duration (milliseconds) if successful, zero if unsupported or negative if failed.
     */
    int vibrateWithCallback(int vibratorId, long vibrationId, long stepId, int effectId,
            int effectStrength);

    /**
     * Call {@link IVibrator#compose} on single vibrator using vibration id for callbacks from HAL.
     *
     * <p>This should only be called if HAL has {@link IVibrator#CAP_COMPOSE_EFFECTS}. The HAL
     * might fail the request otherwise.
     *
     * @return max int value if successful, zero if unsupported or negative if failed.
     */
    int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
            CompositeEffect[] effects);

    /**
     * Call {@link IVibrator#composePwle} on single vibrator using vibration id for callbacks
     * from HAL.
     *
     * <p>This should only be called if HAL has {@link IVibrator#CAP_COMPOSE_PWLE_EFFECTS}. The HAL
     * might fail the request otherwise.
     *
     * @return max int value if successful, zero if unsupported or negative if failed.
     */
    int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
            PrimitivePwle[] effects);

    /**
     * Call {@link IVibrator#composePwleV2} on single vibrator using vibration id for callbacks
     * from HAL.
     *
     * <p>This should only be called if HAL has {@link IVibrator#CAP_COMPOSE_PWLE_EFFECTS_V2}.
     * The HAL might fail the request otherwise.
     *
     * @return max int value if successful, zero if unsupported or negative if failed.
     */
    int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
            CompositePwleV2 composite);
}
