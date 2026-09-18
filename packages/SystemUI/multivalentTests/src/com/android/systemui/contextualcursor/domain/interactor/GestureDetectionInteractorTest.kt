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

import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import android.view.layoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.hardware.input.Flags.FLAG_ENABLE_CONTEXTUAL_CURSOR_DESKTOP_ENTRYPOINTS
import com.android.systemui.SysuiTestCase
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.domain.data.repository.multiDisplayCursorPositionRepository
import com.android.systemui.cursorposition.domain.interactor.multiDisplayCursorPositionInteractor
import com.android.systemui.display.data.repository.fakeDisplayWindowPropertiesRepository
import com.android.systemui.display.domain.interactor.displayWindowPropertiesInteractor
import com.android.systemui.display.shared.model.DisplayWindowProperties
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ENABLE_CONTEXTUAL_CURSOR_DESKTOP_ENTRYPOINTS)
class GestureDetectionInteractorTest : SysuiTestCase() {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val cursorPositionRepository = kosmos.multiDisplayCursorPositionRepository
    private val cursorPositionInteractor = kosmos.multiDisplayCursorPositionInteractor
    private val displayRepository = kosmos.fakeDisplayWindowPropertiesRepository
    private val displayInteractor = kosmos.displayWindowPropertiesInteractor
    private val systemClock = kosmos.fakeSystemClock
    private val windowManager: WindowManager = mock<WindowManager>()
    @Mock lateinit var mockListener: GestureDetectionInteractor.OnShakeGestureListener

    private val Kosmos.underTest by
        Kosmos.Fixture {
            GestureDetectionInteractor(
                cursorPositionInteractor,
                displayInteractor,
                systemClock,
                kosmos.backgroundScope,
            )
        }

    @Before
    fun setup() {
        whenever(windowManager.currentWindowMetrics).thenReturn(metrics)
        displayRepository.insert(createDisplayWindowProperties())
    }

    @Test
    fun isShakeGestureDetected_initialStateIsFalse() =
        kosmos.runTest {
            underTest.addShakeGestureListener(mockListener)

            verify(mockListener, never()).onShakeGestureDetected()
        }

    @Test
    fun isShakeGestureDetected_smallMovements_remainsFalse() =
        kosmos.runTest {
            underTest.addShakeGestureListener(mockListener)

            newCursorPosition(0f, 0f)
            newCursorPosition(5f, 5f)
            newCursorPosition(10f, 10f)
            systemClock.advanceTime(100)

            verify(mockListener, never()).onShakeGestureDetected()
        }

    @Test
    fun isShakeGestureDetected_validShake_becomesTrue() =
        kosmos.runTest {
            underTest.addShakeGestureListener(mockListener)

            simulateShake()

            verify(mockListener).onShakeGestureDetected()
        }

    @Test
    fun isShakeGestureDetected_shakeTooLong_remainsFalse() =
        kosmos.runTest {
            underTest.addShakeGestureListener(mockListener)

            newCursorPosition(0f, 0f) // T=0
            systemClock.advanceTime(150)
            newCursorPosition(30f, 30f) // T=150, DeltaX=30
            systemClock.advanceTime(150)
            newCursorPosition(0f, 0f) // T=300, DeltaX=-30, Change=1
            systemClock.advanceTime(150)
            newCursorPosition(30f, 30f) // T=450, DeltaX=30, Change=2
            systemClock.advanceTime(150)
            newCursorPosition(0f, 0f) // T=600, DeltaX=-30, Change=3
            systemClock.advanceTime(150)
            newCursorPosition(30f, 30f) // T=750, DeltaX=30, Change=4
            systemClock.advanceTime(150)
            newCursorPosition(0f, 0f) // T=900, DeltaX=-30, Change=5

            verify(mockListener, never()).onShakeGestureDetected()
        }

    @Test
    fun isShakeGestureDetected_rangeTooLarge_remainsFalse() =
        kosmos.runTest {
            underTest.addShakeGestureListener(mockListener)

            newCursorPosition(0f, 0f) // T=0
            systemClock.advanceTime(10)
            newCursorPosition(30f, 30f) // T=10, DeltaX=30
            systemClock.advanceTime(10)
            newCursorPosition(0f, 0f) // T=20, DeltaX=-30, Change=1
            systemClock.advanceTime(10)
            newCursorPosition(200f, 200f) // T=30, DeltaX=200, Change=2
            systemClock.advanceTime(10)
            newCursorPosition(0f, 0f) // T=40, DeltaX=-220, Change=3
            systemClock.advanceTime(10)
            newCursorPosition(30f, 30f) // T=50, DeltaX=30
            systemClock.advanceTime(10)
            newCursorPosition(0f, 0f) // T=60, DeltaX=-30, Change=1

            verify(mockListener, never()).onShakeGestureDetected()
        }

    @Test
    fun isShakeGestureDetected_cooldownPeriod_preventsImmediateReshake() =
        kosmos.runTest {
            underTest.addShakeGestureListener(mockListener)

            // First shake: Should be detected
            simulateShake()

            verify(mockListener).onShakeGestureDetected()
            clearInvocations(mockListener)

            // Make a non-shake move
            systemClock.advanceTime(10)
            newCursorPosition(200f, 200f)

            verify(mockListener, never()).onShakeGestureDetected()

            // Second shake: Advance time by less than COOLDOWN_MS (3000L), shake not detected.
            systemClock.advanceTime(2500)
            simulateShake()

            verify(mockListener, never()).onShakeGestureDetected()

            // Third shake attempt: Should be detected as cooldown has passed
            systemClock.advanceTime(500)
            simulateShake()

            verify(mockListener).onShakeGestureDetected()
        }

    private fun createDisplayWindowProperties() =
        DisplayWindowProperties(
            Display.DEFAULT_DISPLAY,
            WindowManager.LayoutParams.TYPE_BASE_APPLICATION,
            context,
            windowManager,
            kosmos.layoutInflater,
        )

    private fun newCursorPosition(x: Float, y: Float) {
        cursorPositionRepository.addCursorPosition(CursorPosition(x, y, Display.DEFAULT_DISPLAY))
    }

    private fun simulateShake() {
        newCursorPosition(0f, 0f) // T=0
        systemClock.advanceTime(10)
        newCursorPosition(30f, 30f) // T=10, DeltaX=30
        systemClock.advanceTime(10)
        newCursorPosition(0f, 0f) // T=20, DeltaX=-30, Change=1
        systemClock.advanceTime(10)
        newCursorPosition(30f, 30f) // T=30, DeltaX=30, Change=2
        systemClock.advanceTime(10)
        newCursorPosition(0f, 0f) // T=40, DeltaX=-30, Change=3
        systemClock.advanceTime(10)
        newCursorPosition(30f, 30f) // T=50, DeltaX=30, Change=4
        systemClock.advanceTime(10)
        newCursorPosition(0f, 0f) // T=60, DeltaX=-30, Change=5
    }

    companion object {
        private val metrics = WindowMetrics(Rect(0, 0, 2560, 1600), mock<WindowInsets>(), 2f)
    }
}
