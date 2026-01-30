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

package com.android.server.companion.datatransfer.continuity.connectivity;

import android.annotation.NonNull;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;

class ConnectedAssociationStore {

    private static final String TAG = "ConnectedAssociationStore";

    private final CompanionDeviceManager mCompanionDeviceManager;
    private final Listener mListener;
    private final Executor mExecutor;
    private final Map<Integer, AssociationInfo> mConnectedAssociations = new HashMap<>();

    @GuardedBy("this")
    private Consumer<List<AssociationInfo>> mAssociationInfoConsumer;

    interface Listener {
        void onTransportConnected(@NonNull AssociationInfo associationInfo);

        void onTransportDisconnected(
                int associationId, @NonNull Collection<AssociationInfo> connectedAssociations);
    }

    ConnectedAssociationStore(
            @NonNull CompanionDeviceManager companionDeviceManager,
            @NonNull Executor executor,
            @NonNull Listener listener) {

        Objects.requireNonNull(companionDeviceManager);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(listener);

        mCompanionDeviceManager = companionDeviceManager;
        mListener = listener;
        mExecutor = executor;
    }

    public void enable() {
        synchronized (this) {
            if (mAssociationInfoConsumer != null) {
                Slog.i(TAG, "ConnectedAssociationStore is already enabled.");
                return;
            }
            mAssociationInfoConsumer = this::onTransportsChanged;
            mCompanionDeviceManager.addOnTransportsChangedListener(
                    mExecutor, mAssociationInfoConsumer);
            Slog.i(TAG, "Enabled ConnectedAssociationStore.");
        }
    }

    public void disable() {
        synchronized (this) {
            if (mAssociationInfoConsumer == null) {
                Slog.i(TAG, "ConnectedAssociationStore is already disabled.");
                return;
            }
            mCompanionDeviceManager.removeOnTransportsChangedListener(mAssociationInfoConsumer);
            mAssociationInfoConsumer = null;
            Slog.i(TAG, "Disabled ConnectedAssociationStore.");
        }
    }

    public Collection<AssociationInfo> getConnectedAssociations() {
        return mConnectedAssociations.values();
    }

    public AssociationInfo getConnectedAssociationById(int associationId) {
        return mConnectedAssociations.get(associationId);
    }

    @VisibleForTesting
    void onTransportsChanged(List<AssociationInfo> associationInfos) {
        List<AssociationInfo> newTaskContinuityAssociations = new ArrayList<>();
        for (AssociationInfo associationInfo : associationInfos) {
            int taskContinuityFlag = associationInfo.getSystemDataSyncFlags()
                    & CompanionDeviceManager.FLAG_TASK_CONTINUITY;
            if (taskContinuityFlag != 0) {
                newTaskContinuityAssociations.add(associationInfo);
            }
        }

        Set<Integer> removedAssociations = new HashSet<>(mConnectedAssociations.keySet());

        Set<AssociationInfo> addedAssociations = new HashSet<>();
        for (AssociationInfo associationInfo : newTaskContinuityAssociations) {
            if (!mConnectedAssociations.containsKey(associationInfo.getId())) {
                addedAssociations.add(associationInfo);
            }

            if (removedAssociations.contains(associationInfo.getId())) {
                removedAssociations.remove(associationInfo.getId());
            }
        }

        for (Integer associationId : removedAssociations) {
            Slog.i(TAG, "Transport disconnected for association: " + associationId);

            mConnectedAssociations.remove(associationId);
            mListener.onTransportDisconnected(associationId, newTaskContinuityAssociations);
        }

        for (AssociationInfo associationInfo : addedAssociations) {
            Slog.i(TAG, "Transport connected for association: " + associationInfo.getId());

            mConnectedAssociations.put(associationInfo.getId(), associationInfo);
            mListener.onTransportConnected(associationInfo);
        }
    }
}
