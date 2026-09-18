/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.testng.Assert.assertThrows;
import static com.google.common.truth.Truth.assertThat;

import android.hardware.vibrator.IVibrator;
import android.os.Parcel;
import android.os.VibrationEffect;
import android.os.VibratorInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrimitiveSegmentTest {
    private static final float TOLERANCE = 1e-2f;

    @Test
    public void testCreation() {
        PrimitiveSegment primitive = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 10);

        assertEquals(-1, primitive.getDuration());
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, primitive.getPrimitiveId());
        assertEquals(10, primitive.getDelay());
        assertEquals(1f, primitive.getScale(), TOLERANCE);
    }

    @Test
    public void testSerialization() {
        PrimitiveSegment original = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 10);
        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        assertEquals(original, PrimitiveSegment.CREATOR.createFromParcel(parcel));
        parcel.setDataPosition(0);
        assertEquals(original, VibrationEffectSegment.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testValidate() {
        new PrimitiveSegment(VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 0).validate();

        assertThrows(IllegalArgumentException.class,
                () -> new PrimitiveSegment(1000, 0, 10).validate());
        assertThrows(IllegalArgumentException.class,
                () -> new PrimitiveSegment(VibrationEffect.Composition.PRIMITIVE_NOOP, -1, 0)
                        .validate());
        assertThrows(IllegalArgumentException.class,
                () -> new PrimitiveSegment(VibrationEffect.Composition.PRIMITIVE_NOOP, 1, -1)
                        .validate());
    }

    @Test
    public void testResolve_ignoresAndReturnsSameEffect() {
        PrimitiveSegment primitive = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 0);
        assertSame(primitive, primitive.resolve(1000));
    }

    @Test
    public void testApplyEffectStrength_ignoresAndReturnsSameEffect() {
        PrimitiveSegment primitive = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 0);
        assertSame(primitive,
                primitive.applyEffectStrength(VibrationEffect.EFFECT_STRENGTH_STRONG));
    }

    @Test
    public void testScale_fullPrimitiveScaleValue() {
        PrimitiveSegment initial = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 0);

        assertEquals(1f, initial.scale(1).getScale(), TOLERANCE);
        assertEquals(0.5f, initial.scale(0.5f).getScale(), TOLERANCE);
        // The original value was not scaled up, so this only scales it down.
        assertEquals(1f, initial.scale(1.5f).getScale(), TOLERANCE);
        assertEquals(2 / 3f, initial.scale(1.5f).scale(2 / 3f).getScale(), TOLERANCE);
        // Does not restore to the exact original value because scale up is a bit offset.
        assertEquals(0.8f, initial.scale(0.8f).getScale(), TOLERANCE);
        assertEquals(0.86f, initial.scale(0.8f).scale(1.25f).getScale(), TOLERANCE);
    }

    @Test
    public void testScale_halfPrimitiveScaleValue() {
        PrimitiveSegment initial = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 0);

        assertEquals(0.5f, initial.scale(1).getScale(), TOLERANCE);
        assertEquals(0.25f, initial.scale(0.5f).getScale(), TOLERANCE);
        // The original value was not scaled up, so this only scales it down.
        assertEquals(0.66f, initial.scale(1.5f).getScale(), TOLERANCE);
        assertEquals(0.44f, initial.scale(1.5f).scale(2 / 3f).getScale(), TOLERANCE);
        // Does not restore to the exact original value because scale up is a bit offset.
        assertEquals(0.4f, initial.scale(0.8f).getScale(), TOLERANCE);
        assertEquals(0.48f, initial.scale(0.8f).scale(1.25f).getScale(), TOLERANCE);
    }

    @Test
    public void testScale_zeroPrimitiveScaleValue() {
        PrimitiveSegment initial = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 0, 0);

        assertEquals(0f, initial.scale(1).getScale(), TOLERANCE);
        assertEquals(0f, initial.scale(0.5f).getScale(), TOLERANCE);
        assertEquals(0f, initial.scale(1.5f).getScale(), TOLERANCE);
        assertEquals(0f, initial.scale(1.5f).scale(2 / 3f).getScale(), TOLERANCE);
        assertEquals(0f, initial.scale(0.8f).scale(1.25f).getScale(), TOLERANCE);
    }

    @Test
    public void testApplyAdaptiveScale() {
        PrimitiveSegment initial = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 0);

        assertEquals(1f, initial.applyAdaptiveScale(1).getScale(), TOLERANCE);
        assertEquals(0.5f, initial.applyAdaptiveScale(0.5f).getScale(), TOLERANCE);
        assertEquals(1f, initial.applyAdaptiveScale(1.5f).getScale(), TOLERANCE);
        assertEquals(0.8f, initial.applyAdaptiveScale(0.8f).getScale(), TOLERANCE);
        // Restores back to the exact original value.
        assertEquals(1f, initial.applyAdaptiveScale(0.8f).applyAdaptiveScale(1.25f).getScale(),
                TOLERANCE);

        initial = new PrimitiveSegment(VibrationEffect.Composition.PRIMITIVE_CLICK, 0, 0);

        assertEquals(0f, initial.applyAdaptiveScale(1).getScale(), TOLERANCE);
        assertEquals(0f, initial.applyAdaptiveScale(0.5f).getScale(), TOLERANCE);
        assertEquals(0f, initial.applyAdaptiveScale(1.5f).getScale(), TOLERANCE);
        assertEquals(0f, initial.applyAdaptiveScale(1.5f).applyAdaptiveScale(2 / 3f).getScale(),
                TOLERANCE);
        assertEquals(0f, initial.applyAdaptiveScale(0.8f).applyAdaptiveScale(1.25f).getScale(),
                TOLERANCE);
    }

    @Test
    public void testDuration() {
        assertEquals(-1, new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_NOOP, 1, 10).getDuration());
        assertEquals(-1, new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 100).getDuration());
        assertEquals(-1, new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_SPIN, 1, 0).getDuration());
    }

    @Test
    public void testDuration_withVibratorSupportingPrimitives_returnsVibratorDurationWithDelay() {
        VibratorInfo vibratorInfo = createVibratorInfoWithSupportedPrimitive(
                VibrationEffect.Composition.PRIMITIVE_CLICK, /* durationMs= */ 10);
        assertEquals(15, new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 5).getDuration(vibratorInfo));
    }

    @Test
    public void testDuration_withVibratorNotSupportingPrimitive_returnsUnknown() {
        VibratorInfo vibratorInfo = createVibratorInfoWithSupportedPrimitive(
                VibrationEffect.Composition.PRIMITIVE_CLICK);
        assertEquals(-1, new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_NOOP, 1, 5).getDuration(vibratorInfo));
    }

    @Test
    public void testVibrationFeaturesSupport_primitiveSupportedByVibrator() {
        assertTrue(createSegment(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .areVibrationFeaturesSupported(
                        createVibratorInfoWithSupportedPrimitive(
                                VibrationEffect.Composition.PRIMITIVE_CLICK)));
        assertTrue(createSegment(VibrationEffect.Composition.PRIMITIVE_THUD)
                .areVibrationFeaturesSupported(
                        createVibratorInfoWithSupportedPrimitive(
                                VibrationEffect.Composition.PRIMITIVE_THUD)));
        assertTrue(createSegment(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
                .areVibrationFeaturesSupported(
                        createVibratorInfoWithSupportedPrimitive(
                                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)));
    }

    @Test
    public void testVibrationFeaturesSupport_primitiveNotSupportedByVibrator() {
        assertFalse(createSegment(VibrationEffect.Composition.PRIMITIVE_CLICK)
                .areVibrationFeaturesSupported(
                        createVibratorInfoWithSupportedPrimitive(
                                VibrationEffect.Composition.PRIMITIVE_THUD)));
        assertFalse(createSegment(VibrationEffect.Composition.PRIMITIVE_THUD)
                .areVibrationFeaturesSupported(
                        createVibratorInfoWithSupportedPrimitive(
                                VibrationEffect.Composition.PRIMITIVE_CLICK)));
        assertFalse(createSegment(VibrationEffect.Composition.PRIMITIVE_THUD)
                .areVibrationFeaturesSupported(
                        createVibratorInfoWithSupportedPrimitive(
                                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)));
    }

    @Test
    public void testIsHapticFeedbackCandidate_returnsTrue() {
        assertTrue(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_NOOP, 1, 10).isHapticFeedbackCandidate());
        assertTrue(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 10).isHapticFeedbackCandidate());
        assertTrue(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_TICK, 1, 10).isHapticFeedbackCandidate());
        assertTrue(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_THUD, 1, 10).isHapticFeedbackCandidate());
        assertTrue(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_SPIN, 1, 10).isHapticFeedbackCandidate());
    }

    @Test
    public void testToString() {
        PrimitiveSegment segment = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 10);
        String str = segment.toString();
        assertThat(str).contains("CLICK");
        assertThat(str).contains("1.0");
        assertThat(str).contains("10");
    }

    @Test
    public void testEquals() {
        PrimitiveSegment segment = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 10);
        assertThat(segment).isEqualTo(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 10));
        assertThat(segment).isNotEqualTo(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_THUD, 1, 10));
        assertThat(segment).isNotEqualTo(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f, 10));
        assertThat(segment).isNotEqualTo(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 20));
        assertThat(segment).isNotEqualTo(new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 10,
                VibrationEffect.Composition.DELAY_TYPE_RELATIVE_START_OFFSET));
    }

    @Test
    public void testApplyStartTime() {
        PrimitiveSegment segment = new PrimitiveSegment(
                VibrationEffect.Composition.PRIMITIVE_CLICK, 1, 10,
                VibrationEffect.Composition.DELAY_TYPE_RELATIVE_START_OFFSET);
        assertThat(segment.getStartTimeMillis()).isEqualTo(-1);

        try {
            segment.applyStartTime(100);
            fail("PrimitiveSegment does not support applying start time.");
        } catch (UnsupportedOperationException e) {
            // Expected.
        }
    }

    private static PrimitiveSegment createSegment(int primitiveId) {
        // note: arbitrary scale and delay values being used.
        return new PrimitiveSegment(primitiveId, 0.2f, 10);
    }

    private static VibratorInfo createVibratorInfoWithSupportedPrimitive(int primitiveId) {
        return createVibratorInfoWithSupportedPrimitive(primitiveId, /* durationMs= */ 10);
    }

    private static VibratorInfo createVibratorInfoWithSupportedPrimitive(int primitiveId,
            int durationMs) {
        return new VibratorInfo.Builder(/* id= */ 1)
                .setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS)
                .setSupportedPrimitive(primitiveId, durationMs)
                .build();
    }
}
