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

import android.Manifest
import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen.Companion.PARTIALLY_MIGRATED_PREFIX
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreenTest.Companion.ApiPreconditionsMapper.ALLOWED
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreenTest.Companion.ApiPreconditionsMapper.CUSTOM
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreenTest.Companion.ApiPreconditionsMapper.ENTERPRISE_RESTRICTION
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreenTest.Companion.ApiPreconditionsMapper.HARDWARE_UNSUPPORTED
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreenTest.Companion.ApiPreconditionsMapper.INVALID_PREFERENCE
import com.android.settingslib.metadata.preferencesapi.Utils.EXCEPTION_MESSAGE_NO_PARAMETER_DEFINED
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageAlreadyDefined
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageMultipleDefines
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageMultipleParametersDefined
import com.android.settingslib.metadata.preferencesapi.Utils.getExceptionMessageWrongOrder
import com.android.settingslib.metadata.preferencesapi.category.Category
import com.android.settingslib.metadata.preferencesapi.multiusers.PreferenceTarget.PROFILE_GROUP
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.preconditions.Custom
import com.android.settingslib.metadata.preferencesapi.preconditions.EnterpriseRestriction
import com.android.settingslib.metadata.preferencesapi.preconditions.HardwareUnsupported
import com.android.settingslib.metadata.preferencesapi.preconditions.InvalidPreference
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.metadata.preferencesapi.types.AnyBoolean
import com.android.settingslib.metadata.preferencesapi.types.AnyInt
import com.android.settingslib.metadata.preferencesapi.types.GeneratedParameterType
import com.android.settingslib.metadata.preferencesapi.types.GeneratedValue
import com.android.settingslib.metadata.test.R
import com.android.settingslib.preference.PreferenceFragment
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowBuild
import com.android.settingslib.metadata.preferencesapi.safe

@RunWith(AndroidJUnit4::class)
class PreferencesApiScreenTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun createPreferencesApiScreenWithoutGetter_throwsError() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                val preferenceKey1 = "ApiPreference"

                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey1,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {}
                    }
                }
            }

        assertThat(exception.message).isEqualTo("'get' block is required")
    }

    @Test
    fun createPreferencesApiScreen_orderIsCorrect() {
        val preferenceKey1 = "ApiPreference1"
        val preferenceKey2 = "ApiPreference2"

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preference(
                        key = preferenceKey1,
                        purpose = R.string.preference_purpose1,
                        type = AnyBoolean,
                    ) {
                        get { execute { false } }
                    }

                    preference(
                        key = preferenceKey2,
                        purpose = R.string.preference_purpose2,
                        type = AnyInt,
                    ) {
                        get { execute { 0 } }
                    }
                }
            }

        // Check we only have 2 preferences in the list
        assertThat(preferenceScreen.preferences.size).isEqualTo(2)

        // Check preference order is correct
        val firstPreference = preferenceScreen.preferences[0] as ApiPreference<Boolean, Boolean>
        assertThat(firstPreference.key).isEqualTo(preferenceKey1)

        val secondPreference = preferenceScreen.preferences[1] as ApiPreference<Int, Int>
        assertThat(secondPreference.key).isEqualTo(preferenceKey2)
    }

    @Test
    fun createPreferencesApiScreenWithSpaRoutePrefix_succeeds() {
        val spaRoutePrefix = "spa_route_prefix"
        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    spaRoutePrefix = spaRoutePrefix,
                    purpose = R.string.preference_screen_purpose,
                ) {}

        assertThat(preferenceScreen.spaRoutePrefix).isEqualTo(spaRoutePrefix)
        assertThat(preferenceScreen.fragment).isNull()
    }

    @Test
    fun getSpaRoute_noParams_returnsStaticPrefix() {
        val spaRoutePrefix = "static/route"
        val screen = object : PreferencesApiScreen(
            key = SCREEN_KEY,
            topLevelSettingsCategory = Category.SYSTEM,
            spaRoutePrefix = spaRoutePrefix,
            purpose = R.string.preference_screen_purpose,
        ) {}

        assertThat(screen.getSpaRoute()).isEqualTo(spaRoutePrefix)
    }

    @Test
    fun getSpaRoute_withParams_noPrepareSpaRoute_returnsStaticPrefix() {
        val spaRoutePrefix = "static/route"
        val screen = object : PreferencesApiScreen(
            key = SCREEN_KEY,
            topLevelSettingsCategory = Category.SYSTEM,
            spaRoutePrefix = spaRoutePrefix,
            purpose = R.string.preference_screen_purpose,
        ) {
            init {
                parameters {
                    parameter(
                        "package",
                        R.string.parameter_purpose1,
                        true,
                        GeneratedParameterType(R.string.parameter_type_description) {
                            listOf(GeneratedValue("value".safe(), "type_description".safe()))
                        },
                    )
                }
            }
        }
        val keyParameters = screen.parametersSchema!!.prepare("package" to "com.example.app")
        screen.initializeParameters(keyParameters)

        assertThat(screen.getSpaRoute()).isEqualTo(spaRoutePrefix)
    }

    @Test
    fun getSpaRoute_withParameters_returnsDynamicRoute() {
        val spaRoutePrefix = "spa/prefix"
        val packageName = "com.example.app"
        val screen = object : PreferencesApiScreen(
            key = SCREEN_KEY,
            topLevelSettingsCategory = Category.SYSTEM,
            purpose = R.string.preference_screen_purpose,
        ) {
            init {
                parameters {
                    parameter(
                        "package",
                        R.string.parameter_purpose1,
                        true,
                        GeneratedParameterType(R.string.parameter_type_description) {
                            listOf(GeneratedValue("value".safe(), "type_description".safe()))
                        },
                    )
                    prepareSpaRoute { params ->
                        "$spaRoutePrefix/${params["package"]}"
                    }
                }
            }
        }

        val keyParameters = screen.parametersSchema!!.prepare("package" to packageName)
        screen.initializeParameters(keyParameters)

        val route = screen.getSpaRoute()

        assertThat(route).isEqualTo("$spaRoutePrefix/$packageName")
    }

    @Test
    fun createPreferencesApiScreenWithGetter_preferencePreconditionCheckWithCurrentUserId_isDisallowed() {
        val preferenceKey = "ApiPreference"

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preference(
                        key = preferenceKey,
                        purpose = R.string.preference_purpose1,
                        type = AnyInt,
                        appliesTo = PROFILE_GROUP
                    ) {
                        preconditions("Preference is available only if the user has profiles.") {
                            val userManager = context.getSystemService(UserManager::class.java)
                            val numProfiles = userManager?.getProfiles(userId)?.size ?: 0

                            if (numProfiles > 1) {
                                Allowed
                            } else {
                                EnterpriseRestriction("No profiles on user $userId")
                            }
                        }
                        get { execute { userHandle.hashCode() } }
                    }
                }
            }

        // Check we only have 1 preference in the list
        assertThat(preferenceScreen.preferences.size).isEqualTo(1)

        val apiOperationContext =
            ApiOperationContext(context = context, parameters = ValidatedKeyParameters.EMPTY)

        val preference = preferenceScreen.preferences[0] as ApiPreference<Int, Int>
        assertThat(preference.key).isEqualTo("ApiPreference")
        runBlocking {
            assertThat(preference.preconditions?.check?.invoke(apiOperationContext) is EnterpriseRestriction).isTrue()
        }
    }

    @Test
    fun createPreferencesApiScreenWithGetters_succeeds() {
        val preferenceValue1 = false
        val preferenceKey1 = "ApiPreference1"
        val preferenceValue2 = 0
        val preferenceKey2 = "ApiPreference2"

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preference(
                        key = preferenceKey1,
                        purpose = R.string.preference_purpose1,
                        type = AnyBoolean,
                    ) {
                        get { execute { preferenceValue1 } }
                    }

                    preference(
                        key = preferenceKey2,
                        purpose = R.string.preference_purpose2,
                        type = AnyInt,
                    ) {
                        get { execute { preferenceValue2 } }
                    }
                }
            }

        // Check we only have 2 preferences in the list
        assertThat(preferenceScreen.preferences.size).isEqualTo(2)

        // Check that getters return the correct value
        val firstPreference = preferenceScreen.preferences[0] as ApiPreference<Boolean, Boolean>
        assertThat(firstPreference.storage(context).getValue(preferenceKey1, Boolean::class.java))
            .isEqualTo(preferenceValue1)

        val secondPreference = preferenceScreen.preferences[1] as ApiPreference<Int, Int>
        assertThat(secondPreference.key).isEqualTo("ApiPreference2")
        assertThat(secondPreference.storage(context).getValue(preferenceKey2, Int::class.java))
            .isEqualTo(preferenceValue2)
    }

    @Test
    fun createPreferencesApiScreenWithGettersAndSetters_succeeds() {
        val preferenceValue1 = false
        val preferenceKey1 = "ApiPreference1"
        var preferenceValue2 = 0
        val preferenceKey2 = "ApiPreference2"
        val newPreferenceValue2 = 22

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preference(
                        key = preferenceKey1,
                        purpose = R.string.preference_purpose1,
                        type = AnyBoolean,
                    ) {
                        get { execute { preferenceValue1 } }
                    }

                    preference(
                        key = preferenceKey2,
                        purpose = R.string.preference_purpose2,
                        type = AnyInt,
                    ) {
                        get { execute { preferenceValue2 } }

                        set { execute { value -> preferenceValue2 = value } }
                    }
                }
            }

        // Check we only have 2 preferences in the list
        assertThat(preferenceScreen.preferences.size).isEqualTo(2)

        // First preference doesn't have a setter, so the getter should return the same value
        val firstPreference = preferenceScreen.preferences[0] as ApiPreference<Boolean, Boolean>
        assertThat(firstPreference.key).isEqualTo(preferenceKey1)
        firstPreference.storage(context).setValue(preferenceKey1, Boolean::class.java, true)
        assertThat(firstPreference.storage(context).getValue(preferenceKey1, Boolean::class.java))
            .isEqualTo(preferenceValue1)

        // Value of the second preference should be changed by setter
        val secondPreference = preferenceScreen.preferences[1] as ApiPreference<Int, Int>
        assertThat(secondPreference.key).isEqualTo(preferenceKey2)
        secondPreference
            .storage(context)
            .setValue(preferenceKey2, Int::class.java, newPreferenceValue2)
        assertThat(secondPreference.storage(context).getValue(preferenceKey2, Int::class.java))
            .isEqualTo(newPreferenceValue2)
    }

    @Test
    fun createPreferencesApiScreenWithPreconditions_stringResDescriptionsAndReasons_returnsCorrectDescriptionsAndReasons() {
        val preferenceValue = false
        val preferenceKey = "ApiPreference"
        var preconditionsCase = ALLOWED

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preference(
                        key = preferenceKey,
                        purpose = R.string.preference_purpose1,
                        type = AnyBoolean,
                    ) {
                        get {
                            preconditions(R.string.preconditions_description1) {
                                when (preconditionsCase) {
                                    ALLOWED -> Allowed
                                    CUSTOM -> Custom(R.string.preconditions_custom_message, stability = PreconditionStability.UNSTABLE)
                                    ENTERPRISE_RESTRICTION ->
                                        EnterpriseRestriction(
                                            R.string.preconditions_enterprise_restriction_message
                                        )
                                    HARDWARE_UNSUPPORTED ->
                                        HardwareUnsupported(
                                            R.string.preconditions_hardware_unsupported_message
                                        )
                                    INVALID_PREFERENCE ->
                                        InvalidPreference(
                                            "",
                                            "",
                                            R.string.preconditions_invalid_preference_message,
                                        )
                                }
                            }
                            execute { preferenceValue }
                        }
                    }
                }
            }

        // Check we only have 1 preference in the list
        assertThat(preferenceScreen.preferences.size).isEqualTo(1)

        // Check get preconditions description message
        val preference = preferenceScreen.preferences[0] as ApiPreference<Boolean, Boolean>
        assertThat(preference.get.preconditions?.getDescription(context))
            .isEqualTo(context.getString(R.string.preconditions_description1))

        // Create the API operation context to be used in ApiPreference calls
        val apiOperationContext = ApiOperationContext(context = context, parameters = ValidatedKeyParameters.EMPTY)

        // Evaluate each preconditions case
        for (case in ApiPreconditionsMapper.entries) {
            preconditionsCase = case

            runBlocking {
                when (case) {
                    ALLOWED -> {
                        assertThat(preference.get.execute(apiOperationContext)).isFalse()
                    }
                    CUSTOM -> {
                        assertThat(
                                (preference.get.preconditions?.check(apiOperationContext) as Custom)
                                    .getReason(context)
                            )
                            .isEqualTo(context.getString(R.string.preconditions_custom_message))
                    }
                    ENTERPRISE_RESTRICTION -> {
                        assertThat(
                                (preference.get.preconditions?.check(apiOperationContext)
                                        as EnterpriseRestriction)
                                    .getReason(context)
                            )
                            .isEqualTo(
                                context.getString(
                                    R.string.preconditions_enterprise_restriction_message
                                )
                            )
                    }
                    HARDWARE_UNSUPPORTED -> {
                        assertThat(
                                (preference.get.preconditions?.check(apiOperationContext)
                                        as HardwareUnsupported)
                                    .getReason(context)
                            )
                            .isEqualTo(
                                context.getString(
                                    R.string.preconditions_hardware_unsupported_message
                                )
                            )
                    }
                    INVALID_PREFERENCE -> {
                        assertThat(
                                (preference.get.preconditions?.check(apiOperationContext)
                                        as InvalidPreference)
                                    .getReason(context)
                            )
                            .isEqualTo(
                                context.getString(R.string.preconditions_invalid_preference_message)
                            )
                    }
                }
            }
        }
    }

    @Test
    fun createPreferencesApiScreenWithPreconditions_stringDescriptionsAndReasons_returnsCorrectDescriptionsAndReasons() {
        val preferenceValue = true
        val preferenceKey = "ApiPreference"
        var preconditionsCase = ALLOWED
        val preconditionDescription = "Preconditions description"
        val customPreconditionsMessage = "Custom preconditions message"
        val enterpriseRestrictionPreconditionsMessage = "Blocked by admin"
        val hardwareUnsupportedPreconditionsMessage = "Plug in the device"
        val invalidPreferencePreconditionsMessage = "Requesting invalid preference"
        val missingPermissionPreconditionsMessage = "You miss an important permission"

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preference(
                        key = preferenceKey,
                        purpose = R.string.preference_purpose1,
                        type = AnyBoolean,
                    ) {
                        get {
                            preconditions(preconditionDescription) {
                                when (preconditionsCase) {
                                    ALLOWED -> Allowed
                                    CUSTOM -> Custom(customPreconditionsMessage, stability = PreconditionStability.UNSTABLE)
                                    ENTERPRISE_RESTRICTION ->
                                        EnterpriseRestriction(
                                            enterpriseRestrictionPreconditionsMessage
                                        )
                                    HARDWARE_UNSUPPORTED ->
                                        HardwareUnsupported(hardwareUnsupportedPreconditionsMessage)
                                    INVALID_PREFERENCE ->
                                        InvalidPreference(
                                            "",
                                            "",
                                            invalidPreferencePreconditionsMessage,
                                        )
                                }
                            }
                            execute { preferenceValue }
                        }
                    }
                }
            }

        // Check we only have 1 preference in the list
        assertThat(preferenceScreen.preferences.size).isEqualTo(1)

        // Check get preconditions description message
        val preference = preferenceScreen.preferences[0] as ApiPreference<Boolean, Boolean>
        assertThat(preference.get.preconditions?.getDescription(context))
            .isEqualTo(preconditionDescription)

        // Create the API operation context to be used in ApiPreference calls
        val apiOperationContext = ApiOperationContext(context = context, parameters = ValidatedKeyParameters.EMPTY)

        // Evaluate each preconditions case
        for (case in ApiPreconditionsMapper.entries) {
            preconditionsCase = case

            runBlocking {
                when (case) {
                    ALLOWED -> {
                        assertThat(preference.get.execute(apiOperationContext)).isTrue()
                    }
                    CUSTOM -> {
                        assertThat(
                                (preference.get.preconditions?.check(apiOperationContext) as Custom)
                                    .getReason(context)
                            )
                            .isEqualTo(customPreconditionsMessage)
                    }
                    ENTERPRISE_RESTRICTION -> {
                        assertThat(
                                (preference.get.preconditions?.check(apiOperationContext)
                                        as EnterpriseRestriction)
                                    .getReason(context)
                            )
                            .isEqualTo(enterpriseRestrictionPreconditionsMessage)
                    }
                    HARDWARE_UNSUPPORTED -> {
                        assertThat(
                                (preference.get.preconditions?.check(apiOperationContext)
                                        as HardwareUnsupported)
                                    .getReason(context)
                            )
                            .isEqualTo(hardwareUnsupportedPreconditionsMessage)
                    }
                    INVALID_PREFERENCE -> {
                        assertThat(
                                (preference.get.preconditions?.check(apiOperationContext)
                                        as InvalidPreference)
                                    .getReason(context)
                            )
                            .isEqualTo(invalidPreferencePreconditionsMessage)
                    }
                }
            }
        }
    }

    @Test
    fun createPreferencesApiScreenWithGetterAndSetter_withValuePreconditions() {
        val initialPreferenceValue = 0
        var preferenceValue = initialPreferenceValue
        val preferenceKey = "ApiPreference"
        val newPreferenceWrongValue = 23
        val newPreferenceValue = 112

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preference(
                        key = preferenceKey,
                        purpose = R.string.preference_purpose1,
                        type = AnyInt,
                    ) {
                        get { execute { preferenceValue } }

                        set {
                            valuePreconditions(
                                R.string.value_preconditions_even_number_description
                            ) { value ->
                                // Value's digits must add up to even number
                                var sum = 0
                                for (digit in value.toString()) {
                                    sum += digit.digitToInt()
                                }
                                if (sum % 2 == 0) {
                                    Allowed
                                } else {
                                    Custom(R.string.value_preconditions_disallow_custom_reason, stability = PreconditionStability.UNSTABLE)
                                }
                            }

                            execute { value -> preferenceValue = value }
                        }
                    }
                }
            }

        // Check we only have 1 preference in the list
        assertThat(preferenceScreen.preferences.size).isEqualTo(1)

        // Trying to set a wrong value throws exception and value stays the same
        val preference = preferenceScreen.preferences[0] as ApiPreference<Int, Int>
        assertThat(preference.key).isEqualTo(preferenceKey)
        val exception =
            assertThrows(IllegalStateException::class.java) {
                preference
                    .storage(context)
                    .setValue(preferenceKey, Int::class.java, newPreferenceWrongValue)
            }
        assertThat(exception.message)
            .isEqualTo(context.getString(R.string.value_preconditions_disallow_custom_reason))
        assertThat(preference.storage(context).getValue(preferenceKey, Int::class.java))
            .isEqualTo(initialPreferenceValue)

        // Setting correct value succeeds
        assertThat(preference.key).isEqualTo(preferenceKey)
        preference.storage(context).setValue(preferenceKey, Int::class.java, newPreferenceValue)
        assertThat(preference.storage(context).getValue(preferenceKey, Int::class.java))
            .isEqualTo(newPreferenceValue)
    }

    @Test
    fun createPreferencesApiScreenWithParametersPermissionsPreconditions_wrongOrderPermissionsBeforeFlag_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        permissions(Manifest.permission.ACCESS_FINE_LOCATION)

                        flag { false }

                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("flag"))
    }

    @Test
    fun createPreferencesApiScreenWithParameters_wrongOrderPreferenceBeforeParameters_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }
                        }

                        parameters {
                            parameter(
                                "package",
                                R.string.parameter_purpose1,
                                true,
                                GeneratedParameterType(R.string.parameter_type_description) {
                                    listOf(GeneratedValue("value".safe(), "type_description".safe()))
                                },
                            )
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("parameters"))
    }

    @Test
    fun createPreferencesApiScreenWithParametersPermissionsPreconditions_wrongOrderPreconditionsBeforePermissions_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        parameters {
                            parameter(
                                "package",
                                R.string.parameter_purpose1,
                                true,
                                GeneratedParameterType(R.string.parameter_type_description) {
                                    listOf(GeneratedValue("value".safe(), "type_description".safe()))
                                },
                            )
                        }

                        preconditions(R.string.preconditions_description1) {
                            HardwareUnsupported(R.string.preconditions_hardware_unsupported_message)
                        }

                        permissions(Manifest.permission.ACCESS_FINE_LOCATION)

                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("permissions"))
    }

    @Test
    fun createPreferencesApiScreenMultipleFlagBlocks_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        flag { true }
                        flag { false }

                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("flag"))
    }

    @Test
    fun createPreferencesApiScreenMultipleParametersBlocks_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        parameters {
                            parameter(
                                "package",
                                R.string.parameter_purpose1,
                                true,
                                GeneratedParameterType(R.string.parameter_type_description) {
                                    listOf(GeneratedValue("value".safe(), "type_description".safe()))
                                },
                            )
                        }

                        parameters {
                            parameter(
                                "component",
                                R.string.parameter_purpose2,
                                true,
                                GeneratedParameterType(R.string.parameter_type_description) {
                                    listOf(GeneratedValue("value".safe(), "type_description".safe()))
                                },
                            )
                        }

                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("parameters"))
    }

    @Test
    fun parameters_emptyBlock_throwsException() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        parameters {}
                    }
                }
            }

        assertThat(exception.message).isEqualTo(EXCEPTION_MESSAGE_NO_PARAMETER_DEFINED)
    }

    @Test
    fun parameters_multipleParameters_throwsException() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        parameters {
                            parameter(
                                "package",
                                R.string.parameter_purpose1,
                                true,
                                GeneratedParameterType(R.string.parameter_type_description) {
                                    listOf(GeneratedValue("value".safe(), "type_description".safe()))
                                },
                            )
                            parameter(
                                "component",
                                R.string.parameter_purpose2,
                                true,
                                GeneratedParameterType(R.string.parameter_type_description) {
                                    listOf(GeneratedValue("value".safe(), "type_description".safe()))
                                },
                            )
                        }
                    }
                }
            }

        assertThat(exception.message)
            .isEqualTo(getExceptionMessageMultipleParametersDefined(2))
    }

    @Test
    fun getAllPossibleParameters_returnsCorrectParameters() = runTest {
        val screen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    parameters {
                        parameter(
                            "package",
                            R.string.parameter_purpose1,
                            true,
                            GeneratedParameterType(R.string.parameter_type_description) {
                                listOf(GeneratedValue("value".safe(), "type_description".safe()))
                            },
                        )
                    }
                }
            }

        val allPossibleParameters = screen.getAllPossibleParameters(context).toList()

        assertThat(allPossibleParameters).hasSize(1)
        assertThat(allPossibleParameters[0].getRequired("package")).isEqualTo("value")
    }

    @Test
    fun createPreferencesApiScreenMultiplePermissionsBlocks_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        permissions(Manifest.permission.ACCESS_FINE_LOCATION)
                        permissions(Manifest.permission.WRITE_SETTINGS)

                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("permissions"))
    }

    @Test
    fun createPreferencesApiScreenMultiplePreconditionsBlocks_fails() {
        val preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preconditions(R.string.preconditions_description1) {
                            HardwareUnsupported(R.string.preconditions_hardware_unsupported_message)
                        }
                        preconditions(R.string.preconditions_description2) {
                            EnterpriseRestriction(
                                R.string.preconditions_enterprise_restriction_message
                            )
                        }

                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("preconditions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithMultiplePermissionsBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            permissions(Manifest.permission.ACCESS_FINE_LOCATION)
                            permissions(Manifest.permission.WRITE_SETTINGS)

                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("permissions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithMultiplePreconditionsBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            preconditions(R.string.preconditions_description1) {
                                HardwareUnsupported(
                                    R.string.preconditions_hardware_unsupported_message
                                )
                            }
                            preconditions(R.string.preconditions_description1) {
                                EnterpriseRestriction(
                                    R.string.preconditions_enterprise_restriction_message
                                )
                            }

                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("preconditions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithMultipleGetBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }

                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("get"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithMultipleSetBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }

                            set { execute { value -> preferenceValue = value } }

                            set { execute { value -> preferenceValue = value } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("set"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithGetterAndSetter_wrongOrderSetBeforeGet_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set { execute { value -> preferenceValue = value } }

                            get { execute { preferenceValue } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("get"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithPermissionsPreconditionsGetterSetter_wrongOrderSetBeforeFlag_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }

                            set { execute { value -> preferenceValue = value } }

                            flag { false }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("flag"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithPermissionsPreconditionsGetterSetter_wrongOrderGetBeforePermissions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get { execute { preferenceValue } }

                            permissions(Manifest.permission.ACCESS_FINE_LOCATION)
                            preconditions(R.string.preconditions_description1) {
                                HardwareUnsupported(
                                    R.string.preconditions_hardware_unsupported_message
                                )
                            }

                            set { execute { value -> preferenceValue = value } }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("permissions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithGetter_wrongOrderExecuteBeforePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get {
                                permissions(Manifest.permission.ACCESS_FINE_LOCATION)

                                execute { preferenceValue }

                                preconditions(R.string.preconditions_description1) {
                                    HardwareUnsupported(
                                        R.string.preconditions_hardware_unsupported_message
                                    )
                                }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("preconditions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithGetter_withMultiplePermissions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get {
                                permissions(Manifest.permission.ACCESS_FINE_LOCATION)
                                permissions(Manifest.permission.WRITE_SETTINGS)

                                execute { preferenceValue }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("permissions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithGetter_withMultiplePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get {
                                preconditions(R.string.preconditions_description1) {
                                    HardwareUnsupported(
                                        R.string.preconditions_hardware_unsupported_message
                                    )
                                }
                                preconditions(R.string.preconditions_description2) {
                                    EnterpriseRestriction(
                                        R.string.preconditions_enterprise_restriction_message
                                    )
                                }

                                execute { preferenceValue }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("preconditions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithGetter_withMultipleExecuteBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            get {
                                execute { preferenceValue }

                                execute { preferenceValue }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("execute"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_withNoPreconditionsInWarning_succeeds() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preference(
                        key = preferenceKey,
                        purpose = R.string.preference_purpose1,
                        type = AnyBoolean,
                    ) {
                        get {
                            execute { preferenceValue }
                        }
                        set {
                            warning {
                                warn(R.string.warning_message1)
                            }

                            execute { value -> preferenceValue = value }
                        }
                    }
                }
            }

        // Check we only have 1 preference in the list
        assertThat(preferenceScreen.preferences.size).isEqualTo(1)

        // Check preference's set operation has the correct warning message
        val preference = preferenceScreen.preferences[0] as ApiPreference<Boolean, Boolean>
        assertThat(preference.set?.warning?.getWarning(context))
            .isEqualTo(context.getString(R.string.warning_message1))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_withNoWarnInWarning_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                warning {
                                    preconditions(R.string.warning_preconditions_description1) {
                                        Custom(R.string.preconditions_custom_message, stability = PreconditionStability.UNSTABLE)
                                    }
                                }

                                execute { value -> preferenceValue = value }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message)
            .isEqualTo("warning 'warn' block is required")
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_withMultiplePreconditionsInWarning_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                warning {
                                    preconditions(R.string.warning_preconditions_description1) {
                                        Custom(R.string.preconditions_custom_message, stability = PreconditionStability.UNSTABLE)
                                    }
                                    valuePreconditions(R.string.warning_preconditions_description1) { value ->
                                        Allowed
                                    }
                                    warn(R.string.warning_message1)
                                }

                                execute { value -> preferenceValue = value }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message)
            .isEqualTo(getExceptionMessageAlreadyDefined("preconditions or valuePreconditions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_wrongOrderExecuteBeforeValuePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                permissions(Manifest.permission.ACCESS_FINE_LOCATION)

                                preconditions(R.string.preconditions_description1) {
                                    HardwareUnsupported(
                                        R.string.preconditions_hardware_unsupported_message
                                    )
                                }

                                execute { value -> preferenceValue = value }

                                valuePreconditions(R.string.value_preconditions_description1) {
                                    value ->
                                    Allowed
                                }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("valuePreconditions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_wrongOrderExecuteBeforeWarning_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                permissions(Manifest.permission.ACCESS_FINE_LOCATION)

                                preconditions(R.string.preconditions_description1) {
                                    HardwareUnsupported(
                                        R.string.preconditions_hardware_unsupported_message
                                    )
                                }

                                execute { value -> preferenceValue = value }

                                warning {
                                    preconditions(R.string.warning_preconditions_description1) {
                                        Custom(R.string.preconditions_custom_message, stability = PreconditionStability.UNSTABLE)
                                    }
                                    warn(R.string.warning_message1)
                                }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageWrongOrder("warning"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_withMultiplePermissions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                permissions(Manifest.permission.ACCESS_FINE_LOCATION)
                                permissions(Manifest.permission.WRITE_SETTINGS)

                                execute { value -> preferenceValue = value }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("permissions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_withMultiplePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                preconditions(R.string.preconditions_description1) {
                                    HardwareUnsupported(
                                        R.string.preconditions_hardware_unsupported_message
                                    )
                                }
                                preconditions(R.string.preconditions_description2) {
                                    EnterpriseRestriction(
                                        R.string.preconditions_enterprise_restriction_message
                                    )
                                }

                                execute { value -> preferenceValue = value }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("preconditions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_withMultipleValuePreconditions_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                valuePreconditions(R.string.value_preconditions_description1) {
                                    value ->
                                    Allowed
                                }
                                valuePreconditions(R.string.value_preconditions_description2) {
                                    value ->
                                    Allowed
                                }

                                execute { value -> preferenceValue = value }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message)
            .isEqualTo(getExceptionMessageMultipleDefines("valuePreconditions"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_withMultipleWarnings_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                warning {
                                    preconditions(R.string.warning_preconditions_description1) {
                                        Custom(R.string.preconditions_custom_message, stability = PreconditionStability.UNSTABLE)
                                    }
                                    warn(R.string.warning_message1)
                                }

                                warning {
                                    valuePreconditions(R.string.warning_preconditions_description1) { value ->
                                        Allowed
                                    }
                                    warn(R.string.warning_message1)
                                }

                                execute { value -> preferenceValue = value }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message)
            .isEqualTo(getExceptionMessageMultipleDefines("warning"))
    }

    @Test
    fun createPreferencesApiScreenPreferenceWithSetter_withMultipleExecuteBlocks_fails() {
        var preferenceValue = false
        val preferenceKey = "ApiPreference"

        val exception =
            assertThrows(IllegalStateException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = SCREEN_KEY,
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                    ) {
                    init {
                        preference(
                            key = preferenceKey,
                            purpose = R.string.preference_purpose1,
                            type = AnyBoolean,
                        ) {
                            set {
                                execute { value -> preferenceValue = value }

                                execute { value -> preferenceValue = value }
                            }
                        }
                    }
                }
            }

        assertThat(exception.message).isEqualTo(getExceptionMessageMultipleDefines("execute"))
    }

    @Test
    fun createPreferencesApiScreen_withAlreadyPartiallyMigrated_keyStartsWithApi_succeeds() {
        val preferenceKey = "ApiPreference"
        val preferenceValue = false

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = "api_screen_key",
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                    alreadyPartiallyMigrated = PreferenceFragment::class,
                ) {}

        assertThat(preferenceScreen.key.startsWith(PARTIALLY_MIGRATED_PREFIX)).isTrue()
    }

    @Test
    fun createPreferencesApiScreen_withAlreadyPartiallyMigrated_keyDoesNotStartWithApi_fails() {
        val preferenceKey = "ApiPreference"
        val preferenceValue = false

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                object :
                    PreferencesApiScreen(
                        key = "screen_key",
                        topLevelSettingsCategory = Category.SYSTEM,
                        fragment = PreferenceFragment::class,
                        purpose = R.string.preference_screen_purpose,
                        alreadyPartiallyMigrated = PreferenceFragment::class,
                    ) {}
            }

        assertThat(exception.message)
            .isEqualTo(
                "The key 'screen_key' must start with 'api_' because it has an already migrated class."
            )
    }

    @Test
    fun createPreferencesApiScreen_screenPreconditionsFail_launchIntentIsNotNull() {
        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preconditions(R.string.preconditions_description1) {
                        Custom(R.string.preconditions_custom_message, stability = PreconditionStability.UNSTABLE)
                    }
                }
            }

        assertThat(preferenceScreen.getLaunchIntent(context, null)).isNotNull()
    }

    @Test
    fun createPreferencesApiScreen_screenPreconditionsMet_launchIntentIsNotNull() {
        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    preconditions(R.string.preconditions_description1) { Allowed }
                }
            }

        assertThat(preferenceScreen.getLaunchIntent(context, null)).isNotNull()
    }

    @Test
    fun createPreferencesApiScreen_failingFlag_skipsCheckWhenDebuggableAndSettingEnabled() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.SKIP_CATALYST_FLAG_CHECKS",
            1
        )

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    flag { false }
                }
            }

        assertThat(preferenceScreen.isFlagEnabled(context)).isTrue()
    }

    @Test
    fun createPreferencesApiScreen_failingFlag_returnsFalse_whenNotDebuggableAndSettingEnabled() {
        ShadowBuild.setType("user")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.SKIP_CATALYST_FLAG_CHECKS",
            1
        )

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    flag { false }
                }
            }

        assertThat(preferenceScreen.isFlagEnabled(context)).isFalse()
    }

    @Test
    fun createPreferencesApiScreen_failingFlag_returnsFalse_whenDebuggableAndSettingDisabled() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(
            context.contentResolver,
            "com.android.settings.SKIP_CATALYST_FLAG_CHECKS",
            0
        )

        val preferenceScreen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    flag { false }
                }
            }

        assertThat(preferenceScreen.isFlagEnabled(context)).isFalse()
    }

    @Test
    fun dynamicSpaScreen_missingPrepareSpaRoute_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            object : PreferencesApiScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                purpose = R.string.preference_screen_purpose,
            ) {
                init {
                    parameters {
                        parameter(
                            "package",
                            R.string.parameter_purpose1,
                            true,
                            GeneratedParameterType(R.string.parameter_type_description) {
                                listOf(GeneratedValue("value".safe(), "type_description".safe()))
                            },
                        )
                        // Missing prepareSpaRoute
                    }
                }
            }
        }
    }

        @Test
    fun createPreferencesApiScreen_withoutTags_returnsApiFirstAndUncategorizedDeviceState() {
        val screen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
            }

        assertThat(screen.tags(context)).asList().containsExactly("api-first", "getUncategorizedDeviceState")
    }

    @Test
    fun createPreferencesApiScreen_withTags_returnsTagsWithApiFirstAndUncategorizedDeviceState() {
        val screen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    tags("tag1", "tag2")
                }
            }

        assertThat(screen.tags(context)).asList().containsExactly("tag1", "tag2", "api-first", "getUncategorizedDeviceState")
    }

        @Test
    fun createPreferencesApiScreen_withAppFunctionTag_returnsTagsWithApiFirstWithoutUncategorizedDeviceState() {
        val screen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    tags("tag1", "tag2", PreferencesApiScreen.APP_FUNCTION_BATTERY)
                }
            }

        assertThat(screen.tags(context)).asList().containsExactly("tag1", "tag2", PreferencesApiScreen.APP_FUNCTION_BATTERY, "api-first")
    }

    @Test
    fun createPreferencesApiScreen_withoutSensitivity_hasDefaultNoSensitivity() {
        val screen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
            }

        assertThat(screen.sensitivityLevel).isEqualTo(SensitivityLevel.NO_SENSITIVITY)
    }

    @Test
    fun createPreferencesApiScreen_withSpecifiedSensitivity_hasSpecifiedSensitivity() {
        val screen =
            object :
                PreferencesApiScreen(
                    key = SCREEN_KEY,
                    topLevelSettingsCategory = Category.SYSTEM,
                    fragment = PreferenceFragment::class,
                    purpose = R.string.preference_screen_purpose,
                ) {
                init {
                    sensitivityLevel(SensitivityLevel.DO_NOT_EXPOSE)
                }
            }

        assertThat(screen.sensitivityLevel).isEqualTo(SensitivityLevel.DO_NOT_EXPOSE)
    }

    @Test
    fun dynamicSpaConstructor_withoutParameters_throwsException() {
        assertThrows(IllegalStateException::class.java) {
            val screen = object : PreferencesApiScreen(
                key = SCREEN_KEY,
                topLevelSettingsCategory = Category.SYSTEM,
                purpose = R.string.preference_screen_purpose,
            ) {
                // Intentionally empty init block
            }
            // The check is performed when the destination is requested
            screen.fragmentClass()
        }
    }

    companion object {
        const val SCREEN_KEY = "ApiScreen"

        enum class ApiPreconditionsMapper {
            ALLOWED,
            ENTERPRISE_RESTRICTION,
            HARDWARE_UNSUPPORTED,
            INVALID_PREFERENCE,
            CUSTOM,
        }
    }
}
