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

package com.android.systemui.inputmethod.domain.interactor

import android.annotation.IntRange
import android.annotation.UserIdInt
import androidx.annotation.MainThread
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.inputmethod.data.repository.ImeSwitcherMenuRepository
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel.Companion.NOT_A_SUBTYPE_INDEX
import com.android.systemui.inputmethod.shared.model.ModelChangeListener
import javax.inject.Inject

/** Interactor for linking the IME Switcher Menu UI and Data. */
@SysUISingleton
class ImeSwitcherMenuInteractor
@Inject
constructor(
    private val repository: ImeSwitcherMenuRepository,
) {

    /**
     * Retrieves the stored data model for the given user, or returns `null` if no model is stored
     * for that user.
     *
     * @param userId the ID of the user whose model to get.
     */
    @MainThread
    fun getModel(@UserIdInt userId: Int): ImeSwitcherMenuModel? = repository.getModel(userId)

    /**
     * Registers the given model change listener.
     *
     * @param listener the listener to register.
     */
    fun registerModelChangeListener(listener: ModelChangeListener) =
        repository.registerModelChangeListener(listener)

    /**
     * Unregisters the given model change listener.
     *
     * @param listener the listener to unregister.
     */
    fun unregisterModelChangeListener(listener: ModelChangeListener) =
        repository.unregisterModelChangeListener(listener)

    /**
     * Called when the IME Switcher Menu visibility changed for the given user on the given display.
     *
     * @param visible the new visibility of the menu.
     * @param displayId the ID of the display where the menu visibility changed.
     * @param userId the ID of the user whose menu visibility changed.
     */
    fun onVisibilityChanged(visible: Boolean, displayId: Int, @UserIdInt userId: Int) {
        repository.notifyVisibilityChanged(visible, displayId, userId)
    }

    /**
     * Called when an IME and subtype was selected in the IME Switcher Menu by the given user. This
     * will switch to the IME if it is enabled and installed, and otherwise will do nothing. If the
     * subtype index is also supplied (not [NOT_A_SUBTYPE_INDEX]) and valid, also switches to it,
     * otherwise the system devices the most sensible default subtype to use.
     *
     * @param imeId the ID of the selected IME.
     * @param subtypeIndex the selected subtype, as an index in the IME's array of subtypes, or
     *   [NOT_A_SUBTYPE_INDEX] if the system should decide the most sensible subtype.
     * @param userId the ID of the user that selected the IME and subtype.
     */
    fun onImeAndSubtypeSelected(
        imeId: String,
        @IntRange(from = NOT_A_SUBTYPE_INDEX.toLong()) subtypeIndex: Int,
        @UserIdInt userId: Int,
    ) {
        repository.notifyImeAndSubtypeSelected(imeId, subtypeIndex, userId)
    }

    /**
     * Whether the settings button should be shown on the IME Switcher Menu.
     *
     * @param userId the ID of the user to show the menu for.
     */
    fun shouldShowSettingsButton(@UserIdInt userId: Int): Boolean {
        return repository.shouldShowSettingsButton(userId)
    }
}
