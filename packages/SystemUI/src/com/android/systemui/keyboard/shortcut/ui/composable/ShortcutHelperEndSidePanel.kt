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

package com.android.systemui.keyboard.shortcut.ui.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType.AppCategories
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCustomizationRequestInfo
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutSubCategory
import com.android.systemui.keyboard.shortcut.ui.model.IconSource
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutCategoryUi
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutsUiState
import com.android.systemui.res.R

@Composable
fun EndSidePanel(
    uiState: ShortcutsUiState.Active,
    onCustomizationModeToggled: (isCustomizing: Boolean) -> Unit,
    category: ShortcutCategoryUi?,
    modifier: Modifier = Modifier,
    onShortcutCustomizationRequested: (ShortcutCustomizationRequestInfo) -> Unit = {},
) {
    val listState = rememberLazyListState()

    LaunchedEffect(key1 = category) { if (category != null) listState.animateScrollToItem(0) }
    if (category == null) {
        NoSearchResultsText(horizontalPadding = 24.dp, fillHeight = false)
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        stickyHeader {
            Column {
                AnimatedVisibility(
                    category.type == AppCategories &&
                        uiState.shouldShowCustomAppsShortcutLimitHeader
                ) {
                    AppCustomShortcutLimitContainer(Modifier.padding(8.dp))
                }
            }
        }
        items(category.subCategories) { subcategory ->
            SubCategoryContainerDualPane(
                uiState.searchQuery,
                subcategory,
                isCustomizing =
                    uiState.isCustomizationModeEnabled && category.type.includeInCustomization,
                onShortcutCustomizationRequested = { requestInfo ->
                    onShortcutCustomizationRequestedInSubCategory(
                        requestInfo,
                        onShortcutCustomizationRequested,
                        category.type,
                    )
                },
                uiState.allowExtendedAppShortcutsCustomization,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (
            category.type == AppCategories &&
                !uiState.isCustomizationModeEnabled &&
                uiState.isExtendedAppCategoryFlagEnabled &&
                uiState.allowExtendedAppShortcutsCustomization
        ) {
            item {
                ShortcutHelperButton(
                    onClick = { onCustomizationModeToggled(/* isCustomizing= */ true) },
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    iconSource = IconSource(imageVector = Icons.Default.Add),
                    modifier = Modifier.heightIn(40.dp),
                    text = stringResource(R.string.shortcut_helper_add_shortcut_button_label),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppCustomShortcutLimitContainer(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(40.dp),
                )
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
            color = MaterialTheme.colorScheme.secondary,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                tint = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(24.dp).padding(8.dp),
                contentDescription = null,
            )
        }

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.shortcut_helper_app_custom_shortcut_limit_exceeded),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleMediumEmphasized,
                textAlign = TextAlign.Center,
            )
            Text(
                text =
                    stringResource(
                        R.string.shortcut_helper_app_custom_shortcut_limit_exceeded_instruction
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SubCategoryContainerDualPane(
    searchQuery: String,
    subCategory: ShortcutSubCategory,
    isCustomizing: Boolean,
    onShortcutCustomizationRequested: (ShortcutCustomizationRequestInfo) -> Unit,
    allowExtendedAppShortcutsCustomization: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(Modifier.padding(16.dp)) {
            SubCategoryTitle(subCategory.label, Modifier.padding(8.dp))
            Spacer(Modifier.height(8.dp))
            subCategory.shortcuts.fastForEachIndexed { index, shortcut ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
                Shortcut(
                    modifier = Modifier.padding(vertical = 8.dp),
                    searchQuery = searchQuery,
                    shortcut = shortcut,
                    isCustomizing = isCustomizing && shortcut.isCustomizable,
                    onShortcutCustomizationRequested = { requestInfo ->
                        when (requestInfo) {
                            is ShortcutCustomizationRequestInfo.SingleShortcutCustomization.Add ->
                                onShortcutCustomizationRequested(
                                    requestInfo.copy(subCategoryLabel = subCategory.label)
                                )

                            is ShortcutCustomizationRequestInfo.SingleShortcutCustomization.Delete ->
                                onShortcutCustomizationRequested(
                                    requestInfo.copy(subCategoryLabel = subCategory.label)
                                )

                            ShortcutCustomizationRequestInfo.Reset ->
                                onShortcutCustomizationRequested(requestInfo)
                        }
                    },
                    allowExtendedAppShortcutsCustomization = allowExtendedAppShortcutsCustomization,
                )
            }
        }
    }
}

private fun onShortcutCustomizationRequestedInSubCategory(
    requestInfo: ShortcutCustomizationRequestInfo,
    onShortcutCustomizationRequested: (ShortcutCustomizationRequestInfo) -> Unit,
    categoryType: ShortcutCategoryType,
) {
    when (requestInfo) {
        is ShortcutCustomizationRequestInfo.SingleShortcutCustomization.Add ->
            onShortcutCustomizationRequested(requestInfo.copy(categoryType = categoryType))

        is ShortcutCustomizationRequestInfo.SingleShortcutCustomization.Delete ->
            onShortcutCustomizationRequested(requestInfo.copy(categoryType = categoryType))

        ShortcutCustomizationRequestInfo.Reset -> onShortcutCustomizationRequested(requestInfo)
    }
}
