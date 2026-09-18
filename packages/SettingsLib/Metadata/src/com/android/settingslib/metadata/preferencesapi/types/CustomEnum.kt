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
import com.android.settingslib.metadata.preferencesapi.resolveString
import kotlin.reflect.KClass
import com.android.settingslib.metadata.preferencesapi.safe
import com.android.settingslib.metadata.preferencesapi.types.EType

inline fun <reified T : Any, E> CustomEnum(
    enumClass: KClass<E>,
    @StringRes description: Int
): CustomEnum<E, T> where E : Enum<E>, E : EnumApi<T> {
    val externalType = when (T::class) {
        Int::class -> EType.Int
        String::class -> EType.String
        Boolean::class -> EType.Boolean
        else -> error("Unsupported external type.")
    } as EType<T>
    return CustomEnum(enumClass, externalType, descriptionRes = description, description = null)
}

inline fun <reified T : Any, E> CustomEnum(
    enumClass: KClass<E>,
    description: String
): CustomEnum<E, T> where E : Enum<E>, E : EnumApi<T> {
    val externalType = when (T::class) {
        Int::class -> EType.Int
        String::class -> EType.String
        Boolean::class -> EType.Boolean
        else -> error("Unsupported external type.")
    } as EType<T>
    return CustomEnum(enumClass, externalType, descriptionRes = null, description = description)
}

/**
 * An entry from the enum.
 *
 * DO NOT CONSTRUCT DIRECTLY. Use the helper methods instead.
*/
class CustomEnum<EnumType, ExternalType : Any> constructor (
    val enumClass: KClass<EnumType>,
    override val externalType: EType<ExternalType>,
    @field:StringRes val descriptionRes: Int?,
    val description: String?,
) : FiniteOptionsType<EnumType, ExternalType> where EnumType : Enum<EnumType>, EnumType : EnumApi<ExternalType> { // todo: support internal type

    init {
        val isStringApi = EnumApiWithString::class.java.isAssignableFrom(enumClass.java)
        val isResApi = EnumApiWithRes::class.java.isAssignableFrom(enumClass.java)

        // Enum class must implement either EnumApiWithString or EnumApiWithRes
        require(isStringApi || isResApi)
        require(descriptionRes != null || description != null)
    }

    @Suppress("UNCHECKED_CAST")
    private val entries: Array<EnumType> = enumClass.java.enumConstants ?: (emptyArray<Any>() as Array<EnumType>)

    private val valueMap: Map<ExternalType, EnumType> = entries.associateBy { it.asApiValue }

    /** Finds the Enum constant associated with the given API value. */
    fun fromApiValue(value: ExternalType): EnumType? = valueMap[value]

    /** Returns all entries. */
    fun getEntries(): List<EnumType> = entries.toList()

    override suspend fun getOptions(context: Context) = entries.map { entry ->
        val purposeString = when (entry) {
            is EnumApiWithString<*> -> entry.purpose
            is EnumApiWithRes<*> -> context.getString(entry.purpose)

            // This case is unreachable because of the init block check
            else -> error("Enum entry does not implement EnumApiWithString or EnumApiWithRes.")
        }
        entry.asApiValue.safe() to purposeString.safe()
    }.toList()

    override fun getDescription(context: Context): String = resolveString(context, descriptionRes, description)

    override fun getKey(): String = "CustomEnum:${enumClass.qualifiedName}"

    override fun convertInternalToExternal(internalValue: EnumType): ExternalType = internalValue.asApiValue
    override fun convertExternalToInternal(externalValue: ExternalType): EnumType = fromApiValue(externalValue)!!
}

/**
 * Defines a contract for Enums that require mapping to a specific API value type and providing a
 * human-readable purpose. Enums using this API should implement one of its sub-interfaces:
 * [EnumApiWithString] or [EnumApiWithRes].
 *
 * This interface allows generic wrappers to handle serialization and UI display logic uniformly
 * across different Enum types.
 *
 * @param T The type of the value used by the API (e.g., [Int], [String]).
 *
 * Example usage:
 * ```
 * // Using strings
 * enum class ConnectionState(
 *   override val asApiValue: Int,
 *   override val purpose: String
 * ) : EnumApiWithString<Int> {
 *   CONNECTED(1, "Connected state"),
 *   DISCONNECTED(0, "Disconnected state"),
 * }
 *
 * // Using string resources
 * enum class ConnectionState(
 *   override val asApiValue: Int,
 *   override val purpose: Int
 * ) : EnumApiWithRes<Int> {
 *   CONNECTED(1, R.string.connected),
 *   DISCONNECTED(0, R.string.disconnected),
 * }
 * ```
 */
interface EnumApi<T> {
    val asApiValue: T
}

/**
 * An [EnumApi] that provides a human-readable purpose as a direct [String].
 */
interface EnumApiWithString<T> : EnumApi<T> {
    val purpose: String
}

/**
 * An [EnumApi] that provides a human-readable purpose as a [StringRes] ID.
 */
interface EnumApiWithRes<T> : EnumApi<T> {
    @get:StringRes
    val purpose: Int
}
