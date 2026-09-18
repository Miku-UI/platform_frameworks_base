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

package com.android.server.vibrator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.CombinedVibration;
import android.os.Process;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.vibrator.BasicPwleSegment;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwleSegment;
import android.os.vibrator.StepSegment;
import android.platform.test.annotations.Presubmit;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@Presubmit
@RunWith(MockitoJUnitRunner.class)
public class VibrationTest {

    @Test
    public void testDebugInfoDump_dumpsSegments() throws Exception {
        // Create PWLE segment
        // PwleSegment(float startAmplitude, float endAmplitude, float startFrequencyHz,
        // float endFrequencyHz, int duration)
        PwleSegment pwle = new PwleSegment(0.5f, 1.0f, 100f, 200f, 50);
        // Create Basic PWLE segment
        // BasicPwleSegment(float startIntensity, float endIntensity, float startSharpness,
        // float endSharpness, int duration)
        BasicPwleSegment basicPwle = new BasicPwleSegment(0.2f, 0.8f, 0.4f, 0.6f, 60);
        // StepSegment(float amplitude, float frequencyHz, int duration)
        StepSegment step = new StepSegment(0.5f, 10);
        // PrebakedSegment(int effectId, boolean shouldFallback, int effectStrength)
        PrebakedSegment prebaked =
                new PrebakedSegment(
                        VibrationEffect.EFFECT_CLICK, true, VibrationEffect.EFFECT_STRENGTH_MEDIUM);
        // PrimitiveSegment(int primitiveId, float scale, int delay)
        PrimitiveSegment primitive =
                new PrimitiveSegment(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, 10);

        VibrationEffect effect =
                VibrationEffect.startComposition()
                        .addEffect(new VibrationEffect.Composed(List.of(pwle), -1))
                        .addEffect(new VibrationEffect.Composed(List.of(basicPwle), -1))
                        .addEffect(new VibrationEffect.Composed(List.of(step), -1))
                        .addEffect(new VibrationEffect.Composed(List.of(prebaked), -1))
                        .addEffect(new VibrationEffect.Composed(List.of(primitive), -1))
                        .compose();

        CombinedVibration combined = CombinedVibration.createParallel(effect);

        VibrationAttributes attrs = new VibrationAttributes.Builder().build();
        VibrationSession.CallerInfo callerInfo =
                new VibrationSession.CallerInfo(attrs, Process.myUid(), 0, "package", "reason");
        VibrationStats stats = new VibrationStats();

        // DebugInfoImpl(VibrationSession.Status status, VibrationSession.CallerInfo callerInfo,
        // int vibrationType, VibrationStats stats, CombinedVibration playedEffect,
        // CombinedVibration originalEffect, int scaleLevel, float scaleFactor, float adaptiveScale)
        Vibration.DebugInfoImpl debugInfo =
                new Vibration.DebugInfoImpl(
                        VibrationSession.Status.RUNNING,
                        callerInfo,
                        0,
                        stats,
                        combined,
                        null,
                        0,
                        1.0f,
                        1.0f);

        ProtoOutputStream protoOut = new ProtoOutputStream();
        long fieldId = VibratorManagerServiceDumpProto.CURRENT_VIBRATION;
        debugInfo.dump(protoOut, fieldId);
        protoOut.flush();

        ProtoInputStream protoIn = new ProtoInputStream(protoOut.getBytes());
        final boolean[] found = new boolean[5];
        int[] path =
                new int[] {
                    (int) fieldId,
                    (int) VibrationProto.PLAYED_EFFECT,
                    (int) CombinedVibrationEffectProto.EFFECTS,
                    (int) SyncVibrationEffectProto.EFFECTS,
                    (int) VibrationEffectProto.SEGMENTS
                };

        traverseProto(
                protoIn,
                path,
                (proto) -> {
                    int fieldNumber = proto.getFieldNumber();
                    if (fieldNumber == (int) SegmentProto.PWLE) {
                        found[0] = true;
                        long token = proto.start(SegmentProto.PWLE);
                        verifyPwle(proto, pwle);
                        proto.end(token);
                    } else if (fieldNumber == (int) SegmentProto.BASIC_PWLE) {
                        found[1] = true;
                        long token = proto.start(SegmentProto.BASIC_PWLE);
                        verifyBasicPwle(proto, basicPwle);
                        proto.end(token);
                    } else if (fieldNumber == (int) SegmentProto.STEP) {
                        found[2] = true;
                        long token = proto.start(SegmentProto.STEP);
                        verifyStep(proto, step);
                        proto.end(token);
                    } else if (fieldNumber == (int) SegmentProto.PREBAKED) {
                        found[3] = true;
                        long token = proto.start(SegmentProto.PREBAKED);
                        verifyPrebaked(proto, prebaked);
                        proto.end(token);
                    } else if (fieldNumber == (int) SegmentProto.PRIMITIVE) {
                        found[4] = true;
                        long token = proto.start(SegmentProto.PRIMITIVE);
                        verifyPrimitive(proto, primitive);
                        proto.end(token);
                    }
                });

        assertTrue("PWLE segment not found", found[0]);
        assertTrue("Basic PWLE segment not found", found[1]);
        assertTrue("Step segment not found", found[2]);
        assertTrue("Prebaked segment not found", found[3]);
        assertTrue("Primitive segment not found", found[4]);
    }

    private void verifyPwle(ProtoInputStream proto, PwleSegment segment) throws Exception {
        while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (proto.getFieldNumber()) {
                case 1 -> // startAmplitude
                    assertEquals(
                            segment.getStartAmplitude(),
                            proto.readFloat(PwleSegmentProto.START_AMPLITUDE),
                            0.01f);
                case 2 -> // endAmplitude
                    assertEquals(
                            segment.getEndAmplitude(),
                            proto.readFloat(PwleSegmentProto.END_AMPLITUDE),
                            0.01f);
                case 3 -> // startFrequency
                    assertEquals(
                            segment.getStartFrequencyHz(),
                            proto.readFloat(PwleSegmentProto.START_FREQUENCY),
                            0.01f);
                case 4 -> // endFrequency
                    assertEquals(
                            segment.getEndFrequencyHz(),
                            proto.readFloat(PwleSegmentProto.END_FREQUENCY),
                            0.01f);
                case 5 -> // duration
                    assertEquals(segment.getDuration(), proto.readInt(PwleSegmentProto.DURATION));
                default -> {
                    // Fall through
                }
            }
        }
    }

    private void verifyBasicPwle(ProtoInputStream proto, BasicPwleSegment segment)
            throws Exception {
        while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (proto.getFieldNumber()) {
                case 1 -> // startIntensity
                    assertEquals(
                        segment.getStartIntensity(),
                        proto.readFloat(BasicPwleSegmentProto.START_INTENSITY),
                        0.01f);
                case 2 -> // endIntensity
                    assertEquals(
                        segment.getEndIntensity(),
                        proto.readFloat(BasicPwleSegmentProto.END_INTENSITY),
                        0.01f);
                case 3 -> // startSharpness
                    assertEquals(
                        segment.getStartSharpness(),
                        proto.readFloat(BasicPwleSegmentProto.START_SHARPNESS),
                        0.01f);
                case 4 -> // endSharpness
                    assertEquals(
                        segment.getEndSharpness(),
                        proto.readFloat(BasicPwleSegmentProto.END_SHARPNESS),
                        0.01f);
                case 5 -> // duration
                    assertEquals(
                        segment.getDuration(), proto.readInt(BasicPwleSegmentProto.DURATION));
                default -> {}
            }
        }
    }

    private void verifyStep(ProtoInputStream proto, StepSegment segment) throws Exception {
        while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (proto.getFieldNumber()) {
                case 1 -> // duration
                    assertEquals(segment.getDuration(), proto.readInt(StepSegmentProto.DURATION));
                case 2 -> // amplitude
                    assertEquals(
                        segment.getAmplitude(), proto.readFloat(StepSegmentProto.AMPLITUDE), 0.01f);
            }
        }
    }

    private void verifyPrebaked(ProtoInputStream proto, PrebakedSegment segment) throws Exception {
        while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (proto.getFieldNumber()) {
                case 1 -> // effect_id
                assertEquals(
                        segment.getEffectId(), proto.readInt(PrebakedSegmentProto.EFFECT_ID));
                case 2 -> // effect_strength
                assertEquals(
                        segment.getEffectStrength(),
                        proto.readInt(PrebakedSegmentProto.EFFECT_STRENGTH));
                case 3 -> // fallback
                assertEquals(
                        segment.shouldFallback(),
                        proto.readInt(PrebakedSegmentProto.FALLBACK) != 0);
            }
        }
    }

    private void verifyPrimitive(ProtoInputStream proto, PrimitiveSegment segment)
            throws Exception {
        while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (proto.getFieldNumber()) {
                case 1 -> // primitive_id
                    assertEquals(
                        segment.getPrimitiveId(),
                        proto.readInt(PrimitiveSegmentProto.PRIMITIVE_ID));
                case 2 -> // scale
                    assertEquals(
                        segment.getScale(), proto.readFloat(PrimitiveSegmentProto.SCALE), 0.01f);
                case 3 -> // delay
                    assertEquals(segment.getDelay(), proto.readInt(PrimitiveSegmentProto.DELAY));
            }
        }
    }

    private void traverseProto(ProtoInputStream proto, int[] path, ProtoConsumer consumer)
            throws Exception {
        traverseProto(proto, path, 0, consumer);
    }

    private void traverseProto(
            ProtoInputStream proto, int[] path, int index, ProtoConsumer consumer)
            throws Exception {
        while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            if (proto.getFieldNumber() == path[index]) {
                long token = proto.start(path[index]);
                if (index == path.length - 1) {
                    while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                        consumer.accept(proto);
                    }
                } else {
                    traverseProto(proto, path, index + 1, consumer);
                }
                proto.end(token);
            }
        }
    }

    @FunctionalInterface
    private interface ProtoConsumer {
        void accept(ProtoInputStream proto) throws Exception;
    }
}
