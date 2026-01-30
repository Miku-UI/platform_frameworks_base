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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.annotation.NonNull;
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

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPackageName = this.getClass().getPackageName();
        assertThat(mPackageName).isNotNull();
        // Mock that the UID of this test becomes the UID of the verifier
        when(mSnapshot.getPackageUidInternal(anyString(), anyLong(), anyInt(), anyInt()))
                .thenReturn(InstrumentationRegistry.getInstrumentation().getContext()
                        .getApplicationInfo().uid);
        setUpMockRemoteServiceForUser(mInjector, mPackageName, mUserId,
                mMockServiceConnector, mMockService);
        setUpMockRemoteServiceForUser(mInjector, mPackageName, mSecondUserId,
                mMockServiceConnectorSecondaryUser, mMockServiceSecondaryUser);
        when(mInjector.getVerificationRequestTimeoutMillis()).thenReturn(
                TEST_TIMEOUT_DURATION_MILLIS);
        when(mInjector.getMaxVerificationExtendedTimeoutMillis()).thenReturn(
                TEST_MAX_TIMEOUT_DURATION_MILLIS);
        when(mInjector.getVerifierConnectionTimeoutMillis()).thenReturn(
                TEST_VERIFIER_CONNECTION_TIMEOUT_DURATION_MILLIS
        );
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
    }

    private static void setUpMockRemoteServiceForUser(
            @Mock DeveloperVerifierController.Injector mockInjector,
            @NonNull String remoteServicePackageName, int userId,
            @Mock ServiceConnector<IDeveloperVerifierService> mockServiceConnector,
            @Mock IDeveloperVerifierService mockService) {
        when(mockInjector.getRemoteService(
                eq(remoteServicePackageName), any(Context.class), eq(userId), any(Handler.class)
        )).thenReturn(mockServiceConnector);
        when(mockServiceConnector.post(any(ServiceConnector.VoidJob.class)))
                .thenAnswer(
                        i -> {
                            ((ServiceConnector.VoidJob) i.getArguments()[0]).run(mockService);
                            return new AndroidFuture<>();
                        });
        when(mockServiceConnector.run(any(ServiceConnector.VoidJob.class)))
                .thenAnswer(
                        i -> {
                            ((ServiceConnector.VoidJob) i.getArguments()[0]).run(mockService);
                            return true;
                        });
    }

    @Test
    public void testRebindService() {
        ArgumentCaptor<ServiceConnector.ServiceLifecycleCallbacks> captor = ArgumentCaptor.forClass(
                ServiceConnector.ServiceLifecycleCallbacks.class);
        assertThat(mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier,
                mUserId, mOnConnectionEstablished)).isTrue();
        verify(mMockServiceConnector).setServiceLifecycleCallbacks(captor.capture());
        ServiceConnector.ServiceLifecycleCallbacks<IDeveloperVerifierService> callbacks =
                captor.getValue();
        // Verify that the countdown to auto-disconnect has started
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        callbacks.onConnected(mMockService);
        verify(mOnConnectionEstablished, times(1)).run();
        verify(mInjector, times(1)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        Runnable autoDisconnectRunnable = runnableCaptor.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.getCallback() == autoDisconnectRunnable), anyLong());
    }

    @Test
    public void testVerifierNotInstalledOnUser() {
        when(mSnapshot.getPackageUidInternal(
                eq(mPackageName), anyLong(), anyInt(), anyInt()
        )).thenReturn(INVALID_UID);
        assertThat(mDeveloperVerifierController.bindToVerifierServiceIfNeeded(mSnapshotSupplier,
                mUserId, mOnConnectionEstablished)).isFalse();
        // Test that nothing crashes if the verifier is available even though there's no bound
        mDeveloperVerifierController.notifyPackageNameAvailable(TEST_PACKAGE_NAME, mUserId);
        mDeveloperVerifierController.notifyVerificationCancelled(TEST_PACKAGE_NAME, mUserId);
        mDeveloperVerifierController.notifyVerificationTimeout(-1, mUserId);
        // Since there was no bound, no call is made to the verifier
        verifyNoMoreInteractions(mMockService);
    }

    @Test
    public void testUnbindService() throws Exception {
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
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false)).isTrue();
        verify(mMockService, times(1)).onVerificationRequired(
                any(DeveloperVerificationSession.class));
        callbacks.onBinderDied();
        // Test the auto-disconnect job is canceled.
        // 2 invocations, one for onBinderDied and one for onDisconnected.
        verify(mInjector, times(2)).removeCallbacks(eq(mHandler), any(Runnable.class));
        // Test that nothing crashes if the service connection is lost
        mDeveloperVerifierController.notifyPackageNameAvailable(TEST_PACKAGE_NAME, mUserId);
        mDeveloperVerifierController.notifyVerificationCancelled(TEST_PACKAGE_NAME, mUserId);
        mDeveloperVerifierController.notifyVerificationTimeout(TEST_ID, mUserId);
        verifyNoMoreInteractions(mMockService);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false)).isTrue();
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ true)).isTrue();
        mDeveloperVerifierController.notifyVerificationTimeout(TEST_ID, mUserId);
        verify(mMockService, times(1)).onVerificationTimeout(eq(TEST_ID));
    }

    @Test
    public void testNotifyPackageNameAvailable() throws Exception {
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false)).isTrue();
        mDeveloperVerifierController.notifyPackageNameAvailable(TEST_PACKAGE_NAME, mUserId);
        verify(mMockService).onPackageNameAvailable(eq(TEST_PACKAGE_NAME));
    }

    @Test
    public void testNotifyVerificationCancelled() throws Exception {
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false)).isTrue();
        mDeveloperVerifierController.notifyVerificationCancelled(TEST_PACKAGE_NAME, mUserId);
        verify(mMockService).onVerificationCancelled(eq(TEST_PACKAGE_NAME));
    }

    @Test
    public void testStartVerificationSession() throws Exception {
        final DeveloperVerificationSession session = setUpSession();
        // Test the auto-disconnect job is canceled when the request is sent out
        verify(mInjector, times(1)).removeCallbacks(eq(mHandler), any(Runnable.class));

        assertThat(session.getId()).isEqualTo(TEST_ID);
        assertThat(session.getInstallSessionId()).isEqualTo(TEST_ID);
        assertThat(session.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(session.getStagedPackageUri()).isEqualTo(TEST_PACKAGE_URI);
        assertThat(session.getSigningInfo().getSigningDetails())
                .isEqualTo(TEST_SIGNING_INFO.getSigningDetails());
        List<SharedLibraryInfo> declaredLibraries = session.getDeclaredLibraries();
        // SharedLibraryInfo doesn't have a "equals" method, so we have to check it indirectly
        assertThat(declaredLibraries.getFirst().toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO1.toString());
        assertThat(declaredLibraries.get(1).toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO2.toString());
        // We can't directly test with PersistableBundle.equals() because the parceled bundle's
        // structure is different, but all the key/value pairs should be preserved as before.
        assertThat(session.getExtensionParams().getString(TEST_KEY))
                .isEqualTo(mTestExtensionParams.getString(TEST_KEY));
    }

    @Test
    public void testNotifyVerificationRetry() throws Exception {
        ArgumentCaptor<DeveloperVerificationSession> captor =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ true)).isTrue();
        // Test the auto-disconnect job is canceled when the request is sent out
        verify(mInjector, times(1)).removeCallbacks(eq(mHandler), any(Runnable.class));
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
        // SharedLibraryInfo doesn't have a "equals" method, so we have to check it indirectly
        assertThat(declaredLibraries.getFirst().toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO1.toString());
        assertThat(declaredLibraries.get(1).toString())
                .isEqualTo(TEST_SHARED_LIBRARY_INFO2.toString());
        // We can't directly test with PersistableBundle.equals() because the parceled bundle's
        // structure is different, but all the key/value pairs should be preserved as before.
        assertThat(session.getExtensionParams().getString(TEST_KEY))
                .isEqualTo(mTestExtensionParams.getString(TEST_KEY));
    }

    @Test
    public void testNotifyVerificationTimeout() throws Exception {
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ true)).isTrue();
        // Test the auto-disconnect job is canceled when the request is sent out
        verify(mInjector, times(1)).removeCallbacks(eq(mHandler), any(Runnable.class));
        mDeveloperVerifierController.notifyVerificationTimeout(TEST_ID, mUserId);
        verify(mMockService).onVerificationTimeout(eq(TEST_ID));
    }

    @Test
    public void testRequestTimeout() {
        // Let the mock handler set request to TIMEOUT, immediately after the request is sent.
        // We can't mock postDelayed because it's final, but we can mock the method it calls.
        when(mHandler.sendMessageAtTime(
                argThat(argument -> argument.obj != null), anyLong())).thenAnswer(
                        i -> {
                            ((Message) i.getArguments()[0]).getCallback().run();
                            return true;
                        });
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false)).isTrue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.obj != null), anyLong());
        verify(mSessionCallback, times(1)).onTimeout();
        verify(mInjector, times(2)).getCurrentTimeMillis();
        verify(mInjector, times(1)).stopTimeoutCountdown(eq(mHandler), any());
        // Test that the countdown to auto-disconnect has started
        verify(mInjector, times(2)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        Runnable autoDisconnectRunnable = runnableCaptor.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.getCallback() == autoDisconnectRunnable), anyLong());
    }

    @Test
    public void testRequestTimeoutWithRetryPass() throws Exception {
        // Only let the first request timeout and let the second one pass
        when(mHandler.sendMessageAtTime(argThat(argument -> argument.obj != null), anyLong()))
                .thenAnswer(
                        i -> {
                            ((Message) i.getArguments()[0]).getCallback().run();
                            return true;
                        })
                .thenAnswer(i -> true);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false)).isTrue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.obj != null), anyLong());
        verify(mSessionCallback, times(1)).onTimeout();
        verify(mInjector, times(2)).getCurrentTimeMillis();
        verify(mInjector, times(1)).stopTimeoutCountdown(eq(mHandler), any());
        // Then retry
        ArgumentCaptor<DeveloperVerificationSession> captor =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ true)).isTrue();
        verify(mMockService).onVerificationRetry(captor.capture());
        DeveloperVerificationSession session = captor.getValue();
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder().setVerified(
                true).build();
        session.reportVerificationComplete(status);
        verify(mSessionCallback, times(1)).onVerificationCompleteReceived(
                eq(status), eq(null));
        verify(mInjector, times(2)).stopTimeoutCountdown(eq(mHandler), any());
    }

    @Test
    public void testRequestIncomplete() throws Exception {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationIncomplete(
                DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN);
        verify(mSessionCallback, times(1)).onVerificationIncompleteReceived(
                eq(DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN));
        verify(mInjector, times(1)).stopTimeoutCountdown(eq(mHandler), any());
        // Test that the countdown to auto-disconnect has started
        verify(mInjector, times(2)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        Runnable autoDisconnectRunnable = runnableCaptor.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.getCallback() == autoDisconnectRunnable), anyLong());
    }

    @Test
    public void testRequestCompleteWithSuccessWithExtensionResponse() throws Exception {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        final DeveloperVerificationSession session = setUpSession();
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder().setVerified(
                true).build();
        PersistableBundle bundle = new PersistableBundle();
        session.reportVerificationComplete(status, bundle);
        verify(mSessionCallback, times(1)).onVerificationCompleteReceived(
                eq(status), eq(bundle));
        verify(mInjector, times(1)).stopTimeoutCountdown(eq(mHandler), any());
        // Test that the countdown to auto-disconnect has started
        verify(mInjector, times(2)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        Runnable autoDisconnectRunnable = runnableCaptor.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.getCallback() == autoDisconnectRunnable), anyLong());
    }

    @Test
    public void testRequestCompleteWithFailure() throws Exception {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        final DeveloperVerificationSession session = setUpSession();
        DeveloperVerificationStatus status = new DeveloperVerificationStatus.Builder()
                .setVerified(false)
                .setFailureMessage(TEST_FAILURE_MESSAGE)
                .build();
        session.reportVerificationComplete(status);
        verify(mSessionCallback, times(1)).onVerificationCompleteReceived(
                eq(status), eq(null));
        verify(mInjector, times(1)).stopTimeoutCountdown(eq(mHandler), any());
        // Test that the countdown to auto-disconnect has started
        verify(mInjector, times(2)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        Runnable autoDisconnectRunnable = runnableCaptor.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.getCallback() == autoDisconnectRunnable), anyLong());
    }

    @Test
    public void testRepeatedRequestCompleteShouldThrow() throws Exception {
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
        final DeveloperVerificationSession session = setUpSession();
        int randomReasonCode = 200;
        session.reportVerificationBypassed(randomReasonCode); // random reason code
        verify(mSessionCallback, times(1)).onVerificationBypassedReceived(
                eq(randomReasonCode));
    }

    @Test
    public void testRequestedBypassedWithMaxReasonCode() throws Exception {
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(Integer.MAX_VALUE);
        verify(mSessionCallback, times(1)).onVerificationBypassedReceived(
                eq(Integer.MAX_VALUE));
    }

    @Test
    public void testRequestBypassed() throws Exception {
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY);
        verify(mSessionCallback, times(1)).onVerificationBypassedReceived(
                eq(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
        verify(mInjector, times(1)).stopTimeoutCountdown(eq(mHandler), any());
        // Test that the countdown to auto-disconnect has started
        verify(mInjector, times(2)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        Runnable autoDisconnectRunnable = runnableCaptor.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.getCallback() == autoDisconnectRunnable), anyLong());
    }

    @Test
    public void testRequestBypassedAfterRequestCompleteShouldThrow() throws Exception {
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
        // Let the mock handler set request to TIMEOUT, immediately after the request is sent.
        // We can't mock postDelayed because it's final, but we can mock the method it calls.
        when(mHandler.sendMessageAtTime(
                argThat(argument -> argument.obj != null), anyLong())).thenAnswer(i -> {
                    ((Message) i.getArguments()[0]).getCallback().run();
                    return true;
                });
        final DeveloperVerificationSession session = setUpSession();
        verify(mSessionCallback, times(1)).onTimeout();
        // Report bypass after session timeout should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationBypassed(
                DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
    }

    @Test
    public void testRequestBypassedAfterRequestIncompleteShouldThrow() throws Exception {
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationIncomplete(
                DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN);
        // Report bypass after reporting incomplete should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationBypassed(
                DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
    }

    @Test
    public void testRequestBypassedAfterRequestBypassedShouldThrow() throws Exception {
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY);
        // Report bypass after reporting bypass should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationBypassed(
                DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY));
    }

    @Test
    public void testRequestCompletedAfterRequestBypassedShouldThrow() throws Exception {
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY);
        // Report complete after reporting bypass should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationComplete(
                new DeveloperVerificationStatus.Builder().setVerified(true).build()));
    }

    @Test
    public void testRequestIncompleteAfterRequestBypassedShouldThrow() throws Exception {
        final DeveloperVerificationSession session = setUpSession();
        session.reportVerificationBypassed(DEVELOPER_VERIFICATION_BYPASSED_REASON_EMERGENCY);
        // Report incomplete after reporting bypass should fail with exception
        expectThrows(IllegalStateException.class, () -> session.reportVerificationIncomplete(
                DeveloperVerificationSession.DEVELOPER_VERIFICATION_INCOMPLETE_UNKNOWN));
    }

    private DeveloperVerificationSession setUpSession() throws Exception {
        ArgumentCaptor<DeveloperVerificationSession> captor =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME,
                TEST_PACKAGE_URI, TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY,
                mTestExtensionParams, mSessionCallback, mOnConnectionEstablished,
                /* retry= */ false)).isTrue();
        verify(mMockService).onVerificationRequired(captor.capture());
        return captor.getValue();
    }


    @Test
    public void testExtendTimeRemaining() throws Exception {
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
        // Mock message handling
        when(mHandler.sendMessageAtTime(argThat(argument -> argument.obj != null), anyLong()))
                .thenAnswer(
                        i -> {
                            ((Message) i.getArguments()[0]).getCallback().run();
                            return true;
                        });
        // Mock time forward as the code continues to check for the current time
        when(mInjector.getCurrentTimeMillis())
                // First called when the tracker is created
                .thenReturn(TEST_REQUEST_START_TIME)
                // Then mock the first timeout check when the timeout time isn't reached yet
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS - 1000)
                // Then mock the same time used to check the remaining time
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS - 1000)
                // Then mock the second timeout check when the timeout time isn't reached yet
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS - 100)
                // Then mock the same time used to check the remaining time
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS - 100)
                // Then mock the third timeout check when the timeout time has been reached
                .thenReturn(TEST_REQUEST_START_TIME + TEST_TIMEOUT_DURATION_MILLIS + 1);
        mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false);
        verify(mHandler, times(3)).sendMessageAtTime(
                argThat(argument -> argument.obj != null), anyLong());
        verify(mInjector, times(6)).getCurrentTimeMillis();
        verify(mSessionCallback, times(1)).onTimeout();
    }

    @Test
    public void testPolicyOverride() throws Exception {
        final DeveloperVerificationSession session = setUpSession();
        final int policy = DEVELOPER_VERIFICATION_POLICY_BLOCK_FAIL_OPEN;
        when(mSessionCallback.onVerificationPolicyOverridden(eq(policy))).thenReturn(true);
        assertThat(session.setPolicy(policy)).isTrue();
        assertThat(session.getPolicy()).isEqualTo(policy);
        verify(mSessionCallback, times(1)).onVerificationPolicyOverridden(eq(policy));
    }

    @Test
    public void testAutoDisconnect() throws Exception {
        // Mock message handling for auto-disconnect.
        when(mHandler.sendMessageAtTime(argThat(argument -> argument.obj == null),
                anyLong()))
                .thenAnswer(
                        i -> {
                            ((Message) i.getArguments()[0]).getCallback().run();
                            return true;
                        });
        ArgumentCaptor<DeveloperVerificationSession> sessionCaptor =
                ArgumentCaptor.forClass(DeveloperVerificationSession.class);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        assertThat(mDeveloperVerifierController.startVerificationSession(
                mSnapshotSupplier, mUserId, TEST_ID, TEST_PACKAGE_NAME, TEST_PACKAGE_URI,
                TEST_SIGNING_INFO, mTestDeclaredLibraries, TEST_POLICY, mTestExtensionParams,
                mSessionCallback, mOnConnectionEstablished, /* retry= */ false)).isTrue();
        verify(mMockService).onVerificationRequired(sessionCaptor.capture());
        verify(mInjector, times(1)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        Runnable autoDisconnectRunnable = runnableCaptor.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                messageCaptor.capture(), anyLong());
        // Expect 1 message which is for the request's timeout countdown
        assertThat(messageCaptor.getValue().obj).isNotNull();
        DeveloperVerificationSession session = sessionCaptor.getValue();
        session.reportVerificationComplete(
                new DeveloperVerificationStatus.Builder().setVerified(true).build(),
                new PersistableBundle());
        verify(mInjector, times(2)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        List<Runnable> autoDisconnectRunnables = runnableCaptor.getAllValues();
        assertThat(autoDisconnectRunnable).isEqualTo(autoDisconnectRunnables.getLast());
        // Expect another messages for auto-disconnect
        verify(mHandler, times(2)).sendMessageAtTime(
                messageCaptor.capture(), anyLong());
        assertThat(messageCaptor.getAllValues().getLast().getCallback())
                .isEqualTo(autoDisconnectRunnable);
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
                /* retry= */ false)).isTrue();
        verify(mMockServiceSecondaryUser).onVerificationRequired(captorSecondaryUser.capture());

        // Set first response to success
        DeveloperVerificationStatus statusSuccess =
                new DeveloperVerificationStatus.Builder().setVerified(true).build();
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Object> tokenCaptor = ArgumentCaptor.forClass(Object.class);
        session.reportVerificationComplete(statusSuccess, new PersistableBundle());
        verify(mSessionCallback, times(1)).onVerificationCompleteReceived(
                eq(statusSuccess), any(PersistableBundle.class));
        verify(mInjector, times(1)).stopTimeoutCountdown(eq(mHandler),
                tokenCaptor.capture());
        // Test that the countdown to auto-disconnect has started for the current user
        verify(mInjector, times(3)).removeCallbacks(eq(mHandler),
                runnableCaptor.capture());
        Runnable autoDisconnectRunnable = runnableCaptor.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.getCallback() == autoDisconnectRunnable), anyLong());

        // Set second response to fail
        DeveloperVerificationStatus statusFailure = new DeveloperVerificationStatus.Builder()
                .setVerified(false).setFailureMessage("Reject").build();
        ArgumentCaptor<Runnable> runnableCaptorSecondaryUser =
                ArgumentCaptor.forClass(Runnable.class);
        DeveloperVerificationSession sessionSecondaryUser = captorSecondaryUser.getValue();
        sessionSecondaryUser.reportVerificationComplete(statusFailure, new PersistableBundle());
        verify(mSessionCallbackSecondaryUser, times(1))
                .onVerificationCompleteReceived(eq(statusFailure), any(PersistableBundle.class));
        verify(mInjector, times(2)).stopTimeoutCountdown(eq(mHandler),
                tokenCaptor.capture());
        List<Object> allTokens = tokenCaptor.getAllValues();
        DeveloperVerificationRequestStatusTracker
                tracker1 = (DeveloperVerificationRequestStatusTracker) allTokens.getFirst();
        DeveloperVerificationRequestStatusTracker
                tracker2 = (DeveloperVerificationRequestStatusTracker) allTokens.getLast();
        assertThat(tracker1.getUserId()).isEqualTo(mUserId);
        assertThat(tracker2.getUserId()).isEqualTo(mSecondUserId);
        // Test that the countdown to auto-disconnect has started for the second user
        verify(mInjector, times(4)).removeCallbacks(eq(mHandler),
                runnableCaptorSecondaryUser.capture());
        Runnable autoDisconnectRunnableSecondaryUser = runnableCaptorSecondaryUser.getValue();
        verify(mHandler, times(1)).sendMessageAtTime(
                argThat(argument -> argument.getCallback() == autoDisconnectRunnableSecondaryUser),
                anyLong());
    }
}
