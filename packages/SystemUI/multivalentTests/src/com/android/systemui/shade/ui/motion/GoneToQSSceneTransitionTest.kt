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
import android.platform.test.annotations.MotionTest
import android.testing.TestableLooper.RunWithLooper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.test.multiTouchSwipe
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesParentViewModelFactory
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
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
import com.android.systemui.scene.ui.composable.GoneScene
import com.android.systemui.scene.ui.composable.SceneContainer
import com.android.systemui.scene.ui.view.sceneJankMonitorFactory
import com.android.systemui.scene.ui.view.sceneTransitionLatencyMonitor
import com.android.systemui.scene.ui.viewmodel.GoneUserActionsViewModel
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.ui.composable.ShadeScene
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.shade.ui.viewmodel.shadeSceneContentViewModelFactory
import com.android.systemui.shade.ui.viewmodel.shadeUserActionsViewModelFactory
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
class GoneToQSSceneTransitionTest : SysuiTestCase() {

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

    private val shadeScene =
        ShadeScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { kosmos.notificationScrollView },
            actionsViewModelFactory = kosmos.shadeUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.shadeSceneContentViewModelFactory,
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            notificationRulesParentViewModelFactory =
                kosmos.notificationRulesParentViewModelFactory,
            jankMonitor = kosmos.interactionJankMonitor,
        )

    @Test
    @Ignore
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    fun multiSwipeDownFromGoneToQSScene_recordingQSContentYCoordinate() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.usingMediaInComposeFragment = true
            kosmos.populateQuickSettings(tileCount = 10)

            val durationMillis = 500L
            val motion =
                recordMotion(
                    content = { GoneSceneToQuickSettingsSceneContainer() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                    }
                                }
                            ) {
                                performTouchInputAsync(onRoot()) {
                                    multiTouchSwipe(
                                        listOf(
                                            {
                                                lerp(
                                                    Offset(x = centerX / 2, y = top),
                                                    Offset(x = centerX / 2, y = bottom),
                                                    it / durationMillis.toFloat(),
                                                )
                                            },
                                            {
                                                lerp(
                                                    Offset(x = right * 0.75f, y = top),
                                                    Offset(x = right * 0.75f, y = bottom),
                                                    it / durationMillis.toFloat(),
                                                )
                                            },
                                        ),
                                        durationMillis = durationMillis,
                                    )
                                }
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

    @Test
    @Ignore
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    fun multiSwipeDownFromGoneNotCrossingThreshold_recordingQSContentYCoordinate() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.usingMediaInComposeFragment = true
            kosmos.populateQuickSettings(tileCount = 10)

            val durationMillis = 500L
            val motion =
                recordMotion(
                    content = { GoneSceneToQuickSettingsSceneContainer() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                    }
                                }
                            ) {
                                performTouchInputAsync(onRoot()) {
                                    multiTouchSwipe(
                                        listOf(
                                            {
                                                lerp(
                                                    Offset(x = centerX / 2, y = top),
                                                    Offset(x = centerX / 2, y = height * 0.075f),
                                                    it / durationMillis.toFloat(),
                                                )
                                            },
                                            {
                                                lerp(
                                                    Offset(x = right * 0.75f, y = top),
                                                    Offset(x = right * 0.75f, y = height * 0.075f),
                                                    it / durationMillis.toFloat(),
                                                )
                                            },
                                        ),
                                        durationMillis = durationMillis,
                                    )
                                }
                                awaitCondition {
                                    kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                }
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
    private fun GoneSceneToQuickSettingsSceneContainer() {

        val transitionState =
            MutableStateFlow<ObservableTransitionState>(ObservableTransitionState.Idle(Scenes.Gone))

        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                val vm =
                    rememberViewModel("HomeScreenShadeTest") {
                        kosmos.sceneContainerViewModelFactory
                            .create() {}
                            .apply { setTransitionState(transitionState = transitionState) }
                    }

                ObserveReadsRoot {
                    SceneContainer(
                        viewModel = vm,
                        sceneByKey =
                            mapOf(
                                Scenes.Gone to goneScene,
                                Scenes.QuickSettings to quickSettingsScene,
                                Scenes.Shade to shadeScene,
                            ),
                        initialSceneKey = Scenes.Gone,
                        transitionsBuilder = kosmos.sceneContainerTransitions,
                        overlayByKey = mapOf(),
                        onTransitionStart = { _, _ -> },
                        onSnap = {},
                        dataSourceDelegator = kosmos.sceneDataSourceDelegator,
                        sceneJankMonitorFactory = kosmos.sceneJankMonitorFactory,
                        sceneTransitionLatencyMonitor = kosmos.sceneTransitionLatencyMonitor,
                    )
                }
            }
        }
    }
}
