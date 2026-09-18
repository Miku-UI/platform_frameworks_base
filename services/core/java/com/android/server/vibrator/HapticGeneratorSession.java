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

import android.Manifest;
import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.hardware.vibrator.HapticGeneratorConfig;
import android.hardware.vibrator.VibrationEffectContent;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.vibrator.IHapticChannelStream;
import android.os.vibrator.IHapticGeneratorSession;
import android.os.vibrator.IHapticGeneratorSessionCallback;
import android.os.vibrator.VibrationConfig;
import android.os.vibrator.VibrationEffectSegment;
import android.util.Slog;

import com.android.internal.util.Preconditions;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A haptic generator session that can generate haptic PCM data from
 * {@link android.os.VibrationEffect}s. It handles the creation of individual conversion streams
 * ({@link HapticGeneratorChannelStream}).
 */
public class HapticGeneratorSession extends IHapticGeneratorSession.Stub implements
        IBinder.DeathRecipient {
    private static final String TAG = "HapticGeneratorSession";

    // Generates globally unique session IDs.
    private static final AtomicLong sNextSessionId = new AtomicLong(0);

    /** Calls into VibratorManager to gain access to the haptic generator HAL controls. */
    public interface VibratorManagerHooks {
        /** Starts a new haptic generator session */
        boolean startHapticGeneratorSession(long sessionId, int vibratorId,
                HapticGeneratorConfig config);

        /** Closes a haptic generator session */
        boolean closeHapticGeneratorSession(long sessionId);

        /** Starts a new haptic generator stream */
        boolean startHapticGeneratorStream(long sessionId, int vibratorId,
                VibrationEffectContent[] segments);

        /** Reads from a haptic generator stream */
        int readHapticGeneratorStream(long sessionId, int vibratorId, byte[] buffer);

        /** Stops a haptic generator stream */
        boolean stopHapticGeneratorStream(long sessionId, int vibratorId);
    }

    private final VibratorManagerHooks mManagerHooks;
    private final VibrationConfig mVibrationConfig;
    private final DeviceAdapter mDeviceAdapter;
    private final IHapticGeneratorSessionCallback mCallback;
    private final int mVibratorId;
    private final long mSessionId;

    /** The ID of the current active stream. */
    private final AtomicLong mCurrentStreamId = new AtomicLong(0);

    /** Returns a new, globally unique session ID. */
    static long getNextSessionId() {
        return sNextSessionId.getAndIncrement();
    }

    /**
     * Creates a new haptic generator session implementation.
     *
     * @param managerHooks  The controller to delegate HAL calls to.
     * @param sessionId     A unique ID for this session.
     * @param vibratorId    The ID of the vibrator to be used for this session.
     * @param deviceAdapter Adapter to modify vibration effects for the specific device.
     * @param callback      The callback to notify the client about the session's status.
     */
    HapticGeneratorSession(
            @NonNull VibratorManagerHooks managerHooks,
            long sessionId,
            int vibratorId,
            @NonNull VibrationConfig vibrationConfig,
            @NonNull DeviceAdapter deviceAdapter,
            @NonNull IHapticGeneratorSessionCallback callback) {
        mManagerHooks = managerHooks;
        mSessionId = sessionId;
        mVibrationConfig = vibrationConfig;
        mDeviceAdapter = deviceAdapter;
        mVibratorId = vibratorId;
        mCallback = callback;
    }

    @Override
    @EnforcePermission(Manifest.permission.USE_VIBRATOR_HAPTIC_GENERATOR)
    public IHapticChannelStream generateHapticChannelStream(
            @NonNull VibrationEffect effect) {
        generateHapticChannelStream_enforcePermission();

        Preconditions.checkArgument(effect.getDuration() < Long.MAX_VALUE,
                "Can't generate haptic channel stream for an indefinitely repeating effect.");
        effect.validate();
        VibrationEffect resolvedEffect =
                effect.resolve(mVibrationConfig.getDefaultVibrationAmplitude());
        VibrationEffect adaptedEffect = mDeviceAdapter.adaptToVibrator(mVibratorId, resolvedEffect);
        Preconditions.checkArgument(adaptedEffect != null,
                "Haptic channel stream can only be generated for supported effects.");

        //TODO(437846004) create a pathway for VendorEffects
        if (!(adaptedEffect instanceof VibrationEffect.Composed composed)) {
            throw new IllegalArgumentException(
                    "Haptic channel stream can only be generated for Composed effects.");
        }

        VibrationEffectContent[] segments = VintfUtils.toHalVibrationEffectContent(
                composed.getSegments().toArray(new VibrationEffectSegment[0]));

        if (segments == null) {
            throw new IllegalArgumentException(
                    "Haptic channel stream can only be generated for supported effects.");
        }

        if (!mManagerHooks.startHapticGeneratorStream(mSessionId, mVibratorId, segments)) {
            throw new IllegalStateException(
                    "Failed to create haptic generator stream for session " + mSessionId);
        }

        return new HapticGeneratorChannelStream(this, mCurrentStreamId.incrementAndGet());
    }

    @Override
    @EnforcePermission(Manifest.permission.USE_VIBRATOR_HAPTIC_GENERATOR)
    public void close() {
        close_enforcePermission();
        closeInternal();
        unlinkToDeath();
    }

    int readHapticGeneratorStream(long streamId, byte[] buffer) {
        if (mCurrentStreamId.get() != streamId) {
            return IHapticChannelStream.READ_STATUS_ERROR_CLOSED;
        }

        return mManagerHooks.readHapticGeneratorStream(mSessionId, mVibratorId, buffer);
    }

    boolean stopHapticGeneratorStream(long streamId) {
        if (mCurrentStreamId.get() != streamId) {
            return false;
        }

        // Reset current stream Id, so subsequent read requests to this stream will fail.
        mCurrentStreamId.set(0);
        return mManagerHooks.stopHapticGeneratorStream(mSessionId, mVibratorId);
    }

    boolean linkToDeath() {
        try {
            mCallback.asBinder().linkToDeath(this, 0);
        } catch (Exception e) {
            Slog.e(TAG, "Error linking session to token death", e);
            return false;
        }

        return true;
    }

    /**
     * Unlinks this session from the client process's death.
     */
    void unlinkToDeath() {
        try {
            mCallback.asBinder().unlinkToDeath(this, 0);
        } catch (Exception e) {
            Slog.wtf(TAG, "Failed to unlink session to token death", e);
        }
    }

    @Override
    public void binderDied() {
        Slog.d(TAG, "Session binder died, closing session " + mSessionId);
        closeInternal();
    }

    private void closeInternal() {
        Slog.d(TAG, "Closing haptic generator session " + mSessionId);
        mManagerHooks.closeHapticGeneratorSession(mSessionId);
    }
}
