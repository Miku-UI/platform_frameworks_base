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

package com.android.systemui.notifications.ui

import android.content.res.mainResources
import android.platform.test.annotations.MotionTest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.compose.animation.scene.isElement
import com.android.compose.snapshot.ObserveReadsRoot
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.fakeActiveMedia
import com.android.systemui.media.remedia.data.repository.fakeMediaRepository
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.motion.createSysUiComposeMotionTestRule
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesParentViewModelFactory
import com.android.systemui.notifications.ui.composable.Notifications.Elements.NotificationScrim
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.footer.domain.interactor.FakeFooterActionInteractor
import com.android.systemui.qs.footerActionsInteractor
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
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
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationScrollViewModel
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.cancelChildren
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.motion.compose.ComposeFeatureCaptures
import platform.test.motion.compose.ComposeRecordingSpec
import platform.test.motion.compose.MotionControl
import platform.test.motion.compose.feature
import platform.test.motion.compose.recordMotion
import platform.test.motion.compose.runTest
import platform.test.motion.golden.asDataPoint
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays.Phone

/**
 * Integration test for the Notification Stack Placeholder within the Single Shade.
 *
 * This test creates a functional [SceneContainer] with [GoneScene] and [ShadeScene], backed by
 * [testKosmos] fakes. It performs touch gestures and records data points:
 * 1. The actual on-screen position and bounds of the [NotificationScrim] UI element.
 * 2. The stack placeholder's position reported to its view models.
 */
@RunWith(AndroidJUnit4::class)
@MotionTest
@LargeTest
@EnableSceneContainer
class StackPlaceholderInSingleShadeIntegrationTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply {
            // Fake this interactor to avoid using a TestableLooper.
            footerActionsInteractor = FakeFooterActionInteractor()
        }
    private val deviceSpec = DeviceEmulationSpec(Phone)
    @get:Rule val rule = createSysUiComposeMotionTestRule(kosmos, deviceSpec)

    private val disposableHandles = mutableListOf<DisposableHandle>()

    @Before
    fun setUp() {
        with(kosmos) {
            enableSingleShade()
            usingMediaInComposeFragment = true
            currentTilesInteractor.setTiles(List(4) { i -> TileSpec.create("Tile#$i") })
        }
    }

    fun setupMediaForShade() {
        kosmos.fakeMediaRepository.currentMedia = mutableStateListOf(kosmos.fakeActiveMedia)
        val userMedia = MediaData(active = true)
        kosmos.mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)
    }

    @After
    fun tearDown() {
        kosmos.testScope.coroutineContext.cancelChildren()
        disposableHandles.forEach { it.dispose() }
    }

    @Test
    fun swipeDownToOpenSingleShade_recordPlaceholderPosition() {
        assertShadeMotion { performSwipe() }
    }

    @Test
    fun swipeDownToOpenSingleShadeWithMedia_recordPlaceholderPosition() {
        setupMediaForShade()
        assertShadeMotion { performSwipe() }
    }

    /**
     * Swipe down across the whole screen with an ease-in, ease-out curve, that decelerates to zero
     * velocity, resulting in a very little overscroll.
     */
    private fun TouchInjectionScope.performSwipe() {
        swipe(
            curve = {
                val progress = it / 200f
                val easedProgress = (1 - cos(progress * PI).toFloat()) / 2f
                Offset(centerX, lerp(top, bottom, easedProgress))
            },
            durationMillis = 200,
        )
    }

    @Test
    fun flingDownToOpenSingleShade_recordPlaceholderPosition() {
        assertShadeMotion { performSwipeWithVelocity() }
    }

    @Test
    fun flingDownToOpenSingleShadeWithMedia_recordPlaceholderPosition() {
        setupMediaForShade()
        assertShadeMotion { performSwipeWithVelocity() }
    }

    /** Fling down half-screen with high exit velocity to trigger an overscroll. */
    private fun TouchInjectionScope.performSwipeWithVelocity() {
        swipeWithVelocity(
            start = topCenter,
            end = center,
            durationMillis = 100,
            endVelocity = 6000.dp.toPx(),
        )
    }

    private fun assertShadeMotion(performGesture: TouchInjectionScope.() -> Unit) =
        rule.runTest(60.seconds) {
            // 1. Capture the placeholder's position reported upstream.
            var stackScrollTop = Float.NaN
            var stackBounds = YSpace(Float.NaN, Float.NaN)
            disposableHandles.add(
                kosmos.notificationScrollViewModel.stackScrollTop.observe { value ->
                    stackScrollTop = value
                }
            )

            disposableHandles.add(
                kosmos.notificationScrollViewModel.stackBounds.observe { value ->
                    stackBounds = value
                }
            )

            // 2. Record motion.
            val motion =
                recordMotion(
                    content = { SceneContainerUnderTest() },
                    recordingSpec =
                        ComposeRecordingSpec(
                            MotionControl(
                                delayRecording = {
                                    awaitCondition {
                                        kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                    }
                                }
                            ) {
                                performTouchInputAsync(onRoot()) { performGesture() }
                                awaitCondition {
                                    kosmos.sceneInteractor.transitionStateFlow.value.isIdle()
                                }
                            },
                            recordBefore = false,
                            recordAfter = false,
                        ) {
                            feature(
                                isElement(NotificationScrim),
                                ComposeFeatureCaptures.positionInRoot,
                                "scrim_position",
                            )
                            feature(
                                isElement(NotificationScrim),
                                ComposeFeatureCaptures.size,
                                "scrim_size",
                            )
                            feature(name = "stack_scroll_top") { stackScrollTop.asDataPoint() }
                            feature(name = "stack_bounds") { stackBounds.asDataPoint() }
                        },
                )

            // 3. Verify.
            assertThat(motion).timeSeriesMatchesGolden()
        }

    @Composable
    private fun SceneContainerUnderTest() {
        PlatformTheme {
            WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                val viewModel =
                    rememberViewModel("KosmosSceneContainerVM") {
                        kosmos.sceneContainerViewModelFactory.create {}
                    }

                val goneScene = createGoneScene()
                val shadeScene = createShadeScene()

                ObserveReadsRoot {
                    SceneContainer(
                        viewModel = viewModel,
                        sceneByKey = mapOf(Scenes.Gone to goneScene, Scenes.Shade to shadeScene),
                        initialSceneKey = Scenes.Gone,
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

    @Composable
    private fun createGoneScene(): GoneScene {
        return GoneScene(
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
    }

    @Composable
    private fun createShadeScene(): ShadeScene {
        val shadeSession =
            object : SaveableSession, Session by Session(SessionStorage()) {
                @Composable
                override fun <T : Any> rememberSaveableSession(
                    vararg inputs: Any?,
                    saver: Saver<T, out Any>,
                    key: String?,
                    init: () -> T,
                ): T = rememberSession(key, inputs = inputs, init = init)
            }

        return ShadeScene(
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
    }
}
