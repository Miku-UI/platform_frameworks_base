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
import android.os.vibrator.RampSegment;
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
                new StepSegment(/* amplitude= */ 0, /* frequencyHz= */ 1, /* duration= */ 10),
                new RampSegment(/* startAmplitude= */ 0.8f, /* endAmplitude= */ 0.2f,
                        /* startFrequencyHz= */ 100, /* endFrequencyHz= */ 1, /* duration= */ 20),
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

        List<VibrationEffectSegment> expectedSegments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new StepSegment(DEFAULT_AMPLITUDE, 0, 10),
                new PrebakedSegment(EFFECT_POP, true, VibrationEffect.EFFECT_STRENGTH_STRONG)));

        // Repeat index maintained after replacement
        assertEquals(1, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 1));

        assertEquals(expectedSegments, segments);
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

        List<VibrationEffectSegment> expectedSegments = new ArrayList<>(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new StepSegment(0, 0, 10),
                new StepSegment(DEFAULT_AMPLITUDE, 0, 10),
                new StepSegment(0, 0, 10),
                new StepSegment(DEFAULT_AMPLITUDE, 0, 10),
                new PrebakedSegment(EFFECT_POP, true, VibrationEffect.EFFECT_STRENGTH_STRONG)));

        // Repeat index maintained after replacement
        assertEquals(5, mAdapter.adaptToVibrator(BASIC_VIBRATOR_INFO, segments, 2));

        assertEquals(expectedSegments, segments);
    }

    private static VibratorInfo createVibratorInfoWithEffects(int... ids) {
        return new VibratorInfo.Builder(0).setSupportedEffects(ids).build();
    }
}
