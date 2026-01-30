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
import android.hardware.vibrator.ActivePwle;
import android.hardware.vibrator.CompositeEffect;
import android.hardware.vibrator.CompositePwleV2;
import android.hardware.vibrator.IVibrationSession;
import android.hardware.vibrator.IVibrator;
import android.hardware.vibrator.IVibratorCallback;
import android.hardware.vibrator.IVibratorManager;
import android.hardware.vibrator.PrimitivePwle;
import android.hardware.vibrator.PwleV2Primitive;
import android.hardware.vibrator.VibrationSessionConfig;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.VibrationEffect;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.os.vibrator.RampSegment;

import com.android.server.vibrator.VintfHalVibratorManager.DefaultHalVibratorManager;
import com.android.server.vibrator.VintfHalVibratorManager.LegacyHalVibratorManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides real {@link HalVibratorManager} implementations for testing, backed with fake and
 * configurable hardware capabilities.
 */
public class HalVibratorManagerHelper {
    private final Map<Integer, HalVibratorHelper> mVibratorHelpers = new HashMap<>();
    private final Handler mHandler;

    private FakeVibrationSession mLastSession;
    private IVibratorCallback mLastTriggerSyncedCallback;

    private int mConnectCount;
    private int mPrepareSyncedCount;
    private int mTriggerSyncedCount;
    private int mCancelSyncedCount;
    private int mStartSessionCount;
    private int mEndSessionCount;
    private int mAbortSessionCount;
    private int mClearSessionsCount;

    private long mCapabilities;
    private int[] mVibratorIds;
    private long mSessionEndDelayMs = Long.MAX_VALUE;
    private boolean mPrepareSyncedShouldFail = false;
    private boolean mTriggerSyncedShouldFail = false;
    private boolean mStartSessionShouldFail = false;

    public HalVibratorManagerHelper(Looper looper) {
        mHandler = new Handler(looper);
    }

    /** Create new {@link VibratorManagerService.NativeHalVibratorManager} for testing. */
    public VibratorManagerService.NativeHalVibratorManager newNativeHalVibratorManager() {
        return new VibratorManagerService.NativeHalVibratorManager(new FakeNativeWrapper());
    }

    /** Create new {@link DefaultHalVibratorManager} for testing. */
    public DefaultHalVibratorManager newDefaultVibratorManager() {
        HalNativeHandler nativeHandler = new FakeHalNativeHandler();
        return new DefaultHalVibratorManager(
                new FakeVibratorManagerSupplier(new FakeVibratorManager()),
                nativeHandler,
                id -> mVibratorHelpers.get(id).newDefaultVibrator(id, nativeHandler));
    }

    /** Create new {@link LegacyHalVibratorManager} for testing. */
    public LegacyHalVibratorManager newLegacyVibratorManager() {
        int vibratorId = VintfHalVibratorManager.DEFAULT_VIBRATOR_ID;
        setVibratorIds(new int[] { vibratorId });
        HalNativeHandler nativeHandler = new FakeHalNativeHandler();
        return new LegacyHalVibratorManager(mVibratorHelpers.get(vibratorId).newDefaultVibrator(
                vibratorId, nativeHandler), nativeHandler);
    }

    /** Return the helper class for given vibrator, or null if ID not found. */
    public HalVibratorHelper getVibratorHelper(int vibratorId) {
        return mVibratorHelpers.get(vibratorId);
    }

    public void setCapabilities(long... capabilities) {
        mCapabilities = Arrays.stream(capabilities).reduce(0, (a, b) -> a | b);
    }

    public void setVibratorIds(int[] vibratorIds) {
        mVibratorIds = vibratorIds;
        mVibratorHelpers.clear();
        if (vibratorIds != null) {
            for (int id : vibratorIds) {
                mVibratorHelpers.put(id, new HalVibratorHelper(mHandler.getLooper()));
            }
        }
    }

    public void setSessionEndDelayMs(long sessionEndDelayMs) {
        mSessionEndDelayMs = sessionEndDelayMs;
    }

    /** Make all prepare synced calls fail. */
    public void setPrepareSyncedToFail() {
        mPrepareSyncedShouldFail = true;
    }

    /** Make all trigger synced calls fail. */
    public void setTriggerSyncedToFail() {
        mTriggerSyncedShouldFail = true;
    }

    /** Make all start session calls fail. */
    public void setStartSessionToFail() {
        mStartSessionShouldFail = true;
    }

    /** Trigger session callback for last synced vibration triggered. */
    public void endLastSyncedVibration() {
        if (mLastTriggerSyncedCallback != null) {
            mHandler.post(() -> {
                try {
                    mLastTriggerSyncedCallback.onComplete();
                } catch (RemoteException e) {
                    e.rethrowAsRuntimeException();
                }
            });
        }
    }

    /** Trigger session callback for last started session. */
    public void endLastSessionAbruptly() {
        if (mLastSession != null) {
            mHandler.post(mLastSession::onComplete);
        }
    }

    public int getConnectCount() {
        return mConnectCount;
    }

    public int getPrepareSyncedCount() {
        return mPrepareSyncedCount;
    }

    public int getTriggerSyncedCount() {
        return mTriggerSyncedCount;
    }

    public int getCancelSyncedCount() {
        return mCancelSyncedCount;
    }

    public int getStartSessionCount() {
        return mStartSessionCount;
    }

    public int getEndSessionCount() {
        return mEndSessionCount;
    }

    public int getAbortSessionCount() {
        return mAbortSessionCount;
    }

    public int getClearSessionsCount() {
        return mClearSessionsCount;
    }

    private boolean hasCapability(long capability) {
        return (mCapabilities & capability) == capability;
    }

    private boolean areVibratorIdsValid(int[] ids) {
        if (mVibratorIds == null) {
            return false;
        }
        List<Integer> vibratorIds = Arrays.stream(mVibratorIds).boxed().toList();
        long validIdCount = Arrays.stream(ids).filter(vibratorIds::contains).count();
        return validIdCount > 0 && validIdCount == ids.length;
    }

    /** Provides fake implementation of {@link VibratorManagerService.NativeWrapper} for testing. */
    public final class FakeNativeWrapper extends VibratorManagerService.NativeWrapper {
        private HalVibratorManager.Callbacks mCallbacks;

        @Override
        public HalVibrator createVibrator(int vibratorId) {
            return mVibratorHelpers.get(vibratorId).newVibratorController(vibratorId);
        }

        @Override
        public void init(HalVibratorManager.Callbacks callback) {
            mConnectCount++;
            mCallbacks = callback;
        }

        @Override
        public long getCapabilities() {
            return mCapabilities;
        }

        @Override
        public int[] getVibratorIds() {
            return mVibratorIds;
        }

        @Override
        public boolean prepareSynced(int[] vibratorIds) {
            if (mPrepareSyncedShouldFail) {
                return false;
            }
            mPrepareSyncedCount++;
            return hasCapability(IVibratorManager.CAP_SYNC) && areVibratorIdsValid(vibratorIds);
        }

        @Override
        public boolean triggerSynced(long vibrationId) {
            if (mTriggerSyncedShouldFail) {
                return false;
            }
            mTriggerSyncedCount++;
            if (hasCapability(IVibratorManager.CAP_SYNC)) {
                mLastTriggerSyncedCallback = new FakeVibratorCallback(
                        () -> mCallbacks.onSyncedVibrationComplete(vibrationId));
                return true;
            }
            return false;
        }

        @Override
        public void cancelSynced() {
            mCancelSyncedCount++;
        }

        @Override
        public boolean startSession(long sessionId, int[] vibratorIds) {
            if (mStartSessionShouldFail) {
                return false;
            }
            mStartSessionCount++;
            if (hasCapability(IVibratorManager.CAP_START_SESSIONS)
                    && areVibratorIdsValid(vibratorIds)) {
                mLastSession = new FakeVibrationSession(
                        () -> mCallbacks.onVibrationSessionComplete(sessionId));
                return true;
            }
            return false;
        }

        @Override
        public void endSession(long sessionId, boolean shouldAbort) {
            FakeVibrationSession session = mLastSession;
            if (session != null) {
                if (shouldAbort) {
                    session.abort();
                } else {
                    session.close();
                }
            } else {
                if (shouldAbort) {
                    mAbortSessionCount++;
                } else {
                    mEndSessionCount++;
                }
            }
        }

        @Override
        public void clearSessions() {
            mClearSessionsCount++;
            endLastSessionAbruptly();
        }
    }

    /** Provides fake implementation of {@link HalNativeHandler} for testing. */
    public final class FakeHalNativeHandler implements HalNativeHandler {
        private HalVibratorManager.Callbacks mManagerCallbacks;
        private HalVibrator.Callbacks mVibratorCallbacks;

        @Override
        public void init(@NonNull HalVibratorManager.Callbacks managerCallbacks,
                @NonNull HalVibrator.Callbacks vibratorCallbacks) {
            mManagerCallbacks = managerCallbacks;
            mVibratorCallbacks = vibratorCallbacks;
        }

        @Override
        public boolean triggerSyncedWithCallback(long vibrationId) {
            if (mTriggerSyncedShouldFail) {
                return false;
            }
            mTriggerSyncedCount++;
            if (hasCapability(IVibratorManager.CAP_SYNC)) {
                mLastTriggerSyncedCallback = new FakeVibratorCallback(
                        () -> mManagerCallbacks.onSyncedVibrationComplete(vibrationId));
                return true;
            }
            return false;
        }

        @Override
        public IVibrationSession startSessionWithCallback(long sessionId, int[] vibratorIds) {
            if (mStartSessionShouldFail) {
                return null;
            }
            mStartSessionCount++;
            if (hasCapability(IVibratorManager.CAP_START_SESSIONS)
                    && areVibratorIdsValid(vibratorIds)) {
                mLastSession = new FakeVibrationSession(
                        () -> mManagerCallbacks.onVibrationSessionComplete(sessionId));
                return mLastSession;
            }
            return null;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
                int durationMs) {
            int result = mVibratorHelpers.get(vibratorId).vibrate(durationMs);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, durationMs);
            }
            return result;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId,
                android.hardware.vibrator.VendorEffect effect) {
            VibrationEffect.VendorEffect vendorEffect = new VibrationEffect.VendorEffect(
                    effect.vendorData, effect.strength, effect.scale, effect.vendorScale);
            int result = mVibratorHelpers.get(vibratorId).vibrate(vendorEffect);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, result);
            }
            return result;
        }

        @Override
        public int vibrateWithCallback(int vibratorId, long vibrationId, long stepId, int effectId,
                int effectStrength) {
            int result = mVibratorHelpers.get(vibratorId).vibrate(effectId, (byte) effectStrength);
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
            int result = mVibratorHelpers.get(vibratorId).vibrate(primitives);
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
            int result = mVibratorHelpers.get(vibratorId).vibrate(primitives);
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
            int result = mVibratorHelpers.get(vibratorId).vibrate(points);
            if (result > 0) {
                scheduleCallback(vibratorId, vibrationId, stepId, result);
            }
            return result;
        }

        private void scheduleCallback(int vibratorId, long vibrationId, long stepId,
                int durationMs) {
            mVibratorHelpers.get(vibratorId).scheduleVibrationCallback(mVibratorCallbacks,
                    vibratorId, vibrationId, stepId, durationMs);
        }
    }

    /** Provides fake implementation of {@link IVibratorManager} for testing. */
    public final class FakeVibratorManager extends IVibratorManager.Stub {
        @Override
        public int getCapabilities() throws RemoteException {
            return (int) mCapabilities;
        }

        @Override
        public int[] getVibratorIds() throws RemoteException {
            return mVibratorIds;
        }

        @Override
        public IVibrator getVibrator(int vibratorId) throws RemoteException {
            return null;
        }

        @Override
        public void prepareSynced(int[] vibratorIds) throws RemoteException {
            if (mPrepareSyncedShouldFail) {
                throw new RemoteException();
            }
            mPrepareSyncedCount++;
            if (!hasCapability(IVibratorManager.CAP_SYNC)) {
                throw new UnsupportedOperationException();
            }
            if (!areVibratorIdsValid(vibratorIds)) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public void triggerSynced(IVibratorCallback callback) throws RemoteException {
            if (callback != null) {
                throw new IllegalArgumentException("HAL java client should not receive callbacks");
            }
            if (mTriggerSyncedShouldFail) {
                throw new RemoteException();
            }
            mTriggerSyncedCount++;
            if (!hasCapability(IVibratorManager.CAP_SYNC)) {
                throw new UnsupportedOperationException();
            }
            mLastTriggerSyncedCallback = callback;
        }

        @Override
        public void cancelSynced() throws RemoteException {
            mCancelSyncedCount++;
            if (!hasCapability(IVibratorManager.CAP_SYNC)) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public IVibrationSession startSession(int[] vibratorIds, VibrationSessionConfig config,
                IVibratorCallback callback) throws RemoteException {
            throw new UnsupportedOperationException(
                    "HAL java client should not be used to start sessions");
        }

        @Override
        public void clearSessions() throws RemoteException {
            mClearSessionsCount++;
            if (!hasCapability(IVibratorManager.CAP_START_SESSIONS)) {
                throw new UnsupportedOperationException();
            }
            endLastSessionAbruptly();
        }

        @Override
        public int getInterfaceVersion() {
            return IVibratorManager.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IVibratorManager.HASH;
        }
    }

    /** Provides fake implementation of {@link IVibrationSession} for testing. */
    public final class FakeVibrationSession extends IVibrationSession.Stub {
        private final IBinder mToken;
        private final IVibratorCallback mCallback;

        public FakeVibrationSession(Runnable callback) {
            mToken = new Binder();
            mCallback = new FakeVibratorCallback(callback);
        }

        @Override
        public IBinder asBinder() {
            return mToken;
        }

        @Override
        public void close() {
            mEndSessionCount++;
            mHandler.postDelayed(this::onComplete, mSessionEndDelayMs);
        }

        @Override
        public void abort() {
            mAbortSessionCount++;
            mHandler.post(this::onComplete);
        }

        @Override
        public int getInterfaceVersion() {
            return IVibrationSession.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return IVibrationSession.HASH;
        }

        private void onComplete() {
            try {
                mCallback.onComplete();
            } catch (RemoteException e) {
                e.rethrowAsRuntimeException();
            }
        }
    }

    /** Provides fake implementation of {@link IVibratorCallback} for testing. */
    public final class FakeVibratorCallback extends IVibratorCallback.Stub {
        private final Runnable mRunnable;

        public FakeVibratorCallback(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void onComplete() {
            mHandler.post(mRunnable);
        }

        @Override
        public int getInterfaceVersion() throws RemoteException {
            return IVibratorCallback.VERSION;
        }

        @Override
        public String getInterfaceHash() throws RemoteException {
            return IVibratorCallback.HASH;
        }
    }

    /** Provides fake implementation of {@link VintfUtils.VintfSupplier} for testing. */
    public final class FakeVibratorManagerSupplier extends
            VintfUtils.VintfSupplier<IVibratorManager> {
        private final IBinder mToken;
        private final IVibratorManager mManager;

        public FakeVibratorManagerSupplier(IVibratorManager manager) {
            mToken = new Binder();
            mManager = manager;
        }

        @Nullable
        @Override
        IBinder connectToService() {
            mConnectCount++;
            return mToken;
        }

        @NonNull
        @Override
        IVibratorManager castService(@NonNull IBinder binder) {
            return mManager;
        }
    }
}
