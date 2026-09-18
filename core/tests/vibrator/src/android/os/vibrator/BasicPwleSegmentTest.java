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
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

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
public class BasicPwleSegmentTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testCreation() {
        BasicPwleSegment segment = new BasicPwleSegment(0.1f, 0.2f, 0.3f, 0.4f, 10);
        assertThat(segment.getStartIntensity()).isEqualTo(0.1f);
        assertThat(segment.getEndIntensity()).isEqualTo(0.2f);
        assertThat(segment.getStartSharpness()).isEqualTo(0.3f);
        assertThat(segment.getEndSharpness()).isEqualTo(0.4f);
        assertThat(segment.getDuration()).isEqualTo(10);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testValidate() {
        new BasicPwleSegment(0, 1, 0, 1, 1).validate();

        assertThrows(
                IllegalArgumentException.class,
                () -> new BasicPwleSegment(-0.1f, 1, 0.5f, 0.5f, 10).validate());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BasicPwleSegment(0, 1.1f, 0.5f, 0.5f, 10).validate());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BasicPwleSegment(0, 1, -0.1f, 0.5f, 10).validate());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BasicPwleSegment(0, 1, 0.5f, 1.1f, 10).validate());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BasicPwleSegment(0, 1, 0.5f, 0.5f, -1).validate());
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testGetDuration() {
        BasicPwleSegment segment = new BasicPwleSegment(0, 1, 0, 1, 10);
        assertThat(segment.getDuration()).isEqualTo(10);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testSerialization() {
        BasicPwleSegment original = new BasicPwleSegment(0.1f, 0.9f, 0.2f, 0.8f, 20);
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        // Creator requires the parcel token to be read first.
        assertThat(parcel.readInt()).isEqualTo(VibrationEffectSegment.PARCEL_TOKEN_BASIC_PWLE);
        parcel.setDataPosition(0);
        assertEquals(original, BasicPwleSegment.CREATOR.createFromParcel(parcel));
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
                        .build();
        VibratorInfo infoWithoutPwle = new VibratorInfo.Builder(0).build();

        BasicPwleSegment segment = new BasicPwleSegment(0, 1, 0, 1, 10);
        assertTrue(segment.areVibrationFeaturesSupported(infoWithPwle));
        assertFalse(segment.areVibrationFeaturesSupported(infoWithoutPwle));
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testScale() {
        BasicPwleSegment segment = new BasicPwleSegment(0.2f, 0.8f, 0.1f, 0.9f, 20);
        assertThat(segment.scale(0.5f))
                .isEqualTo(new BasicPwleSegment(0.1f, 0.4f, 0.1f, 0.9f, 20));
        assertThat(segment.applyAdaptiveScale(1f)).isSameInstanceAs(segment);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testResolve() {
        BasicPwleSegment segment = new BasicPwleSegment(0.1f, 0.2f, 0.5f, 0.8f, 10);
        assertThat(segment.resolve(100)).isSameInstanceAs(segment);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testApplyEffectStrength() {
        BasicPwleSegment segment = new BasicPwleSegment(0.1f, 0.2f, 0.5f, 0.8f, 10);
        assertThat(segment.applyEffectStrength(VibrationEffect.EFFECT_STRENGTH_STRONG))
                .isSameInstanceAs(segment);
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void testToString() {
        BasicPwleSegment segment = new BasicPwleSegment(0.1f, 0.2f, 0.5f, 0.8f, 10);
        String str = segment.toString();
        assertThat(str).contains("0.1");
        assertThat(str).contains("0.2");
        assertThat(str).contains("0.5");
        assertThat(str).contains("0.8");
        assertThat(str).contains("10");
    }

    @Test
    @EnableFlags({Flags.FLAG_NORMALIZED_PWLE_EFFECTS})
    public void testEquals() {
        BasicPwleSegment segment = new BasicPwleSegment(0.1f, 0.2f, 0.5f, 0.8f, 10, 200);
        assertThat(segment).isEqualTo(new BasicPwleSegment(0.1f, 0.2f, 0.5f, 0.8f, 10, 200));
        assertThat(segment).isNotEqualTo(new BasicPwleSegment(0.2f, 0.2f, 0.5f, 0.8f, 10, 200));
        assertThat(segment).isNotEqualTo(new BasicPwleSegment(0.1f, 0.3f, 0.5f, 0.8f, 10, 200));
        assertThat(segment).isNotEqualTo(new BasicPwleSegment(0.1f, 0.2f, 0.6f, 0.8f, 10, 200));
        assertThat(segment).isNotEqualTo(new BasicPwleSegment(0.1f, 0.2f, 0.5f, 0.9f, 10, 200));
        assertThat(segment).isNotEqualTo(new BasicPwleSegment(0.1f, 0.2f, 0.5f, 0.8f, 20, 200));
        assertThat(segment.applyStartTime(100)).isNotEqualTo(segment);
    }

    @Test
    @EnableFlags({Flags.FLAG_NORMALIZED_PWLE_EFFECTS})
    public void testApplyStartTime() {
        BasicPwleSegment segment = new BasicPwleSegment(0.1f, 0.2f, 0.5f, 0.8f, 10);
        assertThat(segment.getStartTimeMillis()).isEqualTo(-1);
        BasicPwleSegment newSegment = segment.applyStartTime(100);
        assertThat(newSegment).isNotSameInstanceAs(segment);
        assertThat(newSegment.getStartTimeMillis()).isEqualTo(100);
        assertThat(newSegment.getDuration()).isEqualTo(10);
    }
}
