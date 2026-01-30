/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.doze;

import static com.android.systemui.doze.DozeMachine.State.DOZE;
import static com.android.systemui.doze.DozeMachine.State.DOZE_AOD;
import static com.android.systemui.doze.DozeMachine.State.FINISH;
import static com.android.systemui.doze.DozeMachine.State.INITIALIZED;
import static com.android.systemui.doze.DozeMachine.State.UNINITIALIZED;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.hardware.display.AmbientDisplayConfiguration;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.phone.BiometricUnlockController;

import dagger.Lazy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
@UiThreadTest
public class DozeSuppressorTest extends SysuiTestCase {

    DozeSuppressor mDozeSuppressor;
    @Mock
    private DozeLog mDozeLog;
    @Mock
    private DozeHost mDozeHost;
    @Mock
    private AmbientDisplayConfiguration mConfig;
    @Mock
    private Lazy<BiometricUnlockController> mBiometricUnlockControllerLazy;
    @Mock
    private BiometricUnlockController mBiometricUnlockController;
    @Mock
    private UserTracker mUserTracker;

    @Mock
    private DozeMachine mDozeMachine;

    @Captor
    private ArgumentCaptor<DozeHost.Callback> mDozeHostCaptor;
    private DozeHost.Callback mDozeHostCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // setup state for NOT ending doze immediately
        when(mBiometricUnlockControllerLazy.get()).thenReturn(mBiometricUnlockController);
        when(mBiometricUnlockController.hasPendingAuthentication()).thenReturn(false);
        when(mDozeHost.isProvisioned()).thenReturn(true);
        when(mUserTracker.getUserId()).thenReturn(ActivityManager.getCurrentUser());

        mDozeSuppressor = new DozeSuppressor(
                mDozeHost,
                mConfig,
                mDozeLog,
                mBiometricUnlockControllerLazy,
                mUserTracker);

        mDozeSuppressor.setDozeMachine(mDozeMachine);
    }

    @After
    public void tearDown() {
        mDozeSuppressor.destroy();
    }

    @Test
    public void testRegistersListenersOnInitialized_unregisteredOnFinish() {
        // check that receivers and callbacks registered
        mDozeSuppressor.transitionTo(UNINITIALIZED, INITIALIZED);
        captureDozeHostCallback();

        // check that receivers and callbacks are unregistered
        mDozeSuppressor.transitionTo(INITIALIZED, FINISH);
        verify(mDozeHost).removeCallback(mDozeHostCallback);
    }

    @Test
    public void testEndDoze_unprovisioned() {
        // GIVEN device unprovisioned
        when(mDozeHost.isProvisioned()).thenReturn(false);

        // WHEN dozing begins
        mDozeSuppressor.transitionTo(UNINITIALIZED, INITIALIZED);

        // THEN doze immediately ends
        verify(mDozeMachine).requestState(FINISH);
    }

    @Test
    public void testEndDoze_hasPendingUnlock() {
        // GIVEN device unprovisioned
        when(mBiometricUnlockController.hasPendingAuthentication()).thenReturn(true);

        // WHEN dozing begins
        mDozeSuppressor.transitionTo(UNINITIALIZED, INITIALIZED);

        // THEN doze immediately ends
        verify(mDozeMachine).requestState(FINISH);
    }

    @Test
    public void testPowerSaveChanged_active() {
        // GIVEN AOD power save is active and doze is initialized
        when(mDozeHost.isPowerSaveActive()).thenReturn(true);
        mDozeSuppressor.transitionTo(UNINITIALIZED, INITIALIZED);
        captureDozeHostCallback();

        // WHEN power save change gets triggered (even if active = false, since it
        // may differ from the aodPowerSaveActive state reported by DostHost)
        mDozeHostCallback.onPowerSaveChanged(false);

        // THEN the state changes to DOZE
        verify(mDozeMachine).requestState(DOZE);
    }

    @Test
    public void testPowerSaveChanged_notActive() {
        // GIVEN DOZE (not showing aod content)
        when(mConfig.alwaysOnEnabled(anyInt())).thenReturn(true);
        mDozeSuppressor.transitionTo(UNINITIALIZED, INITIALIZED);
        when(mDozeMachine.getState()).thenReturn(DOZE);
        captureDozeHostCallback();

        // WHEN power save mode is no longer active
        when(mDozeHost.isPowerSaveActive()).thenReturn(false);
        mDozeHostCallback.onPowerSaveChanged(false);

        // THEN the state changes to DOZE_AOD
        verify(mDozeMachine).requestState(DOZE_AOD);
    }


    @Test
    public void testPowerSaveChanged_notActive_dozeMachineStateNull() {
        // GIVEN DOZE (not showing aod content)
        when(mConfig.alwaysOnEnabled(anyInt())).thenReturn(true);
        mDozeSuppressor.transitionTo(UNINITIALIZED, INITIALIZED);
        when(mDozeMachine.getState()).thenReturn(null);
        captureDozeHostCallback();

        // WHEN power save mode is no longer active
        when(mDozeHost.isPowerSaveActive()).thenReturn(false);
        mDozeHostCallback.onPowerSaveChanged(false);

        // THEN the state doesn't change
        verify(mDozeMachine, never()).requestState(DOZE_AOD);
    }

    @Test
    public void testAlwaysOnSuppressedChanged_nowSuppressed() {
        // GIVEN DOZE_AOD
        when(mConfig.alwaysOnEnabled(anyInt())).thenReturn(true);
        mDozeSuppressor.transitionTo(UNINITIALIZED, INITIALIZED);
        when(mDozeMachine.getState()).thenReturn(DOZE_AOD);
        captureDozeHostCallback();

        // WHEN alwaysOnSuppressedChanged to suppressed=true
        mDozeHostCallback.onAlwaysOnSuppressedChanged(true);

        // THEN DOZE requested
        verify(mDozeMachine).requestState(DOZE);
    }

    @Test
    public void testAlwaysOnSuppressedChanged_notSuppressed() {
        // GIVEN DOZE
        when(mConfig.alwaysOnEnabled(anyInt())).thenReturn(true);
        mDozeSuppressor.transitionTo(UNINITIALIZED, INITIALIZED);
        when(mDozeMachine.getState()).thenReturn(DOZE);
        captureDozeHostCallback();

        // WHEN alwaysOnSuppressedChanged to suppressed=false
        mDozeHostCallback.onAlwaysOnSuppressedChanged(false);

        // THEN DOZE_AOD requested
        verify(mDozeMachine).requestState(DOZE_AOD);
    }

    private void captureDozeHostCallback() {
        verify(mDozeHost).addCallback(mDozeHostCaptor.capture());
        mDozeHostCallback = mDozeHostCaptor.getValue();
    }
}
