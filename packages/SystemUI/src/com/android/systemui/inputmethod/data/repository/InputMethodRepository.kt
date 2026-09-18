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

import android.annotation.SuppressLint
import android.os.UserHandle
import android.provider.Settings
import android.view.inputmethod.Flags
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.IMPickerEntryPoint
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.inputmethod.data.model.InputMethodModel
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import dagger.Binds
import dagger.Module
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** Provides access to input-method related application state in the bouncer. */
interface InputMethodRepository {

    /**
     * Creates and returns a new `Flow` of installed input methods that are enabled for the
     * specified user.
     *
     * @param user The user to query.
     * @param fetchSubtypes Whether to fetch the IME Subtypes as well (requires an additional IPC
     *   call for each IME, avoid if not needed).
     * @see InputMethodManager.getEnabledInputMethodListAsUser
     */
    suspend fun enabledInputMethods(
        user: UserHandle,
        fetchSubtypes: Boolean,
    ): Flow<InputMethodModel>

    /**
     * Returns enabled subtypes for the currently selected input method.
     *
     * @param user The user to query.
     */
    suspend fun selectedInputMethodSubtypes(user: UserHandle): List<InputMethodModel.Subtype>

    /**
     * The currently selected input method subtype for the given user, or null if there is no
     * selected subtype.
     */
    fun selectedInputMethodSubtype(user: UserHandle): Flow<InputMethodModel.Subtype?>

    /**
     * Shows the system's input method picker dialog.
     *
     * @param showAuxiliarySubtypes Whether to show auxiliary input method subtypes in the list of
     *   enabled IMEs.
     * @param entryPoint The entry point where the dialog was requested from.
     * @param displayId The display ID on which to show the dialog.
     * @see InputMethodManager.showInputMethodPickerFromSystem
     */
    suspend fun showInputMethodPicker(
        showAuxiliarySubtypes: Boolean,
        @IMPickerEntryPoint entryPoint: Int,
        displayId: Int,
    )

    /**
     * Toggles the system's input method picker dialog.
     *
     * There's no guarantee that this toggle is an atomic operation. There's potential risk of a
     * race condition when concurrency is involved, or when this is invoked in quick succession.
     *
     * @param showAuxiliarySubtypes Whether to show auxiliary input method subtypes in the list of
     *   enabled IMEs.
     * @param entryPoint The entry point where the dialog was requested from.
     * @param displayId The display ID on which to show the dialog, if it is currently hidden. The
     * param is unused if the dialog is currently shown and should now be hidden.
     * @see InputMethodManager.showInputMethodPickerFromSystem
     */
    suspend fun toggleInputMethodPicker(
        showAuxiliarySubtypes: Boolean,
        @IMPickerEntryPoint entryPoint: Int,
        displayId: Int,
    )

    /**
     * Hides the system's input method picker dialog.
     *
     * @param displayId (legacy unused param)
     * @see InputMethodManager.hideInputMethodPickerFromSystem
     */
    // TODO: b/496501764 - Remove unused "displayId" param.
    suspend fun hideInputMethodPicker(displayId: Int)
}

@SysUISingleton
class InputMethodRepositoryImpl
@Inject
constructor(
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val inputMethodManager: InputMethodManager,
    private val secureSettings: SecureSettings,
) : InputMethodRepository {

    override suspend fun enabledInputMethods(
        user: UserHandle,
        fetchSubtypes: Boolean
    ): Flow<InputMethodModel> {
        return withContext(backgroundDispatcher) {
                inputMethodManager.getEnabledInputMethodListAsUser(user)
            }
            .asFlow()
            .map { inputMethodInfo ->
                InputMethodModel(
                    userId = user.identifier,
                    imeId = inputMethodInfo.id,
                    subtypes =
                        if (fetchSubtypes) {
                            enabledInputMethodSubtypes(
                                user = user,
                                imeInfo = inputMethodInfo,
                                allowsImplicitlyEnabledSubtypes = true,
                            )
                        } else {
                            listOf()
                        }
                )
            }
    }

    override suspend fun selectedInputMethodSubtypes(
        user: UserHandle,
    ): List<InputMethodModel.Subtype> {
        val selectedIme = inputMethodManager.getCurrentInputMethodInfoAsUser(user)
        return if (selectedIme == null) {
            emptyList()
        } else {
            enabledInputMethodSubtypes(
                user = user,
                imeInfo = selectedIme,
                allowsImplicitlyEnabledSubtypes = false,
            )
        }
    }

    override fun selectedInputMethodSubtype(user: UserHandle): Flow<InputMethodModel.Subtype?> {
        return secureSettings
            .observerFlow(
                user.identifier,
                Settings.Secure.DEFAULT_INPUT_METHOD,
                Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
            )
            .onStart { emit(Unit) }
            .mapLatest {
                val selectedSubtypeId =
                    try {
                        secureSettings.getIntForUser(
                            Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE,
                            user.identifier,
                        )
                    } catch (e: Settings.SettingNotFoundException) {
                        null
                    }

                selectedSubtypeId?.let { subtypeId ->
                    selectedInputMethodSubtypes(user).find { it.subtypeId == subtypeId }
                }
            }
            .flowOn(backgroundDispatcher)
    }

    @SuppressLint("MissingPermission")
    override suspend fun showInputMethodPicker(
        showAuxiliarySubtypes: Boolean,
        @IMPickerEntryPoint entryPoint: Int,
        displayId: Int,
    ) {
        withContext(backgroundDispatcher) {
            inputMethodManager.showInputMethodPickerFromSystem(
                showAuxiliarySubtypes,
                entryPoint,
                displayId,
            )
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun toggleInputMethodPicker(
        showAuxiliarySubtypes: Boolean,
        @IMPickerEntryPoint entryPoint: Int,
        displayId: Int,
    ) {
        withContext(backgroundDispatcher) {
            inputMethodManager.toggleInputMethodPickerFromSystem(
                showAuxiliarySubtypes,
                entryPoint,
                displayId,
            )
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun hideInputMethodPicker(displayId: Int) {
        withContext(backgroundDispatcher) {
            inputMethodManager.hideInputMethodPickerFromSystem(displayId)
        }
    }

    /**
     * Returns a list of enabled input method subtypes for the specified input method info.
     *
     * @param user The user to query.
     * @param imeId The ID of the input method whose subtypes list will be returned.
     * @param allowsImplicitlyEnabledSubtypes Whether to allow to return the implicitly enabled
     *   subtypes. If an input method info doesn't have enabled subtypes, the framework will
     *   implicitly enable subtypes according to the current system language.
     * @see InputMethodManager.getEnabledInputMethodSubtypeListAsUser
     */
    private suspend fun enabledInputMethodSubtypes(
        user: UserHandle,
        imeInfo: InputMethodInfo,
        allowsImplicitlyEnabledSubtypes: Boolean,
    ): List<InputMethodModel.Subtype> {
        return withContext(backgroundDispatcher) {
                inputMethodManager.getEnabledInputMethodSubtypeListAsUser(
                    imeInfo.id,
                    allowsImplicitlyEnabledSubtypes,
                    user
                )
            }
            .map {
                val icon =
                    it.iconResId
                        .takeIf { it != 0 }
                        ?.let { resId ->
                            InputMethodModel.SubtypeIcon(
                                resId = resId,
                                packageName = imeInfo.packageName,
                            )
                        }
                InputMethodModel.Subtype(
                    subtypeId = it.subtypeId,
                    isAuxiliary = it.isAuxiliary,
                    icon = icon,
                    shortLabel =
                        if (Flags.imeSubtypeShortLabel()) {
                            it.subtypeShortLabel.toString()?.ifEmpty { null }
                        } else {
                            null
                        },
                )
            }
    }
}

@Module
interface InputMethodRepositoryModule {
    @Binds fun repository(impl: InputMethodRepositoryImpl): InputMethodRepository
}
