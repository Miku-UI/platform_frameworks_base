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

package com.android.systemui.deviceentry.data.repository

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.phone.KeyguardBypassController
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import dagger.Binds
import dagger.Module
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Defines interface for classes that can encapsulate state related to bypassing the lockscreen
 * when/if biometric authentication succeeds.
 */
interface DeviceEntryBypassRepository {
    /**
     * Whether lockscreen bypass is enabled. When enabled, the lockscreen will be automatically
     * dismissed once the authentication challenge is completed.
     *
     * This is a setting that is specific to the face unlock authentication method, because the user
     * intent to unlock is not known. On devices that don't support face unlock, this always returns
     * `true`.
     *
     * When this is `false`, an automatically-triggered face unlock shouldn't automatically dismiss
     * the lockscreen.
     */
    val isBypassEnabled: StateFlow<Boolean>
}

@SysUISingleton
class DeviceEntryBypassRepositoryImpl
@Inject
constructor(
    @Background backgroundScope: CoroutineScope,
    keyguardBypassController: KeyguardBypassController,
) : DeviceEntryBypassRepository {

    override val isBypassEnabled: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val listener =
                    object : KeyguardBypassController.OnBypassStateChangedListener {
                        override fun onBypassStateChanged(isEnabled: Boolean) {
                            trySend(isEnabled)
                        }
                    }
                keyguardBypassController.registerOnBypassStateChangedListener(listener)
                awaitClose {
                    keyguardBypassController.unregisterOnBypassStateChangedListener(listener)
                }
            }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.Eagerly,
                initialValue = keyguardBypassController.bypassEnabled,
            )
}

@Module
interface DeviceEntryBypassRepositoryModule {
    @Binds fun bind(impl: DeviceEntryBypassRepositoryImpl): DeviceEntryBypassRepository
}
