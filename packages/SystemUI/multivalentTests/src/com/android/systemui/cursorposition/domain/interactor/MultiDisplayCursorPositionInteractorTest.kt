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

package com.android.systemui.cursorposition.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.domain.data.repository.multiDisplayCursorPositionRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class MultiDisplayCursorPositionInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val repo = kosmos.multiDisplayCursorPositionRepository

    private val Kosmos.underTest by Kosmos.Fixture { MultiDisplayCursorPositionInteractor(repo) }

    @Test
    fun getCursorPosition_returnsCorrectPosition() =
        kosmos.runTest {
            val currentCursorPosition by collectLastValue(underTest.cursorPositions)

            val cursorPosition = CursorPosition(100f, 200f, 123)
            repo.addCursorPosition(cursorPosition)

            assertThat(currentCursorPosition).isEqualTo(cursorPosition)
        }
}
