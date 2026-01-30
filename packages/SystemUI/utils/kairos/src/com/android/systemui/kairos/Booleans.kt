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

package com.android.systemui.kairos

import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairos.util.toNameData

/** Returns a [State] that is `true` only when all of [states] are `true`. */
@ExperimentalKairosApi
fun allOf(vararg states: State<Boolean>): State<Boolean> =
    allOf(nameTag("allOf").toNameData("allOf"), *states)

internal fun allOf(nameData: NameData, vararg states: State<Boolean>): State<Boolean> =
    combine(nameData, *states) { it.allTrue() }

/** Returns a [State] that is `true` when any of [states] are `true`. */
@ExperimentalKairosApi
fun anyOf(vararg states: State<Boolean>): State<Boolean> =
    anyOf(nameTag("anyOf").toNameData("anyOf"), *states)

internal fun anyOf(nameData: NameData, vararg states: State<Boolean>): State<Boolean> =
    combine(nameData, *states) { it.anyTrue() }

/** Returns a [State] containing the inverse of the Boolean held by the original [State]. */
@ExperimentalKairosApi
fun not(state: State<Boolean>): State<Boolean> =
    not(nameTag("State.not").toNameData("State.not"), state)

internal fun not(nameData: NameData, state: State<Boolean>): State<Boolean> =
    state.mapCheapUnsafe(nameData) { !it }

private fun Iterable<Boolean>.allTrue() = all { it }

private fun Iterable<Boolean>.anyTrue() = any { it }
