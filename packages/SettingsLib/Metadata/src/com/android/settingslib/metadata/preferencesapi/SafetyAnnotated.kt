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

 package com.android.settingslib.metadata.preferencesapi

/** A value that is annotated with safety information. */
sealed interface SafetyAnnotated<T> {
  val value: T

  /** A value that is not safe. */
  data class Unsafe<T>(override val value: T) : SafetyAnnotated<T>

  /** A value that is safe. */
  data class Safe<T>(override val value: T) : SafetyAnnotated<T>
}

fun <T> T.safe(): SafetyAnnotated<T> = SafetyAnnotated.Safe(this)
fun <T> T.unsafe(): SafetyAnnotated<T> = SafetyAnnotated.Unsafe(this)

fun <T1, T2> pair(s1: T1, s2: T2) = Pair(SafetyAnnotated.Unsafe(s1), SafetyAnnotated.Unsafe(s2))
fun <T1, T2> pair(s1: SafetyAnnotated<T1>, s2: SafetyAnnotated<T2>) = Pair(s1, s2)
fun <T1, T2> pair(s1: SafetyAnnotated<T1>, s2: T2) = Pair(SafetyAnnotated.Unsafe(s1), s2)
fun <T1, T2> pair(s1: T1, s2: SafetyAnnotated<T2>) = Pair(s1, SafetyAnnotated.Unsafe(s2))


/**
 * Extract a value from a potentially-safety-annotated value.
 *
 * If the value is unsafe, then the string will be wrapped in <external_data>
 * tags.
 */
fun extractSafety(v: Any, markup: Boolean = true): Any? {
    return if (v is SafetyAnnotated.Unsafe<*>) {
      if (markup) {
        markStringAsExternalData(v.value.toString())
      } else {
        v.value.toString()
      }
    } else if (v is SafetyAnnotated<*>) {
        v.value
    } else {
        v
    }
}

/**
 * Mark a string as external data.
 *
 * This is done by wrapping the string in <external_data> tags and removing
 * content which would break out of the tags.
 */
fun markStringAsExternalData(s: String): String {
    return "<external_data>${s.replace("</external_data>", "")}</external_data>"
}