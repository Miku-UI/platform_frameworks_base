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

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

/**
 * Monitors Wi-Fi state changes to automatically disable ADB over Wi-Fi when the device disconnects
 * from Wi-Fi.
 */
public class AdbBroadcastReceiver extends BroadcastReceiver implements AdbNetworkMonitor {

    private static final String TAG = AdbBroadcastReceiver.class.getSimpleName();

    private final ContentResolver mContentResolver;
    private final Context mContext;
    private final AdbConnectionInfo mAdbConnectionInfo;
    private boolean mStarted = false;

    AdbBroadcastReceiver(@NonNull Context context, @NonNull AdbConnectionInfo info) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mAdbConnectionInfo = info;
    }

    @Override
    public void register() {
        if (mStarted) {
            return;
        }
        IntentFilter intentFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mContext.registerReceiver(this, intentFilter);
        mStarted = true;
    }

    @Override
    public void unregister() {
        if (!mStarted) {
            return;
        }
        mContext.unregisterReceiver(this);
        mStarted = false;
    }

    private void disableWifi(String reason) {
        Slog.i(TAG, reason);
        Settings.Global.putInt(mContentResolver, Settings.Global.ADB_WIFI_ENABLED, 0);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        // We only care about when wifi is disabled, and when there is a wifi network
        // change.
        if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
            int state =
                    intent.getIntExtra(
                            WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);

            if (state == WifiManager.WIFI_STATE_DISABLED) {
                disableWifi("Wifi disabled. Disabling adbwifi.");
            }
        } else if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
            // We only care about wifi type connections
            NetworkInfo networkInfo =
                    (NetworkInfo)
                            intent.getParcelableExtra(
                                    WifiManager.EXTRA_NETWORK_INFO, android.net.NetworkInfo.class);

            // We only care about Wifi network
            if (networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
                return;
            }

            // Check for network disconnect
            if (!networkInfo.isConnected()) {
                disableWifi("Network disconnected. Disabling adbwifi.");
                return;
            }

            WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null || wifiInfo.getNetworkId() == -1) {
                disableWifi("Not connected to any wireless network. Not enabling adbwifi.");
                return;
            }

            // Check for network change
            final String bssid = wifiInfo.getBSSID();
            if (TextUtils.isEmpty(bssid)) {
                disableWifi("Unable to get the wifi ap's BSSID. Disabling adbwifi.");
                return;
            }

            if (!TextUtils.equals(bssid, mAdbConnectionInfo.getBSSID())) {
                disableWifi("Detected wifi network change. Disabling adbwifi.");
                return;
            }
        }
    }
}
