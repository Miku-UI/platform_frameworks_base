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

package com.android.systemui.domain.interactor

import android.content.Context
import android.content.SharedPreferences
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.settings.UserFileManager
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class SharedPreferencesInteractor
@Inject
constructor(
    private val userFileManager: UserFileManager,
    private val userInteractor: SelectedUserInteractor,
    @Background private val bgDispatcher: CoroutineDispatcher,
) {
    fun sharedPreferences(
        fileName: String,
        @Context.PreferencesMode mode: Int,
    ): Flow<SharedPreferences> =
        userInteractor.selectedUser
            .map { userFileManager.getSharedPreferences(fileName, mode, it) }
            .flowOn(bgDispatcher)
}
