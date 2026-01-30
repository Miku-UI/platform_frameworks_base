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

package com.android.systemui.communal.domain.interactor

import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.systemui.CoreStartable
import com.android.systemui.communal.data.repository.CommunalSceneTransitionRepository
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.keyguard.domain.interactor.InternalKeyguardTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionInfo
import com.android.systemui.keyguard.shared.model.TransitionModeOnCanceled
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.CommunalLog
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.util.kotlin.pairwise
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * This class listens to [SceneTransitionLayout] transitions and manages keyguard transition
 * framework (KTF) states accordingly for communal states.
 *
 * There are a few rules:
 * - There are only 2 communal scenes: [CommunalScenes.Communal] and [CommunalScenes.Blank]
 * - When scene framework is on [CommunalScenes.Blank], KTF is allowed to change its scenes freely
 * - When scene framework is on [CommunalScenes.Communal], KTF is locked into
 *   [KeyguardState.GLANCEABLE_HUB]
 */
@SysUISingleton
class CommunalSceneTransitionInteractor
@Inject
constructor(
    val transitionInteractor: KeyguardTransitionInteractor,
    val internalTransitionInteractor: InternalKeyguardTransitionInteractor,
    private val settingsInteractor: CommunalSettingsInteractor,
    @Application private val applicationScope: CoroutineScope,
    @Main private val mainImmediateDispatcher: CoroutineDispatcher,
    private val sceneInteractor: CommunalSceneInteractor,
    private val repository: CommunalSceneTransitionRepository,
    powerInteractor: PowerInteractor,
    keyguardInteractor: KeyguardInteractor,
    @CommunalLog logBuffer: LogBuffer,
) : CoreStartable, CommunalSceneInteractor.OnSceneAboutToChangeListener {

    private var currentTransitionId: UUID? = null
    private var progressJob: Job? = null

    private val logger = Logger(logBuffer, TAG)

    private val currentToState: KeyguardState
        get() = internalTransitionInteractor.currentTransitionInfoInternal().to

    /**
     * The next keyguard state to trigger when exiting [CommunalScenes.Communal]. This is only used
     * if the state is changed by user gesture or not explicitly defined by the caller when changing
     * scenes programmatically.
     *
     * This is needed because we do not always want to exit back to the KTF state we came from. For
     * example, when going from HUB (Communal) -> OCCLUDED (Blank) -> HUB (Communal) and then
     * closing the hub via gesture, we don't want to go back to OCCLUDED but instead either go to
     * DREAM or LOCKSCREEN depending on if there is a dream showing.
     */
    private val nextKeyguardStateInternal =
        combine(
                // Don't use delayed dreaming signal as otherwise we might go to occluded or lock
                // screen when closing hub if dream just started under the hub.
                powerInteractor.isAsleep,
                keyguardInteractor.isDreamingWithOverlay,
                keyguardInteractor.isKeyguardOccluded,
                // This flow doesn't emit immediately after OOBE until the first time keyguard is
                // going away. Emit false here on start to avoid blocking emitting the next
                // keyguard state. See b/423563289.
                keyguardInteractor.isKeyguardGoingAway.onStart { emit(false) },
                keyguardInteractor.isKeyguardShowing,
            ) { asleep, dreaming, occluded, keyguardGoingAway, keyguardShowing ->
                if (asleep) {
                    keyguardInteractor.asleepKeyguardState.value
                } else if (keyguardGoingAway) {
                    KeyguardState.GONE
                } else if (occluded && !dreaming) {
                    KeyguardState.OCCLUDED
                } else if (dreaming) {
                    KeyguardState.DREAMING
                } else if (keyguardShowing) {
                    KeyguardState.LOCKSCREEN
                } else {
                    null
                }
            }
            .filterNotNull()

    private val nextKeyguardState: StateFlow<KeyguardState> =
        combine(
                repository.nextLockscreenTargetState,
                nextKeyguardStateInternal.onStart { emit(KeyguardState.LOCKSCREEN) },
            ) { override, nextState ->
                override ?: nextState
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = KeyguardState.LOCKSCREEN,
            )

    override fun start() {
        if (settingsInteractor.isCommunalFlagEnabled() && !SceneContainerFlag.isEnabled) {
            sceneInteractor.registerSceneStateProcessor(this)
            listenForSceneTransitionProgress()
        }
    }

    /**
     * Called when the scene is programmatically changed, allowing callers to specify which KTF
     * state should be set when transitioning to [CommunalScenes.Blank]
     */
    override fun onSceneAboutToChange(toScene: SceneKey, keyguardState: KeyguardState?) {
        if (toScene != CommunalScenes.Blank || keyguardState == null) return
        repository.nextLockscreenTargetState.value = keyguardState
    }

    /** Monitors [SceneTransitionLayout] state and updates KTF state accordingly. */
    private fun listenForSceneTransitionProgress() {
        applicationScope.launch("$TAG#listenForSceneTransitionProgress", mainImmediateDispatcher) {
            sceneInteractor.transitionState
                .pairwise(ObservableTransitionState.Idle(CommunalScenes.Blank))
                .collect { (prevTransition, transition) ->
                    when (transition) {
                        is ObservableTransitionState.Idle -> handleIdle(prevTransition, transition)
                        is ObservableTransitionState.Transition ->
                            handleTransition(prevTransition, transition)
                    }
                }
        }
    }

    private suspend fun handleIdle(
        prevTransition: ObservableTransitionState,
        idle: ObservableTransitionState.Idle,
    ) {
        val isReversedTransition =
            prevTransition is ObservableTransitionState.Transition &&
                currentTransitionId != null &&
                idle.currentScene == prevTransition.fromContent
        if (
            prevTransition is ObservableTransitionState.Transition &&
                currentTransitionId != null &&
                idle.currentScene == prevTransition.toContent
        ) {
            finishCurrentTransition("transition finished")
        } else {
            // We may receive an Idle event without a corresponding Transition
            // event, such as when snapping to a scene without an animation, or the previous
            // is a reversed scene transition.
            val targetState =
                if (idle.currentScene == CommunalScenes.Communal) {
                    KeyguardState.GLANCEABLE_HUB
                } else if (currentToState == KeyguardState.GLANCEABLE_HUB) {
                    nextKeyguardState.value
                } else {
                    if (isReversedTransition) {
                        // Previous is a reversed transition, finish current ktf transition.
                        finishCurrentTransition("previous transition is reversed")
                    }
                    // Do nothing as we are no longer in the hub state.
                    return
                }
            transitionKtfTo(targetState, "snap to a new state without transition")
            repository.nextLockscreenTargetState.value = null
        }
    }

    private suspend fun finishCurrentTransition(reason: String) {
        if (currentTransitionId == null) return
        logger.i({ "Finishing current keyguard transition: $str1" }) { str1 = reason }
        internalTransitionInteractor.updateTransition(
            currentTransitionId!!,
            1f,
            TransitionState.FINISHED,
        )
        resetTransitionData()
    }

    private suspend fun finishReversedTransitionTo(state: KeyguardState, reason: String) {
        val newTransition =
            TransitionInfo(
                ownerName = this::class.java.simpleName,
                from = internalTransitionInteractor.currentTransitionInfoInternal().to,
                to = state,
                animator = null,
                modeOnCanceled = TransitionModeOnCanceled.REVERSE,
            )
        startTransition(newTransition, reason)
        internalTransitionInteractor.updateTransition(
            currentTransitionId!!,
            1f,
            TransitionState.FINISHED,
        )
        resetTransitionData()
    }

    private fun resetTransitionData() {
        progressJob?.cancel()
        progressJob = null
        currentTransitionId = null
    }

    private suspend fun handleTransition(
        prevTransition: ObservableTransitionState,
        transition: ObservableTransitionState.Transition,
    ) {
        if (
            prevTransition.isTransitioning(
                from = transition.fromContent,
                to = transition.toContent,
            ) && !sceneInteractor.targetSceneChanged.value
        ) {
            // This is a new transition, but exactly the same as the previous state. Skip resetting
            // KTF for this case and just collect the new progress instead.
            collectProgress(transition)
            return
        }
        if (
            prevTransition.isTransitioning(from = transition.fromContent, to = transition.toContent)
        ) {
            // The transition has the same from and to content as the previous transition, but
            // different target scene, it may be a reversed transition.
            val targetScene = sceneInteractor.currentScene.value
            if (
                targetScene == CommunalScenes.Blank &&
                    transition.fromContent == CommunalScenes.Blank
            ) {
                // Prev transition: Blank->Communal, X->hub running
                // New transition: Blank->Communal (reversed->Blank), we should reverse->X
                startReversedTransitionToState(
                    nextKeyguardState.value,
                    "reverse to the next keyguard state",
                )
                collectProgress(transition, isReversed = true)
                return
            }
            if (
                targetScene == CommunalScenes.Communal &&
                    transition.fromContent == CommunalScenes.Communal
            ) {
                // Prev transition: Communal->Blank, hub->X running
                // New transition: Communal->Blank (reversed->Communal), we should reverse->hub
                startReversedTransitionToState(KeyguardState.GLANCEABLE_HUB, "reverse to hub")
                collectProgress(transition, isReversed = true)
                return
            }
        }
        // The new transition does not have the same content key with the previous, or it has the
        // same content key and toContent is just the target scene
        if (transition.toContent == CommunalScenes.Communal) {
            if (currentToState == KeyguardState.GLANCEABLE_HUB) {
                transitionKtfTo(
                    transitionInteractor.startedKeyguardTransitionStep.value.from,
                    "make sure keyguard is ready to transition to hub",
                )
            }
            startTransitionToGlanceableHub("blank -> communal scene transition started")
            collectProgress(transition)
        } else if (transition.toContent == CommunalScenes.Blank) {
            // Another transition started before this one is completed. Transition to the
            // GLANCEABLE_HUB state so that we can properly transition away from it.
            transitionKtfTo(
                KeyguardState.GLANCEABLE_HUB,
                "another transition started before the previous one finished",
            )
            startTransitionFromGlanceableHub("communal -> blank scene transition started")
            collectProgress(transition)
        }
    }

    private suspend fun transitionKtfTo(state: KeyguardState, reason: String) {
        val currentTransition = transitionInteractor.transitionState.value
        if (currentTransition.isFinishedIn(state)) {
            // This is already the state we want to be in
            resetTransitionData()
        } else if (currentTransition.isTransitioning(to = state)) {
            finishCurrentTransition(reason)
        } else {
            finishReversedTransitionTo(state, reason)
        }
    }

    private fun collectProgress(
        transition: ObservableTransitionState.Transition,
        isReversed: Boolean = false,
    ) {
        progressJob?.cancel()
        progressJob =
            applicationScope.launch("$TAG#collectProgress", mainImmediateDispatcher) {
                transition.progress.collect {
                    // during a reversed scene transition, the progress is from 1 to 0
                    val progress = if (isReversed) 1f - it else it
                    updateProgress(progress)
                }
            }
    }

    private suspend fun startTransitionFromGlanceableHub(reason: String) {
        val newTransition =
            TransitionInfo(
                ownerName = this::class.java.simpleName,
                from = KeyguardState.GLANCEABLE_HUB,
                to = nextKeyguardState.value,
                animator = null,
                modeOnCanceled = TransitionModeOnCanceled.RESET,
            )
        repository.nextLockscreenTargetState.value = null
        startTransition(newTransition, reason)
    }

    private suspend fun startTransitionToGlanceableHub(reason: String) {
        val currentState = internalTransitionInteractor.currentTransitionInfoInternal().to
        val newTransition =
            TransitionInfo(
                ownerName = this::class.java.simpleName,
                from = currentState,
                to = KeyguardState.GLANCEABLE_HUB,
                animator = null,
                modeOnCanceled = TransitionModeOnCanceled.RESET,
            )
        startTransition(newTransition, reason)
    }

    private suspend fun startReversedTransitionToState(state: KeyguardState, reason: String) {
        val currentState = internalTransitionInteractor.currentTransitionInfoInternal().to
        val newTransition =
            TransitionInfo(
                ownerName = this::class.java.simpleName,
                from = currentState,
                to = state,
                animator = null,
                modeOnCanceled = TransitionModeOnCanceled.REVERSE,
            )
        startTransition(newTransition, reason)
    }

    private suspend fun startTransition(transitionInfo: TransitionInfo, reason: String) {
        if (currentTransitionId != null) {
            resetTransitionData()
        }
        logger.i({ "Requesting keyguard transition $str1 -> $str2: $str3" }) {
            str1 = transitionInfo.from.name
            str2 = transitionInfo.to.name
            str3 = reason
        }
        currentTransitionId = internalTransitionInteractor.startTransition(transitionInfo)
    }

    private suspend fun updateProgress(progress: Float) {
        if (currentTransitionId == null) return
        internalTransitionInteractor.updateTransition(
            currentTransitionId!!,
            progress.coerceIn(0f, 1f),
            TransitionState.RUNNING,
        )
    }

    private companion object {
        const val TAG = "CommunalSceneTransitionInteractor"
    }
}
