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

package com.android.systemui.log.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.DumpManager
import com.android.systemui.log.LogcatEchoTrackerAlways
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
class LogBufferFactoryImplTest : SysuiTestCase() {

    private val dumpManager: DumpManager = mock()

    private val underTest = LogBufferFactoryImpl(dumpManager, LogcatEchoTrackerAlways())

    @Test
    fun create_alwaysCreatesNewInstance() {
        val b1 = underTest.create(NAME_1, SIZE)
        val b1Copy = underTest.create(NAME_1, SIZE)
        val b2 = underTest.create(NAME_2, SIZE)
        val b2Copy = underTest.create(NAME_2, SIZE)

        assertThat(b1).isNotSameInstanceAs(b1Copy)
        assertThat(b1).isNotSameInstanceAs(b2)
        assertThat(b2).isNotSameInstanceAs(b2Copy)
    }

    @Test
    fun getOrCreate_reusesInstance() {
        val b1 = underTest.getOrCreate(NAME_1, SIZE)
        val b1Copy = underTest.getOrCreate(NAME_1, SIZE)
        val b2 = underTest.getOrCreate(NAME_2, SIZE)
        val b2Copy = underTest.getOrCreate(NAME_2, SIZE)

        assertThat(b1).isSameInstanceAs(b1Copy)
        assertThat(b2).isSameInstanceAs(b2Copy)
        assertThat(b1).isNotSameInstanceAs(b2)
        assertThat(b1Copy).isNotSameInstanceAs(b2Copy)
    }

    companion object {
        private const val NAME_1 = "TestBuffer1"
        private const val NAME_2 = "TestBuffer2"
        private const val SIZE = 100
    }
}
