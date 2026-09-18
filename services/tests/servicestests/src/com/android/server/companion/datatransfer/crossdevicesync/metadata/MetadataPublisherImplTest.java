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
package com.android.server.companion.datatransfer.crossdevicesync.metadata;

import static android.companion.CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC;

import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_METADATA;
import static com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisherImpl.LAST_METADATA_UPDATE_TIMESTAMP_PREFIX;
import static com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisherImpl.NETWORK_ID;
import static com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisherImpl.NETWORK_ID_USER_ALL;

import static com.google.common.truth.Truth.assertThat;

import android.companion.AssociationInfo;
import android.os.PersistableBundle;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.network.fake.FakeNetworkManager.FakeRemoteDevice;
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MetadataPublisherImplTest extends SyncServiceTestBase {
    @Before
    public void setUp() {
        mFakeClock.setCurrentTime(100);
        mFakeNetworkManager.init();
    }

    @Test
    public void init_hasNewMetadata_pingRemoteDevices() {
        int userId = 2;
        addUser(userId);
        mSharedPreferences
                .edit()
                .putLong(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId, 10)
                .commit();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 5));

        mMetadataPublisher.init();

        // Verify that we pinged the remote device.
        assertThat(device.getSentMessages()).hasSize(1);
    }

    @Test
    public void init_noNewMetadata_notPingRemoteDevices() {
        int userId = 2;
        addUser(userId);
        mSharedPreferences
                .edit()
                .putLong(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId, 10)
                .commit();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        mMetadataPublisher.init();

        // Verify that we did not ping the remote device.
        assertThat(device.getSentMessages()).isEmpty();
    }

    @Test
    public void init_newDeviceIsPinged() {
        int userId = 2;
        addUser(userId);
        mSharedPreferences
                .edit()
                .putLong(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId, 10)
                .commit();
        mMetadataPublisher.init();

        // A new device is added.
        FakeRemoteDevice device = mFakeNetworkManager.addRemoteDeviceForUser(userId);

        // Verify that we pinged the new device.
        assertThat(device.getSentMessages()).hasSize(1);
        assertThat(mFakeNetworkManager.getNetworks().get(0).getNetworkId()).isEqualTo(NETWORK_ID);
    }

    @Test
    public void init_newDeviceIsPingedForUserAllMetadata() {
        mSharedPreferences
                .edit()
                .putLong(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + UserHandle.USER_ALL, 10)
                .commit();
        mMetadataPublisher.init();

        // A new device is added.
        FakeRemoteDevice device = mFakeNetworkManager.addRemoteDeviceForUser(1);

        // Verify that we pinged the new device.
        assertThat(device.getSentMessages()).hasSize(1);
        assertThat(mFakeNetworkManager.getNetworks().get(0).getNetworkId())
                .isEqualTo(NETWORK_ID_USER_ALL);
    }

    @Test
    public void setMetadata_metadataSentToCdm() {
        int userId = 2;
        addUser(userId);
        mMetadataPublisher.init();

        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        assertThat(
                        mFakeCompanionDeviceManagerProxy
                                .getLocalMetadata(userId)
                                .getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC)
                                .getBoolean("key"))
                .isTrue();
        assertThat(mSharedPreferences.contains(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId))
                .isTrue();
    }

    @Test
    public void setMetadata_pingRemoteDevices() {
        int userId = 2;
        addUser(userId);
        mMetadataPublisher.init();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        assertThat(device.getSentMessages()).hasSize(1);
        var network = mFakeNetworkManager.getNetworks().get(0);
        assertThat(network.getNetworkId()).isEqualTo(NETWORK_ID);
        assertThat(network.getFeature())
                .isEqualTo(CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_METADATA);
    }

    @Test
    public void setMetadata_userAll_pingRemoteDevices() {
        addUser(1);
        addUser(2);
        mMetadataPublisher.init();
        FakeRemoteDevice device1 =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(
                                /* id= */ 1, /* userId= */ 1, /* timeMetadataSent= */ 10));
        FakeRemoteDevice device2 =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(
                                /* id= */ 2, /* userId= */ 2, /* timeMetadataSent= */ 10));

        mMetadataPublisher.putBooleanMetaData(UserHandle.USER_ALL, "key", true);

        assertThat(device1.getSentMessages()).hasSize(1);
        assertThat(device2.getSentMessages()).hasSize(1);
        assertThat(device1.getSentMessages().get(0).network()).isEqualTo(NETWORK_ID_USER_ALL);
        assertThat(device2.getSentMessages().get(0).network()).isEqualTo(NETWORK_ID_USER_ALL);
        var network = mFakeNetworkManager.getNetworks().get(0);
        assertThat(network.getNetworkId()).isEqualTo(NETWORK_ID_USER_ALL);
        assertThat(network.getFeature())
                .isEqualTo(CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_METADATA);
    }

    @Test
    public void setMetadata_newDeviceIsPinged() {
        int userId = 2;
        addUser(userId);
        mMetadataPublisher.init();
        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        // Simulate a device restart by recreating the network manager.
        mMetadataPublisher.destroy();
        mFakeNetworkManager.destroy();
        mFakeNetworkManager.init();
        mMetadataPublisher.init();

        // New device is added
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        // Verify that we pinged the new device.
        assertThat(device.getSentMessages()).hasSize(1);
        assertThat(device.getSentMessages().get(0).network()).isEqualTo(NETWORK_ID);
    }

    @Test
    public void setUserAllMetadata_newDeviceIsPinged() {
        int userId = 2;
        addUser(userId);
        mMetadataPublisher.init();
        mFakeClock.setCurrentTime(100);
        mMetadataPublisher.putBooleanMetaData(UserHandle.USER_ALL, "key", true);

        // Simulate a device restart by recreating the network manager.
        mMetadataPublisher.destroy();
        mFakeNetworkManager.destroy();
        mFakeNetworkManager.init();
        mMetadataPublisher.init();

        // New device is added
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        // Verify that we pinged the new device.
        assertThat(device.getSentMessages()).hasSize(1);
        assertThat(device.getSentMessages().get(0).network()).isEqualTo(NETWORK_ID_USER_ALL);
    }

    @Test
    public void setMetadata_noChange_doNothing() {
        int userId = 2;
        addUser(userId);
        PersistableBundle metadata = new PersistableBundle();
        metadata.putBoolean("key", true);
        mFakeCompanionDeviceManagerProxy.setLocalMetadata(
                userId, FEATURE_CROSS_DEVICE_SYNC, metadata);
        mMetadataPublisher.init();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        // Set metadata.
        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        // Verify that we didn't update shared prefs.
        assertThat(mSharedPreferences.contains(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId))
                .isFalse();
        // Verify that we didn't ping any devices.
        assertThat(device.getSentMessages()).isEmpty();
    }

    @Test
    public void setMetadata_multipleUpdatesForSameUser_areApplied() {
        // Verifies that multiple metadata updates for the same user are correctly applied.
        // With a synchronous executor, this results in multiple flushes, but the end result
        // should be a merged set of metadata.
        int userId = 2;
        addUser(userId);
        mMetadataPublisher.init();
        FakeRemoteDevice device =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId, /* timeMetadataSent= */ 10));

        // Set two different metadata keys.
        mMetadataPublisher.putBooleanMetaData(userId, "key_bool", true);
        mMetadataPublisher.putIntMetaData(userId, "key_int", 42);

        // Verify both metadata values are present in the final bundle.
        PersistableBundle metadata =
                mFakeCompanionDeviceManagerProxy
                        .getLocalMetadata(userId)
                        .getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC);
        assertThat(metadata.getBoolean("key_bool")).isTrue();
        assertThat(metadata.getInt("key_int")).isEqualTo(42);
        assertThat(metadata.size()).isEqualTo(2);

        // Verify shared preferences are updated.
        assertThat(mSharedPreferences.contains(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId))
                .isTrue();

        // With a synchronous executor, each call to set metadata will trigger a broadcast ping.
        assertThat(device.getSentMessages()).hasSize(2);
    }

    @Test
    public void setMetadata_multipleUpdatesForDifferentUsers_areApplied() {
        // Verifies that metadata updates for different users are handled correctly.
        int userId1 = 1;
        int userId2 = 2;
        addUser(userId1);
        addUser(userId2);
        mMetadataPublisher.init();
        FakeRemoteDevice device1 =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 1, userId1, /* timeMetadataSent= */ 10));
        FakeRemoteDevice device2 =
                mFakeNetworkManager.addRemoteDevice(
                        createAssociationInfo(/* id= */ 2, userId2, /* timeMetadataSent= */ 10));

        // Set metadata for two different users.
        mMetadataPublisher.putBooleanMetaData(userId1, "key1", true);
        mMetadataPublisher.putIntMetaData(userId2, "key2", 123);

        // Verify metadata for user 1.
        PersistableBundle metadata1 =
                mFakeCompanionDeviceManagerProxy
                        .getLocalMetadata(userId1)
                        .getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC);
        assertThat(metadata1.getBoolean("key1")).isTrue();
        assertThat(mSharedPreferences.contains(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId1))
                .isTrue();
        assertThat(device1.getSentMessages()).hasSize(1);

        // Verify metadata for user 2.
        PersistableBundle metadata2 =
                mFakeCompanionDeviceManagerProxy
                        .getLocalMetadata(userId2)
                        .getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC);
        assertThat(metadata2.getInt("key2")).isEqualTo(123);
        assertThat(mSharedPreferences.contains(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId2))
                .isTrue();
        assertThat(device2.getSentMessages()).hasSize(1);
    }

    @Test
    public void setMetadata_overwritesExistingValue() {
        // Verifies that setting metadata with an existing key overwrites the previous value.
        int userId = 2;
        addUser(userId);
        mMetadataPublisher.init();

        // Set an initial value.
        mMetadataPublisher.putStringMetaData(userId, "key", "value1");

        // Set a new value for the same key.
        mMetadataPublisher.putStringMetaData(userId, "key", "value2");

        // Verify the value was overwritten.
        PersistableBundle metadata =
                mFakeCompanionDeviceManagerProxy
                        .getLocalMetadata(userId)
                        .getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC);
        assertThat(metadata.getString("key")).isEqualTo("value2");
        assertThat(metadata.size()).isEqualTo(1);
    }

    @Test
    public void userRemoved_metadataCleanedUp() {
        int userId = 2;
        addUser(userId);
        mMetadataPublisher.init();
        mMetadataPublisher.putBooleanMetaData(userId, "key", true);

        mFakeUserHelper.removeUser(UserHandle.of(userId));

        assertThat(mSharedPreferences.contains(LAST_METADATA_UPDATE_TIMESTAMP_PREFIX + userId))
                .isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy
                                .getLocalMetadata(userId)
                                .getPersistableBundle(FEATURE_CROSS_DEVICE_SYNC))
                .isNull();
        assertThat(mFakeNetworkManager.getNetworks()).isEmpty();
    }

    private void addUser(int userId) {
        mFakeUserHelper.addUser(UserHandle.of(userId));
    }

    private AssociationInfo createAssociationInfo(int id, int userId, long timeMetadataSent) {
        return new AssociationInfo.Builder(
                        id, userId, "com.android.server.companion.datatransfer.crossdevicesync")
                .setDisplayName("displayName" + id)
                .setTimeMetadataSent(timeMetadataSent)
                .build();
    }
}
