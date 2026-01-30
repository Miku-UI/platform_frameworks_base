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
 */

package com.android.systemui.communal.view.viewmodel

import android.content.ComponentName
import android.content.pm.UserInfo
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.provider.Settings
import android.widget.RemoteViews
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags.FLAG_BOUNCER_UI_REVAMP
import com.android.systemui.Flags.FLAG_COMMUNAL_HUB
import com.android.systemui.Flags.FLAG_COMMUNAL_RESPONSIVE_GRID
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_DIRECT_EDIT_MODE
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_V2
import com.android.systemui.Flags.FLAG_HUB_EDIT_MODE_TRANSITION
import com.android.systemui.Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE
import com.android.systemui.Flags.FLAG_NOTIFICATION_SHADE_BLUR
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.classifier.domain.interactor.falsingInteractor
import com.android.systemui.classifier.fakeFalsingManager
import com.android.systemui.communal.data.model.CommunalSmartspaceTimer
import com.android.systemui.communal.data.model.FEATURE_MANUAL_OPEN
import com.android.systemui.communal.data.model.SuppressionReason
import com.android.systemui.communal.data.repository.fakeCommunalMediaRepository
import com.android.systemui.communal.data.repository.fakeCommunalSceneRepository
import com.android.systemui.communal.data.repository.fakeCommunalSmartspaceRepository
import com.android.systemui.communal.data.repository.fakeCommunalTutorialRepository
import com.android.systemui.communal.data.repository.fakeCommunalWidgetRepository
import com.android.systemui.communal.domain.interactor.communalInteractor
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.domain.interactor.communalTutorialInteractor
import com.android.systemui.communal.domain.interactor.editWidgetsActivityStarter
import com.android.systemui.communal.domain.interactor.setCommunalEnabled
import com.android.systemui.communal.domain.interactor.setCommunalV2ConfigEnabled
import com.android.systemui.communal.domain.model.CommunalContentModel
import com.android.systemui.communal.shared.log.CommunalMetricsLogger
import com.android.systemui.communal.shared.log.communalSceneLogger
import com.android.systemui.communal.shared.model.CommunalContentSize
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.communal.shared.model.EditModeState
import com.android.systemui.communal.ui.viewmodel.CommunalViewModel
import com.android.systemui.communal.ui.viewmodel.CommunalViewModel.Companion.POPUP_AUTO_HIDE_TIMEOUT_MS
import com.android.systemui.communal.ui.viewmodel.PopupType
import com.android.systemui.flags.Flags.COMMUNAL_SERVICE_ENABLED
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.media.controls.domain.pipeline.interactor.mediaCarouselInteractor
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.ui.controller.mediaCarouselController
import com.android.systemui.media.controls.ui.view.MediaCarouselScrollHandler
import com.android.systemui.media.controls.ui.view.MediaHost
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.media.remedia.ui.viewmodel.factory.mediaViewModelFactory
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.Transition
import com.android.systemui.scene.data.repository.setTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.statusbar.KeyguardIndicationController
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableFlags(FLAG_COMMUNAL_HUB)
class CommunalViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    @Mock private lateinit var mediaHost: MediaHost

    @Mock private lateinit var mediaCarouselScrollHandler: MediaCarouselScrollHandler

    @Mock private lateinit var metricsLogger: CommunalMetricsLogger

    private val kosmos = testKosmos()

    private val Kosmos.underTest by Kosmos.Fixture { createViewModel() }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        kosmos.fakeFeatureFlagsClassic.set(COMMUNAL_SERVICE_ENABLED, true)
        kosmos.fakeUserTracker.set(userInfos = listOf(MAIN_USER_INFO), selectedUserIndex = 0)
        whenever(mediaHost.visible).thenReturn(true)
        whenever(kosmos.mediaCarouselController.mediaCarouselScrollHandler)
            .thenReturn(mediaCarouselScrollHandler)

        kosmos.powerInteractor.setAwakeForTest()
    }

    private fun createViewModel(): CommunalViewModel {
        return CommunalViewModel(
            kosmos.testDispatcher,
            kosmos.applicationCoroutineScope,
            kosmos.backgroundScope,
            kosmos.keyguardTransitionInteractor,
            kosmos.keyguardInteractor,
            mock<KeyguardIndicationController>(),
            kosmos.communalSceneInteractor,
            kosmos.communalInteractor,
            kosmos.communalSettingsInteractor,
            kosmos.communalTutorialInteractor,
            kosmos.shadeInteractor,
            mediaHost,
            logcatLogBuffer("CommunalViewModelTest"),
            metricsLogger,
            kosmos.mediaCarouselController,
            kosmos.blurConfig,
            false,
            kosmos.communalSceneLogger,
            kosmos.falsingInteractor,
            kosmos.mediaViewModelFactory,
            { kosmos.mediaCarouselInteractor },
        )
    }

    @Test
    fun tutorial_tutorialNotCompletedAndKeyguardVisible_showTutorialContent() =
        kosmos.runTest {
            fakeKeyguardRepository.setKeyguardShowing(true)
            setCommunalEnabled(true)
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_NOT_STARTED
            )

            val communalContent by collectLastValue(underTest.communalContent)

            assertThat(communalContent!!).isNotEmpty()
            communalContent!!.forEach { model ->
                assertThat(model is CommunalContentModel.Tutorial).isTrue()
            }
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2, FLAG_MEDIA_CONTROLS_IN_COMPOSE, FLAG_SCENE_CONTAINER)
    fun ordering_smartspaceBeforeUmoBeforeWidgetsBeforeCtaTile() =
        kosmos.runTest {
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            // Widgets available.
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 0, rank = 30)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, rank = 20)

            // Smartspace available.
            fakeCommunalSmartspaceRepository.setTimers(
                listOf(
                    CommunalSmartspaceTimer(
                        smartspaceTargetId = "target",
                        createdTimestampMillis = 0L,
                        remoteViews = Mockito.mock(RemoteViews::class.java),
                    )
                )
            )

            // Media playing.
            fakeCommunalMediaRepository.mediaActive()

            val communalContent by collectLastValue(underTest.communalContent)

            // Order is smart space, then UMO, widget content and cta tile.
            assertThat(communalContent?.size).isEqualTo(5)
            assertThat(communalContent?.get(0))
                .isInstanceOf(CommunalContentModel.Smartspace::class.java)
            assertThat(communalContent?.get(1)).isInstanceOf(CommunalContentModel.Umo::class.java)
            assertThat(communalContent?.get(2))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
            assertThat(communalContent?.get(3))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
            assertThat(communalContent?.get(4))
                .isInstanceOf(CommunalContentModel.CtaTileInViewMode::class.java)
        }

    /** TODO(b/378171351): Handle ongoing content in responsive grid. */
    @Test
    @DisableFlags(
        FLAG_COMMUNAL_RESPONSIVE_GRID,
        FLAG_GLANCEABLE_HUB_V2,
        FLAG_MEDIA_CONTROLS_IN_COMPOSE,
        FLAG_SCENE_CONTAINER,
    )
    fun ongoingContent_umoAndOneTimer_sizedAppropriately() =
        kosmos.runTest {
            // Widgets available.
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 0, rank = 30)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, rank = 20)

            // Smartspace available.
            fakeCommunalSmartspaceRepository.setTimers(
                listOf(
                    CommunalSmartspaceTimer(
                        smartspaceTargetId = "target",
                        createdTimestampMillis = 0L,
                        remoteViews = Mockito.mock(RemoteViews::class.java),
                    )
                )
            )

            // Media playing.
            fakeCommunalMediaRepository.mediaActive()

            val communalContent by collectLastValue(underTest.communalContent)

            // One timer, UMO, two widgets, and cta.
            assertThat(communalContent?.size).isEqualTo(5)

            val timer = communalContent?.get(0)
            val umo = communalContent?.get(1)

            assertThat(timer).isInstanceOf(CommunalContentModel.Smartspace::class.java)
            assertThat(umo).isInstanceOf(CommunalContentModel.Umo::class.java)

            assertThat(timer?.size).isEqualTo(CommunalContentSize.FixedSize.HALF)
            assertThat(umo?.size).isEqualTo(CommunalContentSize.FixedSize.HALF)
        }

    /** TODO(b/378171351): Handle ongoing content in responsive grid. */
    @Test
    @DisableFlags(
        FLAG_COMMUNAL_RESPONSIVE_GRID,
        FLAG_GLANCEABLE_HUB_V2,
        FLAG_MEDIA_CONTROLS_IN_COMPOSE,
        FLAG_SCENE_CONTAINER,
    )
    fun ongoingContent_umoAndTwoTimers_sizedAppropriately() =
        kosmos.runTest {
            // Widgets available.
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 0, rank = 30)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, rank = 20)

            // Smartspace available.
            fakeCommunalSmartspaceRepository.setTimers(
                listOf(
                    CommunalSmartspaceTimer(
                        smartspaceTargetId = "target",
                        createdTimestampMillis = 0L,
                        remoteViews = Mockito.mock(RemoteViews::class.java),
                    ),
                    CommunalSmartspaceTimer(
                        smartspaceTargetId = "target",
                        createdTimestampMillis = 0L,
                        remoteViews = Mockito.mock(RemoteViews::class.java),
                    ),
                )
            )

            // Media playing.
            fakeCommunalMediaRepository.mediaActive()

            val communalContent by collectLastValue(underTest.communalContent)

            // Two timers, UMO, two widgets, and cta.
            assertThat(communalContent?.size).isEqualTo(6)

            val timer1 = communalContent?.get(0)
            val timer2 = communalContent?.get(1)
            val umo = communalContent?.get(2)

            assertThat(timer1).isInstanceOf(CommunalContentModel.Smartspace::class.java)
            assertThat(timer2).isInstanceOf(CommunalContentModel.Smartspace::class.java)
            assertThat(umo).isInstanceOf(CommunalContentModel.Umo::class.java)

            // One full-sized timer and a half-sized timer and half-sized UMO.
            assertThat(timer1?.size).isEqualTo(CommunalContentSize.FixedSize.HALF)
            assertThat(timer2?.size).isEqualTo(CommunalContentSize.FixedSize.HALF)
            assertThat(umo?.size).isEqualTo(CommunalContentSize.FixedSize.FULL)
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    @EnableFlags(FLAG_MEDIA_CONTROLS_IN_COMPOSE)
    fun communalContent_mediaHostVisible_mediaControlsInCompose_umoIncluded() =
        kosmos.runTest {
            mediaPipelineRepository.addCurrentUserMediaEntry(MediaData().copy(active = true))
            fakeCommunalMediaRepository.mediaActive()

            val communalContent by collectLastValue(underTest.communalContent)
            assertThat(communalContent?.size).isEqualTo(2)
            assertThat(communalContent?.get(0)).isInstanceOf(CommunalContentModel.Umo::class.java)
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2, FLAG_MEDIA_CONTROLS_IN_COMPOSE, FLAG_SCENE_CONTAINER)
    fun communalContent_mediaHostVisible_umoIncluded() =
        kosmos.runTest {
            // Media playing.
            fakeCommunalMediaRepository.mediaActive()

            val communalContent by collectLastValue(underTest.communalContent)
            assertThat(communalContent?.size).isEqualTo(2)
            assertThat(communalContent?.get(0)).isInstanceOf(CommunalContentModel.Umo::class.java)
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2, FLAG_MEDIA_CONTROLS_IN_COMPOSE, FLAG_SCENE_CONTAINER)
    fun communalContent_mediaHostNotVisible_umoExcluded() =
        kosmos.runTest {
            whenever(mediaHost.visible).thenReturn(false)
            mediaHost.updateViewVisibility()
            // Media playing.
            fakeCommunalMediaRepository.mediaActive()

            val communalContent by collectLastValue(underTest.communalContent)
            assertThat(communalContent?.size).isEqualTo(1)
            assertThat(communalContent?.get(0))
                .isNotInstanceOf(CommunalContentModel.Umo::class.java)
        }

    @Test
    fun communalContent_mediaHostVisible_umoToggle() =
        kosmos.runTest {
            mediaHost.updateViewVisibility()
            fakeCommunalMediaRepository.mediaActive()

            val communalContent by collectValues(underTest.communalContent)

            whenever(mediaHost.visible).thenReturn(false)
            mediaHost.updateViewVisibility()

            assertThat(communalContent.size).isEqualTo(1)
        }

    @Test
    fun isEmptyState_isTrue_noWidgetButActiveLiveContent() =
        kosmos.runTest {
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            fakeCommunalWidgetRepository.setCommunalWidgets(emptyList())
            // UMO playing
            fakeCommunalMediaRepository.mediaActive()
            fakeCommunalSmartspaceRepository.setTimers(emptyList())

            val isEmptyState by collectLastValue(underTest.isEmptyState)
            assertThat(isEmptyState).isTrue()
        }

    @Test
    fun isEmptyState_isFalse_withWidgets() =
        kosmos.runTest {
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, rank = 1)
            fakeCommunalMediaRepository.mediaInactive()
            fakeCommunalSmartspaceRepository.setTimers(emptyList())

            val isEmptyState by collectLastValue(underTest.isEmptyState)
            assertThat(isEmptyState).isFalse()
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun dismissCta_hidesCtaTileAndShowsPopup_thenHidesPopupAfterTimeout() =
        kosmos.runTest {
            setIsMainUser(true)
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            val communalContent by collectLastValue(underTest.communalContent)
            val currentPopup by collectLastValue(underTest.currentPopup)

            assertThat(communalContent?.size).isEqualTo(1)
            assertThat(communalContent?.get(0))
                .isInstanceOf(CommunalContentModel.CtaTileInViewMode::class.java)

            underTest.onDismissCtaTile()

            // hide CTA tile and show the popup
            assertThat(communalContent).isEmpty()
            assertThat(currentPopup).isEqualTo(PopupType.CtaTile)

            // hide popup after time elapsed
            advanceTimeBy(POPUP_AUTO_HIDE_TIMEOUT_MS.milliseconds)
            assertThat(currentPopup).isNull()
        }

    @Test
    fun popup_onDismiss_hidesImmediately() =
        kosmos.runTest {
            setIsMainUser(true)
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            val currentPopup by collectLastValue(underTest.currentPopup)

            underTest.onDismissCtaTile()
            assertThat(currentPopup).isEqualTo(PopupType.CtaTile)

            // dismiss the popup directly
            underTest.onHidePopup()
            assertThat(currentPopup).isNull()
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_DIRECT_EDIT_MODE)
    fun customizeWidgetButton_showsThenHidesAfterTimeout() =
        kosmos.runTest {
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )
            val currentPopup by collectLastValue(underTest.currentPopup)

            assertThat(currentPopup).isNull()
            underTest.onLongClick()
            assertThat(currentPopup).isEqualTo(PopupType.CustomizeWidgetButton)
            advanceTimeBy(POPUP_AUTO_HIDE_TIMEOUT_MS.milliseconds)
            assertThat(currentPopup).isNull()
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_DIRECT_EDIT_MODE)
    fun customizeWidgetButton_onDismiss_hidesImmediately() =
        kosmos.runTest {
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )
            val currentPopup by collectLastValue(underTest.currentPopup)

            underTest.onLongClick()
            assertThat(currentPopup).isEqualTo(PopupType.CustomizeWidgetButton)

            underTest.onHidePopup()
            assertThat(currentPopup).isNull()
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_DIRECT_EDIT_MODE)
    fun longClickDirectlyStartsEditMode() =
        kosmos.runTest {
            underTest.onLongClick()
            verify(editWidgetsActivityStarter).startActivity(any())
        }

    @Test
    fun canChangeScene_shadeNotExpanded() =
        kosmos.runTest {
            // On keyguard without any shade expansion.
            fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            runCurrent()
            assertThat(underTest.canChangeScene(CommunalScenes.Communal)).isTrue()
        }

    @Test
    fun canChangeScene_shadeExpanded() =
        kosmos.runTest {
            // On keyguard with shade fully expanded.
            fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
            shadeTestUtil.setLockscreenShadeExpansion(1f)
            runCurrent()
            assertThat(underTest.canChangeScene(CommunalScenes.Communal)).isFalse()
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun canChangeScene_falseTouch() =
        kosmos.runTest {
            setCommunalV2ConfigEnabled(true)
            // On keyguard with shade not expanded.
            fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            fakeFalsingManager.setIsFalseTouch(true)
            runCurrent()
            assertThat(underTest.canChangeScene(CommunalScenes.Communal)).isFalse()
        }

    @Test
    fun touchesAllowed_shadeNotExpanded() =
        kosmos.runTest {
            val touchesAllowed by collectLastValue(underTest.touchesAllowed)

            // On keyguard without any shade expansion.
            fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            runCurrent()
            assertThat(touchesAllowed).isTrue()
        }

    @Test
    fun touchesAllowed_shadeExpanded() =
        kosmos.runTest {
            val touchesAllowed by collectLastValue(underTest.touchesAllowed)

            // On keyguard with shade fully expanded.
            fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
            shadeTestUtil.setLockscreenShadeExpansion(1f)
            runCurrent()
            assertThat(touchesAllowed).isFalse()
        }

    @Test
    fun isFocusable_isFalse_whenTransitioningAwayFromGlanceableHub() =
        kosmos.runTest {
            val isFocusable by collectLastValue(underTest.isFocusable)

            // Shade not expanded.
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            // On communal scene.
            fakeCommunalSceneRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(CommunalScenes.Communal))
            )
            // Open bouncer.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                TransitionStep(
                    from = KeyguardState.GLANCEABLE_HUB,
                    to = KeyguardState.PRIMARY_BOUNCER,
                    transitionState = TransitionState.STARTED,
                )
            )

            fakeKeyguardTransitionRepository.sendTransitionStep(
                from = KeyguardState.GLANCEABLE_HUB,
                to = KeyguardState.PRIMARY_BOUNCER,
                transitionState = TransitionState.RUNNING,
                value = 0.5f,
            )
            assertThat(isFocusable).isEqualTo(false)

            // Transitioned to bouncer.
            fakeKeyguardTransitionRepository.sendTransitionStep(
                from = KeyguardState.GLANCEABLE_HUB,
                to = KeyguardState.PRIMARY_BOUNCER,
                transitionState = TransitionState.FINISHED,
                value = 1f,
            )
            assertThat(isFocusable).isEqualTo(false)
        }

    @Test
    fun isFocusable_isFalse_whenNotOnCommunalScene() =
        kosmos.runTest {
            val isFocusable by collectLastValue(underTest.isFocusable)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GLANCEABLE_HUB,
                testScope = testScope,
            )
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            // Transitioned away from communal scene.
            fakeCommunalSceneRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(CommunalScenes.Blank))
            )

            assertThat(isFocusable).isEqualTo(false)
        }

    @Test
    fun isFocusable_isTrue_whenIdleOnCommunal_andShadeNotExpanded() =
        kosmos.runTest {
            val isFocusable by collectLastValue(underTest.isFocusable)

            // On communal scene.
            fakeCommunalSceneRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(CommunalScenes.Communal))
            )
            // Transitioned to Glanceable hub.
            sceneInteractor.changeScene(Scenes.Communal, "")
            kosmos.setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition =
                    TransitionStep(
                        from = KeyguardState.LOCKSCREEN,
                        to = KeyguardState.GLANCEABLE_HUB,
                    ),
            )
            // Shade not expanded.
            if (!SceneContainerFlag.isEnabled) shadeTestUtil.setLockscreenShadeExpansion(0f)

            assertThat(isFocusable).isEqualTo(true)
        }

    @Test
    fun isFocusable_isFalse_whenQsIsExpanded() =
        kosmos.runTest {
            val isFocusable by collectLastValue(underTest.isFocusable)

            // On communal scene.
            fakeCommunalSceneRepository.setTransitionState(
                flowOf(ObservableTransitionState.Idle(CommunalScenes.Communal))
            )
            // Transitioned to Glanceable hub.
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GLANCEABLE_HUB,
                testScope = testScope,
            )
            // Qs is expanded.
            shadeTestUtil.setQsExpansion(1f)

            assertThat(isFocusable).isEqualTo(false)
        }

    @Test
    fun isCommunalContentFlowFrozen_whenActivityStartedWhileDreaming() =
        kosmos.runTest {
            val isCommunalContentFlowFrozen by
                collectLastValue(underTest.isCommunalContentFlowFrozen)

            // 1. When dreaming not dozing
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            advanceTimeBy(600.milliseconds)

            fakeKeyguardRepository.setDreaming(true)
            fakeKeyguardRepository.setDreamingWithOverlay(true)
            advanceTimeBy(60.milliseconds)
            // And keyguard is occluded by dream
            fakeKeyguardRepository.setKeyguardOccluded(true)

            // And on hub
            sceneInteractor.changeScene(Scenes.Communal, "")
            kosmos.setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition =
                    TransitionStep(from = KeyguardState.DREAMING, to = KeyguardState.GLANCEABLE_HUB),
            )

            // Then flow is not frozen
            assertThat(isCommunalContentFlowFrozen).isEqualTo(false)

            // 2. When dreaming stopped by the new activity about to show on lock screen
            fakeKeyguardRepository.setDreamingWithOverlay(false)
            advanceTimeBy(60.milliseconds)

            // Then flow is frozen
            assertThat(isCommunalContentFlowFrozen).isEqualTo(true)

            // 3. When transitioned to OCCLUDED and activity shows
            sceneInteractor.changeScene(Scenes.Lockscreen, "")
            kosmos.setTransition(
                sceneTransition = Idle(Scenes.Lockscreen),
                stateTransition =
                    TransitionStep(from = KeyguardState.GLANCEABLE_HUB, to = KeyguardState.OCCLUDED),
            )

            // Then flow is not frozen
            assertThat(isCommunalContentFlowFrozen).isEqualTo(false)
        }

    @Test
    fun isCommunalContentFlowFrozen_whenActivityStartedInHandheldMode() =
        kosmos.runTest {
            val isCommunalContentFlowFrozen by
                collectLastValue(underTest.isCommunalContentFlowFrozen)

            // 1. When on keyguard and not occluded
            fakeKeyguardRepository.setKeyguardShowing(true)
            fakeKeyguardRepository.setKeyguardOccluded(false)

            // And transitioned to hub
            kosmos.setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition =
                    TransitionStep(
                        from = KeyguardState.LOCKSCREEN,
                        to = KeyguardState.GLANCEABLE_HUB,
                    ),
            )

            // Then flow is not frozen
            assertThat(isCommunalContentFlowFrozen).isEqualTo(false)

            // 2. When occluded by a new activity
            fakeKeyguardRepository.setKeyguardOccluded(true)
            runCurrent()

            // And transitioning to occluded
            kosmos.setTransition(
                sceneTransition = Transition(from = Scenes.Communal, to = Scenes.Lockscreen),
                stateTransition =
                    TransitionStep(
                        from = KeyguardState.GLANCEABLE_HUB,
                        to = KeyguardState.OCCLUDED,
                        transitionState = TransitionState.STARTED,
                        value = 0f,
                    ),
            )

            // Then flow is frozen
            assertThat(isCommunalContentFlowFrozen).isEqualTo(true)

            // 3. When transition is finished
            kosmos.setTransition(
                sceneTransition = Idle(Scenes.Lockscreen),
                stateTransition =
                    TransitionStep(
                        from = KeyguardState.GLANCEABLE_HUB,
                        to = KeyguardState.OCCLUDED,
                        transitionState = TransitionState.FINISHED,
                        value = 1f,
                    ),
            )

            // Then flow is not frozen
            assertThat(isCommunalContentFlowFrozen).isEqualTo(false)
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun communalContent_emitsFrozenContent_whenFrozen() =
        kosmos.runTest {
            val communalContent by collectLastValue(underTest.communalContent)
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            // When dreaming
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            advanceTimeBy(600.milliseconds)
            fakeKeyguardRepository.setDreaming(true)
            fakeKeyguardRepository.setDreamingWithOverlay(true)
            advanceTimeBy(60.milliseconds)
            fakeKeyguardRepository.setKeyguardOccluded(true)

            // And transitioned to hub
            sceneInteractor.changeScene(Scenes.Communal, "")
            kosmos.setTransition(
                sceneTransition = Idle(Scenes.Communal),
                stateTransition =
                    TransitionStep(from = KeyguardState.DREAMING, to = KeyguardState.GLANCEABLE_HUB),
            )

            // Widgets available
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 0, rank = 30)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, rank = 20)

            // Then hub shows widgets and the CTA tile
            assertThat(communalContent).hasSize(3)

            // When dreaming stopped by another activity which should freeze flow
            fakeKeyguardRepository.setDreamingWithOverlay(false)
            advanceTimeBy(60.milliseconds)

            // New timer available
            fakeCommunalSmartspaceRepository.setTimers(
                listOf(
                    CommunalSmartspaceTimer(
                        smartspaceTargetId = "target",
                        createdTimestampMillis = 0L,
                        remoteViews = Mockito.mock(RemoteViews::class.java),
                    )
                )
            )
            runCurrent()

            // Still only emits widgets and the CTA tile
            assertThat(communalContent).hasSize(3)
            assertThat(communalContent?.get(0))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
            assertThat(communalContent?.get(1))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
            assertThat(communalContent?.get(2))
                .isInstanceOf(CommunalContentModel.CtaTileInViewMode::class.java)
        }

    @Test
    @DisableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun communalContent_emitsLatestContent_whenNotFrozen() =
        kosmos.runTest {
            val communalContent by collectLastValue(underTest.communalContent)
            fakeCommunalTutorialRepository.setTutorialSettingState(
                Settings.Secure.HUB_MODE_TUTORIAL_COMPLETED
            )

            // When dreaming
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(from = DozeStateModel.DOZE, to = DozeStateModel.FINISH)
            )
            advanceTimeBy(600.milliseconds)
            fakeKeyguardRepository.setDreaming(true)
            fakeKeyguardRepository.setDreamingWithOverlay(true)
            advanceTimeBy(60.milliseconds)
            fakeKeyguardRepository.setKeyguardOccluded(true)

            // Transitioned to Glanceable hub.
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.DREAMING,
                to = KeyguardState.GLANCEABLE_HUB,
                testScope = testScope,
            )

            // And widgets available
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 0, rank = 30)
            fakeCommunalWidgetRepository.addWidget(appWidgetId = 1, rank = 20)

            // Then emits widgets and the CTA tile
            assertThat(communalContent).hasSize(3)

            // When new timer available
            fakeCommunalSmartspaceRepository.setTimers(
                listOf(
                    CommunalSmartspaceTimer(
                        smartspaceTargetId = "target",
                        createdTimestampMillis = 0L,
                        remoteViews = Mockito.mock(RemoteViews::class.java),
                    )
                )
            )
            runCurrent()

            // Then emits timer, widgets and the CTA tile
            assertThat(communalContent).hasSize(4)
            assertThat(communalContent?.get(0))
                .isInstanceOf(CommunalContentModel.Smartspace::class.java)
            assertThat(communalContent?.get(1))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
            assertThat(communalContent?.get(2))
                .isInstanceOf(CommunalContentModel.WidgetContent::class.java)
            assertThat(communalContent?.get(3))
                .isInstanceOf(CommunalContentModel.CtaTileInViewMode::class.java)
        }

    @Test
    @DisableFlags(FLAG_MEDIA_CONTROLS_IN_COMPOSE, FLAG_SCENE_CONTAINER)
    fun communalContent_readTriggersUmoVisibilityUpdate() =
        kosmos.runTest {
            verify(mediaHost, never()).updateViewVisibility()

            val communalContent by collectLastValue(underTest.communalContent)

            // updateViewVisibility is called when the flow is collected.
            assertThat(communalContent).isNotNull()
            verify(mediaHost, atLeastOnce()).updateViewVisibility()
        }

    @Test
    fun scrollPosition_persistedOnEditEntry() =
        kosmos.runTest {
            val index = 2
            val offset = 30
            underTest.onScrollPositionUpdated(index, offset)
            underTest.onOpenWidgetEditor(false)

            assertThat(communalInteractor.firstVisibleItemIndex).isEqualTo(index)
            assertThat(communalInteractor.firstVisibleItemOffset).isEqualTo(offset)
        }

    @Test
    fun onTapWidget_logEvent() =
        kosmos.runTest {
            underTest.onTapWidget(ComponentName("test_pkg", "test_cls"), rank = 10)
            verify(metricsLogger).logTapWidget("test_pkg/test_cls", rank = 10)
        }

    @Test
    fun glanceableTouchAvailable_availableWhenNestedScrollingWithoutConsumption() =
        kosmos.runTest {
            val touchAvailable by collectLastValue(underTest.glanceableTouchAvailable)
            assertThat(touchAvailable).isTrue()
            underTest.onHubTouchConsumed()
            assertThat(touchAvailable).isFalse()
            underTest.onNestedScrolling()
            assertThat(touchAvailable).isTrue()
        }

    @Test
    fun selectedKey_changeAffectsAllInstances() =
        kosmos.runTest {
            val model1 = createViewModel()
            val selectedKey1 by collectLastValue(model1.selectedKey)
            val model2 = createViewModel()
            val selectedKey2 by collectLastValue(model2.selectedKey)

            val key = "test"
            model1.setSelectedKey(key)

            assertThat(selectedKey1).isEqualTo(key)
            assertThat(selectedKey2).isEqualTo(key)
        }

    @Test
    fun onShowPreviousMedia_scrollHandler_isCalled() =
        kosmos.runTest {
            underTest.onShowPreviousMedia()
            verify(mediaCarouselScrollHandler).scrollByStep(-1)
        }

    @Test
    fun onShowNextMedia_scrollHandler_isCalled() =
        kosmos.runTest {
            underTest.onShowNextMedia()
            verify(mediaCarouselScrollHandler).scrollByStep(1)
        }

    @Test
    @EnableFlags(FLAG_BOUNCER_UI_REVAMP)
    fun uiIsBlurred_whenPrimaryBouncerIsShowing() =
        kosmos.runTest {
            val viewModel = createViewModel()
            val isUiBlurred by collectLastValue(viewModel.isUiBlurred)
            kosmos.sceneInteractor.changeScene(
                Scenes.Lockscreen,
                "go to lockscreen",
                hideAllOverlays = false,
            )
            kosmos.sceneInteractor.instantlyShowOverlay(Overlays.Bouncer, "go to bouncer")
            kosmos.sceneInteractor.setTransitionState(
                flowOf(
                    ObservableTransitionState.Idle(
                        sceneInteractor.currentScene.value,
                        setOf(Overlays.Bouncer),
                    )
                )
            )
            fakeKeyguardBouncerRepository.setPrimaryShow(true)
            runCurrent()

            assertThat(isUiBlurred).isTrue()

            fakeKeyguardBouncerRepository.setPrimaryShow(false)
            kosmos.sceneInteractor.instantlyHideOverlay(Overlays.Bouncer, "go to bouncer")
            kosmos.sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(sceneInteractor.currentScene.value))
            )
            runCurrent()

            assertThat(isUiBlurred).isFalse()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun uiIsBlurred_whenShadeIsExpanded() =
        kosmos.runTest {
            val viewModel = createViewModel()
            val isUiBlurred by collectLastValue(viewModel.isUiBlurred)

            shadeTestUtil.setShadeExpansion(1.0f)
            assertThat(isUiBlurred).isTrue()

            shadeTestUtil.setShadeExpansion(0.5f)
            assertThat(isUiBlurred).isTrue()

            shadeTestUtil.setShadeExpansion(0.0f)
            assertThat(isUiBlurred).isFalse()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun uiIsBlurred_whenQsIsExpanded() =
        kosmos.runTest {
            val viewModel = createViewModel()
            val isUiBlurred by collectLastValue(viewModel.isUiBlurred)

            shadeTestUtil.setQsExpansion(1.0f)
            assertThat(isUiBlurred).isTrue()

            shadeTestUtil.setQsExpansion(0.5f)
            assertThat(isUiBlurred).isTrue()

            shadeTestUtil.setQsExpansion(0.0f)
            assertThat(isUiBlurred).isFalse()
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun swipeToCommunal() =
        kosmos.runTest {
            setCommunalV2ConfigEnabled(true)
            // Suppress manual opening
            communalSettingsInteractor.setSuppressionReasons(
                listOf(SuppressionReason.ReasonUnknown(FEATURE_MANUAL_OPEN))
            )
            // Shade not expanded
            shadeTestUtil.setLockscreenShadeExpansion(0f)

            val viewModel = createViewModel()
            val swipeToHubEnabled by collectLastValue(viewModel.swipeToHubEnabled)
            assertThat(swipeToHubEnabled).isFalse()

            communalSettingsInteractor.setSuppressionReasons(emptyList())
            assertThat(swipeToHubEnabled).isTrue()

            fakeKeyguardTransitionRepository.sendTransitionStep(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.AOD,
                transitionState = TransitionState.STARTED,
            )
            assertThat(swipeToHubEnabled).isFalse()
        }

    @Test
    @EnableFlags(FLAG_GLANCEABLE_HUB_V2)
    fun swipeToCommunal_falseWhenShadeExpanded() =
        kosmos.runTest {
            fakeKeyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)
            setCommunalV2ConfigEnabled(true)
            fakeKeyguardTransitionRepository.sendTransitionStep(
                from = KeyguardState.AOD,
                to = KeyguardState.LOCKSCREEN,
                transitionState = TransitionState.STARTED,
            )
            // Shade expanded
            shadeTestUtil.setLockscreenShadeExpansion(1f)
            communalSettingsInteractor.setSuppressionReasons(emptyList())

            val viewModel = createViewModel()
            val swipeToHubEnabled by collectLastValue(viewModel.swipeToHubEnabled)
            assertThat(swipeToHubEnabled).isFalse()

            // Shade collapsed
            shadeTestUtil.setLockscreenShadeExpansion(0f)
            assertThat(swipeToHubEnabled).isTrue()
        }

    @Test
    @EnableFlags(FLAG_HUB_EDIT_MODE_TRANSITION)
    fun showBackgroundForEditModeTransition_flagEnabled() =
        kosmos.runTest {
            val showBackground by collectLastValue(underTest.showBackgroundForEditModeTransition)

            // Do not show background when not interacting with edit mode.
            communalSceneInteractor.setEditModeState(null)
            assertThat(showBackground).isFalse()

            // Do not show background yet when edit mode is just starting; user may need to
            // authenticate first.
            communalSceneInteractor.setEditModeState(EditModeState.STARTING)
            assertThat(showBackground).isFalse()

            // Show background when edit mode activity has been created to hide the launching
            // animation below.
            communalSceneInteractor.setEditModeState(EditModeState.CREATED)
            assertThat(showBackground).isTrue()

            // Continue to show background when edit mode activity is showing, though the SystemUI
            // window will be hidden. This ensures that when SystemUI is visible again the
            // background hides the edit mode activity finish animation below.
            communalSceneInteractor.setEditModeState(EditModeState.SHOWING)
            assertThat(showBackground).isTrue()
        }

    @Test
    @DisableFlags(FLAG_HUB_EDIT_MODE_TRANSITION)
    fun showBackgroundForEditModeTransition_flagDisabled_alwaysFalse() =
        kosmos.runTest {
            val showBackground by collectLastValue(underTest.showBackgroundForEditModeTransition)

            communalSceneInteractor.setEditModeState(null)
            assertThat(showBackground).isFalse()

            communalSceneInteractor.setEditModeState(EditModeState.STARTING)
            assertThat(showBackground).isFalse()

            communalSceneInteractor.setEditModeState(EditModeState.CREATED)
            assertThat(showBackground).isFalse()

            communalSceneInteractor.setEditModeState(EditModeState.SHOWING)
            assertThat(showBackground).isFalse()
        }

    private suspend fun setIsMainUser(isMainUser: Boolean) {
        val user = if (isMainUser) MAIN_USER_INFO else SECONDARY_USER_INFO
        with(kosmos.fakeUserRepository) {
            setUserInfos(listOf(user))
            setSelectedUserInfo(user)
        }
        kosmos.fakeUserTracker.set(userInfos = listOf(user), selectedUserIndex = 0)
    }

    private companion object {
        val MAIN_USER_INFO = UserInfo(0, "primary", UserInfo.FLAG_MAIN)
        val SECONDARY_USER_INFO = UserInfo(1, "secondary", 0)

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf(FLAG_COMMUNAL_RESPONSIVE_GRID)
                .andSceneContainer()
        }
    }
}
