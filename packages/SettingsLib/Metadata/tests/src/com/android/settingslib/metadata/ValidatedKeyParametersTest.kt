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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.settingslib.metadata.preferencesapi.types.AnyString
import com.android.settingslib.metadata.test.R
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValidatedKeyParametersTest {

    private val testSchema = KeyParametersSchema {
        parameter("required_param", R.string.required_param_purpose, required = true, type = AnyString)
        parameter("optional_param", R.string.optional_param_purpose, type = AnyString)
    }

    private val optionalSchema = KeyParametersSchema {
        parameter("optional_param", R.string.optional_param_purpose, type = AnyString)
    }

    @Test
    fun schemaBuilder_duplicateParameter_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            KeyParametersSchema {
                parameter("param1", R.string.parameter_purpose1, type = AnyString)
                parameter("param1", R.string.parameter_purpose2, type = AnyString)
            }
        }
        assertThat(exception.message).isEqualTo("Parameter 'param1' is already defined.")
    }

    @Test
    fun schemaBuilder_noParameters() {
        val emptySchema = KeyParametersSchema {}
        assertThat(emptySchema.toString()).isEqualTo("KeyParametersSchema(schema: {})")
    }

    @Test
    fun prepare_validParameters_succeeds() {
        val params = testSchema.prepare(
            mapOf(
                "required_param" to "value1",
                "optional_param" to "value2"
            )
        )
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isEqualTo("value2")
    }

    @Test
    fun prepare_onlyRequiredParameters_succeeds() {
        val params = testSchema.prepare(
            mapOf("required_param" to "value1")
        )
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun prepare_missingRequiredParameter_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            testSchema.prepare(mapOf("optional_param" to "value2"))
        }
        assertThat(exception.message).isEqualTo("Required parameter 'required_param' is missing.")
    }

    @Test
    fun prepare_unknownParameter_isIgnored() {
        val params = testSchema.prepare(
            mapOf(
                "required_param" to "value1",
                "unknown_param" to "value3"
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            // The unknown parameter is ignored during prepare, but trying to access it throws.
            params.get("unknown_param")
        }
    }

    @Test
    fun prepare_varargs_succeeds() {
        val params = testSchema.prepare(
            "required_param" to "value1",
            "optional_param" to "value2"
        )
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isEqualTo("value2")
    }

    @Test
    fun prepareFromBundle_validBundle_succeeds() {
        val bundle = Bundle().apply {
            putString("required_param", "bundle_value1")
            putString("optional_param", "bundle_value2")
        }
        val params = testSchema.prepare(bundle)
        assertThat(params.get("required_param")).isEqualTo("bundle_value1")
        assertThat(params.get("optional_param")).isEqualTo("bundle_value2")
    }

    @Test
    fun prepareFromBundle_onlyRequired_succeeds() {
        val bundle = Bundle().apply {
            putString("required_param", "bundle_value1")
        }
        val params = testSchema.prepare(bundle)
        assertThat(params.get("required_param")).isEqualTo("bundle_value1")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun prepareFromBundle_missingRequired_throwsException() {
        val bundle = Bundle().apply {
            putString("optional_param", "bundle_value2")
        }
        val exception = assertThrows(IllegalArgumentException::class.java) {
            testSchema.prepare(bundle)
        }
        assertThat(exception.message).isEqualTo("Required parameter 'required_param' is missing.")
    }

    @Test
    fun prepareFromBundle_unknownParameter_isIgnored() {
        val bundle = Bundle().apply {
            putString("required_param", "bundle_value1")
            putString("unknown_param", "unknown")
        }
        val params = testSchema.prepare(bundle)
        assertThrows(IllegalArgumentException::class.java) {
            params.get("unknown_param")
        }
    }

    @Test
    fun prepareFromBundle_nullValueInBundle_isSkipped() {
        val bundle = Bundle().apply {
            putString("required_param", "bundle_value1")
            putString("optional_param", null) // This should be skipped
        }
        val params = testSchema.prepare(bundle)
        assertThat(params.get("required_param")).isEqualTo("bundle_value1")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun prepareFromBundle_emptyBundle_throwsForRequired() {
        val bundle = Bundle()
        val exception = assertThrows(IllegalArgumentException::class.java) {
            testSchema.prepare(bundle)
        }
        assertThat(exception.message).isEqualTo("Required parameter 'required_param' is missing.")
    }

    @Test
    fun prepareFromBundle_emptyBundle_succeedsForOptional() {
        val bundle = Bundle()
        val params = optionalSchema.prepare(bundle)
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun get_existingKey_returnsValue() {
        val params = testSchema.prepare("required_param" to "value1")
        assertThat(params.get("required_param")).isEqualTo("value1")
    }

    @Test
    fun get_optionalKeyNotProvided_returnsNull() {
        val params = testSchema.prepare("required_param" to "value1")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun get_unknownKey_throwsException() {
        val params = testSchema.prepare("required_param" to "value1")
        val exception = assertThrows(IllegalArgumentException::class.java) {
            params.get("unknown_key")
        }
        assertThat(exception.message).isEqualTo("Parameter 'unknown_key' is not defined in the schema.")
    }

    @Test
    fun getRequired_existingRequiredKey_returnsValue() {
        val params = testSchema.prepare("required_param" to "value1")
        assertThat(params.getRequired("required_param")).isEqualTo("value1")
    }

    @Test
    fun getRequired_unknownKey_throwsException() {
        val params = testSchema.prepare("required_param" to "value1")
        val exception = assertThrows(IllegalArgumentException::class.java) {
            params.getRequired("unknown_key")
        }
        assertThat(exception.message).isEqualTo("Parameter 'unknown_key' is not defined in the schema.")
    }

    @Test
    fun getRequired_optionalKey_throwsException() {
        val params = testSchema.prepare(
            "required_param" to "value1",
            "optional_param" to "value2"
        )
        val exception = assertThrows(IllegalArgumentException::class.java) {
            params.getRequired("optional_param")
        }
        assertThat(exception.message).isEqualTo("Parameter 'optional_param' is not defined as required in the schema.")
    }

    @Test
    fun schema_isRequiredParameter_nonExistentKey() {
        assertThat(testSchema.isRequiredParameter("non_existent")).isFalse()
    }

    @Test
    fun schema_containsKey_nonExistentKey() {
        assertThat(testSchema.containsKey("non_existent")).isFalse()
    }

    @Test
    fun schema_containsKey_existingKey() {
        assertThat(testSchema.containsKey("required_param")).isTrue()
        assertThat(testSchema.containsKey("optional_param")).isTrue()
    }

    @Test
    fun prepareFromString_validString_succeeds() {
        val params = testSchema.prepare("[required_param=value1,optional_param=123]")
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isEqualTo("123")
    }

    @Test
    fun prepareFromString_validStringWithWhitespace_succeeds() {
        val params = testSchema.prepare("[ required_param = value1 ,  optional_param = 123 ]")
        assertThat(params.get("required_param")).isEqualTo("value1")
        assertThat(params.get("optional_param")).isEqualTo("123")
    }

    @Test
    fun prepareFromString_valueContainingEquals_succeeds() {
        val schema = KeyParametersSchema {
            parameter("url", R.string.parameter_purpose1, required = true, type = AnyString)
        }
        val params = schema.prepare("[url=http://example.com?a=1&b=2]")
        assertThat(params.get("url")).isEqualTo("http://example.com?a=1&b=2")
    }

    @Test
    fun prepareFromString_emptyContent_failsWithRequiredParam() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            testSchema.prepare("[]")
        }
        assertThat(exception.message).isEqualTo("Required parameter 'required_param' is missing.")
    }

    @Test
    fun prepareFromString_emptyContent_succeedsWithOptionalSchema() {
        val params = optionalSchema.prepare("[]")
        assertThat(params.get("optional_param")).isNull()
    }

    @Test
    fun prepareFromString_missingOpeningBracket_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            testSchema.prepare("required_param=value1]")
        }
        assertThat(exception.message).isEqualTo("String must be enclosed in brackets [].")
    }

    @Test
    fun prepareFromString_missingClosingBracket_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            testSchema.prepare("[required_param=value1")
        }
        assertThat(exception.message).isEqualTo("String must be enclosed in brackets [].")
    }

    @Test
    fun prepareFromString_malformedPair_noEquals_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            testSchema.prepare("[required_param:value1]")
        }
        assertThat(exception.message).isEqualTo("Malformed key-value pair: 'required_param:value1'")
    }

    @Test
    fun prepareFromString_emptyKey_throwsException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            testSchema.prepare("[=value,required_param=app]")
        }
        assertThat(exception.message).isEqualTo("Key cannot be empty in pair: '=value'")
    }

    @Test
    fun equals_sameSchemaAndValues_isTrue() {
        val params1 = testSchema.prepare("required_param" to "value1", "optional_param" to "value2")
        val params2 = testSchema.prepare("required_param" to "value1", "optional_param" to "value2")
        assertThat(params1).isEqualTo(params2)
    }

    @Test
    fun equals_differentSchema_isFalse() {
        val params1 = testSchema.prepare("required_param" to "value1")
        val params2 = optionalSchema.prepare("optional_param" to "value1")
        assertThat(params1).isNotEqualTo(params2)
    }

    @Test
    fun equals_differentValues_isFalse() {
        val params1 = testSchema.prepare("required_param" to "value1")
        val params2 = testSchema.prepare("required_param" to "value2")
        assertThat(params1).isNotEqualTo(params2)
    }

    @Test
    fun equals_differentOrder_isTrue() {
        val params1 = testSchema.prepare("optional_param" to "value2", "required_param" to "value1")
        val params2 = testSchema.prepare("required_param" to "value1", "optional_param" to "value2")
        assertThat(params1).isEqualTo(params2)
    }

    @Test
    fun hashCode_sameSchemaAndValues_isEqual() {
        val params1 = testSchema.prepare("required_param" to "value1", "optional_param" to "value2")
        val params2 = testSchema.prepare("required_param" to "value1", "optional_param" to "value2")
        assertThat(params1.hashCode()).isEqualTo(params2.hashCode())
    }

    @Test
    fun hashCode_differentValues_isNotEqual() {
        val params1 = testSchema.prepare("required_param" to "value1")
        val params2 = testSchema.prepare("required_param" to "value2")
        assertThat(params1.hashCode()).isNotEqualTo(params2.hashCode())
    }

    @Test
    fun hashCode_differentOrder_isEqual() {
        val params1 = testSchema.prepare("optional_param" to "value2", "required_param" to "value1")
        val params2 = testSchema.prepare("required_param" to "value1", "optional_param" to "value2")
        assertThat(params1.hashCode()).isEqualTo(params2.hashCode())
    }

    @Test
    fun prepareWith_addNewValue_succeeds() {
        val initialParams = testSchema.prepare("required_param" to "value1")
        val newParams = testSchema.prepareWith(initialParams, "optional_param" to "newValue")
        assertThat(newParams["required_param"]).isEqualTo("value1")
        assertThat(newParams["optional_param"]).isEqualTo("newValue")
    }

    @Test
    fun prepareWith_updateExistingValue_succeeds() {
        val initialParams = testSchema.prepare("required_param" to "value1", "optional_param" to "oldValue")
        val newParams = testSchema.prepareWith(initialParams, "optional_param" to "newValue")
        assertThat(newParams["required_param"]).isEqualTo("value1")
        assertThat(newParams["optional_param"]).isEqualTo("newValue")
    }

    @Test
    fun prepareWith_varargs_succeeds() {
        val initialParams = testSchema.prepare("required_param" to "value1")
        val newParams = testSchema.prepareWith(initialParams, "optional_param" to "newValue", "required_param" to "updatedValue")
        assertThat(newParams["required_param"]).isEqualTo("updatedValue")
        assertThat(newParams["optional_param"]).isEqualTo("newValue")
    }

    @Test
    fun toJsonString_returnsCorrectJson() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schemaString = testSchema.toJsonString(context)
        val json = JSONObject(schemaString)

        assertThat(json.has("required_param")).isTrue()
        val requiredParam = json.getJSONObject("required_param")
        assertThat(requiredParam.getString("purpose")).isEqualTo(context.getString(R.string.required_param_purpose))
        assertThat(requiredParam.getBoolean("required")).isTrue()

        assertThat(json.has("optional_param")).isTrue()
        val optionalParam = json.getJSONObject("optional_param")
        assertThat(optionalParam.getString("purpose")).isEqualTo(context.getString(R.string.optional_param_purpose))
        assertThat(optionalParam.getBoolean("required")).isFalse()
    }

    @Test
    fun toUnresolvedJsonString_returnsCorrectJson() {
        val schemaString = testSchema.toUnresolvedJsonString()
        val json = JSONObject(schemaString)

        assertThat(json.has("required_param")).isTrue()
        val requiredParam = json.getJSONObject("required_param")
        assertThat(requiredParam.getInt("purpose")).isEqualTo(R.string.required_param_purpose)
        assertThat(requiredParam.getBoolean("required")).isTrue()

        assertThat(json.has("optional_param")).isTrue()
        val optionalParam = json.getJSONObject("optional_param")
        assertThat(optionalParam.getInt("purpose")).isEqualTo(R.string.optional_param_purpose)
        assertThat(optionalParam.getBoolean("required")).isFalse()
    }

    @Test
    fun emptyObject_isEmpty() {
        assertThat(optionalSchema.prepareEmpty().isEmpty).isTrue()
    }

    @Test
    fun prepareForApp_createsCorrectParameters() {
        val schema = KeyParametersSchema {
            parameter(KEY_PACKAGE_NAME, R.string.parameter_pkg_purpose, required = true, type = AnyString)
        }
        val params = schema.prepareForApp("com.example.app")
        assertThat(params[KEY_PACKAGE_NAME]).isEqualTo("com.example.app")
    }

    @Test
    fun getPackageName_returnsCorrectValue() {
        val schema = KeyParametersSchema {
            parameter(KEY_PACKAGE_NAME, R.string.parameter_pkg_purpose, required = true, type = AnyString)
        }
        val params = schema.prepareForApp("com.example.app")
        assertThat(params.packageName).isEqualTo("com.example.app")
    }
}
