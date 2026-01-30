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

package com.android.server.connectivity;

import static android.net.IpSecAlgorithm.AUTH_AES_CMAC;
import static android.net.IpSecAlgorithm.AUTH_AES_XCBC;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_AES_GCM;
import static android.net.IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305;
import static android.net.IpSecAlgorithm.AUTH_HMAC_MD5;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA1;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA256;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA384;
import static android.net.IpSecAlgorithm.AUTH_HMAC_SHA512;
import static android.net.IpSecAlgorithm.CRYPT_AES_CBC;
import static android.net.IpSecAlgorithm.CRYPT_AES_CTR;

import static com.android.server.connectivity.VpnConnectivityMetrics.IP_PROTOCOL_IPv4;
import static com.android.server.connectivity.VpnConnectivityMetrics.IP_PROTOCOL_IPv4v6;
import static com.android.server.connectivity.VpnConnectivityMetrics.IP_PROTOCOL_IPv6;
import static com.android.server.connectivity.VpnConnectivityMetrics.IP_PROTOCOL_UNKNOWN;
import static com.android.server.connectivity.VpnConnectivityMetrics.VPN_PROFILE_TYPE_UNKNOWN;
import static com.android.server.connectivity.VpnConnectivityMetrics.VPN_TYPE_UNKNOWN;
import static com.android.testutils.Cleanup.testAndCleanup;
import static com.android.server.connectivity.VpnConnectivityMetrics.buildAllowedAlgorithmsBitmask;
import static com.android.server.connectivity.VpnConnectivityMetrics.checkIpProtocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.ConnectivityManager;
import android.net.Ikev2VpnProfile;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.net.VpnProfile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class VpnConnectivityMetricsTest {
    private static final int USER_ID = 10;
    private static final String VPN_CLIENT_IP_V4 = "192.0.2.1/32";
    private static final String VPN_CLIENT_IP_V6 = "2001:db8:1:2::ffe/128";
    private static final String VPN_SERVER_IP_V4 = "192.0.2.2";
    private static final String VPN_SERVER_IP_V6 = "2001:db8::1";

    @Mock
    private ConnectivityManager mCm;
    @Mock
    private VpnConnectivityMetrics.Dependencies mDeps;
    private VpnConnectivityMetrics mMetrics;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMetrics = new VpnConnectivityMetrics(USER_ID, mCm, mDeps);
    }

    @Test
    public void testBuildAllowedAlgorithmsBitmask() {
        assertEquals(1536, buildAllowedAlgorithmsBitmask(List.of(CRYPT_AES_CBC, CRYPT_AES_CTR)));
        assertEquals(496, buildAllowedAlgorithmsBitmask(
                List.of(AUTH_HMAC_MD5, AUTH_HMAC_SHA1, AUTH_HMAC_SHA256, AUTH_HMAC_SHA384,
                        AUTH_HMAC_SHA512)));
        assertEquals(3, buildAllowedAlgorithmsBitmask(List.of(AUTH_AES_XCBC, AUTH_AES_CMAC)));
        assertEquals(12, buildAllowedAlgorithmsBitmask(
                List.of(AUTH_CRYPT_AES_GCM, AUTH_CRYPT_CHACHA20_POLY1305)));
        assertEquals(1999, buildAllowedAlgorithmsBitmask(Ikev2VpnProfile.DEFAULT_ALGORITHMS));
    }

    @Test
    public void testBuildAllowedAlgorithmsBitmask_UnknownAlgorithm() {
        final AtomicBoolean hasFailed = new AtomicBoolean(false);
        final Log.TerribleFailureHandler originalHandler =
                Log.setWtfHandler((tag, what, system) -> hasFailed.set(true));
        testAndCleanup(() -> {
            buildAllowedAlgorithmsBitmask(List.of("unknown"));
            assertTrue(hasFailed.get());
        }, () -> Log.setWtfHandler(originalHandler));
    }

    @Test
    public void testCheckIpProtocol() {
        final LinkAddress vpnClientIpv4 = new LinkAddress(VPN_CLIENT_IP_V4);
        final LinkAddress vpnClientIpv6 = new LinkAddress(VPN_CLIENT_IP_V6);

        assertEquals(IP_PROTOCOL_UNKNOWN, checkIpProtocol(List.of()));
        assertEquals(IP_PROTOCOL_IPv4, checkIpProtocol(List.of(vpnClientIpv4)));
        assertEquals(IP_PROTOCOL_IPv6, checkIpProtocol(List.of(vpnClientIpv6)));
        assertEquals(IP_PROTOCOL_IPv4v6, checkIpProtocol(List.of(vpnClientIpv4, vpnClientIpv6)));
    }

    private Network verifyNotifyVpnConnected() {
        final Network cellNetwork = new Network(1234);
        final NetworkCapabilities cellCap = new NetworkCapabilities();
        cellCap.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        doReturn(cellCap).when(mCm).getNetworkCapabilities(cellNetwork);

        // Fill in metrics data
        mMetrics.setVpnType(VpnManager.TYPE_VPN_PLATFORM);
        mMetrics.setMtu(1327);
        mMetrics.setVpnProfileType(VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS);
        mMetrics.setAllowedAlgorithms(Ikev2VpnProfile.DEFAULT_ALGORITHMS);
        mMetrics.updateUnderlyingNetworkTypes(new Network[] { cellNetwork });
        mMetrics.updateVpnNetworkIpProtocol(
                List.of(new LinkAddress(VPN_CLIENT_IP_V4), new LinkAddress(VPN_CLIENT_IP_V6)));
        mMetrics.updateServerIpProtocol(InetAddresses.parseNumericAddress(VPN_SERVER_IP_V4));

        // Verify a vpn connected event with the filled in data.
        mMetrics.notifyVpnConnected();
        verify(mDeps, times(1)).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                true /* connected */,
                USER_ID);

        return cellNetwork;
    }

    @Test
    public void testNotifyVpnConnected() {
        verifyNotifyVpnConnected();
    }

    @Test
    public void testNotifyDisconnected() {
        final Network wifiNetwork = new Network(1235);
        final NetworkCapabilities wifiCap = new NetworkCapabilities();
        wifiCap.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        doReturn(wifiCap).when(mCm).getNetworkCapabilities(wifiNetwork);

        // Fill in metrics data
        mMetrics.setVpnType(VpnManager.TYPE_VPN_PLATFORM);
        mMetrics.setMtu(1280);
        mMetrics.setVpnProfileType(VpnProfile.TYPE_IKEV2_FROM_IKE_TUN_CONN_PARAMS);
        mMetrics.setAllowedAlgorithms(Ikev2VpnProfile.DEFAULT_ALGORITHMS);
        mMetrics.updateUnderlyingNetworkTypes(new Network[] { wifiNetwork });
        mMetrics.updateVpnNetworkIpProtocol(List.of(new LinkAddress(VPN_CLIENT_IP_V6)));
        mMetrics.updateServerIpProtocol(InetAddresses.parseNumericAddress(VPN_SERVER_IP_V6));

        // Verify a vpn disconnected event with the filled in data.
        mMetrics.notifyVpnDisconnected();
        verify(mDeps).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv6,
                IP_PROTOCOL_IPv6,
                VpnProfile.TYPE_IKEV2_FROM_IKE_TUN_CONN_PARAMS + 1,
                1999 /* allowedAlgorithms */,
                1280 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_WIFI },
                false /* connected */,
                USER_ID);
    }

    @Test
    public void testInvalidMetrics() {
        final Log.TerribleFailureHandler originalHandler =
                Log.setWtfHandler((tag, what, system) -> {});
        testAndCleanup(() -> {
            // Fill in invalid metrics data
            mMetrics.setVpnType(VpnManager.TYPE_VPN_NONE);
            mMetrics.setMtu(1280);
            mMetrics.setVpnProfileType(-1);
            mMetrics.setAllowedAlgorithms(List.of("unknown"));
            mMetrics.updateVpnNetworkIpProtocol(List.of());
            mMetrics.updateServerIpProtocol(null);

            // Verify a vpn connected event with the filled in correct data.
            mMetrics.notifyVpnConnected();
            verify(mDeps).statsWrite(
                    VPN_TYPE_UNKNOWN,
                    IP_PROTOCOL_UNKNOWN,
                    IP_PROTOCOL_UNKNOWN,
                    VPN_PROFILE_TYPE_UNKNOWN,
                    0 /* allowedAlgorithms */,
                    1280 /* mtu */,
                    new int[0],
                    true /* connected */,
                    USER_ID);

            // Fill in invalid metrics data again
            mMetrics.setVpnType(VpnManager.TYPE_VPN_OEM_SERVICE);
            mMetrics.setVpnProfileType(VpnProfile.TYPE_IKEV2_FROM_IKE_TUN_CONN_PARAMS + 1);
            final List<String> allowedAlgorithms = new ArrayList<>();
            allowedAlgorithms.add(null);
            mMetrics.setAllowedAlgorithms(allowedAlgorithms);
            final List<LinkAddress> addresses = new ArrayList<>();
            addresses.add(null);
            mMetrics.updateVpnNetworkIpProtocol(addresses);
            mMetrics.updateServerIpProtocol(null);

            // Verify a vpn disconnected event with the filled in correct data.
            mMetrics.notifyVpnDisconnected();
            verify(mDeps).statsWrite(
                    VPN_TYPE_UNKNOWN,
                    IP_PROTOCOL_UNKNOWN,
                    IP_PROTOCOL_UNKNOWN,
                    VPN_PROFILE_TYPE_UNKNOWN,
                    0 /* allowedAlgorithms */,
                    1280 /* mtu */,
                    new int[0],
                    false /* connected */,
                    USER_ID);
        }, () -> Log.setWtfHandler(originalHandler));
    }

    @Test
    public void testResetMetrics() {
        final Network cellNetwork = new Network(1234);
        final NetworkCapabilities cellCap = new NetworkCapabilities();
        cellCap.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        doReturn(cellCap).when(mCm).getNetworkCapabilities(cellNetwork);

        // Fill in metrics data
        mMetrics.setVpnType(VpnManager.TYPE_VPN_PLATFORM);
        mMetrics.setMtu(1327);
        mMetrics.setVpnProfileType(VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS);
        mMetrics.setAllowedAlgorithms(Ikev2VpnProfile.DEFAULT_ALGORITHMS);
        mMetrics.updateUnderlyingNetworkTypes(new Network[] { cellNetwork });
        mMetrics.updateVpnNetworkIpProtocol(
                List.of(new LinkAddress(VPN_CLIENT_IP_V4), new LinkAddress(VPN_CLIENT_IP_V6)));
        mMetrics.updateServerIpProtocol(InetAddresses.parseNumericAddress(VPN_SERVER_IP_V4));

        // Verify a vpn connected event with the filled in data.
        mMetrics.notifyVpnConnected();
        verify(mDeps).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                true /* connected */,
                USER_ID);

        // Reset all metrics and verify again. All data should be the default value.
        mMetrics.resetMetrics();
        mMetrics.notifyVpnConnected();
        verify(mDeps).statsWrite(
                VPN_TYPE_UNKNOWN,
                IP_PROTOCOL_UNKNOWN,
                IP_PROTOCOL_UNKNOWN,
                VPN_PROFILE_TYPE_UNKNOWN,
                0 /* allowedAlgorithms */,
                0 /* mtu */,
                new int[0],
                true /* connected */,
                USER_ID);
    }

    @Test
    public void testUpdateServerIpProtocol() {
        verifyNotifyVpnConnected();

        // Update server IP to IPv6, which should trigger both disconnected and connected
        // notifications.
        mMetrics.updateServerIpProtocol(InetAddresses.parseNumericAddress(VPN_SERVER_IP_V6));

        // Verify a vpn disconnected event.
        verify(mDeps).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                false /* connected */,
                USER_ID);

        // Verify a vpn connected event with the new server IP protocol.
        verify(mDeps).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv6,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                true /* connected */,
                USER_ID);
    }

    @Test
    public void testUpdateServerIpProtocol_NoIpProtocolChange() {
        verifyNotifyVpnConnected();

        // Update with the same server IP, which should not trigger any notifications.
        mMetrics.updateServerIpProtocol(InetAddresses.parseNumericAddress(VPN_SERVER_IP_V4));

        verify(mDeps, never()).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                false /* connected */,
                USER_ID);

        verify(mDeps, times(1)).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                true /* connected */,
                USER_ID);
    }

    @Test
    public void testUpdateVpnNetworkIpProtocol() {
        verifyNotifyVpnConnected();

        // Update vpn network IP to IPv4 only, which should trigger both disconnected and connected
        // notifications.
        mMetrics.updateVpnNetworkIpProtocol(List.of(new LinkAddress(VPN_CLIENT_IP_V4)));

        // Verify a vpn disconnected event.
        verify(mDeps).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                false /* connected */,
                USER_ID);

        // Verify a vpn connected event with the new server IP protocol.
        verify(mDeps).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                true /* connected */,
                USER_ID);
    }

    @Test
    public void testUpdateVpnNetworkIpProtocol_NoIpProtocolChange() {
        verifyNotifyVpnConnected();

        // Update with the same vpn network IP, which should not trigger any notifications.
        mMetrics.updateVpnNetworkIpProtocol(
                List.of(new LinkAddress(VPN_CLIENT_IP_V4), new LinkAddress(VPN_CLIENT_IP_V6)));

        verify(mDeps, never()).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                false /* connected */,
                USER_ID);

        verify(mDeps, times(1)).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                true /* connected */,
                USER_ID);
    }

    @Test
    public void testUpdateUnderlyingNetworkTypes() {
        verifyNotifyVpnConnected();

        final Network wifiNetwork = new Network(4321);
        final NetworkCapabilities wifiCap = new NetworkCapabilities();
        wifiCap.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        doReturn(wifiCap).when(mCm).getNetworkCapabilities(wifiNetwork);

        // Update underlying network to Wi-Fi, which should trigger both disconnected and connected
        // notifications.
        mMetrics.updateUnderlyingNetworkTypes(new Network[] { wifiNetwork });

        // Verify a vpn disconnected event.
        verify(mDeps).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                false /* connected */,
                USER_ID);

        // Verify a vpn connected event with the new underlying network.
        verify(mDeps).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_WIFI },
                true /* connected */,
                USER_ID);
    }

    @Test
    public void testUpdateUnderlyingNetworkTypes_NoNetworkTypeChange() {
        final Network cellnetwork = verifyNotifyVpnConnected();

        // Update with thhe same underlying network, which should not trigger any notifications.
        mMetrics.updateUnderlyingNetworkTypes(new Network[] { cellnetwork });

        verify(mDeps, never()).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                false /* connected */,
                USER_ID);

        verify(mDeps, times(1)).statsWrite(
                VpnManager.TYPE_VPN_PLATFORM,
                IP_PROTOCOL_IPv4v6,
                IP_PROTOCOL_IPv4,
                VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS + 1,
                1999 /* allowedAlgorithms */,
                1327 /* mtu */,
                new int[] { NetworkCapabilities.TRANSPORT_CELLULAR },
                true /* connected */,
                USER_ID);
    }
}
