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

package com.android.systemui.dreams.ui.viewmodel

/**
 * A controller responsible for managing the lifecycle and visibility of the dream-related dialog.
 *
 * This controller provides an abstraction for showing and dismissing a dialog within the dream
 * framework, allowing the UI to remain decoupled from the specific implementation details of the
 * dialog's presentation logic.
 */
interface DreamDialogController {
    /**
     * Attempts to show the dream dialog.
     *
     * @return `true` if the dialog was successfully shown, `false` otherwise (e.g., if the dialog
     *   is already visible or the current state prevents it from appearing).
     */
    fun showDialog(): Boolean

    /** Dismisses the dream dialog if it is currently visible. */
    fun dismissDialog()
}
