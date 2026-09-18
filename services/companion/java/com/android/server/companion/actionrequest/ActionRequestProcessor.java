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
import static android.companion.ActionResult.RESULT_ACTIVATED;
import static android.companion.ActionResult.RESULT_DEACTIVATED;
import static android.companion.ActionResult.RESULT_FAILED_TO_ACTIVATE;


import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.ActionRequest;
import android.companion.ActionResult;
import android.companion.AssociationInfo;
import android.companion.IOnActionResultListener;
import android.os.Binder;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Slog;

import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.devicepresence.CompanionAppBinder;
import com.android.server.companion.devicepresence.CompanionServiceConnector;
import com.android.server.companion.devicepresence.DevicePresenceProcessor;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the lifecycle of action requests sent to companion apps and processes the results.
 * This class handles the {@code requestAction -> notifyActionRequestResult} communication loop.
 */
public class ActionRequestProcessor implements AssociationStore.OnChangeListener {
    private static final String TAG = "CDM_ActionRequestProcessor";

    private @Nullable List<String> mRequestActionAllowList = null;

    @NonNull
    private final AssociationStore mAssociationStore;
    @NonNull
    private final DevicePresenceProcessor mDevicePresenceProcessor;
    @NonNull
    private final CompanionAppBinder mCompanionAppBinder;

    /**
     * Tracks which services have an active request for a given action and association.
     */
    private final Map<Integer, Map<Integer, ActionState>> mActiveActionStates =
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
            @NonNull CompanionAppBinder companionAppBinder
    ) {
        this.mAssociationStore = associationStore;
        this.mDevicePresenceProcessor = devicePresenceProcessor;
        this.mCompanionAppBinder = companionAppBinder;
        this.mAssociationStore.registerLocalListener(this);
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
        mActiveActionStates.remove(associationId);
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
    public void clearOnActionResultListener(@NonNull String serviceName) {
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

        if (isRequestBlocked(serviceName)) {
            // Does not need to send the ActionResult since it for test purpose only.
            return;
        }

        final int action = request.getAction();

        Slog.i(TAG, "requestAction() from service=[" + serviceName + "] for ActionRequest=["
                + request + "] on associations=" + Arrays.toString(associationIds));

        if (!STATEFUL_ACTIONS.contains(action)) {
            Slog.w(TAG, "Action " + action + " is not a supported action.");
            return;
        }

        Binder.withCleanCallingIdentity(() -> {
            for (int id : associationIds) {
                final AssociationInfo association = mAssociationStore.getAssociationById(id);
                if (association == null) {
                    Slog.w(TAG, "Skipping requestAction for non-existent association " + id);
                    continue;
                }

                handleActionRequest(association, request, serviceName);
            }
        });
    }

    /**
     * Processes an action result reported by a companion app.
     */
    public void processActionResult(int associationId, @NonNull ActionResult result) {
        Slog.i(TAG, "Processing action result: " + result);

        mAssociationStore.getAssociationWithCallerChecks(associationId);

        final int action = result.getAction();

        final Map<Integer, ActionState> associationActions = mActiveActionStates.get(
                associationId);
        if (associationActions == null) {
            Slog.w(TAG, "Received action result for an unknown association " + associationId);
            return;
        }
        final ActionState actionState = associationActions.get(action);

        // If there's no state, the action was likely already deactivated and cleaned up.
        // This is expected when receiving a RESULT_DEACTIVATED for a request we already
        // handled locally.
        if (actionState == null) {
            Slog.i(TAG, "Received action result for an already cleaned-up action. Ignoring.");
            return;
        }

        switch (result.getResultCode()) {
            case RESULT_ACTIVATED:
                // This is the success case for an activation request.
                if (actionState.mState == ActionState.STATE_PENDING_ACTIVATION) {
                    Slog.i(TAG, "Action " + ActionRequest.actionToString(action)
                            + " is now ACTIVE.");
                    actionState.mState = ActionState.STATE_ACTIVE;
                    broadcastToRequestingServices(actionState, associationId, result);
                } else {
                    Slog.w(TAG, "Received ACTIVATED result for a non-pending action. Ignoring.");
                }
                break;

            case RESULT_FAILED_TO_ACTIVATE:
                // This is the failure case for an activation request.
                if (actionState.mState == ActionState.STATE_PENDING_ACTIVATION) {
                    Slog.w(TAG, "Action " + ActionRequest.actionToString(action)
                            + " FAILED to activate.");
                    // Broadcast the failure, then completely clean up the state.
                    broadcastToRequestingServices(actionState, associationId, result);
                    associationActions.remove(action);
                } else {
                    Slog.w(TAG, "Received FAILED_TO_ACTIVATE result for a non-pending action."
                                    + "Ignoring.");
                }
                break;

            case RESULT_DEACTIVATED:
                if (actionState.mState == ActionState.STATE_ACTIVE
                    || actionState.mState == ActionState.STATE_IDLE) {
                    Slog.i(TAG, "Action " + ActionRequest.actionToString(action)
                            + " is now DEACTIVATED.");

                    broadcastToRequestingServices(actionState, associationId, result);
                    associationActions.remove(action);
                } else {
                    Slog.w(TAG, "Ignoring stale RESULT_DEACTIVATED for action "
                        + ActionRequest.actionToString(action)
                        + " which is in PENDING_ACTIVATION state.");
                }
                break;
        }
    }

    /**
     * Dump the ActionRequestProcessor's state.
     */
    public void dump(@NonNull PrintWriter out) {
        out.println("  ActionRequestProcessor State:");

        if (mActiveActionStates.isEmpty()) {
            out.println("    mActiveActionStates: <empty>");
        } else {
            out.println("    mActiveActionStates:");
            for (Map.Entry<Integer, Map<Integer, ActionState>> entry :
                    mActiveActionStates.entrySet()) {
                final int associationId = entry.getKey();
                final Map<Integer, ActionState> actions = entry.getValue();
                out.println("      Association " + associationId + ":");
                for (Map.Entry<Integer, ActionState> actionEntry : actions.entrySet()) {
                    final int action = actionEntry.getKey();
                    final ActionState actionState = actionEntry.getValue();
                    final String stateString = switch (actionState.mState) {
                        case ActionState.STATE_IDLE -> "IDLE";
                        case ActionState.STATE_PENDING_ACTIVATION -> "PENDING_ACTIVATION";
                        case ActionState.STATE_ACTIVE -> "ACTIVE";
                        default -> "UNKNOWN";
                    };

                    out.println("        "
                            + ActionRequest.actionToString(action) + ": "
                            + "state=" + stateString + ", "
                            + "services=" + actionState.mRequestingServices);
                }
            }
        }

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

    /**
     * Set the requestAction allowlist.
     * @hide
     */
    public void setRequestActionAllowList(@Nullable List<String> allowList) {
        Slog.i(TAG, "Setting requestAction allow-list to: " + allowList);
        if (allowList == null) {
            mRequestActionAllowList = null;
        } else {
            mRequestActionAllowList = new ArrayList<>(allowList);
        }
    }

    private void handleActionRequest(@NonNull AssociationInfo association,
            @NonNull ActionRequest request, @NonNull String serviceName) {
        final int associationId = association.getId();
        final int action = request.getAction();
        final int operation = request.getOperation();

        // Get or create the state holder for this action and association.
        final ActionState actionState = mActiveActionStates
                .computeIfAbsent(associationId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(action, k -> new ActionState());

        switch (operation) {
            case (ActionRequest.OP_ACTIVATE):
                // Add the service to the list of requesters regardless of the current state.
                actionState.mRequestingServices.add(serviceName);

                if (actionState.mState == ActionState.STATE_IDLE) {
                    // First service to request this action.
                    Slog.i(TAG, "First service [" + serviceName + "]. Activating action="
                            + ActionRequest.actionToString(action) + " for association "
                            + associationId);

                    // Transition the state to PENDING and send the request to the app.
                    actionState.mState = ActionState.STATE_PENDING_ACTIVATION;
                    postActionRequest(association, request);

                } else if (actionState.mState == ActionState.STATE_PENDING_ACTIVATION) {
                    // App has not yet sent back a response.
                    Slog.i(TAG, "Service [" + serviceName + "] requested an action that "
                            + "is PENDING. It will be notified when the result arrives.");
                    // DO NOTHING. The service is now "listening" for the pending result.

                } else if (actionState.mState == ActionState.STATE_ACTIVE) {
                    // Companion App has already sent a SUCCESS response.
                    Slog.i(TAG, "Service [" + serviceName + "] requested "
                            + "an already ACTIVE action. Sending immediate confirmation.");
                    sendImmediateCallback(associationId, request, serviceName, RESULT_ACTIVATED);
                }
                break;

            case (ActionRequest.OP_DEACTIVATE):
                if (actionState.mRequestingServices.remove(serviceName)) {
                    if (actionState.mRequestingServices.isEmpty()) {
                        // This was the LAST service.
                        Slog.i(TAG, "Last service [" + serviceName + "]. Deactivating action="
                                + ActionRequest.actionToString(action) + " for association "
                                + associationId);

                        // Send deactivation if the action was either confirmed active OR if an
                        // activation request is currently in flight.
                        if (actionState.mState == ActionState.STATE_ACTIVE
                                || actionState.mState == ActionState.STATE_PENDING_ACTIVATION) {
                            // Immediately reset the state to IDLE to prevent a race condition
                            // where a quick deactivate then activate sequence request would
                            // cause the new activation request to be missed.
                            actionState.mState = ActionState.STATE_IDLE;
                            postActionRequest(association, request);
                        }
                    } else {
                        // Other services are still using it.
                        Slog.i(TAG, "Service [" + serviceName + "] requested to stop action, "
                                + "but other services are still using it.");
                    }
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

    private void sendImmediateCallback(int associationId, @NonNull ActionRequest request,
            @NonNull String serviceName, int resultCode) {
        final IOnActionResultListener listener = findListenerForService(serviceName);
        if (listener == null) {
            Slog.w(TAG, "Could not find listener for service " + serviceName
                    + " to send immediate callback with result code " + resultCode);
            return;
        }

        final ActionResult immediateResult = new ActionResult.Builder(
                request.getAction(), resultCode).build();

        try {
            listener.onActionResult(associationId, immediateResult);
        } catch (RemoteException e) {
            Slog.e(TAG, "Error sending immediate callback for " + serviceName, e);
        }
    }

    /**
     * Helper method to broadcast a result to all services that requested the action.
     */
    private void broadcastToRequestingServices(ActionState actionState, int associationId,
            @NonNull ActionResult result) {
        for (String serviceName : actionState.mRequestingServices) {
            final IOnActionResultListener listener = findListenerForService(serviceName);
            if (listener != null) {
                try {
                    android.os.Trace.asyncTraceForTrackEnd(
                            Trace.TRACE_TAG_SYSTEM_SERVER,
                            "CompanionDeviceManager",
                            result.hashCode()
                    );

                    listener.onActionResult(associationId, result);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error sending action result to " + serviceName, e);
                }
            }
        }
    }

    /**
     * Finds the registered IOnActionResultListener for a given service name.
     */
    @Nullable
    private IOnActionResultListener findListenerForService(String serviceName) {
        for (ActionResultListener listenerWrapper : mActionResultListeners) {
            if (listenerWrapper.mServiceName.equals(serviceName)) {
                return listenerWrapper.mListener;
            }
        }
        return null;
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

    private static final class ActionState {
        private static final int STATE_IDLE = 0;
        private static final int STATE_PENDING_ACTIVATION = 1;
        private static final int STATE_ACTIVE = 2;

        int mState = STATE_IDLE;
        final Set<String> mRequestingServices = ConcurrentHashMap.newKeySet();
    }

    private boolean isRequestBlocked(String serviceName) {
        // If the allow-list is null, test mode is off. Do not block.
        if (mRequestActionAllowList == null) {
            return false;
        }

        // Test mode is ON. If the allow-list is empty, block ALL requests.
        if (mRequestActionAllowList.isEmpty()) {
            Slog.w(TAG, "Blocking background request from [" + serviceName + "] "
                    + "because test mode is active and allow-list is empty.");
            return true;
        }


        // Test mode is ON. If the service is on the list, it is NOT blocked.
        if (mRequestActionAllowList.contains(serviceName)) {
            return false;
        }

        // The test mode is active, and the service was not on the list. Block it.
        Slog.w(TAG, "Blocking background request from [" + serviceName + "] "
                + "because test mode is active and it is not in the allow-list: "
                + mRequestActionAllowList);
        return true;
    }
}
