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

package com.android.systemui.compose

import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.RecomposerInfo
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.tooling.CompositionObserverHandle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Sets up recomposition cause tracing when enabled from the command. */
@SysUISingleton
@OptIn(ExperimentalComposeRuntimeApi::class, InternalComposeTracingApi::class)
class RecompositionCauseTracing
@Inject
constructor(@Application private val coroutineScope: CoroutineScope) {

    private var observeSnapshotHandle: ObserverHandle? = null
    private var recompositionTracingJob: Job? = null
    @OptIn(ExperimentalComposeRuntimeApi::class)
    private val observers = mutableMapOf<RecomposerInfo, CompositionObserverHandle?>()

    private val observer = RecompositionStateTracingObserver(PerfettoSdkTracer)

    fun enable() {
        disable()
        PerfettoSdkTracer.register()
        recompositionTracingJob = coroutineScope.launch { runRecomposerObserver() }
        observeSnapshotHandle = Snapshot.registerGlobalWriteObserver(observer::onStateWrite)
    }

    private suspend fun runRecomposerObserver() {
        Recomposer.runningRecomposers.collect {
            it.forEach { recomposer ->
                if (recomposer !in observers) {
                    observers[recomposer] = recomposer.observe(observer)
                }
            }
        }
    }

    fun disable() {
        recompositionTracingJob?.cancel().also {
            observers.forEach { (_, handle) -> handle?.dispose() }
            observers.clear()
        }
        recompositionTracingJob = null
        observeSnapshotHandle?.dispose()
        observeSnapshotHandle = null
    }
}
