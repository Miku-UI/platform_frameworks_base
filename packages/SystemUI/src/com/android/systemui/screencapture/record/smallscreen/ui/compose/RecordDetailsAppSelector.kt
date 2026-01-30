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

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTaskViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.RecordDetailsAppSelectorViewModel

@Composable
fun RecordDetailsAppSelector(
    viewModel: RecordDetailsAppSelectorViewModel,
    onBackPressed: () -> Unit,
    onTaskSelected: (ScreenCaptureRecentTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        ) {
            PlatformIconButton(
                onClick = onBackPressed,
                iconResource = R.drawable.ic_arrow_back,
                contentDescription = stringResource(R.string.accessibility_back),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.screen_record_capture_target_choose_app),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val tasks = viewModel.recentTasks
        val pagerState = rememberPagerState { tasks?.size ?: 1 }
        HorizontalPager(
            state = pagerState,
            pageSpacing = 22.dp,
            snapPosition = SnapPosition.Center,
            contentPadding = PaddingValues(horizontal = 68.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { index ->
            val task = tasks?.getOrNull(index)
            val taskViewModel =
                task?.let {
                    rememberViewModel("RecordDetailsAppSelector#taskViewModel_$index") {
                        viewModel.createTaskViewModel(task)
                    }
                }
            AppPreview(
                viewModel = taskViewModel,
                onClick = { if (task != null) onTaskSelected(task) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppPreview(
    viewModel: RecentTaskViewModel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        val icon = viewModel?.icon?.getOrNull()
        if (icon == null) {
            Spacer(Modifier.size(18.dp))
        } else {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }

        val shape = RoundedCornerShape(20.dp)
        val shadowColor =
            if (isSystemInDarkTheme()) {
                Color.White
            } else {
                Color.Black
            }
        Box(
            modifier =
                Modifier.shadow(
                        elevation = 4.dp,
                        shape = shape,
                        spotColor = shadowColor,
                        ambientColor = shadowColor,
                    )
                    .clip(shape)
                    .clickable(onClick = onClick)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .aspectRatio(viewModel?.thumbnail?.getOrNull().aspectRatio)
        ) {
            AnimatedContent(
                targetState = viewModel?.thumbnail?.getOrNull(),
                contentAlignment = Alignment.Center,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.fillMaxSize(),
            ) { thumbnail ->
                if (thumbnail == null) {
                    Spacer(
                        modifier =
                            Modifier.background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                    )
                } else {
                    Image(bitmap = thumbnail.asImageBitmap(), contentDescription = null)
                }
            }
        }
    }
}

private val Bitmap?.aspectRatio: Float
    @Composable
    get() {
        return if (this == null) {
            with(LocalResources.current.displayMetrics) { widthPixels / heightPixels.toFloat() }
        } else {
            width / height.toFloat()
        }
    }
