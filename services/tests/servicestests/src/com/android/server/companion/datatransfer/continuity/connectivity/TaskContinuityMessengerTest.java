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
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.companion.IOnMessageReceivedListener;
import android.companion.IOnTransportsChangedListener;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.messages.Proto;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskContinuityMessengerTest {

    private static final int USER_ID = 1;

    @Mock private Context mMockContext;
    @Mock private ICompanionDeviceManager mMockCompanionDeviceManagerService;
    @Mock private TaskContinuityMessenger.Listener mMockListener;
    @Mock private PackageManagerInternal mMockPackageManagerInternal;

    private final Executor mExecutor = Runnable::run;

    private TaskContinuityMessenger mTaskContinuityMessenger;

    private static final TaskContinuityMessage TEST_MESSAGE =
            new TaskContinuityMessage.Builder().build();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        LocalServices.addService(PackageManagerInternal.class, mMockPackageManagerInternal);
        when(mMockContext.getSystemService(Context.COMPANION_DEVICE_SERVICE))
                .thenReturn(
                        new CompanionDeviceManager(
                                mMockCompanionDeviceManagerService, mMockContext));
        when(mMockContext.getMainExecutor()).thenReturn(mExecutor);
        mTaskContinuityMessenger = new TaskContinuityMessenger(USER_ID, mMockContext);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
    }

    @Test
    public void testAddAndRemoveListeners_flowsMessages() throws Exception {
        // Start listening, verifying a message listener is added.
        ArgumentCaptor<IOnMessageReceivedListener> listenerCaptor =
                ArgumentCaptor.forClass(IOnMessageReceivedListener.class);
        mTaskContinuityMessenger.addListener(mMockListener);
        verify(mMockCompanionDeviceManagerService, times(1))
                .addOnMessageReceivedListener(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY), listenerCaptor.capture());
        IOnMessageReceivedListener listener = listenerCaptor.getValue();
        assertThat(listener).isNotNull();

        // Send a message to the listener.
        int expectedAssociationId = 1;
        notifyAssociationsChanged(
                createAssociationInfo(
                        expectedAssociationId, CompanionDeviceManager.FLAG_TASK_CONTINUITY, true));
        listener.onMessageReceived(expectedAssociationId, Proto.toBytes(TEST_MESSAGE));
        TestableLooper.get(this).processAllMessages();
        verify(mMockListener, times(1))
                .onMessageReceived(eq(expectedAssociationId), eq(TEST_MESSAGE));

        // Stop listening, verifying the message listener is removed.
        mTaskContinuityMessenger.removeListener(mMockListener);
        verify(mMockCompanionDeviceManagerService, times(1))
                .removeOnMessageReceivedListener(eq(MESSAGE_ONEWAY_TASK_CONTINUITY), any());
    }

    @Test
    public void testAddAndRemoveListeners_multipleListeners_flowsMessages() throws Exception {
        // Start listening, verifying a message listener is added.
        ArgumentCaptor<IOnMessageReceivedListener> listenerCaptor =
                ArgumentCaptor.forClass(IOnMessageReceivedListener.class);
        mTaskContinuityMessenger.addListener(mMockListener);
        TaskContinuityMessenger.Listener mockListener2 =
                Mockito.mock(TaskContinuityMessenger.Listener.class);
        mTaskContinuityMessenger.addListener(mockListener2);
        verify(mMockCompanionDeviceManagerService, times(1))
                .addOnMessageReceivedListener(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY), listenerCaptor.capture());
        IOnMessageReceivedListener listener = listenerCaptor.getValue();
        assertThat(listener).isNotNull();
        mTaskContinuityMessenger.removeListener(mMockListener);
        verify(mMockCompanionDeviceManagerService, never())
                .removeOnMessageReceivedListener(eq(MESSAGE_ONEWAY_TASK_CONTINUITY), any());
        mTaskContinuityMessenger.removeListener(mockListener2);
        verify(mMockCompanionDeviceManagerService, times(1))
                .removeOnMessageReceivedListener(eq(MESSAGE_ONEWAY_TASK_CONTINUITY), any());
    }

    @Test
    public void testSendMessage_associationSupportsTaskContinuity_sendsMessageToAssociation()
            throws RemoteException, IOException {
        int associationId = 1;
        mTaskContinuityMessenger.addListener(mMockListener);
        notifyAssociationsChanged(
                createAssociationInfo(
                        associationId, CompanionDeviceManager.FLAG_TASK_CONTINUITY, true));
        TaskContinuityMessenger.SendMessageResult result =
                mTaskContinuityMessenger.sendMessage(associationId, TEST_MESSAGE);
        verify(mMockCompanionDeviceManagerService, times(1))
                .sendMessage(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                        eq(Proto.toBytes(TEST_MESSAGE)),
                        aryEq(new int[] {associationId}));
        assertThat(result).isEqualTo(TaskContinuityMessenger.SendMessageResult.SUCCESS);
    }

    @Test
    public void testSendMessage_associationDoesNotSupportTaskContinuity_sendsMessageToAssociation()
            throws RemoteException, IOException {
        int associationId = 1;
        mTaskContinuityMessenger.addListener(mMockListener);
        notifyAssociationsChanged(createAssociationInfo(associationId, 0, false));
        TaskContinuityMessenger.SendMessageResult result =
                mTaskContinuityMessenger.sendMessage(associationId, TEST_MESSAGE);
        verify(mMockCompanionDeviceManagerService, never())
                .sendMessage(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                        eq(Proto.toBytes(TEST_MESSAGE)),
                        aryEq(new int[] {associationId}));
        assertThat(result)
                .isEqualTo(TaskContinuityMessenger.SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND);
    }

    @Test
    public void testSendMessage_associationNotFound_returnsFailure()
            throws RemoteException, IOException {
        int associationId = 1;
        TaskContinuityMessenger.SendMessageResult result =
                mTaskContinuityMessenger.sendMessage(associationId, TEST_MESSAGE);
        verify(mMockCompanionDeviceManagerService, never())
                .sendMessage(
                        eq(MESSAGE_ONEWAY_TASK_CONTINUITY),
                        eq(Proto.toBytes(TEST_MESSAGE)),
                        aryEq(new int[] {associationId}));
        assertThat(result)
                .isEqualTo(TaskContinuityMessenger.SendMessageResult.FAILURE_ASSOCIATION_NOT_FOUND);
    }

    @Test
    public void testOnTransportsChanged_associationConnectedAndDisconnected_notifiesObserver()
            throws RemoteException {
        // Connect an association, verifying it notifies listeners.
        AssociationInfo associationInfo =
                createAssociationInfo(1, CompanionDeviceManager.FLAG_TASK_CONTINUITY, true);
        mTaskContinuityMessenger.addListener(mMockListener);
        notifyAssociationsChanged(associationInfo);
        verify(mMockListener, times(1)).onAssociationConnected(eq(associationInfo));

        // Disconnect the association, verifying it notifies listeners.
        notifyAssociationsChanged();
        verify(mMockListener, times(1)).onAssociationDisconnected(eq(associationInfo.getId()));
    }

    @Test
    public void testOnTransportChanged_taskContinuityDisabled_notifiesObserver()
            throws RemoteException {
        // Connect an association
        AssociationInfo associationInfo =
                createAssociationInfo(1, CompanionDeviceManager.FLAG_TASK_CONTINUITY, true);
        mTaskContinuityMessenger.addListener(mMockListener);
        notifyAssociationsChanged(associationInfo);
        verify(mMockListener, times(1)).onAssociationConnected(eq(associationInfo));

        // Remove FLAG_TASK_CONTINUITY from the association.
        notifyAssociationsChanged(createAssociationInfo(associationInfo.getId(), 0, true));

        // Verify the listener was notified of the disconnection
        verify(mMockListener, times(1)).onAssociationDisconnected(eq(associationInfo.getId()));
    }

    @Test
    public void testOnTransportChanged_notifiedTwiceForSameAssociation_onlyNotifiesListenerOnce()
            throws RemoteException {
        // Register a listener.
        mTaskContinuityMessenger.addListener(mMockListener);

        // Connect an association twice.
        AssociationInfo associationInfo =
                createAssociationInfo(1, CompanionDeviceManager.FLAG_TASK_CONTINUITY, true);
        notifyAssociationsChanged(associationInfo);
        notifyAssociationsChanged(associationInfo);

        // Verify the observer is only notified once for the initial connection.
        verify(mMockListener, times(1)).onAssociationConnected(eq(associationInfo));
        verify(mMockListener, never()).onAssociationDisconnected(eq(associationInfo.getId()));
    }

    @Test
    public void testGetConnectedAssociations() throws RemoteException {
        mTaskContinuityMessenger.addListener(mMockListener);

        // Connect two associations.
        AssociationInfo associationInfo1 =
                createAssociationInfo(1, CompanionDeviceManager.FLAG_TASK_CONTINUITY, true);
        AssociationInfo associationInfo2 =
                createAssociationInfo(2, CompanionDeviceManager.FLAG_TASK_CONTINUITY, true);
        notifyAssociationsChanged(associationInfo1, associationInfo2);

        // Verify that getConnectedAssociations returns the correct set.
        assertThat(mTaskContinuityMessenger.getConnectedAssociations())
                .containsExactly(associationInfo1, associationInfo2);

        // Verify that getConnectedAssociationById returns the correct association.
        AssociationInfo result = mTaskContinuityMessenger.getAssociationInfo(1);
        assertThat(result).isEqualTo(associationInfo1);

        // Disconnect one association.
        mTaskContinuityMessenger.onTransportsChanged(Collections.singletonList(associationInfo2));

        // Verify that getConnectedAssociations returns the updated set.
        assertThat(mTaskContinuityMessenger.getConnectedAssociations())
                .containsExactly(associationInfo2);
    }

    @Test
    public void testGetConnectedAssociations_excludesAssociationsIfFromOtherUsers()
            throws RemoteException {
        mTaskContinuityMessenger.addListener(mMockListener);

        AssociationInfo availableAssociation =
                createAssociationInfo(1, CompanionDeviceManager.FLAG_TASK_CONTINUITY, true);
        AssociationInfo unavailableAssociation =
                createAssociationInfo(2, CompanionDeviceManager.FLAG_TASK_CONTINUITY, false);

        notifyAssociationsChanged(availableAssociation, unavailableAssociation);

        // Verify that getConnectedAssociations returns the correct set.
        assertThat(mTaskContinuityMessenger.getConnectedAssociations())
                .containsExactly(availableAssociation);
    }

    private AssociationInfo createAssociationInfo(
            int associationId, int systemDataSyncFlags, boolean isAssociationAvailableForUser) {
        String deviceName = "name" + associationId;
        String packageName = "com.android.test." + associationId;
        if (isAssociationAvailableForUser) {
            when(mMockPackageManagerInternal.getPackageUid(packageName, 0, USER_ID))
                    .thenReturn(100);
        } else {
            when(mMockPackageManagerInternal.getPackageUid(packageName, 0, USER_ID)).thenReturn(-1);
        }

        return new AssociationInfo.Builder(associationId, USER_ID, packageName)
                .setDisplayName(deviceName)
                .setSystemDataSyncFlags(systemDataSyncFlags)
                .build();
    }

    private void notifyAssociationsChanged(AssociationInfo... associationInfos) {
        ArgumentCaptor<IOnTransportsChangedListener> listenerCaptor =
                ArgumentCaptor.forClass(IOnTransportsChangedListener.class);
        try {
            verify(mMockCompanionDeviceManagerService, times(1))
                    .addOnTransportsChangedListener(listenerCaptor.capture());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        try {
            listenerCaptor.getValue().onTransportsChanged(List.of(associationInfos));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        TestableLooper.get(this).processAllMessages();
    }
}
