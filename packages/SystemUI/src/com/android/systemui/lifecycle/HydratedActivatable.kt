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

package com.android.systemui.lifecycle

import androidx.compose.runtime.State
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * An [Activatable] with convenience methods to easily transform upstream [Flow]s into downstream
 * snapshot-backed [State]s. Also allows non-suspend code to run suspend code.
 *
 * @see [ExclusiveActivatable]
 */
abstract class HydratedActivatable(
    /** Enable this to use [enqueueOnActivatedScope] */
    val enableEnqueuedActivations: Boolean = false
) : Activatable {

    private val hydrator = Hydrator("${this::class.simpleName}.hydrator")

    private var requestChannel: Channel<suspend () -> Unit>? = null

    final override suspend fun activate(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }

            if (enableEnqueuedActivations) {
                launch {
                    requestChannel = Channel<suspend () -> Unit>(BUFFERED)
                    requestChannel!!.receiveAsFlow().collect { it.invoke() }
                }
            }

            try {
                onActivated()
                awaitCancellation()
            } finally {
                requestChannel?.cancel()
                requestChannel = null
                onDeactivated()
            }
        }
    }

    /**
     * Notifies that the [Activatable] has been activated.
     *
     * Serves as an entrypoint to kick off coroutine work that the object requires in order to keep
     * its state fresh and/or perform side-effects.
     *
     * Implementations could follow this pattern:
     * ```kotlin
     * override suspend fun onActivated() {
     *     coroutineScope {
     *         launch { ... }
     *         launch { ... }
     *         launch { ... }
     *     }
     * }
     * ```
     *
     * @see activate
     */
    protected open suspend fun onActivated() {}

    /** Notifies that the [Activatable] has been deactivated. */
    protected open suspend fun onDeactivated() {}

    /**
     * Queues [block] for execution on the activated scope. Requests are executed sequentially.
     *
     * @return [null] when the [Activatable] is not active. Otherwise, returns the [ChannelResult].
     *   A success Channel result means the request is queued but it does not guarantee that [block]
     *   will be executed as the Activatable can still be deactivated before [block] had a chance to
     *   be processed.
     */
    protected fun enqueueOnActivatedScope(block: suspend () -> Unit): ChannelResult<Unit>? {
        if (!enableEnqueuedActivations) error("enableEnqueuedActivations needs to be enabled.")
        return requestChannel?.trySend(block)
    }

    /** @see [Hydrator.hydratedStateOf] */
    protected fun <T> StateFlow<T>.hydratedStateOf(traceName: String): State<T> =
        hydrator.hydratedStateOf(traceName, this)

    /** @see [Hydrator.hydratedStateOf] */
    protected fun <T> Flow<T>.hydratedStateOf(traceName: String, initialValue: T): State<T> =
        hydrator.hydratedStateOf(traceName, initialValue, this)

    /**
     * Returns a [Hydrator.StateDelegateProvider] which will automatically set the [traceName]. Use
     * with the `by` keyword.
     *
     * Usage: `val myState by hydratedStateOf()`
     *
     * @see [Hydrator.hydratedStateOf]
     */
    protected fun <T> StateFlow<T>.hydratedStateOf() = hydrator.hydratedStateOf(this)

    /**
     * Returns a [Hydrator.StateDelegateProvider] which will automatically set the [traceName]. Use
     * with the `by` keyword.
     *
     * Usage: `val myState by hydratedStateOf(initialValue)`
     *
     * @see [Hydrator.hydratedStateOf]
     */
    protected fun <T> Flow<T>.hydratedStateOf(initialValue: T) =
        hydrator.hydratedStateOf(initialValue, this)
}
