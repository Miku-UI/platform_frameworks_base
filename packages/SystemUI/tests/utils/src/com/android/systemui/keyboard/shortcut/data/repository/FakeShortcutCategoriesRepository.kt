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

package com.android.systemui.keyboard.shortcut.data.repository

import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategory
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutHelperState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class FakeShortcutCategoriesRepository(stateRepository: ShortcutHelperStateRepository) :
    ShortcutCategoriesRepository {

    private val _categories = MutableStateFlow<List<ShortcutCategory>>(emptyList())
    override val categories =
        combine(stateRepository.state, _categories) { state, categories ->
            if (state is ShortcutHelperState.Active) {
                categories
            } else {
                emptyList()
            }
        }

    fun setShortcutCategories(vararg categories: ShortcutCategory) {
        _categories.value = categories.asList()
    }
}
