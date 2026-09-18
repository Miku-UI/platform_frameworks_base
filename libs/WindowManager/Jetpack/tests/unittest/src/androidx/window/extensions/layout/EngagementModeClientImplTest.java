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

package androidx.window.extensions.layout;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Test class for {@link EngagementModeClientImpl}.
 *
 * Build/Install/Run:
 *  atest WMJetpackUnitTests:EngagementModeClientImplTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class EngagementModeClientImplTest {

    private static final String FAKE_PACKAGE_NAME = "test.package.name";
    private static final String SERVICE_INTERFACE =
            "androidx.window.extensions.layout.EngagementModeService";

    @Mock
    private Context mMockContext;
    @Mock
    private Consumer<Integer> mMockConsumer;
    @Mock
    private IBinder mMockBinder;
    @Mock
    private IEngagementModeService mMockService;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private ResolveInfo mMockResolveInfo;
    @Mock
    private Handler mMockHandler;

    @Captor
    private ArgumentCaptor<ServiceConnection> mServiceConnectionCaptor;
    @Captor
    private ArgumentCaptor<Intent> mIntentCaptor;
    @Captor
    private ArgumentCaptor<IEngagementModeCallback> mCallbackCaptor;
    @Captor
    private ArgumentCaptor<Runnable> mReconnectRunnableCaptor;

    private EngagementModeClientImpl mClient;
    private final Executor mDirectExecutor = Runnable::run;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockBinder.queryLocalInterface(IEngagementModeService.class.getName()))
                .thenReturn(mMockService);

        mMockResolveInfo.serviceInfo = new ServiceInfo();
        mMockResolveInfo.serviceInfo.packageName = FAKE_PACKAGE_NAME;
        when(mMockPackageManager.resolveService(any(Intent.class), anyInt()))
                .thenReturn(mMockResolveInfo);

        // Default to a valid system app. Individual tests can override this.
        mockSystemAppVerification(true);
    }

    @Test
    public void testClient_startsUnbound_withDefaultFlags() {
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);
        assertThat(mClient.getEngagementModeFlags()).isEqualTo(
                EngagementModeClient.DEFAULT_ENGAGEMENT_MODE);
        verify(mMockConsumer, never()).accept(anyInt());
    }

    @Test
    public void testClient_bindsToServiceIfSystemApp() {
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);
        verify(mMockContext).bindService(mIntentCaptor.capture(),
                mServiceConnectionCaptor.capture(), eq(Context.BIND_AUTO_CREATE));
        Intent intent = mIntentCaptor.getValue();
        assertThat(intent.getAction()).isEqualTo(SERVICE_INTERFACE);
        assertThat(intent.getPackage()).isEqualTo(FAKE_PACKAGE_NAME);
    }

    @Test
    public void testClient_doesNotBindIfProviderNotSystemApp()
            throws PackageManager.NameNotFoundException {
        // Service is NOT a system app
        mockSystemAppVerification(false);

        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);

        // bindService is not called
        verify(mMockContext, never()).bindService(any(), any(), anyInt());
    }

    @Test
    public void testClient_receivesUpdatesFromCallback() throws RemoteException {
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);
        verify(mMockContext).bindService(any(), mServiceConnectionCaptor.capture(), anyInt());
        ServiceConnection connection = mServiceConnectionCaptor.getValue();

        // Connect to service
        connection.onServiceConnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"), mMockBinder);

        // Verify callback is registered
        verify(mMockService).registerCallback(mCallbackCaptor.capture());
        IEngagementModeCallback callback = mCallbackCaptor.getValue();

        // Simulate initial state from service
        int initialFlags = WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_VISUALS_ON;
        callback.onEngagementModeChanged(initialFlags);

        // Client gets the initial state and the update callback is called
        assertThat(mClient.getEngagementModeFlags()).isEqualTo(initialFlags);
        verify(mMockConsumer, times(1)).accept(initialFlags);

        // Simulate engagement mode update from service
        int newFlags = 0;
        callback.onEngagementModeChanged(newFlags);

        // Client is updated and the update callback is called again
        assertThat(mClient.getEngagementModeFlags()).isEqualTo(newFlags);
        verify(mMockConsumer, times(1)).accept(newFlags);
    }

    @Test
    public void testClient_resetsFlagsOnDisconnect() throws RemoteException {
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);
        verify(mMockContext).bindService(any(), mServiceConnectionCaptor.capture(), anyInt());
        ServiceConnection connection = mServiceConnectionCaptor.getValue();

        // Connect to service
        connection.onServiceConnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"), mMockBinder);

        // Verify callback is registered
        verify(mMockService).registerCallback(mCallbackCaptor.capture());
        IEngagementModeCallback callback = mCallbackCaptor.getValue();

        // Simulate initial state from service
        int initialFlags = WindowLayoutInfo.ENGAGEMENT_MODE_FLAG_VISUALS_ON;
        callback.onEngagementModeChanged(initialFlags);

        // Client gets the initial state and the update callback is called
        assertThat(mClient.getEngagementModeFlags()).isEqualTo(initialFlags);
        verify(mMockConsumer, times(1)).accept(initialFlags);

        // Disconnect from service
        connection.onServiceDisconnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"));

        // Verify that the flags are reset to the default value
        assertThat(mClient.getEngagementModeFlags()).isEqualTo(
                EngagementModeClient.DEFAULT_ENGAGEMENT_MODE);
        verify(mMockConsumer, times(1)).accept(EngagementModeClient.DEFAULT_ENGAGEMENT_MODE);
    }

    @Test
    public void testClient_schedulesReconnectOnDisconnect_exponentialBackoff() {
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);
        verify(mMockContext).bindService(any(), mServiceConnectionCaptor.capture(), anyInt());
        ServiceConnection connection = mServiceConnectionCaptor.getValue();

        // Connect to service
        connection.onServiceConnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"), mMockBinder);

        // Disconnect from service
        connection.onServiceDisconnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"));

        // Verify that a reconnect is scheduled with the initial delay.
        verify(mMockHandler).postDelayed(mReconnectRunnableCaptor.capture(),
                eq(EngagementModeClientImpl.INITIAL_RECONNECT_DELAY_MS));

        // Verify that running the captured runnable triggers a reconnect.
        Runnable reconnectRunnable = mReconnectRunnableCaptor.getValue();
        reconnectRunnable.run();

        // Verify bindService is called again (total of 2 times)
        verify(mMockContext, times(2)).bindService(any(), any(), anyInt());

        // Disconnect again
        connection.onServiceDisconnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"));

        // Verify that a reconnect is scheduled with the next delay in the backoff.
        verify(mMockHandler).postDelayed(any(Runnable.class),
                eq(EngagementModeClientImpl.INITIAL_RECONNECT_DELAY_MS * 10));

        // Connect to service again
        connection.onServiceConnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"), mMockBinder);

        // Disconnect again
        connection.onServiceDisconnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"));

        // Verify that the reconnect delay is reset to the initial delay.
        verify(mMockHandler, times(2)).postDelayed(any(Runnable.class),
                eq(EngagementModeClientImpl.INITIAL_RECONNECT_DELAY_MS));
    }

    @Test
    public void testClient_addCallback_bindsOnlyOnce() {
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);

        // Add a listener. This should trigger binding.
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);

        // Add another listener. This should not trigger binding again.
        mClient.addUpdateCallback(mDirectExecutor, i -> { });

        // Verify bindService is only called once
        verify(mMockContext, times(1)).bindService(any(), any(), anyInt());
    }

    @Test
    public void testClient_removeLastCallback_unbindsService() {
        // Simulate that bindService succeeds and returns true
        when(mMockContext.bindService(
                any(), mServiceConnectionCaptor.capture(), anyInt())).thenReturn(true);
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);

        // Add callback, which connects and simulates connection succeeding
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);
        ServiceConnection connection = mServiceConnectionCaptor.getValue();
        connection.onServiceConnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"), mMockBinder);

        // Remove callback
        mClient.removeUpdateCallback(mMockConsumer);

        // Verify unbindService is called and handler callbacks are cancelled
        verify(mMockContext).unbindService(eq(connection));
        verify(mMockHandler).removeCallbacksAndMessages(null);
    }

    @Test
    public void testClient_removeCallback_doesNotUnbindWithActiveListeners() {
        // Simulate that bindService succeeds and returns true
        when(mMockContext.bindService(
                any(), mServiceConnectionCaptor.capture(), anyInt())).thenReturn(true);
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);
        Consumer<Integer> anotherConsumer = i -> {};

        // Add two listeners
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);
        mClient.addUpdateCallback(mDirectExecutor, anotherConsumer);
        ServiceConnection connection = mServiceConnectionCaptor.getValue();
        connection.onServiceConnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"), mMockBinder);

        // Remove first listener
        mClient.removeUpdateCallback(mMockConsumer);

        // Verify unbindService is NOT called, and handler is NOT cancelled
        verify(mMockContext, never()).unbindService(any());
        verify(mMockHandler, never()).removeCallbacksAndMessages(null);

        // Remove second (last) listener
        mClient.removeUpdateCallback(anotherConsumer);

        // Verify unbindService IS called, and handler IS cancelled
        verify(mMockContext).unbindService(eq(connection));
        verify(mMockHandler).removeCallbacksAndMessages(null);
    }

    @Test
    public void testClient_doesNotReconnectOnServiceDisconnect_ifNoListeners() {
        when(mMockContext.bindService(any(), mServiceConnectionCaptor.capture(), anyInt()))
                .thenReturn(true);
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);
        ServiceConnection connection = mServiceConnectionCaptor.getValue();

        // Connect to service
        connection.onServiceConnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"), mMockBinder);

        // Remove listener before service disconnects
        mClient.removeUpdateCallback(mMockConsumer);

        // Disconnect from service
        connection.onServiceDisconnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"));

        // Verify that a reconnect is NOT scheduled.
        verify(mMockHandler, never()).postDelayed(any(Runnable.class), anyLong());
    }

    @Test
    public void testClient_unbindsOnConnect_ifListenersRemovedDuringBind() {
        // Simulate that bindService succeeds and returns true
        when(mMockContext.bindService(
                any(), mServiceConnectionCaptor.capture(), anyInt())).thenReturn(true);
        mClient = new EngagementModeClientImpl(mMockContext, mMockHandler);

        // Add the listener, which triggers the bind
        mClient.addUpdateCallback(mDirectExecutor, mMockConsumer);

        // Capture the connection
        ServiceConnection connection = mServiceConnectionCaptor.getValue();

        // Remove the listener before the service connects
        mClient.removeUpdateCallback(mMockConsumer);

        // Simulate the service connecting (this is the race)
        connection.onServiceConnected(new ComponentName(FAKE_PACKAGE_NAME, "Test"), mMockBinder);

        // Verify that the client should immediately unbind to prevent a leak.
        verify(mMockContext).unbindService(eq(connection));
    }

    private void mockSystemAppVerification(boolean isSystemApp)
            throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        if (isSystemApp) {
            packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        }
        when(mMockPackageManager.getPackageInfo(FAKE_PACKAGE_NAME, 0)).thenReturn(packageInfo);
    }
}
