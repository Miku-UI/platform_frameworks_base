/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.util.composable.kairos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.awaitClose
import com.android.systemui.kairos.launchEffect
import com.android.systemui.kairos.util.NameTag
import com.android.systemui.kairos.util.map

/**
 * Activates the Kairos [buildSpec] within [kairosNetwork], bound to the current composition.
 *
 * [block] will be invoked with the result of activating the [buildSpec]. [buildSpec] will be
 * deactivated automatically when [ActivatedKairosSpec] leaves the composition.
 */
@ExperimentalKairosApi
@Composable
fun <T> ActivatedKairosSpec(
    buildSpec: BuildSpec<T>,
    kairosNetwork: KairosNetwork,
    name: NameTag? = null,
    block: @Composable (T) -> Unit,
) {
    var state by remember { mutableStateOf<Any?>(Uninitialized) }
    LaunchedEffect(key1 = Unit) {
        kairosNetwork.activateSpec(name) {
            val v = buildSpec.applySpec()
            launchEffect(name = name?.map { "$it-effect" }) {
                state = v
                awaitClose { state = Uninitialized }
            }
        }
    }
    state.let {
        if (it !== Uninitialized) {
            @Suppress("UNCHECKED_CAST") block(it as T)
        }
    }
}

private object Uninitialized
