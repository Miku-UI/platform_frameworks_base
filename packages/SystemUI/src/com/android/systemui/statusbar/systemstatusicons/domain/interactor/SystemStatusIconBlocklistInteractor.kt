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
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** An interactor that provides the set of blocked icon slots. */
interface SystemStatusIconBlocklistInteractor {
    /** The set of blocked icon slots. */
    val blockedIconSlots: Flow<Set<String>>
}

/**
 * An empty implementation of [SystemStatusIconBlocklistInteractor]. Used by callers if no icons
 * need to be blocked.
 */
@SysUISingleton
class EmptySystemStatusIconBlockListInteractor @Inject constructor() :
    SystemStatusIconBlocklistInteractor {
    override val blockedIconSlots: Flow<Set<String>> = flowOf(emptySet())
}