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

package com.android.server.vibrator;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.vibrator.IVibrator;
import android.os.VibratorInfo;
import android.os.vibrator.BasicPwleSegment;
import android.os.vibrator.Flags;
import android.os.vibrator.PwleSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class BasicToPwleSegmentAdapterTest {

    private static final float TEST_RESONANT_FREQUENCY = 150;
    private static final float[] TEST_FREQUENCIES =
            new float[]{90f, 120f, 150f, 60f, 30f, 210f, 270f, 300f, 240f, 180f};
    private static final float[] TEST_OUTPUT_ACCELERATIONS =
            new float[]{1.2f, 1.8f, 2.4f, 0.6f, 0.1f, 2.2f, 1.0f, 0.5f, 1.9f, 3.0f};

    private static final VibratorInfo.FrequencyProfile TEST_FREQUENCY_PROFILE =
            new VibratorInfo.FrequencyProfile(TEST_RESONANT_FREQUENCY, TEST_FREQUENCIES,
                    TEST_OUTPUT_ACCELERATIONS);

    private static final VibratorInfo.FrequencyProfile EMPTY_FREQUENCY_PROFILE =
            new VibratorInfo.FrequencyProfile(TEST_RESONANT_FREQUENCY, null, null);

    private BasicToPwleSegmentAdapter mAdapter;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() throws Exception {
        mAdapter = new BasicToPwleSegmentAdapter();
    }

    @Test
    @DisableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegments_withFeatureFlagDisabled_returnsOriginalSegments() {
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                //  startIntensity, endIntensity, startSharpness, endSharpness, duration
                new BasicPwleSegment(0.2f, 0.8f, 0.2f, 0.4f, 20),
                new BasicPwleSegment(0.8f, 0.2f, 0.4f, 0.5f, 100),
                new BasicPwleSegment(0.2f, 0.65f, 0.5f, 0.5f, 50)));
        List<VibrationEffectSegment> originalSegments = new ArrayList<>(segments);

        VibratorInfo vibratorInfo = createVibratorInfo(
                TEST_FREQUENCY_PROFILE, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);

        assertThat(mAdapter.adaptToVibrator(vibratorInfo, segments, /*repeatIndex= */ -1))
                .isEqualTo(-1);
        assertThat(mAdapter.adaptToVibrator(vibratorInfo, segments, /*repeatIndex= */ 1))
                .isEqualTo(1);

        assertThat(segments).isEqualTo(originalSegments);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegments_noPwleCapability_returnsOriginalSegments() {
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                //  startIntensity, endIntensity, startSharpness, endSharpness, duration
                new BasicPwleSegment(0.2f, 0.8f, 0.2f, 0.4f, 20),
                new BasicPwleSegment(0.8f, 0.2f, 0.4f, 0.5f, 100),
                new BasicPwleSegment(0.2f, 0.65f, 0.5f, 0.5f, 50)));
        List<VibrationEffectSegment> originalSegments = new ArrayList<>(segments);

        VibratorInfo vibratorInfo = createVibratorInfo(TEST_FREQUENCY_PROFILE);

        assertThat(mAdapter.adaptToVibrator(vibratorInfo, segments, /*repeatIndex= */ -1))
                .isEqualTo(-1);
        assertThat(mAdapter.adaptToVibrator(vibratorInfo, segments, /*repeatIndex= */ 1))
                .isEqualTo(1);

        assertThat(segments).isEqualTo(originalSegments);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegments_invalidFrequencyProfile_returnsOriginalSegments() {
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                //  startIntensity, endIntensity, startSharpness, endSharpness, duration
                new BasicPwleSegment(0.2f, 0.8f, 0.2f, 0.4f, 20),
                new BasicPwleSegment(0.8f, 0.2f, 0.4f, 0.5f, 100),
                new BasicPwleSegment(0.2f, 0.65f, 0.5f, 0.5f, 50)));
        List<VibrationEffectSegment> originalSegments = new ArrayList<>(segments);
        VibratorInfo vibratorInfo = createVibratorInfo(
                EMPTY_FREQUENCY_PROFILE, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);

        assertThat(mAdapter.adaptToVibrator(vibratorInfo, segments, /*repeatIndex= */ -1))
                .isEqualTo(-1);
        assertThat(mAdapter.adaptToVibrator(vibratorInfo, segments, /*repeatIndex= */ 1))
                .isEqualTo(1);

        assertThat(segments).isEqualTo(originalSegments);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegments_withPwleCapability_adaptSegmentsCorrectly() {
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new StepSegment(/* amplitude= */ 1, /* duration= */ 100),
                //  startIntensity, endIntensity, startSharpness, endSharpness, duration
                new BasicPwleSegment(0.0f, 1.0f, 0.0f, 1.0f, 100),
                new BasicPwleSegment(0.0f, 1.0f, 0.0f, 1.0f, 100),
                new BasicPwleSegment(0.0f, 1.0f, 0.0f, 1.0f, 100)));
        List<VibrationEffectSegment> expectedSegments = Arrays.asList(
                new StepSegment(/* amplitude= */ 1, /* duration= */ 100),
                //  startAmplitude, endAmplitude, startFrequencyHz, endFrequencyHz, duration
                new PwleSegment(0.0f, 1.0f, 30.0f, 300.0f, 100),
                new PwleSegment(0.0f, 1.0f, 30.0f, 300.0f, 100),
                new PwleSegment(0.0f, 1.0f, 30.0f, 300.0f, 100));
        VibratorInfo vibratorInfo = createVibratorInfo(
                TEST_FREQUENCY_PROFILE, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);

        assertThat(mAdapter.adaptToVibrator(vibratorInfo, segments, /*repeatIndex= */ 1))
                .isEqualTo(1);

        assertThat(segments).isEqualTo(expectedSegments);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegments_propagatesStartTime() {
        long startTime = 1234L;
        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new BasicPwleSegment(0.0f, 1.0f, 0.0f, 1.0f, 100, startTime)));
        VibratorInfo vibratorInfo = createVibratorInfo(
                TEST_FREQUENCY_PROFILE, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);

        mAdapter.adaptToVibrator(vibratorInfo, segments, -1);

        assertThat(segments.get(0).getStartTimeMillis()).isEqualTo(startTime);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegments_identifiesLastIntersectionAsMaxFrequency() {
        // Frequencies: 50, 100, 150, 200, 250, 300
        // Accel:       0.01, 1.0, 0.01, 1.0, 1.0, 0.01
        // Threshold is roughly 0.08G-0.14G.
        // The curve exceeds the threshold at [100, 200, 250].
        // It drops below at 150 and 300.
        // The last time it exceeds is around 250-300.
        float[] frequencies = new float[]{50f, 100f, 150f, 200f, 250f, 300f};
        float[] accelerations = new float[]{0.01f, 1.0f, 0.01f, 1.0f, 1.0f, 0.01f};
        VibratorInfo.FrequencyProfile profile = new VibratorInfo.FrequencyProfile(
                150f, frequencies, accelerations);
        VibratorInfo info = createVibratorInfo(profile, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);

        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new BasicPwleSegment(0.0f, 1.0f, 0.0f, 1.0f, 100)));

        mAdapter.adaptToVibrator(info, segments, -1);

        assertThat(segments.get(0)).isInstanceOf(PwleSegment.class);
        PwleSegment adapted = (PwleSegment) segments.get(0);

        // Min frequency should be between 50 and 100.
        assertThat(adapted.getStartFrequencyHz()).isGreaterThan(50f);
        assertThat(adapted.getStartFrequencyHz()).isLessThan(100f);

        // Max frequency should be between 250 and 300.
        // If it used the 2nd intersection (the first drop-off), it would be between 100 and 150.
        assertThat(adapted.getEndFrequencyHz()).isGreaterThan(250f);
        assertThat(adapted.getEndFrequencyHz()).isLessThan(300f);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void
            testBasicPwleSegments_whenAlwaysAboveThreshold_identifiesMaxFrequencyAsMaxAvailable() {
        // Frequencies: 50, 100, 150, 200
        // Accel:       1.0, 1.0, 1.0, 1.0
        float[] frequencies = new float[]{50f, 100f, 150f, 200f};
        float[] accelerations = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        VibratorInfo.FrequencyProfile profile = new VibratorInfo.FrequencyProfile(
                150f, frequencies, accelerations);
        VibratorInfo info = createVibratorInfo(profile, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);

        List<VibrationEffectSegment> segments = new ArrayList<>(Arrays.asList(
                new BasicPwleSegment(0.0f, 1.0f, 0.0f, 1.0f, 100)));

        mAdapter.adaptToVibrator(info, segments, -1);

        assertThat(segments.get(0)).isInstanceOf(PwleSegment.class);
        PwleSegment adapted = (PwleSegment) segments.get(0);

        assertThat(adapted.getStartFrequencyHz()).isEqualTo(50f);
        assertThat(adapted.getEndFrequencyHz()).isEqualTo(200f);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testBasicPwleSegments_withSharpnessF0_mapsToResonantFrequency() {
        // Frequencies: 100, 150, 200
        // Resonant: 150
        float[] frequencies = new float[]{100f, 150f, 200f};
        float[] accelerations = new float[]{1.0f, 1.0f, 1.0f};
        VibratorInfo.FrequencyProfile profile = new VibratorInfo.FrequencyProfile(
                150f, frequencies, accelerations);
        VibratorInfo info = createVibratorInfo(profile, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);

        List<VibrationEffectSegment> segments =
                new ArrayList<>(
                        Arrays.asList(
                                // Sharpness 0.7 should map to resonant frequency 150Hz
                                // Sharpness 0.35 (half of 0.7) should map to 125Hz (halfway between
                                // 100 and 150)
                                // Sharpness 0.85 (halfway between 0.7 and 1.0) should map to 175Hz
                                // (halfway between 150 and 200)
                                new BasicPwleSegment(0.0f, 1.0f, 0.7f, 0.35f, 100),
                                new BasicPwleSegment(0.0f, 1.0f, 0.85f, 1.0f, 100)));

        mAdapter.adaptToVibrator(info, segments, -1);

        assertThat(segments.get(0)).isInstanceOf(PwleSegment.class);
        PwleSegment adapted0 = (PwleSegment) segments.get(0);
        assertThat(adapted0.getStartFrequencyHz()).isEqualTo(150f);
        assertThat(adapted0.getEndFrequencyHz()).isEqualTo(125f);

        assertThat(segments.get(1)).isInstanceOf(PwleSegment.class);
        PwleSegment adapted1 = (PwleSegment) segments.get(1);
        assertThat(adapted1.getStartFrequencyHz()).isEqualTo(175f);
        assertThat(adapted1.getEndFrequencyHz()).isEqualTo(200f);
    }

    private static VibratorInfo createVibratorInfo(VibratorInfo.FrequencyProfile frequencyProfile,
            int... capabilities) {
        return new VibratorInfo.Builder(0)
                .setCapabilities(IntStream.of(capabilities).reduce((a, b) -> a | b).orElse(0))
                .setFrequencyProfile(frequencyProfile)
                .build();
    }
}
