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

import android.content.ComponentName
import android.content.Intent
import android.platform.test.flag.junit.SetFlagsRule
import androidx.fragment.app.testing.FragmentScenario
import androidx.preference.PreferenceFragmentCompat
import com.android.settingslib.datastore.SharedPreferencesStorage
import com.android.settingslib.metadata.FixedArrayMap
import com.android.settingslib.metadata.PreferenceScreenRegistry
import com.android.settingslib.preference.CatalystScreenTestCase
import com.android.systemui.accessibility.accessibilitymenu.Flags
import com.android.systemui.accessibility.accessibilitymenu.R
import com.android.systemui.accessibility.accessibilitymenu.activity.A11yMenuSettingsActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test class for [TopLevelSettingsScreen]. */
class TopLevelSettingsScreenTest : CatalystScreenTestCase() {
    private val flagName = Flags.FLAG_CATALYST_A11Y_MENU

    @get:Rule val setFlagsRule = SetFlagsRule()

    override val preferenceScreenCreator = TopLevelSettingsScreen()

    @Before
    fun setUp() {
        // Setup preference screen registry for test
        val storage =
            SharedPreferencesStorage.getDefault(
                context = appContext,
                name = "a11y_menu_settings_backup",
            )
        PreferenceScreenRegistry.setKeyValueStoreProvider { context, preference -> storage }
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(1) { it.put(TopLevelSettingsScreen.KEY) { TopLevelSettingsScreen() } }
    }

    @Suppress("DEPRECATION")
    override fun enableCatalystScreen() {
        setFlagsRule.enableFlags(flagName)
    }

    @Suppress("DEPRECATION")
    override fun disableCatalystScreen() {
        setFlagsRule.disableFlags(flagName)
    }

    @Test
    fun getKey() {
        assertThat(preferenceScreenCreator.key).isEqualTo(TopLevelSettingsScreen.KEY)
    }

    @Test
    fun getTitle() {
        assertThat(preferenceScreenCreator.title)
            .isEqualTo(R.string.accessibility_menu_settings_name)
    }

    @Test
    fun getPurpose() {
        assertThat(preferenceScreenCreator.purpose)
            .isEqualTo(R.string.accessibility_menu_top_level_settings_purpose)
    }

    @Test
    fun getLaunchIntent_returnsA11yMenuSettingsActivityIntent() {
        val intent = preferenceScreenCreator.getLaunchIntent(appContext, null)
        assertThat(intent).isNotNull()
        assertThat(intent!!.action).isEqualTo(Intent.ACTION_MAIN)
        assertThat(intent.component)
            .isEqualTo(ComponentName(appContext, A11yMenuSettingsActivity::class.java))
    }

    override fun launchFragmentScenario(
        fragmentClass: Class<PreferenceFragmentCompat>
    ): FragmentScenario<PreferenceFragmentCompat> {
        val scenario = super.launchFragmentScenario(fragmentClass)
        scenario.onFragment { fragment ->
            // Pre catalyst, we didn't set up the preference screen's title.
            // Hence, we had to add the title to preference screen directly in order to test the
            // migration test case.
            // We also have a separate test case to test the title in post-catalyst scenario
            fragment.preferenceScreen.title =
                fragment.getString(R.string.accessibility_menu_settings_name)
        }

        return scenario
    }
}
