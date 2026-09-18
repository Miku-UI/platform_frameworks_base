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

public interface AdbdIServicesManager {

    /** Register a service with the framework. */
    void registerService(String instanceName, String serviceType, int port);

    /** Register a service with the framework. Request a callback to monitor service lifecycle. */
    void registerService(
            String instanceName,
            String serviceType,
            int port,
            NsdManager.RegistrationListener registrationCallback);

    /** Unregister a service with the framework. */
    void unregisterService(String instanceName, String serviceType);

    /** Unregister all services with the framework. */
    void unregisterAll();

    /**
     * When an attribute has changed, we cannot just update the TXT since NsdManager does not
     * supports it. Instead, we republish all services (see b/445548047).
     */
    void onAttributeChanged();
}
