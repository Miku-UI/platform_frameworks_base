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
import com.android.systemui.kairos.internal.store.ArrayMapK
import com.android.systemui.kairos.internal.store.FastIterable
import com.android.systemui.kairos.internal.store.MutableMapK
import com.android.systemui.kairos.internal.store.NoValue
import com.android.systemui.kairos.internal.store.SingletonMapK
import com.android.systemui.kairos.internal.store.asArrayMapK
import com.android.systemui.kairos.internal.store.asSingletonMapK
import com.android.systemui.kairos.internal.store.isNotEmpty
import com.android.systemui.kairos.internal.store.singletonMapKOf
import com.android.systemui.kairos.internal.util.fastForEach
import com.android.systemui.kairos.internal.util.hashString
import com.android.systemui.kairos.internal.util.logDuration
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.These
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.plus

internal class MuxDeferredNode<W, K, V, R>(
    nameData: NameData,
    lifecycle: MuxLifecycle<W, K, V, R>,
    val spec: MuxActivator<W, K, V, R>,
    val combiner: EvalScope.(MuxResult<W, K, V>) -> R,
) : MuxNode<W, K, V, R>(nameData, lifecycle) {

    val schedulable = Schedulable.M(this)
    var patches: NodeConnection<FastIterable<K, EventsImpl<V>?>>? = null
    var patchData: FastIterable<K, EventsImpl<V>?>? = null

    override fun visit(logIndent: Int, evalScope: EvalScope) {
        check(epoch < evalScope.epoch) { "node unexpectedly visited multiple times in transaction" }
        logDuration(logIndent, { "MuxDeferred[$nameData].visit" }) {
            if (depthTracker.isDirty()) {
                logDuration(getPrefix = { "compactDownstream" }, start = false) {
                    depthTracker.applyChanges(
                        evalScope.scheduler,
                        downstreamSet,
                        owner = this@MuxDeferredNode,
                    )
                }
            }
            if (upstreamData.isNotEmpty()) {
                val result: R
                logDuration(getPrefix = { "copying upstream data" }, start = false) {
                    result = evalScope.combiner(upstreamData)
                    upstreamData.clear()
                }
                logDuration({ "scheduleDownstream" }) {
                    transactionCache.put(evalScope, result)
                    if (!scheduleAll(currentLogIndent, downstreamSet, evalScope)) {
                        evalScope.scheduleDeactivation(this@MuxDeferredNode)
                    }
                }
            }
        }
    }

    private fun compactIfNeeded(evalScope: EvalScope) {
        depthTracker.propagateChanges(evalScope.compactor, this)
    }

    override fun doDeactivate(evalScope: EvalScope) {
        check(downstreamSet.isEmpty()) { "cannot deactivate a node with downstreams" }
        // Update lifecycle
        if (lifecycle.lifecycleState !is MuxLifecycleState.Active) return
        check(!depthTracker.isDirty()) { "cannot deactivate with dirty depth tracker" }
        lifecycle.lifecycleState = MuxLifecycleState.Inactive(spec)
        // Process branch nodes
        switchedIn.forEach { _, branchNode ->
            branchNode.upstream.removeDownstreamAndDeactivateIfNeeded(
                downstream = branchNode.schedulable,
                evalScope = evalScope,
            )
        }
        // Process patch node
        patches?.removeDownstreamAndDeactivateIfNeeded(
            downstream = schedulable,
            evalScope = evalScope,
        )
    }

    // MOVE phase
    //  - no more node evaluations are occurring. all depth recalculations are deferred to the end
    //    of this phase.
    fun performMove(evalScope: EvalScope) {
        val patch = patchData ?: return
        patchData = null

        // TODO: this logic is very similar to what's in MuxPrompt, maybe turn into an inline fun?

        // We have a patch, process additions/updates and removals
        val adds = mutableListOf<Pair<K, EventsImpl<V>>>()
        val removes = mutableListOf<K>()
        patch.forEach { k, newUpstream ->
            when (newUpstream) {
                null -> removes.add(k)
                else -> adds.add(k to newUpstream)
            }
        }

        val severed = mutableListOf<NodeConnection<*>>()

        // remove and sever
        removes.fastForEach { k ->
            switchedIn.remove(k)?.let { branchNode: BranchNode ->
                val conn = branchNode.upstream
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
            switchedIn.remove(k)?.let { branchNode ->
                val conn = branchNode.upstream
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

            // add new
            val newBranch = BranchNode(k)
            newUpstream.activate(evalScope, newBranch.schedulable)?.let { (conn, _) ->
                newBranch.upstream = conn
                switchedIn[k] = newBranch
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
                        butNot = this@MuxDeferredNode,
                    )
                }
            }
        }

        severed.fastForEach { it.scheduleDeactivationIfNeeded(evalScope) }

        compactIfNeeded(evalScope)
    }

    fun removeDirectPatchNode(scheduler: Scheduler) {
        if (depthTracker.setIsIndirectRoot(false)) {
            depthTracker.schedule(scheduler, this)
        }
        patches = null
    }

    fun removeIndirectPatchNode(
        scheduler: Scheduler,
        depth: Int,
        indirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        // indirectly connected patches forward the indirectSet
        if (
            depthTracker.updateIndirectRoots(removals = indirectSet) or
                depthTracker.removeIndirectUpstream(depth)
        ) {
            depthTracker.schedule(scheduler, this)
        }
        patches = null
    }

    fun moveIndirectPatchNodeToDirect(
        scheduler: Scheduler,
        oldIndirectDepth: Int,
        oldIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        // directly connected patches are stored as an indirect singleton set of the patchNode
        if (
            depthTracker.updateIndirectRoots(removals = oldIndirectSet) or
                depthTracker.removeIndirectUpstream(oldIndirectDepth) or
                depthTracker.setIsIndirectRoot(true)
        ) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun moveDirectPatchNodeToIndirect(
        scheduler: Scheduler,
        newIndirectDepth: Int,
        newIndirectSet: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        // indirectly connected patches forward the indirectSet
        if (
            depthTracker.setIsIndirectRoot(false) or
                depthTracker.updateIndirectRoots(additions = newIndirectSet, butNot = this) or
                depthTracker.addIndirectUpstream(oldDepth = null, newDepth = newIndirectDepth)
        ) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun adjustIndirectPatchNode(
        scheduler: Scheduler,
        oldDepth: Int,
        newDepth: Int,
        removals: ScatterSet<MuxDeferredNode<*, *, *, *>>,
        additions: ScatterSet<MuxDeferredNode<*, *, *, *>>,
    ) {
        // indirectly connected patches forward the indirectSet
        if (
            depthTracker.updateIndirectRoots(
                additions = additions,
                removals = removals,
                butNot = this,
            ) or depthTracker.addIndirectUpstream(oldDepth = oldDepth, newDepth = newDepth)
        ) {
            depthTracker.schedule(scheduler, this)
        }
    }

    fun scheduleMover(logIndent: Int, evalScope: EvalScope) {
        logDuration(logIndent, { "MuxDeferred.scheduleMover" }) {
            patchData =
                checkNotNull(patches) { "mux mover scheduled with unset patches upstream node" }
                    .getPushEvent(currentLogIndent, evalScope)
            evalScope.scheduleMuxMover(this@MuxDeferredNode)
        }
    }

    override fun toString(): String = "${this::class.simpleName}@$hashString[$nameData]"
}

internal inline fun <A> switchDeferredImplSingle(
    nameData: NameData,
    crossinline getStorage: EvalScope.() -> EventsImpl<A>,
    crossinline getPatches: EvalScope.() -> EventsImpl<EventsImpl<A>>,
): EventsImpl<A> {
    val patches =
        mapImpl(getPatches, nameData + "patches") { newEvents, _ -> singletonMapKOf(newEvents) }
    val switchDeferredImpl =
        switchDeferredImpl(
            nameData,
            getStorage = { singletonMapKOf(getStorage()) },
            getPatches = { patches },
            storeFactory = SingletonMapK.Factory,
        ) { singletonMap ->
            @Suppress("UNCHECKED_CAST")
            singletonMap.asSingletonMapK().value as PullNode<A>
        }
    return mapImpl({ switchDeferredImpl }, nameData + "getResult") { node, logIndent ->
        node.getPushEvent(logIndent, this)
    }
}

internal fun <W, K, V, R> switchDeferredImpl(
    nameData: NameData,
    // TODO: we might want to change this to skip the Iterable and just return the MutableMapK
    getStorage: EvalScope.() -> FastIterable<K, EventsImpl<V>>,
    getPatches: EvalScope.() -> EventsImpl<FastIterable<K, EventsImpl<V>?>>,
    storeFactory: MutableMapK.Factory<W, K>,
    combiner: EvalScope.(MuxResult<W, K, V>) -> R,
): EventsImpl<R> =
    MuxLifecycle(MuxDeferredActivator(nameData, getStorage, storeFactory, getPatches, combiner))

private class MuxDeferredActivator<W, K, V, R>(
    private val nameData: NameData,
    private val getStorage: EvalScope.() -> FastIterable<K, EventsImpl<V>>,
    private val storeFactory: MutableMapK.Factory<W, K>,
    private val getPatches: EvalScope.() -> EventsImpl<FastIterable<K, EventsImpl<V>?>>,
    private val combiner: EvalScope.(MuxResult<W, K, V>) -> R,
) : MuxActivator<W, K, V, R> {

    init {
        nameData.forceInit()
    }

    override fun activate(
        evalScope: EvalScope,
        lifecycle: MuxLifecycle<W, K, V, R>,
    ): Pair<MuxNode<W, K, V, R>, (() -> Boolean)?> {
        // Initialize mux node and switched-in connections.
        val muxNode =
            MuxDeferredNode(nameData, lifecycle, this, combiner).apply {
                initializeUpstream(evalScope, getStorage, storeFactory)
                // Update depth based on all initial switched-in nodes.
                initializeDepth()
                // We don't have our patches connection established yet, so for now pretend we have
                // a direct connection to patches. We will update downstream nodes later if this
                // turns out to be a lie.
                depthTracker.setIsIndirectRoot(true)
                depthTracker.reset(this)
            }

        // Schedule for evaluation if any switched-in nodes have already emitted within
        // this transaction.
        if (muxNode.upstreamData.isNotEmpty()) {
            muxNode.schedule(evalScope)
        }

        return muxNode to
            fun(): Boolean {
                // Setup patches connection; deferring allows for a recursive connection, where
                // muxNode is downstream of itself via patches.
                val (patchesConn, needsEval) =
                    getPatches(evalScope).activate(evalScope, downstream = muxNode.schedulable)
                        ?: run {
                            // Turns out we can't connect to patches, so update our depth
                            muxNode.depthTracker.setIsIndirectRoot(false)
                            muxNode.depthTracker.reset(muxNode)
                            return false
                        }
                muxNode.patches = patchesConn

                if (!patchesConn.depthTracker.snapshotIsDirect) {
                    // Turns out patches is indirect, so we are not a root. Update depth and
                    // propagate.
                    if (
                        muxNode.depthTracker.setIsIndirectRoot(false) or
                            muxNode.depthTracker.addIndirectUpstream(
                                oldDepth = null,
                                newDepth = patchesConn.depthTracker.snapshotIndirectDepth,
                            ) or
                            muxNode.depthTracker.updateIndirectRoots(
                                additions = patchesConn.depthTracker.snapshotIndirectRoots,
                                butNot = muxNode,
                            )
                    ) {
                        muxNode.depthTracker.schedule(evalScope.scheduler, muxNode)
                    }
                }
                // Schedule mover to process patch emission at the end of this transaction, if
                // needed.
                if (needsEval) {
                    muxNode.patchData = patchesConn.getPushEvent(0, evalScope)
                    evalScope.scheduleMuxMover(muxNode)
                }

                return true
            }
    }

    override fun toString(): String = "${super.toString()}[$nameData]"
}

internal inline fun <A> mergeNodes(
    nameData: NameData,
    crossinline getPulse: EvalScope.() -> EventsImpl<A>,
    crossinline getOther: EvalScope.() -> EventsImpl<A>,
    crossinline mergeBoth: EvalScope.(A, A) -> A,
): EventsImpl<A> =
    switchDeferredImpl(
            nameData,
            getStorage = { listOf(getPulse(), getOther()).asIterableWithIndex() },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) {
            val results = it.asArrayMapK()
            val firstNode = results[0]
            val secondNode = results[1]
            if (firstNode == null) {
                checkNotNull(secondNode) { "unexpected missing merge result" }
                secondNode.getPushEvent(logIndent = 0, evalScope = this)
            } else {
                val first = firstNode.getPushEvent(logIndent = 0, evalScope = this)
                if (secondNode == null) {
                    first
                } else {
                    val second = secondNode.getPushEvent(logIndent = 0, evalScope = this)
                    mergeBoth(first, second)
                }
            }
        }
        .cached(nameData + "cached")

internal fun <T> Iterable<T>.asIterableWithIndex() = FastIterable { yield ->
    forEachIndexed { index, t -> yield(index, t) }
}

internal inline fun <A, B> mergeNodes(
    nameData: NameData,
    crossinline getPulse: EvalScope.() -> EventsImpl<A>,
    crossinline getOther: EvalScope.() -> EventsImpl<B>,
): EventsImpl<These<A, B>> =
    mergeNodes(
        nameData,
        { mapImpl(getPulse, nameData + "firstMergeInput") { it, _ -> These.first(it) } },
        { mapImpl(getOther, nameData + "secondMergeInput") { it, _ -> These.second(it) } },
    ) { f, s ->
        f as These.First
        s as These.Second
        These.both(f.value, s.value)
    }

internal inline fun <A> mergeNodes(
    nameData: NameData,
    crossinline getPulses: EvalScope.() -> Iterable<EventsImpl<A>>,
): EventsImpl<List<A>> =
    switchDeferredImpl(
            nameData,
            getStorage = { getPulses().asIterableWithIndex() },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) { results ->
            buildList {
                results.asArrayMapK().forEach { i, node ->
                    add(node.getPushEvent(logIndent = 0, evalScope = this@switchDeferredImpl))
                }
            }
        }
        .cached(nameData + "cached")

internal inline fun <A, B : A> mergeReduceNodes(
    nameData: NameData,
    crossinline getPulses: EvalScope.() -> Iterable<EventsImpl<B>>,
    crossinline reduce: EvalScope.(A, B) -> A,
): EventsImpl<A> =
    switchDeferredImpl(
            nameData,
            getStorage = { getPulses().asIterableWithIndex() },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) { results ->
            var reduced: Any? = NoValue
            results.asArrayMapK().forEach { _, node ->
                val a = node.getPushEvent(logIndent = 0, evalScope = this@switchDeferredImpl)
                reduced =
                    if (reduced === NoValue) {
                        a
                    } else {
                        @Suppress("UNCHECKED_CAST") reduce(reduced as A, a)
                    }
            }
            @Suppress("UNCHECKED_CAST")
            reduced as A
        }
        .cached(nameData + "cached")

internal inline fun <A, B> mergeFoldNodes(
    nameData: NameData,
    initialValue: B,
    crossinline getPulses: EvalScope.() -> Iterable<EventsImpl<A>>,
    crossinline reduce: EvalScope.(A, B) -> B,
): EventsImpl<B> =
    switchDeferredImpl(
            nameData,
            getStorage = { getPulses().asIterableWithIndex() },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) { results ->
            var reduced: B = initialValue
            results.asArrayMapK().forEach { _, node ->
                reduced =
                    reduce(
                        node.getPushEvent(logIndent = 0, evalScope = this@switchDeferredImpl),
                        reduced,
                    )
            }
            reduced
        }
        .cached(nameData + "cached")

internal inline fun <A> mergeNodesLeft(
    nameData: NameData,
    crossinline getPulses: EvalScope.() -> Iterable<EventsImpl<A>>,
): EventsImpl<A> =
    switchDeferredImpl(
            nameData,
            getStorage = { getPulses().asIterableWithIndex() },
            getPatches = { neverImpl },
            storeFactory = ArrayMapK.Factory,
        ) combiner@{ results ->
            results.asArrayMapK().forEach { i, node ->
                return@combiner node.getPushEvent(logIndent = 0, evalScope = this)
            }
            error("unexpected missing merge result")
        }
        .cached(nameData + "cached")
