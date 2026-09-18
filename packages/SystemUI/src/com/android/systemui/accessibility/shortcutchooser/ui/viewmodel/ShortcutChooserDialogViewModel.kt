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

package com.android.systemui.accessibility.shortcutchooser.ui.viewmodel

import android.content.Context
import android.util.Log
import android.view.Display.INVALID_DISPLAY
import android.view.accessibility.Flags
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.accessibility.shortcutchooser.domain.interactor.ShortcutChooserDialogInteractor
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.DialogRequestModel
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.domain.interactor.KeyguardInteractor
import com.android.systemui.lifecycle.HydratedActivatable
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter

/** ViewModel for the set of Shortcut Chooser dialogs. */
class ShortcutChooserDialogViewModel
@AssistedInject
constructor(
    @param:Application private val applicationContext: Context,
    private val interactor: ShortcutChooserDialogInteractor,
    private val keyguardInteractor: KeyguardInteractor,
) : HydratedActivatable() {

    enum class DialogType {
        NONE,
        TUTORIAL,
        EDIT_TARGETS,
        TOGGLE_TARGETS,
        QUICK_ACCESS,
        NAV_BAR_CHOOSER,
    }

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced { interactor.dialogRequest.collect { onDialogRequest(it) } }
            launchTraced {
                dialogType
                    .filter { it == DialogType.TOGGLE_TARGETS }
                    .collect { updateEditButtonVisibility() }
            }
            awaitCancellation()
        }
    }

    private val _dialogRequest =
        MutableStateFlow(DialogRequestModel(UserShortcutType.DEFAULT, INVALID_DISPLAY))
    /**
     * Latest snapshot of the dialog request.
     *
     * Kept up to date only while the view model is activated. Should only be read after activation
     * and at least one dialog request has been received.
     */
    val dialogRequest = _dialogRequest.asStateFlow()

    private val _dialogType = MutableStateFlow(DialogType.NONE)
    /** The type of dialog that should be shown. */
    val dialogType = _dialogType.asStateFlow()

    private val _warningDialogTarget = MutableStateFlow<AccessibilityTargetModel?>(null)
    /** The target to show the warning dialog for. */
    val warningDialogTarget = _warningDialogTarget.asStateFlow()

    fun getAllAccessibilityTargets(@UserShortcutType shortcutType: Int) =
        interactor.getAllAccessibilityTargets(shortcutType)

    fun getAssignedAccessibilityTargets(@UserShortcutType shortcutType: Int) =
        interactor.getAssignedAccessibilityTargets(shortcutType)

    fun getDialogContextByDisplayId(dialogDisplayId: Int) =
        interactor.getDialogContextByDisplayId(
            dialogDisplayId = dialogDisplayId,
            applicationContext = applicationContext,
        )

    val accessibilityButtonTargetComponent: String? by
        interactor.accessibilityButtonTargetComponent.hydratedStateOf(null)

    suspend fun setAccessibilityButtonTargetComponent(target: String) =
        interactor.setAccessibilityButtonTargetComponent(target)

    fun enableShortcutForTarget(
        enable: Boolean,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = interactor.enableShortcutForTarget(enable, shortcutType, targetName)

    fun performAccessibilityShortcut(
        displayId: Int,
        @UserShortcutType shortcutType: Int,
        targetName: String,
    ) = interactor.performAccessibilityShortcut(displayId, shortcutType, targetName)

    suspend fun onNavBarTargetSelected(targetName: String) {
        interactor.setAccessibilityButtonTargetComponent(targetName)
    }

    fun dismissDialog() {
        _dialogType.value = DialogType.NONE
    }

    fun showEditDialog() {
        _dialogType.value = DialogType.EDIT_TARGETS
    }

    suspend fun onEditTargetsDoneClick(@UserShortcutType shortcutType: Int) {
        val assignedTargetsCount = interactor.getAssignedAccessibilityTargetsCount(shortcutType)
        if (assignedTargetsCount < 2) {
            dismissDialog()
        } else {
            _dialogType.value = DialogType.TOGGLE_TARGETS
        }
    }

    private val _isEditButtonVisible = MutableStateFlow(false)
    /** Whether the edit button should be shown on the Toggle Targets dialog. */
    val isEditButtonVisible = _isEditButtonVisible.asStateFlow()

    private suspend fun updateEditButtonVisibility() {
        _isEditButtonVisible.value =
            interactor.isCompletedFullUser() && !keyguardInteractor.isKeyguardCurrentlyShowing()
    }

    private suspend fun onDialogRequest(requestModel: DialogRequestModel) {
        _dialogRequest.value = requestModel

        val shortcutType = requestModel.shortcutType

        if (shortcutType == UserShortcutType.SOFTWARE) {
            _dialogType.value = DialogType.NAV_BAR_CHOOSER
            return
        }

        if (shortcutType == UserShortcutType.QUICK_ACCESS) {
            if (Flags.quickAccessShortcutType()) {
                coroutineScope {
                    launchTraced { interactor.enableShortcutForAllAllowedTargets(shortcutType) }
                }
                _dialogType.value = DialogType.QUICK_ACCESS
            }
            return
        }

        val assignedTargetsCount = interactor.getAssignedAccessibilityTargetsCount(shortcutType)

        if (assignedTargetsCount == 1) {
            // The target should be directly performed without showing any dialog.
            Log.d(TAG, "No dialog for shortcut type=$shortcutType with 1 assigned target")
            return
        }

        if (shortcutType == UserShortcutType.TOP_ROW_KEY) {
            if (assignedTargetsCount == 0) {
                if (!keyguardInteractor.isKeyguardCurrentlyShowing()) {
                    _dialogType.value = DialogType.TUTORIAL
                } else {
                    Log.d(
                        TAG,
                        "No dialog for shortcut type=$shortcutType with no assigned targets on lock screen",
                    )
                }
                return
            }
        }

        if (assignedTargetsCount != 0) {
            _dialogType.value = DialogType.TOGGLE_TARGETS
        } else {
            Log.d(TAG, "No dialog for shortcut type=$shortcutType with no assigned targets")
        }
    }

    /**
     * Shows the warning dialog if the target requires a warning dialog.
     *
     * @return true if the warning dialog was shown, false otherwise.
     */
    fun showWarningDialogIfNeeded(target: AccessibilityTargetModel): Boolean {
        if (interactor.isServiceWarningRequired(target)) {
            _warningDialogTarget.value = target
            return true
        }
        return false
    }

    fun dismissWarningDialog() {
        _warningDialogTarget.value = null
    }

    fun allowUntrustedService(target: AccessibilityTargetModel) {
        enableShortcutForTarget(true, target.shortcutType, target.targetName)
    }

    fun denyUntrustedService(target: AccessibilityTargetModel) {
        enableShortcutForTarget(false, target.shortcutType, target.targetName)
    }

    fun getAccessibilityServiceInfo(target: AccessibilityTargetModel) =
        interactor.getAccessibilityServiceInfo(target)

    @AssistedFactory
    interface Factory {
        fun create(): ShortcutChooserDialogViewModel
    }

    private companion object {
        private val TAG = ShortcutChooserDialogViewModel::class.simpleName
    }
}
