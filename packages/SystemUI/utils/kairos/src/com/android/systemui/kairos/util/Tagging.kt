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

package com.android.systemui.kairos.util

import android.os.Build
import android.os.SystemProperties

private val TaggingEnabled: Boolean =
    (Build.IS_ENG || Build.IS_USERDEBUG) &&
        SystemProperties.getBoolean("persist.debug.kairos_name_tagging", false)

/**
 * Developer-specified name used for debugging. Will only be used if Kairos is compiled with name
 * tagging enabled, otherwise will be discarded at build time.
 *
 * Use [nameTag] to construct new values.
 */
sealed interface NameTag

/** Returns a [NameTag] with a lazy [name]. */
fun nameTag(name: () -> String): NameTag = PartialNameTag(name)

/** Returns a [NameTag] containing [name]. */
fun nameTag(name: String): NameTag = PartialNameTag { name }

/** Returns a new [NameTag] containing a name lazily transformed by [block]. */
fun NameTag.map(block: (String) -> String): NameTag =
    if (!TaggingEnabled) {
        NameTaggingDisabled
    } else {
        when (this) {
            is PartialNameTag -> PartialNameTag { block(name()) }
            is NameData -> mapName(block)
        }
    }

internal class FullNameTag(val name: Lazy<String>, val operatorName: String) : NameData {
    override fun toString(): String = "Name(name=${name.value}, operatorName=$operatorName)"
}

@JvmInline internal value class PartialNameTag(val name: () -> String) : NameTag

internal sealed interface NameData : NameTag

internal fun NameTag?.toNameData(operatorName: String): NameData =
    if (!TaggingEnabled) {
        NameTaggingDisabled
    } else {
        when (this) {
            null -> FullNameTag(lazyOf(operatorName), operatorName)
            is FullNameTag -> this
            is PartialNameTag ->
                FullNameTag(lazy(LazyThreadSafetyMode.PUBLICATION) { name() }, operatorName)
            is NameTaggingDisabled -> this
        }
    }

internal data object NameTaggingDisabled : NameData

internal inline fun NameData.mapName(crossinline block: (String) -> String): NameData =
    if (!TaggingEnabled) {
        NameTaggingDisabled
    } else {
        when (this) {
            is NameTaggingDisabled -> this
            is FullNameTag ->
                FullNameTag(
                    lazy(LazyThreadSafetyMode.PUBLICATION) { block(name.value) },
                    operatorName,
                )
        }
    }

@Suppress("NOTHING_TO_INLINE")
internal inline fun NameData.appendNames(vararg names: String, separator: String = "-"): NameData =
    mapName { name ->
        buildString {
            append(name)
            for (next in names) {
                append(separator)
                append(next)
            }
        }
    }

@Suppress("NOTHING_TO_INLINE")
internal inline operator fun NameData.plus(name: String): NameData = appendNames(name)

internal inline operator fun NameData.plus(crossinline name: () -> String): NameData = mapName {
    "$it-${name()}"
}

internal fun NameData.forceInit() {
    if (!TaggingEnabled) return
    when (this) {
        is FullNameTag -> name.value
        NameTaggingDisabled -> Unit
    }
}
