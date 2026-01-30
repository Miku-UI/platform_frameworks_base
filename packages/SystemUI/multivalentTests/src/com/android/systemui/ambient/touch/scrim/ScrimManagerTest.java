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

package com.android.systemui.ambient.touch.scrim;

import static android.service.dreams.Flags.FLAG_DREAMS_V2;

import static com.android.systemui.ambient.touch.TouchSurfaceKt.SURFACE_DREAM;
import static com.android.systemui.ambient.touch.TouchSurfaceKt.SURFACE_HUB;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.EnableFlags;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ScrimManagerTest extends SysuiTestCase {
    @Mock
    ScrimController mBouncerlessScrimController;

    @Mock
    ScrimController mBouncerScrimController;

    @Mock
    KeyguardStateController mKeyguardStateController;

    ScrimManager.Callback mCallback = spy(new ScrimManager.Callback() {
        @Override
        public void onScrimControllerChanged(ScrimController controller) {
            mCurrentController = controller;
        }
    });

    private ScrimController mCurrentController;

    private final FakeExecutor mExecutor = new FakeExecutor(new FakeSystemClock());

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testControllerSelection() {
        when(mKeyguardStateController.canDismissLockScreen()).thenReturn(false);
        ArgumentCaptor<KeyguardStateController.Callback> callbackCaptor =
                ArgumentCaptor.forClass(KeyguardStateController.Callback.class);
        final ScrimManager manager = new ScrimManager(mExecutor, mBouncerScrimController,
                mBouncerlessScrimController, SURFACE_HUB, mKeyguardStateController);
        verify(mKeyguardStateController, never()).addCallback(any());
        manager.addCallback(mCallback);
        mExecutor.runAllReady();
        verify(mKeyguardStateController).addCallback(callbackCaptor.capture());

        assertThat(mCurrentController).isEqualTo(mBouncerScrimController);
        when(mKeyguardStateController.canDismissLockScreen()).thenReturn(true);
        callbackCaptor.getValue().onKeyguardShowingChanged();
        mExecutor.runAllReady();
        assertThat(mCurrentController).isEqualTo(mBouncerlessScrimController);
    }

    @Test
    public void testCallback() {
        when(mKeyguardStateController.canDismissLockScreen()).thenReturn(false);
        ArgumentCaptor<KeyguardStateController.Callback> callbackCaptor =
                ArgumentCaptor.forClass(KeyguardStateController.Callback.class);
        final ScrimManager manager = new ScrimManager(mExecutor, mBouncerScrimController,
                mBouncerlessScrimController, SURFACE_HUB, mKeyguardStateController);
        verify(mKeyguardStateController, never()).addCallback(any());

        manager.addCallback(mCallback);
        mExecutor.runAllReady();
        verify(mKeyguardStateController).addCallback(callbackCaptor.capture());
        when(mKeyguardStateController.canDismissLockScreen()).thenReturn(true);
        callbackCaptor.getValue().onKeyguardShowingChanged();
        mExecutor.runAllReady();
        verify(mCallback).onScrimControllerChanged(eq(mBouncerlessScrimController));
    }

    @Test
    @EnableFlags(FLAG_DREAMS_V2)
    public void testBouncerlessControllerAlwaysUsedOnDream() {
        when(mKeyguardStateController.canDismissLockScreen()).thenReturn(false);
        ArgumentCaptor<KeyguardStateController.Callback> callbackCaptor =
                ArgumentCaptor.forClass(KeyguardStateController.Callback.class);
        final ScrimManager manager = new ScrimManager(mExecutor, mBouncerScrimController,
                mBouncerlessScrimController, SURFACE_DREAM, mKeyguardStateController);
        verify(mKeyguardStateController, never()).addCallback(any());

        manager.addCallback(mCallback);
        mExecutor.runAllReady();
        verify(mKeyguardStateController).addCallback(callbackCaptor.capture());

        assertThat(mCurrentController).isEqualTo(mBouncerlessScrimController);
        when(mKeyguardStateController.canDismissLockScreen()).thenReturn(true);
        callbackCaptor.getValue().onKeyguardShowingChanged();
        mExecutor.runAllReady();
        assertThat(mCurrentController).isEqualTo(mBouncerlessScrimController);
    }

    @Test
    public void testKeyguardStateCallbackRegistration() {
        final ScrimManager manager = new ScrimManager(mExecutor, mBouncerScrimController,
                mBouncerlessScrimController, SURFACE_DREAM, mKeyguardStateController);
        ArgumentCaptor<KeyguardStateController.Callback> callbackCaptor =
                ArgumentCaptor.forClass(KeyguardStateController.Callback.class);
        final ScrimManager.Callback firstMock = mock(ScrimManager.Callback.class);
        final ScrimManager.Callback secondMock = mock(ScrimManager.Callback.class);

        verify(mKeyguardStateController, never()).addCallback(any());
        manager.addCallback(firstMock);
        mExecutor.runAllReady();
        verify(mKeyguardStateController).addCallback(callbackCaptor.capture());
        clearInvocations(mKeyguardStateController);
        manager.addCallback(secondMock);
        mExecutor.runAllReady();
        verify(mKeyguardStateController, never()).addCallback(any());
        manager.removeCallback(firstMock);
        mExecutor.runAllReady();
        verify(mKeyguardStateController, never()).removeCallback(any());
        manager.removeCallback(secondMock);
        mExecutor.runAllReady();
        verify(mKeyguardStateController).removeCallback(callbackCaptor.getValue());
    }
}
