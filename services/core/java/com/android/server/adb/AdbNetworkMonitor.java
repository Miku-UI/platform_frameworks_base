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

/**
 * Interface for monitoring network connectivity to enable/disable ADB over Wi-Fi.
 *
 * <p>Implementations of this interface are responsible for registering and unregistering network
 * monitoring processes.
 */
public interface AdbNetworkMonitor {

    /**
     * Registers the network monitoring process.
     *
     * <p>It is safe to call this method multiple times; implementations should handle this without
     * side effects.
     */
    void register();

    /**
     * Unregisters the network monitoring process.
     *
     * <p>It is safe to call this method multiple times, even if the monitor was not previously
     * registered.
     */
    void unregister();
}
