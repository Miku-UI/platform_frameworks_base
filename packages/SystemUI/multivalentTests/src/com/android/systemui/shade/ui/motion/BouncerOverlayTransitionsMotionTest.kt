/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.shade.ui.motion

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.MotionTest
import android.testing.TestableLooper.RunWithLooper
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.animation.scene.DelegatingTransition
import com.android.compose.animation.scene.FeatureCaptures.elementAlpha
import com.android.compose.animation.scene.HoistedSceneTransitionLayoutState
import com.android.compose.animation.scene.Key
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.featureOfElement
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.keyguard.dagger.KeyguardStatusBarViewComponent
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.authController
import com.android.systemui.bouncer.ui.composable.Bouncer
import com.android.systemui.bouncer.ui.composable.BouncerMotionTestKeys.bouncerActionButtonAlpha
import com.android.systemui.bouncer.ui.composable.BouncerMotionTestKeys.bouncerActionButtonTranslationY
import com.android.systemui.bouncer.ui.composable.BouncerMotionTestKeys.bouncerContentAlpha
import com.android.systemui.bouncer.ui.composable.BouncerOverlay
import com.android.systemui.bouncer.ui.composable.BouncerSceneContainer
import com.android.systemui.bouncer.ui.viewmodel.BouncerOverlayContentViewModel
import com.android.systemui.bouncer.ui.viewmodel.BouncerUserActionsViewModel
import com.android.systemui.bouncer.ui.viewmodel.bouncerOverlayContentViewModel
import com.android.systemui.bouncer.ui.viewmodel.bouncerUserActionsViewModel
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.domain.interactor.keyguardClockInteractorWithImpl
import com.android.systemui.keyguard.ui.composable.LockscreenContent
import com.android.systemui.keyguard.ui.composable.LockscreenScene
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElementFactoryImpl
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElements
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.ambientIndicationAreaProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.clockRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.defaultClockFaceLayout
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.keyguardStatusBarViewComponentFactory
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockIconElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenLowerRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenRootElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenUpperRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.settingsMenuElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.statusBarElementProvider
import com.android.systemui.keyguard.ui.viewmodel.LockscreenUserActionsViewModel
import com.android.systemui.keyguard.ui.viewmodel.keyguardClockViewModelWithImpl
import com.android.systemui.keyguard.ui.viewmodel.lockscreenBehindScrimViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.lockscreenContentViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.lockscreenFrontScrimViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.lockscreenUserActionsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.log.LogBuffer
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.sceneContainerTransitions
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.scene.ui.view.sceneTransitionLatencyMonitor
import com.android.systemui.scene.ui.viewmodel.SceneContainerViewModel
import com.android.systemui.scene.ui.viewmodel.toBouncerTransitionViewModel
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.phone.KeyguardStatusBarViewController
import com.android.systemui.statusbar.phone.systemUIDialogDotFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.dataPointType
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

@RunWith(AndroidJUnit4::class)
@MotionTest
@LargeTest
@RunWithLooper
@EnableSceneContainer
@Ignore("b/496045082")
class BouncerOverlayTransitionsMotionTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val deviceSpec = DeviceEmulationSpec(Phone)
    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    private val lockscreenUserActionsViewModelFactory =
        object : LockscreenUserActionsViewModel.Factory {
            override fun create() = kosmos.lockscreenUserActionsViewModel
        }

    private val keyguardStatusBarViewComponentFactory: KeyguardStatusBarViewComponent.Factory =
        kosmos.keyguardStatusBarViewComponentFactory

    private val keyguardStatusBarViewComponent: KeyguardStatusBarViewComponent =
        mock<KeyguardStatusBarViewComponent>()
    private val keyguardStatusBarViewController: KeyguardStatusBarViewController =
        mock<KeyguardStatusBarViewController>()

    @Before
    fun setup() {
        whenever(keyguardStatusBarViewComponentFactory.build(any(), any()))
            .thenReturn(keyguardStatusBarViewComponent)
        whenever(keyguardStatusBarViewComponent.keyguardStatusBarViewController)
            .thenReturn(keyguardStatusBarViewController)
        whenever(kosmos.authController.scaleFactor).thenReturn(1.5f)
    }

    private val lockScreenContent =
        with(kosmos) {
            LockscreenContent(
                viewModelFactory = lockscreenContentViewModelFactory,
                lockscreenFrontScrimViewModelFactory = lockscreenFrontScrimViewModelFactory,
                lockscreenBehindScrimViewModelFactory = lockscreenBehindScrimViewModelFactory,
                lockscreenElements =
                    LockscreenElements(
                        builder =
                            object : LockscreenElementFactoryImpl.Builder {
                                override fun create(
                                    elements: Map<Key, BaseLockscreenElement>
                                ): LockscreenElementFactoryImpl {
                                    return LockscreenElementFactoryImpl(
                                        elements = elements,
                                        blueprintLog =
                                            LogBuffer(
                                                "Test",
                                                10,
                                                com.android.systemui.util.mockito.mock(),
                                            ),
                                    )
                                }
                            },
                        keyguardClockViewModel = keyguardClockViewModelWithImpl,
                        elementProviders = {
                            setOf(
                                lockscreenRootElementProvider,
                                statusBarElementProvider,
                                lockscreenUpperRegionElementProvider,
                                lockIconElementProvider,
                                ambientIndicationAreaProvider,
                                lockscreenLowerRegionElementProvider,
                                settingsMenuElementProvider,
                                clockRegionElementProvider,
                                defaultClockFaceLayout,
                            )
                        },
                        blueprintLog =
                            LogBuffer(
                                "LockscreenBuffer",
                                10,
                                com.android.systemui.util.mockito.mock(),
                            ),
                    ),
                clockInteractor = keyguardClockInteractorWithImpl,
                interactionJankMonitor = interactionJankMonitor,
            )
        }

    private val lockscreenScene =
        LockscreenScene(
            actionsViewModelFactory = lockscreenUserActionsViewModelFactory,
            lockscreenContent = { lockScreenContent },
        )

    private val bouncerUserActionsViewModelFactory =
        object : BouncerUserActionsViewModel.Factory {
            override fun create() = kosmos.bouncerUserActionsViewModel
        }

    private val bouncerOverlayContentViewModelFactory =
        object : BouncerOverlayContentViewModel.Factory {
            override fun create() = kosmos.bouncerOverlayContentViewModel
        }

    private val bouncerOverlay =
        BouncerOverlay(
            actionsViewModelFactory = bouncerUserActionsViewModelFactory,
            contentViewModelFactory = bouncerOverlayContentViewModelFactory,
            dialogFactory = kosmos.systemUIDialogDotFactory,
        )

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    @Ignore("b/489632440")
    fun swipeUpFromLockscreenToBouncer_recordBouncerOverlayElementsState() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true

            val motion =
                recordMotion(
                    content = { SceneContainerUnderTest() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionState.isIdle()
                                    }
                                }
                            ) {

                                // perform swipe up gesture to invoke LS->BouncerOverlay transition
                                performTouchInputAsync(onRoot()) { swipeUp(durationMillis = 500) }
                                awaitCondition { kosmos.sceneInteractor.transitionState.isIdle() }
                            }
                        ) {
                            featureOfElement(Bouncer.Elements.Background, elementAlpha)
                            feature(bouncerActionButtonTranslationY, Float.dataPointType)
                            feature(bouncerActionButtonAlpha, Float.dataPointType)
                            feature(bouncerContentAlpha, Float.dataPointType)
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Composable
    private fun SceneContainerUnderTest() {
        val vm =
            rememberViewModel("HomeScreenShadeTest") {
                kosmos.sceneContainerViewModelFactory.create {}
            }
        val bouncerSceneContainerState = getBouncerSceneContainerState(vm)
        val snapBouncer: (isShowing: Boolean) -> Unit = snapBouncer(bouncerSceneContainerState)
        val showOrHideBouncer:
            (
                transition: TransitionState.Transition.ShowOrHideOverlay,
                animationScope: CoroutineScope,
            ) -> Unit =
            showOrHideBouncer(bouncerSceneContainerState)
        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                ObserveReadsRoot {
                    BouncerSceneContainer(
                        viewModel = vm,
                        state = bouncerSceneContainerState,
                        bouncerOverlay = bouncerOverlay,
                        toBouncerTransitionViewModel = kosmos.toBouncerTransitionViewModel,
                    )
                    SceneContainer(
                        viewModel = vm,
                        sceneByKey = mapOf(Scenes.Lockscreen to lockscreenScene),
                        initialSceneKey = Scenes.Lockscreen,
                        transitionsBuilder = kosmos.sceneContainerTransitions,
                        overlayByKey = mapOf(Overlays.Bouncer to bouncerOverlay),
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                        sceneTransitionLatencyMonitor = kosmos.sceneTransitionLatencyMonitor,
                        onTransitionStart = { transition, animationScope ->
                            // If the transition that started is specifically meant to show or hide
                            // the bouncer overlay, that needs to be delegated out to the dedicated
                            // bouncer scene container external to this scene container.
                            if (
                                transition is TransitionState.Transition.ShowOrHideOverlay &&
                                    transition !is DelegatingTransition &&
                                    transition.isTransitioningFromOrTo(Overlays.Bouncer)
                            ) {
                                showOrHideBouncer(transition, animationScope)
                            }
                        },
                        onSnap = { idle ->
                            snapBouncer(idle.currentOverlays.contains(Overlays.Bouncer))
                        },
                    )
                }
            }
        }
    }

    private fun getBouncerSceneContainerState(
        vm: SceneContainerViewModel
    ): HoistedSceneTransitionLayoutState {
        return HoistedSceneTransitionLayoutState(
            initialScene = Scenes.Gone,
            onTransitionStart = onTransitionStart(vm),
            deferTransitionProgress = true,
        )
    }

    private fun showOrHideBouncer(
        bouncerSceneContainerState: HoistedSceneTransitionLayoutState
    ): (
        transition: TransitionState.Transition.ShowOrHideOverlay, animationScope: CoroutineScope,
    ) -> Unit {
        val showOrHideBouncer:
            (
                transition: TransitionState.Transition.ShowOrHideOverlay,
                animationScope: CoroutineScope,
            ) -> Unit =
            { transition, animationScope ->
                // This is invoked when the logic in the scene container wants
                // to show or hide the bouncer overlay. The transition is routed
                // to the dedicated bouncer scene container so it runs there and
                // even tracks the user drag/fling, if needed.
                bouncerSceneContainerState.uiBoundState?.startTransitionImmediately(
                    animationScope = animationScope,
                    transition =
                        DelegatingTransition.ShowOrHideOverlay(
                            delegate = transition,
                            fromOrToScene = bouncerSceneContainerState.currentScene,
                            overlay = Overlays.Bouncer,
                        ),
                )
            }
        return showOrHideBouncer
    }

    private fun snapBouncer(
        bouncerSceneContainerState: HoistedSceneTransitionLayoutState
    ): (isShowing: Boolean) -> Unit {
        val snapBouncer: (isShowing: Boolean) -> Unit = { isShowing ->
            // This is invoked when the logic in the scene container wants
            // to snap the bouncer overlay to show or to hide. The snapping
            // is done on the dedicated bouncer scene container so it shows
            // or hides as needed.
            val isBouncerCurrentlyShowing =
                bouncerSceneContainerState.currentOverlays.contains(Overlays.Bouncer)
            if (isShowing != isBouncerCurrentlyShowing) {
                bouncerSceneContainerState.uiBoundState?.snapTo(
                    overlays =
                        if (isShowing) {
                            setOf(Overlays.Bouncer)
                        } else {
                            emptySet()
                        }
                )
            }
        }
        return snapBouncer
    }

    private fun onTransitionStart(
        vm: SceneContainerViewModel
    ): (TransitionState.Transition) -> Unit = { transition ->
        // Here, we check if the transition that was started is
        // specifically meant to hide the bouncer overlay. If so, we
        // must also ask the real scene container to start a parallel
        // transition to hide the bouncer overlay from within itself.
        // While it's true that the real scene container doesn't render
        // the bouncer overlay (as that's actually handled by the
        // dedicated bouncer scene container - the one that uses this
        // state), it still needs to be logically hidden so both scene
        // containers remain in sync.
        if (
            transition is TransitionState.Transition.ShowOrHideOverlay &&
                transition.isTransitioning(from = Overlays.Bouncer)
        ) {
            vm.startTransitionImmediately(
                DelegatingTransition.ShowOrHideOverlay(
                    delegate = transition,
                    fromOrToScene = vm.currentScene,
                    overlay = Overlays.Bouncer,
                )
            )
        }
    }
}
