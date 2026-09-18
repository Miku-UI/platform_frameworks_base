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

package com.android.systemui.kairos

import androidx.collection.mutableScatterMapOf
import com.android.systemui.kairos.internal.EventsImpl
import com.android.systemui.kairos.internal.constInit
import com.android.systemui.kairos.internal.mapImpl
import com.android.systemui.kairos.internal.mergeFoldNodes
import com.android.systemui.kairos.internal.mergeNodes
import com.android.systemui.kairos.internal.mergeNodesLeft
import com.android.systemui.kairos.internal.mergeReduceNodes
import com.android.systemui.kairos.internal.store.FastIterable
import com.android.systemui.kairos.internal.store.HashMapK
import com.android.systemui.kairos.internal.store.MapHolderK
import com.android.systemui.kairos.internal.store.asHashMapK
import com.android.systemui.kairos.internal.switchDeferredImpl
import com.android.systemui.kairos.internal.switchPromptImpl
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairos.util.orNull
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toNameData

/**
 * Merges the given [Events] into a single [Events] that emits events from both.
 *
 * Because [Events] can only emit one value per transaction, the provided [transformCoincidence]
 * function is used to combine coincident emissions to produce the result value to be emitted by the
 * merged [Events].
 *
 * ```
 * fun <A> Events<A>.mergeWith(
 *     other: Events<A>,
 *     transformCoincidence: TransactionScope.(A, A) -> A = { a, _ -> a },
 * ): Events<A> =
 *     listOf(this, other).merge().map { it.reduce(transformCoincidence) }
 * ```
 *
 * @see merge
 * @see mergeLeft
 * @see mergeReduce
 * @see mergeFold
 */
fun <A> Events<A>.mergeWith(
    other: Events<A>,
    transformCoincidence: TransactionScope.(A, A) -> A = { a, _ -> a },
): Events<A> =
    mergeWith(
        nameTag("Events.mergeWith").toNameData("Events.mergeWith"),
        other,
        transformCoincidence,
    )

internal fun <A> Events<A>.mergeWith(
    nameData: NameData,
    other: Events<A>,
    transformCoincidence: TransactionScope.(A, A) -> A = { a, _ -> a },
): Events<A> {
    val node =
        mergeNodes(
            nameData,
            getPulse = { init.connect(initScope = this) },
            getOther = { other.init.connect(initScope = this) },
        ) { a, b ->
            transformCoincidence(a, b)
        }
    return EventsInit(constInit(nameData, node))
}

/**
 * Merges the given [Events] into a single [Events] that emits events from all. All coincident
 * emissions are collected into the emitted [List], preserving the input ordering.
 *
 * ```
 *   fun <A> merge(vararg events: Events<A>): Events<List<A>> = events.asIterable().merge()
 * ```
 *
 * @see mergeWith
 * @see mergeLeft
 * @see mergeReduce
 * @see mergeFold
 */
fun <A> merge(vararg events: Events<A>): Events<List<A>> =
    merge(nameTag("mergeList").toNameData("mergeList"), *events)

internal fun <A> merge(nameData: NameData, vararg events: Events<A>): Events<List<A>> =
    events.asIterable().merge(nameData)

/**
 * Merges the given [Events] into a single [Events] that emits events from all. In the case of
 * coincident emissions, the emission from the left-most [Events] is emitted.
 *
 * ```
 *   fun <A> mergeLeft(vararg events: Events<A>): Events<A> = events.asIterable().mergeLeft()
 * ```
 *
 * @see merge
 * @see mergeWith
 * @see mergeReduce
 * @see mergeFold
 */
fun <A> mergeLeft(vararg events: Events<A>): Events<A> =
    mergeLeft(nameTag("mergeLeftVarArg").toNameData("mergeLeftVarArg"), *events)

internal fun <A> mergeLeft(nameData: NameData, vararg events: Events<A>): Events<A> =
    events.asIterable().mergeLeft(nameData)

/**
 * Merges the given [Events] into a single [Events] that emits events from all.
 *
 * Because [Events] can only emit one value per transaction, the provided [transformCoincidence]
 * function is used to combine coincident emissions to produce the result value to be emitted by the
 * merged [Events].
 *
 * ```
 *   fun <A> merge(vararg events: Events<A>, transformCoincidence: (A, A) -> A): Events<A> =
 *       merge(*events).map { l -> l.reduce(transformCoincidence) }
 * ```
 *
 * @see merge
 * @see mergeWith
 * @see mergeLeft
 * @see mergeFold
 */
fun <A> mergeReduce(
    vararg events: Events<A>,
    transformCoincidence: TransactionScope.(A, A) -> A,
): Events<A> =
    mergeReduce(
        nameTag("mergeVarArg").toNameData("mergeVarArg"),
        events.asIterable(),
        reduce = transformCoincidence,
    )

/**
 * Merges the given [Events] into a single [Events] that emits events from all.
 *
 * Because [Events] can only emit one value per transaction, the provided [transformCoincidence]
 * function is used to combine coincident emissions to produce the result value to be emitted by the
 * merged [Events].
 *
 * ```
 *   fun <A> merge(vararg events: Events<A>, transformCoincidence: (A, A) -> A): Events<A> =
 *       merge(*events).map { l -> l.reduce(transformCoincidence) }
 * ```
 *
 * @see merge
 * @see mergeWith
 * @see mergeLeft
 * @see mergeReduce
 */
fun <A, B> mergeFold(
    initialValue: B,
    vararg events: Events<A>,
    transformCoincidence: TransactionScope.(A, B) -> B,
): Events<B> =
    mergeFold(
        nameTag("mergeVarArg").toNameData("mergeVarArg"),
        events.asIterable(),
        initialValue,
        fold = transformCoincidence,
    )

/**
 * Merges the given [Events] into a single [Events] that emits events from all. All coincident
 * emissions are collected into the emitted [List], preserving the input ordering.
 *
 * @sample com.android.systemui.kairos.KairosSamples.merge
 * @see mergeWith
 * @see mergeLeft
 * @see mergeReduce
 * @see mergeFold
 */
fun <A> Iterable<Events<A>>.merge(): Events<List<A>> =
    merge(nameTag("Iterable<Events>.merge").toNameData("Iterable<Events>.merge"))

internal fun <A> Iterable<Events<A>>.merge(nameData: NameData): Events<List<A>> =
    EventsInit(
        constInit(nameData, mergeNodes(nameData) { map { it.init.connect(initScope = this) } })
    )

/**
 * Merges the given [Events] into a single [Events] that emits events from all. In the case of
 * coincident emissions, the emission from the left-most [Events] is emitted.
 *
 * Semantically equivalent to the following definition:
 * ```
 *   fun <A> Iterable<Events<A>>.mergeLeft(): Events<A> =
 *       merge().map { list -> list.first() }
 * ```
 *
 * In reality, the implementation avoids allocating the intermediate list of all coincident
 * emissions.
 *
 * @see merge
 * @see mergeWith
 * @see mergeReduce
 * @see mergeFold
 */
fun <A> Iterable<Events<A>>.mergeLeft(): Events<A> =
    mergeLeft(nameTag("Iterable<Events>.mergeLeft").toNameData("Iterable<Events>.mergeLeft"))

internal fun <A> Iterable<Events<A>>.mergeLeft(nameData: NameData): Events<A> =
    EventsInit(
        constInit(nameData, mergeNodesLeft(nameData) { map { it.init.connect(initScope = this) } })
    )

/**
 * Merges the given [Events] into a single [Events] that emits events from all. All coincident
 * emissions are accumulated starting with the leftmost emission by applying [reduceCoincidence]
 * from left to right the current accumulator value and each emitted element.
 *
 * Semantically equivalent to the following definition:
 * ```
 *   fun <S, T : S> Iterable<Events<T>>.mergeReduce(
 *       reduceCoincidence: TransactionScope.(S, T) -> S,
 *   ): Events<S> =
 *       merge().map { list -> list.reduce(reduceCoincidence) }
 * ```
 *
 * In reality, the implementation avoids allocating the intermediate list of all coincident
 * emissions.
 *
 * @see merge
 * @see mergeWith
 * @see mergeLeft
 * @see mergeFold
 */
fun <S, T : S> Iterable<Events<T>>.mergeReduce(
    reduceCoincidence: TransactionScope.(S, T) -> S
): Events<S> =
    mergeReduce(
        nameTag("Iterable<Events>.mergeLeft").toNameData("Iterable<Events>.mergeLeft"),
        this,
        reduceCoincidence,
    )

internal fun <S, T : S> mergeReduce(
    nameData: NameData,
    events: Iterable<Events<T>>,
    reduce: TransactionScope.(S, T) -> S,
): Events<S> =
    EventsInit(
        constInit(
            nameData,
            mergeReduceNodes(nameData, { events.map { it.init.connect(initScope = this) } }) {
                s: S,
                t: T ->
                reduce(s, t)
            },
        )
    )

/**
 * Merges the given [Events] into a single [Events] that emits events from all. All coincident
 * emissions are accumulated by applying [foldCoincidence] from left to right to each emitted
 * element and the current accumulator, starting with [initialValue].
 *
 * Semantically equivalent to the following definition:
 * ```
 *   fun <B> Iterable<Events<A>>.mergeFold(
 *       initialValue: B,
 *       foldCoincidence: TransactionScope.(A, B) -> A,
 *   ): Events<B> =
 *       merge().map { list -> list.fold(initialValue, foldCoincidence) }
 * ```
 *
 * In reality, the implementation avoids allocating the intermediate list of all coincident
 * emissions.
 *
 * @see merge
 * @see mergeWith
 * @see mergeLeft
 * @see mergeReduce
 */
fun <A, B> Iterable<Events<A>>.mergeFold(
    initialValue: B,
    foldCoincidence: TransactionScope.(A, B) -> B,
): Events<B> =
    mergeFold(
        nameTag("Iterable<Events>.mergeLeft").toNameData("Iterable<Events>.mergeLeft"),
        this,
        initialValue,
        foldCoincidence,
    )

internal fun <A, B> mergeFold(
    nameData: NameData,
    events: Iterable<Events<A>>,
    initialValue: B,
    fold: TransactionScope.(A, B) -> B,
): Events<B> =
    EventsInit(
        constInit(
            nameData,
            mergeFoldNodes(
                nameData,
                initialValue,
                { events.map { it.init.connect(initScope = this) } },
            ) { a, b ->
                fold(a, b)
            },
        )
    )

/**
 * Creates a new [Events] that emits events from all given [Events]. All simultaneous emissions are
 * collected into the emitted [List], preserving the input ordering.
 *
 * ```
 *   fun <A> Sequence<Events<A>>.merge(): Events<List<A>> = asIterable().merge()
 * ```
 *
 * @see mergeWith
 * @see mergeLeft
 * @see mergeReduce
 * @see mergeFold
 */
fun <A> Sequence<Events<A>>.merge(): Events<List<A>> =
    merge(nameTag("Sequence<Events>.mergeList").toNameData("Sequence<Events>.mergeList"))

internal fun <A> Sequence<Events<A>>.merge(nameData: NameData): Events<List<A>> =
    asIterable().merge(nameData)

/**
 * Creates a new [Events] that emits events from all given [Events]. All simultaneous emissions are
 * collected into the emitted [Map], and are given the same key of the associated [Events] in the
 * input [Map].
 *
 * ```
 *   fun <K, A> Map<K, Events<A>>.merge(): Events<Map<K, A>> =
 *       asSequence()
 *           .map { (k, events) -> events.map { a -> k to a } }
 *           .toList()
 *           .merge()
 *           .map { it.toMap() }
 * ```
 *
 * @see mergeWith
 * @see mergeLeft
 * @see mergeReduce
 * @see mergeFold
 */
fun <K, A> Map<K, Events<A>>.merge(): Events<Map<K, A>> =
    merge(nameTag("Map<K, Events>.merge").toNameData("Map<K, Events>.merge"))

internal fun <K, A> Map<K, Events<A>>.merge(nameData: NameData): Events<Map<K, A>> =
    asSequence()
        .map { (k, events) -> events.mapCheap(nameData + "pairWithKey") { a -> k to a } }
        .toList()
        .merge(nameData)
        .map(nameData + "toMap") { it.toMap() }

/**
 * Returns an [Events] that emits from a merged, incrementally-accumulated collection of [Events]
 * emitted from this, following the patch rules outlined in
 * [Map.applyPatch][com.android.systemui.kairos.util.applyPatch].
 *
 * Conceptually this is equivalent to:
 * ```
 *   fun <K, V> State<Map<K, V>>.mergeEventsIncrementally(): Events<Map<K, V>> =
 *       map { it.merge() }.switchEvents()
 * ```
 *
 * While the behavior is equivalent to the conceptual definition above, the implementation is
 * significantly more efficient.
 *
 * @sample com.android.systemui.kairos.KairosSamples.mergeEventsIncrementally
 * @see merge
 * @see mergeEventsIncrementallyPromptly
 */
fun <K, V> Incremental<K, Events<V>>.mergeEventsIncrementally(): Events<Map<K, V>> =
    mergeEventsIncrementally(
        nameTag("Incremental<K, Events>.mergeEventsIncrementally")
            .toNameData("Incremental<K, Events>.mergeEventsIncrementally")
    )

internal fun <K, V> Incremental<K, Events<V>>.mergeEventsIncrementally(
    nameData: NameData
): Events<Map<K, V>> {
    val patches: EventsImpl<FastIterable<K, EventsImpl<V>?>> =
        mapImpl({ init.connect(initScope = this).patches }, nameData + "patches") { patch, _ ->
            val connected: Map<K, EventsImpl<V>?> =
                patch.mapValues { (_, m) -> m.orNull()?.init?.connect(initScope = this) }
            MapHolderK(connected)
        }
    return EventsInit(
        constInit(
            nameData,
            switchDeferredImpl(
                nameData,
                getStorage = {
                    MapHolderK(
                        init
                            .connect(initScope = this)
                            .getCurrentWithEpoch(evalScope = this)
                            .first
                            .mapValues { (_, events) -> events.init.connect(initScope = this) }
                    )
                },
                getPatches = { patches },
                storeFactory = HashMapK.Factory(),
            ) { nodesByKey ->
                mutableScatterMapOf<K, V>()
                    .also { results ->
                        nodesByKey.asHashMapK().storage.forEach { key, node ->
                            results[key] = node.getPushEvent(logIndent = 0, evalScope = this)
                        }
                    }
                    .asMap()
            },
        )
    )
}

/**
 * Returns an [Events] that emits from a merged, incrementally-accumulated collection of [Events]
 * emitted from this, following the patch rules outlined in
 * [Map.applyPatch][com.android.systemui.kairos.util.applyPatch].
 *
 * Conceptually this is equivalent to:
 * ```
 *   fun <K, V> State<Map<K, V>>.mergeEventsIncrementallyPromptly(): Events<Map<K, V>> =
 *       map { it.merge() }.switchEventsPromptly()
 * ```
 *
 * While the behavior is equivalent to the conceptual definition above, the implementation is
 * significantly more efficient.
 *
 * @sample com.android.systemui.kairos.KairosSamples.mergeEventsIncrementallyPromptly
 * @see merge
 * @see mergeEventsIncrementally
 */
fun <K, V> Incremental<K, Events<V>>.mergeEventsIncrementallyPromptly(): Events<Map<K, V>> =
    mergeEventsIncrementallyPromptly(
        nameTag("Incremental<K, Events>.mergeEventsIncrementallyPromptly")
            .toNameData("Incremental<K, Events>.mergeEventsIncrementallyPromptly")
    )

internal fun <K, V> Incremental<K, Events<V>>.mergeEventsIncrementallyPromptly(
    nameData: NameData
): Events<Map<K, V>> {
    val patches =
        mapImpl({ init.connect(initScope = this).patches }, nameData + "patches") { patch, _ ->
            val connected: Map<K, EventsImpl<V>?> =
                patch.mapValues { (_, m) -> m.orNull()?.init?.connect(initScope = this) }
            MapHolderK(connected)
        }
    return EventsInit(
        constInit(
            nameData,
            switchPromptImpl(
                nameData,
                getStorage = {
                    MapHolderK(
                        init
                            .connect(initScope = this)
                            .getCurrentWithEpoch(evalScope = this)
                            .first
                            .mapValues { (_, events) -> events.init.connect(initScope = this) }
                    )
                },
                getPatches = { patches },
                storeFactory = HashMapK.Factory(),
            ) { nodesByKey ->
                mutableScatterMapOf<K, V>()
                    .also { results ->
                        nodesByKey.asHashMapK().storage.forEach { key, node ->
                            results[key] = node.getPushEvent(logIndent = 0, evalScope = this)
                        }
                    }
                    .asMap()
            },
        )
    )
}
