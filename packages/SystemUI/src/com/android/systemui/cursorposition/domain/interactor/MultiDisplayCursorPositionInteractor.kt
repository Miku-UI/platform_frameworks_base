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

package com.android.systemui.cursorposition.domain.interactor

import com.android.systemui.cursorposition.data.model.CursorPosition
import com.android.systemui.cursorposition.data.repository.MultiDisplayCursorPositionRepository
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@SysUISingleton
class MultiDisplayCursorPositionInteractor
@Inject
constructor(private val repo: MultiDisplayCursorPositionRepository) {
    val cursorPositions: Flow<CursorPosition?> = repo.cursorPositions
}
