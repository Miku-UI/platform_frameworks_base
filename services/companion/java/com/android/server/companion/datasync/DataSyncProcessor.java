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
import android.os.Binder;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.transport.CompanionTransportManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
                        Binder.withCleanCallingIdentity(() -> onReceiveMetadataUpdate(associationId,
                                data));
                    }
                });
        mTransportManager.addListener(
                new IOnTransportsChangedListener.Stub() {
                    @Override
                    public void onTransportsChanged(List<AssociationInfo> associations) {
                        Binder.withCleanCallingIdentity(() -> broadcastMetadata(associations));
                    }
                });
    }

    /**
     * Get the cached local metadata for the user.
     */
    @NonNull
    public PersistableBundle getLocalMetadata(@UserIdInt int userId) {
        PersistableBundle userMetadata = mLocalMetadataStore.readData(userId);
        if (userId == UserHandle.USER_ALL) {
            return userMetadata;
        }

        // Merge the user metadata into the device metadata.
        // Prioritize the user metadata over the device metadata for each entry key conflict.
        PersistableBundle metadata = mLocalMetadataStore.readData(UserHandle.USER_ALL);
        for (String feature : userMetadata.keySet()) {
            if (metadata.containsKey(feature)) {
                PersistableBundle merged = metadata.getPersistableBundle(feature).deepCopy();
                merged.putAll(userMetadata.getPersistableBundle(feature));
                metadata.putPersistableBundle(feature, merged);
            } else {
                metadata.putPersistableBundle(feature, userMetadata.getPersistableBundle(feature));
            }
        }

        return metadata;
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
        final PersistableBundle localMetadata = mLocalMetadataStore.readData(userId);
        if (metadata == null) {
            localMetadata.remove(feature);
        } else {
            localMetadata.putPersistableBundle(feature, metadata);
        }
        mLocalMetadataStore.writeData(userId, localMetadata);

        // Isolate the associations with transport for the user to broadcast to.
        mTransportManager.getAssociationsWithTransport()
                .stream()
                .filter(association -> userId == UserHandle.USER_ALL
                        || association.getUserId() == userId)
                .collect(Collectors.groupingBy(AssociationInfo::getUserId))
                .forEach(this::sendMetadataUpdate);
    }

    /**
     * Set the remote metadata for an association.
     */
    @VisibleForTesting
    public void setRemoteMetadata(int associationId,
            @NonNull PersistableBundle metadata) {
        Slog.i(TAG, "Setting remote metadata for association id=[" + associationId
                + "] value=[" + metadata + "]...");
        metadata.putLong(AssociationInfo.METADATA_TIMESTAMP, System.currentTimeMillis());
        mAssociationStore.updateAssociation(associationId,
                a -> (new AssociationInfo.Builder(a))
                        .setMetadata(metadata)
                        .build());
    }

    /**
     * Enable system data sync.
     */
    public void enableSystemDataSync(int associationId, int flags) {
        Slog.i(TAG, "Enabling system data sync flags for association id=[" + associationId
                + "] flags=[" + flags + "]...");

        mAssociationStore.updateAssociation(associationId,
                a -> (new AssociationInfo.Builder(a))
                        .setSystemDataSyncFlags(a.getSystemDataSyncFlags() | flags)
                        .build());
    }

    /**
     * Disable system data sync.
     */
    public void disableSystemDataSync(int associationId, int flags) {
        Slog.i(TAG, "Disabling system data sync flags for association id=[" + associationId
                + "] flags=[" + flags + "]...");

        mAssociationStore.updateAssociation(associationId,
                a -> (new AssociationInfo.Builder(a))
                        .setSystemDataSyncFlags(a.getSystemDataSyncFlags() & (~flags))
                        .build());
    }


    private void broadcastMetadata(List<AssociationInfo> associations) {
        SparseArray<List<AssociationInfo>> newAssociations = new SparseArray<>();
        synchronized (mAssociationsWithTransport) {
            // Isolate newly attached associations and group by user.
            for (AssociationInfo association : associations) {
                if (!mAssociationsWithTransport.contains(association.getId())) {
                    int userId = association.getUserId();
                    List<AssociationInfo> userAssociations = newAssociations.get(userId);
                    if (userAssociations == null) {
                        userAssociations = new java.util.ArrayList<>();
                        newAssociations.put(userId, userAssociations);
                    }
                    userAssociations.add(association);
                }
            }

            // Update the set of associations with transport.
            mAssociationsWithTransport.clear();
            for (AssociationInfo association : associations) {
                mAssociationsWithTransport.add(association.getId());
            }
        }

        for (int i = 0; i < newAssociations.size(); i++) {
            sendMetadataUpdate(newAssociations.keyAt(i), newAssociations.valueAt(i));
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

        setRemoteMetadata(associationId, metadata);
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
            PersistableBundle localMetadata = getLocalMetadata(userId);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            localMetadata.writeToStream(outputStream);

            // Send the metadata update to the remote devices.
            SparseArray<CompletableFuture<byte[]>> results = mTransportManager.sendMessage(
                    MESSAGE_REQUEST_METADATA_UPDATE,
                    outputStream.toByteArray(),
                    associationIds);

            // Update the metadata sent time after receiving ACK.
            for (int associationId : associationIds) {
                results.get(associationId).thenRunAsync(() -> {
                    mAssociationStore.updateAssociation(associationId,
                            a -> (new AssociationInfo.Builder(a))
                                    .setTimeMetadataSent(System.currentTimeMillis())
                                    .build());
                });
            }

        } catch (IOException e) {
            Slog.e(TAG, "Failed to send metadata update", e);
        }
    }
}
