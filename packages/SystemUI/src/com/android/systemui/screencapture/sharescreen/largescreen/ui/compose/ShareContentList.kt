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

package com.android.systemui.screencapture.sharescreen.largescreen.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTaskViewModel
import com.android.systemui.screencapture.sharescreen.largescreen.ui.viewmodel.ShareContentListViewModel

/**
 * A composable that displays a scrollable list of shareable content (e.g., recent apps).
 *
 * @param modifier The modifier to be applied to the composable.
 * @param viewModel The ViewModel that provides the list of tasks and manages selection state.
 * @param recentTaskViewModelFactory A factory to create a [RecentTaskViewModel] for each item.
 * @param selectedRecentTaskViewModel The selected RecentTaskViewModel.
 */
@Composable
fun ShareContentList(
    modifier: Modifier = Modifier,
    viewModel: ShareContentListViewModel,
    recentTaskViewModelFactory: RecentTaskViewModel.Factory,
    selectedRecentTaskViewModel: RecentTaskViewModel?,
) {
    val recentTasks by viewModel.recentTasks.collectAsStateWithLifecycle(initialValue = null)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = modifier.height(224.dp).width(286.dp),
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Use the real list of recent tasks, handling the nullable case.
            recentTasks?.let { tasks ->
                items(items = tasks) { task ->
                    val currentRecentTaskViewModel: RecentTaskViewModel =
                        rememberViewModel(
                            traceName = "ShareContentListItemViewModel#${task.taskId}",
                            key = task,
                        ) {
                            recentTaskViewModelFactory.create(task)
                        }
                    SelectorItem(
                        currentRecentTaskViewModel = currentRecentTaskViewModel,
                        isSelected =
                            currentRecentTaskViewModel.task == selectedRecentTaskViewModel?.task,
                        onItemSelected = {
                            viewModel.selectedRecentTaskViewModel = currentRecentTaskViewModel
                        },
                    )
                }
            }
        }
    }
}

/**
 * A composable that displays a single item in the share content list.
 *
 * @param currentRecentTaskViewModel The [RecentTaskViewModel] that holds the state for this
 *   specific item.
 * @param isSelected The boolean if the currentRecentTaskViewModel is selected.
 * @param onItemSelected The callback to be invoked when this item is clicked.
 */
@Composable
private fun SelectorItem(
    currentRecentTaskViewModel: RecentTaskViewModel,
    isSelected: Boolean,
    onItemSelected: () -> Unit,
) {
    // Get the icon and label from the item's ViewModel.
    val icon = currentRecentTaskViewModel.icon?.getOrNull()
    val label = currentRecentTaskViewModel.label?.getOrNull()

    Surface(
        shape = RoundedCornerShape(4.dp),
        color =
            if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier =
                Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                    .clickable(onClick = onItemSelected),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        ) {
            Box(
                modifier =
                    Modifier.size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (icon != null) {
                    Image(bitmap = icon.asImageBitmap(), contentDescription = label?.toString())
                }
            }
            Text(
                text = label?.toString() ?: "Title",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
