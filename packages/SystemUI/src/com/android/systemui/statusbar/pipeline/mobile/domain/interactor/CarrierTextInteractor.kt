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
package com.android.systemui.statusbar.pipeline.mobile.domain.interactor

import com.android.keyguard.CarrierTextManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

interface CarrierTextInteractor {
    val initialValue: CharSequence?
    val carrierText: StateFlow<CharSequence?>
}

@Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
@SysUISingleton
class CarrierTextInteractorImpl
@Inject
constructor(
    private val carrierTextManagerBuilder: CarrierTextManager.Builder,
    @Background private val scope: CoroutineScope,
) : CarrierTextInteractor {
    private val carrierTextManager =
        carrierTextManagerBuilder
            .setShowAirplaneMode(false)
            .setShowMissingSim(false)
            .setDebugLocationString("Interactor")
            .build()

    override val initialValue: CharSequence? = null
    override val carrierText: StateFlow<CharSequence?> =
        conflatedCallbackFlow {
                val callback =
                    object : CarrierTextManager.CarrierTextCallback {
                        override fun updateCarrierInfo(
                            info: CarrierTextManager.CarrierTextCallbackInfo
                        ) {
                            trySend(info.carrierText)
                        }
                    }

                carrierTextManager.setListening(callback)
                awaitClose { carrierTextManager.setListening(null) }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(), initialValue)
}
