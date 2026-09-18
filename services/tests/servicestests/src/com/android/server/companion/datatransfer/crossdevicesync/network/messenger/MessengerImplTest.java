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

import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureFailed;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureSucceeded;
import static com.android.server.companion.datatransfer.crossdevicesync.network.messenger.MessengerImpl.RETRY_DELAY_MS;
import static com.android.server.companion.datatransfer.crossdevicesync.network.messenger.MessengerImpl.WAITING_FOR_ACK_TIMEOUT;
import static com.android.server.companion.datatransfer.crossdevicesync.network.messenger.MessengerImpl.WAITING_FOR_TRANSPORT_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.infra.AndroidFuture;
import com.android.internal.util.ArrayUtils;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.fake.FakeCompanionActionController;
import com.android.server.companion.datatransfer.crossdevicesync.network.model.BatchedMessage;
import com.android.server.companion.datatransfer.crossdevicesync.network.model.Message;
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class MessengerImplTest extends SyncServiceTestBase {
    private static final String TEST_NETWORK = "test_network";

    private MessengerImpl mRemoteMessenger1;
    private FakeCompanionDeviceManagerProxy mRemoteCdm1;
    private MessengerImpl mRemoteMessenger2;
    private boolean mAllowOutboundMessages;
    private boolean mAllowInboundMessages;

    @Before
    public void setUp() {
        mMessengerImpl.init();
        RemoteDeviceContext remoteContext1 = simulateConnectedRemoteDevice(/* associationId= */ 1);
        mRemoteMessenger1 = remoteContext1.getMessenger();
        mRemoteCdm1 = remoteContext1.getCDM();
        RemoteDeviceContext remoteContext2 = simulateConnectedRemoteDevice(/* associationId= */ 2);
        mRemoteMessenger2 = remoteContext2.getMessenger();
        linkDeviceAttachmentEvent();
        mFakeCompanionActionController.init();
    }

    @Test
    public void sendMessage_illegalMaxDeliveryAttempts_fail() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 0);

        assertThat(future.isDone()).isTrue();
        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void sendMessage_requestTransport() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 1);

        assertThat(future.isDone()).isFalse();
        AndroidFuture<?> transportRequestFuture =
                mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1);
        assertThat(transportRequestFuture).isNotNull();
    }

    @Test
    public void sendMessage_requestTransportFailed_retry() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();
        AndroidFuture<?> transportRequestFuture =
                mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1);

        // Transport request failed.
        transportRequestFuture.completeExceptionally(new Exception("transport request failed"));
        assertThat(future.isDone()).isFalse();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();

        mFakeClock.advanceTime(RETRY_DELAY_MS);

        // Verify we are requesting transport again.
        transportRequestFuture =
                mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1);
        assertThat(transportRequestFuture.isDone()).isFalse();
    }

    @Test
    public void sendMessage_requestTransportFailedTooManyTimes_fail() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();

        // First failure
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .completeExceptionally(new Exception("transport request failed"));
        assertThat(future.isDone()).isFalse();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
        mFakeClock.advanceTime(RETRY_DELAY_MS);

        // Second failure
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .completeExceptionally(new Exception("transport request failed"));
        assertThat(future.isDone()).isTrue();
        assertThat(isFutureFailed(future)).isTrue();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_requestTransportFailed_noRetry_fail() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 1);
        assertThat(future.isDone()).isFalse();

        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .completeExceptionally(new Exception("transport request failed"));

        assertThat(future.isDone()).isTrue();
        assertThat(isFutureFailed(future)).isTrue();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_requestTransport_alreadyHasTransport_messageSent() {
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);

        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 1);

        assertThat(future.isDone()).isFalse();
        // Verify we've requested transport attachment
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNotNull();
        // Very we've sent the message.
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);
    }

    @Test
    public void sendMessage_requestTransportFailedAfterAlreadyHasTransport_messageSent() {
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 1);

        // Fail the transport attachment future despite we already have a transport.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .completeExceptionally(new Exception());

        // We are still sending the message.
        assertThat(future.isDone()).isFalse();
        // We didn't send detach request
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNotNull();
    }

    @Test
    public void
            sendMessage_requestTransportFailedAfterAlreadyHasTransport_notAffectingRetryCount() {
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);

        // Fail the transport attachment future despite we already have a transport. This does not
        // increment retry count.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .completeExceptionally(new Exception());
        assertThat(future.isDone()).isFalse();

        // Transport is gone.
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, false);
        assertThat(future.isDone()).isFalse();
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();

        // Retry after timeout.
        mFakeClock.advanceTime(RETRY_DELAY_MS);
        assertThat(future.isDone()).isFalse();
        AndroidFuture<?> attachTransportFuture =
                mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1);
        assertThat(future).isNotNull();

        // Transport attachment failed.
        attachTransportFuture.completeExceptionally(new Exception());
        assertThat(isFutureFailed(future)).isTrue();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_waitForTransportTimeout_retryAfterDetach() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);

        mFakeClock.advanceTime(WAITING_FOR_TRANSPORT_TIMEOUT);

        // Verify we've detached transport.
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();

        mFakeClock.advanceTime(RETRY_DELAY_MS);

        // Verify we are requesting transport again.
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNotNull();
    }

    @Test
    public void sendMessage_waitForTransportTimeout_noRetry_fail() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 1);
        assertThat(future.isDone()).isFalse();
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);

        mFakeClock.advanceTime(WAITING_FOR_TRANSPORT_TIMEOUT);

        assertThat(future.isDone()).isTrue();
        assertThat(isFutureFailed(future)).isTrue();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_waitForTransportTimeoutTooManyTimes_fail() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();

        // First timeout
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeClock.advanceTime(WAITING_FOR_TRANSPORT_TIMEOUT);
        assertThat(future.isDone()).isFalse();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
        mFakeClock.advanceTime(RETRY_DELAY_MS);

        // Second timeout
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeClock.advanceTime(WAITING_FOR_TRANSPORT_TIMEOUT);
        assertThat(future.isDone()).isTrue();
        assertThat(isFutureFailed(future)).isTrue();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_waitForAckTimeout_retry() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        // Verify we've sent message
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);

        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        // Verify we are not done yet.
        assertThat(future.isDone()).isFalse();

        mFakeClock.advanceTime(RETRY_DELAY_MS);
        // Verify we are resending the message.
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(2);
        // We didn't send detach request
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNotNull();
    }

    @Test
    public void sendMessage_waitForAckTimeout_noRetry_fail() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 1);
        assertThat(future.isDone()).isFalse();
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);

        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);

        assertThat(future.isDone()).isTrue();
        assertThat(isFutureFailed(future)).isTrue();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_waitForAckTimeoutTooManyTimes_fail() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);

        // First timeout
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        assertThat(future.isDone()).isFalse();
        mFakeClock.advanceTime(RETRY_DELAY_MS);
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(2);
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNotNull();

        // Second timeout
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        assertThat(future.isDone()).isTrue();
        assertThat(isFutureFailed(future)).isTrue();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_receivedAck_done() {
        // Set up remote device.
        mAllowOutboundMessages = true;
        mAllowInboundMessages = true;
        List<Pair<String, byte[]>> remoteReceivedMessages = new ArrayList<>();
        mRemoteMessenger1.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    remoteReceivedMessages.add(Pair.create(networkId, msg));
                });
        // Allow local device to auto-attach transport
        mFakeCompanionActionController.setAutomaticallyAttachTransport(true);

        // Send message.
        byte[] message = new byte[] {1};
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        message,
                        /* maxDeliveryAttempts= */ 1);

        // Verify message is delivered.
        assertThat(future.isDone()).isTrue();
        assertThat(remoteReceivedMessages).hasSize(1);
        assertMessagesEqual(remoteReceivedMessages.get(0), Pair.create(TEST_NETWORK, message));
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_batchedMessages_succeed() {
        mAllowOutboundMessages = true;
        mAllowInboundMessages = true;
        List<Pair<String, byte[]>> remoteReceivedMessages = new ArrayList<>();
        mRemoteMessenger1.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    remoteReceivedMessages.add(Pair.create(networkId, msg));
                });

        byte[] message1 = new byte[] {1};
        AndroidFuture<Boolean> future1 =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        message1,
                        /* maxDeliveryAttempts= */ 1);
        byte[] message2 = new byte[] {2};
        AndroidFuture<Boolean> future2 =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        message2,
                        /* maxDeliveryAttempts= */ 1);

        // Manually complete the transport request and attach the transport.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);

        assertThat(future1.isDone()).isTrue();
        assertThat(future2.isDone()).isTrue();
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);
        assertThat(remoteReceivedMessages).hasSize(2);
        assertMessagesEqual(remoteReceivedMessages.get(0), Pair.create(TEST_NETWORK, message1));
        assertMessagesEqual(remoteReceivedMessages.get(1), Pair.create(TEST_NETWORK, message2));
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessage_waitForAckTimeout_transportRequestFail_retryAttemptReached_fail() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 3);
        assertThat(future.isDone()).isFalse();

        // 1. Succeed transport request, make transport available, let ACK time out.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        assertThat(future.isDone()).isFalse();

        // 2. Transport lost. Fail the transport request.
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, false);
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
        mFakeClock.advanceTime(RETRY_DELAY_MS);
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .completeExceptionally(new Exception("transport request failed"));
        assertThat(future.isDone()).isFalse();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();

        // 3. Fail the transport request again.
        mFakeClock.advanceTime(RETRY_DELAY_MS);
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .completeExceptionally(new Exception("transport request failed"));
        assertThat(future.isDone()).isTrue();
        assertThat(isFutureFailed(future)).isTrue();
        // Transport detached
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void sendMessageWithRetry_AckComeAfterTimeout_succeed() {
        mAllowOutboundMessages = true;
        mAllowInboundMessages = true;

        // Remote sends a message, max 2 attempts.
        AndroidFuture<Boolean> remoteFuture =
                mRemoteMessenger1.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);

        // Local device receives it, but can't ACK due to no transport.
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNotNull();
        assertThat(remoteFuture.isDone()).isFalse();

        // Remote device times out waiting for ACK.
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        assertThat(remoteFuture.isDone()).isFalse(); // It's now pending retry, but not "done".

        // Now, local device gets transport and sends the ACK for the first message.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);

        // Verify the original remote future succeeds upon receiving the late ACK.
        assertThat(isFutureSucceeded(remoteFuture)).isTrue();
    }

    @Test
    public void sendMessageWithRetry_AckComeAfterTransportGone_succeed() {
        mAllowOutboundMessages = true;
        mAllowInboundMessages = true;

        // Remote sends a message, max 2 attempts.
        AndroidFuture<Boolean> remoteFuture =
                mRemoteMessenger1.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);

        // Local device receives it, but can't ACK due to no transport.
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNotNull();
        assertThat(remoteFuture.isDone()).isFalse();

        // Transport is lost on the remote device.
        mRemoteCdm1.setTransportAttachedState(/* associationId= */ 1, false);
        assertThat(remoteFuture.isDone()).isFalse();

        // Now, local device gets transport and sends the ACK for the first message.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);

        // Verify the original remote future succeeds upon receiving the late ACK.
        assertThat(isFutureSucceeded(remoteFuture)).isTrue();
    }

    @Test
    public void sendMessage_transportGoneWhileWaitingAck_retry() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);

        // Transport is lost, this should trigger a retry.
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, false);
        mFakeClock.advanceTime(RETRY_DELAY_MS);

        // Transport is re-established.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);

        // Verify that the message is retried.
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(2);
        assertThat(future.isDone()).isFalse();
    }

    @Test
    public void sendAndReceiveMessageTogether_combineAckAndOutboundMessage() {
        mAllowInboundMessages = true;

        // Remote sends a message.
        mRemoteMessenger1.sendMessage(
                TEST_NETWORK, /* associationId= */ 1, new byte[] {1}, /* maxDeliveryAttempts= */ 1);

        // Local sends a message.
        mMessengerImpl.sendMessage(
                TEST_NETWORK, /* associationId= */ 1, new byte[] {2}, /* maxDeliveryAttempts= */ 1);

        // Manually complete the transport request and attach the transport.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mAllowOutboundMessages = true; // Allow the batched message to be sent.
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);

        // Verify that the outbound message and the ACK are batched together.
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);
    }

    @Test
    public void receiveMessage_listenerTriggered() {
        mAllowInboundMessages = true;
        List<Pair<String, byte[]>> incomingMessages = new ArrayList<>();
        mMessengerImpl.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    incomingMessages.add(Pair.create(networkId, msg));
                });

        byte[] message = new byte[] {1};
        mRemoteMessenger1.sendMessage(
                TEST_NETWORK, /* associationId= */ 1, message, /* maxDeliveryAttempts= */ 1);

        assertThat(incomingMessages).hasSize(1);
        assertMessagesEqual(incomingMessages.get(0), Pair.create(TEST_NETWORK, message));
    }

    @Test
    public void receiveMessage_sendAckWillNotRetry() {
        mAllowInboundMessages = true;
        mAllowOutboundMessages = true;
        List<Pair<String, byte[]>> incomingMessages = new ArrayList<>();
        mMessengerImpl.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    incomingMessages.add(Pair.create(networkId, msg));
                });

        // Incoming message.
        byte[] message = new byte[] {1};
        AndroidFuture<?> remoteFuture =
                mRemoteMessenger1.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        message,
                        /* maxDeliveryAttempts= */ 1);
        assertMessagesEqual(incomingMessages.get(0), Pair.create(TEST_NETWORK, message));

        // Fail the transport request.
        AndroidFuture<?> requestTransportFuture =
                mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1);
        assertThat(requestTransportFuture).isNotNull();
        requestTransportFuture.completeExceptionally(new Exception("transport request failed"));

        // Verify that we will not retry sending another ACK.
        mFakeClock.advanceTime(RETRY_DELAY_MS);
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();

        // Assert remote device timeout.
        assertThat(isFutureFailed(remoteFuture)).isTrue();
    }

    @Test
    public void receiveDuplicateMessages_onlyNotifyListenerOnce() {
        // Enable inbound message only
        mAllowInboundMessages = true;
        List<Pair<String, byte[]>> incomingMessages = new ArrayList<>();
        mMessengerImpl.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    incomingMessages.add(Pair.create(networkId, msg));
                });

        // Incoming message.
        byte[] message = new byte[] {1};
        AndroidFuture<?> remoteFuture =
                mRemoteMessenger1.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        message,
                        /* maxDeliveryAttempts= */ 3);

        // Verify the message has been received
        assertThat(incomingMessages).hasSize(1);
        assertMessagesEqual(incomingMessages.get(0), Pair.create(TEST_NETWORK, message));
        // Remote device hasn't received ACK.
        assertThat(remoteFuture.isDone()).isFalse();

        // ACK timeout. Sender retries.
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        mFakeClock.advanceTime(RETRY_DELAY_MS);

        // Verify that the new retry doesn't end up causing extra listener notification
        assertThat(incomingMessages).hasSize(1);
        // Remote device hasn't received ACK.
        assertThat(remoteFuture.isDone()).isFalse();

        // ACK timeout again. Sender retries.
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        mFakeClock.advanceTime(RETRY_DELAY_MS);

        // Verify that the new retry doesn't end up causing extra listener notification
        assertThat(incomingMessages).hasSize(1);
        // Remote device hasn't received ACK.
        assertThat(remoteFuture.isDone()).isFalse();

        // ACK timeout again. Sender give up.
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        assertThat(isFutureFailed(remoteFuture)).isTrue();
    }

    @Test
    public void senderRestarted_receiverCanReceiveNewMessages() {
        // Setup remote devices.
        mAllowInboundMessages = true;
        mAllowOutboundMessages = true;
        List<Pair<String, byte[]>> incomingMessages = new ArrayList<>();
        mMessengerImpl.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    incomingMessages.add(Pair.create(networkId, msg));
                });
        // Allow automatic transport attachment.
        mFakeCompanionActionController.setAutomaticallyAttachTransport(true);

        // Remote device send message 1
        AndroidFuture<Boolean> future =
                mRemoteMessenger1.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 1);

        // We received message 1
        assertThat(future.isDone()).isTrue();
        assertThat(incomingMessages).hasSize(1);

        // Re-create remote messenger (simulate restart).
        mRemoteMessenger1.destroy();
        mRemoteMessenger1 = simulateConnectedRemoteDevice(/* associationId= */ 1).getMessenger();

        // Remote device send message 2
        future =
                mRemoteMessenger1.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {2},
                        /* maxDeliveryAttempts= */ 1);

        // We received message 2.
        assertThat(future.isDone()).isTrue();
        assertThat(incomingMessages).hasSize(2);
    }

    @Test
    public void multiDeviceSendMessage_messageBatchedAndSendCorrectly() {
        mAllowOutboundMessages = true;
        mAllowInboundMessages = true;

        AndroidFuture<Boolean> future1 =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 1);
        AndroidFuture<Boolean> future2 =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {2},
                        /* maxDeliveryAttempts= */ 1);
        AndroidFuture<Boolean> future3 =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 2,
                        new byte[] {3},
                        /* maxDeliveryAttempts= */ 1);
        AndroidFuture<Boolean> future4 =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 2,
                        new byte[] {4},
                        /* maxDeliveryAttempts= */ 1);

        // Manually complete the transport request and attach the transport for both associations.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 2)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 2, true);

        // Verify that the futures succeeded and the messages were batched.
        assertThat(isFutureSucceeded(future1)).isTrue();
        assertThat(isFutureSucceeded(future2)).isTrue();
        assertThat(isFutureSucceeded(future3)).isTrue();
        assertThat(isFutureSucceeded(future4)).isTrue();
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(2);
    }

    @Test
    public void broadcast_addDevicesReceiveMessage() {
        // Setup remote devices.
        List<Pair<String, byte[]>> remote1ReceivedMessages = new ArrayList<>();
        mRemoteMessenger1.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    remote1ReceivedMessages.add(Pair.create(networkId, msg));
                });
        List<Pair<String, byte[]>> remote2ReceivedMessages = new ArrayList<>();
        mRemoteMessenger2.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    remote2ReceivedMessages.add(Pair.create(networkId, msg));
                });
        // Allow local device to auto-attach transport
        mFakeCompanionActionController.setAutomaticallyAttachTransport(true);

        // Send broadcast message.
        Pair<String, byte[]> message = Pair.create(TEST_NETWORK, new byte[] {1});
        AndroidFuture<?> future1 =
                mMessengerImpl.sendMessage(
                        message.first,
                        /* associationId= */ 1,
                        message.second,
                        /* maxDeliveryAttempts= */ 2);
        AndroidFuture<?> future2 =
                mMessengerImpl.sendMessage(
                        message.first,
                        /* associationId= */ 2,
                        message.second,
                        /* maxDeliveryAttempts= */ 2);

        // Timeout waiting for ACK.
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        assertThat(future1.isDone()).isFalse();
        assertThat(future2.isDone()).isFalse();

        // Retry and both receivers should receive the message.
        mAllowOutboundMessages = true;
        mAllowInboundMessages = true;
        mFakeClock.advanceTime(RETRY_DELAY_MS);

        assertThat(remote1ReceivedMessages).hasSize(1);
        assertThat(remote2ReceivedMessages).hasSize(1);
        assertThat(isFutureSucceeded(future1)).isTrue();
        assertThat(isFutureSucceeded(future2)).isTrue();
    }

    @Test
    public void cancel_transportRequestCancelled() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();

        future.cancel(true);

        // Verify we cancelled the transport request.
        assertThat(mFakeCompanionActionController.getAttachTransportFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void cancel_messageNotSent() {
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);

        future.cancel(true);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);

        // Verify we didn't send message
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).isEmpty();
    }

    @Test
    public void cancel_whileWaitingForAck_preventsRetry() {
        // Send a message with 2 delivery attempts.
        AndroidFuture<Boolean> future =
                mMessengerImpl.sendMessage(
                        TEST_NETWORK,
                        /* associationId= */ 1,
                        new byte[] {1},
                        /* maxDeliveryAttempts= */ 2);
        assertThat(future.isDone()).isFalse();

        // Allow transport to connect and the message to be sent once.
        mFakeCompanionActionController
                .getAttachTransportFuture(/* associationId= */ 1)
                .complete(null);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(/* associationId= */ 1, true);
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);

        // Cancel the future while it's waiting for an ACK.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();

        // Advance the clock past the ACK timeout and retry delay to trigger a potential retry.
        mFakeClock.advanceTime(WAITING_FOR_ACK_TIMEOUT);
        mFakeClock.advanceTime(RETRY_DELAY_MS);

        // Assert that the message was not sent a second time.
        assertThat(mFakeCompanionDeviceManagerProxy.getSentMessages()).hasSize(1);
    }

    @Test
    public void onMessage_malformattedInput_ignored() {
        // Register a listener that should not be called.
        List<Pair<String, byte[]>> incomingMessages = new ArrayList<>();
        mMessengerImpl.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    incomingMessages.add(Pair.create(networkId, msg));
                });

        // Simulate receiving a malformed message that cannot be parsed as a proto (junk data).
        mFakeCompanionDeviceManagerProxy.simulateMessageReceived(
                CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                /* associationId= */ 1,
                new byte[] {1, 2, 3});

        // Assert that the listener was not called and no crash occurred.
        assertThat(incomingMessages).isEmpty();
    }

    @Test
    public void receiveMessages_outOfOrder_ignoresOlderMessage() {
        mAllowInboundMessages = true;
        List<Pair<String, byte[]>> incomingMessages = new ArrayList<>();
        mMessengerImpl.registerMessageListener(
                Runnable::run,
                (networkId, associationId, msg) -> {
                    incomingMessages.add(Pair.create(networkId, msg));
                });

        // Manually create a message with a higher handleId (e.g., 2).
        Message message2 = new Message(2, TEST_NETWORK, new byte[] {2});
        byte[] batchedMessage2 =
                new BatchedMessage(List.of(message2), List.of(), mRemoteMessenger1.hashCode())
                        .toByteArray();

        // Simulate receiving the newer message first.
        mFakeCompanionDeviceManagerProxy.simulateMessageReceived(
                CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                /* associationId= */ 1,
                batchedMessage2);

        // Verify the listener was called for the first message.
        assertThat(incomingMessages).hasSize(1);
        assertMessagesEqual(incomingMessages.get(0), Pair.create(TEST_NETWORK, new byte[] {2}));

        // Manually create a message with a lower handleId (e.g., 1).
        Message message1 = new Message(1, TEST_NETWORK, new byte[] {1});
        byte[] batchedMessage1 =
                new BatchedMessage(List.of(message1), List.of(), mRemoteMessenger1.hashCode())
                        .toByteArray();

        // Simulate receiving the older message second.
        mFakeCompanionDeviceManagerProxy.simulateMessageReceived(
                CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                /* associationId= */ 1,
                batchedMessage1);

        // Assert that the listener was not called again for the older, out-of-order message.
        assertThat(incomingMessages).hasSize(1);
    }

    private RemoteDeviceContext simulateConnectedRemoteDevice(int associationId) {
        RemoteDeviceContext remoteContext = new RemoteDeviceContext();
        FakeCompanionDeviceManagerProxy remoteCDM = remoteContext.getCDM();

        // Setup associations on both devices.
        remoteCDM.addAssociation(/* userId= */ createAssociationInfo(/* id= */ 1));
        mFakeCompanionDeviceManagerProxy.addAssociation(createAssociationInfo(associationId));

        // Hook messages
        remoteCDM.addSentMessageListener(
                CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                sentMessage -> {
                    if (mAllowInboundMessages) {
                        mFakeCompanionDeviceManagerProxy.simulateMessageReceived(
                                CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                                associationId,
                                sentMessage.data());
                    }
                },
                remoteContext.getMainExecutor());
        mFakeCompanionDeviceManagerProxy.addSentMessageListener(
                CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                sentMessage -> {
                    if (mAllowOutboundMessages
                            && ArrayUtils.contains(sentMessage.associationIds(), associationId)) {
                        remoteCDM.simulateMessageReceived(
                                CompanionDeviceManager.MESSAGE_ONEWAY_CROSS_DEVICE_SYNC,
                                /* associationId= */ 1,
                                sentMessage.data());
                    }
                },
                mMainExecutor);

        // Enable automatic transport attach.
        remoteContext.getCompanionActionController().setAutomaticallyAttachTransport(true);

        MessengerImpl messenger = remoteContext.getMessenger();
        linkDeviceAttachmentEvent(messenger, remoteCDM, remoteContext.getMainExecutor());
        messenger.init();
        return remoteContext;
    }

    private AssociationInfo createAssociationInfo(int id) {
        return new AssociationInfo.Builder(
                        id,
                        mContext.getUserId(),
                        "com.android.server.companion.datatransfer.crossdevicesync")
                .setDisplayName("displayName" + id)
                .build();
    }

    private void linkDeviceAttachmentEvent() {
        linkDeviceAttachmentEvent(mMessengerImpl, mFakeCompanionDeviceManagerProxy, mMainExecutor);
    }

    private static void linkDeviceAttachmentEvent(
            MessengerImpl messenger,
            FakeCompanionDeviceManagerProxy companionDeviceManager,
            Executor executor) {
        companionDeviceManager.addOnTransportsChangedListener(
                executor,
                associationInfos -> {
                    messenger.onTransportsChanged(
                            associationInfos.stream()
                                    .map(AssociationInfo::getId)
                                    .collect(Collectors.toSet()));
                });
    }

    private static void assertMessagesEqual(Pair<String, byte[]> m1, Pair<String, byte[]> m2) {
        assertThat(m1.first).isEqualTo(m2.first);
        assertThat(Arrays.equals(m1.second, m2.second)).isTrue();
    }

    private static class RemoteDeviceContext extends SyncServiceTestBase {
        RemoteDeviceContext() {
            mFakeCompanionActionController.init();
        }

        public FakeCompanionDeviceManagerProxy getCDM() {
            return mFakeCompanionDeviceManagerProxy;
        }

        public MessengerImpl getMessenger() {
            return mMessengerImpl;
        }

        public Executor getMainExecutor() {
            return mMainExecutor;
        }

        public FakeCompanionActionController getCompanionActionController() {
            return mFakeCompanionActionController;
        }
    }
}
