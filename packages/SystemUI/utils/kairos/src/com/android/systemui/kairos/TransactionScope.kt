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

import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.These
import com.android.systemui.kairos.util.maybeOf
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toNameData

/**
 * Kairos operations that are available while a transaction is active.
 *
 * These operations do not accumulate state, which makes [TransactionScope] weaker than
 * [StateScope], but allows it to be used in more places.
 */
@ExperimentalKairosApi
interface TransactionScope : KairosScope {

    /**
     * Returns the current value of this [Transactional] as a [DeferredValue].
     *
     * Compared to [sample], you may want to use this instead if you do not need to inspect the
     * sampled value, but instead want to pass it to another Kairos API that accepts a
     * [DeferredValue]. In this case, [sampleDeferred] is both safer and more performant.
     *
     * @see sample
     */
    fun <A> Transactional<A>.sampleDeferred(): DeferredValue<A>

    /**
     * Returns the current value of this [State] as a [DeferredValue].
     *
     * Compared to [sample], you may want to use this instead if you do not need to inspect the
     * sampled value, but instead want to pass it to another Kairos API that accepts a
     * [DeferredValue]. In this case, [sampleDeferred] is both safer and more performant.
     *
     * @see sample
     */
    fun <A> State<A>.sampleDeferred(): DeferredValue<A>

    /**
     * Defers invoking [block] until after the current [TransactionScope] code-path completes,
     * returning a [DeferredValue] that can be used to reference the result.
     *
     * Useful for recursive definitions.
     *
     * @see DeferredValue
     */
    fun <A> deferredTransactionScope(block: TransactionScope.() -> A): DeferredValue<A>

    /** An [Events] that emits once, within the current transaction, and then never again. */
    val now: Events<Unit>

    /**
     * Returns the current value held by this [State]. Guaranteed to be consistent within the same
     * transaction.
     *
     * @see sampleDeferred
     */
    fun <A> State<A>.sample(): A = sampleDeferred().value

    /**
     * Returns the current value held by this [Transactional]. Guaranteed to be consistent within
     * the same transaction.
     *
     * @see sampleDeferred
     */
    fun <A> Transactional<A>.sample(): A = sampleDeferred().value
}

/**
 * Returns an [Events] that emits the value sampled from the [Transactional] produced by each
 * emission of the original [Events], within the same transaction of the original emission.
 */
@ExperimentalKairosApi
fun <A> Events<Transactional<A>>.sampleTransactionals(): Events<A> =
    sampleTransactionals(
        nameTag("Events.sampleTransactionals").toNameData("Events.sampleTransactionals")
    )

internal fun <A> Events<Transactional<A>>.sampleTransactionals(nameData: NameData): Events<A> =
    map(nameData) { it.sample() }

/** @see TransactionScope.sample */
@ExperimentalKairosApi
fun <A, B, C> Events<A>.sample(
    state: State<B>,
    transform: TransactionScope.(A, B) -> C,
): Events<C> =
    sample(nameTag("Events.sampleState").toNameData("Events.sampleState"), state, transform)

internal fun <A, B, C> Events<A>.sample(
    nameData: NameData,
    state: State<B>,
    transform: TransactionScope.(A, B) -> C,
): Events<C> = map(nameData) { transform(it, state.sample()) }

/** @see TransactionScope.sample */
@ExperimentalKairosApi
fun <A, B, C> Events<A>.sample(
    transactional: Transactional<B>,
    transform: TransactionScope.(A, B) -> C,
): Events<C> =
    sample(
        nameTag("Events.sampleTransactional").toNameData("Events.sampleTransactional"),
        transactional,
        transform,
    )

internal fun <A, B, C> Events<A>.sample(
    nameData: NameData,
    transactional: Transactional<B>,
    transform: TransactionScope.(A, B) -> C,
): Events<C> = map(nameData) { transform(it, transactional.sample()) }

/**
 * Like [sample], but if [state] is changing at the time it is sampled ([changes] is emitting), then
 * the new value is passed to [transform].
 *
 * Note that [sample] is both more performant and safer to use with recursive definitions. You will
 * generally want to use it rather than this.
 *
 * @see sample
 */
@ExperimentalKairosApi
fun <A, B, C> Events<A>.samplePromptly(
    state: State<B>,
    transform: TransactionScope.(A, B) -> C,
): Events<C> =
    samplePromptly(
        nameTag("Events.samplePromptly").toNameData("Events.samplePromptly"),
        state,
        transform,
    )

internal fun <A, B, C> Events<A>.samplePromptly(
    nameData: NameData,
    state: State<B>,
    transform: TransactionScope.(A, B) -> C,
): Events<C> =
    sample(nameData + "makeThese", state) { a, b -> These.first(a to b) }
        .mergeWith(
            nameData + "mergeWithSampleTarget",
            state.changes(nameData + "changes").map(nameData + "makeTheseSecond") {
                These.second(it)
            },
        ) { thiz, that ->
            These.both((thiz as These.First).value, (that as These.Second).value)
        }
        .mapMaybe(nameData) { these ->
            when (these) {
                // both present, transform the upstream value and the new value
                is These.Both -> maybeOf(transform(these.first.first, these.second))
                // no upstream present, so don't perform the sample
                is These.Second -> maybeOf()
                // just the upstream, so transform the upstream and the old value
                is These.First -> maybeOf(transform(these.value.first, these.value.second))
            }
        }
