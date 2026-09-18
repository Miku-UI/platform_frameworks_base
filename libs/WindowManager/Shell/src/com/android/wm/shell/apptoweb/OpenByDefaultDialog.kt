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

package com.android.wm.shell.apptoweb

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.DESKTOP_WINDOWING_APP_TO_WEB_CHANGE_OPEN_BY_DEFAULT_SETTINGS
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import java.util.function.Supplier
import kotlinx.coroutines.CoroutineScope

/** Window manager for the open by default settings dialog */
internal class OpenByDefaultDialog(
    context: Context,
    userContext: Context,
    transitions: Transitions,
    taskInfo: RunningTaskInfo,
    taskSurface: SurfaceControl,
    displayController: DisplayController,
    taskResourceLoader: WindowDecorTaskResourceLoader,
    surfaceControlTransactionSupplier: Supplier<SurfaceControl.Transaction>,
    @ShellMainThread mainScope: CoroutineScope,
    listener: DialogLifecycleListener,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
) :
    BaseOpenByDefaultDialog<OpenByDefaultDialogView>(
        context,
        userContext,
        transitions,
        taskInfo,
        taskSurface,
        displayController,
        taskResourceLoader,
        surfaceControlTransactionSupplier,
        mainScope,
        listener,
    ) {

    private lateinit var appIconView: ImageView
    private lateinit var appNameView: TextView

    private lateinit var openInAppButton: RadioButton
    private lateinit var openInBrowserButton: RadioButton

    private var linkHandlingAllowed: Boolean = false

    override val dialogName = TAG

    /** Creates an open by default settings dialog. */
    override fun createDialog() {
        dialog =
            LayoutInflater.from(context)
                .inflate(R.layout.open_by_default_settings_dialog, null /* root */)
                as OpenByDefaultDialogView
        appIconView = dialog.requireViewById(R.id.application_icon)
        appNameView = dialog.requireViewById(R.id.application_name)

        if (Flags.useInputReportedFocusForAccessibility()) {
            showDialogWindow()
        } else {
            // TODO: ag/34061541 - once landed, can refactor with simpler fix
            transitions.runOnIdle(this::showDialogWindow)
        }

        dialog.setDismissOnClickListener { closeMenu() }
        dialog.setConfirmButtonClickListener {
            setDefaultLinkHandlingSetting()
            // Log if user is confirming settings change
            if (isConfirmingSettingsChange()) {
                desktopModeUiEventLogger.log(
                    taskInfo,
                    DESKTOP_WINDOWING_APP_TO_WEB_CHANGE_OPEN_BY_DEFAULT_SETTINGS,
                )
            }
            closeMenu()
        }

        initializeRadioButtons()
    }

    override fun onAnimationEnded() {
        if (!Flags.useInputReportedFocusForAccessibility()) {
            dialog.post {
                dialog.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                val subHeader: TextView = dialog.requireViewById(R.id.dialog_subheader)
                subHeader.requestFocus()
                subHeader.requestAccessibilityFocus()
            }
        }
    }

    private fun initializeRadioButtons() {
        openInAppButton = dialog.requireViewById(R.id.open_in_app_button)
        openInBrowserButton = dialog.requireViewById(R.id.open_in_browser_button)

        val userState =
            getDomainVerificationUserState(domainVerificationManager, packageName) ?: return
        linkHandlingAllowed = userState.isLinkHandlingAllowed
        openInAppButton.isChecked = linkHandlingAllowed
        openInBrowserButton.isChecked = !linkHandlingAllowed
    }

    private fun isConfirmingSettingsChange() =
        if (linkHandlingAllowed) {
            openInBrowserButton.isChecked
        } else {
            openInAppButton.isChecked
        }

    private fun setDefaultLinkHandlingSetting() =
        setDefaultLinkHandlingSetting(openInAppButton.isChecked)

    override fun bindAppInfo(appIconBitmap: Bitmap, appName: CharSequence) {
        appIconView.setImageBitmap(appIconBitmap)
        appNameView.text = appName
    }

    companion object {
        private const val TAG = "OpenByDefaultDialog"
    }
}
