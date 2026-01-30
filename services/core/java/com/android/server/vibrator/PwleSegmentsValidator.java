/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.hardware.vibrator.IVibrator;
import android.os.VibratorInfo;
import android.os.vibrator.BasicPwleSegment;
import android.os.vibrator.Flags;
import android.os.vibrator.PwleSegment;
import android.os.vibrator.VibrationEffectSegment;

import java.util.List;

/**
 * Validates {@link PwleSegment} and {@link BasicPwleSegment} instances to ensure they are
 * compatible with the device's capabilities.
 *
 * <p>This validator enforced the following rules:
 * <ul>
 *   <li>A {@link BasicPwleSegment} is always considered <b>invalid</b>. Its presence indicates
 *   a failure in the steps that should have converted it to a {@link PwleSegment}.</li>
 *   <li>For a {@link PwleSegment} to be valid, all of the following must be true:
 *     <ul>
 *       <li>The device must support {@link IVibrator#CAP_COMPOSE_PWLE_EFFECTS_V2}.</li>
 *       <li>The device has a valid {@link VibratorInfo.FrequencyProfile}.</li>
 *       <li>The segment's start and end frequencies must fall within the supported range.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
final class PwleSegmentsValidator implements VibrationSegmentsValidator {

    @Override
    public boolean hasValidSegments(VibratorInfo info, List<VibrationEffectSegment> segments) {

        boolean hasPwleCapability = info.hasCapability(IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        VibratorInfo.FrequencyProfile frequencyProfile = info.getFrequencyProfile();

        if (!Flags.decoupleFrequencyProfileFromResonance()) {
            float minFrequency = frequencyProfile.getMinFrequencyHz();
            float maxFrequency = frequencyProfile.getMaxFrequencyHz();

            for (VibrationEffectSegment segment : segments) {
                if (segment instanceof BasicPwleSegment && !hasPwleCapability) {
                    return false;
                }
                if (segment instanceof PwleSegment pwleSegment) {
                    if (!hasPwleCapability || pwleSegment.getStartFrequencyHz() < minFrequency
                            || pwleSegment.getStartFrequencyHz() > maxFrequency
                            || pwleSegment.getEndFrequencyHz() < minFrequency
                            || pwleSegment.getEndFrequencyHz() > maxFrequency) {
                        return false;
                    }
                }
            }

            return true;
        }


        for (VibrationEffectSegment segment : segments) {
            if (segment instanceof BasicPwleSegment) {
                return false;
            }
            if (segment instanceof PwleSegment pwleSegment) {
                if (!hasPwleCapability || isFrequencyOutOfRange(pwleSegment.getStartFrequencyHz(),
                        frequencyProfile) || isFrequencyOutOfRange(pwleSegment.getEndFrequencyHz(),
                        frequencyProfile)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Checks if the given frequency is unsupported.
     *
     * <p>A frequency is considered unsupported if it's outside the supported range or if the
     * frequency profile is empty.
     */
    private boolean isFrequencyOutOfRange(float frequency, VibratorInfo.FrequencyProfile profile) {
        return profile.isEmpty() || frequency < profile.getMinFrequencyHz()
                || frequency > profile.getMaxFrequencyHz();
    }
}
