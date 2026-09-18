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
package com.android.server.companion.datatransfer.crossdevicesync.network.messenger;

import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;

import java.util.Set;
import java.util.concurrent.Executor;

/** Interface for exchanging messages with remote devices. */
public interface Messenger extends Dumpable {

    /** Initialize the messenger. */
    void init();

    /** Destroy the messenger. */
    void destroy();

    /**
     * Send message to a remote device in the same network.
     *
     * @param networkId the id of the network this message belongs to.
     * @param associationId the association id to send the message to.
     * @param message the message payload.
     * @param maxDeliveryAttempts the number of attempts to send the message if delivery fails.
     * @return a future that completes when the message is sent or failed.
     */
    AndroidFuture<?> sendMessage(
            String networkId, int associationId, byte[] message, int maxDeliveryAttempts);

    /** Register a message listener. */
    void registerMessageListener(Executor executor, MessageListener listener);

    /** Unregister a message listener. */
    void unregisterMessageListener(MessageListener listener);

    /**
     * Notify the messenger of transports change. Messages will not be sent until transport is
     * attached.
     */
    void onTransportsChanged(Set<Integer> associationsWithTransport);

    /** Message listener for monitoring incoming messages. */
    interface MessageListener {
        /**
         * Called when an incoming message is received.
         *
         * @param networkId the id of the network this message belongs to.
         * @param associationId the association id of the source device.
         * @param message the message payload.
         */
        void onMessage(String networkId, int associationId, byte[] message);
    }
}
