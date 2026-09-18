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

package com.android.server.am;

import android.app.AnrWarningResult;
import android.app.IAnrWarningCallback;
import android.os.IBinder;
import android.os.PerfettoCategories;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Controller for handling ANR warning listeners. */
final class AnrWarningController {
    private static final String TAG = "AnrWarningController";
    private static final int METRIC_FIELD_NOT_APPLICABLE = -1;

    @GuardedBy("mAnrWarningCallbacks")
    private final SparseArray<List<IAnrWarningCallback>> mAnrWarningCallbacks;

    AnrWarningController() {
        this(new SparseArray<>());
    }

    @VisibleForTesting
    AnrWarningController(SparseArray<List<IAnrWarningCallback>> callbacks) {
        mAnrWarningCallbacks = callbacks;
    }

    void registerAnrWarningListener(int callingUid, IAnrWarningCallback callback) {
        synchronized (mAnrWarningCallbacks) {
            maybeCleanupAnrWarningCallbacks();

            List<IAnrWarningCallback> perUidCallbacks = mAnrWarningCallbacks.get(callingUid);
            if (perUidCallbacks == null) {
                perUidCallbacks = new ArrayList<IAnrWarningCallback>();
            }
            perUidCallbacks.add(callback);
            mAnrWarningCallbacks.put(callingUid, perUidCallbacks);

            AnrWarningDeathRecipient deathRecipient = new AnrWarningDeathRecipient(callingUid);
            try {
                callback.asBinder().linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Exception linking death recipient", e);
            }
            logAnrApiEvent(
                    callingUid,
                    FrameworkStatsLog.ANR_WARNING_API_REPORTED__ACTION__REGISTER,
                    perUidCallbacks.size()
            );
        }
    }

    void unregisterAnrWarningListener(int callingUid, IAnrWarningCallback callback) {
        synchronized (mAnrWarningCallbacks) {
            List<IAnrWarningCallback> perUidCallbacks = mAnrWarningCallbacks.get(callingUid);

            // We should never reach this condition but added this check to be on safer side.
            if (perUidCallbacks == null) {
                mAnrWarningCallbacks.remove(callingUid);
                return;
            }

            perUidCallbacks.remove(callback);
            if (perUidCallbacks.isEmpty()) {
                mAnrWarningCallbacks.remove(callingUid);
            } else {
                mAnrWarningCallbacks.put(callingUid, perUidCallbacks);
            }
            logAnrApiEvent(callingUid,
                    FrameworkStatsLog.ANR_WARNING_API_REPORTED__ACTION__UNREGISTER,
                    perUidCallbacks.size()
            );
        }
    }

    /** Notifies the app about a potential ANR. */
    void notifyAnrWarning(
            int uid,
            int anrId,
            UUID errorId,
            int anrType,
            long consumedTimeMs,
            long timeoutMs,
            String description) {
        synchronized (mAnrWarningCallbacks) {
            long startTime = SystemClock.elapsedRealtime();
            int resultCode =
                    FrameworkStatsLog.ANR_WARNING_API_REPORTED__NOTIFY_RESULT__NOTIFY_SUCCESS;
            if (!mAnrWarningCallbacks.contains(uid)) {
                return;
            }

            List<IAnrWarningCallback> perUidCallbacks = mAnrWarningCallbacks.get(uid);
            if (perUidCallbacks == null) {
                return;
            }

            com.android.internal.dev.perfetto.sdk.PerfettoTrace
                    .instant(PerfettoCategories.ANR_CATEGORY, "AnrWarningDetected")
                    .addArg("anrId", anrId)
                    .addArg("errorId", errorId.toString())
                    .addArg("anrTimeoutMs", timeoutMs)
                    .addArg("consumedTimeMs", consumedTimeMs)
                    .emit();

            // Iterate through all the listeners registered for the uid and notify the app
            for (IAnrWarningCallback callback : perUidCallbacks) {
                AnrWarningResult result =
                        new AnrWarningResult(
                                anrId, anrType, consumedTimeMs, timeoutMs, description);

                try {
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "onAnrWarning");
                    callback.onAnrImminent(result);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to notify pre Anr callback");
                    resultCode = FrameworkStatsLog
                            .ANR_WARNING_API_REPORTED__NOTIFY_RESULT__NOTIFY_FAILURE;
                } finally {
                    int duration = (int) (SystemClock.elapsedRealtime() - startTime);
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                    logAnrApiEvent(uid,
                            FrameworkStatsLog.ANR_WARNING_API_REPORTED__ACTION__NOTIFY,
                            perUidCallbacks.size(),
                            duration,
                            resultCode,
                            anrId);
                }
            }
        }
    }

    /**
     * Iterate through and delete any callbacks for which binder is not alive.
     *
     * <p>Each binder object has a registered linkToDeath which also handles removal. This mechanism
     * serves as a backup to guarantee that the list stays in check.
     */
    private void maybeCleanupAnrWarningCallbacks() {
        // Create a temporary list to hold callbacks to be removed.
        ArrayList<IAnrWarningCallback> callbacksToRemove = new ArrayList<IAnrWarningCallback>();

        // Iterate through the results callback, each iteration is for a uid which has registered
        // callbacks.
        for (int i = 0; i < mAnrWarningCallbacks.size(); i++) {
            // Ensure the temporary list is empty
            callbacksToRemove.clear();

            // Grab the current list of callbacks.
            List<IAnrWarningCallback> callbacks = mAnrWarningCallbacks.valueAt(i);

            if (callbacks != null && !callbacks.isEmpty()) {
                // Iterate through each callback individually as a single
                // application can have multiple processes, and each process can register its own
                // ANR warning callback. When one of these processes dies, only the callbacks
                // associated with that specific process will become invalid.
                for (IAnrWarningCallback callback : callbacks) {
                    // If the callback is no longer alive, add it to the list for removal.
                    if (callback == null || !callback.asBinder().isBinderAlive()) {
                        callbacksToRemove.add(callback);
                    }
                }

                // Now remove all the callbacks that were added to the list for removal.
                callbacks.removeAll(callbacksToRemove);
            }
        }
    }

    /** Log for Notify (All fields are relevant) */
    private void logAnrApiEvent(int uid, int action, int callbackCount,
            int latencyMs, int result, int anrId) {

        FrameworkStatsLog.write(
                FrameworkStatsLog.ANR_WARNING_API_REPORTED,
                action,
                uid,
                callbackCount,
                latencyMs,
                result,
                anrId
        );
    }

    /** Log for Register/Unregister (Contextual fields are ignored) */
    private void logAnrApiEvent(int uid, int action, int callbackCount) {
        logAnrApiEvent(
                uid,
                action,
                callbackCount,
                /*latency*/ METRIC_FIELD_NOT_APPLICABLE,
                /*resultCode*/
                FrameworkStatsLog.ANR_WARNING_API_REPORTED__NOTIFY_RESULT__NOTIFY_UNKNOWN,
                /*anrId*/ METRIC_FIELD_NOT_APPLICABLE);
    }

    private class AnrWarningDeathRecipient implements IBinder.DeathRecipient {
        private final int mUid;

        AnrWarningDeathRecipient(int uid) {
            mUid = uid;
        }

        @Override
        public void binderDied() {
            Log.w(TAG, "binderDied without who should not have been called");
        }

        @Override
        public void binderDied(IBinder who) {
            synchronized (mAnrWarningCallbacks) {
                List<IAnrWarningCallback> callbacks = mAnrWarningCallbacks.get(mUid);
                if (callbacks == null) {
                    return;
                }
                // Iterate through the list to find and remove the specific callback
                // associated with the binder that just died.
                callbacks.removeIf(callback -> who.equals(callback.asBinder()));

                // If there are no more active callbacks for this UID, remove the
                // entire entry from the map.
                if (callbacks.isEmpty()) {
                    mAnrWarningCallbacks.remove(mUid);
                }
            }
        }
    }
}
