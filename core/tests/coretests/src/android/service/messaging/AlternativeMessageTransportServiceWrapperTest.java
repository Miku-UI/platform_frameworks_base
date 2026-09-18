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

package android.service.messaging;

import static android.service.messaging.AlternativeMessageTransportService.UPGRADE_STATUS_REJECTED;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class AlternativeMessageTransportServiceWrapperTest {

    private static final String TEST_PACKAGE = "com.android.test.messaging";
    private static final Uri TEST_URI = Uri.parse("content://sms/123");
    private static final ComponentName TEST_COMPONENT =
            new ComponentName(TEST_PACKAGE, "TestService");
    private static final long SERVICE_BIND_TIMEOUT = 10L;

    @Mock private Context mContext;
    @Mock private ScheduledExecutorService mMockScheduler;
    @Mock private Consumer<Integer> mMockClientCallback;
    @Mock private IAlternativeMessageTransportService mMockAidlService;
    @Mock private IBinder mMockBinder;

    private AutoCloseable mMockCloseable;
    private AlternativeMessageTransportServiceWrapper mWrapper;

    // Synchronous executor to run callbacks immediately
    private final Executor mSyncExecutor = Runnable::run;

    @Before
    public void setUp() throws Exception {
        mMockCloseable = MockitoAnnotations.openMocks(this);
        when(mContext.getUser()).thenReturn(UserHandle.of(10));

        mWrapper = new AlternativeMessageTransportServiceWrapper(mContext, mMockScheduler);

        // Mock the AIDL interface
        when(mMockBinder.queryLocalInterface(anyString())).thenReturn(mMockAidlService);
    }

    @After
    public void tearDown() throws Exception {
        if (mWrapper != null) {
            mWrapper.close();
        }
        if (mMockCloseable != null) {
            mMockCloseable.close();
        }
    }

    // =========================================================================
    // Input Validation
    // =========================================================================

    @Test
    @SmallTest
    @SuppressWarnings("ConstantConditions")
    public void testUpgradeMessage_nullArguments_throwsExceptions() {
        assertThrows(NullPointerException.class, () ->
                mWrapper.upgradeMessage(null, TEST_PACKAGE, mSyncExecutor, mMockClientCallback));

        assertThrows(NullPointerException.class, () ->
                mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, null, mMockClientCallback));

        assertThrows(NullPointerException.class, () ->
                mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, mSyncExecutor, null));

        assertThrows(IllegalArgumentException.class, () ->
                mWrapper.upgradeMessage(TEST_URI, "", mSyncExecutor, mMockClientCallback));

        assertThrows(IllegalArgumentException.class, () ->
                mWrapper.upgradeMessage(TEST_URI, null, mSyncExecutor, mMockClientCallback));
    }

    // =========================================================================
    // Binding & Execution Tests
    // =========================================================================

    @Test
    @SmallTest
    public void testUpgradeMessage_bindFails_invokesCallbackWithRejected() {
        when(mContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class))).thenReturn(false);

        mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, mSyncExecutor, mMockClientCallback);

        // Verify bind was attempted
        verify(mContext).bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE), any(UserHandle.class));

        // Verify failure callback executed
        verify(mMockClientCallback).accept(UPGRADE_STATUS_REJECTED);
        verify(mMockScheduler, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    @SmallTest
    public void testUpgradeMessage_bindSucceeds_schedulesTimeoutAndWaits() {
        when(mContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class))).thenReturn(true);

        mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, mSyncExecutor, mMockClientCallback);

        // Should schedule the timeout closure
        verify(mMockScheduler).schedule(any(Runnable.class), eq(SERVICE_BIND_TIMEOUT),
                eq(TimeUnit.SECONDS));
        // Verify we are not invoking the callback prematurely
        verify(mMockClientCallback, never()).accept(anyInt());
    }

    @Test
    @SmallTest
    public void testServiceConnected_invokesAidlMethod() throws RemoteException {
        when(mContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class))).thenReturn(true);

        mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, mSyncExecutor, mMockClientCallback);

        // Capture the ServiceConnection
        ArgumentCaptor<ServiceConnection> captor = ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mContext).bindServiceAsUser(any(Intent.class), captor.capture(),
                anyInt(), any(UserHandle.class));
        ServiceConnection connection = captor.getValue();

        // Simulate service connection
        connection.onServiceConnected(TEST_COMPONENT, mMockBinder);

        // Verify the AIDL interface was called
        verify(mMockAidlService).upgradeMessage(eq(TEST_URI), any(IMessageUpgradeCallback.class));
    }

    @Test
    @SmallTest
    public void testRemoteExceptionDuringUpgrade_invokesCallbackWithRejected()
            throws RemoteException {
        when(mContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class))).thenReturn(true);
        when(mMockBinder.queryLocalInterface(anyString())).thenReturn(mMockAidlService);

        // Throw exception when the wrapper calls the AIDL method
        org.mockito.Mockito.doThrow(new RemoteException("Test Exception"))
                .when(mMockAidlService).upgradeMessage(any(), any());

        mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, mSyncExecutor, mMockClientCallback);

        ArgumentCaptor<ServiceConnection> captor = ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mContext).bindServiceAsUser(any(Intent.class), captor.capture(),
                anyInt(), any(UserHandle.class));
        ServiceConnection connection = captor.getValue();
        connection.onServiceConnected(TEST_COMPONENT, mMockBinder);

        verify(mMockClientCallback).accept(UPGRADE_STATUS_REJECTED);
    }

    // =========================================================================
    // Lifecycle & Cleanup Tests
    // =========================================================================

    @Test
    @SmallTest
    public void testClose_unbindsServiceAndClearsState() {
        when(mContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class))).thenReturn(true);

        mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, mSyncExecutor, mMockClientCallback);

        ArgumentCaptor<ServiceConnection> captor = ArgumentCaptor.forClass(ServiceConnection.class);
        verify(mContext).bindServiceAsUser(any(), captor.capture(), anyInt(), any());

        mWrapper.close();

        // Verify unbind was called
        verify(mContext).unbindService(captor.getValue());
    }

    @Test
    @SmallTest
    public void testScheduleServiceClose_cancelsPreviousFuture() {
        ScheduledFuture mockFuture = mock(ScheduledFuture.class);
        when(mContext.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class))).thenReturn(true);
        when(mMockScheduler.schedule(any(Runnable.class), anyLong(), any()))
                .thenReturn(mockFuture);

        // Call twice to trigger the cancellation of the first future
        mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, mSyncExecutor, mMockClientCallback);
        mWrapper.upgradeMessage(TEST_URI, TEST_PACKAGE, mSyncExecutor, mMockClientCallback);

        verify(mockFuture).cancel(false);
    }
}
