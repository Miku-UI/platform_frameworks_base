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

import static android.os.Vibrator.VIBRATION_EFFECT_SUPPORT_YES;

import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.Flags;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.util.SparseArray;

import java.util.List;

/** Adapter that replaces unsupported {@link PrebakedSegment} with fallback. */
final class PrebakedFallbackAdapter implements VibrationSegmentsAdapter {
    private final SparseArray<VibrationEffect> mFallbackEffects;

    PrebakedFallbackAdapter(SparseArray<VibrationEffect> fallbackEffects) {
        mFallbackEffects = fallbackEffects;
    }

    @Override
    public int adaptToVibrator(VibratorInfo info, List<VibrationEffectSegment> segments,
            int repeatIndex) {
        if (!Flags.removeHidlSupport()) {
            return repeatIndex;
        }
        for (int i = 0; i < segments.size(); i++) {
            if (!(segments.get(i) instanceof PrebakedSegment prebaked)) {
                continue;
            }
            int effectId = prebaked.getEffectId();
            if (info.isEffectSupported(effectId) == VIBRATION_EFFECT_SUPPORT_YES
                    || !prebaked.shouldFallback()) {
                continue;
            }
            if (!(mFallbackEffects.get(effectId) instanceof VibrationEffect.Composed fallback)) {
                continue;
            }
            segments.remove(i);
            segments.addAll(i, fallback.getSegments());
            int segmentsAdded = fallback.getSegments().size() - 1;
            if (repeatIndex > i) {
                repeatIndex += segmentsAdded;
            }
            i += segmentsAdded;
        }
        return repeatIndex;
    }
}
