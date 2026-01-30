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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
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

@RunWith(JUnit4.class)
public final class AdbWifiNetworkMonitorTest {

    private static final int ADB_WIFI_ENABLED = 1;
    private static final int ADB_WIFI_DISABLED = 0;

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    @Mock
    private AdbWifiNetworkMonitor.IsTrustedNetworkChecker mIsTrustedNetworkChecker;
    @Mock
    private ConnectivityManager mConnectivityManager;
    @Mock
    private Network mNetwork;
    @Mock
    private Context mContext;
    @Mock
    private ContentResolver mContentResolver;
    private AdbWifiNetworkMonitor mAdbWifiNetworkMonitor;

    @Captor
    private ArgumentCaptor<NetworkRequest> mNetworkRequestCaptor;

    private NetworkCapabilities mNetworkCapabilities;
    private int mAdbWifiEnabledSetting = -1;

    @Before
    public void setUp() {
        when(mContext.getSystemServiceName(ConnectivityManager.class))
                .thenReturn(Context.CONNECTIVITY_SERVICE);
        when(mContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mConnectivityManager);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);

        mAdbWifiNetworkMonitor = Mockito.spy(new AdbWifiNetworkMonitor(mContext,
                mIsTrustedNetworkChecker));

        doAnswer(invocation -> {
            boolean enable = invocation.getArgument(0);
            mAdbWifiEnabledSetting = enable ? ADB_WIFI_ENABLED : ADB_WIFI_DISABLED;
            return null; // for void methods
        }).when(mAdbWifiNetworkMonitor).setAdbWifiState(anyBoolean(), anyString());

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
    public void onLost_disablesAdbWifi() {
        mAdbWifiNetworkMonitor.onLost(mNetwork);
        assertEquals(ADB_WIFI_DISABLED, mAdbWifiEnabledSetting);
    }

    @Test
    public void onCapabilitiesChanged_nullWifiInfo_disablesAdbWifi() {
        // given
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        assertEquals(ADB_WIFI_DISABLED, mAdbWifiEnabledSetting);
    }

    @Test
    public void onCapabilitiesChanged_invalidNetworkId_disablesAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder().setNetworkId(-1).build();
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        assertEquals(ADB_WIFI_DISABLED, mAdbWifiEnabledSetting);
    }

    @Test
    public void onCapabilitiesChanged_nullBssid_disablesAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder().setNetworkId(1).build();
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        assertEquals(ADB_WIFI_DISABLED, mAdbWifiEnabledSetting);
    }

    @Test
    public void onCapabilitiesChanged_emptyBssid_disablesAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("")
                .build();
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        assertEquals(ADB_WIFI_DISABLED, mAdbWifiEnabledSetting);
    }

    @Test
    public void onCapabilitiesChanged_trustedNetwork_enablesAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("trusted_bssid")
                .build();
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();
        when(mIsTrustedNetworkChecker.isTrusted("trusted_bssid")).thenReturn(true);

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        assertEquals(ADB_WIFI_ENABLED, mAdbWifiEnabledSetting);
    }

    @Test
    public void onCapabilitiesChanged_notTrustedNetwork_disablesAdbWifi() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("untrusted_bssid")
                .build();
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();
        when(mIsTrustedNetworkChecker.isTrusted("untrusted_bssid")).thenReturn(false);

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        assertEquals(ADB_WIFI_DISABLED, mAdbWifiEnabledSetting);
    }

    @Test
    public void onCapabilitiesChanged_sameBssid_deduplicatesCallback() {
        // given
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("trusted_bssid")
                .build();
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();
        when(mIsTrustedNetworkChecker.isTrusted("trusted_bssid")).thenReturn(true);

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        verify(mAdbWifiNetworkMonitor, times(1)).setAdbWifiState(anyBoolean(), anyString());
    }

    @Test
    public void onLost_resetsLastBssid_allowsReconnect() {
        // given: A trusted network connects
        WifiInfo wifiInfo = new WifiInfo.Builder()
                .setNetworkId(1)
                .setBssid("trusted_bssid")
                .build();
        mNetworkCapabilities = new NetworkCapabilities.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setTransportInfo(wifiInfo)
                .build();
        when(mIsTrustedNetworkChecker.isTrusted("trusted_bssid")).thenReturn(true);

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        assertEquals(ADB_WIFI_ENABLED, mAdbWifiEnabledSetting);

        // when
        mAdbWifiNetworkMonitor.onLost(mNetwork);

        // then
        assertEquals(ADB_WIFI_DISABLED, mAdbWifiEnabledSetting);

        // when
        mAdbWifiNetworkMonitor.onCapabilitiesChanged(mNetwork, mNetworkCapabilities);

        // then
        assertEquals(ADB_WIFI_ENABLED, mAdbWifiEnabledSetting);
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
    public void unregister_doesNothing() {
        // given: register() was already called in setUp()

        // when
        mAdbWifiNetworkMonitor.unregister();

        // then
        verify(mConnectivityManager, never()).unregisterNetworkCallback(
                any(ConnectivityManager.NetworkCallback.class));
    }
}
