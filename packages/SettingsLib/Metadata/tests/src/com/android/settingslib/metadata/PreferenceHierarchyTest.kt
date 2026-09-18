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

package com.android.settingslib.metadata

import android.content.Context
import android.os.Bundle
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private const val TEST_SCREEN_KEY = "screen_key_2737"

@RunWith(AndroidJUnit4::class)
class PreferenceHierarchyTest {
    @get:Rule
    val mSetFlagsRule = SetFlagsRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val screen = mock<PreferenceScreenMetadata>()
    private val subScreen = mock<PreferenceScreenMetadata>()
    private val preference = mock<PreferenceMetadata> {
        on { key } doReturn TEST_SCREEN_KEY
        on { bindingKey } doReturn TEST_SCREEN_KEY
    }
    private val testScreenMetadata = mock<PreferenceScreenMetadata>()
    private val preferenceScreenMetadataParameterizedFactory =
        mock<PreferenceScreenMetadataParameterizedFactory>()

    private lateinit var originalProvider: CatalystFlagProvider

    @Before
    fun setUp() {
        originalProvider = CatalystFlagProviderFactory.getInstance()
    }

    @After
    fun tearDown() {
        CatalystFlagProviderFactory.setProvider(originalProvider)
    }

    private fun setCatalystUseKeyParameters(value: Boolean) {
        CatalystFlagProviderFactory.setProvider(object : CatalystFlagProvider {
            override fun catalystUseKeyParameters() = value
        })
    }

    @Test
    fun addMetadata() {
        val hierarchy =
            screen.preferenceHierarchy(context) {
                +subScreen order 1
                +preference
            }
        assertThat(hierarchy.children).hasSize(2)
        (hierarchy.children[0] as PreferenceHierarchyNode).apply {
            assertThat(metadata).isSameInstanceAs(subScreen)
            assertThat(order).isEqualTo(1)
        }
        (hierarchy.children[1] as PreferenceHierarchyNode).apply {
            assertThat(metadata).isSameInstanceAs(preference)
            assertThat(order).isNull()
        }
    }

    @Test
    fun addBeforeAndAfter_addsPreferenceToCorrectPosition() {
        val beforePreference = mock<PreferenceMetadata>()
        val afterPreference = mock<PreferenceMetadata>()
        val hierarchy = screen.preferenceHierarchy(context) {
            +preference
        }
        hierarchy.addBefore(TEST_SCREEN_KEY, beforePreference)
        hierarchy.addAfter(TEST_SCREEN_KEY, afterPreference)

        assertThat(hierarchy.children).hasSize(3)
        assertThat((hierarchy.children[0] as PreferenceHierarchyNode).metadata).isEqualTo(beforePreference)
        assertThat((hierarchy.children[1] as PreferenceHierarchyNode).metadata).isEqualTo(preference)
        assertThat((hierarchy.children[2] as PreferenceHierarchyNode).metadata).isEqualTo(afterPreference)
    }

    @Test
    fun addParameterizedScreenWithKeyParameters_addsScreen() {
        setCatalystUseKeyParameters(true)
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(1) { it.put(TEST_SCREEN_KEY, preferenceScreenMetadataParameterizedFactory) }
        val keyParameters = ValidatedKeyParameters(KeyParametersSchema { }, mock())
        whenever(preferenceScreenMetadataParameterizedFactory.createWithKeyParameters(context, keyParameters)).thenReturn(testScreenMetadata)
        val hierarchy = screen.preferenceHierarchy(context) {
            addParameterizedScreenWithKeyParameters(TEST_SCREEN_KEY, keyParameters)
        }

        assertThat(hierarchy.children).hasSize(1)
        val node = hierarchy.children[0] as PreferenceHierarchyNode
        assertThat(node.metadata).isEqualTo(testScreenMetadata)
    }

    @Test
    fun withParameters_addsScreen() {
        setCatalystUseKeyParameters(true)
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(1) { it.put(TEST_SCREEN_KEY, preferenceScreenMetadataParameterizedFactory) }
        val keyParameters = ValidatedKeyParameters(KeyParametersSchema { }, mock())
        whenever(preferenceScreenMetadataParameterizedFactory.createWithKeyParameters(context, keyParameters)).thenReturn(testScreenMetadata)
        val hierarchy = screen.preferenceHierarchy(context) {
            +(TEST_SCREEN_KEY withParameters keyParameters)
        }

        assertThat(hierarchy.children).hasSize(1)
        val node = hierarchy.children[0] as PreferenceHierarchyNode
        assertThat(node.metadata).isEqualTo(testScreenMetadata)
    }

    @Test
    fun unaryPlus_withKeyParametersFlagEnabled_addsScreen() {
        setCatalystUseKeyParameters(true)
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(1) { it.put(TEST_SCREEN_KEY, preferenceScreenMetadataParameterizedFactory) }
        whenever(preferenceScreenMetadataParameterizedFactory.acceptEmptyArguments()).thenReturn(true)
        whenever(preferenceScreenMetadataParameterizedFactory.create(context)).thenReturn(testScreenMetadata)
        val hierarchy = screen.preferenceHierarchy(context) {
            +TEST_SCREEN_KEY
        }

        assertThat(hierarchy.children).hasSize(1)
        val node = hierarchy.children[0] as PreferenceHierarchyNode
        assertThat(node.metadata).isEqualTo(testScreenMetadata)
    }

    @Test
    fun args_addsScreen() {
        setCatalystUseKeyParameters(false)
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(1) { it.put(TEST_SCREEN_KEY, preferenceScreenMetadataParameterizedFactory) }
        val args = mock<Bundle>()
        whenever(preferenceScreenMetadataParameterizedFactory.create(context, args)).thenReturn(testScreenMetadata).thenReturn(testScreenMetadata)
        val hierarchy = screen.preferenceHierarchy(context) {
            +(TEST_SCREEN_KEY args args)
        }

        assertThat(hierarchy.children).hasSize(1)
        val node = hierarchy.children[0] as PreferenceHierarchyNode
        assertThat(node.metadata).isEqualTo(testScreenMetadata)
    }

    @Test
    fun unaryPlus_withKeyParametersFlagDisabled_addsScreen() {
        setCatalystUseKeyParameters(false)
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(1) { it.put(TEST_SCREEN_KEY, preferenceScreenMetadataParameterizedFactory) }
        whenever(preferenceScreenMetadataParameterizedFactory.acceptEmptyArguments()).thenReturn(true)
        whenever(preferenceScreenMetadataParameterizedFactory.create(context)).thenReturn(testScreenMetadata).thenReturn(testScreenMetadata)
        val hierarchy = screen.preferenceHierarchy(context) {
            +TEST_SCREEN_KEY
        }

        assertThat(hierarchy.children).hasSize(1)
        val node = hierarchy.children[0] as PreferenceHierarchyNode
        assertThat(node.metadata).isEqualTo(testScreenMetadata)
    }
}
