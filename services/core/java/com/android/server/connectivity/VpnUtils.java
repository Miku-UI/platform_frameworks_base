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

import android.annotation.NonNull;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.util.Log;

import java.util.concurrent.Executor;

/**
 * Utility class used for VPN.
 *
 * @hide
 */
public class VpnUtils {

    /**
     * A NetworkCallback to listen for changes on the VPN network. This callback is used to
     * propagate LinkProperties changes of the VPN network to the registered {@link
     * Vpn.VpnNetworkCallback}.
     */
    static class VpnNetworkCallback extends ConnectivityManager.NetworkCallback {
        private final String mTag;
        private final Vpn.VpnNetworkCallback mCallback;
        private final Executor mExecutor;

        VpnNetworkCallback(String tag, Vpn.VpnNetworkCallback callback, Executor executor) {
            mTag = tag;
            mCallback = callback;
            mExecutor = executor;
        }

        @Override
        public void onLinkPropertiesChanged(
                @NonNull Network network, @NonNull LinkProperties linkProperties) {
            Log.d(mTag, "Vpn LP changed for net " + network + " LinkProperties: " + linkProperties);
            mExecutor.execute(() -> mCallback.onVpnNetworkLinkPropertiesChanged(linkProperties));
        }
    }
}
