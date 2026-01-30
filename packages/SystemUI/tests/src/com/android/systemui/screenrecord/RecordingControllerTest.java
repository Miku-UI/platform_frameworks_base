/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.screenrecord;

import static android.os.Process.myUid;

import static com.android.systemui.log.LogBufferHelperKt.logcatLogBuffer;
import static com.android.systemui.screenrecord.ScreenRecordUxController.EXTRA_STATE;
import static com.android.systemui.screenrecord.ScreenRecordUxController.INTENT_UPDATE_STATE;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.projection.StopReason;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.SysuiTestCaseExtKt;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.kosmos.Kosmos;
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger;
import com.android.systemui.mediaprojection.SessionCreationSource;
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDevicePolicyResolver;
import com.android.systemui.mediaprojection.devicepolicy.ScreenCaptureDisabledDialogDelegate;
import com.android.systemui.screenrecord.domain.ScreenRecordingParameters;
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractorKosmosKt;
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingStartStopInteractor;
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingStartStopInteractorKosmosKt;
import com.android.systemui.settings.UserTrackerKosmosKt;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class RecordingControllerTest extends SysuiTestCase {

    private static final int TEST_USER_ID = 12345;

    private final Kosmos mKosmos = SysuiTestCaseExtKt.testKosmosNew(this);
    private final FakeSystemClock mFakeSystemClock = new FakeSystemClock();
    private final FakeExecutor mMainExecutor = new FakeExecutor(mFakeSystemClock);
    @Mock
    private ScreenRecordUxController.StateChangeCallback mCallback;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private ScreenCaptureDevicePolicyResolver mDevicePolicyResolver;
    @Mock
    private MediaProjectionMetricsLogger mMediaProjectionMetricsLogger;

    @Mock
    private ScreenCaptureDisabledDialogDelegate mScreenCaptureDisabledDialogDelegate;
    @Mock
    private SystemUIDialog mScreenCaptureDisabledDialog;
    @Mock
    private ScreenRecordPermissionDialogDelegate.Factory
            mScreenRecordPermissionDialogDelegateFactory;
    @Mock
    private ScreenRecordPermissionContentManager.Factory
            mScreenRecordPermissionContentManagerFactory;
    @Mock
    private ScreenRecordPermissionDialogDelegate mScreenRecordPermissionDialogDelegate;
    @Mock
    private ScreenRecordPermissionContentManager mScreenRecordPermissionContentManager;
    @Mock
    private SystemUIDialog mScreenRecordSystemUIDialog;

    private RecordingController mController;

    private static final int USER_ID = 10;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context spiedContext = spy(mContext);
        when(spiedContext.getUserId()).thenReturn(TEST_USER_ID);

        when(mScreenCaptureDisabledDialogDelegate.createSysUIDialog())
                .thenReturn(mScreenCaptureDisabledDialog);
        when(mScreenRecordPermissionDialogDelegateFactory.create(any(), any(), anyInt(), any()))
                .thenReturn(mScreenRecordPermissionDialogDelegate);
        when(mScreenRecordPermissionContentManagerFactory.create(any(), anyInt(), any(), any()))
                .thenReturn(mScreenRecordPermissionContentManager);
        when(mScreenRecordPermissionDialogDelegate.createDialog())
                .thenReturn(mScreenRecordSystemUIDialog);
        ScreenRecordLegacyUxControllerImpl uxController = new ScreenRecordLegacyUxControllerImpl(
                mMainExecutor,
                mBroadcastDispatcher,
                () -> mDevicePolicyResolver,
                UserTrackerKosmosKt.getUserTracker(mKosmos),
                new RecordingControllerLogger(logcatLogBuffer("RecordingControllerTest")),
                mMediaProjectionMetricsLogger,
                mScreenCaptureDisabledDialogDelegate,
                mScreenRecordPermissionDialogDelegateFactory,
                mScreenRecordPermissionContentManagerFactory
        );
        ScreenRecordUxControllerKosmosKt.setScreenRecordUxController(mKosmos, uxController);
        ScreenRecordingStartStopInteractorKosmosKt.setScreenRecordingStartStopInteractor(
                mKosmos,
                ScreenRecordingServiceInteractorKosmosKt.getScreenRecordingServiceInteractor(
                        mKosmos)
        );
        mController = uxController.getRecordingController();
        mController.addCallback(mCallback);
    }

    // Test that when a countdown in progress is cancelled, the controller goes from starting to not
    // starting, and notifies listeners.
    @Test
    public void testCancelCountdown() {
        mController.startCountdown(100, 10, null, null);

        assertTrue(mController.isStarting());
        assertFalse(mController.isRecording());

        mController.cancelCountdown();

        assertFalse(mController.isStarting());
        assertFalse(mController.isRecording());

        verify(mCallback).onCountdownEnd();
    }

    // Test that when recording is stopped, the stop intent is sent and listeners are notified.
    @Test
    public void testStopRecording() {
        mController.startCountdown(0, 0, start(), stop());
        mController.stopRecording(StopReason.STOP_UNKNOWN);

        assertFalse(mController.isStarting());
        assertFalse(mController.isRecording());
        verify(mCallback).onRecordingEnd();
    }

    // Test that updating the controller state works and notifies listeners.
    @Test
    public void testUpdateState() {
        mController.updateState(true);
        assertTrue(mController.isRecording());
        verify(mCallback).onRecordingStart();

        mController.updateState(false);
        assertFalse(mController.isRecording());
        verify(mCallback).onRecordingEnd();
    }

    // Test that broadcast will update state
    @Test
    public void testUpdateStateBroadcast() {
        // When a recording has started
        mController.startCountdown(0, 0, start(), null);
        verify(mCallback).onCountdownEnd();

        // then the receiver was registered
        verify(mBroadcastDispatcher).registerReceiver(eq(mController.mStateChangeReceiver),
                any(), any(), any());

        // When the receiver gets an update
        Intent intent = new Intent(INTENT_UPDATE_STATE);
        intent.putExtra(EXTRA_STATE, false);
        mController.mStateChangeReceiver.onReceive(mContext, intent);

        // then the state is updated
        assertFalse(mController.isRecording());
        verify(mCallback).onRecordingEnd();

        // and the receiver is unregistered
        verify(mBroadcastDispatcher).unregisterReceiver(eq(mController.mStateChangeReceiver));
    }

    // Test that switching users will stop an ongoing recording
    @Test
    public void testUserChange() {
        mController.startCountdown(0, 0, start(), stop());
        mController.updateState(true);

        // and user is changed
        mController.mUserChangedCallback.onUserChanged(USER_ID, mContext);

        // Ensure that the recording was stopped
        verify(mCallback).onRecordingEnd();
        assertFalse(mController.isRecording());
    }

    @Test
    public void testScreenCapturingNotAllowed_returnsDevicePolicyDialog() {
        when(mDevicePolicyResolver.isScreenCaptureCompletelyDisabled((any()))).thenReturn(true);

        Dialog dialog = mController.createScreenRecordDialog(/* onStartRecordingClicked= */ null);

        assertThat(dialog).isEqualTo(mScreenCaptureDisabledDialog);
    }

    @Test
    public void testScreenCapturingAllowed_returnsNullDevicePolicyDialog() {
        when(mDevicePolicyResolver.isScreenCaptureCompletelyDisabled((any()))).thenReturn(false);

        Dialog dialog = mController.createScreenRecordDialog(/* onStartRecordingClicked= */ null);

        assertThat(dialog).isSameInstanceAs(mScreenRecordSystemUIDialog);
        assertThat(mScreenRecordPermissionDialogDelegate)
                .isInstanceOf(ScreenRecordPermissionDialogDelegate.class);
    }

    @Test
    public void testCreateScreenRecordPermissionContentManager() {
        ScreenRecordPermissionContentManager contentManager =
                mController.createScreenRecordPermissionContentManager(
                        /* onStartRecordingClicked= */ null);
        assertThat(contentManager).isEqualTo(mScreenRecordPermissionContentManager);
    }

    @Test
    public void testScreenCapturingAllowed_returnsFalseIsScreenCaptureDisabled() {
        when(mDevicePolicyResolver.isScreenCaptureCompletelyDisabled((any()))).thenReturn(false);
        assertFalse(mController.isScreenCaptureDisabled());
    }

    @Test
    public void testScreenCapturingNotAllowed_returnsTrueIsScreenCaptureDisabled() {
        when(mDevicePolicyResolver.isScreenCaptureCompletelyDisabled((any()))).thenReturn(true);
        assertTrue(mController.isScreenCaptureDisabled());
    }

    @Test
    public void testScreenCapturingAllowed_logsProjectionInitiated() {
        when(mDevicePolicyResolver.isScreenCaptureCompletelyDisabled((any()))).thenReturn(false);

        mController.createScreenRecordDialog(/* onStartRecordingClicked= */ null);

        verify(mMediaProjectionMetricsLogger)
                .notifyProjectionInitiated(
                        /* hostUid= */ myUid(),
                        SessionCreationSource.SYSTEM_UI_SCREEN_RECORDER);
    }

    private Runnable start() {
        ScreenRecordingStartStopInteractor mInteractor =
                ScreenRecordingStartStopInteractorKosmosKt.getScreenRecordingStartStopInteractor(
                        mKosmos);
        return () -> mInteractor.startRecording(
                new ScreenRecordingParameters(null, ScreenRecordingAudioSource.NONE, 0,
                        false));
    }

    private Runnable stop() {
        ScreenRecordingStartStopInteractor mInteractor =
                ScreenRecordingStartStopInteractorKosmosKt.getScreenRecordingStartStopInteractor(
                        mKosmos);
        return () -> mInteractor.stopRecording(StopReason.STOP_HOST_APP);
    }
}
