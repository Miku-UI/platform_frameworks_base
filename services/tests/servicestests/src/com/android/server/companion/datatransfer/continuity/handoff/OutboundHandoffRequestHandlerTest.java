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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.HandoffActivityData;
import android.companion.datatransfer.continuity.TaskContinuityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.PersistableBundle;
import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.TaskContinuityTest;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffActivityDataMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.tasks.RemoteTaskListenerHolder;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class OutboundHandoffRequestHandlerTest extends TaskContinuityTest {

    @Mock private RemoteTaskListenerHolder mMockRemoteTaskListenerHolder;

    private OutboundHandoffRequestHandler mOutboundHandoffRequestHandler;

    @Before
    public void setUp() {
        mOutboundHandoffRequestHandler =
                new OutboundHandoffRequestHandler(
                        mMockContext, mMockTaskContinuityMessenger, mMockRemoteTaskListenerHolder);
    }

    @Test
    public void testRequestHandoff_success() throws Exception {
        int associationId = 1;
        int taskId = 1;
        FakeHandoffRequestCallback callbackHolder = new FakeHandoffRequestCallback();
        doReturn(TaskContinuityMessenger.SendMessageResult.SUCCESS)
                .when(mMockTaskContinuityMessenger)
                .sendMessage(eq(associationId), any());

        // Request a handoff to a device.
        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, callbackHolder);

        // Verify HandoffRequestMessage was sent.
        verify(mMockTaskContinuityMessenger)
                .sendMessage(
                        eq(associationId),
                        eq(
                                new TaskContinuityMessage.Builder()
                                        .setHandoffRequestMessage(new HandoffRequestMessage(taskId))
                                        .build()));

        // Simulate a response message.
        ComponentName expectedComponentName =
                new ComponentName("com.example.app", "com.example.app.Activity");
        PersistableBundle expectedExtras = new PersistableBundle();
        expectedExtras.putString("key", "value");
        HandoffActivityData handoffActivityData =
                new HandoffActivityData.Builder(expectedComponentName)
                        .setExtras(expectedExtras)
                        .build();
        when(mMockPackageManager.getActivityInfo(
                        eq(expectedComponentName), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(new ActivityInfo());
        doReturn(ActivityManager.START_SUCCESS)
                .when(mMockActivityTaskManagerInternal)
                .startActivityWithConfig(any(), any(), any(), any(), anyInt());

        HandoffRequestResultMessage handoffRequestResultMessage =
                new HandoffRequestResultMessage(
                        taskId,
                        TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS,
                        List.of(new HandoffActivityDataMessage(handoffActivityData, List.of())));
        mOutboundHandoffRequestHandler.onHandoffRequestResultMessageReceived(
                associationId, handoffRequestResultMessage);

        // Verify the intent was launched.
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockActivityTaskManagerInternal, times(1))
                .startActivityWithConfig(any(), any(), intentCaptor.capture(), any(), anyInt());
        Intent actualIntent = intentCaptor.getValue();
        assertThat(actualIntent.getComponent()).isEqualTo(expectedComponentName);
        assertThat(actualIntent.getExtras().size()).isEqualTo(1);
        for (String key : actualIntent.getExtras().keySet()) {
            assertThat(actualIntent.getExtras().getString(key))
                    .isEqualTo(expectedExtras.getString(key));
        }

        // Verify the callback was invoked.
        callbackHolder.verifyInvoked(
                associationId, taskId, TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS);

        // Verify the task was removed from the store.
        verify(mMockRemoteTaskListenerHolder).notifyTaskHandedOff(associationId, taskId);
    }

    @Test
    public void testRequestHandoff_associationNotConnected_returnsFailure() {
        int associationId = 1;
        int taskId = 1;
        FakeHandoffRequestCallback callbackHolder = new FakeHandoffRequestCallback();
        doReturn(TaskContinuityMessenger.SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND)
                .when(mMockTaskContinuityMessenger)
                .sendMessage(eq(associationId), any());

        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, callbackHolder);

        // Verify the callback was invoked.
        callbackHolder.verifyInvoked(
                associationId,
                taskId,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND);
    }

    @Test
    public void testRequestHandoff_multipleTimes_sendsMultipleMessages() throws Exception {
        int associationId = 1;
        int taskId = 1;
        doReturn(TaskContinuityMessenger.SendMessageResult.SUCCESS)
                .when(mMockTaskContinuityMessenger)
                .sendMessage(eq(associationId), any());

        // Request handoff multiple times.
        FakeHandoffRequestCallback firstCallback = new FakeHandoffRequestCallback();
        FakeHandoffRequestCallback secondCallback = new FakeHandoffRequestCallback();
        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, firstCallback);
        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, secondCallback);

        verify(mMockTaskContinuityMessenger, times(2))
                .sendMessage(
                        eq(associationId),
                        eq(
                                new TaskContinuityMessage.Builder()
                                        .setHandoffRequestMessage(new HandoffRequestMessage(taskId))
                                        .build()));
    }

    @Test
    public void testRequestHandoff_failureStatusCode_returnsFailure() {
        // Request a handoff
        int associationId = 1;
        int taskId = 1;
        doReturn(TaskContinuityMessenger.SendMessageResult.SUCCESS)
                .when(mMockTaskContinuityMessenger)
                .sendMessage(eq(associationId), any());
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, callback);

        // Simulate a message failure
        int failureStatusCode = TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT;
        mOutboundHandoffRequestHandler.onHandoffRequestResultMessageReceived(
                associationId,
                new HandoffRequestResultMessage(taskId, failureStatusCode, List.of()));

        // Verify the callback was invoked.
        callback.verifyInvoked(associationId, taskId, failureStatusCode);

        // Verify no intent was launched.
        verify(mMockActivityTaskManagerInternal, never())
                .startActivityWithConfig(any(), any(), any(), any(), anyInt());
    }

    @Test
    public void testRequestHandoff_noActivities_returnsFailure() {
        // Request a handoff
        int associationId = 1;
        int taskId = 1;
        doReturn(TaskContinuityMessenger.SendMessageResult.SUCCESS)
                .when(mMockTaskContinuityMessenger)
                .sendMessage(eq(associationId), any());
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, callback);

        // Return no data for this request.
        mOutboundHandoffRequestHandler.onHandoffRequestResultMessageReceived(
                associationId,
                new HandoffRequestResultMessage(
                        taskId, TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS, List.of()));

        // Verify the callback was invoked.
        callback.verifyInvoked(
                associationId,
                taskId,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);

        // Verify no intent was launched.
        verify(mMockActivityTaskManagerInternal, never())
                .startActivityWithConfig(any(), any(), any(), any(), anyInt());
    }

    @Test
    public void testRequestHandoff_messageWithNullActivity_returnsFailure() {
        // Request a handoff
        int associationId = 1;
        int taskId = 1;
        doReturn(TaskContinuityMessenger.SendMessageResult.SUCCESS)
                .when(mMockTaskContinuityMessenger)
                .sendMessage(eq(associationId), any());
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, callback);

        // Return no data for this request.
        List<HandoffActivityDataMessage> activities = new ArrayList<>();
        activities.add(null);

        mOutboundHandoffRequestHandler.onHandoffRequestResultMessageReceived(
                associationId,
                new HandoffRequestResultMessage(
                        taskId, TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS, activities));

        // Verify the callback was invoked.
        callback.verifyInvoked(
                associationId,
                taskId,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);

        // Verify no intent was launched.
        verify(mMockActivityTaskManagerInternal, never())
                .startActivityWithConfig(any(), any(), any(), any(), anyInt());
    }

    @Test
    public void testRequestHandoff_serializationFailure_returnsFailure() {
        verifyTaskContinuityMessengerFailureCausesFailure(
                TaskContinuityMessenger.SendMessageResult.FAILURE_MESSAGE_SERIALIZATION_FAILED,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);
    }

    @Test
    public void testRequestHandoff_internalError_returnsFailure() {
        verifyTaskContinuityMessengerFailureCausesFailure(
                TaskContinuityMessenger.SendMessageResult.FAILURE_INTERNAL_ERROR,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR);
    }

    @Test
    public void testRequestHandoff_associationNotFound_returnsFailure() {
        verifyTaskContinuityMessengerFailureCausesFailure(
                TaskContinuityMessenger.SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND);
    }

    @Test
    public void testCancelAllOutboundRequests_notifiesAllCallbacks() {
        int associationId = 1;
        int taskId = 1;
        FakeHandoffRequestCallback callbackHolder = new FakeHandoffRequestCallback();
        doReturn(TaskContinuityMessenger.SendMessageResult.SUCCESS)
                .when(mMockTaskContinuityMessenger)
                .sendMessage(eq(associationId), any());
        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, callbackHolder);
        mOutboundHandoffRequestHandler.cancelAllOutboundRequests();
        callbackHolder.verifyInvoked(
                associationId,
                taskId,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_HANDOFF_DISABLED);
    }

    private void verifyTaskContinuityMessengerFailureCausesFailure(
            TaskContinuityMessenger.SendMessageResult sendMessageResult, int expectedStatusCode) {

        int associationId = 1;
        int taskId = 1;
        doReturn(sendMessageResult)
                .when(mMockTaskContinuityMessenger)
                .sendMessage(eq(associationId), any());
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mOutboundHandoffRequestHandler.requestHandoff(associationId, taskId, callback);
        callback.verifyInvoked(associationId, taskId, expectedStatusCode);
        verify(mMockActivityTaskManagerInternal, never())
                .startActivityWithConfig(any(), any(), any(), any(), anyInt());
    }
}
