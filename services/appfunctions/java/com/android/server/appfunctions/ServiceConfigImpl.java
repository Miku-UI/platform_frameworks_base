/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.appfunctions;

import android.provider.DeviceConfig;

import java.time.Duration;

/** Implementation of {@link ServiceConfig} */
public class ServiceConfigImpl implements ServiceConfig {
    static final String DEVICE_CONFIG_PROPERTY_EXECUTION_CANCELLATION_TIMEOUT =
            "execute_app_function_cancellation_timeout_millis";
    static final long DEFAULT_EXECUTE_APP_FUNCTION_CANCELLATION_TIMEOUT_MS = 5000L;
    static final String DEVICE_CONFIG_PROPERTY_RETENTION_MILLIS =
            "app_function_access_history_retention_millis";
    static final long DEFAULT_APP_FUNCTION_ACCESS_HISTORY_RETENTION_MILLIS =
            Duration.ofDays(7).toMillis();
    static final String DEVICE_CONFIG_PROPERTY_EXPIRED_DELETION_INTERVAL_MILLIS =
            "app_function_expired_access_history_deletion_interval_millis";
    static final long DEFAULT_EXPIRED_APP_FUNCTION_HISTORIES_DELETION_INTERVAL_MILLIS =
            Duration.ofHours(24).toMillis();

    @Override
    public long getExecuteAppFunctionCancellationTimeoutMillis() {
        return DeviceConfig.getLong(
                NAMESPACE_APP_FUNCTIONS,
                DEVICE_CONFIG_PROPERTY_EXECUTION_CANCELLATION_TIMEOUT,
                DEFAULT_EXECUTE_APP_FUNCTION_CANCELLATION_TIMEOUT_MS);
    }

    @Override
    public long getAppFunctionAccessHistoryRetentionMillis() {
        return DeviceConfig.getLong(
                NAMESPACE_APP_FUNCTIONS,
                DEVICE_CONFIG_PROPERTY_RETENTION_MILLIS,
                DEFAULT_APP_FUNCTION_ACCESS_HISTORY_RETENTION_MILLIS);
    }

    @Override
    public long getAppFunctionExpiredAccessHistoryDeletionIntervalMillis() {
        return DeviceConfig.getLong(
                NAMESPACE_APP_FUNCTIONS,
                DEVICE_CONFIG_PROPERTY_EXPIRED_DELETION_INTERVAL_MILLIS,
                DEFAULT_EXPIRED_APP_FUNCTION_HISTORIES_DELETION_INTERVAL_MILLIS);
    }
}
