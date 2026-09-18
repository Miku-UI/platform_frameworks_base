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

import static android.os.VibrationEffect.Composition.DELAY_TYPE_PAUSE;
import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.DEFAULT_AMPLITUDE;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.EFFECT_POP;
import static android.os.VibrationEffect.EFFECT_THUD;
import static android.os.VibrationEffect.EFFECT_TICK;

import static org.junit.Assert.assertEquals;

import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.Flags;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.SparseArray;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrebakedFallbackAdapterTest {
    private static final VibratorInfo EMPTY_VIBRATOR_INFO = new VibratorInfo.Builder(0).build();
    private static final VibratorInfo BASIC_VIBRATOR_INFO = createVibratorInfoWithEffects(
            EFFECT_CLICK, EFFECT_TICK);

    private final SparseArray<VibrationEffect> mFallbackEffects = new SparseArray<>();
    private PrebakedFallbackAdapter mAdapter = new PrebakedFallbackAdapter(mFallbackEffects);

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedSegments_flagDisabled_keepsListUnchanged() {
        mFallbackEffects.put(EFFECT_THUD, VibrationEffect.createOneShot(10, 100));
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, false, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_LIGHT)));
        List<VibrationEffectSegment> originalSegments = new ArrayList<>(segments);

        assertEquals(-1, mAdapter.adaptToVibrator(EMPTY_VIBRATOR_INFO, segments, -1));
        assertEquals(1, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 1));

        assertEquals(originalSegments, segments);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testNonPrebakedSegments_keepsListUnchanged() {
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new StepSegment(/* amplitude= */ 0, /* duration= */ 10),
                new StepSegment(/* amplitude= */ 0.8f, /* duration= */ 20),
                new PrimitiveSegment(PRIMITIVE_CLICK, 1f, 100, DELAY_TYPE_PAUSE)));
        List<VibrationEffectSegment> originalSegments = new ArrayList<>(segments);

        assertEquals(-1, mAdapter.adaptToVibrator(EMPTY_VIBRATOR_INFO, segments, -1));
        assertEquals(1, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 1));

        assertEquals(originalSegments, segments);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedSupported_keepsListUnchanged() {
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_TICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT)));
        List<VibrationEffectSegment> originalSegments = new ArrayList<>(segments);

        assertEquals(1, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 1));

        assertEquals(originalSegments, segments);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedUnsupportedWithoutFallback_keepsListUnchanged() {
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_LIGHT)));
        List<VibrationEffectSegment> originalSegments = new ArrayList<>(segments);

        assertEquals(1, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 1));

        assertEquals(originalSegments, segments);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedUnsupportedWithFallbackFalse_keepsListUnchanged() {
        mFallbackEffects.put(EFFECT_THUD, VibrationEffect.createOneShot(10, 100));
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, false, VibrationEffect.EFFECT_STRENGTH_LIGHT)));
        List<VibrationEffectSegment> originalSegments = new ArrayList<>(segments);

        assertEquals(1, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 1));

        assertEquals(originalSegments, segments);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedUnsupportedWithFallback_replacesUnsupportedEffects() {
        mFallbackEffects.put(EFFECT_THUD, VibrationEffect.createOneShot(10, DEFAULT_AMPLITUDE));
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_MEDIUM),
                new PrebakedSegment(EFFECT_POP, true, VibrationEffect.EFFECT_STRENGTH_STRONG)));

        // Repeat index maintained after replacement
        assertEquals(1, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 1));

        assertEquals(3, segments.size());
        assertEquals(EFFECT_CLICK, ((PrebakedSegment) segments.get(0)).getEffectId());
        assertEquals(DEFAULT_AMPLITUDE, ((StepSegment) segments.get(1)).getAmplitude(), 0);
        assertEquals(EFFECT_POP, ((PrebakedSegment) segments.get(2)).getEffectId());

        // Confirm all have default start time
        assertEquals(-1, segments.get(0).getStartTimeMillis());
        assertEquals(-1, segments.get(1).getStartTimeMillis());
        assertEquals(-1, segments.get(2).getStartTimeMillis());
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedUnsupportedWithFallback_replacesUnsupportedAndUpdatesRepeatIndex() {
        mFallbackEffects.put(EFFECT_THUD, VibrationEffect.createWaveform(
                new long[] { 10, 10, 10, 10 }, -1));
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_MEDIUM),
                new PrebakedSegment(EFFECT_POP, true, VibrationEffect.EFFECT_STRENGTH_STRONG)));

        // Repeat index maintained after replacement
        assertEquals(5, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 2));

        assertEquals(6, segments.size());
        assertEquals(EFFECT_CLICK, ((PrebakedSegment) segments.get(0)).getEffectId());
        assertEquals(0, ((StepSegment) segments.get(1)).getAmplitude(), 0);
        assertEquals(DEFAULT_AMPLITUDE, ((StepSegment) segments.get(2)).getAmplitude(), 0);
        assertEquals(0, ((StepSegment) segments.get(3)).getAmplitude(), 0);
        assertEquals(DEFAULT_AMPLITUDE, ((StepSegment) segments.get(4)).getAmplitude(), 0);
        assertEquals(EFFECT_POP, ((PrebakedSegment) segments.get(5)).getEffectId());

        // Confirm all have default start time
        for (VibrationEffectSegment segment : segments) {
            assertEquals(-1, segment.getStartTimeMillis());
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedUnsupportedWithFallback_shiftsStartTime() {
        long fallbackSegmentStartTime = 100L;
        VibrationEffect fallback = new VibrationEffect.Composed(
                Arrays.asList(
                        new StepSegment(DEFAULT_AMPLITUDE, 10, -1),
                        new StepSegment(DEFAULT_AMPLITUDE, 20, fallbackSegmentStartTime),
                        new StepSegment(DEFAULT_AMPLITUDE, 30, -1)),
                -1);
        mFallbackEffects.put(EFFECT_THUD, fallback);

        long prebakedStartTime = 1234L;
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_MEDIUM,
                        prebakedStartTime)));

        mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, -1);

        assertEquals(3, segments.size());
        // First segment had -1 but it is the 1st segment, so it gets prebakedStartTime
        assertEquals(prebakedStartTime, segments.get(0).getStartTimeMillis());
        // Second segment had fallbackSegmentStartTime, so it gets prebakedStartTime +
        // fallbackSegmentStartTime
        assertEquals(
                prebakedStartTime + fallbackSegmentStartTime, segments.get(1).getStartTimeMillis());
        // Third segment had -1 and it is not the 1st segment, so it gets -1
        assertEquals(-1, segments.get(2).getStartTimeMillis());

        segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_MEDIUM)));

        mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, -1);

        assertEquals(3, segments.size());
        // All segments have start time = -1, because the prebaked segment had no start time.
        assertEquals(-1, segments.get(0).getStartTimeMillis());
        assertEquals(-1, segments.get(1).getStartTimeMillis());
        assertEquals(-1, segments.get(2).getStartTimeMillis());
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedUnsupportedWithFallback_propagatesStartTime() {
        mFallbackEffects.put(EFFECT_THUD, VibrationEffect.createWaveform(
                new long[] { 10, 10, 10, 10 }, -1));
        long startTime = 1234L;
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_MEDIUM,
                        startTime)));

        mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, -1);

        assertEquals(4, segments.size());
        assertEquals(startTime, segments.get(0).getStartTimeMillis());
        assertEquals(-1, segments.get(1).getStartTimeMillis());
        assertEquals(-1, segments.get(2).getStartTimeMillis());
        assertEquals(-1, segments.get(3).getStartTimeMillis());
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void
            testPrebakedUnsupportedWithFallback_repeatingWithPrimitivePreamble_propagatesStartTime() {
        VibrationEffect fallback =
                new VibrationEffect.Composed(
                        Arrays.asList(
                                new StepSegment(DEFAULT_AMPLITUDE, 10, -1),
                                new StepSegment(DEFAULT_AMPLITUDE, 20, -1)),
                        -1);
        mFallbackEffects.put(EFFECT_THUD, fallback);

        long prebakedStartTime = 500L;
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new PrimitiveSegment(PRIMITIVE_CLICK, 1f, 0, DELAY_TYPE_PAUSE),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_MEDIUM,
                        prebakedStartTime)));

        // repeatIndex = 1 (pointing to the PrebakedSegment)
        int newRepeatIndex = mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 1);

        assertEquals(1, newRepeatIndex);
        assertEquals(3, segments.size());
        assertEquals(PRIMITIVE_CLICK, ((PrimitiveSegment) segments.get(0)).getPrimitiveId());

        // The first fallback segment should have the startTime from the prebaked segment
        assertEquals(prebakedStartTime, segments.get(1).getStartTimeMillis());
        // The second fallback segment should have -1
        assertEquals(-1, segments.get(2).getStartTimeMillis());
    }

    private static VibratorInfo createVibratorInfoWithEffects(int... ids) {
        return new VibratorInfo.Builder(0).setSupportedEffects(ids).build();
    }
}
