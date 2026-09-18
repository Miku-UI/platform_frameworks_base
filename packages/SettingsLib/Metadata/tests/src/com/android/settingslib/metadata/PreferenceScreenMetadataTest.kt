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
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.preferencesapi.types.AnyString
import com.android.settingslib.metadata.test.R
import com.android.settingslib.metadata.PreferenceScreenMetadata.Companion.EXTRA_FRAGMENT_ARG_KEY
import com.android.settingslib.metadata.PreferenceScreenMetadata.Companion.EXTRA_SCREEN_ARGS
import com.android.settingslib.metadata.PreferenceScreenMetadata.Companion.EXTRA_SCREEN_KEY
import com.android.settingslib.metadata.PreferenceScreenMetadata.Companion.LAUNCH_SETTINGS_PAGES_ACTION
import com.android.settingslib.preference.PreferenceFragment
import com.android.settingslib.preference.PreferenceScreenCreator
import com.android.settingslib.preference.launchFragmentScenario
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceScreenMetadataTest {
    @get:Rule
    val mSetFlagsRule = SetFlagsRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

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
    fun isContainer_isEntryPoint() {
        val innerScreen = Screen("Screen2")
        val screen =
            object : Screen("Screen1") {
                override fun getPreferenceHierarchy(
                    context: Context,
                    coroutineScope: CoroutineScope,
                ) = preferenceHierarchy(context) { +innerScreen.key }
            }
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(2) {
                it.put(screen.key) { screen }
                it.put(innerScreen.key) { innerScreen }
            }
        screen
            .launchFragmentScenario()
            .onFragment {
                val context = screen.preferenceLifecycleContext
                assertThat(screen.isContainer(context)).isTrue()
                assertThat(screen.isEntryPoint(context)).isFalse()
                assertThat(innerScreen.isContainer(context)).isFalse()
                assertThat(innerScreen.isEntryPoint(context)).isTrue()
            }
            .close()
    }

    @Test
    fun isContainer_isEntryPoint_parameterizedScreen_flagDisabled() {
        setCatalystUseKeyParameters(false)
        val innerScreen =
            object : Screen("Screen2", 0.toArgument()) {
                override val bindingKey
                    get() = "screen2:0"
            }
        val screen =
            object : Screen("Screen1", 0.toArgument()) {
                override val bindingKey
                    get() = "screen1:0"

                override fun getPreferenceHierarchy(
                    context: Context,
                    coroutineScope: CoroutineScope,
                ) = preferenceHierarchy(context) { +(innerScreen.key args 0.toArgument()) }
            }
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(2) {
                it.put(
                    screen.key,
                    object : TestParameterizedFactory {
                        override fun create(context: Context, args: Bundle) = screen
                    },
                )
                it.put(
                    innerScreen.key,
                    object : TestParameterizedFactory {
                        override fun create(context: Context, args: Bundle) = innerScreen
                    },
                )
            }
        screen
            .launchFragmentScenario()
            .onFragment {
                val context = screen.preferenceLifecycleContext
                assertThat(screen.isContainer(context)).isTrue()
                assertThat(screen.isEntryPoint(context)).isFalse()
                assertThat(innerScreen.isContainer(context)).isFalse()
                assertThat(innerScreen.isEntryPoint(context)).isTrue()
            }
            .close()
    }

    @Test
    fun isContainer_isEntryPoint_parameterizedScreen_flagEnabled() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema { parameter("id", R.string.required_param_purpose, type = AnyString) }
        val keyParams = schema.prepare("id" to "0")
        val innerScreen =
            object : ScreenWithKeyParams("Screen2", keyParams) {
                override val bindingKey
                    get() = "screen2:0"
            }
        val screen =
            object : ScreenWithKeyParams("Screen1", keyParams) {
                override val bindingKey
                    get() = "screen1:0"

                override fun getPreferenceHierarchy(
                    context: Context,
                    coroutineScope: CoroutineScope,
                ) = preferenceHierarchy(context) { +(innerScreen.key withParameters keyParams) }
            }
        PreferenceScreenRegistry.preferenceScreenMetadataFactories =
            FixedArrayMap(2) {
                it.put(
                    screen.key,
                    object : TestParameterizedFactory {
                        override fun createWithKeyParameters(context: Context, keyParameters: ValidatedKeyParameters) = screen
                        override val parametersSchema = schema
                    },
                )
                it.put(
                    innerScreen.key,
                    object : TestParameterizedFactory {
                        override fun createWithKeyParameters(context: Context, keyParameters: ValidatedKeyParameters) = innerScreen
                        override val parametersSchema = schema
                    },
                )
            }
        screen
            .launchFragmentScenario()
            .onFragment {
                val context = screen.preferenceLifecycleContext
                assertThat(screen.isContainer(context)).isTrue()
                assertThat(screen.isEntryPoint(context)).isFalse()
                assertThat(innerScreen.isContainer(context)).isFalse()
                assertThat(innerScreen.isEntryPoint(context)).isTrue()
            }
            .close()
    }

    @Test
    fun getLaunchIntent_basic_returnsCorrectActionAndScreenKey() {
        val preferenceScreen = object : PreferenceScreenMetadata {
            override val key = SCREEN_KEY
            override val purpose = 0
            override fun fragmentClass(): Class<out Fragment> = PreferenceFragment::class.java
            override fun getPreferenceHierarchy(
                context: Context,
                coroutineScope: CoroutineScope
            ): PreferenceHierarchy = preferenceHierarchy(context) {}
        }

        val intent = preferenceScreen.getLaunchIntent(context, null)

        assertThat(intent).isNotNull()
        assertThat(intent!!.action).isEqualTo(LAUNCH_SETTINGS_PAGES_ACTION)
        assertThat(intent.getStringExtra(EXTRA_SCREEN_KEY)).isEqualTo(SCREEN_KEY)
        // Ensure no other extras are accidentally set
        assertThat(intent.hasExtra(EXTRA_FRAGMENT_ARG_KEY)).isFalse()
        assertThat(intent.hasExtra(EXTRA_SCREEN_ARGS)).isFalse()
    }

    @Test
    fun getLaunchIntent_withMetadata_setsHighlightKey() {
        val preferenceScreen = object : PreferenceScreenMetadata {
            override val key = SCREEN_KEY
            override val purpose = 0
            override fun fragmentClass(): Class<out Fragment> = PreferenceFragment::class.java
            override fun getPreferenceHierarchy(
                context: Context,
                coroutineScope: CoroutineScope
            ): PreferenceHierarchy = preferenceHierarchy(context) {}
        }

        val testHighlightKey = "test_preference_key"

        // Create a simple dummy metadata object
        val metadata = object : PreferenceMetadata {
            override val key = testHighlightKey
            override val title = 0
            override val summary = 0
            override val icon = 0
            override val purpose = 0
        }

        val intent = preferenceScreen.getLaunchIntent(context, metadata)

        assertThat(intent).isNotNull()
        assertThat(intent!!.getStringExtra(EXTRA_SCREEN_KEY)).isEqualTo(SCREEN_KEY)
        assertThat(intent.getStringExtra(EXTRA_FRAGMENT_ARG_KEY)).isEqualTo(testHighlightKey)
    }

    @Test
    fun getLaunchIntent_withArguments_setsScreenArgsBundle() {
        val expectedArgValue = "some_arg_value"

        // Create screen that overrides arguments
        val preferenceScreen = object : PreferenceScreenMetadata {
            override val key = SCREEN_KEY
            override val purpose = 0
            override fun fragmentClass(): Class<out Fragment> = PreferenceFragment::class.java
            override fun getPreferenceHierarchy(
                context: Context,
                coroutineScope: CoroutineScope
            ): PreferenceHierarchy = preferenceHierarchy(context) {}
            // Mocking the behavior of having arguments
            override val arguments: Bundle = Bundle().apply {
                putString("test_arg", expectedArgValue)
            }
        }

        val intent = preferenceScreen.getLaunchIntent(context, null)

        assertThat(intent).isNotNull()
        assertThat(intent!!.hasExtra(EXTRA_SCREEN_ARGS)).isTrue()

        val args = intent.getBundleExtra(EXTRA_SCREEN_ARGS)
        assertThat(args).isNotNull()
        assertThat(args!!.getString("test_arg")).isEqualTo(expectedArgValue)
    }

    open class Screen(override val key: String, override val arguments: Bundle? = null) :
        PreferenceScreenCreator, PreferenceLifecycleProvider {

        override val purpose: Int = 0

        lateinit var preferenceLifecycleContext: PreferenceLifecycleContext

        override fun fragmentClass() = PreferenceFragment::class.java

        override fun onCreate(context: PreferenceLifecycleContext) {
            preferenceLifecycleContext = context
        }

        override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
            preferenceHierarchy(context) {}
    }

    open class ScreenWithKeyParams(override val key: String, override val keyParameters: ValidatedKeyParameters? = null) :
        PreferenceScreenCreator, PreferenceLifecycleProvider {

        override val purpose: Int = 0

        lateinit var preferenceLifecycleContext: PreferenceLifecycleContext

        override fun fragmentClass() = PreferenceFragment::class.java

        override fun onCreate(context: PreferenceLifecycleContext) {
            preferenceLifecycleContext = context
        }

        override fun getPreferenceHierarchy(context: Context, coroutineScope: CoroutineScope) =
            preferenceHierarchy(context) {}
    }

    interface TestParameterizedFactory : PreferenceScreenMetadataParameterizedFactory {
        override fun create(context: Context, args: Bundle): PreferenceScreenMetadata = TODO("Not yet implemented")
        override fun parameters(context: Context): Flow<Bundle> = flowOf(0.toArgument())
        override fun createWithKeyParameters(context: Context, keyParameters: ValidatedKeyParameters): PreferenceScreenMetadata = TODO("Not yet implemented")
        override fun keyParameters(context: Context): Flow<ValidatedKeyParameters> = parameters(context).map { parametersSchema.prepare(it) }
        override val parametersSchema: KeyParametersSchema get() = KeyParametersSchema {}
    }

    companion object {
        const val SCREEN_KEY = "Screen_key"
    }
}

fun Int.toArgument() = Bundle().also { it.putInt(null, this) }