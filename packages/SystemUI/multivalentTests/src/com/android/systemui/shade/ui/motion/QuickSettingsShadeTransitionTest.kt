/*
 * Copyright (C) 2025 The Android Open Source Project
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.desktop.domain.interactor.enableUsingDesktopStatusBar
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.ui.composable.QuickSettingsShadeOverlay
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayActionsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsShadeOverlayContentViewModel
import com.android.systemui.qs.ui.viewmodel.quickSettingsContainerViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsShadeOverlayActionsViewModel
import com.android.systemui.qs.ui.viewmodel.quickSettingsShadeOverlayContentViewModelFactory
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.sceneContainerTransitions
import com.android.systemui.scene.sceneContainerViewModelFactory
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.sceneDataSourceDelegator
import com.android.systemui.scene.transitionState
import com.android.systemui.scene.ui.composable.GoneScene
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.scene.ui.view.sceneTransitionLatencyMonitor
import com.android.systemui.scene.ui.viewmodel.GoneUserActionsViewModel
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.ui.composable.OverlayShadeMotionTestKeys
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import org.junit.Rule
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import platform.test.motion.compose.ComposeFeatureCaptures
import platform.test.motion.compose.ComposeFeatureCaptures.size
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
@EnableFlags(Flags.FLAG_DUAL_SHADE)
class QuickSettingsShadeTransitionTest() : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val deviceSpec = DeviceEmulationSpec(Phone)

    @get:Rule val motionTestRule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    private val quickSettingsShadeOverlayActionsViewModelFactory =
        object : QuickSettingsShadeOverlayActionsViewModel.Factory {
            override fun create() = kosmos.quickSettingsShadeOverlayActionsViewModel
        }
    private val quickSettingsShadeOverlayContentViewModelFactory =
        object : QuickSettingsShadeOverlayContentViewModel.Factory {
            override fun create(
                volumeSliderCoroutineScope: CoroutineScope?
            ): QuickSettingsShadeOverlayContentViewModel =
                kosmos.quickSettingsShadeOverlayContentViewModelFactory.create()
        }

    private val notificationScrollView = Mockito.mock(NotificationScrollView::class.java)

    private val quickSettingsShadeOverlay =
        QuickSettingsShadeOverlay(
            actionsViewModelFactory = quickSettingsShadeOverlayActionsViewModelFactory,
            contentViewModelFactory = quickSettingsShadeOverlayContentViewModelFactory,
            quickSettingsContainerViewModelFactory = kosmos.quickSettingsContainerViewModelFactory,
            notificationStackScrollView = { notificationScrollView },
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
        )

    private val goneScene =
        GoneScene(
            notificationStackScrollView = { notificationScrollView },
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
    @EnableSceneContainer
    fun swipeUpOnQSShadeClosesQSShade() {
        allowTestableLooperAsMainThread()
        motionTestRule.runTest(60.seconds) {
            kosmos.enableDualShade(wideLayout = false)
            kosmos.usingMediaInComposeFragment = true
            kosmos.populateQuickSettings(tileCount = 1)
            kosmos.enableUsingDesktopStatusBar()
            val motion =
                recordMotion(
                    content = { TestSceneContainer() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    runOnMainThreadAndWaitForIdleSync {
                                        kosmos.sceneInteractor.instantlyShowOverlay(
                                            Overlays.QuickSettingsShade,
                                            "testing",
                                        )
                                    }

                                    // TODO replace with awaitIdle(b/480861333)
                                    awaitFrames(1)
                                }
                            ) {
                                performTouchInputAsync(onRoot()) {
                                    swipe(
                                        start = Offset(x = (centerX + right) / 2, y = centerY / 3),
                                        end = Offset(x = (centerX + right) / 2, y = 0f),
                                    )
                                }
                            }
                        ) {
                            // No values are captured as this test only verifies the final state
                            // after the swipe gesture.
                        },
                )
            assert(kosmos.sceneInteractor.currentScene.value == Scenes.Gone)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_NOTIFICATION_SHADE_BLUR)
    fun goneSceneToQuickSettingsShadeOverlayTest() {

        motionTestRule.runTest(60.seconds) {
            kosmos.enableDualShade(wideLayout = false)
            kosmos.usingMediaInComposeFragment = true
            kosmos.populateQuickSettings(tileCount = 7)
            kosmos.enableUsingDesktopStatusBar()
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            val motion =
                recordMotion(
                    content = { TestSceneContainer() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition { kosmos.transitionState.value.isIdle() }
                                }
                            ) {
                                // perform swipe down gesture from top right half
                                performTouchInputAsync(onRoot()) {
                                    swipe(
                                        start = Offset(x = (centerX + right) / 2, y = 0f),
                                        end = Offset(x = (centerX + right) / 2, y = centerY / 2),
                                        durationMillis = 500,
                                    )
                                }
                                awaitCondition { kosmos.transitionState.value.isIdle() }
                            }
                        ) {
                            feature(
                                hasTestTag(resIdToTestTag("quick_settings_container")),
                                size,
                                "quick_settings_container_size",
                            )
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    @EnableSceneContainer
    @EnableFlags(Flags.FLAG_NOTIFICATION_SHADE_BLUR)
    fun recordScrimAlpha_duringSwipeDownToQSShadeOverlay() {

        motionTestRule.runTest(60.seconds) {
            kosmos.enableDualShade(wideLayout = false)
            kosmos.usingMediaInComposeFragment = true
            kosmos.populateQuickSettings(tileCount = 7)
            kosmos.enableUsingDesktopStatusBar()
            kosmos.fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            val motion =
                recordMotion(
                    content = { TestSceneContainer() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition { kosmos.transitionState.value.isIdle() }
                                }
                            ) {
                                // perform swipe down gesture from top right half
                                performTouchInputAsync(onRoot()) {
                                    swipe(
                                        start = Offset(x = (centerX + right) / 2, y = 0f),
                                        end = Offset(x = (centerX + right) / 2, y = centerY / 2),
                                        durationMillis = 500,
                                    )
                                }
                            }
                        ) {
                            feature(OverlayShadeMotionTestKeys.scrimAlpha, Float.dataPointType)
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    @EnableSceneContainer
    fun recordEditIconButtonPosition_duringSwipeDownToOpenQS() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableDualShade(wideLayout = false)
            kosmos.usingMediaInComposeFragment = true
            kosmos.populateQuickSettings(tileCount = 1)
            kosmos.enableUsingDesktopStatusBar()
            val motion =
                recordMotion(
                    content = { TestSceneContainer() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition { kosmos.transitionState.value.isIdle() }
                                }
                            ) {
                                // perform swipe down gesture from top right half
                                performTouchInputAsync(onRoot()) {
                                    swipe(
                                        start = Offset(x = (centerX + right) / 2, y = 0f),
                                        end = Offset(x = (centerX + right) / 2, y = centerY / 3),
                                        durationMillis = 500,
                                    )
                                }
                                awaitCondition { kosmos.transitionState.value.isIdle() }
                            }
                        ) {
                            feature(
                                hasTestTag(resIdToTestTag("qs_edit_mode_button")),
                                ComposeFeatureCaptures.positionInRoot,
                                "qs_edit_mode_button_position",
                            )
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Composable
    private fun TestSceneContainer() {
        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                val vm =
                    rememberViewModel("HomeScreenShadeTest") {
                        kosmos.sceneContainerViewModelFactory.create {}
                    }

                ObserveReadsRoot {
                    SceneContainer(
                        viewModel = vm,
                        sceneByKey = mapOf(Scenes.Gone to goneScene),
                        initialSceneKey = Scenes.Gone,
                        transitionsBuilder = kosmos.sceneContainerTransitions,
                        overlayByKey =
                            mapOf(Overlays.QuickSettingsShade to quickSettingsShadeOverlay),
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
