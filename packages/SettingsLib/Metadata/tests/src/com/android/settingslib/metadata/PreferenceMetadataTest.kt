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
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.preferencesapi.ApiPreference
import com.android.settingslib.metadata.preferencesapi.GetConfig
import com.android.settingslib.metadata.preferencesapi.PreconditionsConfig
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen
import com.android.settingslib.metadata.preferencesapi.SetConfig
import com.android.settingslib.metadata.preferencesapi.ValuePreconditionsConfig
import com.android.settingslib.metadata.preferencesapi.WarningConfig
import com.android.settingslib.metadata.preferencesapi.category.Category
import com.android.settingslib.metadata.preferencesapi.multiusers.PreferenceTarget
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.types.AnyString
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceMetadataTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun accessPreconditionsAsString_apiPreference_withScreenAndPreferencePreconditions() {
        val screenPreconditions = PreconditionsConfig("Screen precondition") { Allowed }
        val preferencePreconditions = PreconditionsConfig("Preference precondition") { Allowed }
        val preference = TestApiPreference(
            screenPreconditions = screenPreconditions,
            preconditions = preferencePreconditions
        )

        val result = preference.accessPreconditionsAsString(context)

        assertThat(result).isEqualTo("Preconditions to accessing: Screen precondition, Preference precondition.")
    }

    @Test
    fun accessPreconditionsAsString_apiPreference_withOnlyScreenPreconditions() {
        val screenPreconditions = PreconditionsConfig("Screen precondition") { Allowed }
        val preference = TestApiPreference(
            screenPreconditions = screenPreconditions
        )

        val result = preference.accessPreconditionsAsString(context)

        assertThat(result).isEqualTo("Preconditions to accessing: Screen precondition.")
    }

    @Test
    fun accessPreconditionsAsString_apiPreference_withOnlyPreferencePreconditions() {
        val preferencePreconditions = PreconditionsConfig("Preference precondition") { Allowed }
        val preference = TestApiPreference(
            preconditions = preferencePreconditions
        )

        val result = preference.accessPreconditionsAsString(context)

        assertThat(result).isEqualTo("Preconditions to accessing: Preference precondition.")
    }

    @Test
    fun accessPreconditionsAsString_apiPreference_noPreconditions() {
        val preference = TestApiPreference()

        val result = preference.accessPreconditionsAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun accessPreconditionsAsString_preferencesApiScreen_withPreconditions() {
        val screenPreconditions = PreconditionsConfig("Screen precondition") { Allowed }
        val screen = TestPreferencesApiScreen(
            screenPreconditions = screenPreconditions
        )

        val result = screen.accessPreconditionsAsString(context)

        assertThat(result).isEqualTo("Preconditions to accessing: Screen precondition.")
    }

    @Test
    fun accessPreconditionsAsString_preferencesApiScreen_noPreconditions() {
        val screen = TestPreferencesApiScreen()

        val result = screen.accessPreconditionsAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun accessPreconditionsAsString_nonApiPreference() {
        val preference = object : PreferenceMetadata {
            override val key = "key"
            override val purpose = 0
        }

        val result = preference.accessPreconditionsAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun getPreconditionsAsString_apiPreference_withGetPreconditions() {
        val getPreconditions = PreconditionsConfig("Get precondition") { Allowed }
        val preference = TestApiPreference(
            get = GetConfig(preconditions = getPreconditions, execute = { "value" })
        )

        val result = preference.getPreconditionsAsString(context)

        assertThat(result).isEqualTo("Preconditions to reading: Get precondition.")
    }

    @Test
    fun getPreconditionsAsString_apiPreference_noGetPreconditions() {
        val preference = TestApiPreference()

        val result = preference.getPreconditionsAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun getPreconditionsAsString_nonApiPreference() {
        val preference = object : PreferenceMetadata {
            override val key = "key"
            override val purpose = 0
        }

        val result = preference.getPreconditionsAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun setPreconditionsAsString_apiPreference_withSetPreconditions() {
        val setPreconditions = PreconditionsConfig("Set precondition") { Allowed }
        val preference = TestApiPreference(
            set = SetConfig(preconditions = setPreconditions, execute = { })
        )

        val result = preference.setPreconditionsAsString(context)

        assertThat(result).isEqualTo("Preconditions to writing: Set precondition.")
    }

    @Test
    fun setPreconditionsAsString_apiPreference_noSetPreconditions() {
        val preference = TestApiPreference()

        val result = preference.setPreconditionsAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun setPreconditionsAsString_nonApiPreference() {
        val preference = object : PreferenceMetadata {
            override val key = "key"
            override val purpose = 0
        }

        val result = preference.setPreconditionsAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun setWarningAsString_apiPreference_withSetWarningWithoutPreconditions() {
        val setWarning = WarningConfig<String>(
            warning = "Set warning"
        )

        val preference = TestApiPreference(
            set = SetConfig(warning = setWarning, execute = { })
        )

        val result = preference.setWarningAsString(context)

        assertThat(result).isEqualTo("[Must show to user]: Set warning.")
    }

    @Test
    fun setWarningAsString_apiPreference_withSetWarningWithPreconditions() {
        val setWarning = WarningConfig<String>(
            warning = "Set warning",
            preconditions = PreconditionsConfig("Set preconditions") { Allowed }
        )

        val preference = TestApiPreference(
            set = SetConfig(warning = setWarning, execute = { })
        )

        val result = preference.setWarningAsString(context)

        assertThat(result).isEqualTo("[Must show to user]: Set warning (if preconditions are met: Set preconditions).")
    }

    @Test
    fun setWarningAsString_apiPreference_withSetWarningWithValuePreconditions() {
        val setWarning = WarningConfig<String>(
            warning = "Set warning",
            valuePreconditions = ValuePreconditionsConfig("Set value preconditions") { _ -> Allowed }
        )

        val preference = TestApiPreference(
            set = SetConfig(warning = setWarning, execute = { })
        )

        val result = preference.setWarningAsString(context)

        assertThat(result).isEqualTo("[Must show to user]: Set warning (if preconditions are met: Set value preconditions).")
    }

    @Test
    fun setWarningAsString_apiPreference_noSetWarning() {
        val preference = TestApiPreference()

        val result = preference.setWarningAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun setWarningAsString_nonApiPreference_notImplementingPreferenceSetWarningProvider() {
        val preference = object : PreferenceMetadata {
            override val key = "key"
            override val purpose = 0
        }

        val result = preference.setWarningAsString(context)

        assertThat(result).isNull()
    }

    @Test
    fun setWarningAsString_nonApiPreference_implementingPreferenceSetWarningProvider() {
        val preference = object : PreferenceMetadata, PreferenceSetWarningProvider {
            override val key = "key"
            override val purpose = 0
            override val setWarning = WarningInfo(
                preconditionsDescription = "Set preconditions",
                warningMessage = "Set warning"
            )
        }

        val result = preference.setWarningAsString(context)

        assertThat(result).isEqualTo("[Must show to user]: Set warning (if preconditions are met: Set preconditions).")
    }

    open class TestApiPreference(
        override val key: String = "key",
        override val purpose: Int = 0,
        override val screenPreconditions: PreconditionsConfig? = null,
        override val preconditions: PreconditionsConfig? = null,
        override val get: GetConfig<String> = GetConfig(execute = { "val" }),
        override val set: SetConfig<String>? = null,
        override val getParameters: () -> ValidatedKeyParameters? = { null },
        override val getParametersSchema: () -> KeyParametersSchema? = { null },
    ) : ApiPreference<String, String>(null, PreferenceTarget.DEVICE) {
        override val type = AnyString
        override val valueType = String::class.java
        override val permissions: Permissions? = null
        override val screenPermissions: Permissions? = null
        override val getScreenParameters: () -> ValidatedKeyParameters? = { null }
        override val supportsWrite = set != null
    }

    open class TestPreferencesApiScreen(
        key: String = "screen_key",
        purpose: Int = 0,
        screenPreconditions: PreconditionsConfig? = null
    ) : PreferencesApiScreen(
        key, Category.SYSTEM, Fragment::class, purpose
    ) {
        init {
            this.screenPreconditions = screenPreconditions
        }
    }
}
