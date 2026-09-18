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
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.MotionTest
import android.testing.TestableLooper.RunWithLooper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.keyguard.dagger.KeyguardStatusBarViewComponent
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_NOTIFICATION_SHADE_BLUR
import com.android.systemui.SysuiTestCase
import com.android.systemui.biometrics.authController
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.ui.composable.LockscreenScene
import com.android.systemui.keyguard.ui.lockscreen.content.lockscreenContent
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.keyguardStatusBarViewComponentFactory
import com.android.systemui.keyguard.ui.viewmodel.LockscreenUserActionsViewModel
import com.android.systemui.keyguard.ui.viewmodel.lockscreenUserActionsViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesParentViewModelFactory
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.ui.composable.QuickSettingsScene
import com.android.systemui.qs.ui.viewmodel.quickSettingsSceneContentViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsUserActionsViewModelFactory
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.sceneContainerTransitions
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.scene.ui.view.sceneTransitionLatencyMonitor
import com.android.systemui.shade.domain.interactor.enableSingleShade
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
import platform.test.motion.golden.asDataPoint
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

@Ignore("b/496045082")
@RunWith(AndroidJUnit4::class)
@MotionTest
@LargeTest
@RunWithLooper
@EnableSceneContainer
class QuickSettingsSceneToLockScreenTransition : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val deviceSpec = DeviceEmulationSpec(Phone)
    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    private val shadeSession =
        object : SaveableSession, Session by Session(SessionStorage()) {
            @Composable
            override fun <T : Any> rememberSaveableSession(
                vararg inputs: Any?,
                saver: Saver<T, out Any>,
                key: String?,
                init: () -> T,
            ): T = rememberSession(key, inputs = inputs, init = init)
        }

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

    private val quickSettingsScene =
        QuickSettingsScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { kosmos.notificationScrollView },
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            actionsViewModelFactory = kosmos.quickSettingsUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.quickSettingsSceneContentViewModelFactory,
            notificationRulesParentViewModelFactory =
                kosmos.notificationRulesParentViewModelFactory,
            jankMonitor = kosmos.interactionJankMonitor,
        )

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun swipeUpFromQuickSettingsSceneToLockScreen_recordingQSPanelYCoordinate() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            kosmos.populateQuickSettings(tileCount = 20)
            val motion =
                recordMotion(
                    content = { QuickSettingsSceneToLockScreenHost() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                    }
                                }
                            ) {
                                performTouchInputAsync(onRoot()) { swipeUp(durationMillis = 500) }
                            }
                        ) {
                            val qsPanelYCoordinate =
                                motionTestRule.toolkit.composeContentTestRule
                                    .onNodeWithTag(Elements.QuickSettingsContent.testTag)
                                    .fetchSemanticsNode()
                                    .positionInRoot
                                    .y
                            feature("qs_panel_position.y") { qsPanelYCoordinate.asDataPoint() }
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Composable
    private fun QuickSettingsSceneToLockScreenHost() {
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
                            mapOf(
                                Scenes.Lockscreen to lockscreenScene,
                                Scenes.QuickSettings to quickSettingsScene,
                            ),
                        initialSceneKey = Scenes.QuickSettings,
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
