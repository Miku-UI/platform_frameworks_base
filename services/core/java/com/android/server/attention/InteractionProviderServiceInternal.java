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
package com.android.server.attention;

import static android.attention.AttentionManager.INTERACTION_TYPE_GESTURE;
import static android.attention.AttentionManager.INTERACTION_TYPE_HOVER;
import static android.attention.AttentionManager.INTERACTION_TYPE_KEY;
import static android.attention.AttentionManager.INTERACTION_TYPE_NONE;

import android.annotation.DurationMillisLong;
import android.annotation.NonNull;
import android.annotation.UptimeMillisLong;
import android.attention.AttentionManager;
import android.attention.IInteractionListener;
import android.attention.InteractionState;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.SparseArray;

import androidx.annotation.Keep;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Keep // Used from JNI code.
public class InteractionProviderServiceInternal extends InteractionProviderInternal {
    private final Object mServiceLock = new Object();

    @GuardedBy("mInteractionProviders")
    private final List<InteractionProvider> mInteractionProviders = new ArrayList<>();

    @GuardedBy("mPidToInteractionListeners")
    private final SparseArray<InteractionListenerRecord> mPidToInteractionListeners =
            new SparseArray<>();

    // Handler for the service runnable.
    private final Handler mHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));

    // Service will periodically check sources only if there is at least one subscriber.
    @GuardedBy("mServiceLock")
    private boolean mIsServiceActive = false;

    @GuardedBy("mServiceLock")
    private final AttentionServicePeriodicRunnable mServiceRunnable =
            new AttentionServicePeriodicRunnable();

    @Override
    public boolean registerInteractionProvider(@NonNull InteractionProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        synchronized (mInteractionProviders) {
            return mInteractionProviders.add(provider);
        }
    }

    @Override
    public boolean unregisterInteractionProvider(@NonNull InteractionProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        synchronized (mInteractionProviders) {
            return mInteractionProviders.remove(provider);
        }
    }

    /**
     * Internal implementation for the set interaction listener API.
     */
    public void setListener(int callingPid,
            @AttentionManager.InteractionType int interactionTypes,
            @DurationMillisLong long debounceTimeMillis,
            IInteractionListener listener) {
        boolean shouldResumeService = false;
        synchronized (mPidToInteractionListeners) {
            if (mPidToInteractionListeners.contains(callingPid)) {
                throw new IllegalArgumentException(
                        "The calling process has already registered a listener.");
            }
            InteractionListenerRecord listenerRecord = new InteractionListenerRecord(callingPid,
                    interactionTypes, debounceTimeMillis, listener);
            try {
                IBinder binder = listener.asBinder();
                binder.linkToDeath(listenerRecord, 0);
            } catch (RemoteException ex) {
                // give up
                throw new RuntimeException(ex);
            }
            mPidToInteractionListeners.put(callingPid, listenerRecord);
            shouldResumeService = mPidToInteractionListeners.size() == 1;
        }
        if (shouldResumeService) {
            resumeService();
        }
    }

    /**
     * Internal implementation for the clear interaction listener API.
     */
    public void clearListener(int callingPid) {
        boolean shouldPauseService = false;
        synchronized (mPidToInteractionListeners) {
            if (!mPidToInteractionListeners.contains(callingPid)) {
                throw new IllegalArgumentException(
                        "The calling process has not registered listener.");
            }
            mPidToInteractionListeners.remove(callingPid);
            shouldPauseService = mPidToInteractionListeners.size() == 0;
        }
        if (shouldPauseService) {
            pauseService();
        }
    }

    private void resumeService() {
        synchronized (mServiceLock) {
            if (mIsServiceActive) {
                return;
            }
            mIsServiceActive = true;
            mHandler.post(mServiceRunnable);
        }
    }

    private void pauseService() {
        synchronized (mServiceLock) {
            pauseServiceLocked();
        }
    }

    private void pauseServiceLocked() {
        if (!mIsServiceActive) {
            return;
        }
        mHandler.removeCallbacks(mServiceRunnable);
        mIsServiceActive = false;
    }

    private static final class InteractionSession {
        @UptimeMillisLong
        private final long mSessionStartMillis;
        @UptimeMillisLong
        private long mLastInteractionMillis;

        InteractionSession(long interactionTimeMillis) {
            this.mSessionStartMillis = interactionTimeMillis;
            this.mLastInteractionMillis = interactionTimeMillis;
        }
    }

    private final class InteractionListenerRecord implements IBinder.DeathRecipient {
        private final int mPid;
        @AttentionManager.InteractionType
        private final int mInteractionTypes;
        @DurationMillisLong
        private final long mDebounceTimeMills;
        private final IInteractionListener mListener;
        private final SparseArray<InteractionSession> mInteractionSessions = new SparseArray<>();
        @UptimeMillisLong
        private final long mRegistrationTime = SystemClock.uptimeMillis();

        InteractionListenerRecord(int pid, @AttentionManager.InteractionType int interactionTypes,
                @DurationMillisLong long debounceTimeMills,
                IInteractionListener listener) {
            this.mPid = pid;
            this.mInteractionTypes = interactionTypes;
            this.mDebounceTimeMills = debounceTimeMills;
            this.mListener = listener;
        }

        @Override
        public void binderDied() {
            synchronized (mPidToInteractionListeners) {
                mPidToInteractionListeners.remove(mPid);
            }
        }
    }

    private final class AttentionServicePeriodicRunnable implements Runnable {
        private static final long POLL_DELAY_MILLIS = 100;
        private static final long INACTIVITY_DELAY_MILLIS = 3000;

        // latest interaction time by interaction-type.
        SparseArray<Long> mLatestInteractions = new SparseArray<>();

        @Override
        public void run() {
            boolean isDeviceIdle = fetchInteractionData();
            boolean hasActiveSessions = notifyListeners();
            boolean pauseService = false;
            synchronized (mServiceLock) {
                if (mIsServiceActive) {
                    // Schedule next run
                    pauseService = isDeviceIdle && (!hasActiveSessions);
                    if (pauseService) {
                        requestWakeBeforePause();
                        pauseServiceLocked();
                    } else {
                        mHandler.postDelayed(this, POLL_DELAY_MILLIS);
                    }
                }
            }
        }

        /**
         * Fetch interaction data from providers.
         * Device is considered idle if there are no new interactions.
         * @return is device idle.
         */
        private boolean fetchInteractionData() {
            synchronized (mInteractionProviders) {
                long latestInteractionTimeMillis = 0L;
                for (InteractionProvider provider : mInteractionProviders) {
                    List<InteractionState> interactions = provider.getSourceInteractions();
                    for (InteractionState interaction : interactions) {
                        validateInteractionType(interaction.interactionTypes);

                        // keep the most recent interaction for each type.
                        mLatestInteractions.put(interaction.interactionTypes,
                                Math.max(interaction.interactionTimeMillis,
                                        mLatestInteractions.get(interaction.interactionTypes, 0L)));

                        // keep track of most recent interaction time overall.
                        latestInteractionTimeMillis = Math.max(latestInteractionTimeMillis,
                                interaction.interactionTimeMillis);
                    }
                }
                // Device is considered idle if there are no recent interactions.
                return SystemClock.uptimeMillis() - latestInteractionTimeMillis
                        > INACTIVITY_DELAY_MILLIS;
            }
        }

        private static void validateInteractionType(int interactionType) {
            switch (interactionType) {
                case INTERACTION_TYPE_KEY, INTERACTION_TYPE_HOVER, INTERACTION_TYPE_GESTURE,
                     INTERACTION_TYPE_NONE -> {
                }
                default -> throw new IllegalArgumentException(
                        "Invalid interaction type: " + interactionType);
            }
        }

        /**
         * Notify applicable listeners of new interactions.
         * @return true if there are active sessions remaining.
         */
        private boolean notifyListeners() {
            boolean hasActiveSessions = false;
            synchronized (mPidToInteractionListeners) {
                for (int i = 0; i < mPidToInteractionListeners.size(); ++i) {
                    InteractionListenerRecord listenerRecord =
                            mPidToInteractionListeners.valueAt(i);
                    notifyListenerLocked(listenerRecord);
                    hasActiveSessions |= listenerRecord.mInteractionSessions.size() > 0;
                }
            }
            return hasActiveSessions;
        }

        @GuardedBy("mPidToInteractionListeners")
        private void notifyListenerLocked(InteractionListenerRecord listenerRecord) {
            final long systemUptimeMillis = SystemClock.uptimeMillis();

            boolean stateChanged = false;
            for (int i = 0; i < mLatestInteractions.size(); ++i) {
                final int interactionType = mLatestInteractions.keyAt(i);
                if ((listenerRecord.mInteractionTypes & interactionType) == 0) {
                    // interaction-type is not subscribed
                    continue;
                }

                final Long interactionTime = mLatestInteractions.valueAt(i);
                if (interactionTime < listenerRecord.mRegistrationTime) {
                    // ignore old interaction.
                    continue;
                }

                // Notify if this interaction has not been notified yet.
                if (!listenerRecord.mInteractionSessions.contains(interactionType)) {
                    stateChanged = true;
                    listenerRecord.mInteractionSessions.put(interactionType,
                            new InteractionSession(interactionTime));
                    continue;
                }

                InteractionSession interactionSession = listenerRecord.mInteractionSessions.get(
                        interactionType);

                // End long sessions immediately if no new interaction have been reported.
                final long sessionLength =
                        systemUptimeMillis - interactionSession.mSessionStartMillis;
                if (sessionLength >= listenerRecord.mDebounceTimeMills
                        && interactionSession.mLastInteractionMillis == interactionTime) {
                    stateChanged = true;
                    listenerRecord.mInteractionSessions.remove(interactionType);
                }

                // End the session if it is expired.
                if (systemUptimeMillis - interactionSession.mLastInteractionMillis
                        >= listenerRecord.mDebounceTimeMills) {
                    stateChanged = true;
                    listenerRecord.mInteractionSessions.remove(interactionType);
                } else {
                    interactionSession.mLastInteractionMillis = interactionTime;
                }
            }

            if (!stateChanged) {
                // Ignore if the state is unchanged.
                return;
            }

            InteractionState stateToNotify = new InteractionState();
            stateToNotify.interactionTimeMillis = -1;
            for (int i = 0; i < listenerRecord.mInteractionSessions.size(); ++i) {
                stateToNotify.interactionTypes |= listenerRecord.mInteractionSessions.keyAt(i);
                stateToNotify.interactionTimeMillis = Math.max(
                        stateToNotify.interactionTimeMillis,
                        listenerRecord.mInteractionSessions.valueAt(i).mLastInteractionMillis);
            }
            try {
                listenerRecord.mListener.onInteractionStateChanged(stateToNotify);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        private void requestWakeBeforePause() {
            synchronized (mInteractionProviders) {
                for (InteractionProvider provider : mInteractionProviders) {
                    provider.requestWakeupCallback(() -> mHandler.post(
                            InteractionProviderServiceInternal.this::resumeService));
                }
            }
        }
    }
}
