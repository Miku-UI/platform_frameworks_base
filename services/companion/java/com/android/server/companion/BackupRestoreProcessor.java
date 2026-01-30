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

package com.android.server.companion;

import static android.os.UserHandle.getCallingUserId;

import static com.android.server.companion.association.AssociationDiskStore.readAssociationsFromPayload;
import static com.android.server.companion.utils.RolesUtils.addRoleHolderForAssociation;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.companion.AssociationInfo;
import android.companion.Flags;
import android.companion.datatransfer.SystemDataTransferRequest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.os.PersistableBundle;
import android.util.Slog;

import com.android.internal.util.CollectionUtils;
import com.android.server.companion.association.AssociationDiskStore;
import com.android.server.companion.association.AssociationRequestsProcessor;
import com.android.server.companion.association.AssociationStore;
import com.android.server.companion.association.Associations;
import com.android.server.companion.datasync.LocalMetadataStore;
import com.android.server.companion.datatransfer.SystemDataTransferRequestStore;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@SuppressLint("LongLogTag")
class BackupRestoreProcessor {
    private static final String TAG = "CDM_BackupRestoreProcessor";
    private static final int BACKUP_AND_RESTORE_VERSION = 0;

    private final Context mContext;
    @NonNull
    private final PackageManagerInternal mPackageManagerInternal;
    @NonNull
    private final AssociationStore mAssociationStore;
    @NonNull
    private final AssociationDiskStore mAssociationDiskStore;
    @NonNull
    private final SystemDataTransferRequestStore mSystemDataTransferRequestStore;
    @NonNull
    private final AssociationRequestsProcessor mAssociationRequestsProcessor;
    @NonNull
    private final LocalMetadataStore mLocalMetadataStore;

    BackupRestoreProcessor(@NonNull Context context,
                           @NonNull PackageManagerInternal packageManagerInternal,
                           @NonNull AssociationStore associationStore,
                           @NonNull AssociationDiskStore associationDiskStore,
                           @NonNull SystemDataTransferRequestStore systemDataTransferRequestStore,
                           @NonNull AssociationRequestsProcessor associationRequestsProcessor,
                           @NonNull LocalMetadataStore localMetadataStore) {
        mContext = context;
        mPackageManagerInternal = packageManagerInternal;
        mAssociationStore = associationStore;
        mAssociationDiskStore = associationDiskStore;
        mSystemDataTransferRequestStore = systemDataTransferRequestStore;
        mAssociationRequestsProcessor = associationRequestsProcessor;
        mLocalMetadataStore = localMetadataStore;
    }

    /**
     * Generate CDM state payload to be backed up.
     * Backup payload is formatted as following:
     * | (4) payload version | (4) AssociationInfo length | AssociationInfo XML
     * | (4) SystemDataTransferRequest length | SystemDataTransferRequest XML (without userId)
     * | (4) LocalMetadata length | LocalMetadata XML |
     */
    byte[] getBackupPayload(int userId) {
        Slog.i(TAG, "getBackupPayload() userId=[" + userId + "].");

        byte[] associationsPayload = mAssociationDiskStore.getBackupPayload(userId);
        int associationsPayloadLength = associationsPayload.length;

        // System data transfer requests are persisted up-to-date already
        byte[] requestsPayload = mSystemDataTransferRequestStore.getBackupPayload(userId);
        int requestsPayloadLength = requestsPayload.length;

        // Local metadata is persisted up-to-date already
        byte[] metadataPayload = mLocalMetadataStore.getBackupPayload(userId);
        int metadataPayloadLength = metadataPayload.length;

        int payloadSize = /* 4 integers */ 16
                + associationsPayloadLength
                + requestsPayloadLength
                + metadataPayloadLength;

        return ByteBuffer.allocate(payloadSize)
                .putInt(BACKUP_AND_RESTORE_VERSION)
                .putInt(associationsPayloadLength)
                .put(associationsPayload)
                .putInt(requestsPayloadLength)
                .put(requestsPayload)
                .putInt(metadataPayloadLength)
                .put(metadataPayload)
                .array();
    }

    /**
     * Create new associations and system data transfer request consents using backed up payload.
     */
    void applyRestoredPayload(byte[] payload, int userId) {
        Slog.i(TAG, "applyRestoredPayload() userId=[" + userId + "], payload size=["
                + payload.length + "].");

        if (payload.length == 0) {
            Slog.i(TAG, "CDM backup payload was empty.");
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);

        // Make sure that payload version matches current version to ensure proper deserialization
        int version = buffer.getInt();
        if (version != BACKUP_AND_RESTORE_VERSION) {
            Slog.e(TAG, "Unsupported backup payload version");
            return;
        }

        // Pre-load the bytes into memory before processing them to ensure payload mal-formatting
        // error is caught early on.
        final byte[] associationsPayload;
        final byte[] requestsPayload;
        final byte[] metadataPayload;
        try {
            // Read the bytes containing backed-up associations
            associationsPayload = new byte[buffer.getInt()];
            buffer.get(associationsPayload);

            // Read the bytes containing backed-up system data transfer requests user consent
            requestsPayload = new byte[buffer.getInt()];
            buffer.get(requestsPayload);
        } catch (Exception bufferException) {
            Slog.e(TAG, "CDM backup payload was mal-formatted.", bufferException);
            return;
        }

        // Metadata was introduced in 26Q2. Ignore if the payload is older and missing the metadata
        // payload section.
        // Restoring local metadata can be done independently of associations and requests, so it
        // will not return early even if the payload is mal-formatted.
        try {
            // Read the bytes containing backed-up local metadata
            metadataPayload = new byte[buffer.getInt()];
            buffer.get(metadataPayload);

            // Handle conflict by prioritizing current metadata over restored metadata.
            PersistableBundle currentMetadata = mLocalMetadataStore.getMetadataForUser(userId);
            PersistableBundle restoredMetadata =
                    mLocalMetadataStore.readMetadataFromPayload(metadataPayload);
            restoredMetadata.putAll(currentMetadata);
            mLocalMetadataStore.setMetadataForUser(userId, restoredMetadata);
        } catch (Exception bufferException) {
            Slog.w(TAG, "Metadata payload was malformatted or missing.", bufferException);
        }

        // Restore associations and system data transfer requests next.
        final Associations restoredAssociations = readAssociationsFromPayload(
                associationsPayload, userId);

        List<SystemDataTransferRequest> restoredRequestsForUser =
                mSystemDataTransferRequestStore.readRequestsFromPayload(requestsPayload, userId);

        // Get a list of installed packages ahead of time.
        List<ApplicationInfo> installedApps = mPackageManagerInternal.getInstalledApplications(
                0, userId, getCallingUserId());

        // Restored device may have a different user ID than the backed-up user's user-ID. Since
        // association ID is dependent on the user ID, restored associations must account for
        // this potential difference on their association IDs.
        for (AssociationInfo restored : restoredAssociations.getAssociations()) {
            // Don't restore a revoked association. Since they weren't added to the device being
            // restored in the first place, there is no need to worry about revoking a role that
            // was never granted either.
            if (restored.isRevoked()) {
                continue;
            }

            // Filter restored requests for those that belong to the restored association.
            List<SystemDataTransferRequest> restoredRequests = CollectionUtils.filter(
                    restoredRequestsForUser, it -> it.getAssociationId() == restored.getId());

            // Handle collision: If a local association belonging to the same package already exists
            // and their tags match, then keep the local one in favor of creating a new association.
            if (handleCollision(userId, restored, restoredRequests)) {
                continue;
            }

            // Create a new association reassigned to this user and a valid association ID
            final String packageName = restored.getPackageName();
            final int newId = mAssociationStore.getNextId();
            AssociationInfo newAssociation = new AssociationInfo.Builder(newId, userId, packageName,
                    restored).build();

            // Check if the companion app for this association is already installed, then do one
            // of the following:
            // (1) If the app is already installed, then go ahead and add this association and grant
            // the role attached to this association to the app.
            // (2) If the app isn't yet installed, then add this association to the list of pending
            // associations to be added when the package is installed in the future.
            boolean isPackageInstalled = installedApps.stream().anyMatch(
                    app -> packageName.equals(app.packageName));
            if (isPackageInstalled) {
                mAssociationRequestsProcessor.maybeGrantRoleAndStoreAssociation(newAssociation,
                        null, null);
            } else {
                newAssociation = (new AssociationInfo.Builder(newAssociation)).setPending(true)
                        .build();
                mAssociationStore.addAssociation(newAssociation);
            }

            // Re-map restored system data transfer requests to newly created associations
            for (SystemDataTransferRequest restoredRequest : restoredRequests) {
                SystemDataTransferRequest newRequest = restoredRequest.copyWithNewId(newId);
                newRequest.setUserId(userId);
                mSystemDataTransferRequestStore.writeRequest(userId, newRequest);
            }
        }
    }

    public void restorePendingAssociations(int userId, String packageName) {
        List<AssociationInfo> pendingAssociations = mAssociationStore.getPendingAssociations(userId,
                packageName);
        if (!pendingAssociations.isEmpty()) {
            Slog.i(TAG, "Found pending associations for package=[" + packageName
                    + "]. Restoring...");
        }
        for (AssociationInfo association : pendingAssociations) {
            AssociationInfo newAssociation = new AssociationInfo.Builder(association)
                    .setPending(false)
                    .build();
            addRoleHolderForAssociation(mContext, newAssociation, success -> {
                if (success) {
                    mAssociationStore.updateAssociation(newAssociation);
                    Slog.i(TAG, "Association=[" + association + "] is restored.");
                } else {
                    Slog.e(TAG, "Failed to restore association=[" + association + "].");
                }
            });
        }
    }

    /**
     * Detects and handles collision between restored association and local association. Returns
     * true if there has been a collision and false otherwise.
     */
    private boolean handleCollision(@UserIdInt int userId,
            AssociationInfo restored,
            List<SystemDataTransferRequest> restoredRequests) {
        List<AssociationInfo> localAssociations = mAssociationStore.getActiveAssociationsByPackage(
                restored.getUserId(), restored.getPackageName());
        Predicate<AssociationInfo> isSameDevice = associationInfo -> {
            boolean matchesMacAddress = Objects.equals(
                    associationInfo.getDeviceMacAddress(),
                    restored.getDeviceMacAddress());
            boolean matchesDeviceId = Flags.associationTag()
                    && (associationInfo.getDeviceId() != null
                    && associationInfo.getDeviceId().equals(restored.getDeviceId()));
            return matchesMacAddress || matchesDeviceId;
        };
        AssociationInfo local = CollectionUtils.find(localAssociations, isSameDevice);

        // No collision detected
        if (local == null) {
            return false;
        }

        Slog.d(TAG, "Conflict detected with association id=" + local.getId()
                + " while restoring CDM backup. Keeping local association.");

        List<SystemDataTransferRequest> localRequests = mSystemDataTransferRequestStore
                .readRequestsByAssociationId(userId, local.getId());

        // If local association doesn't have any existing system data transfer request of same type
        // attached, then restore corresponding request onto the local association. Otherwise, keep
        // the locally stored request.
        for (SystemDataTransferRequest restoredRequest : restoredRequests) {
            boolean requestTypeExists = CollectionUtils.any(localRequests, request ->
                    request.getDataType() == restoredRequest.getDataType());

            // This type of request consent already exists for the association.
            if (requestTypeExists) {
                continue;
            }

            Slog.d(TAG, "Restoring " + restoredRequest.getClass().getSimpleName()
                    + " to an existing association id=[" + local.getId() + "].");

            SystemDataTransferRequest newRequest =
                    restoredRequest.copyWithNewId(local.getId());
            newRequest.setUserId(userId);
            mSystemDataTransferRequestStore.writeRequest(userId, newRequest);
        }

        return true;
    }
}
