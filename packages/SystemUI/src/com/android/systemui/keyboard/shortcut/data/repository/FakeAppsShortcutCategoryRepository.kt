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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAppsShortcutCategoryRepository : ShortcutCategoriesRepository {
    private val _categories = MutableStateFlow(emptyList<ShortcutCategory>())
    override val categories: Flow<List<ShortcutCategory>> = _categories.asStateFlow()

    fun setFakeAppsShortcutCategories(fakeCategories: List<ShortcutCategory>) {
        _categories.value = fakeCategories
    }
}
