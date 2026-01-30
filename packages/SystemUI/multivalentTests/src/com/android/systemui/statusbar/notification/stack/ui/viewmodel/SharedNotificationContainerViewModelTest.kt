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
 *
 */

package com.android.systemui.statusbar.notification.stack.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags.FLAG_GESTURE_BETWEEN_HUB_AND_LOCKSCREEN_MOTION
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_V2
import com.android.systemui.Flags.FLAG_LOCKSCREEN_SHADE_TO_DREAM_TRANSITION_FIX
import com.android.systemui.Flags.FLAG_STATUS_BAR_FOR_DESKTOP
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.keyguardBouncerRepository
import com.android.systemui.common.shared.model.NotificationContainerBounds
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.communal.data.repository.communalSceneRepository
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.desktop.domain.interactor.enableUsingDesktopStatusBar
import com.android.systemui.flags.BrokenWithSceneContainer
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.flags.parameterizeSceneContainerFlag
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.shared.model.BurnInModel
import com.android.systemui.keyguard.shared.model.KeyguardState.ALTERNATE_BOUNCER
import com.android.systemui.keyguard.shared.model.KeyguardState.AOD
import com.android.systemui.keyguard.shared.model.KeyguardState.DOZING
import com.android.systemui.keyguard.shared.model.KeyguardState.DREAMING
import com.android.systemui.keyguard.shared.model.KeyguardState.GLANCEABLE_HUB
import com.android.systemui.keyguard.shared.model.KeyguardState.GONE
import com.android.systemui.keyguard.shared.model.KeyguardState.LOCKSCREEN
import com.android.systemui.keyguard.shared.model.KeyguardState.OCCLUDED
import com.android.systemui.keyguard.shared.model.KeyguardState.PRIMARY_BOUNCER
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.keyguard.ui.viewmodel.AodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.ViewStateAccessor
import com.android.systemui.keyguard.ui.viewmodel.aodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.keyguardRootViewModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.media.controls.domain.pipeline.legacyMediaDataManagerImpl
import com.android.systemui.res.R
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.largeScreenHeaderHelper
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.setActiveNotifs
import com.android.systemui.statusbar.notification.stack.domain.interactor.sharedNotificationContainerInteractor
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel.Companion.PUSHBACK_SCALE
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.SharedNotificationContainerViewModel.HorizontalPosition
import com.android.systemui.testKosmos
import com.android.systemui.window.ui.viewmodel.fakeBouncerTransitions
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class SharedNotificationContainerViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return parameterizeSceneContainerFlag()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val aodBurnInViewModel = mock(AodBurnInViewModel::class.java)
    private lateinit var movementFlow: MutableStateFlow<BurnInModel>

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            fakeFeatureFlagsClassic.apply { set(Flags.FULL_SCREEN_USER_SWITCHER, false) }
        }

    init {
        kosmos.aodBurnInViewModel = aodBurnInViewModel
    }

    private val keyguardTransitionRepository by lazy { kosmos.fakeKeyguardTransitionRepository }

    private val Kosmos.underTest: SharedNotificationContainerViewModel by
        Kosmos.Fixture { sharedNotificationContainerViewModel }

    @Before
    fun setUp() {
        kosmos.enableSingleShade()
        movementFlow = MutableStateFlow(BurnInModel())
        whenever(aodBurnInViewModel.movement).thenReturn(movementFlow)
    }

    @Test
    fun validateMarginStart_splitShade() =
        kosmos.runTest {
            enableSplitShade()
            overrideDimensionPixelSize(R.dimen.notification_panel_margin_horizontal, 20)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(dimens!!.marginStart).isEqualTo(0)
        }

    @Test
    fun validateMarginStart() =
        kosmos.runTest {
            enableSingleShade()
            overrideDimensionPixelSize(R.dimen.notification_panel_margin_horizontal, 20)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(dimens!!.marginStart).isEqualTo(20)
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_STATUS_BAR_FOR_DESKTOP)
    fun validateMarginStart_dualShade_notificationShadeEndAligned() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)
            enableUsingDesktopStatusBar()
            enableDualShade(wideLayout = true)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(checkNotNull(dimens).marginStart).isEqualTo(0)
        }

    @Test
    @DisableSceneContainer
    fun validateHorizontalPosition_singleShade() =
        kosmos.runTest {
            enableSingleShade()
            overrideDimensionPixelSize(R.dimen.shade_panel_width, 200)
            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            val horizontalPosition = checkNotNull(dimens).horizontalPosition
            assertIs<HorizontalPosition.EdgeToEdge>(horizontalPosition)
        }

    @Test
    @DisableSceneContainer
    fun validateHorizontalPosition_splitShade() =
        kosmos.runTest {
            enableSplitShade()
            overrideDimensionPixelSize(R.dimen.shade_panel_width, 200)
            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            val horizontalPosition = checkNotNull(dimens).horizontalPosition
            assertIs<HorizontalPosition.MiddleToEdge>(horizontalPosition)
        }

    @Test
    @EnableSceneContainer
    fun validateHorizontalPosition_sceneContainer_singleShade() =
        kosmos.runTest {
            enableSingleShade()
            overrideDimensionPixelSize(R.dimen.shade_panel_width, 200)
            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            val horizontalPosition = checkNotNull(dimens).horizontalPosition
            assertIs<HorizontalPosition.EdgeToEdge>(horizontalPosition)
        }

    @Test
    @EnableSceneContainer
    fun validateHorizontalPosition_sceneContainer_splitShade() =
        kosmos.runTest {
            enableSplitShade()
            overrideDimensionPixelSize(R.dimen.shade_panel_width, 200)
            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            val horizontalPosition = checkNotNull(dimens).horizontalPosition
            assertIs<HorizontalPosition.MiddleToEdge>(horizontalPosition)
        }

    @Test
    @EnableSceneContainer
    fun validateHorizontalPosition_dualShade_narrowLayout() =
        kosmos.runTest {
            enableDualShade(wideLayout = false)
            overrideDimensionPixelSize(R.dimen.shade_panel_width, 200)
            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            val horizontalPosition = checkNotNull(dimens).horizontalPosition
            assertIs<HorizontalPosition.EdgeToEdge>(horizontalPosition)
        }

    @Test
    @EnableSceneContainer
    fun validateHorizontalPosition_dualShade_wideLayout() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)
            overrideDimensionPixelSize(R.dimen.shade_panel_width, 200)
            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            val horizontalPosition = checkNotNull(dimens).horizontalPosition
            assertIs<HorizontalPosition.EdgeToMiddle>(horizontalPosition)
            assertThat(horizontalPosition.maxWidth).isEqualTo(200)
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_STATUS_BAR_FOR_DESKTOP)
    fun validateHorizontalPosition_dualShade_notificationShadeEndAligned() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)
            overrideDimensionPixelSize(R.dimen.shade_panel_width, 200)
            enableUsingDesktopStatusBar()
            enableDualShade(wideLayout = true)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            val horizontalPosition = checkNotNull(dimens).horizontalPosition
            assertIs<HorizontalPosition.MiddleToEdge>(horizontalPosition)
            assertThat(horizontalPosition.maxWidth).isEqualTo(200)
        }

    @Test
    fun validatePaddingTop_splitShade_usesLargeHeaderHelper() =
        kosmos.runTest {
            enableSplitShade()
            whenever(largeScreenHeaderHelper.getLargeScreenHeaderHeight()).thenReturn(5)
            overrideResource(R.bool.config_use_large_screen_shade_header, true)
            overrideDimensionPixelSize(R.dimen.large_screen_shade_header_height, 10)
            overrideDimensionPixelSize(R.dimen.keyguard_split_shade_top_margin, 50)

            val paddingTop by collectLastValue(underTest.paddingTopDimen)
            fakeConfigurationRepository.onAnyConfigurationChange()

            // Should directly use the header height (flagged on value)
            assertThat(paddingTop).isEqualTo(5)
        }

    @Test
    fun validatePaddingTop_singleShade_usesLargeScreenHeader() =
        kosmos.runTest {
            enableSingleShade()
            whenever(largeScreenHeaderHelper.getLargeScreenHeaderHeight()).thenReturn(10)
            overrideResource(R.bool.config_use_large_screen_shade_header, true)
            overrideDimensionPixelSize(R.dimen.large_screen_shade_header_height, 10)
            overrideDimensionPixelSize(R.dimen.keyguard_split_shade_top_margin, 50)

            val paddingTop by collectLastValue(underTest.paddingTopDimen)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(paddingTop).isEqualTo(10)
        }

    @Test
    fun validatePaddingTop_singleShade_doesNotUseLargeScreenHeader() =
        kosmos.runTest {
            enableSingleShade()
            whenever(largeScreenHeaderHelper.getLargeScreenHeaderHeight()).thenReturn(10)
            overrideResource(R.bool.config_use_large_screen_shade_header, false)
            overrideDimensionPixelSize(R.dimen.large_screen_shade_header_height, 10)
            overrideDimensionPixelSize(R.dimen.keyguard_split_shade_top_margin, 50)

            val paddingTop by collectLastValue(underTest.paddingTopDimen)

            fakeConfigurationRepository.onAnyConfigurationChange()
            assertThat(paddingTop).isEqualTo(0)
        }

    @Test
    fun validateMarginEnd() =
        kosmos.runTest {
            overrideResource(R.dimen.notification_panel_margin_horizontal, 50)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(dimens!!.marginEnd).isEqualTo(50)
        }

    @Test
    @EnableSceneContainer
    @EnableFlags(FLAG_STATUS_BAR_FOR_DESKTOP)
    fun validateMarginEnd_dualShade_isNotificationShadeEndAligned() =
        kosmos.runTest {
            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)
            overrideResource(R.dimen.shade_panel_margin_horizontal, 50)
            enableUsingDesktopStatusBar()
            enableDualShade(wideLayout = true)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(checkNotNull(dimens).marginEnd).isEqualTo(50)
        }

    @Test
    fun validateMarginBottom() =
        kosmos.runTest {
            overrideResource(R.dimen.notification_panel_margin_bottom, 50)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(dimens!!.marginBottom).isEqualTo(50)
        }

    @Test
    @DisableSceneContainer
    fun validateMarginTopWithLargeScreenHeader_usesHelper() =
        kosmos.runTest {
            val headerResourceHeight = 50
            val headerHelperHeight = 100
            whenever(largeScreenHeaderHelper.getLargeScreenHeaderHeight())
                .thenReturn(headerHelperHeight)
            overrideResource(R.bool.config_use_large_screen_shade_header, true)
            overrideDimensionPixelSize(
                R.dimen.large_screen_shade_header_height,
                headerResourceHeight,
            )
            overrideDimensionPixelSize(R.dimen.notification_panel_margin_top, 0)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(dimens!!.marginTop).isEqualTo(headerHelperHeight)
        }

    @Test
    @EnableSceneContainer
    fun validateMarginTopWithLargeScreenHeader_sceneContainerFlagOn_stillZero() =
        kosmos.runTest {
            val headerResourceHeight = 50
            val headerHelperHeight = 100
            whenever(largeScreenHeaderHelper.getLargeScreenHeaderHeight())
                .thenReturn(headerHelperHeight)
            overrideResource(R.bool.config_use_large_screen_shade_header, true)
            overrideDimensionPixelSize(
                R.dimen.large_screen_shade_header_height,
                headerResourceHeight,
            )
            overrideDimensionPixelSize(R.dimen.notification_panel_margin_top, 0)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(dimens!!.marginTop).isEqualTo(0)
        }

    @Test
    fun glanceableHubAlpha_lockscreenToHub() =
        kosmos.runTest {
            val alpha by collectLastValue(underTest.glanceableHubAlpha)

            // Start on lockscreen
            showLockscreen()
            assertThat(alpha).isEqualTo(1f)

            // Start transitioning to glanceable hub
            sceneInteractor.changeScene(Scenes.Lockscreen, "")

            val progress = 0.6f
            setTransition(
                sceneTransition = Transition(from = Scenes.Lockscreen, to = Scenes.Communal),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.STARTED,
                        from = LOCKSCREEN,
                        to = GLANCEABLE_HUB,
                        value = 0f,
                    ),
            )

            setTransition(
                sceneTransition =
                    Transition(
                        from = Scenes.Lockscreen,
                        to = Scenes.Communal,
                        progress = flowOf(progress),
                    ),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.RUNNING,
                        from = LOCKSCREEN,
                        to = GLANCEABLE_HUB,
                        value = progress,
                    ),
            )

            assertThat(alpha).isIn(Range.closed(0f, 1f))

            // Finish transition to glanceable hub
            sceneInteractor.changeScene(Scenes.Communal, "")
            setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.FINISHED,
                        from = LOCKSCREEN,
                        to = GLANCEABLE_HUB,
                        value = 1f,
                    ),
            )
            assertThat(alpha).isEqualTo(0f)

            // While state is GLANCEABLE_HUB, verify alpha is restored to full if glanceable hub is
            // not fully visible.
            shadeTestUtil.setLockscreenShadeExpansion(0.1f)
            assertThat(alpha).isEqualTo(1f)
        }

    @Test
    fun glanceableHubAlpha_dreamToHub() =
        kosmos.runTest {
            val alpha by collectLastValue(underTest.glanceableHubAlpha)

            // Start on lockscreen, notifications should be unhidden.
            showLockscreen()
            assertThat(alpha).isEqualTo(1f)

            // Go to dozing
            keyguardTransitionRepository.sendTransitionSteps(
                from = LOCKSCREEN,
                to = DOZING,
                testScope,
            )
            assertThat(alpha).isEqualTo(1f)

            // Start transitioning to glanceable hub
            sceneInteractor.snapToScene(Scenes.Lockscreen, "")

            val progress = 0.6f
            setTransition(
                sceneTransition = Transition(from = Scenes.Lockscreen, to = Scenes.Communal),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.STARTED,
                        from = DOZING,
                        to = GLANCEABLE_HUB,
                        value = 0f,
                    ),
            )
            setTransition(
                sceneTransition =
                    Transition(
                        from = Scenes.Lockscreen,
                        to = Scenes.Communal,
                        progress = flowOf(progress),
                    ),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.RUNNING,
                        from = DOZING,
                        to = GLANCEABLE_HUB,
                        value = progress,
                    ),
            )
            // Keep notifications hidden during the transition from dream to hub
            assertThat(alpha).isEqualTo(0)

            // Finish transition to glanceable hub
            sceneInteractor.changeScene(Scenes.Communal, "")
            setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.FINISHED,
                        from = DOZING,
                        to = GLANCEABLE_HUB,
                        value = 1f,
                    ),
            )
            assertThat(alpha).isEqualTo(0f)
        }

    @Test
    fun glanceableHubAlpha_dozingToHub() =
        kosmos.runTest {
            val alpha by collectLastValue(underTest.glanceableHubAlpha)

            // Start on lockscreen, notifications should be unhidden.
            showLockscreen()
            assertThat(alpha).isEqualTo(1f)

            // Transition to dream, notifications should be hidden so that transition
            // from dream->hub doesn't cause notification flicker.
            showDream()
            assertThat(alpha).isEqualTo(0f)

            // Start transitioning to glanceable hub
            sceneInteractor.changeScene(Scenes.Lockscreen, "")

            val progress = 0.6f
            setTransition(
                sceneTransition = Transition(from = Scenes.Lockscreen, to = Scenes.Communal),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.STARTED,
                        from = DREAMING,
                        to = GLANCEABLE_HUB,
                        value = 0f,
                    ),
            )
            setTransition(
                sceneTransition =
                    Transition(
                        from = Scenes.Lockscreen,
                        to = Scenes.Communal,
                        progress = flowOf(progress),
                    ),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.RUNNING,
                        from = DREAMING,
                        to = GLANCEABLE_HUB,
                        value = progress,
                    ),
            )
            // Keep notifications hidden during the transition from dream to hub
            assertThat(alpha).isEqualTo(0)

            // Finish transition to glanceable hub
            sceneInteractor.changeScene(Scenes.Communal, "")
            setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition =
                    TransitionStep(
                        transitionState = TransitionState.FINISHED,
                        from = DREAMING,
                        to = GLANCEABLE_HUB,
                        value = 1f,
                    ),
            )
            assertThat(alpha).isEqualTo(0f)
        }

    @Test
    fun validateMarginTop() =
        kosmos.runTest {
            overrideResource(R.bool.config_use_large_screen_shade_header, false)
            overrideDimensionPixelSize(R.dimen.large_screen_shade_header_height, 50)
            overrideDimensionPixelSize(R.dimen.notification_panel_margin_top, 0)

            val dimens by collectLastValue(underTest.configurationBasedDimensions)

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(dimens!!.marginTop).isEqualTo(0)
        }

    @Test
    fun isOnLockscreen() =
        kosmos.runTest {
            val isOnLockscreen by collectLastValue(underTest.isOnLockscreen)

            setTransition(
                sceneTransition = Idle(Scenes.Gone),
                stateTransition = TransitionStep(from = LOCKSCREEN, to = GONE),
            )
            assertThat(isOnLockscreen).isFalse()

            setTransition(
                sceneTransition = Idle(Scenes.Lockscreen),
                stateTransition = TransitionStep(from = GONE, to = LOCKSCREEN),
            )
            assertThat(isOnLockscreen).isTrue()
            // While progressing from lockscreen, should still be true
            setTransition(
                sceneTransition = Transition(from = Scenes.Lockscreen, to = Scenes.Gone),
                stateTransition =
                    TransitionStep(
                        from = LOCKSCREEN,
                        to = GONE,
                        value = 0.8f,
                        transitionState = TransitionState.RUNNING,
                    ),
            )
            assertThat(isOnLockscreen).isTrue()

            setTransition(
                sceneTransition = Idle(Scenes.Lockscreen),
                stateTransition = TransitionStep(from = GONE, to = LOCKSCREEN),
            )
            assertThat(isOnLockscreen).isTrue()

            setTransition(
                sceneTransition = Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer)),
                stateTransition = TransitionStep(from = LOCKSCREEN, to = PRIMARY_BOUNCER),
            )
            assertThat(isOnLockscreen).isTrue()
        }

    @Test
    @DisableSceneContainer
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun isOnLockscreenFalseWhenCommunalShowing() =
        kosmos.runTest {
            val isOnLockscreen by collectLastValue(underTest.isOnLockscreen)

            setTransition(
                sceneTransition = Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer)),
                stateTransition = TransitionStep(from = LOCKSCREEN, to = PRIMARY_BOUNCER),
            )
            assertThat(isOnLockscreen).isTrue()

            showCommunalScene()

            // If bouncer is showing over the hub, it should not be considered on lockscreen
            assertThat(isOnLockscreen).isFalse()
        }

    @Test
    fun isOnLockscreenWithoutShade() =
        kosmos.runTest {
            val isOnLockscreenWithoutShade by collectLastValue(underTest.isOnLockscreenWithoutShade)

            // First on AOD
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            shadeTestUtil.setQsExpansion(0f)
            keyguardTransitionRepository.sendTransitionSteps(
                from = LOCKSCREEN,
                to = OCCLUDED,
                testScope,
            )
            assertThat(isOnLockscreenWithoutShade).isFalse()

            // Now move to lockscreen
            showLockscreen()

            // While state is LOCKSCREEN, validate variations of both shade and qs expansion
            shadeTestUtil.setQsExpansion(0f)
            shadeTestUtil.setLockscreenShadeExpansion(0.1f)
            assertThat(isOnLockscreenWithoutShade).isFalse()

            shadeTestUtil.setLockscreenShadeExpansion(0.1f)
            shadeTestUtil.setShadeAndQsExpansion(0.1f, .9f)
            assertThat(isOnLockscreenWithoutShade).isFalse()

            shadeTestUtil.setLockscreenShadeExpansion(0f)
            shadeTestUtil.setQsExpansion(0.1f)
            assertThat(isOnLockscreenWithoutShade).isFalse()

            shadeTestUtil.setQsExpansion(0f)
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            assertThat(isOnLockscreenWithoutShade).isTrue()
        }

    @Test
    fun isOnGlanceableHubWithoutShade() =
        kosmos.runTest {
            val isOnGlanceableHubWithoutShade by
                collectLastValue(underTest.isOnGlanceableHubWithoutShade)

            // Start on lockscreen
            showLockscreen()
            assertThat(isOnGlanceableHubWithoutShade).isFalse()

            // Move to glanceable hub
            sceneInteractor.changeScene(Scenes.Communal, "")
            setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition = TransitionStep(from = LOCKSCREEN, to = GLANCEABLE_HUB),
            )

            assertThat(isOnGlanceableHubWithoutShade).isTrue()

            // While state is GLANCEABLE_HUB, validate variations of both shade and qs expansion
            shadeTestUtil.setQsExpansion(0f)
            shadeTestUtil.setLockscreenShadeExpansion(0.1f)
            assertThat(isOnGlanceableHubWithoutShade).isFalse()

            shadeTestUtil.setLockscreenShadeExpansion(0.1f)
            shadeTestUtil.setShadeAndQsExpansion(0.1f, .9f)
            assertThat(isOnGlanceableHubWithoutShade).isFalse()

            shadeTestUtil.setLockscreenShadeExpansion(0f)
            shadeTestUtil.setQsExpansion(0.1f)
            assertThat(isOnGlanceableHubWithoutShade).isFalse()

            shadeTestUtil.setQsExpansion(0f)
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            sceneInteractor.changeScene(Scenes.Communal, "")
            setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition = TransitionStep(from = LOCKSCREEN, to = GLANCEABLE_HUB),
            )
            assertThat(isOnGlanceableHubWithoutShade).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun bounds_onLockscreenInSingleShade() =
        kosmos.runTest {
            val bounds by collectLastValue(underTest.bounds)

            // When in single shade
            enableSingleShade()

            // Start on lockscreen
            showLockscreen()

            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 1f, bottom = 2f)
            )

            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = 1f, bottom = 2f))
        }

    @Test
    @DisableSceneContainer
    fun bounds_stableWhenGoingToAlternateBouncer() =
        kosmos.runTest {
            val bounds by collectLastValue(underTest.bounds)

            // Start on lockscreen
            showLockscreen()

            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 1f, bottom = 2f)
            )

            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = 1f, bottom = 2f))

            // Begin transition to AOD
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(LOCKSCREEN, ALTERNATE_BOUNCER, 0f, TransitionState.STARTED)
            )
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(LOCKSCREEN, ALTERNATE_BOUNCER, 0f, TransitionState.RUNNING)
            )

            // This is the last step before FINISHED is sent, which could trigger a change in bounds
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(LOCKSCREEN, ALTERNATE_BOUNCER, 1f, TransitionState.RUNNING)
            )
            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = 1f, bottom = 2f))

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(LOCKSCREEN, ALTERNATE_BOUNCER, 1f, TransitionState.FINISHED)
            )
            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = 1f, bottom = 2f))
        }

    @Test
    @DisableSceneContainer
    fun boundsDoNotChangeWhileLockscreenToAodTransitionIsActive() =
        kosmos.runTest {
            val bounds by collectLastValue(underTest.bounds)

            // Start on lockscreen
            showLockscreen()

            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 1f, bottom = 1f)
            )
            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = 1f, bottom = 1f))

            // Begin transition to AOD
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(LOCKSCREEN, AOD, 0f, TransitionState.STARTED)
            )
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(LOCKSCREEN, AOD, 0.5f, TransitionState.RUNNING)
            )

            // Attempt to update bounds
            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 5f, bottom = 5f)
            )
            // Bounds should not have moved
            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = 1f, bottom = 1f))

            // Transition is over, now move
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(LOCKSCREEN, AOD, 1f, TransitionState.FINISHED)
            )
            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = 5f, bottom = 5f))
        }

    @Test
    @DisableSceneContainer
    fun boundsOnLockscreenInSplitShade_usesLargeHeaderHelper() =
        kosmos.runTest {
            val bounds by collectLastValue(underTest.bounds)

            // When in split shade
            enableSplitShade()
            whenever(largeScreenHeaderHelper.getLargeScreenHeaderHeight()).thenReturn(5)
            overrideDimensionPixelSize(R.dimen.large_screen_shade_header_height, 10)
            overrideDimensionPixelSize(R.dimen.keyguard_split_shade_top_margin, 50)
            fakeConfigurationRepository.onAnyConfigurationChange()

            // Start on lockscreen
            showLockscreen()

            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 1f, bottom = 52f)
            )

            // Top should be equal to bounds (1) - padding adjustment (5)
            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = -4f, bottom = 2f))
        }

    @Test
    @DisableSceneContainer
    fun boundsOnShade() =
        kosmos.runTest {
            val bounds by collectLastValue(underTest.bounds)

            // Start on lockscreen with shade expanded
            showLockscreenWithShadeExpanded()

            // When not in split shade
            sharedNotificationContainerInteractor.setTopPosition(10f)

            assertThat(bounds)
                .isEqualTo(NotificationContainerBounds(top = 10f, bottom = 0f, isAnimated = true))
        }

    @Test
    @DisableSceneContainer
    fun boundsOnQS() =
        kosmos.runTest {
            val bounds by collectLastValue(underTest.bounds)

            // Start on lockscreen with shade expanded
            showLockscreenWithQSExpanded()

            // When not in split shade
            sharedNotificationContainerInteractor.setTopPosition(10f)

            assertThat(bounds)
                .isEqualTo(NotificationContainerBounds(top = 10f, bottom = 0f, isAnimated = false))
        }

    @Test
    fun maxNotificationsOnLockscreen() =
        kosmos.runTest {
            var notificationCount = 10
            val calculateSpace = { space: Float, useExtraShelfSpace: Boolean -> notificationCount }
            val maxNotifications by collectLastValue(underTest.getMaxNotifications(calculateSpace))
            advanceTimeBy(50L)
            showLockscreen()

            enableSingleShade()

            assertThat(maxNotifications).isEqualTo(10)

            // Also updates when directly requested (as it would from NotificationStackScrollLayout)
            notificationCount = 25
            sharedNotificationContainerInteractor.notificationStackChanged()
            advanceTimeBy(50L)
            assertThat(maxNotifications).isEqualTo(25)

            // Also ensure another collection starts with the same value. As an example, folding
            // then unfolding will restart the coroutine and it must get the last value immediately.
            val newMaxNotifications by
                collectLastValue(underTest.getMaxNotifications(calculateSpace))
            advanceTimeBy(50L)
            assertThat(newMaxNotifications).isEqualTo(25)
        }

    @Test
    fun maxNotificationsOnLockscreen_DoesNotUpdateWhenUserInteracting() =
        kosmos.runTest {
            enableSingleShade()
            var notificationCount = 10
            val calculateSpace = { space: Float, useExtraShelfSpace: Boolean -> notificationCount }
            val maxNotifications by collectLastValue(underTest.getMaxNotifications(calculateSpace))
            advanceTimeBy(50L)
            showLockscreen()

            fakeConfigurationRepository.onAnyConfigurationChange()

            assertThat(maxNotifications).isEqualTo(10)

            // Shade expanding... still 10
            shadeTestUtil.setLockscreenShadeExpansion(0.5f)
            assertThat(maxNotifications).isEqualTo(10)

            notificationCount = 25

            // When shade is expanding by user interaction
            shadeTestUtil.setLockscreenShadeTracking(true)

            // Should still be 10, since the user is interacting
            assertThat(maxNotifications).isEqualTo(10)

            shadeTestUtil.setLockscreenShadeTracking(false)
            shadeTestUtil.setLockscreenShadeExpansion(0f)

            // Stopped tracking, show 25
            assertThat(maxNotifications).isEqualTo(25)
        }

    @Test
    fun maxNotificationsOnShade() =
        kosmos.runTest {
            val calculateSpace = { space: Float, useExtraShelfSpace: Boolean -> 10 }
            val maxNotifications by collectLastValue(underTest.getMaxNotifications(calculateSpace))
            advanceTimeBy(50L)

            // Show lockscreen with shade expanded
            showLockscreenWithShadeExpanded()

            enableSingleShade()

            // -1 means No Limit
            assertThat(maxNotifications).isEqualTo(-1)
        }

    @Test
    @DisableSceneContainer
    fun translationY_updatesOnKeyguardForBurnIn() =
        kosmos.runTest {
            val translationY by collectLastValue(underTest.translationY)

            showLockscreen()
            assertThat(translationY).isEqualTo(0)

            movementFlow.value = BurnInModel(translationY = 150)
            assertThat(translationY).isEqualTo(150f)
        }

    @Test
    @DisableSceneContainer
    fun translationY_updatesOnKeyguard() =
        kosmos.runTest {
            val translationY by collectLastValue(underTest.translationY)

            fakeConfigurationRepository.setDimensionPixelSize(
                R.dimen.keyguard_translate_distance_on_swipe_up,
                -100,
            )
            fakeConfigurationRepository.onAnyConfigurationChange()

            // legacy expansion means the user is swiping up, usually for the bouncer
            shadeTestUtil.setShadeExpansion(0.5f)

            showLockscreen()

            // The translation values are negative
            assertThat(translationY).isLessThan(0f)
        }

    @Test
    @DisableSceneContainer
    fun translationY_doesNotUpdateWhenShadeIsExpanded() =
        kosmos.runTest {
            val translationY by collectLastValue(underTest.translationY)

            fakeConfigurationRepository.setDimensionPixelSize(
                R.dimen.keyguard_translate_distance_on_swipe_up,
                -100,
            )
            fakeConfigurationRepository.onAnyConfigurationChange()

            // Legacy expansion means the user is swiping up, usually for the bouncer but also for
            // shade collapsing.
            shadeTestUtil.setShadeExpansion(0.5f)

            showLockscreenWithShadeExpanded()

            assertThat(translationY).isEqualTo(0f)
        }

    @Test
    @DisableSceneContainer
    fun updateBounds_fromGone_withoutTransitions() =
        kosmos.runTest {
            // Start step is already at 1.0
            val runningStep = TransitionStep(GONE, AOD, 1.0f, TransitionState.RUNNING)
            val finishStep = TransitionStep(GONE, AOD, 1.0f, TransitionState.FINISHED)

            val bounds by collectLastValue(underTest.bounds)
            val top = 123f
            val bottom = 456f

            keyguardTransitionRepository.sendTransitionStep(runningStep)
            keyguardTransitionRepository.sendTransitionStep(finishStep)
            keyguardRootViewModel.onNotificationContainerBoundsChanged(top, bottom)

            assertThat(bounds).isEqualTo(NotificationContainerBounds(top = top, bottom = bottom))
        }

    @Test
    fun alphaOnFullQsExpansion() =
        kosmos.runTest {
            val viewState = ViewStateAccessor()
            val alpha by
                collectLastValue(underTest.keyguardAlpha(viewState, testScope.backgroundScope))

            showLockscreenWithQSExpanded()

            // Alpha fades out as QS expands
            shadeTestUtil.setQsExpansion(0.5f)
            assertThat(alpha).isWithin(0.01f).of(0.5f)
            shadeTestUtil.setQsExpansion(0.9f)
            assertThat(alpha).isWithin(0.01f).of(0.1f)

            // Ensure that alpha is set back to 1f when QS is fully expanded
            shadeTestUtil.setQsExpansion(1f)
            assertThat(alpha).isEqualTo(1f)
        }

    @Test
    @BrokenWithSceneContainer(330311871)
    fun alphaWhenGoneIsSetToOne() =
        kosmos.runTest {
            val viewState = ViewStateAccessor()
            val alpha by
                collectLastValue(underTest.keyguardAlpha(viewState, testScope.backgroundScope))

            showLockscreen()
            assertThat(alpha).isEqualTo(1f)

            // GONE transition gets to 90% complete
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = LOCKSCREEN,
                    to = GONE,
                    transitionState = TransitionState.STARTED,
                    value = 0f,
                )
            )
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = LOCKSCREEN,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.9f,
                )
            )
            // Change in state should not immediately set value to 1f. Should wait for transition to
            // complete.
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)

            // Transition is active, and NSSL should be nearly faded out
            assertThat(alpha).isLessThan(0.5f)

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = LOCKSCREEN,
                    to = GONE,
                    transitionState = TransitionState.FINISHED,
                    value = 1f,
                )
            )
            // Should reset to 1f
            assertThat(alpha).isEqualTo(1f)
        }

    @Test
    fun shadeCollapseFadeIn() =
        kosmos.runTest {
            val fadeIn by collectValues(underTest.shadeCollapseFadeIn)

            // Start on lockscreen without the shade
            showLockscreen()
            assertThat(fadeIn[0]).isEqualTo(false)

            // ... then the shade expands
            showLockscreenWithShadeExpanded()
            assertThat(fadeIn[0]).isEqualTo(false)

            // ... it collapses
            showLockscreen()
            assertThat(fadeIn[1]).isEqualTo(true)

            // ... and ensure the value goes back to false
            assertThat(fadeIn[2]).isEqualTo(false)
        }

    @Test
    fun shadeCollapseFadeIn_doesNotRunIfTransitioningToAod() =
        kosmos.runTest {
            val fadeIn by collectValues(underTest.shadeCollapseFadeIn)

            // Start on lockscreen without the shade
            showLockscreen()
            assertThat(fadeIn[0]).isEqualTo(false)

            // ... then the shade expands
            showLockscreenWithShadeExpanded()
            assertThat(fadeIn[0]).isEqualTo(false)

            // ... then user hits power to go to AOD
            keyguardTransitionRepository.sendTransitionSteps(from = LOCKSCREEN, to = AOD, testScope)
            // ... followed by a shade collapse
            showLockscreen()
            // ... does not trigger a fade in
            assertThat(fadeIn[0]).isEqualTo(false)
        }

    @Test
    fun shadeCollapseFadeIn_doesNotRunIfTransitioningToDream() =
        kosmos.runTest {
            val fadeIn by collectValues(underTest.shadeCollapseFadeIn)

            // Start on lockscreen without the shade
            showLockscreen()
            assertThat(fadeIn[0]).isEqualTo(false)

            // ... then the shade expands
            showLockscreenWithShadeExpanded()
            assertThat(fadeIn[0]).isEqualTo(false)

            // ... then user hits power to go to dream
            keyguardTransitionRepository.sendTransitionSteps(
                from = LOCKSCREEN,
                to = DREAMING,
                testScope,
            )
            // ... followed by a shade collapse
            showLockscreen()
            // ... does not trigger a fade in
            assertThat(fadeIn[0]).isEqualTo(false)
        }

    @Test
    @BrokenWithSceneContainer(330311871)
    fun alpha_isZero_fromPrimaryBouncerToGoneWhileCommunalSceneVisible() =
        kosmos.runTest {
            val viewState = ViewStateAccessor()
            val alpha by
                collectLastValue(underTest.keyguardAlpha(viewState, testScope.backgroundScope))

            showPrimaryBouncer()
            showCommunalScene()

            // PRIMARY_BOUNCER->GONE transition is started
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = PRIMARY_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.STARTED,
                    value = 0f,
                )
            )

            // PRIMARY_BOUNCER->GONE transition running
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = PRIMARY_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.1f,
                )
            )
            assertThat(alpha).isEqualTo(0f)

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = PRIMARY_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.9f,
                )
            )
            assertThat(alpha).isEqualTo(0f)

            hideCommunalScene()
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = PRIMARY_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.FINISHED,
                    value = 1f,
                )
            )
            // Resets to 1f after communal scene is hidden
            assertThat(alpha).isEqualTo(1f)
        }

    @Test
    @BrokenWithSceneContainer(330311871)
    fun alpha_fromPrimaryBouncerToGoneWhenCommunalSceneNotVisible() =
        kosmos.runTest {
            val viewState = ViewStateAccessor()
            val alpha by
                collectLastValue(underTest.keyguardAlpha(viewState, testScope.backgroundScope))

            showPrimaryBouncer()
            hideCommunalScene()

            // PRIMARY_BOUNCER->GONE transition is started
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = PRIMARY_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.STARTED,
                )
            )

            // PRIMARY_BOUNCER->GONE transition running
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = PRIMARY_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.1f,
                )
            )
            assertThat(alpha).isIn(Range.closedOpen(0f, 1f))

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = PRIMARY_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.9f,
                )
            )
            assertThat(alpha).isIn(Range.closedOpen(0f, 1f))

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = PRIMARY_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.FINISHED,
                    value = 1f,
                )
            )
            assertThat(alpha).isEqualTo(1f)
        }

    @Test
    @BrokenWithSceneContainer(330311871)
    fun alpha_isZero_fromAlternateBouncerToGoneWhileCommunalSceneVisible() =
        kosmos.runTest {
            val viewState = ViewStateAccessor()
            val alpha by
                collectLastValue(underTest.keyguardAlpha(viewState, testScope.backgroundScope))

            showAlternateBouncer()
            showCommunalScene()

            // ALTERNATE_BOUNCER->GONE transition is started
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = ALTERNATE_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.STARTED,
                    value = 0f,
                )
            )

            // ALTERNATE_BOUNCER->GONE transition running
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = ALTERNATE_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.1f,
                )
            )
            assertThat(alpha).isEqualTo(0f)

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = ALTERNATE_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.9f,
                )
            )
            assertThat(alpha).isEqualTo(0f)

            hideCommunalScene()
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = ALTERNATE_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.FINISHED,
                    value = 1f,
                )
            )
            // Resets to 1f after communal scene is hidden
            assertThat(alpha).isEqualTo(1f)
        }

    @Test
    @BrokenWithSceneContainer(330311871)
    fun alpha_fromAlternateBouncerToGoneWhenCommunalSceneNotVisible() =
        kosmos.runTest {
            val viewState = ViewStateAccessor()
            val alpha by
                collectLastValue(underTest.keyguardAlpha(viewState, testScope.backgroundScope))

            showAlternateBouncer()
            hideCommunalScene()

            // ALTERNATE_BOUNCER->GONE transition is started
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = ALTERNATE_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.STARTED,
                )
            )

            // ALTERNATE_BOUNCER->GONE transition running
            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = ALTERNATE_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.1f,
                )
            )
            assertThat(alpha).isIn(Range.closedOpen(0f, 1f))

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = ALTERNATE_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.RUNNING,
                    value = 0.9f,
                )
            )
            assertThat(alpha).isIn(Range.closedOpen(0f, 1f))

            keyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = ALTERNATE_BOUNCER,
                    to = GONE,
                    transitionState = TransitionState.FINISHED,
                    value = 1f,
                )
            )
            assertThat(alpha).isEqualTo(1f)
        }

    @Test
    @EnableFlags(FLAG_LOCKSCREEN_SHADE_TO_DREAM_TRANSITION_FIX)
    @BrokenWithSceneContainer(430694649)
    fun alpha_isZero_duringLockscreenToDreamTransition() =
        kosmos.runTest {
            val viewState = ViewStateAccessor()
            val alpha by
                collectLastValue(underTest.keyguardAlpha(viewState, testScope.backgroundScope))

            showLockscreenWithQSExpanded()

            // Start lockscreen to dream transition, alpha is 0 once the transition starts.
            keyguardTransitionRepository.sendTransitionStep(
                from = LOCKSCREEN,
                to = DREAMING,
                value = 0f,
                transitionState = TransitionState.STARTED,
            )
            assertThat(alpha).isEqualTo(0f)

            keyguardTransitionRepository.sendTransitionStep(
                from = LOCKSCREEN,
                to = DREAMING,
                value = 0.5f,
                transitionState = TransitionState.RUNNING,
            )
            assertThat(alpha).isEqualTo(0f)

            // Shade collapse animation finishing up. This can happen in the real world as the shade
            // collapse and dream start are not synced in any way. Keyguard alpha should not change
            // even when this is happening as the transition is ongoing.
            shadeTestUtil.setLockscreenShadeExpansion(0.1f)
            shadeTestUtil.setQsExpansion(0.1f)
            assertThat(alpha).isEqualTo(0f)
        }

    @Test
    @DisableSceneContainer
    fun notificationAbsoluteBottom_maxNotificationChanged() =
        kosmos.runTest {
            enableSingleShade()
            var notificationCount = 2
            val calculateSpace = { _: Float, _: Boolean -> notificationCount }
            val shelfHeight = 10F
            val heightForNotification = 20F
            val calculateHeight = { count: Int -> count * heightForNotification + shelfHeight }
            val stackAbsoluteBottom by
                collectLastValue(
                    underTest.getNotificationStackAbsoluteBottomOnLockscreen(
                        calculateSpace,
                        calculateHeight,
                    )
                )
            activeNotificationListRepository.setActiveNotifs(notificationCount)
            showLockscreen()
            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 100F, bottom = 300F)
            )

            sharedNotificationContainerInteractor.notificationStackChanged()
            advanceTimeBy(50L)
            assertThat(stackAbsoluteBottom).isEqualTo(150F)

            notificationCount = 3
            sharedNotificationContainerInteractor.notificationStackChanged()
            advanceTimeBy(50L)
            assertThat(stackAbsoluteBottom).isEqualTo(170F)
        }

    @Test
    @DisableSceneContainer
    fun notificationAbsoluteBottom_noNotificationOnLockscreen() =
        kosmos.runTest {
            enableSingleShade()
            val notificationCount = 0
            val calculateSpace = { _: Float, _: Boolean -> notificationCount }
            val shelfHeight = 10F
            val heightForNotification = 20F
            val calculateHeight = { count: Int -> count * heightForNotification + shelfHeight }
            val stackAbsoluteBottom by
                collectLastValue(
                    underTest.getNotificationStackAbsoluteBottomOnLockscreen(
                        calculateSpace,
                        calculateHeight,
                    )
                )
            showLockscreen()
            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 100F, bottom = 300F)
            )
            activeNotificationListRepository.setActiveNotifs(notificationCount)
            advanceTimeBy(50L)

            assertThat(stackAbsoluteBottom).isEqualTo(0F)
        }

    @Test
    @DisableSceneContainer
    fun notificationAbsoluteBottomOnLockscreen_heightChangedWithoutMaxNotificationChange() =
        kosmos.runTest {
            val notificationCount = 2
            val calculateSpace = { _: Float, _: Boolean -> notificationCount }
            var shelfHeight = 10F
            val heightForNotification = 20F
            val calculateHeight = { count: Int -> count * heightForNotification + shelfHeight }
            val stackAbsoluteBottom by
                collectLastValue(
                    underTest.getNotificationStackAbsoluteBottomOnLockscreen(
                        calculateSpace,
                        calculateHeight,
                    )
                )
            activeNotificationListRepository.setActiveNotifs(notificationCount)
            showLockscreen()

            enableSingleShade()
            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 100F, bottom = 300F)
            )

            sharedNotificationContainerInteractor.notificationStackChanged()
            advanceTimeBy(50L)
            assertThat(stackAbsoluteBottom).isEqualTo(150F)

            shelfHeight = 0f
            sharedNotificationContainerInteractor.notificationStackChanged()
            advanceTimeBy(50L)
            assertThat(stackAbsoluteBottom).isEqualTo(140F)
        }

    @Test
    @DisableSceneContainer
    fun notificationAbsoluteBottomOnLockscreen_zeroOnHomescreen() =
        kosmos.runTest {
            val calculateSpace = { _: Float, _: Boolean -> -1 }
            val shelfHeight = 10F
            val heightForNotification = 20F
            val calculateHeight = { count: Int -> count * heightForNotification + shelfHeight }
            val stackAbsoluteBottom by
                collectLastValue(
                    underTest.getNotificationStackAbsoluteBottomOnLockscreen(
                        calculateSpace,
                        calculateHeight,
                    )
                )
            advanceTimeBy(50L)

            enableSingleShade()
            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 100F, bottom = 300F)
            )
            setTransition(
                sceneTransition = Idle(Scenes.Gone),
                stateTransition = TransitionStep(from = LOCKSCREEN, to = GONE),
            )
            assertThat(stackAbsoluteBottom).isEqualTo(0F)
        }

    @Test
    @DisableSceneContainer
    fun notificationAbsoluteBottom_notReactToHeightChangeOnShade() =
        kosmos.runTest {
            val notificationCount = 2
            val calculateSpace = { _: Float, _: Boolean -> notificationCount }
            val shelfHeight = 10F
            val heightForNotification = 20F
            val calculateHeight = { count: Int -> count * heightForNotification + shelfHeight }
            val stackAbsoluteBottom by
                collectLastValue(
                    underTest.getNotificationStackAbsoluteBottomOnLockscreen(
                        calculateSpace,
                        calculateHeight,
                    )
                )
            showLockscreen()
            enableSingleShade()
            activeNotificationListRepository.setActiveNotifs(notificationCount)
            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 100F, bottom = 300F)
            )
            advanceTimeBy(50L)
            assertThat(stackAbsoluteBottom).isEqualTo(150F)

            showLockscreenWithQSExpanded()
            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 200F, bottom = 300F)
            )
            advanceTimeBy(50L)
            assertThat(stackAbsoluteBottom).isEqualTo(150F)
        }

    @Test
    @DisableSceneContainer
    fun notificationAbsoluteBottom_onlyMediaInNotifications() =
        kosmos.runTest {
            val notificationCount = 0
            val calculateSpace = { _: Float, _: Boolean -> notificationCount }
            val mediaHeight = 100F
            val calculateHeight = { _: Int -> mediaHeight }
            whenever(legacyMediaDataManagerImpl.hasActiveMedia()).thenReturn(true)
            val stackAbsoluteBottom by
                collectLastValue(
                    underTest.getNotificationStackAbsoluteBottomOnLockscreen(
                        calculateSpace,
                        calculateHeight,
                    )
                )
            showLockscreen()
            enableSingleShade()
            activeNotificationListRepository.setActiveNotifs(notificationCount)
            keyguardInteractor.setNotificationContainerBounds(
                NotificationContainerBounds(top = 100F, bottom = 100F)
            )
            advanceTimeBy(50L)
            assertThat(stackAbsoluteBottom).isEqualTo(200F)
        }

    @Test
    fun blurRadius_emitsValues_fromPrimaryBouncerTransitions() =
        kosmos.runTest {
            val blurRadius by collectLastValue(underTest.blurRadius)
            assertThat(blurRadius).isEqualTo(0.0f)

            fakeBouncerTransitions.first().notificationBlurRadius.value = 30.0f
            assertThat(blurRadius).isEqualTo(30.0f)

            fakeBouncerTransitions.last().notificationBlurRadius.value = 40.0f
            assertThat(blurRadius).isEqualTo(40.0f)
        }

    @Test
    @DisableSceneContainer
    @EnableFlags(FLAG_GESTURE_BETWEEN_HUB_AND_LOCKSCREEN_MOTION)
    fun glanceableHubViewScale_transitionFromLockscreenToHubAndBack() =
        kosmos.runTest {
            val scale by collectLastValue(underTest.viewScale)
            showLockscreen()
            assertThat(scale).isEqualTo(1.0f)

            val transitionState: MutableStateFlow<ObservableTransitionState> =
                MutableStateFlow(
                    ObservableTransitionState.Transition(
                        fromScene = CommunalScenes.Blank,
                        toScene = CommunalScenes.Communal,
                        currentScene = flowOf(CommunalScenes.Blank),
                        progress = flowOf(0f),
                        isInitiatedByUserInput = true,
                        isUserInputOngoing = flowOf(false),
                    )
                )

            // Start transition to communal.
            communalSceneRepository.setTransitionState(transitionState)

            // Transition to the glanceable hub and back.
            keyguardTransitionRepository.sendTransitionSteps(
                from = LOCKSCREEN,
                to = GLANCEABLE_HUB,
                testScope,
            )
            transitionState.value = ObservableTransitionState.Idle(CommunalScenes.Communal)

            assertThat(scale).isEqualTo(1 - PUSHBACK_SCALE)

            // Start transitioning back.
            keyguardTransitionRepository.sendTransitionSteps(
                from = GLANCEABLE_HUB,
                to = LOCKSCREEN,
                testScope,
            )
            transitionState.value = ObservableTransitionState.Idle(CommunalScenes.Blank)

            assertThat(scale).isEqualTo(1f)
        }

    @Test
    @DisableSceneContainer
    @EnableFlags(FLAG_GESTURE_BETWEEN_HUB_AND_LOCKSCREEN_MOTION)
    fun glanceableHubViewScale_reset_transitionedAwayFromHub() =
        kosmos.runTest {
            val scale by collectLastValue(underTest.viewScale)

            val transitionState: MutableStateFlow<ObservableTransitionState> =
                MutableStateFlow(ObservableTransitionState.Idle(CommunalScenes.Blank))

            // Transition to the glanceable hub and then to bouncer.
            keyguardTransitionRepository.sendTransitionSteps(
                from = LOCKSCREEN,
                to = GLANCEABLE_HUB,
                testScope,
            )
            transitionState.value = ObservableTransitionState.Idle(CommunalScenes.Communal)

            keyguardTransitionRepository.sendTransitionSteps(
                from = GLANCEABLE_HUB,
                to = PRIMARY_BOUNCER,
                testScope,
            )
            transitionState.value = ObservableTransitionState.Idle(CommunalScenes.Blank)

            assertThat(scale).isEqualTo(1f)
        }

    private suspend fun Kosmos.showLockscreen() {
        shadeTestUtil.setQsExpansion(0f)
        shadeTestUtil.setLockscreenShadeExpansion(0f)
        fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
        keyguardTransitionRepository.sendTransitionSteps(from = AOD, to = LOCKSCREEN, testScope)
    }

    private suspend fun Kosmos.showDream() {
        shadeTestUtil.setQsExpansion(0f)
        shadeTestUtil.setLockscreenShadeExpansion(0f)
        fakeKeyguardRepository.setDreaming(true)
        keyguardTransitionRepository.sendTransitionSteps(
            from = LOCKSCREEN,
            to = DREAMING,
            testScope,
        )
    }

    private suspend fun Kosmos.showLockscreenWithShadeExpanded() {
        shadeTestUtil.setQsExpansion(0f)
        shadeTestUtil.setLockscreenShadeExpansion(1f)
        fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE_LOCKED)
        keyguardTransitionRepository.sendTransitionSteps(from = AOD, to = LOCKSCREEN, testScope)
    }

    private suspend fun Kosmos.showLockscreenWithQSExpanded() {
        shadeTestUtil.setLockscreenShadeExpansion(0f)
        shadeTestUtil.setQsExpansion(1f)
        fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE_LOCKED)
        keyguardTransitionRepository.sendTransitionSteps(from = AOD, to = LOCKSCREEN, testScope)
    }

    private suspend fun Kosmos.showPrimaryBouncer() {
        shadeTestUtil.setQsExpansion(0f)
        shadeTestUtil.setLockscreenShadeExpansion(0f)
        fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
        keyguardBouncerRepository.setPrimaryShow(true)
        keyguardTransitionRepository.sendTransitionSteps(
            from = GLANCEABLE_HUB,
            to = PRIMARY_BOUNCER,
            testScope,
        )
    }

    private suspend fun Kosmos.showAlternateBouncer() {
        shadeTestUtil.setQsExpansion(0f)
        shadeTestUtil.setLockscreenShadeExpansion(0f)
        fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
        keyguardBouncerRepository.setPrimaryShow(false)
        keyguardTransitionRepository.sendTransitionSteps(
            from = GLANCEABLE_HUB,
            to = ALTERNATE_BOUNCER,
            testScope,
        )
    }

    private fun Kosmos.showCommunalScene() {
        val targetScene =
            if (SceneContainerFlag.isEnabled) Scenes.Communal else CommunalScenes.Communal
        communalSceneInteractor.changeScene(targetScene, "test")
    }

    private fun Kosmos.hideCommunalScene() {
        val targetScene =
            if (SceneContainerFlag.isEnabled) Scenes.Lockscreen else CommunalScenes.Blank
        communalSceneInteractor.changeScene(targetScene, "test")
    }

    private fun Kosmos.overrideDimensionPixelSize(id: Int, pixelSize: Int) {
        overrideResource(id, pixelSize)
        fakeConfigurationRepository.setDimensionPixelSize(id, pixelSize)
    }
}
