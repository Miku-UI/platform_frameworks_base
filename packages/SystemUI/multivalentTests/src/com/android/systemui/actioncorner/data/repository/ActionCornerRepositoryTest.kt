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

package com.android.systemui.actioncorner.data.repository

import android.graphics.Rect
import android.provider.Settings.Secure.ACTION_CORNER_ACTION_HOME
import android.provider.Settings.Secure.ACTION_CORNER_TOP_LEFT_ACTION
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION
import android.view.WindowMetrics
import android.view.layoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.actioncorner.data.model.ActionCornerRegion
import com.android.systemui.actioncorner.data.model.ActionCornerState.ActiveActionCorner
import com.android.systemui.actioncorner.data.model.ActionCornerState.InactiveActionCorner
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.domain.data.repository.multiDisplayCursorPositionRepository
import com.android.systemui.display.data.repository.fakeDisplayWindowPropertiesRepository
import com.android.systemui.display.shared.model.DisplayWindowProperties
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.data.repository.userAwareSecureSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ActionCornerRepositoryTest : SysuiTestCase() {
    @get:Rule val mockitoRule: MockitoRule = MockitoJUnit.rule()
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val settingsRepository = kosmos.userAwareSecureSettingsRepository

    private val Kosmos.underTest by Fixture {
        ActionCornerRepositoryImpl(
            cursorPositionRepository,
            kosmos.fakeDisplayWindowPropertiesRepository,
            kosmos.backgroundScope,
        )
    }

    private val cursorPositionRepository = kosmos.multiDisplayCursorPositionRepository
    private val displayRepository = kosmos.fakeDisplayWindowPropertiesRepository
    private val windowManager: WindowManager = mock<WindowManager>()

    @Before
    fun setup() {
        whenever(windowManager.currentWindowMetrics).thenReturn(metrics)
        displayRepository.insert(createDisplayWindowProperties())
    }

    @Test
    fun topLeftCursor_topLeftActionCornerEmitted() = setUpAndRunTest {
        val model by collectLastValue(underTest.actionCornerState)
        addCursorPosition(display.topLeftCursorPos)
        assertThat(model)
            .isEqualTo(
                ActiveActionCorner(ActionCornerRegion.TOP_LEFT, display.topLeftCursorPos.displayId)
            )
    }

    @Test
    fun outOfBoundTopLeftCursor_noActionCornerEmitted() = setUpAndRunTest {
        val model by collectLastValue(underTest.actionCornerState)
        val actionCornerPos = display.topLeftCursorPos
        // Update x and y to make it just out of bound of action corner
        addCursorPosition(
            CursorPosition(
                x = actionCornerPos.x + 1,
                y = actionCornerPos.y + 1,
                actionCornerPos.displayId,
            )
        )
        assertThat(model).isEqualTo(InactiveActionCorner)
    }

    @Test
    fun topRightCursor_topRightActionCornerEmitted() = setUpAndRunTest {
        val model by collectLastValue(underTest.actionCornerState)
        val actionCornerPos = display.topRightCursorPos
        addCursorPosition(actionCornerPos)
        assertThat(model)
            .isEqualTo(ActiveActionCorner(ActionCornerRegion.TOP_RIGHT, actionCornerPos.displayId))
    }

    @Test
    fun outOfBoundTopRightCursor_noActionCornerEmitted() = setUpAndRunTest {
        val model by collectLastValue(underTest.actionCornerState)
        val actionCornerPos = display.topRightCursorPos
        addCursorPosition(
            CursorPosition(
                x = actionCornerPos.x - 1,
                y = actionCornerPos.y + 1,
                actionCornerPos.displayId,
            )
        )
        assertThat(model).isEqualTo(InactiveActionCorner)
    }

    @Test
    fun bottomLeftCursor_bottomLeftActionCornerEmitted() = setUpAndRunTest {
        val model by collectLastValue(underTest.actionCornerState)
        val actionCornerPos = display.bottomLeftCursorPos
        addCursorPosition(actionCornerPos)
        assertThat(model)
            .isEqualTo(
                ActiveActionCorner(ActionCornerRegion.BOTTOM_LEFT, actionCornerPos.displayId)
            )
    }

    @Test
    fun outOfBoundBottomLeftCursor_noActionCornerEmitted() = setUpAndRunTest {
        val model by collectLastValue(underTest.actionCornerState)
        val actionCornerPos = display.bottomLeftCursorPos
        addCursorPosition(
            CursorPosition(
                x = actionCornerPos.x + 1,
                y = actionCornerPos.y - 1,
                actionCornerPos.displayId,
            )
        )
        assertThat(model).isEqualTo(InactiveActionCorner)
    }

    @Test
    fun bottomRightCursor_bottomRightActionCornerEmitted() = setUpAndRunTest {
        val model by collectLastValue(underTest.actionCornerState)
        val actionCornerPos = display.bottomRightCursorPos
        addCursorPosition(actionCornerPos)
        assertThat(model)
            .isEqualTo(
                ActiveActionCorner(ActionCornerRegion.BOTTOM_RIGHT, actionCornerPos.displayId)
            )
    }

    @Test
    fun outOfBoundBottomRightCursor_noActionCornerEmitted() = setUpAndRunTest {
        val model by collectLastValue(underTest.actionCornerState)
        val actionCornerPos = display.bottomRightCursorPos
        addCursorPosition(
            CursorPosition(
                x = actionCornerPos.x - 1,
                y = actionCornerPos.y - 1,
                actionCornerPos.displayId,
            )
        )
        assertThat(model).isEqualTo(InactiveActionCorner)
    }

    @Test
    fun actionCornerCursor_moveOutOfBound_reEnterActionCorner_secondActiveActionCornerEmitted() =
        setUpAndRunTest {
            // Filter out InactiveActionCorner for test readability
            val models by
                collectValues(underTest.actionCornerState.filter { it != InactiveActionCorner })
            val actionCornerPos = display.bottomRightCursorPos
            addCursorPosition(actionCornerPos)
            addCursorPosition(CursorPosition(x = 1000f, y = 1000f, actionCornerPos.displayId))
            addCursorPosition(actionCornerPos)

            val bottomRightModel =
                ActiveActionCorner(ActionCornerRegion.BOTTOM_RIGHT, actionCornerPos.displayId)
            assertThat(models).containsExactly(bottomRightModel, bottomRightModel).inOrder()
        }

    @Test
    fun actionCornerCursor_moveInsideSameCorner_OneActionCornerEmitted() = setUpAndRunTest {
        val models by collectValues(underTest.actionCornerState.drop(1))
        val actionCornerPos = display.bottomRightCursorPos
        addCursorPosition(actionCornerPos)
        // Move within the same corner
        addCursorPosition(
            CursorPosition(actionCornerPos.x + 1, actionCornerPos.y + 1, actionCornerPos.displayId)
        )
        addCursorPosition(
            CursorPosition(actionCornerPos.x + 2, actionCornerPos.y + 2, actionCornerPos.displayId)
        )

        assertThat(models.size).isEqualTo(1)
    }

    @Test
    fun actionCornerState_remainsInactive_whenCursorMovesIntoActiveArea_butDebounceNotMet() =
        setUpAndRunTest {
            val model by collectLastValue(underTest.actionCornerState)

            val actionCornerPos = display.bottomRightCursorPos
            cursorPositionRepository.addCursorPosition(actionCornerPos)

            // Debounce duration has not elapsed yet
            assertThat(model).isEqualTo(InactiveActionCorner)
        }

    private fun Kosmos.addCursorPosition(cursorPosition: CursorPosition) {
        cursorPositionRepository.addCursorPosition(cursorPosition)
        advanceTimeBy(DEBOUNCE_DELAY + 1.milliseconds)
    }

    private fun setUpAndRunTest(testBody: suspend Kosmos.() -> Unit) =
        kosmos.runTest {
            settingsRepository.setInt(ACTION_CORNER_TOP_LEFT_ACTION, ACTION_CORNER_ACTION_HOME)
            testBody()
        }

    private fun createDisplayWindowProperties() =
        DisplayWindowProperties(
            DEFAULT_DISPLAY,
            TYPE_BASE_APPLICATION,
            context,
            windowManager,
            kosmos.layoutInflater,
        )

    companion object {
        private val metrics = WindowMetrics(Rect(0, 0, 2560, 1600), mock<WindowInsets>(), 2f)
        private const val ACTION_CORNER_DP = 8f
        private val DEBOUNCE_DELAY = 50.milliseconds
        private val cornerSize = ACTION_CORNER_DP * metrics.density

        private val display =
            Display(
                // Place the cursor just inside the bound for testing, by putting it at the opposite
                // corner
                // e.g. below shows the cursor position it tests for bottom left corner
                //                       +-----+ <-- cursor position is placed here for testing
                //                       |     |
                // bottom left corner -> +-----+
                topLeftCursorPos = CursorPosition(cornerSize, cornerSize, DEFAULT_DISPLAY),
                topRightCursorPos =
                    CursorPosition(
                        metrics.bounds.width() - cornerSize,
                        cornerSize,
                        DEFAULT_DISPLAY,
                    ),
                bottomLeftCursorPos =
                    CursorPosition(
                        cornerSize,
                        metrics.bounds.height() - cornerSize,
                        DEFAULT_DISPLAY,
                    ),
                bottomRightCursorPos =
                    CursorPosition(
                        metrics.bounds.width() - cornerSize,
                        metrics.bounds.height() - cornerSize,
                        DEFAULT_DISPLAY,
                    ),
            )
    }

    private data class Display(
        val topLeftCursorPos: CursorPosition,
        val topRightCursorPos: CursorPosition,
        val bottomLeftCursorPos: CursorPosition,
        val bottomRightCursorPos: CursorPosition,
    )
}
