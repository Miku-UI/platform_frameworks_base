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
package com.android.server.multisensory.repository.haptics;

import android.annotation.Nullable;
import android.os.VibrationEffect;
import android.os.multisensory.MultisensoryContinuousEffect;

import com.android.server.multisensory.repository.MultisensoryRepository;

/**
 * A haptic effect description for the haptic feedback stored in the {@link MultisensoryRepository}
 *
 * @hide
 */
public interface MultisensoryHapticEffect {

    /**
     * Create a {@link VibrationEffect} from this haptic effect to be played only once.
     *
     * @return A {@link VibrationEffect} to play once or null if the effect does not specify a
     *     single vibration effect.
     */
    @Nullable
    VibrationEffect createSingleVibrationEffect();

    /**
     * Create a {@link MultisensoryContinuousEffect} from this haptic effect to be played as a
     * continuous vibration session.
     *
     * <p>The effect The effect will represent a continuous envelope to be modified at runtime
     * during playback. The envelope should have a finite duration and should be long enough for it
     * to be modified at runtime from direct user input.
     *
     * <p>The effect should only be constructed if the device supports envelope waveforms (see
     * {@link VibrationEffect.WaveformEnvelopeBuilder}). This is because the device should be able
     * to deliver a vibration at a specified frequency within its frequency range.
     *
     * @return The {@link VibrationEffect} to play as a waveform in a vibration session. Null if the
     *     haptic effect should not be used to create a continuous vibration or if the device does
     *     not support envelope waveforms (see {@link VibrationEffect.WaveformEnvelopeBuilder}).
     */
    @Nullable
    MultisensoryContinuousEffect createContinuousEffect();
}
