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

package com.android.settingslib.metadata

import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.preferencesapi.types.AnyString
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PreferenceScreenMetadataTrampolineTest {

    private val screenKey = "test_screen_key"
    private val mockCatalystFlagProvider = mock<CatalystFlagProvider>()
    private lateinit var originalProvider: CatalystFlagProvider

    @Before
    fun setUp() {
        originalProvider = CatalystFlagProviderFactory.getInstance()
        CatalystFlagProviderFactory.setProvider(mockCatalystFlagProvider)
        whenever(mockCatalystFlagProvider.catalystUseKeyParameters()).thenReturn(true)
    }

    @After
    fun tearDown() {
        CatalystFlagProviderFactory.setProvider(originalProvider)
    }

    @Test
    fun getTrampolinedLaunchIntent_standardLaunch_setsRequiredExtras() {
        val screenMetadata = FakeScreenMetadata(screenKey)

        val result = screenMetadata.getTrampolinedLaunchIntent(null)

        assertThat(result.action).isEqualTo(PreferenceScreenMetadata.LAUNCH_SETTINGS_PAGES_ACTION)
        assertThat(result.`package`).isEqualTo("com.android.settings")
        assertThat(result.getStringExtra(PreferenceScreenMetadata.EXTRA_SCREEN_KEY)).isEqualTo(
            screenKey
        )
    }

    @Test
    fun getTrampolinedLaunchIntent_withMetadata_setsFragmentArgKey() {
        val screenMetadata = FakeScreenMetadata(screenKey)
        val highlightMetadata = mock<PreferenceMetadata>()
        whenever(highlightMetadata.key).thenReturn("highlight_key")

        val result = screenMetadata.getTrampolinedLaunchIntent(highlightMetadata)

        assertThat(
            result.getStringExtra(PreferenceScreenMetadata.EXTRA_FRAGMENT_ARG_KEY)
        ).isEqualTo("highlight_key")
    }

    @Test
    fun getTrampolinedLaunchIntent_catalystV2_preservesKeyParameters() {
        val testSchema = KeyParametersSchema {
            parameter("id", "The ID", required = true, type = AnyString)
        }
        val params = testSchema.prepare("id" to "2737")
        val screenMetadata = FakeScreenMetadata(screenKey, keyParameters = params)
        whenever(mockCatalystFlagProvider.catalystUseKeyParameters()).thenReturn(true)

        val result = screenMetadata.getTrampolinedLaunchIntent(null)

        val resultArgs = result.getBundleExtra(PreferenceScreenMetadata.EXTRA_SCREEN_ARGS)
        assertThat(resultArgs).isNotNull()
        assertThat(resultArgs?.getString("id")).isEqualTo("2737")
    }

    @Test
    fun getTrampolinedLaunchIntent_catalystV1_preservesArguments() {
        val args = Bundle().apply { putString("key", "value") }
        val screenMetadata = FakeScreenMetadata(screenKey, arguments = args)
        whenever(mockCatalystFlagProvider.catalystUseKeyParameters()).thenReturn(false)

        val result = screenMetadata.getTrampolinedLaunchIntent(null)

        val resultArgs = result.getBundleExtra(PreferenceScreenMetadata.EXTRA_SCREEN_ARGS)
        assertThat(resultArgs?.getString("key")).isEqualTo("value")
    }

    /** Helper class to provide metadata for testing. */
    private class FakeScreenMetadata(
        override val key: String,
        override val arguments: Bundle? = null,
        override val keyParameters: ValidatedKeyParameters? = null
    ) : PreferenceScreenMetadata {
        override val purpose = 0
        override fun fragmentClass() = null
        override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
            mock<PreferenceHierarchy>()
    }
}