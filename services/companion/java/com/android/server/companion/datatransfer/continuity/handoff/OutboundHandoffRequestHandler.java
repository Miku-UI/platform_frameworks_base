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

import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_HANDOFF_DISABLED;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS;

import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.companion.datatransfer.continuity.TaskContinuityManager.HandoffRequestResultCode;
import android.content.Context;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Slog;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.tasks.RemoteTaskListenerHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Controller for outbound handoff requests.
 *
 * <p>This class is responsible for sending handoff request messages to the remote device and
 * handling the results, either launching the task locally or falling back to a web URL if provided.
 */
public class OutboundHandoffRequestHandler {

    private static final String TAG = OutboundHandoffRequestHandler.class.getSimpleName();

    private final Context mContext;
    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final RemoteTaskListenerHolder mRemoteTaskListenerHolder;
    private final AtomicReference<RemoteCallbackList<IHandoffRequestCallback>> mCallbacksRef =
            new AtomicReference<>(null);

    private record Cookie(int associationId, int taskId) {}

    public OutboundHandoffRequestHandler(
            @NonNull Context context,
            @NonNull TaskContinuityMessenger taskContinuityMessenger,
            @NonNull RemoteTaskListenerHolder remoteTaskListenerHolder) {

        mContext = Objects.requireNonNull(context);
        mTaskContinuityMessenger = Objects.requireNonNull(taskContinuityMessenger);
        mRemoteTaskListenerHolder = Objects.requireNonNull(remoteTaskListenerHolder);
    }

    public void requestHandoff(
            int associationId, int taskId, @NonNull IHandoffRequestCallback callback) {
        Cookie cookie = new Cookie(associationId, taskId);
        FrameworkStatsLog.write(FrameworkStatsLog.HANDOFF_REQUESTED, cookie.hashCode());
        boolean didRegister =
                mCallbacksRef
                        .updateAndGet(
                                callbacks -> {
                                    if (callbacks == null) {
                                        return new RemoteCallbackList<>();
                                    }
                                    return callbacks;
                                })
                        .register(Objects.requireNonNull(callback), cookie);

        if (!didRegister) {
            Slog.e(TAG, "Failed to register callback for handoff request.");
            issueResultToCallback(
                    callback,
                    associationId,
                    taskId,
                    HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);
            return;
        }

        TaskContinuityMessenger.SendMessageResult result =
                mTaskContinuityMessenger.sendMessage(
                        associationId,
                        new TaskContinuityMessage.Builder()
                                .setHandoffRequestMessage(
                                        new HandoffRequestMessage.Builder()
                                                .setTaskId(taskId)
                                                .build())
                                .build());

        switch (result) {
            case TaskContinuityMessenger.SendMessageResult.SUCCESS:
                Slog.i(TAG, "Successfully sent handoff request message.");
                break;
            case TaskContinuityMessenger.SendMessageResult.FAILURE_MESSAGE_SERIALIZATION_FAILED:
                Slog.e(TAG, "Failed to serialize handoff request message.");
                finishHandoffRequest(
                        Predicate.isEqual(cookie),
                        HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);
                break;
            case TaskContinuityMessenger.SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND:
                Slog.w(TAG, "Association " + associationId + " is not connected.");
                finishHandoffRequest(
                        Predicate.isEqual(cookie), HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND);
                break;
            case TaskContinuityMessenger.SendMessageResult.FAILURE_INTERNAL_ERROR:
                Slog.e(TAG, "Failed to send handoff request message - internal error.");
                finishHandoffRequest(
                        Predicate.isEqual(cookie),
                        HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);
                break;
        }
    }

    public void cancelAllOutboundRequests() {
        finishHandoffRequest((cookie) -> true, HANDOFF_REQUEST_RESULT_FAILURE_HANDOFF_DISABLED);
    }

    public void onHandoffRequestResultMessageReceived(
            int associationId, HandoffRequestResultMessage handoffRequestResultMessage) {

        Cookie cookie = new Cookie(associationId, handoffRequestResultMessage.taskId());
        if (!isCallbackRegisteredForCookie(cookie)) {
            return;
        }

        int statusCode = handoffRequestResultMessage.statusCode();

        if (statusCode == HANDOFF_REQUEST_RESULT_SUCCESS
                && !HandoffActivityStarter.start(
                        mContext, handoffRequestResultMessage.activities())) {
            statusCode = HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR;
        }

        finishHandoffRequest(Predicate.isEqual(cookie), statusCode);
        if (statusCode == HANDOFF_REQUEST_RESULT_SUCCESS) {
            mRemoteTaskListenerHolder.notifyTaskHandedOff(
                    associationId, handoffRequestResultMessage.taskId());
        }
    }

    private boolean isCallbackRegisteredForCookie(@NonNull Cookie cookie) {
        RemoteCallbackList<IHandoffRequestCallback> callbacks = mCallbacksRef.get();
        if (callbacks == null) {
            return false;
        }

        for (int i = 0; i < callbacks.getRegisteredCallbackCount(); i++) {
            if (Objects.requireNonNull(cookie).equals(callbacks.getRegisteredCallbackCookie(i))) {
                return true;
            }
        }
        return false;
    }

    private void finishHandoffRequest(
            @NonNull Predicate<Cookie> predicate, @HandoffRequestResultCode int statusCode) {
        Objects.requireNonNull(predicate);

        RemoteCallbackList<IHandoffRequestCallback> callbacks = mCallbacksRef.get();
        if (callbacks == null) {
            return;
        }

        List<IHandoffRequestCallback> callbacksToRemove = new ArrayList<>();
        callbacks.broadcast(
                (callback, cookie) -> {
                    Cookie requestCookie = (Cookie) cookie;
                    if (predicate.test(requestCookie)) {
                        issueResultToCallback(
                                callback,
                                requestCookie.associationId(),
                                requestCookie.taskId(),
                                statusCode);
                        callbacksToRemove.add(callback);
                    }
                });

        Slog.i(TAG, "Clearing " + callbacksToRemove.size() + " callbacks.");
        for (IHandoffRequestCallback callback : callbacksToRemove) {
            if (!callbacks.unregister(callback)) {
                Slog.w(TAG, "Attempted to unregister callback that was not registered.");
            }
        }
    }

    private void issueResultToCallback(
            @NonNull IHandoffRequestCallback callback,
            int associationId,
            int taskId,
            @HandoffRequestResultCode int statusCode) {
        try {
            Objects.requireNonNull(callback)
                    .onHandoffRequestFinished(associationId, taskId, statusCode);

            int statusCodeForMetrics =
                    switch (statusCode) {
                        case HANDOFF_REQUEST_RESULT_SUCCESS ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_SUCCESS;
                        case HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_FAILURE_NO_DATA_PROVIDED_BY_TASK;
                        case HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_FAILURE_TASK_NOT_FOUND;
                        case HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_FAILURE_TIMEOUT;
                        default ->
                                FrameworkStatsLog
                                        .HANDOFF_REQUEST_FINISHED__STATUS_CODE__STATUS_CODE_UNKNOWN;
                    };

            FrameworkStatsLog.write(
                    FrameworkStatsLog.HANDOFF_REQUEST_FINISHED,
                    new Cookie(associationId, taskId).hashCode(),
                    statusCodeForMetrics);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to notify callback of handoff request cancellation", e);
        }
    }
}
