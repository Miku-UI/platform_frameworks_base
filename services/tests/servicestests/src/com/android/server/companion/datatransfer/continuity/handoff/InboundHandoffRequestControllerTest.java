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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.AdditionalMatchers.aryEq;

import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class InboundHandoffRequestControllerTest {

    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;
    @Mock private ActivityTaskManagerInternal mMockActivityTaskManagerInternal;

    private InboundHandoffRequestController mInboundHandoffRequestController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        LocalServices.addService(
            ActivityTaskManagerInternal.class,
            mMockActivityTaskManagerInternal);

        mInboundHandoffRequestController = new InboundHandoffRequestController(
            mMockTaskContinuityMessenger);
    }

     @After
    public void unregisterLocalServices() throws Exception {
        LocalServices.removeServiceForTest(ActivityTaskManagerInternal.class);
    }

    @Test
    public void onHandoffTaskDataRequestSucceeded_sendsSuccessMessage() {
        int associationId = 1;
        int taskId = 2;

        // Setup a pending request
        mInboundHandoffRequestController.onHandoffRequestMessageReceived(
                associationId, new HandoffRequestMessage(taskId));
        verify(mMockActivityTaskManagerInternal, times(1))
                .requestHandoffTaskData(eq(taskId), eq(mInboundHandoffRequestController));

        HandoffActivityData handoffActivityData = new HandoffActivityData.Builder(
                new ComponentName("testPackage", "testActivity"))
            .build();
        List<HandoffActivityData> handoffData = List.of(handoffActivityData);
        mInboundHandoffRequestController.onHandoffTaskDataRequestSucceeded(taskId, handoffData);

        HandoffRequestResultMessage expectedMessage = new HandoffRequestResultMessage(
                taskId,
                HANDOFF_REQUEST_RESULT_SUCCESS,
                List.of(handoffActivityData));
        verify(mMockTaskContinuityMessenger).sendMessage(
                aryEq(new int[]{associationId}),
                eq(expectedMessage));
    }

    @Test
    public void onHandoffTaskDataRequestSucceeded_multipleAssociations_sendsToAll() {

        int firstAssociationId = 1;
        int secondAssociationId = 2;
        int taskId = 3;

        // Setup pending requests from two associations for the same task
        mInboundHandoffRequestController.onHandoffRequestMessageReceived(
                firstAssociationId, new HandoffRequestMessage(taskId));
        mInboundHandoffRequestController.onHandoffRequestMessageReceived(
                secondAssociationId, new HandoffRequestMessage(taskId));
        // requestHandoffTaskData should only be called once for the task
        verify(mMockActivityTaskManagerInternal, times(1))
                .requestHandoffTaskData(eq(taskId), any());

        HandoffActivityData handoffActivityData = new HandoffActivityData.Builder(
            new ComponentName("testPackage", "testActivity"))
        .build();

        List<HandoffActivityData> handoffData = List.of(handoffActivityData);
        mInboundHandoffRequestController.onHandoffTaskDataRequestSucceeded(taskId, handoffData);

        HandoffRequestResultMessage expectedMessage = new HandoffRequestResultMessage(
            taskId,
            HANDOFF_REQUEST_RESULT_SUCCESS,
            List.of(handoffActivityData));
        verify(mMockTaskContinuityMessenger).sendMessage(
                aryEq(new int[]{firstAssociationId, secondAssociationId}),
                eq(expectedMessage));
    }

    @Test
    public void onHandoffTaskDataRequestFailed_sendsFailureMessage_timeout() {
        testHandoffFailure(
                HANDOFF_FAILURE_TIMEOUT, HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT);
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
        mInboundHandoffRequestController.onHandoffRequestMessageReceived(
                associationId, new HandoffRequestMessage(taskId));
        verify(mMockActivityTaskManagerInternal, times(1))
                .requestHandoffTaskData(eq(taskId), any());

        mInboundHandoffRequestController.onHandoffTaskDataRequestFailed(taskId, receiverErrorCode);

        HandoffRequestResultMessage expectedMessage = new HandoffRequestResultMessage(
                taskId, expectedStatusCode, List.of());
        verify(mMockTaskContinuityMessenger)
            .sendMessage(aryEq(new int[]{associationId}),eq(expectedMessage));
    }
}