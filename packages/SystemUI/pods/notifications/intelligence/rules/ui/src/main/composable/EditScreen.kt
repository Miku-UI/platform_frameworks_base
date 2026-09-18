/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.systemui.res.R

/** Handler for when an item is selected or un-selected. */
fun interface SelectionHandler<T> {
    /** Invoked whenever an item is selected or un-selected. */
    fun onSelectionToggled(item: T, isSelected: Boolean)
}

/**
 * A generic composable supporting an edit screen for a particular type [T]. The screen shows the
 * currently-selected set of items.
 *
 * @param title a string shown in the header explaining the type of item being edited.
 * @param initialSelection the currently selected items when the screen first loads.
 * @param onSelectionSaved invoked when the user saves their selected items.
 * @param onDismissRequest invoked when the user leaves the page without saving.
 * @param sortKey a function used to sort items as they're added to the list.
 * @param uniqueId a function that should return a unique identifier for an item.
 * @param icon a composable rendering an icon for a particular item.
 * @param text a function that should return the main text for a particular item.
 * @param inputSlot a text box of some sort that will let users search for new items to add.
 * @param additionalContentSlot an optional composable that will also be included underneath the
 *   [inputSlot] and above the list of currently selected items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EditScreen(
    title: String,
    initialSelection: List<T>,
    onSelectionSaved: (List<T>) -> Unit,
    onDismissRequest: () -> Unit,
    sortKey: (T) -> String,
    uniqueId: (T) -> Any,
    icon: (@Composable (T) -> Unit)?,
    text: (T) -> String,
    inputSlot: @Composable (selectionHandler: SelectionHandler<T>) -> Unit,
    additionalContentSlot:
        @Composable
        ((currentSelection: List<T>, selectionHandler: SelectionHandler<T>) -> Unit)?,
) {

    val currentSelection: MutableList<T> = remember {
        mutableStateListOf<T>().apply { addAll(initialSelection.sortedBy { sortKey(it) }) }
    }

    val selectionHandler =
        object : SelectionHandler<T> {
            override fun onSelectionToggled(item: T, isSelected: Boolean) {
                if (isSelected) {
                    if (!currentSelection.contains(item)) {
                        currentSelection.add(item)
                    }
                } else {
                    currentSelection.remove(item)
                }
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Header(title = title, onDismissRequest = onDismissRequest)

            inputSlot(selectionHandler)
            additionalContentSlot?.let { it(currentSelection, selectionHandler) }

            if (currentSelection.isNotEmpty()) {
                SelectedItems(
                    currentSelection = currentSelection,
                    selectionHandler = selectionHandler,
                    onClearSelection = { currentSelection.clear() },
                    uniqueId = uniqueId,
                    icon = icon,
                    text = text,
                )
            } else {
                NoSelection()
            }
        }

        FloatingSaveButton(
            currentSelection = currentSelection,
            onSelectionSaved = onSelectionSaved,
            sortKey = sortKey,
        )
    }
}

/** Renders the list of items that are currently selected to be part of the rule filter. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> SelectedItems(
    currentSelection: List<T>,
    selectionHandler: SelectionHandler<T>,
    onClearSelection: () -> Unit,
    uniqueId: (T) -> Any,
    icon: (@Composable (T) -> Unit)?,
    text: (T) -> String,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item(key = "Selected header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    stringResource(
                        R.string.notification_rules_number_selected,
                        currentSelection.size,
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.notification_rules_deselect_all),
                    modifier = Modifier.clickable { onClearSelection() },
                )
            }
        }
        items(currentSelection, key = uniqueId) {
            Item(
                model = it,
                isSelected = true,
                selectionHandler = selectionHandler,
                icon = icon,
                text = text,
            )
        }
    }
}

/**
 * Renders a single item.
 *
 * @param isSelected true if the item is currently selected to be part of the rule
 */
@Composable
fun <T> Item(
    model: T,
    isSelected: Boolean,
    selectionHandler: SelectionHandler<T>,
    icon: (@Composable (T) -> Unit)?,
    text: (T) -> String,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Box(Modifier.size(EditScreenDimens.iconSize)) { icon(model) } }

        Text(
            text = text(model),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.height(24.dp).fillMaxWidth(0.8f).padding(horizontal = 8.dp),
        )

        val buttonIconModifier = Modifier.size(18.dp)
        val onClick = { selectionHandler.onSelectionToggled(model, !isSelected) }
        if (isSelected) {
            PlatformOutlinedButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.remove),
                    modifier = buttonIconModifier,
                )
            }
        } else {
            PlatformButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add),
                    modifier = buttonIconModifier,
                )
            }
        }
    }
}

@Composable
private fun NoSelection() {
    Text(
        stringResource(R.string.notification_rules_nothing_selected),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> BoxScope.FloatingSaveButton(
    currentSelection: List<T>,
    onSelectionSaved: (List<T>) -> Unit,
    sortKey: (T) -> String,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors =
            FloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.inverseSurface,
                toolbarContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                fabContainerColor = MaterialTheme.colorScheme.inverseSurface,
                fabContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        shape = FloatingToolbarDefaults.ContainerShape,
        modifier =
            modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset)
                // Use zIndex to ensure it's on top of other content.
                .zIndex(1f)
                // Apply `clip` before `clickable` so the touch ripple is only within the rounded
                // rectangle area.
                .clip(FloatingToolbarDefaults.ContainerShape)
                .clickable { onSelectionSaved(currentSelection.sortedBy { sortKey(it) }) },
    ) {
        Text(
            stringResource(R.string.notification_rules_save_number_selected, currentSelection.size),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/** Common dimensions used by the edit screen. */
object EditScreenDimens {
    val iconSize = 40.dp
}
