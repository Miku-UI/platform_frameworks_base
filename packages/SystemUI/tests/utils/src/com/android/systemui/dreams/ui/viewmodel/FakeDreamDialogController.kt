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

/** A fake implementation of [DreamDialogController] for unit testing. */
class FakeDreamDialogController : DreamDialogController {
    var dialogShowing: Boolean = false
        private set

    private var dialogAllowed = true

    override fun showDialog(): Boolean {
        if (!dialogAllowed) {
            return false
        }
        dialogShowing = true
        return true
    }

    override fun dismissDialog() {
        dialogShowing = false
    }

    /**
     * Sets whether the call to [showDialog] should succeed. Use this to simulate conditions like
     * "switcher disabled" or "user restricted".
     */
    fun setDialogAllowed(allowed: Boolean) {
        dialogAllowed = allowed
    }
}

val DreamDialogController.fake: FakeDreamDialogController
    get() = this as FakeDreamDialogController
