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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.vibrator.ActivePwle;
import android.hardware.vibrator.CompositeEffect;
import android.hardware.vibrator.CompositePwleV2;
import android.hardware.vibrator.FrequencyAccelerationMapEntry;
import android.hardware.vibrator.IVibrationSession;
import android.hardware.vibrator.IVibrator;
import android.hardware.vibrator.IVibratorCallback;
import android.hardware.vibrator.PrimitivePwle;
import android.hardware.vibrator.PwleV2Primitive;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.VibrationEffect;
import android.os.VibrationEffect.VendorEffect;
import android.os.VibratorInfo;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.os.vibrator.RampSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;

import com.android.internal.hidden_from_bootclasspath.android.os.vibrator.Flags;
import com.android.server.vibrator.VintfHalVibrator.DefaultHalVibrator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Provides {@link HalVibrator} with configurable vibrator hardware capabilities and
 * fake interactions for tests.
 */
public final class HalVibratorHelper {
    public static final long EFFECT_DURATION = 20;

    private final Handler mHandler;

    private final Map<Long, PrebakedSegment> mEnabledAlwaysOnEffects = new TreeMap<>();
    private final List<VibrationEffectSegment> mEffectSegments = new ArrayList<>();
    private final List<VendorEffect> mVendorEffects = new ArrayList<>();
    private final List<PwlePoint> mEffectPwlePoints = new ArrayList<>();
    private final List<Integer> mBraking = new ArrayList<>();
    private final List<Float> mAmplitudes = new ArrayList<>();
    private final List<Boolean> mExternalControlStates = new ArrayList<>();
    private int mConnectCount;
    private int mOffCount;

    private boolean mLoadInfoShouldFail = false;
    private boolean mExternalControlShouldFail = false;
    private boolean mOnShouldFail = false;
    private boolean mPrebakedShouldFail = false;
    private boolean mVendorEffectsShouldFail = false;
    private boolean mPrimitivesShouldFail = false;
    private boolean mPwleV1ShouldFail = false;
    private boolean mPwleV2ShouldFail = false;

    private long mCompletionCallbackLatency;
    private long mOnLatency;
    private long mOffLatency;

    private int mCapabilities;
    private int[] mSupportedEffects;
    private int[] mSupportedBraking;
    private int[] mSupportedPrimitives;
    private int mCompositionDelayMax;
    private int mCompositionSizeMax;
    private int mPwleSizeMax;
    private int mPwlePrimitiveDurationMax;
    private int mMaxEnvelopeEffectSize;
    private int mMinEnvelopeEffectControlPointDurationMillis;
    private int mMaxEnvelopeEffectControlPointDurationMillis;
    private float mMinFrequency = Float.NaN;
    private float mResonantFrequency = Float.NaN;
    private float mFrequencyResolution = Float.NaN;
    private float mQFactor = Float.NaN;
    private float[] mMaxAmplitudes;
    private float[] mFrequenciesHz;
    private float[] mOutputAccelerationsGs;
    private long mVendorEffectDuration = EFFECT_DURATION;
    private long mPrimitiveDuration = EFFECT_DURATION;

    public HalVibratorHelper(Looper looper) {
        mHandler = new Handler(looper);
    }

    /** Return new {@link VibratorController} instance. */
    public VibratorController newVibratorController(int vibratorId) {
        return new VibratorController(vibratorId, new FakeNativeWrapper());
    }

    /** Return new {@link DefaultHalVibrator} instance. */
    public DefaultHalVibrator newDefaultVibrator(int vibratorId, HalNativeHandler nativeHandler) {
        FakeVibratorSupplier supplier = new FakeVibratorSupplier(new FakeVibrator());
        return new DefaultHalVibrator(vibratorId, supplier, mHandler, nativeHandler);
    }

    /** Return new and initialized {@link HalVibrator} instance. */
    public HalVibrator newInitializedHalVibrator(int vibratorId, HalVibrator.Callbacks callbacks) {
        HalVibrator vibrator = Flags.removeHidlSupport()
                ? newDefaultVibrator(vibratorId, newInitializedNativeHandler(callbacks))
                : newVibratorController(vibratorId);
        vibrator.init(callbacks);
        vibrator.onSystemReady();
        return vibrator;
    }

    /** Return an initialized {@link HalNativeHandler} instance. */
    public HalNativeHandler newInitializedNativeHandler(HalVibrator.Callbacks callbacks) {
        FakeHalNativeHandler handler = new FakeHalNativeHandler();
        handler.init(null, callbacks);
        return handler;
    }

    /** Makes get info calls fail. */
    public void setLoadInfoToFail() {
        mLoadInfoShouldFail = true;
    }

    /** Makes vibrator ON(millis) calls fail. */
    public void setExternalControlToFail() {
        mExternalControlShouldFail = true;
    }

    /** Makes vibrator ON(millis) calls fail. */
    public void setOnToFail() {
        mOnShouldFail = true;
    }

    /** Makes vibrator perform prebaked effect calls fail. */
    public void setPrebakedToFail() {
        mPrebakedShouldFail = true;
    }

    /** Makes vibrator perform prebaked effect calls fail. */
    public void setVendorEffectsToFail() {
        mVendorEffectsShouldFail = true;
    }

    /** Makes vibrator compose primitives calls fail. */
    public void setPrimitivesToFail() {
        mPrimitivesShouldFail = true;
    }

    /** Makes vibrator compose PWLE V1 calls fail. */
    public void setPwleV1ToFail() {
        mPwleV1ShouldFail = true;
    }

    /** Makes vibrator compose PWLE V2 calls fail. */
    public void setPwleV2ToFail() {
        mPwleV2ShouldFail = true;
    }

    /** Sets the latency for triggering the vibration completed callback. */
    public void setCompletionCallbackLatency(long millis) {
        mCompletionCallbackLatency = millis;
    }

    /** Sets the latency for turning the vibrator hardware on or setting the vibration amplitude. */
    public void setOnLatency(long millis) {
        mOnLatency = millis;
    }

    /** Sets the latency this controller should fake for turning the vibrator off. */
    public void setOffLatency(long millis) {
        mOffLatency = millis;
    }

    /** Set the capabilities of the fake vibrator hardware. */
    public void setCapabilities(int... capabilities) {
        mCapabilities = Arrays.stream(capabilities).reduce(0, (a, b) -> a | b);
    }

    /** Set the effects supported by the fake vibrator hardware. */
    public void setSupportedEffects(int... effects) {
        if (effects != null) {
            effects = Arrays.copyOf(effects, effects.length);
            Arrays.sort(effects);
        }
        mSupportedEffects = effects;
    }

    /** Set the effects supported by the fake vibrator hardware. */
    public void setSupportedBraking(int... braking) {
        if (braking != null) {
            braking = Arrays.copyOf(braking, braking.length);
            Arrays.sort(braking);
        }
        mSupportedBraking = braking;
    }

    /** Set the primitives supported by the fake vibrator hardware. */
    public void setSupportedPrimitives(int... primitives) {
        if (primitives != null) {
            primitives = Arrays.copyOf(primitives, primitives.length);
            Arrays.sort(primitives);
        }
        mSupportedPrimitives = primitives;
    }

    /** Set the maximum composition delay duration in fake vibrator hardware. */
    public void setCompositionDelayMax(int millis) {
        mCompositionDelayMax = millis;
    }

    /** Set the max number of primitives allowed in a composition by the fake vibrator hardware. */
    public void setCompositionSizeMax(int limit) {
        mCompositionSizeMax = limit;
    }

    /** Set the max number of PWLEs allowed in a composition by the fake vibrator hardware. */
    public void setPwleSizeMax(int limit) {
        mPwleSizeMax = limit;
    }

    /** Set the max duration of PWLE primitives in a composition by the fake vibrator hardware. */
    public void setPwlePrimitiveDurationMax(int millis) {
        mPwlePrimitiveDurationMax = millis;
    }

    /** Set the resonant frequency of the fake vibrator hardware. */
    public void setResonantFrequency(float frequencyHz) {
        mResonantFrequency = frequencyHz;
    }

    /** Set the minimum frequency of the fake vibrator hardware. */
    public void setMinFrequency(float frequencyHz) {
        mMinFrequency = frequencyHz;
    }

    /** Set the frequency resolution of the fake vibrator hardware. */
    public void setFrequencyResolution(float frequencyHz) {
        mFrequencyResolution = frequencyHz;
    }

    /** Set the Q factor of the fake vibrator hardware. */
    public void setQFactor(float qFactor) {
        mQFactor = qFactor;
    }

    /** Set the max amplitude supported for each frequency f the fake vibrator hardware. */
    public void setMaxAmplitudes(float... maxAmplitudes) {
        mMaxAmplitudes = maxAmplitudes;
    }

    /** Set the list of available frequencies. */
    public void setFrequenciesHz(float[] frequenciesHz) {
        mFrequenciesHz = frequenciesHz;
    }

    /** Set the max output acceleration achievable by the supported frequencies. */
    public void setOutputAccelerationsGs(float[] accelerationsGs) {
        mOutputAccelerationsGs = accelerationsGs;
    }

    /** Set the duration of vendor effects in fake vibrator hardware. */
    public void setVendorEffectDuration(long millis) {
        mVendorEffectDuration = millis;
    }

    /** Set the duration of primitives in fake vibrator hardware. */
    public void setPrimitiveDuration(long millis) {
        mPrimitiveDuration = millis;
    }

    /** Set the maximum number of control points supported in fake vibrator hardware. */
    public void setMaxEnvelopeEffectSize(int limit) {
        mMaxEnvelopeEffectSize = limit;
    }

    /** Set the minimum segment duration in fake vibrator hardware. */
    public void setMinEnvelopeEffectControlPointDurationMillis(int millis) {
        mMinEnvelopeEffectControlPointDurationMillis = millis;
    }

    /** Set the maximum segment duration in fake vibrator hardware. */
    public void setMaxEnvelopeEffectControlPointDurationMillis(int millis) {
        mMaxEnvelopeEffectControlPointDurationMillis = millis;
    }

    /** Return {@code true} if this controller was initialized. */
    public boolean isInitialized() {
        return mConnectCount > 0;
    }

    /** Return the amplitudes set, including zeroes for each time the vibrator was turned off. */
    public synchronized List<Float> getAmplitudes() {
        return new ArrayList<>(mAmplitudes);
    }

    /** Return the braking values passed to the compose PWLE method. */
    public synchronized List<Integer> getBraking() {
        return new ArrayList<>(mBraking);
    }

    /** Return list of {@link VibrationEffectSegment} played by this controller, in order. */
    public synchronized List<VibrationEffectSegment> getEffectSegments() {
        return new ArrayList<>(mEffectSegments);
    }

    /** Return list of {@link VendorEffect} played by this controller, in order. */
    public synchronized List<VendorEffect> getVendorEffects() {
        return new ArrayList<>(mVendorEffects);
    }

    /** Return list of {@link PwlePoint} played by this controller, in order. */
    public synchronized List<PwlePoint> getEffectPwlePoints() {
        return new ArrayList<>(mEffectPwlePoints);
    }

    /** Return list of states set for external control to the fake vibrator hardware. */
    public synchronized List<Boolean> getExternalControlStates() {
        return new ArrayList<>(mExternalControlStates);
    }

    /** Returns the number of times the vibrator was turned off. */
    public synchronized int getOffCount() {
        return mOffCount;
    }

    /** Return the {@link PrebakedSegment} effect enabled with given id, or {@code null}. */
    @Nullable
    public synchronized PrebakedSegment getAlwaysOnEffect(int id) {
        return mEnabledAlwaysOnEffects.get((long) id);
    }

    int vibrate(int durationMs) {
        if (mOnShouldFail) {
            return -1;
        }
        recordEffectSegment(new StepSegment(VibrationEffect.DEFAULT_AMPLITUDE,
                /* frequencyHz= */ 0, durationMs));
        applyLatency(mOnLatency);
        return durationMs;
    }

    int vibrate(int effectId, byte strength) {
        if (mPrebakedShouldFail) {
            return -1;
        }
        if (mSupportedEffects == null || Arrays.binarySearch(mSupportedEffects, effectId) < 0) {
            return 0;
        }
        recordEffectSegment(new PrebakedSegment(effectId, false, strength));
        applyLatency(mOnLatency);
        return (int) EFFECT_DURATION;
    }

    int vibrate(VendorEffect effect) {
        if (mVendorEffectsShouldFail) {
            return -1;
        }
        if ((mCapabilities & IVibrator.CAP_PERFORM_VENDOR_EFFECTS) == 0) {
            return 0;
        }
        recordVendorEffect(effect);
        applyLatency(mOnLatency);
        return (int) mVendorEffectDuration;
    }

    int vibrate(PrimitiveSegment[] primitives) {
        if (mPrimitivesShouldFail) {
            return -1;
        }
        if (mSupportedPrimitives == null || (mCapabilities & IVibrator.CAP_COMPOSE_EFFECTS) == 0) {
            return 0;
        }
        for (PrimitiveSegment primitive : primitives) {
            if (Arrays.binarySearch(mSupportedPrimitives, primitive.getPrimitiveId()) < 0) {
                return 0;
            }
        }
        long duration = 0;
        for (PrimitiveSegment primitive : primitives) {
            duration += mPrimitiveDuration + primitive.getDelay();
            recordEffectSegment(primitive);
        }
        applyLatency(mOnLatency);
        return (int) duration;
    }

    int vibrate(RampSegment[] primitives) {
        if (mPwleV1ShouldFail) {
            return -1;
        }
        if ((mCapabilities & IVibrator.CAP_COMPOSE_PWLE_EFFECTS) == 0) {
            return 0;
        }
        int duration = 0;
        for (RampSegment primitive : primitives) {
            duration += (int) primitive.getDuration();
            recordEffectSegment(primitive);
        }
        applyLatency(mOnLatency);
        return duration;
    }

    int vibrate(PwlePoint[] pwlePoints) {
        if (mPwleV2ShouldFail) {
            return -1;
        }
        if ((mCapabilities & IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2) == 0) {
            return 0;
        }
        int duration = 0;
        for (PwlePoint pwlePoint: pwlePoints) {
            duration += pwlePoint.getTimeMillis();
            recordEffectPwlePoint(pwlePoint);
        }
        applyLatency(mOnLatency);
        return duration;
    }

    private synchronized void recordEffectSegment(VibrationEffectSegment segment) {
        mEffectSegments.add(segment);
    }

    private synchronized void recordVendorEffect(VendorEffect vendorEffect) {
        mVendorEffects.add(vendorEffect);
    }

    private synchronized void recordEffectPwlePoint(PwlePoint pwlePoint) {
        mEffectPwlePoints.add(pwlePoint);
    }

    private synchronized void recordBraking(int braking) {
        mBraking.add(braking);
    }

    private void applyLatency(long latencyMillis) {
        try {
            if (latencyMillis > 0) {
                Thread.sleep(latencyMillis);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void scheduleVibrationCallback(HalVibrator.Callbacks callbacks, int vibratorId,
            long vibrationId, long stepId, long durationMs) {
        mHandler.postDelayed(
                () -> callbacks.onVibrationStepComplete(vibratorId, vibrationId, stepId),
                durationMs + mCompletionCallbackLatency);
    }

    /** Fake {@link VibratorController.NativeWrapper} implementation for testing. */
    private final class FakeNativeWrapper extends VibratorController.NativeWrapper {
        public int vibratorId;
        public HalVibrator.Callbacks listener;

        @Override
        public void init(int vibratorId, HalVibrator.Callbacks listener) {
            mConnectCount++;
            this.vibratorId = vibratorId;
            this.listener = listener;
        }

        @Override
        public long on(long milliseconds, long vibrationId, long stepId) {
            int result = vibrate((int) milliseconds);
            if (result > 0) {
                scheduleCallback(vibrationId, stepId, milliseconds);
            }
            return result;
        }

        @Override
        public void off() {
            mOffCount++;
            applyLatency(mOffLatency);
        }

        @Override
        public void setAmplitude(float amplitude) {
            mAmplitudes.add(amplitude);
            applyLatency(mOnLatency);
        }

        @Override
        public long perform(long effect, long strength, long vibrationId, long stepId) {
            long duration = vibrate((int) effect, (byte) strength);
            if (duration > 0) {
                scheduleCallback(vibrationId, stepId, duration);
            }
            return duration;
        }

        @Override
        public long performVendorEffect(Parcel vendorData, long strength, float scale,
                float adaptiveScale, long vibrationId, long stepId) {
            PersistableBundle bundle = PersistableBundle.CREATOR.createFromParcel(vendorData);
            long duration = vibrate(new VendorEffect(bundle, (int) strength, scale, adaptiveScale));
            if (duration > 0) {
                scheduleCallback(vibrationId, stepId, mVendorEffectDuration);
                // HAL has unknown duration for vendor effects.
                return Long.MAX_VALUE;
            }
            return duration;
        }

        @Override
        public long compose(PrimitiveSegment[] primitives, long vibrationId, long stepId) {
            long duration = vibrate(primitives);
            if (duration > 0) {
                scheduleCallback(vibrationId, stepId, duration);
            }
            return duration;
        }

        @Override
        public long composePwle(RampSegment[] primitives, int braking, long vibrationId,
                long stepId) {
            long duration = vibrate(primitives);
            if (duration > 0) {
                scheduleCallback(vibrationId, stepId, duration);
            }
            return duration;
        }

        @Override
        public long composePwleV2(PwlePoint[] pwlePoints, long vibrationId, long stepId) {
            long duration = vibrate(pwlePoints);
            if (duration > 0) {
                scheduleCallback(vibrationId, stepId, duration);
            }
            return duration;
        }

        @Override
        public void setExternalControl(boolean enabled) {
            if (!mExternalControlShouldFail) {
                mExternalControlStates.add(enabled);
            }
        }

        @Override
        public void alwaysOnEnable(long id, long effect, long strength) {
            PrebakedSegment prebaked = new PrebakedSegment((int) effect, false, (int) strength);
            mEnabledAlwaysOnEffects.put(id, prebaked);
        }

        @Override
        public void alwaysOnDisable(long id) {
            mEnabledAlwaysOnEffects.remove(id);
        }

        @Override
        public boolean getInfo(VibratorInfo.Builder infoBuilder) {
            infoBuilder.setCapabilities(mCapabilities);
            infoBuilder.setSupportedEffects(mSupportedEffects);
            if ((mCapabilities & IVibrator.CAP_COMPOSE_EFFECTS) != 0) {
                if (mSupportedPrimitives != null) {
                    for (int primitive : mSupportedPrimitives) {
                        infoBuilder.setSupportedPrimitive(primitive, (int) mPrimitiveDuration);
                    }
                }
                infoBuilder.setPrimitiveDelayMax(mCompositionDelayMax);
                infoBuilder.setCompositionSizeMax(mCompositionSizeMax);
            }
            if ((mCapabilities & IVibrator.CAP_GET_Q_FACTOR) != 0) {
                infoBuilder.setQFactor(mQFactor);
            }
            float resonantFrequency =
                    ((mCapabilities & IVibrator.CAP_GET_RESONANT_FREQUENCY) != 0)
                            ? mResonantFrequency
                            : Float.NaN;
            if ((mCapabilities & IVibrator.CAP_FREQUENCY_CONTROL) != 0) {
                infoBuilder.setFrequencyProfile(
                        new VibratorInfo.FrequencyProfile(resonantFrequency, mFrequenciesHz,
                                mOutputAccelerationsGs));
                infoBuilder.setFrequencyProfileLegacy(new VibratorInfo.FrequencyProfileLegacy(
                        resonantFrequency, mMinFrequency, mFrequencyResolution, mMaxAmplitudes));
            } else {
                infoBuilder.setFrequencyProfile(
                        new VibratorInfo.FrequencyProfile(resonantFrequency, null, null));
                infoBuilder.setFrequencyProfileLegacy(new VibratorInfo.FrequencyProfileLegacy(
                        resonantFrequency, Float.NaN, Float.NaN, null));
            }
            if ((mCapabilities & IVibrator.CAP_COMPOSE_PWLE_EFFECTS) != 0) {
                infoBuilder.setSupportedBraking(mSupportedBraking);
                infoBuilder.setPwleSizeMax(mPwleSizeMax);
                infoBuilder.setPwlePrimitiveDurationMax(mPwlePrimitiveDurationMax);
            }
            if ((mCapabilities & IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2) != 0) {
                infoBuilder.setMaxEnvelopeEffectSize(mMaxEnvelopeEffectSize);
                infoBuilder.setMinEnvelopeEffectControlPointDurationMillis(
                        mMinEnvelopeEffectControlPointDurationMillis);
                infoBuilder.setMaxEnvelopeEffectControlPointDurationMillis(
                        mMaxEnvelopeEffectControlPointDurationMillis);
            }
            return !mLoadInfoShouldFail;
        }

        private void scheduleCallback(long vibrationId, long stepId, long durationMs) {
            scheduleVibrationCallback(listener, vibratorId, vibrationId, stepId, durationMs);
        }
    }

    /** Provides fake implementation of {@link HalNativeHandler} for testing. */
    public final class FakeHalNativeHandler implements HalNativeHandler {
        private HalVibrator.Callbacks mCallbacks;

        @Override
        public void init(HalVibratorManager.Callbacks unused, HalVibrator.Callbacks cb) {
            mCallbacks = cb;
        }

        @Override
        public boolean triggerSyncedWithCallback(long vibrationId) {
            return false;
        }

        @Override
        public IVibrationSession startSessionWithCallback(long sessionId, int[] vibratorIds) {
            return null;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
                int durationMs) {
            int result = vibrate(durationMs);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, durationMs);
            }
            return result;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
                android.hardware.vibrator.VendorEffect effect) {
            VendorEffect vendorEffect = new VendorEffect(effect.vendorData, effect.strength,
                    effect.scale, effect.vendorScale);
            int result = vibrate(vendorEffect);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, result);
            }
            return result;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId, int effectId,
                int effectStrength) {
            int result = vibrate(effectId, (byte) effectStrength);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, result);
            }
            return result;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
                CompositeEffect[] effects) {
            PrimitiveSegment[] primitives = new PrimitiveSegment[effects.length];
            for (int i = 0; i < primitives.length; i++) {
                primitives[i] = new PrimitiveSegment(effects[i].primitive, effects[i].scale,
                        effects[i].delayMs);
            }
            int result = vibrate(primitives);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, result);
            }
            return result;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
                PrimitivePwle[] effects) {
            RampSegment[] primitives = new RampSegment[effects.length];
            for (int i = 0; i < primitives.length; i++) {
                ActivePwle pwle = effects[i].getActive();
                primitives[i] = new RampSegment(pwle.startAmplitude, pwle.endAmplitude,
                        pwle.startFrequency, pwle.endFrequency, pwle.duration);
            }
            int result = vibrate(primitives);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, result);
            }
            return result;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
                CompositePwleV2 composite) {
            PwlePoint[] points = new PwlePoint[composite.pwlePrimitives.length];
            for (int i = 0; i < points.length; i++) {
                PwleV2Primitive primitive = composite.pwlePrimitives[i];
                points[i] = new PwlePoint(primitive.amplitude, primitive.frequencyHz,
                        primitive.timeMillis);
            }
            int result = vibrate(points);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, result);
            }
            return result;
        }

        private void scheduleCallback(int vibratorId, long vibrationId, long stepId,
                int durationMs) {
            scheduleVibrationCallback(mCallbacks, vibratorId, vibrationId, stepId, durationMs);
        }
    }

    /** Provides fake implementation of {@link IVibrator} for testing. */
    public final class FakeVibrator extends IVibrator.Stub {
        @Override
        public int getCapabilities() throws RemoteException {
            return mCapabilities;
        }

        @Override
        public int[] getSupportedEffects() throws RemoteException {
            return mSupportedEffects;
        }

        @Override
        public void setAmplitude(float amplitude) throws RemoteException {
            mAmplitudes.add(amplitude);
            applyLatency(mOnLatency);
        }

        @Override
        public void setExternalControl(boolean enabled) throws RemoteException {
            if (mExternalControlShouldFail) {
                throw new RemoteException();
            }
            mExternalControlStates.add(enabled);
        }

        @Override
        public int getCompositionDelayMax() throws RemoteException {
            return mCompositionDelayMax;
        }

        @Override
        public int getCompositionSizeMax() throws RemoteException {
            return mCompositionSizeMax;
        }

        @Override
        public int[] getSupportedPrimitives() throws RemoteException {
            return mSupportedPrimitives;
        }

        @Override
        public int getPrimitiveDuration(int primitive) throws RemoteException {
            return (int) mPrimitiveDuration;
        }

        @Override
        public int[] getSupportedAlwaysOnEffects() throws RemoteException {
            return mSupportedEffects;
        }

        @Override
        public void alwaysOnEnable(int id, int effect, byte strength) throws RemoteException {
            PrebakedSegment prebaked = new PrebakedSegment(effect, false, strength);
            mEnabledAlwaysOnEffects.put((long) id, prebaked);
        }

        @Override
        public void alwaysOnDisable(int id) throws RemoteException {
            mEnabledAlwaysOnEffects.remove((long) id);
        }

        @Override
        public float getResonantFrequency() throws RemoteException {
            return mResonantFrequency;
        }

        @Override
        public float getQFactor() throws RemoteException {
            return mQFactor;
        }

        @Override
        public float getFrequencyResolution() throws RemoteException {
            return mFrequencyResolution;
        }

        @Override
        public float getFrequencyMinimum() throws RemoteException {
            return mMinFrequency;
        }

        @Override
        public float[] getBandwidthAmplitudeMap() throws RemoteException {
            return mMaxAmplitudes;
        }

        @Override
        public int getPwlePrimitiveDurationMax() throws RemoteException {
            return mPwlePrimitiveDurationMax;
        }

        @Override
        public int getPwleCompositionSizeMax() throws RemoteException {
            return mPwleSizeMax;
        }

        @Override
        public int[] getSupportedBraking() throws RemoteException {
            return mSupportedBraking;
        }

        @Override
        public List<FrequencyAccelerationMapEntry> getFrequencyToOutputAccelerationMap()
                throws RemoteException {
            List<FrequencyAccelerationMapEntry> entries = new ArrayList<>();
            if (mFrequenciesHz == null || mOutputAccelerationsGs == null) {
                return entries;
            }
            for (int i = 0; i < mFrequenciesHz.length; i++) {
                FrequencyAccelerationMapEntry entry = new FrequencyAccelerationMapEntry();
                entry.frequencyHz = mFrequenciesHz[i];
                entry.maxOutputAccelerationGs = mOutputAccelerationsGs[i];
                entries.add(entry);
            }
            return entries;
        }

        @Override
        public int getPwleV2PrimitiveDurationMaxMillis() throws RemoteException {
            return mMaxEnvelopeEffectControlPointDurationMillis;
        }

        @Override
        public int getPwleV2CompositionSizeMax() throws RemoteException {
            return mMaxEnvelopeEffectSize;
        }

        @Override
        public int getPwleV2PrimitiveDurationMinMillis() throws RemoteException {
            return mMinEnvelopeEffectControlPointDurationMillis;
        }

        @Override
        public void on(int timeoutMs, IVibratorCallback callback) throws RemoteException {
            if (callback != null) {
                throw new IllegalArgumentException("HAL java client should not receive callbacks");
            }
            int result = vibrate(timeoutMs);
            if (result < 0) {
                throw new RemoteException();
            }
            if (result == 0) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public int perform(int effect, byte strength, IVibratorCallback callback)
                throws RemoteException {
            if (callback != null) {
                throw new IllegalArgumentException("HAL java client should not receive callbacks");
            }
            int duration = vibrate(effect, strength);
            if (duration < 0) {
                throw new RemoteException();
            }
            if (duration == 0) {
                throw new UnsupportedOperationException();
            }
            return duration;
        }

        @Override
        public void performVendorEffect(android.hardware.vibrator.VendorEffect vendorEffect,
                IVibratorCallback callback) throws RemoteException {
            throw new UnsupportedOperationException(
                    "HAL java client should not be used to play vendor effects");
        }

        @Override
        public void compose(CompositeEffect[] composite, IVibratorCallback callback)
                throws RemoteException {
            throw new UnsupportedOperationException(
                    "HAL java client should not be used to play primitive compositions");
        }

        @Override
        public void composePwle(PrimitivePwle[] composite, IVibratorCallback callback)
                throws RemoteException {
            throw new UnsupportedOperationException(
                    "HAL java client should not be used to play pwle");
        }

        @Override
        public void composePwleV2(CompositePwleV2 composite, IVibratorCallback callback)
                throws RemoteException {
            throw new UnsupportedOperationException(
                    "HAL java client should not be used to play pwle v2");
        }

        @Override
        public void off() throws RemoteException {
            mOffCount++;
            applyLatency(mOffLatency);
        }

        @Override
        public int getInterfaceVersion() {
            return IVibrator.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IVibrator.HASH;
        }
    }

    /** Provides fake implementation of {@link VintfUtils.VintfSupplier} for testing. */
    public final class FakeVibratorSupplier extends VintfUtils.VintfSupplier<IVibrator> {
        private final IBinder mToken;
        private final IVibrator mVibrator;

        public FakeVibratorSupplier(IVibrator vibrator) {
            mToken = new Binder();
            mVibrator = vibrator;
        }

        @Nullable
        @Override
        IBinder connectToService() {
            mConnectCount++;
            return mToken;
        }

        @NonNull
        @Override
        IVibrator castService(@NonNull IBinder binder) {
            return mVibrator;
        }
    }
}
