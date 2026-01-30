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

package com.android.systemui.statusbar.systemstatusicons.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.systemstatusicons.data.repository.OrderedIconSlotNamesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Interactor for retrieving the ordered list of icon slot names. */
@SysUISingleton
class OrderedIconSlotNamesInteractor
@Inject
constructor(repository: OrderedIconSlotNamesRepository) {
    val orderedIconSlotNames: StateFlow<List<String>> = repository.orderedIconSlotNames
}
