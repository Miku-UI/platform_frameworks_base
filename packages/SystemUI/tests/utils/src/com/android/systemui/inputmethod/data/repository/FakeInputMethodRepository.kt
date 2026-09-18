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

package com.android.systemui.inputmethod.data.repository

import android.os.UserHandle
import android.view.inputmethod.InputMethodManager.IMPickerEntryPoint
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.inputmethod.data.model.InputMethodModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@SysUISingleton
class FakeInputMethodRepository : InputMethodRepository {

    private val selectedSubtypeId = MutableStateFlow<Int?>(null)

    private var usersToEnabledInputMethods: MutableMap<Int, Flow<InputMethodModel>> = mutableMapOf()

    var selectedInputMethodSubtypes = listOf<InputMethodModel.Subtype>()

    /**
     * The entry point where the input method picker dialog was shown, or `null` if the dialog was
     * not shown.
     */
    @IMPickerEntryPoint var inputMethodPickerShownEntryPoint: Int? = null

    /**
     * The display ID on which the input method picker dialog was shown, or `null` if the dialog was
     * not shown.
     */
    var inputMethodPickerShownDisplayId: Int? = null

    fun setSelectedInputMethodSubtypeId(subtypeId: Int?) {
        selectedSubtypeId.value = subtypeId
    }

    fun setEnabledInputMethods(userId: Int, vararg enabledInputMethods: InputMethodModel) {
        usersToEnabledInputMethods[userId] = enabledInputMethods.asFlow()
    }

    override suspend fun enabledInputMethods(
        user: UserHandle,
        fetchSubtypes: Boolean
    ): Flow<InputMethodModel> {
        return usersToEnabledInputMethods[user.identifier] ?: flowOf()
    }

    override suspend fun selectedInputMethodSubtypes(
        user: UserHandle,
    ): List<InputMethodModel.Subtype> = selectedInputMethodSubtypes


    override fun selectedInputMethodSubtype(user: UserHandle): Flow<InputMethodModel.Subtype?> {
        return selectedSubtypeId.map { id: Int? ->
            id?.let { selectedInputMethodSubtypes.find { it.subtypeId == id } }
        }
    }

    override suspend fun showInputMethodPicker(
        showAuxiliarySubtypes: Boolean,
        @IMPickerEntryPoint entryPoint: Int,
        displayId: Int,
    ) {
        inputMethodPickerShownEntryPoint = entryPoint
        inputMethodPickerShownDisplayId = displayId
    }

    override suspend fun toggleInputMethodPicker(
        showAuxiliarySubtypes: Boolean,
        @IMPickerEntryPoint entryPoint: Int,
        displayId: Int,
    ) {
        if (inputMethodPickerShownDisplayId == null) {
            showInputMethodPicker(showAuxiliarySubtypes, entryPoint, displayId)
        } else {
            hideInputMethodPicker(displayId)
        }
    }

    override suspend fun hideInputMethodPicker(displayId: Int) {
        inputMethodPickerShownEntryPoint = null
        inputMethodPickerShownDisplayId = null
    }
}
