/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.communal.domain.interactor

import android.util.IndentingPrintWriter
import com.android.systemui.communal.data.repository.ContextualSetupRepository
import com.android.systemui.communal.data.repository.SetupState
import com.android.systemui.communal.domain.definition.ContextualSetupDefinition
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.util.kotlin.FlowDumper
import com.android.systemui.util.kotlin.SimpleFlowDumper
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Orchestrates all registered contextual setup flows, producing a single stream of valid launch
 * requests.
 */
@SysUISingleton
class ContextualSetupInteractor
@Inject
constructor(
    private val repository: ContextualSetupRepository,
    // A set of all registered definitions injected by Dagger.
    private val definitions: Set<@JvmSuppressWildcards ContextualSetupDefinition>,
    private val dumpManager: DumpManager,
) : FlowDumper by SimpleFlowDumper() {

    /**
     * Emits a [ContextualSetupDefinition] only when its specific trigger and preconditions are met
     * AND its persistent state is [SetupState.NotStarted]. This represents a valid request to
     * launch a setup flow.
     *
     * The flow emits the highest priority definition that is ready to launch. If multiple
     * definitions are ready with the same priority, the one with the lexicographically smallest
     * [id] is emitted.
     */
    val launchRequest: Flow<ContextualSetupDefinition> =
        if (definitions.isEmpty()) {
            emptyFlow()
        } else {
            combine(
                    definitions.map { definition ->
                        combine(repository.setupState(definition.id), definition.isReady) {
                            state,
                            isReady ->
                            if (
                                state is SetupState.NotStarted &&
                                    isReady &&
                                    definition.target != null
                            ) {
                                definition
                            } else {
                                null
                            }
                        }
                    }
                ) { candidates ->
                    candidates
                        .filterNotNull()
                        .sortedWith(
                            compareByDescending<ContextualSetupDefinition> { it.priority }
                                .thenBy { it.id }
                        )
                        .firstOrNull()
                }
                .filterNotNull()
                .distinctUntilChanged()
                .dumpWhileCollecting("launchRequest")
        }

    /** Must be called to register the interactor with the dump manager. */
    fun init() {
        dumpManager.registerNormalDumpable(TAG, this)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.println("$TAG state:")
        dumpFlows(IndentingPrintWriter(pw, "  "))
        pw.println()

        pw.println("Registered definitions (${definitions.size}):")
        definitions.forEach { definition ->
            pw.println("--------------------------------")
            definition.dump(pw, args)
        }
    }

    private companion object {
        const val TAG = "ContextualSetupInteractor"
    }
}
