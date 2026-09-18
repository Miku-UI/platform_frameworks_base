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

package com.android.systemui.screencapture.domain.interactor

import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.domain.data.repository.fakeMultiDisplayCursorPositionRepository
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.shade.data.repository.fakeFocusedDisplayRepository
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureTargetDisplayInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()
    private val underTest = kosmos.screenCaptureTargetDisplayInteractor

    @Test
    fun targetDisplay_cursorDisplayIdExists_usesCursorDisplay() =
        kosmos.runTest {
            val display1 = display(id = 1)
            val display2 = display(id = 2)
            kosmos.displayRepository.addDisplays(display1, display2)
            kosmos.fakeMultiDisplayCursorPositionRepository.addCursorPosition(
                CursorPosition(0f, 0f, display2.displayId)
            )
            kosmos.fakeFocusedDisplayRepository.setDisplayId(display1.displayId)

            val targetDisplay by collectLastValue(underTest.targetDisplay)

            assertThat(targetDisplay?.displayId).isEqualTo(display2.displayId)
        }

    @Test
    fun targetDisplay_cursorDisplayIdIsNull_usesFocusedDisplay() =
        kosmos.runTest {
            val display1 = display(id = 1)
            val display2 = display(id = 2)
            kosmos.displayRepository.addDisplays(display1, display2)
            kosmos.fakeMultiDisplayCursorPositionRepository.cursorPositions.value = null
            kosmos.fakeFocusedDisplayRepository.setDisplayId(display2.displayId)

            val targetDisplay by collectLastValue(underTest.targetDisplay)

            assertThat(targetDisplay?.displayId).isEqualTo(display2.displayId)
        }

    @Test
    fun targetDisplay_neitherIdMatches_fallsBackToFirstDisplay() =
        kosmos.runTest {
            val display1 = display(id = 1)
            val display2 = display(id = 2)
            kosmos.displayRepository.removeDisplay(Display.DEFAULT_DISPLAY)
            kosmos.displayRepository.addDisplays(display1, display2)
            kosmos.fakeMultiDisplayCursorPositionRepository.addCursorPosition(
                CursorPosition(0f, 0f, 3)
            )
            kosmos.fakeFocusedDisplayRepository.setDisplayId(4)

            val targetDisplay by collectLastValue(underTest.targetDisplay)

            assertThat(targetDisplay?.displayId).isEqualTo(display1.displayId)
        }

    @Test
    fun targetDisplay_noDisplays_emitsNothing() =
        kosmos.runTest {
            kosmos.displayRepository.removeDisplay(Display.DEFAULT_DISPLAY)
            kosmos.fakeFocusedDisplayRepository.setDisplayId(1)

            val targetDisplay by collectLastValue(underTest.targetDisplay)

            assertThat(targetDisplay).isNull()
        }
}
