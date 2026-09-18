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
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.animation.scene.FeatureCaptures.elementAlpha
import com.android.compose.animation.scene.featureOfElement
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.keyguard.dagger.KeyguardStatusBarViewComponent
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.biometrics.authController
import com.android.systemui.bouncer.ui.composable.Bouncer
import com.android.systemui.bouncer.ui.composable.BouncerOverlay
import com.android.systemui.bouncer.ui.composable.BouncerSceneContainer
import com.android.systemui.bouncer.ui.viewmodel.BouncerOverlayContentViewModel
import com.android.systemui.bouncer.ui.viewmodel.BouncerUserActionsViewModel
import com.android.systemui.bouncer.ui.viewmodel.bouncerOverlayContentViewModel
import com.android.systemui.bouncer.ui.viewmodel.bouncerUserActionsViewModel
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.ui.composable.LockscreenContent
import com.android.systemui.keyguard.ui.composable.LockscreenScene
import com.android.systemui.keyguard.ui.lockscreen.content.lockscreenContent
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.keyguardStatusBarViewComponentFactory
import com.android.systemui.keyguard.ui.viewmodel.LockscreenUserActionsViewModel
import com.android.systemui.keyguard.ui.viewmodel.lockscreenUserActionsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.sceneContainerTransitions
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.BouncerSceneTransitionCoordinator
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.scene.ui.view.sceneTransitionLatencyMonitor
import com.android.systemui.scene.ui.viewmodel.toBouncerTransitionViewModel
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.phone.KeyguardStatusBarViewController
import com.android.systemui.statusbar.phone.systemUIDialogDotFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import kotlin.time.Duration.Companion.seconds
import org.junit.Before
import org.junit.Rule
import org.junit.Ignore
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

@Ignore("b/496045082")
@RunWith(AndroidJUnit4::class)
@MotionTest
@LargeTest
@RunWithLooper
@EnableSceneContainer
class BouncerOverlayToLockScreenTransitionTest : SysuiTestCase() {
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

        // with mock, the default value of scaleFactor is 0.
        // this affects the size of lockscreen clock and lock icon.
        // setting this to positive value ensures these are visible.
        whenever(kosmos.authController.scaleFactor).thenReturn(1.5f)
    }

    private val lockscreenScene =
        LockscreenScene(
            actionsViewModelFactory = lockscreenUserActionsViewModelFactory,
            lockscreenContent = { kosmos.lockscreenContent },
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
    fun swipeDownFromBouncerToLockscreen_recordingBouncerAndLockScreenContentAlpha() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.Pin
            )

            val motion =
                recordMotion(
                    content = { SceneContainerUnderTest() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    motionTestRule.toolkit.composeContentTestRule.runOnUiThread {
                                        kosmos.sceneInteractor.instantlyShowOverlay(
                                            Overlays.Bouncer,
                                            loggingReason = "Testing Bouncer to Lockscreen",
                                        )
                                    }
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionState.isIdle(
                                            Overlays.Bouncer
                                        )
                                    }
                                    awaitIdle()
                                }
                            ) {
                                // perform swipe down gesture to invoke BouncerOverlay->LS
                                // transition
                                performTouchInputAsync(onRoot()) { swipeDown(durationMillis = 500) }
                                awaitCondition {
                                    with(kosmos.sceneInteractor.transitionState) {
                                        isIdle(Scenes.Lockscreen) && currentOverlays.isEmpty()
                                    }
                                }
                            }
                        ) {
                            featureOfElement(Bouncer.Elements.Background, elementAlpha)
                            feature(
                                LockscreenContent.LockscreenContentMotionTestKeys.Alpha,
                                Float.dataPointType,
                            )
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Composable
    private fun SceneContainerUnderTest() {
        val vm =
            rememberViewModel("BouncerOverlayToLockScreenTransitionTest") {
                kosmos.sceneContainerViewModelFactory.create {}
            }
        val coordinator = remember { BouncerSceneTransitionCoordinator(vm) }

        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                ObserveReadsRoot {
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
                            coordinator.onMainContainerTransitionStart(transition, animationScope)
                        },
                        onSnap = { idle ->
                            coordinator.onMainContainerSnap(
                                idle.currentOverlays.contains(Overlays.Bouncer)
                            )
                        },
                    )
                    BouncerSceneContainer(
                        viewModel = vm,
                        state = coordinator.bouncerSceneContainerState,
                        bouncerOverlay = bouncerOverlay,
                        toBouncerTransitionViewModel = kosmos.toBouncerTransitionViewModel,
                    )
                }
            }
        }
    }
}
