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

package com.android.server.vibrator;

import static android.os.VibrationEffect.Composition.DELAY_TYPE_PAUSE;
import static android.os.VibrationEffect.Composition.DELAY_TYPE_RELATIVE_START_OFFSET;
import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_FALL;
import static android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_RISE;
import static android.os.VibrationEffect.Composition.PRIMITIVE_SLOW_RISE;
import static android.os.VibrationEffect.Composition.PRIMITIVE_SPIN;
import static android.os.VibrationEffect.Composition.PRIMITIVE_THUD;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;
import static android.os.VibrationEffect.DEFAULT_AMPLITUDE;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.EFFECT_POP;
import static android.os.VibrationEffect.EFFECT_THUD;
import static android.os.VibrationEffect.EFFECT_TICK;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.hardware.vibrator.IVibrator;
import android.os.CombinedVibration;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.VibrationEffect;
import android.os.test.TestLooper;
import android.os.vibrator.BasicPwleSegment;
import android.os.vibrator.Flags;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwleSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationConfig;
import android.os.vibrator.VibrationEffectSegment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.SparseArray;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.LocalServices;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.List;

public class DeviceAdapterTest {
    private static final int EMPTY_VIBRATOR_ID = 1;
    private static final int PWLE_V2_VIBRATOR_ID = 4;
    private static final int PWLE_V2_BASIC_VIBRATOR_ID = 5;
    private static final int PWLE_V2_EMPTY_PROFILE_VIBRATOR_ID = 6;
    private static final int BASIC_VIBRATOR_ID = 7;
    private static final float TEST_MIN_FREQUENCY = 50;
    private static final float TEST_RESONANT_FREQUENCY = 150;
    private static final float TEST_FREQUENCY_RESOLUTION = 25;
    private static final float[] TEST_AMPLITUDE_MAP = new float[]{
            /* 50Hz= */ 0.08f, 0.16f, 0.32f, 0.64f, /* 150Hz= */ 0.8f, 0.72f, /* 200Hz= */ 0.64f};
    private static final int TEST_MAX_ENVELOPE_EFFECT_SIZE = 10;
    private static final int TEST_MIN_ENVELOPE_EFFECT_CONTROL_POINT_DURATION_MILLIS = 20;
    private static final int TEST_MAX_ENVELOPE_EFFECT_CONTROL_POINT_DURATION_MILLIS = 100;
    private static final float[] TEST_FREQUENCIES_HZ = new float[]{30f, 50f, 100f, 120f, 150f};
    private static final float[] TEST_OUTPUT_ACCELERATIONS_GS =
            new float[]{0.0f, 3.0f, 4.0f, 2.0f, 1.0f};

    private static final float[] TEST_BASIC_FREQUENCIES_HZ = new float[]{50f, 200f, 400f, 500f};
    private static final float[] TEST_BASIC_OUTPUT_ACCELERATIONS_GS =
            new float[]{0.05f, 0.5f, 2.0f, 1.0f};

    private static final float PWLE_V2_MIN_FREQUENCY = TEST_FREQUENCIES_HZ[0];
    private static final float PWLE_V2_MAX_FREQUENCY =
            TEST_FREQUENCIES_HZ[TEST_FREQUENCIES_HZ.length - 1];
    private static final int TEST_PRIMITIVE_DURATION = 20;

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private PackageManagerInternal mPackageManagerInternalMock;
    @Mock private HalVibrator.Callbacks mHalCallbacks;

    private final SparseArray<VibrationEffect> mFallbackEffects = new SparseArray<>();

    private TestLooper mTestLooper;
    private VibrationSettings mVibrationSettings;
    private DeviceAdapter mAdapter;

    @Before
    public void setUp() throws Exception {
        when(mPackageManagerInternalMock.getSystemUiServiceComponent())
                .thenReturn(new ComponentName("", ""));
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternalMock);

        Context context = ApplicationProvider.getApplicationContext();
        mTestLooper = new TestLooper();
        mVibrationSettings = new VibrationSettings(context, new Handler(mTestLooper.getLooper()),
                new VibrationConfig(context.getResources()), mFallbackEffects);

        SparseArray<HalVibrator> vibrators = new SparseArray<>();
        vibrators.put(EMPTY_VIBRATOR_ID, createEmptyVibrator(EMPTY_VIBRATOR_ID));
        vibrators.put(PWLE_V2_VIBRATOR_ID, createPwleV2Vibrator(PWLE_V2_VIBRATOR_ID));
        vibrators.put(PWLE_V2_BASIC_VIBRATOR_ID,
                createPwleV2Vibrator(PWLE_V2_VIBRATOR_ID, TEST_BASIC_FREQUENCIES_HZ,
                        TEST_BASIC_OUTPUT_ACCELERATIONS_GS));
        vibrators.put(PWLE_V2_EMPTY_PROFILE_VIBRATOR_ID,
                createPwleV2VibratorWithEmptyProfile(PWLE_V2_EMPTY_PROFILE_VIBRATOR_ID));
        vibrators.put(BASIC_VIBRATOR_ID, createBasicVibrator(BASIC_VIBRATOR_ID));
        for (int i = 0; i < vibrators.size(); i++) {
            vibrators.valueAt(i).init((vibratorId, vibrationId, stepId)  -> {});
        }
        mAdapter = new DeviceAdapter(mVibrationSettings, vibrators);
    }

    @Test
    public void testPrebakedAndPrimitiveSegments_supportedEffects_returnsOriginalSegment() {
        // Fallback ignored, effect supported.
        mFallbackEffects.put(EFFECT_TICK, VibrationEffect.createOneShot(10, 100));
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, false, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrimitiveSegment(PRIMITIVE_TICK, 1, 10),
                new PrebakedSegment(EFFECT_TICK, true, VibrationEffect.EFFECT_STRENGTH_STRONG),
                new PrimitiveSegment(PRIMITIVE_SPIN, 0.5f, 100)),
                /* repeatIndex= */ -1);

        assertThat(mAdapter.adaptToVibrator(BASIC_VIBRATOR_ID, effect)).isEqualTo(effect);
    }

    @Test
    public void testVendorEffect_returnsOriginalSegment() {
        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putInt("key", 1);
        VibrationEffect effect = VibrationEffect.createVendorEffect(vendorData);

        assertThat(mAdapter.adaptToVibrator(EMPTY_VIBRATOR_ID, effect)).isEqualTo(effect);
        assertThat(mAdapter.adaptToVibrator(PWLE_V2_VIBRATOR_ID, effect)).isEqualTo(effect);
    }

    @Test
    public void testMonoCombinedVibration_returnsSameVibrationWhenEffectsUnchanged() {
        // Only on-off effects are supported by all vibrators.
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new StepSegment(1, 10),
                new StepSegment(1, 100)),
                /* repeatIndex= */ -1);

        CombinedVibration expected = CombinedVibration.createParallel(effect);

        assertThat(expected.adapt(mAdapter)).isEqualTo(expected);
    }

    @Test
    public void testStereoCombinedVibration_adaptMappedEffectsAndLeaveUnmappedOnesUnchanged() {
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                // Step(amplitude, duration)
                new StepSegment(1, 10)),
                /* repeatIndex= */ -1);

        int missingVibratorId = 1234;
        CombinedVibration vibration = CombinedVibration.startParallel()
                .addVibrator(missingVibratorId, effect)
                .addVibrator(EMPTY_VIBRATOR_ID, effect)
                .addVibrator(PWLE_V2_VIBRATOR_ID, effect)
                .combine();

        CombinedVibration expected = CombinedVibration.startParallel()
                .addVibrator(missingVibratorId, effect) // unchanged
                .addVibrator(EMPTY_VIBRATOR_ID, new VibrationEffect.Composed(Arrays.asList(
                        // Step(amplitude, duration)
                        new StepSegment(1, 10)),
                        /* repeatIndex= */ -1))
                .addVibrator(PWLE_V2_VIBRATOR_ID, new VibrationEffect.Composed(Arrays.asList(
                        // Step(amplitude, duration)
                        new StepSegment(1, 10)),
                        /* repeatIndex= */ -1))
                .combine();

        assertThat(vibration.adapt(mAdapter)).isEqualTo(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testPwleSegment_withoutPwleV2Capability_returnsNull() {
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new PrimitiveSegment(PRIMITIVE_SPIN, 0.5f, 100),
                new PwleSegment(1, 0.2f, 30, 60, 20),
                new PwleSegment(0.8f, 0.2f, 60, 100, 100),
                new PwleSegment(0.65f, 0.65f, 100, 50, 50)),
                /* repeatIndex= */ 1);

        VibrationEffect.Composed adaptedEffect =
                (VibrationEffect.Composed) mAdapter.adaptToVibrator(BASIC_VIBRATOR_ID, effect);
        assertThat(adaptedEffect).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testPwleSegment_withPwleV2Capability_returnsAdaptedSegments() {
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new PwleSegment(1, 0.2f, 30, 60, 20),
                new PwleSegment(0.8f, 0.2f, 60, 100, 100),
                new PwleSegment(0.65f, 0.65f, 100, 50, 50)),
                /* repeatIndex= */ 1);

        VibrationEffect.Composed expected = new VibrationEffect.Composed(Arrays.asList(
                new PwleSegment(1, 0.2f, 30, 60, 20),
                new PwleSegment(0.8f, 0.2f, 60, 100, 100),
                new PwleSegment(0.65f, 0.65f, 100, 50, 50)),
                /* repeatIndex= */ 1);

        assertThat(mAdapter.adaptToVibrator(PWLE_V2_VIBRATOR_ID, effect)).isEqualTo(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testPwleSegment_withFrequenciesBelowSupportedRange_returnsNull() {
        float frequencyBelowSupportedRange = PWLE_V2_MIN_FREQUENCY - 1f;
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new PwleSegment(0, 0.2f, 30, 60, 20),
                new PwleSegment(0.8f, 0.2f, 60, frequencyBelowSupportedRange, 100),
                new PwleSegment(0.65f, 0.65f, frequencyBelowSupportedRange, 50, 50)),
                /* repeatIndex= */ 1);

        assertThat(mAdapter.adaptToVibrator(PWLE_V2_VIBRATOR_ID, effect)).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testPwleSegment_withFrequenciesAboveSupportedRange_returnsNull() {
        float frequencyAboveSupportedRange = PWLE_V2_MAX_FREQUENCY + 1f;
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new PwleSegment(0, 0.2f, 30, frequencyAboveSupportedRange, 20),
                new PwleSegment(0.8f, 0.2f, frequencyAboveSupportedRange, 100, 100),
                new PwleSegment(0.65f, 0.65f, 100, 50, 50)),
                /* repeatIndex= */ 1);

        assertThat(mAdapter.adaptToVibrator(PWLE_V2_VIBRATOR_ID, effect)).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testPwleSegment_withEmptyProfile_returnsNull() {
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new PwleSegment(1, 0.2f, 30, 60, 20),
                new PwleSegment(0.8f, 0.2f, 60, 100, 100),
                new PwleSegment(0.65f, 0.65f, 100, 50, 50)),
                /* repeatIndex= */ 1);

        assertThat(mAdapter.adaptToVibrator(PWLE_V2_EMPTY_PROFILE_VIBRATOR_ID, effect)).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegment_withoutPwleV2Capability_returnsNull() {
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new PrimitiveSegment(PRIMITIVE_SPIN, 0.5f, 100),
                new BasicPwleSegment(0.2f, 0.8f, 0.2f, 0.4f, 20),
                new BasicPwleSegment(0.8f, 0.2f, 0.4f, 0.5f, 100),
                new BasicPwleSegment(0.2f, 0.65f, 0.5f, 0.5f, 50)),
                /* repeatIndex= */ 1);

        VibrationEffect.Composed adaptedEffect =
                (VibrationEffect.Composed) mAdapter.adaptToVibrator(EMPTY_VIBRATOR_ID, effect);
        assertThat(adaptedEffect).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegment_withPwleV2Capability_returnsAdaptedSegments() {
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new BasicPwleSegment(0.0f, 0.5f, 0.0f, 0.5f, 20),
                new BasicPwleSegment(0.5f, 1.0f, 0.5f, 1.0f, 100),
                new BasicPwleSegment(1.0f, 0.0f, 1.0f, 0.5f, 100)),
                /* repeatIndex= */ 1);

        VibrationEffect.Composed expected = new VibrationEffect.Composed(Arrays.asList(
                new PwleSegment(0.0f, 0.41745436f, 63.52442f, 125.292694f, 20),
                new PwleSegment(0.41745436f, 1.0f, 125.292694f, 500f, 100),
                new PwleSegment(1.0f, 0.0f, 500, 125.292694f, 100)),
                /* repeatIndex= */ 1);

        assertThat(mAdapter.adaptToVibrator(PWLE_V2_BASIC_VIBRATOR_ID, effect)).isEqualTo(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegment_withEmptyProfile_returnsNull() {
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new BasicPwleSegment(0.0f, 0.5f, 0.0f, 0.5f, 20),
                new BasicPwleSegment(0.5f, 1.0f, 0.5f, 1.0f, 100),
                new BasicPwleSegment(1.0f, 0.0f, 1.0f, 0.5f, 100)),
                /* repeatIndex= */ 1);

        assertThat(mAdapter.adaptToVibrator(PWLE_V2_EMPTY_PROFILE_VIBRATOR_ID, effect)).isNull();
    }

    @Test
    public void testUnsupportedPrimitives_returnsNull() {
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                new PrimitiveSegment(PRIMITIVE_TICK, 1, 10),
                new PrimitiveSegment(PRIMITIVE_TICK, 0.5f, 10),
                new PrimitiveSegment(PRIMITIVE_CLICK, 1, 100)),
                /* repeatIndex= */ -1);

        assertThat(mAdapter.adaptToVibrator(EMPTY_VIBRATOR_ID, effect)).isNull();
    }

    @Test
    public void testPrimitiveWithRelativeDelay_returnsPrimitiveWithPauseDelays() {
        int expectedPause = 50;
        int relativeDelay = 50 + TEST_PRIMITIVE_DURATION - 1;
        VibrationEffect.Composed effect = new VibrationEffect.Composed(Arrays.asList(
                // Originally requested (overlapping):
                // tick @ 10ms / tick @ 11ms / click @ 69ms + 20ms pause + click
                // Actually played:
                // 10ms pause + tick + 50ms pause + click + 20ms pause + click
                new PrimitiveSegment(PRIMITIVE_TICK, 1, 10, DELAY_TYPE_RELATIVE_START_OFFSET),
                new PrimitiveSegment(PRIMITIVE_TICK, 0.5f, 1, DELAY_TYPE_RELATIVE_START_OFFSET),
                new PrimitiveSegment(PRIMITIVE_CLICK, 1, relativeDelay,
                        DELAY_TYPE_RELATIVE_START_OFFSET),
                new PrimitiveSegment(PRIMITIVE_CLICK, 0.5f, 20, DELAY_TYPE_PAUSE)),
                /* repeatIndex= */ -1);

        // Delay based on primitive duration
        VibrationEffect.Composed expected = new VibrationEffect.Composed(Arrays.asList(
                new PrimitiveSegment(PRIMITIVE_TICK, 1, 10, DELAY_TYPE_PAUSE),
                new PrimitiveSegment(PRIMITIVE_CLICK, 1, expectedPause, DELAY_TYPE_PAUSE),
                new PrimitiveSegment(PRIMITIVE_CLICK, 0.5f, 20, DELAY_TYPE_PAUSE)),
                /* repeatIndex= */ -1);

        assertThat(mAdapter.adaptToVibrator(EMPTY_VIBRATOR_ID, effect)).isNull();
        assertThat(mAdapter.adaptToVibrator(BASIC_VIBRATOR_ID, effect)).isEqualTo(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedUnsupported_withFallback_replacesUnsupportedPrebaked() {
        mFallbackEffects.put(EFFECT_THUD, VibrationEffect.createWaveform(
                new long[] { 10, 10, 10, 10 }, -1));
        VibrationEffect.Composed effect1 = new VibrationEffect.Composed(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_STRONG),
                new PrebakedSegment(EFFECT_TICK, true, VibrationEffect.EFFECT_STRENGTH_STRONG)),
                /* repeatIndex= */ 2);
        VibrationEffect.Composed adaptedEffect1 = new VibrationEffect.Composed(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new StepSegment(0, 10),
                new StepSegment(DEFAULT_AMPLITUDE, 10),
                new StepSegment(0, 10),
                new StepSegment(DEFAULT_AMPLITUDE, 10),
                new PrebakedSegment(EFFECT_TICK, true, VibrationEffect.EFFECT_STRENGTH_STRONG)),
                /* repeatIndex= */ 5);

        VibrationEffect.Composed effect2 = new VibrationEffect.Composed(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_STRONG)),
                /* repeatIndex= */ 1);
        VibrationEffect.Composed adaptedEffect2 = new VibrationEffect.Composed(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new StepSegment(0, 10),
                new StepSegment(DEFAULT_AMPLITUDE, 10),
                new StepSegment(0, 10),
                new StepSegment(DEFAULT_AMPLITUDE, 10)),
                /* repeatIndex= */ 1);

        assertThat(mAdapter.adaptToVibrator(BASIC_VIBRATOR_ID, effect1)).isEqualTo(adaptedEffect1);
        assertThat(mAdapter.adaptToVibrator(BASIC_VIBRATOR_ID, effect2)).isEqualTo(adaptedEffect2);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void testPrebakedUnsupported_withoutFallback_returnsNull() {
        mFallbackEffects.put(EFFECT_THUD, VibrationEffect.createWaveform(
                new long[] { 10, 10, 10, 10 }, -1));
        VibrationEffect.Composed effect1 = new VibrationEffect.Composed(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_STRONG),
                new PrebakedSegment(EFFECT_POP, true, VibrationEffect.EFFECT_STRENGTH_STRONG)),
                /* repeatIndex= */ 2);
        VibrationEffect.Composed effect2 = new VibrationEffect.Composed(Arrays.asList(
                new PrebakedSegment(EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_LIGHT),
                new PrebakedSegment(EFFECT_THUD, false, VibrationEffect.EFFECT_STRENGTH_STRONG)),
                /* repeatIndex= */ 1);

        assertThat(mAdapter.adaptToVibrator(BASIC_VIBRATOR_ID, effect1)).isNull();
        assertThat(mAdapter.adaptToVibrator(BASIC_VIBRATOR_ID, effect2)).isNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_REMOVE_HIDL_SUPPORT, Flags.FLAG_NORMALIZED_PWLE_EFFECTS})
    public void testAdaptToVibrator_propagatesStartTimeThroughMultipleAdapters() {
        // Config to enable RampDownAdapter
        Context context = ApplicationProvider.getApplicationContext();
        VibrationConfig.Builder configBuilder = new VibrationConfig.Builder(context.getResources());
        configBuilder.setRampDownDurationMs(20);
        VibrationSettings settings = new VibrationSettings(context,
                new Handler(mTestLooper.getLooper()),
                configBuilder.build(), mFallbackEffects);
        SparseArray<HalVibrator> vibrators = new SparseArray<>();
        vibrators.put(BASIC_VIBRATOR_ID, createBasicVibrator(BASIC_VIBRATOR_ID));
        DeviceAdapter adapter = new DeviceAdapter(settings, vibrators);

        // This test case exercises:
        // 1. PrebakedFallbackAdapter: EFFECT_THUD -> Waveform (StepSegment)
        // 2. RampDownAdapter: StepSegment (amplitude 0) -> Ramp down Steps
        mFallbackEffects.put(EFFECT_THUD, VibrationEffect.createWaveform(
                new long[] { 10 }, -1));
        long startTime = 1234L;
        VibrationEffect effect = new VibrationEffect.Composed(Arrays.asList(
                new StepSegment(1f, 10),
                new PrebakedSegment(EFFECT_THUD, true, VibrationEffect.EFFECT_STRENGTH_MEDIUM,
                        startTime)),
                -1);

        VibrationEffect adapted = adapter.adaptToVibrator(BASIC_VIBRATOR_ID, effect);

        assertThat(adapted).isInstanceOf(VibrationEffect.Composed.class);
        List<VibrationEffectSegment> segments = ((VibrationEffect.Composed) adapted).getSegments();

        // 1f/10ms step -> ramp down steps (1 step of 0.5f/5ms + 1 step of 0f/5ms)
        // The replaced THUD (Step(0f, 10ms)) is consumed by the ramp down.
        // So we have:
        // [0] Step(1f, 10ms), start -1
        // [1] Step(0.5f, 5ms), start startTime (propagated)
        // [2] Step(0f, 5ms), start -1
        assertThat(segments.size()).isEqualTo(3);
        assertThat(segments.get(1).getStartTimeMillis()).isEqualTo(startTime);
    }

    private HalVibrator createEmptyVibrator(int vibratorId) {
        return new HalVibratorHelper(mTestLooper.getLooper())
                .newInitializedHalVibrator(vibratorId, mHalCallbacks);
    }

    private HalVibrator createBasicVibrator(int vibratorId) {
        return createVibratorHelperWithEffects(IVibrator.CAP_COMPOSE_EFFECTS)
                .newInitializedHalVibrator(vibratorId, mHalCallbacks);
    }

    private HalVibrator createPwleWithoutFrequenciesVibrator(int vibratorId) {
        HalVibratorHelper helper = createVibratorHelperWithEffects(
                IVibrator.CAP_GET_RESONANT_FREQUENCY, IVibrator.CAP_FREQUENCY_CONTROL,
                IVibrator.CAP_COMPOSE_EFFECTS, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        return helper.newInitializedHalVibrator(vibratorId, mHalCallbacks);
    }

    private HalVibrator createPwleVibrator(int vibratorId) {
        HalVibratorHelper helper = createVibratorHelperWithEffects(
                IVibrator.CAP_GET_RESONANT_FREQUENCY, IVibrator.CAP_FREQUENCY_CONTROL,
                IVibrator.CAP_COMPOSE_EFFECTS, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        helper.setResonantFrequency(TEST_RESONANT_FREQUENCY);
        helper.setMinFrequency(TEST_MIN_FREQUENCY);
        helper.setFrequencyResolution(TEST_FREQUENCY_RESOLUTION);
        helper.setMaxAmplitudes(TEST_AMPLITUDE_MAP);
        return helper.newInitializedHalVibrator(vibratorId, mHalCallbacks);
    }

    private HalVibrator createPwleV2Vibrator(int vibratorId) {
        return createPwleV2Vibrator(vibratorId, TEST_FREQUENCIES_HZ, TEST_OUTPUT_ACCELERATIONS_GS);
    }

    private HalVibrator createPwleV2Vibrator(int vibratorId, float[] frequencies,
            float[] accelerations) {
        HalVibratorHelper helper = createVibratorHelperWithEffects(
                IVibrator.CAP_GET_RESONANT_FREQUENCY, IVibrator.CAP_FREQUENCY_CONTROL,
                IVibrator.CAP_COMPOSE_EFFECTS, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        helper.setResonantFrequency(TEST_RESONANT_FREQUENCY);
        helper.setFrequenciesHz(frequencies);
        helper.setOutputAccelerationsGs(accelerations);
        helper.setMaxEnvelopeEffectSize(TEST_MAX_ENVELOPE_EFFECT_SIZE);
        helper.setMinEnvelopeEffectControlPointDurationMillis(
                TEST_MIN_ENVELOPE_EFFECT_CONTROL_POINT_DURATION_MILLIS);
        helper.setMaxEnvelopeEffectControlPointDurationMillis(
                TEST_MAX_ENVELOPE_EFFECT_CONTROL_POINT_DURATION_MILLIS);
        return helper.newInitializedHalVibrator(vibratorId, mHalCallbacks);
    }

    private HalVibrator createPwleV2VibratorWithEmptyProfile(int vibratorId) {
        HalVibratorHelper helper = createVibratorHelperWithEffects(
                IVibrator.CAP_GET_RESONANT_FREQUENCY, IVibrator.CAP_FREQUENCY_CONTROL,
                IVibrator.CAP_COMPOSE_EFFECTS, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        helper.setFrequenciesHz(null);
        helper.setOutputAccelerationsGs(null);
        return helper.newInitializedHalVibrator(vibratorId, mHalCallbacks);
    }

    private HalVibratorHelper createVibratorHelperWithEffects(int... capabilities) {
        HalVibratorHelper helper = new HalVibratorHelper(mTestLooper.getLooper());
        helper.setCapabilities(capabilities);
        helper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK, PRIMITIVE_THUD,
                PRIMITIVE_SPIN, PRIMITIVE_QUICK_RISE, PRIMITIVE_QUICK_FALL, PRIMITIVE_SLOW_RISE);
        helper.setSupportedEffects(EFFECT_CLICK, EFFECT_TICK);
        helper.setPrimitiveDuration(TEST_PRIMITIVE_DURATION);
        return helper;
    }
}
