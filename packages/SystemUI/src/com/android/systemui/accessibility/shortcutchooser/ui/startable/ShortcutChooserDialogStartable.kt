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

package com.android.systemui.accessibility.shortcutchooser.ui.startable

import android.view.accessibility.Flags as AccessibilityFlags
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.CoreStartable
import com.android.systemui.Flags as SystemUIFlags
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.ui.composable.NavBarMoreOptionsDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.QuickAccessDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.ServiceWarningDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.ShortcutEditorDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.ShortcutPickerDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.composable.TopRowKeyTutorialDialogContent
import com.android.systemui.accessibility.shortcutchooser.ui.viewmodel.ShortcutChooserDialogViewModel
import com.android.systemui.accessibility.shortcutchooser.ui.viewmodel.ShortcutChooserDialogViewModel.DialogType
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.statusbar.phone.ComponentSystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@SysUISingleton
class ShortcutChooserDialogStartable
@Inject
constructor(
    private val viewModelFactory: ShortcutChooserDialogViewModel.Factory,
    private val dialogFactory: SystemUIDialogFactory,
    @param:Application private val applicationScope: CoroutineScope,
) : CoreStartable {

    private val viewModel = viewModelFactory.create()

    private var dialogInstance: ComponentSystemUIDialog? = null
    private var warningDialogInstance: ComponentSystemUIDialog? = null

    override fun start() {
        if (
            !AccessibilityFlags.enableA11yTopRowShortcut() &&
                !SystemUIFlags.launchAccessibilityQuickAccessDialogPermission()
        ) {
            return
        }

        with(applicationScope) {
            launchTraced { observeDialogState() }
            launchTraced { observeWarningDialogState() }
            launchTraced { viewModel.activate() }
        }
    }

    private suspend fun observeDialogState() {
        viewModel.dialogType.collect { dialogType ->
            if (dialogType == DialogType.NONE) {
                dialogInstance?.dismiss()
                dialogInstance = null
            } else if (dialogInstance == null) {
                createDialog()
            }
        }
    }

    private suspend fun observeWarningDialogState() {
        viewModel.warningDialogTarget.collect { target ->
            if (target == null) {
                warningDialogInstance?.dismiss()
                warningDialogInstance = null
            } else if (warningDialogInstance == null) {
                showWarningDialog(target)
            }
        }
    }

    private fun createDialog() {
        val dialogRequest = viewModel.dialogRequest.value
        val dialogContext = viewModel.getDialogContextByDisplayId(dialogRequest.displayId)
        val displayId = dialogContext.displayId

        // Ensure the dialog is shown on the display where the shortcut was triggered.
        dialogInstance =
            dialogFactory
                .create(context = dialogContext) { dialog ->
                    val dialogType by viewModel.dialogType.collectAsStateWithLifecycle()
                    val shortcutType = dialogRequest.shortcutType

                    when (dialogType) {
                        DialogType.TUTORIAL -> {
                            TopRowKeyTutorialDialogContent(
                                onAddFeaturesClick = viewModel::showEditDialog,
                                onCancelClick = viewModel::dismissDialog,
                            )
                        }
                        DialogType.EDIT_TARGETS -> {
                            val allTargets by
                                remember(shortcutType) {
                                        viewModel.getAllAccessibilityTargets(shortcutType)
                                    }
                                    .collectAsStateWithLifecycle(emptyList())
                            ShortcutEditorDialogContent(
                                shortcutType,
                                targets = allTargets,
                                onDoneClick = {
                                    with(applicationScope) {
                                        launchTraced {
                                            viewModel.onEditTargetsDoneClick(shortcutType)
                                        }
                                    }
                                },
                                onTargetToggled = { target ->
                                    viewModel.enableShortcutForTarget(
                                        !target.isAssigned,
                                        shortcutType,
                                        target.targetName,
                                    )
                                },
                            )
                        }
                        DialogType.TOGGLE_TARGETS -> {
                            val assignedTargets by
                                remember(shortcutType) {
                                        viewModel.getAssignedAccessibilityTargets(shortcutType)
                                    }
                                    .collectAsStateWithLifecycle(emptyList())
                            val isEditButtonVisible by
                                viewModel.isEditButtonVisible.collectAsStateWithLifecycle(false)
                            ShortcutPickerDialogContent(
                                targets = assignedTargets,
                                showEditButton = isEditButtonVisible,
                                onEditClick = viewModel::showEditDialog,
                                onDoneClick = viewModel::dismissDialog,
                                onTargetClick = { target ->
                                    viewModel.performAccessibilityShortcut(
                                        displayId,
                                        shortcutType,
                                        target.targetName,
                                    )
                                    viewModel.dismissDialog()
                                },
                            )
                        }
                        DialogType.QUICK_ACCESS -> {
                            val allTargets by
                                remember(shortcutType) {
                                        viewModel.getAllAccessibilityTargets(shortcutType)
                                    }
                                    .collectAsStateWithLifecycle(emptyList())
                            QuickAccessDialogContent(
                                onDoneClick = { viewModel.dismissDialog() },
                                onTargetClick = { target ->
                                    if (!viewModel.showWarningDialogIfNeeded(target)) {
                                        viewModel.performAccessibilityShortcut(
                                            displayId,
                                            shortcutType,
                                            target.targetName,
                                        )
                                        viewModel.dismissDialog()
                                    }
                                },
                                targets = allTargets,
                            )
                        }
                        DialogType.NAV_BAR_CHOOSER -> {
                            val assignedTargets by
                                remember(shortcutType) {
                                        viewModel.getAssignedAccessibilityTargets(shortcutType)
                                    }
                                    .collectAsStateWithLifecycle(emptyList())

                            NavBarMoreOptionsDialogContent(
                                targets = assignedTargets,
                                selectedTarget = viewModel.accessibilityButtonTargetComponent,
                                onTargetSelected = { target ->
                                    with(applicationScope) {
                                        launchTraced {
                                            viewModel.onNavBarTargetSelected(target.targetName)
                                        }
                                    }
                                },
                                onDoneClick = { viewModel.dismissDialog() },
                            )
                        }
                        else -> {}
                    }
                }
                .apply {
                    setOnDismissListener { viewModel.dismissDialog() }
                    show()
                }
    }

    private fun showWarningDialog(target: AccessibilityTargetModel) {
        viewModel.getAccessibilityServiceInfo(target)?.let { info ->
            warningDialogInstance =
                dialogFactory
                    .create {
                        ServiceWarningDialogContent(
                            info,
                            onAllowClick = {
                                viewModel.allowUntrustedService(target)
                                viewModel.dismissWarningDialog()
                            },
                            onDenyClick = {
                                viewModel.denyUntrustedService(target)
                                viewModel.dismissWarningDialog()
                            },
                        )
                    }
                    .apply {
                        setOnDismissListener { viewModel.dismissWarningDialog() }
                        show()
                    }
        }
    }
}
