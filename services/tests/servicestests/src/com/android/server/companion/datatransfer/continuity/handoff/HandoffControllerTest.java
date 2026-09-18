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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.AssociationInfo;
import android.companion.datatransfer.continuity.TaskContinuityManager;
import android.platform.test.annotations.Presubmit;
import com.android.server.companion.datatransfer.continuity.TaskContinuityTest;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import com.android.server.companion.datatransfer.continuity.tasks.FakeRemoteTaskListener;
import com.android.server.companion.datatransfer.continuity.tasks.RemoteTaskFactory;
import com.android.server.companion.datatransfer.continuity.tasks.TaskBroadcaster;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.verification.VerificationMode;

@Presubmit
public class HandoffControllerTest extends TaskContinuityTest {

    @Mock private TaskBroadcaster mMockTaskBroadcaster;
    @Mock private InboundHandoffRequestHandler mMockInboundHandoffRequestHandler;
    @Mock private OutboundHandoffRequestHandler mMockOutboundHandoffRequestHandler;

    private HandoffController mHandoffController;

    @Before
    public void setUp() {
        mHandoffController =
                new HandoffController(USER_ID, mMockContext, mMockTaskContinuityMessenger);
        mHandoffController.mInboundHandoffRequestHandler.set(mMockInboundHandoffRequestHandler);
        mHandoffController.mOutboundHandoffRequestHandler.set(mMockOutboundHandoffRequestHandler);
        mHandoffController.mTaskBroadcaster.set(mMockTaskBroadcaster);
    }

    @Test
    public void testRequestHandoff_callsOutboundHandoffRequestHandler() {
        int associationId = 1;
        int remoteTaskId = 2;
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mHandoffController.enable();
        mHandoffController.requestHandoff(associationId, remoteTaskId, callback);
        verify(mMockOutboundHandoffRequestHandler)
                .requestHandoff(associationId, remoteTaskId, callback);
    }

    @Test
    public void testRequestHandoff_handoffDisabled_returnsFailure() {
        int associationId = 1;
        int remoteTaskId = 2;
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mHandoffController.requestHandoff(associationId, remoteTaskId, callback);
        callback.verifyInvoked(
                associationId,
                remoteTaskId,
                TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_HANDOFF_DISABLED);
    }

    @Test
    public void testOnHandoffRequestMessageReceived_callsInboundHandoffRequestHandler() {
        int associationId = 1;
        HandoffRequestMessage handoffRequestMessage = new HandoffRequestMessage(1);
        mHandoffController.onMessageReceived(
                associationId,
                new TaskContinuityMessage.Builder()
                        .setHandoffRequestMessage(handoffRequestMessage)
                        .build());
        verify(mMockInboundHandoffRequestHandler)
                .onHandoffRequestMessageReceived(associationId, handoffRequestMessage);
    }

    @Test
    public void testOnHandoffRequestResultMessageReceived_callsOutboundHandoffRequestHandler() {
        int associationId = 1;
        HandoffRequestResultMessage handoffRequestResultMessage =
                new HandoffRequestResultMessage(1, 1, List.of());
        mHandoffController.onMessageReceived(
                associationId,
                new TaskContinuityMessage.Builder()
                        .setHandoffRequestResultMessage(handoffRequestResultMessage)
                        .build());
        verify(mMockOutboundHandoffRequestHandler)
                .onHandoffRequestResultMessageReceived(associationId, handoffRequestResultMessage);
    }

    @Test
    public void testOnDisabled_cancelsAllOutboundRequests() {
        mHandoffController.enable();
        mHandoffController.disable();
        verify(mMockOutboundHandoffRequestHandler).cancelAllOutboundRequests();
    }

    @Test
    public void onEnableAndDisable_associationConnected_registersAndUnregistersListeners() {
        when(mMockTaskContinuityMessenger.getConnectedAssociations())
                .thenReturn(List.of(createAssociationInfo(USER_ID, 1)));
        mHandoffController.enable();
        verifyTaskStackBroadcasterRegistered(times(1));

        mHandoffController.disable();
        verifyTaskStackBroadcasterUnregistered(times(1));
    }

    @Test
    public void onEnableAndDisable_associationNotConnected_doesNotListenToActivityTaskManager() {
        when(mMockTaskContinuityMessenger.getConnectedAssociations()).thenReturn(List.of());
        mHandoffController.enable();
        verifyTaskStackBroadcasterRegistered(never());

        mHandoffController.disable();
        verifyTaskStackBroadcasterUnregistered(never());
    }

    @Test
    public void onDisable_notifiesOfTaskListChanged() {
        mHandoffController.enable();

        // Register a listener, verify it is notified.
        FakeRemoteTaskListener fakeRemoteTaskListener = new FakeRemoteTaskListener();
        mHandoffController.registerTaskListener(fakeRemoteTaskListener);
        fakeRemoteTaskListener.verifyListenerEvents(List.of());

        // Disable the controller, verify the listener was notified again.
        mHandoffController.disable();
        fakeRemoteTaskListener.verifyListenerEvents(List.of(), List.of());
    }

    @Test
    public void registerRemoteTaskListener_registersListener() {
        FakeRemoteTaskListener fakeRemoteTaskListener = new FakeRemoteTaskListener();
        mHandoffController.registerTaskListener(fakeRemoteTaskListener);

        // Verify the listener was notified.
        fakeRemoteTaskListener.verifyListenerEvents(List.of());
    }

    @Test
    public void unregisterRemoteTaskListener_unregistersListener() {
        // Register a listener, verify it is notified.
        FakeRemoteTaskListener fakeRemoteTaskListener = new FakeRemoteTaskListener();
        mHandoffController.registerTaskListener(fakeRemoteTaskListener);
        fakeRemoteTaskListener.verifyListenerEvents(List.of());

        // Unregister the listener
        mHandoffController.unregisterTaskListener(fakeRemoteTaskListener);

        // Send a new task stack broadcast message, verify the listener was not notified.
        TaskStackBroadcastMessage taskStackBroadcastMessage =
                new TaskStackBroadcastMessage(List.of());
        mHandoffController.onTaskStackBroadcastMessageReceived(1, taskStackBroadcastMessage);
        fakeRemoteTaskListener.verifyListenerEvents(List.of());
    }

    @Test
    public void onAssociationConnected_notifiesStoreAndBroadcaster() {
        AssociationInfo associationInfo = createAssociationInfo(USER_ID, 1);
        notifyAssociationConnected(associationInfo);
        verify(mMockTaskBroadcaster).onDeviceConnected();
        verifyTaskStackBroadcasterRegistered(times(1));
    }

    @Test
    public void onSecondAssociationConnected_onlyRegistersListenersOnce() {
        AssociationInfo associationInfo = createAssociationInfo(USER_ID, 1);
        notifyAssociationConnected(associationInfo);
        notifyAssociationConnected(associationInfo);
        verifyTaskStackBroadcasterRegistered(times(1));
    }

    @Test
    public void onAssociationDisconnected_notifiesStoreAndBroadcaster() {
        AssociationInfo associationInfo = createAssociationInfo(USER_ID, 1);
        FakeRemoteTaskListener fakeRemoteTaskListener = new FakeRemoteTaskListener();
        mHandoffController.registerTaskListener(fakeRemoteTaskListener);
        notifyAssociationConnected(associationInfo);
        when(mMockTaskContinuityMessenger.getConnectedAssociations()).thenReturn(List.of());
        mHandoffController.onAssociationDisconnected(associationInfo.getId());
        fakeRemoteTaskListener.verifyListenerEvents(List.of(), List.of());
        verifyTaskStackBroadcasterUnregistered(times(1));
    }

    @Test
    public void onAssociationDisconnected_notListening_doesNotUnregisterListeners() {
        mHandoffController.onAssociationDisconnected(1);
        verifyTaskStackBroadcasterUnregistered(never());
    }

    @Test
    public void onTaskStackBroadcastMessageReceived_setsTasks() {
        FakeRemoteTaskListener fakeRemoteTaskListener = new FakeRemoteTaskListener();
        mHandoffController.registerTaskListener(fakeRemoteTaskListener);
        String packageName = "package_name";
        AssociationInfo associationInfo = createAssociationInfo(USER_ID, 1);
        when(mMockTaskContinuityMessenger.getAssociationInfo(associationInfo.getId()))
                .thenReturn(associationInfo);
        setApplicationInfo(packageName, "label");
        RemoteTaskInfo remoteTaskInfo =
                new RemoteTaskInfo(1, packageName, true, 100, new HandoffOptions(true, false));
        mHandoffController.onTaskStackBroadcastMessageReceived(
                associationInfo.getId(), new TaskStackBroadcastMessage(List.of(remoteTaskInfo)));
        fakeRemoteTaskListener.verifyListenerEvents(
                List.of(),
                List.of(
                        RemoteTaskFactory.create(
                                mMockPackageManager,
                                USER_ID,
                                associationInfo.getId(),
                                associationInfo.getDisplayName().toString(),
                                remoteTaskInfo)));
    }

    private void notifyAssociationConnected(AssociationInfo associationInfo) {
        when(mMockTaskContinuityMessenger.getConnectedAssociations())
                .thenReturn(List.of(associationInfo));
        mHandoffController.onAssociationConnected(associationInfo);
    }

    private void verifyTaskStackBroadcasterRegistered(VerificationMode verificationMode) {
        verify(mMockActivityTaskManager, verificationMode)
                .registerTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, verificationMode)
                .registerHandoffEnablementListener(mMockTaskBroadcaster);
    }

    private void verifyTaskStackBroadcasterUnregistered(VerificationMode verificationMode) {
        verify(mMockActivityTaskManager, verificationMode)
                .unregisterTaskStackListener(mMockTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, verificationMode)
                .unregisterHandoffEnablementListener(mMockTaskBroadcaster);
    }
}
