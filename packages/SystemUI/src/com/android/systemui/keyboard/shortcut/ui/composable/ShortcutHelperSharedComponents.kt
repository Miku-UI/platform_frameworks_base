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

import android.graphics.drawable.Icon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.compose.modifiers.thenIf
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.keyboard.shortcut.shared.model.Shortcut as ShortcutModel
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCommand
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutCustomizationRequestInfo
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutIcon
import com.android.systemui.keyboard.shortcut.shared.model.ShortcutKey
import com.android.systemui.keyboard.shortcut.ui.model.IconSource
import com.android.systemui.res.R
import kotlinx.coroutines.delay

@Composable
fun ResetButton(onClick: () -> Unit) {
    ShortcutHelperButton(
        modifier = Modifier.heightIn(40.dp),
        onClick = onClick,
        color = Color.Transparent,
        iconSource = IconSource(imageVector = Icons.Default.Refresh),
        text = stringResource(id = R.string.shortcut_helper_reset_button_text),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(color = MaterialTheme.colorScheme.outlineVariant, width = 1.dp),
    )
}

@Composable
fun DoneButton(onClick: () -> Unit) {
    ShortcutHelperButton(
        modifier = Modifier.heightIn(40.dp),
        onClick = onClick,
        color = MaterialTheme.colorScheme.primary,
        text = stringResource(R.string.shortcut_helper_done_button_text),
        contentColor = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
fun CustomizeButton(onClick: () -> Unit) {
    ShortcutHelperButton(
        modifier = Modifier.heightIn(40.dp),
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        iconSource = IconSource(imageVector = Icons.Default.Tune),
        text = stringResource(id = R.string.shortcut_helper_customize_button_text),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
fun AddShortcutButton(onClick: () -> Unit) {
    ShortcutHelperButton(
        modifier = Modifier.size(32.dp),
        onClick = onClick,
        color = Color.Transparent,
        iconSource = IconSource(imageVector = Icons.Default.Add),
        contentColor = MaterialTheme.colorScheme.primary,
        contentPaddingVertical = 0.dp,
        contentPaddingHorizontal = 0.dp,
        contentDescription = stringResource(R.string.shortcut_helper_add_shortcut_button_label),
        shape = CircleShape,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
    )
}

@Composable
fun DeleteShortcutButton(onClick: () -> Unit) {
    ShortcutHelperButton(
        modifier = Modifier.size(32.dp),
        onClick = onClick,
        color = Color.Transparent,
        iconSource = IconSource(imageVector = Icons.Default.DeleteOutline),
        contentColor = MaterialTheme.colorScheme.primary,
        contentPaddingVertical = 0.dp,
        contentPaddingHorizontal = 0.dp,
        contentDescription = stringResource(R.string.shortcut_helper_delete_shortcut_button_label),
        shape = CircleShape,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
    )
}

@Composable
fun ShortcutCommandContainer(showBackground: Boolean, content: @Composable () -> Unit) {
    if (showBackground) {
        Box(
            modifier =
                Modifier.wrapContentSize()
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(4.dp)
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun ShortcutCommand(command: ShortcutCommand) {
    Row {
        command.keys.forEachIndexed { keyIndex, key ->
            if (keyIndex > 0) {
                Spacer(Modifier.width(4.dp))
            }
            ShortcutKeyContainer {
                if (key is ShortcutKey.Text) {
                    ShortcutTextKey(key)
                } else if (key is ShortcutKey.Icon) {
                    ShortcutIconKey(key)
                }
            }
        }
    }
}

@Composable
fun SubCategoryTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
fun Shortcut(
    modifier: Modifier,
    searchQuery: String,
    shortcut: ShortcutModel,
    allowExtendedAppShortcutsCustomization: Boolean = true,
    isCustomizing: Boolean = false,
    onShortcutCustomizationRequested: (ShortcutCustomizationRequestInfo) -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusColor = MaterialTheme.colorScheme.secondary
    Row(
        modifier
            .thenIf(isFocused) {
                Modifier.border(width = 3.dp, color = focusColor, shape = RoundedCornerShape(16.dp))
            }
            .focusable(interactionSource = interactionSource)
            .padding(8.dp)
            .semantics(mergeDescendants = true) { contentDescription = shortcut.contentDescription }
    ) {
        Row(
            modifier =
                Modifier.width(128.dp).align(Alignment.CenterVertically).weight(0.333f).semantics {
                    hideFromAccessibility()
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (shortcut.icon != null) {
                ShortcutIcon(
                    shortcut.icon,
                    modifier = Modifier.size(24.dp).semantics { hideFromAccessibility() },
                )
            }
            ShortcutDescriptionText(
                searchQuery = searchQuery,
                shortcut = shortcut,
                modifier = Modifier.semantics { hideFromAccessibility() },
            )
        }
        Spacer(modifier = Modifier.width(24.dp).semantics { hideFromAccessibility() })
        ShortcutKeyCombinations(
            modifier = Modifier.weight(.666f).semantics { hideFromAccessibility() },
            shortcut = shortcut,
            isCustomizing = isCustomizing,
            allowExtendedAppShortcutsCustomization = allowExtendedAppShortcutsCustomization,
            onAddShortcutRequested = {
                onShortcutCustomizationRequested(
                    ShortcutCustomizationRequestInfo.SingleShortcutCustomization.Add(
                        label = shortcut.label,
                        defaultShortcutCommand = shortcut.commands.firstOrNull { !it.isCustom },
                        packageName = shortcut.pkgName,
                        className = shortcut.className,
                    )
                )
            },
            onDeleteShortcutRequested = {
                onShortcutCustomizationRequested(
                    ShortcutCustomizationRequestInfo.SingleShortcutCustomization.Delete(
                        label = shortcut.label,
                        defaultShortcutCommand = shortcut.commands.firstOrNull { !it.isCustom },
                        customShortcutCommand = shortcut.commands.firstOrNull { it.isCustom },
                        packageName = shortcut.pkgName,
                        className = shortcut.className,
                    )
                )
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ShortcutsSearchBar(onQueryChange: (String) -> Unit) {
    // Using an "internal query" to make sure the SearchBar is immediately updated, otherwise
    // the cursor moves to the wrong position sometimes, when waiting for the query to come back
    // from the ViewModel.
    var queryInternal by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        // TODO(b/272065229): Added minor delay so TalkBack can take focus of search box by default,
        //  remove when default a11y focus is fixed.
        delay(50)
        focusRequester.requestFocus()
    }
    SearchBar(
        modifier =
            Modifier.fillMaxWidth().focusRequester(focusRequester).onKeyEvent {
                if (it.key == Key.DirectionDown) {
                    focusManager.moveFocus(FocusDirection.Down)
                    return@onKeyEvent true
                } else {
                    return@onKeyEvent false
                }
            },
        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        query = queryInternal,
        active = false,
        onActiveChange = {},
        onQueryChange = {
            queryInternal = it
            onQueryChange(it)
        },
        onSearch = {},
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = {
            Text(
                text = stringResource(R.string.shortcut_helper_search_placeholder),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        windowInsets = WindowInsets(top = 0.dp, bottom = 0.dp, left = 0.dp, right = 0.dp),
        content = {},
    )
}

@Composable
fun KeyboardSettings(onClick: () -> Unit) {
    ClickableShortcutSurface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier =
            Modifier.semantics { role = Role.Button }.fillMaxWidth().padding(horizontal = 12.dp),
        interactionsConfig =
            InteractionsConfig(
                hoverOverlayColor = MaterialTheme.colorScheme.onSurface,
                hoverOverlayAlpha = 0.11f,
                pressedOverlayColor = MaterialTheme.colorScheme.onSurface,
                pressedOverlayAlpha = 0.15f,
                focusOutlineColor = MaterialTheme.colorScheme.secondary,
                focusOutlinePadding = 8.dp,
                focusOutlineStrokeWidth = 3.dp,
                surfaceCornerRadius = 24.dp,
                focusOutlineCornerRadius = 28.dp,
                hoverPadding = 8.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    stringResource(id = R.string.shortcut_helper_keyboard_settings_buttons_label),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Default.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun ShortcutCategoryIcon(
    source: IconSource,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) {
    if (source.imageVector != null) {
        Icon(source.imageVector, contentDescription, modifier, tint)
    } else if (source.painter != null) {
        Image(source.painter, contentDescription, modifier)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TitleBar(isCustomizing: Boolean = false) {
    val text =
        if (isCustomizing) {
            stringResource(R.string.shortcut_helper_customize_mode_title)
        } else {
            stringResource(R.string.shortcut_helper_title)
        }
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        title = {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        },
        windowInsets = WindowInsets(top = 0.dp, bottom = 0.dp, left = 0.dp, right = 0.dp),
        expandedHeight = 64.dp,
    )
}

@Composable
fun NoSearchResultsText(horizontalPadding: Dp, fillHeight: Boolean) {
    var modifier = Modifier.fillMaxWidth()
    if (fillHeight) {
        modifier = modifier.fillMaxHeight()
    }
    Text(
        stringResource(R.string.shortcut_helper_no_search_results),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(28.dp))
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
    )
}

@Composable
private fun ShortcutKeyContainer(shortcutKeyContent: @Composable BoxScope.() -> Unit) {
    Box(
        modifier =
            Modifier.height(36.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                )
    ) {
        shortcutKeyContent()
    }
}

@Composable
private fun BoxScope.ShortcutTextKey(key: ShortcutKey.Text) {
    Text(
        text = key.value,
        modifier =
            Modifier.align(Alignment.Center).padding(horizontal = 12.dp).semantics {
                hideFromAccessibility()
            },
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun BoxScope.ShortcutIconKey(key: ShortcutKey.Icon) {
    Icon(
        painter =
            when (key) {
                is ShortcutKey.Icon.ResIdIcon -> painterResource(key.drawableResId)
                is ShortcutKey.Icon.DrawableIcon -> rememberDrawablePainter(drawable = key.drawable)
            },
        contentDescription = null,
        modifier = Modifier.align(Alignment.Center).padding(6.dp),
    )
}

@Composable
private fun ShortcutIcon(
    icon: ShortcutIcon,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val drawable =
        remember(icon.packageName, icon.resourceId) {
            Icon.createWithResource(icon.packageName, icon.resourceId).loadDrawable(context)
        } ?: return
    Image(
        painter = rememberDrawablePainter(drawable),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

@Composable
private fun ShortcutDescriptionText(
    searchQuery: String,
    shortcut: ShortcutModel,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = textWithHighlightedSearchQuery(shortcut.label, searchQuery),
        style = MaterialTheme.typography.labelLarge.copy(hyphens = Hyphens.Auto),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ShortcutKeyCombinations(
    modifier: Modifier = Modifier,
    shortcut: ShortcutModel,
    isCustomizing: Boolean = false,
    allowExtendedAppShortcutsCustomization: Boolean,
    onAddShortcutRequested: () -> Unit = {},
    onDeleteShortcutRequested: () -> Unit = {},
) {
    FlowRow(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        shortcut.commands.forEachIndexed { index, command ->
            if (index > 0) {
                ShortcutOrSeparator(spacing = 16.dp)
            }
            ShortcutCommandContainer(showBackground = command.isCustom) { ShortcutCommand(command) }
        }

        if (isCustomizing) Spacer(modifier = Modifier.width(16.dp))

        AnimatedVisibility(visible = isCustomizing) {
            if (shortcut.containsCustomShortcutCommands) {
                DeleteShortcutButton(onDeleteShortcutRequested)
            } else if (
                shortcut.containsDefaultShortcutCommands || allowExtendedAppShortcutsCustomization
            ) {
                AddShortcutButton(onAddShortcutRequested)
            }
        }
    }
}

@Composable
private fun textWithHighlightedSearchQuery(text: String, searchValue: String) =
    buildAnnotatedString {
        val searchIndex = text.lowercase().indexOf(searchValue.trim().lowercase())
        val postSearchIndex = searchIndex + searchValue.trim().length

        if (searchIndex > 0) {
            val preSearchText = text.substring(0, searchIndex)
            append(preSearchText)
        }
        if (searchIndex >= 0) {
            val searchText = text.substring(searchIndex, postSearchIndex)
            withStyle(style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)) {
                append(searchText)
            }
            if (postSearchIndex < text.length) {
                val postSearchText = text.substring(postSearchIndex)
                append(postSearchText)
            }
        } else {
            append(text)
        }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.ShortcutOrSeparator(spacing: Dp) {
    Spacer(Modifier.width(spacing))
    Text(
        text = stringResource(R.string.shortcut_helper_key_combinations_or_separator),
        modifier = Modifier.align(Alignment.CenterVertically).semantics { hideFromAccessibility() },
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.width(spacing))
}
