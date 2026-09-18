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

package android.window;

import static android.view.inputmethod.Flags.FLAG_BACK_DISPOSITION_CONTROLS_BACK_INTERCEPTION;
import static android.window.ImeBackCallbackProxy.RESULT_CODE_REGISTER;
import static android.window.ImeBackCallbackProxy.RESULT_CODE_UNREGISTER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link ImeBackCallbackSender}.
 *
 * Build/Install/Run:
 * atest FrameworksCoreTests:ImeBackCallbackSenderTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@RequiresFlagsEnabled(FLAG_BACK_DISPOSITION_CONTROLS_BACK_INTERCEPTION)
public class ImeBackCallbackSenderTest {

    private ImeBackCallbackSender mImeBackCallbackSender;
    private ResultReceiver mResultReceiver;
    private OnBackInvokedCallback mSystemCallback;
    private OnBackInvokedCallback mNonSystemCallback1;
    private OnBackInvokedCallback mNonSystemCallback2;

    @Before
    public void setUp() {
        mImeBackCallbackSender = new ImeBackCallbackSender();
        mResultReceiver = mock(ResultReceiver.class);
        Handler handler = mock(Handler.class);
        mImeBackCallbackSender.setResultReceiver(mResultReceiver);
        mImeBackCallbackSender.setHandler(handler);
        mSystemCallback = mock(OnBackInvokedCallback.class);
        mNonSystemCallback1 = mock(OnBackInvokedCallback.class);
        mNonSystemCallback2 = mock(OnBackInvokedCallback.class);
    }

    @Test
    public void testRegisterSystemCallback() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        verify(mResultReceiver).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));
    }

    @Test
    public void testRegisterNonSystemCallback_withoutSystemCallback() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        verify(mResultReceiver, never()).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));
    }

    @Test
    public void testRegisterNonSystemCallback_withSystemCallback() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(mResultReceiver, times(2)).send(eq(RESULT_CODE_REGISTER), captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
    }

    @Test
    public void testUnregisterSystemCallback() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        mImeBackCallbackSender.unregisterOnBackInvokedCallback(mSystemCallback);
        verify(mResultReceiver).send(eq(RESULT_CODE_UNREGISTER), any(Bundle.class));
    }

    @Test
    public void testUnregisterSystemCallback_unregistersNonSystemCallbacks() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        mImeBackCallbackSender.unregisterOnBackInvokedCallback(mSystemCallback);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(mResultReceiver, times(2)).send(eq(RESULT_CODE_UNREGISTER), captor.capture());
        assertThat(captor.getAllValues()).hasSize(2);
    }

    @Test
    public void testSkipDefaultCallbackRegistration() {
        mImeBackCallbackSender.setSkipDefaultCallbackRegistration(true);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        verify(mResultReceiver, never()).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));
    }

    @Test
    public void testSkipDefaultCallbackRegistration_toggle() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        verify(mResultReceiver).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));

        mImeBackCallbackSender.setSkipDefaultCallbackRegistration(true);
        verify(mResultReceiver).send(eq(RESULT_CODE_UNREGISTER), any(Bundle.class));

        mImeBackCallbackSender.setSkipDefaultCallbackRegistration(false);
        verify(mResultReceiver, times(2)).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));
    }

    @Test
    public void testRegisterMultipleNonSystemCallbacks() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback2);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(mResultReceiver, times(3)).send(eq(RESULT_CODE_REGISTER), captor.capture());
        assertThat(captor.getAllValues()).hasSize(3);
    }

    @Test
    public void testUnregisterNonSystemCallback() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        mImeBackCallbackSender.unregisterOnBackInvokedCallback(mNonSystemCallback1);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(mResultReceiver).send(eq(RESULT_CODE_UNREGISTER), captor.capture());
        assertThat(captor.getAllValues()).hasSize(1);
    }

    @Test
    public void testRegisterNonSystemCallback_withSkipDefaultCallbackRegistration() {
        mImeBackCallbackSender.setSkipDefaultCallbackRegistration(true);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        verify(mResultReceiver, never()).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));
    }

    @Test
    public void testNonSystemCallbacks_registeredWhenSystemCallbackRegisteredAfterSkip() {
        mImeBackCallbackSender.setSkipDefaultCallbackRegistration(true);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        verify(mResultReceiver, never()).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));

        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);

        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(mResultReceiver, times(1)).send(eq(RESULT_CODE_REGISTER), captor.capture());
        assertThat(captor.getAllValues()).hasSize(1);
    }

    @Test
    public void testClear_withSkipDefaultCallbackRegistrationFalse() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback2);

        // Expect 3 REGISTER calls initially
        verify(mResultReceiver, times(3)).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));

        mImeBackCallbackSender.clear();

        // Expect 3 UNREGISTER calls after clear (system + 2 non-system)
        verify(mResultReceiver, times(3)).send(eq(RESULT_CODE_UNREGISTER), any(Bundle.class));
    }

    @Test
    public void testClear_withSkipDefaultCallbackRegistrationTrue() {
        mImeBackCallbackSender.setSkipDefaultCallbackRegistration(true);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback2);

        // Expect 2 REGISTER calls for non-system callbacks, as mSystemCallback is not null.
        verify(mResultReceiver, times(2)).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));

        mImeBackCallbackSender.clear();

        // Expect 2 UNREGISTER calls for non-system callbacks.
        verify(mResultReceiver, times(2)).send(eq(RESULT_CODE_UNREGISTER), any(Bundle.class));
    }

    @Test
    public void testNonSystemCallbackQueuing() {
        // Register non-system callback before system callback
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);

        // Verify it's not sent to app process yet
        verify(mResultReceiver, never()).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));

        // Register system callback
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);

        // Verify both system and non-system callbacks are sent for registration
        verify(mResultReceiver, times(2)).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));
    }

    @Test
    public void testReRegisteringSameNonSystemCallback() {
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_SYSTEM, mSystemCallback);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);
        mImeBackCallbackSender.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNonSystemCallback1);

        // System callback + 2 calls for the same non-system callback
        verify(mResultReceiver, times(3)).send(eq(RESULT_CODE_REGISTER), any(Bundle.class));

        // Unregister once
        mImeBackCallbackSender.unregisterOnBackInvokedCallback(mNonSystemCallback1);
        verify(mResultReceiver, times(1)).send(eq(RESULT_CODE_UNREGISTER), any(Bundle.class));

        // Unregistering again should do nothing
        mImeBackCallbackSender.unregisterOnBackInvokedCallback(mNonSystemCallback1);
        verify(mResultReceiver, times(1)).send(eq(RESULT_CODE_UNREGISTER), any(Bundle.class));
    }
}
