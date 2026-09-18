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

package com.android.systemui.keyguard.domain.interactor.scenetransition

import android.util.Log
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.TAG
import com.android.systemui.keyguard.data.repository.LockscreenSceneTransitionRepository
import com.android.systemui.keyguard.domain.interactor.InternalKeyguardTransitionInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.KeyguardState.UNDEFINED
import com.android.systemui.keyguard.shared.model.TransitionInfo
import com.android.systemui.keyguard.shared.model.TransitionModeOnCanceled
import com.android.systemui.keyguard.shared.model.TransitionState.FINISHED
import com.android.systemui.keyguard.shared.model.TransitionState.RUNNING
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.util.kotlin.pairwise
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * This class listens to scene framework scene transitions and manages keyguard transition framework
 * (KTF) states accordingly.
 *
 * There are a few rules:
 * - When scene framework is on a scene outside of Lockscreen, then KTF is in state UNDEFINED
 * - When scene framework is on Lockscreen, KTF is allowed to change its scenes freely
 * - When scene framework is transitioning away from Lockscreen, then KTF transitions to UNDEFINED
 *   and shares its progress.
 * - When scene framework is transitioning to Lockscreen, then KTF starts a transition to LOCKSCREEN
 *   but it is allowed to interrupt this transition and transition to other internal KTF states
 *
 * There are a few notable differences between SceneTransitionLayout (STL) and KTF that require
 * special treatment when synchronizing both state machines.
 * - STL does not emit cancelations as KTF does
 * - Both STL and KTF require state continuity, though the rules from where starting the next
 *   transition is allowed is different on each side:
 *     - STL has a concept of "currentScene" which can be chosen to be either A or B in a A -> B
 *       transition. The currentScene determines which transition can be started next. In KTF the
 *       currentScene is always the `to` state. Which means transitions can only be started from B.
 *       This also holds true when A -> B was canceled: the next transition needs to start from B.
 *     - KTF can not settle back in its from scene, instead it needs to cancel and start a reversed
 *       transition.
 */
@SysUISingleton
class LockscreenSceneTransitionInteractor
@Inject
constructor(
    private val transitionInteractor: KeyguardTransitionInteractor,
    private val internalTransitionInteractor: InternalKeyguardTransitionInteractor,
    @Application private val applicationScope: CoroutineScope,
    private val sceneInteractor: SceneInteractor,
    private val repository: LockscreenSceneTransitionRepository,
    private val powerInteractor: PowerInteractor,
) : CoreStartable {

    private var currentTransitionId: UUID? = null
    private var progressJob: Job? = null
    private var transitionCurrentSceneJob: Job? = null

    override fun start() {
        listenForSceneTransitionProgress()
        listenForWakefulness()
    }

    private fun listenForWakefulness() {
        applicationScope.launch {
            powerInteractor.isAwake
                .filter { awake -> awake }
                .collect {
                    repository.nextLockscreenTargetState.value?.let {
                        if (KeyguardState.deviceIsAsleepInState(it, scene = null)) {
                            // Clear the next lockscreen state if we're waking up and that state is
                            // an asleep state. Typically, this would be cleared by a changeScene
                            // call, but in some interruption scenarios, we don't change scenes (for
                            // example, if we're doing Shade -> Lockscreen, and then sleep and wake
                            // back up, we'll simply finish the transition to Lockscreen even though
                            // KTF should do UNDEFINED -> AOD (cancel) -> LOCKSCREEN. If we don't
                            // clear this, FromAodTransitionInteractor will wake us from
                            // AOD -> LOCKSCREEN, but we will then return to AOD upon idling in
                            // Scenes.Lockscreen since that's the next lockscreen target state. We
                            // could also fix this by clearing the next lockscreen target state in
                            // each From*TransitionInteractor, but that would require adding a
                            // dependency on this class, which is not ideal. This seems like the
                            // most reasonable fix until we can eliminate KTF.
                            Log.d(TAG, "Clearing nextLsState; it's a sleep state but we're awake")
                            setNextLockscreenTargetState(null)
                        }
                    }
                }
        }
    }

    fun setNextLockscreenTargetState(keyguardState: KeyguardState?) {
        repository.nextLockscreenTargetState.value = keyguardState
    }

    private fun listenForSceneTransitionProgress() {
        applicationScope.launch {
            sceneInteractor.transitionStateFlow
                // The transitionState re-emits when things like the isUserInputOngoing flow change,
                // which we're not interested in.
                .distinctUntilChanged { state1, state2 ->
                    when (state1) {
                        is ObservableTransitionState.Idle ->
                            state2 is ObservableTransitionState.Idle &&
                                state2.currentScene == state1.currentScene &&
                                state2.currentOverlays == state1.currentOverlays
                        is ObservableTransitionState.Transition ->
                            state2 is ObservableTransitionState.Transition &&
                                state2.fromContent == state1.fromContent &&
                                state2.toContent == state1.toContent
                    }
                }
                .pairwise(ObservableTransitionState.Idle(Scenes.Lockscreen))
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
        // We're idle, so this collection is no longer relevant.
        transitionCurrentSceneJob?.cancel()

        when (prevTransition) {
            is ObservableTransitionState.Idle -> handleSnapFromIdleToIdle(prevTransition, idle)
            is ObservableTransitionState.Transition -> {
                // If the previous transition's fromContent is still in currentOverlays, we canceled
                // a transition away from that overlay.
                val canceledOverlayTransition =
                    idle.currentOverlays.contains(prevTransition.fromContent)

                // We're idle on the scene or overlay we were performing a scene transition to.
                val idleOnToContentAfterCompletedTransition =
                    (idle.currentScene == prevTransition.toContent && !canceledOverlayTransition) ||
                        // Idle while showing the overlay we were transitioning to.
                        idle.currentOverlays.contains(prevTransition.toContent)

                // We're idle on the Lockscreen scene and KTF was transitioning to a non-UNDEFINED
                // state. This can be true even if idleOnToContentAfterCompletedTransition is false
                // when we're dealing with interrupted, reversed Scene transitions where internal
                // KTF transitions were separately interrupted. For example:
                //   1. Lockscreen -> Shade is in progress.
                //   2. Power button press, we return to Scenes.Lockscreen/KeyguardState.AOD, but
                //      the Scene framework does this by reversing the current Lockscreen -> Shade
                //      transition but emitting Lockscreen from currentScene.
                //   3. Another power button press occurs, interrupting UNDEFINED -> AOD and
                //      starting AOD -> LOCKSCREEN. The reversed scene transition from
                //      Lockscreen -> Shade is still running and is not aware that we're waking back
                //      up (WAI, since it does not care, we *do* want to end up in
                //      Scenes.Lockscreen.
                //   4. We Idle in Scenes.Lockscreen. The keyguardState passed into the changeScene
                //      call that started this transition was... AOD, so we transition back to AOD
                //      while awake.
                //
                // The prevTransition's toContent this entire time is Shade due to the reversal, so
                // idleOnToContentAfterCompletedTransition is never true.
                val idleOnLockscreenDuringKtfTransitionFromUndefined =
                    idle.currentScene == Scenes.Lockscreen &&
                        idle.currentOverlays.isEmpty() &&
                        internalTransitionInteractor.currentTransitionInfoInternal().to != UNDEFINED

                // If we started a KTF transition in handleTransition, and we're idle on the content
                // we meant to transition to, simply finish the transition.
                if (
                    currentTransitionId != null && idleOnToContentAfterCompletedTransition ||
                        idleOnLockscreenDuringKtfTransitionFromUndefined
                ) {
                    finishCurrentTransition()
                } else {
                    // Otherwise, we're snapping from a transition that didn't start a KTF
                    // transition (for example, snapping to Idle on Lockscreen from a Gone -> Shade
                    // transition). We'll need to start and finish a KTF transition to the
                    // appropriate state here.
                    val targetState =
                        if (
                            idle.currentScene == Scenes.Lockscreen && idle.currentOverlays.isEmpty()
                        ) {
                            repository.nextLockscreenTargetState.value
                                ?: transitionInteractor.startedKeyguardTransitionStep.value.from
                        } else {
                            UNDEFINED
                        }

                    // ...assuming we're not already there.
                    if (
                        internalTransitionInteractor.currentTransitionInfoInternal().to !=
                            targetState
                    ) {
                        startAndFinishTransitionTo(
                            targetState,
                            "Idle after non-KTF relevant transition",
                        )
                    } else if (currentTransitionId != null) {
                        // We're already there but a transition is still running, we will need to
                        // finish it.
                        finishCurrentTransition()
                    }
                }
            }
        }
    }

    /**
     * Handles the case where we snap from being Idle in one scene to Idle in another, without
     * previously being in Transition.
     */
    private suspend fun handleSnapFromIdleToIdle(
        fromIdle: ObservableTransitionState.Idle,
        toIdle: ObservableTransitionState.Idle,
    ) {
        if (
            toIdle.currentScene == Scenes.Lockscreen &&
                (fromIdle.currentScene != Scenes.Lockscreen ||
                    (fromIdle.currentOverlays.isNotEmpty() && toIdle.currentOverlays.isEmpty()))
        ) {
            // We've snapped to Lockscreen either from a non-Lockscreen scene, or from showing
            // an overlay over Lockscreen. In either case, we were in UNDEFINED and should now
            // transition to the appropriate KTF state.
            startAndFinishTransitionTo(
                getNextLockscreenTargetState(),
                "snap to Idle on LS" +
                    if (toIdle.currentOverlays.isNotEmpty()) " Overlays: " + toIdle.currentOverlays
                    else "",
            )
        } else if (
            (toIdle.currentScene != Scenes.Lockscreen || toIdle.currentOverlays.isNotEmpty())
        ) {
            // We've snapped to non-Lockscreen content, either a scene or Lockscreen with an overlay
            // showing. In either case, we should be UNDEFINED.
            startAndFinishTransitionTo(
                UNDEFINED,
                "snap to Idle on ${toIdle.currentScene}" +
                    if (toIdle.currentOverlays.isNotEmpty()) " Overlays: " + toIdle.currentOverlays
                    else "",
            )
        }
    }

    private suspend fun handleTransition(
        prevTransition: ObservableTransitionState,
        transition: ObservableTransitionState.Transition,
    ) {
        transitionCurrentSceneJob?.cancel()

        // Check whether this Transition follows another Transition (without going through Idle).
        // Special setup may be needed.
        maybeHandleSnapFromTransitionToTransition(prevTransition, transition)

        // Collect the currentScene, which is the toScene, unless the transition is reversed, in
        // which case it's the scene we're headed towards. For example, if Gone -> Lockscreen is
        // reversed, toScene will remain Lockscreen and fromScene Gone as progress is reversed back
        // to 0f, but currentScene will emit Gone immediately.
        transitionCurrentSceneJob =
            applicationScope.launch {
                transition.currentScene().collect { currentScene ->
                    if (transition.fromContent == Scenes.Lockscreen) {
                        startTransitionToUndefined()
                        collectProgress(transition)
                    } else if (currentScene == Scenes.Lockscreen) {
                        startTransitionFromUndefinedToLockscreenTargetState()
                        collectProgress(transition)
                    } else {
                        transitionKtfToFinishedInState(
                            UNDEFINED,
                            "transition not from LS and currentScene != LS",
                        )
                    }
                }
            }
    }

    /**
     * Handles relevant cases where we "snap" from one Transition to another, without going through
     * Idle.
     */
    private suspend fun maybeHandleSnapFromTransitionToTransition(
        prevTransition: ObservableTransitionState,
        transition: ObservableTransitionState.Transition,
    ) {
        // Duplicate transition, likely the same one started for a different reason. This should
        // not affect KTF.
        if (
            prevTransition is ObservableTransitionState.Transition &&
                prevTransition.fromContent == transition.fromContent &&
                prevTransition.toContent == transition.toContent
        ) {
            return
        }

        // We are now transitioning from Lockscreen again, and we were either:
        // - Already running a KTF transition away from Lockscreen (to UNDEFINED),
        // - Were in transition between two UNDEFINED scenes.
        // This wouldn't be allowed in KTF, so we need a workaround here to ensure KTF listeners
        // aren't left hanging. Return KTF to the state it came from, which will emit FINISHED.
        // Then, the subsequent transition will be from that state to whatever new state we
        // determine is required.
        if (
            transition.fromContent == Scenes.Lockscreen &&
                internalTransitionInteractor.currentTransitionInfoInternal().to == UNDEFINED &&
                (currentTransitionId != null ||
                    (prevTransition is ObservableTransitionState.Transition &&
                        prevTransition.fromContent != Scenes.Lockscreen &&
                        prevTransition.toContent != Scenes.Lockscreen))
        ) {
            val prevFrom = transitionInteractor.startedKeyguardTransitionStep.value.from
            transitionKtfToFinishedInState(
                prevFrom,
                "snap from Transition -> Transition, reset KTF",
            )
        }
    }

    private fun getNextLockscreenTargetState(): KeyguardState {
        return repository.nextLockscreenTargetState.value ?: LOCKSCREEN
    }

    private suspend fun finishCurrentTransition() {
        internalTransitionInteractor.updateTransition(currentTransitionId!!, 1f, FINISHED)
        resetTransitionData()
    }

    private fun resetTransitionData() {
        progressJob?.cancel()
        progressJob = null
        currentTransitionId = null
    }

    private fun collectProgress(transition: ObservableTransitionState.Transition) {
        progressJob?.cancel()
        progressJob = applicationScope.launch { transition.progress.collect { updateProgress(it) } }
    }

    /**
     * Do whatever is necessary to ensure KTF is in the provided state, either by finishing ongoing
     * transitions or starting/finishing a new one.
     */
    private suspend fun transitionKtfToFinishedInState(state: KeyguardState, reason: String) {
        // TODO(b/330311871): This is based on a sharedFlow and thus might not be up-to-date and
        //  cause a race condition. (There is no known scenario that is currently affected.)
        val currentTransition = transitionInteractor.transitionState.value
        if (currentTransition.isFinishedIn(state)) {
            // This is already the state we want to be in
            resetTransitionData()
        } else if (currentTransition.isTransitioning(to = state)) {
            finishCurrentTransition()
        } else {
            startAndFinishTransitionTo(state, reason)
        }
    }

    /**
     * Starts a KTF transition from UNDEFINED to LOCKSCREEN/AOD/etc, but does not finish it. The
     * transition must be updated and finished manually.
     */
    private suspend fun startTransitionFromUndefinedToLockscreenTargetState() {
        if (internalTransitionInteractor.currentTransitionInfoInternal().to != UNDEFINED) {
            transitionKtfToFinishedInState(
                UNDEFINED,
                "startTransitionFromUndefined, but not UNDEFINED",
            )
        }

        val newTransition =
            TransitionInfo(
                ownerName =
                    "${this::class.java.simpleName} " +
                        "(startTransitionFromUndefinedToLockscreenTargetState)",
                from = UNDEFINED,
                to = getNextLockscreenTargetState(),
                animator = null,
                modeOnCanceled = TransitionModeOnCanceled.RESET,
            )
        setNextLockscreenTargetState(null)
        startTransition(newTransition)
    }

    /**
     * Starts a KTF transition to UNDEFINED, but does not finish it. The transition must be updated
     * and finished manually.
     */
    private suspend fun startTransitionToUndefined() {
        val currentState = internalTransitionInteractor.currentTransitionInfoInternal().to

        if (currentState == UNDEFINED) {
            return
        }

        val newTransition =
            TransitionInfo(
                ownerName = "${this::class.java.simpleName} (startTransitionToUndefined)",
                from = currentState,
                to = UNDEFINED,
                animator = null,
                modeOnCanceled = TransitionModeOnCanceled.RESET,
            )
        setNextLockscreenTargetState(null)
        startTransition(newTransition)
    }

    /** Sends STARTED and FINISHED steps to the provided state, then clears the transition. */
    private suspend fun startAndFinishTransitionTo(state: KeyguardState, reason: String) {
        if (internalTransitionInteractor.currentTransitionInfoInternal().to == state) {
            return
        }

        val newTransition =
            TransitionInfo(
                ownerName = "${this::class.java.simpleName} ($reason)",
                from = internalTransitionInteractor.currentTransitionInfoInternal().to,
                to = state,
                animator = null,
                modeOnCanceled = TransitionModeOnCanceled.REVERSE,
            )
        currentTransitionId = internalTransitionInteractor.startTransition(newTransition)
        internalTransitionInteractor.updateTransition(currentTransitionId!!, 1f, FINISHED)
        resetTransitionData()
    }

    private suspend fun startTransition(transitionInfo: TransitionInfo) {
        if (currentTransitionId != null) {
            resetTransitionData()
        }
        currentTransitionId = internalTransitionInteractor.startTransition(transitionInfo)
    }

    private suspend fun updateProgress(progress: Float) {
        if (currentTransitionId == null) return
        internalTransitionInteractor.updateTransition(
            currentTransitionId!!,
            progress.coerceIn(0f, 1f),
            RUNNING,
        )
    }
}
