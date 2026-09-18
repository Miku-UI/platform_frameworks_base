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

package com.android.settingslib.metadata.preferencesapi.types

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntInRangeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun initialize_noMinAndMax_throwsException() {
        assertThrows(IllegalArgumentException::class.java) { IntInRange(null, null) }
    }

    @Test
    fun getDescription_lowerBound_returnsLowerBoundDescription() {
        assertThat(IntInRange(5, null, 3).getDescription(context))
            .isEqualTo("An integer greater than or equal to 5, with step 3.")
    }

    @Test
    fun getDescription_upperBound_returnsUpperBoundDescription() {
        assertThat(IntInRange(null, 10).getDescription(context))
            .isEqualTo("An integer less than or equal to 10, with step 1.")
    }

    @Test
    fun getDescription_fullRange_returnsUpperBoundDescription() {
        assertThat(IntInRange(6, 10, 2).getDescription(context))
            .isEqualTo("An integer in a given range [6, 10], with step 2.")
    }

    @Test
    fun percentageInt_usesCorrectRange() {
        assertThat(PercentageInt.min).isEqualTo(0)
        assertThat(PercentageInt.max).isEqualTo(100)
        assertThat(PercentageInt.step).isEqualTo(1)
    }
}
