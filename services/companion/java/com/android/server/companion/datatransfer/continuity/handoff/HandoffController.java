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

package com.android.server.companion.datatransfer.continuity.handoff;

import android.annotation.NonNull;
import android.app.ActivityTaskManager;
import android.companion.AssociationInfo;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;
import android.companion.datatransfer.continuity.TaskContinuityManager;
import android.content.Context;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Slog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.FeatureController;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import com.android.server.companion.datatransfer.continuity.tasks.RemoteTaskFactory;
import com.android.server.companion.datatransfer.continuity.tasks.RemoteTaskListenerHolder;
import com.android.server.companion.datatransfer.continuity.tasks.TaskBroadcaster;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class HandoffController extends FeatureController {

    @GuardedBy("this")
    private boolean mIsRegistered = false;

    private final RemoteTaskListenerHolder mRemoteTaskListenerHolder =
            new RemoteTaskListenerHolder();

    private final Context mContext;

    @VisibleForTesting
    final AtomicReference<InboundHandoffRequestHandler> mInboundHandoffRequestHandler;

    @VisibleForTesting
    final AtomicReference<OutboundHandoffRequestHandler> mOutboundHandoffRequestHandler;

    @VisibleForTesting final AtomicReference<TaskBroadcaster> mTaskBroadcaster;

    public HandoffController(
            int userId,
            @NonNull Context context,
            @NonNull TaskContinuityMessenger taskContinuityMessenger) {
        super(userId, Objects.requireNonNull(taskContinuityMessenger));
        mContext = Objects.requireNonNull(context);
        mInboundHandoffRequestHandler = new AtomicReference<>(null);
        mOutboundHandoffRequestHandler = new AtomicReference<>(null);
        mTaskBroadcaster = new AtomicReference<>(null);
    }

    public void registerTaskListener(@NonNull IRemoteTaskListener listener) {
        Slog.v(getTag(), "Registering task listener");
        mRemoteTaskListenerHolder.addListener(Objects.requireNonNull(listener));
    }

    public void unregisterTaskListener(@NonNull IRemoteTaskListener listener) {
        Slog.v(getTag(), "Unregistering task listener");
        mRemoteTaskListenerHolder.removeListener(Objects.requireNonNull(listener));
    }

    public void requestHandoff(
            int associationId, int remoteTaskId, @NonNull IHandoffRequestCallback callback) {
        if (!isEnabled()) {
            Slog.w(getTag(), "Requested Handoff when controller is disabled. Returning failure.");
            try {
                callback.onHandoffRequestFinished(
                        associationId,
                        remoteTaskId,
                        TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_HANDOFF_DISABLED);
            } catch (RemoteException e) {
                Slog.e(getTag(), "Failed to notify callback of handoff request cancellation", e);
            }

            return;
        }

        Slog.v(getTag(), "Requesting handoff from association " + associationId);
        getOutboundHandoffRequestHandler()
                .requestHandoff(associationId, remoteTaskId, Objects.requireNonNull(callback));
    }

    @Override
    public void onEnabled() {
        Slog.v(getTag(), "Registering listeners from ActivityTaskManager");
        maybeListenToActivityTaskManager();
    }

    @Override
    public void onDisabled() {
        Slog.v(getTag(), "Cancelling all outbound handoff requests.");
        OutboundHandoffRequestHandler outboundHandoffRequestHandler =
                mOutboundHandoffRequestHandler.getAndSet(null);
        if (outboundHandoffRequestHandler != null) {
            outboundHandoffRequestHandler.cancelAllOutboundRequests();
        }

        Slog.v(getTag(), "Unregistering listeners from ActivityTaskManager");
        unlistenFromActivityTaskManager();
        Slog.v(getTag(), "Clearing all tasks from RemoteTaskStore");
        mRemoteTaskListenerHolder.notifyAllRemoteTasksChanged(null);
    }

    @Override
    public String getTag() {
        return HandoffController.class.getSimpleName();
    }

    @Override
    protected void onHandoffRequestMessageReceived(
            int associationId, @NonNull HandoffRequestMessage handoffRequestMessage) {
        Slog.v(getTag(), "Handoff request message received from association " + associationId);
        getInboundHandoffRequestHandler()
                .onHandoffRequestMessageReceived(
                        associationId, Objects.requireNonNull(handoffRequestMessage));
    }

    @Override
    public void onAssociationConnected(@NonNull AssociationInfo associationInfo) {
        maybeListenToActivityTaskManager();
        Slog.v(getTag(), "Association connected: " + associationInfo.getId());
        getTaskBroadcaster().onDeviceConnected();
    }

    @Override
    public void onAssociationDisconnected(int associationId) {
        Slog.v(getTag(), "Association disconnected: " + associationId);
        synchronized (this) {
            if (mTaskContinuityMessenger.getConnectedAssociations().isEmpty()) {
                unlistenFromActivityTaskManager();
            }
        }

        mRemoteTaskListenerHolder.notifyRemoteTasksChangedForAssociation(associationId, null);
    }

    @Override
    protected void onTaskStackBroadcastMessageReceived(
            int associationId, @NonNull TaskStackBroadcastMessage taskStackBroadcastMessage) {
        Trace.traceBegin(Trace.TRACE_TAG_SYSTEM_SERVER, "onTaskStackBroadcastMessageReceived");
        Slog.v(getTag(), "Received task stack broadcast message from association " + associationId);
        AssociationInfo associationInfo =
                mTaskContinuityMessenger.getAssociationInfo(associationId);
        if (associationInfo != null) {
            List<RemoteTask> remoteTasks = new ArrayList<>();
            for (RemoteTaskInfo taskInfo :
                    Objects.requireNonNull(taskStackBroadcastMessage).remoteTasks()) {
                RemoteTask remoteTask =
                        RemoteTaskFactory.create(
                                mContext.getPackageManager(),
                                mUserId,
                                associationInfo.getId(),
                                associationInfo.getDisplayName().toString(),
                                taskInfo);
                if (remoteTask != null) {
                    remoteTasks.add(remoteTask);
                }
            }

            mRemoteTaskListenerHolder.notifyRemoteTasksChangedForAssociation(
                    associationId, remoteTasks);
        }
        Trace.traceEnd(Trace.TRACE_TAG_SYSTEM_SERVER);
    }

    @Override
    protected void onHandoffRequestResultMessageReceived(
            int associationId, @NonNull HandoffRequestResultMessage handoffRequestResultMessage) {
        Slog.v(
                getTag(),
                "Handoff request result message received from association " + associationId);
        getOutboundHandoffRequestHandler()
                .onHandoffRequestResultMessageReceived(
                        associationId, Objects.requireNonNull(handoffRequestResultMessage));
    }

    private void maybeListenToActivityTaskManager() {
        synchronized (this) {
            if (!mIsRegistered && !mTaskContinuityMessenger.getConnectedAssociations().isEmpty()) {
                TaskBroadcaster taskBroadcaster = getTaskBroadcaster();
                ((ActivityTaskManager) mContext.getSystemService(Context.ACTIVITY_TASK_SERVICE))
                        .registerTaskStackListener(taskBroadcaster);
                LocalServices.getService(ActivityTaskManagerInternal.class)
                        .registerHandoffEnablementListener(taskBroadcaster);
                mIsRegistered = true;
            }
        }
    }

    private void unlistenFromActivityTaskManager() {
        synchronized (this) {
            if (mIsRegistered) {
                ((ActivityTaskManager) mContext.getSystemService(Context.ACTIVITY_TASK_SERVICE))
                        .unregisterTaskStackListener(getTaskBroadcaster());
                LocalServices.getService(ActivityTaskManagerInternal.class)
                        .unregisterHandoffEnablementListener(getTaskBroadcaster());
                mIsRegistered = false;
            }
        }
    }

    private InboundHandoffRequestHandler getInboundHandoffRequestHandler() {
        return mInboundHandoffRequestHandler.updateAndGet(
                (handler) -> {
                    if (handler == null) {
                        return new InboundHandoffRequestHandler(mTaskContinuityMessenger);
                    }
                    return handler;
                });
    }

    private OutboundHandoffRequestHandler getOutboundHandoffRequestHandler() {
        return mOutboundHandoffRequestHandler.updateAndGet(
                (handler) -> {
                    if (handler == null) {
                        return new OutboundHandoffRequestHandler(
                                mContext, mTaskContinuityMessenger, mRemoteTaskListenerHolder);
                    }
                    return handler;
                });
    }

    private TaskBroadcaster getTaskBroadcaster() {
        return mTaskBroadcaster.updateAndGet(
                (broadcaster) -> {
                    if (broadcaster == null) {
                        return new TaskBroadcaster(mUserId, mContext, mTaskContinuityMessenger);
                    }
                    return broadcaster;
                });
    }
}
