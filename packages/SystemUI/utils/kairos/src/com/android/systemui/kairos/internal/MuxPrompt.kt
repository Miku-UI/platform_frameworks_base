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
import com.android.systemui.kairos.internal.store.MutableMapK
import com.android.systemui.kairos.internal.store.SingletonMapK
import com.android.systemui.kairos.internal.store.asSingle
import com.android.systemui.kairos.internal.store.singleOf
import com.android.systemui.kairos.internal.util.fastForEach
import com.android.systemui.kairos.internal.util.hashString
import com.android.systemui.kairos.internal.util.logDuration
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.Maybe.Absent
import com.android.systemui.kairos.util.Maybe.Present
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.plus

internal class MuxPromptNode<W, K, V>(
    nameData: NameData,
    lifecycle: MuxLifecycle<W, K, V>,
    private val spec: MuxActivator<W, K, V>,
) : MuxNode<W, K, V>(nameData, lifecycle) {

    var patchData: Iterable<Map.Entry<K, Maybe<EventsImpl<V>>>>? = null
    var patches: PatchNode? = null

    override fun visit(logIndent: Int, evalScope: EvalScope) {
        check(epoch < evalScope.epoch) { "node unexpectedly visited multiple times in transaction" }
        logDuration(logIndent, { "MuxPrompt.visit" }) {
            val patch: Iterable<Map.Entry<K, Maybe<EventsImpl<V>>>>? = patchData
            patchData = null

            // If there's a patch, process it.
            patch?.let {
                val needsReschedule = processPatch(patch, evalScope)
                // We may need to reschedule if newly-switched-in nodes have not yet been visited
                // within this transaction.
                val depthIncreased = depthTracker.dirty_depthIncreased()
                if (needsReschedule || depthIncreased) {
                    if (depthIncreased) {
                        depthTracker.schedule(evalScope.compactor, this@MuxPromptNode)
                    }
                    schedule(evalScope)
                    return
                }
            }
            val results = upstreamData.readOnlyCopy().also { upstreamData.clear() }

            // If we don't need to reschedule, or there wasn't a patch at all, then we proceed
            // with merging pre-switch and post-switch results
            adjustDownstreamDepths(evalScope)
            if (results.isNotEmpty()) {
                transactionCache.put(evalScope, results)
                if (!scheduleAll(currentLogIndent, downstreamSet, evalScope)) {
                    evalScope.scheduleDeactivation(this@MuxPromptNode)
                }
            }
        }
    }

    // side-effect: this will populate `upstreamData` with any immediately available results
    private fun processPatch(
        patch: Iterable<Map.Entry<K, Maybe<EventsImpl<V>>>>,
        evalScope: EvalScope,
    ): Boolean {
        var needsReschedule = false
        // We have a patch, process additions/updates and removals
        val adds = mutableListOf<Pair<K, EventsImpl<V>>>()
        val removes = mutableListOf<K>()
        patch.forEach { (k, newUpstream) ->
            when (newUpstream) {
                is Present -> adds.add(k to newUpstream.value)
                Absent -> removes.add(k)
            }
        }

        val severed = mutableListOf<NodeConnection<*>>()

        // remove and sever
        removes.forEach { k ->
            switchedIn.remove(k)?.let { branchNode: BranchNode ->
                val conn: NodeConnection<V> = branchNode.upstream
                severed.add(conn)
                conn.removeDownstream(downstream = branchNode.schedulable)
                if (conn.depthTracker.snapshotIsDirect) {
                    depthTracker.removeDirectUpstream(conn.depthTracker.snapshotDirectDepth)
                } else {
                    depthTracker.removeIndirectUpstream(conn.depthTracker.snapshotIndirectDepth)
                    depthTracker.updateIndirectRoots(
                        removals = conn.depthTracker.snapshotIndirectRoots
                    )
                }
            }
        }

        // add or replace
        adds.fastForEach { (k, newUpstream: EventsImpl<V>) ->
            // remove old and sever, if present
            switchedIn.remove(k)?.let { oldBranch: BranchNode ->
                val conn: NodeConnection<V> = oldBranch.upstream
                severed.add(conn)
                conn.removeDownstream(downstream = oldBranch.schedulable)
                if (conn.depthTracker.snapshotIsDirect) {
                    depthTracker.removeDirectUpstream(conn.depthTracker.snapshotDirectDepth)
                } else {
                    depthTracker.removeIndirectUpstream(conn.depthTracker.snapshotIndirectDepth)
                    depthTracker.updateIndirectRoots(
                        removals = conn.depthTracker.snapshotIndirectRoots
                    )
                }
            }

            // add new
            val newBranch = BranchNode(k)
            newUpstream.activate(evalScope, newBranch.schedulable)?.let { (conn, needsEval) ->
                newBranch.upstream = conn
                switchedIn[k] = newBranch
                if (needsEval) {
                    upstreamData[k] = newBranch.upstream.directUpstream
                } else {
                    needsReschedule = true
                }
                val branchDepthTracker = newBranch.upstream.depthTracker
                if (branchDepthTracker.snapshotIsDirect) {
                    depthTracker.addDirectUpstream(
                        oldDepth = null,
                        newDepth = branchDepthTracker.snapshotDirectDepth,
                    )
                } else {
                    depthTracker.addIndirectUpstream(
                        oldDepth = null,
                        newDepth = branchDepthTracker.snapshotIndirectDepth,
                    )
                    depthTracker.updateIndirectRoots(
                        additions = branchDepthTracker.snapshotIndirectRoots,
                        butNot = null,
                    )
                }
            }
        }

        severed.fastForEach { it.scheduleDeactivationIfNeeded(evalScope) }

        return needsReschedule
    }

    private fun adjustDownstreamDepths(evalScope: EvalScope) {
        if (depthTracker.dirty_depthIncreased()) {
            // schedule downstream nodes on the compaction scheduler; this scheduler is drained at
            // the end of this eval depth, so that all depth increases are applied before we advance
            // the eval step
            depthTracker.schedule(evalScope.compactor, node = this@MuxPromptNode)
        } else if (depthTracker.isDirty()) {
            // schedule downstream nodes on the eval scheduler; this is more efficient and is only
            // safe if the depth hasn't increased
            depthTracker.applyChanges(
                evalScope.scheduler,
                downstreamSet,
                owner = this@MuxPromptNode,
            )
        }
    }

    override fun getPushEvent(logIndent: Int, evalScope: EvalScope): MuxResult<W, K, V> =
        logDuration(logIndent, { "MuxPrompt.getPushEvent" }) {
            transactionCache.getCurrentValue(evalScope)
        }

    override fun doDeactivate() {
        // Update lifecycle
        if (lifecycle.lifecycleState !is MuxLifecycleState.Active) return
        lifecycle.lifecycleState = MuxLifecycleState.Inactive(spec)
        // Process branch nodes
        switchedIn.forEach { _, branchNode ->
            branchNode.upstream.removeDownstreamAndDeactivateIfNeeded(
                downstream = branchNode.schedulable
            )
        }
        // Process patch node
        patches?.let { patches ->
            patches.upstream.removeDownstreamAndDeactivateIfNeeded(downstream = patches.schedulable)
        }
    }

    fun removeIndirectPatchNode(
        scheduler: Scheduler,
        oldDepth: Int,
        indirectSet: ScatterSet<MuxDeferredNode<*, *, *>>,
    ) {
        patches = null
        if (
            depthTracker.removeIndirectUpstream(oldDepth) or
                depthTracker.updateIndirectRoots(removals = indirectSet)
        ) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun removeDirectPatchNode(scheduler: Scheduler, depth: Int) {
        patches = null
        if (depthTracker.removeDirectUpstream(depth)) {
            depthTracker.schedule(scheduler, this)
        }
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"

    inner class PatchNode : SchedulableNode {

        val schedulable = Schedulable.N(this)

        lateinit var upstream: NodeConnection<Iterable<Map.Entry<K, Maybe<EventsImpl<V>>>>>

        override fun schedule(logIndent: Int, evalScope: EvalScope) {
            logDuration(logIndent, { "MuxPromptPatchNode.schedule" }) {
                patchData = upstream.getPushEvent(currentLogIndent, evalScope)
                this@MuxPromptNode.schedule(evalScope)
            }
        }

        override fun adjustDirectUpstream(scheduler: Scheduler, oldDepth: Int, newDepth: Int) {
            this@MuxPromptNode.adjustDirectUpstream(scheduler, oldDepth, newDepth)
        }

        override fun moveIndirectUpstreamToDirect(
            scheduler: Scheduler,
            oldIndirectDepth: Int,
            oldIndirectSet: ScatterSet<MuxDeferredNode<*, *, *>>,
            newDirectDepth: Int,
        ) {
            this@MuxPromptNode.moveIndirectUpstreamToDirect(
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
            removals: ScatterSet<MuxDeferredNode<*, *, *>>,
            additions: ScatterSet<MuxDeferredNode<*, *, *>>,
        ) {
            this@MuxPromptNode.adjustIndirectUpstream(
                scheduler,
                oldDepth,
                newDepth,
                removals,
                additions,
            )
        }

        override fun moveDirectUpstreamToIndirect(
            scheduler: Scheduler,
            oldDirectDepth: Int,
            newIndirectDepth: Int,
            newIndirectSet: ScatterSet<MuxDeferredNode<*, *, *>>,
        ) {
            this@MuxPromptNode.moveDirectUpstreamToIndirect(
                scheduler,
                oldDirectDepth,
                newIndirectDepth,
                newIndirectSet,
            )
        }

        override fun removeDirectUpstream(scheduler: Scheduler, depth: Int) {
            this@MuxPromptNode.removeDirectPatchNode(scheduler, depth)
        }

        override fun removeIndirectUpstream(
            scheduler: Scheduler,
            depth: Int,
            indirectSet: ScatterSet<MuxDeferredNode<*, *, *>>,
        ) {
            this@MuxPromptNode.removeIndirectPatchNode(scheduler, depth, indirectSet)
        }
    }
}

internal inline fun <A> switchPromptImplSingle(
    nameData: NameData,
    crossinline getStorage: EvalScope.() -> EventsImpl<A>,
    crossinline getPatches: EvalScope.() -> EventsImpl<EventsImpl<A>>,
): EventsImpl<A> {
    val patches =
        mapImpl(getPatches, nameData + "patches") { newEvents, _ ->
            singleOf(Maybe.present(newEvents)).asIterable()
        }
    val switchPromptImpl =
        switchPromptImpl(
            nameData,
            getStorage = { singleOf(getStorage()).asIterable() },
            getPatches = { patches },
            storeFactory = SingletonMapK.Factory(),
        )
    return mapImpl({ switchPromptImpl }, nameData + "getResult") { map, logIndent ->
        map.asSingle().getValue(Unit).getPushEvent(logIndent, this)
    }
}

internal fun <W, K, V> switchPromptImpl(
    nameData: NameData,
    getStorage: EvalScope.() -> Iterable<Map.Entry<K, EventsImpl<V>>>,
    getPatches: EvalScope.() -> EventsImpl<Iterable<Map.Entry<K, Maybe<EventsImpl<V>>>>>,
    storeFactory: MutableMapK.Factory<W, K>,
): EventsImpl<MuxResult<W, K, V>> =
    MuxLifecycle(MuxPromptActivator(nameData, getStorage, storeFactory, getPatches))

private class MuxPromptActivator<W, K, V>(
    private val nameData: NameData,
    private val getStorage: EvalScope.() -> Iterable<Map.Entry<K, EventsImpl<V>>>,
    private val storeFactory: MutableMapK.Factory<W, K>,
    private val getPatches: EvalScope.() -> EventsImpl<Iterable<Map.Entry<K, Maybe<EventsImpl<V>>>>>,
) : MuxActivator<W, K, V> {

    init {
        nameData.forceInit()
    }

    override fun activate(
        evalScope: EvalScope,
        lifecycle: MuxLifecycle<W, K, V>,
    ): Pair<MuxNode<W, K, V>, Nothing?>? {
        // Initialize mux node and switched-in connections.
        val movingNode =
            MuxPromptNode(nameData, lifecycle, this).apply {
                initializeUpstream(evalScope, getStorage, storeFactory)
                // Setup patches connection
                val patchNode = PatchNode()
                getPatches(evalScope)
                    .activate(evalScope = evalScope, downstream = patchNode.schedulable)
                    ?.let { (conn, needsEval) ->
                        patchNode.upstream = conn
                        patches = patchNode
                        if (needsEval) {
                            patchData = conn.getPushEvent(0, evalScope)
                        }
                    }
                // Update depth based on all initial switched-in nodes.
                initializeDepth()
                // Update depth based on patches node.
                patches?.upstream?.let { conn ->
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
                            butNot = null,
                        )
                    }
                }
                // Reset all depth adjustments, since no downstream has been notified
                depthTracker.reset(null)
            }

        // Schedule for evaluation if any switched-in nodes or the patches node have
        // already emitted within this transaction.
        if (movingNode.patchData != null || movingNode.upstreamData.isNotEmpty()) {
            movingNode.schedule(evalScope)
        }

        return if (movingNode.patches == null && movingNode.switchedIn.isEmpty()) {
            null
        } else {
            movingNode to null
        }
    }

    override fun toString(): String = "${super.toString()}[$nameData]"
}
