/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

package com.android.systemui.shade

import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.statusbar.IStatusBarService
import com.android.systemui.SysuiTestCase
import com.android.systemui.assist.AssistManager
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.keyguard.data.repository.FakeKeyguardRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.log.LogBuffer
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.power.domain.interactor.PowerInteractorFactory
import com.android.systemui.scene.data.repository.WindowRootViewVisibilityRepository
import com.android.systemui.scene.domain.interactor.WindowRootViewVisibilityInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.data.repository.shadeDisplaysRepository
import com.android.systemui.shade.domain.interactor.shadeDisplaysInteractor
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.NotificationPresenter
import com.android.systemui.statusbar.NotificationShadeWindowController
import com.android.systemui.statusbar.notification.domain.interactor.activeNotificationsInteractor
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.row.NotificationGutsManager
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.statusbar.window.StatusBarWindowController
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore
import com.android.systemui.testKosmos
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.mockito.mock
import com.android.systemui.util.mockito.whenever
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any

@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
@SmallTest
@DisableSceneContainer
class ShadeControllerImplTest : SysuiTestCase() {
    private val executor = FakeExecutor(FakeSystemClock())
    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().also {
            it.shadeDisplaysRepository = it.fakeShadeDisplaysRepository
        }
    private val testScope = kosmos.testScope

    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var keyguardStateController: KeyguardStateController
    @Mock private lateinit var statusBarStateController: StatusBarStateController
    @Mock private lateinit var statusBarKeyguardViewManager: StatusBarKeyguardViewManager
    @Mock private lateinit var deviceProvisionedController: DeviceProvisionedController
    @Mock private lateinit var notificationShadeWindowController: NotificationShadeWindowController
    @Mock private lateinit var windowManager: WindowManager
    @Mock private lateinit var assistManager: AssistManager
    @Mock private lateinit var gutsManager: NotificationGutsManager
    @Mock private lateinit var npvc: NotificationPanelViewController
    @Mock private lateinit var nswvc: NotificationShadeWindowViewController
    @Mock private lateinit var display: Display
    @Mock private lateinit var touchLog: LogBuffer
    @Mock private lateinit var iStatusBarService: IStatusBarService
    @Mock private lateinit var headsUpManager: HeadsUpManager
    @Mock private lateinit var notifPresenter: NotificationPresenter
    @Mock private lateinit var statusBarWindowControllerStore: StatusBarWindowControllerStore
    @Mock private lateinit var statusBarWindowControllerDisplay0: StatusBarWindowController
    @Mock private lateinit var statusBarWindowControllerDisplay2: StatusBarWindowController
    @Mock private lateinit var notificationShadeWindowView: NotificationShadeWindowView

    private val windowRootViewVisibilityInteractor: WindowRootViewVisibilityInteractor by lazy {
        WindowRootViewVisibilityInteractor(
            testScope,
            WindowRootViewVisibilityRepository(iStatusBarService, executor),
            FakeKeyguardRepository(),
            headsUpManager,
            PowerInteractorFactory.create().powerInteractor,
            kosmos.activeNotificationsInteractor,
            kosmos::sceneInteractor,
        )
    }

    private lateinit var underTest: ShadeControllerImpl

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(windowManager.defaultDisplay).thenReturn(display)
        whenever(deviceProvisionedController.isCurrentUserSetup).thenReturn(true)
        whenever(statusBarWindowControllerStore.forDisplay(0))
            .thenReturn(statusBarWindowControllerDisplay0)
        whenever(statusBarWindowControllerStore.forDisplay(2))
            .thenReturn(statusBarWindowControllerDisplay2)
        whenever(nswvc.getView()).thenReturn(notificationShadeWindowView)
        underTest =
            ShadeControllerImpl(
                commandQueue,
                FakeExecutor(FakeSystemClock()),
                windowRootViewVisibilityInteractor,
                keyguardStateController,
                statusBarStateController,
                statusBarKeyguardViewManager,
                statusBarWindowControllerStore,
                deviceProvisionedController,
                notificationShadeWindowController,
                Lazy { nswvc },
                Lazy { npvc },
                Lazy { assistManager },
                Lazy { gutsManager },
                Lazy { kosmos.shadeDisplaysInteractor },
            )
        underTest.setVisibilityListener(mock())
    }

    @Test
    fun testDisableNotificationShade() {
        whenever(commandQueue.panelsEnabled()).thenReturn(false)

        // Trying to open it does nothing.
        underTest.animateExpandShade()
        verify(npvc, never()).expandToNotifications()
        underTest.animateExpandQs()
        verify(npvc, never()).expand(ArgumentMatchers.anyBoolean())
    }

    @Test
    fun testEnableNotificationShade() {
        whenever(commandQueue.panelsEnabled()).thenReturn(true)

        // Can now be opened.
        underTest.animateExpandShade()
        verify(npvc).expandToNotifications()
        underTest.animateExpandQs()
        verify(npvc).expandToQs()
    }

    @Test
    fun cancelExpansionAndCollapseShade_callsCancelCurrentTouch() {
        // GIVEN the shade is tracking a touch
        whenever(npvc.isTracking).thenReturn(true)

        // WHEN cancelExpansionAndCollapseShade is called
        underTest.cancelExpansionAndCollapseShade()

        // VERIFY that cancelCurrentTouch is called
        verify(nswvc).cancelCurrentTouch()
    }

    @Test
    fun cancelExpansionAndCollapseShade_doesNotCallAnimateCollapseShade_whenCollapsed() {
        // GIVEN the shade is tracking a touch
        whenever(npvc.isTracking).thenReturn(false)

        // WHEN cancelExpansionAndCollapseShade is called
        underTest.cancelExpansionAndCollapseShade()

        // VERIFY that cancelCurrentTouch is NOT called
        verify(nswvc, never()).cancelCurrentTouch()
    }

    @Test
    fun visible_changesToTrue_windowInteractorUpdated() {
        underTest.makeExpandedVisible(true)

        assertThat(windowRootViewVisibilityInteractor.isLockscreenOrShadeVisible.value).isTrue()
    }

    @Test
    fun visible_changesToFalse_windowInteractorUpdated() {
        // GIVEN the shade is currently expanded
        underTest.makeExpandedVisible(true)
        assertThat(windowRootViewVisibilityInteractor.isLockscreenOrShadeVisible.value).isTrue()

        // WHEN the shade is collapsed
        underTest.collapseShade()

        // THEN the interactor is notified
        assertThat(windowRootViewVisibilityInteractor.isLockscreenOrShadeVisible.value).isFalse()
    }

    @Test
    fun visible_launchAnimationEnds_windowControllerInstantlyHidden() {
        // GIVEN the shade is currently expanded
        underTest.setNotificationPresenter(notifPresenter)
        underTest.makeExpandedVisible(true)
        assertThat(windowRootViewVisibilityInteractor.isLockscreenOrShadeVisible.value).isTrue()

        // WHEN a fullscreen launch animation ends
        underTest.onLaunchAnimationEnd(launchIsFullScreen = true)

        // THEN the window controller is forced to hide the shade synchronously
        verify(notificationShadeWindowController).setPanelVisible(false)
        verify(notificationShadeWindowController).setForceHideAfterActivityLaunch(true)
    }

    @Test
    fun makeExpandedVisible_recomputesDisableFlags_onCorrectDisplay() {
        whenever(commandQueue.panelsEnabled()).thenReturn(true)
        // GIVEN a non-default displayId
        val displayId = 123
        kosmos.fakeShadeDisplaysRepository.setDisplayId(displayId)

        // WHEN makeExpandedVisible is called
        underTest.makeExpandedVisible(false)

        // THEN recomputeDisableFlags is called with the correct displayId
        verify(commandQueue).recomputeDisableFlags(eq(displayId), anyBoolean())
    }

    @Test
    fun makeExpandedInvisible_recomputesDisableFlags_onCorrectDisplay() {
        Mockito.reset(commandQueue)
        // GIVEN a non-default displayId
        val displayId = 123
        kosmos.fakeShadeDisplaysRepository.setDisplayId(displayId)
        // GIVEN the shade is expanded
        underTest.makeExpandedVisible(true)

        // WHEN makeExpandedInvisible is called
        underTest.makeExpandedInvisible()

        // THEN recomputeDisableFlags is called with the correct displayId
        verify(commandQueue, times(2)).recomputeDisableFlags(eq(displayId), anyBoolean())
    }

    @Test
    fun makeExpandedInvisible_clearsStatusBarForcedVisibleState_onCurrentDisplayId() {
        val externalDisplayId = 2
        kosmos.fakeShadeDisplaysRepository.setDisplayId(externalDisplayId)
        underTest.makeExpandedVisible(true)

        underTest.makeExpandedInvisible()

        verify(statusBarWindowControllerDisplay2).setForceStatusBarVisible(eq(false), any())
    }
}
