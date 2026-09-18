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

package com.android.settingslib.metadata.preferencesapi.types

sealed interface EType<T : Any> {
    val clazz: Class<T>

    fun toString(value: T): kotlin.String
    fun fromString(value: kotlin.String): T

    data object String : EType<kotlin.String> {
        override val clazz = kotlin.String::class.java

        override fun toString(value: kotlin.String) = value
        override fun fromString(value: kotlin.String) = value
    }
    data object Boolean : EType<kotlin.Boolean> {
        override val clazz = kotlin.Boolean::class.javaObjectType
        override fun toString(value: kotlin.Boolean) = value.toString()
        override fun fromString(value: kotlin.String) = value.toBoolean()
    }
    data object Int : EType<kotlin.Int> {
        override val clazz = kotlin.Int::class.javaObjectType
        override fun toString(value: kotlin.Int) = value.toString()
        override fun fromString(value: kotlin.String) = value.toInt()
    }
}
