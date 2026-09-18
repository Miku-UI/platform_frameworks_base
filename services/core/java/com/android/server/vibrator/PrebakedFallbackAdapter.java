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
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.util.Slog;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/** Adapter that replaces unsupported {@link PrebakedSegment} with fallback. */
final class PrebakedFallbackAdapter implements VibrationSegmentsAdapter {
    private static final String TAG = "PrebakedFallbackAdapter";

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
            long startTimeMillis = prebaked.getStartTimeMillis();
            List<VibrationEffectSegment> fallbackSegments = new ArrayList<>(fallback.getSegments());
            if (startTimeMillis >= 0 && !fallbackSegments.isEmpty()) {
                for (int j = 0; j < fallbackSegments.size(); j++) {
                    VibrationEffectSegment segment = fallbackSegments.get(j);
                    if (segment instanceof PrimitiveSegment) {
                        Slog.wtf(TAG, "Found PrimitiveSegment in fallback effect for "
                                + "PrebakedSegment. This co-existence should not happen.");
                        // TODO(b/469962388): Convert PrimitiveSegment to PresetSegment if there is
                        // any.
                        continue;
                    }
                    long segmentStartTimeMillis = segment.getStartTimeMillis();
                    long newStartTimeMillis = segmentStartTimeMillis;
                    if (segmentStartTimeMillis >= 0) {
                        newStartTimeMillis += startTimeMillis;
                    } else if (j == 0) {
                        // If the first segment has a negative start time, set it to the start time
                        // of the PrebakedSegment.
                        newStartTimeMillis = startTimeMillis;
                    }
                    fallbackSegments.set(j, segment.applyStartTime(newStartTimeMillis));
                }
            } else { // startTimeMillis < 0
                for (int j = 0; j < fallbackSegments.size(); j++) {
                    if (fallbackSegments.get(j) instanceof PrimitiveSegment) {
                        Slog.wtf(TAG, "Found PrimitiveSegment in fallback effect for "
                                + "PrebakedSegment. This co-existence should not happen.");
                        // TODO(b/469962388): Convert PrimitiveSegment to PresetSegment if there is
                        // any.
                        continue;
                    }
                    if (fallbackSegments.get(j).getStartTimeMillis() >= 0) {
                        Slog.wtf(TAG, "Found non-negative start time in fallback effect for "
                                + "PrebakedSegment without start time. This is not supported and "
                                + "should not happen.");
                        fallbackSegments.set(j, fallbackSegments.get(j).applyStartTime(-1));
                    }
                }
            }
            segments.remove(i);
            segments.addAll(i, fallbackSegments);
            int segmentsAdded = fallbackSegments.size() - 1;
            if (repeatIndex > i) {
                repeatIndex += segmentsAdded;
            }
            i += segmentsAdded;
        }
        return repeatIndex;
    }
}
