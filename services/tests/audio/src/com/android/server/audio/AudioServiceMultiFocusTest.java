/*
 * Copyright 2026 The Android Open Source Project
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
package com.android.server.audio;

import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.UserProperties;
import android.media.AudioAttributes;
import android.media.IAudioFocusDispatcher;
import android.os.Binder;
import android.os.IBinder;
import android.os.IpcDataCache;
import android.os.PermissionEnforcer;
import android.os.test.TestLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
public class AudioServiceMultiFocusTest {
    private static final AudioAttributes AUDIO_ATTRIBUTES = new AudioAttributes.Builder().setUsage(
            AudioAttributes.USAGE_MEDIA).build();
    private Context mContext;
    private AudioService mAudioService;

    @Before
    public void setUp() {
        IpcDataCache.disableForTestMode();

        UserManagerInternal userManagerInternal = mock(UserManagerInternal.class);
        UserProperties userProperties = new UserProperties.Builder().build();
        when(userManagerInternal.getUserProperties(anyInt())).thenReturn(userProperties);
        LocalServices.addService(UserManagerInternal.class, userManagerInternal);

        TestLooper testLooper = new TestLooper();
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mAudioService = new AudioService(mContext, new NoOpAudioSystemAdapter(),
                new NoOpSystemServerAdapter(), new NoOpSettingsAdapter(),
                new AudioVolumeGroupHelperBase(), mock(AudioPolicyFacade.class),
                testLooper.getLooper(), mock(AppOpsManager.class), mock(PermissionEnforcer.class),
                mock(AudioServerPermissionProvider.class), Runnable::run);

        testLooper.dispatchAll();
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(UserManagerInternal.class);
    }

    @Test
    public void createFocusEnvironment_withValidToken_succeeds() throws Exception {
        IBinder focusEnvToken = mock(IBinder.class);
        ArgumentCaptor<IBinder.DeathRecipient> recipientCaptor = ArgumentCaptor.forClass(
                IBinder.DeathRecipient.class);

        boolean created = mAudioService.createFocusEnvironment(focusEnvToken);

        assertWithMessage("Focus environment should be created successfully").that(
                created).isTrue();
        verify(focusEnvToken).linkToDeath(recipientCaptor.capture(), anyInt());
        assertWithMessage("DeathRecipient should be linked").that(
                recipientCaptor.getValue()).isNotNull();
    }

    @Test
    public void createFocusEnvironment_withExistingToken_fails() {
        IBinder focusEnvToken = mock(IBinder.class);
        mAudioService.createFocusEnvironment(focusEnvToken);

        boolean reCreated = mAudioService.createFocusEnvironment(focusEnvToken);

        assertWithMessage("Re-creating environment should fail").that(reCreated).isFalse();
    }

    @Test
    public void destroyFocusEnvironment_withValidToken_succeeds() throws Exception {
        IBinder envToken = mock(IBinder.class);
        ArgumentCaptor<IBinder.DeathRecipient> recipientCaptor = ArgumentCaptor.forClass(
                IBinder.DeathRecipient.class);
        mAudioService.createFocusEnvironment(envToken);
        verify(envToken).linkToDeath(recipientCaptor.capture(), anyInt());

        boolean destroyed = mAudioService.destroyFocusEnvironment(envToken);

        assertWithMessage("Focus environment should be destroyed successfully")
                .that(destroyed).isTrue();
        verify(envToken).unlinkToDeath(eq(recipientCaptor.getValue()), anyInt());
        assertWithMessage("Environment should be removed from map after destruction")
                .that(mAudioService.getMediaFocusControlForEnvironmentFromMap(envToken)).isNull();
    }

    @Test
    public void destroyFocusEnvironment_withNonExistentToken_fails() {
        IBinder envToken = mock(IBinder.class);

        boolean destroyed = mAudioService.destroyFocusEnvironment(envToken);

        assertWithMessage("Destroying non-existent environment should fail").that(
                destroyed).isFalse();
    }

    @Test
    public void requestAudioFocus_differentEnvironments_grantsFocusIndependently()
            throws Exception {
        IBinder focusToken1 = new Binder("focusEnv1");
        IBinder focusToken2 = new Binder("focusEnv2");
        mAudioService.createFocusEnvironment(focusToken1);
        mAudioService.createFocusEnvironment(focusToken2);
        IAudioFocusDispatcher dispatcherMain = mock(IAudioFocusDispatcher.class);
        IAudioFocusDispatcher dispatcher1 = mock(IAudioFocusDispatcher.class);
        IAudioFocusDispatcher dispatcher2 = mock(IAudioFocusDispatcher.class);

        int resultMain = requestAudioFocus(null, new Binder(), dispatcherMain, "client-main",
                AUDIOFOCUS_GAIN);
        int result1 = requestAudioFocus(focusToken1, new Binder(), dispatcher1, "client-1",
                AUDIOFOCUS_GAIN);
        int result2 = requestAudioFocus(focusToken2, new Binder(), dispatcher2, "client-2",
                AUDIOFOCUS_GAIN);

        assertThat(resultMain).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        assertThat(result1).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        assertThat(result2).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        verify(dispatcherMain, never()).dispatchAudioFocusChange(anyInt(), anyString());
        verify(dispatcher1, never()).dispatchAudioFocusChange(anyInt(), anyString());
        verify(dispatcher2, never()).dispatchAudioFocusChange(anyInt(), anyString());
    }

    @Test
    public void createFocusEnvironment_addsEnvironmentToMap() throws Exception {
        IBinder envToken = mock(IBinder.class);
        ArgumentCaptor<IBinder.DeathRecipient> recipientCaptor = ArgumentCaptor.forClass(
                IBinder.DeathRecipient.class);

        mAudioService.createFocusEnvironment(envToken);

        verify(envToken).linkToDeath(recipientCaptor.capture(), anyInt());
        assertWithMessage("DeathRecipient should be linked at environment creation")
                .that(recipientCaptor.getValue()).isNotNull();
        assertWithMessage("Environment should be added to the map").that(
                mAudioService.getMediaFocusControlForEnvironmentFromMap(envToken)).isNotNull();
    }

    @Test
    public void destroyFocusEnvironment_binderDeath_removesEnvironmentFromMap() throws Exception {
        IBinder envToken = mock(IBinder.class);
        createEnvironmentAndSimulateBinderDeath(envToken);

        assertWithMessage("Environment should be removed after binder death").that(
                mAudioService.getMediaFocusControlForEnvironmentFromMap(envToken)).isNull();
    }

    @Test
    public void destroyFocusEnvironment_afterBinderDeath_fails() throws Exception {
        IBinder envToken = mock(IBinder.class);
        createEnvironmentAndSimulateBinderDeath(envToken);

        boolean destroyed = mAudioService.destroyFocusEnvironment(envToken);

        assertWithMessage("Manual destruction should fail after simulated binder death").that(
                destroyed).isFalse();
    }

    @Test
    public void createFocusEnvironment_afterBinderDeath_succeeds() throws Exception {
        IBinder envToken = mock(IBinder.class);
        createEnvironmentAndSimulateBinderDeath(envToken);

        boolean createdAgain = mAudioService.createFocusEnvironment(envToken);

        assertWithMessage(
                "Environment should be creatable after simulated binder death cleanup").that(
                createdAgain).isTrue();
    }

    @Test
    public void requestAudioFocus_sameEnvironment_transfersFocus() throws Exception {
        IBinder envToken = new Binder("focusEnv");
        String clientId1 = "client-1";
        IBinder clientId1Binder = new Binder(clientId1);
        IAudioFocusDispatcher dispatcher1 = mock(IAudioFocusDispatcher.class);
        String clientId2 = "client-2";
        IBinder clientId2Binder = new Binder(clientId2);
        IAudioFocusDispatcher dispatcher2 = mock(IAudioFocusDispatcher.class);
        mAudioService.createFocusEnvironment(envToken);

        int res1 = requestAudioFocus(envToken, clientId1Binder, dispatcher1, clientId1,
                AUDIOFOCUS_GAIN);
        int res2 = requestAudioFocus(envToken, clientId2Binder, dispatcher2, clientId2,
                AUDIOFOCUS_GAIN);

        assertThat(res1).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        assertThat(res2).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        verify(dispatcher1).dispatchAudioFocusChange(AUDIOFOCUS_LOSS, clientId1);
        verify(dispatcher2, never()).dispatchAudioFocusChange(anyInt(), anyString());
    }

    @Test
    public void requestAudioFocus_separateEnvironment_doesNotAffectDefaultEnvironment()
            throws Exception {
        IBinder envToken = new Binder("focusEnv");
        String clientIdDef = "client-def";
        IBinder clientIdDefBinder = new Binder(clientIdDef);
        IAudioFocusDispatcher dispatcherDef = mock(IAudioFocusDispatcher.class);
        String clientId1 = "client-1";
        IBinder clientId1Binder = new Binder(clientId1);
        IAudioFocusDispatcher dispatcher1 = mock(IAudioFocusDispatcher.class);
        mAudioService.createFocusEnvironment(envToken);

        int resDef = requestAudioFocus(null, clientIdDefBinder, dispatcherDef, clientIdDef,
                AUDIOFOCUS_GAIN);
        int res1 = requestAudioFocus(envToken, clientId1Binder, dispatcher1, clientId1,
                AUDIOFOCUS_GAIN);

        assertThat(resDef).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        assertThat(res1).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        verify(dispatcherDef, never()).dispatchAudioFocusChange(anyInt(), anyString());
    }

    @Test
    public void requestAudioFocus_defaultEnvironment_doesNotAffectSeparateEnvironment()
            throws Exception {
        IBinder envToken = new Binder("focusEnv");
        String clientId1 = "client-1";
        IBinder clientId1Binder = new Binder(clientId1);
        IAudioFocusDispatcher dispatcher1 = mock(IAudioFocusDispatcher.class);
        String clientIdDef = "client-def";
        IBinder clientIdDefBinder = new Binder(clientIdDef);
        IAudioFocusDispatcher dispatcherDef = mock(IAudioFocusDispatcher.class);
        String clientIdDef2 = "client-def-2";
        IBinder clientIdDef2Binder = new Binder(clientIdDef2);
        IAudioFocusDispatcher dispatcherDef2 = mock(IAudioFocusDispatcher.class);
        mAudioService.createFocusEnvironment(envToken);

        int res1 = requestAudioFocus(envToken, clientId1Binder, dispatcher1, clientId1,
                AUDIOFOCUS_GAIN);
        int resDef = requestAudioFocus(null, clientIdDefBinder, dispatcherDef, clientIdDef,
                AUDIOFOCUS_GAIN);

        assertThat(res1).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        assertThat(resDef).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);

        int resDef2 = requestAudioFocus(null, clientIdDef2Binder, dispatcherDef2, clientIdDef2,
                AUDIOFOCUS_GAIN);

        assertThat(resDef2).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        verify(dispatcherDef).dispatchAudioFocusChange(AUDIOFOCUS_LOSS, clientIdDef);
        verify(dispatcher1, never()).dispatchAudioFocusChange(anyInt(), anyString());
    }

    // make an audio focus request with a specified focusEnvToken
    private int requestAudioFocus(IBinder focusEnvToken, IBinder clientBinder,
            IAudioFocusDispatcher dispatcher, String clientId, int focusGain) {
        return mAudioService.requestAudioFocus(AUDIO_ATTRIBUTES, focusGain, clientBinder,
                dispatcher, clientId, mContext.getOpPackageName(), null, 0, null,
                android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE, focusEnvToken);
    }

    // set up and simulate binder death for a focus environment token
    private void createEnvironmentAndSimulateBinderDeath(IBinder envToken) throws Exception {
        ArgumentCaptor<IBinder.DeathRecipient> recipientCaptor = ArgumentCaptor.forClass(
                IBinder.DeathRecipient.class);

        mAudioService.createFocusEnvironment(envToken);
        verify(envToken).linkToDeath(recipientCaptor.capture(), anyInt());
        recipientCaptor.getValue().binderDied(envToken);
    }
}
