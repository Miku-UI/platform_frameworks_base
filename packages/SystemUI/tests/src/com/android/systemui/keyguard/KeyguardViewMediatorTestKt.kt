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

package com.android.systemui.keyguard

import android.app.ActivityManager.RunningTaskInfo
import android.app.IActivityTaskManager
import android.app.WindowConfiguration
import android.graphics.Point
import android.graphics.Rect
import android.internal.statusbar.statusBarService
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.powerManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import android.view.IRemoteAnimationFinishedCallback
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.View
import android.view.ViewRootImpl
import android.view.WindowManager
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLogger
import com.android.internal.widget.lockPatternUtils
import com.android.keyguard.keyguardUnlockAnimationController
import com.android.keyguard.keyguardUpdateMonitor
import com.android.keyguard.mediator.ScreenOnCoordinator
import com.android.keyguard.trustManager
import com.android.systemui.Flags.FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION
import com.android.systemui.Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.RemoteTransitionHelper
import com.android.systemui.animation.activityTransitionAnimator
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.classifier.falsingCollector
import com.android.systemui.common.data.repository.batteryRepositoryDeprecated
import com.android.systemui.common.data.repository.fake
import com.android.systemui.communal.data.model.FEATURE_AUTO_OPEN
import com.android.systemui.communal.data.model.SuppressionReason
import com.android.systemui.communal.data.repository.communalSceneRepository
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.domain.interactor.setCommunalV2Enabled
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.communal.ui.viewmodel.communalTransitionViewModel
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.dreams.DreamOverlayStateController
import com.android.systemui.dreams.ui.viewmodel.dreamViewModel
import com.android.systemui.dump.dumpManager
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.flags.systemPropertiesHelper
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepositorySpy
import com.android.systemui.keyguard.data.repository.keyguardTransitionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionBootInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.sessionTracker
import com.android.systemui.navigationbar.navigationModeController
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.process.processWrapper
import com.android.systemui.settings.userTracker
import com.android.systemui.shade.shadeController
import com.android.systemui.statusbar.notificationShadeDepthController
import com.android.systemui.statusbar.notificationShadeWindowController
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import com.android.systemui.statusbar.phone.dozeParameters
import com.android.systemui.statusbar.phone.screenOffAnimationController
import com.android.systemui.statusbar.phone.scrimController
import com.android.systemui.statusbar.phone.statusBarKeyguardViewManager
import com.android.systemui.statusbar.policy.keyguardStateController
import com.android.systemui.statusbar.policy.userSwitcherController
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.android.systemui.util.DeviceConfigProxy
import com.android.systemui.util.kotlin.javaAdapter
import com.android.systemui.util.settings.fakeSettings
import com.android.systemui.util.time.systemClock
import com.android.systemui.wallpapers.data.repository.wallpaperRepository
import com.android.wm.shell.keyguard.KeyguardTransitions
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Kotlin version of KeyguardViewMediatorTest to allow for coroutine testing. */
@SmallTest
@RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidTestingRunner::class)
@DisableSceneContainer // Class is deprecated in flexi.
class KeyguardViewMediatorTestKt : SysuiTestCase() {
    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            powerManager =
                mock<PowerManager> {
                    on { newWakeLock(anyInt(), any()) } doReturn mock<PowerManager.WakeLock>()
                }
            keyguardTransitionRepository = fakeKeyguardTransitionRepositorySpy

            val mockViewRootImpl = mock<ViewRootImpl> { on { getView() } doReturn mock<View>() }
            statusBarKeyguardViewManager =
                mock<StatusBarKeyguardViewManager> { on { viewRootImpl } doReturn mockViewRootImpl }
        }

    private val mockActivityTransitionAnimator =
        mock<ActivityTransitionAnimator>().also { kosmos.activityTransitionAnimator = it }

    private val Kosmos.dreamViewModelSpy by Kosmos.Fixture { spy(dreamViewModel) }

    private lateinit var testableLooper: TestableLooper

    private val Kosmos.underTest by
        Kosmos.Fixture {
            KeyguardViewMediator(
                mContext,
                uiEventLogger,
                sessionTracker,
                userTracker,
                falsingCollector,
                lockPatternUtils,
                broadcastDispatcher,
                { statusBarKeyguardViewManager },
                dismissCallbackRegistry,
                keyguardUpdateMonitor,
                dumpManager,
                fakeExecutor,
                powerManager,
                trustManager,
                userSwitcherController,
                DeviceConfigProxy(),
                navigationModeController,
                keyguardDisplayManager,
                dozeParameters,
                statusBarStateController,
                keyguardStateController,
                { keyguardUnlockAnimationController },
                screenOffAnimationController,
                { notificationShadeDepthController },
                mock<ScreenOnCoordinator>(),
                mock<KeyguardTransitions>(),
                interactionJankMonitor,
                mock<DreamOverlayStateController>(),
                javaAdapter,
                wallpaperRepository,
                { shadeController },
                { notificationShadeWindowController },
                { mockActivityTransitionAnimator },
                { scrimController },
                mock<IActivityTaskManager>(),
                statusBarService,
                featureFlagsClassic,
                fakeSettings,
                fakeSettings,
                systemClock,
                processWrapper,
                testScope,
                { dreamViewModelSpy },
                { communalTransitionViewModel },
                systemPropertiesHelper,
                { mock<WindowManagerLockscreenVisibilityManager>() },
                selectedUserInteractor,
                keyguardInteractor,
                keyguardTransitionBootInteractor,
                { communalSceneInteractor },
                { communalSettingsInteractor },
                mock<WindowManagerOcclusionManager>(),
            )
        }

    @Before
    fun setUp() {
        testableLooper = TestableLooper.get(this)
        val testViewRoot = mock<ViewRootImpl>()
        whenever(testViewRoot.view).thenReturn(mock<View>())
        whenever(kosmos.statusBarKeyguardViewManager.getViewRootImpl()).thenReturn(testViewRoot)
        whenever(
                kosmos.activityTransitionAnimator.createOriginTransition(
                    any<ActivityTransitionAnimator.Controller>(),
                    eq(kosmos.testScope),
                    anyBoolean(),
                    any<RemoteTransitionHelper>(),
                )
            )
            .thenReturn(mock<IRemoteTransition>())
    }

    @Test
    fun doKeyguardTimeout_changesCommunalScene() =
        kosmos.runTest {
            // Hub is enabled and hub condition is active.
            setCommunalV2Enabled(true)
            enableHubOnCharging()

            // doKeyguardTimeout message received.
            val timeoutOptions = Bundle()
            timeoutOptions.putBoolean(KeyguardViewMediator.EXTRA_TRIGGER_HUB, true)
            underTest.doKeyguardTimeout(timeoutOptions)
            testableLooper.processAllMessages()

            // Hub scene is triggered.
            assertThat(communalSceneRepository.currentScene.value)
                .isEqualTo(CommunalScenes.Communal)
        }

    @Test
    fun doKeyguardTimeout_communalNotAvailable_sleeps() =
        kosmos.runTest {
            // Hub disabled.
            setCommunalV2Enabled(false)

            // doKeyguardTimeout message received.
            val timeoutOptions = Bundle()
            timeoutOptions.putBoolean(KeyguardViewMediator.EXTRA_TRIGGER_HUB, true)
            underTest.doKeyguardTimeout(timeoutOptions)
            testableLooper.processAllMessages()

            // Sleep is requested.
            verify(powerManager)
                .goToSleep(anyOrNull(), eq(PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON), eq(0))

            // Hub scene is not changed.
            assertThat(communalSceneRepository.currentScene.value).isEqualTo(CommunalScenes.Blank)
        }

    @Test
    fun doKeyguardTimeout_dreaming_keyguardNotReset() =
        kosmos.runTest {
            underTest.setShowingLocked(true, "")
            whenever(powerManager.isInteractive()).thenReturn(true)
            whenever(keyguardStateController.isShowing()).thenReturn(true)
            whenever(keyguardUpdateMonitor.isDreaming).thenReturn(true)
            underTest.doDelayedKeyguardAction(0)
            testableLooper.processAllMessages()
            verify(statusBarKeyguardViewManager, never()).reset(anyBoolean())
        }

    @Test
    fun doKeyguardTimeout_hubConditionNotActive_sleeps() =
        kosmos.runTest {
            // Communal enabled, but hub condition set to never.
            setCommunalV2Enabled(true)
            disableHubShowingAutomatically()

            // doKeyguardTimeout message received.
            val timeoutOptions = Bundle()
            timeoutOptions.putBoolean(KeyguardViewMediator.EXTRA_TRIGGER_HUB, true)
            underTest.doKeyguardTimeout(timeoutOptions)
            testableLooper.processAllMessages()

            // Sleep is requested.
            verify(powerManager)
                .goToSleep(anyOrNull(), eq(PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON), eq(0))

            // Hub scene is not changed.
            assertThat(communalSceneRepository.currentScene.value).isEqualTo(CommunalScenes.Blank)
        }

    @DisableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR)
    @EnableFlags(FLAG_ANIMATION_LIBRARY_SHELL_MIGRATION)
    @Test
    fun occludeTransition_occludesKeyguard() =
        kosmos.runTest {
            // GIVEN that keyguard is showing and not occluded.
            underTest.onSystemReady()
            underTest.setShowingLocked(true, "")
            whenever(keyguardStateController.isShowing()).thenReturn(true)
            whenever(keyguardUpdateMonitor.isDreaming).thenReturn(false)
            assertThat(underTest.isShowingAndNotOccluded).isTrue()

            // WHEN an occlude transition starts.
            val token = mock<IBinder>()
            val info = mock<TransitionInfo>()
            val transaction = mock<SurfaceControl.Transaction>()
            val finishCallback = mock<IRemoteTransitionFinishedCallback>()
            underTest.occludeTransition.startAnimation(token, info, transaction, finishCallback)
            testableLooper.processAllMessages()

            // THEN keyguard is locked and occluded.
            assertThat(underTest.isShowing).isTrue()
            assertThat(underTest.isShowingAndNotOccluded).isFalse()
        }

    @Test
    @DisableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR)
    fun unoccludeAnimationRunner_transitionsAwayFromDreamEvenWithEmptyApps() =
        kosmos.runTest {
            setCommunalV2Enabled(false)
            disableHubShowingAutomatically()
            powerInteractor.setAwakeForTest()

            // Given that we are in a dream state.
            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.OFF,
                to = KeyguardState.DREAMING,
                testScope = testScope,
            )

            val apps = emptyArray<RemoteAnimationTarget>()
            val wallpapers = emptyArray<RemoteAnimationTarget>()
            val nonApps = emptyArray<RemoteAnimationTarget>()
            val finishedCallback = mock<IRemoteAnimationFinishedCallback>()

            verify(dreamViewModelSpy, never()).startTransitionFromDream()

            underTest.unoccludeAnimationRunner.onAnimationStart(
                WindowManager.TRANSIT_KEYGUARD_UNOCCLUDE,
                apps,
                wallpapers,
                nonApps,
                finishedCallback,
            )

            verify(finishedCallback).onAnimationFinished()
            verify(dreamViewModelSpy).startTransitionFromDream()
        }

    @Test
    @DisableFlags(FLAG_KEYGUARD_WM_STATE_REFACTOR)
    fun unoccludeAnimation_callsFinishedCallback_whenStartedAfterFromDreamingTransitionFinished() =
        kosmos.runTest {
            underTest.onSystemReady()
            testableLooper.processAllMessages()

            // Keyguard transition finished from dreaming to AOD
            underTest.onDreamingStopped()
            underTest.setShowingLocked(true, "")
            underTest.setDozing(true)
            fakeKeyguardTransitionRepository.transitionTo(KeyguardState.DREAMING, KeyguardState.AOD)

            // Start an unocclude animation afterwards
            val taskInfo =
                RunningTaskInfo().apply {
                    topActivityType = WindowConfiguration.ACTIVITY_TYPE_DREAM
                }
            val apps =
                arrayOf(
                    RemoteAnimationTarget(
                        0,
                        RemoteAnimationTarget.MODE_CLOSING,
                        mock<SurfaceControl>(),
                        false,
                        Rect(),
                        Rect(),
                        0,
                        Point(),
                        Rect(),
                        Rect(),
                        WindowConfiguration(),
                        false,
                        mock<SurfaceControl>(),
                        Rect(),
                        taskInfo,
                        false,
                    )
                )
            val finishedCallback = mock<IRemoteAnimationFinishedCallback>()
            underTest.unoccludeAnimationRunner.onAnimationStart(
                WindowManager.TRANSIT_OLD_KEYGUARD_UNOCCLUDE,
                apps,
                arrayOf(),
                null,
                finishedCallback,
            )
            testableLooper.processAllMessages()

            verify(finishedCallback).onAnimationFinished()
            assertThat(underTest.isShowingAndNotOccluded).isTrue()
        }

    private fun Kosmos.enableHubOnCharging() {
        communalSettingsInteractor.setSuppressionReasons(emptyList())
        batteryRepositoryDeprecated.fake.setDevicePluggedIn(true)
    }

    private fun Kosmos.disableHubShowingAutomatically() {
        communalSettingsInteractor.setSuppressionReasons(
            listOf(SuppressionReason.ReasonUnknown(FEATURE_AUTO_OPEN))
        )
    }
}
