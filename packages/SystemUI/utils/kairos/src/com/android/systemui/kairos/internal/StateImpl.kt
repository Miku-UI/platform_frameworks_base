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

import com.android.systemui.kairos.internal.store.ArrayMapK
import com.android.systemui.kairos.internal.store.FastIterable
import com.android.systemui.kairos.internal.store.HashMapK
import com.android.systemui.kairos.internal.store.MapK
import com.android.systemui.kairos.internal.store.MutableMapK
import com.android.systemui.kairos.internal.store.asArrayMapK
import com.android.systemui.kairos.internal.store.asFastIterable
import com.android.systemui.kairos.internal.store.asHashMapK
import com.android.systemui.kairos.internal.store.map
import com.android.systemui.kairos.internal.util.fastForEach
import com.android.systemui.kairos.internal.util.fastForEachIndexed
import com.android.systemui.kairos.internal.util.hashString
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.appendNames
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.maybeOf
import com.android.systemui.kairos.util.plus
import java.util.concurrent.atomic.AtomicReference

internal open class StateImpl<out A>(
    val nameData: NameData,
    val changes: EventsImpl<A>,
    val store: StateStore<A>,
) {

    init {
        nameData.forceInit()
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun getCurrentWithEpoch(evalScope: EvalScope): Pair<A, Long> =
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
        return if (cache === EmptyCache) maybeOf() else Maybe.present(cache as A)
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
    // emits the new value of the new inner state when that state is emitted, or falls back to the
    // current value if a new state is *not* being emitted this transaction
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
    sa: Init<StateImpl<A>>,
    sb: Init<StateImpl<B>>,
    transform: EvalScope.(A, B) -> Z,
): StateImpl<Z> {
    val stateStore = DerivedZipped2(nameData, sa, sb, transform)
    @Suppress("UNCHECKED_CAST")
    val mergedChanges: EventsImpl<Z> =
        switchDeferredImpl(
            nameData + "mergedChanges",
            getStorage = {
                listOf(sa.connect(this).changes, sb.connect(this).changes).asIterableWithIndex()
            },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) {
            val results = it.asArrayMapK()
            val a =
                if (results.containsKey(0)) {
                    results.getValue(0).getPushEvent(logIndent = 0, evalScope = this) as A
                } else {
                    sa.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val b =
                if (results.containsKey(1)) {
                    results.getValue(1).getPushEvent(logIndent = 0, evalScope = this) as B
                } else {
                    sb.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            transform(a, b)
        }
    val calm = distinctChanges({ mergedChanges }, nameData + "calm", stateStore)
    return StateImpl(nameData, calm, stateStore)
}

internal fun <A, B, C, Z> zipStates(
    nameData: NameData,
    sa: Init<StateImpl<A>>,
    sb: Init<StateImpl<B>>,
    sc: Init<StateImpl<C>>,
    transform: EvalScope.(A, B, C) -> Z,
): StateImpl<Z> {
    val stateStore = DerivedZipped3(nameData, sa, sb, sc, transform)
    @Suppress("UNCHECKED_CAST")
    val mergedChanges: EventsImpl<Z> =
        switchDeferredImpl(
            nameData + "mergedChanges",
            getStorage = {
                listOf(sa.connect(this).changes, sb.connect(this).changes, sc.connect(this).changes)
                    .asIterableWithIndex()
            },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) {
            val results = it.asArrayMapK()
            val a =
                if (results.containsKey(0)) {
                    results.getValue(0).getPushEvent(logIndent = 0, evalScope = this) as A
                } else {
                    sa.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val b =
                if (results.containsKey(1)) {
                    results.getValue(1).getPushEvent(logIndent = 0, evalScope = this) as B
                } else {
                    sb.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val c =
                if (results.containsKey(2)) {
                    results.getValue(2).getPushEvent(logIndent = 0, evalScope = this) as C
                } else {
                    sc.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            transform(a, b, c)
        }
    val calm = distinctChanges({ mergedChanges }, nameData + "calm", stateStore)
    return StateImpl(nameData, calm, stateStore)
}

internal fun <A, B, C, D, Z> zipStates(
    nameData: NameData,
    sa: Init<StateImpl<A>>,
    sb: Init<StateImpl<B>>,
    sc: Init<StateImpl<C>>,
    sd: Init<StateImpl<D>>,
    transform: EvalScope.(A, B, C, D) -> Z,
): StateImpl<Z> {
    val stateStore = DerivedZipped4(nameData, sa, sb, sc, sd, transform)
    @Suppress("UNCHECKED_CAST")
    val mergedChanges: EventsImpl<Z> =
        switchDeferredImpl(
            nameData + "mergedChanges",
            getStorage = {
                listOf(
                        sa.connect(this).changes,
                        sb.connect(this).changes,
                        sc.connect(this).changes,
                        sd.connect(this).changes,
                    )
                    .asIterableWithIndex()
            },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) {
            val results = it.asArrayMapK()
            val a =
                if (results.containsKey(0)) {
                    results.getValue(0).getPushEvent(logIndent = 0, evalScope = this) as A
                } else {
                    sa.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val b =
                if (results.containsKey(1)) {
                    results.getValue(1).getPushEvent(logIndent = 0, evalScope = this) as B
                } else {
                    sb.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val c =
                if (results.containsKey(2)) {
                    results.getValue(2).getPushEvent(logIndent = 0, evalScope = this) as C
                } else {
                    sc.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val d =
                if (results.containsKey(3)) {
                    results.getValue(3).getPushEvent(logIndent = 0, evalScope = this) as D
                } else {
                    sd.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            transform(a, b, c, d)
        }
    val calm = distinctChanges({ mergedChanges }, nameData + "calm", stateStore)
    return StateImpl(nameData, calm, stateStore)
}

internal fun <A, B, C, D, E, Z> zipStates(
    nameData: NameData,
    sa: Init<StateImpl<A>>,
    sb: Init<StateImpl<B>>,
    sc: Init<StateImpl<C>>,
    sd: Init<StateImpl<D>>,
    se: Init<StateImpl<E>>,
    transform: EvalScope.(A, B, C, D, E) -> Z,
): StateImpl<Z> {
    val stateStore = DerivedZipped5(nameData, sa, sb, sc, sd, se, transform)
    @Suppress("UNCHECKED_CAST")
    val mergedChanges: EventsImpl<Z> =
        switchDeferredImpl(
            nameData + "mergedChanges",
            getStorage = {
                listOf(
                        sa.connect(this).changes,
                        sb.connect(this).changes,
                        sc.connect(this).changes,
                        sd.connect(this).changes,
                        se.connect(this).changes,
                    )
                    .asIterableWithIndex()
            },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) {
            val results = it.asArrayMapK()
            val a =
                if (results.containsKey(0)) {
                    results.getValue(0).getPushEvent(logIndent = 0, evalScope = this) as A
                } else {
                    sa.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val b =
                if (results.containsKey(1)) {
                    results.getValue(1).getPushEvent(logIndent = 0, evalScope = this) as B
                } else {
                    sb.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val c =
                if (results.containsKey(2)) {
                    results.getValue(2).getPushEvent(logIndent = 0, evalScope = this) as C
                } else {
                    sc.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val d =
                if (results.containsKey(3)) {
                    results.getValue(3).getPushEvent(logIndent = 0, evalScope = this) as D
                } else {
                    sd.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val e =
                if (results.containsKey(4)) {
                    results.getValue(4).getPushEvent(logIndent = 0, evalScope = this) as E
                } else {
                    se.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            transform(a, b, c, d, e)
        }
    val calm = distinctChanges({ mergedChanges }, nameData + "calm", stateStore)
    return StateImpl(nameData, calm, stateStore)
}

internal fun <A, B, C, D, E, F, Z> zipStates(
    nameData: NameData,
    sa: Init<StateImpl<A>>,
    sb: Init<StateImpl<B>>,
    sc: Init<StateImpl<C>>,
    sd: Init<StateImpl<D>>,
    se: Init<StateImpl<E>>,
    sf: Init<StateImpl<F>>,
    transform: EvalScope.(A, B, C, D, E, F) -> Z,
): StateImpl<Z> {
    val stateStore = DerivedZipped6(nameData, sa, sb, sc, sd, se, sf, transform)
    @Suppress("UNCHECKED_CAST")
    val mergedChanges: EventsImpl<Z> =
        switchDeferredImpl(
            nameData + "mergedChanges",
            getStorage = {
                listOf(
                        sa.connect(this).changes,
                        sb.connect(this).changes,
                        sc.connect(this).changes,
                        sd.connect(this).changes,
                        se.connect(this).changes,
                        sf.connect(this).changes,
                    )
                    .asIterableWithIndex()
            },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) {
            val results = it.asArrayMapK()
            val a =
                if (results.containsKey(0)) {
                    results.getValue(0).getPushEvent(logIndent = 0, evalScope = this) as A
                } else {
                    sa.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val b =
                if (results.containsKey(1)) {
                    results.getValue(1).getPushEvent(logIndent = 0, evalScope = this) as B
                } else {
                    sb.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val c =
                if (results.containsKey(2)) {
                    results.getValue(2).getPushEvent(logIndent = 0, evalScope = this) as C
                } else {
                    sc.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val d =
                if (results.containsKey(3)) {
                    results.getValue(3).getPushEvent(logIndent = 0, evalScope = this) as D
                } else {
                    sd.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val e =
                if (results.containsKey(4)) {
                    results.getValue(4).getPushEvent(logIndent = 0, evalScope = this) as E
                } else {
                    se.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val f =
                if (results.containsKey(5)) {
                    results.getValue(5).getPushEvent(logIndent = 0, evalScope = this) as F
                } else {
                    sf.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            transform(a, b, c, d, e, f)
        }
    val calm = distinctChanges({ mergedChanges }, nameData + "calm", stateStore)
    return StateImpl(nameData, calm, stateStore)
}

internal fun <A, B, C, D, E, F, G, Z> zipStates(
    nameData: NameData,
    sa: Init<StateImpl<A>>,
    sb: Init<StateImpl<B>>,
    sc: Init<StateImpl<C>>,
    sd: Init<StateImpl<D>>,
    se: Init<StateImpl<E>>,
    sf: Init<StateImpl<F>>,
    sg: Init<StateImpl<G>>,
    transform: EvalScope.(A, B, C, D, E, F, G) -> Z,
): StateImpl<Z> {
    val stateStore = DerivedZipped7(nameData, sa, sb, sc, sd, se, sf, sg, transform)
    @Suppress("UNCHECKED_CAST")
    val mergedChanges: EventsImpl<Z> =
        switchDeferredImpl(
            nameData + "mergedChanges",
            getStorage = {
                listOf(
                        sa.connect(this).changes,
                        sb.connect(this).changes,
                        sc.connect(this).changes,
                        sd.connect(this).changes,
                        se.connect(this).changes,
                        sf.connect(this).changes,
                        sg.connect(this).changes,
                    )
                    .asIterableWithIndex()
            },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) {
            val results = it.asArrayMapK()
            val a =
                if (results.containsKey(0)) {
                    results.getValue(0).getPushEvent(logIndent = 0, evalScope = this) as A
                } else {
                    sa.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val b =
                if (results.containsKey(1)) {
                    results.getValue(1).getPushEvent(logIndent = 0, evalScope = this) as B
                } else {
                    sb.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val c =
                if (results.containsKey(2)) {
                    results.getValue(2).getPushEvent(logIndent = 0, evalScope = this) as C
                } else {
                    sc.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val d =
                if (results.containsKey(3)) {
                    results.getValue(3).getPushEvent(logIndent = 0, evalScope = this) as D
                } else {
                    sd.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val e =
                if (results.containsKey(4)) {
                    results.getValue(4).getPushEvent(logIndent = 0, evalScope = this) as E
                } else {
                    se.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val f =
                if (results.containsKey(5)) {
                    results.getValue(5).getPushEvent(logIndent = 0, evalScope = this) as F
                } else {
                    sf.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            val g =
                if (results.containsKey(6)) {
                    results.getValue(6).getPushEvent(logIndent = 0, evalScope = this) as G
                } else {
                    sg.connect(initScope = this).getCurrentWithEpoch(evalScope = this).first
                }
            transform(a, b, c, d, e, f, g)
        }
    val calm = distinctChanges({ mergedChanges }, nameData + "calm", stateStore)
    return StateImpl(nameData, calm, stateStore)
}

internal fun <K, V> zipStateMap(
    nameData: NameData,
    numStates: Int,
    states: Init<FastIterable<K, StateImpl<V>>>,
): StateImpl<Map<K, V>> {
    val storeFactory = HashMapK.Factory<K>()
    val stateStore = DerivedZippedMap(nameData, numStates, states, storeFactory)
    val changes =
        switchDeferredImpl(
                nameData + "mergedChanges",
                getStorage = { states.connect(this).map { it.changes } },
                getPatches = { neverImpl },
                storeFactory = storeFactory,
            ) { results ->
                val patch = results.asHashMapK()
                val resultMap = storeFactory.create<V>(numStates)

                states.connect(this).forEach { k, state ->
                    resultMap[k] =
                        if (patch.containsKey(k)) {
                            patch
                                .getValue(k)
                                .getPushEvent(logIndent = 0, evalScope = this@switchDeferredImpl)
                        } else {
                            state.getCurrentWithEpoch(this@switchDeferredImpl).first
                        }
                }

                resultMap.also {
                    // Read the current value so that it is cached in this transaction and won't
                    // be clobbered by the cache write
                    stateStore.getCurrentWithEpoch(this)
                    stateStore.setCacheFromPush(resultMap, epoch)
                }
            }
            .cached(nameData + "cached")
    val mappedName = nameData + "fromMapK"
    return mapStateImplCheap(
        constInit(mappedName, StateImpl(nameData, changes, stateStore)),
        mappedName,
    ) { hashMapK ->
        hashMapK.asHashMapK().storage.asMap()
    }
}

internal fun <V> zipStateList(
    nameData: NameData,
    states: Init<List<StateImpl<V>>>,
): StateImpl<List<V>> {
    val storeFactory = ArrayMapK.Factory
    val stateStore = DerivedZippedList(nameData, states)
    val changes =
        switchDeferredImpl(
                nameData + "mergedChanges",
                getStorage = { states.connect(this).map { it.changes }.asFastIterable() },
                getPatches = { neverImpl },
                storeFactory = storeFactory,
            ) { results ->
                val patch = results.asArrayMapK()
                buildList {
                        states.connect(this@switchDeferredImpl).fastForEachIndexed { idx, state ->
                            add(
                                if (patch.containsKey(idx)) {
                                    patch
                                        .getValue(idx)
                                        .getPushEvent(
                                            logIndent = 0,
                                            evalScope = this@switchDeferredImpl,
                                        )
                                } else {
                                    state.getCurrentWithEpoch(this@switchDeferredImpl).first
                                }
                            )
                        }
                    }
                    .also {
                        // Read the current value so that it is cached in this transaction and won't
                        // be clobbered by the cache write
                        stateStore.getCurrentWithEpoch(this)
                        stateStore.setCacheFromPush(it, epoch)
                    }
            }
            .cached(nameData + "cached")
    return StateImpl(nameData, changes, stateStore)
}

internal class DerivedZipped2<A, B, Z>(
    val nameData: NameData,
    val sa: Init<StateImpl<A>>,
    val sb: Init<StateImpl<B>>,
    val transform: EvalScope.(A, B) -> Z,
) : StateDerived<Z>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<Z, Long>? {
        var newEpoch = 0L

        val (a, epochA) = sa.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochA)
        val (b, epochB) = sb.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochB)

        return if (newEpoch > validatedEpoch) evalScope.transform(a, b) to newEpoch else null
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal class DerivedZipped3<A, B, C, Z>(
    val nameData: NameData,
    val sa: Init<StateImpl<A>>,
    val sb: Init<StateImpl<B>>,
    val sc: Init<StateImpl<C>>,
    val transform: EvalScope.(A, B, C) -> Z,
) : StateDerived<Z>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<Z, Long>? {
        var newEpoch = 0L

        val (a, epochA) = sa.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochA)
        val (b, epochB) = sb.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochB)
        val (c, epochC) = sc.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochC)

        return if (newEpoch > validatedEpoch) evalScope.transform(a, b, c) to newEpoch else null
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal class DerivedZipped4<A, B, C, D, Z>(
    val nameData: NameData,
    val sa: Init<StateImpl<A>>,
    val sb: Init<StateImpl<B>>,
    val sc: Init<StateImpl<C>>,
    val sd: Init<StateImpl<D>>,
    val transform: EvalScope.(A, B, C, D) -> Z,
) : StateDerived<Z>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<Z, Long>? {
        var newEpoch = 0L

        val (a, epochA) = sa.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochA)
        val (b, epochB) = sb.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochB)
        val (c, epochC) = sc.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochC)
        val (d, epochD) = sd.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochD)

        return if (newEpoch > validatedEpoch) evalScope.transform(a, b, c, d) to newEpoch else null
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal class DerivedZipped5<A, B, C, D, E, Z>(
    val nameData: NameData,
    val sa: Init<StateImpl<A>>,
    val sb: Init<StateImpl<B>>,
    val sc: Init<StateImpl<C>>,
    val sd: Init<StateImpl<D>>,
    val se: Init<StateImpl<E>>,
    val transform: EvalScope.(A, B, C, D, E) -> Z,
) : StateDerived<Z>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<Z, Long>? {
        var newEpoch = 0L

        val (a, epochA) = sa.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochA)
        val (b, epochB) = sb.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochB)
        val (c, epochC) = sc.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochC)
        val (d, epochD) = sd.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochD)
        val (e, epochE) = se.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochE)

        return if (newEpoch > validatedEpoch) {
            evalScope.transform(a, b, c, d, e) to newEpoch
        } else {
            null
        }
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal class DerivedZipped6<A, B, C, D, E, F, Z>(
    val nameData: NameData,
    val sa: Init<StateImpl<A>>,
    val sb: Init<StateImpl<B>>,
    val sc: Init<StateImpl<C>>,
    val sd: Init<StateImpl<D>>,
    val se: Init<StateImpl<E>>,
    val sf: Init<StateImpl<F>>,
    val transform: EvalScope.(A, B, C, D, E, F) -> Z,
) : StateDerived<Z>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<Z, Long>? {
        var newEpoch = 0L

        val (a, epochA) = sa.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochA)
        val (b, epochB) = sb.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochB)
        val (c, epochC) = sc.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochC)
        val (d, epochD) = sd.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochD)
        val (e, epochE) = se.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochE)
        val (f, epochF) = sf.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochF)

        return if (newEpoch > validatedEpoch) {
            evalScope.transform(a, b, c, d, e, f) to newEpoch
        } else {
            null
        }
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal class DerivedZipped7<A, B, C, D, E, F, G, Z>(
    val nameData: NameData,
    val sa: Init<StateImpl<A>>,
    val sb: Init<StateImpl<B>>,
    val sc: Init<StateImpl<C>>,
    val sd: Init<StateImpl<D>>,
    val se: Init<StateImpl<E>>,
    val sf: Init<StateImpl<F>>,
    val sg: Init<StateImpl<G>>,
    val transform: EvalScope.(A, B, C, D, E, F, G) -> Z,
) : StateDerived<Z>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<Z, Long>? {
        var newEpoch = 0L

        val (a, epochA) = sa.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochA)
        val (b, epochB) = sb.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochB)
        val (c, epochC) = sc.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochC)
        val (d, epochD) = sd.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochD)
        val (e, epochE) = se.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochE)
        val (f, epochF) = sf.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochF)
        val (g, epochG) = sg.connect(evalScope).getCurrentWithEpoch(evalScope)
        newEpoch = maxOf(newEpoch, epochG)

        return if (newEpoch > validatedEpoch) {
            evalScope.transform(a, b, c, d, e, f, g) to newEpoch
        } else {
            null
        }
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal class DerivedZippedMap<W, K, V>(
    val nameData: NameData,
    val numStates: Int,
    val upstream: Init<FastIterable<K, StateImpl<V>>>,
    val storeFactory: MutableMapK.Factory<W, K>,
) : StateDerived<MapK<W, K, V>>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<MapK<W, K, V>, Long>? {
        var newEpoch = 0L
        val result = storeFactory.create<V>(numStates)
        upstream.connect(evalScope).forEach { k, state ->
            val (v, epoch) = state.getCurrentWithEpoch(evalScope)
            newEpoch = maxOf(newEpoch, epoch)
            result[k] = v
        }
        return if (newEpoch > validatedEpoch) result to newEpoch else null
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal class DerivedZippedList<V>(
    val nameData: NameData,
    val upstream: Init<List<StateImpl<V>>>,
) : StateDerived<List<V>>() {

    init {
        nameData.forceInit()
    }

    override fun recalc(evalScope: EvalScope): Pair<List<V>, Long> {
        var newEpoch = 0L
        val result = buildList {
            upstream.connect(evalScope).fastForEach { state ->
                val (v, epoch) = state.getCurrentWithEpoch(evalScope)
                newEpoch = maxOf(newEpoch, epoch)
                add(v)
            }
        }
        return result to newEpoch
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal class MutableStateImpl<T>(internal val nameData: NameData, initialValue: Lazy<T>) :
    StateStore<T>() {

    init {
        nameData.forceInit()
    }

    private val transactionCache = TransactionCache<Pair<T, Long>>()
    private val dataRef =
        AtomicReference(Data(initialValue, readEpoch = null, connectedData = null))

    // immutable data tracking emissions and reads
    private data class Data<out T>(
        // the last value that was emitted via a call to update { .. }
        val lastEmit: Lazy<T>,
        // the last time the current value of [lastEmit] was sampled, or null if it hasn't been
        // sampled yet
        val readEpoch: Long?,
        // additional data that is only present when connected to the network
        val connectedData: ConnectedData<T>?,
    )

    // tracks network emissions
    private data class ConnectedData<out T>(
        // the network this MutableState is connected to
        val network: Network,
        // whether there is an emission scheduled due to a call to update { .. }
        val emitScheduled: Boolean,
        // the current value of this state as seen by the network, lags behind [Data.lastEmit] until
        // updated within a transaction
        val currentValue: Lazy<T>,
        // the last time that [currentValue] was updated
        val writeEpoch: Long,
    )

    private val inputNode =
        InputNode<T>(
            nameData,
            activate = {
                dataRef.updateAndGet { data ->
                    check(data.connectedData == null) { "Network mismatch" }
                    data.copy(connectedData = ConnectedData(network, false, data.lastEmit, epoch))
                }
            },
            deactivate = {
                dataRef.updateAndGet { data ->
                    checkNotNull(data.connectedData) { "Network mismatch" }
                    data.copy(connectedData = null)
                }
            },
        )

    val init: Init<StateImpl<T>> =
        constInit(nameData, StateImpl(nameData, inputNode.activated(), this))

    fun update(block: (T) -> T) {
        // update local storage, regardless of network connection
        val data =
            dataRef.getAndUpdate { data ->
                val newEmit = lazyOf(block(data.lastEmit.value))
                // if connected to the network, track emit scheduled
                val connData = data.connectedData?.copy(emitScheduled = true)
                // reset readEpoch for the new value
                data.copy(lastEmit = newEmit, readEpoch = null, connectedData = connData)
            }
        // if connected to network, go through input event emitter
        @Suppress("DeferredResultUnused")
        data.connectedData
            // no need to schedule more than once
            ?.takeUnless { it.emitScheduled }
            ?.let { it.network.transaction("MutableState.update") { pushUpdate(this) } }
    }

    private fun pushUpdate(evalScope: EvalScope) {
        var changed = false
        val data =
            dataRef.getAndUpdate { data ->
                val new = data.lastEmit
                val newConnData =
                    data.connectedData?.let { connData ->
                        val current = connData.currentValue
                        changed = new.value != current.value
                        connData.copy(
                            emitScheduled = false,
                            currentValue = if (changed) new else current,
                            writeEpoch =
                                if (changed) evalScope.epoch + 1 else data.connectedData.writeEpoch,
                        )
                    }
                data.copy(readEpoch = evalScope.epoch, connectedData = newConnData)
            }
        data.connectedData?.let { connData ->
            // cache the old value, for sampling within this transaction
            transactionCache.put(evalScope, connData.currentValue.value to connData.writeEpoch)
            // push the new value if it changed
            val new = data.lastEmit.value
            if (changed) {
                inputNode.visit(evalScope, new)
            }
        }
    }

    override fun toString(): String = "${super.toString()}[$nameData]"

    override fun getCurrentWithEpoch(evalScope: EvalScope): Pair<T, Long> =
        transactionCache.getOrPut(evalScope) {
            val data =
                dataRef.updateAndGet { data ->
                    if (data.readEpoch == null) data.copy(readEpoch = evalScope.epoch) else data
                }
            data.connectedData?.let { it.currentValue.value to it.writeEpoch }
                ?: (data.lastEmit.value to data.readEpoch!!)
        }
}
