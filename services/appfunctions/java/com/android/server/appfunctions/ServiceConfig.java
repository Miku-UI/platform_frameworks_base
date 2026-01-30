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

/** This interface is used to expose configs to the AppFunctionManagerService. */
public interface ServiceConfig {
    // TODO(b/357551503): Obtain namespace from DeviceConfig.
    String NAMESPACE_APP_FUNCTIONS = "appfunctions";

    /**
     * Returns the timeout for which the system server waits for the app function service to
     * successfully cancel the execution of an app function before forcefully unbinding the service.
     */
    long getExecuteAppFunctionCancellationTimeoutMillis();

    /**
     * Returns the maximum age, in milliseconds, for an AppFunction access history record.
     *
     * <p>Access history records older than this retention period will be removed during the next
     * maintenance cleanup.
     */
    long getAppFunctionAccessHistoryRetentionMillis();

    /**
     * Returns the interval, in milliseconds, at which the maintenance job runs to delete expired
     * AppFunction access histories.
     */
    long getAppFunctionExpiredAccessHistoryDeletionIntervalMillis();
}
