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

package com.android.settingslib.metadata.preferencesapi.types

import android.content.Context
import androidx.annotation.StringRes
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.preferencesapi.resolveString
import com.android.settingslib.metadata.preferencesapi.safe
import com.android.settingslib.metadata.preferencesapi.SafetyAnnotated
import com.android.settingslib.metadata.preferencesapi.types.EType

/**
 * The context for a GeneratedType, providing access to the [Context] and any necessary
 * environmental information for executing the lambda.
 *
 * @property context The application context, used for accessing system services and resources.
 */
class GeneratedTypeContext(val context: Context)

/**
 * Represents a single possible value for a [GeneratedType].
 *
 * Each result type provides a specific value and a human-readable description,
 * which can be used by clients to better understand the value.
 *
 * For example, if the value represents the UUID of an app, then the description should be the
 * app package name or the app name.
 *
 * @param T The underlying data type of the value.
 * @property value The actual value.
 * @property description A human-readable description of this specific value.
 */
data class GeneratedValue<T>(
    val value: SafetyAnnotated<T>,
    val description: SafetyAnnotated<String>,
)

inline fun <reified T : Any> GeneratedType(
    @StringRes description: Int,
    unit: String? = null,
    key: String? = null,
    noinline lambda: GeneratedTypeContext.() -> Collection<GeneratedValue<T>>
): GeneratedType<T> {
    val externalType = when (T::class) {
        Int::class -> EType.Int
        String::class -> EType.String
        Boolean::class -> EType.Boolean
        else -> error("Unsupported external type.")
    } as EType<T>
    return GeneratedType(externalType, descriptionRes = description, description = null, unit = unit, key = key, lambda = lambda)
}

inline fun <reified T : Any> GeneratedType(
        description: String,
        unit: String? = null,
        key: String? = null,
    noinline lambda: GeneratedTypeContext.() -> Collection<GeneratedValue<T>>
): GeneratedType<T> {
    val externalType = when (T::class) {
        Int::class -> EType.Int
        String::class -> EType.String
        Boolean::class -> EType.Boolean
        else -> error("Unsupported external type.")
    } as EType<T>
    return GeneratedType(externalType, descriptionRes = null, description = description, unit = unit, key = key, lambda = lambda)
}

inline fun GeneratedParameterType(
    @StringRes description: Int,
    unit: String? = null,
    key: String? = null,
    noinline lambda: GeneratedTypeContext.() -> Collection<GeneratedValue<String>>
): GeneratedType<String> = GeneratedType(EType.String, descriptionRes = description, description = null, unit = unit, key = key, lambda = lambda)

inline fun GeneratedParameterType(
        description: String,
        unit: String? = null,
        key: String? = null,
    noinline lambda: GeneratedTypeContext.() -> Collection<GeneratedValue<String>>
): GeneratedType<String> = GeneratedType(EType.String, descriptionRes = null, description = description, unit = unit, key = key, lambda = lambda)


/**
 * DO NOT CONSTRUCT THIS CLASS DIRECTLY.
 *
 * Instead, use the inline functions [GeneratedType] which provide a more convenient syntax and
 * automatically infer the type.
 */
class GeneratedType<ExternalType : Any> constructor(
    override val externalType: EType<ExternalType>,
    @field:StringRes val descriptionRes: Int?,
    val description: String?,
    private val lambda: GeneratedTypeContext.() -> Collection<GeneratedValue<ExternalType>>,
    private val unit: String? = null,
    private val key: String? = null,
) : DirectFiniteOptionsType<ExternalType> {
    init {
        require(descriptionRes != null || description != null)
    }

    override fun getParametersSchema() = KeyParametersSchema.Builder()
        .parameter("unit", "The unit of measurement (if any) such as dB or milliseconds.", type = AnyString)
        .build()

    override fun getParameters() = getParametersSchema().prepare(buildMap {
        unit?.let { put("unit", it) }
    })

    /** Get the description as a string using the provided context. */
    override fun getDescription(context: Context): String =
        resolveString(context, descriptionRes, description)

    override suspend fun getOptions(context: Context) = lambda(GeneratedTypeContext(context)).map{
        it.value to it.description
    }

    override fun getKey(): String = key ?: "GeneratedType:${descriptionRes ?: description?.hashCode() ?: 0}"
}