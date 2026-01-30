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

package com.android.server.media;

import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;

import static com.android.server.media.AudioRoutingUtils.ATTRIBUTES_MEDIA;
import static com.android.server.media.AudioRoutingUtils.getMediaAudioProductStrategy;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioDevicePort;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.MediaRoute2Info;
import android.media.RoutingSessionInfo;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.HandlerThread;
import android.os.TestLooperManager;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;
import com.android.media.flags.Flags;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(JUnit4.class)
public class AudioManagerRouteControllerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String FAKE_ROUTE_NAME = "fake name";
    private static final String FAKE_BT_ROUTE_ADDRESS = "11:22:33:44:55:66";

    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_SPEAKER, "name_builtin", /* address= */ "");
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_WIRED_HEADSET, "name_wired_hs", /* address= */ "");
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET_WITH_ADDRESS =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_WIRED_HEADSET,
                    "name_wired_hs_with_address",
                    /* address= */ "card=1;device=0");
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, "name_a2dp", /* address= */ "12:34:45");

    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_BUILTIN_EARPIECE =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_EARPIECE, /* name= */ "", /* address= */ "");

    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_NO_NAME =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_DGTL_DOCK_HEADSET,
                    /* name= */ "",
                    /* address= */ "");

    private static final String FAKE_AUDIO_DEVICE_LE_HEADSET_1_ID = "LE_AUDIO_1";
    private static final String FAKE_AUDIO_DEVICE_LE_HEADSET_2_ID = "LE_AUDIO_2";
    private static final String FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME = "name_le_headset_1";
    private static final String FAKE_AUDIO_DEVICE_LE_HEADSET_2_NAME = "name_le_headset_2";
    private static final String FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS = "address_le_headset_1";
    private static final String FAKE_AUDIO_DEVICE_LE_HEADSET_2_ADDRESS = "address_le_headset_2";
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_LE_HEADSET_1_BROADCASTING =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_BLE_BROADCAST,
                    FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME,
                    FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS);
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_LE_HEADSET_1 =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_BLE_HEADSET,
                    FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME,
                    FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS);
    private static final AudioDeviceInfo FAKE_AUDIO_DEVICE_LE_HEADSET_2 =
            createAudioDeviceInfo(
                    AudioSystem.DEVICE_OUT_BLE_HEADSET,
                    FAKE_AUDIO_DEVICE_LE_HEADSET_2_NAME,
                    FAKE_AUDIO_DEVICE_LE_HEADSET_2_ADDRESS);
    private Instrumentation mInstrumentation;
    private AudioDeviceInfo mSelectedAudioDeviceInfo;
    private Set<AudioDeviceInfo> mAvailableAudioDeviceInfos;
    @Mock private AudioManager mMockAudioManager;
    @Mock private DeviceRouteController.EventListener mEventListener;
    @Mock private BluetoothDeviceRoutesManager mMockBluetoothDeviceRoutesManager;
    @Mock private Context mMockContext;
    @Mock private ContentResolver mContentResolver;
    private Context mRealContext;
    private HandlerThread mThread;
    private TestLooperManager mLooperManager;
    private AudioManagerRouteController mControllerUnderTest;
    private AudioDeviceCallback mAudioDeviceCallback;
    private AudioProductStrategy mMediaAudioProductStrategy;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED, MODIFY_AUDIO_ROUTING);
        Resources mockResources = Mockito.mock(Resources.class);
        when(mockResources.getText(anyInt())).thenReturn(FAKE_ROUTE_NAME);
        when(mockResources.getInteger(R.integer.config_audio_sharing_maximum_sinks)).thenReturn(2);
        mRealContext = mInstrumentation.getContext();
        when(mMockContext.getResources()).thenReturn(mockResources);
        // The bluetooth stack needs the application info, but we cannot use a spy because the
        // concrete class is package private, so we just return the application info through the
        // mock.
        when(mMockContext.getApplicationInfo()).thenReturn(mRealContext.getApplicationInfo());
        // Needed to check if it is a TV device.
        when(mMockContext.getPackageManager()).thenReturn(mRealContext.getPackageManager());
        when(mMockContext.getContentResolver()).thenReturn(mContentResolver);
        mThread = new HandlerThread("AudioManagerRouteControllerTestThread");
        mThread.start();
        mLooperManager = mInstrumentation.acquireLooperManager(mThread.getLooper());

        // Setup the initial state so that the route controller is created in a sensible state.
        mSelectedAudioDeviceInfo = FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER;
        mAvailableAudioDeviceInfos = Set.of(FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER);
        updateMockAudioManagerState();
        mMediaAudioProductStrategy = getMediaAudioProductStrategy();

        // Need call setUpControllerUnderTest before each test case.
    }

    @After
    public void tearDown() {
        if (mLooperManager != null) {
            mLooperManager.release();
        }
        if (mThread != null) {
            mThread.quitSafely();
        }
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void getSelectedRoute_afterDevicesConnect_returnsRightSelectedRoute() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP);
        verify(mEventListener).onDeviceRouteChanged();
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ null, // Selected device doesn't change.
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);
    }

    @Test
    public void getSelectedRoute_afterDeviceRemovals_returnsExpectedRoutes() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        verify(mEventListener).onDeviceRouteChanged();

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP);
        verify(mEventListener, times(2)).onDeviceRouteChanged();
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);

        removeAvailableAudioDeviceInfos(
                /* newSelectedDevice= */ null,
                /* devicesToRemove...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);

        removeAvailableAudioDeviceInfos(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER,
                /* devicesToRemove...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
    }

    @Test
    public void onAudioDevicesAdded_clearsAudioRoutingPoliciesCorrectly() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        clearInvocations(mMockAudioManager);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ null, // Selected device doesn't change.
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_BUILTIN_EARPIECE);
        verifyNoMoreInteractions(mMockAudioManager);

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP);
        verify(mMockAudioManager).removePreferredDeviceForStrategy(mMediaAudioProductStrategy);
    }

    @Test
    public void getAvailableDevices_ignoresInvalidMediaOutputs() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ null, // Selected device doesn't change.
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_BUILTIN_EARPIECE);
        verifyNoMoreInteractions(mEventListener);
        assertThat(
                        mControllerUnderTest.getAvailableRoutes().stream()
                                .map(MediaRoute2Info::getType)
                                .toList())
                .containsExactly(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getType())
                .isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
    }

    @Test
    public void transferTo_setsTheExpectedRoutingPolicy() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        MediaRoute2Info builtInSpeakerRoute =
                getAvailableRouteWithType(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
        mControllerUnderTest.transferTo(/* requestId= */ 0L, builtInSpeakerRoute.getId());
        mLooperManager.execute(mLooperManager.next());
        verify(mMockAudioManager)
                .setPreferredDeviceForStrategy(
                        mMediaAudioProductStrategy,
                        createAudioDeviceAttribute(
                                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, /* address= */ ""));

        MediaRoute2Info wiredHeadsetRoute =
                getAvailableRouteWithType(MediaRoute2Info.TYPE_WIRED_HEADSET);
        mControllerUnderTest.transferTo(/* requestId= */ 0L, wiredHeadsetRoute.getId());
        mLooperManager.execute(mLooperManager.next());
        verify(mMockAudioManager)
                .setPreferredDeviceForStrategy(
                        mMediaAudioProductStrategy,
                        createAudioDeviceAttribute(
                                AudioDeviceInfo.TYPE_WIRED_HEADSET, /* address= */ ""));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void transferToNonBtDevice_inBroadcast_stopsBroadcastAndSetsTheExpectedRoutingPolicy() {
        setUpControllerAndLEAudioMocks();
        when(mMockBluetoothDeviceRoutesManager.isLEAudioBroadcastSupported()).thenReturn(true);
        when(mMockBluetoothDeviceRoutesManager.isBtRoute(any())).thenReturn(false);
        when(mMockBluetoothDeviceRoutesManager.getBroadcastingDeviceRoutes())
                .thenReturn(
                        List.of(
                                createMediaRoute2Info(
                                        /* id= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_ID,
                                        /* name= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME,
                                        /* address= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS,
                                        /* volume= */ 0)));
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_BROADCASTING,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_BROADCASTING,
                FAKE_AUDIO_DEVICE_LE_HEADSET_2);

        clearInvocations(mMockAudioManager);
        MediaRoute2Info builtInSpeakerRoute =
                getAvailableRouteWithType(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
        mControllerUnderTest.transferTo(/* requestId= */ 0L, builtInSpeakerRoute.getId());

        mLooperManager.execute(mLooperManager.next());
        verify(mMockBluetoothDeviceRoutesManager).stopBroadcast(null);
        mLooperManager.execute(mLooperManager.next());
        verify(mMockAudioManager)
                .setPreferredDeviceForStrategy(
                        mMediaAudioProductStrategy,
                        createAudioDeviceAttribute(
                                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, /* address= */ ""));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void transferToBtDevice_inBroadcast_stopsBroadcastWithoutSettingRoutingPolicy() {
        setUpControllerAndLEAudioMocks();
        when(mMockBluetoothDeviceRoutesManager.isLEAudioBroadcastSupported()).thenReturn(true);
        when(mMockBluetoothDeviceRoutesManager.isBtRoute(any())).thenReturn(true);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP,
                FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);
        when(mMockBluetoothDeviceRoutesManager.getBroadcastingDeviceRoutes())
                .thenReturn(
                        List.of(
                                createMediaRoute2Info(
                                        /* id= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_ID,
                                        /* name= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME,
                                        /* address= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS,
                                        /* volume= */ 0)));
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_BROADCASTING,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_BROADCASTING,
                FAKE_AUDIO_DEVICE_LE_HEADSET_2);

        clearInvocations(mMockAudioManager);
        MediaRoute2Info a2dpBluetoothRoute =
                getAvailableRouteWithType(MediaRoute2Info.TYPE_BLUETOOTH_A2DP);
        mControllerUnderTest.transferTo(/* requestId= */ 0L, a2dpBluetoothRoute.getId());

        mLooperManager.execute(mLooperManager.next());
        verify(mMockBluetoothDeviceRoutesManager).stopBroadcast(a2dpBluetoothRoute.getId());
        verify(mMockBluetoothDeviceRoutesManager, never())
                .activateBluetoothDeviceWithAddress(a2dpBluetoothRoute.getAddress());
        verify(mMockAudioManager, never())
                .removePreferredDeviceForStrategy(mMediaAudioProductStrategy);
    }

    @Test
    public void updateVolume_propagatesCorrectlyToRouteInfo() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        when(mMockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(2);
        when(mMockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(3);
        when(mMockAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)).thenReturn(1);
        when(mMockAudioManager.isVolumeFixed()).thenReturn(false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);

        MediaRoute2Info selectedRoute = mControllerUnderTest.getSelectedRoutes().getFirst();
        assertThat(selectedRoute.getType()).isEqualTo(MediaRoute2Info.TYPE_WIRED_HEADSET);
        assertThat(selectedRoute.getVolume()).isEqualTo(2);
        assertThat(selectedRoute.getVolumeMax()).isEqualTo(3);
        assertThat(selectedRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);

        MediaRoute2Info onlyTransferrableRoute =
                mControllerUnderTest.getAvailableRoutes().stream()
                        .filter(it -> !it.equals(selectedRoute))
                        .findAny()
                        .orElseThrow();
        assertThat(onlyTransferrableRoute.getType())
                .isEqualTo(MediaRoute2Info.TYPE_BUILTIN_SPEAKER);
        assertThat(onlyTransferrableRoute.getVolume()).isEqualTo(0);
        assertThat(onlyTransferrableRoute.getVolumeMax()).isEqualTo(0);
        assertThat(onlyTransferrableRoute.getVolume()).isEqualTo(0);
        assertThat(onlyTransferrableRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_FIXED);

        when(mMockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(0);
        when(mMockAudioManager.isVolumeFixed()).thenReturn(true);
        mControllerUnderTest.updateVolume(0);
        MediaRoute2Info newSelectedRoute = mControllerUnderTest.getSelectedRoutes().getFirst();
        assertThat(newSelectedRoute.getVolume()).isEqualTo(0);
        assertThat(newSelectedRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_FIXED);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void updateVolume_inBroadcast_propagatesCorrectlyToRouteInfo() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ true);
        when(mMockAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)).thenReturn(2);
        when(mMockAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(3);
        when(mMockAudioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)).thenReturn(1);
        when(mMockAudioManager.isVolumeFixed()).thenReturn(false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);

        MediaRoute2Info selectedRoute = mControllerUnderTest.getSelectedRoutes().getFirst();
        assertThat(selectedRoute.getType()).isEqualTo(MediaRoute2Info.TYPE_WIRED_HEADSET);
        assertThat(selectedRoute.getVolume()).isEqualTo(2);
        assertThat(selectedRoute.getVolumeMax()).isEqualTo(3);
        assertThat(selectedRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);

        when(mMockAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA))
                .thenReturn(
                        List.of(
                                createAudioDeviceAttribute(
                                        AudioDeviceInfo.TYPE_BLE_BROADCAST, /* address= */ "")));
        setBroadcastingDeviceRoutesWithVolume(/* volume */ 85);
        mControllerUnderTest.updateVolume(0);
        MediaRoute2Info newSelectedRoute = mControllerUnderTest.getSelectedRoutes().getFirst();
        assertThat(newSelectedRoute.getType()).isEqualTo(AudioDeviceInfo.TYPE_BLE_HEADSET);
        assertThat(newSelectedRoute.getVolume()).isEqualTo(85);
        assertThat(newSelectedRoute.getVolumeMax()).isEqualTo(255);
        assertThat(newSelectedRoute.getVolumeHandling())
                .isEqualTo(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void setVolume_noBroadcast_setMusicStreamVolume() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        mControllerUnderTest.setVolume(/* requestId */ 0L, FAKE_ROUTE_NAME, 2);
        mLooperManager.execute(mLooperManager.next());

        verify(mMockAudioManager).setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void setVolume_inBroadcast_primaryDevice_setMusicStreamVolume() {
        when(mMockAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA))
                .thenReturn(
                        List.of(
                                createAudioDeviceAttribute(
                                        AudioDeviceInfo.TYPE_BLE_BROADCAST, /* address= */ "")));
        when(mMockBluetoothDeviceRoutesManager.isMediaOnlyRouteInBroadcast(FAKE_ROUTE_NAME))
                .thenReturn(false);
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ true);

        mControllerUnderTest.setVolume(/* requestId */ 0L, FAKE_ROUTE_NAME, 2);
        mLooperManager.execute(mLooperManager.next());

        verify(mMockAudioManager).setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void setVolume_inBroadcast_mediaOnlyDevice_setDeviceVolume() {
        when(mMockAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA))
                .thenReturn(
                        List.of(
                                createAudioDeviceAttribute(
                                        AudioDeviceInfo.TYPE_BLE_BROADCAST, /* address= */ "")));
        when(mMockBluetoothDeviceRoutesManager.isMediaOnlyRouteInBroadcast(FAKE_ROUTE_NAME))
                .thenReturn(true);
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ true);

        mControllerUnderTest.setVolume(/* requestId */ 0L, FAKE_ROUTE_NAME, 85);
        mLooperManager.execute(mLooperManager.next());

        verify(mMockBluetoothDeviceRoutesManager).setRouteVolume(FAKE_ROUTE_NAME, 85);
    }

    @Test
    public void getAvailableRoutes_whenNoProductNameIsProvided_usesTypeToPopulateName() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        assertThat(mControllerUnderTest.getSelectedRoutes().getFirst().getName().toString())
                .isEqualTo(FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER.getProductName().toString());

        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_NO_NAME,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_NO_NAME);

        MediaRoute2Info selectedRoute = mControllerUnderTest.getSelectedRoutes().getFirst();
        assertThat(selectedRoute.getName().toString()).isEqualTo(FAKE_ROUTE_NAME);
    }

    @Test
    public void getAvailableRoutes_whenAddressIsPopulatedForNonBluetoothDevice_usesCorrectName() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET_WITH_ADDRESS,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET_WITH_ADDRESS,
                FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP);

        List<MediaRoute2Info> availableRoutes = mControllerUnderTest.getAvailableRoutes();
        assertThat(availableRoutes.size()).isEqualTo(3);

        assertThat(
                        getAvailableRouteWithType(MediaRoute2Info.TYPE_WIRED_HEADSET)
                                .getName()
                                .toString())
                .isEqualTo(
                        FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET_WITH_ADDRESS
                                .getProductName()
                                .toString());

        assertThat(
                        getAvailableRouteWithType(MediaRoute2Info.TYPE_BLUETOOTH_A2DP)
                                .getName()
                                .toString())
                .isEqualTo(FAKE_AUDIO_DEVICE_INFO_BLUETOOTH_A2DP.getProductName().toString());
    }

    @Test
    public void
            getAvailableRoutes_whenAddressIsNotPopulatedForNonBluetoothDevice_usesCorrectName() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET);

        List<MediaRoute2Info> availableRoutes = mControllerUnderTest.getAvailableRoutes();
        assertThat(availableRoutes.size()).isEqualTo(2);

        assertThat(
                        getAvailableRouteWithType(MediaRoute2Info.TYPE_BUILTIN_SPEAKER)
                                .getName()
                                .toString())
                .isEqualTo(FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER.getProductName().toString());

        assertThat(
                        getAvailableRouteWithType(MediaRoute2Info.TYPE_WIRED_HEADSET)
                                .getName()
                                .toString())
                .isEqualTo(FAKE_AUDIO_DEVICE_INFO_WIRED_HEADSET.getProductName().toString());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void getRoutes_whenLEAudioBroadcastNotSupported_returnsCorrectStates() {
        setUpControllerAndLEAudioMocks();
        when(mMockBluetoothDeviceRoutesManager.isLEAudioBroadcastSupported()).thenReturn(false);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1,
                FAKE_AUDIO_DEVICE_LE_HEADSET_2);

        List<MediaRoute2Info> selectableRoutes = mControllerUnderTest.getSelectableRoutes();
        List<String> availableRoutesNames =
                mControllerUnderTest.getAvailableRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();
        List<String> selectedRoutesNames =
                mControllerUnderTest.getSelectedRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();
        List<MediaRoute2Info> deselectableRoutes = mControllerUnderTest.getDeselectableRoutes();

        assertThat(selectableRoutes).isEmpty();
        assertThat(availableRoutesNames).contains(FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME);
        assertThat(availableRoutesNames).contains(FAKE_AUDIO_DEVICE_LE_HEADSET_2_NAME);
        assertThat(selectedRoutesNames).containsExactly(FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME);
        assertThat(deselectableRoutes).isEmpty();
        assertThat(mControllerUnderTest.getSessionReleaseType())
                .isEqualTo(RoutingSessionInfo.RELEASE_UNSUPPORTED);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void getRoutes_singleDeviceSelectedAndOutputNotBroadcast_returnsCorrectStates() {
        setUpControllerAndLEAudioMocks();
        when(mMockBluetoothDeviceRoutesManager.isLEAudioBroadcastSupported()).thenReturn(true);
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1,
                FAKE_AUDIO_DEVICE_LE_HEADSET_2);

        List<String> selectableRoutesNames =
                mControllerUnderTest.getSelectableRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();
        List<String> availableRoutesNames =
                mControllerUnderTest.getAvailableRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();
        List<String> selectedRoutesNames =
                mControllerUnderTest.getSelectedRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();
        List<MediaRoute2Info> deselectableRoutes = mControllerUnderTest.getDeselectableRoutes();

        assertThat(selectableRoutesNames).containsExactly(FAKE_AUDIO_DEVICE_LE_HEADSET_2_NAME);
        assertThat(availableRoutesNames).contains(FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME);
        assertThat(availableRoutesNames).contains(FAKE_AUDIO_DEVICE_LE_HEADSET_2_NAME);
        assertThat(selectedRoutesNames).containsExactly(FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME);
        assertThat(deselectableRoutes).isEmpty();
        assertThat(mControllerUnderTest.getSessionReleaseType())
                .isEqualTo(RoutingSessionInfo.RELEASE_UNSUPPORTED);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void getRoutes_nonBLEDeviceSelectedAndOutputNotBroadcast_returnsCorrectStates() {
        setUpControllerAndLEAudioMocks();
        when(mMockBluetoothDeviceRoutesManager.isLEAudioBroadcastSupported()).thenReturn(true);
        // Select the built-in speaker, but have a BLE device available.
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1);

        List<MediaRoute2Info> selectableRoutes = mControllerUnderTest.getSelectableRoutes();
        List<String> selectedRoutesNames =
                mControllerUnderTest.getSelectedRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();
        List<MediaRoute2Info> deselectableRoutes = mControllerUnderTest.getDeselectableRoutes();

        // When a non-BLE device is selected, no other devices should be selectable for broadcast.
        assertThat(selectableRoutes).isEmpty();
        // The selected route should be the built-in speaker.
        assertThat(selectedRoutesNames)
                .containsExactly(
                        FAKE_AUDIO_DEVICE_INFO_BUILTIN_SPEAKER.getProductName().toString());
        // Deselectable routes should be empty since we are not in a broadcast session.
        assertThat(deselectableRoutes).isEmpty();
        // Session release type should be unsupported.
        assertThat(mControllerUnderTest.getSessionReleaseType())
                .isEqualTo(RoutingSessionInfo.RELEASE_UNSUPPORTED);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OUTPUT_SWITCHER_PERSONAL_AUDIO_SHARING)
    public void getRoutes_singleDeviceSelectedAndOutputIsBroadcast_returnsCorrectStates() {
        setUpControllerAndLEAudioMocks();
        when(mMockBluetoothDeviceRoutesManager.isLEAudioBroadcastSupported()).thenReturn(true);
        when(mMockBluetoothDeviceRoutesManager.getBroadcastingDeviceRoutes())
                .thenReturn(
                        List.of(
                                createMediaRoute2Info(
                                        /* id= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_ID,
                                        /* name= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME,
                                        /* address= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS,
                                        /* volume= */ 0)));
        addAvailableAudioDeviceInfo(
                /* newSelectedDevice= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_BROADCASTING,
                /* newAvailableDevices...= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_BROADCASTING,
                FAKE_AUDIO_DEVICE_LE_HEADSET_2);

        List<MediaRoute2Info> selectableRoutes = mControllerUnderTest.getSelectableRoutes();
        List<String> availableRoutesNames =
                mControllerUnderTest.getAvailableRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();
        List<String> selectedRoutesNames =
                mControllerUnderTest.getSelectedRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();
        List<String> deselectableRoutesNames =
                mControllerUnderTest.getDeselectableRoutes().stream()
                        .map(it -> it.getName().toString())
                        .toList();

        assertThat(selectableRoutes).isEmpty();
        assertThat(availableRoutesNames).contains(FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME);
        assertThat(availableRoutesNames).contains(FAKE_AUDIO_DEVICE_LE_HEADSET_2_NAME);
        assertThat(selectedRoutesNames).containsExactly(FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME);
        assertThat(deselectableRoutesNames).containsExactly(FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME);
        assertThat(mControllerUnderTest.getSessionReleaseType())
                .isEqualTo(RoutingSessionInfo.RELEASE_TYPE_SHARING);
    }

    // Internal methods.

    private void setUpControllerUnderTest(boolean useMockBluetoothDeviceRoutesManager) {
        if (useMockBluetoothDeviceRoutesManager) {
            mControllerUnderTest =
                    new AudioManagerRouteController(
                            mMockContext,
                            mMockAudioManager,
                            mThread.getLooper(),
                            mMediaAudioProductStrategy,
                            mMockBluetoothDeviceRoutesManager);
        } else {
            BluetoothAdapter btAdapter =
                    mRealContext.getSystemService(BluetoothManager.class).getAdapter();
            mControllerUnderTest =
                    new AudioManagerRouteController(
                            mMockContext,
                            mMockAudioManager,
                            mThread.getLooper(),
                            mMediaAudioProductStrategy,
                            btAdapter);
        }
        mControllerUnderTest.registerRouteChangeListener(mEventListener);
        mControllerUnderTest.start(UserHandle.CURRENT_OR_SELF);

        ArgumentCaptor<AudioDeviceCallback> deviceCallbackCaptor =
                ArgumentCaptor.forClass(AudioDeviceCallback.class);
        verify(mMockAudioManager)
                .registerAudioDeviceCallback(deviceCallbackCaptor.capture(), any());
        mAudioDeviceCallback = deviceCallbackCaptor.getValue();

        // We clear any invocations during setup.
        clearInvocations(mEventListener);
    }

    @NonNull
    private MediaRoute2Info getAvailableRouteWithType(int type) {
        return mControllerUnderTest.getAvailableRoutes().stream()
                .filter(it -> it.getType() == type)
                .findFirst()
                .orElseThrow();
    }

    private void addAvailableAudioDeviceInfo(
            @Nullable AudioDeviceInfo newSelectedDevice, AudioDeviceInfo... newAvailableDevices) {
        Set<AudioDeviceInfo> newAvailableDeviceInfos = new HashSet<>(mAvailableAudioDeviceInfos);
        newAvailableDeviceInfos.addAll(List.of(newAvailableDevices));
        mAvailableAudioDeviceInfos = newAvailableDeviceInfos;
        if (newSelectedDevice != null) {
            mSelectedAudioDeviceInfo = newSelectedDevice;
        }
        updateMockAudioManagerState();
        mAudioDeviceCallback.onAudioDevicesAdded(newAvailableDevices);
    }

    private void removeAvailableAudioDeviceInfos(
            @Nullable AudioDeviceInfo newSelectedDevice, AudioDeviceInfo... devicesToRemove) {
        Set<AudioDeviceInfo> newAvailableDeviceInfos = new HashSet<>(mAvailableAudioDeviceInfos);
        List.of(devicesToRemove).forEach(newAvailableDeviceInfos::remove);
        mAvailableAudioDeviceInfos = newAvailableDeviceInfos;
        if (newSelectedDevice != null) {
            mSelectedAudioDeviceInfo = newSelectedDevice;
        }
        updateMockAudioManagerState();
        mAudioDeviceCallback.onAudioDevicesRemoved(devicesToRemove);
    }

    private void updateMockAudioManagerState() {
        int selectedDeviceAttributesType = mSelectedAudioDeviceInfo.getType();
        String selectedDeviceAttributesAddr = mSelectedAudioDeviceInfo.getAddress();
        when(mMockAudioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA))
                .thenReturn(
                        List.of(createAudioDeviceAttribute(selectedDeviceAttributesType,
                                                           selectedDeviceAttributesAddr)));

        // AudioManager.getDevices() returns only 1 device for a single type.
        HashMap<Integer, AudioDeviceInfo> availableAudioDeviceInfosMap = new HashMap<>();
        for (AudioDeviceInfo deviceInfo : mAvailableAudioDeviceInfos) {
            availableAudioDeviceInfosMap.put(deviceInfo.getType(), deviceInfo);
        }
        // selected audio device should be the put to the hash map at last.
        availableAudioDeviceInfosMap.put(
                mSelectedAudioDeviceInfo.getType(), mSelectedAudioDeviceInfo);

        when(mMockAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
                .thenReturn(availableAudioDeviceInfosMap.values().toArray(new AudioDeviceInfo[0]));
    }

    private void setBroadcastingDeviceRoutesWithVolume(int volume) {
        MediaRoute2Info newSelectedBleRoute =
                createMediaRoute2Info(
                        /* id= */ FAKE_ROUTE_NAME,
                        /* name= */ FAKE_ROUTE_NAME,
                        /* address= */ FAKE_BT_ROUTE_ADDRESS,
                        /* volume= */ volume);
        when(mMockBluetoothDeviceRoutesManager.getBroadcastingDeviceRoutes())
                .thenReturn(ImmutableList.of(newSelectedBleRoute));
    }

    private void setUpControllerAndLEAudioMocks() {
        setUpControllerUnderTest(/* useMockBluetoothDeviceRoutesManager= */ true);

        when(mMockBluetoothDeviceRoutesManager.containsBondedDeviceWithAddress(
                        FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS))
                .thenReturn(true);
        when(mMockBluetoothDeviceRoutesManager.getRouteIdForBluetoothAddress(
                        FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS))
                .thenReturn(FAKE_AUDIO_DEVICE_LE_HEADSET_1_ID);
        when(mMockBluetoothDeviceRoutesManager.getNameForBluetoothAddress(
                        FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS))
                .thenReturn(FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME);
        when(mMockBluetoothDeviceRoutesManager.containsBondedDeviceWithAddress(
                        FAKE_AUDIO_DEVICE_LE_HEADSET_2_ADDRESS))
                .thenReturn(true);
        when(mMockBluetoothDeviceRoutesManager.getRouteIdForBluetoothAddress(
                        FAKE_AUDIO_DEVICE_LE_HEADSET_2_ADDRESS))
                .thenReturn(FAKE_AUDIO_DEVICE_LE_HEADSET_2_ID);
        when(mMockBluetoothDeviceRoutesManager.getNameForBluetoothAddress(
                        FAKE_AUDIO_DEVICE_LE_HEADSET_2_ADDRESS))
                .thenReturn(FAKE_AUDIO_DEVICE_LE_HEADSET_2_NAME);
        when(mMockBluetoothDeviceRoutesManager.getAvailableBluetoothRoutes())
                .thenReturn(
                        List.of(
                                createMediaRoute2Info(
                                        /* id= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_ID,
                                        /* name= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_NAME,
                                        /* address= */ FAKE_AUDIO_DEVICE_LE_HEADSET_1_ADDRESS,
                                        /* volume= */ 0),
                                createMediaRoute2Info(
                                        /* id= */ FAKE_AUDIO_DEVICE_LE_HEADSET_2_ID,
                                        /* name= */ FAKE_AUDIO_DEVICE_LE_HEADSET_2_NAME,
                                        /* address= */ FAKE_AUDIO_DEVICE_LE_HEADSET_2_ADDRESS,
                                        /* volume= */ 0)));
    }

    private static MediaRoute2Info createMediaRoute2Info(
            @NonNull String id, @NonNull String name, @NonNull String address, int volume) {
        return new MediaRoute2Info.Builder(/* id= */ id, /* name= */ name)
                .setType(AudioDeviceInfo.TYPE_BLE_HEADSET)
                .setAddress(address)
                .setSystemRoute(true)
                .addFeature(MediaRoute2Info.FEATURE_LIVE_AUDIO)
                .addFeature(MediaRoute2Info.FEATURE_LOCAL_PLAYBACK)
                .setConnectionState(MediaRoute2Info.CONNECTION_STATE_CONNECTED)
                .setVolume(volume)
                .setVolumeMax(255)
                .build();
    }

    private static AudioDeviceAttributes createAudioDeviceAttribute(
            @AudioDeviceInfo.AudioDeviceType int type,
            @NonNull String address) {
        // Address is unused.
        return new AudioDeviceAttributes(
                AudioDeviceAttributes.ROLE_OUTPUT, type, address);
    }

    private static AudioDeviceInfo createAudioDeviceInfo(
            @AudioDeviceInfo.AudioDeviceType int type, @NonNull String name,
            @NonNull String address) {
        return new AudioDeviceInfo(AudioDevicePort.createForTesting(type, name, address));
    }
}
