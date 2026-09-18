/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.inputmethod

import com.android.systemui.inputmethod.data.repository.ImeSwitcherMenuRepositoryModule
import com.android.systemui.inputmethod.data.repository.InputMethodRepositoryModule
import com.android.systemui.inputmethod.ui.binder.ImeSwitcherMenuBinderModule
import dagger.Module

/**
 * Main dagger module for the input method package.
 *
 * Includes [ImeSwitcherMenuRepositoryModule] and [ImeSwitcherMenuBinderModule] to enforce that all
 * SystemUI implementations provide a binding for
 * [com.android.systemui.inputmethod.ui.ImeSwitcherMenuUi.Factory], which is required for proper IME
 * Switcher Menu functionality (validated in CTS).
 */
@Module(
    includes =
        [
            ImeSwitcherMenuBinderModule::class,
            ImeSwitcherMenuRepositoryModule::class,
            InputMethodRepositoryModule::class,
        ]
)
object InputMethodModule
