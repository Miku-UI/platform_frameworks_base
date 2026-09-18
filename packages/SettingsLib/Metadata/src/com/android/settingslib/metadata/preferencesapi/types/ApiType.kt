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
import com.android.settingslib.metadata.KeyParametersSchema
import com.android.settingslib.metadata.ValidatedKeyParameters
import com.android.settingslib.metadata.preferencesapi.types.EType

/**
 * A type that can be used to define the type of a preference or parameter.
 */
interface ApiType<InternalType, ExternalType : Any> {

    /**
     * Returns the schema for the parameters of the type.
     *
     * This allows us to specify, for example, the range of allowed values for
     * an integer, or filters that must be applied to possible values.
     */
    fun getParametersSchema(): KeyParametersSchema? = null
    fun getParameters(): ValidatedKeyParameters? = null

    val externalType: EType<ExternalType>

    /**
     * Returns the type of the preference.
     */
    fun getType(): Class<ExternalType> = externalType.clazz

    /**
     * Returns the description of the type.
     *
     * This must describe what the type is and what values are allowed.
     */
    fun getDescription(context: Context): String

    /**
     * Returns a globally unique key which refers to this type. It must be
     * stable through multiple executions as long as we don't update the app.
     */
    fun getKey(): String

    fun convertInternalToExternal(internalValue: InternalType): ExternalType
    fun convertExternalToInternal(externalValue: ExternalType): InternalType
    fun convertStringToInternal(value: String): InternalType = convertExternalToInternal(externalType.fromString(value))
}

/** ApiType for types which have the same internal and external type. */
interface DirectApiType<ExternalType : Any> : ApiType<ExternalType, ExternalType> {
    override fun convertInternalToExternal(internalValue: ExternalType): ExternalType = internalValue
    override fun convertExternalToInternal(externalValue: ExternalType): ExternalType = externalValue
}