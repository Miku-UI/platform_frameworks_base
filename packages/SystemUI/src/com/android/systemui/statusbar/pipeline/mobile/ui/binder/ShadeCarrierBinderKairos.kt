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

package com.android.systemui.statusbar.pipeline.mobile.ui.binder

import androidx.core.view.isVisible
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.lifecycle.repeatWhenWindowIsVisible
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.ShadeCarrierGroupMobileIconViewModelKairos
import com.android.systemui.util.AutoMarqueeTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object ShadeCarrierBinderKairos {
    /** Binds the view to the view-model, continuing to update the former based on the latter */
    @ExperimentalKairosApi
    fun bind(
        subId: Int,
        carrierTextView: AutoMarqueeTextView,
        viewModel: BuildSpec<ShadeCarrierGroupMobileIconViewModelKairos>,
        kairosNetwork: KairosNetwork,
        scope: CoroutineScope,
    ): Pair<ShadeCarrierBinding, Job> {
        carrierTextView.isVisible = true
        val job =
            scope.launch {
                carrierTextView.repeatWhenWindowIsVisible {
                    kairosNetwork.activateSpec(
                        nameTag { "ShadeCarrierBinderKairos(subId=$subId).bind" }
                    ) {
                        viewModel.applySpec().carrierName.observe(
                            name = nameTag { "ShadeCarrierBinderKairos(subId=$subId).carrierName" }
                        ) {
                            carrierTextView.text = it
                        }
                    }
                }
            }
        val binding =
            object : ShadeCarrierBinding {
                override fun setTextAppearance(resId: Int) {
                    carrierTextView.setTextAppearance(resId)
                }
            }
        return binding to job
    }
}
