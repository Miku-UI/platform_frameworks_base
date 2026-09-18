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

package com.android.settingslib.metadata.preferencesapi

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferencesapi.multiusers.ManagementScope.OWN_USER
import com.android.settingslib.metadata.preferencesapi.multiusers.PreferenceTarget.USER
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.preconditions.Custom
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.metadata.preferencesapi.types.AnyBoolean
import com.android.settingslib.metadata.test.R
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

@RunWith(AndroidJUnit4::class)
class ApiPreferenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun create_ApiPreference_doesNotCreateApiOperationContext() {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }

        ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
        }.build()

        assertThat(testCounter).isEqualTo(0)
    }

    @Test
    fun evaluatePreconditions_doesNotCreateApiOperationContext_whenAllPreconditionsAreNull() =
        runTest {
            var testCounter = 0
            val getScreenParameters: () -> ValidatedKeyParameters? = {
                testCounter++
                null
            }

            val preference = ApiPreferenceConfigBuilder(
                KEY,
                R.string.preference_purpose1,
                AnyBoolean,
                USER(canManage = OWN_USER),
                null,
                null,
                { null },
                getScreenParameters
            ).apply {
                get { execute { true } }
            }.build()


            val result = preference.getReadPermit(context, 0, 0)

            assertThat(result).isEqualTo(ReadWritePermit.ALLOW)
            assertThat(testCounter).isEqualTo(0)
        }

    @Test
    fun evaluatePreconditions_createsApiOperationContext() = runTest {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }

        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            PreconditionsConfig(
                R.string.preconditions_description1
            ) { Allowed },
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
        }.build()


        val result = preference.getReadPermit(context, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.ALLOW)
        assertThat(testCounter).isEqualTo(1)
    }

    @Test
    fun storage_doesNotCreateApiOperationContext() {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }

        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
        }.build()


        val result = preference.storage(context)

        assertThat(result.contains(KEY)).isTrue()
        assertThat(testCounter).isEqualTo(0)
    }

    @Test
    fun getValue_doesNotCreateApiOperationContext_whenGetIsCalledWithDifferentKey() {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }

        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
        }.build()


        val result = preference.storage(context).getValue("Key2737", Boolean::class.java)

        assertThat(result).isNull()
        assertThat(testCounter).isEqualTo(0)
    }

    @Test
    fun getValue_createsApiOperationContext_whenGetIsCalled() {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }

        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
        }.build()


        val result = preference.storage(context).getValue(KEY, Boolean::class.java)

        assertThat(result).isEqualTo(true)
        assertThat(testCounter).isEqualTo(1)
    }

    @Test
    fun setValue_doesNotCreateApiOperationContext_whenSetIsCalledWithDifferentKey() = runTest {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }
        var executed = false
        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
            set { execute { executed = true } }
        }.build()

        preference.storage(context).setValue("key2737", Boolean::class.java, true)

        assertThat(executed).isFalse()
        assertThat(testCounter).isEqualTo(0)
    }

    @Test
    fun setValue_withWrongType_throwsIllegalArgumentException_andDoesNotCreateApiOperationContext() {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }
        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
            set { execute {} }
        }.build()

        assertThrows(IllegalArgumentException::class.java) {
            preference.storage(context).setValue(KEY, Int::class.java, 2737)
        }
        assertThat(testCounter).isEqualTo(0)
    }

    @Test
    fun setValue_whenValuePreconditionsAreNotMet_throwsIllegalStateException_andCreatesApiOperationContext() {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }
        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
            set {
                valuePreconditions(R.string.preconditions_description1) {
                    Custom(R.string.preconditions_description1, stability = PreconditionStability.UNSTABLE)
                }
                execute {}
            }
        }.build()

        assertThrows(IllegalStateException::class.java) {
            preference.storage(context).setValue(KEY, Boolean::class.java, true)
        }
        assertThat(testCounter).isEqualTo(1)
    }

    @Test
    fun setValue_withCorrectKeyAndValue_executesSetValue_andCreatesApiOperationContext() {
        var testCounter = 0
        val getScreenParameters: () -> ValidatedKeyParameters? = {
            testCounter++
            null
        }
        var executedValue: Boolean? = null
        val preference = ApiPreferenceConfigBuilder<Boolean, Boolean>(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            get { execute { true } }
            set { execute { value -> executedValue = value } }
        }.build()

        preference.storage(context).setValue(KEY, Boolean::class.java, true)

        assertThat(executedValue).isTrue()
        assertThat(testCounter).isEqualTo(1)
    }

    @Test
    fun isFlagEnabled_returnsTrue_whenDebuggableAndSettingEnabled_andFlagCheckFails() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.SKIP_CATALYST_FLAG_CHECKS",
            1
        )
        val getScreenParameters: () -> ValidatedKeyParameters? = { null }

        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            getScreenParameters
        ).apply {
            flag { false }
            get { execute { true } }
        }.build()

        assertThat(preference.isFlagEnabled(context)).isTrue()
    }

    @Test
    fun shouldSkipFlagCheck_returnsTrue_whenDebuggableAndSettingEnabled() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.SKIP_CATALYST_FLAG_CHECKS",
            1
        )

        assertThat(shouldSkipFlagCheck(context)).isTrue()
    }

    @Test
    fun shouldSkipFlagCheck_returnsFalse_whenNotDebuggableAndSettingEnabled() {
        ShadowBuild.setType("user")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.SKIP_CATALYST_FLAG_CHECKS",
            1
        )

        assertThat(shouldSkipFlagCheck(context)).isFalse()
    }

    @Test
    fun shouldSkipFlagCheck_returnsFalse_whenDebuggableAndSettingDisabled() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.SKIP_CATALYST_FLAG_CHECKS",
            0
        )

        assertThat(shouldSkipFlagCheck(context)).isFalse()
    }

    @Test
    fun tags_whenTagsAreDefined_returnsTagsWithApiFirst() {
        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            { null }
        ).apply {
            tags("tag1", "tag2")
            get { execute { true } }
        }.build()

        val tags = preference.tags(context)

        assertThat(tags.toList()).containsExactly("api-first", "tag1", "tag2")
    }

    @Test
    fun tags_whenNoTagsAreDefined_returnsApiFirstOnly() {
        val preference = ApiPreferenceConfigBuilder(
            KEY,
            R.string.preference_purpose1,
            AnyBoolean,
            USER(canManage = OWN_USER),
            null,
            null,
            { null },
            { null }
        ).apply {
            get { execute { true } }
        }.build()

        val tags = preference.tags(context)

        assertThat(tags.toList()).containsExactly("api-first")
    }

    companion object {
        const val KEY = "ApiPreferenceKey"
    }
}
