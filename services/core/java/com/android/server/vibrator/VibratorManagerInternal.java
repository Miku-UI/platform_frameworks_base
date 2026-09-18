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

import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.VibrationAttributes;
import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * Vibrator manager local system service interface.
 *
 * Only for use within the system server.
 *
 * @hide
 */
public abstract class VibratorManagerInternal {

    /**
     * Performs a vibration without requiring the VIBRATE permission.
     *
     * <p>This is only to be called by services within the system_server process.
     *
     * @param deviceId The id of the device on which the vibration should be played.
     * @param effect The {@link VibrationEffect} to be played.
     * @param attrs The {@link VibrationAttributes} for the vibration.
     * @param reason A string for debugging purposes.
     * @param token The token to identify the vibration caller.
     */
    public abstract void vibrateWithoutPermissionCheck(
            int deviceId,
            @NonNull VibrationEffect effect,
            @NonNull VibrationAttributes attrs,
            String reason,
            @NonNull IBinder token);

    /**
     * Cancels a vibration without requiring the VIBRATE permission.
     *
     * <p>This is only to be called by services within the system_server process.
     *
     * @param usageFilter The usage filter to identify the vibration caller. This is a bitwise OR of
     *     {@link VibrationAttributes.Usage} values.
     * @param token The token to identify the vibration caller.
     */
    public abstract void cancelVibrateWithoutPermissionCheck(
            int usageFilter, @NonNull IBinder token);
}
