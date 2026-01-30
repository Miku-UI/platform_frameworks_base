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

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;
import android.content.ContextWrapper;
import android.companion.IOnMessageReceivedListener;
import android.companion.IOnTransportsChangedListener;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.companion.AssociationInfo;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessageSerializer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskContinuityMessengerTest {

    private Context mMockContext;
    @Mock private ICompanionDeviceManager mMockCompanionDeviceManagerService;
    private CompanionDeviceManager mCompanionDeviceManager;
    @Mock private TaskContinuityMessenger.Listener mMockListener;

    private TaskContinuityMessenger mTaskContinuityMessenger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockContext =  Mockito.spy(
            new ContextWrapper(
                InstrumentationRegistry
                    .getInstrumentation()
                    .getTargetContext()));

        // Setup fake services.
        mCompanionDeviceManager
            = new CompanionDeviceManager(
                mMockCompanionDeviceManagerService,
                mMockContext);

        when(mMockContext.getSystemService(Context.COMPANION_DEVICE_SERVICE))
            .thenReturn(mCompanionDeviceManager);

        // Create TaskContinuityMessenger.
        mTaskContinuityMessenger = new TaskContinuityMessenger(mMockContext, mMockListener);

    }

    @Test
    public void testEnableAndDisable_registersListenersAndFlowsMessages() throws Exception {
        // Start listening, verifying a message listener is added.
        ArgumentCaptor<IOnMessageReceivedListener> listenerCaptor
            = ArgumentCaptor.forClass(IOnMessageReceivedListener.class);
        mTaskContinuityMessenger.enable();
        verify(mMockCompanionDeviceManagerService, times(1))
            .addOnMessageReceivedListener(
                eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                listenerCaptor.capture());
        IOnMessageReceivedListener listener = listenerCaptor.getValue();
        assertThat(listener).isNotNull();

        // Send a message to the listener.
        int expectedAssociationId = 1;
        connectAssociations(List.of(expectedAssociationId));
        ContinuityDeviceConnected expectedMessage = new ContinuityDeviceConnected(
                    List.of(new RemoteTaskInfo(1, "label", 1000, new byte[0], true)));

        listener.onMessageReceived(
            expectedAssociationId,
            TaskContinuityMessageSerializer.serialize(expectedMessage));
        TestableLooper.get(this).processAllMessages();
        verify(mMockListener, times(1))
            .onMessageReceived(eq(expectedAssociationId), eq(expectedMessage));

        // Stop listening, verifying the message listener is removed.
        mTaskContinuityMessenger.disable();
        verify(mMockCompanionDeviceManagerService, times(1))
            .removeOnMessageReceivedListener(
                eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                any());
    }

    @Test
    public void testSendMessage_sendsMessageToAssociation() throws RemoteException, IOException {
        int associationId = 1;

        mTaskContinuityMessenger.enable();
        connectAssociations(List.of(associationId));
        ContinuityDeviceConnected expectedMessage = new ContinuityDeviceConnected(
            List.of(new RemoteTaskInfo(1, "label", 1000, new byte[0], true)));
        TaskContinuityMessenger.SendMessageResult result
            = mTaskContinuityMessenger.sendMessage(associationId, expectedMessage);
        verify(mMockCompanionDeviceManagerService, times(1))
            .sendMessage(
                eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                eq(TaskContinuityMessageSerializer.serialize(expectedMessage)),
                aryEq(new int[]{associationId}));
        assertThat(result).isEqualTo(TaskContinuityMessenger.SendMessageResult.SUCCESS);
    }

    @Test
    public void testSendMessage_associationNotFound_returnsFailure()
        throws RemoteException, IOException {

        int associationId = 1;
        ContinuityDeviceConnected expectedMessage = new ContinuityDeviceConnected(
            List.of(new RemoteTaskInfo(1, "label", 1000, new byte[0], true)));
        TaskContinuityMessenger.SendMessageResult result
            = mTaskContinuityMessenger.sendMessage(associationId, expectedMessage);
        verify(mMockCompanionDeviceManagerService, never())
            .sendMessage(
                eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                eq(TaskContinuityMessageSerializer.serialize(expectedMessage)),
                aryEq(new int[]{associationId}));
        assertThat(result)
            .isEqualTo(TaskContinuityMessenger.SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND);
    }

    private void connectAssociations(List<Integer> associationIds) {
        ArgumentCaptor<IOnTransportsChangedListener> listenerCaptor
            = ArgumentCaptor.forClass(IOnTransportsChangedListener.class);
        try {
            verify(mMockCompanionDeviceManagerService, times(1))
                .addOnTransportsChangedListener(listenerCaptor.capture());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        List<AssociationInfo> associationInfos = associationIds.stream()
            .map(id -> new AssociationInfo.Builder(id, 0, "com.android.test")
                    .setDisplayName("name")
                    .build())
            .collect(Collectors.toList());

        try {
            listenerCaptor.getValue().onTransportsChanged(associationInfos);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        TestableLooper.get(this).processAllMessages();
    }
}