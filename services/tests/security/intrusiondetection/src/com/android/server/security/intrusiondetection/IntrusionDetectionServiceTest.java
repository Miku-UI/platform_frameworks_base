/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.security.intrusiondetection;

import static android.Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE;
import static android.Manifest.permission.MANAGE_INTRUSION_DETECTION_STATE;
import static android.Manifest.permission.READ_INTRUSION_DETECTION_STATE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.admin.ConnectEvent;
import android.app.admin.DevicePolicyManagerInternal;
import android.app.admin.DnsEvent;
import android.app.admin.SecurityLog.SecurityEvent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.Looper;
import android.os.PermissionEnforcer;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.test.FakePermissionEnforcer;
import android.os.test.TestLooper;
import android.security.intrusiondetection.IIntrusionDetectionEventTransport;
import android.security.intrusiondetection.IIntrusionDetectionServiceCommandCallback;
import android.security.intrusiondetection.IIntrusionDetectionServiceStateCallback;
import android.security.intrusiondetection.IntrusionDetectionEvent;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.internal.infra.AndroidFuture;
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser;
import com.android.bedstead.permissions.CommonPermissions;
import com.android.bedstead.permissions.annotations.EnsureHasPermission;
import com.android.coretests.apps.testapp.LocalIntrusionDetectionEventTransport;
import com.android.server.ServiceThread;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(BedsteadJUnit4.class)
public class IntrusionDetectionServiceTest {
    private static final int STATE_UNKNOWN =
            IIntrusionDetectionServiceStateCallback.State.UNKNOWN;
    private static final int STATE_DISABLED =
            IIntrusionDetectionServiceStateCallback.State.DISABLED;
    private static final int STATE_ENABLED =
            IIntrusionDetectionServiceStateCallback.State.ENABLED;

    private static final int ERROR_UNKNOWN =
            IIntrusionDetectionServiceCommandCallback.ErrorCode.UNKNOWN;
    private static final int ERROR_PERMISSION_DENIED =
            IIntrusionDetectionServiceCommandCallback.ErrorCode.PERMISSION_DENIED;
    private static final int ERROR_TRANSPORT_UNAVAILABLE =
            IIntrusionDetectionServiceCommandCallback.ErrorCode.TRANSPORT_UNAVAILABLE;
    private static final int ERROR_DATA_SOURCE_UNAVAILABLE =
            IIntrusionDetectionServiceCommandCallback.ErrorCode.DATA_SOURCE_UNAVAILABLE;


    private Context mContext;
    private IntrusionDetectionEventTransportConnection mIntrusionDetectionEventTransportConnection;
    private DataAggregator mDataAggregator;
    private IntrusionDetectionService mIntrusionDetectionService;
    private IBinder mService;
    private TestLooper mTestLooper;
    private Looper mLooper;
    private TestLooper mTestLooperOfDataAggregator;
    private Looper mLooperOfDataAggregator;
    private FakePermissionEnforcer mPermissionEnforcer;
    private boolean mBoundToLoggingService = false;
    private static final String TEST_PKG = "com.android.coretests.apps.testapp";
    private static final String TEST_SERVICE = TEST_PKG + ".TestLoggingService";

    private ServiceConnection mServiceConnectionForTest;
    private LocalIntrusionDetectionEventTransport mTransportForTest;

    DevicePolicyManagerInternal mDevicePolicyManagerInternal;

    @SuppressLint("VisibleForTests")
    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();

        mPermissionEnforcer = new FakePermissionEnforcer();
        mPermissionEnforcer.grant(READ_INTRUSION_DETECTION_STATE);
        mPermissionEnforcer.grant(MANAGE_INTRUSION_DETECTION_STATE);
        mPermissionEnforcer.grant(BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE);

        mTestLooper = new TestLooper();
        mLooper = mTestLooper.getLooper();
        mTestLooperOfDataAggregator = new TestLooper();
        mLooperOfDataAggregator = mTestLooperOfDataAggregator.getLooper();
        mIntrusionDetectionService = new IntrusionDetectionService(new MockInjector(mContext));
        mIntrusionDetectionService.onStart();
        mTransportForTest = new LocalIntrusionDetectionEventTransport(mContext);
    }

    @After
    public void tearDown() {
        if (mServiceConnectionForTest != null) {
            mContext.unbindService(mServiceConnectionForTest);
            mServiceConnectionForTest = null;
        }
    }

    @Test
    public void testAddStateCallback_NoPermission() {
        mPermissionEnforcer.revoke(READ_INTRUSION_DETECTION_STATE);
        StateCallback scb = new StateCallback();
        assertEquals(STATE_UNKNOWN, scb.mState);
        assertThrows(SecurityException.class,
                () -> mIntrusionDetectionService.getBinderService().addStateCallback(scb));
    }

    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    public void testRemoveStateCallback_NoPermission() {
        mPermissionEnforcer.revoke(READ_INTRUSION_DETECTION_STATE);
        StateCallback scb = new StateCallback();
        assertEquals(STATE_UNKNOWN, scb.mState);
        assertThrows(SecurityException.class,
                () -> mIntrusionDetectionService.getBinderService().removeStateCallback(scb));
    }

    @Test
    public void testEnable_NoPermission() {
        mPermissionEnforcer.revoke(MANAGE_INTRUSION_DETECTION_STATE);

        CommandCallback ccb = new CommandCallback();
        assertThrows(SecurityException.class,
                () -> mIntrusionDetectionService.getBinderService().enable(ccb));
    }

    @Test
    public void testDisable_NoPermission() {
        mPermissionEnforcer.revoke(MANAGE_INTRUSION_DETECTION_STATE);

        CommandCallback ccb = new CommandCallback();
        assertThrows(SecurityException.class,
                () -> mIntrusionDetectionService.getBinderService().disable(ccb));
    }

    @Test
    public void testAddStateCallback_Disabled() throws RemoteException {
        StateCallback scb = new StateCallback();
        assertEquals(STATE_UNKNOWN, scb.mState);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb);
        mTestLooper.dispatchAll();
        assertEquals(STATE_DISABLED, scb.mState);
    }

    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    public void testAddStateCallback_Disabled_TwoStateCallbacks() throws RemoteException {
        StateCallback scb1 = new StateCallback();
        assertEquals(STATE_UNKNOWN, scb1.mState);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb1);
        mTestLooper.dispatchAll();
        assertEquals(STATE_DISABLED, scb1.mState);

        StateCallback scb2 = new StateCallback();
        assertEquals(STATE_UNKNOWN, scb2.mState);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb2);
        mTestLooper.dispatchAll();
        assertEquals(STATE_DISABLED, scb2.mState);
    }

    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    public void testRemoveStateCallback() throws RemoteException {
        mIntrusionDetectionService.setState(STATE_DISABLED);
        StateCallback scb1 = new StateCallback();
        StateCallback scb2 = new StateCallback();
        mIntrusionDetectionService.getBinderService().addStateCallback(scb1);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb2);
        mTestLooper.dispatchAll();
        assertEquals(STATE_DISABLED, scb1.mState);
        assertEquals(STATE_DISABLED, scb2.mState);

        doReturn(true).when(mIntrusionDetectionEventTransportConnection).initialize();

        mIntrusionDetectionService.getBinderService().removeStateCallback(scb2);

        CommandCallback ccb = new CommandCallback();

        // Enable will fail; caller does not run as system server.
        doNothing().when(mDataAggregator).enable();
        mIntrusionDetectionService.getBinderService().enable(ccb);

        mTestLooper.dispatchAll();
        assertEquals(STATE_ENABLED, scb1.mState);
        assertEquals(STATE_DISABLED, scb2.mState);
        assertNull(ccb.mErrorCode);
    }

    @Test
    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    public void testEnable_FromDisabled_TwoStateCallbacks() throws RemoteException {
        mIntrusionDetectionService.setState(STATE_DISABLED);
        StateCallback scb1 = new StateCallback();
        StateCallback scb2 = new StateCallback();
        mIntrusionDetectionService.getBinderService().addStateCallback(scb1);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb2);
        mTestLooper.dispatchAll();
        assertEquals(STATE_DISABLED, scb1.mState);
        assertEquals(STATE_DISABLED, scb2.mState);

        doReturn(true).when(mIntrusionDetectionEventTransportConnection).initialize();

        CommandCallback ccb = new CommandCallback();
        mIntrusionDetectionService.getBinderService().enable(ccb);

        // Enable will fail; caller does not run as system server.
        doNothing().when(mDataAggregator).enable();
        mTestLooper.dispatchAll();

        verify(mDataAggregator, times(1)).enable();
        assertEquals(STATE_ENABLED, scb1.mState);
        assertEquals(STATE_ENABLED, scb2.mState);
        assertNull(ccb.mErrorCode);
    }

    @Test
    public void testEnable_FromEnabled_TwoStateCallbacks()
            throws RemoteException {
        mIntrusionDetectionService.setState(STATE_ENABLED);
        StateCallback scb1 = new StateCallback();
        StateCallback scb2 = new StateCallback();
        mIntrusionDetectionService.getBinderService().addStateCallback(scb1);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb2);
        mTestLooper.dispatchAll();
        assertEquals(STATE_ENABLED, scb1.mState);
        assertEquals(STATE_ENABLED, scb2.mState);

        CommandCallback ccb = new CommandCallback();
        mIntrusionDetectionService.getBinderService().enable(ccb);
        mTestLooper.dispatchAll();

        assertEquals(STATE_ENABLED, scb1.mState);
        assertEquals(STATE_ENABLED, scb2.mState);
        assertNull(ccb.mErrorCode);
    }

    @Test
    public void testDisable_FromDisabled_TwoStateCallbacks() throws RemoteException {
        mIntrusionDetectionService.setState(STATE_DISABLED);
        StateCallback scb1 = new StateCallback();
        StateCallback scb2 = new StateCallback();
        mIntrusionDetectionService.getBinderService().addStateCallback(scb1);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb2);
        mTestLooper.dispatchAll();
        assertEquals(STATE_DISABLED, scb1.mState);
        assertEquals(STATE_DISABLED, scb2.mState);

        CommandCallback ccb = new CommandCallback();
        mIntrusionDetectionService.getBinderService().disable(ccb);
        mTestLooper.dispatchAll();

        assertEquals(STATE_DISABLED, scb1.mState);
        assertEquals(STATE_DISABLED, scb2.mState);
        assertNull(ccb.mErrorCode);
    }

    @Test
    public void testDisable_FromEnabled_TwoStateCallbacks() throws RemoteException {
        mIntrusionDetectionService.setState(STATE_ENABLED);
        StateCallback scb1 = new StateCallback();
        StateCallback scb2 = new StateCallback();
        mIntrusionDetectionService.getBinderService().addStateCallback(scb1);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb2);
        mTestLooper.dispatchAll();
        assertEquals(STATE_ENABLED, scb1.mState);
        assertEquals(STATE_ENABLED, scb2.mState);

        doNothing().when(mIntrusionDetectionEventTransportConnection).release();

        ServiceThread mockThread = spy(ServiceThread.class);
        mDataAggregator.setHandler(mLooperOfDataAggregator, mockThread);

        CommandCallback ccb = new CommandCallback();
        mIntrusionDetectionService.getBinderService().disable(ccb);
        mTestLooper.dispatchAll();
        mTestLooperOfDataAggregator.dispatchAll();
        // TODO: We can verify the data sources once we implement them.
        verify(mockThread, times(1)).quitSafely();
        assertEquals(STATE_DISABLED, scb1.mState);
        assertEquals(STATE_DISABLED, scb2.mState);
        assertNull(ccb.mErrorCode);
    }

    @EnsureHasPermission(CommonPermissions.MANAGE_DEVICE_POLICY_AUDIT_LOGGING)
    @Test
    public void testEnable_FromDisable_TwoStateCallbacks_TransportUnavailable()
            throws RemoteException {
        mIntrusionDetectionService.setState(STATE_DISABLED);
        StateCallback scb1 = new StateCallback();
        StateCallback scb2 = new StateCallback();
        mIntrusionDetectionService.getBinderService().addStateCallback(scb1);
        mIntrusionDetectionService.getBinderService().addStateCallback(scb2);
        mTestLooper.dispatchAll();
        assertEquals(STATE_DISABLED, scb1.mState);
        assertEquals(STATE_DISABLED, scb2.mState);

        doReturn(false).when(mIntrusionDetectionEventTransportConnection).initialize();

        CommandCallback ccb = new CommandCallback();
        mIntrusionDetectionService.getBinderService().enable(ccb);
        mTestLooper.dispatchAll();
        assertEquals(STATE_DISABLED, scb1.mState);
        assertEquals(STATE_DISABLED, scb2.mState);
        assertNotNull(ccb.mErrorCode);
        assertEquals(ERROR_TRANSPORT_UNAVAILABLE, ccb.mErrorCode.intValue());
    }

    @Test
    public void testDataAggregator_AddBatchData() {
        mIntrusionDetectionService.setState(STATE_ENABLED);
        ServiceThread mockThread = spy(ServiceThread.class);
        mDataAggregator.setHandler(mLooperOfDataAggregator, mockThread);

        SecurityEvent securityEvent = new SecurityEvent(0, new byte[0]);
        IntrusionDetectionEvent eventOne =
                IntrusionDetectionEvent.createForSecurityEvent(securityEvent);

        ConnectEvent connectEvent = new ConnectEvent(
                "127.0.0.1", 80, null, 0);
        IntrusionDetectionEvent eventTwo =
                IntrusionDetectionEvent.createForConnectEvent(connectEvent);

        DnsEvent dnsEvent = new DnsEvent(
                null, new String[] {"127.0.0.1"}, 1, null, 0);
        IntrusionDetectionEvent eventThree = IntrusionDetectionEvent.createForDnsEvent(dnsEvent);

        List<IntrusionDetectionEvent> events = new ArrayList<>();
        events.add(eventOne);
        events.add(eventTwo);
        events.add(eventThree);

        doReturn(true).when(mIntrusionDetectionEventTransportConnection).addData(any());

        mDataAggregator.addBatchData(events);
        mTestLooperOfDataAggregator.dispatchAll();
        mTestLooper.dispatchAll();

        ArgumentCaptor<List<IntrusionDetectionEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(mIntrusionDetectionEventTransportConnection).addData(captor.capture());
        List<IntrusionDetectionEvent> receivedEvents = captor.getValue();
        assertEquals(receivedEvents.size(), 3);

        assertEquals(receivedEvents.get(0).getType(), IntrusionDetectionEvent.SECURITY_EVENT);
        assertNotNull(receivedEvents.get(0).getSecurityEvent());

        assertEquals(receivedEvents.get(1).getType(),
                IntrusionDetectionEvent.NETWORK_EVENT_CONNECT);
        assertNotNull(receivedEvents.get(1).getConnectEvent());

        assertEquals(receivedEvents.get(2).getType(), IntrusionDetectionEvent.NETWORK_EVENT_DNS);
        assertNotNull(receivedEvents.get(2).getDnsEvent());
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasPermission(
            android.Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE)
    public void test_serviceConnectionEstablishedSuccessfully() throws InterruptedException {
        assertFalse(mBoundToLoggingService);
        mServiceConnectionForTest = startTestService(mTransportForTest);
        assertNotNull(mServiceConnectionForTest);
        assertTrue(mBoundToLoggingService);
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasPermission(
            android.Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE)
    public void test_transportSuccessfullyProcessesSecurityEvent() throws InterruptedException {
        assertFalse(mBoundToLoggingService);
        mServiceConnectionForTest = startTestService(mTransportForTest);
        assertNotNull(mServiceConnectionForTest);
        assertTrue(mBoundToLoggingService);

        IntrusionDetectionEvent event =
                IntrusionDetectionEvent.createForSecurityEvent(
                        new SecurityEvent(123L, new byte[20]));
        List<IntrusionDetectionEvent> events = new ArrayList<>();
        events.add(event);

        assertTrue(mTransportForTest.initialize());
        assertTrue(mTransportForTest.addData(events));
        assertTrue(mTransportForTest.release());
        assertEquals(1, mTransportForTest.getEvents().size());
        assertEquals(
                IntrusionDetectionEvent.SECURITY_EVENT,
                mTransportForTest.getEvents().get(0).getType());
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasPermission(
            android.Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE)
    public void test_transportSuccessfullyProcessesDnsEvent() throws InterruptedException {
        assertFalse(mBoundToLoggingService);
        mServiceConnectionForTest = startTestService(mTransportForTest);
        assertNotNull(mServiceConnectionForTest);
        assertTrue(mBoundToLoggingService);

        DnsEvent dnsEvent = new DnsEvent("google.com", new String[] {"127.0.0.1"}, 1, null, 0);
        IntrusionDetectionEvent event = IntrusionDetectionEvent.createForDnsEvent(dnsEvent);
        List<IntrusionDetectionEvent> events = new ArrayList<>();
        events.add(event);

        assertTrue(mTransportForTest.initialize());
        assertTrue(mTransportForTest.addData(events));
        assertTrue(mTransportForTest.release());
        assertEquals(1, mTransportForTest.getEvents().size());
        assertEquals(
                IntrusionDetectionEvent.NETWORK_EVENT_DNS,
                mTransportForTest.getEvents().get(0).getType());
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasPermission(
            android.Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE)
    public void test_transportSuccessfullyProcessesConnectEvent() throws InterruptedException {
        assertFalse(mBoundToLoggingService);
        mServiceConnectionForTest = startTestService(mTransportForTest);
        assertNotNull(mServiceConnectionForTest);
        assertTrue(mBoundToLoggingService);

        ConnectEvent connectEvent = new ConnectEvent("127.0.0.1", 80, null, 0);
        IntrusionDetectionEvent event = IntrusionDetectionEvent.createForConnectEvent(connectEvent);
        List<IntrusionDetectionEvent> events = new ArrayList<>();
        events.add(event);

        assertTrue(mTransportForTest.initialize());
        assertTrue(mTransportForTest.addData(events));
        assertTrue(mTransportForTest.release());
        assertEquals(1, mTransportForTest.getEvents().size());
        assertEquals(
                IntrusionDetectionEvent.NETWORK_EVENT_CONNECT,
                mTransportForTest.getEvents().get(0).getType());
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasPermission(
            android.Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE)
    public void test_transportThrows_WhenEventIsCorrupt() throws InterruptedException {
        assertFalse(mBoundToLoggingService);
        mServiceConnectionForTest = startTestService(mTransportForTest);
        assertNotNull(mServiceConnectionForTest);
        assertTrue(mBoundToLoggingService);

        // The buffer size it too small, so this throws an exception.
        IntrusionDetectionEvent event =
                IntrusionDetectionEvent.createForSecurityEvent(
                        new SecurityEvent(123L, new byte[0]));

        List<IntrusionDetectionEvent> events = new ArrayList<>();
        events.add(event);
        assertTrue(mTransportForTest.initialize());
        assertThrows(IndexOutOfBoundsException.class, () -> mTransportForTest.addData(events));
    }

    private ServiceConnection startTestService(LocalIntrusionDetectionEventTransport transport)
            throws SecurityException, InterruptedException {
        final String TAG = "startTestService";
        final CountDownLatch latch = new CountDownLatch(1);

        ServiceConnection serviceConnection =
                new ServiceConnection() {
                    // Called when connection with the service is established.
                    @Override
                    public void onServiceConnected(ComponentName className, IBinder service) {
                        mService = transport.getBinder();
                        mBoundToLoggingService = true;
                        latch.countDown();
                    }

                    // Called when the connection with the service disconnects unexpectedly.
                    @Override
                    public void onServiceDisconnected(ComponentName className) {
                        Log.d(TAG, "onServiceDisconnected");
                        mBoundToLoggingService = false;
                    }
                };

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(TEST_PKG, TEST_SERVICE));
        mContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        latch.await(5, TimeUnit.SECONDS);
        return serviceConnection;
    }

    @Test
    @RequireRunOnSystemUser
    @EnsureHasPermission(
            android.Manifest.permission.BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE)
    public void testIntrusionDetectionEventTransportConnection_isValidAndBinds()
            throws InterruptedException {
        IntrusionDetectionEventTransportConnection intrusionDetectionEventTransportConnection =
                new IntrusionDetectionEventTransportConnection(mContext);
        // In a real scenario, the connection will be initialized by the service.
        // Just to show that the connection is valid and able to bind,
        // we initialize it here.
        assertTrue(intrusionDetectionEventTransportConnection.initialize());
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_initialize_emptyConfig()
            throws Exception {
        Context mockContext = mock(Context.class);
        Resources mockResources = mock(Resources.class);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(anyInt())).thenReturn("");

        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.getServiceInfo(any(ComponentName.class), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());

        IntrusionDetectionEventTransportConnection intrusionDetectionEventTransportConnection =
                new IntrusionDetectionEventTransportConnection(mockContext);
        assertFalse(intrusionDetectionEventTransportConnection.initialize());
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_initialize_invalidComponent()
            throws Exception {
        Context mockContext = mock(Context.class);
        Resources mockResources = mock(Resources.class);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(anyInt())).thenReturn("invalid-component-name");

        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.getServiceInfo(any(ComponentName.class), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());

        IntrusionDetectionEventTransportConnection intrusionDetectionEventTransportConnection =
                new IntrusionDetectionEventTransportConnection(mockContext);
        assertFalse(intrusionDetectionEventTransportConnection.initialize());
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_initialize_serviceWithoutPermission()
            throws Exception {
        String componentNameString = TEST_PKG + "/" + TEST_SERVICE;
        PackageManager packageManager = mock(PackageManager.class);
        ServiceInfo serviceInfo =
                mContext.getPackageManager()
                        .getServiceInfo(new ComponentName(TEST_PKG, TEST_SERVICE), 0);
        serviceInfo.permission = "some.other.permission";
        when(packageManager.getServiceInfo(any(ComponentName.class), anyInt()))
                .thenReturn(serviceInfo);

        Context mockContext = mock(Context.class);
        when(mockContext.getPackageManager()).thenReturn(packageManager);
        Resources mockResources = mock(Resources.class);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(anyInt())).thenReturn(componentNameString);

        IntrusionDetectionEventTransportConnection intrusionDetectionEventTransportConnection =
                new IntrusionDetectionEventTransportConnection(mockContext);
        assertFalse(intrusionDetectionEventTransportConnection.initialize());
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_initialize_serviceNameNotFound()
            throws Exception {
        String invalidComponentName = "com.foo.bar/.NonExistentService";
        Context mockContext = mock(Context.class);
        Resources mockResources = mock(Resources.class);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(anyInt())).thenReturn(invalidComponentName);

        PackageManager packageManager = mock(PackageManager.class);
        when(packageManager.getServiceInfo(any(ComponentName.class), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());
        when(mockContext.getPackageManager()).thenReturn(packageManager);

        IntrusionDetectionEventTransportConnection intrusionDetectionEventTransportConnection =
                new IntrusionDetectionEventTransportConnection(mockContext);
        assertFalse(intrusionDetectionEventTransportConnection.initialize());
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_initialize_bindFails()
            throws Exception {
        Context mockContext = mock(Context.class);
        IntrusionDetectionEventTransportConnection intrusionDetectionEventTransportConnection =
                new IntrusionDetectionEventTransportConnection(mockContext);

        when(mockContext.bindServiceAsUser(
                        any(Intent.class),
                        eq(intrusionDetectionEventTransportConnection),
                        anyInt(),
                        any(UserHandle.class)))
                .thenReturn(false);
        Resources mockResources = mock(Resources.class);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(anyInt())).thenReturn(TEST_PKG + "/.TestLoggingService");

        PackageManager packageManager = mock(PackageManager.class);
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.permission = BIND_INTRUSION_DETECTION_EVENT_TRANSPORT_SERVICE;
        when(packageManager.getServiceInfo(any(ComponentName.class), anyInt()))
                .thenReturn(serviceInfo);
        when(mockContext.getPackageManager()).thenReturn(packageManager);

        assertFalse(intrusionDetectionEventTransportConnection.initialize());
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_addData_success() throws Exception {
        Context mockContext = mock(Context.class);
        IntrusionDetectionEventTransportConnection connection =
                new IntrusionDetectionEventTransportConnection(mockContext);

        IIntrusionDetectionEventTransport mockService =
                mock(IIntrusionDetectionEventTransport.class);
        connection.mService = mockService;

        List<IntrusionDetectionEvent> events = new ArrayList<>();
        events.add(
                IntrusionDetectionEvent.createForSecurityEvent(new SecurityEvent(1, new byte[0])));

        doAnswer(
                        invocation -> {
                            AndroidFuture<Boolean> future = invocation.getArgument(1);
                            future.complete(true);
                            return null;
                        })
                .when(mockService)
                .addData(eq(events), any(AndroidFuture.class));

        boolean result = connection.addData(events);

        assertTrue(result);
        verify(mockService).addData(eq(events), any(AndroidFuture.class));
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_addData_serviceReturnsFalse()
            throws Exception {
        Context mockContext = mock(Context.class);
        IntrusionDetectionEventTransportConnection connection =
                new IntrusionDetectionEventTransportConnection(mockContext);

        IIntrusionDetectionEventTransport mockService =
                mock(IIntrusionDetectionEventTransport.class);
        connection.mService = mockService;

        List<IntrusionDetectionEvent> events = new ArrayList<>();
        doAnswer(
                        invocation -> {
                            AndroidFuture<Boolean> future = invocation.getArgument(1);
                            future.complete(false);
                            return null;
                        })
                .when(mockService)
                .addData(eq(events), any(AndroidFuture.class));

        assertFalse(connection.addData(events));
    }

    @Test
    @RequireRunOnSystemUser
    public void
            testIntrusionDetectionEventTransportConnection_addData_serviceThrowsRemoteException()
                    throws Exception {
        Context mockContext = mock(Context.class);
        IntrusionDetectionEventTransportConnection connection =
                new IntrusionDetectionEventTransportConnection(mockContext);

        IIntrusionDetectionEventTransport mockService =
                mock(IIntrusionDetectionEventTransport.class);
        connection.mService = mockService;

        List<IntrusionDetectionEvent> events = new ArrayList<>();
        doThrow(new RemoteException())
                .when(mockService)
                .addData(eq(events), any(AndroidFuture.class));

        assertFalse(connection.addData(events));
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_addData_reinitializationFails()
            throws Exception {
        Context mockContext = mock(Context.class);
        IntrusionDetectionEventTransportConnection connection =
                new IntrusionDetectionEventTransportConnection(mockContext);
        connection.mService = null;

        Resources mockResources = mock(Resources.class);
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getString(anyInt())).thenReturn(""); // Empty config fails initialize

        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.getServiceInfo(any(ComponentName.class), anyInt()))
                .thenThrow(new PackageManager.NameNotFoundException());

        assertFalse(connection.addData(new ArrayList<>()));
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_release_success() throws Exception {
        Context mockContext = mock(Context.class);
        IntrusionDetectionEventTransportConnection connection =
                new IntrusionDetectionEventTransportConnection(mockContext);

        IIntrusionDetectionEventTransport mockService =
                mock(IIntrusionDetectionEventTransport.class);
        connection.mService = mockService;

        connection.release();

        verify(mockService).release(any(AndroidFuture.class));
        verify(mockContext).unbindService(connection);
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_release_remoteException()
            throws Exception {
        Context mockContext = mock(Context.class);
        IntrusionDetectionEventTransportConnection connection =
                new IntrusionDetectionEventTransportConnection(mockContext);

        IIntrusionDetectionEventTransport mockService =
                mock(IIntrusionDetectionEventTransport.class);
        doThrow(new RemoteException()).when(mockService).release(any(AndroidFuture.class));
        connection.mService = mockService;

        connection.release();

        verify(mockContext).unbindService(connection);
    }

    @Test
    @RequireRunOnSystemUser
    public void testIntrusionDetectionEventTransportConnection_release_serviceNull() {
        Context mockContext = mock(Context.class);
        IntrusionDetectionEventTransportConnection connection =
                new IntrusionDetectionEventTransportConnection(mockContext);

        connection.mService = null;

        connection.release();

        verify(mockContext).unbindService(connection);
    }

    private class MockInjector implements IntrusionDetectionService.Injector {
        private final Context mContext;

        MockInjector(Context context) {
            mContext = context;
        }

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        public PermissionEnforcer getPermissionEnforcer() {
            return mPermissionEnforcer;
        }

        @Override
        public Looper getLooper() {
            return mLooper;
        }

        @Override
        public IntrusionDetectionEventTransportConnection
                getIntrusionDetectionEventransportConnection() {
            mIntrusionDetectionEventTransportConnection =
                    spy(new IntrusionDetectionEventTransportConnection(mContext));
            return mIntrusionDetectionEventTransportConnection;
        }

        @Override
        public DataAggregator getDataAggregator(
                IntrusionDetectionService intrusionDetectionService) {
            mDataAggregator = spy(new DataAggregator(mContext, intrusionDetectionService));
            return mDataAggregator;
        }
    }

    private static class StateCallback extends IIntrusionDetectionServiceStateCallback.Stub {
        int mState = STATE_UNKNOWN;

        @Override
        public void onStateChange(int state) throws RemoteException {
            mState = state;
        }
    }

    private static class CommandCallback extends IIntrusionDetectionServiceCommandCallback.Stub {
        Integer mErrorCode = null;

        public void reset() {
            mErrorCode = null;
        }

        @Override
        public void onSuccess() throws RemoteException {

        }

        @Override
        public void onFailure(int errorCode) throws RemoteException {
            mErrorCode = errorCode;
        }
    }
}
