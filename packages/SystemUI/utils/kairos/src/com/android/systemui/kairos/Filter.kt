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

import com.android.systemui.kairos.internal.constInit
import com.android.systemui.kairos.internal.filterImpl
import com.android.systemui.kairos.internal.filterPresentImpl
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toMaybe
import com.android.systemui.kairos.util.toNameData

/** Return an [Events] that emits from the original [Events] only when [state] is `true`. */
@ExperimentalKairosApi
fun <A> Events<A>.filter(state: State<Boolean>): Events<A> =
    filter(nameTag("Events.filter(State)").toNameData("Events.filter(State)"), state)

internal fun <A> Events<A>.filter(nameData: NameData, state: State<Boolean>): Events<A> =
    filter(nameData) { state.sample() }

/**
 * Returns an [Events] containing only values of the original [Events] that are not null.
 *
 * ```
 *  fun <A> Events<A?>.filterNotNull(): Events<A> = mapNotNull { it }
 * ```
 *
 * @see mapNotNull
 */
@ExperimentalKairosApi
fun <A> Events<A?>.filterNotNull(): Events<A> =
    filterNotNull(nameTag("Events.filterNotNull").toNameData("Events.filterNotNull"))

internal fun <A> Events<A?>.filterNotNull(nameData: NameData): Events<A> =
    mapCheap(nameData + "mapToMaybe") { it.toMaybe() }.filterPresent(nameData)

/**
 * Returns an [Events] containing only values of the original [Events] that are instances of [A].
 *
 * ```
 *   inline fun <reified A> Events<*>.filterIsInstance(): Events<A> =
 *       mapNotNull { it as? A }
 * ```
 *
 * @see mapNotNull
 */
@ExperimentalKairosApi
inline fun <reified A> Events<*>.filterIsInstance(): Events<A> =
    mapCheap { it as? A }.filterNotNull()

/**
 * Returns an [Events] containing only values of the original [Events] that are present.
 *
 * ```
 *  fun <A> Events<Maybe<A>>.filterPresent(): Events<A> = mapMaybe { it }
 * ```
 *
 * @see mapMaybe
 */
@ExperimentalKairosApi
fun <A> Events<Maybe<A>>.filterPresent(): Events<A> =
    filterPresent(nameTag("Events.filterPresent").toNameData("Events.filterPresent"))

internal fun <A> Events<Maybe<A>>.filterPresent(nameData: NameData): Events<A> =
    EventsInit(constInit(nameData, filterPresentImpl(nameData) { init.connect(evalScope = this) }))

/**
 * Returns an [Events] containing only values of the original [Events] that satisfy the given
 * [predicate].
 *
 * ```
 *   fun <A> Events<A>.filter(predicate: TransactionScope.(A) -> Boolean): Events<A> =
 *       mapMaybe { if (predicate(it)) present(it) else absent }
 * ```
 *
 * @see mapMaybe
 */
@ExperimentalKairosApi
fun <A> Events<A>.filter(predicate: TransactionScope.(A) -> Boolean): Events<A> =
    filter(nameTag("Events.filter").toNameData("Events.filter"), predicate)

internal fun <A> Events<A>.filter(
    nameData: NameData,
    predicate: TransactionScope.(A) -> Boolean,
): Events<A> {
    val impl = filterImpl(nameData, { init.connect(evalScope = this) }) { predicate(it) }
    return EventsInit(constInit(nameData, impl))
}
