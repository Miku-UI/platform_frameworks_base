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

import com.android.systemui.kairos.internal.util.logDuration
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.forceInit

internal val neverImpl: EventsImpl<Nothing> = EventsImplCheap { null }

internal class MapNode<A, B>(
    val nameData: NameData,
    val upstream: PullNode<A>,
    val transform: EvalScope.(A, Int) -> B,
) : PullNode<B> {

    init {
        nameData.forceInit()
    }

    override fun getPushEvent(logIndent: Int, evalScope: EvalScope): B =
        logDuration(logIndent, { "MapNode.getPushEvent" }) {
            val upstream =
                logDuration({ "upstream event" }) {
                    upstream.getPushEvent(currentLogIndent, evalScope)
                }
            logDuration({ "transform" }) { evalScope.transform(upstream, currentLogIndent) }
        }

    override fun toString(): String = "${super.toString()}[$nameData]"
}

internal inline fun <A, B> mapImpl(
    crossinline upstream: EvalScope.() -> EventsImpl<A>,
    nameData: NameData,
    noinline transform: EvalScope.(A, Int) -> B,
): EventsImpl<B> = EventsImplCheap { downstream ->
    upstream().activate(evalScope = this, downstream)?.let { (connection, needsEval) ->
        ActivationResult(
            connection =
                NodeConnection(
                    directUpstream = MapNode(nameData, connection.directUpstream, transform),
                    schedulerUpstream = connection.schedulerUpstream,
                ),
            needsEval = needsEval,
        )
    }
}

internal class CachedNode<A>(
    val nameData: NameData,
    private val transactionCache: TransactionCache<Lazy<A>>,
    val upstream: PullNode<A>,
) : PullNode<A> {

    init {
        nameData.forceInit()
    }

    override fun getPushEvent(logIndent: Int, evalScope: EvalScope): A =
        logDuration(logIndent, { "CachedNode.getPushEvent" }) {
            val deferred =
                logDuration({ "CachedNode.getOrPut" }, start = false) {
                    transactionCache.getOrPut(evalScope) {
                        evalScope.deferAsync {
                            logDuration({ "CachedNode.getUpstreamEvent" }) {
                                upstream.getPushEvent(currentLogIndent, evalScope)
                            }
                        }
                    }
                }
            logDuration({ "await" }) { deferred.value }
        }

    override fun toString(): String = "${super.toString()}[$nameData]"
}

internal fun <A> EventsImpl<A>.cached(nameData: NameData): EventsImpl<A> {
    val key = TransactionCache<Lazy<A>>()
    return EventsImplCheap { it ->
        activate(this, it)?.let { (connection, needsEval) ->
            ActivationResult(
                connection =
                    NodeConnection(
                        directUpstream = CachedNode(nameData, key, connection.directUpstream),
                        schedulerUpstream = connection.schedulerUpstream,
                    ),
                needsEval = needsEval,
            )
        }
    }
}
