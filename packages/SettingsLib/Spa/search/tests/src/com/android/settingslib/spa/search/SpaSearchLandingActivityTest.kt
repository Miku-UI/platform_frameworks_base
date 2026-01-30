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

package com.android.settingslib.spa.search

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.search.SpaSearchLanding.BundleValue
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingFragment
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingKey
import com.android.settingslib.spa.search.SpaSearchLanding.SpaSearchLandingSpaPage
import com.android.settingslib.spa.search.SpaSearchLandingActivity.Companion.EXTRA_FRAGMENT_ARG_KEY
import com.android.settingslib.spa.search.test.TestSpaSearchLandingActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaSearchLandingActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        TestSpaSearchLandingActivity.clear()
    }

    @Test
    fun tryLaunch_spaPage() {
        val key =
            SpaSearchLandingKey.newBuilder()
                .setSpaPage(
                    SpaSearchLandingSpaPage.newBuilder()
                        .setDestination(DESTINATION)
                        .setHighlightItemKey(HIGHLIGHT_ITEM_KEY)
                )
                .build()

        ActivityScenario.launch<TestSpaSearchLandingActivity>(
            Intent(context, TestSpaSearchLandingActivity::class.java).apply {
                putExtra(EXTRA_FRAGMENT_ARG_KEY, key.encodeToString())
            }
        )

        assertThat(TestSpaSearchLandingActivity.startSpaPageCalledDestination)
            .isEqualTo(DESTINATION)
        assertThat(TestSpaSearchLandingActivity.startSpaPageCalledHighlightItemKey)
            .isEqualTo(HIGHLIGHT_ITEM_KEY)
    }

    @Test
    fun tryLaunch_fragment() {
        val key =
            SpaSearchLandingKey.newBuilder()
                .setFragment(
                    SpaSearchLandingFragment.newBuilder()
                        .setFragmentName(DESTINATION)
                        .setPreferenceKey(PREFERENCE_KEY)
                        .putArguments(
                            ARGUMENT_KEY,
                            BundleValue.newBuilder().setIntValue(ARGUMENT_VALUE).build(),
                        )
                )
                .build()

        ActivityScenario.launch<TestSpaSearchLandingActivity>(
            Intent(context, TestSpaSearchLandingActivity::class.java).apply {
                putExtra(EXTRA_FRAGMENT_ARG_KEY, key.encodeToString())
            }
        )

        assertThat(TestSpaSearchLandingActivity.startFragmentCalledFragmentName)
            .isEqualTo(DESTINATION)
        val arguments = TestSpaSearchLandingActivity.startFragmentCalledArguments!!
        assertThat(arguments.getString(EXTRA_FRAGMENT_ARG_KEY)).isEqualTo(PREFERENCE_KEY)
        assertThat(arguments.getInt(ARGUMENT_KEY)).isEqualTo(ARGUMENT_VALUE)
    }

    private companion object {
        const val DESTINATION = "Destination"
        const val HIGHLIGHT_ITEM_KEY = "highlight_item_key"
        const val PREFERENCE_KEY = "preference_key"
        const val ARGUMENT_KEY = "argument_key"
        const val ARGUMENT_VALUE = 123
    }
}
