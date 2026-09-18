/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade.domain.interactor

import android.graphics.Rect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState.Idle
import com.android.compose.animation.scene.ObservableTransitionState.Transition
import com.android.compose.animation.scene.OverlayKey
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.quickactions.popups.StatusBarPopupChips
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ShadeInteractorSceneContainerImplTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { shadeInteractorSceneContainerImpl }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun qsExpansionWhenInSplitShadeAndQsExpanded() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.qsExpansion)

            // WHEN split shade is enabled and QS is expanded
            enableSplitShade()
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.QuickSettings,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(.3f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)

            // THEN legacy shade expansion is passed through
            assertThat(actual).isEqualTo(.3f)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun qsExpansionWhenInSingleShadeAndQsExpanded() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.qsExpansion)

            // WHEN split shade is not enabled and QS is expanded
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            enableSingleShade()
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.QuickSettings,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(.3f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN shade expansion is zero
            assertThat(actual).isEqualTo(.7f)
        }

    @Test
    fun qsFullscreen_singleShade_falseWhenTransitioning() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isQsFullscreen)

            // WHEN scene transition active
            enableSingleShade()
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.QuickSettings,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Shade),
                        progress = flowOf(.3f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN QS is not fullscreen
            assertThat(actual).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun qsFullscreen_dualShade_falseWhenTransitioning() =
        kosmos.runTest {
            enableDualShade(wideLayout = false)
            val actual by collectLastValue(underTest.isQsFullscreen)

            // WHEN overlay transition active
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition.ReplaceOverlay(
                        fromOverlay = Overlays.QuickSettingsShade,
                        toOverlay = Overlays.NotificationsShade,
                        currentScene = Scenes.Gone,
                        currentOverlays = flowOf(setOf(Overlays.QuickSettingsShade)),
                        progress = flowOf(.3f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                        previewProgress = flowOf(0f),
                        isInPreviewStage = flowOf(false),
                    )
                )
            )

            // THEN QS is not fullscreen
            assertThat(actual).isFalse()
        }

    @Test
    fun qsFullscreen_falseWhenIdleNotQs() =
        kosmos.runTest {
            enableSingleShade()
            val actual by collectLastValue(underTest.isQsFullscreen)

            // WHEN Idle but not on QuickSettings scene
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            sceneInteractor.setTransitionState(flowOf(Idle(Scenes.Shade)))

            // THEN QS is not fullscreen
            assertThat(actual).isFalse()
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun qsFullscreen_splitShade_falseWhenIdleQs() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isQsFullscreen)

            // WHEN split shade is enabled and Idle on QuickSettings scene
            enableSplitShade()
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            sceneInteractor.setTransitionState(flowOf(Idle(Scenes.QuickSettings)))

            // THEN QS is not fullscreen
            assertThat(actual).isFalse()
        }

    @Test
    fun qsFullscreen_singleShade_trueWhenIdleQs() =
        kosmos.runTest {
            enableSingleShade()
            val actual by collectLastValue(underTest.isQsFullscreen)

            // WHEN Idle on QuickSettings scene
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            sceneInteractor.setTransitionState(flowOf(Idle(Scenes.QuickSettings)))

            // THEN QS is fullscreen
            assertThat(actual).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun qsFullscreen_dualShade_trueWhenIdleQs() =
        kosmos.runTest {
            enableDualShade(wideLayout = false)
            val actual by collectLastValue(underTest.isQsFullscreen)

            // WHEN Idle on QuickSettingsShade overlay
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            sceneInteractor.setTransitionState(
                flowOf(
                    Idle(
                        currentScene = Scenes.Gone,
                        currentOverlays = setOf(Overlays.QuickSettingsShade),
                    )
                )
            )

            // THEN QS is fullscreen
            assertThat(actual).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun qsFullscreen_dualShadeWide_trueWhenIdleQs() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)
            val actual by collectLastValue(underTest.isQsFullscreen)

            // WHEN Idle on QuickSettingsShade overlay
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            sceneInteractor.setTransitionState(
                flowOf(
                    Idle(
                        currentScene = Scenes.Gone,
                        currentOverlays = setOf(Overlays.QuickSettingsShade),
                    )
                )
            )

            // THEN QS is fullscreen
            assertThat(actual).isTrue()
        }

    @Test
    fun toggleNotificationsShade_singleShade_throwsException() =
        kosmos.runTest {
            // GIVEN single shade is enabled
            enableSingleShade()

            // WHEN the notifications shade is toggled
            // THEN an IllegalStateException is thrown
            assertThrows(IllegalStateException::class.java) {
                underTest.toggleNotificationsShade("reason")
            }
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun toggleNotificationsShade_splitShade_throwsException() =
        kosmos.runTest {
            // GIVEN split shade is enabled
            enableSplitShade()

            // WHEN the notifications shade is toggled
            // THEN an IllegalStateException is thrown
            assertThrows(IllegalStateException::class.java) {
                underTest.toggleNotificationsShade("reason")
            }
        }

    @Test
    fun toggleQuickSettingsShade_singleShade_throwsException() =
        kosmos.runTest {
            // GIVEN single shade is enabled
            enableSingleShade()

            // WHEN the quick settings shade is toggled
            // THEN an IllegalStateException is thrown
            assertThrows(IllegalStateException::class.java) {
                underTest.toggleQuickSettingsShade("reason")
            }
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun toggleQuickSettingsShade_splitShade_throwsException() =
        kosmos.runTest {
            // GIVEN split shade is enabled
            enableSplitShade()

            // WHEN the quick settings shade is toggled
            // THEN an IllegalStateException is thrown
            assertThrows(IllegalStateException::class.java) {
                underTest.toggleQuickSettingsShade("reason")
            }
        }

    @Test
    fun lockscreenShadeExpansion_idle_onScene() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an expansion flow based on transitions to and from a scene
            val key = Scenes.Shade
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, key)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is idle on the scene
            sceneInteractor.setTransitionState(flowOf(Idle(key)))

            // THEN expansion is 1
            assertThat(expansionAmount).isEqualTo(1f)
        }

    @Test
    fun lockscreenShadeExpansion_idle_onDifferentScene() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an expansion flow based on transitions to and from a scene
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, Scenes.Shade)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is idle on a different scene
            sceneInteractor.setTransitionState(flowOf(Idle(Scenes.Lockscreen)))

            // THEN expansion is 0
            assertThat(expansionAmount).isEqualTo(0f)
        }

    @Test
    fun lockscreenShadeExpansion_transitioning_toScene() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an expansion flow based on transitions to and from a scene
            val key = Scenes.QuickSettings
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, key)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = key,
                        currentScene = flowOf(key),
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN expansion is 0
            assertThat(expansionAmount).isEqualTo(0f)

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN expansion matches the progress
            assertThat(expansionAmount).isEqualTo(.4f)

            // WHEN transition completes
            progress.value = 1f

            // THEN expansion is 1
            assertThat(expansionAmount).isEqualTo(1f)
        }

    @Test
    fun lockscreenShadeExpansion_transitioning_fromScene() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an expansion flow based on transitions to and from a scene
            val key = Scenes.QuickSettings
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, key)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = key,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN expansion is 1
            assertThat(expansionAmount).isEqualTo(1f)

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN expansion reflects the progress
            assertThat(expansionAmount).isEqualTo(.6f)

            // WHEN transition completes
            progress.value = 1f

            // THEN expansion is 0
            assertThat(expansionAmount).isEqualTo(0f)
        }

    @Test
    fun lockscreenShadeExpansion_idleOnScene_openBounceOverlay() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an expansion flow based on overlay transitions while on shade
            val key = Scenes.Shade
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, key)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is starting show overlay
            val progress = MutableStateFlow(0f)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition.showOverlay(
                        fromScene = key,
                        overlay = Overlays.Bouncer,
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                        currentOverlays = flowOf(emptySet()),
                    )
                )
            )

            // THEN expansion is always 1
            assertThat(expansionAmount).isEqualTo(1f)

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN expansion is always 1
            assertThat(expansionAmount).isEqualTo(1f)

            // WHEN transition completes
            progress.value = 1f

            // THEN expansion is always 1
            assertThat(expansionAmount).isEqualTo(1f)
        }

    fun isQsBypassingShade_goneToQs() =
        kosmos.runTest {
            enableSingleShade()
            val actual by collectLastValue(underTest.isQsBypassingShade)

            // WHEN transitioning from QS directly to Gone
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Gone,
                        toScene = Scenes.QuickSettings,
                        currentScene = flowOf(Scenes.QuickSettings),
                        progress = flowOf(.1f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN qs is bypassing shade
            assertThat(actual).isTrue()
        }

    fun isQsBypassingShade_shadeToQs() =
        kosmos.runTest {
            enableSingleShade()
            val actual by collectLastValue(underTest.isQsBypassingShade)

            // WHEN transitioning from QS to Shade
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Shade,
                        toScene = Scenes.QuickSettings,
                        currentScene = flowOf(Scenes.QuickSettings),
                        progress = flowOf(.1f),
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN qs is not bypassing shade
            assertThat(actual).isFalse()
        }

    @Test
    fun lockscreenShadeExpansion_transitioning_toAndFromDifferentScenes() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an expansion flow based on transitions to and from a scene
            val expansion = underTest.sceneBasedExpansion(sceneInteractor, Scenes.QuickSettings)
            val expansionAmount by collectLastValue(expansion)

            // WHEN transition state is starting to between different scenes
            val progress = MutableStateFlow(0f)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = Scenes.Shade,
                        currentScene = flowOf(Scenes.Shade),
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN expansion is 0
            assertThat(expansionAmount).isEqualTo(0f)

            // WHEN transition state is partially complete
            progress.value = .4f

            // THEN expansion is still 0
            assertThat(expansionAmount).isEqualTo(0f)

            // WHEN transition completes
            progress.value = 1f

            // THEN expansion is still 0
            assertThat(expansionAmount).isEqualTo(0f)
        }

    @Test
    fun userInteracting_idle() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = Scenes.Shade
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is idle
            sceneInteractor.setTransitionState(flowOf(Idle(key)))

            // THEN interacting is false
            assertThat(interacting).isFalse()
        }

    @Test
    fun userInteracting_transitioning_toScene_programmatic() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = Scenes.QuickSettings
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = key,
                        currentScene = flowOf(key),
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN interacting is false
            assertThat(interacting).isFalse()

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN interacting is false
            assertThat(interacting).isFalse()

            // WHEN transition completes
            progress.value = 1f

            // THEN interacting is false
            assertThat(interacting).isFalse()
        }

    @Test
    fun userInteracting_transitioning_toScene_userInputDriven() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = Scenes.QuickSettings
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = key,
                        currentScene = flowOf(key),
                        progress = progress,
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN interacting is true
            assertThat(interacting).isTrue()

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN interacting is true
            assertThat(interacting).isTrue()

            // WHEN transition completes
            progress.value = 1f

            // THEN interacting is true
            assertThat(interacting).isTrue()
        }

    @Test
    fun userInteracting_transitioning_fromScene_programmatic() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = Scenes.QuickSettings
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = key,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = progress,
                        isInitiatedByUserInput = false,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN interacting is false
            assertThat(interacting).isFalse()

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN interacting is false
            assertThat(interacting).isFalse()

            // WHEN transition completes
            progress.value = 1f

            // THEN interacting is false
            assertThat(interacting).isFalse()
        }

    @Test
    fun userInteracting_transitioning_fromScene_userInputDriven() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an interacting flow based on transitions to and from a scene
            val key = Scenes.QuickSettings
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, key)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to move to the scene
            val progress = MutableStateFlow(0f)
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = key,
                        toScene = Scenes.Lockscreen,
                        currentScene = flowOf(Scenes.Lockscreen),
                        progress = progress,
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN interacting is true
            assertThat(interacting).isTrue()

            // WHEN transition state is partially to the scene
            progress.value = .4f

            // THEN interacting is true
            assertThat(interacting).isTrue()

            // WHEN transition completes
            progress.value = 1f

            // THEN interacting is true
            assertThat(interacting).isTrue()
        }

    @Test
    fun userInteracting_transitioning_toAndFromDifferentScenes() =
        kosmos.runTest {
            enableSingleShade()
            // GIVEN an interacting flow based on transitions to and from a scene
            val interactingFlow = underTest.sceneBasedInteracting(sceneInteractor, Scenes.Shade)
            val interacting by collectLastValue(interactingFlow)

            // WHEN transition state is starting to between different scenes
            sceneInteractor.setTransitionState(
                flowOf(
                    Transition(
                        fromScene = Scenes.Lockscreen,
                        toScene = Scenes.QuickSettings,
                        currentScene = flowOf(Scenes.QuickSettings),
                        progress = flowOf(0f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )
            )

            // THEN interacting is false
            assertThat(interacting).isFalse()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun expandNotificationsShade_dualShade_opensOverlay() =
        kosmos.runTest {
            enableDualShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.expandNotificationsShade("reason")

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
        }

    @Test
    fun expandNotificationsShade_singleShade_switchesToShadeScene() =
        kosmos.runTest {
            enableSingleShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.expandNotificationsShade("reason")

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun expandNotificationsShade_dualShadeQuickSettingsOpen_replacesOverlay() =
        kosmos.runTest {
            enableDualShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)

            underTest.expandQuickSettingsShade("reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)

            underTest.expandNotificationsShade("reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE, StatusBarPopupChips.FLAG_NAME)
    fun expandNotificationsShade_dualShadeQuickActionsOpen_replacesOverlay() =
        kosmos.runTest {
            enableDualShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)

            showOverlay(Overlays.QuickActions)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.QuickActions)

            underTest.expandNotificationsShade("reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun expandQuickSettingsShade_dualShade_opensOverlay() =
        kosmos.runTest {
            enableDualShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.expandQuickSettingsShade("reason")

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    fun expandQuickSettingsShade_singleShade_switchesToQuickSettingsScene() =
        kosmos.runTest {
            enableSingleShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.expandQuickSettingsShade("reason")

            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun expandQuickSettingsShade_splitShade_switchesToShadeScene() =
        kosmos.runTest {
            enableSplitShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Split)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.expandQuickSettingsShade("reason")

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun expandQuickSettingsShade_dualShadeNotificationsOpen_replacesOverlay() =
        kosmos.runTest {
            enableDualShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)

            underTest.expandNotificationsShade("reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)

            underTest.expandQuickSettingsShade("reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE, StatusBarPopupChips.FLAG_NAME)
    fun expandQuickSettingsShade_dualShadeQuickActionsOpen_replacesOverlay() =
        kosmos.runTest {
            enableDualShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)

            showOverlay(Overlays.QuickActions)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.QuickActions)

            underTest.expandQuickSettingsShade("reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun collapseNotificationsShade_dualShade_hidesOverlay() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            showOverlay(Overlays.NotificationsShade)

            underTest.collapseNotificationsShade("reason")

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()
        }

    @Test
    fun collapseNotificationsShade_singleShade_switchesToLockscreen() =
        kosmos.runTest {
            enableSingleShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)

            sceneInteractor.changeScene(Scenes.Shade, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()

            underTest.collapseNotificationsShade("reason")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()
        }

    @Test
    fun collapseNotificationsShade_singleShade_shadeNotOpen_doesNotSwitchScene() =
        kosmos.runTest {
            enableSingleShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)

            // Change scene to Communal. Shade not open.
            sceneInteractor.changeScene(Scenes.Communal, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()

            // Verify scene does not change.
            underTest.collapseNotificationsShade("reason")
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun collapseQuickSettingsShade_dualShade_hidesOverlay() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            showOverlay(Overlays.QuickSettingsShade)

            underTest.collapseQuickSettingsShade("reason")

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()
        }

    @Test
    fun collapseQuickSettingsShadeNotBypassingShade_singleShade_switchesToShade() =
        kosmos.runTest {
            enableSingleShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)

            sceneInteractor.changeScene(Scenes.QuickSettings, "reason")
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()

            underTest.collapseQuickSettingsShade(
                loggingReason = "reason",
                bypassNotificationsShade = false,
            )

            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()
        }

    @Test
    fun collapseQuickSettingsShadeBypassingShade_singleShade_switchesToLockscreen() =
        kosmos.runTest {
            enableSingleShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)

            sceneInteractor.changeScene(Scenes.QuickSettings, "reason")
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(currentOverlays).isEmpty()

            underTest.collapseQuickSettingsShade(
                loggingReason = "reason",
                bypassNotificationsShade = true,
            )

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    fun collapseQuickSettingsShadeBypassingShade_quickSettingsNotOpen_doesNotSwitchScene() =
        kosmos.runTest {
            enableSingleShade()
            val shadeMode by collectLastValue(shadeMode)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(shadeMode).isEqualTo(ShadeMode.Single)

            // Change scene to Communal. Quick Settings not open.
            sceneInteractor.changeScene(Scenes.Communal, "reason")
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            assertThat(currentOverlays).isEmpty()

            // Verify scene does not change.
            underTest.collapseQuickSettingsShade(
                loggingReason = "reason",
                bypassNotificationsShade = true,
            )
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun collapseEitherShade_dualShade_hidesBothOverlays() =
        kosmos.runTest {
            enableDualShade()
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            showOverlay(Overlays.QuickSettingsShade)
            showOverlay(Overlays.NotificationsShade)
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.QuickSettingsShade, Overlays.NotificationsShade)

            underTest.collapseEitherShade("reason")

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun toggleNotificationsShade_dualShade_showsNotificationsOverlay() =
        kosmos.runTest {
            // GIVEN dual shade is enabled and no overlays are open
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(currentOverlays).isEmpty()

            // WHEN the notifications shade is toggled
            underTest.toggleNotificationsShade("reason")

            // THEN the notifications overlay is now visible
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun toggleNotificationsShade_dualShadeWithNotificationsOpen_hidesOverlay() =
        kosmos.runTest {
            // GIVEN dual shade is enabled and the notifications overlay is open
            enableDualShade()
            showOverlay(Overlays.NotificationsShade)

            // WHEN the notifications shade is toggled
            underTest.toggleNotificationsShade("reason")

            // THEN all overlays are hidden
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun toggleNotificationsShade_dualShadeWithQsOpen_replacesWithNotificationsOverlay() =
        kosmos.runTest {
            // GIVEN dual shade is enabled and the QS overlay is open
            enableDualShade()
            showOverlay(Overlays.QuickSettingsShade)

            // WHEN the notifications shade is toggled
            underTest.toggleNotificationsShade("reason")

            // THEN the QS overlay is replaced by the notifications overlay
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE, StatusBarPopupChips.FLAG_NAME)
    fun toggleNotificationsShade_dualShadeWithQuickActionsOpen_replacesWithNotificationsOverlay() =
        kosmos.runTest {
            // GIVEN dual shade is enabled and the Quick Actions overlay is open
            enableDualShade()
            showOverlay(Overlays.QuickActions)

            // WHEN the notifications shade is toggled
            underTest.toggleNotificationsShade("reason")

            // THEN the Quick Actions overlay is replaced by the notifications overlay
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun toggleQuickSettingsShade_dualShade_showsQsOverlay() =
        kosmos.runTest {
            // GIVEN dual shade is enabled and no overlays are open
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            assertThat(currentOverlays).isEmpty()

            // WHEN the QS shade is toggled
            underTest.toggleQuickSettingsShade("reason")

            // THEN the QS overlay is now visible
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun toggleQuickSettingsShade_dualShadeWithQsOpen_hidesOverlay() =
        kosmos.runTest {
            // GIVEN dual shade is enabled and the QS overlay is open
            enableDualShade()
            showOverlay(Overlays.QuickSettingsShade)

            // WHEN the QS shade is toggled
            underTest.toggleQuickSettingsShade("reason")

            // THEN all overlays are hidden
            assertThat(sceneInteractor.transitionState.currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun toggleQuickSettingsShade_dualShadeWithNotificationsOpen_replacesWithQsOverlay() =
        kosmos.runTest {
            // GIVEN dual shade is enabled and the notifications overlay is open
            enableDualShade()
            showOverlay(Overlays.NotificationsShade)

            // WHEN the QS shade is toggled
            underTest.toggleQuickSettingsShade("reason")

            // THEN the notifications overlay is replaced by the QS overlay
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE, StatusBarPopupChips.FLAG_NAME)
    fun toggleQuickSettingsShade_dualShadeWithQuickActionsOpen_replacesWithQsOverlay() =
        kosmos.runTest {
            // GIVEN dual shade is enabled and the Quick Actions overlay is open
            enableDualShade()
            showOverlay(Overlays.QuickActions)

            // WHEN the QS shade is toggled
            underTest.toggleQuickSettingsShade("reason")

            // THEN the Quick Actions overlay is replaced by the QS overlay
            assertThat(sceneInteractor.transitionState.currentOverlays)
                .containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    fun setShadeBounds_forwardsToShadeRepository() =
        kosmos.runTest {
            var shadeBounds: Rect? = null
            shadeRepository.addShadeBoundsListener { shadeBounds = it }
            assertThat(shadeBounds).isNull()

            val bounds = Rect(0, 0, 100, 100)
            underTest.setShadeOverlayBounds(bounds)

            assertThat(shadeBounds).isEqualTo(bounds)
        }

    private fun Kosmos.showOverlay(overlay: OverlayKey) {
        val shadeMode by collectLastValue(shadeMode)
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        val initialScene = checkNotNull(currentScene)
        assertThat(shadeMode).isEqualTo(ShadeMode.Dual)

        sceneInteractor.showOverlay(overlay, "reason")
        setSceneTransition(
            Idle(initialScene, checkNotNull(sceneInteractor.transitionState.currentOverlays)),
            skipChangeScene = true,
        )
        assertThat(currentScene).isEqualTo(initialScene)
        assertThat(sceneInteractor.transitionState.currentOverlays).contains(overlay)
    }
}
