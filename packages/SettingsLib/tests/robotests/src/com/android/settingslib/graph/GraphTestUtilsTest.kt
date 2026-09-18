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

package com.android.settingslib.graph

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceAvailabilityProvider
import com.android.settingslib.metadata.PreferenceRestrictionProvider
import com.android.settingslib.metadata.PreferenceScreenRegistry
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.isUiOnlyPreference
import com.android.settingslib.robotests.R
import com.android.settingslib.testutils.GraphTestUtils
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.spy
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GraphTestUtilsTest {

    private val application = spy(ApplicationProvider.getApplicationContext<Application>()!!)
    private val context = application as Context

    @Test
    fun setRegistryFactories_setsFactoriesCorrectly() {
        val screen = GraphTestUtils.createScreen(
                GraphTestUtils.PreferenceScreenConfig(
                "test_screen",
                R.string.preference_screen_purpose
            )
        )
        GraphTestUtils.setRegistryFactories(screen)
        val factory = PreferenceScreenRegistry.preferenceScreenMetadataFactories?.get("test_screen")
        assertThat(factory).isNotNull()
        assertThat(factory?.create(context)).isEqualTo(screen)
    }

    @Test
    fun createScreen_hasCorrectKeyTitleSummaryAndPurpose() {
        val screen = GraphTestUtils.createScreen(
            GraphTestUtils.PreferenceScreenConfig(
                "test_screen",
                title = R.string.preference_screen_title,
                summary = R.string.preference_screen_summary,
                purpose = R.string.preference_screen_purpose,
                preferences = listOf()
            )
        )
        assertThat(screen.key).isEqualTo("test_screen")
        assertThat(screen.title).isEqualTo(R.string.preference_screen_title)
        assertThat(screen.summary).isEqualTo(R.string.preference_screen_summary)
        assertThat(screen.purpose).isEqualTo(R.string.preference_screen_purpose)
    }

    @Test
    fun createScreen_withoutSpecifiedSensitivity_hasNoSensitivity() {
        val screen = GraphTestUtils.createScreen(
            GraphTestUtils.PreferenceScreenConfig(
                "test_screen",
                title = R.string.preference_screen_title,
                purpose = R.string.preference_screen_purpose,
                preferences = listOf()
            )
        )
        assertThat(screen.sensitivityLevel).isEqualTo(SensitivityLevel.NO_SENSITIVITY)
    }

    @Test
    fun createScreen_withSpecifiedSensitivity_hasSpecifiedSensitivity() {
        val screen = GraphTestUtils.createScreen(
            GraphTestUtils.PreferenceScreenConfig(
                "test_screen",
                title = R.string.preference_screen_title,
                purpose = R.string.preference_screen_purpose,
                preferences = listOf(),
                sensitivityLevel = SensitivityLevel.DEEP_LINK_ONLY
            )
        )
        assertThat(screen.key).isEqualTo("test_screen")
        assertThat(screen.title).isEqualTo(R.string.preference_screen_title)
        assertThat(screen.purpose).isEqualTo(R.string.preference_screen_purpose)
        assertThat(screen.sensitivityLevel).isEqualTo(SensitivityLevel.DEEP_LINK_ONLY)
    }

    @Test
    fun createScreen_withTwoPreferences_hasCorrectPreferences() = runTest {
        val metadata1 = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                "preference_key",
                R.string.preference_purpose,
            )
        )
        val metadata2 = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                "preference_key2",
                R.string.preference_purpose
            )
        )
        val screen = GraphTestUtils.createScreen(
            GraphTestUtils.PreferenceScreenConfig(
                "test_screen",
                purpose = R.string.preference_screen_purpose,
                title = R.string.preference_screen_title,
                preferences = listOf(metadata1, metadata2)
            )
        )
        assertThat(screen.getPreferenceHierarchy(context, this)
            .find("preference_key")
        ).isEqualTo(metadata1)
        assertThat(screen.getPreferenceHierarchy(context, this)
            .find("preference_key2")
        ).isEqualTo(metadata2)
    }

    @Test
    fun createSimplePreference_createsPreferenceWithCorrectValues() {
        val preference = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                "test_preference",
                purpose = R.string.preference_purpose,
                summary = R.string.preference_summary,
                isAvailable = false,
                isRestricted = true,
                isEnabled = false,
            )
        )
        assertThat(preference.key).isEqualTo("test_preference")
        assertThat(preference.purpose).isEqualTo(R.string.preference_purpose)
        assertThat(preference.summary).isEqualTo(R.string.preference_summary)
        assertThat(preference.isEnabled(context)).isEqualTo(false)
        assertThat((preference as PreferenceAvailabilityProvider).isAvailable(context))
            .isEqualTo(false)
        assertThat((preference as PreferenceRestrictionProvider).isRestricted(context))
            .isEqualTo(true)
    }

    @Test
    fun createSimplePreference_withoutSpecifiedSensitivity_hasNoSensitivity() {
        val preference = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                "test_preference",
                purpose = R.string.preference_purpose,
                isAvailable = false,
                isRestricted = true,
                isEnabled = false,
            )
        )
        assertThat(preference.sensitivityLevel).isEqualTo(SensitivityLevel.NO_SENSITIVITY)
    }

    @Test
    fun createSimplePreference_withSpecifiedSensitivity_hasSpecifiedSensitivity() {
        val preference = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                "test_preference",
                purpose = R.string.preference_purpose,
                isAvailable = false,
                isRestricted = true,
                isEnabled = false,
                sensitivityLevel = SensitivityLevel.DEEP_LINK_ONLY
            )
        )
        assertThat(preference.sensitivityLevel).isEqualTo(SensitivityLevel.DEEP_LINK_ONLY)
    }

    @Test
    fun createPersistentPreference_withoutSpecifiedSensitivity_hasNoSensitivity() {
        val persistentConfig = GraphTestUtils.PersistentPreferenceConfig(
            preferenceConfig = GraphTestUtils.PreferenceConfig(
                "test_preference",
                purpose = R.string.preference_purpose,
                isAvailable = false,
                isRestricted = true,
                isEnabled = false,
                sensitivityLevel = SensitivityLevel.DEEP_LINK_ONLY
            ),
        )
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(
            persistentConfig
        )
        assertThat(preference.sensitivityLevel).isEqualTo(SensitivityLevel.DEEP_LINK_ONLY)
    }

    @Test
    fun createPersistentPreference_withSpecifiedSensitivity_hasSpecifiedSensitivity() {
        val persistentConfig = GraphTestUtils.PersistentPreferenceConfig(
            preferenceConfig = GraphTestUtils.PreferenceConfig(
                "test_preference",
                purpose = R.string.preference_purpose,
                isAvailable = false,
                isRestricted = true,
                isEnabled = false,
                sensitivityLevel = SensitivityLevel.DEEP_LINK_ONLY
            ),
        )
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(
            persistentConfig
        )
        assertThat(preference.sensitivityLevel).isEqualTo(SensitivityLevel.DEEP_LINK_ONLY)
    }

    @Test
    fun createPersistentPreference_respectsPreferenceConfigFlags() {
        val config = GraphTestUtils.PreferenceConfig(
            key = "test_key",
            purpose = R.string.preference_purpose,
            summary = R.string.preference_summary,
            isAvailable = false,
            isRestricted = true,
            isEnabled = false
        )
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(
            GraphTestUtils.PersistentPreferenceConfig(config)
        )

        assertThat(preference.key).isEqualTo("test_key")
        assertThat(preference.purpose).isEqualTo(R.string.preference_purpose)
        assertThat(preference.summary).isEqualTo(R.string.preference_summary)
        assertThat((preference as PreferenceAvailabilityProvider).isAvailable(context)).isFalse()
        assertThat((preference as PreferenceRestrictionProvider).isRestricted(context)).isTrue()
        assertThat((preference).isEnabled(context)).isFalse()
    }

    @Test
    fun createPersistentPreference_respectsPersistentPreferenceConfigPermits() {
        val persistentConfig = GraphTestUtils.PersistentPreferenceConfig(
            preferenceConfig = GraphTestUtils.PreferenceConfig(
                key = "test_key",
                purpose = R.string.preference_purpose
            ),
            valueType = Boolean::class.javaObjectType,
            readPermit = ReadWritePermit.DISALLOW,
            writePermit = ReadWritePermit.REQUIRE_USER_AGREEMENT,
            readPermission = "read_perm",
            writePermission = "write_perm"
        )
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(persistentConfig)

        @Suppress("UNCHECKED_CAST")
        val persistentPreference = preference as PersistentPreference<Boolean>
        assertThat(persistentPreference.valueType).isEqualTo(Boolean::class.javaObjectType)
        assertThat(persistentPreference.getReadPermit(context, 0, 0)).isEqualTo(ReadWritePermit.DISALLOW)
        assertThat(persistentPreference.getWritePermit(context, 0, 0)).isEqualTo(ReadWritePermit.REQUIRE_USER_AGREEMENT)
        assertThat(persistentPreference.getReadPermissions(context)).isEqualTo(Permissions.allOf("read_perm"))
        assertThat(persistentPreference.getWritePermissions(context)).isEqualTo(Permissions.allOf("write_perm"))
    }

    @Test
    fun createPersistentPreference_withDefaultValue_returnsDefaultValue() {
        val persistentConfig = GraphTestUtils.PersistentPreferenceConfig(
            preferenceConfig = GraphTestUtils.PreferenceConfig(
                key = "test_key",
                purpose = R.string.preference_purpose
            ),
            defaultValue = true,
        )
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(persistentConfig)

        assertThat((preference as PersistentPreference<Boolean>).storage(context).getBoolean("test_key")).isEqualTo(true)
    }

    @Test
    fun createPersistentPreference_WithThrowsError_throwsErrorWhenConfigured() {
        val preference = GraphTestUtils.createPersistentPreference<Boolean>(
            GraphTestUtils.PersistentPreferenceConfig(
                GraphTestUtils.PreferenceConfig(
                    key = "test_key",
                    purpose = R.string.preference_purpose,
                ),
                throwsError = true
            )
        )

        @Suppress("UNCHECKED_CAST")
        val persistentPreference = preference as PersistentPreference<Boolean>
        assertThrows(IllegalStateException::class.java) {
            persistentPreference.getWritePermit(context, 0, 0)
        }
    }

    @Test
    fun createIntRangePreference_createsPreferenceWithCorrectValues() {
        val preference = GraphTestUtils.createIntRangePreference(
            "test_key",
            purpose = R.string.preference_purpose,
            1,
            10,
            5
        )
        assertThat(preference.key).isEqualTo("test_key")
        assertThat(preference.purpose).isEqualTo(R.string.preference_purpose)
        assertThat(preference.getMinValue(context)).isEqualTo(1)
        assertThat(preference.getMaxValue(context)).isEqualTo(10)
        assertThat(preference.storage(context).getInt("test_key")).isEqualTo(5)
    }

    @Test
    fun createStorage_storesAndRetrievesValue() {
        val storage = GraphTestUtils.createStorage("initial_value", "preference_key")
        assertThat(storage.getValue("preference_key", String::class.javaObjectType))
            .isEqualTo("initial_value")

        storage.setValue("preference_key", String::class.javaObjectType, "new_value")
        assertThat(storage.getValue("preference_key", String::class.javaObjectType))
            .isEqualTo("new_value")
    }

    @Test
    fun makePermissionPass_mocksPermissionCorrectly() {
        GraphTestUtils.makePermissionPass(application, "test_permission", true)
        assertThat(application.checkPermission("test_permission", 0, 0))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)

        GraphTestUtils.makePermissionPass(application, "test_permission", false)
        assertThat(application.checkPermission("test_permission", 0, 0))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
    }

    @Test
    fun createSimplePreference_whenNonUiOnly_returnsFalseIsUiOnlyPreference() {
        val preference = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                key = "preference_key",
                purpose = R.string.preference_purpose,
                isUiOnly = false
            )
        )
        assertThat(preference.isUiOnlyPreference(context)).isFalse()
    }

    @Test
    fun createSimplePreference_whenUiOnly_returnsTrueIsUiOnlyPreference() {
        val preference = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                key = "preference_key",
                purpose = R.string.preference_purpose,
                isUiOnly = true
            )
        )
        assertThat(preference.isUiOnlyPreference(context)).isTrue()
    }

    @Test
    fun createPersistentPreference_whenNonUiOnly_returnsFalseIsUiOnlyPreference() {
        val persistentPreference = GraphTestUtils.createPersistentPreference<Boolean>(
            GraphTestUtils.PersistentPreferenceConfig(
                GraphTestUtils.PreferenceConfig(
                    key = "preference_key",
                    purpose = R.string.preference_purpose,
                    isUiOnly = false
                ),
            )
        )
        assertThat(persistentPreference.isUiOnlyPreference(context)).isFalse()
    }

    fun createScreen_whenNoDefaultSensitivity_hasNoSensitivity() {
        val screen = GraphTestUtils.createScreen(
            screenConfig = GraphTestUtils.PreferenceScreenConfig(
                screenKey = "preference_screen_key",
                purpose = R.string.preference_screen_purpose,
            )
        )
        assertThat(screen.sensitivityLevel).isEqualTo(SensitivityLevel.NO_SENSITIVITY)
    }

    fun createScreen_whenSpecifiedSensitivity_hasSpecifiedSensitivity() {
        val screen = GraphTestUtils.createScreen(
            screenConfig = GraphTestUtils.PreferenceScreenConfig(
                screenKey = "preference_screen_key",
                purpose = R.string.preference_screen_purpose,
                sensitivityLevel = SensitivityLevel.DO_NOT_EXPOSE
            )
        )
        assertThat(screen.sensitivityLevel).isEqualTo(SensitivityLevel.DO_NOT_EXPOSE)
    }

    @Test
    fun createScreen_withNestedCategories_hasCorrectHierarchy() = runTest {
        val metadata1 = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                "preference_key1",
                R.string.preference_purpose,
            )
        )
        val metadata2 = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                "preference_key2",
                R.string.preference_purpose
            )
        )
        val metadata3 = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                "preference_key3",
                R.string.preference_purpose
            )
        )
        val innerCategory = GraphTestUtils.PreferenceCategoryConfig(
            "inner_category",
            preferences = listOf(metadata3)
        )
        val outerCategory = GraphTestUtils.PreferenceCategoryConfig(
            "outer_category",
            preferences = listOf(metadata2),
            innerCategories = listOf(innerCategory)
        )
        val screen = GraphTestUtils.createScreen(
            GraphTestUtils.PreferenceScreenConfig(
                "test_screen",
                purpose = R.string.preference_screen_purpose,
                title = R.string.preference_screen_title,
                preferences = listOf(metadata1),
                preferencesInCategories = listOf(outerCategory)
            )
        )

        val hierarchy = screen.getPreferenceHierarchy(context, this)
        assertThat(hierarchy.find("preference_key1")).isEqualTo(metadata1)
        assertThat(hierarchy.find("preference_key2")).isEqualTo(metadata2)
        assertThat(hierarchy.find("preference_key3")).isEqualTo(metadata3)
        assertThat(hierarchy.find("outer_category")).isNotNull()
        assertThat(hierarchy.find("inner_category")).isNotNull()
    }

    @Test
    fun createPersistentPreference_whenUiOnly_returnsTrueIsUiOnlyPreference() {
        val persistentPreference = GraphTestUtils.createSimplePreference(
            GraphTestUtils.PreferenceConfig(
                key = "preference_key",
                purpose = R.string.preference_purpose,
                isUiOnly = true
            )
        )
        assertThat(persistentPreference.isUiOnlyPreference(context)).isTrue()
    }
}