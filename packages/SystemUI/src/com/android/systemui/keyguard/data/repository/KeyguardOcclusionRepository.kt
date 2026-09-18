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

package com.android.systemui.keyguard.data.repository

import android.app.ActivityManager.RunningTaskInfo
import android.view.Display
import com.android.internal.policy.IKeyguardService
import com.android.keyguard.logging.KeyguardLogger
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.WindowManagerOcclusionManager
import com.android.systemui.keyguard.data.model.OcclusionEventModel
import com.android.systemui.keyguard.data.model.ShowWhenLockedActivityInfoModel
import com.android.systemui.keyguard.shared.DriveDreamStateFromOcclusion
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.keyguard.KeyguardTransitions
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/**
 * Maintains state about "occluding" activities - activities with FLAG_SHOW_WHEN_LOCKED, which are
 * capable of displaying over the lockscreen while the device is still locked (such as Google Maps
 * navigation).
 *
 * Window Manager considers the device to be in the "occluded" state whenever such an activity is on
 * top of the task stack, including while we're unlocked, while keyguard code considers us to be
 * occluded only when we're locked, with an occluding activity currently displaying over the
 * lockscreen.
 *
 * This dual definition is confusing, so this repository collects all of the signals WM gives us,
 * and consolidates them into [showWhenLockedActivityInfo], which is the actual question WM is
 * answering when they say whether we're 'occluded'. Keyguard then uses this signal to conditionally
 * transition to [KeyguardState.OCCLUDED] where appropriate.
 */
interface KeyguardOcclusionRepository {
    val showWhenLockedActivityInfo: StateFlow<ShowWhenLockedActivityInfoModel>

    /** Called by [IKeyguardService.setOccluded] to set the occlusion boolean. */
    fun setOccludedFromWm(isOccluded: Boolean)

    /** Called by [WindowManagerOcclusionManager] during occlude/unocclude remote animations. */
    fun setOccludedFromRemoteAnimation(onTop: Boolean, taskInfo: RunningTaskInfo?)
}

@SysUISingleton
class KeyguardOcclusionRepositoryImpl
@Inject
constructor(
    val keyguardTransitions: KeyguardTransitions,
    val logger: KeyguardLogger,
    @param:Application val applicationScope: CoroutineScope,
) : KeyguardOcclusionRepository {

    private companion object {
        private val INITIAL_STATE =
            ShowWhenLockedActivityInfoModel(isOnTop = false, taskInfo = null)
    }

    /**
     * [Channel] for occlusion events from sources external to this class, such as from
     * [WindowManagerOcclusionManager] or [IKeyguardService.setOccluded]. These events are merged
     * with events that the repository listens for internally (see [getWmShellEvents]) to produce
     * the final [showWhenLockedActivityInfo] flow.
     */
    private val externalEvents = Channel<OcclusionEventModel>(capacity = Channel.BUFFERED)

    override val showWhenLockedActivityInfo: StateFlow<ShowWhenLockedActivityInfoModel> =
        merge(externalEvents.receiveAsFlow(), getWmShellEvents())
            .scan(INITIAL_STATE) { state, event ->
                val newState =
                    when (event) {
                        is OcclusionEventModel.OccludedFromWm -> {
                            if (event.isOccluded) {
                                // Become occluded, retain the taskInfo we already buffered
                                state.copy(isOnTop = true)
                            } else {
                                // Unocclude, clear everything
                                state.copy(isOnTop = false, taskInfo = null)
                            }
                        }

                        is OcclusionEventModel.OccludingTaskChanged -> {
                            if (state.isOnTop && event.taskInfo == null) {
                                // Prevent intermittent state drop: if we are occluded and task goes
                                // null, retain current taskInfo until OccludedFromWm(false)
                                // arrives.
                                state
                            } else {
                                state.copy(taskInfo = event.taskInfo)
                            }
                        }

                        is OcclusionEventModel.StartedRemoteAnimation -> {
                            ShowWhenLockedActivityInfoModel(
                                isOnTop = event.onTop,
                                taskInfo = event.taskInfo,
                            )
                        }
                    }

                if (state != newState) {
                    logger.logOcclusionStateChange(event, state, newState)
                }
                newState
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = INITIAL_STATE,
            )

    /**
     * The external occlusion signals such as [OcclusionEventModel.OccludedFromWm] and
     * [OcclusionEventModel.StartedRemoteAnimation] tell us when the keyguard initially becomes
     * occluded, but don't tell us when the occluding task changes. This can happen if we are
     * dreaming on top of another showWhenLocked activity, such as Maps. When the dream ends, the
     * keyguard occlusion state doesn't change (we are still occluded), but the top task does
     * change. To handle this case, we also need to listen to task changes.
     */
    private fun getWmShellEvents(): Flow<OcclusionEventModel> {
        if (!DriveDreamStateFromOcclusion.isEnabled) {
            return emptyFlow()
        }

        return conflatedCallbackFlow {
            val listener =
                ShellTaskOrganizer.KeyguardOccludingTaskListener { displayId, taskInfo ->
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        trySend(OcclusionEventModel.OccludingTaskChanged(taskInfo))
                    }
                }
            keyguardTransitions.registerOccludingTaskListener(listener)
            awaitClose { keyguardTransitions.unregisterOccludingTaskListener(listener) }
        }
    }

    override fun setOccludedFromWm(isOccluded: Boolean) {
        externalEvents.trySend(OcclusionEventModel.OccludedFromWm(isOccluded))
    }

    override fun setOccludedFromRemoteAnimation(onTop: Boolean, taskInfo: RunningTaskInfo?) {
        externalEvents.trySend(OcclusionEventModel.StartedRemoteAnimation(onTop, taskInfo))
    }
}
