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

import com.android.systemui.kairos.internal.NoScope
import com.android.systemui.kairos.internal.init
import com.android.systemui.kairos.internal.zipStates
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toNameData

/**
 * Returns a [State] whose value is generated with [transform] by combining the current values of
 * each given [State].
 *
 * @sample com.android.systemui.kairos.KairosSamples.combineState
 */
@ExperimentalKairosApi
@JvmName(name = "stateCombine")
fun <A, B, C> State<A>.combine(other: State<B>, transform: KairosScope.(A, B) -> C): State<C> =
    combine(nameTag("State.combine").toNameData("State.combine"), other, transform)

internal fun <A, B, C> State<A>.combine(
    nameData: NameData,
    other: State<B>,
    transform: KairosScope.(A, B) -> C,
): State<C> = combine(nameData, this, other, transform)

/**
 * Returns a [State] by combining the values held inside the given [States][State] into a [List].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <A> Iterable<State<A>>.combine(): State<List<A>> =
    combine(nameTag("Iterable<State>.combineToList").toNameData("Iterable<State>.combineToList"))

internal fun <A> Iterable<State<A>>.combine(nameData: NameData): State<List<A>> =
    StateInit(
        init(nameData) {
            val states = map { it.init }
            zipStates(
                nameData,
                states.size,
                states = init(nameData) { states.map { it.connect(this) } },
            )
        }
    )

/**
 * Returns a [State] by combining the values held inside the given [States][State] into a [Map].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <K, A> Map<K, State<A>>.combine(): State<Map<K, A>> =
    combine(nameTag("Map<K, State>.combine").toNameData("Map<K, State>.combine"))

internal fun <K, A> Map<K, State<A>>.combine(nameData: NameData): State<Map<K, A>> =
    asIterable()
        .map { (k, state) ->
            state.mapCheapUnsafe(nameData + { "pairWithKey[$k]" }) { v -> k to v }
        }
        .combine(nameData)
        .map(nameData + "toMap") { it.toMap() }

/**
 * Returns a [State] whose value is generated with [transform] by combining the current values of
 * each given [State].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <A, B> Iterable<State<A>>.combine(transform: KairosScope.(List<A>) -> B): State<B> =
    combine(nameTag("Iterable<State>.combine").toNameData("Iterable<State>.combine"), transform)

internal fun <A, B> Iterable<State<A>>.combine(
    nameData: NameData,
    transform: KairosScope.(List<A>) -> B,
): State<B> = combine(nameData + "combineToList").map(nameData, transform)

/**
 * Returns a [State] by combining the values held inside the given [State]s into a [List].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <A> combine(vararg states: State<A>): State<List<A>> =
    combine(nameTag("combineVarArgToList").toNameData("combineVarArgToList"), *states)

internal fun <A> combine(nameData: NameData, vararg states: State<A>): State<List<A>> =
    states.asIterable().combine(nameData)

/**
 * Returns a [State] whose value is generated with [transform] by combining the current values of
 * each given [State].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <A, B> combine(vararg states: State<A>, transform: KairosScope.(List<A>) -> B): State<B> =
    combine(nameTag("combineVarArg").toNameData("combineVarArg"), *states, transform = transform)

internal fun <A, B> combine(
    nameData: NameData,
    vararg states: State<A>,
    transform: KairosScope.(List<A>) -> B,
): State<B> = states.asIterable().combine(nameData, transform)

/**
 * Returns a [State] whose value is generated with [transform] by combining the current values of
 * each given [State].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <A, B, Z> combine(
    stateA: State<A>,
    stateB: State<B>,
    transform: KairosScope.(A, B) -> Z,
): State<Z> = combine(nameTag("combine2").toNameData("combine2"), stateA, stateB, transform)

internal fun <A, B, Z> combine(
    nameData: NameData,
    stateA: State<A>,
    stateB: State<B>,
    transform: KairosScope.(A, B) -> Z,
): State<Z> =
    StateInit(
        init(nameData) {
            zipStates(nameData, stateA.init, stateB.init) { a, b -> NoScope.transform(a, b) }
        }
    )

/**
 * Returns a [State] whose value is generated with [transform] by combining the current values of
 * each given [State].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <A, B, C, Z> combine(
    stateA: State<A>,
    stateB: State<B>,
    stateC: State<C>,
    transform: KairosScope.(A, B, C) -> Z,
): State<Z> = combine(nameTag("combine3").toNameData("combine3"), stateA, stateB, stateC, transform)

internal fun <A, B, C, Z> combine(
    nameData: NameData,
    stateA: State<A>,
    stateB: State<B>,
    stateC: State<C>,
    transform: KairosScope.(A, B, C) -> Z,
): State<Z> =
    StateInit(
        init(nameData) {
            zipStates(nameData, stateA.init, stateB.init, stateC.init) { a, b, c ->
                NoScope.transform(a, b, c)
            }
        }
    )

/**
 * Returns a [State] whose value is generated with [transform] by combining the current values of
 * each given [State].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <A, B, C, D, Z> combine(
    stateA: State<A>,
    stateB: State<B>,
    stateC: State<C>,
    stateD: State<D>,
    transform: KairosScope.(A, B, C, D) -> Z,
): State<Z> =
    combine(nameTag("combine4").toNameData("combine4"), stateA, stateB, stateC, stateD, transform)

internal fun <A, B, C, D, Z> combine(
    nameData: NameData,
    stateA: State<A>,
    stateB: State<B>,
    stateC: State<C>,
    stateD: State<D>,
    transform: KairosScope.(A, B, C, D) -> Z,
): State<Z> =
    StateInit(
        init(nameData) {
            zipStates(nameData, stateA.init, stateB.init, stateC.init, stateD.init) { a, b, c, d ->
                NoScope.transform(a, b, c, d)
            }
        }
    )

/**
 * Returns a [State] whose value is generated with [transform] by combining the current values of
 * each given [State].
 *
 * @see State.combine
 */
@ExperimentalKairosApi
fun <A, B, C, D, E, Z> combine(
    stateA: State<A>,
    stateB: State<B>,
    stateC: State<C>,
    stateD: State<D>,
    stateE: State<E>,
    transform: KairosScope.(A, B, C, D, E) -> Z,
): State<Z> =
    combine(
        nameTag("combine5").toNameData("combine5"),
        stateA,
        stateB,
        stateC,
        stateD,
        stateE,
        transform,
    )

internal fun <A, B, C, D, E, Z> combine(
    nameData: NameData,
    stateA: State<A>,
    stateB: State<B>,
    stateC: State<C>,
    stateD: State<D>,
    stateE: State<E>,
    transform: KairosScope.(A, B, C, D, E) -> Z,
): State<Z> =
    StateInit(
        init(nameData) {
            zipStates(nameData, stateA.init, stateB.init, stateC.init, stateD.init, stateE.init) {
                a,
                b,
                c,
                d,
                e ->
                NoScope.transform(a, b, c, d, e)
            }
        }
    )
