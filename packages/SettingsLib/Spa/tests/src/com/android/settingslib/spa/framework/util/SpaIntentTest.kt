/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.framework.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.spa.framework.common.NullPageProvider
import com.android.settingslib.spa.framework.common.SettingsEntryBuilder
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.framework.common.createSettingsPage
import com.android.settingslib.spa.tests.testutils.SpaEnvironmentForTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpaIntentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val spaEnvironment = SpaEnvironmentForTest(context)

    @Before
    fun setEnvironment() {
        SpaEnvironmentFactory.reset(spaEnvironment)
    }

    @Test
    fun createIntent_nullPage_returnsNull() {
        val nullPage = NullPageProvider.createSettingsPage()
        assertThat(nullPage.createIntent()).isNull()
        assertThat(SettingsEntryBuilder.createInject(nullPage).build().createIntent()).isNull()
    }

    @Test
    fun createIntent_normalPage_returnsCorrectIntent() {
        val page = spaEnvironment.createPage("SppHome")

        val pageIntent = page.createIntent()

        assertThat(pageIntent).isNotNull()
        assertThat(pageIntent!!.getDestination()).isEqualTo(page.buildRoute())
        assertThat(pageIntent.highlightItemKey).isNull()
        assertThat(pageIntent.getSessionName()).isNull()
    }

    @Test
    fun createIntent_entry_returnsCorrectIntent() {
        val page = spaEnvironment.createPage("SppHome")
        val entry = SettingsEntryBuilder.createInject(page).build()

        val entryIntent = entry.createIntent(SESSION_SEARCH)

        assertThat(entryIntent).isNotNull()
        assertThat(entryIntent!!.getDestination()).isEqualTo(page.buildRoute())
        assertThat(entryIntent.highlightItemKey).isNull()
        assertThat(entryIntent.getSessionName()).isEqualTo(SESSION_SEARCH)
    }

    @Test
    fun appendSpaParams_returnsCorrectIntent() {
        val page = spaEnvironment.createPage("SppHome")

        val pageIntent =
            page.createIntent()!!.appendSpaParams(highlightItemKey = HIGHLIGHT_ITEM_KEY)

        assertThat(pageIntent.highlightItemKey).isEqualTo(HIGHLIGHT_ITEM_KEY)
    }

    private companion object {
        const val HIGHLIGHT_ITEM_KEY = "highlight_item_key"
    }
}
