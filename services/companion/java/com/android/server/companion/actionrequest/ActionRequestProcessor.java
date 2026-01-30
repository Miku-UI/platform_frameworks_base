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

package com.android.server.companion.actionrequest;

import static android.companion.ActionRequest.REQUEST_NEARBY_ADVERTISING;
import static android.companion.ActionRequest.REQUEST_NEARBY_SCANNING;
import static android.companion.ActionRequest.REQUEST_TRANSPORT;


import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.ActionRequest;
import android.companion.ActionResult;
import android.companion.AssociationInfo;
import android.companion.IOnActionResultListener;
import android.companion.IOnTransportsChangedListener;
import android.os.RemoteException;
import android.util.Slog;

import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.devicepresence.CompanionAppBinder;
import com.android.server.companion.devicepresence.CompanionServiceConnector;
import com.android.server.companion.devicepresence.DevicePresenceProcessor;
import com.android.server.companion.transport.CompanionTransportManager;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of action requests sent to companion apps and processes the results.
 * This class handles the {@code requestAction -> notifyActionRequestResult} communication loop.
 */
public class ActionRequestProcessor implements AssociationStore.OnChangeListener {
    private static final String TAG = "CDM_ActionRequestProcessor";

    @NonNull
    private final AssociationStore mAssociationStore;
    @NonNull
    private final DevicePresenceProcessor mDevicePresenceProcessor;
    @NonNull
    private final CompanionAppBinder mCompanionAppBinder;
    @NonNull
    private final CompanionTransportManager mCompanionTransportManager;

    /**
     * Tracks which services have an active request for a given action and association.
     */
    private final Map<Integer, Map<Integer, Set<String>>> mActiveActionServices =
            new ConcurrentHashMap<>();

    /**
     *  A thread-safe list of registered listeners for action results.
     */
    private final List<ActionResultListener> mActionResultListeners = new CopyOnWriteArrayList<>();

    // A single set of all actions that are stateful and managed by tokens.
    private static final Set<Integer> STATEFUL_ACTIONS = Set.of(
            REQUEST_NEARBY_SCANNING,
            REQUEST_NEARBY_ADVERTISING,
            REQUEST_TRANSPORT
    );

    public ActionRequestProcessor(
            @NonNull AssociationStore associationStore,
            @NonNull DevicePresenceProcessor devicePresenceProcessor,
            @NonNull CompanionAppBinder companionAppBinder,
            @NonNull CompanionTransportManager companionTransportManager
    ) {
        this.mAssociationStore = associationStore;
        this.mDevicePresenceProcessor = devicePresenceProcessor;
        this.mCompanionAppBinder = companionAppBinder;
        this.mCompanionTransportManager = companionTransportManager;
        this.mAssociationStore.registerLocalListener(this);
        // Register a listener to be notified of transport changes.
        this.mCompanionTransportManager.addListener(new IOnTransportsChangedListener.Stub() {
            @Override
            public void onTransportsChanged(@NonNull List<AssociationInfo> associations) {
                handleOnTransportsChanged(associations);
            }
        });
    }

    /**
     * Implements
     * {@link AssociationStore.OnChangeListener#onAssociationRemoved(AssociationInfo)}
     */
    @Override
    public void onAssociationRemoved(@NonNull AssociationInfo association) {
        final int associationId = association.getId();

        Slog.i(TAG, "Association " + associationId + " was removed. Cleaning up active "
                + "action services cache.");
        mActiveActionServices.remove(associationId);
        for (ActionResultListener listener : mActionResultListeners) {
            listener.mAssociationIdFilter.remove(associationId);
        }
    }

    /**
     * Sets a listener to receive action result for a given system service.
     */
    public void setOnActionResultListener(@NonNull int[] associationIds,
            @NonNull String serviceName, @NonNull IOnActionResultListener listener) {
        if (associationIds.length == 0) {
            throw new IllegalArgumentException("associationIds must be a non-empty array.");
        }
        Slog.i(TAG, "Adding action result listener for service: " + serviceName);


        final Set<Integer> filterSet = new HashSet<>(
                Arrays.stream(associationIds).boxed().toList());

        boolean removed = mActionResultListeners.removeIf(
                l -> l.mServiceName.equals(serviceName));
        if (removed) {
            Slog.i(TAG, "Removed previous listener for service: " + serviceName);
        }

        mActionResultListeners.add(new ActionResultListener(serviceName, listener, filterSet));
    }

    /**
     * Unregisters the listener for action request results for the given service name.
     */
    public void removeOnActionResultListener(@NonNull String serviceName) {
        Slog.i(TAG, "Removing action result listener for service: " + serviceName);

        mActionResultListeners.removeIf(l -> l.mServiceName.equals(serviceName));
    }

    /**
     * Send ActionRequest to the companion apps.
     * System will bind the service before sending the request.
     */
    public void requestAction(@NonNull ActionRequest request,
            @NonNull String serviceName, int[] associationIds) {
        Objects.requireNonNull(request, "request cannot be null.");
        Objects.requireNonNull(serviceName, "serviceName cannot be null.");
        Objects.requireNonNull(associationIds, "associationIds cannot be null.");

        final int action = request.getAction();

        Slog.i(TAG, "requestAction() from service=[" + serviceName + "] for ActionRequest=["
                + request + "] on associations=" + Arrays.toString(associationIds));

        if (!STATEFUL_ACTIONS.contains(action)) {
            Slog.w(TAG, "Action " + action + " is not a supported action.");
            return;
        }
        for (int id : associationIds) {
            final AssociationInfo association = mAssociationStore.getAssociationById(id);
            if (association == null) {
                Slog.w(TAG, "Skipping requestAction for non-existent association " + id);
                continue;
            }

            handleActionRequest(association, request, serviceName);
        }
    }

    /**
     * Processes an action request result reported by a self-managed companion app.
     */
    public void processActionResult(int associationId, @NonNull ActionResult result) {
        Slog.i(TAG, "Processing action result: " + result);

        AssociationInfo association = mAssociationStore.getAssociationById(associationId);
        if (association == null) {
            Slog.w(TAG, "Action result for non-existent association " + associationId);
            return;
        }
        if (!association.isSelfManaged()) {
            throw new IllegalArgumentException("Association id=[" + associationId
                    + "] is not self-managed. Cannot report action results.");
        }

        // Broadcast the result to any listening SYSTEM services.
        broadcastActionRequestResult(associationId, result);
    }

    /**
     * Dump the ActionRequestProcessor's state.
     */
    public void dump(@NonNull PrintWriter out) {
        out.println("  ActionRequestProcessor State:");

        if (mActiveActionServices.isEmpty()) {
            out.println("    mActiveActionServices: <empty>");
        } else {
            out.println("    mActiveActionServices:");
            for (Map.Entry<Integer, Map<Integer, Set<String>>> entry :
                    mActiveActionServices.entrySet()) {
                final int associationId = entry.getKey();
                final Map<Integer, Set<String>> actions = entry.getValue();
                out.println("      Association " + associationId + ":");
                for (Map.Entry<Integer, Set<String>> actionEntry : actions.entrySet()) {
                    out.println("        "
                            + ActionRequest.actionToString(actionEntry.getKey()) + ": "
                            + actionEntry.getValue());
                }
            }
        }
        // Dump Action Result Listeners
        if (mActionResultListeners.isEmpty()) {
            out.println("    mActionResultListeners: <empty>");
        } else {
            out.println("    mActionResultListeners:");
            for (ActionResultListener listener : mActionResultListeners) {
                final String filterString = listener.mAssociationIdFilter.toString();
                out.println("      " + listener.mServiceName + ": filter=" + filterString);
            }
        }
        out.println();
    }

    private void broadcastActionRequestResult(
            int associationId, @NonNull ActionResult result) {
        Slog.i(TAG, "Broadcasting ActionRequestResult to system listeners: " + result);

        for (ActionResultListener actionResultListener : mActionResultListeners) {
            if (actionResultListener.mAssociationIdFilter.contains(associationId)) {
                try {
                    actionResultListener.mListener.onActionResult(associationId, result);
                } catch (RemoteException e) {
                    // Handle exception
                }
            }
        }
    }

    private void handleActionRequest(@NonNull AssociationInfo association,
            @NonNull ActionRequest request, @NonNull String serviceName) {
        final int associationId = association.getId();
        final int action = request.getAction();
        final int operation = request.getOperation();
        // Get or create the services set for this action and association.
        final Set<String> services = mActiveActionServices
                .computeIfAbsent(associationId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(action, k -> new HashSet<>());

        switch(operation) {
            case (ActionRequest.OP_ACTIVATE):
                final boolean wasEmpty = services.isEmpty();
                if (services.add(serviceName) && wasEmpty) {
                    // This is the FIRST service. Send the ACTIVATE request to the app.
                    Slog.i(TAG, "First service [" + serviceName + "]. Activating action=" + action
                            + " for association " + associationId);

                    postActionRequest(association, request);
                }
                break;
            case (ActionRequest.OP_DEACTIVATE):
                if (services.remove(serviceName) && services.isEmpty()) {
                    // This was the LAST service. Send the DEACTIVATE request to the app.
                    Slog.i(TAG, "Last service [" + serviceName + "]. Deactivating action=" + action
                            + " for association " + associationId);

                    postActionRequest(association, request);
                }
                break;
            default:
                Slog.e(TAG, "Unsupported operation: " + operation);
        }
    }

    /**
     * Binds the companion app if needed and posts the action request to its service.
     */
    private void postActionRequest(
            @NonNull AssociationInfo association, @NonNull ActionRequest request) {
        final CompanionServiceConnector connector = getServiceConnectorForAssociation(association);
        if (connector != null) {
            connector.postOnActionRequested(association, request);
        }
    }

    @Nullable
    private CompanionServiceConnector getServiceConnectorForAssociation(
            @NonNull AssociationInfo association) {
        final int userId = association.getUserId();
        final String packageName = association.getPackageName();

        mDevicePresenceProcessor.bindApplicationIfNeeded(
                userId, packageName, association.isSelfManaged());

        final CompanionServiceConnector primaryServiceConnector =
                mCompanionAppBinder.getPrimaryServiceConnector(userId, packageName);

        if (primaryServiceConnector == null) {
            Slog.e(TAG, "Could not get a service connector for " + packageName
                    + " to post a task.");
            return null;
        }

        return primaryServiceConnector;
    }


    private void handleOnTransportsChanged(
            @NonNull List<AssociationInfo> associationsWithTransport) {
        // Create a set of association IDs that currently have a transport.
        final Set<Integer> associationsWithTransportIds = associationsWithTransport.stream()
                .map(AssociationInfo::getId)
                .collect(Collectors.toSet());

        // Iterate over the keys in mActiveActionServices and remove any that are no longer in the
        // list of associations with an active transport.
        mActiveActionServices.keySet().removeIf(associationId -> {
            if (!associationsWithTransportIds.contains(associationId)) {
                Slog.i(TAG, "Transport for association " + associationId + " detached. "
                        + "Clearing active action requests.");
                return true; // Remove this key from the map.
            }
            return false;
        });
    }

    private static final class ActionResultListener {
        final String mServiceName;
        final IOnActionResultListener mListener;
        final Set<Integer> mAssociationIdFilter;

        ActionResultListener(String serviceName,
                IOnActionResultListener listener, Set<Integer> filter) {
            this.mServiceName = serviceName;
            this.mListener = listener;
            this.mAssociationIdFilter = filter;
        }
    }
}
