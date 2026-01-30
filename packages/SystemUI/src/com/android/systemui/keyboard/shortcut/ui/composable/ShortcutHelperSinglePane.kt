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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCategoryType
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutSubCategory
import com.android.systemui.keyboard.shortcut.ui.composable.ShortcutHelperSinglePane.Shapes
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutCategoryUi
import com.android.systemui.keyboard.shortcut.ui.model.ShortcutsUiState
import com.android.systemui.res.R

@Composable
fun ShortcutHelperSinglePane(
    uiState: ShortcutsUiState.Active,
    onSearchQueryChanged: (String) -> Unit,
    selectedCategoryType: ShortcutCategoryType?,
    onCategorySelected: (ShortcutCategoryType?) -> Unit,
    onKeyboardSettingsClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 26.dp)
    ) {
        TitleBar()
        Spacer(modifier = Modifier.height(6.dp))
        ShortcutsSearchBar(onSearchQueryChanged)
        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.shortcutCategories.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                NoSearchResultsText(horizontalPadding = 16.dp, fillHeight = true)
            }
        } else {
            CategoriesPanelSinglePane(uiState, selectedCategoryType, onCategorySelected)
            Spacer(modifier = Modifier.weight(1f))
        }
        KeyboardSettings(onClick = onKeyboardSettingsClicked)
    }
}

@Composable
private fun CategoriesPanelSinglePane(
    uiState: ShortcutsUiState.Active,
    selectedCategoryType: ShortcutCategoryType?,
    onCategorySelected: (ShortcutCategoryType?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        uiState.shortcutCategories.fastForEachIndexed { index, category ->
            val isExpanded = selectedCategoryType == category.type
            val itemShape =
                if (uiState.shortcutCategories.size == 1) {
                    Shapes.singlePaneSingleCategory
                } else if (index == 0) {
                    Shapes.singlePaneFirstCategory
                } else if (index == uiState.shortcutCategories.lastIndex) {
                    Shapes.singlePaneLastCategory
                } else {
                    Shapes.singlePaneCategory
                }
            CategoryItemSinglePane(
                searchQuery = uiState.searchQuery,
                category = category,
                isExpanded = isExpanded,
                onClick = {
                    onCategorySelected(
                        if (isExpanded) {
                            null
                        } else {
                            category.type
                        }
                    )
                },
                shape = itemShape,
            )
        }
    }
}

@Composable
private fun CategoryItemSinglePane(
    searchQuery: String,
    category: ShortcutCategoryUi,
    isExpanded: Boolean,
    onClick: () -> Unit,
    shape: Shape,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceBright, shape = shape, onClick = onClick) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 16.dp),
            ) {
                ShortcutCategoryIcon(modifier = Modifier.size(24.dp), source = category.iconSource)
                Spacer(modifier = Modifier.width(16.dp))
                Text(category.label)
                Spacer(modifier = Modifier.weight(1f))
                RotatingExpandCollapseIcon(isExpanded)
            }
            AnimatedVisibility(visible = isExpanded) {
                ShortcutCategoryDetailsSinglePane(searchQuery, category)
            }
        }
    }
}

@Composable
private fun RotatingExpandCollapseIcon(isExpanded: Boolean) {
    val expandIconRotationDegrees by
        animateFloatAsState(
            targetValue =
                if (isExpanded) {
                    180f
                } else {
                    0f
                },
            label = "Expand icon rotation animation",
        )
    Icon(
        modifier =
            Modifier.background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                )
                .graphicsLayer { rotationZ = expandIconRotationDegrees },
        imageVector = Icons.Default.ExpandMore,
        contentDescription =
            if (isExpanded) {
                stringResource(R.string.shortcut_helper_content_description_collapse_icon)
            } else {
                stringResource(R.string.shortcut_helper_content_description_expand_icon)
            },
        tint = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ShortcutCategoryDetailsSinglePane(searchQuery: String, category: ShortcutCategoryUi) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        category.subCategories.fastForEach { subCategory ->
            ShortcutSubCategorySinglePane(searchQuery, subCategory)
        }
    }
}

@Composable
private fun ShortcutSubCategorySinglePane(searchQuery: String, subCategory: ShortcutSubCategory) {
    // This @Composable is expected to be in a Column.
    SubCategoryTitle(subCategory.label)
    subCategory.shortcuts.fastForEachIndexed { index, shortcut ->
        if (index > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
        Shortcut(Modifier.padding(vertical = 24.dp), searchQuery, shortcut)
    }
}

private object ShortcutHelperSinglePane {

    object Shapes {
        val singlePaneFirstCategory =
            RoundedCornerShape(
                topStart = Dimensions.SinglePaneCategoryCornerRadius,
                topEnd = Dimensions.SinglePaneCategoryCornerRadius,
            )
        val singlePaneLastCategory =
            RoundedCornerShape(
                bottomStart = Dimensions.SinglePaneCategoryCornerRadius,
                bottomEnd = Dimensions.SinglePaneCategoryCornerRadius,
            )
        val singlePaneSingleCategory =
            RoundedCornerShape(size = Dimensions.SinglePaneCategoryCornerRadius)
        val singlePaneCategory = RectangleShape
    }

    object Dimensions {
        val SinglePaneCategoryCornerRadius = 28.dp
    }
}
