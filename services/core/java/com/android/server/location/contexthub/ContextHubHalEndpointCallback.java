/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.server.location.contexthub;

import android.hardware.contexthub.DataFlowSinkContext;
import android.hardware.contexthub.DataFlowSinkRegistrationParams;
import android.hardware.contexthub.DataFlowId;
import android.hardware.contexthub.EndpointId;
import android.hardware.contexthub.HubEndpointInfo;
import android.hardware.contexthub.HubMessage;
import android.hardware.contexthub.IEndpointCallback;
import android.hardware.contexthub.Message;
import android.hardware.contexthub.MessageDeliveryStatus;
import android.os.RemoteException;

/** IEndpointCallback implementation. */
public class ContextHubHalEndpointCallback
        extends android.hardware.contexthub.IEndpointCallback.Stub {
    private final IEndpointLifecycleCallback mEndpointLifecycleCallback;
    private final IEndpointSessionCallback mEndpointSessionCallback;
    private final IEndpointDataFlowCallback mEndpointDataFlowCallback;

    /** Interface for listening for endpoint start and stop events. */
    public interface IEndpointLifecycleCallback {
        /** Called when a batch of endpoints started. */
        void onEndpointStarted(HubEndpointInfo[] endpointInfos);

        /** Called when a batch of endpoints stopped. */
        void onEndpointStopped(HubEndpointInfo.HubEndpointIdentifier[] endpointIds, byte reason);
    }

    /** Interface for listening for endpoint session events. */
    public interface IEndpointSessionCallback {
        /** Called when an endpoint session open is requested by the HAL. */
        void onEndpointSessionOpenRequest(
                int sessionId,
                HubEndpointInfo.HubEndpointIdentifier destinationId,
                HubEndpointInfo.HubEndpointIdentifier initiatorId,
                String serviceDescriptor);

        /** Called when a endpoint close session is completed. */
        void onCloseEndpointSession(int sessionId, byte reason);

        /** Called when a requested endpoint open session is completed */
        void onEndpointSessionOpenComplete(int sessionId);

        /** Called when a message is received for the session */
        void onMessageReceived(int sessionId, HubMessage message);

        /** Called when a message delivery status is received for the session */
        void onMessageDeliveryStatusReceived(int sessionId, int sequenceNumber, byte errorCode);
    }

    /** Interface for listening for data flow events. */
    public interface IEndpointDataFlowCallback {
        /** Called when a data flow host sink is registered. */
        void onDataFlowHostSinkRegistered(
                DataFlowSinkContext handle,
                HubEndpointInfo.HubEndpointIdentifier sourceId,
                HubEndpointInfo.HubEndpointIdentifier sinkId,
                HubMessage msg,
                int sessionId);

        /** Called when a data flow offload endpoint is unregistered. */
        void onDataFlowOffloadEndpointUnregistered(
                DataFlowId dataFlowId,
                HubEndpointInfo.HubEndpointIdentifier endpointId,
                HubEndpointInfo.HubEndpointIdentifier[] destinationIds);
    }

    ContextHubHalEndpointCallback(
            IEndpointLifecycleCallback endpointLifecycleCallback,
            IEndpointSessionCallback endpointSessionCallback,
            IEndpointDataFlowCallback endpointDataFlowCallback) {
        mEndpointLifecycleCallback = endpointLifecycleCallback;
        mEndpointSessionCallback = endpointSessionCallback;
        mEndpointDataFlowCallback = endpointDataFlowCallback;
    }

    @Override
    public void onEndpointStarted(android.hardware.contexthub.EndpointInfo[] halEndpointInfos)
            throws RemoteException {
        if (halEndpointInfos.length == 0) {
            return;
        }
        HubEndpointInfo[] endpointInfos = new HubEndpointInfo[halEndpointInfos.length];
        for (int i = 0; i < halEndpointInfos.length; i++) {
            endpointInfos[i] = new HubEndpointInfo(halEndpointInfos[i]);
        }
        mEndpointLifecycleCallback.onEndpointStarted(endpointInfos);
    }

    @Override
    public void onEndpointStopped(EndpointId[] halEndpointIds, byte reason) throws RemoteException {
        HubEndpointInfo.HubEndpointIdentifier[] endpointIds =
                new HubEndpointInfo.HubEndpointIdentifier[halEndpointIds.length];
        for (int i = 0; i < halEndpointIds.length; i++) {
            endpointIds[i] = new HubEndpointInfo.HubEndpointIdentifier(halEndpointIds[i]);
        }
        mEndpointLifecycleCallback.onEndpointStopped(endpointIds, reason);
    }

    @Override
    public void onEndpointSessionOpenRequest(
            int sessionId, EndpointId destination, EndpointId initiator, String serviceDescriptor)
            throws RemoteException {
        HubEndpointInfo.HubEndpointIdentifier destinationId =
                new HubEndpointInfo.HubEndpointIdentifier(destination.hubId, destination.id);
        HubEndpointInfo.HubEndpointIdentifier initiatorId =
                new HubEndpointInfo.HubEndpointIdentifier(initiator.hubId, initiator.id);
        mEndpointSessionCallback.onEndpointSessionOpenRequest(
                sessionId, destinationId, initiatorId, serviceDescriptor);
    }

    @Override
    public void onCloseEndpointSession(int sessionId, byte reason) throws RemoteException {
        mEndpointSessionCallback.onCloseEndpointSession(sessionId, reason);
    }

    @Override
    public void onEndpointSessionOpenComplete(int sessionId) throws RemoteException {
        mEndpointSessionCallback.onEndpointSessionOpenComplete(sessionId);
    }

    @Override
    public void onMessageReceived(int sessionId, Message message) throws RemoteException {
        HubMessage hubMessage = ContextHubServiceUtil.createHubMessage(message);
        mEndpointSessionCallback.onMessageReceived(sessionId, hubMessage);
    }

    @Override
    public void onMessageDeliveryStatusReceived(
            int sessionId, MessageDeliveryStatus messageDeliveryStatus) throws RemoteException {
        mEndpointSessionCallback.onMessageDeliveryStatusReceived(
                sessionId,
                messageDeliveryStatus.messageSequenceNumber,
                messageDeliveryStatus.errorCode);
    }

    @Override
    public void onDataFlowHostSinkRegistered(DataFlowSinkRegistrationParams params)
            throws RemoteException {
        mEndpointDataFlowCallback.onDataFlowHostSinkRegistered(
                params.context,
                new HubEndpointInfo.HubEndpointIdentifier(
                        params.sourceId.hubId, params.sourceId.id),
                new HubEndpointInfo.HubEndpointIdentifier(params.sinkId.hubId, params.sinkId.id),
                params.msg == null ? null : ContextHubServiceUtil.createHubMessage(params.msg),
                params.sessionId);
    }

    @Override
    public void onDataFlowOffloadEndpointUnregistered(
            DataFlowId dataFlowId, EndpointId endpointId, EndpointId[] destinationIds)
            throws RemoteException {
        HubEndpointInfo.HubEndpointIdentifier[] hubEndpointDestinationIds =
                new HubEndpointInfo.HubEndpointIdentifier[destinationIds.length];
        for (int i = 0; i < destinationIds.length; i++) {
            hubEndpointDestinationIds[i] =
                    new HubEndpointInfo.HubEndpointIdentifier(
                            destinationIds[i].hubId, destinationIds[i].id);
        }
        mEndpointDataFlowCallback.onDataFlowOffloadEndpointUnregistered(
                dataFlowId,
                new HubEndpointInfo.HubEndpointIdentifier(endpointId.hubId, endpointId.id),
                hubEndpointDestinationIds);
    }

    @Override
    public int getInterfaceVersion() throws RemoteException {
        return IEndpointCallback.VERSION;
    }

    @Override
    public String getInterfaceHash() throws RemoteException {
        return IEndpointCallback.HASH;
    }
}
