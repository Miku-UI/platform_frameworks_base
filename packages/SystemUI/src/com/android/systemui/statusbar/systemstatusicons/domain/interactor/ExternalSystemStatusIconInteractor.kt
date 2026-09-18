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

package com.android.systemui.statusbar.systemstatusicons.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.systemstatusicons.data.repository.ExternalSystemStatusIconRepository
import com.android.systemui.statusbar.systemstatusicons.shared.model.ExternalIconModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** An interactor for status bar icons from external processes. */
@SysUISingleton
class ExternalSystemStatusIconInteractor
@Inject
constructor(repository: ExternalSystemStatusIconRepository) {
    /**
     * A list of icons received from outside of the SystemUI process.
     *
     * Sorted so the earliest-received icon is at the *end* of the list. This ensures that new
     * external icons are pre-pended and don't move any external icons that are already showing
     * (because the system status icons are on the end-side).
     */
    val icons: Flow<List<ExternalIconModel>> = repository.icons.map { it.reversed() }
}
