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
import com.android.systemui.kairos.internal.store.HashMapK
import com.android.systemui.kairos.internal.store.MapHolderK
import com.android.systemui.kairos.internal.store.MapK
import com.android.systemui.kairos.internal.store.MutableMapK
import com.android.systemui.kairos.internal.util.logDuration
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.plus

internal class DemuxNode<W, K, A>(
    val nameData: NameData,
    private val branchNodeByKey: MutableMapK<W, K, DemuxNode<W, K, A>.BranchNode>,
    val lifecycle: DemuxLifecycle<K, A>,
    private val spec: DemuxActivator<W, K, A>,
) : SchedulableNode {

    init {
        nameData.forceInit()
    }

    val schedulable = Schedulable.N(this)

    lateinit var upstreamConnection: NodeConnection<MapK<*, K, A>>
    private var epoch: Long = Long.MIN_VALUE

    fun getAndMaybeAddDownstream(evalScope: EvalScope, key: K): BranchNode =
        branchNodeByKey.getOrPut(key) {
            BranchNode(key).apply {
                if (hasCurrentValueForKey(logIndent = 0, evalScope, key)) {
                    updateBranchEpoch(evalScope)
                }
            }
        }

    override fun schedule(logIndent: Int, evalScope: EvalScope) =
        logDuration(logIndent, { "DemuxNode.schedule" }) {
            val upstreamResult =
                logDuration({ "upstream.getPushEvent" }) {
                    upstreamConnection.getPushEvent(currentLogIndent, evalScope)
                }
            updateEpoch(evalScope)
            upstreamResult.forEach { key, _ ->
                if (branchNodeByKey.containsKey(key)) {
                    val branch = branchNodeByKey.getValue(key)
                    branch.schedule(currentLogIndent, evalScope)
                }
            }
        }

    override fun adjustDirectUpstream(scheduler: Scheduler, oldDepth: Int, newDepth: Int) {
        branchNodeByKey.forEach { _, branchNode ->
            branchNode.downstreamSet.adjustDirectUpstream(scheduler, oldDepth, newDepth)
        }
    }

    override fun moveIndirectUpstreamToDirect(
        scheduler: Scheduler,
        oldIndirectDepth: Int,
        oldIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        newDirectDepth: Int,
    ) {
        branchNodeByKey.forEach { _, branchNode ->
            branchNode.downstreamSet.moveIndirectUpstreamToDirect(
                scheduler,
                oldIndirectDepth,
                oldIndirectSet,
                newDirectDepth,
            )
        }
    }

    override fun adjustIndirectUpstream(
        scheduler: Scheduler,
        oldDepth: Int,
        newDepth: Int,
        removals: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        additions: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        branchNodeByKey.forEach { _, branchNode ->
            branchNode.downstreamSet.adjustIndirectUpstream(
                scheduler,
                oldDepth,
                newDepth,
                removals,
                additions,
            )
        }
    }

    override fun moveDirectUpstreamToIndirect(
        scheduler: Scheduler,
        oldDirectDepth: Int,
        newIndirectDepth: Int,
        newIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        branchNodeByKey.forEach { _, branchNode ->
            branchNode.downstreamSet.moveDirectUpstreamToIndirect(
                scheduler,
                oldDirectDepth,
                newIndirectDepth,
                newIndirectSet,
            )
        }
    }

    override fun removeIndirectUpstream(
        scheduler: Scheduler,
        depth: Int,
        indirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        lifecycle.lifecycleState = DemuxLifecycleState.Dead
        branchNodeByKey.forEach { _, branchNode ->
            branchNode.downstreamSet.removeIndirectUpstream(scheduler, depth, indirectSet)
        }
    }

    override fun removeDirectUpstream(scheduler: Scheduler, depth: Int) {
        lifecycle.lifecycleState = DemuxLifecycleState.Dead
        branchNodeByKey.forEach { _, branchNode ->
            branchNode.downstreamSet.removeDirectUpstream(scheduler, depth)
        }
    }

    fun removeDownstreamAndDeactivateIfNeeded(key: K, evalScope: EvalScope) {
        branchNodeByKey.remove(key)
        val deactivate = branchNodeByKey.isEmpty()
        if (deactivate) {
            lifecycle.lifecycleState = DemuxLifecycleState.Inactive(spec)
            upstreamConnection.removeDownstreamAndDeactivateIfNeeded(
                downstream = schedulable,
                evalScope = evalScope,
            )
        }
    }

    fun updateEpoch(evalScope: EvalScope) {
        epoch = evalScope.epoch
    }

    fun getPushEvent(logIndent: Int, evalScope: EvalScope, key: K): A =
        logDuration(logIndent, { "Demux.getPushEvent($key)" }) {
            upstreamConnection.getPushEvent(currentLogIndent, evalScope).getValue(key)
        }

    override fun toString(): String = "${super.toString()}[$nameData]"

    private fun hasCurrentValueForKey(logIndent: Int, evalScope: EvalScope, key: K): Boolean =
        evalScope.epoch == epoch &&
            upstreamConnection.getPushEvent(logIndent, evalScope).containsKey(key)

    inner class BranchNode(val key: K) : PushNode<A> {

        val downstreamSet = DownstreamSet()
        private var branchEpoch = Long.MIN_VALUE

        override val depthTracker: DepthTracker
            get() = upstreamConnection.depthTracker

        fun updateBranchEpoch(evalScope: EvalScope) {
            branchEpoch = evalScope.epoch
        }

        override fun hasCurrentValue(logIndent: Int, evalScope: EvalScope): Boolean =
            branchEpoch == evalScope.epoch

        override fun getPushEvent(logIndent: Int, evalScope: EvalScope): A =
            getPushEvent(logIndent, evalScope, key)

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
            downstreamSet.remove(downstream)
            val canDeactivate = downstreamSet.isEmpty()
            if (canDeactivate) {
                removeDownstreamAndDeactivateIfNeeded(key, evalScope)
            }
        }

        override fun deactivateIfNeeded(evalScope: EvalScope) {
            if (downstreamSet.isEmpty()) {
                removeDownstreamAndDeactivateIfNeeded(key, evalScope)
            }
        }

        override fun scheduleDeactivationIfNeeded(evalScope: EvalScope) {
            if (downstreamSet.isEmpty()) {
                evalScope.scheduleDeactivation(this)
            }
        }

        fun schedule(logIndent: Int, evalScope: EvalScope) {
            logDuration(logIndent, { "DemuxBranchNode($key).schedule" }) {
                updateBranchEpoch(evalScope)
                if (!scheduleAll(currentLogIndent, downstreamSet, evalScope)) {
                    evalScope.scheduleDeactivation(this@BranchNode)
                }
            }
        }
    }
}

internal fun <K, A> demuxImpl(
    nameData: NameData,
    upstream: EvalScope.() -> EventsImpl<Map<K, A>>,
    numKeys: Int?,
): DemuxImpl<K, A> =
    DemuxImpl(
        nameData,
        DemuxLifecycle(
            nameData,
            DemuxLifecycleState.Inactive(
                DemuxActivator(
                    nameData,
                    numKeys,
                    mapImpl(upstream, nameData + "toMapHolder") { it, _ -> MapHolderK(it) },
                    HashMapK.Factory(),
                )
            ),
        ),
    )

internal class DemuxActivator<W, K, A>(
    private val nameData: NameData,
    private val numKeys: Int?,
    private val upstream: EventsImpl<MapK<*, K, A>>,
    private val storeFactory: MutableMapK.Factory<W, K>,
) {

    init {
        nameData.forceInit()
    }

    fun activate(evalScope: EvalScope, lifecycle: DemuxLifecycle<K, A>): DemuxNode<W, K, A>? {
        val demux = DemuxNode(nameData, storeFactory.create(numKeys), lifecycle, this)
        return upstream.activate(evalScope, demux.schedulable)?.let { (conn, needsEval) ->
            demux.apply {
                upstreamConnection = conn
                if (needsEval) {
                    updateEpoch(evalScope)
                }
            }
        }
    }

    override fun toString(): String = "${super.toString()}[$nameData]"
}

internal class DemuxImpl<in K, out A>(
    val nameData: NameData,
    private val dmux: DemuxLifecycle<K, A>,
) {
    fun eventsForKey(key: K): EventsImpl<A> = EventsImplCheap { downstream ->
        dmux.activate(evalScope = this, key)?.let { branchNode ->
            branchNode.addDownstream(downstream)
            ActivationResult(
                connection = NodeConnection(branchNode, branchNode),
                needsEval = branchNode.hasCurrentValue(0, evalScope = this),
            )
        }
    }

    override fun toString(): String = "${super.toString()}[$nameData]"
}

internal class DemuxLifecycle<K, A>(
    val nameData: NameData,
    var lifecycleState: DemuxLifecycleState<K, A>,
) {

    init {
        nameData.forceInit()
    }

    fun activate(evalScope: EvalScope, key: K): DemuxNode<*, K, A>.BranchNode? =
        when (val state = lifecycleState) {
            is DemuxLifecycleState.Dead -> {
                null
            }

            is DemuxLifecycleState.Active -> {
                state.node.getAndMaybeAddDownstream(evalScope, key)
            }

            is DemuxLifecycleState.Inactive -> {
                state.spec
                    .activate(evalScope, this@DemuxLifecycle)
                    .also { result ->
                        lifecycleState =
                            if (result == null) {
                                DemuxLifecycleState.Dead
                            } else {
                                DemuxLifecycleState.Active(result)
                            }
                    }
                    ?.getAndMaybeAddDownstream(evalScope, key)
            }
        }

    override fun toString(): String = "${super.toString()}[$nameData]"
}

internal sealed interface DemuxLifecycleState<out K, out A> {
    class Inactive<K, A>(val spec: DemuxActivator<*, K, A>) : DemuxLifecycleState<K, A> {
        override fun toString(): String = "Inactive"
    }

    class Active<K, A>(val node: DemuxNode<*, K, A>) : DemuxLifecycleState<K, A> {
        override fun toString(): String = "Active(node=$node)"
    }

    data object Dead : DemuxLifecycleState<Nothing, Nothing>
}
