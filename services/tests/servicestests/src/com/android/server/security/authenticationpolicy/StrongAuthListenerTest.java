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

package com.android.server.security.authenticationpolicy;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.hardware.biometrics.BiometricRequestConstants;
import android.hardware.biometrics.BiometricSourceType;
import android.hardware.biometrics.events.AuthenticationSucceededInfo;
import android.os.Handler;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.IntConsumer;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class StrongAuthListenerTest {

    private static final int USER_ID = 10;

    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    private StrongAuthListener mStrongAuthListener;
    private Handler mHandler;

    @Mock
    private IntConsumer mOnStrongAuth;

    @Before
    public void setUp() {
        mHandler = new Handler(TestableLooper.get(this).getLooper());
        mStrongAuthListener = new StrongAuthListener(mHandler, mOnStrongAuth);
    }

    @Test
    public void testLockSettingsListener_onAuthenticationSucceeded_callsCallback() {
        mStrongAuthListener.asLockSettingsStateListener().onAuthenticationSucceeded(USER_ID);
        TestableLooper.get(this).processAllMessages();

        verify(mOnStrongAuth).accept(USER_ID);
    }

    @Test
    public void testBiometricListener_onAuthenticationSucceeded_callsCallback() throws Exception {
        AuthenticationSucceededInfo authInfo = new AuthenticationSucceededInfo.Builder(
                BiometricSourceType.FINGERPRINT,
                BiometricRequestConstants.REASON_AUTH_KEYGUARD,
                true /* isStrongBiometric */,
                USER_ID).build();

        mStrongAuthListener.asAuthenticationStateListener().onAuthenticationSucceeded(authInfo);
        TestableLooper.get(this).processAllMessages();

        verify(mOnStrongAuth).accept(USER_ID);
    }

    @Test
    public void testBiometricListener_onAuthenticationSucceeded_notStrong_ignored() throws Exception {
        AuthenticationSucceededInfo authInfo = new AuthenticationSucceededInfo.Builder(
                BiometricSourceType.FINGERPRINT,
                BiometricRequestConstants.REASON_AUTH_KEYGUARD,
                false /* isStrongBiometric */,
                USER_ID).build();

        mStrongAuthListener.asAuthenticationStateListener().onAuthenticationSucceeded(authInfo);
        TestableLooper.get(this).processAllMessages();

        verify(mOnStrongAuth, never()).accept(USER_ID);
    }
}
