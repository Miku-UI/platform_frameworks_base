/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static com.android.internal.vibrator.persistence.XmlConstants.TAG_REPEATING;
import static com.android.internal.vibrator.persistence.XmlConstants.TAG_WAVEFORM_EFFECT;
import static com.android.internal.vibrator.persistence.XmlConstants.TAG_WAVEFORM_ENTRY;
import static com.android.internal.vibrator.persistence.XmlConstants.VALUE_AMPLITUDE_DEFAULT;

import android.annotation.NonNull;
import android.os.VibrationEffect;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.util.IntArray;
import android.util.LongArray;

import com.android.internal.vibrator.persistence.SerializedComposedEffect.SerializedSegment;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Serialized representation of a waveform effect created via
 * {@link VibrationEffect#createWaveform(long[], int[], int)}.
 *
 * @hide
 */
@android.ravenwood.annotation.RavenwoodKeepWholeClass
final class SerializedAmplitudeStepWaveform implements SerializedSegment {

    /** Interface for building step segments with duration, amplitude and start time. */
    interface StepSegmentBuilder {
        void addDurationAmplitudeAndStartTime(long durationMs, int amplitude, long startTimeMillis);
    }

    @NonNull private final long[] mTimings;
    @NonNull private final int[] mAmplitudes;
    @NonNull private final long[] mStartTimesMillis;
    private final int mRepeatIndex;

    private SerializedAmplitudeStepWaveform(long[] timings, int[] amplitudes,
            long[] startTimesMillis, int repeatIndex) {
        mTimings = timings;
        mAmplitudes = amplitudes;
        mStartTimesMillis = startTimesMillis;
        mRepeatIndex = repeatIndex;
    }

    @Override
    public void deserializeIntoComposition(@NonNull VibrationEffect.Composition composition) {
        List<StepSegment> segments = new ArrayList<>();
        for (int i = 0; i < mTimings.length; i++) {
            float parsedAmplitude = mAmplitudes[i] == VibrationEffect.DEFAULT_AMPLITUDE
                    ? VibrationEffect.DEFAULT_AMPLITUDE : (float) mAmplitudes[i] / 255f;
            segments.add(new StepSegment(parsedAmplitude, (int) mTimings[i], mStartTimesMillis[i]));
        }
        composition.addEffect(new VibrationEffect.Composed(segments, mRepeatIndex));
    }

    @Override
    public void write(@NonNull TypedXmlSerializer serializer) throws IOException {
        serializer.startTag(NAMESPACE, TAG_WAVEFORM_EFFECT);

        for (int i = 0; i < mTimings.length; i++) {
            if (i == mRepeatIndex) {
                serializer.startTag(NAMESPACE, TAG_REPEATING);
            }
            writeWaveformEntry(serializer, i);
        }

        if (mRepeatIndex >= 0) {
            serializer.endTag(NAMESPACE, TAG_REPEATING);
        }
        serializer.endTag(NAMESPACE, TAG_WAVEFORM_EFFECT);
    }

    private void writeWaveformEntry(@NonNull TypedXmlSerializer serializer, int index)
            throws IOException {
        serializer.startTag(NAMESPACE, TAG_WAVEFORM_ENTRY);

        if (mAmplitudes[index] == VibrationEffect.DEFAULT_AMPLITUDE) {
            serializer.attribute(NAMESPACE, ATTRIBUTE_AMPLITUDE, VALUE_AMPLITUDE_DEFAULT);
        } else {
            serializer.attributeInt(NAMESPACE, ATTRIBUTE_AMPLITUDE, mAmplitudes[index]);
        }

        serializer.attributeLong(NAMESPACE, ATTRIBUTE_DURATION_MS, mTimings[index]);
        if (mStartTimesMillis[index] >= 0) {
            serializer.attributeLong(NAMESPACE, ATTRIBUTE_START_TIME_MS, mStartTimesMillis[index]);
        }
        serializer.endTag(NAMESPACE, TAG_WAVEFORM_ENTRY);
    }

    @Override
    public String toString() {
        return "SerializedAmplitudeStepWaveform{"
                + "timings=" + Arrays.toString(mTimings)
                + ", amplitudes=" + Arrays.toString(mAmplitudes)
                + ", startTimesMillis=" + Arrays.toString(mStartTimesMillis)
                + ", repeatIndex=" + mRepeatIndex
                + '}';
    }

    /** Builder for {@link SerializedAmplitudeStepWaveform}. */
    static final class Builder implements StepSegmentBuilder {
        private final LongArray mTimings = new LongArray();
        private final IntArray mAmplitudes = new IntArray();
        private final LongArray mStartTimesMillis = new LongArray();
        private int mRepeatIndex = -1;

        @Override
        public void addDurationAmplitudeAndStartTime(long durationMs, int amplitude,
                long startTimeMillis) {
            mTimings.add(durationMs);
            mAmplitudes.add(amplitude);
            mStartTimesMillis.add(startTimeMillis);
        }

        void setRepeatIndexToCurrentEntry() {
            mRepeatIndex = mTimings.size();
        }

        boolean hasNonZeroDuration() {
            for (int i = 0; i < mTimings.size(); i++) {
                if (mTimings.get(i) > 0) {
                    return true;
                }
            }
            return false;
        }

        SerializedAmplitudeStepWaveform build() {
            return new SerializedAmplitudeStepWaveform(
                    mTimings.toArray(), mAmplitudes.toArray(), mStartTimesMillis.toArray(),
                    mRepeatIndex);
        }
    }

    /** Parser implementation for the {@link XmlConstants#TAG_WAVEFORM_EFFECT}. */
    static final class Parser {

        @NonNull
        static SerializedAmplitudeStepWaveform parseNext(@NonNull TypedXmlPullParser parser)
                throws XmlParserException, IOException {
            XmlValidator.checkStartTag(parser, TAG_WAVEFORM_EFFECT);
            XmlValidator.checkTagHasNoUnexpectedAttributes(parser);

            Builder waveformBuilder = new Builder();
            int outerDepth = parser.getDepth();

            // Read all nested tag that is not a repeating tag as a waveform entry.
            while (XmlReader.readNextTagWithin(parser, outerDepth)
                    && !TAG_REPEATING.equals(parser.getName())) {
                parseWaveformEntry(parser, waveformBuilder);
            }

            // If found a repeating tag, read its content.
            if (TAG_REPEATING.equals(parser.getName())) {
                parseRepeating(parser, waveformBuilder);
            }

            // Check schema assertions about <waveform-effect>
            XmlValidator.checkParserCondition(waveformBuilder.hasNonZeroDuration(),
                    "Unexpected %s tag with total duration zero", TAG_WAVEFORM_EFFECT);

            // Consume tag
            XmlReader.readEndTag(parser, TAG_WAVEFORM_EFFECT, outerDepth);

            return waveformBuilder.build();
        }

        static void parseWaveformEntry(TypedXmlPullParser parser, StepSegmentBuilder builder)
                throws XmlParserException, IOException {
            XmlValidator.checkStartTag(parser, TAG_WAVEFORM_ENTRY);
            XmlValidator.checkTagHasNoUnexpectedAttributes(
                    parser, ATTRIBUTE_DURATION_MS, ATTRIBUTE_AMPLITUDE, ATTRIBUTE_START_TIME_MS);

            String rawAmplitude = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_AMPLITUDE);
            int amplitude = VALUE_AMPLITUDE_DEFAULT.equals(rawAmplitude)
                    ? VibrationEffect.DEFAULT_AMPLITUDE
                    : XmlReader.readAttributeIntInRange(
                            parser, ATTRIBUTE_AMPLITUDE, 0, VibrationEffect.MAX_AMPLITUDE);
            int durationMs = XmlReader.readAttributeIntNonNegative(parser, ATTRIBUTE_DURATION_MS);
            long startTimeMillis = -1;
            if (parser.getAttributeIndex(NAMESPACE, ATTRIBUTE_START_TIME_MS) >= 0) {
                startTimeMillis = XmlReader.readAttributeIntInRange(
                        parser, ATTRIBUTE_START_TIME_MS, 0, Integer.MAX_VALUE);
            }

            builder.addDurationAmplitudeAndStartTime(durationMs, amplitude, startTimeMillis);

            // Consume tag
            XmlReader.readEndTag(parser);
        }

        private static void parseRepeating(TypedXmlPullParser parser, Builder waveformBuilder)
                throws XmlParserException, IOException {
            XmlValidator.checkStartTag(parser, TAG_REPEATING);
            XmlValidator.checkTagHasNoUnexpectedAttributes(parser);

            waveformBuilder.setRepeatIndexToCurrentEntry();

            boolean hasEntry = false;
            int outerDepth = parser.getDepth();
            while (XmlReader.readNextTagWithin(parser, outerDepth)) {
                parseWaveformEntry(parser, waveformBuilder);
                hasEntry = true;
            }

            // Check schema assertions about <repeating>
            XmlValidator.checkParserCondition(hasEntry, "Unexpected empty %s tag", TAG_REPEATING);

            // Consume tag
            XmlReader.readEndTag(parser, TAG_REPEATING, outerDepth);
        }
    }
}
