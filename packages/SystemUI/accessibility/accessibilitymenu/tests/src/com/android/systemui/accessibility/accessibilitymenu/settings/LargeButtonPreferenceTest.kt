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

package com.android.systemui.accessibility.accessibilitymenu.settings

import com.android.systemui.accessibility.accessibilitymenu.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/** Test class for [LargeButtonPreference]. */
class LargeButtonPreferenceTest {

    private lateinit var largeButtonPreference: LargeButtonPreference

    @Before
    fun setUp() {
        largeButtonPreference = LargeButtonPreference()
    }

    @Test
    fun getKey_returnsCorrectKey() {
        assertThat(largeButtonPreference.key).isEqualTo(LargeButtonPreference.KEY)
    }

    @Test
    fun getTitle_returnsCorrectTitleResource() {
        assertThat(largeButtonPreference.title)
            .isEqualTo(R.string.accessibility_menu_large_buttons_title)
    }

    @Test
    fun getSummary_returnsCorrectSummaryResource() {
        assertThat(largeButtonPreference.summary)
            .isEqualTo(R.string.accessibility_menu_large_buttons_summary)
    }

    @Test
    fun getPurpose_returnsCorrectPurposeResource() {
        assertThat(largeButtonPreference.purpose).isEqualTo(R.string.large_button_setting_purpose)
    }
}
