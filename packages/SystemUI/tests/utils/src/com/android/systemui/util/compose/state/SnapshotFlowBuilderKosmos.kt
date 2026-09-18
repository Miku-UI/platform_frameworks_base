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

package com.android.systemui.util.compose.state

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.SnapshotFlowManager
import androidx.compose.runtime.snapshotFlow
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.test.TestDispatcher

val Kosmos.snapshotFlowBuilder: SnapshotFlowBuilder by
    Kosmos.Fixture { TestSnapshotFlowBuilder(testDispatcher) }

@OptIn(ExperimentalComposeRuntimeApi::class)
private class TestSnapshotFlowBuilder(private val testDispatcher: TestDispatcher) :
    SnapshotFlowBuilder {
    val manager = SnapshotFlowManager()

    override fun dispose() {
        manager.dispose()
    }

    override fun <R> snapshotFlow(block: () -> R): Flow<R> =
        snapshotFlow(manager, block).flowOn(testDispatcher)
}
