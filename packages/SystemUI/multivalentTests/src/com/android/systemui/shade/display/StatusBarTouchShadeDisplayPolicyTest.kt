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

package com.android.systemui.shade.display

import android.view.Display
import android.view.Display.TYPE_EXTERNAL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.coroutines.collectValues
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shade.data.repository.statusBarTouchShadeDisplayPolicy
import com.android.systemui.shade.domain.interactor.notificationElement
import com.android.systemui.shade.domain.interactor.qsElement
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarTouchShadeDisplayPolicyTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val displayRepository = kosmos.displayRepository
    private val notificationElement = kosmos.notificationElement
    private val qsElement = kosmos.qsElement

    private val underTest = kosmos.statusBarTouchShadeDisplayPolicy

    @Test
    fun displayId_defaultsToDefaultDisplay() {
        assertThat(underTest.displayId.value).isEqualTo(Display.DEFAULT_DISPLAY)
    }

    @Test
    fun setExpansionIntent_updatesDisplayId() =
        testScope.runTest {
            val displayId by collectLastValue(underTest.displayId)
            displayRepository.addDisplays(display(id = 2, type = TYPE_EXTERNAL))

            underTest.setExpansionIntentForElement(notificationElement, 2)

            assertThat(displayId).isEqualTo(2)
        }

    @Test
    fun setExpansionIntent_nonExistentDisplay_doesNotUpdateDisplayId() =
        testScope.runTest {
            val displayIds by collectValues(underTest.displayId)
            assertThat(displayIds).isEqualTo(listOf(Display.DEFAULT_DISPLAY))

            underTest.setExpansionIntentForElement(notificationElement, 2)

            assertThat(displayIds).isEqualTo(listOf(Display.DEFAULT_DISPLAY))
        }

    @Test
    fun setExpansionIntent_afterDisplayRemoved_revertsToDefaultDisplay() =
        testScope.runTest {
            val displayId by collectLastValue(underTest.displayId)
            displayRepository.addDisplays(display(id = 2, type = TYPE_EXTERNAL))
            underTest.setExpansionIntentForElement(notificationElement, 2)
            assertThat(displayId).isEqualTo(2)

            displayRepository.removeDisplay(2)

            // Never set, as 2 was not a display according to the repository.
            assertThat(displayId).isEqualTo(Display.DEFAULT_DISPLAY)
        }

    @Test
    fun setNotificationExpansionIntent_intentIsSet() =
        testScope.runTest {
            underTest.setExpansionIntentForElement(notificationElement, 2)
            assertThat(underTest.consumeExpansionIntent()).isEqualTo(notificationElement)
        }

    @Test
    fun setQsExpansionIntent_intentIsSet() =
        testScope.runTest {
            underTest.setExpansionIntentForElement(qsElement, 2)
            assertThat(underTest.consumeExpansionIntent()).isEqualTo(qsElement)
        }

    @Test
    fun consumeExpansionIntent_returnsNullAfterConsumed() =
        testScope.runTest {
            underTest.setExpansionIntentForElement(notificationElement, 2)
            underTest.consumeExpansionIntent()

            assertThat(underTest.consumeExpansionIntent()).isNull()
        }
}
