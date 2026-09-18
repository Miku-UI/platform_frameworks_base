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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.android.systemui.qs.panels.ui.compose.infinitegrid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.systemui.common.ui.icons.MoreVert
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeTopBarDefaults.editModeTopAppBarColors
import com.android.systemui.qs.panels.ui.compose.infinitegrid.EditModeTopBarDefaults.menuItemColors
import com.android.systemui.qs.panels.ui.viewmodel.EditTopBarActionViewModel
import com.android.systemui.res.R

/**
 * Expandable top app bar for Edit mode.
 *
 * Displays a top bar that collapses and expands when the user's scroll the page. It displays the
 * page's title, subtitle, navigation arrow and optionals actions.
 *
 * @param onStopEditing callback when the user clicks on the navigation arrow
 * @param subtitle the composable displayed as subtitle
 * @param modifier [Modifier] applied to the top app bar
 * @param scrollBehavior [TopAppBarScrollBehavior] to use for the top bar
 * @param collapsibleActions list of [EditTopBarActionViewModel] to display. If more than one action
 *   is passed, they will be grouped in a dropdown menu.
 * @param actions Additional actions to show regardless of [collapsibleActions]
 */
@Composable
fun EditModeExpandableTopBar(
    onStopEditing: () -> Unit,
    subtitle: @Composable (expanded: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    collapsibleActions: SnapshotStateList<EditTopBarActionViewModel>,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TwoRowsTopAppBar(
        colors = editModeTopAppBarColors(),
        title = { Title(MaterialTheme.typography.headlineMediumEmphasized) },
        scrollBehavior = scrollBehavior,
        subtitle = subtitle,
        navigationIcon = { NavigationArrow(onStopEditing) },
        actions = {
            actions()
            CollapsibleActions(collapsibleActions)
        },
        modifier = modifier.padding(vertical = 8.dp),
        windowInsets = WindowInsets(0.dp),
    )
}

@Composable
private fun Title(style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(id = R.string.qs_edit_tiles),
        style = style,
        modifier = modifier,
        overflow = TextOverflow.Ellipsis,
        maxLines = 2,
    )
}

@Composable
private fun NavigationArrow(
    onStopEditing: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
) {
    IconButton(
        modifier = modifier,
        onClick = onStopEditing,
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription =
                stringResource(id = com.android.internal.R.string.action_bar_up_description),
        )
    }
}

@Composable
private fun CollapsibleActions(
    collapsibleActions: SnapshotStateList<EditTopBarActionViewModel>,
    modifier: Modifier = Modifier,
) {
    if (collapsibleActions.size == 1) {
        SingleTopBarAction(collapsibleActions.single(), modifier)
    } else if (collapsibleActions.size > 1) {
        TopBarActionOverflow(collapsibleActions, modifier)
    }
}

@Composable
private fun SingleTopBarAction(
    editTopBarActionViewModel: EditTopBarActionViewModel,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = { editTopBarActionViewModel.onClick() },
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        modifier = modifier,
    ) {
        Icon(
            editTopBarActionViewModel.icon,
            contentDescription = stringResource(id = editTopBarActionViewModel.labelId),
        )
    }
}

@Composable
private fun TopBarActionOverflow(
    actionsViewModel: SnapshotStateList<EditTopBarActionViewModel>,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { showMenu = !showMenu },
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Icon(
                MoreVert,
                contentDescription = stringResource(R.string.qs_edit_menu_content_description),
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.testTag(OPTIONS_DROP_DOWN_TEST_TAG).requiredWidthIn(min = 216.dp),
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ) {
            actionsViewModel.forEach { action ->
                key(action.labelId) {
                    DropdownMenuElement(action, dismissDropdown = { showMenu = false })
                }
            }
        }
    }
}

@Composable
private fun DropdownMenuElement(
    action: EditTopBarActionViewModel,
    dismissDropdown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenuItem(
        onClick = {
            action.onClick()
            dismissDropdown()
        },
        text = {
            Box(modifier = Modifier.padding(start = 6.dp)) {
                Text(
                    text = stringResource(action.labelId),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.wrapContentHeight(Alignment.CenterVertically),
                )
            }
        },
        leadingIcon = {
            Icon(action.icon, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        colors = menuItemColors(),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier.heightIn(min = 52.dp),
    )
}

private object EditModeTopBarDefaults {
    @ReadOnlyComposable
    @Composable
    fun menuItemColors() =
        MenuItemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = Color.Transparent,
            disabledTextColor = Color.Transparent,
            disabledLeadingIconColor = Color.Transparent,
            disabledTrailingIconColor = Color.Transparent,
        )

    @Composable
    fun editModeTopAppBarColors(): TopAppBarColors {
        return TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            subtitleContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val OPTIONS_DROP_DOWN_TEST_TAG = "OptionsDropdown"
