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
@file:OptIn(ExperimentalKairosApi::class)

package com.android.app.concurrent.benchmark.event

import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.MutableState as KairosMutableState
import com.android.systemui.kairos.State as KairosState
import com.android.systemui.kairos.TransactionScope
import com.android.systemui.kairos.combine as combineKairosState
import com.android.systemui.kairos.launchKairosNetwork
import com.android.systemui.kairos.map as mapKairosState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias KairosStateBoxIn<T> = EventBox<T, KairosState<*>>

typealias KairosStateBoxOut<T> = EventBox<T, KairosState<T>>

private class KairosStateBoxImpl<out T, E : KairosState<T>>(event: E) :
    AbstractEventBox<T, E>(event)

private fun <T> KairosStateBoxIn<T>.unbox(): KairosState<T> =
    (this as KairosStateBoxImpl<T, *>).event

class KairosObservationContext(val scope: BuildScope) : ReadContext<KairosState<*>> {
    override fun <T> KairosStateBoxIn<T>.observe(block: (T) -> Unit) {
        with(scope) { unbox().observeSync { block(it) } }
    }
}

class KairosWriteContext(val scope: TransactionScope) : WriteContext<KairosState<*>> {
    override fun <T> KairosStateBoxIn<T>.update(value: T) {
        val kairosState = (this as KairosStateBoxImpl<T, *>).event as KairosMutableState<T>
        with(scope) { kairosState.setValue(value) }
    }

    override fun <T> KairosStateBoxIn<T>.current(): T {
        TODO("Not yet implemented")
    }
}

class KairosWritableEventBuilder(val scope: CoroutineScope) :
    EventContext,
    WritableEventFactory<KairosMutableState<*>>,
    EventContextProvider<KairosState<*>>,
    EventCombiner<KairosState<*>>,
    IntEventCombiner<KairosState<*>>,
    MapOperator<KairosState<*>> {

    val kairosNetwork = scope.launchKairosNetwork()

    override fun <T> createWritableEvent(value: T): EventBox<T, KairosMutableState<T>> {
        return KairosStateBoxImpl(KairosMutableState(kairosNetwork, value))
    }

    override fun <T1, T2, T3> combineEvents(
        a: KairosStateBoxIn<T1>,
        b: KairosStateBoxIn<T2>,
        transform: (T1, T2) -> T3,
    ): KairosStateBoxIn<T3> {
        return KairosStateBoxImpl(
            combineKairosState(a.unbox(), b.unbox()) { aVal, bVal -> transform(aVal, bVal) }
        )
    }

    override fun <T1, T2, T3, T4> combineEvents(
        a: KairosStateBoxIn<T1>,
        b: KairosStateBoxIn<T2>,
        c: KairosStateBoxIn<T3>,
        transform: (T1, T2, T3) -> T4,
    ): KairosStateBoxIn<T4> {
        return KairosStateBoxImpl(
            combineKairosState(a.unbox(), b.unbox(), c.unbox()) { aVal, bVal, cVal ->
                transform(aVal, bVal, cVal)
            }
        )
    }

    override fun <R> combineIntEvents(
        events: Iterable<KairosStateBoxIn<Int>>,
        transform: (Array<Int>) -> R,
    ): KairosStateBoxOut<R> {
        val kairosStates = events.map { it.unbox() }
        return KairosStateBoxImpl(
            kairosStates.combineKairosState { input -> transform(input.toTypedArray()) }
        )
    }

    override fun read(block: ReadContext<KairosState<*>>.() -> Unit): AutoCloseable {
        val job =
            scope.launch {
                kairosNetwork.activateSpec {
                    val observationContext = KairosObservationContext(this)
                    observationContext.block()
                }
            }
        return AutoCloseable { job.cancel() }
    }

    override fun write(block: WriteContext<KairosState<*>>.() -> Unit) {
        scope.launch {
            kairosNetwork.transact {
                val updateContext = KairosWriteContext(this)
                updateContext.block()
            }
        }
    }

    override fun <A, B> KairosStateBoxIn<A>.map(transform: (A) -> B): KairosStateBoxOut<B> {
        this as KairosStateBoxImpl<A, *>
        return KairosStateBoxImpl(event.mapKairosState { transform(it) })
    }
}

abstract class BaseKairosEventBenchmark(threadParam: ThreadFactory<Any, CoroutineScope>) :
    BaseEventBenchmark<CoroutineScope, KairosWritableEventBuilder>(
        threadParam,
        { KairosWritableEventBuilder(it) },
    )
