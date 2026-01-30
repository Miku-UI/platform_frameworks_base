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
import android.os.IVibratorStateListener;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.os.vibrator.RampSegment;
import android.util.IndentingPrintWriter;

import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByNative;

/** Handles interactions with a single vibrator HAL. */
interface HalVibrator {

    /** Callbacks from the vibrator HAL. */
    @UsedByNative(
            description = "Called from JNI in jni/VibratorManagerService.cpp",
            kind = KeepItemKind.CLASS_AND_MEMBERS)
    interface Callbacks {
        /** Callback triggered when a vibration step is complete. */
        void onVibrationStepComplete(int vibratorId, long vibrationId, long stepId);
    }

    /** Representation of the vibrator state based on the interactions through this interface. */
    enum State {
        IDLE, VIBRATING, UNDER_EXTERNAL_CONTROL
    }

    /** Initializes the vibrator HAL and set the callback instance for future interactions. */
    void init(@NonNull Callbacks callbacks);

    /** Notifies the boot phase system ready. This might load some HAL static data. */
    void onSystemReady();

    /** Return the {@link VibratorInfo} representing the vibrator capabilities and hardware spec. */
    @NonNull VibratorInfo getInfo();

    /**
     * Return {@code true} if this vibrator is currently vibrating, false otherwise.
     *
     * <p>This state is controlled by interactions with this interface, and is automatically
     * notified to any registered {@link IVibratorStateListener} on change.
     */
    boolean isVibrating();

    /** Register state listener for this vibrator. */
    boolean registerVibratorStateListener(@NonNull IVibratorStateListener listener);

    /** Remove registered state listener for this vibrator. */
    boolean unregisterVibratorStateListener(@NonNull IVibratorStateListener listener);

    /**
     * Returns the current amplitude the device is vibrating.
     *
     * <p>This value is set to 1 by the method {@link #on(long, long, long)}, and can be updated via
     * {@link #setAmplitude(float)} if called while the device is vibrating.
     *
     * <p>If the device is vibrating via any other method then the current amplitude is unknown and
     * this will return -1.
     *
     * <p>If {@link #isVibrating()} is false then this will be zero.
     */
    float getCurrentAmplitude();

    /**
     * Set the vibrator control to be external or not, based on given flag.
     *
     * <p>This will affect the state of {@link #isVibrating()}.
     *
     * @return true if successful.
     */
    boolean setExternalControl(boolean externalControl);

    /**
     * Update the predefined vibration effect saved with given id for always-on vibration.
     *
     * <p>This will remove the saved effect if given value is {@code null}.
     *
     * @return true if successful.
     */
    boolean setAlwaysOn(int id, @Nullable PrebakedSegment prebaked);

    /**
     * Set the vibration amplitude.
     *
     * <p>This will NOT affect the state of {@link #isVibrating()}.
     *
     * @return true if successful.
     */
    boolean setAmplitude(float amplitude);

    /**
     * Turn on the vibrator for {@code milliseconds} time, using {@code vibrationId} and
     * {@code stepId} for completion callback.
     *
     * <p>This will affect the state of {@link #isVibrating()}.
     *
     * @return The positive duration of the vibration started, if successful, zero if the vibrator
     * do not support the input or a negative number if the operation failed.
     */
    long on(long vibrationId, long stepId, long milliseconds);

    /**
     * Plays vendor vibration effect, using {@code vibrationId} and {@code stepId} for completion
     * callback.
     *
     * <p>This will affect the state of {@link #isVibrating()}.
     *
     * @return The positive duration of the vibration started, if successful, zero if the vibrator
     * do not support the input or a negative number if the operation failed.
     */
    long on(long vibrationId, long stepId, VibrationEffect.VendorEffect vendorEffect);

    /**
     * Plays predefined vibration effect, using {@code vibrationId} and {@code stepId} for
     * completion callback.
     *
     * <p>This will affect the state of {@link #isVibrating()}.
     *
     * @return The positive duration of the vibration started, if successful, zero if the vibrator
     * do not support the input or a negative number if the operation failed.
     */
    long on(long vibrationId, long stepId, PrebakedSegment prebaked);

    /**
     * Plays a composition of vibration primitives, using {@code vibrationId} and {@code stepId} for
     * completion callback.
     *
     * <p>This will affect the state of {@link #isVibrating()}.
     *
     * @return The positive duration of the vibration started, if successful, zero if the vibrator
     * do not support the input or a negative number if the operation failed.
     */
    long on(long vibrationId, long stepId, PrimitiveSegment[] primitives);

    /**
     * Plays a composition of pwle primitives, using {@code vibrationId} and {@code stepId} for
     * completion callback.
     *
     * <p>This will affect the state of {@link #isVibrating()}.
     *
     * @return The positive duration of the vibration started, if successful, zero if the vibrator
     * do not support the input or a negative number if the operation failed.
     */
    long on(long vibrationId, long stepId, RampSegment[] primitives);

    /**
     * Plays a composition of pwle v2 points, using {@code vibrationId} and {@code stepId} for
     * completion callback.
     *
     * <p>This will affect the state of {@link #isVibrating()}.
     *
     * @return The positive duration of the vibration started, if successful, zero if the vibrator
     * do not support the input or a negative number if the operation failed.
     */
    long on(long vibrationId, long stepId, PwlePoint[] pwlePoints);

    /**
     * Turns off the vibrator.
     *
     * <p>This will affect the state of {@link #isVibrating()}.
     */
    boolean off();

    /** Writes information into dumpsys. */
    void dump(IndentingPrintWriter pw);
}
