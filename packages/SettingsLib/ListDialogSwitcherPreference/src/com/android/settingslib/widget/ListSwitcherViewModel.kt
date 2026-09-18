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
package com.android.settingslib.widget

import android.graphics.drawable.Drawable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * A [ViewModel] that facilitates communication between a [ListDialogSwitcherPreference] and its
 * [ListSwitcherDialogFragment].
 *
 * This ViewModel acts as a shared, centralized hub for holding the dialog's temporary data (like
 * the list of items to display) and for communicating events, such as item selection or button
 * clicks, back to the appropriate preference.
 */
class ListSwitcherViewModel : ViewModel() {
    /** The list of items to display in the dialog. */
    var items: List<ListDialogItemInfo>? = null

    /** The title to be shown at the top of the dialog. */
    var dialogTitle: String? = null

    /** The text for the optional action button in the dialog. */
    var dialogButtonText: String? = null

    /** The icon for the optional action button in the dialog. */
    var dialogButtonIcon: Drawable? = null

    /** The unique key of the preference that is currently showing the dialog. */
    var preferenceKey: String? = null

    /**
     * A [LiveData] stream that emits the selected item and the preference key it belongs to.
     *
     * Multiple preferences observe this stream, but each one filters the event by the key to
     * ensure it only reacts to its own selection events.
     */
    private val _onItemSelected = MutableLiveData<Pair<String, ListDialogItemInfo>>()
    val onItemSelected: LiveData<Pair<String, ListDialogItemInfo>> = _onItemSelected

    /**
     * A map of callbacks for the dialog's optional action button, keyed by preference key.
     * This ensures that the correct listener is invoked for the correct preference.
     */
    private val dialogButtonClickedCallbacks = mutableMapOf<String, () -> Unit>()

    /**
     * Posts the selected item to the [onItemSelected] LiveData stream.
     *
     * @param item The [ListDialogItemInfo] that was selected by the user.
     */
    fun onItemSelected(item: ListDialogItemInfo) {
        preferenceKey?.let { key ->
            _onItemSelected.value = Pair(key, item)
        }
    }

    /**
     * Invokes the registered callback for the dialog's action button.
     *
     * The `preferenceKey` is used to find the correct callback to execute.
     */
    fun onDialogButtonClicked() {
        preferenceKey?.let { key ->
            dialogButtonClickedCallbacks[key]?.invoke()
        }
    }

    /**
     * Registers a callback to be invoked when the dialog's action button is clicked.
     *
     * @param key The unique key of the preference registering the callback.
     * @param callback The lambda to execute on click.
     */
    fun registerOnDialogButtonClickedCallback(key: String, callback: () -> Unit) {
        dialogButtonClickedCallbacks[key] = callback
    }

    /**
     * Unregisters the callback for a given preference key to prevent memory leaks.
     *
     * @param key The unique key of the preference whose callback should be removed.
     */
    fun unregisterOnDialogButtonClickedCallback(key: String) {
        dialogButtonClickedCallbacks.remove(key)
    }
}