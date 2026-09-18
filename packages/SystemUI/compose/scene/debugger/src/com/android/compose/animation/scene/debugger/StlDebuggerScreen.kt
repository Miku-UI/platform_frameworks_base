/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.compose.animation.scene.debugger

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.debug.DebugLabelPosition
import com.android.compose.animation.scene.debug.StlDebugKeys
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StlDebuggerScreen() {
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(SettingsUtils.checkPermission(context)) }
    val propertiesList = remember { getAllDebugProperties() }

    LaunchedEffect(Unit) {
        while (!hasPermission) {
            hasPermission = SettingsUtils.checkPermission(context)
            delay(1000)
        }
    }

    if (hasPermission) {
        DisposableEffect(Unit) {
            val resolver = context.contentResolver
            val handler = Handler(Looper.getMainLooper())

            val observer =
                object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        propertiesList.forEach { it.refresh(context) }
                    }
                }

            propertiesList.forEach { prop ->
                resolver.registerContentObserver(
                    Settings.Global.getUriFor(prop.key),
                    false,
                    observer,
                )
            }
            propertiesList.forEach { it.refresh(context) }

            onDispose { resolver.unregisterContentObserver(observer) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("STL Debugger") },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp),
                    ) {
                        Text("Toggle All", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        val anyEnabled =
                            propertiesList.any {
                                it.type == PropertyType.BOOLEAN && it.stateValue == "true"
                            }
                        Switch(
                            checked = anyEnabled,
                            onCheckedChange = { enable ->
                                propertiesList
                                    .filter { it.type == PropertyType.BOOLEAN }
                                    .forEach { prop ->
                                        prop.updateValue(context, enable.toString())
                                    }
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.weight(1f)) {
                if (!hasPermission) {
                    PermissionWarningBanner()
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val grouped = propertiesList.groupBy { it.category.name }

                    grouped.forEach { (category, props) ->
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        items(props) { prop -> PropertyRow(prop, enabled = hasPermission) }
                    }
                }
            }
            StlPreviewSection()
        }
    }
}

@Composable
private fun StlPreviewSection() {
    var isExpanded by remember { mutableStateOf(true) }

    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    imageVector =
                        if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                )
            }

            if (isExpanded) {
                HorizontalDivider()
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(0.4f)
                            .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    DebugPreviewStl()
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningBanner() {
    val context = LocalContext.current
    val cmd = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier =
            Modifier.fillMaxWidth().padding(16.dp).clickable {
                Log.w("StlDebug", "Run command to grant permission: `$cmd`")
            },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Permission Missing",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Tap to print command to Logcat", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                cmd,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.1f)).padding(4.dp),
            )
        }
    }
}

@Composable
private fun PropertyRow(
    prop: DebugProperty,
    enabled: Boolean,
    onChanged: (DebugProperty) -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    if (prop.key == StlDebugKeys.ELEMENT_FILTER.key || prop.key == StlDebugKeys.EXCLUDE_STLS.key) {
        FilterPropertyEditor(prop = prop, onChanged = onChanged)
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Row {
                Text(
                    prop.label,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Text(
                    text = " (${prop.key})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = prop.description,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                textAlign = TextAlign.Justify,
            )
        }

        if (!enabled) {
            Text("Locked", style = MaterialTheme.typography.labelSmall)
        } else {
            when (prop.type) {
                PropertyType.BOOLEAN -> {
                    Switch(
                        checked = prop.stateValue == "true",
                        onCheckedChange = { isChecked ->
                            prop.updateValue(context, isChecked.toString())
                            onChanged(prop)
                        },
                    )
                }
                PropertyType.STRING -> {
                    var textState by remember(prop.stateValue) { mutableStateOf(prop.stateValue) }

                    // Sync internal state with prop state if it changes externally
                    LaunchedEffect(prop.stateValue) {
                        if (textState != prop.stateValue) textState = prop.stateValue
                    }

                    OutlinedTextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier.width(160.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    prop.updateValue(context, textState)
                                    focusManager.clearFocus()
                                    onChanged(prop)
                                }
                            ),
                        trailingIcon = {
                            if (textState.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        textState = ""
                                        prop.updateValue(context, "")
                                        onChanged(prop)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                    )
                                }
                            }
                        },
                    )
                }
                PropertyType.ENUM -> {
                    EnumDropdown(
                        prop = prop,
                        onSelected = { newVal ->
                            prop.updateValue(context, newVal)
                            onChanged(prop)
                        },
                        onReset = {
                            prop.updateValue(context, "")
                            onChanged(prop)
                        },
                        onNext = {
                            val options = prop.enumOptions
                            if (options.isNotEmpty()) {
                                val currentIndex = options.indexOf(prop.stateValue)
                                val nextIndex = (currentIndex + 1) % options.size
                                val nextVal = options[nextIndex]

                                prop.updateValue(context, nextVal)
                                onChanged(prop)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EnumDropdown(
    prop: DebugProperty,
    onSelected: (String) -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (prop.stateValue.isNotEmpty()) {
            IconButton(onClick = onReset) {
                Icon(imageVector = Icons.Filled.Restore, contentDescription = "Reset to Default")
            }
        }

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (prop.stateValue.isEmpty()) "Default" else prop.stateValue,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                prop.enumOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                contentDescription = "Cycle Next",
            )
        }
    }
}

@Composable
private fun FilterPropertyEditor(prop: DebugProperty, onChanged: (DebugProperty) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val currentFilters =
        remember(prop.stateValue) {
            prop.stateValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

    var textState by remember { mutableStateOf("") }

    fun saveFilters(newFilters: List<String>) {
        val joined = newFilters.joinToString(",")
        prop.updateValue(context, joined)
        onChanged(prop)
    }

    fun addFilter() {
        if (textState.isBlank()) return

        val newEntries = textState.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val updatedList = (currentFilters + newEntries).distinct()

        saveFilters(updatedList)
        textState = ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(prop.label, fontWeight = FontWeight.Medium)
                Text(
                    text = prop.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Justify,
                )
            }

            OutlinedTextField(
                value = textState,
                onValueChange = { newValue ->
                    if (newValue.endsWith(",")) {
                        textState = newValue.removeSuffix(",")
                        addFilter()
                    } else {
                        textState = newValue
                    }
                },
                modifier = Modifier.width(200.dp),
                singleLine = true,
                placeholder = { Text("Add filter...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            addFilter()
                            focusManager.clearFocus()
                        }
                    ),
            )
        }

        if (currentFilters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                currentFilters.forEach { filter ->
                    InputChip(
                        selected = true,
                        onClick = { saveFilters(currentFilters - filter) },
                        label = { Text(filter) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        colors =
                            InputChipDefaults.inputChipColors(
                                selectedContainerColor =
                                    MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTrailingIconColor =
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        border = null,
                    )
                }

                InputChip(
                    selected = false,
                    onClick = { saveFilters(emptyList()) },
                    label = { Text("Clear All") },
                    trailingIcon = null,
                    colors =
                        InputChipDefaults.inputChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                            containerColor = Color.Transparent,
                        ),
                )
            }
        }
    }
}

private enum class PropertyType {
    BOOLEAN,
    STRING,
    ENUM,
}

private enum class Category {
    STL,
    CONTENT,
    ELEMENT,
}

private data class DebugProperty(
    val category: Category,
    val label: String,
    val key: String,
    val description: String,
    val type: PropertyType,
    val enumOptions: List<String> = emptyList(),
) {
    var stateValue by mutableStateOf("")
        private set

    fun refresh(context: Context) {
        stateValue = SettingsUtils.get(context, key, type == PropertyType.BOOLEAN)
    }

    fun updateValue(context: Context, newValue: String) {
        stateValue = newValue
        SettingsUtils.put(context, key, newValue, isBool = (type == PropertyType.BOOLEAN))
    }
}

private fun getAllDebugProperties(): List<DebugProperty> {
    val positions = DebugLabelPosition.entries.map { it.name }

    return listOf(
        // --- Element ---
        DebugProperty(
            Category.ELEMENT,
            "Element Key Filter",
            StlDebugKeys.ELEMENT_FILTER.key,
            "Setting no filter will display all element labels. As soon as you set a filter, only the specified elements will be shown. It affects borders, labels and logs.",
            PropertyType.STRING,
        ),
        DebugProperty(
            Category.ELEMENT,
            "Show Borders",
            StlDebugKeys.SHOW_ELEMENT_BORDERS.key,
            "Element borders very often overlap. For this reason they are randomly dotted, colored and offset by 0-2px(!) to make them distinguishable.",
            PropertyType.BOOLEAN,
        ),
        DebugProperty(
            Category.ELEMENT,
            "Show Labels",
            StlDebugKeys.SHOW_ELEMENT_LABELS.key,
            "The label shows \"E:<elementKey>\" and \"In:<contentKey>\" on the second line. This is very useful to understand which content is currently responsible for composing this element (especially during transitions) as this is a common source of bugs and confusion.",
            PropertyType.BOOLEAN,
        ),
        DebugProperty(
            Category.ELEMENT,
            "Label Position",
            StlDebugKeys.POS_ELEMENT_LABEL.key,
            "Choose a different label position when it collides with other labels.",
            PropertyType.ENUM,
            positions,
        ),
        DebugProperty(
            Category.ELEMENT,
            "Log Changes",
            StlDebugKeys.LOG_ELEMENTS.key,
            "This will show two types of logs. When the transitionState changes it will log a summary of all element states. Only if a element key filter is set it will show a frame-by-frame state summary for selected elements.",
            PropertyType.BOOLEAN,
        ),
        DebugProperty(
            Category.ELEMENT,
            "Log Verbose",
            StlDebugKeys.LOG_ELEMENTS_VERBOSE.key,
            "Only if a element key filter is set it will log all kinds of verbose events for all of the inner Element.kt composition phases.",
            PropertyType.BOOLEAN,
        ),

        // --- Content ---
        DebugProperty(
            Category.CONTENT,
            "Show Borders",
            StlDebugKeys.SHOW_CONTENT_BORDERS.key,
            "Content borders for scenes and overlays.",
            PropertyType.BOOLEAN,
        ),
        DebugProperty(
            Category.CONTENT,
            "Show Labels",
            StlDebugKeys.SHOW_CONTENT_LABELS.key,
            "The label will show \"S:<key>\" for scenes and \"O:<key>\" for overlays.",
            PropertyType.BOOLEAN,
        ),
        DebugProperty(
            Category.CONTENT,
            "Label Position",
            StlDebugKeys.POS_CONTENT_LABEL.key,
            "Choose a different label position when it collides with other labels.",
            PropertyType.ENUM,
            positions,
        ),

        // --- STL Global ---
        DebugProperty(
            Category.STL,
            "Exclude STLs",
            StlDebugKeys.EXCLUDE_STLS.key,
            "Enter STL keys to filter them OUT. Use this when specific STL labels overlap each other or distract you.",
            PropertyType.STRING,
        ),
        DebugProperty(
            Category.STL,
            "Show Borders",
            StlDebugKeys.SHOW_STL_BORDERS.key,
            "Show borders around each STL. These are drawn behind content borders and element borders.",
            PropertyType.BOOLEAN,
        ),
        DebugProperty(
            Category.STL,
            "Show Labels",
            StlDebugKeys.SHOW_STL_LABELS.key,
            "STL labels show: <StlName> \\n <TransitionState>. The content names within transitions are truncated to 14 chars. For nested STLs [Nested(<NestingDepth>)] is appended to the name.",
            PropertyType.BOOLEAN,
        ),
        DebugProperty(
            Category.STL,
            "Label Position",
            StlDebugKeys.POS_STL_LABEL.key,
            "Choose a different label position when it collides with other labels.",
            PropertyType.ENUM,
            positions,
        ),
    )
}
