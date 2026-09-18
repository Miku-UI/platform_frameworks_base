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

package com.android.systemui.contextualcursor.domain.interactor

import android.app.contextualsearch.ContextualSearchManager
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.layoutInflater
import android.view.windowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.hardware.input.Flags.FLAG_ENABLE_CONTEXTUAL_CURSOR_DESKTOP_ENTRYPOINTS
import com.android.systemui.SysuiTestCase
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.domain.data.repository.multiDisplayCursorPositionRepository
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.display.data.repository.fakeDisplayWindowPropertiesRepository
import com.android.systemui.display.shared.model.DisplayWindowProperties
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.launcherProxy
import com.android.systemui.launcherProxyService
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.statusbar.policy.data.repository.fakeUserSetupRepository
import com.android.systemui.testKosmos
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ENABLE_CONTEXTUAL_CURSOR_DESKTOP_ENTRYPOINTS)
class ContextualCursorInteractorTest : SysuiTestCase() {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Fixture { kosmos.contextualCursorInteractor }

    @Before
    fun setup() {
        whenever(kosmos.launcherProxyService.proxy).thenReturn(kosmos.launcherProxy)
        whenever(kosmos.windowManager.currentWindowMetrics).thenReturn(metrics)
        kosmos.fakeUserSetupRepository.setUserSetUp(true)
        kosmos.fakeDeviceProvisioningRepository.setDeviceProvisioned(true)
        kosmos.fakeUserRepository.setUserManagerLogoutEnabled(true)
        kosmos.fakeDisplayWindowPropertiesRepository.insert(createDisplayWindowProperties())
        kosmos.underTest.start()
    }

    @Test
    fun notInvoke_whenDeviceNotEntered() =
        kosmos.runTest {
            val deviceEntered by collectLastValue(deviceEntryInteractor.isDeviceEntered)
            assertThat(deviceEntered).isFalse()

            simulateShake()
            verify(launcherProxy, never()).invokeContextualSearch(any(), any())
        }

    @Test
    fun notInvoke_whenUserNotSetup() =
        kosmos.runTest {
            setDeviceEntered()
            fakeUserSetupRepository.setUserSetUp(false)

            simulateShake()
            verify(launcherProxy, never()).invokeContextualSearch(any(), any())
        }

    @Test
    fun notInvoke_whenDeviceNotProvisioned() =
        kosmos.runTest {
            setDeviceEntered()
            fakeDeviceProvisioningRepository.setDeviceProvisioned(false)

            simulateShake()
            verify(launcherProxy, never()).invokeContextualSearch(any(), any())
        }

    @Test
    fun notInvoke_whenUserSignedOut() =
        kosmos.runTest {
            setDeviceEntered()
            fakeUserRepository.setUserManagerLogoutEnabled(false)

            simulateShake()
            verify(launcherProxy, never()).invokeContextualSearch(any(), any())
        }

    @Test
    fun invoke_whenAllActivated() =
        kosmos.runTest {
            setDeviceEntered()

            simulateShake()
            verify(launcherProxy)
                .invokeContextualSearch(
                    eq(ContextualSearchManager.ENTRYPOINT_SYSTEM_ACTION),
                    eq(null),
                )
        }

    private fun newCursorPosition(x: Float, y: Float) {
        kosmos.multiDisplayCursorPositionRepository.addCursorPosition(
            CursorPosition(x, y, Display.DEFAULT_DISPLAY)
        )
    }

    private fun createDisplayWindowProperties() =
        DisplayWindowProperties(
            Display.DEFAULT_DISPLAY,
            WindowManager.LayoutParams.TYPE_BASE_APPLICATION,
            context,
            kosmos.windowManager,
            kosmos.layoutInflater,
        )

    private fun simulateShake() {
        newCursorPosition(0f, 0f) // T=0
        kosmos.fakeSystemClock.advanceTime(10)
        newCursorPosition(30f, 30f) // T=10, DeltaX=30
        kosmos.fakeSystemClock.advanceTime(10)
        newCursorPosition(0f, 0f) // T=20, DeltaX=-30, Change=1
        kosmos.fakeSystemClock.advanceTime(10)
        newCursorPosition(30f, 30f) // T=30, DeltaX=30, Change=2
        kosmos.fakeSystemClock.advanceTime(10)
        newCursorPosition(0f, 0f) // T=40, DeltaX=-30, Change=3
        kosmos.fakeSystemClock.advanceTime(10)
        newCursorPosition(30f, 30f) // T=50, DeltaX=30, Change=4
        kosmos.fakeSystemClock.advanceTime(10)
        newCursorPosition(0f, 0f) // T=60, DeltaX=-30, Change=5
    }

    companion object {
        private val metrics = WindowMetrics(Rect(0, 0, 2560, 1600), mock<WindowInsets>(), 2f)
    }
}
