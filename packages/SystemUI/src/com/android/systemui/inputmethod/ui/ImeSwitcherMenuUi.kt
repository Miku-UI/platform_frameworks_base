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

package com.android.systemui.inputmethod.ui

import android.content.Context
import android.util.IndentingPrintWriter
import android.view.inputmethod.InputMethodManager.IMPickerEntryPoint
import androidx.annotation.MainThread
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel

/** Base interface for any IME Switcher Menu UI implementation. */
interface ImeSwitcherMenuUi {

    /** Shows the IME Switcher Menu, with a list of IMEs and their subtypes. */
    @MainThread fun show()

    /** Hides the IME Switcher Menu, dismissing it. */
    @MainThread fun dismiss()

    /**
     * Dumps the internal state of the IME Switcher Menu.
     *
     * @param pw the writer to dump the data to.
     * @param description the description to append to the first dumped line.
     */
    fun dump(pw: IndentingPrintWriter, description: String)

    interface Factory {

        /**
         * Creates a new IME Switcher Menu UI instance.
         *
         * @param context the context of the UI.
         * @param entryPoint the entry point where the menu was requested from.
         * @param viewModelFactory the factory to create view model.
         */
        fun create(
            context: Context,
            @IMPickerEntryPoint entryPoint: Int,
            viewModelFactory: (context: Context) -> ImeSwitcherMenuViewModel,
        ): ImeSwitcherMenuUi
    }
}
