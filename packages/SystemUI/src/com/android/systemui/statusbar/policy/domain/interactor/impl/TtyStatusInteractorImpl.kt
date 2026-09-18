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

package com.android.systemui.statusbar.policy.domain.interactor.impl

import android.annotation.SuppressLint
import android.content.IntentFilter
import android.telecom.TelecomManager
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.policy.domain.interactor.TtyStatusInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Interactor responsible for determining if TTY is enabled. */
@SuppressLint("MissingPermission")
@SysUISingleton
class TtyStatusInteractorImpl
@Inject
constructor(
    @Background scope: CoroutineScope,
    @Background bgDispatcher: CoroutineDispatcher,
    broadcastDispatcher: BroadcastDispatcher,
    telecomManager: TelecomManager?,
) : TtyStatusInteractor {
    /** The current TTY state. */
    override val isEnabled: StateFlow<Boolean> =
        if (telecomManager == null) {
            MutableStateFlow(false)
        } else {
            broadcastDispatcher
                .broadcastFlow(IntentFilter(TelecomManager.ACTION_CURRENT_TTY_MODE_CHANGED)) {
                    intent,
                    _ ->
                    val currentTtyMode =
                        intent.getIntExtra(
                            TelecomManager.EXTRA_CURRENT_TTY_MODE,
                            TelecomManager.TTY_MODE_OFF,
                        )
                    currentTtyMode.isEnabled()
                }
                .flowOn(bgDispatcher)
                .onStart { telecomManager.currentTtyMode.isEnabled() }
                .stateIn(
                    scope = scope,
                    started = SharingStarted.WhileSubscribed(),
                    initialValue = false,
                )
        }

    private fun Int.isEnabled(): Boolean {
        return this != TelecomManager.TTY_MODE_OFF
    }
}
