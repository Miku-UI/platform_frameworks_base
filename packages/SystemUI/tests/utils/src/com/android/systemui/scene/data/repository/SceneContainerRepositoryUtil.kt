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

package com.android.systemui.scene.data.repository

import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.testScope
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

private val mutableTransitionState =
    MutableStateFlow<ObservableTransitionState>(ObservableTransitionState.Idle(Scenes.Lockscreen))

suspend fun Kosmos.setTransition(
    /** The scene transition to be set on the scene container. */
    sceneTransition: ObservableTransitionState,
    /**
     * The transition step to be set on KTF. When sceneContainerFlag is true and you pass deprecated
     * KeyguardStates they will be automatically converted to UNDEFINED to match what would be
     * possible in prod.
     */
    stateTransition: TransitionStep? = null,
    /**
     * If true unlock the device before changing the scene. This makes transitions to Gone or
     * Idle(Gone) possible. The device will be locked afterwards again.
     */
    unlockDevice: Boolean = false,
    /**
     * If true skip the actual `changeScene` call which contains validity checks about the current
     * scene. If you are just observing the `transitionState` (ObservableTransitionState) in your
     * test you can skip this. If you need the `currentScene` or other fields provided by the
     * SceneDataSource you need to pass false here so the sceneInteractor is actually making a scene
     * change including all checks.
     */
    skipChangeScene: Boolean = false,
) {
    var state: TransitionStep? = stateTransition
    if (SceneContainerFlag.isEnabled) {
        setSceneTransition(
            transition = sceneTransition,
            unlockDevice = unlockDevice,
            skipChangeScene = skipChangeScene,
        )

        if (state != null) {
            state = getSceneContainerConvertedState(state)
        }
    }

    if (state == null) return
    fakeKeyguardTransitionRepository.sendTransitionSteps(
        step = state,
        testScope = testScope,
        fillInSteps = true,
    )
    runCurrent()
}

fun Kosmos.setSceneTransition(
    /** The scene transition to be set on the scene container. */
    transition: ObservableTransitionState,
    /**
     * If true unlock the device before changing the scene. This makes transitions to Gone or
     * Idle(Gone) possible. The device will be locked afterwards again.
     */
    unlockDevice: Boolean = false,
    /**
     * If true skip the actual `changeScene` call which contains validity checks about the current
     * scene. If you are just observing the `transitionState` (ObservableTransitionState) in your
     * test you can skip this. If you need the `currentScene` or other fields provided by the
     * SceneDataSource you need to pass false here so the sceneInteractor is actually making a scene
     * change including all checks.
     */
    skipChangeScene: Boolean = false,
) {
    sceneContainerRepository.setTransitionState(mutableTransitionState)
    mutableTransitionState.value = transition
    if (unlockDevice) {
        unlockDevice()
    }
    if (!skipChangeScene) {
        runCurrent()
        testScope.backgroundScope
        if (transition is ObservableTransitionState.Idle && transition.isIdle()) {
            sceneContainerRepository.instantlyTransitionTo(
                transition.currentScene,
                transition.currentOverlays,
            )
        } else {
            sceneInteractor.startTransitionImmediately(
                transition.toTransitionState() as TransitionState.Transition
            )
        }
    }
    if (unlockDevice) {
        lockDevice()
    }
    runCurrent()
}

fun Kosmos.unlockDevice() {
    fakeDeviceEntryRepository.deviceUnlockStatus.value =
        DeviceUnlockStatus(isUnlocked = true, deviceUnlockSource = null)
    runCurrent()
}

fun Kosmos.lockDevice() {
    fakeDeviceEntryRepository.deviceUnlockStatus.value =
        DeviceUnlockStatus(isUnlocked = false, deviceUnlockSource = null)
    runCurrent()
}

private fun getCurrentCurrentScene(transition: ObservableTransitionState): SceneKey {
    return when (transition) {
        is ObservableTransitionState.Idle -> transition.currentScene
        is ObservableTransitionState.Transition.ChangeScene ->
            runBlocking {
                transition.currentScene.firstOrNull()
                    ?: throw error("Empty currentScene Flow provided")
            }
        is ObservableTransitionState.Transition.ReplaceOverlay -> transition.currentScene
        is ObservableTransitionState.Transition.ShowOrHideOverlay -> transition.currentScene
    }
}

fun Transition(
    from: SceneKey,
    to: SceneKey,
    currentScene: Flow<SceneKey> = flowOf(to),
    progress: Flow<Float> = flowOf(0f),
    isInitiatedByUserInput: Boolean = false,
    isUserInputOngoing: Flow<Boolean> = flowOf(false),
    previewProgress: Flow<Float> = flowOf(0f),
    isInPreviewStage: Flow<Boolean> = flowOf(false),
    key: TransitionKey? = null,
): ObservableTransitionState.Transition {
    return ObservableTransitionState.Transition(
        fromScene = from,
        toScene = to,
        currentScene = currentScene,
        progress = progress,
        isInitiatedByUserInput = isInitiatedByUserInput,
        isUserInputOngoing = isUserInputOngoing,
        previewProgress = previewProgress,
        isInPreviewStage = isInPreviewStage,
        key = key,
    )
}

fun ShowOverlay(
    overlay: OverlayKey,
    fromScene: SceneKey,
    currentOverlays: Flow<Set<OverlayKey>> = flowOf(setOf(overlay)),
    progress: Flow<Float> = flowOf(0f),
    isInitiatedByUserInput: Boolean = false,
    isUserInputOngoing: Flow<Boolean> = flowOf(false),
    previewProgress: Flow<Float> = flowOf(0f),
    isInPreviewStage: Flow<Boolean> = flowOf(false),
): ObservableTransitionState.Transition {
    return ObservableTransitionState.Transition.showOverlay(
        overlay = overlay,
        fromScene = fromScene,
        currentOverlays = currentOverlays,
        progress = progress,
        isInitiatedByUserInput = isInitiatedByUserInput,
        isUserInputOngoing = isUserInputOngoing,
        previewProgress = previewProgress,
        isInPreviewStage = isInPreviewStage,
    )
}

fun HideOverlay(
    overlay: OverlayKey,
    toScene: SceneKey,
    currentOverlays: Flow<Set<OverlayKey>> = flowOf(setOf(overlay)),
    progress: Flow<Float> = flowOf(0f),
    isInitiatedByUserInput: Boolean = false,
    isUserInputOngoing: Flow<Boolean> = flowOf(false),
    previewProgress: Flow<Float> = flowOf(0f),
    isInPreviewStage: Flow<Boolean> = flowOf(false),
): ObservableTransitionState.Transition {
    return ObservableTransitionState.Transition.hideOverlay(
        overlay = overlay,
        toScene = toScene,
        currentOverlays = currentOverlays,
        progress = progress,
        isInitiatedByUserInput = isInitiatedByUserInput,
        isUserInputOngoing = isUserInputOngoing,
        previewProgress = previewProgress,
        isInPreviewStage = isInPreviewStage,
    )
}

fun Idle(
    currentScene: SceneKey,
    currentOverlays: Set<OverlayKey> = emptySet(),
): ObservableTransitionState.Idle {
    return ObservableTransitionState.Idle(currentScene, currentOverlays)
}

private fun getSceneContainerConvertedState(state: TransitionStep): TransitionStep? {
    val step =
        TransitionStep(
            from = state.from.mapToSceneContainerState(),
            to = state.to.mapToSceneContainerState(),
            value = state.value,
            transitionState = state.transitionState,
        )
    if (step.from == KeyguardState.UNDEFINED && step.to == KeyguardState.UNDEFINED) return null
    return step
}
