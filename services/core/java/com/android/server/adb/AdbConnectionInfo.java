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

/** The AdbConnectionInfo class stores the last known ADBWifi configuration. */
public class AdbConnectionInfo {

    private String mBssid;
    private String mSsid;
    private int mPort;

    AdbConnectionInfo() {
        mBssid = "";
        mSsid = "";
        mPort = 0;
    }

    AdbConnectionInfo(String bssid, String ssid) {
        mBssid = bssid;
        mSsid = ssid;
        mPort = 0;
    }

    synchronized void copy(AdbConnectionInfo other) {
        mBssid = other.mBssid;
        mSsid = other.mSsid;
        mPort = other.mPort;
    }

    public synchronized String getBSSID() {
        return mBssid;
    }

    public synchronized String getSSID() {
        return mSsid;
    }

    public synchronized int getPort() {
        return mPort;
    }

    public synchronized void setPort(int port) {
        mPort = port;
    }

    /** */
    public synchronized void clear() {
        mBssid = "";
        mSsid = "";
        mPort = 0;
    }
}
