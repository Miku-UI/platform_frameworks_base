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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MimeTypes
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.PlatformTextButton
import com.android.compose.theme.PlatformTheme
import com.android.systemui.dialog.ui.composable.AlertDialogContent
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.player.ui.compose.VideoPlayer
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.PostRecordingViewModel
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class SmallScreenPostRecordingActivity
@Inject
constructor(
    private val videoPlayer: VideoPlayer,
    private val viewModelFactory: PostRecordingViewModel.Factory,
    private val postRecordSnackbarDialogs: PostRecordSnackbarDialogs,
    private val systemUIDialogFactory: SystemUIDialogFactory,
) : ComponentActivity() {

    private val shouldShowVideoSaved: Boolean
        get() = intent.getBooleanExtra(SHOULD_SHOW_VIDEO_SAVED, SHOULD_SHOW_VIDEO_SAVED_DEFAULT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlatformTheme { Content() } }
    }

    @Composable
    private fun Content() {
        val coroutineScope = rememberCoroutineScope()
        val viewModel =
            rememberViewModel("SmallScreenPostRecordingActivity#viewModel") {
                viewModelFactory.create(intent.data ?: error("Data URI is missing"))
            }

        LaunchedEffect(shouldShowVideoSaved) {
            if (shouldShowVideoSaved) {
                intent.putExtra(SHOULD_SHOW_VIDEO_SAVED, false)
                postRecordSnackbarDialogs.showVideoSaved()
            }
        }

        val shouldUseFlatBottomBar =
            booleanResource(R.bool.screen_record_post_recording_flat_bottom_bar)
        Box(
            modifier =
                Modifier.background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!shouldUseFlatBottomBar) {
                    Spacer(modifier = Modifier.size(50.dp))
                }
                Box(modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally)) {
                    videoPlayer.Content(uri = viewModel.videoUri, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.size(32.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 24.dp).height(40.dp),
                ) {
                    val rowModifier = Modifier.weight(1f).fillMaxHeight()
                    PostRecordButton(
                        onClick = {
                            viewModel.retake()
                            finish()
                        },
                        drawableLoaderViewModel = viewModel,
                        iconRes = R.drawable.ic_arrow_back,
                        labelRes = R.string.screen_record_retake,
                        modifier = rowModifier,
                    )
                    PostRecordButton(
                        onClick = { viewModel.edit() },
                        drawableLoaderViewModel = viewModel,
                        iconRes = R.drawable.ic_edit_square,
                        labelRes = R.string.screen_record_edit,
                        modifier = rowModifier,
                    )
                    PostRecordButton(
                        onClick = {
                            coroutineScope.launch {
                                if (confirmDeletion(viewModel)) {
                                    postRecordSnackbarDialogs.showVideoDeleted(viewModel.videoUri)
                                    finish()
                                }
                            }
                        },
                        drawableLoaderViewModel = viewModel,
                        iconRes = R.drawable.ic_screenshot_delete,
                        labelRes = R.string.screen_record_delete,
                        modifier = rowModifier,
                    )
                    if (shouldUseFlatBottomBar) {
                        PrimaryButton(
                            text = stringResource(R.string.screenrecord_share_label),
                            onClick = { viewModel.share() },
                            modifier = rowModifier,
                        )
                    }
                }
                if (!shouldUseFlatBottomBar) {
                    val shareIcon by
                        loadIcon(
                            viewModel,
                            R.drawable.ic_screenshot_share,
                            contentDescription = null,
                        )
                    PrimaryButton(
                        text = stringResource(R.string.screenrecord_share_label),
                        icon = shareIcon,
                        onClick = { viewModel.share() },
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                                .height(56.dp),
                    )
                }
            }
            TextButton(
                onClick = { finish() },
                modifier = Modifier.padding(horizontal = 12.dp).size(48.dp).align(Alignment.TopEnd),
            ) {
                LoadingIcon(
                    icon = loadIcon(viewModel, R.drawable.ic_close, null).value,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }

    private suspend fun confirmDeletion(viewModel: DrawableLoaderViewModel) =
        suspendCancellableCoroutine { continuation ->
            val dialog =
                systemUIDialogFactory.create(context = this) { dialog ->
                    LaunchedEffect(dialog) {
                        dialog.setOnDismissListener {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                    AlertDialogContent(
                        title = {
                            Text(stringResource(R.string.screen_record_delete_dialog_title))
                        },
                        content = {
                            Text(stringResource(R.string.screen_record_delete_dialog_content))
                        },
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
                        positiveButton = {
                            PlatformButton(
                                onClick = {
                                    continuation.resume(true)
                                    dialog.dismiss()
                                }
                            ) {
                                Text(stringResource(id = R.string.screen_record_delete))
                            }
                        },
                        negativeButton = {
                            PlatformTextButton(onClick = { dialog.dismiss() }) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        },
                    )
                }
            dialog.show()
            continuation.invokeOnCancellation { dialog.dismiss() }
        }

    companion object {

        private const val SHOULD_SHOW_VIDEO_SAVED = "should_show_video_saved"
        private const val SHOULD_SHOW_VIDEO_SAVED_DEFAULT = false

        fun getStartingIntent(
            context: Context,
            videoUri: Uri,
            shouldShowVideoSaved: Boolean = SHOULD_SHOW_VIDEO_SAVED_DEFAULT,
        ): Intent {
            return Intent(context, SmallScreenPostRecordingActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setDataAndType(videoUri, MimeTypes.VIDEO_MP4)
                .putExtra(SHOULD_SHOW_VIDEO_SAVED, shouldShowVideoSaved)
        }
    }
}

@Composable
private fun PostRecordButton(
    onClick: () -> Unit,
    @DrawableRes iconRes: Int,
    labelRes: Int,
    drawableLoaderViewModel: DrawableLoaderViewModel,
    modifier: Modifier = Modifier,
) {
    PlatformOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
    ) {
        LoadingIcon(
            icon = loadIcon(drawableLoaderViewModel, iconRes, contentDescription = null).value,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
    }
}
