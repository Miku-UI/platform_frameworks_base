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

package com.android.server.signalcollector;

import static android.app.ActivityManager.PROCESS_STATE_UNKNOWN;
import static android.app.ActivityManager.UID_OBSERVER_GONE;
import static android.app.ActivityManager.UID_OBSERVER_PROCSTATE;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityManager.ProcessState;
import android.app.IActivityManager;
import android.app.UidObserver;
import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.os.profiling.anomaly.AnomalyDetectorManagerLocal;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamConfig;
import com.android.os.profiling.anomaly.collector.binder.BinderSpamData;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.signalcollector.binder.BinderSpamSignalCollector;

/**
 * Service for managing signal collectors and tracking process state for anomaly
 * detection.
 */
public final class SignalCollectorService extends SystemService {
    private static final String TAG = "SignalCollectorService";

    private final Injector mInjector;
    /** A map of uid to its {@link ProcessState}. */
    @GuardedBy("mUidProcessState")
    private final SparseIntArray mUidProcessState = new SparseIntArray();
    private final SignalCollectorManagerInternalImpl mInternal =
            new SignalCollectorManagerInternalImpl();

    @Nullable
    private BinderSpamSignalCollector mBinderSpamSignalCollector;
    public SignalCollectorService(Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting
    SignalCollectorService(Context context, Injector injector) {
        super(context);
        mInjector = injector;
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "onStart()");
        registerUidObserver();
        initSignalCollectors();
        publishLocalService(SignalCollectorManagerInternal.class, getInternal());
    }

    private final UidObserver mUidObserver = new UidObserver() {
        @Override
        public void onUidGone(int uid, boolean disabled) {
            synchronized (mUidProcessState) {
                mUidProcessState.delete(uid);
            }
        }

        @Override
        public void onUidStateChanged(
                int uid, int processState, long procStateSeq, int capability) {
            synchronized (mUidProcessState) {
                mUidProcessState.put(uid, processState);
            }
        }
    };

    private void registerUidObserver() {
        try {
            mInjector.getActivityManager().registerUidObserver(
                    mUidObserver,
                    UID_OBSERVER_PROCSTATE | UID_OBSERVER_GONE,
                    PROCESS_STATE_UNKNOWN,
                    /* caller */ null);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to register UidObserver", e);
        }
    }

    private void initSignalCollectors() {
        AnomalyDetectorManagerLocal anomalyDetectorManagerLocal =
                mInjector.getAnomalyDetectorManagerLocal();
        if (anomalyDetectorManagerLocal == null) {
            return;
        }

        Slog.i(TAG, "Registering binder spam signal collector");
        mBinderSpamSignalCollector = new BinderSpamSignalCollector();
        anomalyDetectorManagerLocal.registerSignalCollector(
                BinderSpamConfig.class, BinderSpamData.class, mBinderSpamSignalCollector);
    }

    /**
     * Get the {@link ProcessState} of the given uid.
     */
    @ProcessState
    public int getProcessState(int uid) {
        synchronized (mUidProcessState) {
            return mUidProcessState.get(uid, PROCESS_STATE_UNKNOWN);
        }
    }

    @VisibleForTesting
    SignalCollectorManagerInternal getInternal() {
        return mInternal;
    }

    private final class SignalCollectorManagerInternalImpl extends SignalCollectorManagerInternal {
        @Override
        @Nullable
        public BinderSpamSignalCollector getBinderSpamSignalCollector() {
            return mBinderSpamSignalCollector;
        }
    }

    @VisibleForTesting
    public static class Injector {
        /** Get the IActivityManager. */
        public IActivityManager getActivityManager() {
            return ActivityManager.getService();
        }

        /** Get the AnomalyDetectorManagerLocal. */
        @Nullable
        public AnomalyDetectorManagerLocal getAnomalyDetectorManagerLocal() {
            try {
                return LocalManagerRegistry.getManagerOrThrow(AnomalyDetectorManagerLocal.class);
            } catch (LocalManagerRegistry.ManagerNotFoundException e) {
                Slog.e(TAG, "AnomalyDetectorManagerLocal is not available!", e);
                return null;
            }
        }
    }
}
