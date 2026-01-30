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

package com.android.systemui.kairos.internal

import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.CoalescingEventProducerScope
import com.android.systemui.kairos.CoalescingMutableEvents
import com.android.systemui.kairos.DeferredValue
import com.android.systemui.kairos.EffectScope
import com.android.systemui.kairos.EventProducerScope
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.EventsInit
import com.android.systemui.kairos.KairosCoroutineScope
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.KairosScope
import com.android.systemui.kairos.KeyedEvents
import com.android.systemui.kairos.LocalNetwork
import com.android.systemui.kairos.MutableEvents
import com.android.systemui.kairos.TransactionEffectScope
import com.android.systemui.kairos.TransactionScope
import com.android.systemui.kairos.groupByKey
import com.android.systemui.kairos.init
import com.android.systemui.kairos.internal.util.childScope
import com.android.systemui.kairos.internal.util.invokeOnCancel
import com.android.systemui.kairos.internal.util.launchImmediate
import com.android.systemui.kairos.launchEffect
import com.android.systemui.kairos.mergeLeft
import com.android.systemui.kairos.takeUntil
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.Maybe.Absent
import com.android.systemui.kairos.util.Maybe.Present
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.NameTag
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.map
import com.android.systemui.kairos.util.mapName
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toNameData
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

internal class BuildScopeImpl(
    val nameData: NameData,
    val createdEpoch: Long,
    val stateScope: StateScopeImpl,
    val coroutineScope: CoroutineScope,
) : InternalBuildScope, InternalStateScope by stateScope {

    init {
        nameData.forceInit()
    }

    private val job: Job
        get() = coroutineScope.coroutineContext.job

    override val kairosNetwork: LocalNetwork by lazy {
        LocalNetwork(nameData, network, coroutineScope, stateScope.deathSignalLazy)
    }

    override fun <T> events(
        name: NameTag?,
        builder: suspend EventProducerScope<T>.() -> Unit,
    ): Events<T> {
        val fullTag = name.toNameData("BuildScope.events")
        return buildEvents(
            fullTag,
            constructEvents = { inputNode ->
                val events = MutableEvents(network, fullTag, inputNode)
                events to EventProducerScope { value -> events.emit(value) }
            },
            builder = builder,
        )
    }

    override fun <In, Out> coalescingEvents(
        getInitialValue: KairosScope.() -> Out,
        coalesce: (old: Out, new: In) -> Out,
        name: NameTag?,
        builder: suspend CoalescingEventProducerScope<In>.() -> Unit,
    ): Events<Out> {
        val nameData = name.toNameData("BuildScope.coalescingEvents")
        return buildEvents(
            nameData,
            constructEvents = { inputNode ->
                val events =
                    CoalescingMutableEvents(
                        nameData,
                        coalesce = { old, new: In -> coalesce(old.value, new) },
                        network = network,
                        getInitialValue = { NoScope.getInitialValue() },
                        impl = inputNode,
                    )
                events to CoalescingEventProducerScope { value -> events.emit(value) }
            },
            builder = builder,
        )
    }

    override fun <A> asyncScope(
        coroutineContext: CoroutineContext,
        name: NameTag?,
        block: BuildSpec<A>,
    ): Pair<DeferredValue<A>, Job> {
        val nameData = name.toNameData("BuildScope.asyncScope")
        val childScope = mutableChildBuildScope(nameData, coroutineContext)
        return DeferredValue(deferAsync { block(childScope) }) to childScope.job
    }

    override fun <R> deferredBuildScope(block: BuildScope.() -> R): DeferredValue<R> =
        DeferredValue(deferAsync { block() })

    override fun deferredBuildScopeAction(block: BuildScope.() -> Unit) {
        deferAction { block() }
    }

    override fun <A> Events<A>.observe(
        coroutineContext: CoroutineContext,
        name: NameTag?,
        block: EffectScope.(A) -> Unit,
    ): DisposableHandle {
        val nameData = name.toNameData("Events.observe")
        val interceptor =
            coroutineContext[ContinuationInterceptor]
                ?: coroutineScope.coroutineContext[ContinuationInterceptor]
        return observeInternal(nameData, coroutineContext) { effectScope, output ->
            scheduleDispatchedOutput(interceptor = interceptor) { effectScope.block(output) }
        }
    }

    override fun <A> Events<A>.observeSync(
        name: NameTag?,
        block: TransactionEffectScope.(A) -> Unit,
    ): DisposableHandle {
        val nameData = name.toNameData("Events.observeSync")
        return observeInternal(nameData, EmptyCoroutineContext) { effectScope, output ->
            val scope =
                object :
                    TransactionEffectScope,
                    TransactionScope by this@observeInternal,
                    EffectScope by effectScope {}
            scope.block(output)
        }
    }

    override fun <A, B> Events<A>.mapBuild(
        name: NameTag?,
        transform: BuildScope.(A) -> B,
    ): Events<B> {
        val nameData = name.toNameData("Events.mapBuild")
        val childScope = coroutineScope.childScope()
        return EventsInit(
            constInit(
                nameData,
                mapImpl({ init.connect(evalScope = this) }, nameData) { spec, _ ->
                        reenterBuildScope(outerScope = this@BuildScopeImpl, childScope)
                            .transform(spec)
                    }
                    .cached(nameData),
            )
        )
    }

    override fun <K, A, B> Events<Map<K, Maybe<BuildSpec<A>>>>.applyLatestSpecForKey(
        initialSpecs: DeferredValue<Map<K, BuildSpec<B>>>,
        numKeys: Int?,
        name: NameTag?,
    ): Pair<Events<Map<K, Maybe<A>>>, DeferredValue<Map<K, B>>> {
        val nameData = name.toNameData("Events.applyLatestSpecForKey")
        val eventsByKey: KeyedEvents<K, Maybe<BuildSpec<A>>> =
            groupByKey(nameData + "eventsByKey", numKeys)
        val childCoroutineScope = coroutineScope.childScope()
        val initOut: Lazy<Map<K, B>> = deferAsync {
            // swap out the CoroutineScope used for this build scope with the child scope
            reenterBuildScope(this@BuildScopeImpl, childCoroutineScope).run {
                initialSpecs.unwrapped.value.mapValues { (k, spec) ->
                    val newEnd: Events<Maybe<BuildSpec<A>>> = eventsByKey[k]
                    val newScope =
                        childBuildScope(
                            newEnd,
                            nameData.mapName { "$it[key=$k, epoch=$epoch, init=true]" },
                        )
                    newScope.spec()
                }
            }
        }
        val changesImpl: EventsImpl<Map<K, Maybe<A>>> =
            mapImpl(
                upstream = { this@applyLatestSpecForKey.init.connect(evalScope = this) },
                nameData + "changes",
            ) { upstreamMap, _ ->
                reenterBuildScope(this@BuildScopeImpl, childCoroutineScope).run {
                    upstreamMap.mapValues { (k: K, ma: Maybe<BuildSpec<A>>) ->
                        ma.map { spec ->
                            val newName =
                                nameData.mapName { "$it[key=$k, epoch=$epoch, init = false]" }
                            val newEnd: Events<Maybe<BuildSpec<A>>> =
                                eventsByKey[k].skipNextUnsafe(newName + "newEnd")
                            val newScope = childBuildScope(newEnd, newName)
                            newScope.spec()
                        }
                    }
                }
            }
        val changes: Events<Map<K, Maybe<A>>> =
            EventsInit(constInit(nameData, changesImpl.cached(nameData)))
        // Ensure effects are observed; otherwise init will stay alive longer than expected
        changes.observeSync(nameData + "observeNoop")
        return changes to DeferredValue(initOut)
    }

    override fun toString(): String = "${super.toString()}[$nameData]"

    private fun <A> Events<A>.observeInternal(
        nameData: NameData,
        context: CoroutineContext,
        block: EvalScope.(EffectScope, A) -> Unit,
    ): DisposableHandle {
        val subRef = AtomicReference<Maybe<Output<A>>?>(null)
        val childScope: CoroutineScope = coroutineScope.childScope(context)
        val handle = DisposableHandle {
            subRef.getAndSet(Absent)?.let { output ->
                if (output is Present) {
                    @Suppress("DeferredResultUnused")
                    network.transaction("observeEffect cancelled") {
                        scheduleDeactivation(output.value)
                    }
                }
            }
        }
        val effectScope: EffectScope = effectScope(childScope, nameData + "effectScope")
        val outputNode =
            Output<A>(
                nameData,
                onDeath = { subRef.set(Absent) },
                onEmit = onEmit@{ output ->
                        if (subRef.get() !is Present) return@onEmit
                        // Not cancelled, safe to emit]
                        block(effectScope, output)
                    },
            )
        // Defer, in case any EventsLoops / StateLoops still need to be set
        deferAction {
            // Check for immediate cancellation
            if (subRef.get() != null) {
                childScope.cancel()
                return@deferAction
            }
            // Stop observing when this scope dies
            truncateToScope(this@observeInternal, nameData + "truncateToScope")
                .init
                .connect(evalScope = stateScope.evalScope)
                .activate(evalScope = stateScope.evalScope, outputNode.schedulable)
                ?.let { (conn, needsEval) ->
                    outputNode.upstream = conn
                    if (!subRef.compareAndSet(null, Maybe.present(outputNode))) {
                        // Handle's already been disposed, schedule deactivation
                        scheduleDeactivation(outputNode)
                    } else if (needsEval) {
                        outputNode.schedule(0, evalScope = stateScope.evalScope)
                    }
                } ?: run { childScope.cancel() }
        }
        return handle
    }

    private fun effectScope(childScope: CoroutineScope, nameData: NameData) =
        object : EffectScope {
            override fun <R> async(
                context: CoroutineContext,
                start: CoroutineStart,
                name: NameTag?,
                block: suspend KairosCoroutineScope.() -> R,
            ): Deferred<R> {
                val asyncNameData = name.toNameData("EffectScope.async")
                return childScope.async(context, start) newScope@{
                    val childEndSignal: Events<Unit> =
                        this@BuildScopeImpl.newStopEmitter(asyncNameData + "childEndSignal").apply {
                            this@newScope.invokeOnCancel { emit(Unit) }
                        }
                    val childStateScope: StateScopeImpl =
                        this@BuildScopeImpl.stateScope.childStateScope(
                            childEndSignal,
                            asyncNameData,
                        )
                    val localNetwork =
                        LocalNetwork(
                            asyncNameData,
                            network = this@BuildScopeImpl.network,
                            scope = this@newScope,
                            deathSignalLazy = childStateScope.deathSignalLazy,
                        )
                    val scope =
                        object : KairosCoroutineScope, CoroutineScope by this@newScope {
                            override val kairosNetwork: KairosNetwork = localNetwork
                        }
                    scope.block()
                }
            }

            override val kairosNetwork: KairosNetwork =
                LocalNetwork(
                    nameData,
                    network = this@BuildScopeImpl.network,
                    scope = childScope,
                    deathSignalLazy = this@BuildScopeImpl.stateScope.deathSignalLazy,
                )
        }

    private fun <A, T : Events<A>, S> buildEvents(
        nameData: NameData,
        constructEvents: (InputNode<A>) -> Pair<T, S>,
        builder: suspend S.() -> Unit,
    ): Events<A> {
        var job: Job? = null
        val stopEmitter = newStopEmitter(nameData + "stopEmitter")
        // Create a child scope that will be kept alive beyond the end of this transaction.
        val childScope = coroutineScope.childScope()
        lateinit var emitterAndScope: Pair<T, S>
        val inputNode =
            InputNode<A>(
                nameData,
                activate = {
                    // It's possible that activation occurs after all effects have been run, due
                    // to a MuxDeferred switch-in. For this reason, we need to activate in a new
                    // transaction.
                    check(job == null) { "[$nameData] already activated" }
                    job =
                        childScope.launchImmediate {
                            network
                                .transaction("buildEvents") {
                                    reenterBuildScope(this@BuildScopeImpl, childScope).launchEffect(
                                        nameData + "activatedBuilderEffect"
                                    ) {
                                        builder(emitterAndScope.second)
                                        stopEmitter.emit(Unit)
                                    }
                                }
                                .await()
                                .join()
                        }
                },
                deactivate = {
                    checkNotNull(job) { "[$nameData] already deactivated" }.cancel()
                    job = null
                },
            )
        emitterAndScope = constructEvents(inputNode)
        // Deactivate once scope dies, or once [builder] completes.
        val deactivateSignal: Events<*> =
            mergeLeft(nameData + "deactivateSignal", deathSignal, stopEmitter)
        return takeUntil(nameData + "takeUntilStopped", emitterAndScope.first, deactivateSignal)
    }

    private fun newStopEmitter(nameData: NameData): CoalescingMutableEvents<Unit, Unit> =
        CoalescingMutableEvents(
            nameData,
            coalesce = { _, _: Unit -> },
            network = network,
            getInitialValue = {},
        )

    fun childBuildScope(newEnd: Events<Any>, nameData: NameData): BuildScopeImpl {
        val newCoroutineScope: CoroutineScope = coroutineScope.childScope()
        val newChildBuildScope = newChildBuildScope(newCoroutineScope, newEnd, nameData)
        // When the end signal emits, cancel all running coroutines in the new scope
        val outputNode =
            Output<Any?>(nameData + "observeLifetime", onEmit = { newCoroutineScope.cancel() })
        deferAction {
            newChildBuildScope.deathSignal.init
                .connect(stateScope.evalScope)
                .activate(stateScope.evalScope, outputNode.schedulable)
                ?.let { (conn, needsEval) ->
                    outputNode.upstream = conn
                    if (needsEval) {
                        outputNode.schedule(0, evalScope = stateScope.evalScope)
                    }
                }
        }
        return newChildBuildScope
    }

    private fun mutableChildBuildScope(
        childNameData: NameData,
        coroutineContext: CoroutineContext,
    ): BuildScopeImpl {
        val stopEmitter = newStopEmitter(childNameData + "stopEmitter")
        val newCoroutineScope = coroutineScope.childScope(coroutineContext)
        // If the job is cancelled, emit the stop signal
        newCoroutineScope.coroutineContext.job.invokeOnCompletion { stopEmitter.emit(Unit) }
        return newChildBuildScope(newCoroutineScope, stopEmitter, childNameData)
    }

    private fun newChildBuildScope(
        newCoroutineScope: CoroutineScope,
        newEnd: Events<Any>,
        nameData: NameData,
    ): BuildScopeImpl {
        // Ensure that once this transaction is done, the new child scope enters the completing
        // state (kept alive so long as there are child jobs).
        scheduleOutput(
            OneShot(nameData + "completeJob") {
                (newCoroutineScope.coroutineContext.job as CompletableJob).complete()
            }
        )
        return BuildScopeImpl(
            nameData,
            epoch,
            stateScope = stateScope.childStateScope(newEnd, nameData),
            coroutineScope = newCoroutineScope,
        )
    }
}

private fun EvalScope.reenterBuildScope(
    outerScope: BuildScopeImpl,
    coroutineScope: CoroutineScope,
) =
    BuildScopeImpl(
        outerScope.nameData,
        outerScope.createdEpoch,
        stateScope =
            StateScopeImpl(
                outerScope.stateScope.nameData,
                outerScope.stateScope.createdEpoch,
                evalScope = this,
                deathSignalLazy = outerScope.stateScope.deathSignalLazy,
            ),
        coroutineScope,
    )
