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

import android.content.res.mainResources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.MotionTest
import android.testing.TestableLooper.RunWithLooper
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.animation.scene.FeatureCaptures.elementAlpha
import com.android.compose.animation.scene.Key
import com.android.compose.animation.scene.featureOfElement
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.keyguard.dagger.KeyguardStatusBarViewComponent
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_NOTIFICATION_SHADE_BLUR
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.biometrics.authController
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
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.sceneContainerTransitions
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.GoneScene
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.scene.ui.view.sceneTransitionLatencyMonitor
import com.android.systemui.scene.ui.viewmodel.GoneUserActionsViewModel
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.KeyguardStatusBarViewController
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
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

@Ignore("b/496045082")
@RunWith(AndroidJUnit4::class)
@MotionTest
@LargeTest
@RunWithLooper
@EnableSceneContainer
class LockScreenToGoneSceneTransitionTest : SysuiTestCase() {
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
                                        blueprintLog = logcatLogBuffer("Test"),
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
                        blueprintLog = logcatLogBuffer("LockscreenBuffer"),
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

    private val goneScene =
        GoneScene(
            notificationStackScrollView = { kosmos.notificationScrollView },
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            viewModelFactory =
                object : GoneUserActionsViewModel.Factory {
                    override fun create(): GoneUserActionsViewModel {
                        return GoneUserActionsViewModel(
                            kosmos.shadeModeInteractor,
                            kosmos.mainResources,
                        )
                    }
                },
        )

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun swipeUpFromLockScreenToGoneScene_recordingLockScreenAlpha() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            kosmos.fakeAuthenticationRepository.setAuthenticationMethod(
                AuthenticationMethodModel.None
            )
            val motion =
                recordMotion(
                    content = { LockscreenToGoneSceneHost() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                    }
                                }
                            ) {
                                performTouchInputAsync(onRoot()) { swipeUp(endY = centerY) }
                            }
                        ) {
                            featureOfElement(LockscreenElementKeys.Region.Upper, elementAlpha)
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Composable
    private fun LockscreenToGoneSceneHost() {
        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                val vm =
                    rememberViewModel("HomeScreenShadeTest") {
                        kosmos.sceneContainerViewModelFactory.create {}
                    }
                ObserveReadsRoot {
                    SceneContainer(
                        viewModel = vm,
                        sceneByKey =
                            mapOf(Scenes.Lockscreen to lockscreenScene, Scenes.Gone to goneScene),
                        initialSceneKey = Scenes.Lockscreen,
                        transitionsBuilder = kosmos.sceneContainerTransitions,
                        overlayByKey = mapOf(),
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                        sceneTransitionLatencyMonitor = kosmos.sceneTransitionLatencyMonitor,
                        onTransitionStart = { _, _ -> },
                        onSnap = {},
                    )
                }
            }
        }
    }
}
