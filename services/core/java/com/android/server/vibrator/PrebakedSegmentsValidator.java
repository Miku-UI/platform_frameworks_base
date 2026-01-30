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

import android.os.VibratorInfo;
import android.os.vibrator.Flags;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.VibrationEffectSegment;

import java.util.List;

/**
 * Validates prebaked segments to ensure they are compatible with the device's capabilities.
 *
 * <p>The segments will be considered invalid if one of the prebaked effects is not supported.
 */
final class PrebakedSegmentsValidator implements VibrationSegmentsValidator {

    @Override
    public boolean hasValidSegments(VibratorInfo info, List<VibrationEffectSegment> segments) {
        if (!Flags.removeHidlSupport()) {
            return true;
        }
        int segmentCount = segments.size();
        for (int i = 0; i < segmentCount; i++) {
            if (!(segments.get(i) instanceof PrebakedSegment prebaked)) {
                continue;
            }
            if (info.isEffectSupported(prebaked.getEffectId()) != VIBRATION_EFFECT_SUPPORT_YES) {
                return false;
            }
        }
        return true;
    }
}
