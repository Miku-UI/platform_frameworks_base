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

package com.android.systemui.inputmethod.ui.viewmodel

import android.view.inputmethod.InputMethodManager.IMPickerEntryPoint
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.inputmethod.domain.interactor.ImeSwitcherMenuInteractor
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel
import com.android.systemui.inputmethod.shared.model.ModelChangeListener
import javax.inject.Inject

/** The ViewModel for the [com.android.systemui.inputmethod.ui.binder.ImeSwitcherMenuBinder]. */
@SysUISingleton
class ImeSwitcherMenuBinderViewModel
@Inject
constructor(private val interactor: ImeSwitcherMenuInteractor) {

    /**
     * Registers the given display change listener.
     *
     * @param listener the listener to register.
     * @return a runnable to unregister the listener.
     */
    fun registerDisplayChangeListener(listener: DisplayChangeListener): () -> Unit {
        val modelListener =
            object : ModelChangeListener {

                override fun onChanged(userId: Int, model: ImeSwitcherMenuModel?) {
                    listener.onChanged(userId, model?.entryPoint, model?.displayId)
                }
            }
        interactor.registerModelChangeListener(modelListener)
        return { interactor.unregisterModelChangeListener(modelListener) }
    }

    /**
     * Listener to be notified when the display that should show the UI tied to a user has changed.
     */
    interface DisplayChangeListener {

        /**
         * Called when the display that should show the UI tied to a user has changed.
         *
         * @param userId the ID of the user whose display changed.
         * @param entryPoint the entry point where the UI was requested from, or `null` if no UI
         *   should be shown.
         * @param displayId the ID of the display that should show the UI, or `null` if no UI should
         *   be shown.
         */
        fun onChanged(userId: Int, @IMPickerEntryPoint entryPoint: Int?, displayId: Int?)
    }
}
