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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.vibrator.CompositeEffect;
import android.hardware.vibrator.HapticGeneratorConfig;
import android.hardware.vibrator.OneShotPrimitive;
import android.hardware.vibrator.PwleV2Primitive;
import android.hardware.vibrator.VibrationEffectContent;
import android.media.audio.common.AidlConversion;
import android.os.BadParcelableException;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.ParcelableHolder;
import android.os.RemoteException;
import android.os.vibrator.HapticGeneratorSession;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwleSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Helpers for interacting with VINTF objects. */
class VintfUtils {
    private static final String TAG = "VintfUtils";

    /** {@link Supplier} that takes a VINTF object and throw {@link RemoteException} */
    @FunctionalInterface
    interface VintfGetter<I, R> {
        @Nullable
        R get(I hal) throws RemoteException;
    }

    /** {@link Runnable} that takes a VINTF object and throw {@link RemoteException} */
    @FunctionalInterface
    interface VintfRunnable<I> {
        void run(I hal) throws RemoteException;
    }

    /** Cached {@link Supplier} for remote VINTF objects that resets on dead object. */
    abstract static class VintfSupplier<I> implements Supplier<I>, IBinder.DeathRecipient {
        private static final String TAG = "VintfSupplier";

        @GuardedBy("this")
        private I mInstance = null;

        @Nullable
        abstract IBinder connectToService();

        @NonNull
        abstract I castService(@NonNull IBinder binder);

        @Nullable
        @Override
        public synchronized I get() {
            if (mInstance == null) {
                IBinder binder = connectToService();
                if (binder != null) {
                    mInstance = castService(binder);
                    try {
                        binder.linkToDeath(this, 0);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to register DeathRecipient for " + mInstance);
                    }
                }
            }
            return mInstance;
        }

        @Override
        public synchronized void binderDied() {
            clear();
        }

        public synchronized void clear() {
            mInstance = null;
        }
    }

    /**
     * Runs getter on VINTF object provided by supplier, if any.
     *
     * <p>This automatically clears the cached object in given {@code supplier} if a
     * {@link DeadObjectException} is thrown by the remote method call, so future interactions can
     * load a new instance.
     *
     * @throws RuntimeException if supplier returns null or there is a {@link RemoteException} or
     * {@link RuntimeException} from the remote method call.
     */
    @Nullable
    static <I, T> T get(VintfSupplier<I> supplier, VintfGetter<I, T> getter) {
        I hal = supplier.get();
        if (hal == null) {
            throw new RuntimeException("Missing HAL service");
        }
        try {
            return getter.get(hal);
        } catch (RemoteException e) {
            if (e instanceof DeadObjectException) {
                supplier.clear();
            }
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Same as {@link #get(VintfSupplier, VintfGetter)}, but throws no exception and
     * returns default on error.
     */
    @Nullable
    static <I, T> T getOrDefault(VintfSupplier<I> supplier, VintfGetter<I, T> getter,
            @Nullable T defaultValue, Consumer<Throwable> errorHandler) {
        try {
            return get(supplier, getter);
        } catch (RuntimeException e) {
            errorHandler.accept(e);
        }
        return defaultValue;
    }

    /** Same as {@link #get(VintfSupplier, VintfGetter)}, but throws no exception. */
    static <I, T> void getNoThrow(VintfSupplier<I> supplier, VintfGetter<I, T> getter,
            Consumer<T> resultHandler, Consumer<Throwable> errorHandler) {
        try {
            resultHandler.accept(get(supplier, getter));
        } catch (RuntimeException e) {
            errorHandler.accept(e);
        }
    }

    /**
     * Runs runnable on VINTF object provided by supplier, if any.
     *
     * <p>This automatically clears the cached object in given {@code supplier} if a
     * {@link DeadObjectException} is thrown by the remote method call, so future interactions can
     * load a new instance.
     *
     * @throws RuntimeException if supplier returns null or there is a {@link RemoteException} or
     * {@link RuntimeException} from the remote method call.
     */
    static <I> void run(VintfSupplier<I> supplier, VintfRunnable<I> runnable) {
        I hal = supplier.get();
        if (hal == null) {
            throw new RuntimeException("Missing HAL service");
        }
        try {
            runnable.run(hal);
        } catch (RemoteException e) {
            if (e instanceof DeadObjectException) {
                supplier.clear();
            }
            throw e.rethrowAsRuntimeException();
        }
    }

    /** Same as {@link #run(VintfSupplier, VintfRunnable)}, but throws no exception. */
    static <I> boolean runNoThrow(VintfSupplier<I> supplier, VintfRunnable<I> runnable,
            Consumer<Throwable> errorHandler) {
        try {
            run(supplier, runnable);
            return true;
        } catch (RuntimeException e) {
            errorHandler.accept(e);
        }
        return false;
    }

    /**
     * Converts an array of {@link VibrationEffectSegment} into a list of HAL
     * {@link VibrationEffectContent} variants.
     *
     * <p>This supports converting {@link PrebakedSegment}, {@link PrimitiveSegment},
     * {@link StepSegment}, and {@link PwleSegment}.
     *
     * @param segments The vibration effect segments to convert.
     * @return A list of HAL VibrationEffectContent variants, or null if the conversion fails due to
     *         an unsupported segment type.
     */
    @Nullable
    static VibrationEffectContent[] toHalVibrationEffectContent(
            @NonNull VibrationEffectSegment[] segments) {

        ArrayList<VibrationEffectContent> effects = new ArrayList<>();

        for (int i = 0; i < segments.length; i++) {
            VibrationEffectSegment segment = segments[i];
            switch (segment) {
                case PrebakedSegment prebaked -> {
                    android.hardware.vibrator.PredefinedEffect predefinedEffect =
                            new android.hardware.vibrator.PredefinedEffect();
                    predefinedEffect.effect = prebaked.getEffectId();
                    predefinedEffect.strength = (byte) prebaked.getEffectStrength();
                    effects.add(VibrationEffectContent.predefined(predefinedEffect));
                }
                case PrimitiveSegment primitive -> {
                    CompositeEffect compositeEffect = new CompositeEffect();
                    compositeEffect.primitive = primitive.getPrimitiveId();
                    compositeEffect.scale = primitive.getScale();
                    compositeEffect.delayMs = primitive.getDelay();
                    effects.add(VibrationEffectContent.composite(compositeEffect));
                }
                case PwleSegment pwleSegment -> {
                    PwleV2Primitive pwle = new PwleV2Primitive();

                    if (i == 0 || !(segments[i - 1] instanceof PwleSegment)) {
                        pwle.amplitude = pwleSegment.getStartAmplitude();
                        pwle.frequencyHz = pwleSegment.getStartFrequencyHz();
                        pwle.timeMillis = 0;
                        effects.add(VibrationEffectContent.pwleV2Primitive(pwle));
                    }

                    pwle.amplitude = pwleSegment.getEndAmplitude();
                    pwle.frequencyHz = pwleSegment.getEndFrequencyHz();
                    pwle.timeMillis = (int) pwleSegment.getDuration();
                    effects.add(VibrationEffectContent.pwleV2Primitive(pwle));
                }
                case StepSegment stepSegment -> {
                    OneShotPrimitive oneShotPrimitive = new OneShotPrimitive();
                    oneShotPrimitive.amplitude = stepSegment.getAmplitude();
                    oneShotPrimitive.timeMillis = (int) stepSegment.getDuration();
                    effects.add(VibrationEffectContent.oneShotPrimitive(
                            oneShotPrimitive));
                }
                default -> {
                    Slog.w(TAG, "Unsupported segment type for haptic generator stream: "
                            + segment.getClass().getSimpleName());
                    return null; // Fail the entire conversion if one segment is unsupported
                }
            }
        }

        return effects.toArray(new VibrationEffectContent[0]);
    }

    /**
     * Converts a {@link HapticGeneratorSession.Config} into a {@link HapticGeneratorConfig}.
     *
     * @param config The session config.
     * @return A converted HAL HapticGeneratorConfig.
     * @throws IllegalArgumentException on conversion errors.
     * @throws BadParcelableException if vendor parcelable is invalid.
     */
    @NonNull
    static HapticGeneratorConfig toHalHapticGeneratorConfig(
            @NonNull HapticGeneratorSession.Config config) {
        HapticGeneratorConfig result = new HapticGeneratorConfig();
        result.audioFormat = AidlConversion.api2aidl_AudioFormat_AudioConfigBase(
                config.getAudioFormat(), /* isInput= */ false);
        ParcelableHolder extension = config.getVendorExtension();
        if (extension != null) {
            // This will fail if parcelable stability < Parcelable.PARCELABLE_STABILITY_VINTF
            result.vendorExtension.setParcelable(extension.getParcelable(Parcelable.class));
        }
        return result;
    }

    // Non-instantiable helper class.
    private VintfUtils() {
    }
}
