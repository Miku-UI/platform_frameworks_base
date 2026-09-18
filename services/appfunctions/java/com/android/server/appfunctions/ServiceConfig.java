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

import android.annotation.IntRange;

import java.util.concurrent.atomic.AtomicInteger;

/** This interface is used to expose configs to the AppFunctionManagerService. */
public interface ServiceConfig {
    // TODO(b/357551503): Obtain namespace from DeviceConfig.
    String NAMESPACE_APP_FUNCTIONS = "appfunctions";

    /**
     * The test page size that would be used by {@link
     * ServiceConfig#getSearchAppFunctionInternalPageSize} when provided.
     */
    AtomicInteger sTestPageSize = new AtomicInteger(0);

    /**
     * The minimum page size that would be returned by {@link
     * ServiceConfig#getSearchAppFunctionInternalPageSize}.
     */
    int MIN_PAGE_SIZE = 1;

    /**
     * The maximum page size that would be returned by {@link
     * ServiceConfig#getSearchAppFunctionInternalPageSize}.
     */
    int MAX_PAGE_SIZE = 10_000;

    /**
     * Returns the timeout for which the system server waits for the app function service to
     * successfully cancel the execution of an app function before forcefully unbinding the service.
     */
    long getExecuteAppFunctionCancellationTimeoutMillis();

    /**
     * Returns the internal page size used when fetching a page from the system server via {@link
     * android.app.appfunctions.IAppFunctionSearchResults#getNextPage}.
     */
    @IntRange(from = MIN_PAGE_SIZE, to = MAX_PAGE_SIZE)
    int getSearchAppFunctionInternalPageSize();

    /**
     * Returns the debounce time in milliseconds for app function metadata changes.
     *
     * <p>This is the maximum time the system server will wait before notifying the observers of app
     * function metadata changes.
     */
    int getAppFunctionMetadataChangeDebounceMilliseconds();

    /** Returns the maximum number of agent packages whose allowlists can be cached. */
    int getAppFunctionAllowlistCacheSize();

    /** Returns the debounce time in milliseconds for app function enabled state changes. */
    long getAppFunctionEnabledStateChangeDebounceMilliseconds();

    /** Returns the maximum debounce time in milliseconds for app function enabled state changes. */
    long getAppFunctionEnabledStateChangeMaxDebounceMilliseconds();
}
