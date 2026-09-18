/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.net.nsd.NsdManager;
import android.util.Slog;

public class AdbdServicesManagerNoop implements AdbdIServicesManager {
    private static final String TAG = AdbdServicesManagerNoop.class.getSimpleName();

    AdbdServicesManagerNoop() {}

    @Override
    public void registerService(String instanceName, String serviceType, int port) {
        Slog.i(TAG, "Registering service noop");
    }

    @Override
    public void registerService(
            String instanceName,
            String serviceType,
            int port,
            NsdManager.RegistrationListener registrationCallback) {
        Slog.i(TAG, "Registering service noop");
    }

    @Override
    public void unregisterService(String instanceName, String serviceType) {
        Slog.i(TAG, "Unregister service noop");
    }

    @Override
    public void unregisterAll() {
        Slog.i(TAG, "Unregister all noop");
    }

    @Override
    public void onAttributeChanged() {
        Slog.i(TAG, "Attribute changed noop");
    }
}
