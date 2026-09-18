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

import static android.companion.CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.messages.Proto;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Facilitates communication between devices, including sending and receiving messages between
 * devices. Internally, it uses the {@link CompanionDeviceManager} to send and receive messages.
 */
public class TaskContinuityMessenger {

    private static final String TAG = TaskContinuityMessenger.class.getSimpleName();

    private final int mUserId;
    private final Context mContext;

    @GuardedBy("this")
    private final ArraySet<Listener> mListeners = new ArraySet<>(0);

    @GuardedBy("this")
    private final ArrayMap<Integer, AssociationInfo> mConnectedAssociations = new ArrayMap<>(0);

    @GuardedBy("this")
    private BiConsumer<Integer, byte[]> mIncomingMessageConsumer;

    @GuardedBy("this")
    private Consumer<List<AssociationInfo>> mAssociationInfoConsumer;

    public interface Listener {
        void onAssociationConnected(@NonNull AssociationInfo associationInfo);

        void onAssociationDisconnected(int associationId);

        void onMessageReceived(int associationId, @NonNull TaskContinuityMessage message);
    }

    public TaskContinuityMessenger(int userId, @NonNull Context context) {
        mUserId = userId;
        mContext = Objects.requireNonNull(context);
    }

    public void addListener(@NonNull Listener listener) {
        synchronized (this) {
            mListeners.add(Objects.requireNonNull(listener));
            if (mListeners.size() == 1) {
                CompanionDeviceManager companionDeviceManager =
                        (CompanionDeviceManager)
                                mContext.getSystemService(Context.COMPANION_DEVICE_SERVICE);
                if (mIncomingMessageConsumer == null) {
                    mIncomingMessageConsumer = this::onMessageReceived;
                    companionDeviceManager.addOnMessageReceivedListener(
                            mContext.getMainExecutor(),
                            MESSAGE_ONEWAY_TASK_CONTINUITY,
                            mIncomingMessageConsumer);
                }

                if (mAssociationInfoConsumer == null) {
                    mAssociationInfoConsumer = this::onTransportsChanged;
                    companionDeviceManager.addOnTransportsChangedListener(
                            mContext.getMainExecutor(), mAssociationInfoConsumer);
                }
            }
        }
    }

    public void removeListener(@NonNull Listener listener) {
        synchronized (this) {
            mListeners.remove(Objects.requireNonNull(listener));
            if (mListeners.isEmpty()) {
                CompanionDeviceManager companionDeviceManager =
                        (CompanionDeviceManager)
                                mContext.getSystemService(Context.COMPANION_DEVICE_SERVICE);
                if (mIncomingMessageConsumer != null) {
                    companionDeviceManager.removeOnMessageReceivedListener(
                            MESSAGE_ONEWAY_TASK_CONTINUITY, mIncomingMessageConsumer);
                    mIncomingMessageConsumer = null;
                }

                if (mAssociationInfoConsumer != null) {
                    companionDeviceManager.removeOnTransportsChangedListener(
                            mAssociationInfoConsumer);
                    mAssociationInfoConsumer = null;
                }
            }
        }
    }

    @NonNull
    public Collection<AssociationInfo> getConnectedAssociations() {
        synchronized (this) {
            return List.copyOf(mConnectedAssociations.values());
        }
    }

    @Nullable
    public AssociationInfo getAssociationInfo(int associationId) {
        synchronized (this) {
            return mConnectedAssociations.get(associationId);
        }
    }

    public enum SendMessageResult {
        SUCCESS,
        FAILURE_MESSAGE_SERIALIZATION_FAILED,
        FAILURE_ASSOCIATION_NOT_FOUND,
        FAILURE_INTERNAL_ERROR,
    }

    public SendMessageResult sendMessage(
            int associationId, @NonNull TaskContinuityMessage message) {
        return sendMessage(new int[] {associationId}, Objects.requireNonNull(message));
    }

    public SendMessageResult sendMessage(
            @NonNull int[] associationIds, @NonNull TaskContinuityMessage message) {

        Objects.requireNonNull(associationIds);
        Objects.requireNonNull(message);

        Slog.i(TAG, "Sending message to " + associationIds.length + " associations.");
        byte[] serializedMessage;
        try {
            serializedMessage = Proto.toBytes(message);
        } catch (IOException e) {
            Slog.e(TAG, "Failed to serialize message: " + message, e);
            recordMessageSentEventForLogging(
                    associationIds.length,
                    message,
                    FrameworkStatsLog
                            .TASK_CONTINUITY_MESSAGE_SENT__RESULT__TASK_CONTINUITY_MESSAGE_SENT_RESULT_FAILURE_MESSAGE_SERIALIZATION_FAILED);
            return SendMessageResult.FAILURE_MESSAGE_SERIALIZATION_FAILED;
        }

        for (int associationId : associationIds) {
            if (getAssociationInfo(associationId) == null) {
                Slog.w(TAG, "Association " + associationId + " is not connected.");
                recordMessageSentEventForLogging(
                        associationIds.length,
                        message,
                        FrameworkStatsLog
                                .TASK_CONTINUITY_MESSAGE_SENT__RESULT__TASK_CONTINUITY_MESSAGE_SENT_RESULT_FAILURE_ASSOCIATION_NOT_FOUND);
                return SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND;
            }
        }

        CompanionDeviceManager companionDeviceManager =
                (CompanionDeviceManager)
                        mContext.getSystemService(Context.COMPANION_DEVICE_SERVICE);
        try {
            companionDeviceManager.sendMessage(
                    CompanionDeviceManager.MESSAGE_ONEWAY_TASK_CONTINUITY,
                    serializedMessage,
                    associationIds);
            Slog.i(TAG, "Sending message to " + associationIds.length + " associations.");
            recordMessageSentEventForLogging(
                    associationIds.length,
                    message,
                    FrameworkStatsLog
                            .TASK_CONTINUITY_MESSAGE_SENT__RESULT__TASK_CONTINUITY_MESSAGE_SENT_RESULT_SUCCESS);
            return SendMessageResult.SUCCESS;
        } catch (Exception e) {
            Slog.e(TAG, "Failed to send message to associations", e);
            recordMessageSentEventForLogging(
                    associationIds.length,
                    message,
                    FrameworkStatsLog
                            .TASK_CONTINUITY_MESSAGE_SENT__RESULT__TASK_CONTINUITY_MESSAGE_SENT_RESULT_FAILURE_INTERNAL_ERROR);
            return SendMessageResult.FAILURE_INTERNAL_ERROR;
        }
    }

    public SendMessageResult sendMessage(@NonNull TaskContinuityMessage message) {
        int[] connectedAssociations;
        synchronized (this) {
            connectedAssociations = new int[mConnectedAssociations.size()];
            for (int i = 0; i < mConnectedAssociations.size(); i++) {
                connectedAssociations[i] = mConnectedAssociations.keyAt(i);
            }
        }

        return sendMessage(connectedAssociations, Objects.requireNonNull(message));
    }

    @VisibleForTesting
    void onTransportsChanged(List<AssociationInfo> associationInfos) {
        synchronized (this) {
            ArraySet<Integer> oldAssociationIds = new ArraySet<>(mConnectedAssociations.keySet());
            for (AssociationInfo associationInfo : associationInfos) {
                boolean isTaskContinuityEnabled =
                        (associationInfo.getSystemDataSyncFlags()
                                        & CompanionDeviceManager.FLAG_TASK_CONTINUITY)
                                != 0;

                boolean isAssociationAvailableToUser =
                        LocalServices.getService(PackageManagerInternal.class)
                                        .getPackageUid(associationInfo.getPackageName(), 0, mUserId)
                                >= 0;

                if (isTaskContinuityEnabled && isAssociationAvailableToUser) {
                    // Only notify listeners of a new association if it was not already connected.
                    if (!oldAssociationIds.remove(associationInfo.getId())) {
                        mConnectedAssociations.put(associationInfo.getId(), associationInfo);
                        Slog.i(
                                TAG,
                                "Transport connected for association: " + associationInfo.getId());
                        for (Listener listener : mListeners) {
                            listener.onAssociationConnected(associationInfo);
                        }
                    }
                }
            }

            // Any remaining associations in oldAssociationIds are no longer connected.
            for (Integer associationId : oldAssociationIds) {
                Slog.i(TAG, "Transport disconnected for association: " + associationId);

                mConnectedAssociations.remove(associationId);
                for (Listener listener : mListeners) {
                    listener.onAssociationDisconnected(associationId);
                }
            }
        }
    }

    private void onMessageReceived(int associationId, byte[] data) {
        Slog.v(TAG, "Received message from association id: " + associationId);
        try {
            TaskContinuityMessage taskContinuityMessage =
                    new TaskContinuityMessage.Builder().readFromBytes(data).build();
            FrameworkStatsLog.write(
                    FrameworkStatsLog.TASK_CONTINUITY_MESSAGE_RECEIVED,
                    taskContinuityMessage.getTypeForMetrics());
            synchronized (this) {
                for (Listener listener : mListeners) {
                    listener.onMessageReceived(associationId, taskContinuityMessage);
                }
            }
        } catch (IOException e) {
            Slog.e(TAG, "Failed to parse task continuity message", e);
        }
    }

    private void recordMessageSentEventForLogging(
            int targetAssociationCount, @NonNull TaskContinuityMessage message, int result) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.TASK_CONTINUITY_MESSAGE_SENT,
                Objects.requireNonNull(message).getTypeForMetrics(),
                targetAssociationCount,
                result);
    }
}
