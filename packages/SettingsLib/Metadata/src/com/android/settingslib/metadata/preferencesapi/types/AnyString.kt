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
import com.android.settingslib.metadata.R
import com.android.settingslib.metadata.preferencesapi.types.EType

/** Any string value. */
object AnyString: DirectApiType<String> {
    override val externalType = EType.String
    override fun getDescription(context: Context): String =
        context.getString(R.string.any_string_type_description)
    override fun getKey(): String = "AnyString"
}
