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

package com.android.systemui.demomode.domain.interactor

import android.os.Bundle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.demomode.DemoMode
import com.android.systemui.demomode.DemoModeController
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The interactor that captures demo mode related events. */
@SysUISingleton
class DemoModeInteractor
@Inject
constructor(
    private val demoModeController: DemoModeController,
    @Background private val backgroundScope: CoroutineScope,
) {
    /** A [StateFlow] tracking whether we are in demo mode. */
    val isInDemoMode: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val callback =
                    object : DemoMode {
                        override fun onDemoModeStarted() {
                            trySend(true)
                        }

                        override fun onDemoModeFinished() {
                            trySend(false)
                        }

                        override fun dispatchDemoCommand(command: String, args: Bundle) {}
                    }

                demoModeController.addCallback(callback)
                awaitClose { demoModeController.removeCallback(callback) }
            }
            .stateIn(
                backgroundScope,
                SharingStarted.WhileSubscribed(),
                demoModeController.isInDemoMode,
            )
}
