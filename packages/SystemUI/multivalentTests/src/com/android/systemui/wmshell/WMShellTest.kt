/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.wmshell

import android.content.pm.UserInfo
import android.graphics.Color
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.keyguardUpdateMonitor
import com.android.systemui.Flags.FLAG_COMMUNAL_HUB
import com.android.systemui.Flags.FLAG_GLANCEABLE_HUB_BLURRED_BACKGROUND
import com.android.systemui.Flags.FLAG_SHADE_APP_LAUNCH_ANIMATION_SKIP_IN_DESKTOP
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.domain.interactor.setCommunalAvailable
import com.android.systemui.communal.ui.viewmodel.communalTransitionViewModel
import com.android.systemui.communal.util.fakeCommunalColors
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.display.data.repository.createPerDisplayInstanceSysUIStateRepository
import com.android.systemui.flags.Flags.COMMUNAL_SERVICE_ENABLED
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.ScreenLifecycle
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.wakefulnessLifecycle
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.model.sysUiState
import com.android.systemui.notetask.NoteTaskInitializer
import com.android.systemui.settings.FakeDisplayTracker
import com.android.systemui.settings.userTracker
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.commandQueue
import com.android.systemui.statusbar.commandline.commandRegistry
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.kotlin.javaAdapter
import com.android.wm.shell.desktopmode.DesktopMode
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.data.DesktopRepository.VisibleTasksListener
import com.android.wm.shell.onehanded.OneHanded
import com.android.wm.shell.onehanded.OneHandedEventCallback
import com.android.wm.shell.onehanded.OneHandedTransitionCallback
import com.android.wm.shell.pip.Pip
import com.android.wm.shell.recents.RecentTasks
import com.android.wm.shell.splitscreen.SplitScreen
import com.android.wm.shell.sysui.ShellInterface
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.concurrent.Executor
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

/**
 * Tests for [WMShell].
 *
 * Build/Install/Run: atest SystemUITests:WMShellTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class WMShellTest : SysuiTestCase() {
    val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.pip by Kosmos.Fixture { mock<Pip>() }
    private val Kosmos.oneHanded by Kosmos.Fixture { mock<OneHanded>() }
    private val Kosmos.desktopMode by Kosmos.Fixture { mock<DesktopMode>() }
    private val Kosmos.recentTasks by Kosmos.Fixture { mock<RecentTasks>() }
    private val Kosmos.screenLifecycle by Kosmos.Fixture { mock<ScreenLifecycle>() }
    private val Kosmos.displayTracker by Kosmos.Fixture { FakeDisplayTracker(context) }
    private val Kosmos.shellInterface by Kosmos.Fixture { mock<ShellInterface>() }
    private val Kosmos.perDisplayRepository by
        Kosmos.Fixture { createPerDisplayInstanceSysUIStateRepository() }

    private val Kosmos.underTest by
        Kosmos.Fixture {
            WMShell(
                /* context = */ context,
                /* shell = */ shellInterface,
                /* pipOptional = */ Optional.of(pip),
                /* splitScreenOptional = */ Optional.of(mock<SplitScreen>()),
                /* oneHandedOptional = */ Optional.of(oneHanded),
                /* desktopMode = */ Optional.of(desktopMode),
                /* recentTasks = */ Optional.of(recentTasks),
                /* commandQueue = */ commandQueue,
                /* commandRegistry = */ commandRegistry,
                /* configurationController = */ configurationController,
                /* keyguardStateController = */ keyguardStateController,
                /* keyguardUpdateMonitor = */ keyguardUpdateMonitor,
                /* screenLifecycle = */ screenLifecycle,
                /* sysUiState = */ sysUiState,
                /* wakefulnessLifecycle = */ wakefulnessLifecycle,
                /* userTracker = */ userTracker,
                /* displayTracker = */ displayTracker,
                /* noteTaskInitializer = */ mock<NoteTaskInitializer>(),
                /* communalTransitionViewModel = */ communalTransitionViewModel,
                /* javaAdapter = */ javaAdapter,
                /* sysUiMainExecutor = */ fakeExecutor,
                /* perDisplayRepository= */ perDisplayRepository,
            )
        }

    @Before
    fun setUp() {
        kosmos.fakeUserRepository.setUserInfos(listOf(MAIN_USER_INFO))
        kosmos.fakeFeatureFlagsClassic.set(COMMUNAL_SERVICE_ENABLED, true)
    }

    @Test
    fun initPip_registersCommandQueueCallback() =
        kosmos.runTest {
            underTest.initPip(pip)
            verify(commandQueue).addCallback(any(CommandQueue.Callbacks::class.java))
        }

    @Test
    fun initOneHanded_registersCallbacks() =
        kosmos.runTest {
            underTest.initOneHanded(oneHanded)
            verify(commandQueue).addCallback(any(CommandQueue.Callbacks::class.java))
            verify(screenLifecycle).addObserver(any(ScreenLifecycle.Observer::class.java))
            verify(oneHanded)
                .registerTransitionCallback(any(OneHandedTransitionCallback::class.java))
            verify(oneHanded).registerEventCallback(any(OneHandedEventCallback::class.java))
        }

    @Test
    fun initDesktopMode_registersListener() =
        kosmos.runTest {
            underTest.initDesktopMode(desktopMode)
            verify(desktopMode)
                .addVisibleTasksListener(
                    any(VisibleTasksListener::class.java),
                    any(Executor::class.java),
                )
            verify(desktopMode)
                .addDeskChangeListener(
                    any(DesktopRepository.DeskChangeListener::class.java),
                    any(Executor::class.java),
                )
        }

    @Test
    @EnableFlags(FLAG_SHADE_APP_LAUNCH_ANIMATION_SKIP_IN_DESKTOP)
    fun onActiveDeskChanged_enterDesktop_desktopStateIsActive() =
        kosmos.runTest {
            val displayId = Display.DEFAULT_DISPLAY
            val displaySysUiState = perDisplayRepository[displayId]
            underTest.initDesktopMode(desktopMode)
            val listenerCaptor =
                ArgumentCaptor.forClass(DesktopRepository.DeskChangeListener::class.java)
            verify(desktopMode)
                .addDeskChangeListener(listenerCaptor.capture(), any(Executor::class.java))
            val listener = listenerCaptor.value
            displaySysUiState
                ?.setFlag(SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE, false)
                ?.commitUpdate()

            listener.onActiveDeskChanged(
                displayId,
                newActiveDeskId = 1,
                oldActiveDeskId = DesktopRepository.INVALID_DESK_ID,
            )
            fakeExecutor.runAllReady()

            assertThat(
                    displaySysUiState?.isFlagEnabled(SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE)
                )
                .isTrue()
        }

    @Test
    @EnableFlags(FLAG_SHADE_APP_LAUNCH_ANIMATION_SKIP_IN_DESKTOP)
    fun onActiveDeskChanged_exitDesktop_desktopStateIsNotActive() =
        kosmos.runTest {
            val displayId = Display.DEFAULT_DISPLAY
            val displaySysUiState = perDisplayRepository[displayId]
            underTest.initDesktopMode(desktopMode)
            val listenerCaptor =
                ArgumentCaptor.forClass(DesktopRepository.DeskChangeListener::class.java)
            verify(desktopMode)
                .addDeskChangeListener(listenerCaptor.capture(), any(Executor::class.java))
            val listener = listenerCaptor.value
            displaySysUiState
                ?.setFlag(SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE, true)
                ?.commitUpdate()

            listener.onActiveDeskChanged(
                displayId,
                newActiveDeskId = DesktopRepository.INVALID_DESK_ID,
                oldActiveDeskId = 1,
            )
            fakeExecutor.runAllReady()

            assertThat(
                    displaySysUiState?.isFlagEnabled(SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE)
                )
                .isFalse()
        }

    @Test
    @EnableFlags(FLAG_SHADE_APP_LAUNCH_ANIMATION_SKIP_IN_DESKTOP)
    fun onActiveDeskChanged_stayInDesktop_desktopStateIsActive() =
        kosmos.runTest {
            val displayId = Display.DEFAULT_DISPLAY
            val displaySysUiState = perDisplayRepository[displayId]
            underTest.initDesktopMode(desktopMode)
            val listenerCaptor =
                ArgumentCaptor.forClass(DesktopRepository.DeskChangeListener::class.java)
            verify(desktopMode)
                .addDeskChangeListener(listenerCaptor.capture(), any(Executor::class.java))
            val listener = listenerCaptor.value
            displaySysUiState
                ?.setFlag(SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE, true)
                ?.commitUpdate()

            listener.onActiveDeskChanged(displayId, newActiveDeskId = 2, oldActiveDeskId = 1)
            fakeExecutor.runAllReady()

            assertThat(
                    displaySysUiState?.isFlagEnabled(SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE)
                )
                .isTrue()
        }

    @Test
    @EnableFlags(FLAG_SHADE_APP_LAUNCH_ANIMATION_SKIP_IN_DESKTOP)
    fun onActiveDeskChanged_stayOutsideDesktop_desktopStateIsNotActive() =
        kosmos.runTest {
            val displayId = Display.DEFAULT_DISPLAY
            val displaySysUiState = perDisplayRepository[displayId]
            underTest.initDesktopMode(desktopMode)
            val listenerCaptor =
                ArgumentCaptor.forClass(DesktopRepository.DeskChangeListener::class.java)
            verify(desktopMode)
                .addDeskChangeListener(listenerCaptor.capture(), any(Executor::class.java))
            val listener = listenerCaptor.value
            displaySysUiState
                ?.setFlag(SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE, false)
                ?.commitUpdate()

            listener.onActiveDeskChanged(
                displayId,
                newActiveDeskId = DesktopRepository.INVALID_DESK_ID,
                oldActiveDeskId = DesktopRepository.INVALID_DESK_ID,
            )
            fakeExecutor.runAllReady()

            assertThat(
                    displaySysUiState?.isFlagEnabled(SYSUI_STATE_FREEFORM_ACTIVE_IN_DESKTOP_MODE)
                )
                .isFalse()
        }

    @Test
    fun initRecentTasks_registersListener() =
        kosmos.runTest {
            underTest.initRecentTasks(recentTasks)
            verify(recentTasks).addAnimationStateListener(any(Executor::class.java), any())
        }

    @Test
    @EnableFlags(FLAG_COMMUNAL_HUB)
    @DisableFlags(FLAG_GLANCEABLE_HUB_BLURRED_BACKGROUND)
    fun initRecentTasks_setRecentsBackgroundColorWhenCommunal() =
        kosmos.runTest {
            val black = Color.valueOf(Color.BLACK)
            fakeCommunalColors.setBackgroundColor(black)

            fakeKeyguardRepository.setKeyguardShowing(false)

            underTest.initRecentTasks(recentTasks)
            verify(recentTasks).setTransitionBackgroundColor(null)
            verify(recentTasks, never()).setTransitionBackgroundColor(black)

            // Transition to occluded from the glanceable hub.
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.GLANCEABLE_HUB,
                to = KeyguardState.OCCLUDED,
                testScope,
            )
            setCommunalAvailable(true)

            verify(recentTasks).setTransitionBackgroundColor(black)
        }

    @Test
    fun init_ensureUserChangeCallback() =
        kosmos.runTest {
            val userId = userTracker.userId

            underTest.start()

            verify(shellInterface)
                .onUserChanged(eq(userId), argThat { context -> context.userId == userId })
        }

    private companion object {
        val MAIN_USER_INFO = UserInfo(0, "primary", UserInfo.FLAG_MAIN)
    }
}
