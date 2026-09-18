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

package com.android.server.audio;

import static android.media.AudioManager.AUDIOFOCUS_GAIN;
import static android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
import static android.media.AudioManager.FOCUS_ISOLATION_EXIT_LOSE_FOCUS;
import static android.media.AudioManager.FOCUS_ISOLATION_EXIT_RETAIN_FOCUS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.AudioAttributes;
import android.media.IAudioFocusDispatcher;
import android.media.audio.Flags;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for the focus isolation methods in {@link MediaFocusControl}. */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_AUDIO_FOCUS_ISOLATION)
public class FocusIsolationTest {

    private static final int UID_1 = 101;
    private static final int UID_2 = 102;
    private static final AudioAttributes AUDIO_ATTRIBUTES =
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private MediaFocusControl mMediaFocusControl;
    @Mock private PlayerFocusEnforcer mPlayerFocusEnforcer;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mMediaFocusControl = new MediaFocusControl(mPlayerFocusEnforcer, false);
    }

    @Test
    public void enterFocusIsolation_noPreviousFocus_succeeds() {
        IBinder token = new Binder();

        boolean result = mMediaFocusControl.enterFocusIsolation(UID_1, token);

        assertThat(result).isTrue();
    }

    @Test
    public void enterFocusIsolation_withPreviousFocus_removesFromFocusStack() {
        IAudioFocusDispatcher focusDispatcher = mock(IAudioFocusDispatcher.class);
        int result =
                mMediaFocusControl.requestAudioFocus(
                        UID_1,
                        AUDIO_ATTRIBUTES,
                        AUDIOFOCUS_GAIN,
                        new Binder(),
                        focusDispatcher,
                        "client_1",
                        "com.test.app1",
                        0,
                        0,
                        false,
                        0,
                        false /*isInCall*/);
        assertThat(result).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        assertThat(mMediaFocusControl.getFocusStack()).hasSize(1);
        IBinder token = new Binder();

        mMediaFocusControl.enterFocusIsolation(UID_1, token);

        assertThat(mMediaFocusControl.getFocusStack()).isEmpty();
    }

    @Test
    public void enterFocusIsolation_alreadyInIsolation_returnsFalse() {
        boolean firstResult = mMediaFocusControl.enterFocusIsolation(UID_1, new Binder());

        boolean secondResult = mMediaFocusControl.enterFocusIsolation(UID_1, new Binder());

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();
    }

    @Test
    public void requestAudioFocus_whileInIsolation_doesNotGetFocus() {
        IAudioFocusDispatcher focusDispatcher1 = mock(IAudioFocusDispatcher.class);
        mMediaFocusControl.requestAudioFocus(
                UID_1,
                AUDIO_ATTRIBUTES,
                AUDIOFOCUS_GAIN,
                new Binder(),
                focusDispatcher1,
                "client_1",
                "com.test.app1",
                0,
                0,
                false,
                0,
                false /*isInCall*/);
        IAudioFocusDispatcher focusDispatcher2 = mock(IAudioFocusDispatcher.class);
        IBinder token1 = new Binder();
        mMediaFocusControl.enterFocusIsolation(UID_2, token1);

        mMediaFocusControl.requestAudioFocus(
                UID_2,
                AUDIO_ATTRIBUTES,
                AUDIOFOCUS_GAIN,
                new Binder(),
                focusDispatcher2,
                "client_2",
                "com.test.app2",
                0,
                0,
                false,
                0,
                false /*isInCall*/);

        assertThat(mMediaFocusControl.getFocusStack()).hasSize(1);
        assertThat(mMediaFocusControl.getFocusStack().getFirst().getClientId())
                .isEqualTo("client_1");
    }

    @Test
    public void exitFocusIsolation_loseFocusMode_losesFocus() throws RemoteException {
        IAudioFocusDispatcher focusDispatcher = mock(IAudioFocusDispatcher.class);
        IBinder token = new Binder();
        mMediaFocusControl.enterFocusIsolation(UID_1, token);
        int focusRequestResult =
                mMediaFocusControl.requestAudioFocus(
                        UID_1,
                        AUDIO_ATTRIBUTES,
                        AUDIOFOCUS_GAIN,
                        new Binder(),
                        focusDispatcher,
                        "client_1",
                        "com.test.app1",
                        0,
                        0,
                        false,
                        0,
                        false /*isInCall*/);

        boolean focusIsolationExitResult =
                mMediaFocusControl.exitFocusIsolation(token, FOCUS_ISOLATION_EXIT_LOSE_FOCUS);

        assertThat(focusRequestResult).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        assertThat(focusIsolationExitResult).isTrue();
        verify(focusDispatcher).dispatchAudioFocusChange(AUDIOFOCUS_LOSS, "client_1");
    }

    @Test
    public void exitFocusIsolation_withRetainFocusAndActivePlayback_regainsFocus()
            throws RemoteException {
        IAudioFocusDispatcher focusDispatcher1 = mock(IAudioFocusDispatcher.class);
        mMediaFocusControl.requestAudioFocus(
                UID_1,
                AUDIO_ATTRIBUTES,
                AUDIOFOCUS_GAIN,
                new Binder(),
                focusDispatcher1,
                "client_1",
                "com.test.app1",
                0,
                0,
                false,
                0,
                false /*isInCall*/);
        IBinder token = new Binder();
        mMediaFocusControl.enterFocusIsolation(UID_1, token);
        IAudioFocusDispatcher focusDispatcher2 = mock(IAudioFocusDispatcher.class);
        mMediaFocusControl.requestAudioFocus(
                UID_2,
                AUDIO_ATTRIBUTES,
                AUDIOFOCUS_GAIN,
                new Binder(),
                focusDispatcher2,
                "client_2",
                "com.test.app2",
                0,
                0,
                false,
                0,
                false /*isInCall*/);
        when(mPlayerFocusEnforcer.isPlaybackActiveForUid(UID_1)).thenReturn(true);

        boolean exitFocusResult =
                mMediaFocusControl.exitFocusIsolation(token, FOCUS_ISOLATION_EXIT_RETAIN_FOCUS);

        assertThat(exitFocusResult).isTrue();
        assertThat(mMediaFocusControl.getFocusStack()).hasSize(1);
        assertThat(mMediaFocusControl.getFocusStack().get(0).getClientId()).isEqualTo("client_1");
        verify(focusDispatcher2).dispatchAudioFocusChange(AUDIOFOCUS_LOSS, "client_2");
    }

    @Test
    public void exitFocusIsolation_retainFocusWithoutActivePlayback_losesFocus()
            throws RemoteException {
        IAudioFocusDispatcher focusDispatcher = mock(IAudioFocusDispatcher.class);
        IBinder token = new Binder();
        mMediaFocusControl.enterFocusIsolation(UID_1, token);
        mMediaFocusControl.requestAudioFocus(
                UID_1,
                AUDIO_ATTRIBUTES,
                AUDIOFOCUS_GAIN,
                new Binder(),
                focusDispatcher,
                "client_1",
                "com.test.app1",
                0,
                0,
                false,
                0,
                false /*isInCall*/);
        when(mPlayerFocusEnforcer.isPlaybackActiveForUid(UID_1)).thenReturn(false);

        assertTrue(mMediaFocusControl.exitFocusIsolation(token, FOCUS_ISOLATION_EXIT_RETAIN_FOCUS));

        assertThat(mMediaFocusControl.getFocusStack()).isEmpty();
        verify(focusDispatcher).dispatchAudioFocusChange(AUDIOFOCUS_LOSS, "client_1");
    }

    @Test
    public void exitFocusIsolation_noIsolationRecord_returnsFalse() {
        assertFalse(
                mMediaFocusControl.exitFocusIsolation(
                        new Binder(), FOCUS_ISOLATION_EXIT_LOSE_FOCUS));
    }

    @Test
    public void requestAudioFocus_whileInIsolationWithExistingRequest_replacesOldRequest()
            throws RemoteException {
        IAudioFocusDispatcher focusDispatcher1 = mock(IAudioFocusDispatcher.class);
        IBinder token = new Binder();
        mMediaFocusControl.enterFocusIsolation(UID_1, token);
        int result1 =
                mMediaFocusControl.requestAudioFocus(
                        UID_1,
                        AUDIO_ATTRIBUTES,
                        AUDIOFOCUS_GAIN,
                        new Binder(),
                        focusDispatcher1,
                        "client_1",
                        "com.test.app1",
                        0,
                        0,
                        false,
                        0,
                        false /*isInCall*/);
        IAudioFocusDispatcher focusDispatcher2 = mock(IAudioFocusDispatcher.class);

        int result2 =
                mMediaFocusControl.requestAudioFocus(
                        UID_1,
                        AUDIO_ATTRIBUTES,
                        AUDIOFOCUS_GAIN,
                        new Binder(),
                        focusDispatcher2,
                        "client_2",
                        "com.test.app1",
                        0,
                        0,
                        false,
                        0,
                        false /*isInCall*/);

        assertThat(result1).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        assertThat(result2).isEqualTo(AUDIOFOCUS_REQUEST_GRANTED);
        verify(focusDispatcher1).dispatchAudioFocusChange(AUDIOFOCUS_LOSS, "client_1");
    }

    @Test
    public void enterIsolation_whileMultipleActiveRequests_releasesNonGain()
            throws RemoteException {
        IAudioFocusDispatcher focusDispatcher1 = mock(IAudioFocusDispatcher.class);
        mMediaFocusControl.requestAudioFocus(
                UID_1,
                AUDIO_ATTRIBUTES,
                AUDIOFOCUS_GAIN,
                new Binder(),
                focusDispatcher1,
                "client_1",
                "com.test.app",
                0,
                0,
                false,
                0,
                false /*isInCall*/);
        IAudioFocusDispatcher focusDispatcher2 = mock(IAudioFocusDispatcher.class);
        mMediaFocusControl.requestAudioFocus(
                UID_1,
                AUDIO_ATTRIBUTES,
                AUDIOFOCUS_GAIN_TRANSIENT,
                new Binder(),
                focusDispatcher2,
                "client_2",
                "com.test.app",
                0,
                0,
                false,
                0,
                false /*isInCall*/);

        mMediaFocusControl.enterFocusIsolation(UID_1, new Binder());

        verify(focusDispatcher1).dispatchAudioFocusChange(AUDIOFOCUS_GAIN, "client_1");
        verify(focusDispatcher2).dispatchAudioFocusChange(eq(AUDIOFOCUS_LOSS), anyString());
        assertTrue(mMediaFocusControl.getFocusStack().isEmpty());
    }
}
