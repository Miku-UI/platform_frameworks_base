/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Slog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdbdServicesManager implements AdbdIServicesManager {

    private static final String TAG = AdbDebuggingManager.class.getSimpleName();

    private final Handler mHandler;

    // The manager we use to publish/unpublish services
    private final NsdManager mNsdManager;

    // We keep track of all services we register in this map. Keys are generated via the
    // keyForService method.
    private final Map<String, AdbdRegistrationListener> mRegisteredServices = new HashMap<>();

    private final ContentResolver mContentResolver;

    // To make sure the device keep on responding to mDNS probes even if the screen is off.
    private final WifiManager.MulticastLock mAdbMulticastLock;

    /** A RegistrationCallback that does nothing. */
    private static final NsdManager.RegistrationListener NO_OP =
            new NsdManager.RegistrationListener() {
                @Override
                public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {}

                @Override
                public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {}

                @Override
                public void onServiceRegistered(NsdServiceInfo serviceInfo) {}

                @Override
                public void onServiceUnregistered(NsdServiceInfo serviceInfo) {}
            };

    AdbdServicesManager(Context context, String purpose, Handler handler) {
        mHandler = handler;
        mNsdManager = context.getSystemService(NsdManager.class);
        mContentResolver = context.getContentResolver();
        WifiManager wifiManager =
                context.getApplicationContext().getSystemService(WifiManager.class);
        mAdbMulticastLock = wifiManager.createMulticastLock("AdbMulticastLock-" + purpose);
        mAdbMulticastLock.setReferenceCounted(false);
    }

    private String keyForService(String instanceName, String serviceType) {
        return instanceName + "." + serviceType;
    }

    @Override
    public void registerService(String instanceName, String serviceType, int port) {
        registerService(instanceName, serviceType, port, NO_OP);
    }

    @Override
    public void registerService(
            String instanceName,
            String serviceType,
            int port,
            NsdManager.RegistrationListener registrationCallback) {
        String key = keyForService(instanceName, serviceType);
        if (mRegisteredServices.containsKey(key)) {
            AdbdRegistrationListener listener = mRegisteredServices.get(key);
            unregisterService(listener.mInstanceName, listener.mServiceType);
        }

        Slog.i(TAG, "Registering service '" + key + ":" + port + "' with Framework");

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(instanceName);
        serviceInfo.setServiceType(serviceType);
        serviceInfo.setPort(port);
        if (AdbDebuggingManager.wifiLifeCycleOverAdbdauthSupported()) {
            serviceInfo.setAttribute("v", "2.0");
            serviceInfo.setAttribute("name", SystemProperties.get("ro.product.model", ""));
            serviceInfo.setAttribute("api", SystemProperties.get("ro.build.version.sdk_full", ""));
            serviceInfo.setAttribute(
                    "given_name",
                    Settings.Global.getString(mContentResolver, Settings.Global.DEVICE_NAME));
            serviceInfo.setAttribute("serial", SystemProperties.get("ro.serialno", ""));
        } else {
            serviceInfo.setAttribute("v", "1");
        }

        AdbdRegistrationListener listener =
                new AdbdRegistrationListener(instanceName, serviceType, port, registrationCallback);

        try {
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            Slog.e(TAG, "Unable to register " + key, e);
        }

        mRegisteredServices.put(key, listener);
        checkMulticastLock();
    }

    @Override
    public void unregisterService(String instanceName, String serviceType) {
        String key = keyForService(instanceName, serviceType);
        if (!mRegisteredServices.containsKey(key)) {
            Slog.i(TAG, "unregister service noop for" + key);
            return;
        }

        Slog.i(TAG, "Unregister service '" + key + "' with Framework");
        try {
            mNsdManager.unregisterService(mRegisteredServices.get(key));
        } catch (Exception e) {
            Slog.e(TAG, "Unable to unregister " + key, e);
        }
        mRegisteredServices.remove(key);
        checkMulticastLock();
    }

    @Override
    public void unregisterAll() {
        for (AdbdRegistrationListener service : new ArrayList<>(mRegisteredServices.values())) {
            unregisterService(service.mInstanceName, service.mServiceType);
        }
    }

    // When an attribute has changed, we cannot just update the TXT since NsdManager does not
    // supports it. Instead, we republish all services (see b/445548047).
    @Override
    public void onAttributeChanged() {
        List<AdbdRegistrationListener> services = new ArrayList<>(mRegisteredServices.values());
        for (AdbdRegistrationListener service : services) {
            unregisterService(service.mInstanceName, service.mServiceType);
        }
        for (AdbdRegistrationListener service : services) {
            registerService(
                    service.mInstanceName,
                    service.mServiceType,
                    service.mPort,
                    service.mRegistrationCallback);
        }
    }

    private void checkMulticastLock() {
        if (mRegisteredServices.isEmpty()) {
            Slog.d(TAG, "Released multicast lock");
            mAdbMulticastLock.release();
        } else {
            Slog.d(TAG, "Acquired multicast lock");
            mAdbMulticastLock.acquire();
        }
    }

    private class AdbdRegistrationListener implements NsdManager.RegistrationListener {
        final String mInstanceName;
        final String mServiceType;
        final int mPort;
        final NsdManager.RegistrationListener mRegistrationCallback;

        private AdbdRegistrationListener(
                String instanceName,
                String serviceType,
                int port,
                NsdManager.RegistrationListener registrationCallback) {
            mInstanceName = instanceName;
            mServiceType = serviceType;
            mPort = port;
            mRegistrationCallback = registrationCallback;
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Slog.e(TAG, "Failed to register service (err=" + errorCode + "): " + serviceInfo);
            mRegistrationCallback.onRegistrationFailed(serviceInfo, errorCode);
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Slog.e(TAG, "Failed to unregister service (err=" + errorCode + "): " + serviceInfo);
            mRegistrationCallback.onRegistrationFailed(serviceInfo, errorCode);
        }

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Slog.i(TAG, "Registered service '" + serviceInfo + "'");
            String hostname = serviceInfo.getHostname();
            if (hostname != null) {
                mHandler.sendMessage(
                        mHandler.obtainMessage(
                                AdbDebuggingManager.AdbDebuggingHandler.MSG_ON_LOCALHOSTNAME_KNOWN,
                                hostname + ".local"));
            }
            mRegistrationCallback.onServiceRegistered(serviceInfo);
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Slog.i(TAG, "Unregistered service '" + serviceInfo + "'");
            mRegistrationCallback.onServiceUnregistered(serviceInfo);
        }
    }
}
