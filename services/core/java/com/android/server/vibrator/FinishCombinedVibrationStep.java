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
import android.os.Trace;

import java.util.List;

/**
 * Finish a synced vibration started by a {@link StartCombinedVibrationStep}.
 *
 * <p>This only plays after all active vibrators steps have finished.
 */
final class FinishCombinedVibrationStep extends Step {

    FinishCombinedVibrationStep(VibrationStepConductor conductor) {
        // No predefined startTime, just wait for all steps in the queue.
        super(conductor, Long.MAX_VALUE);
    }

    @Override
    public boolean isCleanUp() {
        // This step only notes that all the vibrators has been turned off.
        return true;
    }

    @NonNull
    @Override
    public List<Step> play() {
        Trace.traceBegin(Trace.TRACE_TAG_VIBRATOR, "FinishCombinedVibrationStep");
        try {
            conductor.vibratorManagerHooks.noteVibratorOff(
                    conductor.getVibration().callerInfo.uid);
            return VibrationStepConductor.EMPTY_STEP_LIST;
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_VIBRATOR);
        }
    }

    @NonNull
    @Override
    public List<Step> cancel() {
        cancelImmediately();
        return VibrationStepConductor.EMPTY_STEP_LIST;
    }

    @Override
    public void cancelImmediately() {
        conductor.vibratorManagerHooks.noteVibratorOff(conductor.getVibration().callerInfo.uid);
    }
}
