/*
 *  Copyright (C) 2022 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.systemui.keyguard.domain.interactor

import android.annotation.SuppressLint
import android.util.Log
import com.android.app.tracing.coroutines.flow.filterTraced
import com.android.app.tracing.coroutines.flow.shareInTraced
import com.android.app.tracing.coroutines.flow.stateInTraced
import com.android.app.tracing.coroutines.flow.traceAs
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.app.tracing.coroutines.traceCoroutine
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.Flags.keyguardTransitionForceFinishOnScreenOff
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.data.repository.KeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.KeyguardState.OFF
import com.android.systemui.keyguard.shared.model.KeyguardState.UNDEFINED
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.shared.model.ScreenPowerState
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.util.kotlin.WithPrev
import com.android.systemui.util.kotlin.mapDirect
import com.android.systemui.util.kotlin.pairwise
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Encapsulates business-logic related to the keyguard transitions. */
@SysUISingleton
class KeyguardTransitionInteractor
@Inject
constructor(
    @Application val scope: CoroutineScope,
    private val repository: KeyguardTransitionRepository,
    private val sceneInteractor: SceneInteractor,
    private val powerInteractor: PowerInteractor,
) {
    private val transitionMap = mutableMapOf<Edge.StateToState, MutableSharedFlow<TransitionStep>>()

    /**
     * Numerous flows are derived from, or care directly about, the transition value in and out of a
     * single state. This prevent the redundant filters from running.
     */
    private val transitionValueCache = mutableMapOf<KeyguardState, MutableSharedFlow<Float>>()

    @SuppressLint("SharedFlowCreation")
    private fun getTransitionValueFlow(state: KeyguardState): MutableSharedFlow<Float> {
        return transitionValueCache.getOrPut(state) {
            MutableSharedFlow<Float>(
                    replay = 1,
                    extraBufferCapacity = 2,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST,
                )
                .traceAs("KTF-${state.name}")
        }
    }

    @Deprecated("Not performant - Use something else in this class")
    val transitions = repository.transitions

    val transitionState: StateFlow<TransitionStep> =
        transitions.stateInTraced(
            "KTF-transitionState",
            scope,
            SharingStarted.Eagerly,
            TransitionStep(),
        )

    private val sceneTransitionPair =
        sceneInteractor.transitionStateFlow
            .pairwise()
            .stateInTraced(
                "KTF-sceneTransitionPair",
                scope,
                SharingStarted.Eagerly,
                WithPrev(
                    sceneInteractor.transitionStateFlow.value,
                    sceneInteractor.transitionStateFlow.value,
                ),
            )

    /**
     * A pair of the most recent STARTED step, and the transition step immediately preceding it. The
     * transition framework enforces that the previous step is either a CANCELED or FINISHED step,
     * and that the previous step was *to* the state the STARTED step is *from*.
     *
     * This flow can be used to access the previous step to determine whether it was CANCELED or
     * FINISHED. In the case of a CANCELED step, we can also figure out which state we were coming
     * from when we were canceled.
     */
    @SuppressLint("SharedFlowCreation")
    @Deprecated("Use scene container aware alternatives in this class.")
    val startedStepWithPrecedingStep =
        repository.transitions
            .pairwise()
            .filter { it.newValue.transitionState == TransitionState.STARTED }
            .shareInTraced(
                "KTF-startedStepWithPrecedingStep",
                scope,
                SharingStarted.Eagerly,
                replay = 1,
            )

    init {
        // Create on startup to avoid overhead during first transition
        for (state in KeyguardState.entries) {
            getTransitionValueFlow(state).tryEmit(0f)
        }

        scope.launch("KTF-transition-update") {
            repository.transitions.collect { step ->
                // Collect non-canceled steps and emit transition values.
                if (step.transitionState != TransitionState.CANCELED) {
                    val value =
                        if (step.transitionState == TransitionState.FINISHED) 1f else step.value
                    getTransitionValueFlow(step.from).tryEmit(1f - value)
                    getTransitionValueFlow(step.to).tryEmit(value)
                }

                // FROM->TO
                transitionMap[Edge.create(step.from, step.to)]?.tryEmit(step)
                // FROM->(ANY)
                transitionMap[Edge.create(step.from, null)]?.tryEmit(step)
                // (ANY)->TO
                transitionMap[Edge.create(null, step.to)]?.tryEmit(step)

                // Safety: When any transition is FINISHED, ensure all other transitionValue flows
                // other than the FINISHED state are reset to a value of 0f. There have been rare
                // but severe bugs that get the device stuck in a bad state when these are not
                // properly reset.
                if (step.transitionState == TransitionState.FINISHED) {
                    for (state in KeyguardState.entries) {
                        if (state != step.to) {
                            val flow = getTransitionValueFlow(state)
                            val replayCache = flow.replayCache
                            if (!replayCache.isEmpty() && replayCache.last() != 0f) {
                                flow.tryEmit(0f)
                            }
                        }
                    }
                }
            }
        }

        // If a transition from state A -> B is canceled in favor of a transition from B -> C, we
        // need to ensure we emit transitionValue(A) = 0f, since no further steps will be emitted
        // where the from or to states are A. This would leave transitionValue(A) stuck at an
        // arbitrary non-zero value.
        scope.launch("KTF-update-canceled") {
            startedStepWithPrecedingStep.collect { (prevStep, startedStep) ->
                if (
                    prevStep.transitionState == TransitionState.CANCELED &&
                        startedStep.to != prevStep.from
                ) {
                    getTransitionValueFlow(prevStep.from).emit(0f)
                } else if (prevStep.transitionState == TransitionState.RUNNING) {
                    Log.e(
                        TAG,
                        "STARTED step ($startedStep) was preceded by a RUNNING step " +
                            "($prevStep), which should never happen. Things could go badly here.",
                    )
                }
            }
        }

        if (keyguardTransitionForceFinishOnScreenOff()) {
            /**
             * If the screen is turning off, finish the current transition immediately. Further
             * frames won't be visible anyway. However, when going to AOD, the screen goes to OFF
             * but then right to DOZE, so we need the animation to continue.
             */
            scope.launch("KTF-force-finish") {
                powerInteractor.screenPowerState
                    .filter {
                        it == ScreenPowerState.SCREEN_TURNING_OFF &&
                            startedKeyguardTransitionStep.value.to != KeyguardState.AOD
                    }
                    .collect { repository.forceFinishCurrentTransition() }
            }
        }
    }

    fun transition(edge: Edge, edgeWithoutSceneContainer: Edge? = null): Flow<TransitionStep> {
        return transition(
            if (SceneContainerFlag.isEnabled || edgeWithoutSceneContainer == null) {
                edge
            } else {
                edgeWithoutSceneContainer
            }
        )
    }

    /** Given an [edge], return a Flow to collect only relevant [TransitionStep]s. */
    @SuppressLint("SharedFlowCreation")
    fun transition(edge: Edge): Flow<TransitionStep> {
        edge.verifyValidKeyguardStates()
        val mappedEdge = getMappedEdge(edge)

        val flow: Flow<TransitionStep> =
            transitionMap.getOrPut(mappedEdge) {
                MutableSharedFlow<TransitionStep>(
                        extraBufferCapacity = 10,
                        onBufferOverflow = BufferOverflow.DROP_OLDEST,
                    )
                    .traceAs("KTF-${mappedEdge.from}-to-${mappedEdge.to}")
            }

        if (!SceneContainerFlag.isEnabled) {
            return flow
        }
        if (edge.isContentWildcardEdge()) {
            return simulateTransitionStepsForSceneTransitions(edge)
        }
        return flow.filterTraced("stl-filter") { step ->
            val fromContent =
                when (edge) {
                    is Edge.StateToState -> edge.from?.mapToSceneContainerContent()
                    is Edge.StateToContent -> edge.from?.mapToSceneContainerContent()
                    is Edge.ContentToState -> edge.from
                }

            val toContent =
                when (edge) {
                    is Edge.StateToState -> edge.to?.mapToSceneContainerContent()
                    is Edge.StateToContent -> edge.to
                    is Edge.ContentToState -> edge.to?.mapToSceneContainerContent()
                }

            val isTransitioningBetweenLockscreenStates =
                fromContent.isLockscreenOrNull() && toContent.isLockscreenOrNull()
            val isTransitioningBetweenDesiredScenes =
                sceneInteractor.transitionStateFlow.value.isTransitioning(fromContent, toContent)

            // We need special treatment for snap to transitions as in STL the transition state
            // will just immediately change value to Idle(snapToDestination). In KTF this is not
            // possible so we need to make sure that the transitionStates that get emitted as a
            // consequence of a snapTo (coming from LockscreenSceneTransitionInteractor) are not
            // filtered out. This is easy for the Idle(A) -> Idle(B) case since we can check the
            // previousValue therefore all KTF transitions step.from = A && step.to = B are
            // properly passed through.
            // Special Note: Even though the following conditions are aiming at fixing the snapTo
            // cases that are common and therefore surfaced this issue, in STL there is no guarantee
            // that there is any "state continuity". Therefore, these cases can also apply and work
            // correctly in scenarios where no snapTo is involved (e.g. device hanging). The cases
            // have always been considered by `LockscreenSceneTransitionInteractor`, see the
            // documentation and test cases there for further details. Instant flipping from any
            // state to a transition has always been covered "out of the box" by
            // `isTransitioningBetweenDesiredScenes`. There are just the Idle cases that need more
            // treatment here because KTF doesn't have the concept of Idle and instead needs to
            // wrap up all running transitions with a FINISHED step.
            val isSnapToBetweenDesiredScenes =
                sceneTransitionPair.value.previousValue.isIdle(fromContent) &&
                    sceneInteractor.transitionStateFlow.value.isIdle(toContent)

            // We can also call snapTo while running a transition. This gives us 3 scenarios:
            // 1) A -> B; snapTo(B); Idle(B)
            // 2) B -> A; snapTo(B); Idle(B)
            // 3) A -> C; snapTo(B); Idle(B)
            // Case 1) is the "normal" case, we essentially just finish in B early. Handling the
            // terminal cases will be handled by `terminalStepBelongsToPreviousTransition` as it
            // does for any other FINISHED/CANCELED step.
            // Case 2) is handled by `belongsToInstantReversedTransition`. From the perspective
            // of the `edge` filter, a transition to the reversed transition (to => from) was
            // running just prior to when we ended up in `toContent`. This can't happen in KTF
            // organically in conjunction with the checked STL states, so we know this is either a
            // snapTo, or an "intantReveresedTransition" which are commonly used by LSTI to sync up
            // KTF to wherever STL just landed on (for any reason).
            // Case 3) is handled here. We snapped to an Idle state that is unrelated to the
            // previous transition. In this case the only thing we can observe is that in
            // order for KTF to catch up to the STL state LSTI needs to interrupt the previous
            // transition (A -> C) and then start emitting new steps to land on (B), these steps
            // have to be (C -> B), since we just canceled a transition to C. The CANCELED step
            // itself will be passed through by `terminalStepBelongsToPreviousTransition`, while
            // this condition will pass through the (C -> B) steps.
            // To summarize: We know we previously had to transition TO (C) what has now become the
            // `fromContent` and we know we end up in Idle(toContent).
            val isSnapToToUnrelatedState =
                sceneTransitionPair.value.previousValue.isTransitioning(to = fromContent) &&
                    sceneInteractor.transitionStateFlow.value.isIdle(toContent)

            // When in STL A -> B settles in A we can't do the same in KTF as KTF requires us to
            // start B -> A to get back to A. [LockscreenSceneTransitionInteractor] will emit these
            // steps but because STL is Idle(A) at this point (and never even started a B -> A in
            // the first place) [isTransitioningBetweenDesiredScenes] will not be satisfied. We need
            // this condition to not filter out the STARTED and FINISHED step of the "artificially"
            // reversed B -> A transition.
            val belongsToInstantReversedTransition =
                sceneInteractor.topmostContent.value == toContent &&
                    sceneTransitionPair.value.previousValue.isTransitioning(toContent, fromContent)

            // We can't compare the terminal step with the current sceneTransition because
            // a) STL has no guarantee that it will settle in Idle() when finished/canceled
            // b) Comparing to Idle(toScene) would make any other FINISHED step settling in
            //    toScene pass as well
            val terminalStepBelongsToPreviousTransition =
                (step.transitionState == TransitionState.FINISHED ||
                    step.transitionState == TransitionState.CANCELED) &&
                    sceneTransitionPair.value.previousValue.isTransitioning(fromContent, toContent)

            return@filterTraced isTransitioningBetweenLockscreenStates ||
                isSnapToBetweenDesiredScenes ||
                isSnapToToUnrelatedState ||
                isTransitioningBetweenDesiredScenes ||
                terminalStepBelongsToPreviousTransition ||
                belongsToInstantReversedTransition
        }
    }

    private fun ContentKey?.isLockscreenOrNull() = this == Scenes.Lockscreen || this == null

    /**
     * This function will return a flow that simulates TransitionSteps based on STL movements
     * filtered by [edge].
     *
     * STL transitions outside of Lockscreen Transitions are not tracked in KTI. This is an issue
     * for wildcard edges, as this means that Scenes.Bouncer -> Scenes.Gone would not appear while
     * AOD -> Scenes.Bouncer would appear.
     *
     * This function will track STL transitions only when a wildcard edge is provided and emit a
     * RUNNING step for each update to [Transition.progress]. It will also emit a STARTED and
     * FINISHED step when the transitions starts and finishes.
     *
     * All TransitionSteps will have UNDEFINED as to and from state even when one of them is the
     * Lockscreen Scene. It indicates that both are scenes but it should not be relevant to
     * consumers of the [transition] API as usually all viewModels are just interested in the
     * progress value. The correct filtering based on the provided [edge] is always the
     * responsibility of KTI and therefore only proper [TransitionStep]s are emitted. The filter is
     * applied within this function.
     */
    private fun simulateTransitionStepsForSceneTransitions(edge: Edge) =
        sceneInteractor.transitionStateFlow
            .flatMapLatestWithFinished {
                when (it) {
                    is ObservableTransitionState.Idle -> {
                        flowOf()
                    }
                    is ObservableTransitionState.Transition -> {
                        val isMatchingTransition =
                            when (edge) {
                                is Edge.StateToState ->
                                    throw IllegalStateException("Should not be reachable.")
                                is Edge.ContentToState -> it.isTransitioning(from = edge.from)
                                is Edge.StateToContent -> it.isTransitioning(to = edge.to)
                            }
                        if (!isMatchingTransition) {
                            return@flatMapLatestWithFinished flowOf()
                        }
                        flow {
                            emit(
                                TransitionStep(
                                    from = UNDEFINED,
                                    to = UNDEFINED,
                                    value = 0f,
                                    transitionState = TransitionState.STARTED,
                                )
                            )
                            emitAll(
                                it.progress.map { progress ->
                                    TransitionStep(
                                        from = UNDEFINED,
                                        to = UNDEFINED,
                                        value = progress,
                                        transitionState = TransitionState.RUNNING,
                                    )
                                }
                            )
                        }
                    }
                }
            }
            .traceAs("KTF-transition-simulator")

    /**
     * This function is similar to flatMapLatest but it will additionally emit a FINISHED
     * TransitionStep whenever the flattened innerFlow emitted a STARTED step and is now being
     * replaced by a new innerFlow.
     *
     * This is to make sure that every STARTED step will receive a corresponding FINISHED step.
     *
     * We can't simply write this into a flow {} block because Transition.progress doesn't complete.
     * We also can't emit the FINISHED step simply when an Idle state is reached because a)
     * Transitions are not guaranteed to finish in Idle and b) There can be multiple Idle
     * transitions after another
     */
    private fun <T> Flow<T>.flatMapLatestWithFinished(
        transform: suspend (T) -> Flow<TransitionStep>
    ): Flow<TransitionStep> = channelFlow {
        var job: Job? = null
        var startedEmitted = false

        coroutineScope {
            collect { value ->
                traceCoroutine("cancelAndJoin") { job?.cancelAndJoin() }

                job =
                    launch("KTF-flatMapLatestWithFinished") {
                        val innerFlow = transform(value)
                        try {
                            innerFlow.collect { step ->
                                if (step.transitionState == TransitionState.STARTED) {
                                    startedEmitted = true
                                }
                                traceCoroutine("send($step)") { send(step) }
                            }
                        } finally {
                            if (startedEmitted) {
                                val step =
                                    TransitionStep(
                                        from = UNDEFINED,
                                        to = UNDEFINED,
                                        value = 1f,
                                        transitionState = TransitionState.FINISHED,
                                    )
                                traceCoroutine("send($step)") { send(step) }
                                startedEmitted = false
                            }
                        }
                    }
            }
        }
    }

    /**
     * Converts old KTF states to UNDEFINED when [SceneContainerFlag] is enabled.
     *
     * Does nothing otherwise.
     *
     * This method should eventually be removed when new code is only written for scene container.
     * Even when all edges are ported today, there is still development on going in production that
     * might utilize old states.
     */
    private fun getMappedEdge(edge: Edge): Edge.StateToState {
        if (!SceneContainerFlag.isEnabled) return edge as Edge.StateToState
        return when (edge) {
            is Edge.StateToState ->
                Edge.create(
                    from = edge.from?.mapToSceneContainerState(),
                    to = edge.to?.mapToSceneContainerState(),
                )
            is Edge.ContentToState -> Edge.create(UNDEFINED, edge.to)
            is Edge.StateToContent -> Edge.create(edge.from, UNDEFINED)
        }
    }

    fun transitionValue(
        content: ContentKey? = null,
        stateWithoutSceneContainer: KeyguardState,
    ): Flow<Float> {
        return if (SceneContainerFlag.isEnabled && content != null) {
            sceneInteractor.transitionProgress(content)
        } else {
            transitionValue(stateWithoutSceneContainer)
        }
    }

    /**
     * The amount of transition into or out of the given [KeyguardState].
     *
     * The value will be `0` (or close to `0`, due to float point arithmetic) if not in this step or
     * `1` when fully in the given state.
     */
    fun transitionValue(state: KeyguardState): Flow<Float> {
        if (SceneContainerFlag.isEnabled && state != state.mapToSceneContainerState()) {
            throw IllegalStateException(
                "SceneContainer is enabled but a deprecated state $state is used."
            )
        }
        return getTransitionValueFlow(state)
    }

    /** The last [TransitionStep] with a [TransitionState] of STARTED */
    @Deprecated("Use scene container aware alternatives in this class")
    val startedKeyguardTransitionStep: StateFlow<TransitionStep> =
        repository.transitions
            .filter { step -> step.transitionState == TransitionState.STARTED }
            .stateInTraced(
                "KTF-startedKeyguardTransitionStep",
                scope,
                SharingStarted.Eagerly,
                TransitionStep(),
            )

    /**
     * The [KeyguardState] we're currently in.
     *
     * If we're not in transition, this is simply the [finishedKeyguardState]. If we're in
     * transition, this is the state we're transitioning *from*.
     *
     * Absent CANCELED transitions, [currentKeyguardState] and [finishedKeyguardState] are always
     * identical - if a transition FINISHES in a given state, the subsequent state we START a
     * transition *from* would always be that same previously FINISHED state.
     *
     * However, if a transition is CANCELED, the next transition will START from a state we never
     * FINISHED in. For example, if we transition from GONE -> DOZING, but CANCEL that transition in
     * favor of DOZING -> LOCKSCREEN, we've STARTED a transition *from* DOZING despite never
     * FINISHING in DOZING. Thus, the current state will be DOZING but the FINISHED state will still
     * be GONE.
     *
     * In this example, if there was DOZING-related state that needs to be set up in order to
     * properly render a DOZING -> LOCKSCREEN transition, it would never be set up if we were
     * listening for [finishedKeyguardState] to emit DOZING. However, [currentKeyguardState] would
     * emit DOZING immediately upon STARTING DOZING -> LOCKSCREEN, allowing us to set up the state.
     *
     * Whether you want to use [currentKeyguardState] or [finishedKeyguardState] depends on your
     * specific use case and how you want to handle cancellations. In general, if you're dealing
     * with state/UI present across multiple [KeyguardState]s, you probably want
     * [currentKeyguardState]. If you're dealing with state/UI encapsulated within a single state,
     * you likely want [finishedKeyguardState].
     *
     * As an example, let's say you want to animate in a message on the lockscreen UI after waking
     * up, and that TextView is not involved in animations between states. You'd want to collect
     * [finishedKeyguardState], so you'll only animate it in once we're settled on the lockscreen.
     * If you use [currentKeyguardState] in this case, a DOZING -> LOCKSCREEN transition that is
     * interrupted by a LOCKSCREEN -> GONE transition would cause the message to become visible
     * immediately upon LOCKSCREEN -> GONE STARTING, as the current state would become LOCKSCREEN in
     * that case. That's likely not what you want.
     *
     * On the other hand, let's say you're animating the smartspace from alpha 0f to 1f during
     * DOZING -> LOCKSCREEN, but the transition is interrupted by LOCKSCREEN -> GONE. LS -> GONE
     * needs the smartspace to be alpha=1f so that it can play the shared-element unlock animation.
     * In this case, we'd want to collect [currentKeyguardState] and ensure the smartspace is
     * visible when the current state is LOCKSCREEN. If you use [finishedKeyguardState] in this
     * case, the smartspace will never be set to alpha = 1f and you'll have a half-faded smartspace
     * during the LS -> GONE transition.
     *
     * As a helpful footnote, here's the values of [finishedKeyguardState] and
     * [currentKeyguardState] during a sequence with two cancellations:
     * 1. We're FINISHED in GONE. currentKeyguardState=GONE; finishedKeyguardState=GONE.
     * 2. We START a transition from GONE -> DOZING. currentKeyguardState=GONE;
     *    finishedKeyguardState=GONE.
     * 3. We CANCEL this transition and START a transition from DOZING -> LOCKSCREEN.
     *    currentKeyguardState=DOZING; finishedKeyguardState=GONE.
     * 4. We subsequently also CANCEL DOZING -> LOCKSCREEN and START LOCKSCREEN -> GONE.
     *    currentKeyguardState=LOCKSCREEN finishedKeyguardState=GONE.
     * 5. LOCKSCREEN -> GONE is allowed to FINISH. currentKeyguardState=GONE;
     *    finishedKeyguardState=GONE.
     */
    @Deprecated("Use scene container aware alternatives in this class")
    val currentKeyguardState: StateFlow<KeyguardState> =
        repository.transitions
            .mapDirect {
                if (it.transitionState == TransitionState.FINISHED) {
                    it.to
                } else {
                    it.from
                }
            }
            .stateInTraced("KTF-currentKeyguardState", scope, SharingStarted.Eagerly, OFF)

    val isInTransition =
        combine(isInTransitionWhere({ true }, { true }), sceneInteractor.transitionStateFlow) {
            isKeyguardTransitioning,
            sceneTransitionState ->
            isKeyguardTransitioning ||
                (SceneContainerFlag.isEnabled && sceneTransitionState.isTransitioning())
        }

    /**
     * Whether we're in a transition to and from the given [KeyguardState]s, but haven't yet
     * completed it.
     *
     * Provide [edgeWithoutSceneContainer] when the edge is different from what it is without it. If
     * the edges are equal before and after the flag it is sufficient to provide just [edge].
     */
    fun isInTransition(edge: Edge, edgeWithoutSceneContainer: Edge? = null): Flow<Boolean> {
        return if (SceneContainerFlag.isEnabled) {
                if (edge.isContentWildcardEdge()) {
                    sceneInteractor.transitionStateFlow.map {
                        when (edge) {
                            is Edge.StateToState ->
                                throw IllegalStateException("Should not be reachable.")
                            is Edge.ContentToState -> it.isTransitioning(from = edge.from)
                            is Edge.StateToContent -> it.isTransitioning(to = edge.to)
                        }
                    }
                } else {
                    transition(edge).map { it.transitionState.isTransitioning() }
                }
            } else {
                transition(edgeWithoutSceneContainer ?: edge).map {
                    it.transitionState.isTransitioning()
                }
            }
            .onStart { emit(false) }
            .traceAs("isInTransition-$edge-$edgeWithoutSceneContainer")
            .distinctUntilChanged()
    }

    /**
     * Whether we're in a transition between two [KeyguardState]s that match the given predicates,
     * but haven't yet completed it.
     *
     * If you only care about a single state for both from and to, instead use the optimized
     * [isInTransition].
     */
    @Deprecated("Use scene container aware alternatives in this class")
    fun isInTransitionWhere(
        fromStatePredicate: (KeyguardState) -> Boolean = { true },
        toStatePredicate: (KeyguardState) -> Boolean = { true },
    ): Flow<Boolean> {
        return repository.transitions
            .filter { it.transitionState != TransitionState.CANCELED }
            .map {
                it.transitionState != TransitionState.FINISHED &&
                    fromStatePredicate(it.from) &&
                    toStatePredicate(it.to)
            }
            .distinctUntilChanged()
    }

    /**
     * Whether we're in a transition between two [KeyguardState]s that match the given predicates,
     * but haven't yet completed it.
     */
    fun isInTransitionWhere(
        toStatePredicate: (KeyguardState, ContentKey?) -> Boolean
    ): Flow<Boolean> {
        return repository.transitions
            .filter { it.transitionState != TransitionState.CANCELED }
            .map {
                it.transitionState != TransitionState.FINISHED &&
                    toStatePredicate(
                        it.to,
                        if (SceneContainerFlag.isEnabled) {
                            sceneInteractor.currentScene.value
                        } else {
                            null
                        },
                    )
            }
            .distinctUntilChanged()
    }

    /** Whether we've FINISHED a transition to a state that matches the given predicate. */
    @Deprecated("Use scene container aware alternatives in this class")
    fun isFinishedInStateWhere(stateMatcher: (KeyguardState) -> Boolean): Flow<Boolean> {
        return finishedKeyguardState.map { stateMatcher(it) }.distinctUntilChanged()
    }

    /**
     * Whether we've FINISHED a transition to a state that matches the given predicate with the
     * current scene.
     */
    fun isFinishedInStateWhereWithScene(
        stateMatcher: (KeyguardState, ContentKey) -> Boolean
    ): Flow<Boolean> {
        return finishedKeyguardState
            .map { stateMatcher(it, sceneInteractor.currentScene.value) }
            .distinctUntilChanged()
    }

    fun isFinishedIn(
        content: ContentKey,
        stateWithoutSceneContainer: KeyguardState,
    ): Flow<Boolean> {
        return if (SceneContainerFlag.isEnabled) {
                combine(sceneInteractor.topmostContent, sceneInteractor.transitionStateFlow) {
                    topmostContent,
                    state ->
                    topmostContent == content || state.isTransitioning(from = content)
                }
            } else {
                isFinishedIn(stateWithoutSceneContainer)
            }
            .distinctUntilChanged()
    }

    /** Whether we've FINISHED a transition to a state */
    fun isFinishedIn(state: KeyguardState): Flow<Boolean> {
        state.checkValidState()
        return finishedKeyguardState.map { it == state }.distinctUntilChanged()
    }

    fun isCurrentlyIn(scene: SceneKey, stateWithoutSceneContainer: KeyguardState): Flow<Boolean> {
        return if (SceneContainerFlag.isEnabled) {
                // In STL there is no difference between finished/currentState
                isFinishedIn(scene, stateWithoutSceneContainer)
            } else {
                stateWithoutSceneContainer.checkValidState()
                currentKeyguardState.map { it == stateWithoutSceneContainer }
            }
            .distinctUntilChanged()
    }

    @Deprecated("Use scene container aware alternatives in this class")
    fun getCurrentState(): KeyguardState {
        return currentKeyguardState.replayCache.last()
    }

    @Deprecated("Use scene container aware alternatives in this class")
    fun getStartedState(): KeyguardState {
        return startedKeyguardTransitionStep.value.to
    }

    @Deprecated("Use scene container aware alternatives in this class")
    val finishedKeyguardState: StateFlow<KeyguardState> =
        repository.transitions
            .filter { it.transitionState == TransitionState.FINISHED }
            .map { it.to }
            .stateInTraced("KTF-finishedKeyguardState", scope, SharingStarted.Eagerly, OFF)

    companion object {
        private val TAG = KeyguardTransitionInteractor::class.simpleName
    }
}
