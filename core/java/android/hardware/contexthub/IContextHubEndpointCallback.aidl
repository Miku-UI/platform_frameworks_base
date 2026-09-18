/*
 * Copyright 2024 The Android Open Source Project
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

package android.hardware.contexthub;

import android.hardware.contexthub.DataFlowId;
import android.hardware.contexthub.DataFlowSinkContext;
import android.hardware.contexthub.HubEndpointInfo;
import android.hardware.contexthub.HubMessage;
import android.hardware.contexthub.HubServiceInfo;

/**
 * @hide
 */
oneway interface IContextHubEndpointCallback {
    /**
     * Request from system service to open a session, requested by a specific initiator.
     *
     * @param sessionId An integer identifying the session, assigned by the initiator
     * @param initiator HubEndpointInfo representing the requester
     * @param serviceDescriptor Nullable string representing the service associated with this
     *         session
     */
    void onSessionOpenRequest(int sessionId, in HubEndpointInfo initiator,
            in @nullable String serviceDescriptor);

    /**
     * Request from system service to close a specific session
     *
     * @param sessionId An integer identifying the session
     * @param reason An integer identifying the reason
     */
    void onSessionClosed(int sessionId, int reason);

    /**
     * Notifies the system service that the session requested by IContextHubEndpoint.openSession
     * is ready to use.
     *
     * @param sessionId The integer representing the communication session, previously set in
     *         IContextHubEndpoint.openSession(). This id is assigned by the HAL.
     */
    void onSessionOpenComplete(int sessionId);

    /**
     * Message notification from system service for a specific session
     *
     * @param sessionId The integer representing the communication session, previously set in
     *         IContextHubEndpoint.openSession(). This id is assigned by the HAL.
     * @param message The HubMessage parcelable that represents the message.
     */
    void onMessageReceived(int sessionId, in HubMessage message);

    /**
     * Callback delivering a sink context for a data flow whose source is an offload endpoint.
     *
     * @param context The context used to give the new sink access to the data flow.
     * @param source The offload endpoint which is the source of this data flow.
     * @param msg [optional] An optional message sent by the offload endpoint.
     * @param sessionId [optional] An optional open session id between the data flow source and
     *         the destination endpoint to associate this call with. If msg is provided, this
     *         session can be used to send a MessageDeliveryStatus in response. Ignored if set to
     *         SESSION_ID_INVALID.
     */
    void onDataFlowHostSinkRegistered(in DataFlowSinkContext context, in HubEndpointInfo source,
            in @nullable HubMessage msg, int sessionId);

    /**
     * Callback notifying this endpoint that an endpoint on the other side of a data flow has
     * stopped using it. This callback will only be invoked for a data flow that this endpoint is
     * currently producing to or consuming from. It will be called both in the case that the offload
     * endpoint crashed and when the endpoint intentionally stops accessing the data flow.
     *
     * If this endpoint is a sink on the data flow, it must stop accessing it and release
     * resources associated with the data flow. If it is the source, it must release resources
     * associated with the other endpoint.
     *
     * @param dataFlowId The id of the active data flow.
     * @param endpoint The endpoint that is no longer attached to the data flow.
     */
    void onDataFlowOffloadEndpointUnregistered(
            in DataFlowId dataFlowId, in @nullable HubEndpointInfo endpoint);

    /**
     * Callback notifying this endpoint that the provided data flows are no longer accessible, e.g.
     * due to runtime permission changes.
     *
     * @param dataFlowIds The ids of the data flows that are no longer accessible.
     */
    void onDataFlowsInaccessible(in DataFlowId[] dataFlowIds);
}
