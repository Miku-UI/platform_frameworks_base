/*
 * Copyright (C) 2023 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.deviceentry.domain.interactor.deviceUnlockedInteractor
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.deviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.data.model.asIterable
import com.android.systemui.scene.data.model.sceneStackOf
import com.android.systemui.scene.data.repository.HideOverlay
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneBackInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class WindowManagerLockscreenVisibilityInteractorTest : SysuiTestCase() {

    /** Helper to strip out the logging reason from the lockscreen visibility flow. */
    private val lockscreenVisibilityBoolean by lazy {
        underTest.lockscreenVisibility.map { it.first }
    }
    private val lockscreenSurfaceVisibilityFlow = MutableStateFlow<Boolean?>(false)
    private val primaryBouncerSurfaceVisibilityFlow = MutableStateFlow<Boolean?>(false)
    private val surfaceBehindIsAnimatingFlow = MutableStateFlow(false)

    private val kosmos =
        testKosmos().apply {
            fromLockscreenTransitionInteractor = mock<FromLockscreenTransitionInteractor>()
            fromPrimaryBouncerTransitionInteractor = mock<FromPrimaryBouncerTransitionInteractor>()
            keyguardSurfaceBehindInteractor = mock<KeyguardSurfaceBehindInteractor>()

            whenever(fromLockscreenTransitionInteractor.surfaceBehindVisibility)
                .thenReturn(lockscreenSurfaceVisibilityFlow)
            whenever(fromPrimaryBouncerTransitionInteractor.surfaceBehindVisibility)
                .thenReturn(primaryBouncerSurfaceVisibilityFlow)
            whenever(keyguardSurfaceBehindInteractor.isAnimatingSurface)
                .thenReturn(surfaceBehindIsAnimatingFlow)
        }

    private lateinit var underTest: WindowManagerLockscreenVisibilityInteractor

    @Before
    fun setUp() {
        underTest = kosmos.windowManagerLockscreenVisibilityInteractor
        kosmos.setSceneTransition(ObservableTransitionState.Idle(Scenes.Lockscreen))
    }

    @Test
    @DisableSceneContainer
    fun surfaceBehindVisibility_switchesToCorrectFlow() =
        kosmos.runTest {
            val values by collectValues(underTest.surfaceBehindVisibility)

            // Start on LOCKSCREEN.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )

            runCurrent()

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )

            runCurrent()

            assertEquals(
                listOf(
                    false // We should start with the surface invisible on LOCKSCREEN.
                ),
                values,
            )

            val lockscreenSpecificSurfaceVisibility = true
            lockscreenSurfaceVisibilityFlow.emit(lockscreenSpecificSurfaceVisibility)
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GONE,
                )
            )

            runCurrent()

            // We started a transition from LOCKSCREEN, we should be using the value emitted by the
            // lockscreenSurfaceVisibilityFlow.
            assertEquals(listOf(false, lockscreenSpecificSurfaceVisibility), values)

            // Go back to LOCKSCREEN, since we won't emit 'true' twice in a row.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    lockscreenSpecificSurfaceVisibility,
                    false, // FINISHED (LOCKSCREEN)
                ),
                values,
            )

            val bouncerSpecificVisibility = true
            primaryBouncerSurfaceVisibilityFlow.emit(bouncerSpecificVisibility)
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.PRIMARY_BOUNCER,
                    to = KeyguardState.GONE,
                )
            )

            runCurrent()

            // We started a transition from PRIMARY_BOUNCER, we should be using the value emitted by
            // the primaryBouncerSurfaceVisibilityFlow.
            assertEquals(
                listOf(
                    false,
                    lockscreenSpecificSurfaceVisibility,
                    false,
                    bouncerSpecificVisibility,
                ),
                values,
            )
        }

    @Test
    @EnableSceneContainer
    fun surfaceBehindVisibility_fromLockscreenToGone_dependsOnDeviceEntry() =
        kosmos.runTest {
            val isSurfaceBehindVisible by collectLastValue(underTest.surfaceBehindVisibility)
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // Before the transition, we start on Lockscreen so the surface should start invisible.
            setSceneTransition(ObservableTransitionState.Idle(Scenes.Lockscreen))
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(isSurfaceBehindVisible).isFalse()

            // Unlocked with fingerprint.
            deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )

            // Start the transition to Gone, the surface should remain invisible.
            setSceneTransition(
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Gone,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    progress = flowOf(0.3f),
                    currentScene = flowOf(Scenes.Lockscreen),
                )
            )
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(isSurfaceBehindVisible).isFalse()

            // Towards the end of the transition, the surface should continue to remain invisible.
            setSceneTransition(
                ObservableTransitionState.Transition(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Gone,
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                    progress = flowOf(0.9f),
                    currentScene = flowOf(Scenes.Gone),
                )
            )
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(isSurfaceBehindVisible).isFalse()

            // After the transition, settles on Gone. Surface behind should stay visible now.
            setSceneTransition(ObservableTransitionState.Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(isSurfaceBehindVisible).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun surfaceBehindVisibility_fromBouncerToGone_becomesTrue() =
        kosmos.runTest {
            val isSurfaceBehindVisible by collectLastValue(underTest.surfaceBehindVisibility)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            // Before the transition, we start on Bouncer so the surface should start invisible.
            setSceneTransition(
                ObservableTransitionState.Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer))
            )
            sceneInteractor.showOverlay(Overlays.Bouncer, "")
            assertThat(currentOverlays).contains(Overlays.Bouncer)
            assertThat(isSurfaceBehindVisible).isFalse()

            // Unlocked with fingerprint.
            deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )

            // Start the transition to Gone, the surface should remain invisible prior to hitting
            // the threshold.
            setSceneTransition(
                ObservableTransitionState.Transition.hideOverlay(
                    overlay = Overlays.Bouncer,
                    toScene = Scenes.Gone,
                    currentOverlays = flowOf(setOf(Overlays.Bouncer)),
                    progress =
                        flowOf(
                            FromPrimaryBouncerTransitionInteractor
                                .TO_GONE_SURFACE_BEHIND_VISIBLE_THRESHOLD
                        ),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            )
            assertThat(currentOverlays).contains(Overlays.Bouncer)
            assertThat(isSurfaceBehindVisible).isFalse()

            // Once the transition passes the threshold, the surface should become visible.
            setSceneTransition(
                ObservableTransitionState.Transition.hideOverlay(
                    overlay = Overlays.Bouncer,
                    toScene = Scenes.Gone,
                    currentOverlays = flowOf(setOf(Overlays.Bouncer)),
                    progress =
                        flowOf(
                            FromPrimaryBouncerTransitionInteractor
                                .TO_GONE_SURFACE_BEHIND_VISIBLE_THRESHOLD + 0.01f
                        ),
                    isInitiatedByUserInput = false,
                    isUserInputOngoing = flowOf(false),
                )
            )
            assertThat(currentOverlays).contains(Overlays.Bouncer)
            assertThat(isSurfaceBehindVisible).isTrue()

            // After the transition, settles on Gone. Surface behind should stay visible now.
            setSceneTransition(ObservableTransitionState.Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")
            sceneInteractor.hideOverlay(Overlays.Bouncer, "")
            assertThat(currentOverlays).doesNotContain(Overlays.Bouncer)
            assertThat(isSurfaceBehindVisible).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun surfaceBehindVisibility_idleWhileUnlocked_alwaysTrue() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()
            val isSurfaceBehindVisible by collectLastValue(underTest.surfaceBehindVisibility)
            val currentScene by collectLastValue(sceneInteractor.currentScene)

            // Unlocked with fingerprint.
            deviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            setSceneTransition(ObservableTransitionState.Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")
            assertThat(currentScene).isEqualTo(Scenes.Gone)

            listOf(Scenes.Shade, Scenes.QuickSettings, Scenes.Shade, Scenes.Gone).forEach { scene ->
                setSceneTransition(ObservableTransitionState.Idle(scene))
                sceneInteractor.changeScene(scene, "")
                assertThat(currentScene).isEqualTo(scene)
                assertWithMessage("Unexpected visibility for scene \"${scene.debugName}\"")
                    .that(isSurfaceBehindVisible)
                    .isTrue()
            }
        }

    @Test
    @EnableSceneContainer
    fun surfaceBehindVisibility_whileDeviceNotProvisioned_alwaysTrue() =
        kosmos.runTest {
            val isSurfaceBehindVisible by collectLastValue(underTest.surfaceBehindVisibility)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(isSurfaceBehindVisible).isFalse()

            fakeDeviceProvisioningRepository.setDeviceProvisioned(false)
            runCurrent()

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(isSurfaceBehindVisible).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun surfaceBehindVisibility_idleWhileLocked_alwaysFalse() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()
            val isSurfaceBehindVisible by collectLastValue(underTest.surfaceBehindVisibility)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            listOf(Scenes.Shade, Scenes.QuickSettings, Scenes.Shade, Scenes.Lockscreen).forEach {
                scene ->
                setSceneTransition(ObservableTransitionState.Idle(scene))
                sceneInteractor.changeScene(scene, "")
                assertWithMessage("Unexpected visibility for scene \"${scene.debugName}\"")
                    .that(isSurfaceBehindVisible)
                    .isFalse()
            }
        }

    @Test
    @DisableSceneContainer
    fun testUsingGoingAwayAnimation_duringTransitionToGone() =
        kosmos.runTest {
            val values by collectValues(underTest.usingKeyguardGoingAwayAnimation)

            // Start on LOCKSCREEN.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    false // Not using the animation when we're just sitting on LOCKSCREEN.
                ),
                values,
            )

            surfaceBehindIsAnimatingFlow.emit(true)
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                testScope,
            )
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    true, // Still true when we're FINISHED -> GONE, since we're still animating.
                ),
                values,
            )

            surfaceBehindIsAnimatingFlow.emit(false)
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    true,
                    false, // False once the animation ends.
                ),
                values,
            )
        }

    @Test
    @DisableSceneContainer
    fun testNotUsingGoingAwayAnimation_evenWhenAnimating_ifStateIsNotGone() =
        kosmos.runTest {
            val values by collectValues(underTest.usingKeyguardGoingAwayAnimation)

            // Start on LOCKSCREEN.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    false // Not using the animation when we're just sitting on LOCKSCREEN.
                ),
                values,
            )

            surfaceBehindIsAnimatingFlow.emit(true)
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    true, // We're happily animating while transitioning to gone.
                ),
                values,
            )

            // Oh no, we're still surfaceBehindAnimating=true, but no longer transitioning to GONE.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.CANCELED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    true,
                    false, // Despite the animator still running, this should be false.
                ),
                values,
            )

            surfaceBehindIsAnimatingFlow.emit(false)
            runCurrent()

            assertEquals(
                listOf(
                    false,
                    true,
                    false, // The animator ending should have no effect.
                ),
                values,
            )
        }

    @Test
    @DisableSceneContainer
    fun lockscreenVisibility_visibleWhenGone() =
        kosmos.runTest {
            val values by collectValues(lockscreenVisibilityBoolean)

            // Start on LOCKSCREEN.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true // Unsurprisingly, we should start with the lockscreen visible on
                    // LOCKSCREEN.
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true // Lockscreen remains visible while we're transitioning to GONE.
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true,
                    false, // Once we're fully GONE, the lockscreen should not be visible.
                ),
                values,
            )
        }

    @Test
    @DisableSceneContainer
    fun testLockscreenVisibility_usesFromState_ifCanceled() =
        kosmos.runTest {
            val values by collectValues(lockscreenVisibilityBoolean)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                testScope,
            )

            runCurrent()

            assertEquals(
                listOf(
                    // Initially should be true, as we start in LOCKSCREEN.
                    true,
                    // Then, false, since we finish in GONE.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.RUNNING,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true,
                    // Should remain false as we transition from GONE.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.CANCELED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )

            runCurrent()

            assertEquals(
                listOf(
                    true,
                    false,
                    // If we cancel and then go from LS -> GONE, we should immediately flip to the
                    // visibility of the from state (LS).
                    true,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.LOCKSCREEN,
                )
            )

            runCurrent()

            assertEquals(listOf(true, false, true), values)
        }

    /**
     * Tests the special case for insecure camera launch. CANCELING a transition from GONE and then
     * STARTING a transition back to GONE should never show the lockscreen, even though the current
     * state during the AOD/isAsleep -> GONE transition is AOD (where lockscreen visibility = true).
     */
    @Test
    @DisableSceneContainer
    fun testLockscreenVisibility_falseDuringTransitionToGone_fromCanceledGone() =
        kosmos.runTest {
            val values by collectValues(lockscreenVisibilityBoolean)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                testScope,
            )

            runCurrent()
            assertEquals(
                listOf(
                    true,
                    // Not visible since we're GONE.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.RUNNING,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.CANCELED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.RUNNING,
                    from = KeyguardState.AOD,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.GONE,
                )
            )

            runCurrent()
            assertEquals(
                listOf(
                    true,
                    // Remains not visible from GONE -> AOD (canceled) -> AOD since we never
                    // FINISHED in AOD, and special-case handling for the insecure camera launch
                    // ensures that we use the lockscreen visibility for GONE (false) if we're
                    // STARTED to GONE after a CANCELED from GONE.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.LOCKSCREEN,
                testScope,
            )

            assertEquals(
                listOf(
                    true,
                    false,
                    // Make sure there's no stuck overrides or something - we should make lockscreen
                    // visible again once we're finished in LOCKSCREEN.
                    true,
                ),
                values,
            )
        }

    @Test
    @DisableSceneContainer
    fun testLockscreenVisibility_trueDuringTransitionToGone_fromNotCanceledGone() =
        kosmos.runTest {
            val values by collectValues(lockscreenVisibilityBoolean)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                testScope,
            )

            runCurrent()
            assertEquals(
                listOf(
                    true,
                    // Not visible when finished in GONE.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.RUNNING,
                    from = KeyguardState.GONE,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true,
                    // Still not visible during GONE -> LOCKSCREEN.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.LOCKSCREEN,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true,
                    false,
                    // Visible now that we're FINISHED in LOCKSCREEN.
                    true,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.RUNNING,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true,
                    false,
                    // Remains true until the transition ends.
                    true,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.GONE,
                )
            )

            runCurrent()
            assertEquals(
                listOf(
                    true,
                    false,
                    true,
                    // Until we're finished in GONE again.
                    false,
                ),
                values,
            )
        }

    @Test
    @DisableSceneContainer
    fun testLockscreenVisibility_falseDuringWakeAndUnlockToGone_fromNotCanceledGone() =
        kosmos.runTest {
            val values by collectValues(lockscreenVisibilityBoolean)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                testScope,
            )

            runCurrent()
            assertEquals(
                listOf(
                    true,
                    // Not visible when finished in GONE.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.RUNNING,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true,
                    // Still not visible during GONE -> AOD.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.GONE,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true,
                    false,
                    // Visible now that we're FINISHED in AOD.
                    true,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.STARTED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.RUNNING,
                    from = KeyguardState.AOD,
                    to = KeyguardState.GONE,
                )
            )
            runCurrent()

            assertEquals(
                listOf(
                    true,
                    false,
                    true,
                    // Becomes false immediately since we're wake and unlocking.
                    false,
                ),
                values,
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.AOD,
                    to = KeyguardState.GONE,
                )
            )

            runCurrent()
            assertEquals(
                listOf(
                    true,
                    false,
                    true,
                    // Until we're finished in GONE again.
                    false,
                ),
                values,
            )
        }

    @Test
    @EnableSceneContainer
    fun lockscreenVisibilityWithScenes() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()
            val isDeviceUnlocked by
                collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus.map { it.isUnlocked })
            assertThat(isDeviceUnlocked).isFalse()

            setSceneTransition(Idle(Scenes.Lockscreen))
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            val lockscreenVisibility by collectLastValue(lockscreenVisibilityBoolean)
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Shade))
            sceneInteractor.changeScene(Scenes.Shade, "")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Transition(from = Scenes.Shade, to = Scenes.QuickSettings))
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.QuickSettings))
            sceneInteractor.changeScene(Scenes.QuickSettings, "")
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Transition(from = Scenes.QuickSettings, to = Scenes.Shade))
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Shade))
            sceneInteractor.changeScene(Scenes.Shade, "")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer)))
            sceneInteractor.snapToScene(Scenes.Lockscreen, "")
            sceneInteractor.showOverlay(Overlays.Bouncer, "")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.Bouncer)
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(HideOverlay(overlay = Overlays.Bouncer, toScene = Scenes.Gone))
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Gone))
            kosmos.authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            assertThat(isDeviceUnlocked).isTrue()
            sceneInteractor.changeScene(Scenes.Gone, "")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(lockscreenVisibility).isFalse()

            setSceneTransition(Idle(Scenes.Shade))
            sceneInteractor.changeScene(Scenes.Shade, "")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(lockscreenVisibility).isFalse()

            setSceneTransition(Transition(from = Scenes.Shade, to = Scenes.QuickSettings))
            assertThat(lockscreenVisibility).isFalse()

            setSceneTransition(Idle(Scenes.QuickSettings))
            sceneInteractor.changeScene(Scenes.QuickSettings, "")
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(lockscreenVisibility).isFalse()

            setSceneTransition(Idle(Scenes.Shade))
            sceneInteractor.changeScene(Scenes.Shade, "")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(lockscreenVisibility).isFalse()

            setSceneTransition(Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(lockscreenVisibility).isFalse()

            powerInteractor.setAsleepForTest()
            setSceneTransition(Transition(from = Scenes.Gone, to = Scenes.Lockscreen))
            // Lockscreen remains not visible during the transition so that the unlocked app content
            // is visible under the light reveal screen off animation.
            assertThat(lockscreenVisibility).isFalse()

            setSceneTransition(Idle(Scenes.Lockscreen))
            sceneInteractor.changeScene(Scenes.Lockscreen, "")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(lockscreenVisibility).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun lockscreenVisibilityWithScenes_staysTrue_despiteEnteringIndirectly() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()
            val isDeviceUnlocked by
                collectLastValue(deviceUnlockedInteractor.deviceUnlockStatus.map { it.isUnlocked })
            assertThat(isDeviceUnlocked).isFalse()

            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)

            val lockscreenVisibility by collectLastValue(lockscreenVisibilityBoolean)
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Shade))
            sceneInteractor.changeScene(Scenes.Shade, "")
            sceneBackInteractor.onSceneChange(from = Scenes.Lockscreen, to = Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(lockscreenVisibility).isTrue()
            val sceneBackStack by collectLastValue(sceneBackInteractor.backStack)
            assertThat(sceneBackStack?.asIterable()?.toList()).isEqualTo(listOf(Scenes.Lockscreen))

            val isDeviceEntered by collectLastValue(deviceEntryInteractor.isDeviceEntered)
            val isDeviceEnteredDirectly by
                collectLastValue(deviceEntryInteractor.isDeviceEnteredDirectly)
            runCurrent()
            assertThat(isDeviceEntered).isFalse()
            assertThat(isDeviceEnteredDirectly).isFalse()

            kosmos.authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            sceneBackInteractor.updateBackStack { sceneStackOf(Scenes.Gone) }
            assertThat(sceneBackStack?.asIterable()?.toList()).isEqualTo(listOf(Scenes.Gone))

            assertThat(isDeviceEntered).isTrue()
            assertThat(isDeviceEnteredDirectly).isFalse()
            assertThat(isDeviceUnlocked).isTrue()
            assertThat(lockscreenVisibility).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun sceneContainer_usingGoingAwayAnimation_duringTransitionToGone() =
        kosmos.runTest {
            val usingKeyguardGoingAwayAnimation by
                collectLastValue(underTest.usingKeyguardGoingAwayAnimation)

            setSceneTransition(lsToGone)
            assertThat(usingKeyguardGoingAwayAnimation).isTrue()

            setSceneTransition(ObservableTransitionState.Idle(Scenes.Gone))
            assertThat(usingKeyguardGoingAwayAnimation).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun sceneContainer_usingGoingAwayAnimation_surfaceBehindIsAnimating() =
        kosmos.runTest {
            val usingKeyguardGoingAwayAnimation by
                collectLastValue(underTest.usingKeyguardGoingAwayAnimation)

            setSceneTransition(lsToGone)
            surfaceBehindIsAnimatingFlow.emit(true)
            assertThat(usingKeyguardGoingAwayAnimation).isTrue()

            setSceneTransition(ObservableTransitionState.Idle(Scenes.Gone))
            assertThat(usingKeyguardGoingAwayAnimation).isTrue()

            setSceneTransition(goneToLs)
            assertThat(usingKeyguardGoingAwayAnimation).isTrue()

            surfaceBehindIsAnimatingFlow.emit(false)
            assertThat(usingKeyguardGoingAwayAnimation).isFalse()
        }

    @Test
    fun aodVisibility_visibleFullyInAod_falseOtherwise() =
        kosmos.runTest {
            val aodVisibility by collectValues(underTest.aodVisibility)

            fakeKeyguardTransitionRepository.sendTransitionStepsThroughRunning(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                testScope,
                throughValue = 0.5f,
            )

            assertEquals(listOf(false), aodVisibility)

            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    transitionState = TransitionState.FINISHED,
                    from = KeyguardState.LOCKSCREEN,
                    to = KeyguardState.AOD,
                )
            )
            runCurrent()

            assertEquals(listOf(false, true), aodVisibility)

            fakeKeyguardTransitionRepository.sendTransitionStepsThroughRunning(
                from = KeyguardState.AOD,
                to = KeyguardState.GONE,
                testScope,
            )
            runCurrent()

            assertEquals(listOf(false, true, false), aodVisibility)
        }

    @Test
    @EnableSceneContainer
    fun lockscreenVisibility_notVisibleCollapsingShadeOverLockscreen() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()

            setSceneTransition(Idle(Scenes.Lockscreen))

            val lockscreenVisibility by collectLastValue(lockscreenVisibilityBoolean)
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Shade))
            sceneInteractor.changeScene(Scenes.Shade, "")
            assertThat(lockscreenVisibility).isTrue()

            // Ensure that LS remains not visible during Shade -> Lockscreen. Since Shade is not
            // explicitly a Keyguard scene, we've had regressions where lockscreen becomes visible
            // during transitions from Shade.
            setSceneTransition(Transition(from = Scenes.Shade, to = Scenes.Lockscreen))
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Lockscreen))
            sceneInteractor.changeScene(Scenes.Lockscreen, "")
            assertThat(lockscreenVisibility).isTrue()

            kosmos.authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            setSceneTransition(Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")
            assertThat(lockscreenVisibility).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun lockscreenVisibility_remainsVisibleDuringLsGone() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()

            setSceneTransition(Idle(Scenes.Lockscreen))

            val lockscreenVisibility by collectLastValue(lockscreenVisibilityBoolean)
            assertThat(lockscreenVisibility).isTrue()

            kosmos.authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            setSceneTransition(Transition(from = Scenes.Lockscreen, to = Scenes.Gone))
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")
            assertThat(lockscreenVisibility).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun lockscreenVisibility_remainsNotVisibleDuringGoneLs() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()

            kosmos.authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            setSceneTransition(Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")

            val lockscreenVisibility by collectLastValue(lockscreenVisibilityBoolean)
            assertThat(lockscreenVisibility).isFalse()

            // Lockscreen vis remains false during Gone -> LS so the unlocked app content is visible
            // during the screen off animation.
            powerInteractor.setAsleepForTest()
            setSceneTransition(Transition(from = Scenes.Gone, to = Scenes.Lockscreen))
            assertThat(lockscreenVisibility).isFalse()

            setSceneTransition(Idle(Scenes.Lockscreen))
            sceneInteractor.changeScene(Scenes.Lockscreen, "")
            assertThat(lockscreenVisibility).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun lockscreenVisibility_becomesVisible_ifAwakeDuringGoneLs() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()

            powerInteractor.setAwakeForTest()
            kosmos.authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            setSceneTransition(Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")

            val lockscreenVisibility by collectLastValue(lockscreenVisibilityBoolean)
            assertThat(lockscreenVisibility).isFalse()

            // Lockscreen vis remains false during Gone -> LS so the unlocked app content is visible
            // during the screen off animation.
            powerInteractor.setAsleepForTest()
            setSceneTransition(Transition(from = Scenes.Gone, to = Scenes.Lockscreen))
            assertThat(lockscreenVisibility).isFalse()

            powerInteractor.setAwakeForTest()
            runCurrent()
            assertThat(lockscreenVisibility).isTrue()

            setSceneTransition(Idle(Scenes.Lockscreen))
            sceneInteractor.changeScene(Scenes.Lockscreen, "")
            assertThat(lockscreenVisibility).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun lockscreenVisibility_remainsNotVisible_ifCameraLaunch() =
        kosmos.runTest {
            enableSingleShade()
            runCurrent()

            kosmos.authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
            setSceneTransition(Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")

            val lockscreenVisibility by collectLastValue(lockscreenVisibilityBoolean)
            assertThat(lockscreenVisibility).isFalse()

            // Lockscreen vis remains false during Gone -> LS so the unlocked app content is visible
            // during the screen off animation.
            powerInteractor.setAsleepForTest()
            setSceneTransition(Transition(from = Scenes.Gone, to = Scenes.Lockscreen))
            assertThat(lockscreenVisibility).isFalse()

            powerInteractor.onCameraLaunchGestureDetected()
            powerInteractor.setAwakeForTest()
            runCurrent()
            assertThat(lockscreenVisibility).isFalse()

            setSceneTransition(Transition(from = Scenes.Lockscreen, to = Scenes.Gone))
            setSceneTransition(Idle(Scenes.Gone))
            sceneInteractor.changeScene(Scenes.Gone, "")
            assertThat(lockscreenVisibility).isFalse()
        }

    companion object {
        private val progress = MutableStateFlow(0f)

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
    }
}
