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


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.adb.AdbDebuggingManager.AdbDebuggingHandler;

/**
 * Monitors Wi-Fi state changes to automatically enable ADB over Wi-Fi when the device connects to a
 * trusted network and disable it otherwise.
 */
public class AdbWifiNetworkMonitor extends ConnectivityManager.NetworkCallback
        implements AdbNetworkMonitor {

    private static final String TAG = AdbWifiNetworkMonitor.class.getSimpleName();

    private final Context mContext;
    private final Handler mAdbDebuggingHandler;

    /**
     * Stores the SSID of the most recently processed network.
     *
     * <p>This is used to deduplicate callbacks, which may fire multiple times for the same network
     * change event. By comparing against the last known SSID, we can ensure we only react to a
     * given network update once.
     */
    @Nullable private String mLastSSID;

    private boolean mStarted = false;

    AdbWifiNetworkMonitor(
            @NonNull Context context,
            @NonNull Handler adbDebuggingHandler) {
        // Flag is required to receive BSSID and SSID info in the callback.
        super(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO);

        mContext = context;
        mAdbDebuggingHandler = adbDebuggingHandler;
        register();
    }

    @Override
    public final void register() {
        if (mStarted) {
            return;
        }
        ConnectivityManager connectivityManager =
                mContext.getSystemService(ConnectivityManager.class);
        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        // Filter out callbacks from hotspot networks.
                        // Currently hotspot NetworkCallbacks aren't received, but that'll change
                        // in the future. See b/201616245 for more information.
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
        connectivityManager.registerNetworkCallback(request, this);
        mStarted = true;
    }

    @Override
    public final void unregister() {
        if (!mStarted) {
            return;
        }
        ConnectivityManager connectivityManager =
                mContext.getSystemService(ConnectivityManager.class);
        connectivityManager.unregisterNetworkCallback(this);
        mStarted = false;
    }

    @Override
    public final void onCapabilitiesChanged(
            @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
        if (networkCapabilities.getTransportInfo() instanceof WifiInfo wifiInfo) {
            Slog.i(TAG, "Wi-Fi network available");
            processWifiConnection(wifiInfo);
        } else {
            mAdbDebuggingHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_STOP_TLS_SERVICE);
            Slog.i(TAG, "Wi-Fi network not available. Stopping adb over Wi-Fi.");
        }
    }

    @Override
    public final void onLost(@NonNull Network network) {
        mLastSSID = null;
        mAdbDebuggingHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_STOP_TLS_SERVICE);
        Slog.i(TAG, "Wi-Fi network lost. Stopping adb over Wi-Fi.");
    }

    private void processWifiConnection(WifiInfo wifiInfo) {
        if (wifiInfo.getNetworkId() == -1
                || TextUtils.isEmpty(wifiInfo.getBSSID())
                || TextUtils.isEmpty(wifiInfo.getSSID())
                || TextUtils.equals(wifiInfo.getSSID(), WifiManager.UNKNOWN_SSID)) {
            mAdbDebuggingHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_STOP_TLS_SERVICE);
            Slog.i(
                    TAG,
                    TextUtils.formatSimple(
                            "Wi-Fi connection info is invalid {networkId=%d, bssid=%s, ssid=%s}."
                                    + " Stopping adb over Wi-Fi.",
                            wifiInfo.getNetworkId(), wifiInfo.getBSSID(), wifiInfo.getSSID()));
            return;
        }

        if (TextUtils.equals(wifiInfo.getSSID(), mLastSSID)) {
            Slog.i(TAG, "Received the same Wi-Fi SSID. Ignoring.");
            return;
        }
        mLastSSID = wifiInfo.getSSID();

        mAdbDebuggingHandler.sendEmptyMessage(AdbDebuggingHandler.MSG_START_TLS_SERVICE);
        Slog.i(
                TAG,
                TextUtils.formatSimple(
                        "Connected to a Wi-Fi network {bssid=%s, ssid=%s}. Attempting to start adb"
                                + " over Wi-Fi.",
                        wifiInfo.getBSSID(), wifiInfo.getSSID()));
    }
}
