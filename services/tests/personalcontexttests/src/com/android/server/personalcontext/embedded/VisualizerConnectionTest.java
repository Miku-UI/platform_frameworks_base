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

package com.android.server.personalcontext.embedded;

import static com.android.server.personalcontext.util.InsightUtils.fakePublishInsight;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.IInsightSurfaceVisualizer;
import android.service.personalcontext.embedded.IVisualizationResult;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.embedded.InsightSurfaceVisualizerService;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VisualizerConnectionTest {
    @Mock
    private ComponentName mComponentName;
    @Mock
    private IInsightSurfaceVisualizer mVisualizer;
    @Mock
    private IBinder mBinder;
    @Mock
    private Context mContext;
    @Mock
    private Consumer<Set<UUID>> mOnVisualizerDiedCallback;

    private final TestInjector mTestInjector = new TestInjector();
    private VisualizerConnection mVisualizerConnection;


    private class TestInjector implements VisualizerConnection.Injector {
        private ServiceConnection mServiceConnection;
        private int mDisconnectCount = 0;

        public ServiceConnection getServiceConnection() {
            return mServiceConnection;
        }

        public int getDisconnectCount() {
            return mDisconnectCount;
        }

        public void resetDisconnectCount() {
            mDisconnectCount = 0;
        }

        @Override
        public boolean connectToService(Intent intent, ServiceConnection serviceConnection) {
            mServiceConnection = serviceConnection;
            serviceConnection.onServiceConnected(mComponentName, mBinder);
            return true;
        }

        @Override
        public void disconnectFromService(ServiceConnection serviceConnection) {
            mServiceConnection = null;
            mDisconnectCount += 1;
        }

        @Override
        public void executeAction(Runnable action) {
            action.run();
        }

        @Override
        public void onVisualizerDied(Set<UUID> connectedClientIds) {
            mOnVisualizerDiedCallback.accept(connectedClientIds);
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        mVisualizerConnection = new VisualizerConnection(mComponentName, mTestInjector);
        when(IInsightSurfaceVisualizer.Stub.asInterface(mBinder)).thenReturn(mVisualizer);
    }

    @Test
    public void testGetComponentName() {
        assertThat(mVisualizerConnection.getComponentName()).isEqualTo(mComponentName);
    }

    @Test
    public void testOnUnregistered() throws RemoteException {
        final InsightSurfaceClientInfo client = mock(InsightSurfaceClientInfo.class);
        createVisualizationForClient(client, true);
        assertThat(mTestInjector.getServiceConnection()).isNotNull();
        mVisualizerConnection.onUnregistered();
        assertThat(mTestInjector.getServiceConnection()).isNull();
    }

    @Test
    public void testOnClientDisconnected() throws RemoteException {
        final InsightSurfaceClientInfo client = createClient();
        createVisualizationForClient(client, true);
        mVisualizerConnection.onClientDisconnected(client);
        verify(mVisualizer).onClientDisconnected(eq(client), any());
    }

    @Test
    public void testCreateVisualizationForClient() throws RemoteException {
        createVisualizationForClient(createClient(), true);
    }

    @Test
    public void testUnbindFromService_whenLastClientDisconnects() throws RemoteException {
        final InsightSurfaceClientInfo client = createClient();
        createVisualizationForClient(client, true);
        assertThat(mTestInjector.getServiceConnection()).isNotNull();
        mVisualizerConnection.onClientDisconnected(client);
        verify(mVisualizer).onClientDisconnected(eq(client), any());
        assertThat(mTestInjector.getServiceConnection()).isNull();
    }

    @Test
    public void testDoesNotUnbindFromService_whenClientStillConnected() throws RemoteException {
        final InsightSurfaceClientInfo client1 = createClient();
        final InsightSurfaceClientInfo client2 = createClient();

        createVisualizationForClient(client1, true);
        createVisualizationForClient(client2, true);

        mVisualizerConnection.onClientDisconnected(client1);
        assertThat(mTestInjector.getServiceConnection()).isNotNull();
    }

    @Test
    public void testUnbindFromService_whenNoVisualizationCreated() throws RemoteException {
        final InsightSurfaceClientInfo client = createClient();
        createVisualizationForClient(client, false);
        assertThat(mTestInjector.getServiceConnection()).isNull();
    }

    @Test
    public void connectToVisualizer_withDefaultInjector_usesCorrectBindFlags()
            throws RemoteException  {
        // Use the constructor that creates a DefaultInjector to test its behavior.
        final VisualizerConnection connection = new VisualizerConnection(
                mComponentName, mContext, mOnVisualizerDiedCallback, Runnable::run);
        final ContextInsight insight = new BundleInsight.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);

        // Trigger the service binding.
        connection.createVisualizationForClient(
                fakePublishInsight(insight), createClient(), renderToken, (success) -> {});

        // Capture the arguments passed to context.bindService to verify the flags.
        final ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        final ArgumentCaptor<Integer> flagsCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mContext).bindService(
                intentCaptor.capture(), any(ServiceConnection.class), flagsCaptor.capture());

        // Verify that the BIND_ALLOW_ACTIVITY_STARTS flag is included.
        final int expectedFlags = Context.BIND_AUTO_CREATE
                | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS;
        assertThat(flagsCaptor.getValue()).isEqualTo(expectedFlags);

        // Also verify the intent is constructed correctly.
        final Intent capturedIntent = intentCaptor.getValue();
        assertThat(capturedIntent.getComponent()).isEqualTo(mComponentName);
        assertThat(capturedIntent.getAction())
                .isEqualTo(InsightSurfaceVisualizerService.SERVICE_INTERFACE);
    }

    @Test
    public void testOnServiceDisconnected_doesNotCallDisconnectFromService()
            throws RemoteException {
        createVisualizationForClient(createClient(), true);
        ServiceConnection serviceConnection = mTestInjector.getServiceConnection();
        assertThat(serviceConnection).isNotNull();

        serviceConnection.onServiceDisconnected(mComponentName);
        assertThat(mTestInjector.getServiceConnection()).isNotNull();
    }

    @Test
    public void testOnBindingDied_doesNotCallDisconnectFromService()
            throws RemoteException {
        createVisualizationForClient(createClient(), true);
        ServiceConnection serviceConnection = mTestInjector.getServiceConnection();
        assertThat(serviceConnection).isNotNull();

        serviceConnection.onBindingDied(mComponentName);
        assertThat(mTestInjector.getServiceConnection()).isNotNull();
    }

    @Test
    public void testNoVisualization_andClientDisconnects_disconnectsVisualizerOnce()
            throws RemoteException {
        mTestInjector.resetDisconnectCount();
        final InsightSurfaceClientInfo client = createClient();
        createVisualizationForClient(client, true);
        createVisualizationForClient(client, false);
        mVisualizerConnection.onClientDisconnected(client);
        assertThat(mTestInjector.getDisconnectCount()).isEqualTo(1);
    }

    @Test
    public void testServiceConnected_linksToDeath_callsOnVisualizerDiedCallback()
            throws RemoteException {
        // Trigger the service connection.
        createVisualizationForClient(createClient(), true);

        // Verify that linkToDeath was called on the binder.
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(mBinder).linkToDeath(deathRecipientCaptor.capture(), eq(0));

        // Verify the onVisualizerDiedCallback is called.
        deathRecipientCaptor.getValue().binderDied();
        verify(mOnVisualizerDiedCallback).accept(any());
    }

    private InsightSurfaceClientInfo createClient() {
        final InsightSurfaceClientInfo client = mock(InsightSurfaceClientInfo.class);
        when(client.getId()).thenReturn(UUID.randomUUID());
        return client;
    }

    private void createVisualizationForClient(
            InsightSurfaceClientInfo client,
            boolean shouldSucceed) throws RemoteException {
        final ContextInsight insight = new BundleInsight.Builder().build();
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);
        ArgumentCaptor<IVisualizationResult> resultCaptor =
                ArgumentCaptor.forClass(IVisualizationResult.class);

        mVisualizerConnection.createVisualizationForClient(
                fakePublishInsight(insight), client, renderToken, (success) -> {});
        verify(mVisualizer).createVisualizationForClient(
                argThat(wrapper ->
                        wrapper.getPublishedContextInsight().getInsight() == insight),
                any(),
                eq(renderToken),
                resultCaptor.capture(),
                any());
        resultCaptor.getValue().onResult(shouldSucceed);
    }
}
