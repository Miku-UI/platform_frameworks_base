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

package com.android.server.media;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.internal.R;
import com.android.media.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public class BluetoothDeviceRoutesManagerTest {

    private static final String FAKE_ADDRESS = "00:11:22:33:44:55";
    private static final String FAKE_ALIAS = "Fake Alias";
    private static final String UNKNOWN_NAME = "Unknown Name";

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private BluetoothAdapter mMockBluetoothAdapter;
    @Mock private BluetoothProfileMonitor mMockBluetoothProfileMonitor;
    @Mock private BluetoothDevice mMockBluetoothDevice;
    @Mock private BluetoothDeviceRoutesManager.BluetoothRoutesUpdatedListener mMockListener;

    private BluetoothDeviceRoutesManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getText(R.string.unknownName)).thenReturn(UNKNOWN_NAME);
        when(mMockResources.getText(R.string.bluetooth_a2dp_audio_route_name))
                .thenReturn("Bluetooth route");

        when(mMockBluetoothDevice.getAddress()).thenReturn(FAKE_ADDRESS);
        when(mMockBluetoothDevice.getAlias()).thenReturn(FAKE_ALIAS);
        when(mMockBluetoothDevice.isConnected()).thenReturn(true);

        // Ensure the device is recognized as a connected A2DP device.
        when(mMockBluetoothProfileMonitor.isProfileSupported(anyInt(), any())).thenReturn(false);
        when(mMockBluetoothProfileMonitor.isProfileSupported(eq(BluetoothProfile.A2DP), any()))
                .thenReturn(true);

        when(mMockBluetoothAdapter.getBondedDevices()).thenReturn(Set.of(mMockBluetoothDevice));

        mManager =
                new BluetoothDeviceRoutesManager(
                        mMockContext,
                        Looper.getMainLooper(),
                        mMockBluetoothAdapter,
                        mMockBluetoothProfileMonitor);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void start_doesNotHoldLockWhileCallingBluetoothStack() {
        AtomicBoolean getBondedDevicesLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            getBondedDevicesLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return Set.of(mMockBluetoothDevice);
                        })
                .when(mMockBluetoothAdapter)
                .getBondedDevices();
        AtomicBoolean getAddressLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            getAddressLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return FAKE_ADDRESS;
                        })
                .when(mMockBluetoothDevice)
                .getAddress();
        AtomicBoolean isConnectedLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            isConnectedLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return true;
                        })
                .when(mMockBluetoothDevice)
                .isConnected();
        AtomicBoolean getAliasLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            getAliasLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return FAKE_ALIAS;
                        })
                .when(mMockBluetoothDevice)
                .getAlias();
        AtomicBoolean isProfileSupportedLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            isProfileSupportedLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return invocation.getArgument(0).equals(BluetoothProfile.A2DP);
                        })
                .when(mMockBluetoothProfileMonitor)
                .isProfileSupported(anyInt(), any());

        mManager.start(UserHandle.ALL, mMockListener);

        verify(mMockBluetoothAdapter).getBondedDevices();
        assertThat(getBondedDevicesLockHeld.get()).isFalse();
        verify(mMockBluetoothDevice, org.mockito.Mockito.atLeastOnce()).getAddress();
        assertThat(getAddressLockHeld.get()).isFalse();
        verify(mMockBluetoothDevice).isConnected();
        assertThat(isConnectedLockHeld.get()).isFalse();
        verify(mMockBluetoothDevice, org.mockito.Mockito.atLeastOnce()).getAlias();
        assertThat(getAliasLockHeld.get()).isFalse();
        verify(mMockBluetoothProfileMonitor).isProfileSupported(eq(BluetoothProfile.A2DP), any());
        assertThat(isProfileSupportedLockHeld.get()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void getNameForBluetoothAddress_doesNotCallBluetoothStack() {
        mManager.start(UserHandle.ALL, mMockListener);
        clearInvocations(mMockBluetoothDevice);

        String name = mManager.getNameForBluetoothAddress(FAKE_ADDRESS);

        assertThat(name).isEqualTo(FAKE_ALIAS);
        verify(mMockBluetoothDevice, never()).getAlias();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void activateBluetoothDeviceWithAddress_doesNotHoldLockWhileCallingBluetoothStack() {
        mManager.start(UserHandle.ALL, mMockListener);
        clearInvocations(mMockBluetoothAdapter);
        AtomicBoolean setActiveDeviceLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            setActiveDeviceLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return null;
                        })
                .when(mMockBluetoothAdapter)
                .setActiveDevice(any(), anyInt());

        mManager.activateBluetoothDeviceWithAddress(FAKE_ADDRESS);

        verify(mMockBluetoothAdapter).setActiveDevice(eq(mMockBluetoothDevice), anyInt());
        assertThat(setActiveDeviceLockHeld.get()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void isMediaOnlyRouteInBroadcast_doesNotHoldLockWhileCallingBluetoothStack() {
        mManager.start(UserHandle.ALL, mMockListener);
        clearInvocations(mMockBluetoothProfileMonitor);
        AtomicBoolean isMediaOnlyLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            isMediaOnlyLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return false;
                        })
                .when(mMockBluetoothProfileMonitor)
                .isMediaOnlyDeviceInBroadcast(any());

        mManager.isMediaOnlyRouteInBroadcast(FAKE_ADDRESS);

        verify(mMockBluetoothProfileMonitor).isMediaOnlyDeviceInBroadcast(eq(mMockBluetoothDevice));
        assertThat(isMediaOnlyLockHeld.get()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void setRouteVolume_doesNotHoldLockWhileCallingBluetoothStack() {
        mManager.start(UserHandle.ALL, mMockListener);
        clearInvocations(mMockBluetoothProfileMonitor);
        AtomicBoolean setDeviceVolumeLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            setDeviceVolumeLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return null;
                        })
                .when(mMockBluetoothProfileMonitor)
                .setDeviceVolume(any(), anyInt(), anyBoolean());

        mManager.setRouteVolume(FAKE_ADDRESS, 10);

        verify(mMockBluetoothProfileMonitor)
                .setDeviceVolume(eq(mMockBluetoothDevice), eq(10), anyBoolean());
        assertThat(setDeviceVolumeLockHeld.get()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void getRouteIdForBluetoothAddress_doesNotHoldLockWhileCallingBluetoothStack() {
        mManager.start(UserHandle.ALL, mMockListener);
        clearInvocations(mMockBluetoothProfileMonitor);
        AtomicBoolean isProfileSupportedLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            isProfileSupportedLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return invocation.getArgument(0).equals(BluetoothProfile.A2DP);
                        })
                .when(mMockBluetoothProfileMonitor)
                .isProfileSupported(anyInt(), any());

        mManager.getRouteIdForBluetoothAddress(FAKE_ADDRESS);

        verify(mMockBluetoothProfileMonitor, org.mockito.Mockito.atLeastOnce())
                .isProfileSupported(anyInt(), any());
        assertThat(isProfileSupportedLockHeld.get()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void getAvailableBluetoothRoutes_doesNotCallBluetoothStack() {
        mManager.start(UserHandle.ALL, mMockListener);
        clearInvocations(mMockBluetoothAdapter);
        clearInvocations(mMockBluetoothDevice);
        clearInvocations(mMockBluetoothProfileMonitor);

        mManager.getAvailableBluetoothRoutes();

        verify(mMockBluetoothAdapter, never()).getBondedDevices();
        verify(mMockBluetoothDevice, never()).isConnected();
        verify(mMockBluetoothProfileMonitor, never()).isProfileSupported(anyInt(), any());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void getBroadcastingDeviceRoutes_doesNotHoldLockWhileCallingBluetoothStack() {
        mManager.start(UserHandle.ALL, mMockListener);
        clearInvocations(mMockBluetoothProfileMonitor);
        AtomicBoolean getDevicesWithBroadcastSourceLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            getDevicesWithBroadcastSourceLockHeld.set(
                                    Thread.holdsLock(mManager.mLock));
                            return java.util.List.of(mMockBluetoothDevice);
                        })
                .when(mMockBluetoothProfileMonitor)
                .getDevicesWithBroadcastSource();
        AtomicBoolean isMediaOnlyLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            isMediaOnlyLockHeld.set(Thread.holdsLock(mManager.mLock));
                            return false;
                        })
                .when(mMockBluetoothProfileMonitor)
                .isMediaOnlyDeviceInBroadcast(any());

        mManager.getBroadcastingDeviceRoutes();

        verify(mMockBluetoothProfileMonitor).getDevicesWithBroadcastSource();
        assertThat(getDevicesWithBroadcastSourceLockHeld.get()).isFalse();
        verify(mMockBluetoothProfileMonitor).isMediaOnlyDeviceInBroadcast(eq(mMockBluetoothDevice));
        assertThat(isMediaOnlyLockHeld.get()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FIX_MR2_DEADLOCK_ON_BT_GET_ALIAS)
    public void isLEAudioBroadcastSupported_doesNotHoldLockWhileCallingBluetoothStack() {
        mManager.start(UserHandle.ALL, mMockListener);
        clearInvocations(mMockBluetoothAdapter);
        AtomicBoolean isLeAudioBroadcastAssistantSupportedLockHeld = new AtomicBoolean(false);
        doAnswer(
                        invocation -> {
                            isLeAudioBroadcastAssistantSupportedLockHeld.set(
                                    Thread.holdsLock(mManager.mLock));
                            return android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;
                        })
                .when(mMockBluetoothAdapter)
                .isLeAudioBroadcastAssistantSupported();

        mManager.isLEAudioBroadcastSupported();

        verify(mMockBluetoothAdapter).isLeAudioBroadcastAssistantSupported();
        assertThat(isLeAudioBroadcastAssistantSupportedLockHeld.get()).isFalse();
    }
}
