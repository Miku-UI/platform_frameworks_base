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

package com.android.systemui.keyguard.domain.interactor

import android.app.ActivityManager.RunningTaskInfo
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor
import com.android.systemui.deviceentry.domain.interactor.DeviceUnlockedInteractor
import com.android.systemui.keyguard.data.model.ShowWhenLockedActivityInfoModel
import com.android.systemui.keyguard.data.repository.KeyguardOcclusionRepository
import com.android.systemui.keyguard.domain.model.OcclusionStateModel
import com.android.systemui.keyguard.shared.DriveDreamStateFromOcclusion
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.log.table.TableLogBuffer
import com.android.systemui.log.table.logDiffsForTable
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.util.kotlin.BooleanFlowOperators.anyOf
import com.android.systemui.util.kotlin.sample
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Logic related to keyguard occlusion. The keyguard is occluded when an activity with
 * FLAG_SHOW_WHEN_LOCKED is on top of the activity task stack, with that activity displaying on top
 * of ("occluding") the lockscreen UI. Common examples of this are Google Maps Navigation and the
 * secure camera.
 *
 * This should usually be used only by keyguard internal classes. Most System UI use cases should
 * use [KeyguardTransitionInteractor] to see if we're in [KeyguardState.OCCLUDED] instead or
 * [Scenes.Occluded] for SceneTransitionLayout.
 */
@SysUISingleton
class KeyguardOcclusionInteractor
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    private val repository: KeyguardOcclusionRepository,
    private val powerInteractor: PowerInteractor,
    private val transitionInteractor: KeyguardTransitionInteractor,
    private val deviceEntryInteractor: DeviceEntryInteractor,
    private val internalTransitionInteractor: InternalKeyguardTransitionInteractor,
    keyguardInteractor: KeyguardInteractor,
    deviceUnlockedInteractor: Lazy<DeviceUnlockedInteractor>,
    private val sceneInteractor: Lazy<SceneInteractor>,
) {
    val showWhenLockedActivityInfo: StateFlow<ShowWhenLockedActivityInfoModel> =
        repository.showWhenLockedActivityInfo

    /**
     * Whether a SHOW_WHEN_LOCKED activity is on top of the task stack. This does not necessarily
     * mean we're OCCLUDED, as we could be GONE (unlocked), with an activity that can (but is not
     * currently) displaying over the lockscreen.
     *
     * Transition interactors use this to determine when we should transition to the OCCLUDED state.
     *
     * Outside of the transition/occlusion interactors, you almost certainly don't want to use this.
     * Instead, use KeyguardTransitionInteractor to figure out if we're in KeyguardState.OCCLUDED.
     */
    val isShowWhenLockedActivityOnTop: StateFlow<Boolean> =
        showWhenLockedActivityInfo
            .map { it.isOnTop }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    /** Whether we should start a transition due to the power button launch gesture. */
    fun shouldTransitionFromPowerButtonGesture(): Boolean {
        // powerButtonLaunchGestureTriggered remains true while we're awake from a power button
        // gesture. Check that we were asleep or transitioning to asleep before starting a
        // transition, to ensure we don't transition while moving between, for example,
        // *_BOUNCER -> LOCKSCREEN.
        return powerInteractor.detailedWakefulness.value.powerButtonLaunchGestureTriggered &&
            KeyguardState.deviceIsAsleepInState(
                internalTransitionInteractor.currentTransitionInfoInternal().to,
                if (SceneContainerFlag.isEnabled) {
                    sceneInteractor.get().currentScene.value
                } else {
                    null
                },
            )
    }

    /**
     * Whether the SHOW_WHEN_LOCKED activity was launched from the double tap power button gesture.
     * This remains true while the activity is running and emits false once it is killed.
     */
    val showWhenLockedActivityLaunchedFromPowerGesture =
        merge(
                // Emit true when the power launch gesture is triggered, since this means a
                // SHOW_WHEN_LOCKED activity will be launched from the gesture (unless we're
                // currently
                // GONE, in which case we're going back to GONE and launching the insecure camera).
                powerInteractor.detailedWakefulness
                    .sample(
                        transitionInteractor.isFinishedIn(
                            content = Scenes.Gone,
                            stateWithoutSceneContainer = KeyguardState.GONE,
                        ),
                        ::Pair,
                    )
                    .map { (wakefulness, isOnGone) ->
                        wakefulness.powerButtonLaunchGestureTriggered && !isOnGone
                    },
                // Emit false once that activity goes away.
                isShowWhenLockedActivityOnTop.filter { !it }.map { false },
            )
            .stateIn(applicationScope, SharingStarted.Eagerly, false)

    /**
     * Whether launching an occluding activity will automatically dismiss keyguard. This happens if
     * the keyguard is dismissable.
     */
    val occludingActivityWillDismissKeyguard: StateFlow<Boolean> =
        if (SceneContainerFlag.isEnabled) {
                deviceUnlockedInteractor.get().deviceUnlockStatus.map { it.isUnlocked }
            } else {
                keyguardInteractor.isKeyguardDismissible
            }
            .stateIn(scope = applicationScope, SharingStarted.Eagerly, false)

    /**
     * Called to let System UI know that WM says a SHOW_WHEN_LOCKED activity is on top (or no longer
     * on top).
     *
     * This signal arrives from WM when a SHOW_WHEN_LOCKED activity is started or killed - it is
     * never set directly by System UI. While we might be the reason the activity was started
     * (launching the camera from the power button gesture), we ultimately only receive this signal
     * once that activity starts. It's up to us to start the appropriate keyguard transitions,
     * because that activity is going to be visible (or not) regardless.
     */
    fun setOccludedFromWm(isOccluded: Boolean) {
        repository.setOccludedFromWm(isOccluded)
    }

    /**
     * Called when the remote animation for occlusion/unocclusion starts. This is an early signal
     * that we are occluding or unoccluding.
     */
    fun setOccludedFromRemoteAnimation(onTop: Boolean, taskInfo: RunningTaskInfo?) {
        repository.setOccludedFromRemoteAnimation(onTop, taskInfo)
    }

    /** Has the device been entered (Gone state) or asleep? */
    private val isGoneOrAsleep: StateFlow<Boolean> =
        anyOf(
                powerInteractor.isAsleep.onStart {
                    emit(powerInteractor.detailedWakefulness.value.isAsleep())
                },
                deviceEntryInteractor.isDeviceEntered,
            )
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    /**
     * Whether the keyguard is in an occluded state: when "show when locked" activity is present.
     * Asleep should always take precedence.
     */
    val isKeyguardOccluded: StateFlow<Boolean> =
        combine(isShowWhenLockedActivityOnTop, isGoneOrAsleep) {
                isShowWhenLockedActivityOnTop,
                isGoneOrAsleep ->
                isKeyguardOccluded(isShowWhenLockedActivityOnTop, isGoneOrAsleep)
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    isKeyguardOccluded(isShowWhenLockedActivityOnTop.value, isGoneOrAsleep.value),
            )

    /** The unified semantic state of occlusion, derived from WindowManager task info. */
    val occlusionState: StateFlow<OcclusionStateModel> =
        combine(showWhenLockedActivityInfo, isGoneOrAsleep, ::calculateOcclusionState)
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue =
                    calculateOcclusionState(showWhenLockedActivityInfo.value, isGoneOrAsleep.value),
            )

    private fun calculateOcclusionState(
        info: ShowWhenLockedActivityInfoModel,
        isGoneOrAsleep: Boolean,
    ): OcclusionStateModel {
        val occluded = isKeyguardOccluded(info.isOnTop, isGoneOrAsleep)
        return when {
            !occluded -> OcclusionStateModel.NONE
            !DriveDreamStateFromOcclusion.isEnabled -> OcclusionStateModel.LEGACY_OCCLUDED_GENERIC
            info.isDream() -> OcclusionStateModel.DREAM
            else -> OcclusionStateModel.APP
        }
    }

    private fun isKeyguardOccluded(
        isOccludingActivityShown: Boolean,
        isGoneOrAsleep: Boolean,
    ): Boolean {
        return isOccludingActivityShown && !isGoneOrAsleep
    }

    suspend fun hydrateTableLogBuffer(tableLogBuffer: TableLogBuffer) {
        occlusionState
            .logDiffsForTable(
                tableLogBuffer = tableLogBuffer,
                initialValue = OcclusionStateModel.NONE,
            )
            .collect()
    }
}
