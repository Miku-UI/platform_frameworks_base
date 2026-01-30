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

import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.EFFECT_STRENGTH_MEDIUM;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.vibrator.IVibrator;
import android.os.VibrationEffect;
import android.os.VibratorInfo;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.os.vibrator.RampSegment;

import org.junit.Test;

public class DefaultHalVibratorTest extends HalVibratorTestCase {

    @Override
    HalVibrator newVibrator(int vibratorId) {
        HalNativeHandler nativeHandler = mHelper.newInitializedNativeHandler(mCallbacksMock);
        return mHelper.newDefaultVibrator(vibratorId, nativeHandler);
    }

    @Test
    public void init_doesNotLoadInfo() {
        // Values from HAL ignored.
        mHelper.setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHelper.setSupportedEffects(VibrationEffect.EFFECT_CLICK);
        HalVibrator vibrator = newVibrator(VIBRATOR_ID);

        vibrator.init(mCallbacksMock);
        assertThat(vibrator.getInfo()).isEqualTo(new VibratorInfo.Builder(VIBRATOR_ID).build());
    }

    @Test
    public void setExternalControl_failed_doesNotChangeVibrateState() {
        mHelper.setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHelper.setExternalControlToFail();
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        assertThat(vibrator.isVibrating()).isFalse();

        assertThat(vibrator.setExternalControl(true)).isFalse();
        assertThat(vibrator.isVibrating()).isFalse();
    }

    @Test
    public void on_withDurationFailed_doesNotTurnVibratorOn() {
        mHelper.setOnToFail();
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        assertThat(vibrator.on(1, 1, /* milliseconds= */ 100)).isEqualTo(-1L);
        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }

    @Test
    public void on_withDurationAndCallbackFailed_doesNotTurnVibratorOn() {
        mHelper.setCapabilities(IVibrator.CAP_ON_CALLBACK);
        mHelper.setOnToFail();
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);

        assertThat(vibrator.on(1, 1, /* milliseconds= */ 100)).isEqualTo(-1L);
        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }

    @Test
    public void on_withPrebakedFailed_doesNotTurnVibratorOn() {
        mHelper.setSupportedEffects(EFFECT_CLICK);
        mHelper.setPrebakedToFail();
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrebakedSegment prebaked = createPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_MEDIUM);

        assertThat(vibrator.on(1, 1, prebaked)).isEqualTo(-1L);
        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }

    @Test
    public void on_withPrebakedAndCallbackFailed_doesNotTurnVibratorOn() {
        mHelper.setCapabilities(IVibrator.CAP_PERFORM_CALLBACK);
        mHelper.setSupportedEffects(EFFECT_CLICK);
        mHelper.setPrebakedToFail();
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrebakedSegment prebaked = createPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_MEDIUM);

        assertThat(vibrator.on(1, 1, prebaked)).isEqualTo(-1L);
        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }

    @Test
    public void on_withComposedFailed_doesNotTurnVibratorOn() {
        mHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        mHelper.setPrimitivesToFail();
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PrimitiveSegment[] primitives = new PrimitiveSegment[]{
                new PrimitiveSegment(PRIMITIVE_CLICK, 0.5f, 10),
        };

        assertThat(vibrator.on(1, 1, primitives)).isEqualTo(-1L);
        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }

    @Test
    public void on_withComposedPwleFailed_doesNotTurnVibratorOn() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        mHelper.setPwleV1ToFail();
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        RampSegment[] ramps = new RampSegment[]{
                new RampSegment(/* startAmplitude= */ 0, /* endAmplitude= */ 1,
                        /* startFrequencyHz= */ 100, /* endFrequencyHz= */ 200, /* duration= */ 10)
        };

        assertThat(vibrator.on(1, 1, ramps)).isEqualTo(-1L);
        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }

    @Test
    public void on_withComposedPwleV2Failed_doesNotTurnVibratorOn() {
        mHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        mHelper.setPwleV2ToFail();
        HalVibrator vibrator = newInitializedVibrator(VIBRATOR_ID);
        PwlePoint[] points = new PwlePoint[]{
                new PwlePoint(/*amplitude=*/ 0, /*frequencyHz=*/ 100, /*timeMillis=*/ 0),
                new PwlePoint(/*amplitude=*/ 1, /*frequencyHz=*/ 200, /*timeMillis=*/ 30)
        };

        assertThat(vibrator.on(1, 1, points)).isEqualTo(-1L);
        assertThat(vibrator.isVibrating()).isFalse();
        assertThat(vibrator.getCurrentAmplitude()).isEqualTo(0f);
    }
}
