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

package com.android.systemui.util.lifecycle.kairos

import com.android.systemui.KairosActivatable
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.EventsLoop
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.IncrementalLoop
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.State
import com.android.systemui.kairos.StateLoop
import com.android.systemui.kairos.effect
import com.android.systemui.kairos.toColdConflatedFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Utilities for defining [State] and [Events] from a constructor without a provided [BuildScope].
 * These instances are not active until the builder is [activated][activate]; while you can
 * immediately use them with other Kairos APIs, it is an error to [observe][BuildScope.observe] them
 * if they have not been activated by the time the containing transaction has ended.
 *
 * ```kotlin
 * class MyRepository(private val dataSource: DataSource) : KairosBuilder by kairosBuilder() {
 *   val dataSourceEvent = buildEvents<SomeData> {
 *       // inside this lambda, we have access to a BuildScope, which can be used to create
 *       // new inputs to the Kairos network
 *       dataSource.someDataFlow.toEvents()
 *   }
 * }
 * ```
 */
interface KairosBuilder : KairosActivatable {
    /**
     * Returns a forward-reference to a [State] that will be instantiated when this builder is
     * [activated][activate].
     */
    fun <R> buildState(block: BuildScope.() -> State<R>): State<R>

    /**
     * Returns a forward-reference to an [Events] that will be instantiated when this builder is
     * [activated][activate].
     */
    fun <R> buildEvents(block: BuildScope.() -> Events<R>): Events<R>

    /**
     * Returns a forward-reference to an [Incremental] that will be instantiated when this builder
     * is [activated][activate].
     */
    fun <K, V> buildIncremental(block: BuildScope.() -> Incremental<K, V>): Incremental<K, V>

    /** Defers [block] until this builder is [activated][activate]. */
    fun onActivated(block: BuildScope.() -> Unit)
}

/** Returns an [KairosBuilder] that can only be [activated][KairosActivatable.activate] once. */
fun kairosBuilder(): KairosBuilder = KairosBuilderImpl()

private class KairosBuilderImpl : KairosBuilder {

    private var _builds: MutableList<KairosActivatable>? = mutableListOf()
    private var _startables: MutableList<KairosActivatable>? = mutableListOf()

    private val startables
        get() = checkNotNull(_startables) { "Kairos network has already been initialized" }

    private val builds
        get() = checkNotNull(_builds) { "Kairos network has already been initialized" }

    override fun <R> buildState(block: BuildScope.() -> State<R>): State<R> =
        StateLoop<R>().apply { builds.add { loopback = block() } }

    override fun <R> buildEvents(block: BuildScope.() -> Events<R>): Events<R> =
        EventsLoop<R>().apply { builds.add { loopback = block() } }

    override fun <K, V> buildIncremental(
        block: BuildScope.() -> Incremental<K, V>
    ): Incremental<K, V> = IncrementalLoop<K, V>().apply { builds.add { loopback = block() } }

    override fun onActivated(block: BuildScope.() -> Unit) {
        startables.add { block() }
    }

    override fun BuildScope.activate() {
        builds.forEach { it.run { activate() } }
        _builds = null
        deferredBuildScopeAction {
            startables.forEach { it.run { activate() } }
            _startables = null
        }
    }
}

/**
 * Like [toColdConflatedFlow], but does not attempt to observe [state] until the [KairosBuilder] has
 * been [activated][KairosActivatable.activate].
 */
fun <T> KairosBuilder.buildColdConflatedFlow(state: State<T>): Flow<T> {
    val network = CompletableDeferred<KairosNetwork>()
    onActivated { effect { network.complete(kairosNetwork) } }
    return flow { emitAll(state.toColdConflatedFlow(network.await())) }
}

/**
 * Like [toColdConflatedFlow], but does not attempt to [activate][KairosNetwork.activateSpec] and
 * [observe][BuildScope.observe] [stateSpec] until the [KairosBuilder] has been
 * [activated][KairosActivatable.activate].
 */
@JvmName("buildColdConflatedFlowFromStateSpec")
fun <T> KairosBuilder.buildColdConflatedFlow(stateSpec: BuildSpec<State<T>>): Flow<T> {
    val network = CompletableDeferred<KairosNetwork>()
    onActivated { effect { network.complete(kairosNetwork) } }
    return flow { emitAll(stateSpec.toColdConflatedFlow(network.await())) }
}

/**
 * Like [toColdConflatedFlow], but does not attempt to observe [events] until the [KairosBuilder]
 * has been [activated][KairosActivatable.activate].
 */
fun <T> KairosBuilder.buildColdConflatedFlow(events: Events<T>): Flow<T> {
    val network = CompletableDeferred<KairosNetwork>()
    onActivated { effect { network.complete(kairosNetwork) } }
    return flow { emitAll(events.toColdConflatedFlow(network.await())) }
}

/**
 * Like [toColdConflatedFlow], but does not attempt to [activate][KairosNetwork.activateSpec] and
 * [observe][BuildScope.observe] [spec] until the [KairosBuilder] has been
 * [activated][KairosActivatable.activate].
 */
@JvmName("buildColdConflatedFlowFromEventsSpec")
fun <T> KairosBuilder.buildColdConflatedFlow(eventsSpec: BuildSpec<Events<T>>): Flow<T> {
    val network = CompletableDeferred<KairosNetwork>()
    onActivated { effect { network.complete(kairosNetwork) } }
    return flow { emitAll(eventsSpec.toColdConflatedFlow(network.await())) }
}

/**
 * Convenience shorthand for:
 * ```
 *     buildColdConflatedFlow(state).stateIn(scope, sharingStarted, initialValue)
 * ```
 *
 * @see buildColdConflatedFlow
 */
fun <T> KairosBuilder.buildStateFlow(
    state: State<T>,
    scope: CoroutineScope,
    sharingStarted: SharingStarted,
    initialValue: T,
): StateFlow<T> = buildColdConflatedFlow(state).stateIn(scope, sharingStarted, initialValue)

/**
 * Convenience shorthand for:
 * ```
 *     buildColdConflatedFlow(spec).stateIn(scope, sharingStarted, initialValue)
 * ```
 *
 * @see buildColdConflatedFlow
 */
fun <T> KairosBuilder.buildStateFlow(
    spec: BuildSpec<State<T>>,
    scope: CoroutineScope,
    sharingStarted: SharingStarted,
    initialValue: T,
): StateFlow<T> = buildColdConflatedFlow(spec).stateIn(scope, sharingStarted, initialValue)
