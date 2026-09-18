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
package com.android.systemui.screencapture.ui

import android.content.Context
import android.view.Display
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.android.compose.PlatformButton
import com.android.compose.PlatformTextButton
import com.android.compose.dialog.AlertDialogContent
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.statusbar.phone.SystemUIDialog.DIALOG_WINDOW_TYPE
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Shows a confirmation dialog for deletion and suspends until the user confirms or cancels. */
suspend fun postRecordingConfirmDeletion(
    dialogFactory: SystemUIDialogFactory,
    context: Context,
    viewModel: DrawableLoaderViewModel,
    display: Display? = null,
): Boolean = suspendCancellableCoroutine { continuation ->
    val dialogContext: Context =
        if (display != null) {
            context.createWindowContext(display, DIALOG_WINDOW_TYPE, null)
        } else {
            context
        }
    val dialog =
        dialogFactory.create(context = dialogContext) { dialog ->
            LaunchedEffect(dialog) {
                dialog.setOnDismissListener {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            ConfirmDeletionDialogContent(
                onConfirm = {
                    if (continuation.isActive) continuation.resume(true)
                    dialog.dismiss()
                },
                onDismiss = { dialog.dismiss() },
                icon = {
                    LoadingIcon(
                        loadIcon(
                                viewModel = viewModel,
                                resId = R.drawable.ic_screenshot_delete,
                                contentDescription = null,
                            )
                            .value
                    )
                },
            )
        }
    dialog.window?.setType(DIALOG_WINDOW_TYPE)
    dialog.show()
    continuation.invokeOnCancellation { dialog.dismiss() }
}

/** Reusable Composable content for the body of the confirm deletion dialog. */
@Composable
private fun ConfirmDeletionDialogContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    AlertDialogContent(
        title = { Text(stringResource(R.string.screen_record_delete_dialog_title)) },
        content = { Text(stringResource(R.string.screen_record_delete_dialog_content)) },
        icon = icon,
        positiveButton = {
            PlatformButton(onClick = onConfirm) {
                Text(stringResource(id = R.string.screen_record_delete))
            }
        },
        negativeButton = {
            PlatformTextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        },
    )
}
