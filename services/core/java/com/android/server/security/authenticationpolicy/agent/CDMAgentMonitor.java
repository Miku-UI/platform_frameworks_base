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

package com.android.server.security.authenticationpolicy.agent;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.DeviceId;
import android.companion.DevicePresenceEvent;
import android.os.Handler;
import android.util.Slog;

import java.util.List;

/**
 * This class is a helper utility for subscribing to CDM events for connected devices.
 *
 * It will search for connected devices that are able & authorized by CDM to perform
 * automated actions on behalf of the user and notify callers when their connection status changes.
 *
 * @hide
 */
@SuppressLint("MissingPermission")
public class CDMAgentMonitor {

    private static final String TAG = "CDMAgentMonitor";
    private static final String SERVICE_NAME = TAG;

    private final Handler mHandler;
    private final CompanionDeviceManager mDeviceManager;
    private final int mUserId;
    private Listener mListener;

    /** Callbacks for lifecycle events for connected agents. */
    interface Listener {
        /** Called once when an authorized agent first connects to this device. */
        void onAgentConnectionStarted(int associationId);

        /**
         * Called once when an authorized agent disconnects after
         * {@link #onAgentConnectionStarted(int)}.
         */
        void onAgentConnectionStopped(int associationId);
    }

    private final CompanionDeviceManager.OnAssociationsChangedListener mAssociationListener =
            this::updateAssociations;

    /**
     * Creates a new connect list by subscribing to CDM events. Call {@link #start()} to being
     * receiving events and {@link #stop()} to stop subscribing.
     *
     * @param handler                Handler for all listener callbacks & CDM events
     * @param userId                 user id
     * @param companionDeviceManager companion device manager for subscribing to connected agents
     * @param listener               callbacks for agent lifecycle
     */
    CDMAgentMonitor(@NonNull Handler handler, int userId,
            @NonNull CompanionDeviceManager companionDeviceManager,
            @NonNull Listener listener) {
        mHandler = handler;
        mDeviceManager = companionDeviceManager;
        mUserId = userId;
        mListener = listener;
    }

    /**
     * Start monitoring for connections.
     *
     * Do not call this method more than once. Create a new instance of the class instead.
     */
    public void start() {
        mHandler.post(() -> {
            if (mListener != null) {
                Slog.d(TAG, "Start monitoring");

                mDeviceManager.addOnAssociationsChangedListener(mHandler::post,
                        mAssociationListener, mUserId);
                mAssociationListener.onAssociationsChanged(
                        mDeviceManager.getAllAssociations(mUserId));
            }
        });
    }

    /**
     * Stop monitoring for connections.
     *
     * Do not call this method more than once. Create a new instance of the class instead.
     */
    public void stop() {
        mHandler.post(() -> {
            if (mListener != null) {
                Slog.d(TAG, "Stop monitoring");

                mDeviceManager.removeOnAssociationsChangedListener(mAssociationListener);
                updateAssociations(List.of());
                mListener = null;
            }
        });
    }

    private void updateAssociations(List<AssociationInfo> associationList) {
        final var toSubscribe = associationList.stream()
                .filter(this::isCrossDeviceAgent)
                .toList();

        Slog.d(TAG, "Associations for user: " + mUserId + " count: " + toSubscribe.size());

        if (!toSubscribe.isEmpty()) {
            for (var x : toSubscribe) {
                final boolean present = mDeviceManager.isDevicePresent(x.getId());
                Slog.d(TAG, "Initial state of association: " + x.getId() + " present: " + present);
                if (present) {
                    handlePresenceEvent(new DevicePresenceEvent.Builder()
                            .setAssociationId(x.getId())
                            .setEvent(DevicePresenceEvent.EVENT_BT_CONNECTED)
                            .build());
                }
            }
            mDeviceManager.setOnDevicePresenceEventListener(
                    toSubscribe.stream().mapToInt(AssociationInfo::getId).toArray(),
                    SERVICE_NAME, mHandler::post, this::handlePresenceEvent);
        } else {
            mDeviceManager.removeOnDevicePresenceEventListener(SERVICE_NAME);
        }
    }

    private void handlePresenceEvent( DevicePresenceEvent event) {
        Slog.d(TAG, "handlePresenceEvent: " + event);

        switch (event.getEvent()) {
            case DevicePresenceEvent.EVENT_BT_CONNECTED ->
                    onConnected(event.getAssociationId());
            case DevicePresenceEvent.EVENT_BT_DISCONNECTED,
                 DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED ->
                    onDisconnected(event.getAssociationId());
        }
    }

    private void onConnected(int associationId) {
        Slog.v(TAG, "onConnected: " + associationId);

        if (mListener != null) {
            mListener.onAgentConnectionStarted(associationId);
        }
    }

    private void onDisconnected(int associationId) {
        Slog.v(TAG, "onDisconnected: " + associationId);

        if (mListener != null) {
            mListener.onAgentConnectionStopped(associationId);
        }
    }

    private boolean isCrossDeviceAgent(AssociationInfo association) {
        return (association != null) && association.isRemoteAiAgentSupported();
    }
}
