/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.widget.scaffold

import android.os.Build
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.window.embedding.ActivityEmbeddingController
import com.android.settingslib.spa.framework.compose.localActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    isFirstLayerPageWhenEmbedded: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    CustomizedLargeTopAppBar(
        title = title,
        navigationIcon = { NavigationIcon(isFirstLayerPageWhenEmbedded) },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun NavigationIcon(isFirstLayerPageWhenEmbedded: Boolean) {
    if (shouldShowNavigateBack(isFirstLayerPageWhenEmbedded)) NavigateBack()
}

/** Whether the current page should show the navigate back button. */
@Composable
private fun shouldShowNavigateBack(isFirstLayerPageWhenEmbedded: Boolean): Boolean =
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> true
        !isFirstLayerPageWhenEmbedded -> true
        else -> {
            val activity = localActivity() ?: return true
            remember(activity) {
                !ActivityEmbeddingController.getInstance(activity).isActivityEmbedded(activity)
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
internal fun TopAppBarScrollBehavior.collapse() {
    with(state) { heightOffset = heightOffsetLimit }
}
