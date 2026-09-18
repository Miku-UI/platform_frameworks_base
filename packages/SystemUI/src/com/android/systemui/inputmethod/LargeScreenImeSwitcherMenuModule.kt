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

package com.android.systemui.inputmethod

import com.android.systemui.inputmethod.ui.ImeSwitcherMenuUi
import com.android.systemui.inputmethod.ui.view.LargeScreenImeSwitcherMenuDialogDelegate
import dagger.Binds
import dagger.Module

/**
 * Module for providing objects for the IME Switcher Menu with support for large screens.
 *
 * Note: This module only provides the UI binding, the core Repository and Binder modules are
 * included from [InputMethodModule] to ensure this UI is available in all SystemUI implementations.
 */
@Module
interface LargeScreenImeSwitcherMenuModule {

    @Binds
    fun bindUiFactory(impl: LargeScreenImeSwitcherMenuDialogDelegate.Factory): ImeSwitcherMenuUi.Factory
}
