/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.EFFECT_DOUBLE_CLICK;
import static android.os.VibrationEffect.EFFECT_STRENGTH_MEDIUM;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.hardware.vibrator.IVibrator;
import android.os.IBinder;
import android.os.IVibratorStateListener;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.test.TestLooper;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.os.vibrator.RampSegment;
import android.os.vibrator.StepSegment;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

/** Base test class for {@link HalVibrator} implementations. */
public abstract class HalVibratorTestCase {
    static final int VIBRATOR_ID = 0;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock HalVibrator.Callbacks mCallbacksMock;
    @Mock private IVibratorStateListener mVibratorStateListenerMock;
    @Mock private IBinder mVibratorStateListenerBinderMock;

    private final TestLooper mTestLooper = new TestLooper();
    final HalVibratorHelper mHelper = new HalVibratorHelper(mTestLooper.getLooper());

    abstract HalVibrator newVibrator(int vibratorId);

    HalVibrator newInitializedVibrator(int vibratorId) {
        HalVibrator vibrator = newVibrator(vibratorId);
        vibrator.init(mCallbacksMock);
        vibrator.onSystemReady();
        return vibrator;
    }

    @Before
    public void setUp() throws Exception {
        when(mVibratorStateListenerMock.asBinder()).thenReturn(mVibratorStateListenerBinderMock);
    }

    @Test
    public void init_initializesVibratorAndSetsId() {
        HalVibrator vibrator = newVibrator(VIBRATOR_ID);
        vibrator.init(mCallbacksMock);

        assertThat(mHelper.isInitialized()).isTrue();
        assertThat(vibrator.getInfo().getId()).isEqualTo(VIBRATOR_ID);
    }

    @Test
    public void init_turnsOffVibratorAndDisablesExternalControl() {
        mHelper.setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        HalVibrator vibrator = newVibrator(VIBRATOR_ID);
        vibrator.init(mCallbacksMock);

        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(mHelper.getOffCount()).isEqualTo(1);
        assertThat(mHelper.getExternalControlStates()).containsExactly(false).inOrder();
    }

    @Test
    public void getInfo_basicInfo_loadFromHal() {
        mHelper.setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL, IVibrator.CAP_COMPOSE_EFFECTS);
        mHelper.setSupportedEffects(EFFECT_CLICK, EFFECT_DOUBLE_CLICK);
        VibratorInfo info = newInitializedVibrator(VIBRATOR_ID).getInfo();

        assertThat(info.getCapabilities())
                .isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL | IVibrator.CAP_COMPOSE_EFFECTS);
        assertThat(toSupportedList(info.getSupportedEffects()))
                .containsExactly(EFFECT_CLICK, EFFECT_DOUBLE_CLICK);
    }

    @Test
    public void getInfo_qFactorSupported_loadValue() {
        mHelper.setCapabilities(IVibrator.CAP_GET_Q_FACTOR);
        mHelper.setQFactor(123f);
        assertThat(newInitializedVibrator(VIBRATOR_ID).getInfo().getQFactor()).isEqualTo(123f);
    }

    @Test
    public void getInfo_qFactorUnsupported_returnsNaN() {
        mHelper.setQFactor(123f);
        assertThat(newInitializedVibrator(VIBRATOR_ID).getInfo().getQFactor()).isNaN();
    }

    @Test
    public void getInfo_resonantFrequencySupported_loadValue() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY);
        mHelper.setResonantFrequency(123f);
        assertThat(newInitializedVibrator(VIBRATOR_ID).getInfo().getResonantFrequencyHz())
                .isEqualTo(123f);
    }

    @Test
    public void getInfo_resonantFrequencyUnsupported_returnsNaN() {
        mHelper.setResonantFrequency(123f);
        assertThat(newInitializedVibrator(VIBRATOR_ID).getInfo().getResonantFrequencyHz()).isNaN();
    }

    @Test
    public void getInfo_primitivesSupported_loadCompositionValues() {
        mHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        mHelper.setPrimitiveDuration(10);
        mHelper.setCompositionDelayMax(30);
        mHelper.setCompositionSizeMax(100);
        VibratorInfo info = newInitializedVibrator(VIBRATOR_ID).getInfo();

        SparseIntArray supportedPrimitives = info.getSupportedPrimitives();
        assertThat(supportedPrimitives.size()).isEqualTo(2);
        assertThat(supportedPrimitives.get(PRIMITIVE_CLICK)).isEqualTo(10);
        assertThat(supportedPrimitives.get(PRIMITIVE_TICK)).isEqualTo(10);
        assertThat(info.getPrimitiveDelayMax()).isEqualTo(30);
        assertThat(info.getCompositionSizeMax()).isEqualTo(100);
    }

    @Test
    public void getInfo_primitivesUnsupported_returnsDefaults() {
        mHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        mHelper.setPrimitiveDuration(10);
        mHelper.setCompositionDelayMax(30);
        mHelper.setCompositionSizeMax(100);
        VibratorInfo info = newInitializedVibrator(VIBRATOR_ID).getInfo();

        assertThat(info.getSupportedPrimitives().size()).isEqualTo(0);
        assertThat(info.getPrimitiveDelayMax()).isEqualTo(0);
        assertThat(info.getCompositionSizeMax()).isEqualTo(0);
    }

    @Test
    public void getInfo_pwleV2Supported_loadValues() {
        mHelper.setCapabilities(IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        mHelper.setMaxEnvelopeEffectSize(100);
        mHelper.setMinEnvelopeEffectControlPointDurationMillis(10);
        mHelper.setMaxEnvelopeEffectControlPointDurationMillis(30);
        VibratorInfo info = newInitializedVibrator(VIBRATOR_ID).getInfo();

        assertThat(info.getMinEnvelopeEffectControlPointDurationMillis()).isEqualTo(10);
        assertThat(info.getMaxEnvelopeEffectControlPointDurationMillis()).isEqualTo(30);
        assertThat(info.getMaxEnvelopeEffectSize()).isEqualTo(100);
    }

    @Test
    public void getInfo_pwleV2Unsupported_returnsDefaults() {
        mHelper.setMaxEnvelopeEffectSize(100);
        mHelper.setMinEnvelopeEffectControlPointDurationMillis(10);
        mHelper.setMaxEnvelopeEffectControlPointDurationMillis(30);
        VibratorInfo info = newInitializedVibrator(VIBRATOR_ID).getInfo();

        assertThat(info.areEnvelopeEffectsSupported()).isFalse();
        assertThat(info.getMinEnvelopeEffectControlPointDurationMillis()).isEqualTo(0);
        assertThat(info.getMaxEnvelopeEffectControlPointDurationMillis()).isEqualTo(0);
        assertThat(info.getMaxEnvelopeEffectSize()).isEqualTo(0);
    }

    @Test
    public void getInfo_frequencyControlSupported_loadValues() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL);
        mHelper.setResonantFrequency(125f);
        mHelper.setMinFrequency(100f);
        mHelper.setFrequencyResolution(25f);
        mHelper.setMaxAmplitudes(0.5f, 1f, 0.5f);
        mHelper.setFrequenciesHz(new float[] { 100, 200 });
        mHelper.setOutputAccelerationsGs(new float[] { 0.5f, 1f });
        VibratorInfo info = newInitializedVibrator(VIBRATOR_ID).getInfo();

        assertThat(info.getFrequencyProfileLegacy().getFrequencyResolutionHz()).isEqualTo(25f);
        assertThat(info.getFrequencyProfileLegacy().getMaxAmplitudes()).hasLength(3);
        assertThat(info.getFrequencyProfile().getFrequenciesHz()).hasLength(2);
        assertThat(info.getFrequencyProfile().getOutputAccelerationsGs()).hasLength(2);
    }

    @Test
    public void getInfo_frequencyControlUnsupported_returnsDefaults() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY);
        mHelper.setResonantFrequency(125f);
        mHelper.setMinFrequency(100f);
        mHelper.setFrequencyResolution(25f);
        mHelper.setMaxAmplitudes(0.5f, 1f, 0.5f);
        mHelper.setFrequenciesHz(new float[] { 100, 200 });
        mHelper.setOutputAccelerationsGs(new float[] { 0.5f, 1f });
        VibratorInfo info = newInitializedVibrator(VIBRATOR_ID).getInfo();

        assertThat(info.getFrequencyProfileLegacy().isEmpty()).isTrue();
        assertThat(info.getFrequencyProfile().getFrequenciesHz()).isNull();
        assertThat(info.getFrequencyProfile().getOutputAccelerationsGs()).isNull();
    }

    @Test
    public void setExternalControl_withCapability_enablesExternalControl() {
        mHelper.setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        assertThat(vibrator.isVibrating()).isFalse();

        vibrator.setExternalControl(true);
        assertThat(vibrator.isVibrating()).isTrue();

        vibrator.setExternalControl(false);
        assertThat(vibrator.isVibrating()).isFalse();

        assertThat(mHelper.getExternalControlStates())
                .containsExactly(false, true, false).inOrder();
    }

    @Test
    public void setExternalControl_withNoCapability_ignoresExternalControl() {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        assertThat(vibrator.isVibrating()).isFalse();

        vibrator.setExternalControl(true);
        assertThat(vibrator.isVibrating()).isFalse();

        assertThat(mHelper.getExternalControlStates()).isEmpty();
    }

    @Test
    public void setAlwaysOn_withCapability_enablesAndDisablesAlwaysOnEffect() {
        mHelper.setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        mHelper.setSupportedEffects(EFFECT_CLICK);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        PrebakedSegment prebaked = createPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_MEDIUM);

        vibrator.setAlwaysOn(1, prebaked);
        assertThat(mHelper.getAlwaysOnEffect(1)).isEqualTo(prebaked);

        vibrator.setAlwaysOn(1, null);
        assertThat(mHelper.getAlwaysOnEffect(1)).isNull();
    }

    @Test
    public void setAlwaysOn_withoutCapability_ignoresEffect() {
        mHelper.setSupportedEffects(EFFECT_CLICK);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrebakedSegment prebaked = createPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_MEDIUM);

        vibrator.setAlwaysOn(1, prebaked);
        assertThat(mHelper.getAlwaysOnEffect(1)).isNull();
    }

    @Test
    public void setAmplitude_vibratorIdle_ignoresAmplitude() {
        mHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        assertThat(vibrator.isVibrating()).isFalse();

        vibrator.setAmplitude(1);
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
        assertThat(mHelper.getAmplitudes()).containsExactly(1f).inOrder();
    }

    @Test
    public void setAmplitude_vibratorUnderExternalControl_ignoresAmplitude() {
        mHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL, IVibrator.CAP_EXTERNAL_CONTROL);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        vibrator.setExternalControl(true);
        assertThat(vibrator.isVibrating()).isTrue();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(-1f);

        vibrator.setAmplitude(1);
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(-1f);
        assertThat(mHelper.getAmplitudes()).containsExactly(1f).inOrder();
    }

    @Test
    public void setAmplitude_vibratorVibrating_setsAmplitude() {
        mHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        vibrator.on(1, 1, /* milliseconds= */ 100);
        assertThat(vibrator.isVibrating()).isTrue();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(-1f);

        vibrator.setAmplitude(1);
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(1f);
        assertThat(mHelper.getAmplitudes()).containsExactly(1f).inOrder();
    }

    @Test
    public void on_withDuration_turnsVibratorOn() {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        assertThat(vibrator.on(1, 1, /* milliseconds= */ 100)).isEqualTo(100L);
        assertThat(vibrator.isVibrating()).isTrue();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(-1f);
        assertThat(mHelper.getEffectSegments()).containsExactly(createStep(100)).inOrder();
    }

    @Test
    public void on_withDurationAndCallbackSupported_triggersCallbackFromHal() {
        long durationMs = 10;
        mHelper.setCapabilities(IVibrator.CAP_ON_CALLBACK);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        assertThat(vibrator.on(10, 100, durationMs)).isEqualTo(durationMs);
        mTestLooper.moveTimeForward(durationMs);
        mTestLooper.dispatchAll();

        verify(mCallbacksMock).onVibrationStepComplete(eq(VIBRATOR_ID), eq(10L), eq(100L));
    }

    @Test
    public void on_withDurationAndCallbackNotSupported_triggersCallbackFromHandler() {
        long durationMs = 10;
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        assertThat(vibrator.on(1, 2, durationMs)).isEqualTo(durationMs);
        mTestLooper.moveTimeForward(durationMs);
        mTestLooper.dispatchAll();

        verify(mCallbacksMock).onVibrationStepComplete(eq(VIBRATOR_ID), eq(1L), eq(2L));
    }

    @Test
    public void on_withPrebaked_performsEffect() {
        mHelper.setSupportedEffects(EFFECT_CLICK);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrebakedSegment prebaked = createPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_MEDIUM);

        assertThat(vibrator.on(1, 1, prebaked)).isEqualTo(HalVibratorHelper.EFFECT_DURATION);
        assertThat(vibrator.isVibrating()).isTrue();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(-1f);
        assertThat(mHelper.getEffectSegments()).containsExactly(prebaked).inOrder();
    }

    @Test
    public void on_withPrebakedAndCallbackSupported_triggersCallbackFromHal() {
        mHelper.setCapabilities(IVibrator.CAP_PERFORM_CALLBACK);
        mHelper.setSupportedEffects(EFFECT_CLICK);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrebakedSegment prebaked = createPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_MEDIUM);

        assertThat(vibrator.on(123, 456, prebaked)).isEqualTo(HalVibratorHelper.EFFECT_DURATION);
        mTestLooper.moveTimeForward(HalVibratorHelper.EFFECT_DURATION);
        mTestLooper.dispatchAll();

        verify(mCallbacksMock).onVibrationStepComplete(eq(VIBRATOR_ID), eq(123L), eq(456L));
    }

    @Test
    public void on_withPrebakedAndCallbackNotSupported_triggersCallbackFromHandler() {
        mHelper.setSupportedEffects(EFFECT_CLICK);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrebakedSegment prebaked = createPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_MEDIUM);

        assertThat(vibrator.on(3, 4, prebaked)).isEqualTo(HalVibratorHelper.EFFECT_DURATION);
        mTestLooper.moveTimeForward(HalVibratorHelper.EFFECT_DURATION);
        mTestLooper.dispatchAll();

        verify(mCallbacksMock).onVibrationStepComplete(eq(VIBRATOR_ID), eq(3L), eq(4L));
    }

    @Test
    public void on_withPrebakedNotSupported_fail() {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrebakedSegment prebaked = createPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_MEDIUM);

        assertThat(vibrator.on(1, 1, prebaked)).isEqualTo(0L);
    }

    @Test
    public void on_withComposed_performsEffect() {
        mHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrimitiveSegment[] primitives = new PrimitiveSegment[]{
                new PrimitiveSegment(PRIMITIVE_CLICK, 0.5f, 10),
                new PrimitiveSegment(PRIMITIVE_TICK, 0.7f, 100),
        };

        long expectedDuration = 2 * HalVibratorHelper.EFFECT_DURATION + 10 + 100;
        assertThat(vibrator.on(1, 1, primitives)).isEqualTo(expectedDuration);
        assertThat(vibrator.isVibrating()).isTrue();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(-1f);
        assertThat(mHelper.getEffectSegments()).containsExactlyElementsIn(primitives).inOrder();
    }

    @Test
    public void on_withComposedAndCallbackSupported_triggersCallbackFromHal() {
        mHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrimitiveSegment[] primitives = new PrimitiveSegment[]{
                new PrimitiveSegment(PRIMITIVE_CLICK, 0.5f, 10),
                new PrimitiveSegment(PRIMITIVE_TICK, 0.7f, 100),
        };

        long expectedDuration = 2 * HalVibratorHelper.EFFECT_DURATION + 10 + 100;
        assertThat(vibrator.on(5, 6, primitives)).isEqualTo(expectedDuration);
        mTestLooper.moveTimeForward(expectedDuration);
        mTestLooper.dispatchAll();

        verify(mCallbacksMock).onVibrationStepComplete(eq(VIBRATOR_ID), eq(5L), eq(6L));
    }

    @Test
    public void on_withComposedUnsupported_fail() {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrimitiveSegment[] primitives = new PrimitiveSegment[]{
                new PrimitiveSegment(PRIMITIVE_TICK, 0.5f, 10),
        };

        assertThat(vibrator.on(1, 1, primitives)).isEqualTo(0L);
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }

    @Test
    public void on_withComposedUnsupportedPrimitive_fail() {
        mHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrimitiveSegment[] primitives = new PrimitiveSegment[]{
                new PrimitiveSegment(PRIMITIVE_TICK, 0.5f, 10),
        };

        assertThat(vibrator.on(1, 1, primitives)).isEqualTo(0L);
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }

    @Test
    public void on_withComposedPwle_performsEffect() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        RampSegment[] ramps = new RampSegment[]{
                new RampSegment(/* startAmplitude= */ 0, /* endAmplitude= */ 1,
                        /* startFrequencyHz= */ 100, /* endFrequencyHz= */ 200, /* duration= */ 10)
        };

        assertThat(vibrator.on(1, 1, ramps)).isEqualTo(10L);
        assertThat(vibrator.isVibrating()).isTrue();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(-1f);
        assertThat(mHelper.getEffectSegments()).containsExactlyElementsIn(ramps).inOrder();
    }

    @Test
    public void on_withComposedPwleAndCallbackSupported_triggersCallbackFromHal() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        RampSegment[] ramps = new RampSegment[]{
                new RampSegment(/* startAmplitude= */ 0, /* endAmplitude= */ 1,
                        /* startFrequencyHz= */ 100, /* endFrequencyHz= */ 200, /* duration= */ 10)
        };

        assertThat(vibrator.on(7, 8, ramps)).isEqualTo(10L);
        mTestLooper.moveTimeForward(10);
        mTestLooper.dispatchAll();

        verify(mCallbacksMock).onVibrationStepComplete(eq(VIBRATOR_ID), eq(7L), eq(8L));
    }

    @Test
    public void on_withComposedPwleNotSupported_fail() {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        RampSegment[] ramps = new RampSegment[]{
                new RampSegment(/* startAmplitude= */ 0, /* endAmplitude= */ 1,
                        /* startFrequencyHz= */ 100, /* endFrequencyHz= */ 200, /* duration= */ 10)
        };

        assertThat(vibrator.on(1, 1, ramps)).isEqualTo(0L);
    }

    @Test
    public void on_withComposedPwleV2_performsEffect() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PwlePoint[] points = new PwlePoint[]{
                new PwlePoint(/*amplitude=*/ 0, /*frequencyHz=*/ 100, /*timeMillis=*/ 0),
                new PwlePoint(/*amplitude=*/ 1, /*frequencyHz=*/ 200, /*timeMillis=*/ 30)
        };

        assertThat(vibrator.on(1, 1, points)).isEqualTo(30L);
        assertThat(vibrator.isVibrating()).isTrue();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(-1f);
        assertThat(mHelper.getEffectPwlePoints()).containsExactlyElementsIn(points).inOrder();
    }

    @Test
    public void on_withComposedPwleV2AndCallbackSupported_triggersCallbackFromHal() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PwlePoint[] points = new PwlePoint[]{
                new PwlePoint(/*amplitude=*/ 0, /*frequencyHz=*/ 100, /*timeMillis=*/ 0),
                new PwlePoint(/*amplitude=*/ 1, /*frequencyHz=*/ 200, /*timeMillis=*/ 30)
        };

        assertThat(vibrator.on(1, 1, points)).isEqualTo(30L);
        mTestLooper.moveTimeForward(30);
        mTestLooper.dispatchAll();

        verify(mCallbacksMock).onVibrationStepComplete(eq(VIBRATOR_ID), eq(1L), eq(1L));
    }

    @Test
    public void on_withComposedPwleV2NotSupported_performsEffect() {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PwlePoint[] points = new PwlePoint[]{
                new PwlePoint(/*amplitude=*/ 0, /*frequencyHz=*/ 100, /*timeMillis=*/ 0),
                new PwlePoint(/*amplitude=*/ 1, /*frequencyHz=*/ 200, /*timeMillis=*/ 30)
        };

        assertThat(vibrator.on(1, 1, points)).isEqualTo(0L);
    }

    @Test
    public void off_turnsOffVibrator() {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        vibrator.on(1, 1, /* milliseconds= */ 100);
        assertThat(vibrator.isVibrating()).isTrue();

        vibrator.off();
        vibrator.off();
        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(mHelper.getOffCount()).isEqualTo(3); // one extra call from system ready.
    }

    @Test
    public void registerVibratorStateListener_callbacksAreTriggered() throws Exception {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        vibrator.registerVibratorStateListener(mVibratorStateListenerMock);
        vibrator.on(1, 1, /* milliseconds= */ 10);
        vibrator.on(2, 1, /* milliseconds= */ 100);
        vibrator.off();
        vibrator.off();

        InOrder inOrderVerifier = inOrder(mVibratorStateListenerMock);
        // First notification done when listener is registered.
        inOrderVerifier.verify(mVibratorStateListenerMock).onVibrating(false);
        inOrderVerifier.verify(mVibratorStateListenerMock).onVibrating(eq(true));
        inOrderVerifier.verify(mVibratorStateListenerMock).onVibrating(eq(false));
        inOrderVerifier.verifyNoMoreInteractions();
    }

    @Test
    public void unregisterVibratorStateListener_callbackNotTriggeredAfter() throws Exception {
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        vibrator.registerVibratorStateListener(mVibratorStateListenerMock);
        verify(mVibratorStateListenerMock).onVibrating(false);

        vibrator.on(1, 1, /* milliseconds= */ 10);
        verify(mVibratorStateListenerMock).onVibrating(true);

        vibrator.unregisterVibratorStateListener(mVibratorStateListenerMock);
        Mockito.clearInvocations(mVibratorStateListenerMock);

        vibrator.on(2, 1, /* milliseconds= */ 100);
        verifyNoMoreInteractions(mVibratorStateListenerMock);
    }

    PrebakedSegment createPrebaked(int effectId, int effectStrength) {
        return new PrebakedSegment(effectId, /* shouldFallback= */ false, effectStrength);
    }

    private StepSegment createStep(int millis) {
        return new StepSegment(VibrationEffect.DEFAULT_AMPLITUDE, 0f, millis);
    }

    private List<Integer> toSupportedList(SparseBooleanArray supportArray) {
        List<Integer> result = new ArrayList<>(supportArray.size());
        for (int i = 0; i < supportArray.size(); i++) {
            if (supportArray.valueAt(i)) {
                result.add(supportArray.keyAt(i));
            }
        }
        return result;
    }
}
