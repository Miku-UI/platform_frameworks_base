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

package com.android.wm.shell.desktopai.core

import com.android.wm.shell.desktopai.api.AggregatedContext
import com.android.wm.shell.desktopai.api.ContextQuery
import com.android.wm.shell.desktopai.api.IUserContextService

/**
 * A mock implementation of [IUserContextService] for testing and development.
 *
 * This class simulates the retrieval of context data by returning hardcoded, static values. It
 * respects the [ContextQuery] filtering logic, returning only the data types explicitly requested
 * via their IDs.
 */
class MockUserContextService : IUserContextService {

    /**
     * Simulates collecting context.
     *
     * It checks [ContextQuery.getRequestedIds] and populates the response with fake data if the
     * specific ID matches known test types.
     */
    override fun getContext(query: ContextQuery): AggregatedContext = AggregatedContext()
}
