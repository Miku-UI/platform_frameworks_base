/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.compose

import androidx.collection.mutableIntSetOf
import androidx.collection.mutableLongListOf
import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.snapshots.tooling.SnapshotObserver
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition

/**
 * Once the androidx version is available, we should rely on it and delete this class. This version
 * comes from aosp/3838154.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
@InternalComposeTracingApi
class RecompositionStateTracingObserver(private val tracer: Tracer) :
    CompositionObserver, CompositionRegistrationObserver, AutoCloseable, SnapshotObserver {
    private val compositionHandles =
        mutableScatterMapOf<ObservableComposition, CompositionObserverHandle>()
    private val scopes = mutableScatterMapOf<RecomposeScope, ScopeData>()

    @InternalComposeTracingApi
    interface Tracer {
        fun beginSection(
            sectionName: String,
            debugInfo: String,
            flowIds: LongArray,
            terminatingFlowIds: LongArray,
        )

        fun instantEvent(
            sectionName: String,
            debugInfo: String,
            flowIds: LongArray,
            terminatingFlowIds: LongArray,
        )

        fun endSection()

        fun isEnabled(): Boolean
    }

    override fun onCompositionRegistered(composition: ObservableComposition) {
        compositionHandles[composition] = composition.setObserver(this)
    }

    override fun onCompositionUnregistered(composition: ObservableComposition) {
        compositionHandles -= composition
    }

    override fun onBeginComposition(composition: ObservableComposition) {
        // Nothing to do here, composition is already traced
    }

    override fun onScopeEnter(scope: RecomposeScope) {
        if (!tracer.isEnabled()) return

        val data = scopes[scope]

        if (data != null) {
            data.resetReads()
            if (data.hasInvalidations()) {
                tracer.beginSection(
                    sectionName = "Recompose group",
                    debugInfo = "",
                    flowIds = EMPTY_FLOW_IDS,
                    terminatingFlowIds = data.invalidationFlowIds(),
                )
                data.startedSlice = true
            }
        }
    }

    override fun onReadInScope(scope: RecomposeScope, value: Any) {
        if (!tracer.isEnabled()) return

        val data = scopes[scope] ?: ScopeData(scope).also { scopes[scope] = it }

        val trace = Exception()
        tracer.instantEvent(
            "State read of $value",
            debugInfo = trace.renderStackTrace(),
            longArrayOf(scope.flowId(value)),
            EMPTY_FLOW_IDS,
        )

        data.read(value)
    }

    fun onStateWrite(instance: Any) {
        if (!tracer.isEnabled()) return

        val flows = mutableLongListOf()
        scopes.forEach { scope, data ->
            if (data.hasRead(instance)) {
                flows += scope.flowId(instance)
            }
        }

        tracer.instantEvent(
            "State write of $instance",
            debugInfo = Exception().renderStackTrace(),
            if (flows.isEmpty()) EMPTY_FLOW_IDS else LongArray(flows.size) { flows[it] },
            EMPTY_FLOW_IDS,
        )
    }

    override fun onScopeExit(scope: RecomposeScope) {
        if (!tracer.isEnabled()) return

        val data = scopes[scope]
        data?.resetInvalidations()

        if (data != null && data.startedSlice) {
            data.startedSlice = false
            tracer.endSection()
        }
    }

    override fun onEndComposition(composition: ObservableComposition) {
        // Nothing to do here, composition is already traced
    }

    override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) {
        if (!tracer.isEnabled()) return

        if (value == null) {
            tracer.instantEvent(
                "Direct invalidation",
                debugInfo = Exception().renderStackTrace(),
                longArrayOf(scope.flowId(null)),
                EMPTY_FLOW_IDS,
            )
        }

        scopes[scope]?.invalidateWith(value)
    }

    override fun onScopeDisposed(scope: RecomposeScope) {
        scopes -= scope
    }

    override fun close() {
        compositionHandles.forEachValue { it.dispose() }
        compositionHandles.clear()
        scopes.clear()
    }

    internal companion object {
        internal val EMPTY_FLOW_IDS = LongArray(0)
    }
}

fun identityHashCode(instance: Any?): Int = System.identityHashCode(instance)

private class ScopeData(private val scope: RecomposeScope) {
    private val invalidationIds = mutableIntSetOf()
    var startedSlice = false
    private val readIds = mutableIntSetOf()

    fun invalidateWith(instance: Any?) {
        invalidationIds += identityHashCode(instance)
    }

    fun read(instance: Any) {
        readIds += identityHashCode(instance)
    }

    fun invalidationFlowIds(): LongArray =
        LongArray(invalidationIds.size).also { ids ->
            var index = 0
            invalidationIds.forEach { ids[index++] = scope.flowId(it) }
        }

    fun hasRead(instance: Any) = identityHashCode(instance) in readIds

    fun hasInvalidations() = invalidationIds.isNotEmpty()

    fun resetInvalidations() {
        invalidationIds.clear()
    }

    fun resetReads() {
        readIds.clear()
    }
}

private fun RecomposeScope.flowId(instance: Any?): Long =
    (identityHashCode(this).toLong() shl 32) or identityHashCode(instance).toLong()

private fun RecomposeScope.flowId(hash: Int): Long =
    (identityHashCode(this).toLong() shl 32) or hash.toLong()

private fun Exception.renderStackTrace(): String =
    stackTrace.joinToString("\n") { it.toString().replaceAfter("androidx", "...") }
