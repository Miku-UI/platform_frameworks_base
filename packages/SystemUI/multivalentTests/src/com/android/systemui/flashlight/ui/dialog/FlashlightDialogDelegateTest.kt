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

package com.android.systemui.flashlight.ui.dialog

import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.Expandable
import com.android.systemui.animation.mockDialogTransitionAnimator
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class FlashlightDialogDelegateTest : SysuiTestCase() {

    val kosmos = testKosmos()

    @Test
    fun createAndShowDialog_whenNoExpandable_dialogIsShowing() =
        kosmos.runTest {
            val underTest = kosmos.flashlightDialogDelegate

            val dialog = underTest.createDialog()

            assertThat(dialog.isShowing).isFalse()

            underTest.showDialog()

            assertThat(dialog.isShowing).isTrue()
        }

    @Test
    fun showDialog_withExpandable_animates() =
        kosmos.runTest {
            val underTest = flashlightDialogDelegateWithMockAnimator
            val expandable = mock<Expandable> {}
            whenever(expandable.dialogTransitionController(any())).thenReturn(mock())

            underTest.showDialog(expandable)

            verify(mockDialogTransitionAnimator).show(any(), any(), anyBoolean())
        }

    @Test
    fun showDialog_withoutExpandable_doesNotAnimate() =
        kosmos.runTest {
            val underTest = flashlightDialogDelegateWithMockAnimator

            underTest.showDialog()

            verify(mockDialogTransitionAnimator, never()).show(any(), any(), anyBoolean())
        }
}
