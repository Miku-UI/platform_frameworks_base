/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.settingslib.media;

import static android.media.MediaRoute2ProviderService.REASON_UNKNOWN_ERROR;
import static android.media.RoutingChangeInfo.ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceAttributes;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.MediaRoute2Info;
import android.media.RoutingChangeInfo;
import android.media.RoutingSessionInfo;

import com.android.settingslib.bluetooth.A2dpProfile;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.CachedBluetoothDeviceManager;
import com.android.settingslib.bluetooth.HearingAidProfile;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.settingslib.testutils.shadow.ShadowBluetoothAdapter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowBluetoothAdapter.class})
public class LocalMediaManagerTest {
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    private static final String TEST_DEVICE_NAME_1 = "device_name_1";
    private static final String TEST_DEVICE_NAME_2 = "device_name_2";
    private static final String TEST_DEVICE_ID_1 = "device_id_1";
    private static final String TEST_DEVICE_ID_2 = "device_id_2";
    private static final String TEST_DEVICE_ID_3 = "device_id_3";
    private static final String TEST_PHONE_DEVICE_ID = "phone_device_id";
    private static final String TEST_CURRENT_DEVICE_ID = "currentDevice_id";
    private static final String TEST_PACKAGE_NAME = "com.test.playmusic";
    private static final String TEST_SESSION_ID = "session_id";
    private static final String TEST_ADDRESS = "00:01:02:03:04:05";

    @Mock
    private LocalBluetoothManager mLocalBluetoothManager;
    @Mock
    private LocalMediaManager.DeviceCallback mCallback;
    @Mock
    private HearingAidProfile mHapProfile;
    @Mock
    private A2dpProfile mA2dpProfile;
    @Mock
    private LocalBluetoothProfileManager mLocalProfileManager;
    @Mock
    private MediaRoute2Info mRouteInfo1;
    @Mock
    private MediaRoute2Info mRouteInfo2;
    @Mock
    private AudioManager mAudioManager;

    private Context mContext;
    private LocalMediaManager mLocalMediaManager;
    private InfoMediaManager mInfoMediaManager;
    private ShadowBluetoothAdapter mShadowBluetoothAdapter;
    private InfoMediaDevice mInfoMediaDevice1;
    private InfoMediaDevice mInfoMediaDevice2;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        final List<BluetoothDevice> bluetoothDevices = new ArrayList<>();
        mShadowBluetoothAdapter = Shadow.extract(BluetoothAdapter.getDefaultAdapter());
        mShadowBluetoothAdapter.setMostRecentlyConnectedDevices(bluetoothDevices);
        when(mRouteInfo1.getName()).thenReturn(TEST_DEVICE_NAME_1);
        when(mRouteInfo1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(mRouteInfo2.getName()).thenReturn(TEST_DEVICE_NAME_2);
        when(mRouteInfo2.getId()).thenReturn(TEST_DEVICE_ID_2);
        when(mLocalBluetoothManager.getProfileManager()).thenReturn(mLocalProfileManager);
        when(mLocalProfileManager.getA2dpProfile()).thenReturn(mA2dpProfile);
        when(mLocalProfileManager.getHearingAidProfile()).thenReturn(mHapProfile);

        // Need to call constructor to initialize final fields.
        mInfoMediaManager =
                mock(
                        InfoMediaManager.class,
                        withSettings()
                                .useConstructor(
                                        mContext,
                                        TEST_PACKAGE_NAME,
                                        android.os.Process.myUserHandle(),
                                        mLocalBluetoothManager,
                                        /* mediaController */ null));
        doReturn(
                        List.of(
                                new RoutingSessionInfo.Builder(TEST_SESSION_ID, TEST_PACKAGE_NAME)
                                        .addSelectedRoute(TEST_DEVICE_ID_1)
                                        .build()))
                .when(mInfoMediaManager)
                .getRoutingSessionsForPackage();

        mInfoMediaDevice1 = spy(
                new InfoMediaDevice(mContext, mRouteInfo1, /* dynamicRouteAttributes= */
                        null, /* item */ null));
        mInfoMediaDevice2 = new InfoMediaDevice(mContext, mRouteInfo2, /* dynamicRouteAttributes= */
                null, /* item */ null);
        mLocalMediaManager =
                new LocalMediaManager(
                        mContext, mLocalBluetoothManager, mInfoMediaManager, TEST_PACKAGE_NAME);
        mLocalMediaManager.mAudioManager = mAudioManager;
        mLocalMediaManager.registerCallback(mCallback);
        clearInvocations(mCallback);
    }

    @Test
    public void onDeviceListAdded_shouldClearDeviceList() {
        final MediaDevice device = mock(MediaDevice.class);
        mLocalMediaManager.mMediaDevices.add(device);
        assertThat(mLocalMediaManager.mMediaDevices).hasSize(1);
        mLocalMediaManager.mMediaDeviceCallback.onDeviceListAdded(Collections.emptyList());
        assertThat(mLocalMediaManager.mMediaDevices).isEmpty();
    }

    @Test
    public void connectDevice_deviceNotEqualCurrentConnectedDevice_connectDevice() {
        final MediaDevice currentDevice = mock(MediaDevice.class);
        final MediaDevice device = mock(MediaDevice.class);
        mLocalMediaManager.mMediaDevices.add(currentDevice);
        mLocalMediaManager.mMediaDevices.add(device);
        mLocalMediaManager.mCurrentConnectedDevice = currentDevice;

        when(device.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(currentDevice.getId()).thenReturn(TEST_CURRENT_DEVICE_ID);

        assertThat(mLocalMediaManager.connectDevice(device)).isTrue();
        ArgumentCaptor<RoutingChangeInfo> captor = ArgumentCaptor.forClass(RoutingChangeInfo.class);
        verify(mInfoMediaManager).connectToDevice(eq(device), captor.capture());
        RoutingChangeInfo capturedInfo = captor.getValue();
        assertThat(capturedInfo.getEntryPoint()).isEqualTo(ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED);
        assertThat(capturedInfo.isSuggested()).isEqualTo(false);
    }

    @Test
    public void connectDevice_deviceNotEqualCurrentConnectedDevice_isConnectingState() {
        mLocalMediaManager.mMediaDevices.add(mInfoMediaDevice1);
        mLocalMediaManager.mMediaDevices.add(mInfoMediaDevice2);
        mLocalMediaManager.mCurrentConnectedDevice = mInfoMediaDevice1;

        assertThat(mLocalMediaManager.connectDevice(mInfoMediaDevice2)).isTrue();

        verify(mInfoMediaManager)
                .setDeviceState(
                        mInfoMediaDevice2, LocalMediaManager.MediaDeviceState.STATE_CONNECTING);
    }

    @Test
    public void connectDevice_deviceEqualCurrentConnectedDevice_notConnectingState() {
        mLocalMediaManager.mMediaDevices.add(mInfoMediaDevice1);
        mLocalMediaManager.mMediaDevices.add(mInfoMediaDevice2);
        mLocalMediaManager.mCurrentConnectedDevice = mInfoMediaDevice1;

        assertThat(mLocalMediaManager.connectDevice(mInfoMediaDevice1)).isFalse();

        assertThat(mInfoMediaDevice1.getState()).isNotEqualTo(LocalMediaManager.MediaDeviceState
                .STATE_CONNECTING);
    }

    @Test
    public void connectDevice_bluetoothDeviceNotConnected_connectBluetoothDevice() {
        final MediaDevice device = mock(BluetoothMediaDevice.class);
        final CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        mLocalMediaManager.mMediaDevices.add(device);

        when(device.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(((BluetoothMediaDevice) device).getCachedDevice()).thenReturn(cachedDevice);
        when(cachedDevice.isConnected()).thenReturn(false);
        when(cachedDevice.isBusy()).thenReturn(false);

        assertThat(mLocalMediaManager.connectDevice(device)).isTrue();

        verify(cachedDevice).connect();
    }

    @Test
    public void getMediaDeviceById_idExist_shouldReturnMediaDevice() {
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);
        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(device2);

        when(device1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(device2.getId()).thenReturn(TEST_DEVICE_ID_2);

        MediaDevice device = mLocalMediaManager.getMediaDeviceById(TEST_DEVICE_ID_2);

        assertThat(device.getId()).isEqualTo(TEST_DEVICE_ID_2);
    }

    @Test
    public void getMediaDeviceById_idNotExist_shouldReturnNull() {
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);
        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(device2);

        when(device1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(device2.getId()).thenReturn(TEST_DEVICE_ID_2);

        MediaDevice device = mLocalMediaManager.getMediaDeviceById(TEST_CURRENT_DEVICE_ID);

        assertThat(device).isNull();
    }

    @Test
    public void getMediaDeviceById_idIsNull_shouldReturnNull() {
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);
        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(device2);

        when(device1.getId()).thenReturn(null);
        when(device2.getId()).thenReturn(null);

        MediaDevice device = mLocalMediaManager.getMediaDeviceById(TEST_CURRENT_DEVICE_ID);

        assertThat(device).isNull();
    }

    @Test
    public void onDeviceListAdded_addDevicesList() {
        final List<MediaDevice> devices = new ArrayList<>();
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);
        devices.add(device1);
        devices.add(device2);

        when(device1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(device2.getId()).thenReturn(TEST_DEVICE_ID_2);

        assertThat(mLocalMediaManager.mMediaDevices).isEmpty();
        mLocalMediaManager.mMediaDeviceCallback.onDeviceListAdded(devices);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(2);
        verify(mCallback).onDeviceListUpdate(any());
    }

    @Test
    public void onDeviceListAdded_addDeviceList() {
        final List<MediaDevice> devices = new ArrayList<>();
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);
        final MediaDevice device3 = mock(MediaDevice.class);
        devices.add(device1);
        devices.add(device2);
        mLocalMediaManager.mMediaDevices.add(device3);

        when(device1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(device2.getId()).thenReturn(TEST_DEVICE_ID_2);
        when(device3.getId()).thenReturn(TEST_DEVICE_ID_3);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(1);
        mLocalMediaManager.mMediaDeviceCallback.onDeviceListAdded(devices);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(2);
        verify(mCallback).onDeviceListUpdate(any());
    }

    @Test
    public void onDeviceListRemoved_removeAll() {
        final List<MediaDevice> devices = new ArrayList<>();
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);
        devices.add(device1);
        devices.add(device2);
        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(device2);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(2);
        mLocalMediaManager.mMediaDeviceCallback
                .onDeviceListRemoved(mLocalMediaManager.mMediaDevices);

        assertThat(mLocalMediaManager.mMediaDevices).isEmpty();
        verify(mCallback).onDeviceListUpdate(any());
    }

    @Test
    public void onDeviceListRemoved_phoneDeviceNotLastDeviceAfterRemoveDeviceList_removeList() {
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);
        final MediaDevice device3 = mock(MediaDevice.class);

        mLocalMediaManager.mMediaDevices.clear();
        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(device2);
        mLocalMediaManager.mMediaDevices.add(device3);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(3);

        final List<MediaDevice> devicesToRemove = new ArrayList<>();
        devicesToRemove.add(device1);
        devicesToRemove.add(device3);
        mLocalMediaManager.mMediaDeviceCallback.onDeviceListRemoved(devicesToRemove);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(1);
        verify(mCallback).onDeviceListUpdate(any());
    }

    @Test
    public void onDeviceListAdded_isTv_bondedDeviceAddedToList() {
        setUpTvEnvironment();
        List<MediaDevice> devices = setUpMediaDevice(MediaDevice.MediaDeviceType.TYPE_PHONE_DEVICE,
                TEST_PHONE_DEVICE_ID);
        List<LocalBluetoothProfile> profiles = setUpProfiles();
        BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        CachedBluetoothDeviceManager cachedDeviceManager = setUpCachedDeviceManager();
        setUpCachedDevice(bluetoothDevice, cachedDeviceManager, profiles, TEST_ADDRESS,
                TEST_DEVICE_NAME_1);
        setUpMostRecentlyConnectedDevice(bluetoothDevice);

        mLocalMediaManager.mMediaDeviceCallback.onDeviceListAdded(devices);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(2);
    }

    @Test
    public void onDeviceListAdded_isTvNoBluetoothManager_bondedDeviceIgnored() {
        // Simulate TV Environment
        setUpTvEnvironment();
        List<MediaDevice> devices = setUpMediaDevice(MediaDevice.MediaDeviceType.TYPE_PHONE_DEVICE,
                TEST_PHONE_DEVICE_ID);
        List<LocalBluetoothProfile> profiles = setUpProfiles();
        BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        CachedBluetoothDeviceManager cachedDeviceManager = setUpCachedDeviceManager();
        setUpCachedDevice(bluetoothDevice, cachedDeviceManager, profiles, TEST_ADDRESS,
                TEST_DEVICE_NAME_1);
        setUpMostRecentlyConnectedDevice(bluetoothDevice);

        //Setup LocalMediaManager with nullBluetoothManager
        LocalMediaManager localMediaManager = setUpLocalMediaManagerWithNullBluetoothManager(
                mInfoMediaManager, TEST_PACKAGE_NAME, mCallback, mAudioManager);
        localMediaManager.mMediaDeviceCallback.onDeviceListAdded(devices);

        assertThat(localMediaManager.mMediaDevices).hasSize(1);
    }

    @Test
    public void onDeviceListAdded_hasMutingExpectedDeviceNoBluetoothManager_deviceIgnored() {
        setUpTvEnvironment();
        List<MediaDevice> devices = setUpMediaDevice(MediaDevice.MediaDeviceType.TYPE_PHONE_DEVICE,
                TEST_PHONE_DEVICE_ID);
        BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        setUpMostRecentlyConnectedDevice(bluetoothDevice);
        List<LocalBluetoothProfile> profiles = setUpProfiles();
        when(mA2dpProfile.getActiveDevice()).thenReturn(bluetoothDevice);
        CachedBluetoothDeviceManager cachedDeviceManager = setUpCachedDeviceManager();
        setUpCachedDevice(bluetoothDevice, cachedDeviceManager, profiles, TEST_ADDRESS,
                TEST_DEVICE_NAME_1);
        setUpAudioDeviceAttributes();
        when(mHapProfile.getActiveDevices()).thenReturn(new ArrayList<>());

        LocalMediaManager localMediaManager = setUpLocalMediaManagerWithNullBluetoothManager(
                mInfoMediaManager, TEST_PACKAGE_NAME, mCallback, mAudioManager);
        localMediaManager.mMediaDeviceCallback.onDeviceListAdded(devices);

        assertThat(localMediaManager.mMediaDevices).hasSize(1);
    }

    @Test
    public void onConnectedDeviceChanged_connectedAndCurrentDeviceAreDifferent_notifyThemChanged() {
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);

        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(device2);
        mLocalMediaManager.mCurrentConnectedDevice = device1;

        when(device1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(device2.getId()).thenReturn(TEST_DEVICE_ID_2);

        mLocalMediaManager.mMediaDeviceCallback.onConnectedDeviceChanged(TEST_DEVICE_ID_2);

        assertThat(mLocalMediaManager.getCurrentConnectedDevice()).isEqualTo(device2);
        verify(mCallback).onSelectedDeviceStateChanged(device2,
                LocalMediaManager.MediaDeviceState.STATE_CONNECTED);
    }

    @Test
    public void onConnectedDeviceChanged_connectedAndCurrentDeviceAreSame_doNothing() {
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);

        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(device2);
        mLocalMediaManager.mCurrentConnectedDevice = device1;

        when(device1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(device2.getId()).thenReturn(TEST_DEVICE_ID_2);

        mLocalMediaManager.mMediaDeviceCallback.onConnectedDeviceChanged(TEST_DEVICE_ID_1);

        verify(mCallback, never()).onDeviceAttributesChanged();
    }

    @Test
    public void onConnectedDeviceChanged_isConnectedState() {
        mLocalMediaManager.mMediaDevices.add(mInfoMediaDevice1);
        mLocalMediaManager.mMediaDevices.add(mInfoMediaDevice2);
        mInfoMediaDevice1.setState(LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED);

        assertThat(mInfoMediaDevice1.getState()).isEqualTo(LocalMediaManager.MediaDeviceState
                .STATE_DISCONNECTED);
        mLocalMediaManager.mMediaDeviceCallback.onConnectedDeviceChanged(TEST_DEVICE_ID_1);

        verify(mInfoMediaManager)
                .setDeviceState(
                        mInfoMediaDevice1, LocalMediaManager.MediaDeviceState.STATE_CONNECTED);
    }

    @Test
    public void onConnectedDeviceChanged_nullConnectedDevice_noException() {
        mLocalMediaManager.mMediaDeviceCallback.onConnectedDeviceChanged(TEST_DEVICE_ID_2);
    }

    @Test
    public void onDeviceAttributesChanged_failingTransferring_shouldResetState() {
        final MediaDevice currentDevice = mock(MediaDevice.class);
        final MediaDevice device = mock(BluetoothMediaDevice.class);
        final CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        mLocalMediaManager.mMediaDevices.add(device);
        mLocalMediaManager.mMediaDevices.add(currentDevice);
        when(device.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(currentDevice.getId()).thenReturn(TEST_CURRENT_DEVICE_ID);
        when(((BluetoothMediaDevice) device).getCachedDevice()).thenReturn(cachedDevice);
        when(cachedDevice.isConnected()).thenReturn(false);
        when(cachedDevice.isBusy()).thenReturn(false);

        mLocalMediaManager.connectDevice(device);

        mLocalMediaManager.mDeviceAttributeChangeCallback.onDeviceAttributesChanged();
        verify(mInfoMediaManager)
                .setDeviceState(device, LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED);
    }

    @Test
    public void onRequestFailed_checkDevicesState() {
        mLocalMediaManager.mMediaDevices.add(mInfoMediaDevice1);
        mLocalMediaManager.mMediaDevices.add(mInfoMediaDevice2);
        mInfoMediaDevice1.setState(LocalMediaManager.MediaDeviceState.STATE_CONNECTING);
        mInfoMediaDevice2.setState(LocalMediaManager.MediaDeviceState.STATE_CONNECTED);

        assertThat(mInfoMediaDevice1.getState()).isEqualTo(LocalMediaManager.MediaDeviceState
                .STATE_CONNECTING);
        assertThat(mInfoMediaDevice2.getState()).isEqualTo(LocalMediaManager.MediaDeviceState
                .STATE_CONNECTED);
        mLocalMediaManager.mMediaDeviceCallback.onRequestFailed(REASON_UNKNOWN_ERROR);

        verify(mInfoMediaManager)
                .setDeviceState(
                        mInfoMediaDevice1,
                        LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED);
        assertThat(mInfoMediaDevice2.getState()).isEqualTo(LocalMediaManager.MediaDeviceState
                .STATE_CONNECTED);
    }

    @Test
    public void onDeviceAttributesChanged_shouldBeCalled() {
        mLocalMediaManager.mDeviceAttributeChangeCallback.onDeviceAttributesChanged();

        verify(mCallback).onDeviceAttributesChanged();
    }

    @Test
    public void getActiveMediaSession_verifyCorrectSession() {
        final List<RoutingSessionInfo> routingSessionInfos = new ArrayList<>();
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);
        when(info.getId()).thenReturn(TEST_SESSION_ID);
        routingSessionInfos.add(info);
        when(mInfoMediaManager.getRemoteSessions()).thenReturn(routingSessionInfos);

        assertThat(mLocalMediaManager.getRemoteRoutingSessions().get(0).getId())
                .matches(TEST_SESSION_ID);
    }

    @Test
    public void onDeviceListAdded_haveMutingExpectedDevice_addMutingExpectedDevice() {
        final List<MediaDevice> devices = new ArrayList<>();
        final MediaDevice device1 = mock(MediaDevice.class);
        devices.add(device1);

        final List<LocalBluetoothProfile> profiles = new ArrayList<>();
        final A2dpProfile a2dpProfile = mock(A2dpProfile.class);
        profiles.add(a2dpProfile);

        final List<BluetoothDevice> bluetoothDevices = new ArrayList<>();
        final BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        final CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        final CachedBluetoothDeviceManager cachedManager = mock(CachedBluetoothDeviceManager.class);
        bluetoothDevices.add(bluetoothDevice);
        mShadowBluetoothAdapter.setMostRecentlyConnectedDevices(bluetoothDevices);

        AudioDeviceAttributes audioDeviceAttributes = new AudioDeviceAttributes(
                AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, TEST_ADDRESS);

        when(mAudioManager.getMutingExpectedDevice()).thenReturn(audioDeviceAttributes);
        when(mLocalBluetoothManager.getCachedDeviceManager()).thenReturn(cachedManager);
        when(cachedManager.findDevice(bluetoothDevice)).thenReturn(cachedDevice);
        when(cachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(cachedDevice.isConnected()).thenReturn(false);
        when(cachedDevice.getUiAccessibleProfiles()).thenReturn(profiles);
        when(cachedDevice.getDevice()).thenReturn(bluetoothDevice);
        when(cachedDevice.getAddress()).thenReturn(TEST_ADDRESS);
        when(mA2dpProfile.getActiveDevice()).thenReturn(bluetoothDevice);
        when(mHapProfile.getActiveDevices()).thenReturn(new ArrayList<>());

        when(device1.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(device1.getDeviceType()).thenReturn(MediaDevice.MediaDeviceType.TYPE_PHONE_DEVICE);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(0);
        mLocalMediaManager.mMediaDeviceCallback.onDeviceListAdded(devices);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(2);
        assertThat(mLocalMediaManager.mMediaDevices.getLast().isMutingExpectedDevice()).isTrue();
        verify(mCallback).onDeviceListUpdate(any());
    }

    @Test
    public void onDeviceListAdded_transferToDisconnectedBluetooth_verifyConnectDevice() {
        final List<MediaDevice> devices = new ArrayList<>();
        final MediaDevice currentDevice = mock(MediaDevice.class);
        final MediaDevice device = mock(BluetoothMediaDevice.class);
        final CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        mLocalMediaManager.mMediaDevices.add(device);
        mLocalMediaManager.mMediaDevices.add(currentDevice);

        when(device.getId()).thenReturn(TEST_DEVICE_ID_1);
        when(currentDevice.getId()).thenReturn(TEST_CURRENT_DEVICE_ID);
        when(((BluetoothMediaDevice) device).getCachedDevice()).thenReturn(cachedDevice);
        when(cachedDevice.isConnected()).thenReturn(false);
        when(cachedDevice.isBusy()).thenReturn(false);

        mLocalMediaManager.connectDevice(device);

        verify(cachedDevice).connect();
        when(device.isConnected()).thenReturn(true);
        mLocalMediaManager.mCurrentConnectedDevice = currentDevice;
        devices.add(mInfoMediaDevice1);
        devices.add(currentDevice);
        mLocalMediaManager.mMediaDeviceCallback.onDeviceListAdded(devices);

        ArgumentCaptor<RoutingChangeInfo> captor = ArgumentCaptor.forClass(RoutingChangeInfo.class);
        verify(mInfoMediaManager).connectToDevice(eq(mInfoMediaDevice1), captor.capture());
        RoutingChangeInfo capturedInfo = captor.getValue();
        assertThat(capturedInfo.getEntryPoint()).isEqualTo(ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED);
        assertThat(capturedInfo.isSuggested()).isEqualTo(false);
    }

    @Test
    public void onRequestFailed_shouldDispatchOnRequestFailed() {
        mLocalMediaManager.mMediaDeviceCallback.onRequestFailed(1);

        verify(mCallback).onRequestFailed(1);
    }

    @Test
    public void onDeviceListAdded_bluetoothAdapterIsNull_noDisconnectedDeviceAdded() {
        final List<MediaDevice> devices = new ArrayList<>();
        final MediaDevice device1 = mock(MediaDevice.class);
        final MediaDevice device2 = mock(MediaDevice.class);
        final MediaDevice device3 = mock(MediaDevice.class);
        devices.add(device1);
        devices.add(device2);
        mLocalMediaManager.mMediaDevices.add(device3);

        mShadowBluetoothAdapter = null;

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(1);
        mLocalMediaManager.mMediaDeviceCallback.onDeviceListAdded(devices);

        assertThat(mLocalMediaManager.mMediaDevices).hasSize(2);
        verify(mCallback).onDeviceListUpdate(any());
    }

    @Test
    public void adjustSessionVolume_verifyCorrectSessionVolume() {
        final List<RoutingSessionInfo> routingSessionInfos = new ArrayList<>();
        final RoutingSessionInfo info = mock(RoutingSessionInfo.class);
        when(info.getId()).thenReturn(TEST_SESSION_ID);
        routingSessionInfos.add(info);
        when(mInfoMediaManager.getRoutingSessionById(TEST_SESSION_ID)).thenReturn(info);

        mLocalMediaManager.adjustSessionVolume(TEST_SESSION_ID, 10);

        verify(mInfoMediaManager).adjustSessionVolume(info, 10);
    }

    @Test
    public void updateCurrentConnectedDevice_bluetoothDeviceIsActive_returnBluetoothDevice() {
        final BluetoothMediaDevice device1 = mock(BluetoothMediaDevice.class);
        final BluetoothMediaDevice device2 = mock(BluetoothMediaDevice.class);
        final PhoneMediaDevice phoneDevice = mock(PhoneMediaDevice.class);
        final CachedBluetoothDevice cachedDevice1 = mock(CachedBluetoothDevice.class);
        final CachedBluetoothDevice cachedDevice2 = mock(CachedBluetoothDevice.class);
        final BluetoothDevice bluetoothDevice1 = mock(BluetoothDevice.class);
        final BluetoothDevice bluetoothDevice2 = mock(BluetoothDevice.class);

        when(mA2dpProfile.getActiveDevice()).thenReturn(bluetoothDevice2);
        when(mHapProfile.getActiveDevices()).thenReturn(new ArrayList<>());
        when(device1.getCachedDevice()).thenReturn(cachedDevice1);
        when(device2.getCachedDevice()).thenReturn(cachedDevice2);
        when(cachedDevice1.getDevice()).thenReturn(bluetoothDevice1);
        when(cachedDevice2.getDevice()).thenReturn(bluetoothDevice2);
        when(cachedDevice1.isActiveDevice(BluetoothProfile.A2DP)).thenReturn(false);
        when(cachedDevice2.isActiveDevice(BluetoothProfile.A2DP)).thenReturn(true);
        when(device1.isConnected()).thenReturn(true);
        when(device2.isConnected()).thenReturn(true);

        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(phoneDevice);
        mLocalMediaManager.mMediaDevices.add(device2);

        assertThat(mLocalMediaManager.updateCurrentConnectedDevice()).isEqualTo(device2);
    }

    @Test
    public void updateCurrentConnectedDevice_phoneDeviceIsActive_returnPhoneDevice() {
        final BluetoothMediaDevice device1 = mock(BluetoothMediaDevice.class);
        final BluetoothMediaDevice device2 = mock(BluetoothMediaDevice.class);
        final PhoneMediaDevice phoneDevice = mock(PhoneMediaDevice.class);
        final CachedBluetoothDevice cachedDevice1 = mock(CachedBluetoothDevice.class);
        final CachedBluetoothDevice cachedDevice2 = mock(CachedBluetoothDevice.class);
        final BluetoothDevice bluetoothDevice1 = mock(BluetoothDevice.class);
        final BluetoothDevice bluetoothDevice2 = mock(BluetoothDevice.class);

        when(mA2dpProfile.getActiveDevice()).thenReturn(null);
        when(mHapProfile.getActiveDevices()).thenReturn(new ArrayList<>());
        when(device1.getCachedDevice()).thenReturn(cachedDevice1);
        when(device2.getCachedDevice()).thenReturn(cachedDevice2);
        when(cachedDevice1.getDevice()).thenReturn(bluetoothDevice1);
        when(cachedDevice2.getDevice()).thenReturn(bluetoothDevice2);
        when(cachedDevice1.isActiveDevice(BluetoothProfile.A2DP)).thenReturn(false);
        when(cachedDevice2.isActiveDevice(BluetoothProfile.A2DP)).thenReturn(false);
        when(device1.isConnected()).thenReturn(true);
        when(device2.isConnected()).thenReturn(true);

        mLocalMediaManager.mMediaDevices.add(device1);
        mLocalMediaManager.mMediaDevices.add(phoneDevice);
        mLocalMediaManager.mMediaDevices.add(device2);

        assertThat(mLocalMediaManager.updateCurrentConnectedDevice()).isEqualTo(phoneDevice);
    }

    @Test
    public void getSessionReleaseType_returnCorrectType() {
        when(mInfoMediaManager.getSessionReleaseType())
                .thenReturn(RoutingSessionInfo.RELEASE_TYPE_SHARING);
        assertThat(mLocalMediaManager.getSessionReleaseType())
                .isEqualTo(RoutingSessionInfo.RELEASE_TYPE_SHARING);
    }

    @Test
    public void getMissingPermissionsInfo_returnsInfoFromInfoMediaManager() {
        ComponentName component = new ComponentName(TEST_PACKAGE_NAME, "TestClass");
        MissingPermissionsInfo info = new MissingPermissionsInfo(component, Collections.emptySet());
        when(mInfoMediaManager.getMissingPermissionsInfo()).thenReturn(info);

        assertThat(mLocalMediaManager.getMissingPermissionsInfo()).isEqualTo(info);
    }

    @Test
    public void onMissingPermissionsUpdated_notifiesCallbacks() {
        ComponentName component = new ComponentName(TEST_PACKAGE_NAME, "TestClass");
        MissingPermissionsInfo info = new MissingPermissionsInfo(component, Collections.emptySet());

        mLocalMediaManager.mMediaDeviceCallback.onMissingPermissionsUpdated(info);

        verify(mCallback).onMissingPermissionsUpdated(info);
    }

    private void setUpTvEnvironment() {
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(mContext.getPackageManager());
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_LEANBACK, true);
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_TELEVISION, true);
    }

    private List<MediaDevice> setUpMediaDevice(int type,
            String id) {
        List<MediaDevice> devices = new ArrayList<>();
        MediaDevice device = mock(MediaDevice.class);
        when(device.getDeviceType()).thenReturn(type);
        when(device.getId()).thenReturn(id);
        devices.add(device);
        return devices;
    }

    private void setUpCachedDevice(BluetoothDevice bluetoothDevice,
            CachedBluetoothDeviceManager cachedDeviceManager, List<LocalBluetoothProfile> profiles,
            String address, String name) {

        CachedBluetoothDevice cachedDevice = mock(CachedBluetoothDevice.class);
        when(cachedDeviceManager.findDevice(bluetoothDevice)).thenReturn(cachedDevice);
        when(cachedDevice.getDevice()).thenReturn(bluetoothDevice);
        when(cachedDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(cachedDevice.isConnected()).thenReturn(false);
        when(cachedDevice.getUiAccessibleProfiles()).thenReturn(profiles);
        when(cachedDevice.getAddress()).thenReturn(address);
        when(cachedDevice.getName()).thenReturn(name);
        when(bluetoothDevice.getAddress()).thenReturn(address);
    }

    private LocalMediaManager setUpLocalMediaManagerWithNullBluetoothManager(
            InfoMediaManager infoMediaManager, String packageName,
            LocalMediaManager.DeviceCallback callback, AudioManager audioManager) {
        LocalMediaManager localMediaManager = new LocalMediaManager(
                mContext, /* localBluetoothManager= */ null, infoMediaManager, packageName);
        localMediaManager.registerCallback(callback);
        localMediaManager.mAudioManager = audioManager;

        return localMediaManager;
    }

    private CachedBluetoothDeviceManager setUpCachedDeviceManager() {
        CachedBluetoothDeviceManager cachedDeviceManager = mock(CachedBluetoothDeviceManager.class);
        when(mLocalBluetoothManager.getCachedDeviceManager()).thenReturn(cachedDeviceManager);
        return cachedDeviceManager;
    }

    private void setUpMostRecentlyConnectedDevice(BluetoothDevice bluetoothDevice) {
        List<BluetoothDevice> mostRecentlyConnected = new ArrayList<>();
        mostRecentlyConnected.add(bluetoothDevice);
        mShadowBluetoothAdapter.setMostRecentlyConnectedDevices(mostRecentlyConnected);
    }

    private List<LocalBluetoothProfile> setUpProfiles() {
        List<LocalBluetoothProfile> profiles = new ArrayList<>();
        profiles.add(mA2dpProfile);
        return profiles;
    }

    private void setUpAudioDeviceAttributes() {
        AudioDeviceAttributes audioDeviceAttributes = new AudioDeviceAttributes(
                AudioSystem.DEVICE_OUT_BLUETOOTH_A2DP, TEST_ADDRESS);
        when(mAudioManager.getMutingExpectedDevice()).thenReturn(audioDeviceAttributes);
    }
}