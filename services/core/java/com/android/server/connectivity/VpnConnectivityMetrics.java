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
import static android.net.VpnManager.TYPE_VPN_OEM;

import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnManager;
import android.util.IndentingPrintWriter;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.net.VpnProfile;
import com.android.internal.util.FrameworkStatsLog;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Class to record the VpnConnectionReported into statsd, facilitating the logging of independent
 * VPN connection details per user.
 */
public class VpnConnectivityMetrics {
    private static final String TAG = VpnConnectivityMetrics.class.getSimpleName();
    private static final int MAX_LOG_RECORDS = 100;
    private final LocalLog mMetricLogs = new LocalLog(MAX_LOG_RECORDS);
    // Copied from corenetworking platform vpn enum
    @VisibleForTesting
    static final int VPN_TYPE_UNKNOWN = 0;
    @VisibleForTesting
    static final int VPN_PROFILE_TYPE_UNKNOWN = 0;
    private static final int VPN_PROFILE_TYPE_IKEV2_FROM_IKE_TUN_CONN_PARAMS = 10;
    private static final int UNKNOWN_UNDERLYING_NETWORK_TYPE = -1;
    @VisibleForTesting
    static final int IP_PROTOCOL_UNKNOWN = 0;
    @VisibleForTesting
    static final int IP_PROTOCOL_IPv4 = 1;
    @VisibleForTesting
    static final int IP_PROTOCOL_IPv6 = 2;
    @VisibleForTesting
    static final int IP_PROTOCOL_IPv4v6 = 3;
    private static final SparseArray<String> sAlgorithms = new SparseArray<>();
    /**
     * A static mapping from {@link VpnProfile} types to the corresponding integer values
     * defined in the {@code VpnProfileType} enum for metrics reporting. This allows for a
     * direct and efficient lookup of the profile type integer based on the VpnProfile constant.
     */
    private static final SparseIntArray sVpnProfileTypeMap = new SparseIntArray();
    private final int mUserId;
    @NonNull
    private final ConnectivityManager mConnectivityManager;
    @NonNull
    private final Dependencies mDependencies;
    private int mVpnType = VPN_TYPE_UNKNOWN;
    private int mVpnProfileType = VPN_PROFILE_TYPE_UNKNOWN;
    private int mMtu = 0;
    /**
     * A bitmask representing the set of currently allowed algorithms.
     * Each bit in this integer corresponds to an algorithm defined in {@code sAlgorithms}.
     * If a bit at a certain position (index) is set, the algorithm corresponding to that
     * index in {@code sAlgorithms} is considered allowed.
     */
    private int mAllowedAlgorithms = 0;
    /**
     * The maximum value that {@code mAllowedAlgorithms} can take.
     * It's calculated based on the number of algorithms defined in {@code sAlgorithms}.
     * Each algorithm corresponds to a bit in the bitmask, so the maximum value is
     * 2^numberOfAlgorithms - 1.
     * This value should be updated if {@code sAlgorithms} is modified.
     */
    private static final int MAX_ALLOWED_ALGORITHMS_VALUE = (1 << 11) - 1;
    /**
     * An array representing the transport types of the underlying networks for the VPN.
     * Each element in this array corresponds to a specific underlying network.
     * The value of each element is the primary transport type of the network
     * (e.g., {@link NetworkCapabilities#TRANSPORT_WIFI},
     * {@link NetworkCapabilities#TRANSPORT_CELLULAR}).
     * If the transport type of a network cannot be determined, the value will be
     * {@code UNKNOWN_UNDERLYING_NETWORK_TYPE}.
     */
    @NonNull
    private int[] mUnderlyingNetworkTypes = new int[0];
    private int mVpnNetworkIpProtocol = IP_PROTOCOL_UNKNOWN;
    private int mServerIpProtocol = IP_PROTOCOL_UNKNOWN;

    // Static initializer block to populate the sAlgorithms and sVpnProfileTypeMap mappings.
    // For sAlgorithms, it associates integer keys (which also serve as bit positions for the
    // mAllowedAlgorithms bitmask) with their respective algorithm string constants.
    // For sVpnProfileTypeMap, it maps VpnProfile types to their corresponding enum values.
    static {
        sAlgorithms.put(0, AUTH_AES_CMAC);
        sAlgorithms.put(1, AUTH_AES_XCBC);
        sAlgorithms.put(2, AUTH_CRYPT_AES_GCM);
        sAlgorithms.put(3, AUTH_CRYPT_CHACHA20_POLY1305);
        sAlgorithms.put(4, AUTH_HMAC_MD5);
        sAlgorithms.put(5, AUTH_HMAC_SHA1);
        sAlgorithms.put(6, AUTH_HMAC_SHA256);
        sAlgorithms.put(7, AUTH_HMAC_SHA384);
        sAlgorithms.put(8, AUTH_HMAC_SHA512);
        sAlgorithms.put(9, CRYPT_AES_CBC);
        sAlgorithms.put(10, CRYPT_AES_CTR);

        sVpnProfileTypeMap.put(VpnProfile.TYPE_PPTP, 1 /* VpnProfileType.TYPE_PPTP */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_L2TP_IPSEC_PSK,
                2 /* VpnProfileType.TYPE_L2TP_IPSEC_PSK */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_L2TP_IPSEC_RSA,
                3 /* VpnProfileType.TYPE_L2TP_IPSEC_RSA */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_IPSEC_XAUTH_PSK,
                4 /* VpnProfileType.TYPE_TYPE_IPSEC_XAUTH_PSK */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_IPSEC_XAUTH_RSA,
                5 /* VpnProfileType.TYPE_IPSEC_XAUTH_RSA */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_IPSEC_HYBRID_RSA,
                6 /* VpnProfileType.TYPE_IPSEC_HYBRID_RSA */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_IKEV2_IPSEC_USER_PASS,
                7 /* VpnProfileType.TYPE_IKEV2_IPSEC_USER_PASS */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_IKEV2_IPSEC_PSK,
                8 /* VpnProfileType.TYPE_IKEV2_IPSEC_PSK */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_IKEV2_IPSEC_RSA,
                9 /* VpnProfileType.TYPE_IKEV2_IPSEC_RSA */);
        sVpnProfileTypeMap.put(VpnProfile.TYPE_IKEV2_FROM_IKE_TUN_CONN_PARAMS,
                10 /* VpnProfileType.TYPE_IKEV2_FROM_IKE_TUN_CONN_PARAMS */);
    }

    /**
     * Dependencies of VpnConnectivityMetrics, for injection in tests.
     */
    public static class Dependencies {

        /**
         * @see FrameworkStatsLog
         */
        public void statsWrite(int vpnType, int vpnNetworkIpProtocol, int serverIpProtocol,
                int vpnProfileType, int allowedAlgorithms, int mtu, int[] underlyingNetworkType,
                boolean connected, int userId) {
            FrameworkStatsLog.write(FrameworkStatsLog.VPN_CONNECTION_REPORTED, vpnType,
                    vpnNetworkIpProtocol, serverIpProtocol, vpnProfileType, allowedAlgorithms, mtu,
                    underlyingNetworkType, connected, userId);
        }
    }

    public VpnConnectivityMetrics(int userId, ConnectivityManager connectivityManager) {
        this(userId, connectivityManager, new Dependencies());
    }

    @VisibleForTesting
    VpnConnectivityMetrics(int userId, ConnectivityManager connectivityManager,
            Dependencies dependencies) {
        mUserId = userId;
        mConnectivityManager = connectivityManager;
        mDependencies = dependencies;
    }

    /**
     * Sets the VPN type.
     *
     * @param type The type of the VPN, as defined in {@link VpnManager.VpnType}.
     */
    public void setVpnType(@VpnManager.VpnType int type) {
        mVpnType = type;
    }

    /**
     * Sets the MTU for the VPN connection.
     *
     * @param mtu The MTU value in bytes.
     */
    public void setMtu(int mtu) {
        mMtu = mtu;
    }

    /**
     * Sets the VPN profile type.
     *
     * @param vpnProfile The integer value representing the VPN profile.
     */
    public void setVpnProfileType(int vpnProfile) {
        mVpnProfileType = sVpnProfileTypeMap.get(vpnProfile, VPN_PROFILE_TYPE_UNKNOWN);
    }

    /**
     * Sets the allowed algorithms based on a provided list of algorithm names.
     * This method converts the list of string names into a bitmask representation
     * which is then stored in {@code mAllowedAlgorithms}.
     *
     * @param allowedAlgorithms A list of strings, where each string is the name of a algorithm to
     *                          be allowed.
     */
    public void setAllowedAlgorithms(@NonNull List<String> allowedAlgorithms) {
        mAllowedAlgorithms = buildAllowedAlgorithmsBitmask(allowedAlgorithms);
    }

    /**
     * Constructs a bitmask representing the set of allowed algorithms from a list of
     * algorithm names.
     * <p>
     * Each known algorithm name in the input list corresponds to a specific bit
     * in the returned integer. If an algorithm name from the list is found in
     * {@link #sAlgorithms}, the bit at the index of that algorithm in {@link #sAlgorithms}
     * is set in the bitmask.
     * </p>
     *
     * @param allowedAlgorithms A list of strings, where each string is the name of a algorithm.
     * @return An integer bitmask where each set bit indicates an allowed algorithm based on its
     *         index in {@link #sAlgorithms}. Returns 0 if the input list is empty, or contains only
     *         unknown algorithms.
     */
    @VisibleForTesting
    static int buildAllowedAlgorithmsBitmask(@NonNull List<String> allowedAlgorithms) {
        int bitmask = 0;
        for (String ac : allowedAlgorithms) {
            final int index = sAlgorithms.indexOfValue(ac);
            if (index < 0) {
                Log.wtf(TAG, "Unknown allowed algorithm: " + ac);
                continue;
            }
            bitmask |= 1 << index;
        }
        return bitmask;
    }

    /**
     * Sets the transport types of the underlying networks for the VPN.
     * <p>
     * This method processes an array of {@link android.net.Network} objects. For each network,
     * it attempts to retrieve its {@link android.net.NetworkCapabilities} and extracts the
     * primary transport type (e.g., Wi-Fi, cellular). If capabilities cannot be retrieved
     * for a specific network, a predefined {@code UNKNOWN_UNDERLYING_NETWORK_TYPE} is
     * used for that entry.
     *
     * <p>
     * Note: This method contains a synchronized call to ConnectivityManager to query the network
     * capability, so this method should not be called from the Network Callback.
     *
     * <p>
     * If the underlying network types have changed are different from current, a sequence of
     * notifications is triggered: first, a disconnection notification is sent with the old value,
     * then {@code mUnderlyingNetworkTypes} is updated with the new value, and finally, a connection
     * notification is issued with the new value.
     *
     * @param networks An array of {@link android.net.Network} objects representing the underlying
     *                 networks currently in use.
     */
    public void updateUnderlyingNetworkTypes(@NonNull Network[] networks) {
        // Note: If the underlying network is lost, an empty underlying network won't be set.
        // Instead, only a countdown timer will be activated. After a timeout, the NetworkAgent
        // disconnects, and the disconnection is then notified from there. Therefore, the recorded
        // time may not be accurate because there may be a gap between the NetworkAgent disconnect
        // and the loss of the underlying network.
        if (networks.length == 0) {
            return; // Return if no networks.
        }

        int[] newTypes = new int[networks.length];
        for (int i = 0; i < networks.length; i++) {
            final NetworkCapabilities capabilities =
                    mConnectivityManager.getNetworkCapabilities(networks[i]);
            if (capabilities != null) {
                // Get the primary transport type of the network.
                newTypes[i] = capabilities.getTransportTypes()[0];
            } else {
                newTypes[i] = UNKNOWN_UNDERLYING_NETWORK_TYPE;
            }
        }
        // Set the underlying network types directly if it's the default value, skipping the
        // connection status notification. Those notifications will be sent only when the VPN
        // connection is established and the underlying network types change.
        if (mUnderlyingNetworkTypes.length == 0) {
            mUnderlyingNetworkTypes = newTypes;
            return;
        }
        // Return if no type change.
        if (Arrays.equals(mUnderlyingNetworkTypes, newTypes)) {
            return;
        }
        // Notify the ip protocol change and set the new ip protocol.
        notifyVpnDisconnected();
        mUnderlyingNetworkTypes = newTypes;
        notifyVpnConnected();
    }

    /**
     * Sets the IP protocol for the vpn network based on a list of {@link LinkAddress} objects.
     *
     * <p>
     * If the vpn network ip protocol has changed is different from current, a sequence of
     * notifications is triggered: first, a disconnection notification is sent with the old value,
     * then {@code mUnderlyingNetworkTypes} is updated with the new value, and finally, a connection
     * notification is issued with the new value.
     *
     * @param addresses A list of {@link LinkAddress} objects representing the IP addresses
     *                  configured on the VPN network.
     */
    public void updateVpnNetworkIpProtocol(@NonNull List<LinkAddress> addresses) {
        final int newVpnNetworkIpProtocol = checkIpProtocol(addresses);
        // Set the vpn network ip protocol directly if it's the default value, skipping the
        // connection status notification. Those notifications will be sent only when the VPN
        // connection is established and the vpn network ip protocol changes.
        if (mVpnNetworkIpProtocol == IP_PROTOCOL_UNKNOWN) {
            mVpnNetworkIpProtocol = newVpnNetworkIpProtocol;
            return;
        }
        // Return if no ip protocol change.
        if (mVpnNetworkIpProtocol == newVpnNetworkIpProtocol) {
            return;
        }
        // Notify the ip protocol change and set the new ip protocol.
        notifyVpnDisconnected();
        mVpnNetworkIpProtocol = newVpnNetworkIpProtocol;
        notifyVpnConnected();
    }

    /**
     * Sets the IP protocol for the server based on its {@link InetAddress}.
     *
     * <p>
     * If the server ip protocol has changed is different from current, a sequence of notifications
     * is triggered: first, a disconnection notification is sent with the old value, then
     * {@code mUnderlyingNetworkTypes} is updated with the new value, and finally, a connection
     * notification is issued with the new value.
     *
     * @param address The {@link InetAddress} of the server.
     */
    public void updateServerIpProtocol(@NonNull InetAddress address) {
        final int newServerIpProtocol = getIpProtocolVersion(address);
        // Set the server ip protocol directly if it's the default value, skipping the connection
        // status notification. Those notifications will be sent only when the VPN connection is
        // established and the server ip protocol changes.
        if (mServerIpProtocol == IP_PROTOCOL_UNKNOWN) {
            mServerIpProtocol = newServerIpProtocol;
            return;
        }
        // Return if no ip protocol change.
        if (mServerIpProtocol == newServerIpProtocol) {
            return;
        }
        // Notify the ip protocol change and set the new ip protocol.
        notifyVpnDisconnected();
        mServerIpProtocol = newServerIpProtocol;
        notifyVpnConnected();
    }

    /**
     * Determines the IP protocol version of a given {@link InetAddress}.
     *
     * @param address The {@link InetAddress} for which to determine the IP protocol version.
     * @return An integer representing the IP protocol version:
     * {@link #IP_PROTOCOL_IPv4} for IPv4 addresses,
     * {@link #IP_PROTOCOL_IPv6} for IPv6 addresses,
     * or {@link #IP_PROTOCOL_UNKNOWN} for any other address types.
     */
    private static int getIpProtocolVersion(@NonNull InetAddress address) {
        // Assume that if the address is not IPv4, it is IPv6. It does not consider other cases like
        // IPv4-mapped IPv6 addresses.
        return (address instanceof Inet4Address) ? IP_PROTOCOL_IPv4 :
                (address instanceof Inet6Address) ? IP_PROTOCOL_IPv6 : IP_PROTOCOL_UNKNOWN;
    }

    /**
     * Analyzes a list of {@link LinkAddress} objects to determine the overall IP protocol(s) in
     * use.
     *
     * @param addresses A list of {@link LinkAddress} objects to be checked.
     * @return An integer representing the detected IP protocol.
     */
    @VisibleForTesting
    static int checkIpProtocol(@NonNull List<LinkAddress> addresses) {
        boolean hasIpv4 = false;
        boolean hasIpv6 = false;
        int ipProtocol = IP_PROTOCOL_UNKNOWN;
        for (LinkAddress address : addresses) {
            if (address == null) continue;
            if (address.isIpv4()) {
                hasIpv4 = true;
            } else if (address.isIpv6()) {
                hasIpv6 = true;
            }
        }
        if (hasIpv4 && hasIpv6) {
            ipProtocol = IP_PROTOCOL_IPv4v6;
        } else if (hasIpv4) {
            ipProtocol = IP_PROTOCOL_IPv4;
        } else if (hasIpv6) {
            ipProtocol = IP_PROTOCOL_IPv6;
        }
        return ipProtocol;
    }

    /**
     * Validates and corrects the internal VPN metrics to ensure the collected data fall within
     * acceptable ranges.
     * <p>
     * This method checks the values of {@code mVpnType}, {@code mVpnNetworkIpProtocol},
     * {@code mServerIpProtocol}, {@code mVpnProfileType}, and {@code mAllowedAlgorithms}.
     * If any value is found to be outside its expected bounds, an error is logged, and the metric
     * is reset to default state.
     * </p>
     */
    private void validateAndCorrectMetrics() {
        if (mVpnType < VPN_TYPE_UNKNOWN || mVpnType > TYPE_VPN_OEM) {
            Log.e(TAG, "Invalid vpnType: " + mVpnType);
            mVpnType = VPN_TYPE_UNKNOWN;
        }
        if (mVpnNetworkIpProtocol < IP_PROTOCOL_UNKNOWN
                || mVpnNetworkIpProtocol > IP_PROTOCOL_IPv4v6) {
            Log.e(TAG, "Invalid vpnNetworkIpProtocol: " + mVpnNetworkIpProtocol);
            mVpnNetworkIpProtocol = IP_PROTOCOL_UNKNOWN;
        }
        if (mServerIpProtocol < IP_PROTOCOL_UNKNOWN || mServerIpProtocol > IP_PROTOCOL_IPv6) {
            Log.e(TAG, "Invalid serverIpProtocol: " + mServerIpProtocol);
            mServerIpProtocol = IP_PROTOCOL_UNKNOWN;
        }
        if (mVpnProfileType < VPN_PROFILE_TYPE_UNKNOWN
                || mVpnProfileType > VPN_PROFILE_TYPE_IKEV2_FROM_IKE_TUN_CONN_PARAMS) {
            Log.e(TAG, "Invalid vpnProfileType: " + mVpnProfileType);
            mVpnProfileType = VPN_PROFILE_TYPE_UNKNOWN;
        }
        if (mAllowedAlgorithms < 0 || mAllowedAlgorithms > MAX_ALLOWED_ALGORITHMS_VALUE) {
            Log.e(TAG, "Invalid allowedAlgorithms: " + mAllowedAlgorithms);
            mAllowedAlgorithms = 0;
        }
    }

    private void validateAndReportVpnConnectionEvent(boolean connected) {
        validateAndCorrectMetrics();
        mMetricLogs.log("Report VPN connection event: " + (connected ? "CONNECTED" : "DISCONNECTED")
                + ", vpnType=" + mVpnType
                + ", vpnProfileType=" + mVpnProfileType
                + ", underlyingNetworkTypes=" + Arrays.toString(mUnderlyingNetworkTypes)
                + ", vpnNetworkIpProtocol=" + mVpnNetworkIpProtocol
                + ", serverIpProtocol=" + mServerIpProtocol
                + ", allowedAlgorithms=" + mAllowedAlgorithms
                + ", mtu=" + mMtu);
        mDependencies.statsWrite(
                mVpnType,
                mVpnNetworkIpProtocol,
                mServerIpProtocol,
                mVpnProfileType,
                mAllowedAlgorithms,
                mMtu,
                mUnderlyingNetworkTypes,
                connected,
                mUserId);
    }

    /**
     * Notifies that a VPN connected event has occurred.
     *
     * This method gathers the current VPN state information from internal fields and reports it to
     * the system's statistics logging service.
     */
    public void notifyVpnConnected() {
        validateAndReportVpnConnectionEvent(true /* connected */);
    }

    /**
     * Notifies that a VPN disconnected event has occurred.
     *
     * This method gathers the current VPN state information from internal fields and reports it to
     * the system's statistics logging service.
     */
    public void notifyVpnDisconnected() {
        validateAndReportVpnConnectionEvent(false /* connected */);
    }

    /**
     * Resets all internal VPN metrics to their default states.
     * <p>
     * This method should be called to ensure a clean state.
     * </p>
     */
    public void resetMetrics() {
        mVpnType = VPN_TYPE_UNKNOWN;
        mVpnNetworkIpProtocol = IP_PROTOCOL_UNKNOWN;
        mServerIpProtocol = IP_PROTOCOL_UNKNOWN;
        mVpnProfileType = VPN_PROFILE_TYPE_UNKNOWN;
        mAllowedAlgorithms = 0;
        mMtu = 0;
        mUnderlyingNetworkTypes = new int[0];
    }

    /**
     * Dumps the local log buffer.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.println("VpnConnectivityMetrics logs (most recent first):");
        pw.increaseIndent();
        mMetricLogs.reverseDump(pw);
        pw.decreaseIndent();
    }
}
