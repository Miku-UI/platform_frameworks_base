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

package com.android.systemui.privacy

import android.content.testableContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PrivacyChipContentDescriptionGeneratorTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply {
            testableContext.orCreateTestableResources.apply {
                overrideResource(R.string.ongoing_privacy_dialog_separator, SEPARATOR)
                overrideResource(R.string.ongoing_privacy_dialog_last_separator, FINAL_SEPARATOR)
            }
        }

    private val underTest = PrivacyChipContentDescriptionGenerator(kosmos.testableContext)

    @Test
    fun noElements_emptyString() =
        kosmos.runTest {
            assertThat(underTest.joinTypesForContentDescription(emptySet())).isEmpty()
        }

    @Test
    fun oneElement_justThatString() =
        kosmos.runTest {
            PrivacyType.entries.forEach {
                assertThat(underTest.joinTypesForContentDescription(setOf(it)))
                    .isEqualTo(it.getName(testableContext))
            }
        }

    @Test
    fun twoElements_joinedByFinalSeparator() =
        kosmos.runTest {
            val types = setOf(PrivacyType.TYPE_CAMERA, PrivacyType.TYPE_LOCATION)
            val expected =
                types.sorted().joinToString(FINAL_SEPARATOR) { it.getName(testableContext) }
            assertThat(underTest.joinTypesForContentDescription(types)).isEqualTo(expected)
        }

    @Test
    fun threeElements_joinedBySeparator_andThenFinalSeparator() =
        kosmos.runTest {
            val types =
                setOf(
                    PrivacyType.TYPE_CAMERA,
                    PrivacyType.TYPE_LOCATION,
                    PrivacyType.TYPE_MICROPHONE,
                )
            val expected =
                types
                    .sorted()
                    .map { it.getName(testableContext) }
                    .let { sortedList ->
                        "${sortedList[0]}$SEPARATOR${sortedList[1]}$FINAL_SEPARATOR${sortedList[2]}"
                    }
            assertThat(underTest.joinTypesForContentDescription(types)).isEqualTo(expected)
        }

    private companion object {
        const val SEPARATOR = ", "
        const val FINAL_SEPARATOR = " and "
    }
}
