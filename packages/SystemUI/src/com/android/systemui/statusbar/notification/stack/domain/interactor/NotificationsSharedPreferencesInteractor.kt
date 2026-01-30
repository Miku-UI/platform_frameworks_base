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

package com.android.systemui.statusbar.notification.stack.domain.interactor

import android.content.Context
import android.content.SharedPreferences
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.domain.interactor.SharedPreferencesInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@SysUISingleton
class NotificationsSharedPreferencesInteractor
@Inject
constructor(
    sharedPreferencesInteractor: SharedPreferencesInteractor,
    @Background scope: CoroutineScope,
) {
    val sharedPreferences: StateFlow<SharedPreferences?> =
        sharedPreferencesInteractor
            .sharedPreferences(FILENAME, Context.MODE_PRIVATE)
            .stateIn(scope, SharingStarted.Eagerly, null)
}

private const val FILENAME = "notifs_prefs"
