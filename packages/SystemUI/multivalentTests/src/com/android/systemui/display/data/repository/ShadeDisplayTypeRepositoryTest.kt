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

package com.android.systemui.display.data.repository

import android.testing.TestableLooper
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.domain.interactor.shadeDisplayTypeRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@SmallTest
class ShadeDisplayTypeRepositoryTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest = kosmos.shadeDisplayTypeRepository

    @Before
    fun setup() {
        kosmos.setDisplayType(Display.DEFAULT_DISPLAY, Display.TYPE_INTERNAL)
    }

    @Test
    fun displayType_shadeMovesToExternal_returnsExternal() =
        kosmos.runTest {
            val type by collectLastValue(underTest.displayType)

            assertThat(type).isEqualTo(Display.TYPE_INTERNAL)

            setDisplayType(Display.DEFAULT_DISPLAY + 1, Display.TYPE_EXTERNAL)
            fakeShadeDisplaysRepository.setPendingDisplayId(Display.DEFAULT_DISPLAY + 1)

            assertThat(type).isEqualTo(Display.TYPE_EXTERNAL)
        }

    @Test
    fun displayType_displayTypeChanges_returnsExternal() =
        kosmos.runTest {
            val type by collectLastValue(underTest.displayType)

            assertThat(type).isEqualTo(Display.TYPE_INTERNAL)

            setDisplayType(Display.DEFAULT_DISPLAY + 1, Display.TYPE_EXTERNAL)
            fakeShadeDisplaysRepository.setPendingDisplayId(Display.DEFAULT_DISPLAY + 1)

            assertThat(type).isEqualTo(Display.TYPE_EXTERNAL)
        }

    @Test
    fun displayType_goesOutAndBackToInternal_returnsInternal() =
        kosmos.runTest {
            val type by collectLastValue(underTest.displayType)

            assertThat(type).isEqualTo(Display.TYPE_INTERNAL)

            setDisplayType(Display.DEFAULT_DISPLAY + 1, Display.TYPE_EXTERNAL)
            fakeShadeDisplaysRepository.setPendingDisplayId(Display.DEFAULT_DISPLAY + 1)

            assertThat(type).isEqualTo(Display.TYPE_EXTERNAL)

            setDisplayType(Display.DEFAULT_DISPLAY + 1, Display.TYPE_INTERNAL)

            assertThat(type).isEqualTo(Display.TYPE_INTERNAL)
        }

    @Test
    fun displayType_anotherDisplayIdChanges_stillInternal() =
        kosmos.runTest {
            val type by collectLastValue(underTest.displayType)

            assertThat(type).isEqualTo(Display.TYPE_INTERNAL)

            setDisplayType(Display.DEFAULT_DISPLAY + 1, Display.TYPE_EXTERNAL)

            assertThat(type).isEqualTo(Display.TYPE_INTERNAL)
        }
}
