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

package com.android.server.display.plugin.types;

import android.annotation.FloatRange;
import android.os.PowerManager;

import com.android.internal.annotations.Keep;
import com.android.server.display.brightness.BrightnessReason;

/**
 * Max Brightness Cap override value.
 * Works with existing brightness clamping system in
 * {@link com.android.server.display.brightness.clamper.BrightnessClamperController}
 * to override current brightness cap.
 *
 * @param maxBrightnessCap         - Brightness max value to clamp to
 *                                 Value in range from BRIGHTNESS_MIN to BRIGHTNESS_MAX.
 *                                 If not used should be set to PowerManager.BRIGHTNESS_MAX
 * @param customTransitionRate     - Custom transition rate for transitioning to new max brightness.
 *                                 If not used should be set to
 *                                 DisplayBrightnessState.CUSTOM_ANIMATION_RATE_NOT_SET
 */
@Keep
public record MaxBrightnessCapOverride(
        @FloatRange(from = PowerManager.BRIGHTNESS_MIN, to = PowerManager.BRIGHTNESS_MAX)
        float maxBrightnessCap,
        float customTransitionRate,
        @BrightnessReason.Modifier int reasonModifier) {
    public MaxBrightnessCapOverride(float maxBrightnessCap, float customTransitionRate) {
        this(maxBrightnessCap, customTransitionRate, BrightnessReason.MODIFIER_NONE);
    }
}
