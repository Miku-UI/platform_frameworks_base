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

package com.android.wm.shell.apptoweb

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Binder
import android.util.Slog
import android.view.Display
import android.view.IWindow
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager.LayoutParams
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
import android.view.WindowlessWindowManager
import android.window.TaskConstants
import androidx.annotation.VisibleForTesting
import com.android.window.flags.Flags
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.compatui.DialogAnimationController
import com.android.wm.shell.compatui.DialogContainerSupplier
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import java.util.function.Supplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

typealias SurfaceControlViewHostFactory =
    (Context, Display, WindowlessWindowManager, String) -> SurfaceControlViewHost

/** Base window manager for the open by default settings dialog */
abstract class BaseOpenByDefaultDialog<T>(
    protected val context: Context,
    private val userContext: Context,
    protected val transitions: Transitions,
    protected val taskInfo: RunningTaskInfo,
    private val taskSurface: SurfaceControl,
    private val displayController: DisplayController,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    private val surfaceControlTransactionSupplier: Supplier<SurfaceControl.Transaction>,
    @ShellMainThread private val mainScope: CoroutineScope,
    private val listener: DialogLifecycleListener,
    animationController: DialogAnimationController<T>? = null,
    private val surfaceControlViewHostFactory: SurfaceControlViewHostFactory = { c, d, wwm, s ->
        SurfaceControlViewHost(c, d, wwm, s)
    },
) where T : DialogContainerSupplier, T : View {
    @get:VisibleForTesting var viewHost: SurfaceControlViewHost? = null
    private lateinit var dialogWindowManager: DialogWindowManager

    protected val domainVerificationManager =
        checkNotNull(userContext.getSystemService(DomainVerificationManager::class.java)) {
            "Expected non-null DomainVerificationManager"
        }
    protected val packageName =
        checkNotNull(taskInfo.baseActivity) { "Expected non-null base activity" }.packageName

    private var loadAppInfoJob: Job? = null

    protected lateinit var dialog: T
    private val animationController =
        animationController ?: DialogAnimationController<T>(context, dialogName)

    protected abstract val dialogName: String

    private var isCloseRequested = false

    init {
        createDialog()
        listener.onDialogCreated()
        loadAppInfoJob =
            mainScope.launch {
                if (!isActive) return@launch
                val (name, icon) = taskResourceLoader.getNameAndHeaderIcon(taskInfo)
                bindAppInfo(icon, name)
            }
    }

    protected fun showDialogWindow() {
        if (isCloseRequested) {
            // This dialog was already requested to be closed before being created.
            Slog.w(TAG, "showDialogWindow: Dialog was already requested to be closed.")
            return
        }
        val display = displayController.getDisplay(taskInfo.displayId)
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        val lp =
            LayoutParams(
                    taskBounds.width(),
                    taskBounds.height(),
                    TYPE_APPLICATION_PANEL,
                    FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    token = Binder()
                    title = "$dialogName of task=${taskInfo.taskId}"
                    setTrustedOverlay()
                }

        dialogWindowManager = DialogWindowManager(taskInfo.configuration)
        viewHost =
            surfaceControlViewHostFactory
                .invoke(context, display, dialogWindowManager, "Dialog")
                .apply { setView(dialog, lp) }

        if (
            Flags.enableOpenByDefaultDialogFocusBugfix() ||
                Flags.useInputReportedFocusForAccessibility()
        ) {
            checkNotNull(viewHost) { "Expected non-null view host" }.requestInputFocus(true)
        }
        animationController.startEnterAnimation(dialog, this::onAnimationEnded)
    }

    protected fun setDefaultLinkHandlingSetting(allowed: Boolean) {
        try {
            domainVerificationManager.setDomainVerificationLinkHandlingAllowed(packageName, allowed)
        } catch (e: NameNotFoundException) {
            Slog.e(
                TAG,
                "Failed to change link handling policy due to the package name is not found: $e",
            )
        }
    }

    protected fun closeMenu() {
        isCloseRequested = true
        if (
            Flags.useInputReportedFocusForAccessibility() ||
                Flags.enableOpenByDefaultDialogFocusBugfix()
        ) {
            viewHost?.requestInputFocus(false)
        }
        loadAppInfoJob?.cancel()
        animationController.startExitAnimation(dialog) {
            // Release the host and manager after the exit animation
            viewHost?.release()
            dialogWindowManager.release()
            listener.onDialogDismissed()
        }
    }

    /** Relayout the dialog to the new task bounds. */
    fun relayout(taskInfo: RunningTaskInfo) {
        if (!::dialogWindowManager.isInitialized) return
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        dialogWindowManager.relayout(taskBounds)
        viewHost?.relayout(taskBounds.width(), taskBounds.height())
    }

    /** Dismiss dialog and set it to null, so it that it will be re-created on the next opening. */
    fun dismiss() = closeMenu()

    protected abstract fun createDialog()

    protected abstract fun onAnimationEnded()

    protected abstract fun bindAppInfo(appIconBitmap: Bitmap, appName: CharSequence)

    /** Handles showing, positioning and tearing down the dialog surface */
    private inner class DialogWindowManager(config: Configuration) :
        WindowlessWindowManager(
            config,
            /* rootSurface= */ null,
            /* hostInputTransferToken= */ null,
        ) {

        private var leash: SurfaceControl? = null

        override fun getParentSurface(window: IWindow, attrs: LayoutParams): SurfaceControl {
            val newLeash =
                SurfaceControl.Builder()
                    .setContainerLayer()
                    .setName("${dialogName}Leash")
                    .setParent(taskSurface)
                    .setCallsite("$dialogName.getParentSurface")
                    .build()
            leash = newLeash

            val t = surfaceControlTransactionSupplier.get()
            val taskBounds = taskInfo.configuration.windowConfiguration.bounds
            t.setPosition(newLeash, /* x= */ 0f, /* y= */ 0f)
                .setWindowCrop(newLeash, taskBounds.width(), taskBounds.height())
                .setLayer(newLeash, TaskConstants.TASK_CHILD_LAYER_SETTINGS_DIALOG)
                .show(newLeash)
                .apply()

            return newLeash
        }

        fun relayout(taskBounds: Rect) {
            leash?.let {
                surfaceControlTransactionSupplier
                    .get()
                    .setWindowCrop(it, taskBounds.width(), taskBounds.height())
                    .apply()
            }
        }

        fun release() {
            leash?.let { surfaceControlTransactionSupplier.get().remove(it).apply() }
            leash = null
        }
    }

    companion object {
        private const val TAG = "BaseOpenByDefaultDialog"
    }
}
