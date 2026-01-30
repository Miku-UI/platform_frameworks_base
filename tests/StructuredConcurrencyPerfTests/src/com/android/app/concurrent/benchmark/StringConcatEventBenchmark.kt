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
package com.android.app.concurrent.benchmark

import androidx.benchmark.BlackHole
import androidx.benchmark.ExperimentalBlackHoleApi
import com.android.app.concurrent.benchmark.base.ConcurrentBenchmarkRule
import com.android.app.concurrent.benchmark.event.BaseFlowEventBenchmark
import com.android.app.concurrent.benchmark.event.BaseSimpleEventBenchmark
import com.android.app.concurrent.benchmark.event.DistinctUntilChangedOperator
import com.android.app.concurrent.benchmark.event.EventCombiner
import com.android.app.concurrent.benchmark.event.EventContextProvider
import com.android.app.concurrent.benchmark.event.FilterOperator
import com.android.app.concurrent.benchmark.event.FlowWritableEventBuilder
import com.android.app.concurrent.benchmark.event.MapOperator
import com.android.app.concurrent.benchmark.event.SimpleEvent
import com.android.app.concurrent.benchmark.event.SimpleWritableEventBuilder
import com.android.app.concurrent.benchmark.event.WritableEventFactory
import com.android.app.concurrent.benchmark.util.ExecutorServiceCoroutineScopeBuilder
import com.android.app.concurrent.benchmark.util.ExecutorThreadBuilder
import com.android.app.concurrent.benchmark.util.HandlerImmediateThreadBuilder
import com.android.app.concurrent.benchmark.util.HandlerThreadBuilder
import com.android.app.concurrent.benchmark.util.HandlerThreadImmediateScopeBuilder
import com.android.app.concurrent.benchmark.util.HandlerThreadScopeBuilder
import com.android.app.concurrent.benchmark.util.ThreadFactory
import com.android.app.concurrent.benchmark.util.dbg
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@OptIn(ExperimentalBlackHoleApi::class)
private sealed interface StringConcatEventBenchmark<T, E : Any>
    where
        T : WritableEventFactory<E>,
        T : EventContextProvider<E>,
        T : EventCombiner<E>,
        T : DistinctUntilChangedOperator<E>,
        T : FilterOperator<E>,
        T : MapOperator<E> {
    val benchmarkRule: ConcurrentBenchmarkRule
    val context: T

    private fun T.manyStringConcatFlows() {
        val k = AtomicInteger()
        val eventA = createWritableEvent("A:0") // A:$n
        val eventB = createWritableEvent("B:0") // B:$n
        val eventC = createWritableEvent("C:0") // C:$n
        val eventD = createWritableEvent("D:0") // D:$n
        val eventE = createWritableEvent("E:0") // E:$n

        // A:$n+B:$n
        val eventAB = combineEvents(eventA, eventB) { a, b -> "$a+$b" }

        // --C:$n--
        val eventCm = eventC.map { "--$it--" }

        // A:$n+D:$n
        val eventAD = combineEvents(eventA, eventD) { a, d -> "$a+$d" }

        // ==E:$n==
        val eventEm = eventE.map { "==$it==" }

        // ==E:$n==+B:$n
        val eventEmB = combineEvents(eventEm, eventB) { eM, b -> "$eM+$b" }

        // C:$n+==E:$n==
        val eventCEm = combineEvents(eventC, eventEm) { c, eM -> "$c+$eM" }

        // A:$n+B:$n+==E:$n==+==E:$n==+B:$n
        val eventABEmEmB =
            combineEvents(eventAB, eventEm, eventEmB) { ab, eM, eMb -> "$ab+$eM+$eMb" }

        // A:$n+D:$n+==E:$n==+B:$n
        val eventADEmB = combineEvents(eventAD, eventEmB) { ad, eMb -> "$ad+$eMb" }

        // --C:$n--+C:$n+==E:$n==
        val eventCmCEm = combineEvents(eventCm, eventCEm) { cM, ceM -> "$cM+$ceM" }

        // ##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##
        val eventABEmEmBm = eventABEmEmB.map { "##$it##" }

        // ##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##+A:$n+D:$n
        val eventABEmEmBmAD =
            combineEvents(eventABEmEmBm, eventAD) { abeMeMbM, ad -> "$abeMeMbM+$ad" }
                .distinctUntilChanged()

        // --C:$n--+C:$n+==E:$n==+A:$n+D:$n+==E:$n==+B:$n+A:$n+B:$n
        val eventCmCEmADEmBAB =
            combineEvents(eventCmCEm, eventADEmB, eventAB) { cMceM, adeMb, ab ->
                    "$cMceM+$adeMb+$ab"
                }
                .distinctUntilChanged()

        // --C:$n--+A:$n+B:$n+==E:$n==+==E:$n==+B:$n
        val eventCmABEmEmB =
            combineEvents(eventCm, eventABEmEmB) { cM, abeMeMb -> "$cM+$abeMeMb" }
                .distinctUntilChanged()

        var receivedVal1 = ""
        var receivedVal2 = ""
        var receivedVal3 = ""

        benchmarkRule.runBenchmark {
            withBarrier(count = 3) {
                beforeFirstIteration { barrier ->
                    read {
                        eventABEmEmBmAD.observe {
                            val n = k.get()
                            if (it == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##+A:$n+D:$n") {
                                dbg { "receivedVal1=$receivedVal1" }
                                receivedVal1 = it
                                barrier.countDown()
                            }
                        }
                        eventCmCEmADEmBAB.observe {
                            val n = k.get()
                            if (it == "--C:$n--+C:$n+==E:$n==+A:$n+D:$n+==E:$n==+B:$n+A:$n+B:$n") {
                                dbg { "receivedVal2=$receivedVal2" }
                                receivedVal2 = it
                                barrier.countDown()
                            }
                        }
                        eventCmABEmEmB.observe {
                            val n = k.get()
                            if (it == "--C:$n--+A:$n+B:$n+==E:$n==+==E:$n==+B:$n") {
                                dbg { "receivedVal3=$receivedVal3" }
                                receivedVal3 = it
                                barrier.countDown()
                            }
                        }
                    }
                }
            }
            onEachIteration { n ->
                k.set(n)
                write {
                    eventA.update("A:$n")
                    eventB.update("B:$n")
                    eventC.update("C:$n")
                    eventD.update("D:$n")
                    eventE.update("E:$n")
                }
            }
            stateChecker(
                isInExpectedState = { n ->
                    receivedVal1 == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##+A:$n+D:$n" &&
                        receivedVal2 ==
                            "--C:$n--+C:$n+==E:$n==+A:$n+D:$n+==E:$n==+B:$n+A:$n+B:$n" &&
                        receivedVal3 == "--C:$n--+A:$n+B:$n+==E:$n==+==E:$n==+B:$n"
                },
                expectedStr =
                    """
                                receivedVal1 == "##A:n+B:n+==E:n==+==E:n==+B:n##+A:n+D:n" &&
                                receivedVal2 == "--C:n--+C:n+==E:n==+A:n+D:n+==E:n==+B:n+A:n+B:n" &&
                                receivedVal3 == "--C:n--+A:n+B:n+==E:n==+==E:n==+B:n"
                    """
                        .trimIndent(),
                expectedCalc = { n ->
                    """
                    $receivedVal1 == "##A:$n+B:$n+==E:$n==+==E:$n==+B:$n##+A:$n+D:$n" &&
                    $receivedVal2 == "--C:$n--+C:$n+==E:$n==+A:$n+D:$n+==E:$n==+B:$n+A:$n+B:$n" &&
                    $receivedVal3 == "--C:$n--+A:$n+B:$n+==E:$n==+==E:$n==+B:$n"
        """
                        .trimIndent()
                },
            )
            afterLastIteration {
                BlackHole.consume(receivedVal1)
                BlackHole.consume(receivedVal2)
                BlackHole.consume(receivedVal3)
            }
        }
    }

    @Test
    fun benchmarkE_manyStringConcatFlows() {
        context.manyStringConcatFlows()
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SimpleStringConcatEventBenchmark(param: ThreadFactory<Any, Executor>) :
    BaseSimpleEventBenchmark(param),
    StringConcatEventBenchmark<SimpleWritableEventBuilder, SimpleEvent<*>> {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() =
            listOf(ExecutorThreadBuilder, HandlerThreadBuilder, HandlerImmediateThreadBuilder)
    }
}

@RunWith(Parameterized::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowStringConcatEventBenchmark(param: ThreadFactory<Any, CoroutineScope>) :
    BaseFlowEventBenchmark(param), StringConcatEventBenchmark<FlowWritableEventBuilder, Flow<*>> {

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun getDispatchers() =
            listOf(
                ExecutorServiceCoroutineScopeBuilder,
                HandlerThreadScopeBuilder,
                HandlerThreadImmediateScopeBuilder,
            )
    }
}
