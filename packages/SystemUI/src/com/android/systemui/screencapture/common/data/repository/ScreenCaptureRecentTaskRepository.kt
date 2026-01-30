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

package com.android.systemui.screencapture.common.data.repository

import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.kairos.awaitClose
import com.android.systemui.mediaprojection.appselector.data.RecentTask
import com.android.systemui.mediaprojection.appselector.data.RecentTaskListProvider
import com.android.systemui.screencapture.common.ScreenCaptureUi
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.systemui.utils.coroutines.flow.mapLatestConflated
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Repository of the current recent tasks. */
interface ScreenCaptureRecentTaskRepository {
    /** The current recent tasks. */
    val recentTasks: StateFlow<List<RecentTask>?>
}

/** The default implementation of [ScreenCaptureRecentTaskRepository]. */
@ScreenCaptureUiScope
class ScreenCaptureRecentTaskRepositoryImpl
@Inject
constructor(
    @ScreenCaptureUi scope: CoroutineScope,
    @Background bgContext: CoroutineContext,
    recentTaskListProvider: RecentTaskListProvider,
    taskStackChangeListeners: TaskStackChangeListeners,
) : ScreenCaptureRecentTaskRepository {

    override val recentTasks: StateFlow<List<RecentTask>?> =
        conflatedCallbackFlow {
                val listener =
                    object : TaskStackChangeListener {
                        override fun onTaskStackChanged() {
                            trySend(Unit)
                        }
                    }

                taskStackChangeListeners.registerTaskStackListener(listener)

                awaitClose { taskStackChangeListeners.unregisterTaskStackListener(listener) }
            }
            .onStart { emit(Unit) }
            .mapLatestConflated { recentTaskListProvider.loadRecentTasks() }
            .flowOn(bgContext)
            .stateIn(scope, SharingStarted.Eagerly, null)
}
