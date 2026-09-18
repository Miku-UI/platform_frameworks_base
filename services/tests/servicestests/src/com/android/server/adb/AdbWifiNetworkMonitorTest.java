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

package com.android.server.adb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public final class AdbWifiNetworkMonitorTest {

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    @Mock
    private ConnectivityManager mConnectivityManager;
    @Mock
    private Network mNetwork;
    @Mock
    private Context mContext;
    @Mock
    private ContentResolver mContentResolver;
    @Mock
    private Handler mAdbDebuggingHandler;

    private AdbWifiNetworkMonitor mAdbWifiNetworkMonitor;

    @Captor
    private ArgumentCaptor<NetworkRequest> mNetworkRequestCaptor;

    @Captor
    private ArgumentCaptor<Message> mHandlerMessageCaptor;

    @Before
    public void setUp() {
        when(mContext.getSystemServiceName(ConnectivityManager.class))
                .thenReturn(Context.CONNECTIVITY_SERVICE);
        when(mContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mConnectivityManager);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);

        mAdbWifiNetworkMonitor = Mockito.spy(new AdbWifiNetworkMonitor(mContext,
                mAdbDebuggingHandler
        ));

        mAdbWifiNetworkMonitor.register();

        verify(mConnectivityManager).registerNetworkCallback(
                mNetworkRequestCaptor.capture(), any(ConnectivityManager.NetworkCallback.class));
    }

    @Test
    public void registerNetworkCallback_onlyConsidersWifi() {
        assertArrayEquals(new int[]{NetworkCapabilities.TRANSPORT_WIFI},
                mNetworkRequestCaptor.getValue().getTransportTypes());
    }

    @Test
    public void registerNetworkCallback_filtersOutHotspotCallbacks() {
        assertTrue(mNetworkRequestCaptor.getValue().hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET));
    }

    @Test
    public void onLost_stopsAdbWifi() {
        mAdbWifiNetworkMonitor.onLost(mNetwork);

        assertAdbWifiStopped();
    }

    @Test
    public void onCapabilitiesChanged_nullWifiInfo_stopsAdbWifi() {
        // given
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStopped();
    }

    @Test
    public void onCapabilitiesChanged_invalidNetworkId_stopsAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(-1)
                .setSsid("ssid".getBytes(StandardCharsets.UTF_8))
                .setBssid("bssid")
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStopped();
    }

    @Test
    public void onCapabilitiesChanged_nullBssid_stopsAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setSsid("ssid".getBytes(StandardCharsets.UTF_8))
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStopped();
    }

    @Test
    public void onCapabilitiesChanged_nullSsid_stopsAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("bssid")
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStopped();
    }

    @Test
    public void onCapabilitiesChanged_emptyBssid_stopsAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("")
                .setSsid("ssid".getBytes(StandardCharsets.UTF_8))
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStopped();
    }

    @Test
    public void onCapabilitiesChanged_emptySsid_stopsAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("bssid")
                .setSsid("".getBytes(StandardCharsets.UTF_8))
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStopped();
    }

    @Test
    public void onCapabilitiesChanged_invalidSsid_stopsAdbWifi() {
        // given
        WifiInfo wifiInfo = Mockito.mock(WifiInfo.class);
        when(wifiInfo.getNetworkId()).thenReturn(1);
        when(wifiInfo.getBSSID()).thenReturn("bssid");
        when(wifiInfo.getSSID()).thenReturn(WifiManager.UNKNOWN_SSID);
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStopped();
    }

    @Test
    public void onCapabilitiesChanged_connectedToNetwork_attemptsToStartAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("bssid")
                .setSsid("ssid".getBytes(StandardCharsets.UTF_8))
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStarted();
    }


    @Test
    public void onCapabilitiesChanged_sameSsidAndBssid_deduplicatesCallback() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("bssid")
                .setSsid("ssid".getBytes(StandardCharsets.UTF_8))
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStarted();
    }

    @Test
    public void onCapabilitiesChanged_sameSsid_differentBssid_deduplicatesCallback() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("bssid")
                .setSsid("ssid".getBytes(StandardCharsets.UTF_8))
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("different_bssid")
                .setSsid("ssid".getBytes(StandardCharsets.UTF_8))
                .build();
        networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        assertAdbWifiStarted();
    }

    @Test
    public void onLost_resetsLastSsid_allowsReconnect() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("bssid")
                .setSsid("ssid".getBytes(StandardCharsets.UTF_8))
                .build();
        NetworkCapabilities networkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        verify(mAdbDebuggingHandler, times(1))
                .sendMessageAtTime(mHandlerMessageCaptor.capture(), anyLong());
        assertEquals(AdbDebuggingManager.AdbDebuggingHandler.MSG_START_TLS_SERVICE,
                mHandlerMessageCaptor.getValue().what);

        // when
        mAdbWifiNetworkMonitor.onLost(mNetwork);

        // then
        verify(mAdbDebuggingHandler, times(2))
                .sendMessageAtTime(mHandlerMessageCaptor.capture(), anyLong());
        assertEquals(AdbDebuggingManager.AdbDebuggingHandler.MSG_STOP_TLS_SERVICE,
                mHandlerMessageCaptor.getValue().what);

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, networkCapabilities);

        // then
        verify(mAdbDebuggingHandler, times(3))
                .sendMessageAtTime(mHandlerMessageCaptor.capture(), anyLong());
        assertEquals(AdbDebuggingManager.AdbDebuggingHandler.MSG_START_TLS_SERVICE,
                mHandlerMessageCaptor.getValue().what);
    }

    @Test
    public void register_isIdempotent() {
        // given: register() was already called in setUp()

        // when
        mAdbWifiNetworkMonitor.register(); // Call it again

        // then
        verify(mConnectivityManager, times(1)).registerNetworkCallback(
                any(NetworkRequest.class), any(ConnectivityManager.NetworkCallback.class));
    }

    @Test
    public void unregister_isIdempotent() {
        // when
        mAdbWifiNetworkMonitor.unregister();
        mAdbWifiNetworkMonitor.unregister();

        // then
        verify(mConnectivityManager, times(1)).unregisterNetworkCallback(
                any(ConnectivityManager.NetworkCallback.class));
    }

    private void assertAdbWifiStopped() {
        verify(mAdbDebuggingHandler, times(1))
                .sendMessageAtTime(mHandlerMessageCaptor.capture(), anyLong());
        assertEquals(AdbDebuggingManager.AdbDebuggingHandler.MSG_STOP_TLS_SERVICE,
                mHandlerMessageCaptor.getValue().what);
    }

    private void assertAdbWifiStarted() {
        verify(mAdbDebuggingHandler, times(1))
                .sendMessageAtTime(mHandlerMessageCaptor.capture(), anyLong());
        assertEquals(AdbDebuggingManager.AdbDebuggingHandler.MSG_START_TLS_SERVICE,
                mHandlerMessageCaptor.getValue().what);
    }
}
