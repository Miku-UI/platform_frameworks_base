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

import androidx.collection.ScatterSet
import com.android.systemui.kairos.internal.util.logDuration
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.isPresent
import com.android.systemui.kairos.util.orElseGet
import com.android.systemui.kairos.util.plus

internal class FilterNode<A>(
    val nameData: NameData,
    val lifecycle: FilterLifecycle<A>,
    private val spec: FilterActivator<A>,
    private val predicate: EvalScope.(A) -> Boolean,
) : SchedulableNode, PushNode<A> {

    init {
        nameData.forceInit()
    }

    val schedulable = Schedulable.N(this)

    private val downstreamSet = DownstreamSet()
    lateinit var upstreamConnection: NodeConnection<A>
    private var epoch: Long = Long.MIN_VALUE

    @Suppress("NOTHING_TO_INLINE")
    inline fun hasCurrentValue(evalScope: EvalScope): Boolean = evalScope.epoch == epoch

    override fun schedule(logIndent: Int, evalScope: EvalScope) =
        logDuration(logIndent, { "FilterNode.schedule" }) {
            val upstreamResult =
                logDuration({ "upstream.getPushEvent" }) {
                    upstreamConnection.getPushEvent(currentLogIndent, evalScope)
                }
            if (evalScope.predicate(upstreamResult)) {
                updateEpoch(evalScope)
                if (!scheduleAll(currentLogIndent, downstreamSet, evalScope)) {
                    evalScope.scheduleDeactivation(this@FilterNode)
                }
            }
        }

    override fun adjustDirectUpstream(scheduler: Scheduler, oldDepth: Int, newDepth: Int) {
        downstreamSet.adjustDirectUpstream(scheduler, oldDepth, newDepth)
    }

    override fun moveIndirectUpstreamToDirect(
        scheduler: Scheduler,
        oldIndirectDepth: Int,
        oldIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        newDirectDepth: Int,
    ) {
        downstreamSet.moveIndirectUpstreamToDirect(
            scheduler,
            oldIndirectDepth,
            oldIndirectSet,
            newDirectDepth,
        )
    }

    override fun adjustIndirectUpstream(
        scheduler: Scheduler,
        oldDepth: Int,
        newDepth: Int,
        removals: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        additions: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        downstreamSet.adjustIndirectUpstream(scheduler, oldDepth, newDepth, removals, additions)
    }

    override fun moveDirectUpstreamToIndirect(
        scheduler: Scheduler,
        oldDirectDepth: Int,
        newIndirectDepth: Int,
        newIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        downstreamSet.moveDirectUpstreamToIndirect(
            scheduler,
            oldDirectDepth,
            newIndirectDepth,
            newIndirectSet,
        )
    }

    override fun removeIndirectUpstream(
        scheduler: Scheduler,
        depth: Int,
        indirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        lifecycle.lifecycleState = FilterLifecycleState.Dead
        downstreamSet.removeIndirectUpstream(scheduler, depth, indirectSet)
    }

    override fun removeDirectUpstream(scheduler: Scheduler, depth: Int) {
        lifecycle.lifecycleState = FilterLifecycleState.Dead
        downstreamSet.removeDirectUpstream(scheduler, depth)
    }

    fun updateEpoch(evalScope: EvalScope) {
        epoch = evalScope.epoch
    }

    override fun toString(): String = "${super.toString()}[$nameData]"

    override val depthTracker: DepthTracker
        get() = upstreamConnection.depthTracker

    override fun hasCurrentValue(logIndent: Int, evalScope: EvalScope): Boolean =
        hasCurrentValue(evalScope)

    override fun getPushEvent(logIndent: Int, evalScope: EvalScope): A =
        logDuration(logIndent, { "Filter.getPushEvent()" }) {
            upstreamConnection.getPushEvent(currentLogIndent, evalScope)
        }

    override fun addDownstream(downstream: Schedulable) {
        downstreamSet.add(downstream)
    }

    override fun removeDownstream(downstream: Schedulable) {
        downstreamSet.remove(downstream)
    }

    override fun removeDownstreamAndDeactivateIfNeeded(
        downstream: Schedulable,
        evalScope: EvalScope,
    ) {
        removeDownstream(downstream)
        deactivateIfNeeded(evalScope)
    }

    override fun deactivateIfNeeded(evalScope: EvalScope) {
        if (downstreamSet.isEmpty()) {
            lifecycle.lifecycleState = FilterLifecycleState.Inactive(spec)
            upstreamConnection.removeDownstreamAndDeactivateIfNeeded(
                downstream = schedulable,
                evalScope = evalScope,
            )
        }
    }

    override fun scheduleDeactivationIfNeeded(evalScope: EvalScope) {
        if (downstreamSet.isEmpty()) {
            evalScope.scheduleDeactivation(this)
        }
    }
}

internal class FilterActivator<A>(
    private val nameData: NameData,
    private val getUpstream: EvalScope.() -> EventsImpl<A>,
    private val predicate: EvalScope.(A) -> Boolean,
) {

    init {
        nameData.forceInit()
    }

    fun activate(evalScope: EvalScope, lifecycle: FilterLifecycle<A>): FilterNode<A>? {
        val filter = FilterNode(nameData, lifecycle, this, predicate)
        return evalScope.getUpstream().activate(evalScope, filter.schedulable)?.let {
            (conn, needsEval) ->
            filter.apply {
                upstreamConnection = conn
                if (needsEval && evalScope.predicate(conn.getPushEvent(logIndent = 0, evalScope))) {
                    updateEpoch(evalScope)
                }
            }
        }
    }

    override fun toString(): String = "${super.toString()}[$nameData]"
}

internal class FilterLifecycle<A>(
    val nameData: NameData,
    var lifecycleState: FilterLifecycleState<A>,
) : EventsImpl<A> {

    init {
        nameData.forceInit()
    }

    override fun activate(evalScope: EvalScope, downstream: Schedulable): ActivationResult<A>? =
        when (val state = lifecycleState) {
            is FilterLifecycleState.Dead -> {
                null
            }

            is FilterLifecycleState.Active -> {
                state.node.addDownstream(downstream)
                ActivationResult(
                    connection = NodeConnection(state.node, state.node),
                    needsEval = state.node.hasCurrentValue(evalScope),
                )
            }

            is FilterLifecycleState.Inactive -> {
                state.spec
                    .activate(evalScope, this@FilterLifecycle)
                    .also { result ->
                        lifecycleState =
                            if (result == null) {
                                FilterLifecycleState.Dead
                            } else {
                                FilterLifecycleState.Active(result)
                            }
                    }
                    ?.let { filterNode ->
                        filterNode.addDownstream(downstream)
                        ActivationResult(
                            connection = NodeConnection(filterNode, filterNode),
                            needsEval = filterNode.hasCurrentValue(evalScope),
                        )
                    }
            }
        }

    override fun toString(): String = "${super.toString()}[$nameData]"
}

internal sealed interface FilterLifecycleState<out A> {
    class Inactive<A>(val spec: FilterActivator<A>) : FilterLifecycleState<A> {
        override fun toString(): String = "Inactive"
    }

    class Active<A>(val node: FilterNode<A>) : FilterLifecycleState<A> {
        override fun toString(): String = "Active(node=$node)"
    }

    data object Dead : FilterLifecycleState<Nothing>
}

internal fun <A> filterPresentImpl(
    nameData: NameData,
    getPulse: EvalScope.() -> EventsImpl<Maybe<A>>,
): EventsImpl<A> {
    val filter = filterImpl(nameData, getPulse) { ma -> ma.isPresent() }
    return mapImpl({ filter }, nameData + "fromMaybe") { ma, _ ->
        ma.orElseGet { error("unexpected missing filter result") }
    }
}

internal fun <A> filterImpl(
    nameData: NameData,
    getPulse: EvalScope.() -> EventsImpl<A>,
    predicate: EvalScope.(A) -> Boolean,
): EventsImpl<A> =
    FilterLifecycle(
        nameData,
        FilterLifecycleState.Inactive(FilterActivator(nameData, getPulse, predicate)),
    )
