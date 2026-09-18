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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.keyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.realKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionInfo
import com.android.systemui.keyguard.shared.model.TransitionModeOnCanceled
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class LockscreenSceneTransitionInteractorTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply { keyguardTransitionRepository = realKeyguardTransitionRepository }

    private val testScope = kosmos.testScope
    private val underTest = kosmos.lockscreenSceneTransitionInteractor

    private val progress = MutableStateFlow(0f)

    private val sceneTransitions =
        MutableStateFlow<ObservableTransitionState>(
            ObservableTransitionState.Idle(Scenes.Lockscreen)
        )

    private val lsToGone =
        ObservableTransitionState.Transition(
            Scenes.Lockscreen,
            Scenes.Gone,
            flowOf(Scenes.Lockscreen),
            progress,
            false,
            flowOf(false),
        )

    private val goneToLs =
        ObservableTransitionState.Transition(
            Scenes.Gone,
            Scenes.Lockscreen,
            flowOf(Scenes.Lockscreen),
            progress,
            false,
            flowOf(false),
        )

    @Before
    fun setUp() {
        underTest.start()
        kosmos.sceneContainerRepository.setTransitionState(sceneTransitions)
        testScope.launch {
            kosmos.realKeyguardTransitionRepository.emitInitialStepsFromOff(
                KeyguardState.LOCKSCREEN,
                testSetup = true,
            )
        }
    }

    /** STL: Ls -> Gone, then settle with Idle(Gone). This is the default case. */
    @Test
    fun transition_from_ls_scene_end_in_gone() =
        testScope.runTest {
            sceneTransitions.value = lsToGone

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Gone)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /** STL: Ls -> overlay, then settle with Idle(overlay). */
    @Test
    fun transition_overlay_from_ls_scene_end_in_gone() =
        testScope.runTest {
            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.NotificationsShade,
                    fromContent = Scenes.Lockscreen,
                    toContent = Overlays.NotificationsShade,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(emptySet()),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            sceneTransitions.value =
                ObservableTransitionState.Idle(
                    Scenes.Lockscreen,
                    setOf(Overlays.NotificationsShade),
                )
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /**
     * STL: Ls -> Gone, then settle with Idle(Ls). KTF in this scenario needs to invert the
     * transition LS -> UNDEFINED to UNDEFINED -> LS as there is no mechanism in KTF to
     * finish/settle to progress 0.0f.
     */
    @Test
    fun transition_from_ls_scene_end_in_ls() =
        testScope.runTest {
            sceneTransitions.value = lsToGone

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /**
     * STL: Ls -> Gone, then settle with Idle(Ls). KTF starts in AOD and needs to inverse correctly
     * back to AOD.
     */
    @Test
    fun transition_from_ls_scene_on_aod_end_in_ls() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )
            sceneTransitions.value = lsToGone

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /**
     * STL: Gone -> Ls, then settle with Idle(Ls). This is the default case in the reverse
     * direction.
     */
    @Test
    fun transition_to_ls_scene_end_in_ls() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = goneToLs

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /** STL: Ls with overlay, then settle with Idle(Ls). */
    @Test
    fun transition_overlay_to_ls_scene_end_in_ls() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)

            sceneTransitions.value =
                ObservableTransitionState.Idle(
                    Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.NotificationsShade),
                )

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.NotificationsShade,
                    fromContent = Overlays.NotificationsShade,
                    toContent = Scenes.Lockscreen,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(setOf(Overlays.NotificationsShade)),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /** STL: Gone -> Ls (AOD), will transition to AOD once */
    @Test
    fun transition_to_ls_scene_with_changed_next_scene_is_respected_just_once() =
        testScope.runTest {
            underTest.setNextLockscreenTargetState(KeyguardState.AOD)
            sceneTransitions.value = goneToLs

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Shade,
                    Scenes.Lockscreen,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )
        }

    @Test
    fun emitTransitionWithIrrelevantFieldsChanged_doesNotAffectKtf() =
        testScope.runTest {
            underTest.setNextLockscreenTargetState(KeyguardState.AOD)
            sceneTransitions.value = goneToLs

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Shade,
                    Scenes.Lockscreen,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Shade,
                    Scenes.Lockscreen,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    isUserInputOngoing = flowOf(true), // Change only this flow
                )

            // Should have no effect.
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )
        }

    /**
     * STL: Gone -> Ls, then settle with Idle(Gone). KTF in this scenario needs to invert the
     * transition UNDEFINED -> LS to LS -> UNDEFINED as there is no mechanism in KTF to
     * finish/settle to progress 0.0f.
     */
    @Test
    fun transition_to_ls_scene_end_in_from_scene() =
        testScope.runTest {
            sceneTransitions.value = goneToLs

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Gone)
            val stepM3 = allSteps[allSteps.size - 3]
            val stepM2 = allSteps[allSteps.size - 2]

            assertTransition(
                step = stepM3,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = stepM2,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupted by Shade -> Ls. KTF in this scenario needs to invert the
     * transition UNDEFINED -> LS to LS -> UNDEFINED as there is no mechanism in KTF to
     * finish/settle to progress 0.0f. Then restart a different transition UNDEFINED -> Ls.
     */
    @Test
    fun transition_to_ls_scene_end_in_to_ls_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = goneToLs
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Shade,
                    Scenes.Lockscreen,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 5],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 4],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.2f
            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.2f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupted by Ls -> Shade. This is like continuing the transition from
     * Ls before the transition before has properly settled. This can happen in STL e.g. with an
     * accelerated swipe (quick successive fling gestures).
     */
    @Test
    fun transition_to_ls_scene_end_in_from_ls_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = goneToLs
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0.0f,
            )

            progress.value = 0.2f
            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.2f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupted by Gone -> Shade. This is going back to Gone but starting a
     * transition from Gone before settling in Gone. KTF needs to make sure the transition is
     * properly inversed and settled in UNDEFINED.
     */
    @Test
    fun transition_to_ls_scene_end_in_other_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = goneToLs
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Gone,
                    Scenes.Shade,
                    flowOf(Scenes.Shade),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupt in KTF LS -> AOD, then stl still finishes in Ls. After a KTF
     * transition is started (UNDEFINED -> LOCKSCREEN) KTF immediately considers the active scene to
     * be LOCKSCREEN. This means that all listeners for LOCKSCREEN are active and may start a new
     * transition LOCKSCREEN -> *. Here we test LS -> AOD.
     *
     * KTF is allowed to already start and play the other transition, while the STL transition may
     * finish later (gesture completes much later). When we eventually settle the STL transition in
     * Ls we do not want to force KTF back to its original destination (LOCKSCREEN). Instead, for
     * this scenario the settle can be ignored.
     */
    @Test
    fun transition_to_ls_scene_interrupted_by_ktf_transition_then_finish_in_lockscreen() =
        testScope.runTest {
            sceneTransitions.value = goneToLs

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            // Scene progress should not affect KTF transition anymore
            progress.value = 0.7f
            assertTransition(currentStep!!, progress = 0f)

            // Scene transition still finishes but should not impact KTF transition
            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupt in KTF LS -> AOD, then stl finishes in Gone.
     *
     * Refers to: `transition_to_ls_scene_interrupted_by_ktf_transition_then_finish_in_lockscreen`
     *
     * This is similar to the previous scenario but the gesture may have gone back to its origin. In
     * this case we can not ignore the settlement, because whatever KTF has done in the meantime it
     * needs to immediately finish in UNDEFINED (there is a jump cut).
     */
    @Test
    fun transition_to_ls_scene_interrupted_by_ktf_transition_then_finish_in_gone() =
        testScope.runTest {
            sceneTransitions.value = goneToLs

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.7f
            assertThat(currentStep?.value).isEqualTo(0f)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Gone)

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupt in KTF LS -> AOD, then STL Gone -> Shade
     *
     * Refers to: `transition_to_ls_scene_interrupted_by_ktf_transition_then_finish_in_lockscreen`
     *
     * This is similar to the previous scenario but the gesture may have been interrupted by any
     * other transition. KTF needs to immediately finish in UNDEFINED (there is a jump cut).
     */
    @Test
    fun transition_to_ls_interrupted_by_ktf_transition_then_interrupted_by_other_transition() =
        testScope.runTest {
            sceneTransitions.value = goneToLs

            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            assertTransition(step = currentStep!!, state = TransitionState.RUNNING, progress = 0.4f)

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.7f
            assertTransition(currentStep!!, progress = 0f)

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Gone,
                    Scenes.Shade,
                    flowOf(Scenes.Shade),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupt in KTF LS -> AOD, then STL Ls -> Shade
     *
     * In this scenario it is important that the last STL transition Ls -> Shade triggers a cancel
     * of the * -> AOD transition but then also properly starts a transition AOD (not LOCKSCREEN) ->
     * UNDEFINED transition.
     */
    @Test
    fun transition_to_ls_interrupted_by_ktf_transition_then_interrupted_by_from_ls_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = goneToLs
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.7f
            assertTransition(currentStep!!, progress = 0f)

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )
            allSteps[allSteps.size - 3]

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.CANCELED,
                progress = 0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.2f
            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.2f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupt in KTF LS -> AOD, then STL Shade -> Ls
     *
     * In this scenario it is important KTF is brought back into a FINISHED UNDEFINED state
     * considering the state is already on AOD from where a new UNDEFINED -> LOCKSCREEN transition
     * can be started.
     */
    @Test
    fun transition_to_ls_interrupted_by_ktf_transition_then_interrupted_by_to_ls_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = goneToLs
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.7f
            assertTransition(currentStep!!, progress = 0f)

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Shade,
                    Scenes.Lockscreen,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 5],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.CANCELED,
                progress = 0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 4],
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 1f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.7f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupt multiple canceled KTF transitions, then STL Ls -> Shade
     *
     * Similar to
     * `transition_to_ls_scene_interrupted_by_ktf_transition_then_interrupted_by_from_ls_transition`
     * but here KTF is canceled multiple times such that in the end OCCLUDED -> UNDEFINED is
     * properly started. (not from AOD or LOCKSCREEN)
     *
     * Note: there is no test which tests multiple cancels from the STL side, this is because all
     * STL transitions trigger a response from LockscreenSceneTransitionInteractor which forces KTF
     * into a specific state, so testing each pair is enough. Meanwhile KTF can move around without
     * any reaction from LockscreenSceneTransitionInteractor.
     */
    @Test
    fun transition_to_ls_interrupted_by_ktf_cancel_sequence_interrupted_by_from_ls_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = lsToGone
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.AOD,
                    to = KeyguardState.DOZING,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.AOD,
                to = KeyguardState.DOZING,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            kosmos.realKeyguardTransitionRepository.startTransition(
                TransitionInfo(
                    ownerName = this.javaClass.simpleName,
                    from = KeyguardState.DOZING,
                    to = KeyguardState.OCCLUDED,
                    animator = null,
                    modeOnCanceled = TransitionModeOnCanceled.RESET,
                )
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.DOZING,
                to = KeyguardState.OCCLUDED,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.7f
            assertTransition(currentStep!!, progress = 0f)

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.DOZING,
                to = KeyguardState.OCCLUDED,
                state = TransitionState.CANCELED,
                progress = 0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.OCCLUDED,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.2f
            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.OCCLUDED,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.2f,
            )
        }

    /**
     * STL: Gone -> Ls, then interrupted by KTF LS -> AOD which is FINISHED before STL Ls -> Shade
     *
     * Similar to
     * `transition_to_ls_scene_interrupted_by_ktf_transition_then_interrupted_by_from_ls_transition`
     * but here KTF is finishing the transition and only then gets interrupted. Should correctly
     * start AOD -> UNDEFINED.
     */
    @Test
    fun transition_to_ls_scene_interrupted_and_finished_by_ktf_interrupted_by_from_ls_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = lsToGone
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            val ktfUuid =
                kosmos.realKeyguardTransitionRepository.startTransition(
                    TransitionInfo(
                        ownerName = this.javaClass.simpleName,
                        from = KeyguardState.LOCKSCREEN,
                        to = KeyguardState.AOD,
                        animator = null,
                        modeOnCanceled = TransitionModeOnCanceled.RESET,
                    )
                )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            kosmos.realKeyguardTransitionRepository.updateTransition(
                ktfUuid!!,
                1f,
                TransitionState.FINISHED,
            )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.2f
            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.AOD,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.2f,
            )
        }

    @Test
    fun transitionToShade() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            val currentScene = MutableStateFlow(Scenes.Lockscreen)

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Shade,
                    currentScene = currentScene,
                    progress,
                    true,
                    flowOf(false),
                )
            runCurrent()

            progress.value = 0.4f

            // When you lift your finger, the current scene updates to Shade prior to the Idle
            // transition arriving.
            currentScene.value = Scenes.Shade

            sceneTransitions.value = ObservableTransitionState.Idle(currentScene = Scenes.Shade)

            assertTransition(
                step = allSteps[allSteps.size - 4],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0.0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1.0f,
            )
        }

    /**
     * STL: Ls -> Gone, then interrupted by Ls -> Bouncer. This happens when the next transition is
     * immediately started from Gone without settling in Idle. This specifically happens when
     * dragging down on Ls and then changing direction. The transition will switch from -> Shade to
     * -> Bouncer without settling or signaling any cancellation as STL considers this to be the
     * same gesture.
     *
     * In STL there is no guarantee that transitions settle in Idle before continuing.
     */
    @Test
    fun transition_from_ls_scene_interrupted_by_other_from_ls_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = lsToGone

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0f,
            )

            progress.value = 0.4f
            sceneTransitions.value =
                ObservableTransitionState.Transition.showOverlay(
                    overlay = Overlays.Bouncer,
                    fromScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(setOf(Overlays.Bouncer)),
                    progress = progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 5],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 4],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0f,
            )
        }

    /**
     * STL: Ls -> Gone, then interrupted by Gone -> Ls. This happens when the next transition is
     * immediately started from Gone without settling in Idle. In STL there is no guarantee that
     * transitions settle in Idle before continuing.
     */
    @Test
    fun transition_from_ls_scene_interrupted_by_to_ls_transition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = lsToGone
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Gone,
                    Scenes.Lockscreen,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            progress.value = 0.2f
            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.2f,
            )
        }

    /**
     * STL: Ls -> Gone, then interrupted by Gone -> Occluded. This happens when the next transition
     * is immediately started from Gone without settling in Idle. In STL there is no guarantee that
     * transitions settle in Idle before continuing.
     */
    @Test
    fun transition_from_ls_scene_interrupted_to_another_scene() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = lsToGone
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Gone,
                    Scenes.Occluded,
                    flowOf(Scenes.Occluded),
                    progress,
                    false,
                    flowOf(false),
                )

            // Should finish going to UNDEFINED since Gone and Occluded are both UNDEFINED.
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    /**
     * STL: Ls -> Gone, then interrupted by Gone -> Ls. This happens when the next transition is
     * immediately started from Gone without settling in Idle. In STL there is no guarantee that
     * transitions settle in Idle before continuing.
     */
    @Test
    fun transition_from_ls_scene_interrupted_back_to_ls() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = lsToGone
            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Gone,
                    Scenes.Lockscreen,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )
        }

    /**
     * When a transition away from the lockscreen is interrupted by an `Idle(Lockscreen)`, a
     * `keyguardState` that was set during the transition is consumed and passed to KTF.
     */
    @Test
    fun transition_from_ls_scene_keyguardStateSet_then_interrupted_by_idle_on_ls() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Gone,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )
            progress.value = 0.4f
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            underTest.setNextLockscreenTargetState(KeyguardState.AOD)
            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    @Test
    fun transitionFromLsToShade_interruptedByAod_goesToSleepAndFinishesInAod() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Shade),
                    progress,
                    false,
                    flowOf(false),
                )
            progress.value = 0.4f
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            underTest.setNextLockscreenTargetState(KeyguardState.AOD)
            kosmos.powerInteractor.setAsleepForTest()
            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )
            runCurrent()

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)
            runCurrent()

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.FINISHED,
            )
        }

    @Test
    fun transitionFromLsToShade_interruptedByAod_interruptedAgainByWake_finishesInLs() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Shade),
                    progress,
                    false,
                    flowOf(false),
                )
            progress.value = 0.4f
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            underTest.setNextLockscreenTargetState(KeyguardState.AOD)
            kosmos.powerInteractor.setAsleepForTest()
            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )
            runCurrent()

            // The original, canceled Lockscreen -> Shade transition is just going to run backwards
            // and then end, even though we've meanwhile turned the screen off and back on.
            kosmos.powerInteractor.setAwakeForTest()
            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)
            runCurrent()

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.FINISHED,
            )
        }

    /**
     * Transition from Bouncer to Lockscreen which is canceled, returning to Bouncer.
     *
     * We need to ensure that despite technically finishing a transition to Scenes.Lockscreen, we
     * return to UNDEFINED since the Bouncer overlay remained visible.
     */
    @Test
    fun canceledTransitionFromBouncerToLockscreen_returnsToUndefined() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = goneToLs

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Scenes.Lockscreen,
                    toContent = Overlays.Bouncer,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(emptySet()),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )

            // Idle on Lockscreen with Bouncer showing.
            sceneTransitions.value =
                ObservableTransitionState.Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer))

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
            )

            // Start hiding Bouncer.
            progress.value = 0.4f
            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Overlays.Bouncer,
                    toContent = Scenes.Lockscreen,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(setOf(Overlays.Bouncer)),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            // Go back to Bouncer.
            sceneTransitions.value =
                ObservableTransitionState.Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer))

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    @Test
    fun snapFromUnrelatedTransition_toIdleOnLs() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Gone)

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Gone,
                    Scenes.Shade,
                    flowOf(Scenes.Shade),
                    progress,
                    false,
                    flowOf(false),
                )

            progress.value = 0.4f

            // The most recent KTF step should be the FINISHED from Ls -> Gone. The 40% complete
            // Gone -> Shade is not relevant to KTF and should not have started any transitions.
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            val stepCount = allSteps.size

            // Snap to Lockscreen/AOD.
            underTest.setNextLockscreenTargetState(KeyguardState.AOD)
            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            // Make sure those are the only two steps emitted.
            assertEquals(stepCount + 2, allSteps.size)
        }

    @Test
    fun snapFromRelevantTransition_toIdleOnLs() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            underTest.setNextLockscreenTargetState(KeyguardState.LOCKSCREEN)
            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Gone,
                    flowOf(Scenes.Gone),
                    progress,
                    false,
                    flowOf(false),
                )

            progress.value = 0.4f

            // We're partway from Ls -> Gone.
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            val stepCount = allSteps.size

            // We snap to Lockscreen while the target state changes to AOD (sleeping while
            // unlocking). Ensure current KTF transition is canceled and the new next state is
            // respected.
            underTest.setNextLockscreenTargetState(KeyguardState.AOD)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            // Make sure those are the only two steps emitted.
            assertEquals(stepCount + 3, allSteps.size)
        }

    @Test
    fun snapFromRelevantOverlayTransitionAwayFromLs_toIdleOnLs() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)
            underTest.setNextLockscreenTargetState(KeyguardState.LOCKSCREEN)

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Scenes.Lockscreen,
                    toContent = Overlays.Bouncer,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(emptySet()),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )

            progress.value = 0.4f

            // We're partway from Ls -> Bouncer
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            val stepCount = allSteps.size

            // We snap to Lockscreen while the target state changes to AOD (sleeping while
            // swiping up on bouncer because we are the worst kind of user). Ensure current KTF
            // transition is canceled and the new next state is respected.
            underTest.setNextLockscreenTargetState(KeyguardState.AOD)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.AOD,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            // Make sure those are the only two steps emitted.
            assertEquals(stepCount + 3, allSteps.size)
        }

    /**
     * Dragging from Lockscreen to Bouncer, then snapping to transition from Lockscreen to Dream
     * without an Idle in between.
     *
     * This is the case where KTF is transitioning from LS -> UNDEFINED (from the bouncer
     * transition), and now needs to transition from... LS -> UNDEFINED again (since Dream is also
     * UNDEFINED). Given KTF's constraints, this requires us to go back to LS before starting the
     * new transition to UNDEFINED.
     *
     * We don't want to use the existing LS -> UNDEFINED transition from LS -> Bouncer since then
     * anyone listening to that won't be aware that we've canceled it.
     */
    @Test
    fun snapLockscreenToOverlayTransition_lockscreenToDreamTransition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Scenes.Lockscreen,
                    toContent = Overlays.Bouncer,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(emptySet()),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            progress.value = 0.4f

            // We're partway from Ls -> Bouncer
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            val stepCount = allSteps.size

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Dream,
                    flowOf(Scenes.Dream),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 5],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 4],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            // Make sure those are the only five steps emitted.
            assertEquals(stepCount + 5, allSteps.size)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Dream)
            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertEquals(stepCount + 6, allSteps.size)
        }

    /**
     * Dragging from Lockscreen to Bouncer, then snapping to a transition between two scenes totally
     * unrelated to Lockscreen. The transition to UNDEFINED should simply end - no canceling or
     * reversing, since there are no KTF-relevant edges between either of the new Scenes.
     */
    @Test
    fun snapLockscreenToOverlayTransition_toUnrelatedTransition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Scenes.Lockscreen,
                    toContent = Overlays.Bouncer,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(emptySet()),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            progress.value = 0.4f

            // We're partway from Ls -> Bouncer
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            val stepCount = allSteps.size

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Shade,
                    Scenes.Dream,
                    flowOf(Scenes.Dream),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertEquals(stepCount + 1, allSteps.size)
        }

    /**
     * Dragging from Lockscreen to Bouncer, then snapping to a transition between two scenes totally
     * unrelated to Lockscreen. The transition to UNDEFINED should simply end - no canceling or
     * reversing, since there are no KTF-relevant edges between either of the new Scenes.
     */
    @Test
    fun snapUnrelatedTransition_toLockscreenToOverlayTransition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Shade,
                    Scenes.Dream,
                    flowOf(Scenes.Dream),
                    progress,
                    false,
                    flowOf(false),
                )

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Dream,
                    Scenes.Shade,
                    flowOf(Scenes.Shade),
                    progress,
                    false,
                    flowOf(false),
                )

            progress.value = 0.4f

            // We were on unrelated scenes, in transition. Should be UNDEFINED and sitting there.
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            val stepCount = allSteps.size

            // Snap to transitioning from Lockscreen to Bouncer. This should send KTF back to
            // LOCKSCREEN so that it can then start LS -> UNDEFINED for Lockscreen -> Bouncer.
            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Scenes.Lockscreen,
                    toContent = Overlays.Bouncer,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(emptySet()),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 4],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            assertEquals(stepCount + 4, allSteps.size)
        }

    @Test
    fun snapTransitionToLockscreen_transitionBetweenUnrelatedScenes() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Overlays.Bouncer,
                    toContent = Scenes.Lockscreen,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(setOf(Overlays.Bouncer)),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )

            progress.value = 0.4f

            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            val stepCount = allSteps.size

            // Snap to transitioning from Lockscreen to Bouncer. This should send KTF back to
            // LOCKSCREEN so that it can then start LS -> UNDEFINED for Lockscreen -> Bouncer.
            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Dream,
                    Scenes.Shade,
                    flowOf(Scenes.Shade),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0.6f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )

            assertEquals(stepCount + 3, allSteps.size)
        }

    /**
     * Lockscreen -> Bouncer, snapping to Shade -> Lockscreen. Since Bouncer and Shade are different
     * content, we should replace LS -> UNDEFINED with UNDEFINED -> LS, starting at 0f, instead of
     * reversing from 0.4f like we would if we transitioned from Bouncer -> Lockscreen.
     */
    @Test
    fun snapLockscreenToOverlayTransition_toTransitionFromThirdSceneToLockscreen() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Scenes.Lockscreen,
                    toContent = Overlays.Bouncer,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(emptySet()),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            progress.value = 0.4f

            // We're partway from Ls -> Bouncer
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            val stepCount = allSteps.size

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Shade,
                    Scenes.Lockscreen,
                    flowOf(Scenes.Lockscreen),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.STARTED,
                progress = 0f, // Progress resets because this is a totally different transition.
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            assertEquals(stepCount + 3, allSteps.size)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Lockscreen)

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
            assertEquals(stepCount + 4, allSteps.size)
        }

    @Test
    fun snapLockscreenToBouncer_toGone_finishesUndefinedTransition() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Scenes.Lockscreen,
                    toContent = Overlays.Bouncer,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(emptySet()),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            progress.value = 0.4f

            // We're partway from Ls -> Bouncer
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            // Snap to Idle in Gone. -> Bouncer/UNDEFINED is still RUNNING, but Gone is also an
            // UNDEFINED scene, so we should end up FINISHED.
            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Gone)

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
        }

    @Test
    fun snapOverlayToLockscreenTransition_toTransitionFromLockscreenToThirdScene() =
        testScope.runTest {
            val currentStep by collectLastValue(kosmos.realKeyguardTransitionRepository.transitions)
            val allSteps by collectValues(kosmos.realKeyguardTransitionRepository.transitions)

            sceneTransitions.value =
                ObservableTransitionState.Transition.ShowOrHideOverlay(
                    overlay = Overlays.Bouncer,
                    fromContent = Overlays.Bouncer,
                    toContent = Scenes.Lockscreen,
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = flowOf(setOf(Overlays.Bouncer)),
                    progress,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            progress.value = 0.4f

            // We're partway from Bouncer -> LS
            assertTransition(
                step = currentStep!!,
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            val stepCount = allSteps.size

            sceneTransitions.value =
                ObservableTransitionState.Transition(
                    Scenes.Lockscreen,
                    Scenes.Shade,
                    flowOf(Scenes.Shade),
                    progress,
                    false,
                    flowOf(false),
                )

            assertTransition(
                step = allSteps[allSteps.size - 3],
                from = KeyguardState.UNDEFINED,
                to = KeyguardState.LOCKSCREEN,
                state = TransitionState.CANCELED,
                progress = 0.4f,
            )

            assertTransition(
                step = allSteps[allSteps.size - 2],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.STARTED,
                progress = 0f, // Progress resets because this is a totally different transition.
            )

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.RUNNING,
                progress = 0.4f,
            )

            assertEquals(stepCount + 3, allSteps.size)

            sceneTransitions.value = ObservableTransitionState.Idle(Scenes.Shade)

            assertTransition(
                step = allSteps[allSteps.size - 1],
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.UNDEFINED,
                state = TransitionState.FINISHED,
                progress = 1f,
            )
            assertEquals(stepCount + 4, allSteps.size)
        }

    private fun assertTransition(
        step: TransitionStep,
        from: KeyguardState? = null,
        to: KeyguardState? = null,
        state: TransitionState? = null,
        progress: Float? = null,
    ) {
        if (from != null) assertThat(step.from).isEqualTo(from)
        if (to != null) assertThat(step.to).isEqualTo(to)
        if (state != null) assertThat(step.transitionState).isEqualTo(state)
        if (progress != null) assertThat(step.value).isEqualTo(progress)
    }
}
