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
import com.android.systemui.kairos.internal.store.FastIterable
import com.android.systemui.kairos.internal.store.MapK
import com.android.systemui.kairos.internal.store.MutableMapK
import com.android.systemui.kairos.internal.util.fastForEach
import com.android.systemui.kairos.internal.util.hashString
import com.android.systemui.kairos.internal.util.logDuration
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.forceInit

internal typealias MuxResult<W, K, V> = MapK<W, K, PullNode<V>>

/** Base class for muxing nodes, which have a (potentially dynamic) collection of upstream nodes. */
internal sealed class MuxNode<W, K, V, R>(
    val nameData: NameData,
    val lifecycle: MuxLifecycle<W, K, V, R>,
) : PushNode<R> {

    init {
        nameData.forceInit()
    }

    lateinit var upstreamData: MutableMapK<W, K, PullNode<V>>
    lateinit var switchedIn: MutableMapK<W, K, BranchNode>

    var markedForCompaction = false
    var markedForEvaluation = false

    val downstreamSet: DownstreamSet = DownstreamSet()

    // TODO: inline DepthTracker? would need to be added to PushNode signature
    final override val depthTracker = DepthTracker()

    val transactionCache = TransactionCache<R>()
    val epoch
        get() = transactionCache.epoch

    final override fun getPushEvent(logIndent: Int, evalScope: EvalScope): R =
        logDuration(logIndent, { "Mux[$this].getPushEvent" }) {
            transactionCache.getCurrentValue(evalScope)
        }

    @Suppress("NOTHING_TO_INLINE")
    inline fun hasCurrentValue(evalScope: EvalScope): Boolean = epoch == evalScope.epoch

    final override fun hasCurrentValue(logIndent: Int, evalScope: EvalScope): Boolean =
        hasCurrentValue(evalScope)

    final override fun addDownstream(downstream: Schedulable) {
        downstreamSet.add(downstream)
    }

    final override fun removeDownstream(downstream: Schedulable) {
        // TODO: return boolean?
        downstreamSet.remove(downstream)
    }

    final override fun removeDownstreamAndDeactivateIfNeeded(
        downstream: Schedulable,
        evalScope: EvalScope,
    ) {
        downstreamSet.remove(downstream)
        val deactivate = downstreamSet.isEmpty()
        if (deactivate) {
            doDeactivate(evalScope)
        }
    }

    final override fun deactivateIfNeeded(evalScope: EvalScope) {
        if (downstreamSet.isEmpty()) {
            doDeactivate(evalScope)
        }
    }

    /** visit this node from the scheduler (push eval) */
    abstract fun visit(logIndent: Int, evalScope: EvalScope)

    /** perform deactivation logic, propagating to all upstream nodes. */
    protected abstract fun doDeactivate(evalScope: EvalScope)

    final override fun scheduleDeactivationIfNeeded(evalScope: EvalScope) {
        if (downstreamSet.isEmpty()) {
            evalScope.scheduleDeactivation(this)
        }
    }

    fun adjustDirectUpstream(scheduler: Scheduler, oldDepth: Int, newDepth: Int) {
        if (depthTracker.addDirectUpstream(oldDepth, newDepth)) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun moveIndirectUpstreamToDirect(
        scheduler: Scheduler,
        oldIndirectDepth: Int,
        oldIndirectRoots: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        newDepth: Int,
    ) {
        if (
            depthTracker.addDirectUpstream(oldDepth = null, newDepth) or
                depthTracker.removeIndirectUpstream(depth = oldIndirectDepth) or
                depthTracker.updateIndirectRoots(removals = oldIndirectRoots)
        ) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun adjustIndirectUpstream(
        scheduler: Scheduler,
        oldDepth: Int,
        newDepth: Int,
        removals: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        additions: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        if (
            depthTracker.addIndirectUpstream(oldDepth, newDepth) or
                depthTracker.updateIndirectRoots(
                    additions,
                    removals,
                    butNot = this as? MuxDeferredNode<*, *, *, *>,
                )
        ) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun moveDirectUpstreamToIndirect(
        scheduler: Scheduler,
        oldDepth: Int,
        newDepth: Int,
        newIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        if (
            depthTracker.addIndirectUpstream(oldDepth = null, newDepth) or
                depthTracker.removeDirectUpstream(oldDepth) or
                depthTracker.updateIndirectRoots(
                    additions = newIndirectSet,
                    butNot = this as? MuxDeferredNode<*, *, *, *>,
                )
        ) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun removeDirectUpstream(scheduler: Scheduler, depth: Int, key: K) {
        switchedIn.remove(key)
        if (depthTracker.removeDirectUpstream(depth)) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun removeIndirectUpstream(
        scheduler: Scheduler,
        oldDepth: Int,
        indirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        key: K,
    ) {
        switchedIn.remove(key)
        if (
            depthTracker.removeIndirectUpstream(oldDepth) or
                depthTracker.updateIndirectRoots(removals = indirectSet)
        ) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun visitCompact(scheduler: Scheduler) {
        if (depthTracker.isDirty()) {
            depthTracker.applyChanges(scheduler, downstreamSet, this@MuxNode)
        }
    }

    fun schedule(evalScope: EvalScope) {
        // TODO: Potential optimization
        //  Detect if this node is guaranteed to have a single upstream within this transaction,
        //  then bypass scheduling it. Instead immediately schedule its downstream and treat this
        //  MuxNode as a Pull (effectively making it a mapCheap).
        depthTracker.schedule(evalScope.scheduler, this)
    }

    override fun toString(): String = "${super.toString()}[$nameData]"

    /** An input branch of a mux node, associated with a key. */
    inner class BranchNode(val key: K) : SchedulableNode {

        val schedulable = Schedulable.N(this)

        lateinit var upstream: NodeConnection<V>

        override fun schedule(logIndent: Int, evalScope: EvalScope) {
            logDuration(logIndent, { "MuxBranchNode.schedule" }) {
                upstreamData[key] = upstream.directUpstream
                this@MuxNode.schedule(evalScope)
            }
        }

        override fun adjustDirectUpstream(scheduler: Scheduler, oldDepth: Int, newDepth: Int) {
            this@MuxNode.adjustDirectUpstream(scheduler, oldDepth, newDepth)
        }

        override fun moveIndirectUpstreamToDirect(
            scheduler: Scheduler,
            oldIndirectDepth: Int,
            oldIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
            newDirectDepth: Int,
        ) {
            this@MuxNode.moveIndirectUpstreamToDirect(
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
            this@MuxNode.adjustIndirectUpstream(scheduler, oldDepth, newDepth, removals, additions)
        }

        override fun moveDirectUpstreamToIndirect(
            scheduler: Scheduler,
            oldDirectDepth: Int,
            newIndirectDepth: Int,
            newIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        ) {
            this@MuxNode.moveDirectUpstreamToIndirect(
                scheduler,
                oldDirectDepth,
                newIndirectDepth,
                newIndirectSet,
            )
        }

        override fun removeDirectUpstream(scheduler: Scheduler, depth: Int) {
            removeDirectUpstream(scheduler, depth, key)
        }

        override fun removeIndirectUpstream(
            scheduler: Scheduler,
            depth: Int,
            indirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        ) {
            removeIndirectUpstream(scheduler, depth, indirectSet, key)
        }

        override fun toString(): String = "MuxBranchNode(key=$key, mux=${this@MuxNode})"
    }
}

/** Tracks lifecycle of MuxNode in the network. Essentially a mutable ref for MuxLifecycleState. */
internal class MuxLifecycle<W, K, V, R>(var lifecycleState: MuxLifecycleState<W, K, V, R>) :
    EventsImpl<R> {

    override fun toString(): String = "MuxLifecycle[$hashString][$lifecycleState]"

    override fun activate(evalScope: EvalScope, downstream: Schedulable): ActivationResult<R>? =
        when (val state = lifecycleState) {
            is MuxLifecycleState.Dead -> {
                null
            }

            is MuxLifecycleState.Active -> {
                state.node.addDownstream(downstream)
                ActivationResult(
                    connection = NodeConnection(state.node, state.node),
                    needsEval = state.node.hasCurrentValue(evalScope),
                )
            }

            is MuxLifecycleState.Inactive -> {
                state.spec
                    .activate(evalScope, this@MuxLifecycle)
                    .also { node ->
                        lifecycleState =
                            if (node == null) {
                                MuxLifecycleState.Dead
                            } else {
                                MuxLifecycleState.Active(node.first)
                            }
                    }
                    ?.let { (node, postActivate) ->
                        if (postActivate?.invoke() == false && node.switchedIn.isEmpty()) {
                            lifecycleState = MuxLifecycleState.Dead
                            null
                        } else {
                            node.addDownstream(downstream)
                            ActivationResult(
                                connection = NodeConnection(node, node),
                                needsEval = false,
                            )
                        }
                    }
            }
        }
}

internal sealed interface MuxLifecycleState<out W, out K, out V, out R> {
    class Inactive<W, K, V, R>(val spec: MuxActivator<W, K, V, R>) : MuxLifecycleState<W, K, V, R> {
        override fun toString(): String = "Inactive"
    }

    class Active<W, K, V, R>(val node: MuxNode<W, K, V, R>) : MuxLifecycleState<W, K, V, R> {
        override fun toString(): String = "Active(node=$node)"
    }

    data object Dead : MuxLifecycleState<Nothing, Nothing, Nothing, Nothing>
}

internal interface MuxActivator<W, K, V, R> {
    fun activate(
        evalScope: EvalScope,
        lifecycle: MuxLifecycle<W, K, V, R>,
    ): Pair<MuxNode<W, K, V, R>, (() -> Boolean)?>?
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun <W, K, V, R> MuxLifecycle(
    onSubscribe: MuxActivator<W, K, V, R>
): EventsImpl<R> = MuxLifecycle(MuxLifecycleState.Inactive(onSubscribe))

// activation logic

internal fun <W, K, V, R> MuxNode<W, K, V, R>.initializeUpstream(
    evalScope: EvalScope,
    getStorage: EvalScope.() -> FastIterable<K, EventsImpl<V>>,
    storeFactory: MutableMapK.Factory<W, K>,
) {
    val storage = getStorage(evalScope)
    val initUpstream = buildList {
        storage.forEach { key, events ->
            val branchNode = BranchNode(key)
            add(
                events.activate(evalScope, branchNode.schedulable)?.let { (conn, needsEval) ->
                    Triple(
                        key,
                        branchNode.apply { upstream = conn },
                        if (needsEval) conn.directUpstream else null,
                    )
                }
            )
        }
    }
    switchedIn = storeFactory.create(initUpstream.size)
    upstreamData = storeFactory.create(initUpstream.size)
    initUpstream.fastForEach {
        it?.let { (key, branch, upstream) ->
            switchedIn[key] = branch
            upstream?.let { upstreamData[key] = upstream }
        }
    }
}

internal fun <W, K, V, R> MuxNode<W, K, V, R>.initializeDepth() {
    switchedIn.forEach { _, branch ->
        val conn = branch.upstream
        if (conn.depthTracker.snapshotIsDirect) {
            depthTracker.addDirectUpstream(
                oldDepth = null,
                newDepth = conn.depthTracker.snapshotDirectDepth,
            )
        } else {
            depthTracker.addIndirectUpstream(
                oldDepth = null,
                newDepth = conn.depthTracker.snapshotIndirectDepth,
            )
            depthTracker.updateIndirectRoots(
                additions = conn.depthTracker.snapshotIndirectRoots,
                butNot = this as? MuxDeferredNode<*, *, *, *>,
            )
        }
    }
}
