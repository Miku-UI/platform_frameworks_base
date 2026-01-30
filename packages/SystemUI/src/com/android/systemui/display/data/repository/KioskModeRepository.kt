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

package com.android.systemui.display.data.repository

import android.app.ActivityManager
import android.app.ActivityManager.LOCK_TASK_MODE_LOCKED
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.kairos.awaitClose
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Repository for the current kiosk mode (lock task mode) state. */
interface KioskModeRepository {
    /** A flow that emits `true` when the device enters kiosk mode, and `false` when it exits. */
    val isInKioskMode: StateFlow<Boolean>
}

@SysUISingleton
class KioskModeRepositoryImpl
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    @Background private val bgContext: CoroutineContext,
    private val activityManager: ActivityManager,
    private val taskStackChangeListeners: TaskStackChangeListeners,
) : KioskModeRepository {

    override val isInKioskMode: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    object : TaskStackChangeListener {
                        override fun onLockTaskModeChanged(mode: Int) {
                            trySend(mode == LOCK_TASK_MODE_LOCKED)
                        }
                    }

                taskStackChangeListeners.registerTaskStackListener(listener)

                // When the flow is cancelled (e.g., the scope is destroyed),
                // unregister the listener to prevent memory leaks.
                awaitClose { taskStackChangeListeners.unregisterTaskStackListener(listener) }
            }
            .onStart { emit(activityManager.lockTaskModeState == LOCK_TASK_MODE_LOCKED) }
            .flowOn(bgContext)
            .stateIn(scope, SharingStarted.WhileSubscribed(), false)
}
