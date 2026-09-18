/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.multisensory.repository.haptics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.VibratorEnvelopeEffectInfo;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class MultisensoryComposedEffectTest {

    @Mock private VibratorInfo.FrequencyProfile mFrequencyProfile;

    @Mock private VibratorInfo mVibratorInfo;
    @Mock private VibratorEnvelopeEffectInfo mVibratorEnvelopeEffectInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mVibratorInfo.areEnvelopeEffectsSupported()).thenReturn(false);
        when(mVibratorInfo.hasAmplitudeControl()).thenReturn(false);
        when(mVibratorInfo.isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK))
                .thenReturn(false);
        when(mVibratorInfo.getFrequencyProfile()).thenReturn(mFrequencyProfile);
        when(mVibratorEnvelopeEffectInfo.getMinControlPointDurationMillis()).thenReturn(5L);
    }

    @Test
    public void createSingleVibrationEffect_onEmptyPrimitivesEffect_returnsNull() {
        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        MultisensoryComposedEffect underTest = builder.build();

        assertNull(underTest.createSingleVibrationEffect());
    }

    @Test
    public void createSingleVibrationEffect_withUnsupportedPrimitive_returnsNull() {
        when(mVibratorInfo.isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK))
                .thenReturn(false);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        builder.addPrimitiveEffect(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK, /*scale*/ 1f, /*delayMillis*/ 0);
        MultisensoryComposedEffect underTest = builder.build();

        assertNull(underTest.createSingleVibrationEffect());
    }

    @Test
    public void createSingleVibrationEffect_withSupportedPrimitive_returnsEffect() {
        when(mVibratorInfo.isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK))
                .thenReturn(true);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        builder.addPrimitiveEffect(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK, /*scale*/ 1f, /*delayMillis*/ 0);
        MultisensoryComposedEffect underTest = builder.build();

        assertNotNull(underTest.createSingleVibrationEffect());
    }

    @Test
    public void createSingleVibrationEffect_unsupportedPrimitiveAndAmplitudeControl_hasFallback() {
        when(mVibratorInfo.isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK))
                .thenReturn(false);
        when(mVibratorInfo.hasAmplitudeControl()).thenReturn(true);
        VibrationEffect fallbackEffect =
                VibrationEffect.createWaveform(new long[] {100, 50}, new int[] {255, 100}, -1);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        builder.addPrimitiveEffect(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK, /*scale*/ 1f, /*delayMillis*/ 0);
        builder.addAmplitudeControlFallbackEffect(fallbackEffect);
        MultisensoryComposedEffect underTest = builder.build();

        assertEquals(fallbackEffect, underTest.createSingleVibrationEffect());
    }

    @Test
    public void createSingleVibrationEffect_unsupportedPrimitiveNoAmplitudeControl_returnsNull() {
        when(mVibratorInfo.isPrimitiveSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK))
                .thenReturn(false);
        when(mVibratorInfo.hasAmplitudeControl()).thenReturn(false);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        builder.addPrimitiveEffect(
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK, /*scale*/ 1f, /*delayMillis*/ 0);
        MultisensoryComposedEffect underTest = builder.build();

        assertNull(underTest.createSingleVibrationEffect());
    }

    @Test
    public void createContinuousEffect_withSupportedEnvelope_returnsEffect() {
        when(mVibratorInfo.areEnvelopeEffectsSupported()).thenReturn(true);
        when(mFrequencyProfile.getMinFrequencyHz()).thenReturn(50f);
        when(mFrequencyProfile.getMaxFrequencyHz()).thenReturn(200f);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        builder.addEnvelopeEffect(/*baseScale*/ 1f, /*baseFrequency*/ 100f);
        MultisensoryComposedEffect underTest = builder.build();

        assertNotNull(underTest.createContinuousEffect());
    }

    @Test
    public void createContinuousEffect_withUnsupportedEnvelope_returnsNull() {
        when(mVibratorInfo.areEnvelopeEffectsSupported()).thenReturn(false);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        builder.addEnvelopeEffect(/*baseScale*/ 1f, /*baseFrequency*/ 100f);
        MultisensoryComposedEffect underTest = builder.build();

        assertNull(underTest.createContinuousEffect());
    }

    @Test
    public void createContinuousEffect_withNoFrequency_returnsNull() {
        when(mVibratorInfo.areEnvelopeEffectsSupported()).thenReturn(true);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        MultisensoryComposedEffect underTest = builder.build();

        assertNull(underTest.createContinuousEffect());
    }

    @Test
    public void createContinuousEffect_withFrequencyBelowRange_returnsNull() {
        when(mVibratorInfo.areEnvelopeEffectsSupported()).thenReturn(true);
        when(mFrequencyProfile.getMinFrequencyHz()).thenReturn(50f);
        when(mFrequencyProfile.getMaxFrequencyHz()).thenReturn(200f);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        builder.addEnvelopeEffect(/*baseScale*/ 1f, /*baseFrequency*/ 49f);
        MultisensoryComposedEffect underTest = builder.build();

        assertNull(underTest.createContinuousEffect());
    }

    @Test
    public void createContinuousEffect_withFrequencyAboveRange_returnsNull() {
        when(mVibratorInfo.areEnvelopeEffectsSupported()).thenReturn(true);
        when(mFrequencyProfile.getMinFrequencyHz()).thenReturn(50f);
        when(mFrequencyProfile.getMaxFrequencyHz()).thenReturn(200f);

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        builder.addEnvelopeEffect(/*baseScale*/ 1f, /*baseFrequency*/ 201f);
        MultisensoryComposedEffect underTest = builder.build();

        assertNull(underTest.createContinuousEffect());
    }

    @Test
    public void addPrimitiveEffect_withNegativeScale_throwsIllegalArgumentException() {
        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.addPrimitiveEffect(
                                VibrationEffect.Composition.PRIMITIVE_LOW_TICK, /*scale*/
                                -1f, /*delayMillis*/
                                0));
    }

    @Test
    public void addPrimitiveEffect_withScaleGreaterThanOne_throwsIllegalArgumentException() {
        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.addPrimitiveEffect(
                                VibrationEffect.Composition.PRIMITIVE_LOW_TICK, /*scale*/
                                1.1f, /*delayMillis*/
                                0));
    }

    @Test
    public void addPrimitiveEffect_withNegativeDelay_throwsIllegalArgumentException() {
        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        builder.addPrimitiveEffect(
                                VibrationEffect.Composition.PRIMITIVE_LOW_TICK, /*scale*/
                                1f, /*delayMillis*/
                                -1));
    }

    @Test
    public void addEnvelopeEffect_withNegativeScale_throwsIllegalArgumentException() {
        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.addEnvelopeEffect(/*baseScale*/ -1f, /*baseFrequency*/ 100f));
    }

    @Test
    public void addEnvelopeEffect_withScaleGreaterThanOne_throwsIllegalArgumentException() {
        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.addEnvelopeEffect(/*baseScale*/ 1.1f, /*baseFrequency*/ 100f));
    }

    @Test
    public void addEnvelopeEffect_withNegativeFrequency_throwsIllegalArgumentException() {
        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(mVibratorInfo, mVibratorEnvelopeEffectInfo);
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.addEnvelopeEffect(/*baseScale*/ 1f, /*baseFrequency*/ -1f));
    }
}
