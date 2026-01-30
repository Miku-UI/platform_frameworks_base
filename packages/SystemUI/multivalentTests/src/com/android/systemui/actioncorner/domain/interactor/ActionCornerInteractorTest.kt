/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.actioncorner.domain.interactor

import android.provider.Settings.Secure.ACTION_CORNER_ACTION_HOME
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_LOCKSCREEN
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_NOTIFICATIONS
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_OVERVIEW
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_QUICK_SETTINGS
import android.provider.Settings.Secure.ACTION_CORNER_BOTTOM_LEFT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_BOTTOM_RIGHT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_TOP_LEFT_ACTION
import android.provider.Settings.Secure.ACTION_CORNER_TOP_RIGHT_ACTION
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.LauncherProxyService
import com.android.systemui.SysuiTestCase
import com.android.systemui.actioncorner.data.model.ActionCornerRegion
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_LEFT
import com.android.systemui.actioncorner.data.model.ActionCornerRegion.BOTTOM_RIGHT
import com.android.systemui.actioncorner.data.model.ActionCornerState.ActiveActionCorner
import com.android.systemui.actioncorner.data.repository.ActionCornerSettingRepository
import com.android.systemui.actioncorner.data.repository.FakeActionCornerRepository
import com.android.systemui.inputdevice.data.repository.FakePointerDeviceRepository
import com.android.systemui.keyguard.domain.interactor.windowManagerLockscreenVisibilityInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionStep
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.setTransition
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.HOME
import com.android.systemui.shared.system.actioncorner.ActionCornerConstants.OVERVIEW
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.policy.data.repository.fakeUserSetupRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.data.repository.userAwareSecureSettingsRepository
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ActionCornerInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.actionCornerRepository by Fixture { FakeActionCornerRepository() }

    private val settingsRepository = kosmos.userAwareSecureSettingsRepository
    private val Kosmos.actionCornerSettingRepository by Fixture {
        ActionCornerSettingRepository(settingsRepository, testScope.backgroundScope, testDispatcher)
    }

    private val Kosmos.launcherProxyService by Fixture { mock<LauncherProxyService>() }
    private val Kosmos.windowManager by Fixture { mock<IWindowManager>() }
    private val Kosmos.commandQueue by Fixture { mock<CommandQueue>() }
    private val Kosmos.fakePointerRepository by Fixture { FakePointerDeviceRepository() }

    private val Kosmos.underTest by Fixture {
        ActionCornerInteractor(
            actionCornerRepository,
            launcherProxyService,
            actionCornerSettingRepository,
            fakePointerRepository,
            windowManagerLockscreenVisibilityInteractor,
            fakeUserSetupRepository,
            commandQueue,
            windowManager,
        )
    }

    @Before
    fun setUp() {
        kosmos.fakeUserSetupRepository.setUserSetUp(true)
        kosmos.fakePointerRepository.setIsAnyPointerConnected(true)
        kosmos.underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun bottomLeftCornerActivated_overviewActionConfigured_notifyLauncherOfOverviewAction() =
        unlockScreenAndRunTest {
            settingsRepository.setInt(
                ACTION_CORNER_BOTTOM_LEFT_ACTION,
                ACTION_CORNER_ACTION_OVERVIEW,
            )
            actionCornerRepository.addState(ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY))
            verify(launcherProxyService).onActionCornerActivated(OVERVIEW, DEFAULT_DISPLAY)
        }

    @Test
    fun bottomRightCornerActivated_homeActionConfigured_notifyLauncherOfHomeAction() =
        unlockScreenAndRunTest {
            settingsRepository.setInt(ACTION_CORNER_BOTTOM_RIGHT_ACTION, ACTION_CORNER_ACTION_HOME)
            actionCornerRepository.addState(ActiveActionCorner(BOTTOM_RIGHT, DEFAULT_DISPLAY))
            verify(launcherProxyService).onActionCornerActivated(HOME, DEFAULT_DISPLAY)
        }

    @Test
    fun topLeftCornerActivated_notificationsActionConfigured_toggleNotificationShade() =
        unlockScreenAndRunTest {
            settingsRepository.setInt(
                ACTION_CORNER_TOP_LEFT_ACTION,
                ACTION_CORNER_ACTION_NOTIFICATIONS,
            )

            actionCornerRepository.addState(
                ActiveActionCorner(ActionCornerRegion.TOP_LEFT, DEFAULT_DISPLAY)
            )

            verify(commandQueue).toggleNotificationsPanel()
        }

    @Test
    fun topRightCornerActivated_qsActionConfigured_toggleQsPanel() = unlockScreenAndRunTest {
        settingsRepository.setInt(
            ACTION_CORNER_TOP_RIGHT_ACTION,
            ACTION_CORNER_ACTION_QUICK_SETTINGS,
        )

        actionCornerRepository.addState(
            ActiveActionCorner(ActionCornerRegion.TOP_RIGHT, DEFAULT_DISPLAY)
        )

        verify(commandQueue).toggleQuickSettingsPanel()
    }

    @Test
    fun actionCornerActivated_lockscreenActionConfigured_lockScreen() = unlockScreenAndRunTest {
        settingsRepository.setInt(
            ACTION_CORNER_BOTTOM_RIGHT_ACTION,
            ACTION_CORNER_ACTION_LOCKSCREEN,
        )
        actionCornerRepository.addState(ActiveActionCorner(BOTTOM_RIGHT, DEFAULT_DISPLAY))
        verify(windowManager).lockNow(eq(null))
    }

    @Test
    fun userNotSetUp_overviewActionConfigured_actionCornerActivated_actionNotTriggered() =
        unlockScreenAndRunTest {
            settingsRepository.setInt(
                ACTION_CORNER_BOTTOM_LEFT_ACTION,
                ACTION_CORNER_ACTION_OVERVIEW,
            )
            fakeUserSetupRepository.setUserSetUp(false)
            actionCornerRepository.addState(ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY))
            verify(launcherProxyService, never()).onActionCornerActivated(OVERVIEW, DEFAULT_DISPLAY)
        }

    @Test
    fun lockscreenVisible_overviewActionConfigured_actionCornerActivated_actionNotTriggered() =
        kosmos.runTest {
            // We are in lockscreen by default
            settingsRepository.setInt(
                ACTION_CORNER_BOTTOM_LEFT_ACTION,
                ACTION_CORNER_ACTION_OVERVIEW,
            )

            actionCornerRepository.addState(ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY))
            verify(launcherProxyService, never()).onActionCornerActivated(OVERVIEW, DEFAULT_DISPLAY)
        }

    @Test
    fun noActionConfigured_cursorMovesIntoActiveArea_actionNotTriggered() = unlockScreenAndRunTest {
        // No action configured to corners by default
        actionCornerRepository.addState(ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY))
        verify(launcherProxyService, never()).onActionCornerActivated(OVERVIEW, DEFAULT_DISPLAY)
    }

    @Test
    fun activeActionCorner_pointerDeviceDisconnected_actionNotTriggered() = unlockScreenAndRunTest {
        settingsRepository.setInt(ACTION_CORNER_BOTTOM_LEFT_ACTION, ACTION_CORNER_ACTION_OVERVIEW)
        fakePointerRepository.setIsAnyPointerConnected(false)

        actionCornerRepository.addState(ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY))
        verify(launcherProxyService, never()).onActionCornerActivated(OVERVIEW, DEFAULT_DISPLAY)
    }

    @Test
    fun activeActionCorner_lockscreen_actionNotReTriggeredAfterUnlock() = unlockScreenAndRunTest {
        settingsRepository.setInt(ACTION_CORNER_BOTTOM_LEFT_ACTION, ACTION_CORNER_ACTION_OVERVIEW)
        actionCornerRepository.addState(ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY))

        setTransition(
            sceneTransition = Idle(Scenes.Lockscreen),
            stateTransition =
                TransitionStep(from = KeyguardState.GONE, to = KeyguardState.LOCKSCREEN),
        )
        setTransition(
            sceneTransition = Idle(Scenes.Gone),
            stateTransition =
                TransitionStep(from = KeyguardState.LOCKSCREEN, to = KeyguardState.GONE),
        )
        actionCornerRepository.addState(ActiveActionCorner(BOTTOM_LEFT, DEFAULT_DISPLAY))

        verify(launcherProxyService, times(1)).onActionCornerActivated(OVERVIEW, DEFAULT_DISPLAY)
    }

    private fun unlockScreenAndRunTest(testBody: suspend Kosmos.() -> Unit) =
        kosmos.runTest {
            setTransition(
                sceneTransition = Idle(Scenes.Gone),
                stateTransition =
                    TransitionStep(from = KeyguardState.LOCKSCREEN, to = KeyguardState.GONE),
            )
            testBody()
        }
}
