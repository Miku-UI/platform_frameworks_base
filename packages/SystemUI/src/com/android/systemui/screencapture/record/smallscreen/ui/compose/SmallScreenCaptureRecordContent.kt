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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.screencapture.record.smallscreen.ui.compose

import android.annotation.StringRes
import android.graphics.Region
import android.view.ViewTreeObserver.InternalInsetsInfo
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.compose.modifiers.animatedBackground
import com.android.compose.modifiers.thenIf
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureUi
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.PrimaryButton
import com.android.systemui.screencapture.common.ui.compose.ScreenCaptureContent
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.record.smallscreen.shared.model.RecordDetailsPopupType
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.SmallScreenCaptureRecordViewModel
import com.android.systemui.util.view.listenToComputeInternalInsets
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val toolbarHeight = 64.dp

@ScreenCaptureUiScope
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class SmallScreenCaptureRecordContent
@Inject
constructor(
    @ScreenCaptureUi private val window: Window?,
    @ScreenCaptureUi private val visibilityTransition: Transition<Boolean>,
    private val viewModelFactory: SmallScreenCaptureRecordViewModel.Factory,
) : ScreenCaptureContent {

    @Composable
    override fun Content() {
        val viewModel =
            rememberViewModel("SmallScreenCaptureRecordContent#viewModel") {
                viewModelFactory.create()
            }
        SetupWindow(viewModel.detailsPopup.contentDescriptionRes)

        val viewTreeObserver = window?.decorView?.viewTreeObserver
        val toolbarBounds = remember { Region.obtain() }
        val settingsBounds = remember { Region.obtain() }
        val dimBounds = remember { Region.obtain() }
        LaunchedEffect(viewTreeObserver) {
            viewTreeObserver?.listenToComputeInternalInsets {
                if (viewModel.shouldShowDim) {
                    touchableRegion.op(dimBounds, Region.Op.UNION)
                } else {
                    setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
                    touchableRegion.op(toolbarBounds, Region.Op.UNION)
                    touchableRegion.op(settingsBounds, Region.Op.UNION)
                }
            }
        }

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier =
                Modifier.dim(
                    isVisible = visibilityTransition.targetState && viewModel.shouldShowDim,
                    region = dimBounds,
                    onClick = { viewModel.dismiss() },
                ),
        ) {
            val toolbarYAnimationOffset =
                with(LocalDensity.current) {
                    toolbarHeight.roundToPx() + WindowInsets.safeContent.getTop(this)
                }
            visibilityTransition.AnimatedVisibility(
                visible = { it },
                enter =
                    slideInVertically(
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        initialOffsetY = { -toolbarYAnimationOffset },
                    ),
                exit =
                    slideOutVertically(
                        targetOffsetY = { -toolbarYAnimationOffset },
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    ),
            ) {
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
                            .padding(horizontal = 16.dp),
                ) {
                    TransientSurface(
                        transient = viewModel.isTransient,
                        shape = FloatingToolbarDefaults.ContainerShape,
                        modifier =
                            Modifier.fillBoundsInWindowIf(
                                region = toolbarBounds,
                                condition = viewTreeObserver != null,
                            ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.height(toolbarHeight)
                                    .padding(horizontal = 12.dp)
                                    .horizontalScroll(state = rememberScrollState()),
                        ) {
                            PlatformIconButton(
                                onClick = { viewModel.dismiss() },
                                contentDescription =
                                    stringResource(
                                        id = R.string.underlay_close_button_content_description
                                    ),
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                iconResource = R.drawable.ic_close,
                            )
                            ToggleToolbarButton(
                                visible = viewModel.shouldShowSettingsButton,
                                checked = viewModel.detailsPopup == RecordDetailsPopupType.Settings,
                                onCheckedChange = {
                                    if (it) {
                                        viewModel.showSettings()
                                    } else {
                                        viewModel.resetDetailsPopup()
                                    }
                                },
                                icon = {
                                    LoadingIcon(
                                        icon =
                                            loadIcon(
                                                    viewModel = viewModel,
                                                    resId = R.drawable.ic_settings,
                                                    contentDescription =
                                                        ContentDescription.Resource(
                                                            R.string.screen_record_settings
                                                        ),
                                                )
                                                .value,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                            )
                            ToggleToolbarButton(
                                visible = viewModel.shouldShowMarkupButton,
                                checked = viewModel.markupEnabled == true,
                                onCheckedChange = { viewModel.setMarkupEnabled(it) },
                                icon = {
                                    LoadingIcon(
                                        icon =
                                            loadIcon(
                                                    viewModel = viewModel,
                                                    resId = R.drawable.ic_markup,
                                                    contentDescription =
                                                        ContentDescription.Resource(
                                                            R.string.screen_record_markup
                                                        ),
                                                )
                                                .value,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                            )
                            ToggleToolbarButton(
                                visible = viewModel.shouldShowColorPickerButton,
                                checked =
                                    viewModel.detailsPopup == RecordDetailsPopupType.ColorSelector,
                                onCheckedChange = {
                                    if (it) {
                                        viewModel.showCameraColorSelector()
                                    } else {
                                        viewModel.resetDetailsPopup()
                                    }
                                },
                                icon = {
                                    val colorInt =
                                        viewModel.recordDetailsColorPickerViewModel.cameraColor
                                    if (colorInt == android.graphics.Color.TRANSPARENT) {
                                        LoadingIcon(
                                            icon =
                                                loadIcon(
                                                        viewModel = viewModel,
                                                        resId = R.drawable.ic_palette,
                                                        contentDescription =
                                                            ContentDescription.Resource(
                                                                R.string.screen_record_color_picker
                                                            ),
                                                    )
                                                    .value,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    } else {
                                        RecordDetailsColorItem(
                                            color = Color(colorInt),
                                            selected = false,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                            )

                            Spacer(Modifier.width(12.dp))

                            val coroutineScope = rememberCoroutineScope()
                            var buttonJob: Job? by remember { mutableStateOf(null) }
                            ToolbarPrimaryButton(
                                recording = viewModel.isRecording,
                                onClick = {
                                    if (buttonJob == null) {
                                        buttonJob =
                                            coroutineScope.launch {
                                                viewModel.onPrimaryButtonTapped()
                                                buttonJob = null
                                            }
                                    }
                                },
                                viewModel = viewModel,
                                modifier = Modifier.height(40.dp),
                            )
                        }
                    }

                    val detailsSurfaceVisible =
                        visibilityTransition.targetState &&
                            viewModel.detailsPopup != RecordDetailsPopupType.Invisible
                    val detailsSurfaceAlpha by
                        animateFloatAsState(
                            targetValue = if (detailsSurfaceVisible) 1f else 0f,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        )
                    TransientSurface(
                        transient = viewModel.isTransient,
                        shape = RoundedCornerShape(28.dp),
                        modifier =
                            Modifier.fillBoundsInWindowIf(
                                    region = settingsBounds,
                                    condition = viewTreeObserver != null,
                                )
                                .graphicsLayer { alpha = detailsSurfaceAlpha },
                    ) {
                        val motionScheme = MaterialTheme.motionScheme
                        AnimatedContent(
                            targetState = viewModel.detailsPopup,
                            contentAlignment = Alignment.Center,
                            transitionSpec = {
                                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                                    fadeOut(motionScheme.defaultEffectsSpec()) using
                                    SizeTransform(
                                        sizeAnimationSpec = { _, _ ->
                                            motionScheme.defaultSpatialSpec()
                                        }
                                    )
                            },
                            modifier = Modifier.widthIn(max = 352.dp),
                        ) { detailsPopup ->
                            val contentModifier = Modifier.fillMaxWidth()
                            when (detailsPopup) {
                                RecordDetailsPopupType.Settings -> {
                                    RecordDetailsSettings(
                                        parametersViewModel =
                                            viewModel.recordDetailsParametersViewModel,
                                        targetViewModel = viewModel.recordDetailsTargetViewModel,
                                        drawableLoaderViewModel = viewModel,
                                        onAppSelectorClicked = { viewModel.showAppSelector() },
                                        modifier = contentModifier,
                                    )
                                }

                                RecordDetailsPopupType.AppSelector -> {
                                    RecordDetailsAppSelector(
                                        viewModel = viewModel.recordDetailsAppSelectorViewModel,
                                        onBackPressed = { viewModel.showSettings() },
                                        onTaskSelected = {
                                            viewModel.recordDetailsTargetViewModel.selectTask(it)
                                            viewModel.showSettings()
                                        },
                                        modifier = contentModifier,
                                    )
                                }

                                RecordDetailsPopupType.ColorSelector ->
                                    RecordDetailsColorPicker(
                                        viewModel = viewModel.recordDetailsColorPickerViewModel,
                                        modifier = contentModifier,
                                    )

                                RecordDetailsPopupType.Invisible -> {
                                    /* do nothing */
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SetupWindow(@StringRes contentDescriptionRes: Int?) {
        val title = stringResource(R.string.screenrecord_title)
        val windowTitle =
            if (contentDescriptionRes == null) {
                title
            } else {
                "$title. ${stringResource(contentDescriptionRes)}"
            }
        DisposableEffect(window, windowTitle) {
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
                window.setTitle(windowTitle)
            }
            onDispose { /* do nothing */ }
        }
    }
}

@Composable
private fun ToggleToolbarButton(
    visible: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    AnimatedVisibility(
        visible = visible,
        enter =
            expandHorizontally(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                expandFrom = Alignment.Start,
            ) + fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
        exit =
            shrinkHorizontally(
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                shrinkTowards = Alignment.Start,
            ) + fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            content = icon,
            shapes = ToggleButtonShapes(shape = shape, pressedShape = shape, checkedShape = shape),
            colors =
                ToggleButtonDefaults.tonalToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.secondary,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            contentPadding = PaddingValues(6.dp),
            modifier = modifier.size(48.dp).padding(6.dp),
        )
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
        val buttonContentDescription =
            stringResource(
                if (isRecording) {
                    R.string.screenrecord_stop_description
                } else {
                    R.string.screenrecord_start_description
                }
            )
        PrimaryButton(
            onClick = onClick,
            text =
                stringResource(
                    if (isRecording) {
                        R.string.screenrecord_stop_label
                    } else {
                        R.string.screenrecord_continue
                    }
                ),
            icon =
                loadIcon(
                        viewModel = viewModel,
                        resId =
                            if (isRecording) {
                                R.drawable.ic_stop
                            } else {
                                R.drawable.ic_screenrecord
                            },
                        contentDescription = null,
                    )
                    .value,
            contentPadding = PaddingValues(horizontal = 14.dp),
            iconPadding = 4.dp,
            colors =
                if (isRecording) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                },
            modifier =
                modifier.basicMarquee().semantics(true) {
                    contentDescription = buttonContentDescription
                },
        )
    }
}

@Composable
private fun TransientSurface(
    transient: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val surfaceBackground by
        animateColorAsState(
            if (transient) {
                MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    val contentAlpha by animateFloatAsState(if (transient) 0f else 1f)
    Box(modifier = modifier.animatedBackground(color = { surfaceBackground }, shape = shape)) {
        Box(content = content, modifier = Modifier.graphicsLayer { alpha = contentAlpha })
    }
}

@Composable
private fun Modifier.dim(isVisible: Boolean, onClick: () -> Unit, region: Region): Modifier {
    val backgroundScrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
    val backgroundOpacity by animateFloatAsState(if (isVisible) 1f else 0f)
    return animatedBackground(color = { backgroundScrim }, alpha = { backgroundOpacity })
        .clickable(
            enabled = isVisible,
            interactionSource = null,
            indication = null,
            onClick = onClick,
        )
        .fillBoundsInWindowIf(region = region, condition = isVisible)
}

@Composable
private fun Modifier.fillBoundsInWindowIf(region: Region, condition: Boolean): Modifier =
    thenIf(condition) {
        Modifier.onGloballyPositioned { layoutCoordinates ->
            with(layoutCoordinates.boundsInWindow()) {
                region.set(
                    left.roundToInt(),
                    top.roundToInt(),
                    right.roundToInt(),
                    bottom.roundToInt(),
                )
            }
        }
    }
