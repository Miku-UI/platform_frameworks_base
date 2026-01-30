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

package com.android.systemui.screencapture.record.smallscreen.ui.compose

import android.graphics.Rect as AndroidRect
import android.view.ViewTreeObserver.InternalInsetsInfo
import android.view.Window
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.compose.modifiers.thenIf
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureUi
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.RecordDetailsPopupType
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.SmallScreenCaptureRecordViewModel
import com.android.systemui.util.view.listenToComputeInternalInsets
import javax.inject.Inject

@ScreenCaptureUiScope
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class SmallScreenCaptureRecordContent
@Inject
constructor(
    @ScreenCaptureUi private val window: Window?,
    private val viewModelFactory: SmallScreenCaptureRecordViewModel.Factory,
) : ScreenCaptureContent {

    @Composable
    override fun Content() {
        val viewModel =
            rememberViewModel("SmallScreenCaptureRecordContent#viewModel") {
                viewModelFactory.create()
            }

        val toolbarRect: AndroidRect = remember(Unit) { AndroidRect() }
        val detailsRect: AndroidRect = remember(Unit) { AndroidRect() }
        LaunchedEffect(window) {
            window ?: return@LaunchedEffect
            window.decorView.viewTreeObserver.listenToComputeInternalInsets {
                setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION)

                touchableRegion.union(toolbarRect)
                touchableRegion.union(detailsRect)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeContent.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = 30.dp),
        ) {
            // TODO(b/428686600) use Toolbar shared with the large screen
            Surface(
                shape = FloatingToolbarDefaults.ContainerShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.onGloballyPositioned { toolbarRect.set(it.boundsInWindow()) },
            ) {
                Row(
                    modifier = Modifier.height(64.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformIconButton(
                        onClick = { viewModel.dismiss() },
                        contentDescription =
                            stringResource(id = R.string.underlay_close_button_content_description),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        iconResource = R.drawable.ic_close,
                    )
                    AnimatedVisibility(visible = viewModel.shouldShowSettingsButton) {
                        ToggleToolbarButton(
                            checked = viewModel.shouldShowDetails,
                            onCheckedChanged = { viewModel.shouldShowSettings(it) },
                            icon = {
                                LoadingIcon(
                                    icon =
                                        loadIcon(
                                                viewModel = viewModel,
                                                resId = R.drawable.ic_settings,
                                                contentDescription = null,
                                            )
                                            .value,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    ToolbarPrimaryButton(
                        recording = viewModel.isRecording,
                        onClick = { viewModel.onPrimaryButtonTapped() },
                        viewModel = viewModel,
                        modifier = Modifier.height(40.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = viewModel.shouldShowDetails,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 2.dp,
                    modifier =
                        Modifier.animateContentSize().onGloballyPositioned {
                            detailsRect.set(it.boundsInWindow())
                        },
                ) {
                    AnimatedContent(
                        targetState = viewModel.detailsPopup,
                        contentAlignment = Alignment.Center,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier.widthIn(max = 352.dp),
                    ) { currentPopup ->
                        val contentModifier = Modifier.fillMaxWidth()
                        when (currentPopup) {
                            RecordDetailsPopupType.Settings ->
                                RecordDetailsSettings(
                                    parametersViewModel =
                                        viewModel.recordDetailsParametersViewModel,
                                    targetViewModel = viewModel.recordDetailsTargetViewModel,
                                    drawableLoaderViewModel = viewModel,
                                    onAppSelectorClicked = { viewModel.showAppSelector() },
                                    modifier = contentModifier,
                                )

                            RecordDetailsPopupType.AppSelector ->
                                RecordDetailsAppSelector(
                                    viewModel = viewModel.recordDetailsAppSelectorViewModel,
                                    onBackPressed = { viewModel.showSettings() },
                                    onTaskSelected = {
                                        viewModel.recordDetailsTargetViewModel.selectTask(it)
                                        viewModel.showSettings()
                                    },
                                    modifier = contentModifier,
                                )

                            RecordDetailsPopupType.MarkupColorSelector ->
                                RecordDetailsMarkupColorSelector(modifier = contentModifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleToolbarButton(
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    icon: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(48.dp)
                .padding(6.dp)
                .thenIf(checked) {
                    Modifier.background(color = secondaryColor, shape = RoundedCornerShape(12.dp))
                }
                .clickable(onClick = { onCheckedChanged(!checked) }),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                if (checked) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
        ) {
            icon()
        }
    }
}

@Composable
private fun ToolbarPrimaryButton(
    recording: Boolean,
    viewModel: DrawableLoaderViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(targetState = recording) { isRecording ->
        if (isRecording) {
            PrimaryButton(
                onClick = onClick,
                text = stringResource(R.string.screenrecord_stop_label),
                icon =
                    loadIcon(
                            viewModel = viewModel,
                            resId = R.drawable.ic_stop,
                            contentDescription = null,
                        )
                        .value,
                contentPadding = PaddingValues(horizontal = 14.dp),
                iconPadding = 4.dp,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                modifier = modifier,
            )
        } else {
            PrimaryButton(
                onClick = onClick,
                text = stringResource(R.string.screen_capture_toolbar_record_button),
                icon =
                    loadIcon(
                            viewModel = viewModel,
                            resId = R.drawable.ic_screenrecord,
                            contentDescription = null,
                        )
                        .value,
                contentPadding = PaddingValues(horizontal = 14.dp),
                iconPadding = 4.dp,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = modifier,
            )
        }
    }
}

private fun AndroidRect.set(rect: Rect) {
    set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
}
