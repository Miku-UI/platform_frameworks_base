/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade.display.domain.interactor

import android.view.Display.TYPE_EXTERNAL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.display
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.shade.data.repository.fakeFocusedDisplayRepository
import com.android.systemui.shade.data.repository.statusBarTouchShadeDisplayPolicy
import com.android.systemui.shade.domain.interactor.notificationElement
import com.android.systemui.shade.domain.interactor.qsElement
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShadeExpansionTargetDisplayInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val focusedDisplayRepository = kosmos.fakeFocusedDisplayRepository
    private val dualShadeGestureSplitRatio =
        context.resources.getFloat(R.dimen.config_invocationGestureSplitRatio)

    private val underTest = kosmos.shadeExpansionTargetDisplayInteractor

    @Test
    fun onStatusBarTouched_leftSide_setsNotificationIntent() =
        kosmos.runTest {
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)
            displayRepository.addDisplays(display(id = 2, type = TYPE_EXTERNAL))

            underTest.setExpansionIntentFromStatusBarEvent(
                eventX = LEFT_SIDE_X,
                displayId = 2,
                statusBarWidth = STATUS_BAR_WIDTH,
                shadeInvocationSplitRatio = dualShadeGestureSplitRatio,
                isRtl = false,
            )

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(notificationElement)
            assertThat(displayId).isEqualTo(2)
        }

    @Test
    fun onStatusBarTouched_rightSide_setsQsIntent() =
        kosmos.runTest {
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)
            displayRepository.addDisplays(display(id = 2, type = TYPE_EXTERNAL))

            underTest.setExpansionIntentFromStatusBarEvent(
                eventX = RIGHT_SIDE_X,
                displayId = 2,
                statusBarWidth = STATUS_BAR_WIDTH,
                shadeInvocationSplitRatio = dualShadeGestureSplitRatio,
                isRtl = false,
            )

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(qsElement)
            assertThat(displayId).isEqualTo(2)
        }

    @Test
    fun onStatusBarTouched_leftSideOfSplit_intentSetToNotifications() =
        kosmos.runTest {
            val leftSideOfSplit = STATUS_BAR_WIDTH * dualShadeGestureSplitRatio - 1
            underTest.setExpansionIntentFromStatusBarEvent(
                displayId = 2,
                eventX = leftSideOfSplit,
                statusBarWidth = STATUS_BAR_WIDTH,
                shadeInvocationSplitRatio = dualShadeGestureSplitRatio,
                isRtl = false,
            )

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(notificationElement)
        }

    @Test
    fun onStatusBarTouched_rightSideOfSplit_intentSetToQs() =
        kosmos.runTest {
            val rightSideOfSplit = STATUS_BAR_WIDTH * dualShadeGestureSplitRatio + 1
            underTest.setExpansionIntentFromStatusBarEvent(
                displayId = 2,
                eventX = rightSideOfSplit,
                statusBarWidth = STATUS_BAR_WIDTH,
                shadeInvocationSplitRatio = dualShadeGestureSplitRatio,
                isRtl = false,
            )

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(qsElement)
        }

    @Test
    fun onStatusBarTouched_rtl_leftSideOfSplit_intentSetToQs() =
        kosmos.runTest {
            val leftSideOfSplit = STATUS_BAR_WIDTH * (1 - dualShadeGestureSplitRatio) - 1
            underTest.setExpansionIntentFromStatusBarEvent(
                displayId = 2,
                eventX = leftSideOfSplit,
                statusBarWidth = STATUS_BAR_WIDTH,
                shadeInvocationSplitRatio = dualShadeGestureSplitRatio,
                isRtl = true,
            )

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(qsElement)
        }

    @Test
    fun onStatusBarTouched_rtl_rightSideOfSplit_intentSetToNotifications() =
        kosmos.runTest {
            val rightSideOfSplit = STATUS_BAR_WIDTH * (1 - dualShadeGestureSplitRatio) + 1
            underTest.setExpansionIntentFromStatusBarEvent(
                displayId = 2,
                eventX = rightSideOfSplit,
                statusBarWidth = STATUS_BAR_WIDTH,
                shadeInvocationSplitRatio = dualShadeGestureSplitRatio,
                isRtl = true,
            )

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(notificationElement)
        }

    @Test
    fun onNotificationPanelKeyboardShortcut_setsNotificationIntentOnFocusedDisplay() =
        kosmos.runTest {
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)
            displayRepository.addDisplays(display(id = 3, type = TYPE_EXTERNAL))

            focusedDisplayRepository.setDisplayId(3)

            underTest.onNotificationPanelKeyboardShortcut()

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(notificationElement)
            assertThat(displayId).isEqualTo(3)
        }

    @Test
    fun onQSPanelKeyboardShortcut_setsQsIntentOnFocusedDisplay() =
        kosmos.runTest {
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)
            displayRepository.addDisplays(display(id = 4, type = TYPE_EXTERNAL))

            focusedDisplayRepository.setDisplayId(4)

            underTest.onQSPanelKeyboardShortcut()

            assertThat(statusBarTouchShadeDisplayPolicy.consumeExpansionIntent())
                .isEqualTo(qsElement)
            assertThat(displayId).isEqualTo(4)
        }

    @Test
    fun updateShadeDisplay_IfNeeded_updatesPolicy() =
        kosmos.runTest {
            val displayId by collectLastValue(statusBarTouchShadeDisplayPolicy.displayId)
            displayRepository.addDisplays(display(id = 2, type = TYPE_EXTERNAL))

            underTest.updateShadeDisplayIfNeeded(displayId = 2)

            assertThat(displayId).isEqualTo(2)
        }

    companion object {
        private const val STATUS_BAR_WIDTH = 100
        private const val LEFT_SIDE_X = STATUS_BAR_WIDTH * 0.1f
        private const val RIGHT_SIDE_X = STATUS_BAR_WIDTH * 0.95f
    }
}
