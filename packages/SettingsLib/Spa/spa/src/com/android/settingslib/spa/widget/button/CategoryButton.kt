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

package com.android.settingslib.spa.widget.button

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.SettingsTheme

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun CategoryButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    val size = ButtonDefaults.MediumContainerHeight
    TextButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier
            .heightIn(size)
            .padding(horizontal = SettingsSpace.small1),
        colors = ButtonDefaults.textButtonColors().copy(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = ButtonDefaults.contentPaddingFor(size)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(size))
        )
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
        Text(text = text, style = ButtonDefaults.textStyleFor(size))
    }
}

@Preview
@Composable
private fun CategoryButtonPreview() {
    SettingsTheme {
        CategoryButton(icon = Icons.Outlined.Add, text = "Add SIM") {}
    }
}
