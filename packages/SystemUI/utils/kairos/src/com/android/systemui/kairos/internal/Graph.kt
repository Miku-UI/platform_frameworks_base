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

import androidx.collection.MutableScatterSet
import androidx.collection.ScatterSet
import androidx.collection.mutableScatterSetOf
import com.android.systemui.kairos.internal.util.Bag
import com.android.systemui.kairos.internal.util.fastForEach
import java.util.TreeMap

internal val InputTracker = DepthTracker().apply { snapshotIsDirect = true }

/**
 * Tracks all upstream connections for Mux nodes.
 *
 * Connections come in two flavors:
 * 1. **DIRECT** :: The upstream node may emit events that would cause the owner of this depth
 *    tracker to also emit.
 * 2. **INDIRECT** :: The upstream node will not emit events, but may start doing so in a future
 *    transaction (at which point its depth will change to DIRECT).
 *
 * DIRECT connections are the standard, active connections that propagate events through the graph.
 * They are used to calculate the evaluation depth of a node, so that it is only visited once it is
 * certain that all DIRECT upstream connections have already been visited (or are not emitting in
 * the current transaction).
 *
 * It is *invalid* for a node to be directly upstream of itself. Doing so is an error.
 *
 * INDIRECT connections identify nodes that are still "alive" (should not be garbage-collected) but
 * are presently "dormant". This only occurs when a MuxDeferredNode has nothing switched-in, but is
 * still connected to its "patches" upstream node, implying that something *may* be switched-in at a
 * later time.
 *
 * It is *invalid* for a node to be indirectly upstream of itself. These connections are
 * automatically filtered out.
 *
 * When there are no connections, either DIRECT or INDIRECT, a node *dies* and all incoming/outgoing
 * connections are freed so that it can be garbage-collected.
 *
 * Note that there is an edge case where a MuxDeferredNode is connected to itself via its "patches"
 * upstream node. In this case:
 * 1. If the node has switched-in upstream nodes, then this is perfectly valid. Downstream nodes
 *    will see a direct connection to this MuxDeferredNode.
 * 2. Otherwise, the node would normally be considered "dormant" and downstream nodes would see an
 *    indirect connection. However, because a node cannot be indirectly upstream of itself, then the
 *    MuxDeferredNode sees no connection via its patches upstream node, and so is considered "dead".
 *    Conceptually, this makes some sense: The only way for this recursive MuxDeferredNode to become
 *    non-dormant is to switch some upstream nodes back in, but since the patches node is itself,
 *    this will never happen.
 *
 * This behavior underpins the recursive definition of `nextOnly`.
 */
internal class DepthTracker {

    var snapshotIsDirect = false
    private var snapshotIsIndirectRoot = false

    var snapshotIndirectDepth: Int = 0
    var snapshotDirectDepth: Int = 0

    private val _snapshotIndirectRoots = MutableScatterSet<MuxDeferredNode<*, *, *>>()

    val snapshotIndirectRoots: ScatterSet<MuxDeferredNode<*, *, *>>
        get() = _snapshotIndirectRoots.toScatterSet()

    private val indirectAdditions = MutableScatterSet<MuxDeferredNode<*, *, *>>()
    private val indirectRemovals = MutableScatterSet<MuxDeferredNode<*, *, *>>()

    private inline val trackIndirectRootDiffs
        get() = !snapshotIsDirect // && !snapshotIsIndirectRoot

    private val dirty_directUpstreamDepths = TreeMap<Int, Int>()
    private val dirty_indirectUpstreamDepths = TreeMap<Int, Int>()
    private val dirty_indirectUpstreamRoots = Bag<MuxDeferredNode<*, *, *>>()
    var dirty_directDepth = 0
    private var dirty_indirectDepth = 0
    private var dirty_depthIsDirect = false
    private var dirty_isIndirectRoot = false

    fun schedule(scheduler: Scheduler, node: MuxNode<*, *, *>) {
        if (dirty_depthIsDirect) {
            scheduler.schedule(dirty_directDepth, node)
        } else {
            scheduler.scheduleIndirect(dirty_indirectDepth, node)
        }
    }

    // only used by MuxDeferred
    // and only when there is a direct connection to the patch node
    fun setIsIndirectRoot(isRoot: Boolean): Boolean {
        if (isRoot != dirty_isIndirectRoot) {
            dirty_isIndirectRoot = isRoot
            return !dirty_depthIsDirect
        }
        return false
    }

    // adds an upstream connection, and recalcs depth
    // returns true if depth has changed
    fun addDirectUpstream(oldDepth: Int?, newDepth: Int): Boolean {
        if (oldDepth != null) {
            dirty_directUpstreamDepths.compute(oldDepth) { _, count ->
                count?.minus(1)?.takeIf { it > 0 }
            }
        }
        dirty_directUpstreamDepths.compute(newDepth) { _, current -> current?.plus(1) ?: 1 }
        return recalcDepth()
    }

    private fun recalcDepth(): Boolean {
        val newDepth =
            dirty_directUpstreamDepths.lastEntry()?.let { (maxDepth, _) -> maxDepth + 1 } ?: 0

        val isDirect = dirty_directUpstreamDepths.isNotEmpty()
        val isDirectChanged = dirty_depthIsDirect != isDirect
        dirty_depthIsDirect = isDirect

        return (newDepth != dirty_directDepth).also { dirty_directDepth = newDepth } or
            isDirectChanged
    }

    private fun recalcIndirDepth(): Boolean {
        val newDepth =
            dirty_indirectUpstreamDepths.lastEntry()?.let { (maxDepth, _) -> maxDepth + 1 } ?: 0
        return (!dirty_depthIsDirect && !dirty_isIndirectRoot && newDepth != dirty_indirectDepth)
            .also { dirty_indirectDepth = newDepth }
    }

    fun removeDirectUpstream(depth: Int): Boolean {
        dirty_directUpstreamDepths.compute(depth) { _, count -> count?.minus(1)?.takeIf { it > 0 } }
        return recalcDepth()
    }

    fun addIndirectUpstream(oldDepth: Int?, newDepth: Int): Boolean =
        if (oldDepth == newDepth) {
            false
        } else {
            if (oldDepth != null) {
                dirty_indirectUpstreamDepths.compute(oldDepth) { _, current ->
                    current?.minus(1)?.takeIf { it > 0 }
                }
            }
            dirty_indirectUpstreamDepths.compute(newDepth) { _, current -> current?.plus(1) ?: 1 }
            recalcIndirDepth()
        }

    fun removeIndirectUpstream(depth: Int): Boolean {
        dirty_indirectUpstreamDepths.compute(depth) { _, current ->
            current?.minus(1)?.takeIf { it > 0 }
        }
        return recalcIndirDepth()
    }

    fun updateIndirectRoots(
        additions: ScatterSet<MuxDeferredNode<*, *, *>>? = null,
        removals: ScatterSet<MuxDeferredNode<*, *, *>>? = null,
        butNot: MuxDeferredNode<*, *, *>? = null,
    ): Boolean {
        val addsChanged =
            additions
                ?.let { dirty_indirectUpstreamRoots.addAll(additions, butNot) }
                ?.let { newlyAdded ->
                    if (trackIndirectRootDiffs) {
                        val remainder = indirectRemovals.applyRemovalDiff(newlyAdded)
                        indirectAdditions.addAll(remainder)
                    }
                    true
                } ?: false
        val removalsChanged =
            removals
                ?.let { dirty_indirectUpstreamRoots.removeAll(removals) }
                ?.let { fullyRemoved ->
                    if (trackIndirectRootDiffs) {
                        val remainder = indirectAdditions.applyRemovalDiff(fullyRemoved)
                        indirectRemovals.addAll(remainder)
                    }
                    true
                } ?: false
        return (!dirty_depthIsDirect && (addsChanged || removalsChanged))
    }

    private fun <T> MutableScatterSet<T>.applyRemovalDiff(changeSet: ScatterSet<T>): ScatterSet<T> {
        if (isEmpty()) return changeSet
        val remainder = mutableScatterSetOf<T>()
        changeSet.forEach { element ->
            if (!remove(element)) {
                remainder.add(element)
            }
        }
        return remainder
    }

    fun propagateChanges(scheduler: Scheduler, muxNode: MuxNode<*, *, *>) {
        if (isDirty()) {
            schedule(scheduler, muxNode)
        }
    }

    private fun <T> buildScatterSet(block: MutableScatterSet<T>.() -> Unit): ScatterSet<T> =
        mutableScatterSetOf<T>().apply(block)

    private fun <T> ScatterSet<T>.toScatterSet(): ScatterSet<T> = buildScatterSet {
        addAll(this@toScatterSet)
    }

    fun applyChanges(scheduler: Scheduler, downstreamSet: DownstreamSet, owner: MuxNode<*, *, *>) {
        when {
            dirty_depthIsDirect -> {
                if (snapshotIsDirect) {
                    val oldDepth = snapshotDirectDepth
                    reset(owner as? MuxDeferredNode<*, *, *>)
                    downstreamSet.adjustDirectUpstream(
                        scheduler,
                        oldDepth = oldDepth,
                        newDepth = dirty_directDepth,
                    )
                } else {
                    val oldIndirectDepth = snapshotIndirectDepth
                    val oldIndirectSet = snapshotIndirectRoots
                    reset(owner as? MuxDeferredNode<*, *, *>)
                    downstreamSet.moveIndirectUpstreamToDirect(
                        scheduler,
                        oldIndirectDepth = oldIndirectDepth,
                        oldIndirectSet = oldIndirectSet,
                        newDirectDepth = dirty_directDepth,
                    )
                }
            }

            dirty_hasIndirectUpstream() || dirty_isIndirectRoot -> {
                if (snapshotIsDirect) {
                    val oldDirectDepth = snapshotDirectDepth
                    val newIndirectSet = buildScatterSet {
                        dirty_indirectUpstreamRoots.addAllKeysTo(this)
                        if (dirty_isIndirectRoot) {
                            add(owner as MuxDeferredNode<*, *, *>)
                        }
                    }
                    reset(owner as? MuxDeferredNode<*, *, *>)
                    downstreamSet.moveDirectUpstreamToIndirect(
                        scheduler,
                        oldDirectDepth = oldDirectDepth,
                        newIndirectDepth = dirty_indirectDepth,
                        newIndirectSet = newIndirectSet,
                    )
                } else {
                    val oldDepth = snapshotIndirectDepth
                    val wasIndirectRoot = snapshotIsIndirectRoot
                    val removals = buildScatterSet {
                        addAll(indirectRemovals)
                        if (wasIndirectRoot && !dirty_isIndirectRoot) {
                            add(owner as MuxDeferredNode<*, *, *>)
                        }
                    }
                    val additions = buildScatterSet {
                        addAll(indirectAdditions)
                        if (!wasIndirectRoot && dirty_isIndirectRoot) {
                            add(owner as MuxDeferredNode<*, *, *>)
                        }
                    }
                    reset(owner as? MuxDeferredNode<*, *, *>)
                    downstreamSet.adjustIndirectUpstream(
                        scheduler,
                        oldDepth = oldDepth,
                        newDepth = dirty_indirectDepth,
                        removals = removals,
                        additions = additions,
                    )
                }
            }

            else -> {
                // die
                owner.lifecycle.lifecycleState = MuxLifecycleState.Dead

                if (snapshotIsDirect) {
                    downstreamSet.removeDirectUpstream(scheduler, depth = snapshotDirectDepth)
                } else {
                    downstreamSet.removeIndirectUpstream(
                        scheduler,
                        depth = snapshotIndirectDepth,
                        indirectSet = snapshotIndirectRoots,
                    )
                }
            }
        }
    }

    fun dirty_hasDirectUpstream(): Boolean = dirty_depthIsDirect

    private fun dirty_hasIndirectUpstream(): Boolean = dirty_indirectUpstreamRoots.isNotEmpty()

    override fun toString(): String =
        "DepthTracker(" +
            "sIsDirect=$snapshotIsDirect, " +
            "sDirectDepth=$snapshotDirectDepth, " +
            "sIndirectDepth=$snapshotIndirectDepth, " +
            "sIndirectRoots=$snapshotIndirectRoots, " +
            "dIsIndirectRoot=$dirty_isIndirectRoot, " +
            "dDirectDepths=$dirty_directUpstreamDepths, " +
            "dIndirectDepths=$dirty_indirectUpstreamDepths, " +
            "dIndirectRoots=$dirty_indirectUpstreamRoots" +
            ")"

    fun reset(owner: MuxDeferredNode<*, *, *>?) {
        snapshotIsDirect = dirty_depthIsDirect
        snapshotDirectDepth = dirty_directDepth
        snapshotIndirectDepth = dirty_indirectDepth
        if (
            indirectAdditions.isNotEmpty() ||
                indirectRemovals.isNotEmpty() ||
                snapshotIsIndirectRoot != dirty_isIndirectRoot
        ) {
            _snapshotIndirectRoots.clear()
            dirty_indirectUpstreamRoots.addAllKeysTo(_snapshotIndirectRoots)
            if (dirty_isIndirectRoot) {
                _snapshotIndirectRoots.add(owner!!)
            }
        }
        snapshotIsIndirectRoot = dirty_isIndirectRoot
        indirectAdditions.clear()
        indirectRemovals.clear()
    }

    fun isDirty(): Boolean =
        when {
            snapshotIsDirect -> !dirty_depthIsDirect || snapshotDirectDepth != dirty_directDepth
            else ->
                dirty_depthIsDirect ||
                    snapshotIsIndirectRoot != dirty_isIndirectRoot ||
                    snapshotIndirectDepth != dirty_indirectDepth ||
                    indirectAdditions.isNotEmpty() ||
                    indirectRemovals.isNotEmpty()
        }

    fun dirty_depthIncreased(): Boolean =
        snapshotDirectDepth < dirty_directDepth || !snapshotIsDirect && dirty_depthIsDirect
}

/**
 * Tracks downstream nodes to be scheduled when the owner of this DownstreamSet produces a value in
 * a transaction.
 */
internal class DownstreamSet {

    val outputs = MutableScatterSet<Output<*>>(initialCapacity = 0)
    val stateWriters = ArrayList<StateSource<*>>(/* initialCapacity= */ 0)
    val muxMovers = MutableScatterSet<MuxDeferredNode<*, *, *>>(initialCapacity = 0)
    val nodes = MutableScatterSet<SchedulableNode>(initialCapacity = 0)

    fun add(schedulable: Schedulable) {
        when (schedulable) {
            is Schedulable.S -> stateWriters.add(schedulable.state)
            is Schedulable.M -> muxMovers.add(schedulable.muxMover)
            is Schedulable.N -> nodes.add(schedulable.node)
            is Schedulable.O -> outputs.add(schedulable.output)
        }
    }

    fun remove(schedulable: Schedulable) {
        when (schedulable) {
            is Schedulable.S -> error("WTF: latches are never removed")
            is Schedulable.M -> muxMovers.remove(schedulable.muxMover)
            is Schedulable.N -> nodes.remove(schedulable.node)
            is Schedulable.O -> outputs.remove(schedulable.output)
        }
    }

    fun adjustDirectUpstream(scheduler: Scheduler, oldDepth: Int, newDepth: Int) {
        nodes.forEach { node -> node.adjustDirectUpstream(scheduler, oldDepth, newDepth) }
    }

    fun moveIndirectUpstreamToDirect(
        scheduler: Scheduler,
        oldIndirectDepth: Int,
        oldIndirectSet: ScatterSet<MuxDeferredNode<*, *, *>>,
        newDirectDepth: Int,
    ) {
        nodes.forEach { node ->
            node.moveIndirectUpstreamToDirect(
                scheduler,
                oldIndirectDepth,
                oldIndirectSet,
                newDirectDepth,
            )
        }
        muxMovers.forEach { mover ->
            mover.moveIndirectPatchNodeToDirect(scheduler, oldIndirectDepth, oldIndirectSet)
        }
    }

    fun adjustIndirectUpstream(
        scheduler: Scheduler,
        oldDepth: Int,
        newDepth: Int,
        removals: ScatterSet<MuxDeferredNode<*, *, *>>,
        additions: ScatterSet<MuxDeferredNode<*, *, *>>,
    ) {
        nodes.forEach { node ->
            node.adjustIndirectUpstream(scheduler, oldDepth, newDepth, removals, additions)
        }
        muxMovers.forEach { mover ->
            mover.adjustIndirectPatchNode(scheduler, oldDepth, newDepth, removals, additions)
        }
    }

    fun moveDirectUpstreamToIndirect(
        scheduler: Scheduler,
        oldDirectDepth: Int,
        newIndirectDepth: Int,
        newIndirectSet: ScatterSet<MuxDeferredNode<*, *, *>>,
    ) {
        nodes.forEach { node ->
            node.moveDirectUpstreamToIndirect(
                scheduler,
                oldDirectDepth,
                newIndirectDepth,
                newIndirectSet,
            )
        }
        muxMovers.forEach { mover ->
            mover.moveDirectPatchNodeToIndirect(scheduler, newIndirectDepth, newIndirectSet)
        }
    }

    fun removeIndirectUpstream(
        scheduler: Scheduler,
        depth: Int,
        indirectSet: ScatterSet<MuxDeferredNode<*, *, *>>,
    ) {
        nodes.forEach { node -> node.removeIndirectUpstream(scheduler, depth, indirectSet) }
        muxMovers.forEach { mover -> mover.removeIndirectPatchNode(scheduler, depth, indirectSet) }
        outputs.forEach { output -> output.kill() }
        stateWriters.fastForEach { writer -> writer.kill() }
    }

    fun removeDirectUpstream(scheduler: Scheduler, depth: Int) {
        nodes.forEach { node -> node.removeDirectUpstream(scheduler, depth) }
        muxMovers.forEach { mover -> mover.removeDirectPatchNode(scheduler) }
        outputs.forEach { output -> output.kill() }
        stateWriters.fastForEach { writer -> writer.kill() }
    }

    fun clear() {
        outputs.clear()
        stateWriters.clear()
        muxMovers.clear()
        nodes.clear()
    }
}

// TODO: remove this indirection
internal sealed interface Schedulable {
    data class S constructor(val state: StateSource<*>) : Schedulable

    data class M constructor(val muxMover: MuxDeferredNode<*, *, *>) : Schedulable

    data class N constructor(val node: SchedulableNode) : Schedulable

    data class O constructor(val output: Output<*>) : Schedulable
}

internal fun DownstreamSet.isEmpty() =
    nodes.isEmpty() && outputs.isEmpty() && muxMovers.isEmpty() && stateWriters.isEmpty()

@Suppress("NOTHING_TO_INLINE") internal inline fun DownstreamSet.isNotEmpty() = !isEmpty()

internal fun scheduleAll(
    logIndent: Int,
    downstreamSet: DownstreamSet,
    evalScope: EvalScope,
): Boolean {
    downstreamSet.nodes.forEach { node -> node.schedule(logIndent, evalScope) }
    downstreamSet.muxMovers.forEach { mover -> mover.scheduleMover(logIndent, evalScope) }
    downstreamSet.outputs.forEach { output -> output.schedule(logIndent, evalScope) }
    downstreamSet.stateWriters.fastForEach { writer -> writer.schedule(logIndent, evalScope) }
    return downstreamSet.isNotEmpty()
}
