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
@file:JvmName("CompatUITypeUtils")

package com.android.wm.shell.compatui.api

/**
 * Safely casts this instance of type [S] to a specific reified subtype [T].
 *
 * This inline function leverages reified type parameters for both the receiver type [S] and the
 * target type [T] to perform a runtime type check. This allows for a safe cast without the need for
 * an `UNCHECKED_CAST` suppression. It is particularly useful for downcasting to a more specific
 * type.
 *
 * @param S The type of the receiver instance (this). Must be a reified type parameter.
 * @param T The target subtype of [S] to which this instance should be cast. Must be a reified type
 *   parameter (used with `inline`).
 * @return This instance cast to type [T] if it is compatible, otherwise `null`.
 */
inline fun <reified S, reified T : S> S.asType(): T? = this as? T

/**
 * Safely casts this instance of type [S] to a specific target class [T].
 *
 * This function provides a Java-friendly way to perform a safe downcast, as Java does not support
 * Kotlin's reified type parameters. It explicitly accepts a {@link Class} object representing the
 * target type to enable a runtime type check.
 *
 * This extension function will appear as a static method in the compiled Kotlin utility class when
 * called from Java.
 *
 * @param S The type of the receiver instance (this).
 * @param T The target type to which this instance should be cast, which must be a subtype of [S].
 * @param targetClass The {@link Class} object representing the target type [T].
 * @param <S> The generic type of the receiver.
 * @param <T> The generic type of the target class, which must extend S.
 * @return This instance cast to type [T] if it is compatible with `targetClass`, otherwise `null`.
 */
@JvmName("asTypeJava") // Provides a distinct name for Java callers
fun <S, T : S> S.asType(targetClass: Class<T>): T? {
    return if (targetClass.isInstance(this)) {
        @Suppress("UNCHECKED_CAST") // This cast is safe due to the isInstance check
        this as T
    } else {
        null
    }
}
