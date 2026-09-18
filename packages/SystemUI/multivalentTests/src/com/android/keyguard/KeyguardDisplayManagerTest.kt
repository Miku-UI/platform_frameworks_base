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
package com.android.keyguard

import android.hardware.display.DisplayManagerGlobal
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.DisplayAdjustments
import android.view.DisplayInfo
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.keyguard.KeyguardDisplayManager.DeviceStateHelper
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.navigationbar.NavigationBarController
import com.android.systemui.navigationbar.views.NavigationBarView
import com.android.systemui.settings.FakeDisplayTracker
import com.android.systemui.shade.data.repository.FakeShadeDisplayRepository
import com.android.systemui.statusbar.policy.KeyguardStateController
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executor
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class KeyguardDisplayManagerTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    @Mock private val navigationBarController = mock(NavigationBarController::class.java)
    @Mock
    private val presentationFactory = mock(ConnectedDisplayKeyguardPresentationFactory::class.java)
    @Mock
    private val connectedDisplayKeyguardPresentation =
        mock(ConnectedDisplayConstraintLayoutKeyguardPresentation::class.java)
    @Mock private val deviceStateHelper = mock(DeviceStateHelper::class.java)
    @Mock private val keyguardStateController = mock(KeyguardStateController::class.java)
    private val shadePositionRepository = FakeShadeDisplayRepository()
    private val displayRepository = kosmos.displayRepository

    private val mainExecutor = Executor { it.run() }
    private val backgroundExecutor = Executor { it.run() }
    private lateinit var manager: KeyguardDisplayManager
    private val displayTracker = FakeDisplayTracker(mContext)
    // The default and secondary displays are both in the default group
    private lateinit var defaultDisplay: Display
    private lateinit var secondaryDisplay: Display

    private val testScope = kosmos.testScope

    // This display is in a different group from the default and secondary displays.
    private lateinit var alwaysUnlockedDisplay: Display

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        manager =
            KeyguardDisplayManager(
                mContext,
                { navigationBarController },
                displayTracker,
                mainExecutor,
                backgroundExecutor,
                deviceStateHelper,
                keyguardStateController,
                presentationFactory,
                { shadePositionRepository },
                testScope.backgroundScope,
                /* isCentralizedWallpaperPresentationEnabled= */ false,
                displayRepository,
            )
        whenever(presentationFactory.create(any())).doReturn(connectedDisplayKeyguardPresentation)

        defaultDisplay =
            Display(
                DisplayManagerGlobal.getInstance(),
                Display.DEFAULT_DISPLAY,
                DisplayInfo(),
                DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS,
            )
        secondaryDisplay =
            Display(
                DisplayManagerGlobal.getInstance(),
                Display.DEFAULT_DISPLAY + 1,
                DisplayInfo(),
                DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS,
            )

        val alwaysUnlockedDisplayInfo = DisplayInfo()
        alwaysUnlockedDisplayInfo.displayId = Display.DEFAULT_DISPLAY + 2
        alwaysUnlockedDisplayInfo.flags = Display.FLAG_ALWAYS_UNLOCKED
        alwaysUnlockedDisplay =
            Display(
                DisplayManagerGlobal.getInstance(),
                Display.DEFAULT_DISPLAY,
                alwaysUnlockedDisplayInfo,
                DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS,
            )
    }

    @Test
    fun testShow_defaultDisplayOnly() {
        displayTracker.allDisplays = arrayOf(defaultDisplay)
        manager.show()
        verify(presentationFactory, never()).create(any())
    }

    @Test
    fun testShow_includeSecondaryDisplay() {
        displayTracker.allDisplays = arrayOf(defaultDisplay, secondaryDisplay)
        manager.show()
        verify(presentationFactory).create(eq(secondaryDisplay))
    }

    @Test
    fun testShow_includeAlwaysUnlockedDisplay() {
        displayTracker.allDisplays = arrayOf(defaultDisplay, alwaysUnlockedDisplay)

        manager.show()
        verify(presentationFactory, never()).create(any())
    }

    @Test
    fun testShow_includeSecondaryAndAlwaysUnlockedDisplays() {
        displayTracker.allDisplays =
            arrayOf(defaultDisplay, secondaryDisplay, alwaysUnlockedDisplay)

        manager.show()
        verify(presentationFactory).create(eq(secondaryDisplay))
    }

    @Test
    fun testShow_concurrentDisplayActive_occluded() {
        displayTracker.allDisplays = arrayOf(defaultDisplay, secondaryDisplay)

        whenever(deviceStateHelper.isConcurrentDisplayActive(secondaryDisplay)).thenReturn(true)
        whenever(keyguardStateController.isOccluded).thenReturn(true)
        verify(presentationFactory, never()).create(eq(secondaryDisplay))
    }

    @Test
    fun testShow_rearDisplayOuterDefaultActive_occluded() {
        displayTracker.allDisplays = arrayOf(defaultDisplay, secondaryDisplay)

        whenever(deviceStateHelper.isRearDisplayOuterDefaultActive(secondaryDisplay))
            .thenReturn(true)
        whenever(keyguardStateController.isOccluded).thenReturn(true)
        verify(presentationFactory, never()).create(eq(secondaryDisplay))
    }

    @Test
    fun testShow_presentationCreated() {
        displayTracker.allDisplays = arrayOf(defaultDisplay, secondaryDisplay)

        manager.show()

        verify(presentationFactory).create(eq(secondaryDisplay))
    }

    @Test
    fun show_shadeMovesDisplay_newPresentationCreated() {
        displayTracker.allDisplays = arrayOf(defaultDisplay, secondaryDisplay)
        // Shade in the default display, we expect the presentation to be in the secondary only
        shadePositionRepository.setPendingDisplayId(defaultDisplay.displayId)

        manager.show()

        verify(presentationFactory).create(eq(secondaryDisplay))
        verify(presentationFactory, never()).create(eq(defaultDisplay))
        reset(presentationFactory)
        whenever(presentationFactory.create(any())).thenReturn(connectedDisplayKeyguardPresentation)

        // Let's move it to the secondary display. We expect it will be added in the default
        // one.
        shadePositionRepository.setPendingDisplayId(secondaryDisplay.displayId)
        testScope.advanceUntilIdle()

        verify(presentationFactory).create(eq(defaultDisplay))
        reset(presentationFactory)
        whenever(presentationFactory.create(any())).thenReturn(connectedDisplayKeyguardPresentation)

        // Let's move it back! it should be re-created (it means it was removed before)
        shadePositionRepository.setPendingDisplayId(defaultDisplay.displayId)
        testScope.advanceUntilIdle()

        verify(presentationFactory).create(eq(secondaryDisplay))
    }

    @Test
    fun show_shadeInSecondaryDisplay_defaultOneHasPresentation() {
        displayTracker.allDisplays = arrayOf(defaultDisplay, secondaryDisplay)
        shadePositionRepository.setPendingDisplayId(secondaryDisplay.displayId)

        manager.show()

        verify(presentationFactory).create(eq(defaultDisplay))
    }

    @Test
    fun show_shadeInDefaultDisplay_secondaryOneHasPresentation() {
        displayTracker.allDisplays = arrayOf(defaultDisplay, secondaryDisplay)
        shadePositionRepository.setPendingDisplayId(defaultDisplay.displayId)

        manager.show()

        verify(presentationFactory).create(eq(secondaryDisplay))
    }

    @Test
    fun hide_centralizedWallpaperPresentationEnabled_updateNavigationBarVisibility() =
        testScope.runTest {
            val mockNavigationBarView = mock(NavigationBarView::class.java)
            val rootView = View(context)
            whenever(mockNavigationBarView.rootView).thenReturn(rootView)
            whenever(navigationBarController.getNavigationBarView(eq(secondaryDisplay.displayId)))
                .thenReturn(mockNavigationBarView)
            displayRepository.addDisplay(defaultDisplay)
            displayRepository.addDisplay(secondaryDisplay)
            val keyguardDisplayManager =
                KeyguardDisplayManager(
                    mContext,
                    { navigationBarController },
                    displayTracker,
                    mainExecutor,
                    backgroundExecutor,
                    deviceStateHelper,
                    keyguardStateController,
                    presentationFactory,
                    { shadePositionRepository },
                    testScope.backgroundScope,
                    /* isCentralizedWallpaperPresentationEnabled= */ true,
                    displayRepository,
                )
            // The navigation bar's visibility will only be updated after the keyguard has been
            // shown.
            keyguardDisplayManager.show()

            keyguardDisplayManager.hide()

            verify(navigationBarController).getNavigationBarView(eq(secondaryDisplay.displayId))
            // Default display navigation bar is handled by StatusBarKeyguardViewManager
            verify(navigationBarController, times(0))
                .getNavigationBarView(eq(defaultDisplay.displayId))
            assertThat(rootView.visibility).isEqualTo(View.VISIBLE)
        }
}
