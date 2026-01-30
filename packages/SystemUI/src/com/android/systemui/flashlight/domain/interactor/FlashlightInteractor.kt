/* 999
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

package com.android.systemui.flashlight.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.flashlight.data.repository.FlashlightRepository
import javax.inject.Inject

/** Observe and control flashlight state. */
@SysUISingleton
class FlashlightInteractor @Inject constructor(private val repository: FlashlightRepository) {
    fun setEnabled(enabled: Boolean) {
        repository.setEnabled(enabled)
    }

    fun setLevel(level: Int) {
        repository.setLevel(level)
    }

    fun setTemporaryLevel(level: Int) {
        repository.setTemporaryLevel(level)
    }

    val state = repository.state

    val deviceSupportsFlashlight: Boolean
        get() = repository.deviceSupportsFlashlight
}
