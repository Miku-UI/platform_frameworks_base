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

/**
 * Marks a class as being a parameterized preference.
 *
 * Any class implementing this interface is expected to hold the [ValidatedKeyParameters] that were used
 * to configure and create it.
 */
interface ParameterizedPreference {
    /**
     * The validated set of parameters that this preference instance was created with.
     */
    val parameters: ValidatedKeyParameters
}

/**
 * A generic factory for creating instances of a [ParameterizedPreference].
 *
 * This interface provides a standardized way for parameterized preference to define the parameter
 * requirements and instantiate a preference object using those parameters.
 *
 * Example Usage:
 * ```
 * // In a companion object of a preference class that implements ParameterizedPreference
 * companion object : ParameterizedPreferenceFactory<MyPreference> {
 *     override val parametersSchema = KeyParametersSchema {
 *         parameter("id", "The unique identifier", required = true)
 *     }
 *
 *     // Assign the constructor reference directly to the creator property.
 *     override val creator = ::MyPreference
 * }
 *
 * // Instantiation is clean and function-like
 * val preference = MyPreference("id" to "user_123")
 * ```
 *
 * @param T The type of [ParameterizedPreference] this factory creates.
 */
interface ParameterizedPreferenceFactory<T : ParameterizedPreference> {
    /**
     * The schema for the parameters required to create an instance of [T].
     */
    val parametersSchema: KeyParametersSchema

    /**
     * The function that creates an instance of [T] from a validated set of parameters.
     *
     * It is typically implemented by assigning the constructor reference.
     *
     * The function takes a [ValidatedKeyParameters] object that is guaranteed to have been successfully
     * validated against the [parametersSchema].
     */
    val creator: (ValidatedKeyParameters) -> T

    /**
     * A convenience operator that validates a map of parameters and creates an instance of [T].
     *
     * This method allows for a concise instantiation syntax. It validates the input against the
     * [parametersSchema] and then invokes the [creator] with the result.
     *
     * @param parameters A map of parameter names to their string values.
     * @return A new instance of [T].
     * @throws IllegalArgumentException if the provided parameters do not conform to the schema.
     */
    operator fun invoke(parameters: Map<String, String>) = creator(parametersSchema.prepare(parameters))

    /**
     * A convenience operator that validates a map of parameters and creates an instance of [T].
     *
     * This method allows for a concise instantiation syntax. It validates the input against the
     * [parametersSchema] and then invokes the [creator] with the result.
     *
     * @param parameters A map of parameter names to their string values.
     * @return A new instance of [T].
     * @throws IllegalArgumentException if the provided parameters do not conform to the schema.
     */
    operator fun invoke(vararg values: Pair<String, String>) = invoke(values.toMap())
}