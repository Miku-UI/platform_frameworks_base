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

package com.android.server.pm.verify.developer;

import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;
import static android.content.pm.PackageInstaller.DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY;
import static android.content.pm.verify.developer.DeveloperVerificationSession.DEVELOPER_VERIFICATION_BYPASSED_REASON_UNSPECIFIED;
import static android.os.Process.INVALID_UID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.SharedLibraryInfo;
import android.content.pm.SigningInfo;
import android.content.pm.VersionedPackage;
import android.content.pm.verify.developer.DeveloperVerificationSession;
import android.content.pm.verify.developer.DeveloperVerificationStatus;
import android.content.pm.verify.developer.IDeveloperVerifierService;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.infra.AndroidFuture;
import com.android.internal.infra.ServiceConnector;
import com.android.server.pm.Computer;
import com.android.server.pm.PackageInstallerSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Presubmit
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DeveloperVerifierControllerTest {
    private static final int TEST_ID = 100;
    private static final int TEST_ID2 = 200;
    private static final String TEST_PACKAGE_NAME = "com.foo";
    private static final Uri TEST_PACKAGE_URI = Uri.parse("test://test");
    private static final SigningInfo TEST_SIGNING_INFO = new SigningInfo();
    private static final SharedLibraryInfo TEST_SHARED_LIBRARY_INFO1 =
            new SharedLibraryInfo("sharedLibPath1", TEST_PACKAGE_NAME,
                    Collections.singletonList("path1"), "sharedLib1", 101,
                    SharedLibraryInfo.TYPE_DYNAMIC, new VersionedPackage(TEST_PACKAGE_NAME, 1),
                    null, null, false);
    private static final SharedLibraryInfo TEST_SHARED_LIBRARY_INFO2 =
            new SharedLibraryInfo("sharedLibPath2", TEST_PACKAGE_NAME,
                    Collections.singletonList("path2"), "sharedLib2", 102,
                    SharedLibraryInfo.TYPE_DYNAMIC,
                    new VersionedPackage(TEST_PACKAGE_NAME, 2), null, null, false);
    private static final String TEST_KEY = "test key";
    private static final String TEST_VALUE = "test value";
    private static final String TEST_FAILURE_MESSAGE = "verification failed!";
    private static final long TEST_REQUEST_START_TIME = 0L;
    private static final long TEST_TIMEOUT_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(1);
    private static final long TEST_MAX_TIMEOUT_DURATION_MILLIS =
            TimeUnit.MINUTES.toMillis(10);
    private static final long TEST_VERIFIER_CONNECTION_TIMEOUT_DURATION_MILLIS =
            TimeUnit.SECONDS.toMillis(10);
    private static final long TEST_PRE_WARM_TIMEOUT_DURATION_MILLIS =
            TimeUnit.MINUTES.toMillis(3);
    private static final long TEST_IDLE_TIMEOUT_MILLIS =
            TimeUnit.SECONDS.toMillis(30);
    private static final int TEST_POLICY = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_CLOSED;

    private final ArrayList<SharedLibraryInfo> mTestDeclaredLibraries = new ArrayList<>();
    private final PersistableBundle mTestExtensionParams = new PersistableBundle();
    private final int mUserId = ActivityManager.getCurrentUser();
    private final int mSecondUserId = mUserId + 1;

    @Mock
    Context mContext;
    @Mock
    Handler mHandler;
    @Mock
    DeveloperVerifierController.Injector mInjector;
    @Mock
    ServiceConnector<IDeveloperVerifierService> mMockServiceConnector;
    @Mock
    IDeveloperVerifierService mMockService;
    @Mock
    ServiceConnector<IDeveloperVerifierService> mMockServiceConnectorSecondaryUser;
    @Mock
    IDeveloperVerifierService mMockServiceSecondaryUser;
    @Mock
    Computer mSnapshot;
    @Mock
    Runnable mOnConnectionEstablished;
    Supplier<Computer> mSnapshotSupplier = () -> mSnapshot;
    @Mock
    PackageInstallerSession.DeveloperVerifierCallback mSessionCallback;
    @Mock
    PackageInstallerSession.DeveloperVerifierCallback mSessionCallbackSecondaryUser;

    private DeveloperVerifierController mDeveloperVerifierController;
    private String mPackageName;
    private int mTestVerificationFlags;

    // Capture all tasks scheduled via Handler
    private final List<Message> mScheduledMessages = new ArrayList<>();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPackageName = this.getClass().getPackageName();
        assertThat(mPackageName).isNotNull();

        final int testUid = InstrumentationRegistry.getInstrumentation().getContext()
                .getApplicationInfo().uid;
        // Mock that the UID of this test becomes the UID of the verifier
        when(mSnapshot.getPackageUidInternal(anyString(), anyLong(), anyInt(), anyInt()))
                .thenReturn(testUid);

        setUpMockRemoteServiceForUser(mInjector, mPackageName, mUserId,
                mMockServiceConnector, mMockService);
        setUpMockRemoteServiceForUser(mInjector, mPackageName, mSecondUserId,
                mMockServiceConnectorSecondaryUser, mMockServiceSecondaryUser);

        when(mInjector.getVerificationRequestTimeoutMillis()).thenReturn(
                TEST_TIMEOUT_DURATION_MILLIS);
        when(mInjector.getMaxVerificationExtendedTimeoutMillis()).thenReturn(
                TEST_MAX_TIMEOUT_DURATION_MILLIS);
        when(mInjector.getVerifierConnectionTimeoutMillis()).thenReturn(
                TEST_VERIFIER_CONNECTION_TIMEOUT_DURATION_MILLIS);
        when(mInjector.getPreWarmedSessionExpirationDurationMillis()).thenReturn(
                TEST_PRE_WARM_TIMEOUT_DURATION_MILLIS);
        when(mInjector.getVerifierAutoDisconnectionAfterIdleMillis()).thenReturn(
                TEST_IDLE_TIMEOUT_MILLIS);
        when(mInjector.getVerifierAutoDisconnectionAfterConnectionMillis()).thenReturn(
                TEST_VERIFIER_CONNECTION_TIMEOUT_DURATION_MILLIS);

        // Mock time forward as the code continues to check for the current time
        when(mInjector.getCurrentTimeMillis())
                .thenReturn(TEST_REQUEST_START_TIME)
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS + 1);

        mTestDeclaredLibraries.add(TEST_SHARED_LIBRARY_INFO1);
        mTestDeclaredLibraries.add(TEST_SHARED_LIBRARY_INFO2);
        mTestExtensionParams.putString(TEST_KEY, TEST_VALUE);
        mDeveloperVerifierController = new DeveloperVerifierController(
                mContext, mHandler, new ComponentName(mPackageName, "testClass"), mInjector);
        assertThat(mDeveloperVerifierController.getVerifierPackageName()).isNotNull();

        mScheduledMessages.clear();
        doAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            Message copy = Message.obtain();
            copy.copyFrom(msg);
            copy.obj = msg.obj;
            copy.setCallback(msg.getCallback());
            mScheduledMessages.add(copy);
            return true;
        }).when(mHandler).sendMessageAtTime(any(), anyLong());

        doAnswer(invocation -> {
            Object token = invocation.getArgument(1);
            mScheduledMessages.removeIf(m -> m.obj == token);
            return null;
        }).when(mInjector).stopTimeoutCountdown(eq(mHandler), any());

        doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            mScheduledMessages.removeIf(m -> m.getCallback() == callback);
            return null;
        }).when(mInjector).removeCallbacks(eq(mHandler), any());
    }

    private static void setUpMockRemoteServiceForUser(
            DeveloperVerifierController.Injector injector,
            String packageName, int userId,
            ServiceConnector<IDeveloperVerifierService> mockConnector,
            IDeveloperVerifierService mockService) {
        when(injector.getRemoteService(eq(packageName), any(), eq(userId), any()))
                .thenReturn(mockConnector);

        when(mockConnector.post(any())).thenAnswer(invocation -> {
            ServiceConnector.VoidJob<IDeveloperVerifierService> job = invocation.getArgument(0);
            if (job != null) {
                job.run(mockService);
            }
            return new AndroidFuture<Void>();
        });
        when(mockConnector.run(any())).thenAnswer(invocation -> {
            ServiceConnector.VoidJob<IDeveloperVerifierService> job = invocation.getArgument(0);
            if (job != null) {
                job.run(mockService);
            }
            return true;
        });
    }

    private Object getReaperToken() {
        for (int i = mScheduledMessages.size() - 1; i >= 0; i--) {
            Message msg = mScheduledMessages.get(i);
            if (msg.getCallback() != null && msg.obj != null && !(msg.obj instanceof Runnable)) {
                return msg.obj;
            }
        }
        return null;
    }

    private Runnable getAutoDisconnectRunnable() {
        for (int i = mScheduledMessages.size() - 1; i >= 0; i--) {
            Message msg = mScheduledMessages.get(i);
            if (msg.getCallback() != null && msg.obj == null) {
                return msg.getCallback();
            }
        }
        return null;
    }


    @Test
    public void testGetVerifierInfo() {
        // Test that the verifier package name is correct
        assertEquals(mPackageName, mDeveloperVerifierController.getVerifierPackageName());
    }

    @Test
    public void testRebindService() throws Exception {
        // Test that rebinding a service doesn't clear the existing connection
        ArgumentCaptor<ServiceConnector.ServiceLifecycleCallbacks> captor = ArgumentCaptor.forClass(
                ServiceConnector.ServiceLifecycleCallbacks.class);
        mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier, mUserId,
                mOnConnectionEstablished);
        verify(mMockServiceConnector).setServiceLifecycleCallbacks(captor.capture());
        captor.getValue().onConnected(mMockService);

        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
    }

    @Test
    public void testVerifierNotInstalledOnUser() {
        // Test that the verifier UID is invalid
        when(mSnapshot.getPackageUidInternal(
                eq(mPackageName), anyLong(), anyInt(), anyInt()
        )).thenReturn(INVALID_UID);
        assertThat(mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier,
                mUserId, mOnConnectionEstablished)).isFalse();
        // Test that notifying the verifier when it's not installed doesn't crash
        mDeveloperVerifierController.notifyPackageNameAvailable(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);
        mDeveloperVerifierController.notifyVerificationCancelled(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);
        mDeveloperVerifierController.notifyVerificationTimeout(-1, mUserId);
        verifyNoMoreInteractions(mMockService);
    }

    @Test
    public void testUnbindService() throws Exception {
        // Test that the verifier is unbound and destroyed
        ArgumentCaptor<ServiceConnector.ServiceLifecycleCallbacks> captor = ArgumentCaptor.forClass(
                ServiceConnector.ServiceLifecycleCallbacks.class);
        assertThat(mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier,
                mUserId, mOnConnectionEstablished)).isTrue();
        verify(mMockServiceConnector).setServiceLifecycleCallbacks(captor.capture());
        ServiceConnector.ServiceLifecycleCallbacks<IDeveloperVerifierService> callbacks =
                captor.getValue();
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false,
                mTestVerificationFlags)).isTrue();
        verify(mMockService, times(1)).onVerificationRequired(
                any(DeveloperVerificationSession.class));
        callbacks.onBinderDied();
        // Test the auto-disconnect job is canceled.
        // 2 invocations, one for onBinderDied and one for onDisconnected.
        verify(mInjector, times(2)).removeCallbacks(eq(mHandler), any(Runnable.class));
        // Test that nothing crashes if the service connection is lost
        mDeveloperVerifierController.notifyPackageNameAvailable(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);
        mDeveloperVerifierController.notifyVerificationCancelled(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);
        mDeveloperVerifierController.notifyVerificationTimeout(TEST_ID, mUserId);
        verifyNoMoreInteractions(mMockService);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false,
                mTestVerificationFlags)).isTrue();
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ true,
                mTestVerificationFlags)).isTrue();
    }

    @Test
    public void testNotifyPackageNameAvailable() throws Exception {
        // Test that the remote service has received the notification
        mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier, mUserId,
                () -> {
                });
        mDeveloperVerifierController.notifyPackageNameAvailable(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);
        verify(mMockService, times(1)).onPackageNameAvailable(eq(TEST_PACKAGE_NAME));
        // Verify reaper started
        Object token = getReaperToken();
        assertThat(token).isNotNull();
    }

    @Test
    public void testNotifyVerificationCancelled() throws Exception {
        // Test that the remote service has received the notification
        mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier, mUserId,
                () -> {
                });
        // Add a pre-warmed session first
        mDeveloperVerifierController.notifyPackageNameAvailable(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);

        mDeveloperVerifierController.notifyVerificationCancelled(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);
        verify(mMockService, times(1)).onVerificationCancelled(eq(TEST_PACKAGE_NAME));

        // Canceling the only pre-warmed session should trigger auto-disconnect
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
    }

    @Test
    public void testPreWarmedSessionTimeout() throws Exception {
        mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier, mUserId,
                () -> {});
        // Mock time for notifyPackageNameAvailable
        when(mInjector.getCurrentTimeMillis())
                .thenReturn(TEST_REQUEST_START_TIME)
                .thenReturn(TEST_REQUEST_START_TIME + TEST_PRE_WARM_TIMEOUT_DURATION_MILLIS + 1);

        mDeveloperVerifierController.notifyPackageNameAvailable(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);

        Object token = getReaperToken();
        assertThat(token).isNotNull();

        // Run the timeout check
        findMessageByToken(token).getCallback().run();

        // After timeout, it should schedule auto-disconnect
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
    }

    @Test
    public void testStartVerificationSession() throws Exception {
        ArgumentCaptor<DeveloperVerificationSession> captor =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false,
                mTestVerificationFlags)).isTrue();
        // Test the auto-disconnect job is canceled when the request is sent out
        verify(mInjector, atLeastOnce()).removeCallbacks(eq(mHandler), any(Runnable.class));
        // Test that the remote service has received the request with correct params
        verify(mMockService).onVerificationRequired(captor.capture());
        DeveloperVerificationSession session = captor.getValue();
        assertThat(session.getId()).isEqualTo(TEST_ID);
        assertThat(session.getInstallSessionId()).isEqualTo(TEST_ID);
        assertThat(session.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(session.getStagedPackageUri()).isEqualTo(TEST_PACKAGE_URI);
        assertThat(session.getSigningInfo().getSigningDetails())
                .isEqualTo(TEST_SIGNING_INFO.getSigningDetails());
        List<SharedLibraryInfo> declaredLibraries = session.getDeclaredLibraries();
        assertThat(declaredLibraries.get(0).toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO1.toString());
        assertThat(declaredLibraries.get(1).toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO2.toString());
        assertThat(session.getExtensionParams().getString(TEST_KEY))
                .isEqualTo(mTestExtensionParams.getString(TEST_KEY));
        if (android.content.pm.Flags.verificationServiceAdb()) {
            assertThat(session.getVerificationFlags()).isEqualTo(mTestVerificationFlags);
        }
    }

    @Test
    public void testNotifyVerificationRetry() throws Exception {
        ArgumentCaptor<DeveloperVerificationSession> captor =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ true,
                mTestVerificationFlags)).isTrue();
        // Test the auto-disconnect job is canceled when the request is sent out
        verify(mInjector, atLeastOnce()).removeCallbacks(eq(mHandler), any(Runnable.class));
        // Test that the remote service has received the request with correct params
        verify(mMockService).onVerificationRetry(captor.capture());
        DeveloperVerificationSession session = captor.getValue();
        assertThat(session.getId()).isEqualTo(TEST_ID);
        assertThat(session.getInstallSessionId()).isEqualTo(TEST_ID);
        assertThat(session.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(session.getStagedPackageUri()).isEqualTo(TEST_PACKAGE_URI);
        assertThat(session.getSigningInfo().getSigningDetails())
                .isEqualTo(TEST_SIGNING_INFO.getSigningDetails());
        List<SharedLibraryInfo> declaredLibraries = session.getDeclaredLibraries();
        assertThat(declaredLibraries.get(0).toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO1.toString());
        assertThat(declaredLibraries.get(1).toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO2.toString());
        assertThat(session.getExtensionParams().getString(TEST_KEY))
                .isEqualTo(mTestExtensionParams.getString(TEST_KEY));
    }

    @Test
    public void testNotifyVerificationTimeout() throws Exception {
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ true,
                mTestVerificationFlags)).isTrue();
        // Test the auto-disconnect job is canceled when the request is sent out
        verify(mInjector, atLeastOnce()).removeCallbacks(eq(mHandler), any(Runnable.class));
        // Test that the remote service has received the notification
        mDeveloperVerifierController.notifyVerificationTimeout(TEST_ID, mUserId);
        verify(mMockService).onVerificationTimeout(eq(TEST_ID));
    }

    private DeveloperVerificationSession setUpSession() throws Exception {
        ArgumentCaptor<DeveloperVerificationSession> captor =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME,
                TEST_PACKAGE_URI, TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY,
                mTestExtensionParams, mSessionCallback, mOnConnectionEstablished,
                /* retry= */ false, mTestVerificationFlags)).isTrue();

        // Ensure connection is established for the session
        ArgumentCaptor<ServiceConnector.ServiceLifecycleCallbacks> lifecycleCaptor =
                ArgumentCaptor.forClass(ServiceConnector.ServiceLifecycleCallbacks.class);
        verify(mMockServiceConnector, atLeastOnce()).setServiceLifecycleCallbacks(
                lifecycleCaptor.capture());
        lifecycleCaptor.getValue().onConnected(mMockService);

        verify(mMockService, atLeastOnce()).onVerificationRequired(captor.capture());
        return captor.getValue();
    }

    @Test
    public void testRequestTimeout() {
        // Mock time forward as the code continues to check for the current time
        when(mInjector.getCurrentTimeMillis())
                .thenReturn(TEST_REQUEST_START_TIME)
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS + 1);

        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false,
                mTestVerificationFlags)).isTrue();

        Object token = getReaperToken();
        assertThat(token).isNotNull();
        findMessageByToken(token).getCallback().run();

        // Test that the session callback is notified of the timeout
        verify(mSessionCallback, times(1)).onTimeout();
        verify(mInjector, atLeastOnce()).getCurrentTimeMillis();
        verify(mInjector, atLeastOnce()).stopTimeoutCountdown(eq(mHandler), any());

        // Test that the countdown to auto-disconnect has started
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
    }

    @Test
    public void testRequestTimeoutWithRetryPass() throws Exception {
        // Mock time forward as the code continues to check for the current time
        when(mInjector.getCurrentTimeMillis())
                .thenReturn(TEST_REQUEST_START_TIME)
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS + 1);

        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false,
                mTestVerificationFlags)).isTrue();

        Object token = getReaperToken();
        assertThat(token).isNotNull();
        findMessageByToken(token).getCallback().run();

        // Test that the session callback is notified of the timeout
        verify(mSessionCallback, times(1)).onTimeout();
        verify(mInjector, atLeastOnce()).stopTimeoutCountdown(eq(mHandler), any());
        // Then retry
        ArgumentCaptor<DeveloperVerificationSession> captor =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ true,
                mTestVerificationFlags)).isTrue();
        // Test that the remote service has received the request for retry
        verify(mMockService).onVerificationRetry(captor.capture());
        DeveloperVerificationSession session = captor.getValue();
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder().setVerified(
                true).build();
        // Test that reporting complete on retry is successful
        session.reportVerificationComplete(status);
        verify(mSessionCallback, times(1)).onVerificationCompleteReceived(
                eq(status), eq(null));
        verify(mInjector, atLeastOnce()).stopTimeoutCountdown(eq(mHandler), any());
    }

    @Test
    public void testRequestIncomplete() throws Exception {
        // Test that the session callback is notified of the incomplete report
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationIncomplete(
                DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN);
        verify(mSessionCallback, times(1)).onVerificationIncompleteReceived(
                eq(DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN));
        verify(mInjector, atLeastOnce()).stopTimeoutCountdown(eq(mHandler), any());

        // Test that the countdown to auto-disconnect has started
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
    }

    @Test
    public void testRequestCompleteWithSuccessWithExtensionResponse() throws Exception {
        // Test that the session callback is notified of the completion report
        final DeveloperVerificationSession session = setUpSession();
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder().setVerified(
                true).build();
        PersistableBundle bundle = new PersistableBundle();
        session.reportVerificationComplete(status, bundle);
        verify(mSessionCallback, times(1)).onVerificationCompleteReceived(
                eq(status), eq(bundle));
        verify(mInjector, atLeastOnce()).stopTimeoutCountdown(eq(mHandler), any());

        // Test that the countdown to auto-disconnect has started
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
    }

    @Test
    public void testRequestCompleteWithFailure() throws Exception {
        // Test that the session callback is notified of the completion report with failure
        final DeveloperVerificationSession session = setUpSession();
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder()
                .setVerified(false)
                .setFailureMessage(TEST_FAILURE_MESSAGE)
                .build();
        session.reportVerificationComplete(status);
        verify(mSessionCallback, times(1)).onVerificationCompleteReceived(
                eq(status), eq(null));
        verify(mInjector, atLeastOnce()).stopTimeoutCountdown(eq(mHandler), any());

        // Test that the countdown to auto-disconnect has started
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
    }

    @Test
    public void testRepeatedRequestCompleteShouldThrow() throws Exception {
        // Test that multiple complete report should throw an exception
        final DeveloperVerificationSession session = setUpSession();
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder().setVerified(
                true).build();
        session.reportVerificationComplete(status);
        // getters should throw after the report
        expectThrows(IllegalStateException.class, session::getTimeoutTime);
        // Report again should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationComplete(status));
    }

    @Test
    public void testRequestBypassedWithIllegalArgumentThrows() throws Exception {
        // Test that bypass reason should be valid
        final DeveloperVerificationSession session = setUpSession();
        // Reason cannot be negative
        expectThrows(IllegalArgumentException.class,
                () -> session.reportVerificationBypassed(-1));
        // Reason cannot be unspecified
        expectThrows(IllegalArgumentException.class,
                () -> session.reportVerificationBypassed(
                        DEVELOPER_VERIFICATION_BYPASSED_REASON_UNSPECIFIED));
    }

    @Test
    public void testRequestedBypassedWithUndefinedReason() throws Exception {
        // Test that bypass reason could be an undefined code (for future extension)
        final DeveloperVerificationSession session = setUpSession();
        int randomReasonCode = 200;
        session.reportVerificationBypassed(randomReasonCode); // random reason code
        verify(mSessionCallback, times(1)).onVerificationBypassedReceived(
                eq(randomReasonCode));
    }

    @Test
    public void testRequestedBypassedWithMaxReasonCode() throws Exception {
        // Test that bypass reason could be the max possible code
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(Integer.MAX_VALUE);
        verify(mSessionCallback, times(1)).onVerificationBypassedReceived(
                eq(Integer.MAX_VALUE));
    }

    @Test
    public void testRequestBypassed() throws Exception {
        // Test that the session callback is notified of the bypass report
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY);
        verify(mSessionCallback, times(1)).onVerificationBypassedReceived(
                eq(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
        verify(mInjector, atLeastOnce()).stopTimeoutCountdown(eq(mHandler), any());

        // Test that the countdown to auto-disconnect has started
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
    }

    @Test
    public void testRequestBypassedAfterRequestCompleteShouldThrow() throws Exception {
        // Test that report bypass after completion report should throw an exception
        final DeveloperVerificationSession session = setUpSession();
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder().setVerified(
                true).build();
        session.reportVerificationComplete(status);
        // Report bypass after reporting complete should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationBypassed(
                DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
    }

    @Test
    public void testRequestBypassedAfterRequestTimeoutShouldThrow() throws Exception {
        // Test that report bypass after session timeout should throw an exception
        // Mock time forward
        when(mInjector.getCurrentTimeMillis())
                .thenReturn(TEST_REQUEST_START_TIME)
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS + 1);

        final DeveloperVerificationSession session = setUpSession();
        Object token = getReaperToken();
        findMessageByToken(token).getCallback().run();

        verify(mSessionCallback, times(1)).onTimeout();
        // Report bypass after session timeout should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationBypassed(
                DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
    }

    @Test
    public void testRequestBypassedAfterRequestIncompleteShouldThrow() throws Exception {
        // Test that report bypass after reporting incomplete should throw an exception
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationIncomplete(
                DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN);
        // Report bypass after reporting incomplete should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationBypassed(
                DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
    }

    @Test
    public void testRequestBypassedAfterRequestBypassedShouldThrow() throws Exception {
        // Test that report bypass twice should throw an exception
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY);
        // Report bypass after reporting bypass should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationBypassed(
                DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
    }

    @Test
    public void testRequestCompletedAfterRequestBypassedShouldThrow() throws Exception {
        // Test that report completion after reporting bypass should throw an exception
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY);
        // Report complete after reporting bypass should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationComplete(
                new DeveloperVerificationStatus.Builder().setVerified(true).build()));
    }

    @Test
    public void testRequestIncompleteAfterRequestBypassedShouldThrow() throws Exception {
        // Test that report incomplete after reporting bypass should throw an exception
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY);
        // Report incomplete after reporting bypass should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationIncomplete(
                DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN));
    }

    @Test
    public void testExtendTimeRemaining() throws Exception {
        // Test that the verifier can request to extend the timeout
        final DeveloperVerificationSession session = setUpSession();
        final long initialTimeoutTime = TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS;
        assertThat(session.getTimeoutTime().toEpochMilli()).isEqualTo(initialTimeoutTime);
        final long extendTimeMillis = TEST_TIMEOUT_DURATION_MILLIS;
        assertThat(session.extendTimeout(Duration.ofMillis(extendTimeMillis)).toMillis())
                .isEqualTo(extendTimeMillis);
        assertThat(session.getTimeoutTime().toEpochMilli())
                .isEqualTo(initialTimeoutTime + extendTimeMillis);
        verify(mSessionCallback, times(1)).onTimeoutExtensionRequested();
    }

    @Test
    public void testExtendTimeExceedsMax() throws Exception {
        // Test that the timeout can be extended to only up to a maximum
        final DeveloperVerificationSession session = setUpSession();
        final long initialTimeoutTime = TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS;
        final long maxTimeoutTime = TEST_REQUEST_START_TIME + TEST_MAX_TIMEOUT_DURATION_MILLIS;
        assertThat(session.getTimeoutTime().toEpochMilli()).isEqualTo(initialTimeoutTime);
        final long extendTimeMillis = TEST_MAX_TIMEOUT_DURATION_MILLIS;
        assertThat(session.extendTimeout(Duration.ofMillis(extendTimeMillis)).toMillis()).isEqualTo(
                TEST_MAX_TIMEOUT_DURATION_MILLIS - TEST_TIMEOUT_DURATION_MILLIS);
        assertThat(session.getTimeoutTime().toEpochMilli()).isEqualTo(maxTimeoutTime);
    }


    @Test
    public void testTimeoutChecksMultipleTimes() {
        // Use a single-element array to bypass the "effectively final" restriction for variables
        // captured in lambda expressions, allowing us to update the mocked time later in the test.
        long[] currentTimeMillis = { TEST_REQUEST_START_TIME };
        // Mock time forward as the code continues to check for the current time
        when(mInjector.getCurrentTimeMillis()).thenAnswer(invocation -> currentTimeMillis[0]);

        currentTimeMillis[0] = TEST_REQUEST_START_TIME;
        mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false,
                mTestVerificationFlags);

        Object token = getReaperToken();
        assertThat(token).isNotNull();

        // Run first check (not timed out)
        currentTimeMillis[0] = TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS - 1000;
        findMessageByToken(token).getCallback().run();

        // Run second check (still not timed out)
        currentTimeMillis[0] = TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS - 100;
        findMessageByToken(token).getCallback().run();

        // Run third check (timed out)
        currentTimeMillis[0] = TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS + 1;
        findMessageByToken(token).getCallback().run();

        verify(mInjector, atLeastOnce()).getCurrentTimeMillis();
        verify(mSessionCallback, times(1)).onTimeout();
    }

    @Test
    public void testPolicyOverride() throws Exception {
        // Test that the session policy can be overridden
        final DeveloperVerificationSession session = setUpSession();
        final int policy = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;
        when(mSessionCallback.onVerificationPolicyOverridden(eq(policy))).thenReturn(true);
        assertThat(session.setPolicy(policy)).isTrue();
        assertThat(session.getPolicy()).isEqualTo(policy);
        verify(mSessionCallback, times(1)).onVerificationPolicyOverridden(eq(policy));
    }

    @Test
    public void testStopAutoDisconnectIsCalledWhenRequestIsDelivered() throws Exception {
        ArgumentCaptor<ServiceConnector.VoidJob> jobCaptor =
                ArgumentCaptor.forClass(ServiceConnector.VoidJob.class);
        when(mMockServiceConnector.post(jobCaptor.capture()))
                .thenReturn(new AndroidFuture<>());

        // Bind and trigger onConnected
        ArgumentCaptor<ServiceConnector.ServiceLifecycleCallbacks> callbacksCaptor =
                ArgumentCaptor.forClass(ServiceConnector.ServiceLifecycleCallbacks.class);
        assertThat(mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier,
                mUserId, mOnConnectionEstablished)).isTrue();
        verify(mMockServiceConnector).setServiceLifecycleCallbacks(callbacksCaptor.capture());
        callbacksCaptor.getValue().onConnected(mMockService);

        // Verify scheduled
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();

        // Start session
        mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false,
                mTestVerificationFlags);

        // Now execute posted job
        jobCaptor.getValue().run(mMockService);

        // Test the auto-disconnect job is canceled when the request is sent out
        verify(mInjector, atLeastOnce()).removeCallbacks(eq(mHandler), any(Runnable.class));
        verify(mMockService).onVerificationRequired(any(DeveloperVerificationSession.class));
    }

    @Test
    public void testAutoDisconnect() throws Exception {
        // Create a pre-warmed session
        mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier, mUserId,
                () -> {});
        mDeveloperVerifierController.notifyPackageNameAvailable(
                TEST_ID, TEST_PACKAGE_NAME, mUserId);

        final DeveloperVerificationSession session = setUpSession();

        // Final completion triggers auto-disconnect schedule because the pre-warmed
        // session was consumed/cleared by the startVerificationSession call.
        session.reportVerificationComplete(
                new DeveloperVerificationStatus.Builder().setVerified(true).build(),
                new PersistableBundle());

        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();
        autoDisconnectRunnable.run();

        // Expect the service unbind is called
        verify(mMockServiceConnector, times(1)).unbind();
    }

    @Test
    public void testAutoDisconnectMultiUser() throws Exception {
        // First send out request for current user
        final DeveloperVerificationSession session = setUpSession();

        // Then send out request for another user
        ArgumentCaptor<DeveloperVerificationSession> captorSecondaryUser =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, /* userId= */ mSecondUserId, TEST_ID2, TEST_PACKAGE_NAME,
                TEST_PACKAGE_URI, TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY,
                mTestExtensionParams, mSessionCallbackSecondaryUser, mOnConnectionEstablished,
                /* retry= */ false, mTestVerificationFlags)).isTrue();

        // Established connection for secondary user
        ArgumentCaptor<ServiceConnector.ServiceLifecycleCallbacks> lifecycleCaptor =
                ArgumentCaptor.forClass(ServiceConnector.ServiceLifecycleCallbacks.class);
        verify(mMockServiceConnectorSecondaryUser, atLeastOnce()).setServiceLifecycleCallbacks(
                lifecycleCaptor.capture());
        lifecycleCaptor.getValue().onConnected(mMockServiceSecondaryUser);

        verify(mMockServiceSecondaryUser).onVerificationRequired(captorSecondaryUser.capture());

        // Set first response to success
        DeveloperVerificationStatus statusSuccess =
                new DeveloperVerificationStatus.Builder().setVerified(true).build();
        session.reportVerificationComplete(statusSuccess, new PersistableBundle());
        verify(mSessionCallback, times(1)).onVerificationCompleteReceived(
                eq(statusSuccess), any(PersistableBundle.class));

        // Test that the countdown to auto-disconnect has started for the current user
        Runnable autoDisconnectRunnable = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnable).isNotNull();

        // Set second response to fail
        DeveloperVerificationStatus statusFailure = new DeveloperVerificationStatus.Builder()
                .setVerified(false).setFailureMessage("Reject").build();
        DeveloperVerificationSession sessionSecondaryUser = captorSecondaryUser.getValue();
        sessionSecondaryUser.reportVerificationComplete(statusFailure, new PersistableBundle());
        verify(mSessionCallbackSecondaryUser, times(1))
                .onVerificationCompleteReceived(eq(statusFailure), any(PersistableBundle.class));

        // Test that the countdown to auto-disconnect has started for the second user
        Runnable autoDisconnectRunnableSecondaryUser = getAutoDisconnectRunnable();
        assertThat(autoDisconnectRunnableSecondaryUser).isNotNull();
    }

    private Message findMessageByToken(Object token) {
        for (int i = mScheduledMessages.size() - 1; i >= 0; i--) {
            Message m = mScheduledMessages.get(i);
            if (m.obj == token) {
                return m;
            }
        }
        return null;
    }
}
