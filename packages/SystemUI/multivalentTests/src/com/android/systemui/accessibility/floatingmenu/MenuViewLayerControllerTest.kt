/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.accessibility.floatingmenu

import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.testing.TestableLooper
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.accessibility.AccessibilityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.HearingAidDeviceManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.Magnification
import com.android.systemui.inputdevice.data.repository.PointerDeviceRepository
import com.android.systemui.keyboard.data.repository.KeyboardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.navigationbar.NavigationModeController
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.SecureSettings
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [MenuViewLayerController]. */
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
class MenuViewLayerControllerTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val windowManager = mock<WindowManager>()
    private val accessibilityManager = mock<AccessibilityManager>()
    private val hearingAidDeviceManager = mock<HearingAidDeviceManager>()
    private val secureSettings = mock<SecureSettings>()
    private val windowMetrics = mock<WindowMetrics>()
    private val keyboardRepository =
        mock<KeyboardRepository> { on { isAnyKeyboardConnected } doReturn flowOf(false) }
    private val pointerDeviceRepository =
        mock<PointerDeviceRepository> { on { isAnyPointerDeviceConnected } doReturn flowOf(false) }

    private lateinit var menuViewLayerController: MenuViewLayerController

    @Before
    fun setUp() {
        val wm = mContext.getSystemService(WindowManager::class.java)
        whenever(windowManager.maximumWindowMetrics).thenReturn(wm.maximumWindowMetrics)
        mContext.addMockSystemService(Context.WINDOW_SERVICE, windowManager)

        whenever(windowManager.currentWindowMetrics).thenReturn(windowMetrics)
        whenever(windowMetrics.bounds).thenReturn(Rect(0, 0, 1080, 2340))
        whenever(windowMetrics.windowInsets).thenReturn(stubDisplayInsets())

        menuViewLayerController =
            MenuViewLayerController(
                mContext,
                windowManager,
                accessibilityManager,
                secureSettings,
                mock<NavigationModeController>(),
                hearingAidDeviceManager,
                keyboardRepository,
                pointerDeviceRepository,
                mock<Magnification>(),
                kosmos.keyguardTransitionInteractor,
                kosmos.sceneInteractor,
            )
    }

    @Test
    fun show_shouldAddViewToWindow() {
        menuViewLayerController.show()

        verify(windowManager).addView(any(), any())
    }

    @Test
    fun hide_menuIsShowing_removeViewFromWindow() {
        menuViewLayerController.show()

        menuViewLayerController.hide()

        verify(windowManager).removeView(any())
    }

    private fun stubDisplayInsets(): WindowInsets {
        val stubStatusBarHeight = 118
        val stubNavigationBarHeight = 125
        return WindowInsets.Builder()
            .setVisible(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(), true)
            .setInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                Insets.of(0, stubStatusBarHeight, 0, stubNavigationBarHeight),
            )
            .build()
    }
}
