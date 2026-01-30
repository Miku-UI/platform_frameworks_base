/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.credentialmanager.common.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.credentialmanager.common.material.rememberModalBottomSheetState
import com.android.credentialmanager.ui.theme.EntryShape

/** Draws a modal bottom sheet with the same styles and effects shared by various flows. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ModalBottomSheet(
        sheetContent: @Composable () -> Unit,
        onDismiss: () -> Unit,
        isInitialRender: Boolean,
        onInitialRenderComplete: () -> Unit,
        isAutoSelectFlow: Boolean,
) {
    val state = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
    )
    androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            sheetState = state,
            content = {
                Box(
                        modifier = Modifier
                                .animateContentSize()
                                .wrapContentHeight()
                                .fillMaxWidth()
                ) {
                    sheetContent()
                }
            },
            scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = .32f),
            shape = EntryShape.TopRoundedCorner,
            contentWindowInsets = { WindowInsets.navigationBars },
            dragHandle = null,
            // Never take over the full screen. We always want to leave some top scrim space
            // for exiting and viewing the underlying app to help a user gain context.
            modifier = Modifier.padding(top = 72.dp),
    )
}
