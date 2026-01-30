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

import com.android.systemui.kairos.internal.store.HashMapK
import com.android.systemui.kairos.internal.store.MapK
import com.android.systemui.kairos.internal.store.MutableArrayMapK
import com.android.systemui.kairos.internal.store.MutableMapK
import com.android.systemui.kairos.internal.store.StoreEntry
import com.android.systemui.kairos.internal.util.hashString
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.appendNames
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.maybeOf
import com.android.systemui.kairos.util.plus

internal open class StateImpl<out A>(
    val nameData: NameData,
    val changes: EventsImpl<A>,
    val store: StateStore<A>,
) {

    init {
        nameData.forceInit()
    }

    fun getCurrentWithEpoch(evalScope: EvalScope): Pair<A, Long> =
        store.getCurrentWithEpoch(evalScope)

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal sealed class StateDerived<A> : CachedStateStore<A>() {

    private var invalidatedEpoch = Long.MIN_VALUE

    protected var validatedEpoch = Long.MIN_VALUE
        private set

    protected var cache: Any? = EmptyCache
        private set

    private val transactionCache = TransactionCache<Lazy<Pair<A, Long>>>()

    override fun getCurrentWithEpoch(evalScope: EvalScope): Pair<A, Long> =
        transactionCache.getOrPut(evalScope) { evalScope.deferAsync { pull(evalScope) } }.value

    fun pull(evalScope: EvalScope): Pair<A, Long> {
        @Suppress("UNCHECKED_CAST")
        val result =
            recalc(evalScope)?.let { (newValue, epoch) ->
                newValue.also {
                    if (epoch > validatedEpoch) {
                        validatedEpoch = epoch
                        if (cache != newValue) {
                            cache = newValue
                            invalidatedEpoch = epoch
                        }
                    }
                }
            } ?: (cache as A)
        return result to invalidatedEpoch
    }

    fun getCachedUnsafe(): Maybe<A> {
        @Suppress("UNCHECKED_CAST")
        return if (cache == EmptyCache) maybeOf() else Maybe.present(cache as A)
    }

    protected abstract fun recalc(evalScope: EvalScope): Pair<A, Long>?

    override fun setCacheFromPush(value: A, epoch: Long) {
        cache = value
        validatedEpoch = epoch + 1
        invalidatedEpoch = epoch + 1
    }

    private data object EmptyCache
}

internal sealed class StateStore<out S> {
    abstract fun getCurrentWithEpoch(evalScope: EvalScope): Pair<S, Long>
}

internal sealed class CachedStateStore<S> : StateStore<S>() {
    abstract fun setCacheFromPush(value: S, epoch: Long)
}

internal class StateSource<S>(init: Lazy<S>, val nameData: NameData) : CachedStateStore<S>() {
    constructor(init: S, nameData: NameData) : this(lazyOf(init), nameData)

    init {
        nameData.forceInit()
    }

    private val transactionCache = TransactionCache<Pair<S, Long>>()

    var upstreamConnection: NodeConnection<S>? = null

    private var _current: Lazy<S> = init

    private var writeEpoch = 0L

    override fun getCurrentWithEpoch(evalScope: EvalScope): Pair<S, Long> =
        transactionCache.getOrPut(evalScope) { _current.value to writeEpoch }

    override fun setCacheFromPush(value: S, epoch: Long) {
        _current = lazyOf(value)
        writeEpoch = epoch + 1
    }

    override fun toString(): String =
        "StateImpl(nameTag=$nameData, current=$_current, writeEpoch=$writeEpoch)"

    fun getStorageUnsafe(): Maybe<S> =
        if (_current.isInitialized()) Maybe.present(_current.value) else maybeOf()

    fun kill() {
        upstreamConnection = null
    }

    fun schedule(logIndent: Int, evalScope: EvalScope) {
        // Note: this *relies* on the calm node (created in [activatedStateSource]) querying the
        //  current value of this state, thus caching it within the current transaction
        upstreamConnection!!.getPushEvent(logIndent, evalScope)
    }
}

internal fun <A> constState(nameData: NameData, init: A): StateImpl<A> =
    StateImpl(nameData, neverImpl, StateSource(init, nameData))

internal inline fun <A> activatedStateSource(
    nameData: NameData,
    evalScope: EvalScope,
    crossinline getChanges: EvalScope.() -> EventsImpl<A>,
    init: Lazy<A>,
): StateImpl<A> {
    val store = StateSource(init, nameData)
    val calm = distinctChanges(getChanges, nameData + "calm", store)
    evalScope.scheduleOutput(
        OneShot(nameData + "activateState") {
            calm.activate(evalScope = this, downstream = Schedulable.S(store))?.let {
                (connection, needsEval) ->
                store.upstreamConnection = connection
                if (needsEval) {
                    store.schedule(0, this)
                }
            }
        }
    )
    return StateImpl(nameData, calm, store)
}

internal inline fun <A> distinctChanges(
    crossinline getUpstream: EvalScope.() -> EventsImpl<A>,
    nameData: NameData,
    state: CachedStateStore<A>,
): EventsImpl<A> {
    val newValues =
        mapImpl(getUpstream, nameData + "newValues") { new, _ ->
                val (current, _) = state.getCurrentWithEpoch(evalScope = this)
                if (new != current) {
                    state.setCacheFromPush(new, epoch)
                    maybeOf(new)
                } else {
                    maybeOf()
                }
            }
            // cache this for consistency: it is impure due to setCacheFromPush
            .cached(nameData.appendNames("newValues", "cached"))
    return filterPresentImpl(nameData) { newValues }
}

internal fun <A, B> mapStateImplCheap(
    stateImpl: Init<StateImpl<A>>,
    nameData: NameData,
    transform: EvalScope.(A) -> B,
): StateImpl<B> =
    StateImpl(
        nameData,
        changes =
            mapImpl({ stateImpl.connect(this).changes }, nameData + "mappedCheapChanges") { it, _ ->
                transform(it)
            },
        store = DerivedMapCheap(nameData, stateImpl, transform),
    )

internal class DerivedMapCheap<A, B>(
    val nameData: NameData,
    val upstream: Init<StateImpl<A>>,
    private val transform: EvalScope.(A) -> B,
) : StateStore<B>() {

    init {
        nameData.forceInit()
    }

    override fun getCurrentWithEpoch(evalScope: EvalScope): Pair<B, Long> {
        val (a, epoch) = upstream.connect(evalScope).getCurrentWithEpoch(evalScope)
        return evalScope.transform(a) to epoch
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal fun <A, B> mapStateImpl(
    stateImpl: InitScope.() -> StateImpl<A>,
    nameData: NameData,
    transform: EvalScope.(A) -> B,
): StateImpl<B> {
    val store = DerivedMap(nameData, stateImpl, transform)
    val upstream =
        mapImpl({ stateImpl().changes }, nameData + "mappedChanges") { it, _ -> transform(it) }
            .cached(nameData.appendNames("mappedChanges", "cached"))
    val mappedChanges: EventsImpl<B> = distinctChanges({ upstream }, nameData + "calm", store)
    return StateImpl(nameData, mappedChanges, store)
}

internal class DerivedMap<A, B>(
    val nameData: NameData,
    val upstream: InitScope.() -> StateImpl<A>,
    private val transform: EvalScope.(A) -> B,
) : StateDerived<B>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<B, Long>? {
        val (a, epoch) = evalScope.upstream().getCurrentWithEpoch(evalScope)
        return if (epoch > validatedEpoch) {
            evalScope.transform(a) to epoch
        } else {
            null
        }
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal fun <A> flattenStateImpl(
    stateImpl: InitScope.() -> StateImpl<StateImpl<A>>,
    nameData: NameData,
): StateImpl<A> {
    // emits the current value of the new inner state, when that state is emitted
    val switchEvents: EventsImpl<A> =
        mapImpl({ stateImpl().changes }, nameData + "switchEvents") { newInner, _ ->
            newInner.getCurrentWithEpoch(this).first
        }
    // emits the new value of the new inner state when that state is emitted, or
    // falls back to the current value if a new state is *not* being emitted this
    // transaction
    val innerChanges: EventsImpl<EventsImpl<A>> =
        mapImpl({ stateImpl().changes }, nameData + "innerChanges") { newInner, _ ->
            mergeNodes(
                nameData + { "innerChanges[inner=${newInner.nameData}]" },
                { switchEvents },
                { newInner.changes },
            ) { _, new ->
                new
            }
        }
    val switchedChanges: EventsImpl<A> =
        switchPromptImplSingle(
            nameData + "switchedChanges",
            getStorage = { stateImpl().getCurrentWithEpoch(evalScope = this).first.changes },
            getPatches = { innerChanges },
        )
    val store: DerivedFlatten<A> = DerivedFlatten(nameData, stateImpl)
    return StateImpl(
        nameData,
        distinctChanges({ switchedChanges }, nameData + "calm", store),
        store,
    )
}

internal class DerivedFlatten<A>(
    val nameData: NameData,
    val upstream: InitScope.() -> StateImpl<StateImpl<A>>,
) : StateDerived<A>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<A, Long> {
        val (inner, epoch0) = evalScope.upstream().getCurrentWithEpoch(evalScope)
        val (a, epoch1) = inner.getCurrentWithEpoch(evalScope)
        return a to maxOf(epoch0, epoch1)
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal fun <A, B> flatMapStateImpl(
    stateImpl: InitScope.() -> StateImpl<A>,
    nameData: NameData,
    transform: EvalScope.(A) -> StateImpl<B>,
): StateImpl<B> {
    val mapped = mapStateImpl(stateImpl, nameData + "mapStatePreFlatten", transform)
    return flattenStateImpl({ mapped }, nameData)
}

internal fun <A, B, Z> zipStates(
    nameData: NameData,
    l1: Init<StateImpl<A>>,
    l2: Init<StateImpl<B>>,
    transform: EvalScope.(A, B) -> Z,
): StateImpl<Z> {
    val zipped =
        zipStateList(
            nameData + "zippedList",
            2,
            init(nameData) { listOf(l1.connect(this), l2.connect(this)) },
        )
    return mapStateImpl({ zipped }, nameData) {
        @Suppress("UNCHECKED_CAST") transform(it[0] as A, it[1] as B)
    }
}

internal fun <A, B, C, Z> zipStates(
    nameData: NameData,
    l1: Init<StateImpl<A>>,
    l2: Init<StateImpl<B>>,
    l3: Init<StateImpl<C>>,
    transform: EvalScope.(A, B, C) -> Z,
): StateImpl<Z> {
    val zipped =
        zipStateList(
            nameData + "zippedList",
            3,
            init(nameData) { listOf(l1.connect(this), l2.connect(this), l3.connect(this)) },
        )
    return mapStateImpl({ zipped }, nameData) {
        @Suppress("UNCHECKED_CAST") transform(it[0] as A, it[1] as B, it[2] as C)
    }
}

internal fun <A, B, C, D, Z> zipStates(
    nameData: NameData,
    l1: Init<StateImpl<A>>,
    l2: Init<StateImpl<B>>,
    l3: Init<StateImpl<C>>,
    l4: Init<StateImpl<D>>,
    transform: EvalScope.(A, B, C, D) -> Z,
): StateImpl<Z> {
    val zipped =
        zipStateList(
            nameData + "zippedList",
            4,
            init(nameData) {
                listOf(l1.connect(this), l2.connect(this), l3.connect(this), l4.connect(this))
            },
        )
    return mapStateImpl({ zipped }, nameData) {
        @Suppress("UNCHECKED_CAST") transform(it[0] as A, it[1] as B, it[2] as C, it[3] as D)
    }
}

internal fun <A, B, C, D, E, Z> zipStates(
    nameData: NameData,
    l1: Init<StateImpl<A>>,
    l2: Init<StateImpl<B>>,
    l3: Init<StateImpl<C>>,
    l4: Init<StateImpl<D>>,
    l5: Init<StateImpl<E>>,
    transform: EvalScope.(A, B, C, D, E) -> Z,
): StateImpl<Z> {
    val zipped =
        zipStateList(
            nameData + "zippedList",
            5,
            init(nameData) {
                listOf(
                    l1.connect(this),
                    l2.connect(this),
                    l3.connect(this),
                    l4.connect(this),
                    l5.connect(this),
                )
            },
        )
    return mapStateImpl({ zipped }, nameData) {
        @Suppress("UNCHECKED_CAST")
        transform(it[0] as A, it[1] as B, it[2] as C, it[3] as D, it[4] as E)
    }
}

internal fun <K, V> zipStateMap(
    nameData: NameData,
    numStates: Int,
    states: Init<Map<K, StateImpl<V>>>,
): StateImpl<Map<K, V>> =
    zipStates(
        nameData,
        numStates = numStates,
        states = init(nameData) { states.connect(this).asIterable() },
        storeFactory = HashMapK.Factory(),
    )

internal fun <V> zipStateList(
    nameData: NameData,
    numStates: Int,
    states: Init<List<StateImpl<V>>>,
): StateImpl<List<V>> {
    val zipped =
        zipStates(
            nameData,
            numStates = numStates,
            states = init(nameData) { states.connect(this).asIterableWithIndex() },
            storeFactory = MutableArrayMapK.Factory(),
        )
    // Like mapCheap, but with caching (or like map, but without the calm changes, as they are not
    // necessary).
    return StateImpl(
        nameData,
        changes =
            mapImpl({ zipped.changes }, nameData + "changes") { arrayStore, _ ->
                arrayStore.values.toList()
            },
        DerivedMap(
            nameData,
            upstream = { zipped },
            transform = { arrayStore -> arrayStore.values.toList() },
        ),
    )
}

internal fun <W, K, A> zipStates(
    nameData: NameData,
    numStates: Int,
    states: Init<Iterable<Map.Entry<K, StateImpl<A>>>>,
    storeFactory: MutableMapK.Factory<W, K>,
): StateImpl<MutableMapK<W, K, A>> {
    if (numStates == 0) {
        return constState(nameData, storeFactory.create(0))
    }
    val stateStore = DerivedZipped(nameData, numStates, states, storeFactory)
    // No need for calm; invariant ensures that changes will only emit when there's a difference
    val mergedChanges: EventsImpl<MapK<W, K, PullNode<A>>> =
        switchDeferredImpl(
            nameData + "mergedChanges",
            getStorage = {
                states
                    .connect(this)
                    .asSequence()
                    .map { (k, v) -> StoreEntry(k, v.changes) }
                    .asIterable()
            },
            getPatches = { neverImpl },
            storeFactory = storeFactory,
        )
    val changes: EventsImpl<MutableMapK<W, K, A>> =
        mapImpl({ mergedChanges }, nameData + "changes") { patch, logIndent ->
                val muxStore = storeFactory.create<A>(numStates)
                states.connect(this).forEach { (k, state) ->
                    muxStore[k] =
                        if (patch.contains(k)) {
                            patch.getValue(k).getPushEvent(logIndent, evalScope = this@mapImpl)
                        } else {
                            state.getCurrentWithEpoch(evalScope = this@mapImpl).first
                        }
                }
                // Read the current value so that it is cached in this transaction and won't be
                // clobbered by the cache write
                stateStore.getCurrentWithEpoch(evalScope = this)
                muxStore.also { stateStore.setCacheFromPush(it, epoch) }
            }
            .cached(nameData + "cached")
    return StateImpl(nameData, changes, stateStore)
}

internal class DerivedZipped<W, K, A>(
    val nameData: NameData,
    private val upstreamSize: Int,
    val upstream: Init<Iterable<Map.Entry<K, StateImpl<A>>>>,
    private val storeFactory: MutableMapK.Factory<W, K>,
) : StateDerived<MutableMapK<W, K, A>>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<MutableMapK<W, K, A>, Long> {
        var newEpoch = 0L
        val store = storeFactory.create<A>(upstreamSize)
        for ((key, value) in upstream.connect(evalScope)) {
            val (a, epoch) = value.getCurrentWithEpoch(evalScope)
            newEpoch = maxOf(newEpoch, epoch)
            store[key] = a
        }
        return store to newEpoch
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal fun <A> zipStates(
    nameData: NameData,
    numStates: Int,
    states: Init<List<StateImpl<A>>>,
): StateImpl<List<A>> =
    if (numStates <= 0) {
        constState(nameData, emptyList())
    } else {
        zipStateList(nameData, numStates, states)
    }
