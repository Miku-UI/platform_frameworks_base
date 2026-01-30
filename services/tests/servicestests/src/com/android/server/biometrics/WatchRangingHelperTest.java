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

package com.android.server.biometrics;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import android.content.Context;
import android.hardware.biometrics.IIdentityCheckStateListener.WatchRangingState;
import android.os.Handler;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.proximity.IProximityResultCallback;
import android.proximity.ProximityResultCode;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.IAuthenticationPolicyService;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@Presubmit
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper()
public class WatchRangingHelperTest {
    private static final long AUTHENTICATION_REQUEST_ID = 1;

    @Rule
    public MockitoRule mockito = MockitoJUnit.rule();

    @Mock
    private IAuthenticationPolicyService mAuthenticationPolicyService;
    @Mock
    private Context mContext;

    private ArgumentCaptor<IProximityResultCallback> mProximityResultCallbackArgumentCaptor;
    private WatchRangingHelper mWatchRangingHelper;

    @Before
    public void setup() {
        final AuthenticationPolicyManager authenticationPolicyManager =
                new AuthenticationPolicyManager(mContext, mAuthenticationPolicyService);
        mWatchRangingHelper = new WatchRangingHelper(AUTHENTICATION_REQUEST_ID,
                authenticationPolicyManager, new Handler(TestableLooper.get(this).getLooper()),
                watchRangingState -> {});
        mProximityResultCallbackArgumentCaptor = ArgumentCaptor.forClass(
                IProximityResultCallback.class);
    }

    @Test
    public void testNullAuthenticationPolicyManager() {
        mWatchRangingHelper = new WatchRangingHelper(AUTHENTICATION_REQUEST_ID,
                null, new Handler(TestableLooper.get(this).getLooper()),
                watchRangingState -> {});

        mWatchRangingHelper.startWatchRanging();

        verifyNoInteractions(mAuthenticationPolicyService);
        assertThat(mWatchRangingHelper.getWatchRangingState()).isEqualTo(
                WatchRangingState.WATCH_RANGING_IDLE);
    }

    @Test
    public void testStartWatchRanging() throws RemoteException {
        mWatchRangingHelper.startWatchRanging();

        verify(mAuthenticationPolicyService).startWatchRangingForIdentityCheck(
                eq(AUTHENTICATION_REQUEST_ID), any());
        assertThat(mWatchRangingHelper.getWatchRangingState()).isEqualTo(
                WatchRangingState.WATCH_RANGING_STARTED);
    }

    @Test
    public void testStartWatchRanging_onError() throws RemoteException {
        mWatchRangingHelper.startWatchRanging();

        verify(mAuthenticationPolicyService).startWatchRangingForIdentityCheck(
                eq(AUTHENTICATION_REQUEST_ID), mProximityResultCallbackArgumentCaptor.capture());

        mProximityResultCallbackArgumentCaptor.getValue().onError(
                ProximityResultCode.NO_RANGING_RESULT);

        waitForIdle();

        verify(mAuthenticationPolicyService).cancelWatchRangingForRequestId(
                AUTHENTICATION_REQUEST_ID);
        assertThat(mWatchRangingHelper.getWatchRangingState()).isEqualTo(
                WatchRangingState.WATCH_RANGING_STOPPED);
    }

    @Test
    public void testStartWatchRanging_onSuccess() throws RemoteException {
        mWatchRangingHelper.startWatchRanging();

        verify(mAuthenticationPolicyService).startWatchRangingForIdentityCheck(
                eq(AUTHENTICATION_REQUEST_ID), mProximityResultCallbackArgumentCaptor.capture());

        mProximityResultCallbackArgumentCaptor.getValue().onSuccess(
                ProximityResultCode.SUCCESS);

        waitForIdle();

        verify(mAuthenticationPolicyService).cancelWatchRangingForRequestId(
                AUTHENTICATION_REQUEST_ID);
        assertThat(mWatchRangingHelper.getWatchRangingState()).isEqualTo(
                WatchRangingState.WATCH_RANGING_SUCCESSFUL);
    }

    @Test
    public void testCancelWatchRanging() throws RemoteException {
        mWatchRangingHelper.cancelWatchRanging();

        verify(mAuthenticationPolicyService).cancelWatchRangingForRequestId(
                AUTHENTICATION_REQUEST_ID);
    }

    private void waitForIdle() {
        TestableLooper.get(this).processAllMessages();
    }

}
