/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.media.projection;


import static android.Manifest.permission.RECORD_SENSITIVE_CONTENT;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
import static android.media.projection.MediaProjectionManager.TYPE_MIRRORING;
import static android.media.projection.MediaProjectionManager.TYPE_SCREEN_CAPTURE;
import static android.media.projection.ReviewGrantedConsentResult.RECORD_CANCEL;
import static android.media.projection.ReviewGrantedConsentResult.RECORD_CONTENT_DISPLAY;
import static android.media.projection.ReviewGrantedConsentResult.RECORD_CONTENT_TASK;
import static android.media.projection.ReviewGrantedConsentResult.UNKNOWN;
import static android.provider.Settings.Global.DISABLE_SCREEN_SHARE_PROTECTIONS_FOR_APPS_AND_NOTIFICATIONS;
import static android.view.ContentRecordingSession.TARGET_UID_FULL_SCREEN;
import static android.view.ContentRecordingSession.TARGET_UID_UNKNOWN;
import static android.view.ContentRecordingSession.createDisplaySession;
import static android.view.ContentRecordingSession.createTaskSession;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManagerInternal;
import android.app.ActivityOptions.LaunchCookie;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ApplicationInfoFlags;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.media.projection.IAppContentProjectionCallback;
import android.media.projection.IAppContentProjectionSession;
import android.media.projection.IMediaProjection;
import android.media.projection.IMediaProjectionCallback;
import android.media.projection.IMediaProjectionWatcherCallback;
import android.media.projection.ReviewGrantedConsentResult;
import android.media.projection.StopReason;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.testing.TestableContext;
import android.view.ContentRecordingSession;
import android.view.ContentRecordingSession.RecordContent;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.media.projection.flags.Flags;
import com.android.server.LocalServices;
import com.android.server.testutils.OffsettableClock;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the {@link MediaProjectionManagerService} class.
 * <p>
 * Build/Install/Run:
 * atest FrameworksServicesTests:MediaProjectionManagerServiceTest
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
@SuppressLint({"UseCheckPermission", "VisibleForTests", "MissingPermission"})
public class MediaProjectionManagerServiceTest {
    private static final int UID = Process.myUid();
    private static final String PACKAGE_NAME = "test.package";
    private final ApplicationInfo mAppInfo = new ApplicationInfo();
    private final PackageInfo mPackageInfo = new PackageInfo();
    private final TestLooper mTestLooper = new TestLooper();
    private static final ContentRecordingSession DISPLAY_SESSION =
            createDisplaySession(DEFAULT_DISPLAY);
    // Callback registered by an app on a MediaProjection instance.
    private final FakeIMediaProjectionCallback mIMediaProjectionCallback =
            new FakeIMediaProjectionCallback();

    private final MediaProjectionManagerService.Injector mPreventReusedTokenEnabledInjector =
            new MediaProjectionManagerService.Injector() {
                @Override
                boolean shouldMediaProjectionPreventReusingConsent(
                        MediaProjectionManagerService.MediaProjection projection) {
                    return true;
                }
            };

    private final MediaProjectionManagerService.Injector mPreventReusedTokenDisabledInjector =
            new MediaProjectionManagerService.Injector() {
                @Override
                boolean shouldMediaProjectionPreventReusingConsent(
                        MediaProjectionManagerService.MediaProjection projection) {
                    return false;
                }
            };

    private final MediaProjectionManagerService.Injector mTestLooperInjector =
            new MediaProjectionManagerService.Injector() {
                @Override
                Looper createCallbackLooper() {
                    return mTestLooper.getLooper();
                }
            };

    private final MediaProjectionManagerService.Injector mMediaProjectionMetricsLoggerInjector =
            new MediaProjectionManagerService.Injector() {
                @Override
                MediaProjectionMetricsLogger mediaProjectionMetricsLogger(Context context) {
                    return mMediaProjectionMetricsLogger;
                }
            };

    @Rule
    public final TestableContext mContext = spy(
            new TestableContext(InstrumentationRegistry.getInstrumentation().getContext()));

    private MediaProjectionManagerService mService;
    private OffsettableClock mClock;
    private ContentRecordingSession mWaitingDisplaySession =
            createDisplaySession(DEFAULT_DISPLAY);

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private ActivityManagerInternal mAmInternal;
    @Mock
    private WindowManagerInternal mWindowManagerInternal;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private KeyguardManager mKeyguardManager;

    private AppOpsManager mAppOpsManager;
    @Mock
    private IMediaProjectionWatcherCallback mWatcherCallback;
    @Mock private MediaProjectionMetricsLogger mMediaProjectionMetricsLogger;
    @Captor
    private ArgumentCaptor<ContentRecordingSession> mSessionCaptor;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mWatcherCallback.asBinder()).thenReturn(new Binder());

        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.addService(ActivityManagerInternal.class, mAmInternal);
        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.addService(WindowManagerInternal.class, mWindowManagerInternal);

        mAppOpsManager = mockAppOpsManager();
        mContext.addMockSystemService(AppOpsManager.class, mAppOpsManager);
        mContext.addMockSystemService(KeyguardManager.class, mKeyguardManager);
        mContext.setMockPackageManager(mPackageManager);

        mClock = new OffsettableClock.Stopped();
        mWaitingDisplaySession.setWaitingForConsent(true);
        mWaitingDisplaySession.setVirtualDisplayId(5);

        mAppInfo.targetSdkVersion = 32;
        mAppInfo.packageName = PACKAGE_NAME;

        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        doReturn(UID).when(mPackageManager).getPackageUidAsUser(anyString(), any(int.class));

        mService = new MediaProjectionManagerService(mContext);
    }

    private static AppOpsManager mockAppOpsManager() {
        return mock(AppOpsManager.class, invocationOnMock -> {
            if (invocationOnMock.getMethod().getName().startsWith("noteOp")) {
                // Mockito will return 0 for non-stubbed method which corresponds to MODE_ALLOWED
                // and is not what we want.
                return AppOpsManager.MODE_IGNORED;
            }
            return Answers.RETURNS_DEFAULTS.answer(invocationOnMock);
        });
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.removeServiceForTest(WindowManagerInternal.class);
    }

    @Test
    public void testGetActiveProjectionInfoInternal() throws NameNotFoundException {
        assertThat(mService.getActiveProjectionInfo()).isNull();

        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();

        // Create a projection, active is still null.
        assertThat(projection).isNotNull();
        assertThat(mService.getActiveProjectionInfo()).isNull();

        // Start the projection, active is now not null.
        projection.start(mIMediaProjectionCallback);
        assertThat(mService.getActiveProjectionInfo()).isNotNull();
    }

    @Test
    public void testCreateProjection() throws NameNotFoundException {
        // Create a first projection.
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        // We are allowed to create a new projection.
        MediaProjectionManagerService.MediaProjection secondProjection =
                startProjectionPreconditions();

        // This is a new projection.
        assertThat(secondProjection).isNotNull();
        assertThat(secondProjection).isNotEqualTo(projection);
    }

    @Test
    public void testCreateProjection_priorProjectionGrant() throws
            NameNotFoundException, InterruptedException {
        // Create a first projection.
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        FakeIMediaProjectionCallback callback1 = new FakeIMediaProjectionCallback();
        projection.start(callback1);

        // Create a second projection.
        MediaProjectionManagerService.MediaProjection secondProjection =
                startProjectionPreconditions();
        FakeIMediaProjectionCallback callback2 = new FakeIMediaProjectionCallback();
        secondProjection.start(callback2);

        // Check that the first projection get stopped, but not the second projection.
        final int timeout = 5;
        boolean stoppedCallback1 = callback1.mLatch.await(timeout, TimeUnit.SECONDS);
        boolean stoppedCallback2 = callback2.mLatch.await(timeout, TimeUnit.SECONDS);

        assertThat(stoppedCallback1).isTrue();
        assertThat(stoppedCallback2).isFalse();
    }

    @Test
    public void testCreateProjection_keyguardLocked() throws Exception {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();

        doReturn(true).when(mKeyguardManager).isKeyguardLocked();
        doReturn(PackageManager.PERMISSION_DENIED).when(mPackageManager)
                .checkPermission(RECORD_SENSITIVE_CONTENT, projection.packageName);
        projection.start(mIMediaProjectionCallback);
        projection.notifyVirtualDisplayCreated(10);

        assertThat(mService.getActiveProjectionInfo()).isNull();
        assertThat(mIMediaProjectionCallback.mLatch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testCreateProjection_keyguardLocked_packageAllowlisted()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();

        doReturn(true).when(mKeyguardManager).isKeyguardLocked();
        doReturn(PackageManager.PERMISSION_GRANTED).when(mPackageManager)
                .checkPermission(RECORD_SENSITIVE_CONTENT, projection.packageName);
        projection.start(mIMediaProjectionCallback);
        projection.notifyVirtualDisplayCreated(10);

        // The projection was started because it was allowed to capture the keyguard.
        assertThat(mService.getActiveProjectionInfo()).isNotNull();
    }

    @Test
    public void testCreateProjection_keyguardLocked_AppOpMediaProjection()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager)
                .noteOpNoThrow(eq(AppOpsManager.OP_PROJECT_MEDIA),
                        eq(projection.uid), eq(projection.packageName), nullable(String.class),
                        nullable(String.class));
        doReturn(true).when(mKeyguardManager).isKeyguardLocked();

        doReturn(PackageManager.PERMISSION_DENIED).when(mPackageManager).checkPermission(
                RECORD_SENSITIVE_CONTENT, projection.packageName);

        projection.start(mIMediaProjectionCallback);
        projection.notifyVirtualDisplayCreated(10);

        // The projection was started because it was allowed to capture the keyguard.
        assertThat(mService.getActiveProjectionInfo()).isNotNull();
    }

    @Test
    public void testCreateProjection_keyguardLocked_screenshareProtectionsDisabled()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        int value = Settings.Global.getInt(mContext.getContentResolver(),
                DISABLE_SCREEN_SHARE_PROTECTIONS_FOR_APPS_AND_NOTIFICATIONS, 0);
        try {
            Settings.Global.putInt(mContext.getContentResolver(),
                    DISABLE_SCREEN_SHARE_PROTECTIONS_FOR_APPS_AND_NOTIFICATIONS, 1);
            doReturn(true).when(mKeyguardManager).isKeyguardLocked();

            doReturn(PackageManager.PERMISSION_DENIED).when(mPackageManager).checkPermission(
                    RECORD_SENSITIVE_CONTENT, projection.packageName);

            projection.start(mIMediaProjectionCallback);
            projection.notifyVirtualDisplayCreated(10);

            // The projection was started because it was allowed to capture the keyguard.
            assertThat(mService.getActiveProjectionInfo()).isNotNull();
        } finally {
            Settings.Global.putInt(mContext.getContentResolver(),
                    DISABLE_SCREEN_SHARE_PROTECTIONS_FOR_APPS_AND_NOTIFICATIONS, value);
        }
    }

    @Test
    public void testCreateProjection_keyguardLocked_noDisplayCreated()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        doReturn(true).when(mKeyguardManager).isKeyguardLocked();

        doReturn(PackageManager.PERMISSION_DENIED).when(mPackageManager).checkPermission(
                RECORD_SENSITIVE_CONTENT, projection.packageName);

        projection.start(mIMediaProjectionCallback);

        // The projection was started because it was allowed to capture the keyguard.
        assertThat(mService.getActiveProjectionInfo()).isNotNull();
    }

    @EnableFlags(android.companion.virtualdevice.flags
            .Flags.FLAG_MEDIA_PROJECTION_KEYGUARD_RESTRICTIONS)
    @Test
    public void testCreateProjection_keyguardLocked_screenshareProtectionsDisabled()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        int value = Settings.Global.getInt(mContext.getContentResolver(),
                DISABLE_SCREEN_SHARE_PROTECTIONS_FOR_APPS_AND_NOTIFICATIONS, 0);
        try {
            Settings.Global.putInt(mContext.getContentResolver(),
                    DISABLE_SCREEN_SHARE_PROTECTIONS_FOR_APPS_AND_NOTIFICATIONS, 1);
            doReturn(true).when(mKeyguardManager).isKeyguardLocked();

            doReturn(PackageManager.PERMISSION_DENIED).when(mPackageManager).checkPermission(
                    RECORD_SENSITIVE_CONTENT, projection.packageName);

            projection.start(mIMediaProjectionCallback);
            projection.notifyVirtualDisplayCreated(10);

            // The projection was started because it was allowed to capture the keyguard.
            assertThat(mService.getActiveProjectionInfo()).isNotNull();
        } finally {
            Settings.Global.putInt(mContext.getContentResolver(),
                    DISABLE_SCREEN_SHARE_PROTECTIONS_FOR_APPS_AND_NOTIFICATIONS, value);
        }
    }

    @EnableFlags(android.companion.virtualdevice.flags
            .Flags.FLAG_MEDIA_PROJECTION_KEYGUARD_RESTRICTIONS)
    @Test
    public void testCreateProjection_keyguardLocked_noDisplayCreated()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        doReturn(true).when(mKeyguardManager).isKeyguardLocked();

        doReturn(PackageManager.PERMISSION_DENIED).when(mPackageManager).checkPermission(
                RECORD_SENSITIVE_CONTENT, projection.packageName);

        projection.start(mIMediaProjectionCallback);

        // The projection was started because it was allowed to capture the keyguard.
        assertThat(mService.getActiveProjectionInfo()).isNotNull();
    }

    @Test
    public void testCreateProjection_attemptReuse_noPriorProjectionGrant()
            throws NameNotFoundException {
        // Create a first projection.
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        // We are not allowed to retrieve the prior projection, since we are not waiting for the
        // user's consent.
        assertThat(startReusedProjectionPreconditions()).isNull();
    }

    @Test
    public void testCreateProjection_attemptReuse_priorProjectionGrant_notWaiting()
            throws NameNotFoundException {
        // Create a first projection.
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        // Mark this projection as not waiting for the user to review consent.
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        mService.setContentRecordingSession(DISPLAY_SESSION);

        // We are not allowed to retrieve the prior projection, since we are not waiting for the
        // user's consent.
        assertThat(startReusedProjectionPreconditions()).isNull();
    }

    @Test
    public void testCreateProjection_attemptReuse_priorProjectionGrant_waiting()
            throws NameNotFoundException {
        // Create a first projection.
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        // Mark this projection as waiting for the user to review consent.
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        mService.setContentRecordingSession(mWaitingDisplaySession);

        // We are allowed to create another projection, reusing a prior grant if necessary.
        MediaProjectionManagerService.MediaProjection secondProjection =
                startReusedProjectionPreconditions();

        // This is a new projection, since we are waiting for the user's consent; simply provide
        // the projection grant from before.
        assertThat(secondProjection).isNotNull();
        assertThat(secondProjection).isEqualTo(projection);
    }

    @Test
    public void testCreateProjection_attemptReuse_priorProjectionGrant_waiting_differentPackage()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        // Mark this projection as not waiting.
        mService.setContentRecordingSession(mWaitingDisplaySession);

        // We are allowed to create another projection.
        MediaProjectionManagerService.MediaProjection secondProjection =
                mService.createProjectionInternal(
                        UID + 10,
                        PACKAGE_NAME + "foo",
                        TYPE_MIRRORING,
                        /* isPermanentGrant= */ true,
                        Display.DEFAULT_DISPLAY);

        assertThat(secondProjection).isNotNull();

        // But this is a new projection.
        assertThat(secondProjection).isNotEqualTo(projection);
    }

    @Test
    public void testReuseProjection_keyguardNotLocked_startConsentDialog()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        doNothing().when(mContext).startActivityAsUser(any(), any());
        doReturn(false).when(mKeyguardManager).isKeyguardLocked();

        MediaProjectionManagerService.BinderService mediaProjectionBinderService =
                mService.new BinderService(mContext);
        mediaProjectionBinderService.requestConsentForInvalidProjection(projection);

        verify(mContext).startActivityAsUser(any(), any());
    }

    @Test
    public void testReuseProjection_keyguardLocked_noConsentDialog() throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        doReturn(PackageManager.PERMISSION_DENIED).when(mPackageManager).checkPermission(
                RECORD_SENSITIVE_CONTENT, projection.packageName);
        doReturn(true).when(mKeyguardManager).isKeyguardLocked();
        MediaProjectionManagerService.BinderService mediaProjectionBinderService =
                mService.new BinderService(mContext);
        mediaProjectionBinderService.requestConsentForInvalidProjection(projection);

        verify(mContext, never()).startActivityAsUser(any(), any());
    }

    @Test
    public void stop_noActiveProjections_doesNotLog() throws Exception {
        MediaProjectionManagerService service =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);
        MediaProjectionManagerService.MediaProjection projection =
                startProjectionPreconditions(service);

        projection.stop(StopReason.STOP_UNKNOWN);

        verifyNoMoreInteractions(mMediaProjectionMetricsLogger);
    }

    @Test
    public void stop_noSession_logsHostUidAndUnknownTargetUid() throws Exception {
        MediaProjectionManagerService service =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);
        MediaProjectionManagerService.MediaProjection projection =
                startProjectionPreconditions(service);
        projection.start(mIMediaProjectionCallback);

        final @StopReason int stopReason = StopReason.STOP_UNKNOWN;
        projection.stop(stopReason);

        verify(mMediaProjectionMetricsLogger).logStopped(UID, TARGET_UID_UNKNOWN, stopReason);
    }

    @Test
    public void stop_displaySession_logsHostUidAndUnknownTargetUidFullScreen() throws Exception {
        MediaProjectionManagerService service =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);
        MediaProjectionManagerService.MediaProjection projection =
                startProjectionPreconditions(service);
        projection.start(mIMediaProjectionCallback);
        doReturn(true)
                .when(mWindowManagerInternal)
                .setContentRecordingSession(any(ContentRecordingSession.class));
        service.setContentRecordingSession(DISPLAY_SESSION);

        final @StopReason int stopReason = StopReason.STOP_UNKNOWN;
        projection.stop(stopReason);

        verify(mMediaProjectionMetricsLogger).logStopped(UID, TARGET_UID_FULL_SCREEN, stopReason);
    }

    @Test
    public void stop_taskSession_logsHostUidAndTargetUid() throws Exception {
        int targetUid = 1234;
        int stopReason = StopReason.STOP_UNKNOWN;
        MediaProjectionManagerService service =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);
        MediaProjectionManagerService.MediaProjection projection =
                startProjectionPreconditions(service);
        projection.start(mIMediaProjectionCallback);
        doReturn(true)
                .when(mWindowManagerInternal)
                .setContentRecordingSession(any(ContentRecordingSession.class));
        ContentRecordingSession taskSession = createTaskSession(mock(IBinder.class));
        taskSession.setTargetUid(targetUid);
        service.setContentRecordingSession(taskSession);

        projection.stop(stopReason);

        verify(mMediaProjectionMetricsLogger).logStopped(UID, targetUid, stopReason);
    }

    @Test
    public void testIsValid_multipleStarts_preventionDisabled() throws NameNotFoundException {
        MediaProjectionManagerService service = new MediaProjectionManagerService(mContext,
                mPreventReusedTokenDisabledInjector);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions(
                service);
        // No starts yet, and not timed out yet - so still valid.
        assertThat(projection.isValid()).isTrue();

        // Only one start - so still valid.
        projection.start(mIMediaProjectionCallback);
        assertThat(projection.isValid()).isTrue();

        // Second start - technically allowed to start again, without stopping in between.
        // Token should no longer be valid.
        projection.start(mIMediaProjectionCallback);
        assertThat(projection.isValid()).isFalse();
    }

    @Test
    public void testIsValid_restart() throws NameNotFoundException {
        MediaProjectionManagerService service = new MediaProjectionManagerService(mContext,
                mPreventReusedTokenDisabledInjector);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions(
                service);
        // No starts yet, and not timed out yet - so still valid.
        assertThat(projection.isValid()).isTrue();

        // Only one start - so still valid.
        projection.start(mIMediaProjectionCallback);
        assertThat(projection.isValid()).isTrue();

        projection.stop(StopReason.STOP_UNKNOWN);

        // Second start - so not valid.
        projection.start(mIMediaProjectionCallback);
        assertThat(projection.isValid()).isFalse();
    }

    @Test
    public void testIsValid_timeout() throws NameNotFoundException {
        final MediaProjectionManagerService.Injector mClockInjector =
                new MediaProjectionManagerService.Injector() {
                    @Override
                    MediaProjectionManagerService.Clock createClock() {
                        // Always return the same value for elapsed time.
                        return () -> mClock.now();
                    }
                    @Override
                    boolean shouldMediaProjectionPreventReusingConsent(
                            MediaProjectionManagerService.MediaProjection projection) {
                        return false;
                    }
                };
        final MediaProjectionManagerService service = new MediaProjectionManagerService(mContext,
                mClockInjector);
        MediaProjectionManagerService.MediaProjection projection = createProjectionPreconditions(
                service);
        mClock.fastForward(projection.mDefaultTimeoutMillis + 10);

        // Immediate timeout - so no longer valid.
        assertThat(projection.isValid()).isFalse();
    }

    @Test
    public void testIsValid_virtualDisplayAlreadyCreated() throws NameNotFoundException {
        MediaProjectionManagerService service = new MediaProjectionManagerService(mContext,
                mPreventReusedTokenDisabledInjector);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions(
                service);
        // Simulate MediaProjection#createVirtualDisplay being invoked previously.
        projection.notifyVirtualDisplayCreated(10);

        // Trying to re-use token on another MediaProjection#createVirtualDisplay - no longer valid.
        assertThat(projection.isValid()).isFalse();
    }

    // TODO(269273190): Test flag using compat annotations instead.
    @Test
    public void testIsValid_invalid_preventionEnabled()
            throws NameNotFoundException {
        MediaProjectionManagerService service = new MediaProjectionManagerService(mContext,
                mPreventReusedTokenEnabledInjector);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions(
                service);
        projection.start(mIMediaProjectionCallback);
        projection.stop(StopReason.STOP_UNKNOWN);
        // Second start - so not valid.
        projection.start(mIMediaProjectionCallback);

        assertThrows(SecurityException.class, projection::isValid);
    }

    // TODO(269273190): Test flag using compat annotations instead.
    @Test
    public void testIsValid_invalid_preventionDisabled()
            throws NameNotFoundException {
        MediaProjectionManagerService service = new MediaProjectionManagerService(mContext,
                mPreventReusedTokenDisabledInjector);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions(
                service);
        projection.start(mIMediaProjectionCallback);
        projection.stop(StopReason.STOP_UNKNOWN);

        // Second start - so not valid.
        projection.start(mIMediaProjectionCallback);

        assertThat(projection.isValid()).isFalse();
    }

    @Test
    public void testIsCurrentProjectionInternal_invalid() throws NameNotFoundException {
        IBinder iBinder = mock(IBinder.class);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();

        // Create a projection, current is false.
        assertThat(projection).isNotNull();
        assertThat(mService.isCurrentProjection(iBinder)).isFalse();

        // Start the projection, and test a random token.
        projection.start(mIMediaProjectionCallback);
        assertThat(mService.isCurrentProjection(iBinder)).isFalse();
    }

    @Test
    public void testIsCurrentProjectionInternal_noProjection() {
        IBinder iBinder = mock(IBinder.class);
        assertThat(mService.isCurrentProjection(iBinder)).isFalse();
    }

    @Test
    public void testIsCurrentProjectionInternal_currentProjection()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();

        // Create a projection, current is false.
        assertThat(projection).isNotNull();
        assertThat(mService.isCurrentProjection(projection.asBinder())).isFalse();

        // Start the projection, is current is now true.
        projection.start(mIMediaProjectionCallback);
        assertThat(mService.isCurrentProjection(projection.asBinder())).isTrue();
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_noCurrentProjection() {
        // Gracefully handle invocation without a current projection.
        mService.setUserReviewGrantedConsentResult(RECORD_CONTENT_DISPLAY,
                mock(IMediaProjection.class));
        assertThat(mService.getActiveProjectionInfo()).isNull();
        verify(mWindowManagerInternal, never()).setContentRecordingSession(any(
                ContentRecordingSession.class));
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_projectionNotCurrent() throws Exception {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);
        assertThat(mService.isCurrentProjection(projection)).isTrue();
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        // Some other token.
        final IMediaProjection otherProjection = mock(IMediaProjection.class);
        doReturn(mock(IBinder.class)).when(otherProjection).asBinder();
        // Waiting for user to review consent.
        mService.setContentRecordingSession(mWaitingDisplaySession);
        mService.setUserReviewGrantedConsentResult(RECORD_CONTENT_DISPLAY, otherProjection);

        // Display result is ignored; only the first session is set.
        verify(mWindowManagerInternal, times(1)).setContentRecordingSession(
                eq(mWaitingDisplaySession));
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_projectionNull() throws Exception {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);
        assertThat(mService.isCurrentProjection(projection)).isTrue();
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        // Some other token.
        final IMediaProjection otherProjection = null;
        // Waiting for user to review consent.
        mService.setContentRecordingSession(mWaitingDisplaySession);
        mService.setUserReviewGrantedConsentResult(RECORD_CONTENT_DISPLAY, otherProjection);

        // Display result is ignored; only the first session is set.
        verify(mWindowManagerInternal, times(1)).setContentRecordingSession(
                eq(mWaitingDisplaySession));
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_projectionNull_consentNotGranted()
            throws Exception {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);
        assertThat(mService.isCurrentProjection(projection)).isTrue();
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        // Some other token.
        final IMediaProjection otherProjection = null;
        // Waiting for user to review consent.
        mService.setContentRecordingSession(mWaitingDisplaySession);
        mService.setUserReviewGrantedConsentResult(RECORD_CANCEL, otherProjection);

        // Display result is ignored; only the first session is set.
        verify(mWindowManagerInternal, times(1)).setContentRecordingSession(
                eq(mWaitingDisplaySession));
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_noVirtualDisplay() throws Exception {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);
        // Do not indicate that the virtual display was created.
        ContentRecordingSession session = mWaitingDisplaySession;
        session.setVirtualDisplayId(INVALID_DISPLAY);
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        // Waiting for user to review consent.
        assertThat(mService.isCurrentProjection(projection)).isTrue();
        mService.setContentRecordingSession(session);

        mService.setUserReviewGrantedConsentResult(RECORD_CONTENT_DISPLAY, projection);
        // A session is sent, indicating consent is granted to record but the virtual display isn't
        // ready yet.
        verify(mWindowManagerInternal, times(2)).setContentRecordingSession(
                mSessionCaptor.capture());
        // Examine latest value.
        final ContentRecordingSession capturedSession = mSessionCaptor.getValue();
        assertThat(capturedSession.isWaitingForConsent()).isFalse();
        assertThat(capturedSession.getVirtualDisplayId()).isEqualTo(INVALID_DISPLAY);
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_thenVirtualDisplayCreated() throws Exception {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);
        assertThat(mService.isCurrentProjection(projection)).isTrue();
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        // Waiting for user to review consent.
        mService.setContentRecordingSession(mWaitingDisplaySession);
        mService.setUserReviewGrantedConsentResult(RECORD_CONTENT_DISPLAY, projection);

        // Virtual Display is finally created.
        projection.notifyVirtualDisplayCreated(10);
        verifySetSessionWithContent(ContentRecordingSession.RECORD_CONTENT_DISPLAY);
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_unknown_updatedSession() throws Exception {
        testSetUserReviewGrantedConsentResult_userCancelsSession(
                /* isSetSessionSuccessful= */ true, UNKNOWN);
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_unknown_failedToUpdateSession()
            throws Exception {
        testSetUserReviewGrantedConsentResult_userCancelsSession(
                /* isSetSessionSuccessful= */ false, UNKNOWN);
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_cancel_updatedSession() throws Exception {
        testSetUserReviewGrantedConsentResult_userCancelsSession(
                /* isSetSessionSuccessful= */ true, RECORD_CANCEL);
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_cancel_failedToUpdateSession()
            throws Exception {
        testSetUserReviewGrantedConsentResult_userCancelsSession(
                /* isSetSessionSuccessful= */ false, RECORD_CANCEL);
    }

    @Test
    public void notifyPermissionRequestInitiated_forwardsToLogger() {
        int hostUid = 123;
        int sessionCreationSource = 456;
        mService =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);

        mService.notifyPermissionRequestInitiated(hostUid, sessionCreationSource);

        verify(mMediaProjectionMetricsLogger).logInitiated(hostUid, sessionCreationSource);
    }

    @Test
    public void notifyPermissionRequestDisplayed_forwardsToLogger() {
        int hostUid = 123;
        mService =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);

        mService.notifyPermissionRequestDisplayed(hostUid);

        verify(mMediaProjectionMetricsLogger).logPermissionRequestDisplayed(hostUid);
    }

    @Test
    public void notifyPermissionRequestCancelled_forwardsToLogger() {
        int hostUid = 123;
        mService =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);

        mService.notifyPermissionRequestCancelled(hostUid);

        verify(mMediaProjectionMetricsLogger).logProjectionPermissionRequestCancelled(hostUid);
    }

    @Test
    public void notifyAppSelectorDisplayed_forwardsToLogger() {
        int hostUid = 456;
        mService =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);

        mService.notifyAppSelectorDisplayed(hostUid);

        verify(mMediaProjectionMetricsLogger).logAppSelectorDisplayed(hostUid);
    }

    @Test
    public void notifyWindowingModeChanged_forwardsToLogger() throws Exception {
        int targetUid = 123;
        mService =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);

        ContentRecordingSession taskSession = createTaskSession(mock(IBinder.class));
        taskSession.setTargetUid(targetUid);
        mService.setContentRecordingSession(taskSession);

        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        mService.notifyWindowingModeChanged(
                RECORD_CONTENT_TASK, targetUid, WINDOWING_MODE_MULTI_WINDOW);

        verify(mMediaProjectionMetricsLogger).logChangedWindowingMode(RECORD_CONTENT_TASK,
                projection.uid, targetUid, WINDOWING_MODE_MULTI_WINDOW);
    }

    @Test
    public void notifyCaptureBoundsChanged_forwardsToLoggerAndResizeCallbacks() throws Exception {
        int targetUid = 123;
        mService =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);

        ContentRecordingSession taskSession = createTaskSession(mock(IBinder.class));
        taskSession.setTargetUid(targetUid);
        mService.setContentRecordingSession(taskSession);

        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        Rect newBounds = new Rect(0, 0, 1000, 2000);
        mService.notifyCaptureBoundsChanged(RECORD_CONTENT_TASK, targetUid, newBounds);

        verify(mMediaProjectionMetricsLogger)
                .logChangedCaptureBounds(RECORD_CONTENT_TASK, projection.uid, targetUid, newBounds);
    }

    /**
     * Executes and validates scenario where the consent result indicates the projection ends.
     */
    private void testSetUserReviewGrantedConsentResult_userCancelsSession(
            boolean isSetSessionSuccessful, @ReviewGrantedConsentResult int consentResult)
            throws Exception {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);
        projection.notifyVirtualDisplayCreated(10);
        // Waiting for user to review consent.
        assertThat(mService.isCurrentProjection(projection)).isTrue();
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        mService.setContentRecordingSession(mWaitingDisplaySession);

        doReturn(isSetSessionSuccessful).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));

        mService.setUserReviewGrantedConsentResult(consentResult, projection);
        verify(mWindowManagerInternal, atLeastOnce()).setContentRecordingSession(
                mSessionCaptor.capture());
        // Null value to stop session.
        assertThat(mSessionCaptor.getValue()).isNull();
        assertThat(mService.isCurrentProjection(projection.asBinder())).isFalse();
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_displayMirroring_startedSession()
            throws NameNotFoundException {
        testSetUserReviewGrantedConsentResult_startedSession(RECORD_CONTENT_DISPLAY,
                ContentRecordingSession.RECORD_CONTENT_DISPLAY);
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_displayMirroring_failedToStartSession()
            throws NameNotFoundException {
        testSetUserReviewGrantedConsentResult_failedToStartSession(RECORD_CONTENT_DISPLAY,
                ContentRecordingSession.RECORD_CONTENT_DISPLAY);
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_taskMirroring_startedSession()
            throws NameNotFoundException {
        testSetUserReviewGrantedConsentResult_startedSession(RECORD_CONTENT_TASK,
                ContentRecordingSession.RECORD_CONTENT_TASK);
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_taskMirroring_failedToStartSession()
            throws NameNotFoundException {
        testSetUserReviewGrantedConsentResult_failedToStartSession(RECORD_CONTENT_TASK,
                ContentRecordingSession.RECORD_CONTENT_TASK);
    }

    /**
     * Executes and validates scenario where the consent result indicates the projection continues,
     * and successfully started projection.
     */
    private void testSetUserReviewGrantedConsentResult_startedSession(
            @ReviewGrantedConsentResult int consentResult,
            @RecordContent int recordedContent)
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.setLaunchCookie(new LaunchCookie());
        projection.start(mIMediaProjectionCallback);
        projection.notifyVirtualDisplayCreated(10);
        // Waiting for user to review consent.
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        mService.setContentRecordingSession(mWaitingDisplaySession);

        mService.setUserReviewGrantedConsentResult(consentResult, projection);
        verifySetSessionWithContent(recordedContent);
        assertThat(mService.isCurrentProjection(projection)).isTrue();
    }

    /**
     * Executes and validates scenario where the consent result indicates the projection continues,
     * but unable to continue projection.
     */
    private void testSetUserReviewGrantedConsentResult_failedToStartSession(
            @ReviewGrantedConsentResult int consentResult,
            @RecordContent int recordedContent)
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);
        projection.notifyVirtualDisplayCreated(10);
        // Waiting for user to review consent.
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                eq(mWaitingDisplaySession));
        mService.setContentRecordingSession(mWaitingDisplaySession);

        doReturn(false).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));

        mService.setUserReviewGrantedConsentResult(consentResult, projection);
        verifySetSessionWithContent(recordedContent);
        assertThat(mService.isCurrentProjection(projection.asBinder())).isFalse();
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_displayMirroring_noPriorSession()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.setLaunchCookie(new LaunchCookie());
        projection.start(mIMediaProjectionCallback);
        // Skip setting the prior session details.

        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));

        mService.setUserReviewGrantedConsentResult(RECORD_CONTENT_DISPLAY, projection);
        // Result is ignored & session not updated.
        verify(mWindowManagerInternal, never()).setContentRecordingSession(any(
                ContentRecordingSession.class));
        // Current session continues.
        assertThat(mService.isCurrentProjection(projection)).isTrue();
    }

    @Test
    public void testSetUserReviewGrantedConsentResult_displayMirroring_sessionNotWaiting()
            throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.setLaunchCookie(new LaunchCookie());
        projection.start(mIMediaProjectionCallback);
        // Session is not waiting for user's consent.
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        mService.setContentRecordingSession(DISPLAY_SESSION);

        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));

        mService.setUserReviewGrantedConsentResult(RECORD_CONTENT_DISPLAY, projection);
        // Result is ignored; only the original session was ever sent.
        verify(mWindowManagerInternal).setContentRecordingSession(eq(
                DISPLAY_SESSION));
        // Current session continues.
        assertThat(mService.isCurrentProjection(projection)).isTrue();
    }

    @FlakyTest(bugId = 288342281)
    @Test
    public void setContentRecordingSession_successful_notifiesListeners()
            throws Exception {
        mService.addCallback(mWatcherCallback);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        mService.setContentRecordingSession(DISPLAY_SESSION);

        verify(mWatcherCallback).onRecordingSessionSet(
                projection.getProjectionInfo(),
                DISPLAY_SESSION
        );
    }

    @Test
    public void setContentRecordingSession_success_logsCaptureInProgress()
            throws Exception {
        mService.addCallback(mWatcherCallback);
        MediaProjectionManagerService service =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);
        MediaProjectionManagerService.MediaProjection projection =
                startProjectionPreconditions(service);
        projection.start(mIMediaProjectionCallback);
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));

        service.setContentRecordingSession(DISPLAY_SESSION);

        verify(mMediaProjectionMetricsLogger).logInProgress(
                projection.uid,
                DISPLAY_SESSION.getTargetUid()
        );
    }

    @Test
    public void setContentRecordingSession_taskSession_logsCaptureInProgressWithTargetUid()
            throws Exception {
        mService.addCallback(mWatcherCallback);
        MediaProjectionManagerService service =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);
        MediaProjectionManagerService.MediaProjection projection =
                startProjectionPreconditions(service);
        projection.start(mIMediaProjectionCallback);
        doReturn(true)
                .when(mWindowManagerInternal)
                .setContentRecordingSession(any(ContentRecordingSession.class));
        int targetUid = 123455;

        ContentRecordingSession taskSession = createTaskSession(mock(IBinder.class));
        taskSession.setTargetUid(targetUid);
        service.setContentRecordingSession(taskSession);

        verify(mMediaProjectionMetricsLogger).logInProgress(projection.uid, targetUid);
    }

    @Test
    public void setContentRecordingSession_failure_doesNotLogCaptureInProgress() throws Exception {
        mService.addCallback(mWatcherCallback);
        MediaProjectionManagerService service =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);
        MediaProjectionManagerService.MediaProjection projection =
                startProjectionPreconditions(service);
        projection.start(mIMediaProjectionCallback);
        doReturn(false).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));

        service.setContentRecordingSession(DISPLAY_SESSION);

        verify(mMediaProjectionMetricsLogger, never()).logInProgress(
                anyInt(),
                anyInt()
        );
    }

    @Test
    public void setContentRecordingSession_sessionNull_doesNotLogCaptureInProgress()
            throws Exception {
        mService.addCallback(mWatcherCallback);
        MediaProjectionManagerService service =
                new MediaProjectionManagerService(mContext, mMediaProjectionMetricsLoggerInjector);
        MediaProjectionManagerService.MediaProjection projection =
                startProjectionPreconditions(service);
        projection.start(mIMediaProjectionCallback);
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));

        service.setContentRecordingSession(null);

        verify(mMediaProjectionMetricsLogger, never()).logInProgress(
                anyInt(),
                anyInt()
        );
    }

    @Test
    public void setContentRecordingSession_notifiesListenersOnCallbackLooper()
            throws Exception {
        mService = new MediaProjectionManagerService(mContext, mTestLooperInjector);
        mService.addCallback(mWatcherCallback);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);
        doReturn(true).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));

        mService.setContentRecordingSession(DISPLAY_SESSION);
        // Callback not notified yet, as test looper hasn't dispatched the message yet
        verify(mWatcherCallback, never()).onRecordingSessionSet(any(), any());

        mTestLooper.dispatchAll();
        // Message dispatched on test looper. Callback should now be notified.
        verify(mWatcherCallback).onRecordingSessionSet(
                projection.getProjectionInfo(),
                DISPLAY_SESSION
        );
    }

    @Test
    public void setContentRecordingSession_failure_doesNotNotifyListeners()
            throws Exception {
        mService.addCallback(mWatcherCallback);
        MediaProjectionManagerService.MediaProjection projection = startProjectionPreconditions();
        projection.start(mIMediaProjectionCallback);

        doReturn(false).when(mWindowManagerInternal).setContentRecordingSession(
                any(ContentRecordingSession.class));
        mService.setContentRecordingSession(DISPLAY_SESSION);

        verify(mWatcherCallback, never()).onRecordingSessionSet(any(), any());
    }

    @Test
    public void createProjectionForSecondaryDisplay() throws NameNotFoundException {
        MediaProjectionManagerService.MediaProjection projection =
                createProjectionPreconditions(mService, 200);
        assertThat(projection.getDisplayId()).isEqualTo(200);
    }

    @Test
    @EnableFlags(Flags.FLAG_RECORDING_OVERLAY)
    public void createProjectionForOverlay_forContextualSearch() throws Exception {
        MediaProjectionManagerService.MediaProjection projection =
                createProjectionPreconditions(mService);
        projection.setRecordingOverlay(true);

        assertThat(projection.isRecordingOverlay()).isTrue();
    }

    @Test
    @DisableFlags(Flags.FLAG_RECORDING_OVERLAY)
    public void createProjectionForOverlay_withoutFlag() throws Exception {
        MediaProjectionManagerService.MediaProjection projection = createProjectionPreconditions(
                mService);
        projection.setRecordingOverlay(true);

        assertThat(projection.isRecordingOverlay()).isFalse();
    }

    @Test
    public void createProjection_doesNotGrantAlertWindowByDefault() throws Exception {
        mAppInfo.privateFlags |= PRIVATE_FLAG_PRIVILEGED;
        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfoAsUser(anyString(), anyInt(),
                anyInt());
        MediaProjectionManagerService.MediaProjection projection =
                mService.createProjectionInternal(
                        UID,
                        PACKAGE_NAME,
                        TYPE_SCREEN_CAPTURE,
                        /* isPermanentGrant= */ false,
                        DEFAULT_DISPLAY);

        doReturn(AppOpsManager.MODE_DEFAULT).when(mAppOpsManager).unsafeCheckOpRawNoThrow(
                AppOpsManager.OP_SYSTEM_ALERT_WINDOW, projection.uid, projection.packageName);
        projection.start(mIMediaProjectionCallback);
        projection.stop(StopReason.STOP_HOST_APP);

        verify(mAppOpsManager, never()).setMode(eq(AppOpsManager.OP_SYSTEM_ALERT_WINDOW),
                eq(projection.uid), eq(projection.packageName), anyInt());
    }

    @Test
    public void createProjection_alertWindowRequested_isGranted() throws Exception {
        mAppInfo.privateFlags |= PRIVATE_FLAG_PRIVILEGED;
        mPackageInfo.requestedPermissions = new String[]{Manifest.permission.SYSTEM_ALERT_WINDOW};
        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfoAsUser(anyString(), anyInt(),
                anyInt());
        MediaProjectionManagerService.MediaProjection projection =
                mService.createProjectionInternal(
                        UID,
                        PACKAGE_NAME,
                        TYPE_SCREEN_CAPTURE,
                        /* isPermanentGrant= */ false,
                        DEFAULT_DISPLAY);

        doReturn(AppOpsManager.MODE_DEFAULT).when(mAppOpsManager).unsafeCheckOpRawNoThrow(
                AppOpsManager.OP_SYSTEM_ALERT_WINDOW, projection.uid, projection.packageName);
        projection.start(mIMediaProjectionCallback);
        verify(mAppOpsManager).setMode(AppOpsManager.OP_SYSTEM_ALERT_WINDOW, projection.uid,
                projection.packageName, AppOpsManager.MODE_ALLOWED);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).unsafeCheckOpRawNoThrow(
                AppOpsManager.OP_SYSTEM_ALERT_WINDOW, projection.uid, projection.packageName);

        projection.stop(StopReason.STOP_HOST_APP);
        verify(mAppOpsManager).setMode(AppOpsManager.OP_SYSTEM_ALERT_WINDOW, projection.uid,
                projection.packageName, AppOpsManager.MODE_DEFAULT);
    }

    @Test
    @EnableFlags(Flags.FLAG_RECORDING_OVERLAY)
    public void createProjectionForOverlay_grantsApplicationOverlay() throws Exception {
        mAppInfo.privateFlags |= PRIVATE_FLAG_PRIVILEGED;
        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfoAsUser(anyString(), anyInt(),
                anyInt());
        MediaProjectionManagerService.MediaProjection projection =
                mService.createProjectionInternal(
                        UID,
                        PACKAGE_NAME,
                        TYPE_SCREEN_CAPTURE,
                        /* isPermanentGrant= */ false,
                        DEFAULT_DISPLAY);
        projection.setRecordingOverlay(true);

        doReturn(AppOpsManager.MODE_DEFAULT).when(mAppOpsManager).unsafeCheckOpRawNoThrow(
                AppOpsManager.OP_SYSTEM_APPLICATION_OVERLAY, projection.uid,
                projection.packageName);
        projection.start(mIMediaProjectionCallback);
        verify(mAppOpsManager).setUidMode(AppOpsManager.OP_SYSTEM_APPLICATION_OVERLAY,
                projection.uid, AppOpsManager.MODE_ALLOWED);
        doReturn(AppOpsManager.MODE_ALLOWED).when(mAppOpsManager).unsafeCheckOpRawNoThrow(
                AppOpsManager.OP_SYSTEM_APPLICATION_OVERLAY, projection.uid,
                projection.packageName);

        projection.stop(StopReason.STOP_HOST_APP);
        verify(mAppOpsManager).setUidMode(AppOpsManager.OP_SYSTEM_APPLICATION_OVERLAY,
                projection.uid, AppOpsManager.MODE_DEFAULT);
    }

    @Test
    @EnableFlags(Flags.FLAG_RECORDING_OVERLAY)
    public void createProjectionForDisplay_doesNotGrantApplicationOverlay() throws Exception {
        mAppInfo.privateFlags |= PRIVATE_FLAG_PRIVILEGED;
        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfoAsUser(anyString(), anyInt(),
                anyInt());
        MediaProjectionManagerService.MediaProjection projection =
                mService.createProjectionInternal(
                        UID,
                        PACKAGE_NAME,
                        TYPE_SCREEN_CAPTURE,
                        /* isPermanentGrant= */ false,
                        DEFAULT_DISPLAY);

        doReturn(AppOpsManager.MODE_DEFAULT).when(mAppOpsManager).unsafeCheckOpRawNoThrow(
                AppOpsManager.OP_SYSTEM_APPLICATION_OVERLAY, projection.uid,
                projection.packageName);
        projection.start(mIMediaProjectionCallback);
        projection.stop(StopReason.STOP_HOST_APP);
        verify(mAppOpsManager, never()).setUidMode(eq(AppOpsManager.OP_SYSTEM_APPLICATION_OVERLAY),
                eq(projection.uid), anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_START_UID_CHECK)
    public void createProjection_cannotBeStartedFromDifferentUid() throws Exception {
        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        doReturn(mPackageInfo).when(mPackageManager).getPackageInfoAsUser(anyString(), anyInt(),
                anyInt());
        int uid = 10;
        MediaProjectionManagerService.MediaProjection projection =
                mService.createProjectionInternal(
                        uid,
                        PACKAGE_NAME,
                        TYPE_SCREEN_CAPTURE,
                        /* isPermanentGrant= */ false,
                        DEFAULT_DISPLAY);

        // Start MediaProjection from a different UID
        assertThrows(SecurityException.class, () -> projection.start(mIMediaProjectionCallback));
    }

    @Test
    public void appContentProjection_cannotProjectVideo() throws Exception {
        IBinder binder1 = mock(IBinder.class);
        IAppContentProjectionCallback callback1 = mock(IAppContentProjectionCallback.class);
        IAppContentProjectionSession session1 = mock(IAppContentProjectionSession.class);
        when(callback1.asBinder()).thenReturn(binder1);
        IMediaProjection projection = createAppContentProjection(session1, callback1);

        assertThat(projection.canProjectVideo()).isFalse();
    }

    @Test
    public void setAppContentProjectionCallback_notifiesListeners() throws Exception {
        mService.addCallback(mWatcherCallback);

        IBinder binder1 = mock(IBinder.class);
        IAppContentProjectionCallback callback1 = mock(IAppContentProjectionCallback.class);
        IAppContentProjectionSession session1 = mock(IAppContentProjectionSession.class);
        when(callback1.asBinder()).thenReturn(binder1);
        IMediaProjection projection = createAppContentProjection(session1, callback1);

        projection.start(mIMediaProjectionCallback);
        mTestLooper.dispatchAll();
        verify(mWatcherCallback, timeout(1000)).onStart(any());

        assertThat(mService.getActiveProjectionInfo()).isNotNull();
        assertThat(mService.getActiveProjectionInfo().getPackageName()).isEqualTo(PACKAGE_NAME);
    }

    @Test
    public void setAppContentProjectionCallback_onBinderDied_stopsProjection() throws Exception {
        createProjectionPreconditions(mService);
        IBinder binder = mock(IBinder.class);
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        IAppContentProjectionCallback callback1 = mock(IAppContentProjectionCallback.class);
        IAppContentProjectionSession session1 = mock(IAppContentProjectionSession.class);
        when(callback1.asBinder()).thenReturn(binder);
        IMediaProjection projection = createAppContentProjection(session1, callback1);

        projection.start(mIMediaProjectionCallback);

        verify(binder).linkToDeath(deathRecipientCaptor.capture(), eq(0));

        // Trigger death
        deathRecipientCaptor.getValue().binderDied();

        // Verify projection stopped
        assertThat(mService.getActiveProjectionInfo()).isNull();
    }

    @Test
    public void setAppContentProjectionCallback_oldCallbackDied_doesNotStopProjection()
            throws Exception {
        IBinder binder1 = mock(IBinder.class);
        IAppContentProjectionCallback callback1 = mock(IAppContentProjectionCallback.class);
        IAppContentProjectionSession session1 = mock(IAppContentProjectionSession.class);
        when(callback1.asBinder()).thenReturn(binder1);
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor1 =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);


        mService.new BinderService(mContext);
        IMediaProjection projection = createAppContentProjection(session1, callback1);
        projection.start(mIMediaProjectionCallback);
        verify(binder1).linkToDeath(deathRecipientCaptor1.capture(), eq(0));
        assertThat(mService.getActiveProjectionInfo()).isNotNull();

        // Set a new callback
        IBinder binder2 = mock(IBinder.class);
        IAppContentProjectionCallback callback2 = mock(IAppContentProjectionCallback.class);
        IAppContentProjectionSession session2 = mock(IAppContentProjectionSession.class);
        when(callback2.asBinder()).thenReturn(binder2);
        projection = createAppContentProjection(session2, callback2);

        projection.start(mIMediaProjectionCallback);

        // Trigger death of the old callback
        deathRecipientCaptor1.getValue().binderDied();

        mTestLooper.dispatchAll();
        // Verify projection is STILL ACTIVE
        assertThat(mService.getActiveProjectionInfo()).isNotNull();
    }

    @Test
    public void setAppContentProjectionCallback_onStop_notifiesCallback() throws Exception {
        MediaProjectionManagerService.BinderService binderService =
                mService.new BinderService(mContext);
        IAppContentProjectionCallback callback1 = mock(IAppContentProjectionCallback.class);
        IAppContentProjectionSession session1 = mock(IAppContentProjectionSession.class);
        IBinder binder1 = mock(IBinder.class);
        when(callback1.asBinder()).thenReturn(binder1);
        IMediaProjection projection = createAppContentProjection(session1, callback1);

        projection.start(mIMediaProjectionCallback);
        binderService.stopActiveProjection(StopReason.STOP_HOST_APP);

        // Call dispatchAll twice because the first dispatch call will
        // post again on the handler
        mTestLooper.dispatchAll();
        mTestLooper.dispatchAll();
        verify(callback1, timeout(1000)).onSessionStopped();
    }

    private void verifySetSessionWithContent(@RecordContent int content) {
        verify(mWindowManagerInternal, atLeastOnce()).setContentRecordingSession(
                mSessionCaptor.capture());
        assertThat(mSessionCaptor.getValue()).isNotNull();
        assertThat(mSessionCaptor.getValue().getContentToRecord()).isEqualTo(content);
    }

    // Set up preconditions for creating a projection.
    private MediaProjectionManagerService.MediaProjection createProjectionPreconditions(
            MediaProjectionManagerService service) throws NameNotFoundException {
        return createProjectionPreconditions(service, Display.DEFAULT_DISPLAY);
    }

    private MediaProjectionManagerService.MediaProjection createProjectionPreconditions(
            MediaProjectionManagerService service, int displayId) throws NameNotFoundException {
        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        return service.createProjectionInternal(
                UID,
                PACKAGE_NAME,
                TYPE_MIRRORING,
                /* isPermanentGrant= */ false,
                displayId);
    }

    private IMediaProjection createAppContentProjection(
            IAppContentProjectionSession session, IAppContentProjectionCallback callback)
            throws NameNotFoundException {
        mAppInfo.privateFlags |= PRIVATE_FLAG_PRIVILEGED;
        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        MediaProjectionManagerService.BinderService mediaProjectionBinderService =
                mService.new BinderService(mContext);
        return mediaProjectionBinderService.createProjectionForAppContent(UID, PACKAGE_NAME,
                session, 0, false, callback);
    }

    // Set up preconditions for starting a projection, with no foreground service requirements.
    private MediaProjectionManagerService.MediaProjection startProjectionPreconditions(
            MediaProjectionManagerService service)
            throws NameNotFoundException {
        mAppInfo.privateFlags |= PRIVATE_FLAG_PRIVILEGED;
        return createProjectionPreconditions(service);
    }

    // Set up preconditions for starting a projection, with no foreground service requirements.
    private MediaProjectionManagerService.MediaProjection startProjectionPreconditions()
            throws NameNotFoundException {
        mAppInfo.privateFlags |= PRIVATE_FLAG_PRIVILEGED;
        return createProjectionPreconditions(mService);
    }

    // Set up preconditions for starting a projection, retrieving a pre-existing projection.
    private MediaProjectionManagerService.MediaProjection startReusedProjectionPreconditions()
            throws NameNotFoundException {
        mAppInfo.privateFlags |= PRIVATE_FLAG_PRIVILEGED;
        doReturn(mAppInfo).when(mPackageManager).getApplicationInfoAsUser(anyString(),
                any(ApplicationInfoFlags.class), any(UserHandle.class));
        return mService.getProjectionInternal(UID, PACKAGE_NAME);
    }

    private static class FakeIMediaProjectionCallback extends IMediaProjectionCallback.Stub {
        CountDownLatch mLatch = new CountDownLatch(1);
        @Override
        public void onStop() throws RemoteException {
            mLatch.countDown();
        }

        @Override
        public void onCapturedContentResize(int width, int height) throws RemoteException {
        }

        @Override
        public void onCapturedContentVisibilityChanged(boolean isVisible) throws RemoteException {
        }
    }
}
