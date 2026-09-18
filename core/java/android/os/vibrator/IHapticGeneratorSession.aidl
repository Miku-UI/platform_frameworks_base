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

package android.os.vibrator;

import android.os.VibrationEffect;
import android.os.vibrator.IHapticChannelStream;

/**
 * Represents an active haptic generator session with the vibrator service.
 * @hide
 */
interface IHapticGeneratorSession {

    /**
     * Creates a new stream to convert a VibrationEffect into haptic PCM data.
     *
     * @param effect The vibration effect to be converted.
     * @return The binder object for the newly created haptic channel stream.
     */
    @EnforcePermission("USE_VIBRATOR_HAPTIC_GENERATOR")
    IHapticChannelStream generateHapticChannelStream(in VibrationEffect effect);

    /**
     * Closes the session and releases all associated resources.
     */
    @EnforcePermission("USE_VIBRATOR_HAPTIC_GENERATOR")
    void close();
}