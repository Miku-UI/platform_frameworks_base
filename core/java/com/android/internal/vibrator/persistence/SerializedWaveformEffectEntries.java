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

package com.android.internal.vibrator.persistence;

import static com.android.internal.vibrator.persistence.XmlConstants.ATTRIBUTE_AMPLITUDE;
import static com.android.internal.vibrator.persistence.XmlConstants.ATTRIBUTE_DURATION_MS;
import static com.android.internal.vibrator.persistence.XmlConstants.ATTRIBUTE_START_TIME_MS;
import static com.android.internal.vibrator.persistence.XmlConstants.NAMESPACE;
import static com.android.internal.vibrator.persistence.XmlConstants.TAG_WAVEFORM_ENTRY;
import static com.android.internal.vibrator.persistence.XmlConstants.VALUE_AMPLITUDE_DEFAULT;

import android.annotation.NonNull;
import android.os.VibrationEffect;
import android.os.vibrator.StepSegment;
import android.util.IntArray;
import android.util.LongArray;

import com.android.internal.vibrator.persistence.SerializedComposedEffect.SerializedSegment;
import com.android.internal.vibrator.persistence.SerializedAmplitudeStepWaveform.StepSegmentBuilder;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Serialized representation of a list of waveform entries created via
 * {@link VibrationEffect#createWaveform(long[], int[], int)}.
 *
 * @hide
 */
@android.ravenwood.annotation.RavenwoodKeepWholeClass
final class SerializedWaveformEffectEntries implements SerializedSegment {

    @NonNull
    private final long[] mTimings;
    @NonNull
    private final int[] mAmplitudes;
    @NonNull
    private final long[] mStartTimesMillis;

    private SerializedWaveformEffectEntries(@NonNull long[] timings,
            @NonNull int[] amplitudes, @NonNull long[] startTimesMillis) {
        mTimings = timings;
        mAmplitudes = amplitudes;
        mStartTimesMillis = startTimesMillis;
    }

    @Override
    public void deserializeIntoComposition(@NonNull VibrationEffect.Composition composition) {
        List<StepSegment> segments = new ArrayList<>();
        for (int i = 0; i < mTimings.length; i++) {
            float parsedAmplitude = mAmplitudes[i] == VibrationEffect.DEFAULT_AMPLITUDE
                    ? VibrationEffect.DEFAULT_AMPLITUDE
                    : (float) mAmplitudes[i] / VibrationEffect.MAX_AMPLITUDE;
            segments.add(new StepSegment(parsedAmplitude, (int) mTimings[i], mStartTimesMillis[i]));
        }
        composition.addEffect(new VibrationEffect.Composed(segments, -1));
    }

    @Override
    public void write(@NonNull TypedXmlSerializer serializer) throws IOException {
        for (int i = 0; i < mTimings.length; i++) {
            serializer.startTag(NAMESPACE, TAG_WAVEFORM_ENTRY);

            if (mAmplitudes[i] == VibrationEffect.DEFAULT_AMPLITUDE) {
                serializer.attribute(NAMESPACE, ATTRIBUTE_AMPLITUDE, VALUE_AMPLITUDE_DEFAULT);
            } else {
                serializer.attributeInt(NAMESPACE, ATTRIBUTE_AMPLITUDE, mAmplitudes[i]);
            }

            serializer.attributeLong(NAMESPACE, ATTRIBUTE_DURATION_MS, mTimings[i]);
            if (mStartTimesMillis[i] >= 0) {
                serializer.attributeLong(NAMESPACE, ATTRIBUTE_START_TIME_MS, mStartTimesMillis[i]);
            }
            serializer.endTag(NAMESPACE, TAG_WAVEFORM_ENTRY);
        }

    }

    @Override
    public String toString() {
        return "SerializedWaveformEffectEntries{"
                + "timings=" + Arrays.toString(mTimings)
                + ", amplitudes=" + Arrays.toString(mAmplitudes)
                + ", startTimesMillis=" + Arrays.toString(mStartTimesMillis)
                + '}';
    }

    /** Builder for {@link SerializedWaveformEffectEntries}. */
    static final class Builder implements StepSegmentBuilder {
        private final LongArray mTimings = new LongArray();
        private final IntArray mAmplitudes = new IntArray();
        private final LongArray mStartTimesMillis = new LongArray();

        @Override
        public void addDurationAmplitudeAndStartTime(long durationMs, int amplitude,
                long startTimeMillis) {
            mTimings.add(durationMs);
            mAmplitudes.add(amplitude);
            mStartTimesMillis.add(startTimeMillis);
        }

        boolean hasNonZeroDuration() {
            for (int i = 0; i < mTimings.size(); i++) {
                if (mTimings.get(i) > 0) {
                    return true;
                }
            }
            return false;
        }

        SerializedWaveformEffectEntries build() {
            return new SerializedWaveformEffectEntries(
                    mTimings.toArray(), mAmplitudes.toArray(), mStartTimesMillis.toArray());
        }
    }

    /** Parser implementation for the {@link XmlConstants#TAG_WAVEFORM_ENTRY}. */
    static final class Parser {

        /** Parses a single {@link XmlConstants#TAG_WAVEFORM_ENTRY} into the builder. */
        public static void parseWaveformEntry(TypedXmlPullParser parser, Builder waveformBuilder)
                throws XmlParserException, IOException {
            SerializedAmplitudeStepWaveform.Parser.parseWaveformEntry(parser, waveformBuilder);
        }
    }
}
