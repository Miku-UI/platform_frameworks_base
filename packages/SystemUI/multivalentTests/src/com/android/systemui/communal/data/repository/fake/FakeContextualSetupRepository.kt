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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, a
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.communal.data.repository.fake

import com.android.systemui.communal.data.repository.ContextualSetupRepository
import com.android.systemui.communal.data.repository.SetupState
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeContextualSetupRepository : ContextualSetupRepository {
    private val setupState = mutableMapOf<String, MutableStateFlow<SetupState>>()
    private val failureCount = mutableMapOf<String, Int>()

    override fun setupState(id: String): Flow<SetupState> {
        return setupState.getOrPut(id) { MutableStateFlow(SetupState.NotStarted) }
    }

    // For testing purposes
    suspend fun updateState(id: String, state: SetupState) {
        setupState.getOrPut(id) { MutableStateFlow(SetupState.NotStarted) }.value = state
    }

    override suspend fun snooze(id: String, duration: Duration) {
        val expirationTime = Instant.now().plusMillis(duration.inWholeMilliseconds)
        updateState(id, SetupState.Snoozed(expirationTime))
    }

    override suspend fun dismiss(id: String) {
        updateState(id, SetupState.Dismissed)
    }

    override suspend fun complete(id: String) {
        updateState(id, SetupState.Completed)
    }

    override suspend fun reset(id: String) {
        updateState(id, SetupState.NotStarted)
    }

    override suspend fun incrementFailureCount(id: String): Int {
        val count = failureCount.getOrDefault(id, 0) + 1
        failureCount[id] = count
        return count
    }

    fun setSetupState(id: String, state: SetupState) {
        setupState.getOrPut(id) { MutableStateFlow(SetupState.NotStarted) }.value = state
    }
}
