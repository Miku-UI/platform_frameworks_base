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

package com.android.systemui.display.domain.interactor

import android.view.Display
import com.android.systemui.display.data.repository.DisplayTypeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Extracts the display type logic for current display. */
class DisplayTypeInteractor(displayTypeRepository: DisplayTypeRepository) {

    /** True if display type is `TYPE_INTERNAL` */
    val isInternalDisplay: Flow<Boolean> =
        displayTypeRepository.displayType.map { it == Display.TYPE_INTERNAL }.distinctUntilChanged()
}
