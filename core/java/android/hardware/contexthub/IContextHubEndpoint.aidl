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
import android.hardware.contexthub.DataFlowInfo;
import android.hardware.contexthub.DataFlowSinkContext;
import android.hardware.contexthub.HubEndpointInfo;
import android.hardware.contexthub.HubMessage;
import android.hardware.contexthub.HubServiceInfo;
import android.hardware.contexthub.SharedDataCapabilities;
import android.hardware.contexthub.SharedDataRegion;
import android.hardware.contexthub.SharedDataRegionRequirements;
import android.hardware.location.IContextHubTransactionCallback;

/**
 * @hide
 */
interface IContextHubEndpoint {
    /** Invalid session id. */
    const int SESSION_ID_INVALID = -1;

    /**
     * Retrieve the up-to-date EndpointInfo, with assigned endpoint id.
     */
    HubEndpointInfo getAssignedHubEndpointInfo();

    /**
     * Request system service to open a session with a specific destination.
     *
     * @param destination A valid HubEndpointInfo representing the destination.
     * @param serviceDescriptor An optional descriptor of the service to scope this session to.
     *
     * @throws IllegalArgumentException If the HubEndpointInfo is not valid.
     * @throws IllegalStateException If there are too many opened sessions.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    int openSession(in HubEndpointInfo destination, in @nullable String serviceDescriptor);

    /**
     * Request system service to close a specific session
     *
     * @param sessionId An integer identifying the session, assigned by system service
     * @param reason An integer identifying the reason
     *
     * @throws IllegalStateException If the session wasn't opened.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void closeSession(int sessionId, int reason);

    /**
     * Callback when a session is opened. This callback is the status callback for a previous
     * IContextHubEndpointCallback.onSessionOpenRequest().
     *
     * @param sessionId The integer representing the communication session, previously set in
     *         onSessionOpenRequest().
     *
     * @throws IllegalStateException If the session wasn't opened.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void openSessionRequestComplete(int sessionId);

    /**
     * Unregister this endpoint from the HAL, invalidate the EndpointInfo previously assigned.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void unregister();

    /**
     * Send a message parcelable to system service for a specific session.
     *
     * @param sessionId The integer representing the communication session, previously set in
     *         IContextHubEndpoint.openSession(). This id is assigned by the HAL.
     * @param message The HubMessage parcelable that represents the message and its delivery
     *         options.
     * @param transactionCallback Nullable. If the hub message requires a reply, the
     *         transactionCallback
     *                            will be set to non-null.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void sendMessage(int sessionId, in HubMessage message,
            in @nullable IContextHubTransactionCallback transactionCallback);

    /**
     * Send a message delivery status to system service for a specific message
     *
     * @param sessionId The integer representing the communication session, previously set in
     *         IContextHubEndpoint.openSession(). This id is assigned by the HAL.
     * @param messageSeqNumber The message sequence number, this should match a previously received
     *         HubMessage.
     * @param errorCode The message delivery status detail.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void sendMessageDeliveryStatus(int sessionId, int messageSeqNumber, byte errorCode);

    /**
     * Invoked when a callback from IContextHubEndpointCallback finishes.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    oneway void onCallbackFinished();

    /**
     * Allocates a shared data region for publishing data flows to offload endpoints.
     *
     * @param requirements The requirements for the allocation, including size.
     *
     * @return The newly allocated region.
     *
     * @throws IllegalArgumentException if any of the requirements are invalid.
     * @throws UnsupportedOperationException if shared data regions are not supported.
     * @throws ServerSpecificException if the allocation failed due to insufficient memory or an
     *         unsupported target hub configuration.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    SharedDataRegion allocateSharedDataRegion(in SharedDataRegionRequirements requirements);

    /**
     * Frees a previously allocated shared data region.
     *
     * @param id The system service-assigned id of the region to free. Must have been previously
     *         successfully returned by allocateSharedDataRegion() and not already freed.
     *
     * @throws IllegalArgumentException if the id wasn't previously successfully assigned to a
     *         region by allocateSharedDataRegion().
     * @throws IllegalStateException if the region is in use.
     * @throws UnsupportedOperationException if shared data regions are not supported.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void freeSharedDataRegion(int id);

    /**
     * Registers a new data flow from a host source in the given shared data region.
     *
     * @param endpoint The endpoint that will produce on this data flow.
     * @param info The information about the data flow to register.
     *
     * @return An id scoped to this message hub representing the new data flow.
     *
     * @throws IllegalArgumentException if the region doesn't exist or is not active, or if the data
     *         flow metadata offset is invalid.
     * @throws UnsupportedOperationException if shared data regions are not supported.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    int registerDataFlowHostSource(in DataFlowInfo info);

    /**
     * Unregisters the data flow with given id.
     *
     * NOTE: This does not guarantee that sinks that have access to the data flow will stop
     * accessing it. It will clean up the state that helps propagate data flow alerts in
     * either direction.
     *
     * @param id The id of the data flow to remove. Must have been successfully returned by
     *         registerDataFlowHostSource() and not already removed.
     *
     * @throws IllegalArgumentException if the id is unknown.
     * @throws UnsupportedOperationException if shared data regions are not supported.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void unregisterDataFlowHostSource(int id);

    /**
     * Sends a sink context for a data flow on this hub to an offload endpoint.
     *
     * The service will call IRegisterOffloadSinkCallback::addSinkInRegion() from the
     * thread servicing this call to provide a region for allocating the sink descriptor. The
     * sink descriptor is allocated at that time and its descriptor is passed back to the system
     * service as the return value.
     *
     * @param context The context used to give the new sink access to the data flow.
     * @param sink The offload endpoint that will read from this data flow.
     * @param callback The callback to provide additional information to the system service within
     *         this call.
     * @param msg [optional] An optional message to send to the offload endpoint.
     * @param sessionId [optional] An optional open session id between the data flow source and
     *         the destination endpoint to associate this call with. If msg is provided, this
     *         session can be used to send a MessageDeliveryStatus in response. Ignored if set to
     *         SESSION_ID_INVALID.
     * @param transactionCallback [optional] Set if the client requires a reply to this call.
     *
     * @throws IllegalArgumentException if the data flow doesn't exist or is not active, or if the
     *         sink context is invalid.
     * @throws SecurityException if the sink endpoint is not allowed to access the data flow.
     * @throws UnsupportedOperationException if shared data regions are not supported.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void registerDataFlowOffloadSink(in DataFlowSinkContext context, in HubEndpointInfo sink,
            in IRegisterOffloadSinkCallback callback, in @nullable HubMessage msg,
            int sessionId, in @nullable IContextHubTransactionCallback transactionCallback);

    /**
     * Releases system service resources associated with the client reading from a data flow
     * received via IContextHubEndpointCallback::registerDataFlowHostSink().
     *
     * @param dataFlowId The id of the data flow to release.
     *
     * @throws IllegalArgumentException if the data flow doesn't exist.
     * @throws UnsupportedOperationException if shared data regions are not supported.
     */
    @EnforcePermission("ACCESS_CONTEXT_HUB")
    void unregisterDataFlowHostSink(in DataFlowId dataFlowId);

    /**
     * An interface to nest callbacks from the system service to the client within
     * registerDataFlowOffloadSink().
     *
     * @hide
     */
    interface IRegisterOffloadSinkCallback {
        /**
         * Provides an optional region for allocating the sink descriptor. The system service
         * will call this while servicing IContextHubEndpoint::registerDataFlowOffloadSink().
         *
         * @param region The shared data region to allocate the sink descriptor from. If null,
         *         the descriptor will be allocated from the primary region returned by
         *         allocateSharedDataRegion(). Otherwise, the descriptor will be allocated in the
         *         given region.
         *
         * @return The offset of the sink descriptor in the provided region if not null or in
         *         the primary region.
         */
        long addSinkInRegion(in @nullable SharedDataRegion region);
    }
}
