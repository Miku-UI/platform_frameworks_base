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

import com.android.systemui.kairos.internal.CompletableLazy
import com.android.systemui.kairos.internal.EventsImpl
import com.android.systemui.kairos.internal.Init
import com.android.systemui.kairos.internal.InitScope
import com.android.systemui.kairos.internal.InputNode
import com.android.systemui.kairos.internal.Network
import com.android.systemui.kairos.internal.NoScope
import com.android.systemui.kairos.internal.activated
import com.android.systemui.kairos.internal.cached
import com.android.systemui.kairos.internal.constInit
import com.android.systemui.kairos.internal.init
import com.android.systemui.kairos.internal.mapImpl
import com.android.systemui.kairos.internal.neverImpl
import com.android.systemui.kairos.internal.util.hashString
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.NameTag
import com.android.systemui.kairos.util.NameTaggingDisabled
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toMaybe
import com.android.systemui.kairos.util.toNameData
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * A series of values of type [A] available at discrete points in time.
 *
 * [Events] follow these rules:
 * 1. Within a single Kairos network transaction, an [Events] instance will only emit *once*.
 * 2. The order that different [Events] instances emit values within a transaction is undefined, and
 *    are conceptually *simultaneous*.
 * 3. [Events] emissions are *ephemeral* and do not last beyond the transaction they are emitted,
 *    unless explicitly [observed][BuildScope.observe] or [held][StateScope.holdState] as a [State].
 */
sealed class Events<out A> {
    companion object {
        /** An [Events] with no values. */
        val empty: Events<Nothing> = EmptyEvents
    }
}

/** An [Events] with no values. */
val emptyEvents: Events<Nothing> = Events.empty

/**
 * A forward-reference to an [Events]. Useful for recursive definitions.
 *
 * This reference can be used like a standard [Events], but will throw an error if its [loopback] is
 * unset before it is [observed][BuildScope.observe].
 *
 * @sample com.android.systemui.kairos.KairosSamples.eventsLoop
 */
class EventsLoop<A> : Events<A>() {

    private val nameData: NameData = NameTaggingDisabled

    private val deferred = CompletableLazy<Events<A>>()

    internal val init: Init<EventsImpl<A>> =
        init(nameData) { deferred.value.init.connect(initScope = this) }

    /**
     * The [Events] this reference is referring to. Must be set before this [EventsLoop] is
     * [observed][BuildScope.observe].
     */
    var loopback: Events<A>? = null
        set(value) {
            value?.let {
                check(!deferred.isInitialized()) { "EventsLoop.loopback has already been set." }
                deferred.setValue(value)
                field = value
            }
        }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Events<A> = this

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Events<A>) {
        loopback = value
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

/**
 * Returns an [Events] that acts as a deferred-reference to the [Events] produced by this [Lazy].
 *
 * When the returned [Events] is accessed by the Kairos network, the [Lazy]'s [value][Lazy.value]
 * will be queried and used.
 *
 * Useful for recursive definitions.
 *
 * ```
 *   fun <A> Lazy<Events<A>>.defer() = deferredEvents { value }
 * ```
 *
 * @see deferredEvents
 */
fun <A> Lazy<Events<A>>.defer(): Events<A> = deferInline { value }

/**
 * Returns an [Events] that acts as a deferred-reference to the [Events] produced by this
 * [DeferredValue].
 *
 * When the returned [Events] is accessed by the Kairos network, the [DeferredValue] will be queried
 * and used.
 *
 * Useful for recursive definitions.
 *
 * ```
 *   fun <A> DeferredValue<Events<A>>.defer() = deferredEvents { get() }
 * ```
 *
 * @see deferredEvents
 */
fun <A> DeferredValue<Events<A>>.defer(): Events<A> = deferInline { unwrapped.value }

/**
 * Returns an [Events] that acts as a deferred-reference to the [Events] produced by [block].
 *
 * When the returned [Events] is accessed by the Kairos network, [block] will be invoked and the
 * returned [Events] will be used.
 *
 * Useful for recursive definitions.
 */
fun <A> deferredEvents(block: KairosScope.() -> Events<A>): Events<A> = deferInline {
    NoScope.block()
}

/**
 * Returns an [Events] that contains only the
 * [present][com.android.systemui.kairos.util.Maybe.present] results of applying [transform] to each
 * value of the original [Events].
 *
 * @sample com.android.systemui.kairos.KairosSamples.mapMaybe
 * @see mapNotNull
 */
fun <A, B> Events<A>.mapMaybe(transform: TransactionScope.(A) -> Maybe<B>): Events<B> =
    mapMaybe(nameTag("Events.mapMaybe").toNameData("Events.mapMaybe"), transform)

internal fun <A, B> Events<A>.mapMaybe(
    nameData: NameData,
    transform: TransactionScope.(A) -> Maybe<B>,
): Events<B> = map(nameData + "map", transform).filterPresent(nameData)

/**
 * Returns an [Events] that contains only the non-null results of applying [transform] to each value
 * of the original [Events].
 *
 * ```
 *  fun <A> Events<A>.mapNotNull(transform: TransactionScope.(A) -> B?): Events<B> =
 *      mapMaybe { if (it == null) absent else present(it) }
 * ```
 *
 * @see mapMaybe
 */
fun <A, B> Events<A>.mapNotNull(transform: TransactionScope.(A) -> B?): Events<B> =
    mapNotNull(nameTag("Events.mapNotNull").toNameData("Events.mapNotNull"), transform)

internal fun <A, B> Events<A>.mapNotNull(
    nameData: NameData,
    transform: TransactionScope.(A) -> B?,
): Events<B> = mapMaybe(nameData) { transform(it).toMaybe() }

/**
 * Returns an [Events] containing the results of applying [transform] to each value of the original
 * [Events].
 *
 * @sample com.android.systemui.kairos.KairosSamples.mapEvents
 */
fun <A, B> Events<A>.map(transform: TransactionScope.(A) -> B): Events<B> {
    return map(nameTag("Events.map").toNameData("Events.map"), transform)
}

internal fun <A, B> Events<A>.map(
    nameData: NameData,
    transform: TransactionScope.(A) -> B,
): Events<B> {
    val mapped: EventsImpl<B> =
        mapImpl({ init.connect(initScope = this) }, nameData) { a, _ -> transform(a) }
    return EventsInit(constInit(nameData, mapped.cached(nameData + "cached")))
}

/**
 * Like [map], but the emission is not cached during the transaction. Use only if [transform] is
 * fast and pure. If you are unsure if you need this, then you should prefer [map].
 *
 * @sample com.android.systemui.kairos.KairosSamples.mapCheap
 * @see map
 */
fun <A, B> Events<A>.mapCheap(transform: TransactionScope.(A) -> B): Events<B> =
    mapCheap(nameTag("Events.mapCheap").toNameData("Events.mapCheap"), transform)

internal fun <A, B> Events<A>.mapCheap(
    nameData: NameData,
    transform: TransactionScope.(A) -> B,
): Events<B> =
    EventsInit(
        constInit(
            nameData,
            mapImpl({ init.connect(initScope = this) }, nameData) { a, _ -> transform(a) },
        )
    )

/**
 * Returns an [Events] that invokes [action] before each value of the original [Events] is emitted.
 * Useful for logging and debugging.
 *
 * ```
 *   fun <A> Events<A>.onEach(action: TransactionScope.(A) -> Unit): Events<A> =
 *       map { it.also { action(it) } }
 * ```
 *
 * Note that the side effects performed in [onEach] are only performed while the resulting [Events]
 * is connected to an output of the Kairos network. If your goal is to reliably perform side effects
 * in response to an [Events], use the output combinators available in [BuildScope], such as
 * [BuildScope.toSharedFlow] or [BuildScope.observe].
 */
fun <A> Events<A>.onEach(action: TransactionScope.(A) -> Unit): Events<A> =
    onEach(nameTag("Events.onEach").toNameData("Events.onEach"), action)

internal fun <A> Events<A>.onEach(
    nameData: NameData,
    action: TransactionScope.(A) -> Unit,
): Events<A> = map(nameData) { it.also { action(it) } }

/**
 * Splits an [Events] of pairs into a pair of [Events], where each returned [Events] emits half of
 * the original.
 *
 * ```
 *   fun <A, B> Events<Pair<A, B>>.unzip(): Pair<Events<A>, Events<B>> {
 *       val lefts = map { it.first }
 *       val rights = map { it.second }
 *       return lefts to rights
 *   }
 * ```
 */
fun <A, B> Events<Pair<A, B>>.unzip(): Pair<Events<A>, Events<B>> =
    unzip(nameTag("Events.unzip").toNameData("Events.unzip"))

internal fun <A, B> Events<Pair<A, B>>.unzip(nameData: NameData): Pair<Events<A>, Events<B>> {
    val lefts = map(nameData + "getFirst") { it.first }
    val rights = map(nameData + "getSecond") { it.second }
    return lefts to rights
}

/**
 * A mutable [Events] that provides the ability to [emit] values to the network, handling
 * backpressure by coalescing all emissions into batches.
 *
 * @see KairosNetwork.coalescingMutableEvents
 */
class CoalescingMutableEvents<in In, Out>
private constructor(
    internal val nameData: NameData,
    internal val coalesce: (old: Lazy<Out>, new: In) -> Out,
    private val getInitialValue: () -> Out,
    internal val networkRef: AtomicReference<Network?>,
    internal val impl: InputNode<Out>,
) : Events<Out>() {

    init {
        nameData.forceInit()
    }

    private val storage = AtomicReference(false to lazy { getInitialValue() })

    private constructor(
        nameData: NameData,
        coalesce: (old: Lazy<Out>, new: In) -> Out,
        getInitialValue: () -> Out,
        networkRef: AtomicReference<Network?>,
    ) : this(
        nameData,
        coalesce,
        getInitialValue,
        networkRef,
        InputNode(
            nameData,
            activate = { check(networkRef.compareAndSet(null, network)) { "Network mismatch" } },
            deactivate = { check(networkRef.compareAndSet(network, null)) { "Network mismatch" } },
        ),
    )

    internal constructor(
        nameData: NameData,
        coalesce: (old: Lazy<Out>, new: In) -> Out,
        getInitialValue: () -> Out,
        network: Network,
    ) : this(nameData, coalesce, network, getInitialValue, InputNode(nameData))

    internal constructor(
        nameData: NameData,
        coalesce: (old: Lazy<Out>, new: In) -> Out,
        getInitialValue: () -> Out,
    ) : this(nameData, coalesce, getInitialValue, AtomicReference(null))

    internal constructor(
        nameData: NameData,
        coalesce: (old: Lazy<Out>, new: In) -> Out,
        network: Network,
        getInitialValue: () -> Out,
        inputNode: InputNode<Out>,
    ) : this(nameData, coalesce, getInitialValue, AtomicReference(network), inputNode)

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"

    /**
     * Inserts [value] into the current batch, enqueueing it for emission from this [Events] if not
     * already pending.
     *
     * Backpressure occurs when [emit] is called while the Kairos network is currently in a
     * transaction; if called multiple times, then emissions will be coalesced into a single batch
     * that is then processed when the network is ready.
     */
    fun emit(value: In) {
        val network = networkRef.get() ?: return
        val (scheduled, _) =
            storage.getAndUpdate { (_, batch) -> true to lazyOf(coalesce(batch, value)) }
        if (!scheduled) {
            @Suppress("DeferredResultUnused")
            network.transaction("CoalescingMutableEvents.emit") {
                val (_, batch) = storage.getAndSet(false to lazy { getInitialValue() })
                impl.visit(this, batch.value)
            }
        }
    }
}

/**
 * A mutable [Events] that provides the ability to [emit] values to the network, handling
 * backpressure by suspending the emitter.
 *
 * @see KairosNetwork.coalescingMutableEvents
 */
class MutableEvents<T>
private constructor(
    internal val nameData: NameData,
    private val networkRef: AtomicReference<Network?>,
    internal val impl: InputNode<T>,
) : Events<T>() {

    init {
        nameData.forceInit()
    }

    private val storage = AtomicReference<Job?>(null)

    private constructor(
        nameData: NameData,
        networkRef: AtomicReference<Network?>,
    ) : this(
        nameData,
        networkRef,
        InputNode(
            nameData,
            activate = { check(networkRef.compareAndSet(null, network)) { "Network mismatch" } },
            deactivate = { check(networkRef.compareAndSet(network, null)) { "Network mismatch" } },
        ),
    )

    internal constructor(nameData: NameData) : this(nameData, AtomicReference(null))

    internal constructor(
        nameData: NameData,
        network: Network,
        inputNode: InputNode<T>,
    ) : this(nameData, AtomicReference(network), inputNode)

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"

    /**
     * Emits a [value] to this [Events], suspending the caller until the Kairos transaction
     * containing the emission has completed.
     */
    suspend fun emit(value: T) {
        val network = networkRef.get() ?: return
        coroutineScope {
            var jobOrNull: Job? = null
            val newEmit =
                launch(start = CoroutineStart.LAZY) {
                    jobOrNull?.join()
                    jobOrNull = null
                    network.transaction("MutableEvents.emit") { impl.visit(this, value) }.await()
                }
            jobOrNull = storage.getAndSet(newEmit)
            newEmit.join()
            storage.compareAndSet(newEmit, null)
        }
    }
}

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
fun <T> ConflatedMutableEvents(name: NameTag? = null): CoalescingMutableEvents<T, T> =
    CoalescingMutableEvents(
        name.toNameData("CoalescingMutableEvents"),
        coalesce = { _, new -> new },
        { error("WTF: init value accessed for conflatedMutableEvents") },
    )

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
fun <In, Out> CoalescingMutableEvents(
    initialValue: Out,
    name: NameTag? = null,
    coalesce: KairosScope.(old: Out, new: In) -> Out,
): CoalescingMutableEvents<In, Out> =
    CoalescingMutableEvents(
        name.toNameData("CoalescingMutableEvents"),
        coalesce = { old, new -> NoScope.coalesce(old.value, new) },
        { initialValue },
    )

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
fun <In, Out> CoalescingMutableEvents(
    getInitialValue: KairosScope.() -> Out,
    name: NameTag? = null,
    coalesce: KairosScope.(old: Out, new: In) -> Out,
): CoalescingMutableEvents<In, Out> =
    CoalescingMutableEvents(
        name.toNameData("CoalescingMutableEvents"),
        coalesce = { old, new -> NoScope.coalesce(old.value, new) },
        { NoScope.getInitialValue() },
    )

/** Returns a [MutableEvents] that can emit values into this [KairosNetwork]. */
fun <T> MutableEvents(name: NameTag? = null): MutableEvents<T> =
    MutableEvents(name.toNameData("MutableEvents"))

private data object EmptyEvents : Events<Nothing>()

internal class EventsInit<out A>(val init: Init<EventsImpl<A>>) : Events<A>() {
    override fun toString(): String = "${this::class.simpleName}@$hashString"
}

internal val <A> Events<A>.init: Init<EventsImpl<A>>
    get() =
        when (this) {
            is EmptyEvents -> constInit(nameTag("neverEvents").toNameData("neverEvents"), neverImpl)
            is EventsInit -> init
            is EventsLoop -> init
            is CoalescingMutableEvents<*, A> -> constInit(nameData, impl.activated())
            is MutableEvents -> constInit(nameData, impl.activated())
        }

private inline fun <A> deferInline(crossinline block: InitScope.() -> Events<A>): Events<A> =
    EventsInit(init(NameTaggingDisabled) { block().init.connect(initScope = this) })
