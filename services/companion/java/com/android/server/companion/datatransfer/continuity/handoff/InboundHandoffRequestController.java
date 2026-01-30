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

import static android.app.HandoffFailureCode.HANDOFF_FAILURE_TIMEOUT;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_UNSUPPORTED_DEVICE;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_UNSUPPORTED_TASK;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_UNKNOWN_TASK;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_INTERNAL_ERROR;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_TIMEOUT;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK;

import android.annotation.NonNull;
import android.app.HandoffActivityData;
import android.app.IHandoffTaskDataReceiver;
import android.content.Context;
import android.os.Binder;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageSerializer;
import com.android.server.wm.ActivityTaskManagerInternal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Responsible for receiving handoff requests from other devices and passing back data needed to
 * reinflate the tasks on the remote device.
 */
public class InboundHandoffRequestController extends IHandoffTaskDataReceiver.Stub {

    private static final String TAG = "InboundHandoffRequestController";

    // Map of task id to list of association ids that have a pending handoff request for that task.
    private final Map<Integer, List<Integer>> mPendingHandoffRequests = new HashMap<>();
    private final TaskContinuityMessenger mTaskContinuityMessenger;
    private final ActivityTaskManagerInternal mActivityTaskManagerInternal;

    public InboundHandoffRequestController(
        @NonNull TaskContinuityMessenger taskContinuityMessenger) {
        Objects.requireNonNull(taskContinuityMessenger);

        mActivityTaskManagerInternal = LocalServices.getService(ActivityTaskManagerInternal.class);
        mTaskContinuityMessenger = taskContinuityMessenger;
    }

    @Override
    public void onHandoffTaskDataRequestSucceeded(
        int taskId,
        List<HandoffActivityData> handoffActivityData) {
        final long ident = Binder.clearCallingIdentity();
        try {
            Slog.v(TAG, "onHandoffTaskDataRequestSucceeded for " + taskId);
            finishRequest(new HandoffRequestResultMessage(
                taskId,
                HANDOFF_REQUEST_RESULT_SUCCESS,
                handoffActivityData));
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void onHandoffTaskDataRequestFailed(int taskId, int errorCode) {
        final long ident = Binder.clearCallingIdentity();
        try {
            Slog.v(TAG, "onHandoffTaskDataRequestFailed for " + taskId);
            finishRequest(new HandoffRequestResultMessage(
                taskId,
                getStatusCodeFromHandoffTaskDataReceiverCode(errorCode),
                List.of()));
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public void onHandoffRequestMessageReceived(
        int associationId,
        HandoffRequestMessage handoffRequestMessage) {

        synchronized (mPendingHandoffRequests) {
            if (mPendingHandoffRequests.containsKey(handoffRequestMessage.taskId())) {
                // Add this request to the list of pending requests for this task.
                mPendingHandoffRequests
                    .get(handoffRequestMessage.taskId())
                    .add(associationId);
            } else {
                // Track this as a new request.
                List<Integer> associationIds = new ArrayList<>();
                associationIds.add(associationId);
                mPendingHandoffRequests.put(handoffRequestMessage.taskId(), associationIds);
                Slog.i(TAG, "Requesting handoff data for task " + handoffRequestMessage.taskId());
                mActivityTaskManagerInternal.requestHandoffTaskData(
                    handoffRequestMessage.taskId(),
                    this);
            }
        }
    }

    private void finishRequest(HandoffRequestResultMessage handoffRequestResultMessage) {
        synchronized (mPendingHandoffRequests) {
            if (!mPendingHandoffRequests.containsKey(handoffRequestResultMessage.taskId())) {
                Slog.w(TAG, "Received HandoffActivityData for task "
                    + handoffRequestResultMessage.taskId()
                    + ", but no pending request were found.");
                return;
            }

            List<Integer> associationIds = mPendingHandoffRequests
                .get(handoffRequestResultMessage.taskId());
            mPendingHandoffRequests.remove(handoffRequestResultMessage.taskId());

            int[] associationIdsArray = new int[associationIds.size()];
            for (int i = 0; i < associationIds.size(); i++) {
                associationIdsArray[i] = associationIds.get(i);
            }

            Slog.i(TAG, "Sending result message to " + associationIds.size() + " associations.");
            mTaskContinuityMessenger.sendMessage(associationIdsArray, handoffRequestResultMessage);
        }
    }

    private static int getStatusCodeFromHandoffTaskDataReceiverCode(
        int handoffTaskDataReceiverCode) {

        switch (handoffTaskDataReceiverCode) {
            case HANDOFF_FAILURE_TIMEOUT:
                return HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT;
            case HANDOFF_FAILURE_UNKNOWN_TASK:
                return HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND;
            default:
                return HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK;
        }
    }
}