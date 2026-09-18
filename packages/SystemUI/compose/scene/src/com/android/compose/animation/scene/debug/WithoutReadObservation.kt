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

package com.android.compose.animation.scene.debug

import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.snapshots.Snapshot

/**
 * For debug code we want to make sure that no state subscribes to updates from within debug code.
 * This might change the recomposition behavior while debugging and might mislead the developer.
 *
 * This wrapper class guards any class against accidental reads without calling
 * [SnapShot.withoutReadObservation()]. The caller is forced to use the [read] method.
 *
 * Make sure to call any states within [read]. You can still unwrap a state value and then call a
 * child state property on it, e.g.: `myVal.read { state }.anotherState` DON'T do this. Instead do
 * `myVal.read { state.anotherState }`
 */
@JvmInline
internal value class WithoutReadObservation<T>(private val value: T) {
    inline fun <R> read(block: @DisallowComposableCalls T.() -> R): R {
        return Snapshot.withoutReadObservation { value.block() }
    }
}

internal fun <T> T.withoutReadObservation(): WithoutReadObservation<T> =
    WithoutReadObservation(this)
