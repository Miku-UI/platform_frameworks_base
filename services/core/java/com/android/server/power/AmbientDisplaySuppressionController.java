/**
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

package com.android.server.power;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Flags;
import android.os.PowerManager;
import android.os.PowerManager.FlagAmbientSuppression;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArraySet;
import android.util.Slog;

import com.android.internal.statusbar.IStatusBarService;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Communicates with System UI to suppress the ambient display.
 */
public class AmbientDisplaySuppressionController {
    private static final String TAG = "AmbientDisplaySuppressionController";

    /**
     * A {@link SuppressionToken} is a unique identifier for a suppression request. It is unique
     * based on an id (determined by the controller) and a tag (determined by the process).
     */
    private record SuppressionToken(int id, String tag) {
        public boolean belongsToId(int id) {
            return this.id() == id;
        }
    }

    private final Set<SuppressionToken> mSuppressionTokens;

    private final Map<SuppressionToken, Integer> mSuppressions;

    private final AmbientDisplaySuppressionChangedCallback mCallback;
    private IStatusBarService mStatusBarService;

    /** Interface to get a list of available logical devices. */
    interface AmbientDisplaySuppressionChangedCallback {
        /**
         * Called when the suppression state changes.
         *
         * @param isSuppressed Whether ambient is suppressed.
         */
        void onSuppressionChanged(boolean isSuppressed);

        /**
         * Called when the suppression state changes.
         *
         * @param suppressionState the new aggregate suppression state.
         */
        void onSuppressionChanged(@FlagAmbientSuppression int suppressionState);
    }

    AmbientDisplaySuppressionController(
            @NonNull AmbientDisplaySuppressionChangedCallback callback) {
        mSuppressionTokens = Collections.synchronizedSet(new ArraySet<>());
        mSuppressions = Collections.synchronizedMap(new HashMap<>());
        mCallback = requireNonNull(callback);
    }

    /**
     * Suppresses ambient display.
     *
     * @deprecated Use {@link #suppress(String, int, int)} instead.
     * @param token A persistible identifier for the ambient display suppression.
     * @param callingUid The uid of the calling application.
     * @param suppress If true, suppresses the ambient display. Otherwise, unsuppresses it.
     */
    @Deprecated
    public void suppress(@NonNull String token, int callingUid, boolean suppress) {
        SuppressionToken suppressionToken = new SuppressionToken(callingUid, requireNonNull(token));
        final boolean wasSuppressed = isSuppressed();

        if (suppress) {
            mSuppressionTokens.add(suppressionToken);
        } else {
            mSuppressionTokens.remove(suppressionToken);
        }

        final boolean isSuppressed = isSuppressed();
        if (isSuppressed != wasSuppressed) {
            mCallback.onSuppressionChanged(isSuppressed);
        }

        try {
            synchronized (mSuppressionTokens) {
                getStatusBar().suppressAmbientDisplay(isSuppressed);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to suppress ambient display", e);
        }
    }

    /**
     * Suppresses ambient display.
     *
     * @param token A persistible identifier for the ambient display suppression.
     * @param callingUid The uid of the calling application.
     * @param suppressionFlags flags specifying how to suppress the ambient display.
     */
    public void suppress(@NonNull String token, int callingUid,
            @FlagAmbientSuppression int suppressionFlags) {
        final SuppressionToken suppressionToken =
                new SuppressionToken(callingUid, requireNonNull(token));
        final int existingSuppression = calculateSuppression();

        if (suppressionFlags != PowerManager.FLAG_AMBIENT_SUPPRESSION_NONE) {
            mSuppressions.put(suppressionToken, suppressionFlags);
        } else {
            mSuppressions.remove(suppressionToken);
        }

        final int currentSuppression = calculateSuppression();
        if (existingSuppression != currentSuppression) {
            mCallback.onSuppressionChanged(currentSuppression);
        }

        try {
            synchronized (mSuppressionTokens) {
                getStatusBar().suppressAmbientDisplay(
                        (currentSuppression & PowerManager.FLAG_AMBIENT_SUPPRESSION_AOD)
                                == PowerManager.FLAG_AMBIENT_SUPPRESSION_AOD);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to suppress ambient display", e);
        }
    }

    /**
     * Returns the tokens used to suppress ambient display through
     * {@link #suppress(String, int, boolean)}.
     *
     * @param callingUid The uid of the calling application.
     */
    List<String> getSuppressionTokens(int callingUid) {
        List<String> result = new ArrayList<>();

        if (Flags.lowLightDreamBehavior()) {
            synchronized (mSuppressions) {
                for (Map.Entry<SuppressionToken, Integer> entry : mSuppressions.entrySet()) {
                    if (entry.getKey().belongsToId(callingUid)) {
                        result.add(entry.getKey().tag);
                    }
                }
            }
        } else {
            synchronized (mSuppressionTokens) {
                for (SuppressionToken token: mSuppressionTokens) {
                    if (token.belongsToId(callingUid)) {
                        result.add(token.tag);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns whether ambient display is suppressed for the given token.
     *
     * @param token A persistible identifier for the ambient display suppression.
     * @param callingUid The uid of the calling application.
     */
    public boolean isSuppressed(@NonNull String token, int callingUid) {
        if (Flags.lowLightDreamBehavior()) {
            return mSuppressions.containsKey(
                    new SuppressionToken(callingUid, requireNonNull(token)));
        }

        return mSuppressionTokens.contains(new SuppressionToken(callingUid, requireNonNull(token)));
    }

    /**
     * Calculates the current ambient suppression by aggregating the flags across active
     * suppressions.
     * @return the aggregate suppression flags from all active suppressions
     */
    public @FlagAmbientSuppression int calculateSuppression() {
        int suppression = PowerManager.FLAG_AMBIENT_SUPPRESSION_NONE;
        synchronized (mSuppressions) {
            for (Map.Entry<SuppressionToken, Integer> entry : mSuppressions.entrySet()) {
                suppression |= entry.getValue();
            }
        }

        return suppression;
    }

    /**
     * Returns whether ambient display is suppressed.
     */
    public boolean isSuppressed() {
        if (Flags.lowLightDreamBehavior()) {
            return (calculateSuppression() & PowerManager.FLAG_AMBIENT_SUPPRESSION_DREAM)
                    == PowerManager.FLAG_AMBIENT_SUPPRESSION_DREAM;
        }

        return !mSuppressionTokens.isEmpty();
    }

    /**
     * Dumps the state of ambient display suppression and the list of suppression tokens into
     * {@code pw}.
     */
    public void dump(PrintWriter pw) {
        pw.println("AmbientDisplaySuppressionController:");
        pw.println(" ambientDisplaySuppressed=" + isSuppressed());
        pw.println(" mSuppressionTokens=" + mSuppressionTokens);

        if (Flags.lowLightDreamBehavior()) {
            pw.println(" mSuppressions=" + mSuppressions);
        }
    }

    private synchronized IStatusBarService getStatusBar() {
        if (mStatusBarService == null) {
            mStatusBarService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        }
        return mStatusBarService;
    }
}
