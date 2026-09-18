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

import android.annotation.Nullable;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorInfo;
import android.os.multisensory.MultisensoryContinuousEffect;
import android.os.multisensory.MultisensoryManager;
import android.os.multisensory.MultisensoryVibrationControlPoint;
import android.os.vibrator.VibratorEnvelopeEffectInfo;
import android.util.Slog;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A haptic effect in the Multisensory Design System (MSDS) defined by compositions of {@link
 * VibrationEffect.Composition} primitives.
 *
 * <p>The effect supports fallback vibrations if the composition of primitives is not supported and
 * if the device supports amplitude control.
 *
 * @hide
 */
public class MultisensoryComposedEffect implements MultisensoryHapticEffect {

    private static final String TAG = "MultisensoryComposedEffect";

    // The duration of a vibration session
    private static final int VIBRATION_SESSION_DURATION_MILLIS = 10_000;

    // Values of delay and amplitudes to use with the createWaveform haptics API. Used for fallbacks
    private static final long SPIN_DELAY = 56L;
    private static final long[] SPIN_WAVEFORM_TIMINGS = {
        20, 20, 3, 43, 20, 20, 3, 56, 20, 20, 3, 43, 20, 20, 3, 56, 20, 20, 3, 43, 20, 20, 3
    };
    private static final int[] SPIN_WAVEFORM_AMPLITUDES = {
        40, 80, 40, 0, 40, 80, 40, 10, 40, 80, 40, 0, 40, 80, 40, 10, 40, 80, 40, 0, 40, 80, 40
    };

    /**
     * Create the {@link MultisensoryHapticEffect} for a given token that encapsulates a {@link
     * MultisensoryComposedEffect}.
     *
     * @param token The token constant.
     * @param vibratorInfo The {@link VibratorInfo} of the device vibrator.
     * @param vibratorEnvelopeEffectInfo The {@link VibratorEnvelopeEffectInfo} of the device
     *     virbator.
     * @return A {@link MultisensoryHapticEffect} associated with the token.
     */
    public static @android.annotation.NonNull MultisensoryHapticEffect createHapticEffect(
            @MultisensoryManager.Token int token,
            VibratorInfo vibratorInfo,
            VibratorEnvelopeEffectInfo vibratorEnvelopeEffectInfo) {

        MultisensoryComposedEffect.Builder builder =
                new MultisensoryComposedEffect.Builder(vibratorInfo, vibratorEnvelopeEffectInfo);
        switch (token) {
            case MultisensoryManager.TOKEN_FAILURE_HIGH_EMPHASIS ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_SPIN, /*scale*/
                                    1f, /*delayMillis*/
                                    0)
                            .addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_SPIN, /*scale*/
                                    1f,
                                    (int) SPIN_DELAY)
                            .addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_SPIN, /*scale*/
                                    1f,
                                    (int) SPIN_DELAY)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createWaveform(
                                            SPIN_WAVEFORM_TIMINGS,
                                            SPIN_WAVEFORM_AMPLITUDES, /*repeat*/
                                            -1));
            case MultisensoryManager.TOKEN_FAILURE ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    0)
                            .addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    114)
                            .addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    114)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createWaveform(
                                            new long[] {
                                                10, 10, 10, 114, 10, 10, 10, 114, 10, 10, 10
                                            },
                                            new int[] {10, 255, 20, 0, 10, 255, 20, 0, 10, 255, 20},
                                            /*repeat*/ -1));
            case MultisensoryManager.TOKEN_SUCCESS ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    0)
                            .addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    114)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createWaveform(
                                            new long[] {10, 10, 10, 114, 10, 10, 10},
                                            new int[] {10, 255, 20, 0, 10, 255, 20}, /*repeat*/
                                            -1));
            case MultisensoryManager.TOKEN_STOP,
                    MultisensoryManager.TOKEN_CANCEL,
                    MultisensoryManager.TOKEN_SWITCH_ON,
                    MultisensoryManager.TOKEN_SWITCH_OFF ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    0)
                            .addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    52)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createWaveform(
                                            new long[] {10, 10, 10, 52, 10, 10, 10},
                                            new int[] {10, 255, 20, 0, 10, 255, 20},
                                            /*repeat*/ -1));
            case MultisensoryManager.TOKEN_UNLOCK, MultisensoryManager.TOKEN_LOCK ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_TICK, /*scale*/
                                    1f, /*delayMillis*/
                                    0)
                            .addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    52)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createWaveform(
                                            new long[] {5, 52, 10, 10, 10},
                                            new int[] {100, 0, 10, 255, 20},
                                            /*repeat*/ -1));
            case MultisensoryManager.TOKEN_START ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_THUD, /*scale*/
                                    1f, /*delayMillis*/
                                    0)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createWaveform(
                                            new long[] {50, 100, 100, 50},
                                            new int[] {5, 50, 20, 10}, /*repeat*/
                                            -1));
            case MultisensoryManager.TOKEN_PAUSE,
                    MultisensoryManager.TOKEN_LONG_PRESS,
                    MultisensoryManager.TOKEN_KEYPRESS_DELETE,
                    MultisensoryManager.TOKEN_DRAG_INDICATOR_THRESHOLD_LIMIT ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    1f, /*delayMillis*/
                                    0)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            case MultisensoryManager.TOKEN_SWIPE_INDICATOR_THRESHOLD_LIMIT,
                    MultisensoryManager.TOKEN_TAP_HIGH_EMPHASIS,
                    MultisensoryManager.TOKEN_KEYPRESS_SPACEBAR,
                    MultisensoryManager.TOKEN_KEYPRESS_RETURN ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    0.7f, /*delayMillis*/
                                    0)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            case MultisensoryManager.TOKEN_TAP_MEDIUM_EMPHASIS,
                    MultisensoryManager.TOKEN_KEYPRESS_STANDARD ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    0.5f, /*delayMillis*/
                                    0)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            case MultisensoryManager.TOKEN_DRAG_INDICATOR_CONTINUOUS -> {
                for (int i = 0; i < 5; i++) {
                    builder.addPrimitiveEffect(
                            VibrationEffect.Composition.PRIMITIVE_LOW_TICK, /*cale*/
                            0.3f, /*delayMillis*/
                            0);
                }
                builder.addAmplitudeControlFallbackEffect(
                                VibrationEffect.createWaveform(
                                        new long[] {10, 20, 20, 10},
                                        new int[] {10, 30, 50, 10}, /*repeat*/
                                        -1))
                        // TODO(b/462734796): Clarify the parameters of base envelopes
                        .addEnvelopeEffect(/*baseScale*/ 0.2f, /*baseFrequency*/ 100f);
            }
            case MultisensoryManager.TOKEN_DRAG_INDICATOR_DISCRETE ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_TICK, /*scale*/
                                    0.5f, /*delayMillis*/
                                    0)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                            // TODO(b/462734796): Clarify the parameters of base envelopes
                            .addEnvelopeEffect(/*baseScale*/ 0.01f, /*baseFrequency*/ 100f);
            case MultisensoryManager.TOKEN_TAP_LOW_EMPHASIS ->
                    builder.addPrimitiveEffect(
                                    VibrationEffect.Composition.PRIMITIVE_CLICK, /*scale*/
                                    0.3f, /*delayMillis*/
                                    0)
                            .addAmplitudeControlFallbackEffect(
                                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            default -> { }
        }
        return builder.build();
    }

    // Info objects to determine the support level
    private final VibratorInfo mVibratorInfo;
    private final VibratorEnvelopeEffectInfo mVibratorEnvelopeEffectInfo;

    // VibrationEffect that defines an amplitude-control fallback
    private final VibrationEffect mAmplitudeControlFallbackEffect;

    // Attributes that define a composition of primitives
    private @VibrationEffect.Composition.PrimitiveType int[] mPrimitiveIds;
    private float[] mPrimitiveScales;
    private int[] mPrimitiveDelaysMillis;

    // Attributes to create an envelope waveform for a vibration session
    private final float mEnvelopeScale;
    private final float mEnvelopeFrequency;

    private MultisensoryComposedEffect(Builder builder) {
        mVibratorInfo = builder.mVibratorInfo;
        mVibratorEnvelopeEffectInfo = builder.mVibratorEnvelopeEffectInfo;
        mAmplitudeControlFallbackEffect = builder.mAmplitudeControlFallbackEffect;

        if (builder.mPrimitiveIds != null) {
            int primitives = builder.mPrimitiveIds.size();
            mPrimitiveIds = new int[primitives];
            mPrimitiveScales = new float[primitives];
            mPrimitiveDelaysMillis = new int[primitives];
            for (int i = 0; i < builder.mPrimitiveIds.size(); i++) {
                mPrimitiveIds[i] = builder.mPrimitiveIds.get(i);
                mPrimitiveScales[i] = builder.mPrimitiveScales.get(i);
                mPrimitiveDelaysMillis[i] = builder.mPrimitiveDelaysMillis.get(i);
            }
        }

        mEnvelopeScale = builder.mEnvelopeScale;
        mEnvelopeFrequency = builder.mEnvelopeFrequency;
    }

    @Override
    public @Nullable VibrationEffect createSingleVibrationEffect() {
        if (supportsPrimitivesEffect()) {
            return createPrimitivesEffect();
        } else if (mVibratorInfo.hasAmplitudeControl()) {
            return mAmplitudeControlFallbackEffect;
        } else {
            return null;
        }
    }

    private boolean supportsPrimitivesEffect() {
        if (mPrimitiveIds == null) return false;

        for (int primitiveId : mPrimitiveIds) {
            if (!mVibratorInfo.isPrimitiveSupported(primitiveId)) {
                return false;
            }
        }
        return true;
    }

    private VibrationEffect createPrimitivesEffect() {
        VibrationEffect.Composition composition = VibrationEffect.startComposition();
        for (int i = 0; i < mPrimitiveIds.length; i++) {
            composition.addPrimitive(
                    mPrimitiveIds[i], mPrimitiveScales[i], mPrimitiveDelaysMillis[i]);
        }
        return composition.compose();
    }

    @Override
    public @Nullable MultisensoryContinuousEffect createContinuousEffect() {
        if (!mVibratorInfo.areEnvelopeEffectsSupported()) {
            Slog.w(TAG, "Session effect is null because envelope effects are not supported");
            return null;
        }
        if (mEnvelopeFrequency == -1) {
            Slog.w(TAG, "Session effect is null because envelope frequency was not specified");
            return null;
        }

        float minFrequency = mVibratorInfo.getFrequencyProfile().getMinFrequencyHz();
        float maxFrequency = mVibratorInfo.getFrequencyProfile().getMaxFrequencyHz();
        if (mEnvelopeFrequency < minFrequency || mEnvelopeFrequency > maxFrequency) {
            Slog.w(
                    TAG,
                    "Session effect is null because the frequency is out of the device range."
                            + ". Envelope frequency = "
                            + mEnvelopeFrequency
                            + ". Range = ["
                            + minFrequency
                            + ", "
                            + maxFrequency
                            + "] Hz");
            return null;
        }

        long rampDuration = mVibratorEnvelopeEffectInfo.getMinControlPointDurationMillis();
        List<MultisensoryVibrationControlPoint> controlPoints =
                createContinuousWaveformControlPoints(
                        mEnvelopeScale,
                        mEnvelopeFrequency,
                        rampDuration,
                        VIBRATION_SESSION_DURATION_MILLIS);
        MultisensoryContinuousEffect effect = new MultisensoryContinuousEffect();
        effect.controlPoints = controlPoints;
        return effect;
    }

    /**
     * Create a continuous waveform of a fixed duration.
     *
     * <p>The waveform is padded at the beginning and end by two ramps that linearly increase and
     * decrease the amplitude. The first ramp increases the amplitude from 0 to the base amplitude.
     * The second decreases the waveform from the base amplitude to zero.
     *
     * @param baseScale The base scale in the range from 0 to 1.
     * @param baseFrequencyHz The base frequency of the waveform in Hz. Must be positive.
     * @param rampDurationMillis The duration of the waveform in milliseconds.
     * @param waveformDurationMillis The duration (in milliseconds) of the linear ramps.
     */
    private List<MultisensoryVibrationControlPoint> createContinuousWaveformControlPoints(
            float baseScale,
            float baseFrequencyHz,
            long rampDurationMillis,
            long waveformDurationMillis) {

        int timeMillis = 0;
        MultisensoryVibrationControlPoint start = new MultisensoryVibrationControlPoint();
        start.amplitude = 0f;
        start.frequencyHz = baseFrequencyHz;
        start.timeMillis = timeMillis;

        timeMillis = (int) (timeMillis + rampDurationMillis);
        MultisensoryVibrationControlPoint rampToWaveform = new MultisensoryVibrationControlPoint();
        rampToWaveform.amplitude = baseScale;
        rampToWaveform.frequencyHz = baseFrequencyHz;
        rampToWaveform.timeMillis = timeMillis;

        timeMillis = (int) (timeMillis + waveformDurationMillis);
        MultisensoryVibrationControlPoint waveform = new MultisensoryVibrationControlPoint();
        waveform.amplitude = baseScale;
        waveform.frequencyHz = baseFrequencyHz;
        waveform.timeMillis = timeMillis;

        timeMillis = (int) (timeMillis + rampDurationMillis);
        MultisensoryVibrationControlPoint rampToZero = new MultisensoryVibrationControlPoint();
        rampToZero.amplitude = 0f;
        rampToZero.frequencyHz = baseFrequencyHz;
        rampToZero.timeMillis = timeMillis;

        ArrayList<MultisensoryVibrationControlPoint> controlPoints = new ArrayList<>();
        controlPoints.add(start);
        controlPoints.add(rampToWaveform);
        controlPoints.add(waveform);
        controlPoints.add(rampToZero);
        return controlPoints;
    }

    public static final class Builder {

        private final VibratorInfo mVibratorInfo;
        private final VibratorEnvelopeEffectInfo mVibratorEnvelopeEffectInfo;
        private VibrationEffect mAmplitudeControlFallbackEffect = null;
        private @VibrationEffect.Composition.PrimitiveType ArrayList<Integer> mPrimitiveIds = null;
        private ArrayList<Float> mPrimitiveScales = null;
        private ArrayList<Integer> mPrimitiveDelaysMillis = null;

        private float mEnvelopeScale = -1f;
        private float mEnvelopeFrequency = -1f;

        public Builder(
                VibratorInfo vibratorInfo, VibratorEnvelopeEffectInfo vibratorEnvelopeEffectInfo) {
            this.mVibratorInfo = vibratorInfo;
            this.mVibratorEnvelopeEffectInfo = vibratorEnvelopeEffectInfo;
        }

        /**
         * Add a primitive composition effect to the {@link MultisensoryComposedEffect}
         *
         * @param primitiveId The primitive Id from {@link VibrationEffect.Composition}.
         * @param scale A scale from 0 to 1 for the vibration of the primitive.
         * @param delayMillis A delay in milliseconds between the end of the previous primitive and
         *     the primitive being added.
         * @return The {@link Builder} of the {@link MultisensoryComposedEffect} being built.
         * @throws IllegalArgumentException If the scale is outside the [0, 1] range, or if
         *     delayMillis is negative.
         */
        public @NonNull Builder addPrimitiveEffect(
                @VibrationEffect.Composition.PrimitiveType int primitiveId,
                float scale,
                int delayMillis)
                throws IllegalArgumentException {
            if (scale < 0 || scale > 1) {
                throw new IllegalArgumentException(
                        "The primitive scale must be in the range [0,1]."
                                + " A value of "
                                + scale
                                + " was used");
            }
            if (delayMillis < 0) {
                throw new IllegalArgumentException(
                        "The primitive delay must be > 0"
                                + " A value of "
                                + delayMillis
                                + " was used");
            }

            if (this.mPrimitiveIds == null) {
                this.mPrimitiveIds = new ArrayList<>();
                this.mPrimitiveScales = new ArrayList<>();
                this.mPrimitiveDelaysMillis = new ArrayList<>();
            }
            this.mPrimitiveIds.add(primitiveId);
            this.mPrimitiveScales.add(scale);
            this.mPrimitiveDelaysMillis.add(delayMillis);
            return this;
        }

        /**
         * Add an envelope effect to the {@link MultisensoryComposedEffect}.
         *
         * <p>The baseScale and baseFrequency will be used to construct a waveform envelope using
         * the {@link VibrationEffect.WaveformEnvelopeBuilder} API if supported.
         *
         * @param baseScale The base scale of the envelope in the range from 0 to 1.
         * @param baseFrequency The base frequency of the envelope in Hz. It must fall within the
         *     frequency range of the device, as provided by {@link VibratorInfo.FrequencyProfile}.
         * @return The {@link Builder} of the {@link MultisensoryComposedEffect} being built.
         * @throws IllegalArgumentException If the scale is outside the [0, 1] range, or if
         *     baseFrequency is negative.
         */
        public @NonNull Builder addEnvelopeEffect(float baseScale, float baseFrequency) {
            if (baseScale < 0f || baseScale > 1f) {
                throw new IllegalArgumentException(
                        "The envelope base scale must be in the range "
                                + "[0,1]. A value of "
                                + baseScale
                                + " was used");
            }
            if (baseFrequency < 0) {
                throw new IllegalArgumentException(
                        "The envelope frequency must be > 0"
                                + " A value of "
                                + baseFrequency
                                + " was used");
            }

            this.mEnvelopeScale = baseScale;
            this.mEnvelopeFrequency = baseFrequency;
            return this;
        }

        /**
         * Add an amplitude-controlled fallback {@link VibrationEffect} to the {@link
         * MultisensoryComposedEffect}.
         *
         * <p>If the device supports amplitude control (see {@link Vibrator#hasAmplitudeControl()}),
         * use this method to add a fallback vibration if the device does not support the primitive
         * composition created with {@link #addPrimitiveEffect(int, float, int)}.
         *
         * @param fallbackEffect The fallback effect.
         * @return The {@link Builder} of the {@link MultisensoryComposedEffect} being built.
         */
        public @NonNull Builder addAmplitudeControlFallbackEffect(VibrationEffect fallbackEffect) {
            this.mAmplitudeControlFallbackEffect = fallbackEffect;
            return this;
        }

        /**
         * Build a {@link MultisensoryComposedEffect} object.
         *
         * @return A {@link MultisensoryComposedEffect}
         */
        public @NonNull MultisensoryComposedEffect build() {
            return new MultisensoryComposedEffect(this);
        }
    }
}
