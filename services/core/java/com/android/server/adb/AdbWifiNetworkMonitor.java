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

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Monitors Wi-Fi state changes to automatically enable ADB over Wi-Fi when the device connects to a
 * trusted network and disable it otherwise.
 */
public class AdbWifiNetworkMonitor extends ConnectivityManager.NetworkCallback
        implements AdbNetworkMonitor {

    private static final String TAG = AdbWifiNetworkMonitor.class.getSimpleName();

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private final IsTrustedNetworkChecker mIsTrustedNetworkChecker;

    /**
     * Stores the BSSID of the most recently processed network.
     *
     * <p>This is used to deduplicate callbacks, which may fire multiple times for the same network
     * change event. By comparing against the last known BSSID, we can ensure we only react to a
     * given network update once.
     */
    @Nullable private String mLastBSSID;

    private boolean mStarted = false;

    @VisibleForTesting
    interface IsTrustedNetworkChecker {
        boolean isTrusted(String bssid);
    }

    AdbWifiNetworkMonitor(
            @NonNull Context context, @NonNull IsTrustedNetworkChecker isTrustedNetworkChecker) {
        // Flag is required to receive BSSID info in the callback.
        super(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO);

        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mIsTrustedNetworkChecker = isTrustedNetworkChecker;
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
        // This NetworkMonitor must remain registered even when ADB over Wi-Fi is disabled.
        // This allows it to automatically re-enable ADB over Wi-Fi when the device connects
        // to a trusted network.
    }

    @Override
    public final void onCapabilitiesChanged(
            @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
        if (networkCapabilities.getTransportInfo() instanceof WifiInfo wifiInfo) {
            Slog.i(TAG, "Wi-Fi network available");
            processWifiConnection(wifiInfo);
        } else {
            setAdbWifiState(false, "Wi-Fi network not available. Disabling adb over Wi-Fi.");
        }
    }

    @Override
    public final void onLost(@NonNull Network network) {
        mLastBSSID = null;
        setAdbWifiState(false, "Wi-Fi network lost. Disabling adb over Wi-Fi.");
    }

    private void processWifiConnection(WifiInfo wifiInfo) {
        if (wifiInfo == null
                || wifiInfo.getNetworkId() == -1
                || TextUtils.isEmpty(wifiInfo.getBSSID())) {
            setAdbWifiState(false, "Wi-Fi connection info is invalid. Disabling adb over Wi-Fi.");
            return;
        }

        if (TextUtils.equals(wifiInfo.getBSSID(), mLastBSSID)) {
            Slog.i(TAG, "Received the same Wi-Fi BSSID. Ignoring.");
            return;
        }
        mLastBSSID = wifiInfo.getBSSID();

        boolean isTrusted = mIsTrustedNetworkChecker.isTrusted(wifiInfo.getBSSID());
        if (isTrusted) {
            setAdbWifiState(true, "Connected to a trusted Wi-Fi network. Enabling adb over Wi-Fi.");
        } else {
            setAdbWifiState(
                    false, "Connected to a non-trusted Wi-Fi network. Disabling adb over Wi-Fi.");
        }
    }

    @VisibleForTesting
    protected void setAdbWifiState(boolean enabled, String reason) {
        Slog.i(TAG, reason);
        Settings.Global.putInt(mContentResolver, Settings.Global.ADB_WIFI_ENABLED, enabled ? 1 : 0);
    }
}
