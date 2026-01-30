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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaRoute2Info;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.settingslib.bluetooth.CachedBluetoothDevice;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class BluetoothMediaDeviceTest {

    private static final String TEST_ADDRESS = "11:22:33:44:55:66";
    private static final String TEST_ROUTE_NAME = "Name from route";
    private static final String TEST_CACHED_DEVICE_NAME = "Name from cached device";

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private CachedBluetoothDevice mDevice;

    @Mock
    private MediaRoute2Info mRouteInfo;

    private Context mContext;
    private BluetoothMediaDevice mBluetoothMediaDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        when(mDevice.isActiveDevice(BluetoothProfile.A2DP)).thenReturn(true);
        when(mDevice.isActiveDevice(BluetoothProfile.HEARING_AID)).thenReturn(true);
        when(mDevice.isActiveDevice(BluetoothProfile.LE_AUDIO)).thenReturn(true);

        mBluetoothMediaDevice = new BluetoothMediaDevice(mContext, mDevice,
                null, /* dynamicRouteAttributes= */ null, null);
    }

    @Test
    public void isCachedBluetoothDeviceConnected_deviceConnected_returnTrue() {
        when(mDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mDevice.isConnected()).thenReturn(true);

        assertThat(mBluetoothMediaDevice.isConnected()).isTrue();
    }

    @Test
    public void isCachedBluetoothDeviceConnected_deviceNotConnected_returnFalse() {
        when(mDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
        when(mDevice.isConnected()).thenReturn(false);

        assertThat(mBluetoothMediaDevice.isConnected()).isFalse();
    }

    @Test
    public void isFastPairDevice_isUntetheredHeadset_returnTrue() {
        final BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        when(mDevice.getDevice()).thenReturn(bluetoothDevice);

        final String value = "True";
        final byte[] bytes = value.getBytes();
        when(bluetoothDevice.getMetadata(BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET))
                .thenReturn(bytes);

        assertThat(mBluetoothMediaDevice.isFastPairDevice()).isTrue();
    }

    @Test
    public void isFastPairDevice_isNotUntetheredHeadset_returnFalse() {
        final BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        when(mDevice.getDevice()).thenReturn(bluetoothDevice);

        final String value = "asjdaioshfaio";
        final byte[] bytes = value.getBytes();
        when(bluetoothDevice.getMetadata(BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET))
                .thenReturn(bytes);

        assertThat(mBluetoothMediaDevice.isFastPairDevice()).isFalse();
    }

    @Test
    public void getIcon_isNotFastPairDevice_drawableTypeIsNotBitmapDrawable() {
        final BluetoothDevice bluetoothDevice = mock(BluetoothDevice.class);
        when(mDevice.getDevice()).thenReturn(bluetoothDevice);

        final String value = "False";
        final byte[] bytes = value.getBytes();
        when(bluetoothDevice.getMetadata(BluetoothDevice.METADATA_IS_UNTETHERED_HEADSET))
                .thenReturn(bytes);

        assertThat(mBluetoothMediaDevice.getIcon() instanceof BitmapDrawable).isFalse();
    }

    @Test
    public void getId_returnsCachedBluetoothDeviceAddress() {
        when(mDevice.getAddress()).thenReturn(TEST_ADDRESS);
        assertThat(mBluetoothMediaDevice.getId()).isEqualTo(TEST_ADDRESS);
    }

    @Test
    public void getName_hasRouteInfo_usesNameFromRoute() {
        when(mRouteInfo.getName()).thenReturn(TEST_ROUTE_NAME);
        when(mDevice.getName()).thenReturn(TEST_CACHED_DEVICE_NAME);
        MediaDevice bluetoothMediaDevice = new BluetoothMediaDevice(mContext, mDevice,
                mRouteInfo, /* dynamicRouteAttributes= */ null, null);

        assertThat(bluetoothMediaDevice.getName()).isEqualTo(TEST_ROUTE_NAME);
    }

    @Test
    public void getName_noRouteInfo_usesNameFromCachedDevice() {
        when(mDevice.getName()).thenReturn(TEST_CACHED_DEVICE_NAME);

        MediaDevice bluetoothMediaDevice = new BluetoothMediaDevice(mContext, mDevice,
                null, /* dynamicRouteAttributes= */ null, null);
        assertThat(bluetoothMediaDevice.getName()).isEqualTo(TEST_CACHED_DEVICE_NAME);
    }

    @Test
    public void getIsMutingExpectedDevice_dependsOnConstructorArgument() {
        MediaDevice bluetoothMediaDevice = new BluetoothMediaDevice(mContext, mDevice,
                null, /* dynamicRouteAttributes= */ null, null);
        assertThat(bluetoothMediaDevice.isMutingExpectedDevice()).isFalse();

        MediaDevice mutingExpectedDevice = new BluetoothMediaDevice(mContext, mDevice,
                null, /* dynamicRouteAttributes= */ null, null, /* isMutingExpectedDevice= */ true);
        assertThat(mutingExpectedDevice.isMutingExpectedDevice()).isTrue();
    }
}
