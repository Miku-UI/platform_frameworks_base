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
package com.android.systemui.keyguard

import android.app.IActivityTaskManager
import android.internal.statusbar.statusBarService
import android.os.PowerManager
import android.os.powerManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.testing.TestableLooper.RunWithLooper
import android.view.View
import android.view.ViewRootImpl
import androidx.test.filters.SmallTest
import com.android.internal.logging.uiEventLogger
import com.android.internal.widget.lockPatternUtils
import com.android.keyguard.keyguardUnlockAnimationController
import com.android.keyguard.keyguardUpdateMonitor
import com.android.keyguard.mediator.ScreenOnCoordinator
import com.android.keyguard.trustManager
import com.android.systemui.Flags.FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.classifier.falsingCollector
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.domain.interactor.communalSettingsInteractor
import com.android.systemui.communal.ui.viewmodel.communalTransitionViewModel
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.dreams.DreamOverlayStateController
import com.android.systemui.dreams.ui.viewmodel.dreamViewModel
import com.android.systemui.dump.dumpManager
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.featureFlagsClassic
import com.android.systemui.flags.systemPropertiesHelper
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionBootInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.sessionTracker
import com.android.systemui.navigationbar.navigationModeController
import com.android.systemui.plugins.statusbar.statusBarStateController
import com.android.systemui.process.processWrapper
import com.android.systemui.scene.data.repository.Idle
import com.android.systemui.scene.data.repository.ShowOverlay
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
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
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidTestingRunner::class)
@EnableSceneContainer
class KeyguardViewMediatorSceneContainerTest : SysuiTestCase() {
  companion object {
    const val USER_ID = 10
  }

  private val kosmos =
    testKosmos().useUnconfinedTestDispatcher().apply {
      powerManager =
        mock<PowerManager> {
          on { newWakeLock(anyInt(), any()) } doReturn mock<PowerManager.WakeLock>()
        }
      val mockViewRootImpl = mock<ViewRootImpl> { on { getView() } doReturn mock<View>() }
      statusBarKeyguardViewManager =
        mock<StatusBarKeyguardViewManager> { on { viewRootImpl } doReturn mockViewRootImpl }
    }

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
        { mock<ActivityTransitionAnimator>() },
        { scrimController },
        mock<IActivityTaskManager>(),
        statusBarService,
        featureFlagsClassic,
        fakeSettings,
        fakeSettings,
        systemClock,
        processWrapper,
        testScope,
        { dreamViewModel },
        { communalTransitionViewModel },
        systemPropertiesHelper,
        { mock<WindowManagerLockscreenVisibilityManager>() },
        selectedUserInteractor,
        keyguardInteractor,
        keyguardTransitionBootInteractor,
        { communalSceneInteractor },
        { communalSettingsInteractor },
        mock<WindowManagerOcclusionManager>(),
        Optional.empty(),
        { kosmos.sceneInteractor },
      )
    }

  @Before
  fun setUp() {
    testableLooper = TestableLooper.get(this)
    kosmos.underTest.setShowingLocked(true, "test")
  }

  @Test
  @EnableFlags(FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS)
  fun handleUserSwitching_secureUserWithFlag_dismissesKeyguardAndAwaitsBouncer() =
    kosmos.runTest {
      whenever(kosmos.lockPatternUtils.isSecure(USER_ID)).thenReturn(true)
      kosmos.setSceneTransition(Idle(Scenes.Lockscreen))

      // Trigger handleUserSwitching should dismiss keyguard, but not run the callback yet.
      val callback = mock<Runnable>()
      underTest.handleUserSwitching(USER_ID, callback)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager).dismissAndCollapse()
      verify(callback, never()).run()

      // Update state to transitioning to Bouncer
      kosmos.setSceneTransition(
        ShowOverlay(overlay = Overlays.Bouncer, fromScene = Scenes.Lockscreen)
      )
      verify(callback, never()).run()

      // Update state to Bouncer
      kosmos.setSceneTransition(Idle(Scenes.Lockscreen, setOf(Overlays.Bouncer)))
      verify(callback).run()
    }

  @Test
  @DisableFlags(FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS)
  fun handleUserSwitching_secureUserWithoutFlag_immediatelyRunsCallback() =
    kosmos.runTest {
      whenever(kosmos.lockPatternUtils.isSecure(USER_ID)).thenReturn(true)
      kosmos.setSceneTransition(Idle(Scenes.Lockscreen))

      // Trigger handleUserSwitching should not dismiss keyguard and immediately run the
      // callback.
      val callback = mock<Runnable>()
      underTest.handleUserSwitching(USER_ID, callback)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager, never()).dismissAndCollapse()
      verify(callback).run()
    }

  @Test
  @EnableFlags(FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS)
  fun handleUserSwitching_insecureUserWithFlag_dismissesKeyguardAndRunsCallbackImmediately() =
    kosmos.runTest {
      whenever(kosmos.lockPatternUtils.isSecure(USER_ID)).thenReturn(false)
      kosmos.setSceneTransition(Idle(Scenes.Lockscreen))

      // Trigger handleUserSwitching should dismiss keyguard and run the callback immediately.
      val callback = mock<Runnable>()
      underTest.handleUserSwitching(USER_ID, callback)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager).dismissAndCollapse()
      verify(callback).run()
    }

  @Test
  @DisableFlags(FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS)
  fun handleUserSwitching_insecureUserWithoutFlag_dismissesKeyguardAndRunsCallbackImmediately() =
    kosmos.runTest {
      whenever(kosmos.lockPatternUtils.isSecure(USER_ID)).thenReturn(false)
      kosmos.setSceneTransition(Idle(Scenes.Lockscreen))

      // Trigger handleUserSwitching should dismiss keyguard and run the callback immediately.
      val callback = mock<Runnable>()
      underTest.handleUserSwitching(USER_ID, callback)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager).dismissAndCollapse()
      verify(callback).run()
    }

  @Test
  @EnableFlags(FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS)
  fun handleUserSwitchComplete_secureUserWithFlag_doesNotDismissKeyguard() =
    kosmos.runTest {
      whenever(kosmos.lockPatternUtils.isSecure(USER_ID)).thenReturn(true)

      // Trigger handleUserSwitchComplete should not dismiss keyguard.
      underTest.handleUserSwitchComplete(USER_ID)
      testableLooper.moveTimeForward(600)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager, never()).dismissAndCollapse()
    }

  @Test
  @DisableFlags(FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS)
  fun handleUserSwitchComplete_secureUserWithoutFlag_dismissesKeyguard() =
    kosmos.runTest {
      whenever(kosmos.lockPatternUtils.isSecure(USER_ID)).thenReturn(true)

      // Trigger handleUserSwitchComplete should dismiss keyguard (after 500ms delay).
      underTest.handleUserSwitchComplete(USER_ID)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager, never()).dismissAndCollapse()
      testableLooper.moveTimeForward(600)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager).dismissAndCollapse()
    }

  @Test
  @EnableFlags(FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS)
  fun handleUserSwitchComplete_insecureUserWithFlag_doesNotDismissKeyguard() =
    kosmos.runTest {
      whenever(kosmos.lockPatternUtils.isSecure(USER_ID)).thenReturn(false)

      // Trigger handleUserSwitchComplete should not dismiss keyguard.
      underTest.handleUserSwitchComplete(USER_ID)
      testableLooper.moveTimeForward(600)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager, never()).dismissAndCollapse()
    }

  @Test
  @DisableFlags(FLAG_TRANSITION_TO_BOUNCER_WHILE_SWITCHING_USERS)
  fun handleUserSwitchComplete_insecureUserWithoutFlag_doesNotDismissKeyguard() =
    kosmos.runTest {
      whenever(kosmos.lockPatternUtils.isSecure(USER_ID)).thenReturn(false)

      // Trigger handleUserSwitchComplete should not dismiss keyguard.
      underTest.handleUserSwitchComplete(USER_ID)
      testableLooper.moveTimeForward(600)
      testableLooper.processAllMessages()
      verify(kosmos.statusBarKeyguardViewManager, never()).dismissAndCollapse()
    }
}
