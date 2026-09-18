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

package com.android.server.companion.datatransfer.crossdevicesync.network;

import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_DROPPED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_RECEIVED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_FAILED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_FINISHED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_STARTED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_FAILED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_FINISHED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_STARTED;
import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_UNSPECIFIED;
import static com.android.server.companion.datatransfer.crossdevicesync.network.MessageRecord.MAX_DELIVERY_ATTEMPT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import android.companion.AssociationInfo;
import android.companion.DevicePresenceEvent;
import android.os.PersistableBundle;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy.DevicePresenceListener;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeFrameworkStatsLogProxy;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.NetworkListener;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.RemoteDevice;
import com.android.server.companion.datatransfer.crossdevicesync.network.advertiser.Advertiser.AdvertisingSession;
import com.android.server.companion.datatransfer.crossdevicesync.network.messenger.fake.FakeMessenger.SentMessage;
import com.android.server.companion.datatransfer.crossdevicesync.network.scanner.fake.FakeScanner;
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

@RunWith(AndroidJUnit4.class)
public class NetworkManagerImplTest extends SyncServiceTestBase {
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private static final Predicate<RemoteDevice> INCLUDE_ALL_DEVICES = device -> true;
    private static final int FEATURE = 123;

    @Mock private NetworkListener mListener;

    @Before
    public void setUp() {
        mNetworkManagerImpl.init();
    }

    @Test
    public void init_noAssociations_noRemoteDevice() {
        assertThat(mNetworkManagerImpl.getRemoteDevices()).isEmpty();
    }

    @Test
    public void init_withAssociations_createsRemoteDevices() {
        // Clear the associations added by setUp().
        mNetworkManagerImpl.destroy();
        AssociationInfo info1 = createAssociationInfo(/* id= */ 1);
        AssociationInfo info2 = createAssociationInfo(/* id= */ 2);
        mFakeCompanionDeviceManagerProxy.addAssociation(info1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info2);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 2, /* hasTransport= */ true);

        mNetworkManagerImpl.init();

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1, 2);
        assertThat(devices.get(1).hasTransport()).isFalse();
        assertThat(devices.get(2).hasTransport()).isTrue();
    }

    @Test
    public void init_alreadyInitialized_throwsIllegalStateException() {
        // The manager is already initialized in setUp(). Calling it again should throw.
        assertThrows(IllegalStateException.class, () -> mNetworkManagerImpl.init());
    }

    @Test
    public void destroy_listenersAreCleared() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mNetworkManagerImpl.destroy();
        mFakeCompanionDeviceManagerProxy.addAssociation(createAssociationInfo(/* id= */ 2));

        assertThat(mNetworkManagerImpl.getRemoteDevices()).isEmpty();
        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).isEmpty();
    }

    @Test
    public void onAssociationsChanged_addAssociation_createsRemoteDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);

        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1);
    }

    @Test
    public void onAssociationsChanged_addAssociation_registersPresenceListener() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).hasSize(1);
        DevicePresenceListener listener =
                mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners().get(0);
        assertThat(listener.serviceName()).isEqualTo(mContext.getPackageName());
        assertThat(listener.associationIds()).asList().containsExactly(1);
    }

    @Test
    public void onAssociationsChanged_removeAssociation_removesRemoteDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mFakeCompanionDeviceManagerProxy.removeAssociation(/* associationId= */ 1);

        assertThat(mNetworkManagerImpl.getRemoteDevices()).isEmpty();
    }

    @Test
    public void onAssociationsChanged_removeAssociation_unregistersPresenceListener() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mFakeCompanionDeviceManagerProxy.removeAssociation(/* associationId= */ 1);

        assertThat(mFakeCompanionDeviceManagerProxy.getDevicePresenceListeners()).isEmpty();
    }

    @Test
    public void onAssociationsChanged_noChange_doesNothing() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        // Adding the same info again should not trigger the listener.
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        Map<Integer, RemoteDevice> devices = mNetworkManagerImpl.getRemoteDevices();
        assertThat(devices.keySet()).containsExactly(1);
        assertThat(devices.get(1).hasTransport()).isFalse();
    }

    @Test
    public void onTransportChanged_addTransport_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).hasTransport()).isTrue();
    }

    @Test
    public void onTransportChanged_removeTransport_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ false);

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).hasTransport()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_bleAppeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_bleDisappeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_DISAPPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_btConnected_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BT_CONNECTED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBtConnected()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_btDisconnected_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BT_CONNECTED, null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BT_DISCONNECTED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBtConnected()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedAppeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED,
                        null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedAppeared()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedDisappeared_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED,
                        null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED,
                        null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedAppeared()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedNearby_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                        null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedNearby()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_selfManagedNotNearby_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_NEARBY,
                        null));

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_SELF_MANAGED_NOT_NEARBY,
                        null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isSelfManagedNearby()).isFalse();
    }

    @Test
    public void onDevicePresenceEvent_duplicateEvent_doesNotChangeState() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));
        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();

        // Simulate the same event again.
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void onDevicePresenceEvent_associationRemoved_updatesDevice() {
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1,
                        DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED,
                        null));
        assertThat(mNetworkManagerImpl.getRemoteDevices().get(1).isAssociationRemoved()).isTrue();
    }

    @Test
    public void createNetwork_success() {
        Network network = mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES);

        assertThat(network).isNotNull();
        assertThat(network.getNetworkId()).isEqualTo("network1");
        assertThat(mNetworkManagerImpl.getNetworks().get(0)).isSameInstanceAs(network);
    }

    @Test
    public void createNetwork_returnsExisting() {
        Network network1 = mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES);
        Network network2 = mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES);

        assertThat(network1).isSameInstanceAs(network2);
    }

    @Test
    public void createNetwork_throwsWhenNotInitialized() {
        mNetworkManagerImpl.destroy();

        assertThrows(
                IllegalStateException.class,
                () -> mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES));
    }

    @Test
    public void createNetwork_throwsWhenUserIdConflicts() {
        mNetworkManagerImpl.createNetworkForUser(
                UserHandle.USER_SYSTEM, "network1", INCLUDE_ALL_DEVICES);
        mNetworkManagerImpl.createNetwork("network2", INCLUDE_ALL_DEVICES);

        assertThrows(
                IllegalArgumentException.class,
                () -> mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mNetworkManagerImpl.createNetworkForUser(
                                UserHandle.USER_SYSTEM, "network2", INCLUDE_ALL_DEVICES));
    }

    @Test
    public void networkMembership_deviceAddedAndRemoved() {
        Network network = mNetworkManagerImpl.createNetwork("network1", RemoteDevice::hasTransport);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);

        assertThat(network.getRemoteDevices()).hasSize(1);
        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ false);

        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void networkMembership_deviceJoinsWhenTransportBecomesAvailable() {
        Network network = mNetworkManagerImpl.createNetwork("network1", RemoteDevice::hasTransport);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        assertThat(network.getRemoteDevices()).isEmpty();
        verifyNotReceivedDeviceChanged(mListener, /* associationId= */ 1);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);

        assertThat(network.getRemoteDevices()).hasSize(1);
        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);
    }

    @Test
    public void networkMembership_deviceLeavesWhenAssociationRemoved() {
        Network network = mNetworkManagerImpl.createNetwork("network1", RemoteDevice::hasTransport);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);
        assertThat(network.getRemoteDevices()).hasSize(1);
        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.removeAssociation(/* associationId= */ 1);

        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void networkMembership_onlyMatchedUserCanJoin() {
        Network network1 = mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES);
        NetworkListener listener1 = mock(NetworkListener.class);
        network1.registerListener(mMainExecutor, listener1);
        Network network2 =
                mNetworkManagerImpl.createNetworkForUser(
                        UserHandle.USER_SYSTEM, "network2", INCLUDE_ALL_DEVICES);
        NetworkListener listener2 = mock(NetworkListener.class);
        network2.registerListener(mMainExecutor, listener2);
        AssociationInfo info1 = createAssociationInfo(/* id= */ 1, UserHandle.USER_SYSTEM);
        mFakeCompanionDeviceManagerProxy.addAssociation(info1);
        AssociationInfo info2 = createAssociationInfo(/* id= */ 2, /* userId= */ 10);
        mFakeCompanionDeviceManagerProxy.addAssociation(info2);

        assertThat(network1.getRemoteDevices()).hasSize(2);
        assertThat(network2.getRemoteDevices()).hasSize(1);
        verifyReceivedDeviceChanged(listener1, /* associationId= */ 1);
        verifyReceivedDeviceChanged(listener1, /* associationId= */ 2);
        verifyReceivedDeviceChanged(listener2, /* associationId= */ 1);
        verifyNotReceivedDeviceChanged(listener2, /* associationId= */ 2);
    }

    @Test
    public void network_destroy() {
        Network network = mNetworkManagerImpl.createNetwork("network1", RemoteDevice::hasTransport);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);
        assertThat(network.getRemoteDevices()).hasSize(1);
        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);

        network.destroy();

        assertThat(mNetworkManagerImpl.getNetworks()).isEmpty();
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ false);
        verify(mListener, never()).onDeviceRemoved(1);
    }

    @Test
    public void network_listenersNotifiedOnChanges() {
        Network network = mNetworkManagerImpl.createNetwork("network1", RemoteDevice::hasTransport);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);
        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ false);

        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void network_messagingMethodsDoNotCrashWhenDestroyed() {
        Network network = mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES);

        network.destroy();

        assertThat(network.broadcastMessage(new byte[0], 0)).isEqualTo(-1);
        assertThat(network.multicastMessage(Collections.singletonList(1), new byte[0], 0))
                .isEqualTo(-1);
        network.cancelMessage(0);
    }

    @Test
    public void unregisterListener_isNotNotified() {
        Network network = mNetworkManagerImpl.createNetwork("network1", RemoteDevice::hasTransport);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);
        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);

        network.unregisterListener(mListener);
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ false);
        verify(mListener, never()).onDeviceRemoved(1);
    }

    @Test
    public void networkMembership_associationCondition() {
        // Create a network that only accepts devices with a specific metadata flag.
        Network network =
                mNetworkManagerImpl.createNetwork(
                        "network1",
                        v ->
                                v.getAssociationInfoCache()
                                        .getMetadata()
                                        .getBoolean("is_wearable", false));
        network.registerListener(mMainExecutor, mListener);

        // Add a device with empty metadata and verify it doesn't join.
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);
        assertThat(network.getRemoteDevices()).isEmpty();
        verifyNotReceivedDeviceChanged(mListener, /* associationId= */ 1);

        // Update the association to add the "is_wearable" flag to the metadata.
        PersistableBundle metadata = new PersistableBundle();
        metadata.putBoolean("is_wearable", true);
        AssociationInfo wearableInfo =
                new AssociationInfo.Builder(info).setMetadata(metadata).build();
        mFakeCompanionDeviceManagerProxy.addAssociation(wearableInfo);

        // Verify the device now joins the network.
        assertThat(network.getRemoteDevices()).hasSize(1);
        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);
        reset(mListener);

        // Update the association to use empty metadata again.
        AssociationInfo nonWearableInfo =
                new AssociationInfo.Builder(info).setMetadata(new PersistableBundle()).build();
        mFakeCompanionDeviceManagerProxy.addAssociation(nonWearableInfo);

        // Verify the device leaves the network.
        assertThat(network.getRemoteDevices()).isEmpty();
        verify(mListener).onDeviceRemoved(1);
    }

    @Test
    public void onDeviceChanged_isCalledForExistingMemberStateUpdate() {
        Network network = mNetworkManagerImpl.createNetwork("network1", RemoteDevice::hasTransport);
        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);
        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);
        assertThat(network.getRemoteDevices().get(1).isBleAppeared()).isFalse();
        reset(mListener);

        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        /* associationId= */ 1, DevicePresenceEvent.EVENT_BLE_APPEARED, null));

        verifyReceivedDeviceChanged(mListener, /* associationId= */ 1);
        assertThat(network.getRemoteDevices().get(1).isBleAppeared()).isTrue();
    }

    @Test
    public void registerListener_onDestroyedNetwork_doesNotRegister() {
        Network network = mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES);
        network.destroy();

        network.registerListener(mMainExecutor, mListener);
        AssociationInfo info = createAssociationInfo(/* id= */ 1);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);

        verifyNotReceivedDeviceChanged(mListener, /* associationId= */ 1);
    }

    @Test
    public void broadcastMessage_sendsToAllDevicesInNetwork() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {1, 2, 3}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        SentMessage message1 = mFakeMessenger.getSentMessages().get(0);
        assertThat(message1.associationId).isEqualTo(1);
        assertThat(message1.message).isEqualTo(new byte[] {1, 2, 3});
        SentMessage message2 = mFakeMessenger.getSentMessages().get(1);
        assertThat(message2.associationId).isEqualTo(2);
    }

    @Test
    public void broadcastMessageToUserNetwork_sendsToAllDevicesInNetwork() {
        // Setup 4 devices belonging to 2 users.
        setupTwoDevices(/* userId= */ 1, /* present= */ true, /* nearby= */ true);
        setupTwoDevices(/* userId= */ 2, /* present= */ true, /* nearby= */ true);
        // Setup 2 networks with the same network id but owned by different users.
        Network network1 =
                mNetworkManagerImpl.createNetworkForUser(
                        /* userId= */ 1, "network", INCLUDE_ALL_DEVICES);
        Network network2 =
                mNetworkManagerImpl.createNetworkForUser(
                        /* userId= */ 2, "network", INCLUDE_ALL_DEVICES);

        network1.broadcastMessage(new byte[] {1, 2, 3}, 0);
        network2.broadcastMessage(new byte[] {4, 5, 6}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(4);
        // First 2 messages are sent to device 1 and 2
        SentMessage message1 = mFakeMessenger.getSentMessages().get(0);
        assertThat(message1.associationId).isEqualTo(1);
        assertThat(message1.message).isEqualTo(new byte[] {1, 2, 3});
        SentMessage message2 = mFakeMessenger.getSentMessages().get(1);
        assertThat(message2.associationId).isEqualTo(2);
        assertThat(message2.message).isEqualTo(new byte[] {1, 2, 3});
        // Second 2 messages are sent to device 3 and 4.
        SentMessage message3 = mFakeMessenger.getSentMessages().get(2);
        assertThat(message3.associationId).isEqualTo(3);
        assertThat(message3.message).isEqualTo(new byte[] {4, 5, 6});
        SentMessage message4 = mFakeMessenger.getSentMessages().get(3);
        assertThat(message4.associationId).isEqualTo(4);
        assertThat(message4.message).isEqualTo(new byte[] {4, 5, 6});
    }

    @Test
    public void multicastMessage_sendsToSpecifiedDevices() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.multicastMessage(Collections.singletonList(2), new byte[] {4, 5, 6}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
        SentMessage message = mFakeMessenger.getSentMessages().get(0);
        assertThat(message.associationId).isEqualTo(2);
        assertThat(message.message).isEqualTo(new byte[] {4, 5, 6});
    }

    @Test
    public void cancelMessage_preventsMessageFromBeingSent() {
        setupTwoDevices(/* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        long messageId =
                network.broadcastMessage(new byte[] {7, 8, 9}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        network.cancelMessage(messageId);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
    }

    @Test
    public void cancelMessage_notCancelingMessageFromAnotherNetwork() {
        Network network1 =
                mNetworkManagerImpl.createNetworkForUser(
                        UserHandle.USER_SYSTEM, "network1", INCLUDE_ALL_DEVICES);
        Network network2 =
                mNetworkManagerImpl.createNetworkForUser(
                        /* userId= */ 10, "network1", INCLUDE_ALL_DEVICES);
        long msg1 = network1.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        long msg2 = network2.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);

        // Network1 cannot cancel message 2.
        network1.cancelMessage(msg2);
        assertThat(network2.isMessagePending(msg2)).isTrue();

        // Network2 cannot cancel message 1.
        network2.cancelMessage(msg1);
        assertThat(network1.isMessagePending(msg1)).isTrue();

        // Network1 can cancel message 1.
        network1.cancelMessage(msg1);
        assertThat(network1.isMessagePending(msg1)).isFalse();

        // Network2 can cancel message 2.
        network2.cancelMessage(msg2);
        assertThat(network2.isMessagePending(msg2)).isFalse();
    }

    @Test
    public void messageToOfflineDevice_waitsForPresence() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);

        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void messageWithScanFlag_triggersScanner() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {2}, Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);

        assertThat(mFakeScanner.getSessions()).hasSize(1);
        assertThat(mFakeScanner.getSessions().iterator().next().getAssociationId()).isEqualTo(1);
    }

    @Test
    public void messageWithNearbyOnlyFlag_notSentToFarDevice() {
        setupTwoDevices(/* present= */ true, /* nearby= */ false);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {3}, Network.MESSAGE_FLAG_NEARBY_ONLY);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
        assertThat(mFakeMessenger.getSentMessages().get(0).associationId).isEqualTo(1);
    }

    @Test
    public void multicastMessage_sendsToSubsetOfDevicesInNetwork() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        setupDevice(/* id= */ 3, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        assertThat(network.getRemoteDevices()).hasSize(3);

        network.multicastMessage(List.of(1, 3), new byte[] {4, 5, 6}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        assertThat(mFakeMessenger.getTargetAssociationIds()).containsExactly(1, 3);
    }

    @Test
    public void invalidateNetworks_reEvaluatesNetworkMembership() {
        AtomicBoolean condition = new AtomicBoolean(true);
        Network network = mNetworkManagerImpl.createNetwork("test", v -> condition.get());
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        assertThat(network.getRemoteDevices()).hasSize(1);

        condition.set(false);
        // No change yet, because no event has triggered re-evaluation.
        assertThat(network.getRemoteDevices()).hasSize(1);

        mNetworkManagerImpl.invalidateNetworks();

        // Now the device should be removed.
        assertThat(network.getRemoteDevices()).isEmpty();
    }

    @Test
    public void destroy_listenersAreNotNotifiedOfSubsequentChanges() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        network.registerListener(mMainExecutor, mListener);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        reset(mListener);

        network.destroy();

        // This would have triggered onDeviceChanged if the listener was still registered.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ false);

        verify(mListener, never()).onDeviceChanged(any());
    }

    @Test
    public void messageWithStickyAndNearbyOnlyFlags_sendsWhenDeviceBecomesNearby() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.broadcastMessage(
                new byte[] {1}, Network.MESSAGE_FLAG_STICKY | Network.MESSAGE_FLAG_NEARBY_ONLY);

        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
        assertThat(mFakeMessenger.getSentMessages().get(0).associationId).isEqualTo(1);
    }

    @Test
    public void destroyNetwork_cancelsPendingMessages() {
        setupTwoDevices(/* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        network.broadcastMessage(new byte[] {4}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        network.destroy();
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);
        makeDevicePresent(/* associationId= */ 2, /* nearby= */ true);

        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
    }

    @Test
    public void inboundMessage_notifiesListener() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        network.registerListener(mMainExecutor, mListener);

        mFakeMessenger.receiveMessage("test", 2, new byte[] {5, 6});

        verify(mListener).onNetworkMessage(2, new byte[] {5, 6});
    }

    @Test
    public void inboundMessage_forUnknownNetwork_isDropped() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        network.registerListener(mMainExecutor, mListener);

        mFakeMessenger.receiveMessage("unknown_network", 1, new byte[] {7, 8});

        verify(mListener, never()).onNetworkMessage(1, new byte[] {7, 8});
    }

    @Test
    public void inboundMessage_fromUnknownDevice_isDropped() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        network.registerListener(mMainExecutor, mListener);

        // Send a message from an association ID that does not exist.
        mFakeMessenger.receiveMessage("test", 999, new byte[] {1, 2});

        // Verify the message is dropped and the listener is not notified.
        verify(mListener, never()).onNetworkMessage(anyInt(), any());
    }

    @Test
    public void inboundMessage_sentToRightUser() {
        // Setup 2 devices for 2 users.
        setupDevice(/* id= */ 1, /* userId= */ 1, /* present= */ true, /* nearby= */ true);
        setupDevice(/* id= */ 2, /* userId= */ 2, /* present= */ true, /* nearby= */ true);
        // Setup 2 networks for 2 users sharing the same network id.
        Network network1 =
                mNetworkManagerImpl.createNetworkForUser(
                        /* userId= */ 1, "network", INCLUDE_ALL_DEVICES);
        NetworkListener listener1 = mock(NetworkListener.class);
        network1.registerListener(mMainExecutor, listener1);
        Network network2 =
                mNetworkManagerImpl.createNetworkForUser(
                        /* userId= */ 2, "network", INCLUDE_ALL_DEVICES);
        NetworkListener listener2 = mock(NetworkListener.class);
        network2.registerListener(mMainExecutor, listener2);

        // Inbound message from device 1 sent to network 1
        mFakeMessenger.receiveMessage("network", 1, new byte[] {1, 2});
        verify(listener1).onNetworkMessage(1, new byte[] {1, 2});
        verify(listener2, never()).onNetworkMessage(1, new byte[] {1, 2});

        // Inbound message from device 2 sent to network 2
        mFakeMessenger.receiveMessage("network", 2, new byte[] {3, 4});
        verify(listener1, never()).onNetworkMessage(2, new byte[] {3, 4});
        verify(listener2).onNetworkMessage(2, new byte[] {3, 4});
    }

    @Test
    public void messageLifecycle_success() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {1}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        SentMessage sentMessage1 = mFakeMessenger.getSentMessages().get(0);
        SentMessage sentMessage2 = mFakeMessenger.getSentMessages().get(1);
        sentMessage1.completeSuccessfully();
        sentMessage2.completeSuccessfully();
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
    }

    @Test
    public void messageLifecycle_failureAndRetry_passesCorrectAttemptCount() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_RETRY_IF_FAILED);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        assertThat(mFakeMessenger.getSentMessages().get(0).maxDeliveryAttempts)
                .isEqualTo(MAX_DELIVERY_ATTEMPT);
        assertThat(mFakeMessenger.getSentMessages().get(1).maxDeliveryAttempts)
                .isEqualTo(MAX_DELIVERY_ATTEMPT);
    }

    @Test
    public void messageLifecycle_noRetry_passesCorrectAttemptCount() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {1}, 0);

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        assertThat(mFakeMessenger.getSentMessages().get(0).maxDeliveryAttempts).isEqualTo(1);
        assertThat(mFakeMessenger.getSentMessages().get(1).maxDeliveryAttempts).isEqualTo(1);
    }

    @Test
    public void cancelMessage_cancelsInFlightMessage() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        long messageId = network.broadcastMessage(new byte[] {1, 2, 3}, 0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        SentMessage sentMessage = mFakeMessenger.getSentMessages().get(0);
        assertThat(sentMessage.getFuture().isCancelled()).isFalse();

        network.cancelMessage(messageId);
        mNetworkManagerImpl.invalidateNetworks();

        assertThat(sentMessage.getFuture().isCancelled()).isTrue();
    }

    @Test
    public void messageLifecycle_failed() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        network.broadcastMessage(new byte[] {1}, 0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        SentMessage sentMessage = mFakeMessenger.getSentMessages().get(0);

        sentMessage.completeWithException(new Exception("Failed to send"));
        mNetworkManagerImpl.invalidateNetworks();

        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
    }

    @Test
    public void inboundMessage_fromNonMemberDevice_isDropped() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network =
                mNetworkManagerImpl.createNetwork("test", device -> device.getAssociationId() == 1);
        network.registerListener(mMainExecutor, mListener);
        assertThat(network.getRemoteDevices()).hasSize(1);
        assertThat(network.getRemoteDevices().get(1)).isNotNull();

        mFakeMessenger.receiveMessage("test", 2, new byte[] {1, 2, 3});

        verify(mListener, never()).onNetworkMessage(2, new byte[] {1, 2, 3});
    }

    @Test
    public void broadcastMessage_deliversToDevicesJoiningNetworkAfterBroadcast() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Broadcast a sticky message while device 1 is online.
        network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages().size()).isEqualTo(1);
        assertThat(mFakeMessenger.getTargetAssociationIds()).containsExactly(1);

        // Add device 2 to the network, also online.
        setupDevice(/* id= */ 2, /* present= */ true, /* nearby= */ true);

        // The broadcast should have been delivered to the new member.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
        assertThat(mFakeMessenger.getTargetAssociationIds()).containsExactly(1, 2);
    }

    @Test
    public void recreatingNetwork_doesNotInheritOldListeners() {
        Network network1 = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        network1.registerListener(mMainExecutor, mListener);

        network1.destroy();

        mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        // Verify the listener from the old network was not notified.
        verify(mListener, never()).onDeviceChanged(any());
    }

    @Test
    public void recreatingNetwork_doesNotInheritOldMessages() {
        Network network1 = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        network1.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        network1.destroy();

        Network network2 = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Verify the sticky message from the old network was not sent.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
    }

    @Test
    public void cancelStickyBroadcast_isNotDeliveredToNewDevices() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a sticky broadcast to the first device.
        long messageId = network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);

        // Cancel the broadcast.
        network.cancelMessage(messageId);

        // Add a new device to the network.
        setupDevice(/* id= */ 2, /* present= */ true, /* nearby= */ true);

        // Verify that the cancelled sticky message is NOT sent to the new device.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void stickyMessageFailed_resendAfterReappearingUntilSuccess() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a sticky message to the first device.
        network.unicastMessage(/* associationId= */ 1, new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);

        // Broadcast failed.
        SentMessage message = mFakeMessenger.getSentMessages().get(0);
        message.completeWithException(new Exception("Failed to send"));

        // Device disappears and reappears;
        makeDeviceDisappear(/* associationId= */ 1);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Message is sent again.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);

        // Device disappears and reappears;
        makeDeviceDisappear(/* associationId= */ 1);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Still waiting for message.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);

        // Broadcast failed.
        message = mFakeMessenger.getSentMessages().get(1);
        message.completeWithException(new Exception("Failed to send"));

        // Device disappears and reappears;
        makeDeviceDisappear(/* associationId= */ 1);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Message is sent again.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(3);

        // Succeed!
        message = mFakeMessenger.getSentMessages().get(2);
        message.completeSuccessfully();

        // Device disappears and reappears;
        makeDeviceDisappear(/* associationId= */ 1);
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // No new message is sent.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(3);
    }

    @Test
    public void stickyBroadcastDeliveredToNewDevices() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a sticky broadcast.
        long messageId = network.broadcastMessage(new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Add a new device to the network.
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        // Verify that the message is sent to that device.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void stickyBroadcastDeliveredAfterDeviceLeaveAndRejoinNetwork() {
        Network network = mNetworkManagerImpl.createNetwork("test", RemoteDevice::hasTransport);
        // Add a new device to the network.
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ false);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);

        // Send a sticky broadcast.
        network.broadcastMessage(
                new byte[] {1}, Network.MESSAGE_FLAG_STICKY | Network.MESSAGE_FLAG_NEARBY_ONLY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Device removed from network, then rejoin
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ false);
        assertThat(network.getRemoteDevices()).hasSize(0);
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);
        assertThat(network.getRemoteDevices()).hasSize(1);

        // Becomes nearby.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Verify that the message is sent to that device.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void nonStickyBroadcastNotDeliveredToNewDevices() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a non-sticky broadcast.
        long messageId = network.broadcastMessage(new byte[] {1}, /* flags= */ 0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Add a new device to the network.
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        // Verify that the message is not sent to that device.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);
    }

    @Test
    public void scanMessageTriggersScanningUntilDeviceBecomesPresent() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a message that triggers scanning.
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);

        // Verify that scanning has started.
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        // Now, make the device present.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Verify the message was sent and the scan was stopped to save resources.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
        assertThat(session.isActive()).isFalse();
    }

    @Test
    public void scanNearbyMessageTriggersScanningUntilDeviceBecomesNearby() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a message that triggers scanning.
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT | Network.MESSAGE_FLAG_NEARBY_ONLY);

        // Verify that scanning has started.
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        // Make the device present but not nearby.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ false);

        // Verify that scan continues and message is not sent
        assertThat(session.isActive()).isTrue();
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Device becomes nearby
        makeDevicePresent(/* associationid= */ 1, /* nearby= */ true);

        // Verify that scan stops and message is sent
        assertThat(session.isActive()).isFalse();
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void nonStickyScanMessageFailed_notRescanOrResentAfterReappear() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a message that triggers scanning.
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);

        // Device present and message failed.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);
        SentMessage message = mFakeMessenger.getSentMessages().get(0);
        message.completeWithException(new Exception("Failed to send"));

        // Device disappears.
        makeDeviceDisappear(/* associationId= */ 1);

        // Verify that scanning is not started.
        assertThat(mFakeScanner.getSessions()).hasSize(0);

        // Device appears.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Verify that message is not resent.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void stickyScanMessageFailed_rescanAndResentAfterReappear() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a sticky message that triggers scanning.
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT | Network.MESSAGE_FLAG_STICKY);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);

        // Device present and message failed.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);
        SentMessage message = mFakeMessenger.getSentMessages().get(0);
        message.completeWithException(new Exception("Failed to send"));

        // Device disappears.
        makeDeviceDisappear(/* associationId= */ 1);

        // Verify that scanning is started.
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        assertThat(mFakeScanner.getAnySession().isActive()).isTrue();

        // Device appears.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Verify that scanning stopped and the message is resent.
        assertThat(mFakeScanner.getSessions()).hasSize(0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);

        // Device disappear after message sent.
        message = mFakeMessenger.getSentMessages().get(1);
        message.completeSuccessfully();
        makeDeviceDisappear(/* associationId= */ 1);

        // Verify that scanning is not started.
        assertThat(mFakeScanner.getSessions()).hasSize(0);

        // Device appears.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Verify that message is not resent.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(2);
    }

    @Test
    public void stickyScanBroadcastTriggerScanForNewDevices() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a sticky scan broadcast.
        network.broadcastMessage(
                new byte[] {1},
                Network.MESSAGE_FLAG_STICKY | Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(0);

        // Add a new device to the network.
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);

        // Verify that scanning started.
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        assertThat(mFakeScanner.getAnySession().isActive()).isTrue();

        // New device is present.
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Verify that scanning stopped and the message is sent to that device.
        assertThat(mFakeScanner.getSessions()).hasSize(0);
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void stickyMessageDoesNotSurviveNetworkDestroy() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        // Send a sticky broadcast
        network.broadcastMessage(
                new byte[] {1}, Network.MESSAGE_FLAG_STICKY | Network.MESSAGE_FLAG_NEARBY_ONLY);
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();

        // Destroy and re-create
        network.destroy();
        mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        // Make device nearby
        makeDevicePresent(/* associationId= */ 1, /* nearby= */ true);

        // Message is not sent.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
    }

    @Test
    public void stickyMessageSticksIfDeviceIsOutOfNetwork() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", RemoteDevice::hasTransport);
        // Send a sticky message to an out-of-network device.
        long msgId =
                network.unicastMessage(
                        /* associationId= */ 1, new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        // Not sent since device is not in network.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
        // Message sticks.
        assertThat(network.isMessagePending(msgId)).isTrue();

        // Device joins network later.
        mFakeCompanionDeviceManagerProxy.setTransportAttachedState(
                /* associationId= */ 1, /* hasTransport= */ true);

        // Message is sent.
        assertThat(mFakeMessenger.getSentMessages()).hasSize(1);
    }

    @Test
    public void stickyMessageDoesNotStickIfDeviceNotExisting() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        // Send a sticky message to a non-existing device.
        long msgId =
                network.unicastMessage(
                        /* associationId= */ 1, new byte[] {1}, Network.MESSAGE_FLAG_STICKY);
        // Not sent since device does not exist.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
        // Message doesn't stick.
        assertThat(network.isMessagePending(msgId)).isFalse();

        // Device associates later.
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        // Message is not sent since it didn't stick.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
    }

    @Test
    public void stickyMessageDoesNotStickIfDeviceUserIdMismatch() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        Network network =
                mNetworkManagerImpl.createNetworkForUser(
                        /* userId= */ 10, "test", INCLUDE_ALL_DEVICES);
        // Send a sticky message to an out-of-network device with mismatched user id.
        long msgId =
                network.unicastMessage(
                        /* associationId= */ 1, new byte[] {1}, Network.MESSAGE_FLAG_STICKY);

        // Not sent since device is not in network.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
        // Message doesn't stick.
        assertThat(network.isMessagePending(msgId)).isFalse();
    }

    @Test
    public void nonStickyMessage_toOfflineDevice_isCancelled() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);

        // Send a non-sticky message to an offline device.
        long msgId = network.unicastMessage(/* associationId= */ 1, new byte[] {1}, /* flags= */ 0);

        // Verify the message is not sent and is immediately cancelled.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
        assertThat(network.isMessagePending(msgId)).isFalse();
    }

    @Test
    public void nonStickyMessage_toOutOfNetworkDevice_isCancelled() {
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        // Create a network that excludes device 1.
        Network network =
                mNetworkManagerImpl.createNetwork("test", device -> device.getAssociationId() != 1);

        // Send a non-sticky message to the out-of-network device.
        long msgId = network.unicastMessage(/* associationId= */ 1, new byte[] {1}, /* flags= */ 0);

        // Verify the message is not sent and is immediately cancelled.
        assertThat(mFakeMessenger.getSentMessages()).isEmpty();
        assertThat(network.isMessagePending(msgId)).isFalse();
    }

    @Test
    public void scanningStoppedWhenDeviceLeavesNetwork() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network =
                mNetworkManagerImpl.createNetwork("test", device -> device.getAssociationId() == 1);
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        mFakeCompanionDeviceManagerProxy.removeAssociation(1);

        assertThat(session.isActive()).isFalse();
    }

    @Test
    public void scanningStoppedWhenMessageCancelled() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        long messageId =
                network.unicastMessage(
                        /* associationId= */ 1,
                        new byte[] {1},
                        Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        network.cancelMessage(messageId);

        assertThat(session.isActive()).isFalse();
    }

    @Test
    public void scanningStoppedWhenNetworkDestroyed() {
        setupDevice(/* id= */ 1, /* present= */ false, /* nearby= */ false);
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        network.unicastMessage(
                /* associationId= */ 1,
                new byte[] {1},
                Network.MESSAGE_FLAG_SCANNING_IF_NOT_PRESENT);
        assertThat(mFakeScanner.getSessions()).hasSize(1);
        FakeScanner.FakeScanningSession session = mFakeScanner.getAnySession();
        assertThat(session.isActive()).isTrue();

        network.destroy();

        assertThat(session.isActive()).isFalse();
    }

    @Test
    public void advertisingStartedWhenDeviceJoinsNetwork() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        assertThat(mFakeAdvertiser.getSessions()).isEmpty();

        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);

        assertThat(mFakeAdvertiser.getSessions()).hasSize(1);
        assertThat(mFakeAdvertiser.getAnySession().isActive()).isTrue();
    }

    @Test
    public void advertisingStoppedWhenDeviceLeavesNetwork() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        assertThat(mFakeAdvertiser.getSessions()).hasSize(1);
        AdvertisingSession advertisingSession = mFakeAdvertiser.getAnySession();
        assertThat(advertisingSession.isActive()).isTrue();

        mFakeCompanionDeviceManagerProxy.removeAssociation(1);

        assertThat(mFakeAdvertiser.getSessions()).isEmpty();
        assertThat(advertisingSession.isActive()).isFalse();
    }

    @Test
    public void advertisingStoppedWhenNetworkDestroyed() {
        Network network = mNetworkManagerImpl.createNetwork("test", INCLUDE_ALL_DEVICES);
        setupDevice(/* id= */ 1, /* present= */ true, /* nearby= */ true);
        assertThat(mFakeAdvertiser.getSessions()).hasSize(1);
        AdvertisingSession advertisingSession = mFakeAdvertiser.getAnySession();
        assertThat(advertisingSession.isActive()).isTrue();

        network.destroy();

        assertThat(mFakeAdvertiser.getSessions()).isEmpty();
        assertThat(advertisingSession.isActive()).isFalse();
    }

    @Test
    public void messageSentToMultipleNetworks_allSentInFIFOOrder() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network1 = mNetworkManagerImpl.createNetwork("network1", INCLUDE_ALL_DEVICES);
        Network network2 = mNetworkManagerImpl.createNetwork("network2", INCLUDE_ALL_DEVICES);

        network1.broadcastMessage(new byte[] {1}, 0);
        network2.unicastMessage(1, new byte[] {2}, 0);
        network1.multicastMessage(List.of(1, 2), new byte[] {3}, 0);
        network2.broadcastMessage(new byte[] {4}, 0);

        List<SentMessage> sentMessages = mFakeMessenger.getSentMessages();
        assertThat(sentMessages).hasSize(7);

        // network1.broadcastMessage(new byte[] {1}, 0);
        assertThat(sentMessages.get(0).networkId).isEqualTo("network1");
        assertThat(sentMessages.get(0).associationId).isEqualTo(1);
        assertThat(sentMessages.get(0).message).isEqualTo(new byte[] {1});
        assertThat(sentMessages.get(1).networkId).isEqualTo("network1");
        assertThat(sentMessages.get(1).associationId).isEqualTo(2);
        assertThat(sentMessages.get(1).message).isEqualTo(new byte[] {1});

        // network2.unicastMessage(1, new byte[] {2}, 0);
        assertThat(sentMessages.get(2).networkId).isEqualTo("network2");
        assertThat(sentMessages.get(2).associationId).isEqualTo(1);
        assertThat(sentMessages.get(2).message).isEqualTo(new byte[] {2});

        // network1.multicastMessage(List.of(1, 2), new byte[] {3}, 0);
        assertThat(sentMessages.get(3).networkId).isEqualTo("network1");
        assertThat(sentMessages.get(3).associationId).isEqualTo(1);
        assertThat(sentMessages.get(3).message).isEqualTo(new byte[] {3});
        assertThat(sentMessages.get(4).networkId).isEqualTo("network1");
        assertThat(sentMessages.get(4).associationId).isEqualTo(2);
        assertThat(sentMessages.get(4).message).isEqualTo(new byte[] {3});

        // network2.broadcastMessage(new byte[] {4}, 0);
        assertThat(sentMessages.get(5).networkId).isEqualTo("network2");
        assertThat(sentMessages.get(5).associationId).isEqualTo(1);
        assertThat(sentMessages.get(5).message).isEqualTo(new byte[] {4});
        assertThat(sentMessages.get(6).networkId).isEqualTo("network2");
        assertThat(sentMessages.get(6).associationId).isEqualTo(2);
        assertThat(sentMessages.get(6).message).isEqualTo(new byte[] {4});
    }

    @Test
    public void inboundMessage_logsEvent() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        mNetworkManagerImpl.createNetwork("test", FEATURE, INCLUDE_ALL_DEVICES);

        mFakeMessenger.receiveMessage("test", 2, new byte[] {5, 6});

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_RECEIVED,
                                FEATURE));
    }

    @Test
    public void inboundMessage_unknownNetwork_dropped_logsEvent() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        mNetworkManagerImpl.createNetwork("test", FEATURE, INCLUDE_ALL_DEVICES);

        mFakeMessenger.receiveMessage("unknown_network", 1, new byte[] {7, 8});

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_DROPPED,
                                CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_UNSPECIFIED));
    }

    @Test
    public void inboundMessage_associationNotPartOfNetwork_dropped_logsEvent() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        mNetworkManagerImpl.createNetwork("test", FEATURE, device -> device.getAssociationId() < 2);

        // Receive a message from an association that's not part of this network.
        mFakeMessenger.receiveMessage("test", 2, new byte[] {7, 8});

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_INBOUND_MESSAGE_DROPPED,
                                FEATURE));
    }

    @Test
    public void messageQueue_logsEvents() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", FEATURE, INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {1}, 0);

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_STARTED,
                                FEATURE));

        mFakeFrameworkStatsLogProxy.clear();
        mFakeMessenger.getSentMessages().get(0).completeSuccessfully();
        mFakeMessenger.getSentMessages().get(1).completeSuccessfully();
        mNetworkManagerImpl.invalidateNetworks();

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_FINISHED,
                                FEATURE,
                                0L));
    }

    @Test
    public void messageQueue_failed_logsEvent() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", FEATURE, INCLUDE_ALL_DEVICES);

        long id = network.broadcastMessage(new byte[] {1}, 0);
        network.cancelMessage(id);
        mNetworkManagerImpl.invalidateNetworks();

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_QUEUE_FAILED,
                                FEATURE,
                                0L));
    }

    @Test
    public void messageSend_logsEvents() {
        setupTwoDevices(/* present= */ true, /* nearby= */ true);
        Network network = mNetworkManagerImpl.createNetwork("test", FEATURE, INCLUDE_ALL_DEVICES);

        network.broadcastMessage(new byte[] {1}, 0);

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_STARTED,
                                FEATURE));

        mFakeFrameworkStatsLogProxy.clear();
        mFakeClock.advanceTime(1000);
        mFakeMessenger.getSentMessages().get(0).completeSuccessfully();

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_FINISHED,
                                FEATURE,
                                1000L));

        mFakeFrameworkStatsLogProxy.clear();
        mFakeClock.advanceTime(500);
        mFakeMessenger.getSentMessages().get(1).completeWithException(new Exception());

        assertThat(mFakeFrameworkStatsLogProxy.getWrites())
                .contains(
                        new FakeFrameworkStatsLogProxy.WriteCall(
                                CROSS_DEVICE_SYNC_EVENT__EVENT__EVENT_MESSAGE_SEND_FAILED,
                                FEATURE,
                                1500L));
    }

    private void setupDevice(int id, boolean present, boolean nearby) {
        setupDevice(id, mContext.getUserId(), present, nearby);
    }

    private void setupDevice(int id, int userId, boolean present, boolean nearby) {
        AssociationInfo info = createAssociationInfo(id, userId);
        mFakeCompanionDeviceManagerProxy.addAssociation(info);
        if (present) {
            makeDevicePresent(id, nearby);
        }
    }

    private void setupTwoDevices(boolean present, boolean nearby) {
        setupTwoDevices(mContext.getUserId(), present, nearby);
    }

    private void setupTwoDevices(int userId, boolean present, boolean nearby) {
        int nextAssociationId = 0;
        for (AssociationInfo associationInfo :
                mFakeCompanionDeviceManagerProxy.getAllAssociations(UserHandle.USER_ALL)) {
            nextAssociationId = Math.max(nextAssociationId, associationInfo.getId());
        }
        setupDevice(/* id= */ nextAssociationId + 1, userId, present, nearby);
        setupDevice(/* id= */ nextAssociationId + 2, userId, present, nearby);
    }

    private void makeDevicePresent(int associationId, boolean nearby) {
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        associationId, DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED, null));
        if (nearby) {
            mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                    new DevicePresenceEvent(
                            associationId, DevicePresenceEvent.EVENT_BT_CONNECTED, null));
        } else {
            mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                    new DevicePresenceEvent(
                            associationId, DevicePresenceEvent.EVENT_BT_DISCONNECTED, null));
        }
    }

    private void makeDeviceDisappear(int associationId) {
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        associationId, DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED, null));
        mFakeCompanionDeviceManagerProxy.simulateDevicePresenceEvent(
                new DevicePresenceEvent(
                        associationId, DevicePresenceEvent.EVENT_BT_DISCONNECTED, null));
    }

    private AssociationInfo createAssociationInfo(int id) {
        return createAssociationInfo(id, mContext.getUserId());
    }

    private AssociationInfo createAssociationInfo(int id, int userId) {
        return new AssociationInfo.Builder(
                        id, userId, "com.android.server.companion.datatransfer.crossdevicesync")
                .setDisplayName("displayName" + id)
                .build();
    }

    private void verifyReceivedDeviceChanged(NetworkListener listener, int associationId) {
        ArgumentCaptor<RemoteDevice> captor = ArgumentCaptor.forClass(RemoteDevice.class);
        verify(listener, atLeastOnce()).onDeviceChanged(captor.capture());
        assertThat(
                        captor.getAllValues().stream()
                                .filter(device -> device.getAssociationId() == associationId)
                                .count())
                .isAtLeast(1);
    }

    private void verifyNotReceivedDeviceChanged(NetworkListener listener, int associationId) {
        ArgumentCaptor<RemoteDevice> captor = ArgumentCaptor.forClass(RemoteDevice.class);
        try {
            verify(listener, atLeastOnce()).onDeviceChanged(captor.capture());
        } catch (Throwable e) {
            // Not invoked.
            return;
        }
        assertThat(
                        captor.getAllValues().stream()
                                .filter(device -> device.getAssociationId() == associationId)
                                .count())
                .isEqualTo(0);
    }
}
