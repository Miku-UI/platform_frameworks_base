/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.VibratorInfo;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that applies the ramp down duration config to bring down the vibrator amplitude smoothly.
 *
 * <p>This prevents the device from ringing when it cannot handle abrupt changes between ON and OFF
 * states. This will not change other types of abrupt amplitude changes in the original effect. The
 * effect overall duration is preserved by this transformation.
 *
 * <p>Waveforms with ON/OFF segments are handled gracefully by the ramp down changes. Each OFF
 * segment preceded by an ON segment will be shortened, and a step down will be added to the
 * transition between ON and OFF. The ramps/steps can be shorter than the configured duration in
 * order to preserve the waveform  timings, but they will still soften the ringing effect.
 *
 * <p>If the segment preceding an OFF segment is a {@link StepSegment} then a sequence of steps will
 * be used to bring the amplitude down to zero. This ensures that the transition from the last
 * amplitude to zero will be handled by the same vibrate method.
 */
final class RampDownAdapter implements VibrationSegmentsAdapter {

    private static final int STEP_DURATION_IN_MILLIS = 5;

    private final int mRampDownDuration;

    RampDownAdapter(int rampDownDuration) {
        mRampDownDuration = rampDownDuration;
    }

    @Override
    public int adaptToVibrator(VibratorInfo info, List<VibrationEffectSegment> segments,
            int repeatIndex) {
        if (mRampDownDuration <= 0) {
            // Nothing to do, no ramp down duration configured.
            return repeatIndex;
        }
        repeatIndex = addRampDownToZeroAmplitudeSegments(segments, repeatIndex);
        repeatIndex = addRampDownToLoop(segments, repeatIndex);
        return repeatIndex;
    }

    /**
     * This will add steps down to zero as follows:
     *
     * <ol>
     *     <li>Remove the OFF segment that follows a segment of non-zero amplitude;
     *     <li>Add a list of {@link StepSegment} starting at the
     *         previous segment's amplitude, with min between the configured ramp down
     *         duration or the removed segment's duration;
     *     <li>Add a zero amplitude segment following the steps, if necessary, to fill the remaining
     *         duration;
     * </ol>
     */
    private int addRampDownToZeroAmplitudeSegments(List<VibrationEffectSegment> segments,
            int repeatIndex) {
        int segmentCount = segments.size();
        for (int i = 1; i < segmentCount; i++) {
            float previousAmplitude = getStepAmplitudeOrZero(segments.get(i - 1));

            if (!isOffStepSegment(segments.get(i)) || Float.compare(previousAmplitude, 0) == 0) {
                continue;
            }

            long offDuration = segments.get(i).getDuration();
            long startTimeMillis = segments.get(i).getStartTimeMillis();
            List<VibrationEffectSegment> replacementSegments = createStepsDown(previousAmplitude,
                    offDuration);

            if (startTimeMillis >= 0 && !replacementSegments.isEmpty()) {
                replacementSegments.set(0,
                        replacementSegments.get(0).applyStartTime(startTimeMillis));
            }

            int segmentsAdded = replacementSegments.size() - 1;

            VibrationEffectSegment originalOffSegment = segments.remove(i);
            segments.addAll(i, replacementSegments);

            if (repeatIndex >= i) {
                if (repeatIndex == i) {
                    // This effect is repeating to the removed off segment: add it back at the
                    // end of the vibration so the loop timings are preserved, and skip it.
                    segments.add(originalOffSegment);
                    repeatIndex++;
                    segmentCount++;
                }
                repeatIndex += segmentsAdded;
            }
            i += segmentsAdded;
            segmentCount += segmentsAdded;
        }
        return repeatIndex;
    }

    /**
     * This will ramps down to zero at the repeating index of the given effect, if set, only if
     * the last segment ends at a non-zero amplitude and the repeating segment has zero amplitude.
     * The update is described as:
     *
     * <ol>
     *     <li>Add a sequence of steps down to zero following the last segment, with the min
     *         between the removed segment duration and the configured ramp down duration;
     *     <li>Skip the zero-amplitude segment by incrementing the repeat index, splitting it if
     *         necessary to skip the correct amount;
     * </ol>
     */
    private int addRampDownToLoop(List<VibrationEffectSegment> segments, int repeatIndex) {
        if (repeatIndex < 0) {
            // Nothing to do, no ramp down duration configured or effect is not repeating.
            return repeatIndex;
        }

        float lastAmplitude = getStepAmplitudeOrZero(segments.get(segments.size() - 1));

        if (Float.compare(lastAmplitude, 0) == 0
                || !isOffStepSegment(segments.get(repeatIndex))) {
            // Nothing to do, not going back from a positive amplitude to a off segment.
            return repeatIndex;
        }

        VibrationEffectSegment offSegment = segments.get(repeatIndex);
        long offDuration = offSegment.getDuration();

        if (offDuration > mRampDownDuration) {
            // Split the zero amplitude segment and start repeating from the second half, to
            // preserve waveform timings. This will update the waveform as follows:
            //  R              R+1
            //  |   ____        |  ____
            // _|__/       => __|_/    \
            segments.set(repeatIndex,
                    updateStepDuration(offSegment, offDuration - mRampDownDuration));
            segments.add(repeatIndex, updateStepDuration(offSegment, mRampDownDuration));
        }

        // Skip the zero amplitude segment and append ramp/steps down at the end.
        repeatIndex++;
        segments.addAll(
                createStepsDown(lastAmplitude, Math.min(offDuration, mRampDownDuration)));

        return repeatIndex;
    }

    private List<VibrationEffectSegment> createStepsDown(float amplitude, long duration) {
        // Step down for at most the configured ramp duration.
        int stepCount = (int) Math.min(duration, mRampDownDuration) / STEP_DURATION_IN_MILLIS;
        float amplitudeStep = amplitude / stepCount;
        List<VibrationEffectSegment> steps = new ArrayList<>();
        for (int i = 1; i < stepCount; i++) {
            steps.add(new StepSegment(amplitude - i * amplitudeStep,
                    STEP_DURATION_IN_MILLIS));
        }
        int remainingDuration = (int) duration - STEP_DURATION_IN_MILLIS * (stepCount - 1);
        steps.add(new StepSegment(0, remainingDuration));
        return steps;
    }

    /** Returns the amplitude of the segment if it's a StepSegment, otherwise returns 0. */
    private static float getStepAmplitudeOrZero(VibrationEffectSegment segment) {
        if (segment instanceof StepSegment step) {
            return step.getAmplitude();
        }
        return 0;
    }

    private static VibrationEffectSegment updateStepDuration(VibrationEffectSegment segment,
            long newDuration) {
        if (segment instanceof StepSegment step) {
            return new StepSegment(step.getAmplitude(), (int) newDuration,
                    step.getStartTimeMillis());
        }
        return segment;
    }

    /** Returns true if the segment is a step that starts and ends at zero amplitude. */
    private static boolean isOffStepSegment(VibrationEffectSegment segment) {
        if (segment instanceof StepSegment step) {
            return step.getAmplitude() == 0;
        }
        return false;
    }

    /** Returns true if the segment is a step that ends at a non-zero amplitude. */
    private static boolean endsWithNonZeroAmplitude(VibrationEffectSegment segment) {
        if (segment instanceof StepSegment) {
            return ((StepSegment) segment).getAmplitude() != 0;
        }
        return false;
    }
}
