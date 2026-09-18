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

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.Browser
import android.provider.Settings
import android.testing.TestableContext
import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import com.android.systemui.accessibility.accessibilitymenu.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test class for [HelpPreference]. */
class HelpPreferenceTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val context = TestableContext(InstrumentationRegistry.getInstrumentation().targetContext)

    @Mock private lateinit var mockPackageManager: PackageManager
    private lateinit var helpPreference: HelpPreference

    @Before
    fun setUp() {
        helpPreference = HelpPreference()
        context.setMockPackageManager(mockPackageManager)
    }

    @Test
    fun getKey_returnsCorrectKey() {
        assertThat(helpPreference.key).isEqualTo("pref_help")
    }

    @Test
    fun getTitle_returnsCorrectTitleResource() {
        assertThat(helpPreference.title).isEqualTo(R.string.pref_help_title)
    }

    @Test
    fun getPurpose_returnsCorrectPurposeResource() {
        assertThat(helpPreference.purpose).isEqualTo(R.string.pref_help_purpose)
    }

    @Test
    fun intent_returnsIntentWithViewActionAndCorrectUri() {
        val intent = helpPreference.intent(context)
        assertThat(intent).isNotNull()
        assertThat(intent!!.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.`package`).isNull() // Should open in default browser
        assertThat(intent.data).isEqualTo(context.getString(R.string.help_url).toUri())
        assertThat(intent.getStringExtra(Browser.EXTRA_APPLICATION_ID))
            .isEqualTo(context.packageName)
    }

    @Test
    fun isAvailable_setupWizardNotComplete_returnsFalse() {
        setSetupWizardComplete(isComplete = false)
        setBrowserIntentResolvable(isResolvable = true)

        assertThat(helpPreference.isAvailable(context)).isFalse()
    }

    @Test
    fun isAvailable_noBrowserAvailable_returnsFalse() {
        setSetupWizardComplete(isComplete = true)
        setBrowserIntentResolvable(isResolvable = false)

        assertThat(helpPreference.isAvailable(context)).isFalse()
    }

    @Test
    fun isAvailable_setupWizardCompleteAndBrowserAvailable_returnsTrue() {
        setSetupWizardComplete(isComplete = true)
        setBrowserIntentResolvable(isResolvable = true)

        assertThat(helpPreference.isAvailable(context)).isTrue()
    }

    @Test
    fun indexable_returnsFalse() {
        assertThat(helpPreference.indexable).isFalse()
    }

    private fun setSetupWizardComplete(isComplete: Boolean) {
        Settings.Secure.putInt(
            context.contentResolver,
            Settings.Secure.USER_SETUP_COMPLETE,
            if (isComplete) 1 else 0,
        )
    }

    private fun setBrowserIntentResolvable(isResolvable: Boolean) {
        whenever(mockPackageManager.queryIntentActivities(any<Intent>(), any<Int>()))
            .thenReturn(if (isResolvable) listOf(mock<ResolveInfo>()) else emptyList<ResolveInfo>())
    }
}
