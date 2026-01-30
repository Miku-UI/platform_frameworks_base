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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.ICompanionDeviceManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class ConnectedAssociationStoreTest {

    private Context mMockContext;
    @Mock private ICompanionDeviceManager mMockCompanionDeviceManagerService;
    @Mock private Executor mMockExecutor;
    @Mock private ConnectedAssociationStore.Listener mMockListener;

    private ConnectedAssociationStore mConnectedAssociationStore;

    private CompanionDeviceManager mCompanionDeviceManager;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        mMockContext =
                Mockito.spy(
                        new ContextWrapper(
                                InstrumentationRegistry.getInstrumentation().getTargetContext()));

        mCompanionDeviceManager =
                new CompanionDeviceManager(mMockCompanionDeviceManagerService, mMockContext);

        mConnectedAssociationStore =
                new ConnectedAssociationStore(
                        mCompanionDeviceManager, mMockContext.getMainExecutor(), mMockListener);
    }

    @Test
    public void testEnableAndDisable_registersListener() throws RemoteException {
        mConnectedAssociationStore.enable();
        verify(mMockCompanionDeviceManagerService, times(1)).addOnTransportsChangedListener(any());

        mConnectedAssociationStore.disable();
        verify(mMockCompanionDeviceManagerService, times(1))
                .removeOnTransportsChangedListener(any());
    }

    @Test
    public void testOnTransportConnected_notifyObserver() throws RemoteException {
        // Simulate a new association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name", true);
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo));

        // Verify the observer is notified.
        verify(mMockListener).onTransportConnected(eq(associationInfo));
    }

    @Test
    public void testOnTransportDisconnected_notifyObserver() throws RemoteException {
        // Start with an association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name", true);
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo));

        // Simulate the association being disconnected.
        mConnectedAssociationStore.onTransportsChanged(Collections.emptyList());

        // Verify the observer is notified of the disconnection.
        verify(mMockListener).onTransportDisconnected(eq(associationInfo.getId()), any());
    }

    @Test
    public void testOnTransportChanged_noChange_noNotification() throws RemoteException {
        // Start with an association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name", true);
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo));

        // Simulate the same association still connected.
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo));

        // Verify the observer is only notified once for the initial connection.
        verify(mMockListener, times(1)).onTransportConnected(eq(associationInfo));
        verify(mMockListener, never()).onTransportDisconnected(eq(associationInfo.getId()), any());
    }

    @Test
    public void testOnTransportChanged_taskContinuityDisabled_notifiesObserver()
            throws RemoteException {
        // Start with an association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name", true);
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo));

        // Simulate the same association still connected.
        AssociationInfo associationInfo2 = createAssociationInfo(1, "name", false);
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo2));

        // Verify the observer is only notified once for the initial connection.
        verify(mMockListener, times(1)).onTransportConnected(eq(associationInfo));
        verify(mMockListener, times(1)).onTransportDisconnected(eq(associationInfo.getId()), any());
    }

    @Test
    public void testOnTransportChanged_taskContinuityEnabled_notifiesObserver()
            throws RemoteException {
        // Start with an association connected.
        AssociationInfo associationInfo = createAssociationInfo(1, "name", false);
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo));

        // Simulate the same association still connected.
        AssociationInfo associationInfo2 = createAssociationInfo(1, "name", true);
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo2));

        // Verify the observer is only notified once for the initial connection.
        verify(mMockListener, times(1)).onTransportConnected(eq(associationInfo2));
        verify(mMockListener, never()).onTransportDisconnected(eq(associationInfo.getId()), any());
    }

    @Test
    public void testGetConnectedAssociations() throws RemoteException {
        // Connect two associations.
        AssociationInfo associationInfo1 = createAssociationInfo(1, "name", true);
        AssociationInfo associationInfo2 = createAssociationInfo(2, "name", true);
        mConnectedAssociationStore.onTransportsChanged(List.of(associationInfo1, associationInfo2));

        // Verify that getConnectedAssociations returns the correct set.
        assertThat(mConnectedAssociationStore.getConnectedAssociations())
                .containsExactly(associationInfo1, associationInfo2);

        AssociationInfo result = mConnectedAssociationStore.getConnectedAssociationById(1);
        assertThat(result).isEqualTo(associationInfo1);

        // Disconnect one association.
        mConnectedAssociationStore.onTransportsChanged(Collections.singletonList(associationInfo2));

        // Verify that getConnectedAssociations returns the updated set.
        assertThat(mConnectedAssociationStore.getConnectedAssociations())
                .containsExactly(associationInfo2);
    }

    private AssociationInfo createAssociationInfo(
            int associationId, String deviceName, boolean isTaskContinuityEnabled) {
        int systemDataSyncFlags =
                isTaskContinuityEnabled ? CompanionDeviceManager.FLAG_TASK_CONTINUITY : 0;

        return new AssociationInfo.Builder(associationId, 0, "com.android.test")
                .setDisplayName(deviceName)
                .setSystemDataSyncFlags(systemDataSyncFlags)
                .build();
    }
}
