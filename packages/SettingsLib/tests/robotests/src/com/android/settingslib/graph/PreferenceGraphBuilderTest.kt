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

package com.android.settingslib.graph

import android.content.Context

import android.provider.Settings
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider
import com.android.settingslib.datastore.Permissions
import com.android.settingslib.metadata.PersistentPreference
import com.android.settingslib.metadata.PreferenceHierarchy
import com.android.settingslib.metadata.PreferenceScreenMetadata
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferencesapi.PreferencesApiScreen
import com.android.settingslib.metadata.preferencesapi.preconditions.Allowed
import com.android.settingslib.metadata.preferencesapi.types.AnyInt
import com.android.settingslib.metadata.preferenceHierarchy
import com.android.settingslib.metadata.preferencesapi.category.Category
import com.android.settingslib.metadata.preferencesapi.types.AnyString
import com.google.common.truth.Truth.assertThat
import com.android.settingslib.graph.PreferenceGetterFlags
import com.android.settingslib.metadata.CatalystFlagProvider
import com.android.settingslib.metadata.CatalystFlagProviderFactory
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.PreferenceSetWarningProvider
import com.android.settingslib.metadata.WarningInfo
import com.android.settingslib.metadata.preferencesapi.ApiPreference
import com.android.settingslib.metadata.preferencesapi.GetConfig
import com.android.settingslib.metadata.preferencesapi.PreconditionsConfig
import com.android.settingslib.metadata.preferencesapi.SetConfig
import com.android.settingslib.metadata.preferencesapi.multiusers.PreferenceTarget
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class PreferenceGraphBuilderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var originalProvider: CatalystFlagProvider

    @Before
    fun setUp() {
        originalProvider = CatalystFlagProviderFactory.getInstance()
    }

    private open class TestPreference(
        override val sensitivityLevel: Int,
        private val writePermissions: Permissions? = null
    ) : PersistentPreference<Int>, PreferenceSetWarningProvider {
        override val bindingKey: String = "test_key"
        override val valueType: Class<Int> = Int::class.javaObjectType
        override val key: String = "test_key"
        override val purpose: Int = 2737

        override fun isPersistent(context: Context): Boolean = true

        override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int): Int? =
            ReadWritePermit.ALLOW

        override fun getWritePermissions(context: Context): Permissions? = writePermissions

        override val setWarning = WarningInfo(
            preconditionsDescription = "set_warning_preconditions_description",
            warningMessage = "set_warning_message"
        )

        override val supportsWrite = true
    }

    @After
    fun tearDown() {
        ShadowBuild.reset()
        CatalystFlagProviderFactory.setProvider(originalProvider)
    }

    private fun setCatalystUseKeyParameters(value: Boolean) {
        CatalystFlagProviderFactory.setProvider(object : CatalystFlagProvider {
            override fun catalystUseKeyParameters() = value
        })
    }

    private open class TestApiPreference(
        override val key: String = "key",
        override val purpose: Int = 0,
        override val screenPreconditions: PreconditionsConfig? = null,
        override val preconditions: PreconditionsConfig? = null,
        override val get: GetConfig<Int> = GetConfig(execute = { 42 }),
        override val set: SetConfig<Int>? = null,
        val parametersSchema: KeyParametersSchema? = null,
        val parameters: ValidatedKeyParameters? = null
    ) : ApiPreference<Int, Int>(null, PreferenceTarget.DEVICE) {
        override val type = AnyInt
        override val valueType = Int::class.javaObjectType
        override val permissions: Permissions? = null
        override val screenPermissions: Permissions? = null
        override val getScreenParameters: () -> ValidatedKeyParameters? = { null }
        override val getParametersSchema: () -> KeyParametersSchema? = { parametersSchema }
        override val getParameters: () -> ValidatedKeyParameters? = { parameters }
        override val supportsWrite = set != null
    }

    @Test
    fun evalWritePermit_debuggableAndUnknownSensitivity_settingEnabled_returnsWritePermit_onUserdebug() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 1)
        val preference = TestPreference(SensitivityLevel.DO_NOT_EXPOSE)

        val result = preference.evalWritePermit(context, 0, 0)

        // Allowed because it is debuggable AND the flag is set
        assertThat(result).isEqualTo(ReadWritePermit.ALLOW)
    }

    @Test
    fun evalWritePermit_debuggableAndUnknownSensitivity_settingDisabled_returnsDisallow_onUserdebug() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 0)
        val preference = TestPreference(SensitivityLevel.DO_NOT_EXPOSE)

        val result = preference.evalWritePermit(context, 0, 0)

        // Disallowed because the flag is off, even on userdebug
        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    @Test
    fun evalWritePermit_notDebuggableAndUnknownSensitivity_settingEnabled_returnsDisallow() {
        ShadowBuild.setType("user")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 1)
        val preference = TestPreference(SensitivityLevel.DO_NOT_EXPOSE)

        val result = preference.evalWritePermit(context, 0, 0)

        // Disallowed because it is not a debuggable build
        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    @Test
    fun evalWritePermit_highSensitivity_settingEnabled_returnsDisallow() {
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 1)
        val preference = TestPreference(SensitivityLevel.DEEP_LINK_ONLY)

        val result = preference.evalWritePermit(context, 0, 0)

        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    @Test
    fun evalWritePermit_requiresConfirmationSensitivity_returnsDisallow() {
        // Arrange: Create a preference with REQUIRES_CONFIRMATION sensitivity level.
        // The build type and global setting should not affect this outcome.
        ShadowBuild.setType("userdebug")
        Settings.Global.putInt(context.contentResolver, SETTING_KEY, 1)
        val preference = TestPreference(SensitivityLevel.REQUIRES_CONFIRMATION)

        // Act: Evaluate the write permit.
        val result = preference.evalWritePermit(context, 0, 0)

        // Assert: The result should be DISALLOW, as this sensitivity level is strictly disallowed.
        assertThat(result).isEqualTo(ReadWritePermit.DISALLOW)
    }

    private val screenMetadata = object : PreferenceScreenMetadata {
        override val bindingKey: String = "screen_key"
        override val key: String = "screen_key"
        override val purpose: Int = 0
        override fun fragmentClass(): Class<out Fragment>? = null

        override fun getPreferenceHierarchy(
            context: Context,
            coroutineScope: CoroutineScope
        ): PreferenceHierarchy = preferenceHierarchy(context) {}
    }

    @Test
    fun toProto_isApiPreference_includesScreenPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preconditions("screen_precondition_desc") { Allowed }
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get { execute { 42 } }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)
        assertThat(proto.getPreconditionsList).containsExactly("screen_precondition_desc")
    }

    @Test
    fun toProto_isApiPreference_includesPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    preconditions("precondition_desc") { Allowed }
                    get { execute { 42 } }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.getPreconditionsList).containsExactly("precondition_desc")
    }

    @Test
    fun toProto_isApiPreference_includesGetPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        preconditions("get_precondition_desc") { Allowed }
                        execute { 42 }
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.getPreconditionsList).containsExactly("get_precondition_desc")
    }

    @Test
    fun toProto_isApiPreference_includesSetPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        execute { 1 }
                    }
                    set {
                        preconditions("set_precondition_desc") { Allowed }
                        execute {}
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.setPreconditionsList).containsExactly("set_precondition_desc")
    }

    @Test
    fun toProto_isApiPreference_includesSetValuePreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        execute { 1 }
                    }
                    set {
                        valuePreconditions("set_value_precondition_desc") { _ -> Allowed }
                        execute {}
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.setPreconditionsList).containsExactly("set_value_precondition_desc")
    }

    @Test
    fun toProto_isPreferencesApiScreen_includesGetPreconditions() {
        val preference = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preconditions("get_precondition_desc") { Allowed }
            }
        }
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.getPreconditionsList).containsExactly("get_precondition_desc")
    }

    @Test
    fun toProto_apiPreferenceWithSetter_isWritable() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get { execute { 42 } }
                    set { execute { } }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.writable).isTrue()
    }

    @Test
    fun toProto_apiPreferenceWithoutSetter_isNotWritable() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get { execute { 42 } }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.writable).isFalse()
    }

    @Test
    fun toProto_preferencesApiScreen_isNotWritable() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {}
        val proto = screen.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.writable).isFalse()
    }

    @Test
    fun toProto_apiPreference_includesKeyParametersAndSchema() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema {
            parameter("test_param", "test_purpose", required = true, type = AnyString)
        }
        val params = schema.prepare("test_param" to "value")

        val preference = TestApiPreference(
            parametersSchema = schema,
            parameters = params
        )

        val proto = preference.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.hasParametersSchema()).isTrue()
        assertThat(proto.hasKeyParameters()).isTrue()
        assertThat(proto.keyParameters.valuesMap).containsEntry("test_param", "value")
    }

    @Test
    fun toProto_preferenceScreenMetadata_includesKeyParametersAndSchema() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema {
            parameter("test_param", "test_purpose", required = true, type = AnyString)
        }
        val params = schema.prepare("test_param" to "value")
        val screenMetadataWithParams = object : PreferenceScreenMetadata {
            override val bindingKey: String = "screen_key"
            override val key: String = "screen_key"
            override val purpose: Int = 0
            override fun fragmentClass(): Class<out Fragment>? = null
            override val keyParametersSchema: KeyParametersSchema = schema
            override val keyParameters: ValidatedKeyParameters = params

            override fun getPreferenceHierarchy(
                context: Context,
                coroutineScope: CoroutineScope
            ): PreferenceHierarchy = preferenceHierarchy(context) {}
        }

        val proto = screenMetadataWithParams.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.hasParametersSchema()).isTrue()
        assertThat(proto.hasKeyParameters()).isTrue()
        assertThat(proto.keyParameters.valuesMap).containsEntry("test_param", "value")
    }

    @Test
    fun toProto_apiPreference_includesValueDescriptorInSchema() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema {
            parameter("test_param", "test_purpose", required = true, type = AnyInt)
        }
        val params = schema.prepare("test_param" to "value")

        val preference = TestApiPreference(
            parametersSchema = schema,
            parameters = params
        )

        val proto = preference.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.hasParametersSchema()).isTrue()
        assertThat(proto.parametersSchema.parametersMap).containsKey("test_param")
        val paramProto = proto.parametersSchema.parametersMap["test_param"]!!
        assertThat(paramProto.hasValueDescriptor()).isTrue()
        assertThat(paramProto.valueDescriptor.hasRangeValue()).isTrue()
    }

    @Test
    fun toProto_preferenceScreenMetadata_includesValueDescriptorInSchema() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema {
            parameter("test_param", "test_purpose", required = true, type = AnyInt)
        }
        val params = schema.prepare("test_param" to "value")
        val screenMetadataWithParams = object : PreferenceScreenMetadata {
            override val bindingKey: String = "screen_key"
            override val key: String = "screen_key"
            override val purpose: Int = 0
            override fun fragmentClass(): Class<out Fragment>? = null
            override val keyParametersSchema: KeyParametersSchema = schema
            override val keyParameters: ValidatedKeyParameters = params

            override fun getPreferenceHierarchy(
                context: Context,
                coroutineScope: CoroutineScope
            ): PreferenceHierarchy = preferenceHierarchy(context) {}
        }

        val proto = screenMetadataWithParams.toProto(context, 0, 0, screenMetadata, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.hasParametersSchema()).isTrue()
        assertThat(proto.parametersSchema.parametersMap).containsKey("test_param")
        val paramProto = proto.parametersSchema.parametersMap["test_param"]!!
        assertThat(paramProto.hasValueDescriptor()).isTrue()
        assertThat(paramProto.valueDescriptor.hasRangeValue()).isTrue()
    }

    @Test
    fun toProto_isApiPreference_includesSetWarningWithoutPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        execute { 1 }
                    }
                    set {
                        warning {
                            warn("set_warning_with_preconditions_message")
                        }
                        execute {}
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.setWarning.preconditionsList).isEmpty()
        assertThat(proto.setWarning.warning).isEqualTo("set_warning_with_preconditions_message")
    }

    @Test
    fun toProto_isApiPreference_includesSetWarningWithPreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        execute { 1 }
                    }
                    set {
                        warning {
                            preconditions("set_warning_preconditions_desc") { Allowed }
                            warn("set_warning_with_preconditions_message")
                        }
                        execute {}
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.setWarning.preconditionsList).containsExactly("set_warning_preconditions_desc")
        assertThat(proto.setWarning.warning).isEqualTo("set_warning_with_preconditions_message")
    }

    @Test
    fun toProto_isApiPreference_includesSetWarningWithValuePreconditions() {
        val screen = object : PreferencesApiScreen(
            key = "test_api_screen",
            topLevelSettingsCategory = Category.SYSTEM,
            fragment = Fragment::class,
            purpose = 0,
        ) {
            init {
                preference(
                    key = "test_api_preference",
                    type = AnyInt,
                    purpose = 0,
                ) {
                    get {
                        execute { 1 }
                    }
                    set {
                        warning {
                            valuePreconditions("set_warning_value_preconditions_desc") { _ -> Allowed }
                            warn("set_warning_with_value_preconditions_message")
                        }
                        execute {}
                    }
                }
            }
        }
        val preference = screen.preferences.first()
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.setWarning.preconditionsList).containsExactly("set_warning_value_preconditions_desc")
        assertThat(proto.setWarning.warning).isEqualTo("set_warning_with_value_preconditions_message")
    }

    @Test
    fun toProto_standardPreference_inheritsKeyParametersAndSchemaFromScreen() {
        setCatalystUseKeyParameters(true)
        val schema = KeyParametersSchema {
            parameter("test_param", "test_purpose", required = true, type = AnyString)
        }
        val params = schema.prepare("test_param" to "value")

        val screenMetadataWithParams = object : PreferenceScreenMetadata {
            override val bindingKey: String = "screen_key"
            override val key: String = "screen_key"
            override val purpose: Int = 0
            override fun fragmentClass(): Class<out Fragment>? = null
            override val keyParametersSchema: KeyParametersSchema = schema
            override val keyParameters: ValidatedKeyParameters = params

            override fun getPreferenceHierarchy(
                context: Context,
                coroutineScope: CoroutineScope
            ): PreferenceHierarchy = preferenceHierarchy(context) {}
        }

        val preference = TestPreference(SensitivityLevel.NO_SENSITIVITY)
        val proto = preference.toProto(context, 0, 0, screenMetadataWithParams, false, PreferenceGetterFlags.METADATA)

        assertThat(proto.hasParametersSchema()).isTrue()
        assertThat(proto.hasKeyParameters()).isTrue()
        assertThat(proto.keyParameters.valuesMap).containsEntry("test_param", "value")
    }

    @Test
    fun toProto_legacyPreferenceWithPreferenceSetWarningProvider_includesSetWarningInfo() {
        val preference = TestPreference(SensitivityLevel.NO_SENSITIVITY)
        val proto = preference.toProto(context, 0, 0, screenMetadata, false, 0)

        assertThat(proto.setWarning.preconditionsList).containsExactly("set_warning_preconditions_description")
        assertThat(proto.setWarning.warning).isEqualTo("set_warning_message")
    }

    private class TestDiscreteIntPreference(
        override val sensitivityLevel: Int = SensitivityLevel.NO_SENSITIVITY,
    ) : PersistentPreference<Int>, com.android.settingslib.metadata.DiscreteIntValue {
        override val bindingKey: String = "discrete_int_key"
        override val valueType: Class<Int> = Int::class.javaObjectType
        override val key: String = "discrete_int_key"
        override val purpose: Int = 2737
        override val values: Int = android.R.array.emailAddressTypes
        override val valuesDescription: Int = android.R.array.emailAddressTypes

        override fun isPersistent(context: Context): Boolean = true
        override val supportsWrite: Boolean = true

        override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int): Int? =
            ReadWritePermit.ALLOW

        override fun getWritePermissions(context: Context): Permissions? = null
    }

    @Test
    fun toProto_discreteIntValue_includesValueDescriptor() {
        val preference = TestDiscreteIntPreference()
        val proto = preference.toProto(
            context,
            0,
            0,
            screenMetadata,
            false,
            PreferenceGetterFlags.METADATA or PreferenceGetterFlags.VALUE_DESCRIPTOR
        )

        assertThat(proto.hasValueDescriptor()).isTrue()
        assertThat(proto.valueDescriptor.possibleValuesList).isNotEmpty()
    }

    private class TestDiscreteTextPreference(
        override val sensitivityLevel: Int = SensitivityLevel.NO_SENSITIVITY,
    ) : PersistentPreference<CharSequence>, com.android.settingslib.metadata.DiscreteTextValue {
        override val bindingKey: String = "discrete_text_key"
        override val valueType: Class<CharSequence> = CharSequence::class.javaObjectType
        override val key: String = "discrete_text_key"
        override val purpose: Int = 2737
        override val values: Int = android.R.array.imProtocols
        override val valuesDescription: Int = android.R.array.imProtocols

        override fun isPersistent(context: Context): Boolean = true
        override val supportsWrite: Boolean = true

        override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int): Int? =
            ReadWritePermit.ALLOW

        override fun getWritePermissions(context: Context): Permissions? = null
    }

    @Test
    fun toProto_discreteTextValue_includesValueDescriptor() {
        val preference = TestDiscreteTextPreference()
        val proto = preference.toProto(
            context,
            0,
            0,
            screenMetadata,
            false,
            PreferenceGetterFlags.METADATA or PreferenceGetterFlags.VALUE_DESCRIPTOR
        )

        assertThat(proto.hasValueDescriptor()).isTrue()
        assertThat(proto.valueDescriptor.possibleValuesList).isNotEmpty()
    }

    private class TestDiscreteStringPreference(
        override val sensitivityLevel: Int = SensitivityLevel.NO_SENSITIVITY,
    ) : PersistentPreference<String>, com.android.settingslib.metadata.DiscreteStringValue {
        override val bindingKey: String = "discrete_string_key"
        override val valueType: Class<String> = String::class.javaObjectType
        override val key: String = "discrete_string_key"
        override val purpose: Int = 2737
        override val values: Int = android.R.array.imProtocols
        override val valuesDescription: Int = android.R.array.imProtocols

        override fun isPersistent(context: Context): Boolean = true
        override val supportsWrite: Boolean = true

        override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int): Int? =
            ReadWritePermit.ALLOW

        override fun getWritePermissions(context: Context): Permissions? = null
    }

    @Test
    fun toProto_discreteStringValue_includesValueDescriptor() {
        val preference = TestDiscreteStringPreference()
        val proto = preference.toProto(
            context,
            0,
            0,
            screenMetadata,
            false,
            PreferenceGetterFlags.METADATA or PreferenceGetterFlags.VALUE_DESCRIPTOR
        )

        assertThat(proto.hasValueDescriptor()).isTrue()
        assertThat(proto.valueDescriptor.possibleValuesList).isNotEmpty()
    }

    companion object {
        private const val SETTING_KEY = "com.android.settings.UNKNOWN_SENSITIVITY_IS_AVAILABLE"
    }
}
