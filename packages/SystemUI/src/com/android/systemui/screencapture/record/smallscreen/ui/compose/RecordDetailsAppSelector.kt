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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.focused
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformIconButton
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTaskViewModel
import com.android.systemui.screencapture.record.smallscreen.ui.viewmodel.RecordDetailsAppSelectorViewModel
import kotlinx.coroutines.launch

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
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        }
        val coroutineScope = rememberCoroutineScope()
        val tasks = viewModel.recentTasks.takeUnless { it.isNullOrEmpty() } ?: return@Column
        val pagerState: PagerState = rememberPagerState { tasks.size }
        HorizontalPager(
            state = pagerState,
            pageSpacing = 22.dp,
            snapPosition = SnapPosition.Center,
            contentPadding = PaddingValues(horizontal = 68.dp),
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Carousel },
        ) { index ->
            val task = tasks[index]
            val taskViewModel =
                rememberViewModel("RecordDetailsAppSelector#taskViewModel_$index") {
                    viewModel.createTaskViewModel(task)
                }
            val currentIndex = pagerState.settledPage
            val description =
                if (index == currentIndex) {
                    taskViewModel.label?.getOrNull()?.toString() ?: ""
                } else {
                    if (index > currentIndex) {
                        stringResource(R.string.screen_record_accessibility_label_show_next)
                    } else {
                        stringResource(R.string.screen_record_accessibility_label_show_previous)
                    }
                }
            AppPreview(
                viewModel = taskViewModel,
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(
                            onClick = {
                                if (index == currentIndex) {
                                    onTaskSelected(task)
                                } else {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                            }
                        )
                        .semantics(true) {
                            focused = index == currentIndex
                            contentDescription = description
                        },
            )
        }
    }
}

@Composable
private fun AppPreview(viewModel: RecentTaskViewModel, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        val icon = viewModel.icon?.getOrNull()
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
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.aspectRatio(
                    with(LocalResources.current.displayMetrics) {
                        widthPixels / heightPixels.toFloat()
                    }
                ),
        ) {
            val animationSpec: FiniteAnimationSpec<Float> =
                MaterialTheme.motionScheme.slowEffectsSpec()
            AnimatedContent(
                targetState = viewModel.thumbnail?.getOrNull(),
                transitionSpec = {
                    fadeIn(animationSpec = animationSpec) togetherWith
                        fadeOut(animationSpec = animationSpec)
                },
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.shadow(
                            elevation = 4.dp,
                            shape = shape,
                            spotColor = shadowColor,
                            ambientColor = shadowColor,
                        )
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            ) { thumbnail ->
                val sizeModifier = Modifier.fillMaxSize()
                if (thumbnail == null) {
                    Spacer(modifier = sizeModifier)
                } else {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = sizeModifier,
                    )
                }
            }
        }
    }
}
