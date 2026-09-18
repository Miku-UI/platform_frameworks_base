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
import android.graphics.drawable.Icon
import android.net.Uri
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.ActionButtonGroupItem
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.PostCaptureToastBar
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.PostRecordSnackbarDialogs
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingActionsViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingImmediateVideoViewModel
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialog.DIALOG_WINDOW_TYPE
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PostRecordingShelf
@AssistedInject
constructor(
    @Assisted private val uri: Uri,
    @Assisted private val thumbnail: Icon?,
    @Assisted private val display: Display,
    @Assisted private val notificationId: Int,
    @Application private val context: Context,
    private val dialogFactory: SystemUIDialogFactory,
    private val actionsViewModelFactory: PostRecordingActionsViewModel.Factory,
    private val videoViewModelFactory: PostRecordingImmediateVideoViewModel.Factory,
    private val postRecordSnackbarDialogs: PostRecordSnackbarDialogs,
    private val accessibilityManager: AccessibilityManager,
) {
    private val visibleState = MutableTransitionState(false)
    private val dialog: SystemUIDialog =
        dialogFactory
            .create(
                context = context.createWindowContext(display, DIALOG_WINDOW_TYPE, null),
                theme = R.style.Theme_SystemUI_Dialog,
            ) { dialogInstance ->
                DialogContent(
                    uri = uri,
                    thumbnail = thumbnail,
                    notificationId = notificationId,
                    window = dialogInstance.window,
                )
            }
            .apply {
                setupWindow(window!!)
                setOnDismissListener { visibleState.targetState = false }
            }

    private fun setupWindow(window: Window) {
        val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val horizontalGravity = if (isRtl) Gravity.RIGHT else Gravity.LEFT

        window.attributes =
            window.attributes.apply {
                title = "PostRecordingShelf"
                gravity = Gravity.BOTTOM or horizontalGravity
            }
        with(window) {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setType(DIALOG_WINDOW_TYPE)
            setWindowAnimations(-1)
        }
    }

    @Composable
    private fun DialogContent(uri: Uri, thumbnail: Icon?, notificationId: Int, window: Window?) {
        var isConfirmDeletionDialogShowing by remember { mutableStateOf(false) }
        if (!visibleState.targetState && visibleState.isIdle) {
            SideEffect {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }

        val coroutineScope = rememberCoroutineScope()

        var timeoutJob by remember { mutableStateOf<Job?>(null) }

        fun resetTimeout() {
            timeoutJob?.cancel()
            timeoutJob = coroutineScope.launch { scheduleTimeout() }
        }

        window!!.decorView.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun onRequestSendAccessibilityEvent(
                    host: ViewGroup,
                    child: View,
                    event: AccessibilityEvent,
                ): Boolean {
                    if (event.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                        resetTimeout()
                    }
                    return super.onRequestSendAccessibilityEvent(host, child, event)
                }
            }

        LaunchedEffect(visibleState.targetState, isConfirmDeletionDialogShowing) {
            if (visibleState.targetState && !isConfirmDeletionDialogShowing) {
                resetTimeout()
            } else {
                // If the deletion dialog shows, stop the timer entirely
                timeoutJob?.cancel()
            }
        }

        val actionsViewModel =
            rememberViewModel("PostRecordingShelf#viewModel") {
                actionsViewModelFactory.create(uri, display.displayId)
            }
        val videoViewModel =
            rememberViewModel("PostRecordingShelf#viewModel") {
                videoViewModelFactory.create(uri, notificationId)
            }
        val parentUri = actionsViewModel.parentUri
        val shareIcon =
            loadIcon(
                    viewModel = actionsViewModel,
                    resId = R.drawable.ic_screenshot_share,
                    contentDescription =
                        ContentDescription.Loaded(
                            stringResource(
                                R.string.screen_capture_post_recording_shelf_share_button_a11y
                            )
                        ),
                )
                .value
        val folderIcon =
            loadIcon(
                    viewModel = actionsViewModel,
                    resId = R.drawable.ic_screen_capture_folder,
                    contentDescription =
                        ContentDescription.Loaded(
                            stringResource(
                                R.string.screen_capture_post_recording_shelf_folder_button_a11y
                            )
                        ),
                )
                .value
        val deleteIcon =
            loadIcon(
                    viewModel = actionsViewModel,
                    resId = R.drawable.ic_screenshot_delete,
                    contentDescription =
                        ContentDescription.Loaded(
                            stringResource(
                                R.string.screen_capture_post_recording_shelf_delete_button_a11y
                            )
                        ),
                )
                .value
        val actionButtonItems =
            remember(parentUri) {
                buildList {
                    add(
                        ActionButtonGroupItem(
                            icon = shareIcon,
                            onClick = {
                                actionsViewModel.share()
                                hide()
                            },
                        )
                    )

                    if (parentUri != null) {
                        add(
                            ActionButtonGroupItem(
                                icon = folderIcon,
                                onClick = {
                                    actionsViewModel.openInFolder()
                                    hide()
                                },
                            )
                        )
                    }

                    add(
                        ActionButtonGroupItem(
                            icon = deleteIcon,
                            onClick = {
                                actionsViewModel.executeDismissingKeyguard {
                                    coroutineScope.launch {
                                        isConfirmDeletionDialogShowing = true
                                        if (
                                            postRecordingConfirmDeletion(
                                                dialogFactory,
                                                context,
                                                actionsViewModel,
                                                display,
                                            )
                                        ) {
                                            hide()
                                            postRecordSnackbarDialogs.showVideoDeleted(
                                                uri = videoViewModel.recording.uri,
                                                thumbnail = videoViewModel.recording.thumbnail,
                                                notificationId = videoViewModel.notificationId,
                                                display = display,
                                            )
                                        }
                                        isConfirmDeletionDialogShowing = false
                                    }
                                }
                            },
                        )
                    )
                }
            }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

        Box(
            modifier =
                Modifier.fillMaxSize()
                    .clickable(onClick = { hide() }, indication = null, interactionSource = null)
                    .safeDrawingPadding(),
            contentAlignment = Alignment.BottomStart,
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter =
                    fadeIn(animationSpec = spring()) +
                        slideInHorizontally(
                            animationSpec = spring(),
                            initialOffsetX = { it -> if (isRtl) it else -it },
                        ),
                exit =
                    fadeOut(animationSpec = spring()) +
                        slideOutHorizontally(
                            animationSpec = spring(),
                            targetOffsetX = { it -> if (isRtl) it else -it },
                        ),
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
                    PostRecordingThumbnail(
                        viewmodel = actionsViewModel,
                        preview = thumbnail?.bitmap?.asImageBitmap(),
                        modifier =
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .border(4.dp, MaterialTheme.colorScheme.surfaceBright)
                                .width(200.dp)
                                .height(128.dp)
                                .clickable {
                                    actionsViewModel.view()
                                    hide()
                                },
                    )
                    PostCaptureToastBar(
                        actionButtonGroup = actionButtonItems,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    fun show() {
        if (!dialog.isShowing) {
            visibleState.targetState = true
            dialog.show()
        }
    }

    fun hide() {
        if (dialog.isShowing) {
            visibleState.targetState = false
        }
    }

    @Composable
    private fun PostRecordingThumbnail(
        viewmodel: DrawableLoaderViewModel,
        preview: ImageBitmap?,
        modifier: Modifier = Modifier,
    ) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceBright).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription =
                        stringResource(R.string.screen_capture_post_recording_shelf_thumbnail_a11y),
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier =
                        Modifier.matchParentSize()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(12.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIcon(
                        loadIcon(
                                viewModel = viewmodel,
                                resId = R.drawable.ic_screen_capture_movie,
                                contentDescription = null,
                            )
                            .value,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }

    private suspend fun scheduleTimeout() {
        val recommendedTimeout =
            accessibilityManager.getRecommendedTimeoutMillis(
                DEFAULT_TIMEOUT.inWholeMilliseconds.toInt(),
                AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS,
            )

        coroutineScope {
            launch {
                delay(recommendedTimeout.toLong())
                hide()
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            uri: Uri,
            thumbnail: Icon?,
            display: Display,
            notificationId: Int,
        ): PostRecordingShelf
    }

    companion object {
        private val DEFAULT_TIMEOUT = 6.seconds
    }
}
