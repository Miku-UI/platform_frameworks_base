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

package com.android.systemui.statusbar.quickactions.av.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.DrawablePainter
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.AvControlsPanelContentViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.ButtonViewModel
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.PageType
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.SensorAccessSummary
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.SensorActivityViewModel
import kotlinx.coroutines.launch

/**
 * The main content of the AV Controls Panel, displaying sensor access, and options for Blur, Studio
 * Look, Camera Framing, Studio Mic, and Live Captions.
 */
@Composable
fun AvControlsPanelContent(
    viewModelFactory: AvControlsPanelContentViewModel.Factory,
    setCurrentPage: (PageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        rememberViewModel("MainPage.viewModel", key = setCurrentPage) {
            viewModelFactory.create(setCurrentPage = setCurrentPage)
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        horizontalAlignment = Alignment.Start,
        modifier = modifier,
    ) {
        SensorAccessButton(
            viewModelFactory = viewModel.sensorActivityViewModelFactory,
            setCurrentPage = setCurrentPage,
        )
        ControlsGrid(viewModel = viewModel, setCurrentPage = setCurrentPage)
    }
}

@Composable
private fun ControlsGrid(
    viewModel: AvControlsPanelContentViewModel,
    setCurrentPage: (PageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        horizontalAlignment = Alignment.Start,
        modifier = modifier,
    ) {
        if (viewModel.showBlurControls || viewModel.showStudioLookControls) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.height(IntrinsicSize.Max),
            ) {
                if (viewModel.showStudioLookControls) {
                    SectionButton(
                        buttonViewModelFactory = {
                            viewModel.studioLookButtonViewModelFactory.create(
                                setCurrentPage = setCurrentPage
                            )
                        },
                        traceName = "StudioLookSectionButton",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (viewModel.showBlurControls) {
                    SectionButton(
                        buttonViewModelFactory = {
                            viewModel.blurButtonViewModelFactory.create(
                                setCurrentPage = setCurrentPage
                            )
                        },
                        traceName = "BlurSectionButton",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
        if (
            viewModel.showLiveCaptionsButton ||
                viewModel.showStudioMicButton ||
                viewModel.showCameraFramingButton
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (viewModel.showCameraFramingButton) {
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleButton(
                            buttonViewModelFactory = {
                                viewModel.cameraFramingViewModelFactory.create()
                            },
                            traceName = "CameraFramingButton",
                        )
                    }
                }
                if (viewModel.showStudioMicButton) {
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleButton(
                            buttonViewModelFactory = {
                                viewModel.studioMicViewModelFactory.create()
                            },
                            traceName = "StudioMicButton",
                        )
                    }
                }
                if (viewModel.showLiveCaptionsButton) {
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleButton(
                            buttonViewModelFactory = {
                                viewModel.liveCaptionsViewModelFactory.create()
                            },
                            traceName = "LiveCaptionsButton",
                        )
                    }
                }
            }
        }
    }
}

/** A simple toggle button with an icon and optional subtext. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SimpleButton(
    buttonViewModelFactory: () -> ButtonViewModel,
    traceName: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel: ButtonViewModel =
        rememberViewModel("MainPage.$traceName") { buttonViewModelFactory() }

    val contentDescription = viewModel.state.subText?.let { stringResource(it) }
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }

    Column(
        modifier = modifier.then(semanticsModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Top),
    ) {
        ToggleButton(
            modifier = Modifier.height(56.dp).fillMaxWidth(),
            checked = viewModel.state.isEnabled,
            onCheckedChange = { scope.launch { viewModel.onClick() } },
        ) {
            viewModel.state.image?.let {
                Icon(painter = painterResource(id = it), contentDescription = null)
            }
        }
        viewModel.state.subText?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A button used for navigating to a specific section (e.g., Blur, Studio Look). */
@Composable
private fun SectionButton(
    buttonViewModelFactory: () -> ButtonViewModel,
    traceName: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val viewModel: ButtonViewModel =
        rememberViewModel("MainPage.$traceName") { buttonViewModelFactory() }
    ListItem(
        modifier =
            modifier
                .height(56.dp)
                .clip(shape = RoundedCornerShape(if (viewModel.state.isEnabled) 16.dp else 360.dp))
                .fillMaxWidth()
                .clickable(onClick = { scope.launch { viewModel.onClick() } }),
        leadingContent = {
            viewModel.state.image?.let {
                Icon(painter = painterResource(id = it), contentDescription = null)
            }
        },
        headlineContent = {
            viewModel.state.mainTitle?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
        },
        supportingContent = {
            if (viewModel.state.isEnabled) {
                viewModel.state.subTitle?.let {
                    val text =
                        if (viewModel.state.subTitleArg != null) {
                            stringResource(it, viewModel.state.subTitleArg!!)
                        } else {
                            stringResource(it)
                        }
                    Text(text = text, style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        colors = itemColors(viewModel.state.isEnabled),
    )
}

/**
 * A button that displays active sensor usage (camera, microphone) and navigates to the sensor
 * activity page.
 */
@Composable
private fun SensorAccessButton(
    viewModelFactory: SensorActivityViewModel.Factory,
    setCurrentPage: (PageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    // TODO(467631762): Enable icons once the icon is available.
    val ICONS_ENABLED = false

    val viewModel =
        rememberViewModel("SensorAccessButton.viewModel", key = setCurrentPage) {
            viewModelFactory.create(setCurrentPage = setCurrentPage)
        }
    if (viewModel.showSensorAccessSection) {
        ListItem(
            modifier =
                modifier
                    .clip(shape = RoundedCornerShape(22.dp))
                    .clickable(onClick = { viewModel.enterDedicatedPage() }),
            leadingContent =
                if (ICONS_ENABLED)
                    viewModel.activeAppsIconDrawable?.let {
                        {
                            Icon(
                                painter = DrawablePainter(drawable = it),
                                contentDescription = null,
                            )
                        }
                    }
                else null,
            headlineContent = {
                viewModel.activeAppsSensorSectionSummary?.let { summary ->
                    Text(
                        text =
                            when (summary) {
                                is SensorAccessSummary.Simple -> summary.text
                                is SensorAccessSummary.WithCount ->
                                    "${summary.prefix} ${stringResource(summary.suffixResId, summary.suffixArg)}"
                            },
                        style = MaterialTheme.typography.titleSmallEmphasized,
                    )
                }
            },
            supportingContent =
                viewModel.activeAppsSensorSectionSupportText?.let {
                    {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            trailingContent = {
                Icon(
                    painter = painterResource(id = R.drawable.gs_keyboard_arrow_right),
                    contentDescription = null,
                )
            },
            colors = itemColors(false),
        )
    }
}
