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

package com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel

import androidx.compose.runtime.getValue
import com.android.systemui.activateIn
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.map
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.util.composable.kairos.hydratedComposeStateOf
import com.android.systemui.util.lifecycle.kairos.kairosBuilder
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class MobileIconsStateKairos
@AssistedInject
constructor(viewModel: MobileIconsViewModelKairos, private val kairosNetwork: KairosNetwork) :
    MobileIconsState, ExclusiveActivatable() {

    private val builder = kairosBuilder()

    override val isStackable: Boolean by
        builder.hydratedComposeStateOf("isStackable", viewModel.isStackable, false)

    override val mobileSubViewModels: List<MobileIconState.Factory> by
        builder.hydratedComposeStateOf(
            "mobileSubViewModels",
            viewModel.icons.map {
                it.map { (subId, iconVm) ->
                    object : MobileIconState.Factory {
                        override val subscriptionId: Int = subId

                        override fun create(): MobileIconState {
                            return MobileIconStateKairos(iconVm, kairosNetwork)
                        }
                    }
                }
            },
            emptyList(),
        )

    override suspend fun onActivated() {
        builder.activateIn(kairosNetwork)
    }

    @AssistedFactory
    fun interface Factory : MobileIconsState.Factory {
        override fun create(): MobileIconsStateKairos
    }
}
