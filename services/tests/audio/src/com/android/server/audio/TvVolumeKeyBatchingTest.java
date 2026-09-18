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

package com.android.server.audio;

import static android.media.AudioManager.DEVICE_OUT_SPEAKER;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.audio.Flags.FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS;

import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioDeviceAttributes;
import android.media.AudioSystem;
import android.os.IpcDataCache;
import android.os.PermissionEnforcer;
import android.os.test.TestLooper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.KeyEvent;

import androidx.test.core.app.ApplicationProvider;

import com.android.internal.R;

import libcore.junit.util.compat.CoreCompatChangeRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

@Presubmit
public class TvVolumeKeyBatchingTest {
    @Rule public TestRule compatChangeRule = new CoreCompatChangeRule();

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String TAG = "VolumeKeyBatchingTest";

    private Context mContext;
    private Resources mResources;
    private String mPackageName;
    private AudioSystemAdapter mSpyAudioSystem;
    private SystemServerAdapter mSystemServer;
    private SettingsAdapter mSettingsAdapter;
    private AudioVolumeGroupHelperBase mAudioVolumeGroupHelper;
    private TestLooper mTestLooper;

    private AudioService mAudioService;

    private final AudioPolicyFacade mMockAudioPolicy = mock(AudioPolicyFacade.class);

    @Before
    public void setUp() throws Exception {

        IpcDataCache.disableForTestMode();
        mTestLooper = new TestLooper();

        mContext = spy(ApplicationProvider.getApplicationContext());
        mResources = spy(mContext.getResources());
        mPackageName = mContext.getOpPackageName();

        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getBoolean(R.bool.config_useFixedVolume))
                .thenReturn(false);

        when(mContext.checkCallingOrSelfPermission(
                 Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED))
                 .thenReturn(PackageManager.PERMISSION_GRANTED);

        mSpyAudioSystem = spy(new NoOpAudioSystemAdapter());
        mSystemServer = new NoOpSystemServerAdapter();
        mSettingsAdapter = new NoOpSettingsAdapter();
        mAudioVolumeGroupHelper = new AudioVolumeGroupHelperBase();
    }

    private void prepareAudioService(
            int longPressVolumeKeysPerAdjustment, float longPressVolumeAdjustmentScaleFactor) {
        if (android.os.Looper.myLooper() == null) {
            android.os.Looper.prepare();
        }

        doReturn(longPressVolumeKeysPerAdjustment).when(mResources)
                .getInteger(R.integer.config_tvLongPressVolumeKeysPerAdjustment);
        doReturn(longPressVolumeAdjustmentScaleFactor).when(mResources)
                .getFloat(R.dimen.config_tvLongPressVolumeAdjustmentScaleFactor);

        mAudioService = new AudioService(mContext, mSpyAudioSystem, mSystemServer,
                mSettingsAdapter, mAudioVolumeGroupHelper, mMockAudioPolicy,
                mTestLooper.getLooper(), mock(AppOpsManager.class),
                mock(PermissionEnforcer.class),
                mock(AudioServerPermissionProvider.class), Runnable::run) {
            @Override
            public int getDeviceForStream(int stream, boolean selectAbsoluteDevices) {
                return AudioSystem.DEVICE_OUT_SPEAKER;
            }
            @Override
            public AudioDeviceAttributes getDeviceAttributesForStream(int stream,
                    boolean selectAbsoluteDevices) {
                    return new AudioDeviceAttributes(DEVICE_OUT_SPEAKER, "speaker");
            }
        };

        AudioService.MIN_STREAM_VOLUME[AudioSystem.STREAM_MUSIC] = 0;
        AudioService.MAX_STREAM_VOLUME[AudioSystem.STREAM_MUSIC] = 100;

        mTestLooper.dispatchAll();
    }

    @Test
    @DisableFlags({FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS})
    public void flagDisabled_longPressBehaviorNotAffected() {
        // Halved frequency of long press volume adjustments; 5x magnitude
        prepareAudioService(2, 5.0f);

        // Set initial volume level
        mAudioService.setStreamVolume(STREAM_MUSIC, 0, 0, mPackageName);
        mTestLooper.dispatchAll();

        // 1. First key in long press triggers 1x volume adjustment
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(1), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 2. Second key in long press triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(2), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 3. Third key in long press triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(3), eq(false), eq(DEVICE_OUT_SPEAKER));
    }

    @Test
    @EnableFlags({FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS})
    public void defaultConfig_longPressBehaviorNotAffected() {
        // Default config: does not change volume adjustment logic
        prepareAudioService(1, 1.0f);

        // Set initial volume level
        mAudioService.setStreamVolume(STREAM_MUSIC, 0, 0, mPackageName);
        mTestLooper.dispatchAll();

        // 1. First key in long press triggers 1x volume adjustment
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(1), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 2. Second key in long press triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(2), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 3. Third key in long press triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(3), eq(false), eq(DEVICE_OUT_SPEAKER));
    }

    @Test
    @EnableFlags({FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS})
    public void adjustedConfig_batchesAndScalesLongPresses() {
        // Halved frequency of long press volume adjustments; 5x magnitude
        prepareAudioService(2, 5.0f);

        // Set initial volume level
        mAudioService.setStreamVolume(STREAM_MUSIC, 0, 0, mPackageName);
        mTestLooper.dispatchAll();

        // 1. First key in long press triggers an unscaled volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(1), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 2. Second key triggers no volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem, never()).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), anyInt(), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 3. Third key triggers 5x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(6), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 4. Fourth key triggers no volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem, never()).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), anyInt(), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 5. Fifth key triggers 5x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(11), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 6. Key release triggers no adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem, never()).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), anyInt(), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 7. After key release, first key (volume down) triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(10), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 8. After key release, second key triggers no volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem, never()).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), anyInt(), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 9. After key release, third key triggers 5x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(5), eq(false), eq(DEVICE_OUT_SPEAKER));
    }

    @Test
    @EnableFlags({FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS})
    public void longPressVolumeUp_doesNotOvershootMaxVolume() {
        // Halved frequency of long press volume adjustments; 5x magnitude
        prepareAudioService(2, 5.0f);

        // Set initial volume level
        mAudioService.setStreamVolume(STREAM_MUSIC, 97, 0, mPackageName);
        mTestLooper.dispatchAll();

        // 1. First key in long press triggers an unscaled volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(98), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 2. Second key triggers no volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem, never()).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), anyInt(), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 3. Third key triggers 5x volume adjustment, but does not overshoot the max of 100
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(100), eq(false), eq(DEVICE_OUT_SPEAKER));
    }


    @Test
    @EnableFlags({FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS})
    public void longPressVolumeDown_doesNotOvershootMinVolume() {
        // Halved frequency of long press volume adjustments; 5x magnitude
        prepareAudioService(2, 5.0f);

        // Set initial volume level
        mAudioService.setStreamVolume(STREAM_MUSIC, 4, 0, mPackageName);
        mTestLooper.dispatchAll();

        // 1. First key in long press triggers an unscaled volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(3), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 2. Second key triggers no volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem, never()).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), anyInt(), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 3. Third key triggers 5x volume adjustment, but does not overshoot the min of 0
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(0), eq(false), eq(DEVICE_OUT_SPEAKER));
    }

    /**
     * If we call handleVolumeKy with isOnTv == false, the config should have no effect.
     */
    @Test
    @EnableFlags({FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS})
    public void configOnlyAffectsTv() {
        // Halved frequency of long press volume adjustments; 5x magnitude
        prepareAudioService(2, 5.0f);

        // Set initial volume level
        mAudioService.setStreamVolume(STREAM_MUSIC, 0, 0, mPackageName);
        mTestLooper.dispatchAll();

        // 1. First key in long press triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                false, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(1), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 2. Second key triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                false, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(2), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 3. Third key triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                false, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(3), eq(false), eq(DEVICE_OUT_SPEAKER));
    }

    @Test
    @EnableFlags({FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS})
    public void configDoesNotAffectShortPresses() {
        // Halved frequency of long press volume adjustments; 5x magnitude
        prepareAudioService(2, 5.0f);

        // Set initial volume level
        mAudioService.setStreamVolume(STREAM_MUSIC, 0, 0, mPackageName);
        mTestLooper.dispatchAll();

        // 1. Key press triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(1), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 2. Key release triggers no volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem, never()).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), anyInt(), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 3. Key press triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(2), eq(false), eq(DEVICE_OUT_SPEAKER));
    }

    /**
     * Normally, the system should release an ongoing long press (KeyEvent.ACTION_UP) before
     * switching the volume adjustment direction. If this doesn't happen somehow, we should still
     * restart the batching logic for the new volume adjustment direction.
     */
    @Test
    @EnableFlags({FLAG_TV_VOLUME_KEY_BATCHING_DURING_LONG_PRESS})
    public void directionSwitchDuringLongPress_restartsBatchingLogic() {
        // Halved frequency of long press volume adjustments; 5x magnitude
        prepareAudioService(2, 5.0f);

        // Set initial volume level
        mAudioService.setStreamVolume(STREAM_MUSIC, 0, 0, mPackageName);
        mTestLooper.dispatchAll();

        // 1. First key in long press triggers 1x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(1), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 2. Second key triggers no volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem, never()).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), anyInt(), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 3. Third key triggers 5x volume adjustment
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(6), eq(false), eq(DEVICE_OUT_SPEAKER));

        // 4. Unexpectedly switched direction to volume down, without a preceding key release.
        //    Triggers 1x volume adjustment.
        reset(mSpyAudioSystem);
        mAudioService.handleVolumeKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN),
                true, mPackageName, "test");
        mTestLooper.dispatchAll();
        verify(mSpyAudioSystem).setStreamVolumeIndexAS(
                eq(STREAM_MUSIC), eq(5), eq(false), eq(DEVICE_OUT_SPEAKER));
    }
}
