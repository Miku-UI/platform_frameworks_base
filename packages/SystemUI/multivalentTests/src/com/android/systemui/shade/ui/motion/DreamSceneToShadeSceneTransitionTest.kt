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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.dream.ui.composable.DreamScene
import com.android.systemui.dreams.ui.viewmodel.dreamUserActionsViewModelFactory
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesParentViewModelFactory
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
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
import com.android.systemui.shade.ui.composable.ShadeScene
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.shade.ui.viewmodel.shadeSceneContentViewModelFactory
import com.android.systemui.shade.ui.viewmodel.shadeUserActionsViewModelFactory
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeFeatureCaptures.positionInRoot
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.feature
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
class DreamSceneToShadeSceneTransitionTest : SysuiTestCase() {
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

    private val dreamScene =
        DreamScene(actionsViewModelFactory = kosmos.dreamUserActionsViewModelFactory)

    private val shadeScene =
        ShadeScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { kosmos.notificationScrollView },
            actionsViewModelFactory = kosmos.shadeUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.shadeSceneContentViewModelFactory,
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            jankMonitor = kosmos.interactionJankMonitor,
            notificationRulesParentViewModelFactory = kosmos.notificationRulesParentViewModelFactory,
        )

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
    fun swipeDownFromDreamToShadeScene_recordingQSPanelPosition() {
        motionTestRule.runTest(60.seconds) {
            kosmos.enableSingleShade()
            kosmos.usingMediaInComposeFragment = true
            kosmos.populateQuickSettings(tileCount = 4)
            val motion =
                recordMotion(
                    content = { DreamSceneToShadeSceneSceneContainer() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionState.isIdle()
                                    }
                                }
                            ) {
                                performTouchInputAsync(onRoot()) { swipeDown(startY = centerY) }
                                awaitCondition { kosmos.sceneInteractor.transitionState.isIdle() }
                            }
                        ) {
                            feature(
                                hasTestTag(resIdToTestTag("quick_qs_panel")),
                                positionInRoot,
                                "quick_qs_panel",
                                useUnmergedTree = true,
                            )
                        },
                )
            assertThat(motion).timeSeriesMatchesGolden()
        }
    }

    @Composable
    private fun DreamSceneToShadeSceneSceneContainer() {

        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                val vm =
                    rememberViewModel("HomeScreenShadeTest") {
                        kosmos.sceneContainerViewModelFactory.create() {}
                    }
                ObserveReadsRoot {
                    SceneContainer(
                        viewModel = vm,
                        sceneByKey = mapOf(Scenes.Dream to dreamScene, Scenes.Shade to shadeScene),
                        initialSceneKey = Scenes.Dream,
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
