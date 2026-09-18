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

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.fragment.app.testing.FragmentScenario
import androidx.preference.Preference
import androidx.test.core.app.ApplicationProvider
import com.android.systemui.accessibility.accessibilitymenu.R
import com.android.systemui.accessibility.accessibilitymenu.activity.A11yMenuSettingsActivity.A11yMenuPreferenceFragment
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Tests for [A11yMenuPreferenceFragment]. */
class A11yMenuPreferenceFragmentTest {

    @Test
    fun launchFragment_preferenceItemsCountIsTwo() {
        FragmentScenario.launch(A11yMenuPreferenceFragment::class.java).onFragment { fragment ->
            assertThat(fragment.preferenceScreen.preferenceCount).isEqualTo(2)
        }
    }

    @Test
    fun launchFragment_verifyPreferencesOrder() {
        FragmentScenario.launch(A11yMenuPreferenceFragment::class.java).onFragment { fragment ->
            assertThat(fragment.preferenceScreen.getPreference(0).key)
                .isEqualTo(LargeButtonPreference.KEY)
            assertThat(fragment.preferenceScreen.getPreference(1).key).isEqualTo(HelpPreference.KEY)
        }
    }

    @Test
    fun launchFragment_canResolveBrowserIntent_helpPreferenceIsVisible() {
        assumeTrue(canResolveBrowserIntent())

        FragmentScenario.launch(A11yMenuPreferenceFragment::class.java).onFragment { fragment ->
            val helpPreference = fragment.findPreference<Preference>(HelpPreference.KEY)

            assertThat(helpPreference).isNotNull()
            assertThat(helpPreference!!.isVisible).isTrue()
        }
    }

    @Test
    fun launchFragment_cannotResolveBrowserIntent_helpPreferenceIsInvisible() {
        assumeFalse(canResolveBrowserIntent())

        FragmentScenario.launch(A11yMenuPreferenceFragment::class.java).onFragment { fragment ->
            val helpPreference = fragment.findPreference<Preference>(HelpPreference.KEY)

            assertThat(helpPreference).isNotNull()
            assertThat(helpPreference!!.isVisible).isFalse()
        }
    }

    private fun canResolveBrowserIntent(): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val browserIntent = Intent(Intent.ACTION_VIEW, context.getString(R.string.help_url).toUri())

        return context.packageManager.queryIntentActivities(browserIntent, 0).isNotEmpty()
    }
}
