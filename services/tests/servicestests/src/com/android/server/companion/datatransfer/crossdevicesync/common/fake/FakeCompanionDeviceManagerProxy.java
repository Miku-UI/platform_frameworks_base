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
package com.android.server.companion.datatransfer.crossdevicesync.common.fake;

import android.annotation.UserIdInt;
import android.companion.ActionRequest;
import android.companion.ActionResult;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager.OnAssociationsChangedListener;
import android.companion.DevicePresenceEvent;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.server.companion.datatransfer.crossdevicesync.common.CompanionDeviceManagerProxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** A fake implementation of {@link CompanionDeviceManagerProxy} for testing. */
public class FakeCompanionDeviceManagerProxy implements CompanionDeviceManagerProxy {
    private static final String TAG = "FakeCDM";

    private record AssociationListener(
            Executor executor, OnAssociationsChangedListener listener, int userId) {}

    private record TransportListener(Executor executor, Consumer<List<AssociationInfo>> listener) {}

    private record MessageListener(Executor executor, BiConsumer<Integer, byte[]> listener) {}

    public record DevicePresenceListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            Consumer<DevicePresenceEvent> listener) {}

    private record SentMessageListener(
            int messageType, Consumer<SentMessage> listener, Executor executor) {}

    public record ActionResultListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            BiConsumer<Integer, ActionResult> listener) {}

    private final Map<Integer, List<AssociationRecord>> mAssociations = new HashMap<>();
    private final List<AssociationListener> mAssociationListeners = new ArrayList<>();
    private final List<TransportListener> mTransportListeners = new ArrayList<>();
    private final Map<Integer, List<MessageListener>> mMessageListeners = new HashMap<>();
    private final List<DevicePresenceListener> mDevicePresenceListeners = new ArrayList<>();
    private final List<SentMessage> mSentMessages = new ArrayList<>();
    private final List<SentMessageListener> mSentMessageListeners = new ArrayList<>();
    private final List<ActionResultListener> mActionResultListeners = new ArrayList<>();
    private final Map<Integer, PersistableBundle> mMetadata = new HashMap<>();

    public FakeCompanionDeviceManagerProxy() {}

    /** Returns the list of registered device presence listeners. */
    public List<DevicePresenceListener> getDevicePresenceListeners() {
        return mDevicePresenceListeners;
    }

    @Override
    public List<AssociationInfo> getAllAssociations(@UserIdInt int userId) {
        if (userId == UserHandle.USER_ALL) {
            return mAssociations.values().stream()
                    .flatMap(List::stream)
                    .map(r -> r.associationInfo)
                    .toList();
        }
        return mAssociations.getOrDefault(userId, Collections.emptyList()).stream()
                .map(r -> r.associationInfo)
                .toList();
    }

    @Override
    public List<AssociationInfo> getAllAssociationsWithTransports() {
        return mAssociations.values().stream()
                .flatMap(List::stream)
                .filter(r -> r.hasTransport)
                .map(r -> r.associationInfo)
                .toList();
    }

    @Override
    public void addOnAssociationsChangedListener(
            Executor executor, OnAssociationsChangedListener listener, @UserIdInt int userId) {
        mAssociationListeners.add(new AssociationListener(executor, listener, userId));
    }

    @Override
    public void removeOnAssociationsChangedListener(OnAssociationsChangedListener listener) {
        mAssociationListeners.removeIf(l -> l.listener.equals(listener));
    }

    @Override
    public void addOnTransportsChangedListener(
            Executor executor, Consumer<List<AssociationInfo>> listener) {
        mTransportListeners.add(new TransportListener(executor, listener));
        executor.execute(() -> listener.accept(getAllAssociationsWithTransports()));
    }

    @Override
    public void removeOnTransportsChangedListener(Consumer<List<AssociationInfo>> listener) {
        mTransportListeners.removeIf(l -> l.listener.equals(listener));
    }

    @Override
    public void setOnDevicePresenceEventListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            Consumer<DevicePresenceEvent> listener) {
        mDevicePresenceListeners.add(
                new DevicePresenceListener(associationIds, serviceName, executor, listener));
        List<DevicePresenceEvent> events =
                mAssociations.values().stream()
                        .flatMap(
                                records -> {
                                    List<DevicePresenceEvent> list = new ArrayList<>();
                                    for (AssociationRecord r : records) {
                                        if (r.associationRemovedEvent != null) {
                                            list.add(r.associationRemovedEvent);
                                            continue;
                                        }
                                        if (r.blePresenceEvent != null) {
                                            list.add(r.blePresenceEvent);
                                        }
                                        if (r.btConnectedEvent != null) {
                                            list.add(r.btConnectedEvent);
                                        }
                                        if (r.selfManagedAppearedEvent != null) {
                                            list.add(r.selfManagedAppearedEvent);
                                        }
                                        if (r.selfManagedNearbyEvent != null) {
                                            list.add(r.selfManagedNearbyEvent);
                                        }
                                    }
                                    return list.stream();
                                })
                        .toList();
        if (!events.isEmpty()) {
            events.forEach(e -> executor.execute(() -> listener.accept(e)));
        }
    }

    @Override
    public void removeOnDevicePresenceEventListener(String serviceName) {
        mDevicePresenceListeners.removeIf(l -> l.serviceName.equals(serviceName));
    }

    @Override
    public void sendMessage(int messageType, byte[] data, int[] associationIds) {
        for (int associationId : associationIds) {
            AssociationRecord r = findAssociationById(associationId);
            if (r == null || !r.hasTransport) {
                return;
            }
        }
        SentMessage sentMessage = new SentMessage(messageType, data, associationIds);
        mSentMessages.add(sentMessage);
        for (SentMessageListener listener : mSentMessageListeners) {
            if (listener.messageType == messageType) {
                listener.executor.execute(() -> listener.listener.accept(sentMessage));
            }
        }
    }

    public void addSentMessageListener(
            int messageType, Consumer<SentMessage> listener, Executor executor) {
        mSentMessageListeners.add(new SentMessageListener(messageType, listener, executor));
    }

    @Override
    public void addOnMessageReceivedListener(
            Executor executor, int messageType, BiConsumer<Integer, byte[]> listener) {
        mMessageListeners
                .computeIfAbsent(messageType, k -> new ArrayList<>())
                .add(new MessageListener(executor, listener));
    }

    @Override
    public void removeOnMessageReceivedListener(
            int messageType, BiConsumer<Integer, byte[]> listener) {
        if (mMessageListeners.containsKey(messageType)) {
            mMessageListeners.get(messageType).removeIf(l -> l.listener().equals(listener));
        }
    }

    @Override
    public void requestAction(ActionRequest request, String serviceName, int[] associationIds) {
        Log.d(TAG, "Received action request: " + request);
        boolean isActivateOp = request.getOperation() == ActionRequest.OP_ACTIVATE;
        for (int associationId : associationIds) {
            AssociationRecord associationRecord = findAssociationById(associationId);
            if (associationRecord == null) {
                continue;
            }
            switch (request.getAction()) {
                case ActionRequest.REQUEST_NEARBY_SCANNING -> {
                    if (isActivateOp) {
                        associationRecord.servicesRequestedScanning.add(serviceName);
                    } else {
                        associationRecord.servicesRequestedScanning.remove(serviceName);
                    }
                    if (isActivateOp == associationRecord.scanningRequestSucceeded) {
                        notifyActionResult(
                                associationId,
                                request.getAction(),
                                isActivateOp
                                        ? ActionResult.RESULT_ACTIVATED
                                        : ActionResult.RESULT_DEACTIVATED,
                                serviceName);
                    }
                }
                case ActionRequest.REQUEST_NEARBY_ADVERTISING -> {
                    if (isActivateOp) {
                        associationRecord.servicesRequestedAdvertising.add(serviceName);
                    } else {
                        associationRecord.servicesRequestedAdvertising.remove(serviceName);
                    }
                    if (isActivateOp == associationRecord.advertisingRequestSucceeded) {
                        notifyActionResult(
                                associationId,
                                request.getAction(),
                                isActivateOp
                                        ? ActionResult.RESULT_ACTIVATED
                                        : ActionResult.RESULT_DEACTIVATED,
                                serviceName);
                    }
                }
                case ActionRequest.REQUEST_TRANSPORT -> {
                    if (isActivateOp) {
                        associationRecord.servicesRequestedTransport.add(serviceName);
                    } else {
                        associationRecord.servicesRequestedTransport.remove(serviceName);
                    }
                    if (isActivateOp == associationRecord.transportAttachRequestSucceeded) {
                        notifyActionResult(
                                associationId,
                                request.getAction(),
                                isActivateOp
                                        ? ActionResult.RESULT_ACTIVATED
                                        : ActionResult.RESULT_DEACTIVATED,
                                serviceName);
                    }
                }
            }
        }
    }

    @Override
    public void setOnActionResultListener(
            int[] associationIds,
            String serviceName,
            Executor executor,
            BiConsumer<Integer, ActionResult> listener) {
        mActionResultListeners.add(
                new ActionResultListener(associationIds, serviceName, executor, listener));
    }

    @Override
    public void clearOnActionResultListener(String serviceName) {
        mActionResultListeners.removeIf(l -> l.serviceName().equals(serviceName));
    }

    @Override
    public void setLocalMetadata(int userId, String feature, PersistableBundle value) {
        mMetadata
                .computeIfAbsent(userId, k -> new PersistableBundle())
                .putPersistableBundle(feature, value);
    }

    @Override
    public PersistableBundle getLocalMetadata(int userId) {
        PersistableBundle data = mMetadata.get(userId);
        return data == null ? new PersistableBundle() : data;
    }

    public boolean hasActionRequest(String serviceName, int associationId, int actionId) {
        AssociationRecord r = findAssociationById(associationId);
        if (r == null) {
            return false;
        }
        return switch (actionId) {
            case ActionRequest.REQUEST_TRANSPORT ->
                    r.servicesRequestedTransport.contains(serviceName);
            case ActionRequest.REQUEST_NEARBY_ADVERTISING ->
                    r.servicesRequestedAdvertising.contains(serviceName);
            case ActionRequest.REQUEST_NEARBY_SCANNING ->
                    r.servicesRequestedScanning.contains(serviceName);
            default -> false;
        };
    }

    public void simulateActionResult(int associationId, int actionId, boolean activated) {
        AssociationRecord association = findAssociationById(associationId);
        if (association == null) {
            Log.w(TAG, "Ignoring result for unknown association: " + associationId);
            return;
        }
        boolean changed = false;
        Set<String> requestedServices =
                switch (actionId) {
                    case ActionRequest.REQUEST_NEARBY_SCANNING -> {
                        if (association.scanningRequestSucceeded != activated) {
                            association.scanningRequestSucceeded = activated;
                            changed = true;
                        }
                        yield association.servicesRequestedScanning;
                    }
                    case ActionRequest.REQUEST_NEARBY_ADVERTISING -> {
                        if (association.advertisingRequestSucceeded != activated) {
                            association.advertisingRequestSucceeded = activated;
                            changed = true;
                        }
                        yield association.servicesRequestedAdvertising;
                    }
                    case ActionRequest.REQUEST_TRANSPORT -> {
                        if (association.transportAttachRequestSucceeded != activated) {
                            association.transportAttachRequestSucceeded = activated;
                            changed = true;
                        }
                        yield association.servicesRequestedTransport;
                    }
                    default -> throw new IllegalArgumentException("Unknown actionId: " + actionId);
                };
        boolean requestedActive = !requestedServices.isEmpty();
        if (requestedActive && !activated) {
            requestedServices.clear();
            changed = true;
        }
        if (changed) {
            Log.d(TAG, "Notifying action results.");
            notifyActionResult(
                    associationId,
                    actionId,
                    activated ? ActionResult.RESULT_ACTIVATED : ActionResult.RESULT_DEACTIVATED,
                    /* serviceName= */ null);
        } else {
            Log.w(TAG, "Ignoring result for action: " + actionId + ", activated=" + activated);
        }
    }

    private void notifyActionResult(
            int associationId, int actionId, int result, @Nullable String serviceName) {
        ActionResult actionResult = new ActionResult.Builder(actionId, result).build();
        for (ActionResultListener listener : new ArrayList<>(mActionResultListeners)) {
            if (serviceName == null || listener.serviceName.equals(serviceName)) {
                listener.executor.execute(
                        () -> listener.listener.accept(associationId, actionResult));
            }
        }
    }

    /** Simulates a device presence event. */
    public void simulateDevicePresenceEvent(DevicePresenceEvent event) {
        AssociationRecord r = findAssociationById(event.getAssociationId());
        if (r == null) {
            return;
        }
        r.recordPresenceEvent(event);
        for (DevicePresenceListener listener : mDevicePresenceListeners) {
            for (int associationId : listener.associationIds) {
                if (associationId == event.getAssociationId()) {
                    listener.executor.execute(() -> listener.listener.accept(event));
                    break;
                }
            }
        }
    }

    /** Sets the list of associations with transports for testing. */
    public void setTransportAttachedState(int associationId, boolean hasTransport) {
        AssociationRecord r = findAssociationById(associationId);
        if (r == null) {
            return;
        }
        if (r.setHasTransport(hasTransport)) {
            List<AssociationInfo> associationsWithTransports = getAllAssociationsWithTransports();
            for (TransportListener transportListener : mTransportListeners) {
                transportListener.executor.execute(
                        () -> transportListener.listener.accept(associationsWithTransports));
            }
        }
    }

    /** Adds an association for the given user and notifies listeners. */
    public void addAssociation(AssociationInfo associationInfo) {
        AssociationRecord r = findAssociationById(associationInfo.getId());
        if (r != null) {
            if (r.associationInfo.equals(associationInfo)) {
                return;
            }
            r.updateInfo(associationInfo);
        } else {
            List<AssociationRecord> records =
                    mAssociations.computeIfAbsent(
                            associationInfo.getUserId(), k -> new ArrayList<>());
            r = new AssociationRecord(associationInfo);
            records.add(r);
        }
        notifyAssociationsChanged(r.associationInfo.getUserId());
    }

    /** Removes an association for the given user and notifies listeners. */
    public void removeAssociation(int associationId) {
        AssociationRecord r = findAssociationById(associationId);
        if (r == null) {
            return;
        }
        List<AssociationRecord> records = mAssociations.get(r.associationInfo.getUserId());
        boolean removed = records.remove(r);
        if (removed) {
            notifyAssociationsChanged(r.associationInfo.getUserId());
        }
    }

    private void notifyAssociationsChanged(int userId) {
        List<AssociationInfo> userAssociations = getAllAssociations(userId);
        List<AssociationInfo> allAssociations = getAllAssociations(UserHandle.USER_ALL);
        for (AssociationListener associationListener : mAssociationListeners) {
            if (associationListener.userId == userId) {
                associationListener.executor.execute(
                        () -> associationListener.listener.onAssociationsChanged(userAssociations));
            } else if (associationListener.userId == UserHandle.USER_ALL) {
                associationListener.executor.execute(
                        () -> associationListener.listener.onAssociationsChanged(allAssociations));
            }
        }
    }

    /** Simulates receiving a message and notifies the appropriate listeners. */
    public void simulateMessageReceived(int messageType, int associationId, byte[] data) {
        if (findAssociationById(associationId) == null) {
            return;
        }

        if (mMessageListeners.containsKey(messageType)) {
            for (MessageListener messageListener : mMessageListeners.get(messageType)) {
                messageListener.executor.execute(
                        () -> messageListener.listener.accept(associationId, data));
            }
        }
    }

    @Nullable
    private AssociationRecord findAssociationById(int associationId) {
        return mAssociations.values().stream()
                .flatMap(List::stream)
                .filter(r -> r.associationId == associationId)
                .findFirst()
                .orElse(null);
    }

    /** Returns the list of messages sent via {@link #sendMessage}. */
    public List<SentMessage> getSentMessages() {
        return mSentMessages;
    }

    /** Clears the list of sent messages. */
    public void clearSentMessages() {
        mSentMessages.clear();
    }

    /** A container for a message sent via {@link #sendMessage}. */
    @SuppressWarnings("ArrayRecordComponent")
    public record SentMessage(int messageType, byte[] data, int[] associationIds) {}

    private static class AssociationRecord {
        public final int associationId;
        public final Set<String> servicesRequestedTransport = new HashSet<>();
        public final Set<String> servicesRequestedScanning = new HashSet<>();
        public final Set<String> servicesRequestedAdvertising = new HashSet<>();
        public AssociationInfo associationInfo;
        public boolean hasTransport;
        @Nullable public DevicePresenceEvent blePresenceEvent;
        @Nullable public DevicePresenceEvent btConnectedEvent;
        @Nullable public DevicePresenceEvent selfManagedAppearedEvent;

        @Nullable public DevicePresenceEvent selfManagedNearbyEvent;

        @Nullable public DevicePresenceEvent associationRemovedEvent;
        public boolean transportAttachRequestSucceeded;
        public boolean scanningRequestSucceeded;
        public boolean advertisingRequestSucceeded;

        AssociationRecord(AssociationInfo info) {
            this.associationId = info.getId();
            this.associationInfo = info;
        }

        public void updateInfo(AssociationInfo associationInfo) {
            this.associationInfo = associationInfo;
        }

        public void recordPresenceEvent(DevicePresenceEvent event) {
            switch (event.getEvent()) {
                case DevicePresenceEvent.EVENT_BLE_APPEARED,
                        DevicePresenceEvent.EVENT_BLE_DISAPPEARED ->
                        blePresenceEvent = event;
                case DevicePresenceEvent.EVENT_BT_CONNECTED,
                        DevicePresenceEvent.EVENT_BT_DISCONNECTED ->
                        btConnectedEvent = event;
                case DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED ->
                        selfManagedAppearedEvent = event;
                case DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY ->
                        selfManagedNearbyEvent = event;
                case DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED ->
                        associationRemovedEvent = event;
            }
        }

        public boolean setHasTransport(boolean hasTransport) {
            if (this.hasTransport == hasTransport) {
                return false;
            }
            this.hasTransport = hasTransport;
            return true;
        }
    }
}
