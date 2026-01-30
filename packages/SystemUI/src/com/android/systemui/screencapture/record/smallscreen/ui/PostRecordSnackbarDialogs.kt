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

package com.android.systemui.screencapture.record.smallscreen.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModelImpl
import com.android.systemui.screencapture.record.smallscreen.ui.compose.PostRecordSnackbar
import com.android.systemui.screencapture.record.smallscreen.ui.compose.SnackbarVisualsWithIcon
import com.android.systemui.statusbar.phone.DialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class PostRecordSnackbarDialogs
@Inject
constructor(
    @Application private val context: Context,
    private val dialogFactory: SystemUIDialogFactory,
    private val drawableViewModel: DrawableLoaderViewModelImpl,
    private val activityStarter: ActivityStarter,
) {

    fun showVideoSaved() {
        showSnackbar(
            SnackbarVisualsWithIcon(
                iconRes = R.drawable.ic_sync_saved_locally,
                message = context.getString(R.string.screen_record_video_saved),
            )
        )
    }

    fun showVideoDeleted(uri: Uri) {
        showSnackbar(
            visuals =
                SnackbarVisualsWithIcon(
                    iconRes = R.drawable.ic_screenshot_delete,
                    message = context.getString(R.string.screen_record_video_deleted),
                    actionLabel = context.getString(R.string.screen_record_undo),
                ),
            onActionPerformed = {
                activityStarter.startActivity(
                    SmallScreenPostRecordingActivity.getStartingIntent(context, uri),
                    true,
                )
            },
            onDismissed = {
                val file = uri.path?.let(::File)
                with(file ?: return@showSnackbar) {
                    if (exists()) {
                        delete()
                    }
                }
            },
        )
    }

    private fun showSnackbar(
        visuals: SnackbarVisualsWithIcon,
        onActionPerformed: (() -> Unit)? = null,
        onDismissed: (() -> Unit)? = null,
    ) {
        val actionHandler =
            ActionHandler(onActionPerformed = onActionPerformed, onDismissed = onDismissed)
        dialogFactory
            .create(
                theme = R.style.ScreenCapture_PostRecord_SnackbarDialog,
                dialogDelegate = SnackbarDialogDelegate { actionHandler.notifyDismiss() },
            ) { dialog ->
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(visuals, onActionPerformed) {
                    when (snackbarHostState.showSnackbar(visuals)) {
                        SnackbarResult.ActionPerformed -> actionHandler.notifyAction()
                        SnackbarResult.Dismissed -> actionHandler.notifyDismiss()
                    }
                    dialog.dismissWithoutAnimation()
                }
                Box(contentAlignment = Alignment.Center) {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        PostRecordSnackbar(viewModel = drawableViewModel, data = data)
                    }
                }
            }
            .show()
    }
}

/** Ensures that only either [onActionPerformed] or [onDismissed] is called */
private class ActionHandler(
    private val onActionPerformed: (() -> Unit)? = null,
    private val onDismissed: (() -> Unit)? = null,
) {
    private val notified = AtomicBoolean(false)

    fun notifyDismiss() {
        if (notified.compareAndSet(false, true)) {
            onDismissed?.invoke()
        }
    }

    fun notifyAction() {
        if (notified.compareAndSet(false, true)) {
            onActionPerformed?.invoke()
        }
    }
}

private class SnackbarDialogDelegate(private val onDismissed: () -> Unit) :
    DialogDelegate<SystemUIDialog> {

    override fun onCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        super.onCreate(dialog, savedInstanceState)
        dialog.setOnDismissListener { onDismissed() }
        with(dialog.window!!) {
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setWindowAnimations(-1)
        }
    }

    override fun getWidth(dialog: SystemUIDialog): Int = WindowManager.LayoutParams.WRAP_CONTENT

    override fun getHeight(dialog: SystemUIDialog): Int = WindowManager.LayoutParams.WRAP_CONTENT
}
