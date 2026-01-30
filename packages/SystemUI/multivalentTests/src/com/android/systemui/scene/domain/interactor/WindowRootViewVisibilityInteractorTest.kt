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

package com.android.systemui.scene.domain.interactor

import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.internal.statusbar.IStatusBarService
import com.android.systemui.SysuiTestCase
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.FakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.domain.interactor.PowerInteractor
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.data.repository.WindowRootViewVisibilityRepository
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.NotificationPresenter
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.setActiveNotifs
import com.android.systemui.statusbar.notification.domain.interactor.activeNotificationsInteractor
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.init.NotificationsController
import com.android.systemui.testKosmos
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.argumentCaptor
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class WindowRootViewVisibilityInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val testScope = kosmos.testScope
    private val iStatusBarService = mock<IStatusBarService>()
    private lateinit var executor: FakeExecutor
    private lateinit var windowRootViewVisibilityRepository: WindowRootViewVisibilityRepository
    private val keyguardRepository = FakeKeyguardRepository()
    private val headsUpManager = mock<HeadsUpManager>()
    private val notificationPresenter = mock<NotificationPresenter>()
    private val notificationsController = mock<NotificationsController>()
    private lateinit var powerInteractor: PowerInteractor
    private val activeNotificationsRepository = kosmos.activeNotificationListRepository
    private lateinit var underTest: WindowRootViewVisibilityInteractor

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        executor = kosmos.fakeExecutor
        windowRootViewVisibilityRepository =
            WindowRootViewVisibilityRepository(iStatusBarService, executor)
        powerInteractor = kosmos.powerInteractor

        underTest =
            WindowRootViewVisibilityInteractor(
                    testScope.backgroundScope,
                    windowRootViewVisibilityRepository,
                    keyguardRepository,
                    headsUpManager,
                    powerInteractor,
                    kosmos.activeNotificationsInteractor,
                    kosmos::sceneInteractor,
                )
                .apply { setUp(notificationPresenter, notificationsController) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun isLockscreenOrShadeVisible_true() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isLockscreenOrShadeVisible)
            if (SceneContainerFlag.isEnabled) {
                // Idle: Current scene is Shade
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.Shade))
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Idle: Current scene is Lockscreen
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Idle: NotificationsShade overlay is present
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Idle(
                            currentScene = Scenes.Gone,
                            currentOverlays = setOf(Overlays.NotificationsShade),
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Idle: QuickSettingsShade overlay is present
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Idle(
                            currentScene = Scenes.Gone,
                            currentOverlays = setOf(Overlays.QuickSettingsShade),
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Test Transition States: To Shade
                val arbitraryProgress = flowOf(0.5f)
                val userInputOngoing = flowOf(true)

                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition(
                            fromScene = Scenes.Gone,
                            toScene = Scenes.Shade,
                            currentScene = flowOf(Scenes.Shade),
                            progress = arbitraryProgress,
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = userInputOngoing,
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Transition: From Shade
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition(
                            fromScene = Scenes.Shade,
                            toScene = Scenes.Gone,
                            currentScene = flowOf(Scenes.Gone),
                            progress = arbitraryProgress,
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = userInputOngoing,
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Transition: To Lockscreen
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition(
                            fromScene = Scenes.Gone,
                            toScene = Scenes.Lockscreen,
                            currentScene = flowOf(Scenes.Lockscreen),
                            progress = arbitraryProgress,
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = userInputOngoing,
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Transition: From Lockscreen
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition(
                            fromScene = Scenes.Lockscreen,
                            toScene = Scenes.Gone,
                            currentScene = flowOf(Scenes.Gone),
                            progress = arbitraryProgress,
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = userInputOngoing,
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Transition: To NotificationsShade Overlay
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition.showOverlay(
                            overlay = Overlays.NotificationsShade,
                            fromScene = Scenes.Gone,
                            currentOverlays = flowOf(setOf(Overlays.NotificationsShade)),
                            progress = arbitraryProgress,
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = userInputOngoing,
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()

                // Transition: From QuickSettingsShade Overlay
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition.hideOverlay(
                            overlay = Overlays.QuickSettingsShade,
                            toScene = Scenes.Gone, // The scene to which we return
                            currentOverlays =
                                flowOf(emptySet()), // Overlays after this one is hidden
                            progress = arbitraryProgress,
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = userInputOngoing,
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isTrue()
            } else {
                underTest.setIsLockscreenOrShadeVisible(true)

                assertThat(actual).isTrue()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun isLockscreenOrShadeVisible_false() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isLockscreenOrShadeVisible)
            if (SceneContainerFlag.isEnabled) {

                // Idle: Current scene is Gone
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.Gone))
                )
                testScope.runCurrent()
                assertThat(actual).isFalse()

                // Idle: Current scene is QuickSettings (not an overlay in this context)
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.QuickSettings))
                )
                testScope.runCurrent()
                assertThat(actual).isFalse()

                // Idle: Current scene is Communal
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.Communal))
                )
                testScope.runCurrent()
                assertThat(actual).isFalse()

                // Idle: Current scene is Dream
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.Dream))
                )
                testScope.runCurrent()
                assertThat(actual).isFalse()

                // Transition: To a scene that is not Lockscreen or Shade.
                val arbitraryProgress = flowOf(0.5f)
                val userInputOngoing = flowOf(true)

                kosmos.sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Transition(
                            fromScene = Scenes.Gone,
                            toScene = Scenes.Communal,
                            currentScene = flowOf(Scenes.Communal),
                            progress = arbitraryProgress,
                            isInitiatedByUserInput = true,
                            isUserInputOngoing = userInputOngoing,
                        )
                    )
                )
                testScope.runCurrent()
                assertThat(actual).isFalse()
            } else {
                underTest.setIsLockscreenOrShadeVisible(false)

                assertThat(actual).isFalse()
            }
        }

    @Test
    @DisableSceneContainer
    // When SceneContainerFlag is enabled, setIsLockscreenOrShadeVisible directly
    // no longer controls the interactor's isLockscreenOrShadeVisible flow.
    fun isLockscreenOrShadeVisible_matchesRepo() {
        windowRootViewVisibilityRepository.setIsLockscreenOrShadeVisible(true)

        assertThat(underTest.isLockscreenOrShadeVisible.value).isTrue()

        windowRootViewVisibilityRepository.setIsLockscreenOrShadeVisible(false)

        assertThat(underTest.isLockscreenOrShadeVisible.value).isFalse()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun isLockscreenOrShadeVisibleAndInteractive_notVisible_false() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isLockscreenOrShadeVisibleAndInteractive)
            powerInteractor.setAwakeForTest()

            if (SceneContainerFlag.isEnabled) {
                // When SceneContainerFlag is enabled, setIsLockscreenOrShadeVisible directly
                // no longer controls the interactor's isLockscreenOrShadeVisible flow.
                // Instead, we change the scene to one that results in isLockscreenOrShadeVisible
                // being false.
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.Gone))
                )
                testScope.runCurrent()
            } else {
                underTest.setIsLockscreenOrShadeVisible(false)
            }
            assertThat(actual).isFalse()
        }

    @Test
    fun isLockscreenOrShadeVisibleAndInteractive_deviceAsleep_false() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isLockscreenOrShadeVisibleAndInteractive)
            underTest.setIsLockscreenOrShadeVisible(true)

            powerInteractor.setAsleepForTest()

            assertThat(actual).isFalse()
        }

    @Test
    fun isLockscreenOrShadeVisibleAndInteractive_visibleAndAwake_true() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isLockscreenOrShadeVisibleAndInteractive)

            underTest.setIsLockscreenOrShadeVisible(true)
            powerInteractor.setAwakeForTest()

            assertThat(actual).isTrue()
        }

    @Test
    fun isLockscreenOrShadeVisibleAndInteractive_visibleAndStartingToWake_true() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isLockscreenOrShadeVisibleAndInteractive)

            underTest.setIsLockscreenOrShadeVisible(true)
            powerInteractor.setAwakeForTest()

            assertThat(actual).isTrue()
        }

    @Test
    fun isLockscreenOrShadeVisibleAndStartingToSleep_false() =
        kosmos.runTest {
            val actual by collectLastValue(underTest.isLockscreenOrShadeVisibleAndInteractive)

            underTest.setIsLockscreenOrShadeVisible(true)
            powerInteractor.setAsleepForTest()

            assertThat(actual).isFalse()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun lockscreenShadeInteractive_statusBarServiceNotified() =
        kosmos.runTest {
            underTest.start()

            makeLockscreenShadeVisible()
            testScope.runCurrent()
            executor.runAllReady()

            verify(iStatusBarService).onPanelRevealed(any(), any())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun lockscreenShadeNotInteractive_statusBarServiceNotified() =
        kosmos.runTest {
            underTest.start()
            testScope.runCurrent()

            if (SceneContainerFlag.isEnabled) {
                // Lockscreen/Shade is visible and interactive
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
                )
                testScope.runCurrent()
                powerInteractor.setAwakeForTest()
                testScope.runCurrent()
                executor.runAllReady()

                reset(iStatusBarService)

                // Make the lockscreen/shade "not interactive".
                kosmos.sceneInteractor.setTransitionState(
                    flowOf(ObservableTransitionState.Idle(Scenes.Gone))
                )
                testScope.runCurrent()
                executor.runAllReady()
            } else {
                // First, make the shade visible
                makeLockscreenShadeVisible()
                testScope.runCurrent()
                reset(iStatusBarService)

                // WHEN lockscreen or shade is no longer visible
                underTest.setIsLockscreenOrShadeVisible(false)
                testScope.runCurrent()
                executor.runAllReady()
            }

            // THEN status bar service is notified
            verify(iStatusBarService).onPanelHidden()
        }

    @Test
    fun lockscreenShadeInteractive_presenterCollapsed_notifEffectsNotCleared() =
        kosmos.runTest {
            underTest.start()
            keyguardRepository.setStatusBarState(StatusBarState.SHADE)

            whenever(notificationPresenter.isPresenterFullyCollapsed).thenReturn(true)

            makeLockscreenShadeVisible()

            val shouldClearNotifEffects = argumentCaptor<Boolean>()
            verify(iStatusBarService).onPanelRevealed(shouldClearNotifEffects.capture(), any())
            assertThat(shouldClearNotifEffects.value).isFalse()
        }

    @Test
    fun lockscreenShadeInteractive_nullPresenter_notifEffectsNotCleared() =
        kosmos.runTest {
            underTest.start()
            keyguardRepository.setStatusBarState(StatusBarState.SHADE)

            underTest.setUp(presenter = null, notificationsController)

            makeLockscreenShadeVisible()

            val shouldClearNotifEffects = argumentCaptor<Boolean>()
            verify(iStatusBarService).onPanelRevealed(shouldClearNotifEffects.capture(), any())
            assertThat(shouldClearNotifEffects.value).isFalse()
        }

    @Test
    fun lockscreenShadeInteractive_stateKeyguard_notifEffectsNotCleared() =
        kosmos.runTest {
            underTest.start()
            whenever(notificationPresenter.isPresenterFullyCollapsed).thenReturn(false)

            keyguardRepository.setStatusBarState(StatusBarState.KEYGUARD)

            makeLockscreenShadeVisible()

            val shouldClearNotifEffects = argumentCaptor<Boolean>()
            verify(iStatusBarService).onPanelRevealed(shouldClearNotifEffects.capture(), any())
            assertThat(shouldClearNotifEffects.value).isFalse()
        }

    @Test
    fun lockscreenShadeInteractive_stateShade_presenterNotCollapsed_notifEffectsCleared() =
        kosmos.runTest {
            underTest.start()
            whenever(notificationPresenter.isPresenterFullyCollapsed).thenReturn(false)

            keyguardRepository.setStatusBarState(StatusBarState.SHADE)

            makeLockscreenShadeVisible()

            val shouldClearNotifEffects = argumentCaptor<Boolean>()
            verify(iStatusBarService).onPanelRevealed(shouldClearNotifEffects.capture(), any())
            assertThat(shouldClearNotifEffects.value).isTrue()
        }

    @Test
    fun lockscreenShadeInteractive_stateShadeLocked_presenterNotCollapsed_notifEffectsCleared() =
        kosmos.runTest {
            underTest.start()
            whenever(notificationPresenter.isPresenterFullyCollapsed).thenReturn(false)

            keyguardRepository.setStatusBarState(StatusBarState.SHADE_LOCKED)

            makeLockscreenShadeVisible()

            val shouldClearNotifEffects = argumentCaptor<Boolean>()
            verify(iStatusBarService).onPanelRevealed(shouldClearNotifEffects.capture(), any())
            assertThat(shouldClearNotifEffects.value).isTrue()
        }

    @Test
    fun lockscreenShadeInteractive_hasHeadsUpAndNotifPresenterCollapsed_notifCountOne() =
        kosmos.runTest {
            underTest.start()

            whenever(headsUpManager.hasPinnedHeadsUp()).thenReturn(true)
            whenever(notificationPresenter.isPresenterFullyCollapsed).thenReturn(true)
            activeNotificationsRepository.setActiveNotifs(4)

            makeLockscreenShadeVisible()

            val notifCount = argumentCaptor<Int>()
            verify(iStatusBarService).onPanelRevealed(any(), notifCount.capture())
            assertThat(notifCount.value).isEqualTo(1)
        }

    @Test
    fun lockscreenShadeInteractive_hasHeadsUpAndNullPresenter_notifCountOne() =
        kosmos.runTest {
            underTest.start()

            whenever(headsUpManager.hasPinnedHeadsUp()).thenReturn(true)
            underTest.setUp(presenter = null, notificationsController)

            makeLockscreenShadeVisible()

            val notifCount = argumentCaptor<Int>()
            verify(iStatusBarService).onPanelRevealed(any(), notifCount.capture())
            assertThat(notifCount.value).isEqualTo(1)
        }

    @Test
    fun lockscreenShadeInteractive_noHeadsUp_notifCountMatchesNotifController() =
        kosmos.runTest {
            underTest.start()
            whenever(notificationPresenter.isPresenterFullyCollapsed).thenReturn(true)

            whenever(headsUpManager.hasPinnedHeadsUp()).thenReturn(false)
            activeNotificationsRepository.setActiveNotifs(9)

            makeLockscreenShadeVisible()

            val notifCount = argumentCaptor<Int>()
            verify(iStatusBarService).onPanelRevealed(any(), notifCount.capture())
            assertThat(notifCount.value).isEqualTo(9)
        }

    @Test
    fun lockscreenShadeInteractive_notifPresenterNotCollapsed_notifCountMatchesNotifController() =
        kosmos.runTest {
            underTest.start()
            whenever(headsUpManager.hasPinnedHeadsUp()).thenReturn(true)

            whenever(notificationPresenter.isPresenterFullyCollapsed).thenReturn(false)
            activeNotificationsRepository.setActiveNotifs(8)

            makeLockscreenShadeVisible()

            val notifCount = argumentCaptor<Int>()
            verify(iStatusBarService).onPanelRevealed(any(), notifCount.capture())
            assertThat(notifCount.value).isEqualTo(8)
        }

    @Test
    fun lockscreenShadeInteractive_noHeadsUp_noNotifController_notifCountZero() =
        kosmos.runTest {
            underTest.start()
            whenever(notificationPresenter.isPresenterFullyCollapsed).thenReturn(true)

            whenever(headsUpManager.hasPinnedHeadsUp()).thenReturn(false)
            underTest.setUp(notificationPresenter, notificationsController = null)

            makeLockscreenShadeVisible()

            val notifCount = argumentCaptor<Int>()
            verify(iStatusBarService).onPanelRevealed(any(), notifCount.capture())
            assertThat(notifCount.value).isEqualTo(0)
        }

    private fun makeLockscreenShadeVisible() {
        if (SceneContainerFlag.isEnabled) {
            kosmos.sceneInteractor.setTransitionState(
                flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
            )
        } else {
            underTest.setIsLockscreenOrShadeVisible(true)
        }

        powerInteractor.setAwakeForTest()
        testScope.runCurrent()
        executor.runAllReady()
    }
}
