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

package com.android.server.companion.datatransfer.continuity.settings;

import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IHandoffFeatureStateListener;
import android.companion.datatransfer.continuity.TaskContinuityManager;
import android.os.Bundle;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserManager;
import android.util.Slog;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.UserManagerInternal.UserRestrictionsListener;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Manages settings for Handoff across all users on a device. */
public class HandoffSettingsManager implements UserRestrictionsListener {

    private static final String TAG = HandoffSettingsManager.class.getSimpleName();

    private final HandoffPreferenceStore mHandoffPreferenceStore;

    private AtomicReference<RemoteCallbackList<IHandoffFeatureStateListener>>
            mHandoffFeatureStateListeners = new AtomicReference<>(null);

    public HandoffSettingsManager(@NonNull HandoffPreferenceStore handoffPreferenceStore) {
        mHandoffPreferenceStore = Objects.requireNonNull(handoffPreferenceStore);
        LocalServices.getService(UserManagerInternal.class).addUserRestrictionsListener(this);
    }

    /**
     * Returns whether handoff is active for the given user (available and enabled).
     *
     * @param userId The user ID of the user to check.
     * @return True if handoff is active for the user, false otherwise.
     */
    public boolean isHandoffActiveForUser(int userId) {
        if (getHandoffAvailabilityForUser(userId)
                != TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE) {
            return false;
        }
        return mHandoffPreferenceStore.isHandoffEnabledForUser(userId);
    }

    /**
     * Sets whether handoff is enabled for the given user. Handoff availability has precedence over
     * this setting.
     *
     * @param userId The user ID of the user to set.
     * @param enabled True if handoff is enabled, false otherwise.
     */
    public void setHandoffEnabledForUser(int userId, boolean enabled) {
        mHandoffPreferenceStore.setHandoffEnabledForUser(userId, enabled);
        notifyHandoffFeatureStateChanged(userId);
    }

    public void registerHandoffFeatureStateListener(
            int userId, @NonNull IHandoffFeatureStateListener listener) {
        if (!mHandoffFeatureStateListeners
                .updateAndGet(
                        listeners -> {
                            if (listeners == null) {
                                return new RemoteCallbackList<>();
                            }
                            return listeners;
                        })
                .register(Objects.requireNonNull(listener), userId)) {
            Slog.e(TAG, "Failed to register handoff feature state listener");
            return;
        }

        try {
            listener.onHandoffFeatureStateChanged(
                    getHandoffAvailabilityForUser(userId),
                    mHandoffPreferenceStore.isHandoffEnabledForUser(userId));
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify handoff feature state change", e);
        }
    }

    public void unregisterHandoffFeatureStateListener(
            int userId, @NonNull IHandoffFeatureStateListener listener) {
        RemoteCallbackList<IHandoffFeatureStateListener> listeners =
                mHandoffFeatureStateListeners.get();
        if (listeners == null) {
            return;
        }

        if (listeners.unregister(Objects.requireNonNull(listener))) {
            Slog.i(TAG, "Successfully unregistered handoff feature state listener");
        } else {
            Slog.e(TAG, "Failed to unregister handoff feature state listener");
        }
    }

    private int getHandoffAvailabilityForUser(int userId) {
        if (LocalServices.getService(UserManagerInternal.class)
                .getUserRestriction(userId, UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF)) {
            return TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_DISABLED_BY_POLICY;
        }

        return TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE;
    }

    @Override
    public void onUserRestrictionsChanged(
            int userId, Bundle newRestrictions, Bundle prevRestrictions) {

        boolean wasRestricted =
                prevRestrictions.getBoolean(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, false);
        boolean isRestricted =
                newRestrictions.getBoolean(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, false);

        if (wasRestricted != isRestricted) {
            notifyHandoffFeatureStateChanged(userId);
        }
    }

    private void notifyHandoffFeatureStateChanged(int userId) {
        RemoteCallbackList<IHandoffFeatureStateListener> listeners =
                mHandoffFeatureStateListeners.get();
        if (listeners == null) {
            return;
        }

        listeners.broadcast(
                (listener, token) -> {
                    if ((int) token == userId) {
                        try {
                            listener.onHandoffFeatureStateChanged(
                                    getHandoffAvailabilityForUser(userId),
                                    mHandoffPreferenceStore.isHandoffEnabledForUser(userId));
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Failed to notify handoff feature state change", e);
                        }
                    }
                });
    }
}
