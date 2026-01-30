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

package com.android.server.companion.datasync;

import static android.companion.CompanionDeviceManager.MESSAGE_REQUEST_METADATA_UPDATE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.companion.AssociationInfo;
import android.companion.IOnMessageReceivedListener;
import android.companion.IOnTransportsChangedListener;
import android.os.PersistableBundle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.transport.CompanionTransportManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * This processor orchestrates metadata synchronization between two companion devices.
 */
public class DataSyncProcessor {

    private static final String TAG = "CDM_DataSyncProcessor";

    private final AssociationStore mAssociationStore;
    private final LocalMetadataStore mLocalMetadataStore;
    private final CompanionTransportManager mTransportManager;

    @GuardedBy("mAssociationsWithTransport")
    private final Set<Integer> mAssociationsWithTransport = new HashSet<>();

    public DataSyncProcessor(
            AssociationStore associationStore,
            LocalMetadataStore localMetadataStore,
            CompanionTransportManager transportManager) {
        mAssociationStore = associationStore;
        mLocalMetadataStore = localMetadataStore;
        mTransportManager = transportManager;

        // Register listeners for transport and message events.
        mTransportManager.addListener(MESSAGE_REQUEST_METADATA_UPDATE,
                new IOnMessageReceivedListener.Stub() {
                    @Override
                    public void onMessageReceived(int associationId, byte[] data) {
                        onReceiveMetadataUpdate(associationId, data);
                    }
                });
        mTransportManager.addListener(
                new IOnTransportsChangedListener.Stub() {
                    @Override
                    public void onTransportsChanged(List<AssociationInfo> associations) {
                        broadcastMetadata(associations);
                    }
                });
    }

    /**
     * Get the cached local metadata for the user.
     */
    public PersistableBundle getLocalMetadata(@UserIdInt int userId) {
        return mLocalMetadataStore.getMetadataForUser(userId);
    }

    /**
     * Set the local metadata for the current device.
     */
    public void setLocalMetadata(@UserIdInt int userId,
            @NonNull String feature,
            @Nullable PersistableBundle metadata) {
        Slog.i(TAG, "Setting local metadata for user=[" + userId
                + "] feature=[" + feature + "] value=[" + metadata + "]...");

        // Update the local metadata for the user.
        final PersistableBundle localMetadata = mLocalMetadataStore.getMetadataForUser(userId);
        if (metadata == null) {
            localMetadata.remove(feature);
        } else {
            localMetadata.putPersistableBundle(feature, metadata);
        }
        mLocalMetadataStore.setMetadataForUser(userId, localMetadata);

        // Isolate the associations with transport for the user to broadcast to.
        List<AssociationInfo> associations = mTransportManager.getAssociationsWithTransport()
                .stream()
                .filter(association -> association.getUserId() == userId)
                .collect(Collectors.toList());
        sendMetadataUpdate(userId, associations);
    }

    private void broadcastMetadata(List<AssociationInfo> associations) {
        synchronized (mAssociationsWithTransport) {
            // Isolate newly attached associations and group by user.
            associations.stream()
                    .filter(association ->
                            !mAssociationsWithTransport.contains(association.getId()))
                    .collect(Collectors.groupingBy(AssociationInfo::getUserId))
                    .forEach(this::sendMetadataUpdate);

            // Update the set of associations with transport.
            mAssociationsWithTransport.clear();
            mAssociationsWithTransport.addAll(associations.stream()
                    .map(AssociationInfo::getId)
                    .collect(Collectors.toSet()));
        }
    }

    private void onReceiveMetadataUpdate(int associationId, byte[] data) {
        Slog.i(TAG, "Received metadata update for associationId=[" + associationId + "]");

        PersistableBundle metadata;
        try {
            metadata = PersistableBundle.readFromStream(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse received metadata", e);
        }
        metadata.putLong(AssociationInfo.METADATA_TIMESTAMP, System.currentTimeMillis());

        AssociationInfo association =
                mAssociationStore.getAssociationWithCallerChecks(associationId);
        AssociationInfo updated = (new AssociationInfo.Builder(association))
                .setMetadata(metadata)
                .build();
        mAssociationStore.updateAssociation(updated);
    }

    private void sendMetadataUpdate(@UserIdInt int userId,
            @NonNull List<AssociationInfo> associations) {
        if (associations.isEmpty()) {
            return;
        }
        int[] associationIds = associations.stream()
                .mapToInt(AssociationInfo::getId)
                .toArray();
        Slog.i(TAG, "Sending metadata update to " + associationIds.length + " associations");

        try {
            // Get the local metadata for the user.
            PersistableBundle localMetadata = mLocalMetadataStore.getMetadataForUser(userId);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            localMetadata.writeToStream(outputStream);

            // Send the metadata update to the remote devices.
            SparseArray<Future<byte[]>> results = mTransportManager.sendMessage(
                    MESSAGE_REQUEST_METADATA_UPDATE,
                    outputStream.toByteArray(),
                    associationIds);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to send metadata update", e);
        }
    }
}
