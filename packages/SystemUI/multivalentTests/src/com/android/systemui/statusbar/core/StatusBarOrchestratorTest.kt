/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.core

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.plugins.mockPluginDependencyProvider
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.power.shared.model.WakeSleepReason
import com.android.systemui.power.shared.model.WakefulnessState
import com.android.systemui.shade.mockShadeSurface
import com.android.systemui.statusbar.data.model.StatusBarMode
import com.android.systemui.statusbar.data.model.StatusBarMode.LIGHTS_OUT
import com.android.systemui.statusbar.data.model.StatusBarMode.LIGHTS_OUT_TRANSPARENT
import com.android.systemui.statusbar.data.model.StatusBarMode.OPAQUE
import com.android.systemui.statusbar.data.model.StatusBarMode.SEMI_TRANSPARENT
import com.android.systemui.statusbar.data.model.StatusBarMode.TRANSPARENT
import com.android.systemui.statusbar.data.repository.fakeStatusBarModePerDisplayRepository
import com.android.systemui.statusbar.phone.PhoneStatusBarTransitions
import com.android.systemui.statusbar.phone.mockAutoHideController
import com.android.systemui.statusbar.phone.ui.statusBarIconController
import com.android.systemui.statusbar.policy.fakeConfigurationController
import com.android.systemui.statusbar.window.data.repository.fakeStatusBarWindowStatePerDisplayRepository
import com.android.systemui.statusbar.window.fakeStatusBarWindowController
import com.android.systemui.statusbar.window.shared.model.StatusBarWindowState
import com.android.systemui.testKosmos
import com.android.wm.shell.bubbles.bubbles
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarOrchestratorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val fakeStatusBarModePerDisplayRepository = kosmos.fakeStatusBarModePerDisplayRepository
    private val mockPluginDependencyProvider = kosmos.mockPluginDependencyProvider
    private val mockShadeSurface = kosmos.mockShadeSurface
    private val fakeBouncerRepository = kosmos.fakeKeyguardBouncerRepository
    private val fakeStatusBarWindowStatePerDisplayRepository =
        kosmos.fakeStatusBarWindowStatePerDisplayRepository
    private val fakePowerRepository = kosmos.fakePowerRepository
    private val mockBubbles = kosmos.bubbles
    private val fakeStatusBarWindowController = kosmos.fakeStatusBarWindowController
    private val fakeStatusBarInitializer = kosmos.fakeStatusBarInitializer
    private val dumpManager = kosmos.dumpManager

    private val orchestrator = kosmos.statusBarOrchestrator

    @Test
    fun start_setsUpPluginDependencies() {
        orchestrator.start()

        verify(mockPluginDependencyProvider).allowPluginDependency(DarkIconDispatcher::class.java)
        verify(mockPluginDependencyProvider)
            .allowPluginDependency(StatusBarStateController::class.java)
    }

    @Test
    fun start_attachesWindow() {
        orchestrator.start()

        assertThat(fakeStatusBarWindowController.isAttached).isTrue()
    }

    @Test
    fun start_updatesShadeExpansion() {
        orchestrator.start()

        verify(mockShadeSurface).updateExpansionAndVisibility()
    }

    @Test
    fun bouncerShowing_setsImportanceForA11yToNoHideDescendants() =
        testScope.runTest {
            orchestrator.start()

            fakeBouncerRepository.setPrimaryShow(isShowing = true)

            verify(fakeStatusBarInitializer.statusBarViewController)
                .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
        }

    @Test
    fun bouncerNotShowing_setsImportanceForA11yToNoHideDescendants() =
        testScope.runTest {
            orchestrator.start()

            fakeBouncerRepository.setPrimaryShow(isShowing = false)

            verify(fakeStatusBarInitializer.statusBarViewController)
                .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO)
        }

    @Test
    fun deviceGoesToSleep_barTransitionsAnimationsAreFinished() =
        testScope.runTest {
            putDeviceToSleep()

            orchestrator.start()

            verify(fakeStatusBarInitializer.statusBarTransitions).finishAnimations()
        }

    @Test
    fun deviceIsAwake_barTransitionsAnimationsAreNotFinished() =
        testScope.runTest {
            awakeDevice()

            orchestrator.start()

            verify(fakeStatusBarInitializer.statusBarTransitions, never()).finishAnimations()
        }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_ALWAYS_SCHEDULE_AUTO_HIDE)
    fun autoHide_invokedWhenBarModeChanges_flagOff() =
        kosmos.runTest {
            setStatusBarMode(TRANSPARENT)
            orchestrator.start()
            reset(mockAutoHideController)

            setStatusBarMode(SEMI_TRANSPARENT)

            verify(mockAutoHideController).touchAutoHide()

            reset(mockAutoHideController)
            setStatusBarMode(OPAQUE)

            verify(mockAutoHideController).touchAutoHide()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_ALWAYS_SCHEDULE_AUTO_HIDE)
    fun autoHide_invokedWhenBarModeChanges_flagOn() =
        kosmos.runTest {
            setStatusBarMode(TRANSPARENT)
            orchestrator.start()
            reset(mockAutoHideController)

            setStatusBarMode(SEMI_TRANSPARENT)

            verify(mockAutoHideController).touchAutoHide()

            reset(mockAutoHideController)
            setStatusBarMode(OPAQUE)

            verify(mockAutoHideController).touchAutoHide()
        }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_ALWAYS_SCHEDULE_AUTO_HIDE)
    fun autoHide_invokedWhenTransitionsChanges_flagOff() =
        kosmos.runTest {
            setStatusBarMode(TRANSPARENT)
            orchestrator.start()
            reset(mockAutoHideController)

            fakeStatusBarInitializer.setNewTransitions(mock<PhoneStatusBarTransitions>())

            verify(mockAutoHideController).touchAutoHide()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_ALWAYS_SCHEDULE_AUTO_HIDE)
    fun autoHide_invokedWhenTransitionsChanges_flagOn() =
        kosmos.runTest {
            setStatusBarMode(TRANSPARENT)
            orchestrator.start()
            reset(mockAutoHideController)

            fakeStatusBarInitializer.setNewTransitions(mock<PhoneStatusBarTransitions>())

            verify(mockAutoHideController).touchAutoHide()
        }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_ALWAYS_SCHEDULE_AUTO_HIDE)
    fun autoHide_notInvokedWhenAnimateChanges_flagOff() =
        kosmos.runTest {
            setStatusBarMode(TRANSPARENT)
            setStatusBarWindowState(StatusBarWindowState.Showing)
            orchestrator.start()
            reset(mockAutoHideController)

            // Changing the window state will affect the `shouldAnimateNextBarModeChange` value
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            verify(mockAutoHideController, never()).touchAutoHide()
        }

    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_ALWAYS_SCHEDULE_AUTO_HIDE)
    fun autoHide_notInvokedWhenAnimateChanges_flagOn() =
        kosmos.runTest {
            setStatusBarMode(TRANSPARENT)
            setStatusBarWindowState(StatusBarWindowState.Showing)
            orchestrator.start()
            reset(mockAutoHideController)

            // Changing the window state will affect the `shouldAnimateNextBarModeChange` value
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            verify(mockAutoHideController, never()).touchAutoHide()
        }

    @Test
    @DisableFlags(Flags.FLAG_STATUS_BAR_ALWAYS_SCHEDULE_AUTO_HIDE)
    fun autoHide_flagOff_notInvokedWhenTransientShownStateChanges() =
        kosmos.runTest {
            setStatusBarMode(TRANSPARENT)
            orchestrator.start()
            reset(mockAutoHideController)

            setTransientStatusBar()

            verify(mockAutoHideController, never()).touchAutoHide()

            reset(mockAutoHideController)
            abortTransientStatusBar()

            verify(mockAutoHideController, never()).touchAutoHide()
        }

    /** Regression test for b/428659575. */
    @Test
    @EnableFlags(Flags.FLAG_STATUS_BAR_ALWAYS_SCHEDULE_AUTO_HIDE)
    fun autoHide_flagOn_invokedWhenTransientShownStateChanges() =
        kosmos.runTest {
            setStatusBarMode(TRANSPARENT)
            orchestrator.start()
            reset(mockAutoHideController)

            setTransientStatusBar()

            verify(mockAutoHideController).touchAutoHide()

            reset(mockAutoHideController)
            abortTransientStatusBar()

            verify(mockAutoHideController).touchAutoHide()
        }

    @Test
    fun statusBarVisible_notifiesBubbles() =
        testScope.runTest {
            setStatusBarMode(TRANSPARENT)
            setStatusBarWindowState(StatusBarWindowState.Showing)

            orchestrator.start()

            verify(mockBubbles).onStatusBarVisibilityChanged(/* visible= */ true)
        }

    @Test
    fun statusBarInLightsOutMode_notifiesBubblesWithStatusBarInvisible() =
        testScope.runTest {
            setStatusBarMode(LIGHTS_OUT)
            setStatusBarWindowState(StatusBarWindowState.Showing)

            orchestrator.start()

            verify(mockBubbles).onStatusBarVisibilityChanged(/* visible= */ false)
        }

    @Test
    fun statusBarInLightsOutTransparentMode_notifiesBubblesWithStatusBarInvisible() =
        testScope.runTest {
            setStatusBarMode(LIGHTS_OUT_TRANSPARENT)
            setStatusBarWindowState(StatusBarWindowState.Showing)

            orchestrator.start()

            verify(mockBubbles).onStatusBarVisibilityChanged(/* visible= */ false)
        }

    @Test
    fun statusBarWindowNotShowing_notifiesBubblesWithStatusBarInvisible() =
        testScope.runTest {
            setStatusBarMode(TRANSPARENT)
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            orchestrator.start()

            verify(mockBubbles).onStatusBarVisibilityChanged(/* visible= */ false)
        }

    @Test
    fun statusBarModeChange_transitionsToModeWithAnimation() =
        testScope.runTest {
            awakeDevice()
            abortTransientStatusBar()
            setStatusBarWindowState(StatusBarWindowState.Showing)
            setStatusBarMode(TRANSPARENT)

            orchestrator.start()

            verify(fakeStatusBarInitializer.statusBarTransitions)
                .transitionTo(TRANSPARENT.toTransitionModeInt(), /* animate= */ true)
        }

    @Test
    fun statusBarModeChange_keepsTransitioningAsModeChanges() =
        testScope.runTest {
            awakeDevice()
            abortTransientStatusBar()
            setStatusBarWindowState(StatusBarWindowState.Showing)
            setStatusBarMode(TRANSPARENT)

            orchestrator.start()

            verify(fakeStatusBarInitializer.statusBarTransitions)
                .transitionTo(TRANSPARENT.toTransitionModeInt(), /* animate= */ true)

            setStatusBarMode(OPAQUE)
            verify(fakeStatusBarInitializer.statusBarTransitions)
                .transitionTo(OPAQUE.toTransitionModeInt(), /* animate= */ true)

            setStatusBarMode(LIGHTS_OUT)
            verify(fakeStatusBarInitializer.statusBarTransitions)
                .transitionTo(LIGHTS_OUT.toTransitionModeInt(), /* animate= */ true)

            setStatusBarMode(LIGHTS_OUT_TRANSPARENT)
            verify(fakeStatusBarInitializer.statusBarTransitions)
                .transitionTo(LIGHTS_OUT_TRANSPARENT.toTransitionModeInt(), /* animate= */ true)
        }

    @Test
    fun statusBarModeChange_transientIsShown_transitionsToModeWithoutAnimation() =
        testScope.runTest {
            awakeDevice()
            setTransientStatusBar()
            setStatusBarWindowState(StatusBarWindowState.Showing)
            setStatusBarMode(TRANSPARENT)

            orchestrator.start()

            verify(fakeStatusBarInitializer.statusBarTransitions)
                .transitionTo(/* mode= */ TRANSPARENT.toTransitionModeInt(), /* animate= */ false)
        }

    @Test
    fun statusBarModeChange_windowIsHidden_transitionsToModeWithoutAnimation() =
        testScope.runTest {
            awakeDevice()
            abortTransientStatusBar()
            setStatusBarWindowState(StatusBarWindowState.Hidden)
            setStatusBarMode(TRANSPARENT)

            orchestrator.start()

            verify(fakeStatusBarInitializer.statusBarTransitions)
                .transitionTo(/* mode= */ TRANSPARENT.toTransitionModeInt(), /* animate= */ false)
        }

    @Test
    fun statusBarModeChange_deviceIsAsleep_transitionsToModeWithoutAnimation() =
        testScope.runTest {
            putDeviceToSleep()
            abortTransientStatusBar()
            setStatusBarWindowState(StatusBarWindowState.Showing)
            setStatusBarMode(TRANSPARENT)

            orchestrator.start()

            verify(fakeStatusBarInitializer.statusBarTransitions)
                .transitionTo(/* mode= */ TRANSPARENT.toTransitionModeInt(), /* animate= */ false)
        }

    @Test
    fun statusBarModeAnimationConditionsChange_withoutBarModeChange_noNewTransitionsHappen() =
        testScope.runTest {
            awakeDevice()
            abortTransientStatusBar()
            setStatusBarWindowState(StatusBarWindowState.Showing)
            setStatusBarMode(TRANSPARENT)

            orchestrator.start()

            putDeviceToSleep()
            awakeDevice()
            setTransientStatusBar()
            abortTransientStatusBar()

            verify(fakeStatusBarInitializer.statusBarTransitions, times(1))
                .transitionTo(TRANSPARENT.toTransitionModeInt(), /* animate= */ true)
        }

    @Test
    fun stop_unregisterDumpable() {
        orchestrator.start()

        orchestrator.stop()

        verify(dumpManager).unregisterDumpable("StatusBarOrchestrator")
    }

    @Test
    fun start_densityChange_IconRefresh() {
        orchestrator.start()

        kosmos.fakeConfigurationController.notifyDensityOrFontScaleChanged()

        verify(kosmos.statusBarIconController).refreshIconGroups(any())
    }

    @Test
    fun stop_densityChange_noIconRefresh() {
        orchestrator.start()
        orchestrator.stop()

        kosmos.fakeConfigurationController.notifyDensityOrFontScaleChanged()

        verify(kosmos.statusBarIconController, never()).refreshIconGroups(any())
    }

    private fun putDeviceToSleep() {
        fakePowerRepository.updateWakefulness(
            rawState = WakefulnessState.ASLEEP,
            lastWakeReason = WakeSleepReason.KEY,
            lastSleepReason = WakeSleepReason.KEY,
            powerButtonLaunchGestureTriggered = true,
        )
    }

    private fun awakeDevice() {
        fakePowerRepository.updateWakefulness(
            rawState = WakefulnessState.AWAKE,
            lastWakeReason = WakeSleepReason.KEY,
            lastSleepReason = WakeSleepReason.KEY,
            powerButtonLaunchGestureTriggered = true,
        )
    }

    private fun setTransientStatusBar() {
        fakeStatusBarModePerDisplayRepository.showTransient()
    }

    private fun abortTransientStatusBar() {
        fakeStatusBarModePerDisplayRepository.abortTransient()
    }

    private fun setStatusBarWindowState(state: StatusBarWindowState) {
        fakeStatusBarWindowStatePerDisplayRepository.setWindowState(state)
    }

    private fun setStatusBarMode(statusBarMode: StatusBarMode) {
        fakeStatusBarModePerDisplayRepository.statusBarMode.value = statusBarMode
    }
}
