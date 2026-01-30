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

package com.android.systemui.media.controls.util

import android.os.Handler
import com.android.settingslib.media.LocalMediaManager
import com.android.settingslib.media.SuggestedDeviceConnectionManager
import com.android.settingslib.media.SuggestedDeviceManager
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class SuggestedDeviceManagerFactory
@Inject
constructor(
    @Main private val handler: Handler,
    @Application private val applicationScope: CoroutineScope,
) {
    fun create(localMediaManager: LocalMediaManager): SuggestedDeviceManager {
        val suggestedDeviceConnectionManager =
            SuggestedDeviceConnectionManager(localMediaManager, applicationScope)
        return SuggestedDeviceManager(localMediaManager, handler, suggestedDeviceConnectionManager)
    }
}
