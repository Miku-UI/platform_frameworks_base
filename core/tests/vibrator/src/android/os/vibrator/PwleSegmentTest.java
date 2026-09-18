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
package android.os.vibrator;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;

import static org.testng.Assert.assertThrows;

import android.hardware.vibrator.IVibrator;
import android.os.Parcel;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PwleSegmentTest {
    private static final float TEST_RESONANT_FREQUENCY = 150;
    private static final float[] TEST_FREQUENCIES = new float[]{50, 100, 150, 200};
    private static final float[] TEST_AMPLITUDES = new float[]{0.1f, 0.5f, 1f, 0.8f};
    private static final VibratorInfo.FrequencyProfile TEST_FREQUENCY_PROFILE =
            new VibratorInfo.FrequencyProfile(
                    TEST_RESONANT_FREQUENCY, TEST_FREQUENCIES, TEST_AMPLITUDES);
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testCreation() {
        PwleSegment segment = new PwleSegment(0.1f, 0.2f, 50f, 100f, 10);
        assertThat(segment.getStartAmplitude()).isEqualTo(0.1f);
        assertThat(segment.getEndAmplitude()).isEqualTo(0.2f);
        assertThat(segment.getStartFrequencyHz()).isEqualTo(50f);
        assertThat(segment.getEndFrequencyHz()).isEqualTo(100f);
        assertThat(segment.getDuration()).isEqualTo(10);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testValidate() {
        new PwleSegment(0, 1, 1, 200, 1).validate();
        assertThrows(
                IllegalArgumentException.class,
                () -> new PwleSegment(-0.1f, 1, 50, 100, 10).validate());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PwleSegment(0, 1.1f, 50, 100, 10).validate());
        assertThrows(
                IllegalArgumentException.class, () -> new PwleSegment(0, 1, 0, 100, 10).validate());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PwleSegment(0, 1, 50, -10, 10).validate());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PwleSegment(0, 1, 50, 100, -1).validate());
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testGetDuration() {
        PwleSegment segment = new PwleSegment(0, 1, 50, 100, 10);
        assertThat(segment.getDuration()).isEqualTo(10);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testSerialization() {
        PwleSegment original = new PwleSegment(0.1f, 0.9f, 60f, 180f, 20);
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        // Creator requires the parcel token to be read first.
        assertThat(parcel.readInt()).isEqualTo(VibrationEffectSegment.PARCEL_TOKEN_PWLE);
        parcel.setDataPosition(0);
        assertEquals(original, PwleSegment.CREATOR.createFromParcel(parcel));
        parcel.setDataPosition(0);
        assertEquals(original, VibrationEffectSegment.CREATOR.createFromParcel(parcel));
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testAreVibrationFeaturesSupported() {
        VibratorInfo infoWithPwle =
                new VibratorInfo.Builder(0)
                        .setCapabilities(
                                IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2
                                        | IVibrator.CAP_FREQUENCY_CONTROL
                                        | IVibrator.CAP_GET_RESONANT_FREQUENCY)
                        .setFrequencyProfile(TEST_FREQUENCY_PROFILE)
                        .build();
        VibratorInfo infoWithoutPwle = new VibratorInfo.Builder(0).build();
        PwleSegment segment = new PwleSegment(0, 1, 50, 200, 10);
        assertThat(segment.areVibrationFeaturesSupported(infoWithPwle)).isTrue();
        assertThat(segment.areVibrationFeaturesSupported(infoWithoutPwle)).isFalse();
        // Frequency out of range.
        PwleSegment segmentHighFreq = new PwleSegment(0, 1, 50, 250, 10);
        assertThat(segmentHighFreq.areVibrationFeaturesSupported(infoWithPwle)).isFalse();
    }

    @Test
    @EnableFlags({Flags.FLAG_NORMALIZED_PWLE_EFFECTS})
    public void testScale() {
        PwleSegment segment = new PwleSegment(0.2f, 0.8f, 50, 150, 20);
        assertThat(segment.scale(0.5f)).isEqualTo(new PwleSegment(0.1f, 0.4f, 50, 150, 20));
        assertThat(segment.applyAdaptiveScale(1f)).isSameInstanceAs(segment);
        assertThat(segment.applyAdaptiveScale(0.5f))
                .isEqualTo(new PwleSegment(0.1f, 0.4f, 50, 150, 20));
        assertThat(segment.applyAdaptiveScale(1.5f)) // Should constrain to 1.0
                .isEqualTo(new PwleSegment(0.3f, 1f, 50, 150, 20));
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testResolve() {
        PwleSegment segment = new PwleSegment(0.1f, 0.2f, 50f, 100f, 10);
        assertThat(segment.resolve(100)).isSameInstanceAs(segment);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testApplyEffectStrength() {
        PwleSegment segment = new PwleSegment(0.1f, 0.2f, 50f, 100f, 10);
        assertThat(segment.applyEffectStrength(VibrationEffect.EFFECT_STRENGTH_STRONG))
                .isSameInstanceAs(segment);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testToString() {
        PwleSegment segment = new PwleSegment(0.1f, 0.2f, 50f, 100f, 10);
        String str = segment.toString();
        assertThat(str).contains("0.1");
        assertThat(str).contains("0.2");
        assertThat(str).contains("50.0");
        assertThat(str).contains("100.0");
        assertThat(str).contains("10");
    }

    @Test
    @EnableFlags({Flags.FLAG_NORMALIZED_PWLE_EFFECTS})
    public void testEquals() {
        PwleSegment segment = new PwleSegment(0.1f, 0.2f, 50f, 100f, 10, 200);
        assertThat(segment).isEqualTo(new PwleSegment(0.1f, 0.2f, 50f, 100f, 10, 200));
        assertThat(segment).isNotEqualTo(new PwleSegment(0.2f, 0.2f, 50f, 100f, 10, 200));
        assertThat(segment).isNotEqualTo(new PwleSegment(0.1f, 0.3f, 50f, 100f, 10, 200));
        assertThat(segment).isNotEqualTo(new PwleSegment(0.1f, 0.2f, 60f, 100f, 10, 200));
        assertThat(segment).isNotEqualTo(new PwleSegment(0.1f, 0.2f, 50f, 110f, 10, 200));
        assertThat(segment).isNotEqualTo(new PwleSegment(0.1f, 0.2f, 50f, 100f, 20, 200));
        assertThat(segment.applyStartTime(100)).isNotEqualTo(segment);
    }

    @Test
    @EnableFlags({Flags.FLAG_NORMALIZED_PWLE_EFFECTS})
    public void testApplyStartTime() {
        PwleSegment segment = new PwleSegment(0.1f, 0.2f, 50f, 100f, 10);
        assertThat(segment.getStartTimeMillis()).isEqualTo(-1);
        PwleSegment newSegment = segment.applyStartTime(100);
        assertThat(newSegment).isNotSameInstanceAs(segment);
        assertThat(newSegment.getStartTimeMillis()).isEqualTo(100);
        assertThat(newSegment.getDuration()).isEqualTo(10);
    }

}
