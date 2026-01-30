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

package com.android.systemui.kairos

import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.kairos.internal.BuildScopeImpl
import com.android.systemui.kairos.internal.EvalScope
import com.android.systemui.kairos.internal.Network
import com.android.systemui.kairos.internal.NoScope
import com.android.systemui.kairos.internal.StateScopeImpl
import com.android.systemui.kairos.internal.util.childScope
import com.android.systemui.kairos.util.FullNameTag
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.NameTag
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.mapName
import com.android.systemui.kairos.util.toNameData
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/** Marks APIs that are still **experimental** and shouldn't be used in general production code. */
@RequiresOptIn(
    message = "This API is experimental and should not be used in general production code."
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalKairosApi

/**
 * External interface to a Kairos network of reactive components. Can be used to make transactional
 * queries and modifications to the network.
 */
@ExperimentalKairosApi
interface KairosNetwork {
    /**
     * Runs [block] inside of a transaction, suspending until the transaction is complete.
     *
     * The [BuildScope] receiver exposes methods that can be used to query or modify the network. If
     * the network is cancelled while the caller of [transact] is suspended, then the call will be
     * cancelled.
     */
    suspend fun <R> transact(block: TransactionScope.() -> R): R

    /**
     * Activates [spec] in a transaction, suspending indefinitely. While suspended, all observers
     * and long-running effects are kept alive. When cancelled, observers are unregistered and
     * effects are cancelled.
     */
    suspend fun activateSpec(name: NameTag? = null, spec: BuildSpec<*>)

    /** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
    fun <In, Out> coalescingMutableEvents(
        getInitialValue: KairosScope.() -> Out,
        name: NameTag? = null,
        coalesce: KairosScope.(old: Out, new: In) -> Out,
    ): CoalescingMutableEvents<In, Out>

    /** Returns a [MutableState] that can emit values into this [KairosNetwork]. */
    fun <T> mutableEvents(name: NameTag? = null): MutableEvents<T>

    /** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
    fun <T> conflatedMutableEvents(name: NameTag? = null): CoalescingMutableEvents<T, T>

    /** Returns a [MutableState]. with initial state [initialValue]. */
    fun <T> mutableStateDeferred(
        initialValue: DeferredValue<T>,
        name: NameTag? = null,
    ): MutableState<T>
}

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> KairosNetwork.coalescingMutableEvents(
    initialValue: Out,
    name: NameTag? = null,
    coalesce: KairosScope.(old: Out, new: In) -> Out,
): CoalescingMutableEvents<In, Out> =
    coalescingMutableEvents(getInitialValue = { initialValue }, name, coalesce)

/** Returns a [MutableState] with initial state [initialValue]. */
@ExperimentalKairosApi
fun <T> KairosNetwork.mutableState(initialValue: T, name: NameTag? = null): MutableState<T> =
    mutableStateDeferred(deferredOf(initialValue), name)

/** Returns a [MutableState] with initial state [initialValue]. */
@ExperimentalKairosApi
fun <T> MutableState(
    network: KairosNetwork,
    initialValue: T,
    name: NameTag? = null,
): MutableState<T> = network.mutableState(initialValue, name)

/** Returns a [MutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <T> MutableEvents(network: KairosNetwork, name: NameTag? = null): MutableEvents<T> =
    network.mutableEvents(name)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> CoalescingMutableEvents(
    network: KairosNetwork,
    initialValue: Out,
    name: NameTag? = null,
    coalesce: KairosScope.(old: Out, new: In) -> Out,
): CoalescingMutableEvents<In, Out> =
    network.coalescingMutableEvents({ initialValue }, name, coalesce)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> CoalescingMutableEvents(
    network: KairosNetwork,
    getInitialValue: KairosScope.() -> Out,
    name: NameTag? = null,
    coalesce: KairosScope.(old: Out, new: In) -> Out,
): CoalescingMutableEvents<In, Out> =
    network.coalescingMutableEvents(getInitialValue, name, coalesce)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <T> ConflatedMutableEvents(
    network: KairosNetwork,
    name: NameTag? = null,
): CoalescingMutableEvents<T, T> = network.conflatedMutableEvents(name)

/**
 * Activates [spec] in a transaction and invokes [block] with the result, suspending indefinitely.
 * While suspended, all observers and long-running effects are kept alive. When cancelled, observers
 * are unregistered and effects are cancelled.
 */
@ExperimentalKairosApi
suspend fun <R> KairosNetwork.activateSpec(
    name: NameTag? = null,
    spec: BuildSpec<R>,
    block: suspend KairosCoroutineScope.(R) -> Unit,
) {
    val fullName = name.toNameData("KairosNetwork.activateSpec")
    activateSpec(fullName) {
        val result = spec.applySpec()
        launchEffect(fullName.mapName { "$it-effectBlock" }) { block(result) }
    }
}

internal class LocalNetwork(
    val nameData: NameData,
    private val network: Network,
    private val scope: CoroutineScope,
    private val deathSignalLazy: Lazy<Events<*>>,
) : KairosNetwork {

    init {
        nameData.forceInit()
    }

    override suspend fun <R> transact(block: TransactionScope.() -> R): R =
        network.transaction("KairosNetwork.transact") { block() }.awaitOrCancel()

    override suspend fun activateSpec(name: NameTag?, spec: BuildSpec<*>): Unit = coroutineScope {
        val nameData = name.toNameData("KairosNetwork.activateSpec")
        lateinit var completionHandle: DisposableHandle
        val childEndSignal = conflatedMutableEvents<Unit>(nameData.mapName { "$it-specEndSignal" })
        coroutineContext.job.invokeOnCompletion {
            completionHandle.dispose()
            childEndSignal.emit(Unit)
        }
        val job =
            launch(start = CoroutineStart.LAZY) {
                network
                    .transaction("KairosNetwork.activateSpec") {
                        enterBuildScope(this@coroutineScope)
                            .childBuildScope(childEndSignal, nameData)
                            .run {
                                launchScope(nameData.mapName { "$it-applySpec" }) {
                                    spec.applySpec()
                                }
                            }
                    }
                    .awaitOrCancel()
                    .joinOrCancel()
                completionHandle.dispose()
            }
        completionHandle = scope.coroutineContext.job.invokeOnCompletion { job.cancel() }
        job.start()
    }

    private fun EvalScope.enterBuildScope(coroutineScope: CoroutineScope) =
        BuildScopeImpl(
            nameData,
            epoch,
            stateScope =
                StateScopeImpl(
                    nameData,
                    epoch,
                    evalScope = this,
                    deathSignalLazy = deathSignalLazy,
                ),
            coroutineScope = coroutineScope,
        )

    private suspend fun <T> Deferred<T>.awaitOrCancel(): T =
        try {
            await()
        } catch (ex: CancellationException) {
            cancel(ex)
            throw ex
        }

    private suspend fun Job.joinOrCancel(): Unit =
        try {
            join()
        } catch (ex: CancellationException) {
            cancel(ex)
            throw ex
        }

    override fun <In, Out> coalescingMutableEvents(
        getInitialValue: KairosScope.() -> Out,
        name: NameTag?,
        coalesce: KairosScope.(old: Out, new: In) -> Out,
    ): CoalescingMutableEvents<In, Out> =
        CoalescingMutableEvents(
            name.toNameData("KairosNetwork.coalescingMutableEvents"),
            coalesce = { old, new -> NoScope.coalesce(old.value, new) },
            network,
            { NoScope.getInitialValue() },
        )

    override fun <T> conflatedMutableEvents(name: NameTag?): CoalescingMutableEvents<T, T> =
        CoalescingMutableEvents(
            name.toNameData("KairosNetwork.conflatedMutableEvents"),
            coalesce = { _, new -> new },
            network,
            { error("WTF: init value accessed for conflatedMutableEvents") },
        )

    override fun <T> mutableEvents(name: NameTag?): MutableEvents<T> =
        MutableEvents(network, name.toNameData("KairosNetwork.mutableEvents"))

    override fun <T> mutableStateDeferred(
        initialValue: DeferredValue<T>,
        name: NameTag?,
    ): MutableState<T> =
        MutableState(
            name.toNameData("KairosNetwork.mutableStateDeferred"),
            network,
            initialValue.unwrapped,
        )

    override fun toString(): String = "${super.toString()}[$nameData]"
}

/**
 * Combination of an [KairosNetwork] and a [Job] that, when cancelled, will cancel the entire Kairos
 * network.
 */
@ExperimentalKairosApi
class RootKairosNetwork
internal constructor(private val network: Network, private val scope: CoroutineScope, job: Job) :
    Job by job,
    KairosNetwork by LocalNetwork(
        FullNameTag(lazyOf("root"), "launchKairosNetwork"),
        network,
        scope,
        lazyOf(emptyEvents),
    )

/** Constructs a new [RootKairosNetwork] in the given [CoroutineScope] and [CoalescingPolicy]. */
@ExperimentalKairosApi
fun CoroutineScope.launchKairosNetwork(
    context: CoroutineContext = EmptyCoroutineContext,
    coalescingPolicy: CoalescingPolicy = CoalescingPolicy.Normal,
): RootKairosNetwork {
    val scope = childScope(context)
    val network = Network(scope, coalescingPolicy)
    scope.launchTraced("launchKairosNetwork scheduler") { network.runInputScheduler() }
    return RootKairosNetwork(network, scope, scope.coroutineContext.job)
}

/** Constructs a new [RootKairosNetwork] in the given [CoroutineScope] and [CoalescingPolicy]. */
@ExperimentalKairosApi
fun KairosNetwork(
    scope: CoroutineScope,
    coalescingPolicy: CoalescingPolicy = CoalescingPolicy.Normal,
): RootKairosNetwork = scope.launchKairosNetwork(coalescingPolicy = coalescingPolicy)

/** Configures how multiple input events are processed by the network. */
@ExperimentalKairosApi
enum class CoalescingPolicy {
    /**
     * Each input event is processed in its own transaction. This policy has the least overhead but
     * can cause backpressure if the network becomes flooded with inputs.
     */
    None,
    /**
     * Input events are processed as they appear. Compared to [Eager], this policy will not
     * internally [yield][kotlinx.coroutines.yield] to allow more inputs to be processed before
     * starting a transaction. This means that if there is a race between an input and a transaction
     * occurring, it is beholden to the
     * [CoroutineDispatcher][kotlinx.coroutines.CoroutineDispatcher] to determine the ordering.
     *
     * Note that any input events which miss being included in a transaction will be immediately
     * scheduled for a subsequent transaction.
     */
    Normal,
    /**
     * Input events are processed eagerly. Compared to [Normal], this policy will internally
     * [yield][kotlinx.coroutines.yield] to allow for as many input events to be processed as
     * possible. This can be useful for noisy networks where many inputs can be handled
     * simultaneously, potentially improving throughput.
     */
    Eager,
}

@ExperimentalKairosApi
interface HasNetwork : KairosScope {
    /**
     * A [KairosNetwork] handle that is bound to the lifetime of a [BuildScope].
     *
     * It supports all of the standard functionality by which external code can interact with this
     * Kairos network, but all [activated][KairosNetwork.activateSpec] [BuildSpec]s are bound as
     * children to the [BuildScope], such that when the [BuildScope] is destroyed, all children are
     * also destroyed.
     */
    val kairosNetwork: KairosNetwork
}

/** Returns a [MutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <T> HasNetwork.MutableEvents(name: NameTag? = null): MutableEvents<T> =
    MutableEvents(kairosNetwork, name)

/** Returns a [MutableState] with initial state [initialValue]. */
@ExperimentalKairosApi
fun <T> HasNetwork.MutableState(initialValue: T, name: NameTag? = null): MutableState<T> =
    MutableState(kairosNetwork, initialValue, name)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> HasNetwork.CoalescingMutableEvents(
    initialValue: Out,
    name: NameTag? = null,
    coalesce: KairosScope.(old: Out, new: In) -> Out,
): CoalescingMutableEvents<In, Out> =
    CoalescingMutableEvents(kairosNetwork, initialValue, name, coalesce)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> HasNetwork.CoalescingMutableEvents(
    getInitialValue: KairosScope.() -> Out,
    name: NameTag? = null,
    coalesce: KairosScope.(old: Out, new: In) -> Out,
): CoalescingMutableEvents<In, Out> =
    CoalescingMutableEvents(kairosNetwork, getInitialValue, name, coalesce)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <T> HasNetwork.ConflatedMutableEvents(name: NameTag? = null): CoalescingMutableEvents<T, T> =
    ConflatedMutableEvents(kairosNetwork, name)
