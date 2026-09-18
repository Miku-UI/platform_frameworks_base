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

import static android.app.HandoffFailureCode.HANDOFF_FAILURE_EMPTY_TASK;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_INTERNAL_ERROR;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_TIMEOUT;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_UNKNOWN_TASK;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_UNSUPPORTED_DEVICE;
import static android.app.HandoffFailureCode.HANDOFF_FAILURE_UNSUPPORTED_TASK;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import com.android.server.companion.datatransfer.continuity.TaskContinuityTest;
import com.android.server.companion.datatransfer.continuity.messages.HandoffActivityDataMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

@Presubmit
public class InboundHandoffRequestHandlerTest extends TaskContinuityTest {

    private InboundHandoffRequestHandler mInboundHandoffRequestHandler;

    @Before
    public void setUp() {
        mInboundHandoffRequestHandler =
                new InboundHandoffRequestHandler(mMockTaskContinuityMessenger);
    }

    @Test
    public void onHandoffTaskDataRequestSucceeded_sendsSuccessMessage() {
        int associationId = 1;
        int taskId = 2;

        // Setup a pending request
        mInboundHandoffRequestHandler.onHandoffRequestMessageReceived(
                associationId, new HandoffRequestMessage(taskId));
        verify(mMockActivityTaskManagerInternal, times(1))
                .requestHandoffTaskData(eq(taskId), eq(mInboundHandoffRequestHandler));

        HandoffActivityData handoffActivityData =
                new HandoffActivityData.Builder(new ComponentName("testPackage", "testActivity"))
                        .build();
        List<HandoffActivityData> handoffData = List.of(handoffActivityData);
        mInboundHandoffRequestHandler.onHandoffTaskDataRequestSucceeded(taskId, handoffData);

        TaskContinuityMessage expectedMessage =
                new TaskContinuityMessage.Builder()
                        .setHandoffRequestResultMessage(
                                new HandoffRequestResultMessage(
                                        taskId,
                                        HANDOFF_REQUEST_RESULT_SUCCESS,
                                        List.of(
                                                new HandoffActivityDataMessage(
                                                        handoffActivityData, List.of()))))
                        .build();
        verify(mMockTaskContinuityMessenger)
                .sendMessage(aryEq(new int[] {associationId}), eq(expectedMessage));
    }

    @Test
    public void onHandoffTaskDataRequestSucceeded_multipleAssociations_sendsToAll() {

        int firstAssociationId = 1;
        int secondAssociationId = 2;
        int taskId = 3;

        // Setup pending requests from two associations for the same task
        mInboundHandoffRequestHandler.onHandoffRequestMessageReceived(
                firstAssociationId, new HandoffRequestMessage(taskId));
        mInboundHandoffRequestHandler.onHandoffRequestMessageReceived(
                secondAssociationId, new HandoffRequestMessage(taskId));
        // requestHandoffTaskData should be called each time a request is received.
        verify(mMockActivityTaskManagerInternal, times(2))
                .requestHandoffTaskData(eq(taskId), any());

        HandoffActivityData handoffActivityData =
                new HandoffActivityData.Builder(new ComponentName("testPackage", "testActivity"))
                        .build();

        List<HandoffActivityData> handoffData = List.of(handoffActivityData);
        mInboundHandoffRequestHandler.onHandoffTaskDataRequestSucceeded(taskId, handoffData);

        TaskContinuityMessage expectedMessage =
                new TaskContinuityMessage.Builder()
                        .setHandoffRequestResultMessage(
                                new HandoffRequestResultMessage(
                                        taskId,
                                        HANDOFF_REQUEST_RESULT_SUCCESS,
                                        List.of(
                                                new HandoffActivityDataMessage(
                                                        handoffActivityData, List.of()))))
                        .build();
        verify(mMockTaskContinuityMessenger)
                .sendMessage(
                        aryEq(new int[] {firstAssociationId, secondAssociationId}),
                        eq(expectedMessage));
    }

    @Test
    public void onHandoffTaskDataRequestFailed_sendsFailureMessage_timeout() {
        testHandoffFailure(HANDOFF_FAILURE_TIMEOUT, HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT);
    }

    @Test
    public void onHandoffTaskDataRequestFailed_sendsFailureMessage_unknownTask() {
        testHandoffFailure(
                HANDOFF_FAILURE_UNKNOWN_TASK, HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND);
    }

    @Test
    public void onHandoffTaskDataRequestFailed_sendsFailureMessage_unsupportedTask() {
        testHandoffFailure(
                HANDOFF_FAILURE_UNSUPPORTED_TASK,
                HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK);
    }

    @Test
    public void onHandoffTaskDataRequestFailed_sendsFailureMessage_emptyTask() {
        testHandoffFailure(
                HANDOFF_FAILURE_EMPTY_TASK,
                HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK);
    }

    @Test
    public void onHandoffTaskDataRequestFailed_sendsFailureMessage_unsupportedDevice() {
        testHandoffFailure(
                HANDOFF_FAILURE_UNSUPPORTED_DEVICE,
                HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK);
    }

    @Test
    public void onHandoffTaskDataRequestFailed_sendsFailureMessage_internalError() {
        testHandoffFailure(
                HANDOFF_FAILURE_INTERNAL_ERROR,
                HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK);
    }

    private void testHandoffFailure(int receiverErrorCode, int expectedStatusCode) {
        int associationId = 1;
        int taskId = 1;

        // Setup a pending request
        mInboundHandoffRequestHandler.onHandoffRequestMessageReceived(
                associationId, new HandoffRequestMessage(taskId));
        verify(mMockActivityTaskManagerInternal, times(1))
                .requestHandoffTaskData(eq(taskId), any());

        mInboundHandoffRequestHandler.onHandoffTaskDataRequestFailed(taskId, receiverErrorCode);

        TaskContinuityMessage expectedMessage =
                new TaskContinuityMessage.Builder()
                        .setHandoffRequestResultMessage(
                                new HandoffRequestResultMessage(
                                        taskId, expectedStatusCode, List.of()))
                        .build();
        verify(mMockTaskContinuityMessenger)
                .sendMessage(aryEq(new int[] {associationId}), eq(expectedMessage));
    }
}
