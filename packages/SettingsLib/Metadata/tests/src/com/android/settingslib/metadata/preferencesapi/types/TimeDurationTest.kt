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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class TimeDurationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun getDescription_returnsDateFormatDescription() {
        assertThat(TimeDuration.getDescription(context))
            .isEqualTo(
                context.getString(
                    com.android.settingslib.metadata.R.string.time_duration_type_description
                )
            )
    }

    @Test
    fun convertInternalToExternal_returnExpectResults() {
        assertThat(TimeDuration.convertInternalToExternal(0.milliseconds)).isEqualTo(0)
        assertThat(TimeDuration.convertInternalToExternal(499.milliseconds)).isEqualTo(0)
        assertThat(TimeDuration.convertInternalToExternal(500.milliseconds)).isEqualTo(1)
        assertThat(TimeDuration.convertInternalToExternal(1234.seconds)).isEqualTo(1234)
        assertThat(TimeDuration.convertInternalToExternal(365.days)).isEqualTo(365 * 24 * 60 * 60)
        assertThat(TimeDuration.convertInternalToExternal((20 * 365).days))
            .isEqualTo(20 * 365 * 24 * 60 * 60)
    }

    @Test
    fun convertExternalToInternal_returnExpectResults() {
        assertThat(TimeDuration.convertExternalToInternal(0)).isEqualTo(0.seconds)
        assertThat(TimeDuration.convertExternalToInternal(1)).isEqualTo(1.seconds)
        assertThat(TimeDuration.convertExternalToInternal(1234)).isEqualTo(1234.seconds)
        assertThat(TimeDuration.convertExternalToInternal(365 * 24 * 60 * 60)).isEqualTo(365.days)
        assertThat(TimeDuration.convertExternalToInternal(20 * 365 * 24 * 60 * 60))
            .isEqualTo((20 * 365).days)
    }
}
