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
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.catalyst.flags.Flags
import com.android.settingslib.metadata.preferencesapi.types.AnyString
import com.android.settingslib.metadata.test.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import java.util.function.Consumer

@RunWith(AndroidJUnit4::class)
class PreferenceScreenRegistryTest {
    @get:Rule
    val mSetFlagsRule = SetFlagsRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val screenKey = "screen_key"
    private val notAScreenKey = "not_a_screen_key"
    private val mockScreen = mock<PreferenceScreenMetadata>()

    private lateinit var originalProvider: CatalystFlagProvider

    @Before
    fun setUp() {
        originalProvider = CatalystFlagProviderFactory.getInstance()
        PreferenceScreenRegistry.preferenceScreenMetadataFactories = FixedArrayMap()
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

    private fun setMetadataFactory(screenKey: String, factory: PreferenceScreenMetadataFactory) {
        PreferenceScreenRegistry.preferenceScreenMetadataFactories = FixedArrayMap(1, Consumer { initializer ->
            initializer.put(screenKey, factory)
        })
    }

    @Test
    fun isParameterized_screenIsParameterized_returnsTrue() {
        setMetadataFactory(screenKey, object : TestParameterizedFactory {})

        val result = PreferenceScreenRegistry.isParameterized(context, screenKey)

        assertThat(result).isTrue()
    }

    @Test
    fun isParameterized_screenIsNotParameterized_returnsFalse() {
        setMetadataFactory(screenKey, object : PreferenceScreenMetadataFactory {
            override fun create(context: Context) = mockScreen
        })

        val result = PreferenceScreenRegistry.isParameterized(context, screenKey)

        assertThat(result).isFalse()
    }

    @Test
    fun isParameterized_screenKeyNotFound_returnsFalse() {
        val result = PreferenceScreenRegistry.isParameterized(context, notAScreenKey)

        assertThat(result).isFalse()
    }

    @Test
    fun getParameters_screenIsParameterized_returnsParameters() {
        runBlocking {
            val flow = flowOf(Bundle().apply { putString("key", "value") })
            setMetadataFactory(screenKey, object : TestParameterizedFactory {
                override fun parameters(context: Context) = flow
            })

            val parameters = PreferenceScreenRegistry.getParameters(context, screenKey)
            val parametersList = parameters.toList()

            assertThat(parametersList).hasSize(1)
            assertThat(parametersList[0].getString("key")).isEqualTo("value")
        }
    }

    @Test
    fun getParameters_screenIsNotParameterized_returnsEmptyFlow() {
        runBlocking {
            setMetadataFactory(screenKey, object : PreferenceScreenMetadataFactory {
                override fun create(context: Context) = mockScreen
            })

            val parameters = PreferenceScreenRegistry.getParameters(context, screenKey)
            val parametersList = parameters.toList()

            assertThat(parametersList).isEmpty()
        }
    }

    @Test
    fun getParameters_screenKeyNotFound_returnsEmptyFlow() {
        runBlocking {
            val parameters = PreferenceScreenRegistry.getParameters(context, screenKey)
            val parametersList = parameters.toList()

            assertThat(parametersList).isEmpty()
        }
    }

    @Test
    fun create_withKeyParameters_flagEnabled() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema { parameter("id", R.string.required_param_purpose, type = AnyString) }
        val keyParams = schema.prepare("id" to "123")
        setMetadataFactory(screenKey, object : TestParameterizedFactory {
            override fun createWithKeyParameters(context: Context, keyParameters: ValidatedKeyParameters): PreferenceScreenMetadata {
                assertThat(keyParameters).isEqualTo(keyParams)
                return mockScreen
            }
            override val parametersSchema = schema
        })

        val result = PreferenceScreenRegistry.createWithKeyParameters(context, screenKey, keyParams)
        assertThat(result).isSameInstanceAs(mockScreen)
    }

    @Test
    fun create_withBundle_flagDisabled() {
        setCatalystUseKeyParameters(false)
        val args = Bundle().apply { putString("id", "123") }
        setMetadataFactory(screenKey, object : TestParameterizedFactory {
            override fun create(context: Context, args: Bundle): PreferenceScreenMetadata {
                assertThat(args.getString("id")).isEqualTo("123")
                return mockScreen
            }
        })

        val result = PreferenceScreenRegistry.create(context, screenKey, args)
        assertThat(result).isSameInstanceAs(mockScreen)
    }

    @Test
    fun create_fromCoordinate_flagEnabled() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema { parameter("id", R.string.required_param_purpose, type = AnyString) }
        val keyParams = schema.prepare("id" to "123")
        val coordinate = PreferenceScreenCoordinate(screenKey, keyParams)
        setMetadataFactory(screenKey, object : TestParameterizedFactory {
            override fun createWithKeyParameters(context: Context, keyParameters: ValidatedKeyParameters): PreferenceScreenMetadata {
                assertThat(keyParameters).isEqualTo(keyParams)
                return mockScreen
            }
            override val parametersSchema = schema
        })

        val result = PreferenceScreenRegistry.create(context, coordinate)
        assertThat(result).isSameInstanceAs(mockScreen)
    }

    @Test
    fun create_fromCoordinate_flagDisabled() {
        setCatalystUseKeyParameters(false)
        val args = Bundle().apply { putString("id", "123") }
        val coordinate = PreferenceScreenCoordinate(screenKey, args)
        setMetadataFactory(screenKey, object : TestParameterizedFactory {
            override fun create(context: Context, args: Bundle): PreferenceScreenMetadata {
                assertThat(args.getString("id")).isEqualTo("123")
                return mockScreen
            }
        })

        val result = PreferenceScreenRegistry.create(context, coordinate)
        assertThat(result).isSameInstanceAs(mockScreen)
    }

    @Test
    fun getKeyParameters_returnsFlow() {
        runBlocking {
            val schema = KeyParametersSchema { parameter("id", R.string.required_param_purpose, type = AnyString) }
            val keyParams = schema.prepare("id" to "123")
            val flow = flowOf(keyParams)
            setMetadataFactory(screenKey, object : TestParameterizedFactory {
                override fun keyParameters(context: Context) = flow
            })

            val resultFlow = PreferenceScreenRegistry.getKeyParameters(context, screenKey)
            val resultList = resultFlow.toList()
            assertThat(resultList).containsExactly(keyParams)
        }
    }

    @Test
    fun getScreenParametersSchema_returnsSchema() {
        val schema = KeyParametersSchema { parameter("id", R.string.required_param_purpose, type = AnyString) }
        setMetadataFactory(screenKey, object : TestParameterizedFactory {
            override val parametersSchema = schema
        })

        val resultSchema = PreferenceScreenRegistry.getScreenParametersSchema(screenKey)
        assertThat(resultSchema).isSameInstanceAs(schema)
    }

    @Test
    fun createScreenInstanceForMetadata_onKeyAndInexistentFactory_returnsNull() {
        val result = PreferenceScreenRegistry.createScreenInstanceForMetadata(context, notAScreenKey)

        assertThat(result).isNull()
    }

    @Test
    fun createScreenInstanceForMetadata_onNullKey_returnsNull() {
        val result = PreferenceScreenRegistry.createScreenInstanceForMetadata(context, null)

        assertThat(result).isNull()
    }

    @Test
    fun createScreenInstanceForMetadata_onValidKey_returnsMetadata() {
        setMetadataFactory(screenKey) { mockScreen }

        val result = PreferenceScreenRegistry.createScreenInstanceForMetadata(context, screenKey)

        assertThat(result).isSameInstanceAs(mockScreen)
    }

    @Test
    fun createScreenInstanceForMetadata_onNotParameterizedFactory_returnsMetadata() {
        val factory = PreferenceScreenMetadataFactory { mockScreen }

        val result = PreferenceScreenRegistry.createScreenInstanceForMetadata(context, factory)

        assertThat(result).isSameInstanceAs(mockScreen)
    }

    @Test
    fun createScreenInstanceForMetadata_onParameterizedFactory_flagEnabled_returnsMetadata() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema { parameter("id", R.string.required_param_purpose, type = AnyString) }
        val keyParams = schema.prepare("id" to "123")
        val factory = object : TestParameterizedFactory {
            override fun keyParameters(context: Context) = flowOf(keyParams)
            override fun createWithKeyParameters(context: Context, keyParameters: ValidatedKeyParameters): PreferenceScreenMetadata {
                assertThat(keyParameters).isEqualTo(keyParams)
                return mockScreen
            }
        }

        val result = PreferenceScreenRegistry.createScreenInstanceForMetadata(context, factory)

        assertThat(result).isSameInstanceAs(mockScreen)
    }

    @Test
    fun createScreenInstanceForMetadata_onParameterizedFactory_flagEnabled_noParameters_returnsNull() {
        setCatalystUseKeyParameters(true)
        val factory = object : TestParameterizedFactory {
            override fun keyParameters(context: Context): Flow<ValidatedKeyParameters> = flowOf()
        }

        val result = PreferenceScreenRegistry.createScreenInstanceForMetadata(context, factory)

        assertThat(result).isNull()
    }

    @Test
    fun createScreenInstanceForMetadata_onParameterizedFactory_flagDisabled_returnsMetadata() {
        setCatalystUseKeyParameters(false)
        val args = Bundle().apply { putString("id", "123") }
        val factory = object : TestParameterizedFactory {
            override fun parameters(context: Context) = flowOf(args)
            override fun create(context: Context, args: Bundle): PreferenceScreenMetadata {
                assertThat(args.getString("id")).isEqualTo("123")
                return mockScreen
            }
        }

        val result = PreferenceScreenRegistry.createScreenInstanceForMetadata(context, factory)

        assertThat(result).isSameInstanceAs(mockScreen)
    }

    @Test
    fun createScreenInstanceForMetadata_onParameterizedFactory_flagDisabled_noParameters_returnsNull() {
        setCatalystUseKeyParameters(false)
        val factory = object : TestParameterizedFactory {
            override fun parameters(context: Context): Flow<Bundle> = flowOf()
        }

        val result = PreferenceScreenRegistry.createScreenInstanceForMetadata(context, factory)

        assertThat(result).isNull()
    }

    interface TestParameterizedFactory : PreferenceScreenMetadataParameterizedFactory {
        override fun create(context: Context, args: Bundle): PreferenceScreenMetadata = error("Should not be called")
        override fun parameters(context: Context): Flow<Bundle> = flowOf(Bundle.EMPTY)
        override fun createWithKeyParameters(context: Context, keyParameters: ValidatedKeyParameters): PreferenceScreenMetadata = error("Should not be called")
        override fun keyParameters(context: Context): Flow<ValidatedKeyParameters> = flowOf(parametersSchema.prepareEmpty())
        override val parametersSchema: KeyParametersSchema get() = KeyParametersSchema {}
    }
}
