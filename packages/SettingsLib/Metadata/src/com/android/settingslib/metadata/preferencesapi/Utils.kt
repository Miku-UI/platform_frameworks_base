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
import androidx.annotation.StringRes

/**
 * A utility object for creating standardized exception messages for the API-first approach.
 *
 * This object centralizes common error message templates to ensure that validation errors
 * (e.g. incorrect block order, duplicate definitions) are reported consistently across the module.
 */
internal object Utils {
    private const val EXCEPTION_MESSAGE_WRONG_ORDER = "%s block was defined in wrong order"
    private const val EXCEPTION_MESSAGE_MULTIPLE_DEFINES =
        "%s block should be defined only once per screen"

    private const val EXCEPTION_MESSAGE_ALREADY_DEFINED =
        "%s block has already been defined"

    const val EXCEPTION_MESSAGE_NO_PARAMETER_DEFINED =
        "no parameter has been defined for this screen"

    private const val EXCEPTION_MESSAGE_MULTIPLE_PARAMETERS_DEFINED =
        "only one parameters expected, but %s found"

    fun getExceptionMessageWrongOrder(item: String) =
        String.format(EXCEPTION_MESSAGE_WRONG_ORDER, item)

    fun getExceptionMessageMultipleDefines(item: String) =
        String.format(EXCEPTION_MESSAGE_MULTIPLE_DEFINES, item)

    fun getExceptionMessageAlreadyDefined(item: String) =
        String.format(EXCEPTION_MESSAGE_ALREADY_DEFINED, item)

    fun getExceptionMessageMultipleParametersDefined(size: Int) =
        String.format(EXCEPTION_MESSAGE_MULTIPLE_PARAMETERS_DEFINED, size)
}

/**
 * Resolves a string from either a string resource or a direct string value.
 * This is an internal utility to ensure consistent handling of descriptions and reasons
 * across the Catalyst API-first approach.
 */
internal fun resolveString(
    context: Context,
    @StringRes stringRes: Int?,
    string: String?,
): String {
    return when {
        stringRes != null -> context.getString(stringRes)
        string != null -> string

        /*
         * This case should be unreachable if callers perform a `require` check in their init
         * blocks.
         */
        else -> error("Both stringRes and string cannot be null.")
    }
}
