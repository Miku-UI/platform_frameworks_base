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

package com.android.wm.shell.desktopai.api

/**
 * Defines the contract for a service capable of gathering user context information.
 *
 * Implementations of this service orchestrate the retrieval of various data points (such as recent
 * apps, device state, or location) and package them into a unified response based on the caller's
 * requirements.
 */
interface IUserContextService {

    /**
     * Collects and aggregates context data according to the specifications in the provided query.
     *
     * This method filters the data retrieval process to ensure only the requested information
     * defined in the [query] is fetched, optimizing system resources.
     *
     * @param query The [ContextQuery] defining the specific data types (by ID) to be collected.
     * @return An [AggregatedContext] containing the gathered data mapped by their type IDs.
     */
    fun getContext(query: ContextQuery): AggregatedContext
}
