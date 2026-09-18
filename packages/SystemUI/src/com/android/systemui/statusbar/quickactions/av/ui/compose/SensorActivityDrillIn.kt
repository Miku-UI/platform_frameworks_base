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

import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.DrawablePainter
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.quickactions.av.shared.model.Sensor
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.PageType
import com.android.systemui.statusbar.quickactions.av.ui.viewmodel.SensorActivityViewModel

/** A drill-in page displaying detailed sensor usage (camera, microphone) by apps. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SensorActivityDrillIn(
    viewModelFactory: SensorActivityViewModel.Factory,
    setCurrentPage: (PageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        rememberViewModel("SensorActivityDrillIn.viewModel", key = setCurrentPage) {
            viewModelFactory.create(setCurrentPage = setCurrentPage)
        }
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme

    DrillIn(
        drillInTitle = stringResource(R.string.av_panel_title),
        subtitle = stringResource(R.string.sensor_activity_header),
        returnToMainPage = { setCurrentPage(PageType.MAIN) },
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
            modifier = modifier,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top),
            ) {
                viewModel.appDetails.let {
                    val count = it.size
                    it.forEachIndexed { index, item ->
                        val isFirstItem = (index == 0)
                        val isLastItem = (index == count - 1)
                        if (!isFirstItem) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        AppDetailItem(
                            viewModel = viewModel,
                            appIcon = item.icon,
                            appName = item.appName,
                            packageName = item.packageName,
                            sensorUsages = item.sensorUsages,
                            isFirstItem = isFirstItem,
                            isLastItem = isLastItem,
                        )
                    }
                }
            }

            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.sensor_activity_see_all_access_button),
                        textAlign = TextAlign.Center,
                        style =
                            typography.labelMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            ),
                        color = colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                modifier =
                    Modifier.clip(shape = RoundedCornerShape(size = 1000.dp)) // Make it a circle
                        .width(348.dp)
                        .height(36.dp)
                        .clickable(onClick = { viewModel.openPrivacyDashboard() }),
                colors =
                    ListItemDefaults.colors()
                        .copy(
                            containerColor = colorScheme.surface,
                            headlineColor = colorScheme.onSurface,
                        ),
            )
        }
    }
}

/** A list item displaying details for a specific app's sensor usage. */
@Composable
private fun AppDetailItem(
    viewModel: SensorActivityViewModel,
    appIcon: Drawable?,
    appName: String,
    packageName: String,
    sensorUsages: List<Sensor>,
    isFirstItem: Boolean,
    isLastItem: Boolean,
) {
    // TODO(467631762): Enable icons once the icon is available.
    val ICONS_ENABLED = false
    val colorScheme = MaterialTheme.colorScheme

    val bigRadius = 16.dp
    val smallRadius = 4.dp
    val topCornerRadius = if (isFirstItem) bigRadius else smallRadius
    val bottomCornerRadius = if (isLastItem) bigRadius else smallRadius
    val shape =
        RoundedCornerShape(
            topStart = topCornerRadius,
            topEnd = topCornerRadius,
            bottomStart = bottomCornerRadius,
            bottomEnd = bottomCornerRadius,
        )
    ListItem(
        leadingContent =
            if (ICONS_ENABLED)
                appIcon?.let {
                    { Icon(painter = DrawablePainter(drawable = it), contentDescription = null) }
                }
            else null,
        headlineContent = {
            Text(
                text = appName,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = colorScheme.onSurface,
            )
        },
        supportingContent = {
            SupportingContent(
                viewModel = viewModel,
                packageName = packageName,
                sensors = sensorUsages,
            )
        },
        colors =
            ListItemDefaults.colors()
                .copy(
                    containerColor = colorScheme.surface,
                    headlineColor = colorScheme.onSurface,
                    supportingTextColor = colorScheme.primary,
                ),
        modifier = Modifier.height(92.dp).clip(shape = shape),
    )
}

/** The supporting content for an app detail item, showing used sensors and action buttons. */
@Composable
private fun SupportingContent(
    viewModel: SensorActivityViewModel,
    packageName: String,
    sensors: List<Sensor>,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.Start),
        ) {
            val hasCamera = sensors.contains(Sensor.CAMERA)
            val hasMicrophone = sensors.contains(Sensor.MICROPHONE)
            if (hasCamera) {
                Icon(
                    painter = painterResource(id = R.drawable.av_controls_chip_camera),
                    contentDescription = null,
                    tint = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.sensor_activity_camera_label),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            if (hasCamera && hasMicrophone) {
                Text(
                    text = "•",
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            if (hasMicrophone) {
                Icon(
                    painter = painterResource(id = R.drawable.av_controls_chip_mic),
                    contentDescription = null,
                    tint = colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.sensor_activity_microphone_label),
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.sensor_activity_close_app_button),
                style = typography.titleSmallEmphasized,
                color = colorScheme.primary,
                modifier = Modifier.clickable(onClick = { viewModel.closeApp(packageName) }),
            )
            Text(
                text = stringResource(R.string.sensor_activity_manage_access_button),
                style = typography.titleSmallEmphasized,
                color = colorScheme.primary,
                modifier = Modifier.clickable(onClick = { viewModel.manageApp(packageName) }),
            )
        }
    }
}
