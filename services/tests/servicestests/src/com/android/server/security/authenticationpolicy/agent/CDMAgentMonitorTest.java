/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.security.authenticationpolicy.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.DevicePresenceEvent;
import android.companion.ICompanionDeviceManager;
import android.companion.IOnAssociationsChangedListener;
import android.companion.IOnDevicePresenceEventListener;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class CDMAgentMonitorTest {

    private static final int USER_ID = 10;
    private static final String SERVICE_NAME = "CDMAgentMonitor";

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private Context mMockContext;
    @Mock
    private ICompanionDeviceManager mMockService;
    @Mock
    private CDMAgentMonitor.Listener mMockListener;
    private CompanionDeviceManager mCompanionDeviceManager;
    private Handler mHandler;
    private CDMAgentMonitor mMonitor;

    @Before
    public void setUp() throws RemoteException {
        when(mMockContext.getUserId()).thenReturn(USER_ID);
        when(mMockService.getAllAssociationsForUser(anyInt())).thenReturn(Collections.emptyList());

        mCompanionDeviceManager = new CompanionDeviceManager(mMockService, mMockContext);
        mHandler = new Handler(TestableLooper.get(this).getLooper());

        mMonitor = new CDMAgentMonitor(mHandler, USER_ID, mCompanionDeviceManager, mMockListener);

        mMonitor.start();
        TestableLooper.get(this).processAllMessages();
    }

    @Test
    public void testStart_subscribesToAssociations() throws RemoteException {
        verify(mMockService).addOnAssociationsChangedListener(any(), eq(USER_ID));
    }

    @Test
    public void testAssociationsChanged_subscribesToPresence() throws RemoteException {
        ArgumentCaptor<IOnAssociationsChangedListener> captor =
                ArgumentCaptor.forClass(IOnAssociationsChangedListener.class);
        verify(mMockService).addOnAssociationsChangedListener(captor.capture(), eq(USER_ID));

        AssociationInfo association = createAssociation(1, true);

        captor.getValue().onAssociationsChanged(List.of(association));
        TestableLooper.get(this).processAllMessages();

        verify(mMockService).setOnDevicePresenceEventListener(
                eq(new int[]{1}), eq(SERVICE_NAME), any(), eq(USER_ID));
    }

    @Test
    public void testAssociationsChanged_ignoresNonAgentAssociations() throws RemoteException {
        ArgumentCaptor<IOnAssociationsChangedListener> captor =
                ArgumentCaptor.forClass(IOnAssociationsChangedListener.class);
        verify(mMockService).addOnAssociationsChangedListener(captor.capture(), eq(USER_ID));

        AssociationInfo association = createAssociation(1, false);

        captor.getValue().onAssociationsChanged(List.of(association));
        TestableLooper.get(this).processAllMessages();

        // Should NOT call setOnDevicePresenceEventListener, but instead remove any existing ones
        verify(mMockService, atLeastOnce()).removeOnDevicePresenceEventListener(eq(SERVICE_NAME),
                eq(USER_ID));
    }

    @Test
    public void testAssociationsChanged_mixOfAssociations_subscribesOnlyToAgents()
            throws RemoteException {
        ArgumentCaptor<IOnAssociationsChangedListener> captor =
                ArgumentCaptor.forClass(IOnAssociationsChangedListener.class);
        verify(mMockService).addOnAssociationsChangedListener(captor.capture(), eq(USER_ID));

        AssociationInfo agent = createAssociation(1, true);
        AssociationInfo nonAgent = createAssociation(2, false);

        captor.getValue().onAssociationsChanged(List.of(agent, nonAgent));
        TestableLooper.get(this).processAllMessages();

        // Verify that only the agent's ID is passed to the presence listener
        verify(mMockService).setOnDevicePresenceEventListener(
                eq(new int[]{1}), eq(SERVICE_NAME), any(), eq(USER_ID));
    }

    @Test
    public void testPresenceEvent_triggersListener() throws RemoteException {
        ArgumentCaptor<IOnAssociationsChangedListener> assocCaptor =
                ArgumentCaptor.forClass(IOnAssociationsChangedListener.class);
        verify(mMockService).addOnAssociationsChangedListener(assocCaptor.capture(), eq(USER_ID));

        int associationId = 123;
        AssociationInfo association = createAssociation(associationId, true);

        assocCaptor.getValue().onAssociationsChanged(List.of(association));
        TestableLooper.get(this).processAllMessages();

        ArgumentCaptor<IOnDevicePresenceEventListener> presenceCaptor =
                ArgumentCaptor.forClass(IOnDevicePresenceEventListener.class);
        verify(mMockService).setOnDevicePresenceEventListener(
                any(), eq(SERVICE_NAME), presenceCaptor.capture(), eq(USER_ID));

        DevicePresenceEvent event = new DevicePresenceEvent.Builder()
                .setAssociationId(associationId)
                .setEvent(DevicePresenceEvent.EVENT_BT_CONNECTED)
                .build();

        presenceCaptor.getValue().onDevicePresence(event);
        TestableLooper.get(this).processAllMessages();

        verify(mMockListener).onAgentConnectionStarted(associationId);
    }

    @Test
    public void testDisconnectedEvent_triggersListener() throws RemoteException {
        ArgumentCaptor<IOnAssociationsChangedListener> assocCaptor =
                ArgumentCaptor.forClass(IOnAssociationsChangedListener.class);
        verify(mMockService).addOnAssociationsChangedListener(assocCaptor.capture(), eq(USER_ID));

        int associationId = 123;
        AssociationInfo association = createAssociation(associationId, true);

        assocCaptor.getValue().onAssociationsChanged(List.of(association));
        TestableLooper.get(this).processAllMessages();

        ArgumentCaptor<IOnDevicePresenceEventListener> presenceCaptor =
                ArgumentCaptor.forClass(IOnDevicePresenceEventListener.class);
        verify(mMockService).setOnDevicePresenceEventListener(
                any(), eq(SERVICE_NAME), presenceCaptor.capture(), eq(USER_ID));

        DevicePresenceEvent event = new DevicePresenceEvent.Builder()
                .setAssociationId(associationId)
                .setEvent(DevicePresenceEvent.EVENT_BT_DISCONNECTED)
                .build();

        presenceCaptor.getValue().onDevicePresence(event);
        TestableLooper.get(this).processAllMessages();

        verify(mMockListener).onAgentConnectionStopped(associationId);
    }

    @Test
    public void testStop_unsubscribes() throws RemoteException {
        ArgumentCaptor<IOnAssociationsChangedListener> captor =
                ArgumentCaptor.forClass(IOnAssociationsChangedListener.class);
        verify(mMockService).addOnAssociationsChangedListener(captor.capture(), eq(USER_ID));

        mMonitor.stop();
        TestableLooper.get(this).processAllMessages();

        verify(mMockService).removeOnAssociationsChangedListener(eq(captor.getValue()),
                eq(USER_ID));
    }

    private AssociationInfo createAssociation(int id, boolean isAgent) {
        return new AssociationInfo.Builder(id, USER_ID, "pkg")
                .setDisplayName("Device")
                .setRemoteAiAgentSupported(isAgent)
                .build();
    }
}
