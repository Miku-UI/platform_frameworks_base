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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * Creates [Flows][Flow] that emit whenever observed
 * [Compose States][androidx.compose.runtime.State] change.
 *
 * Unlike raw [snapshotFlow][androidx.compose.runtime.snapshotFlow] (without the optional `manager`
 * argument), usage of [SnapshotFlowBuilder.snapshotFlow] scales better as the number of usages
 * increases.
 *
 * Unlike [snapshotFlow] *with* the `manager` argument, [SnapshotFlowBuilder] properly sets up
 * thread safety guarantees, so that returned [Flows][Flow] can be [collected][Flow.collect] from
 * any [CoroutineDispatcher].
 *
 * @see androidx.compose.runtime.snapshotFlow
 */
interface SnapshotFlowBuilder {

    /**
     * Create a [Flow] from observable [Snapshot] state. (e.g. state holders returned by
     * [mutableStateOf][androidx.compose.runtime.mutableStateOf].)
     *
     * @see androidx.compose.runtime.snapshotFlow
     */
    fun <R> snapshotFlow(block: () -> R): Flow<R>

    /**
     * Disposes of this builder. Disposing of a manager disconnects it from the [Snapshot] system,
     * rendering it incapable of handling any subscriptions.
     *
     * If this method is called after this builder has been disposed of, an [IllegalStateException]
     * will be thrown.
     *
     * @see [SnapshotFlowManager.dispose]
     */
    fun dispose()

    companion object {
        /** [SnapshotFlowBuilder] that observes state changes on the Main thread. */
        val Main: SnapshotFlowBuilder = SnapshotFlowBuilderImpl(AndroidUiDispatcher.Main)

        /**
         * Returns a new [SnapshotFlowBuilder] that observes state changes on a new dedicated
         * background thread.
         */
        fun createOnNewBackgroundThread(): SnapshotFlowBuilder {
            val executor = Executors.newSingleThreadExecutor()
            return SnapshotFlowBuilderImpl(executor.asCoroutineDispatcher()) { executor.shutdown() }
        }
    }
}

@OptIn(ExperimentalComposeRuntimeApi::class)
private class SnapshotFlowBuilderImpl(
    private val context: CoroutineContext,
    onDispose: (() -> Unit)? = null,
) : SnapshotFlowBuilder {

    private val manager = SnapshotFlowManager()
    private val onDisposeRef = AtomicReference(onDispose)

    override fun <R> snapshotFlow(block: () -> R): Flow<R> =
        snapshotFlow(manager, block).flowOn(context)

    override fun dispose() {
        manager.dispose()
        onDisposeRef.getAndSet(null)?.invoke()
    }
}
