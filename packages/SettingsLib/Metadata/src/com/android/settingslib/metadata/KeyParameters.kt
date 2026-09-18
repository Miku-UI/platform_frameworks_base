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
import androidx.annotation.StringRes
import com.android.settingslib.metadata.preferencesapi.types.ApiType
import com.android.settingslib.metadata.preferencesapi.types.FiniteOptionsType
import org.json.JSONObject
import com.android.settingslib.metadata.preferencesapi.SafetyAnnotated

/**
 * Holds an unvalidated set of key-value parameters. This class is a simple data container.
 *
 * @property values The validated map of parameter names to their string values.
 */
open class KeyParameters(
    open val values: Map<String, String>
) {
    /**
     * Returns `true` if this [KeyParameters] object contains no parameters.
     */
    val isEmpty
        get() = values.isEmpty()

    /**
     * Retrieves the value for a given parameter key.
     *
     * @param key The name of the parameter to retrieve.
     * @return The string value of the parameter, or `null` if the parameter does not exist.
     */
    open operator fun get(key: String): String? {
        return values[key]
    }

    /**
     * Converts this [KeyParameters] instance into an Android [Bundle].
     *
     * @return A new [Bundle] containing all the key-value pairs stored in this object.
     */
    fun toBundle() = values.toBundle()

    /**
     * Converts the key-value parameters into a string format suitable for persistence and parsing.
     * The format is `[key1=value1,key2=value2,...]`.
     */
    fun toParametersString() = values.toSerializableString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyParameters) return false
        return values == other.values
    }

    override fun hashCode(): Int {
        return values.hashCode()
    }
}

/**
 * Holds a validated set of key-value parameters based on a [KeyParametersSchema].
 *
 * This class is a simple data container. An instance of this class is guaranteed to be valid
 * because it can only be created by the [KeyParametersSchema.prepare] method, which performs
 * all necessary validation.
 *
 * @property schema The schema that this set of parameters conforms to.
 * @property values The validated map of parameter names to their string values.
 */
@ConsistentCopyVisibility
data class ValidatedKeyParameters constructor(
    private val schema: KeyParametersSchema,
    override val values: Map<String, String>
) : KeyParameters(values) {

    /**
     * Retrieves the value for a given parameter key.
     *
     * @param key The name of the parameter to retrieve.
     * @return The string value of the parameter, or `null` if the parameter is optional and was
     * not provided.
     * @throws IllegalArgumentException if the key is not defined in the schema.
     */
    override operator fun get(key: String): String? {
        if (!schema.containsKey(key)) {
            throw IllegalArgumentException("Parameter '$key' is not defined in the schema (contains ${schema.getParameters().keys}).")
        }
        return values[key]
    }

    /**
     * Retrieves the value for a given parameter key in the correct type.
     *
     * This will ultimately replace the get<String> method.
     *
     * @param key The name of the parameter to retrieve.
     * @return The value of the parameter, or `null` if the parameter is optional and was not
     * provided.
     * @throws IllegalArgumentException if the key is not defined in the schema.
     */
    fun <T> getTyped(key: String): T? {
        val value = get(key) ?: return null
        val type = schema.getParameters()[key]?.type as ApiType<T, *>? ?: return null
        return type.convertStringToInternal(value)
    }

    /**
     * Retrieves the value for a parameter that is explicitly defined as **required** in the schema.
     *
     * This method enforces that the parameter was declared with `required = true` in the
     * [KeyParametersSchema]. It is a stricter version of [get] and should be used when the schema
     * itself guarantees the presence of a value.
     *
     * @param key The name of the required parameter to retrieve.
     * @return The name of the parameter.
     * @throws IllegalArgumentException if the key is not defined in the schema, or if the parameter
     * is not marked as `required` in the schema.
     * @throws IllegalStateException if the value is unexpectedly null, which would indicate a bug
     * in the validation logic.
     */
    fun getRequired(key: String): String {
        if (!schema.containsKey(key)) {
            throw IllegalArgumentException("Parameter '$key' is not defined in the schema. (contains ${schema.getParameters().keys})")
        }

        if (!schema.isRequiredParameter(key)) {
            throw IllegalArgumentException("Parameter '$key' is not defined as required in the schema.")
        }

        return values[key] ?: error("Value for required parameter '$key' was null.")
    }

    companion object {
        val EMPTY = ValidatedKeyParameters(KeyParametersSchema { }, emptyMap())
    }
}

/**
 * Defines the schema for a set of parameters, including their names, purposes, and validation rules.
 * This class acts as a factory for creating validated [ValidatedKeyParameters] instances.
 *
 * Use the DSL function `KeyParametersSchema { ... }` to create an instance.
 *
 * @param schema A map from a parameter name to its definition.
 */
class KeyParametersSchema private constructor(
    private val schema: Map<String, ParameterDefinition>
) {
    /**
     * Holds the definition and rules for a single parameter within the schema.
     *
     * @property name The unique name of the parameter.
     * @property purpose The string resource of a human-readable purpose of what the parameter is for.
     * @property required If `true`, this parameter must be provided when creating [ValidatedKeyParameters].
     * @property type The type of the parameter, used to generate its possible values.
     */
    data class ParameterDefinition(
        val name: String,
        @StringRes val purpose: Int,
        val required: Boolean,
        val type: ApiType<*, *>
    ) {
        constructor(
            name: String,
            description: String,
            required: Boolean,
            type: ApiType<*, *>,
        ) : this(name, description.hashCode(), required, type) {
            purposeHashMap[description.hashCode()] = description
        }

        // TODO (b/468973102): remove this when all current parameterized screens migrated to string res purpose
        private val purposeHashMap = hashMapOf<Int, String>()

        /**
         * Returns a map representation of the parameter definition, suitable for serialization.
         *
         * @param context The [Context] used to resolve the string resource for the `purpose` resource id.
         * @return A map containing the resolved purpose string and the `required` flag.
         */
        fun toParameterSchemaMap(context: Context): Map<String, Any> {
            return mapOf(
                PURPOSE_KEY to (purposeHashMap[purpose] ?: context.getString(purpose)),
                REQUIRED_KEY to required
            )
        }

        /**
         * Returns a map representation of the parameter definition with an unresolved purpose.
         */
        internal fun toUnresolvedParameterSchemaMap(): Map<String, Any> {
            return mapOf(
                PURPOSE_KEY to purpose,
                REQUIRED_KEY to required
            )
        }

        companion object {
            const val PURPOSE_KEY = "purpose"
            const val REQUIRED_KEY = "required"
        }
    }

    /**
     * A builder for constructing a [KeyParametersSchema] using a DSL-style syntax.
     */
    class Builder {
        private val parameters = mutableMapOf<String, ParameterDefinition>()

        /**
         * Defines a parameter and adds it to the schema.
         *
         * @param name The unique name for the parameter.
         * @param purpose The string resource if of a human-readable purpose of the parameter.
         * @param required Whether this parameter must be provided. Defaults to `false`.
         * @param type The type of the parameter, used to generate its possible values.
         * @throws IllegalArgumentException if a parameter with the same name is already defined.
         */
        fun parameter(name: String, @StringRes purpose: Int, required: Boolean = false, type: ApiType<*, *>): Builder {
            if (parameters.containsKey(name)) {
                throw IllegalArgumentException("Parameter '$name' is already defined.")
            }
            parameters[name] = ParameterDefinition(name, purpose, required, type)
            return this
        }

        // TODO (b/468973102): remove this when all current parameterized screens migrated to string res purpose
        fun parameter(name: String, purpose: String, required: Boolean = false, type: ApiType<*, *>): Builder {
            if (parameters.containsKey(name)) {
                throw IllegalArgumentException("Parameter '$name' is already defined.")
            }

            parameters[name] = ParameterDefinition(name, purpose, required, type)
            return this
        }

        /**
         * Builds and returns the final [KeyParametersSchema] instance. For internal use by the DSL.
         */
        internal fun build(): KeyParametersSchema = KeyParametersSchema(parameters.toMap())
    }

    /**
     * Creates a validated [ValidatedKeyParameters] instance from a map of provided values.
     *
     * This method checks the provided values against the schema rules, ensuring that all required
     * parameters are present. Unknown parameters are ignored.
     *
     * @param providedValues A map of parameter names to their string values.
     * @return A validated [ValidatedKeyParameters] instance.
     * @throws IllegalArgumentException if a required parameter is missing.
     */
    fun prepare(providedValues: Map<String, String>): ValidatedKeyParameters {
        val finalValues = mutableMapOf<String, String>()

        // Validate provided values and check for required parameters
        schema.forEach { (name, def) ->
            val value = providedValues[name]
            if (value != null) {
                finalValues[name] = value
            } else if (def.required) {
                throw IllegalArgumentException("Required parameter '$name' is missing.")
            }
        }
        return ValidatedKeyParameters(this, finalValues)
    }

    /**
     * A convenience method to create a validated [ValidatedKeyParameters] instance from a `vararg` of [Pair]s.
     *
     * @see prepare(providedValues: Map<String, String>)
     */
    fun prepare(vararg values: Pair<*, *>): ValidatedKeyParameters{
        val valuesMap = mutableMapOf<String, String>()
        for ((key, value) in values) {
            val keyString = if (key is SafetyAnnotated<*>) {
                key.value.toString()
            } else if (key is String) {
                key
            } else {
                error("Key is not a string or SafetyAnnotated<String>")
            }
            val valueString = if (value is SafetyAnnotated<*>) {
                value.value.toString()
            } else if (value is String) {
                value
            } else {
                error("Value is not a string or SafetyAnnotated<String>")
            }

            valuesMap[keyString] = valueString
        }

        return prepare(valuesMap)
    }

    /**
     * A convenience method to create a validated [ValidatedKeyParameters] instance from a [Bundle].
     *
     * @see prepare(providedValues: Map<String, String>)
     */
    fun prepare(bundle: Bundle): ValidatedKeyParameters {
        val providedValues = bundle.keySet().mapNotNull { key ->
            bundle.getString(key)?.let { value -> key to value }
        }.toMap()
        return prepare(providedValues)
    }

    /**
     * A convenience method to create a validated [ValidatedKeyParameters] instance from an
     * [KeyParameters].
     *
     * @see prepare(providedValues: Map<String, String>)
     */
    fun prepare(keyParameters: KeyParameters) = prepare(keyParameters.values)

    /**
     * Creates a new [ValidatedKeyParameters] instance by updating an existing one with new values.
     *
     * This method takes an existing, valid [ValidatedKeyParameters] object, merges it with the
     * new values, and then re-validates the entire set to produce a new, immutable object.
     *
     * @param existing The original [ValidatedKeyParameters] object.
     * @param newValues A map of the new key-value pairs to apply.
     * @return A new, validated [ValidatedKeyParameters] instance.
     * @throws IllegalArgumentException if validation fails.
     */
    fun prepareWith(existing: ValidatedKeyParameters?, newValues: Map<String, String>): ValidatedKeyParameters {
        val currentValues = existing?.values?.toMutableMap() ?: mutableMapOf()
        currentValues.putAll(newValues)

        return prepare(currentValues)
    }

    /**
     * A convenience overload for updating with a single key-value pair.
     */
    fun prepareWith(existing: ValidatedKeyParameters?, vararg newValues: Pair<String, String>): ValidatedKeyParameters {
        return prepareWith(existing, newValues.toMap())
    }

    /**
     * Creates a validated [ValidatedKeyParameters] instance from a string representation.
     *
     * The expected format is `[key1=value1,key2=value2,...]`. This method parses the string
     * and then validates the resulting key-value pairs against the schema.
     *
     * @param parametersString The string representation of the parameters.
     * @return A validated [ValidatedKeyParameters] instance.
     * @throws IllegalArgumentException if the string is malformed or if validation against
     * the schema fails.
     */
    fun prepare(parametersString: String): ValidatedKeyParameters {
        return prepare(parametersString.deserializeToMap())
    }

    /**
     * Creates an empty [ValidatedKeyParameters] instance.
     *
     * This method is primarily used for backward compatibility when a preference screen
     * transitions from being non-parameterized to parameterized. In such migration scenarios,
     * the parameterized screen is expected to gracefully accept and handle empty [ValidatedKeyParameters]
     * to ensure compatibility with older configurations or entry points.
     */
    fun prepareEmpty(): ValidatedKeyParameters {
        // This skips the required check as it is used when there are no
        // parameters.
        return ValidatedKeyParameters(this, mapOf())
    }

    /**
     * Returns the map of parameter definitions.
     */
    fun getParameters(): Map<String, ParameterDefinition> = schema

    /**
     * Checks if a parameter key is defined in this schema.
     */
    fun containsKey(key: String): Boolean = schema.containsKey(key)

    /**
     * Checks if a parameter is defined as required in the schema.
     *
     * @param key The name of the parameter to check.
     * @return `true` if the parameter exists in the schema and is marked as required,
     * `false` otherwise.
     */
    internal fun isRequiredParameter(key: String): Boolean {
        return schema[key]?.required ?: false
    }

    /**
     * Returns a JSON string representation of the schema.
     *
     * This method serializes the entire schema, including the names of all defined parameters,
     * their resolved human-readable purposes, and whether they are required. The resulting JSON
     * is suitable for clients to understand the parameterization.
     *
     * The format is a JSON string like:
     * `{"key1":{"purpose":"The purpose of this parameter","required":true},...}`
     *
     * @param context The [Context] used to resolve the string resources for the parameter purposes.
     * @return A JSON string representing the complete schema.
     */
    fun toJsonString(context: Context): String {
        val schemaMap = schema.mapValues { it.value.toParameterSchemaMap(context) }
        return JSONObject(schemaMap).toString()
    }

    /**
     * Returns a JSON string representation of the schema with unresolved `purpose` resource IDs.
     *
     * The format is a JSON string like:
     * `{"key1":{"purpose":2737,"required":true},...}`
     *
     * @return A JSON string representing the complete schema.
     */
    fun toUnresolvedJsonString(): String {
        val schemaMap = schema.mapValues { it.value.toUnresolvedParameterSchemaMap() }
        return JSONObject(schemaMap).toString()
    }

    override fun toString() = "KeyParametersSchema(schema: ${toUnresolvedJsonString()})"
}

/**
 * A DSL entry point for creating a [KeyParametersSchema] in a clean and readable way.
 *
 * Example:
 * ```
 * val mySchema = KeyParametersSchema {
 *     parameter("pkg", R.string.purpose_package_name, required = true)
 * }
 * ```
 */
fun KeyParametersSchema(block: KeyParametersSchema.Builder.() -> Unit): KeyParametersSchema {
    val builder = KeyParametersSchema.Builder()
    block(builder)
    return builder.build()
}

// Should we have here a unified place to store the keys?
const val KEY_PACKAGE_NAME = "pkg"

/**
 * Convenience method to prepare KeyParameters with a single application package name.
 * Assumes the schema includes the KEY_PACKAGE_NAME parameter.
 *
 * @param packageName The package name value.
 * @return Validated KeyParameters.
 */
fun KeyParametersSchema.prepareForApp(packageName: String): ValidatedKeyParameters {
    return prepare(KEY_PACKAGE_NAME to packageName)
}

/**
 * Convenience method to retrieve the package name from a KeyParameters.
 */
val ValidatedKeyParameters.packageName: String?
    get() = get(KEY_PACKAGE_NAME)
