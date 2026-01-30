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

import com.android.systemui.kairos.internal.DemuxImpl
import com.android.systemui.kairos.internal.constInit
import com.android.systemui.kairos.internal.demuxMap
import com.android.systemui.kairos.util.Either
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.These
import com.android.systemui.kairos.util.maybeFirst
import com.android.systemui.kairos.util.maybeSecond
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairos.util.orError
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toNameData

/**
 * Returns a [KeyedEvents] that can be used to efficiently split a single [Events] into multiple
 * downstream [Events].
 *
 * The input [Events] emits [Map] instances that specify which downstream [Events] the associated
 * value will be emitted from. These downstream [Events] can be obtained via
 * [KeyedEvents.eventsForKey].
 *
 * An example:
 * ```
 *   val fooEvents: Events<Map<String, Foo>> = ...
 *   val fooById: GroupedEvents<String, Foo> = fooEvents.groupByKey()
 *   val fooBar: Events<Foo> = fooById["bar"]
 * ```
 *
 * This is semantically equivalent to `val fooBar = fooEvents.mapNotNull { map -> map["bar"] }` but
 * is significantly more efficient; specifically, using [mapNotNull] in this way incurs a `O(n)`
 * performance hit, where `n` is the number of different [mapNotNull] operations used to filter on a
 * specific key's presence in the emitted [Map]. [groupByKey] internally uses a [HashMap] to lookup
 * the appropriate downstream [Events], and so operates in `O(1)`.
 *
 * The optional [numKeys] argument is an optimization used to initialize the internal [HashMap].
 *
 * Note that the returned [KeyedEvents] should be cached and re-used to gain the performance
 * benefit.
 *
 * @sample com.android.systemui.kairos.KairosSamples.groupByKey
 * @see selector
 */
@ExperimentalKairosApi
fun <K, A> Events<Map<K, A>>.groupByKey(numKeys: Int? = null): KeyedEvents<K, A> =
    groupByKey(nameTag("Events.groupByKey").toNameData("Events.groupByKey"), numKeys)

internal fun <K, A> Events<Map<K, A>>.groupByKey(
    nameData: NameData,
    numKeys: Int? = null,
): KeyedEvents<K, A> = KeyedEvents(demuxMap(nameData, { init.connect(this) }, numKeys))

/**
 * Returns a [KeyedEvents] that can be used to efficiently split a single [Events] into multiple
 * downstream [Events]. The downstream [Events] are associated with a [key][K], which is derived
 * from each emission of the original [Events] via [extractKey].
 *
 * ```
 *   fun <K, A> Events<A>.groupBy(
 *       numKeys: Int? = null,
 *       extractKey: TransactionScope.(A) -> K,
 *   ): GroupedEvents<K, A> =
 *       map { mapOf(extractKey(it) to it) }.groupByKey(numKeys)
 * ```
 *
 * @see groupByKey
 */
@ExperimentalKairosApi
fun <K, A> Events<A>.groupBy(
    numKeys: Int? = null,
    extractKey: TransactionScope.(A) -> K,
): KeyedEvents<K, A> =
    groupBy(nameTag("Events.groupBy").toNameData("Events.groupBy"), numKeys, extractKey)

internal fun <K, A> Events<A>.groupBy(
    nameData: NameData,
    numKeys: Int? = null,
    extractKey: TransactionScope.(A) -> K,
): KeyedEvents<K, A> =
    map(nameData + "extractKey") { mapOf(extractKey(it) to it) }.groupByKey(nameData, numKeys)

/**
 * A mapping from keys of type [K] to [Events] emitting values of type [A].
 *
 * @see groupByKey
 */
@ExperimentalKairosApi
class KeyedEvents<in K, out A> internal constructor(internal val impl: DemuxImpl<K, A>) {
    /**
     * Returns an [Events] that emits values of type [A] that correspond to the given [key].
     *
     * @see groupByKey
     */
    fun eventsForKey(key: K): Events<A> =
        eventsForKey(
            key,
            nameTag("KeyedEvents.eventsForKey").toNameData("KeyedEvents.eventsForKey"),
        )

    internal fun eventsForKey(key: K, nameData: NameData): Events<A> =
        EventsInit(constInit(nameData, impl.eventsForKey(key)))

    /**
     * Returns an [Events] that emits values of type [A] that correspond to the given [key].
     *
     * @see groupByKey
     */
    operator fun get(key: K): Events<A> = eventsForKey(key)
}

/**
 * Returns two new [Events] that contain elements from this [Events] that satisfy or don't satisfy
 * [predicate].
 *
 * Using this is equivalent to `upstream.filter(predicate) to upstream.filter { !predicate(it) }`
 * but is more efficient; specifically, [partition] will only invoke [predicate] once per element.
 *
 * ```
 *   fun <A> Events<A>.partition(
 *       predicate: TransactionScope.(A) -> Boolean
 *   ): Pair<Events<A>, Events<A>> =
 *       map { if (predicate(it)) left(it) else right(it) }.partitionEither()
 * ```
 *
 * @see partitionEither
 */
@ExperimentalKairosApi
fun <A> Events<A>.partition(
    predicate: TransactionScope.(A) -> Boolean
): Pair<Events<A>, Events<A>> =
    partition(nameTag("Events.partition").toNameData("Events.partition"), predicate)

internal fun <A> Events<A>.partition(
    nameData: NameData,
    predicate: TransactionScope.(A) -> Boolean,
): Pair<Events<A>, Events<A>> {
    val grouped: KeyedEvents<Boolean, A> = groupBy(nameData, numKeys = 2, extractKey = predicate)
    return Pair(grouped.eventsForKey(true, nameData), grouped.eventsForKey(false, nameData))
}

/**
 * Returns two new [Events] that contain elements from this [Events]; [Pair.first] will contain
 * [First] values, and [Pair.second] will contain [Second] values.
 *
 * Using this is equivalent to using [filterIsInstance] in conjunction with [map] twice, once for
 * [First]s and once for [Second]s, but is slightly more efficient; specifically, the
 * [filterIsInstance] check is only performed once per element.
 *
 * ```
 *   fun <A, B> Events<Either<A, B>>.partitionEither(): Pair<Events<A>, Events<B>> =
 *     map { it.asThese() }.partitionThese()
 * ```
 *
 * @see partitionThese
 */
@ExperimentalKairosApi
fun <A, B> Events<Either<A, B>>.partitionEither(): Pair<Events<A>, Events<B>> =
    partitionEither(nameTag("Events.partitionEither").toNameData("Events.partitionEither"))

internal fun <A, B> Events<Either<A, B>>.partitionEither(
    nameData: NameData
): Pair<Events<A>, Events<B>> {
    val (left, right) = partition(nameData) { it is Either.First }
    return Pair(
        left.mapCheap(nameData + "getFirst") { (it as Either.First).value },
        right.mapCheap(nameData + "getSecond") { (it as Either.Second).value },
    )
}

/**
 * Returns two new [Events] that contain elements from this [Events]; [Pair.first] will contain
 * [These.first] values, and [Pair.second] will contain [These.second] values. If the original
 * emission was a [These.both], then both result [Events] will emit a value simultaneously.
 *
 * @sample com.android.systemui.kairos.KairosSamples.partitionThese
 */
@ExperimentalKairosApi
fun <A, B> Events<These<A, B>>.partitionThese(): Pair<Events<A>, Events<B>> =
    partitionThese(nameTag("Events.partitionThese").toNameData("Events.partitionThese"))

internal fun <A, B> Events<These<A, B>>.partitionThese(
    nameData: NameData
): Pair<Events<A>, Events<B>> {
    val grouped =
        mapCheap(nameData + "projectBools") {
                when (it) {
                    is These.Both -> mapOf(true to it, false to it)
                    is These.Second -> mapOf(false to it)
                    is These.First -> mapOf(true to it)
                }
            }
            .groupByKey(nameData, numKeys = 2)
    return Pair(
        grouped.eventsForKey(true, nameData).mapCheap(nameData + "getFirst") {
            it.maybeFirst().orError { "unexpected missing value" }
        },
        grouped.eventsForKey(false, nameData).mapCheap(nameData + "getSecond") {
            it.maybeSecond().orError { "unexpected missing value" }
        },
    )
}
