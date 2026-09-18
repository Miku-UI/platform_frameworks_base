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
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.android.internal.logging.UiEventLogger
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.screencapture.record.shared.model.ScreenRecordEvent
import com.android.systemui.screencapture.record.smallscreen.ui.compose.PostRecordSnackbar
import com.android.systemui.screencapture.record.smallscreen.ui.compose.SnackbarVisualsWithIcon
import com.android.systemui.screenrecord.notification.ScreenRecordingServiceNotificationInteractor
import com.android.systemui.statusbar.phone.DialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialog.DIALOG_WINDOW_TYPE
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class PostRecordSnackbarDialogs
@Inject
constructor(
    @Application private val context: Context,
    private val dialogFactory: SystemUIDialogFactory,
    private val drawableViewModel: DrawableLoaderViewModel,
    private val activityStarter: ActivityStarter,
    private val screenCaptureRecordFeaturesInteractor: ScreenCaptureRecordFeaturesInteractor,
    private val screenRecordingServiceNotificationInteractor:
        ScreenRecordingServiceNotificationInteractor,
    private val uiEventLogger: UiEventLogger,
) {

    fun showVideoSaved() {
        showSnackbar(
            SnackbarVisualsWithIcon(
                iconRes = R.drawable.ic_sync_saved_locally,
                message = context.getString(R.string.screen_record_video_saved),
            )
        )
    }

    fun showVideoDeleted(
        uri: Uri,
        notificationId: Int,
        display: Display? = null,
        thumbnail: Icon? = null,
    ) {
        showSnackbar(
            display = display,
            visuals =
                SnackbarVisualsWithIcon(
                    iconRes = R.drawable.ic_screenshot_delete,
                    message = context.getString(R.string.screen_record_video_deleted),
                    actionLabel = context.getString(R.string.screen_record_undo),
                ),
            onActionPerformed = {
                if (!screenCaptureRecordFeaturesInteractor.isLargeScreenRecordingEnabled) {
                    activityStarter.startActivity(
                        SmallScreenPostRecordingActivity.showRecording(
                            context = context,
                            videoUri = uri,
                            notificationId = notificationId,
                        ),
                        true,
                    )
                }
            },
            onDismissed = {
                context.contentResolver.delete(uri, null)
                screenRecordingServiceNotificationInteractor.cancel(notificationId)
                uiEventLogger.log(ScreenRecordEvent.SCREEN_RECORD_POST_RECORDING_DELETE)
            },
        )
    }

    private fun showSnackbar(
        visuals: SnackbarVisualsWithIcon,
        onActionPerformed: (() -> Unit)? = null,
        onDismissed: (() -> Unit)? = null,
        display: Display? = null,
    ) {
        val actionHandler =
            ActionHandler(onActionPerformed = onActionPerformed, onDismissed = onDismissed)
        val dialogContext: Context =
            if (display != null) {
                context.createWindowContext(display, DIALOG_WINDOW_TYPE, null)
            } else {
                context
            }

        val dialog =
            dialogFactory.create(
                context = dialogContext,
                theme = R.style.ScreenCapture_PostRecord_SnackbarDialog,
                dialogDelegate =
                    SnackbarDialogDelegate(screenCaptureRecordFeaturesInteractor) {
                        actionHandler.notifyDismiss()
                    },
            ) { dialog ->
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(visuals, onActionPerformed) {
                    when (snackbarHostState.showSnackbar(visuals)) {
                        SnackbarResult.ActionPerformed -> actionHandler.notifyAction()
                        SnackbarResult.Dismissed -> actionHandler.notifyDismiss()
                    }
                    dialog.dismissWithoutAnimation()
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier.padding(
                            WindowInsets.safeDrawing.asPaddingValues().coerceAllAtLeast(24.dp)
                        ),
                ) {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        PostRecordSnackbar(viewModel = drawableViewModel, data = data)
                    }
                }
            }
        dialog.window?.setType(DIALOG_WINDOW_TYPE)
        dialog.show()
    }
}

@Composable
private fun PaddingValues.coerceAllAtLeast(min: Dp): PaddingValues =
    PaddingValues(
        start = calculateStartPadding(LocalLayoutDirection.current).coerceAtLeast(min),
        top = calculateTopPadding().coerceAtLeast(min),
        end = calculateEndPadding(LocalLayoutDirection.current).coerceAtLeast(min),
        bottom = calculateBottomPadding().coerceAtLeast(min),
    )

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

private class SnackbarDialogDelegate(
    private val featuresInteractor: ScreenCaptureRecordFeaturesInteractor,
    private val onDismissed: () -> Unit,
) : DialogDelegate<SystemUIDialog> {

    override fun onCreate(dialog: SystemUIDialog, savedInstanceState: Bundle?) {
        super.onCreate(dialog, savedInstanceState)
        dialog.setOnDismissListener { onDismissed() }
        with(dialog.window!!) {
            val windowGravity =
                if (featuresInteractor.isLargeScreenRecordingEnabled) {
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                } else {
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
            setGravity(windowGravity)
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
