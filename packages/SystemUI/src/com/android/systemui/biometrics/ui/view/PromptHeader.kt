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

package com.android.systemui.biometrics.ui.view

import android.hardware.biometrics.PromptContentView
import android.widget.LinearLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.biometrics.ui.binder.BiometricCustomizedViewBinder
import com.android.systemui.biometrics.ui.viewmodel.CredentialHeaderViewModel
import com.android.systemui.res.R

@Composable
fun CustomPromptContentView(
    contentView: PromptContentView?,
    onMoreOptionsPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (contentView == null) {
        return
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                BiometricCustomizedViewBinder.bind(this, contentView) { onMoreOptionsPressed() }
            }
        },
    )
}

// TODO: Will likely get promoted to general prompt header on compose move
@Composable
fun PromptHeader(
    header: CredentialHeaderViewModel,
    modifier: Modifier = Modifier,
    onContentViewMoreOptionsButtonPressed: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Logo
        Image(
            painter = rememberDrawablePainter(header.icon),
            contentDescription = null,
            modifier =
                Modifier.size(dimensionResource(R.dimen.biometric_prompt_logo_size)).testTag("logo"),
        )
        // Logo Description
        if (header.logoDescription.isNotBlank()) {
            Text(
                text = header.logoDescription,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                            top =
                                dimensionResource(
                                    R.dimen.biometric_prompt_logo_description_top_padding
                                )
                        )
                        .testTag("logo_description"),
            )
        }

        // Title
        if (header.title.isNotBlank()) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp).testTag("title").semantics { heading() },
            )
        }

        // Subtitle
        if (header.subtitle.isNotBlank()) {
            Text(
                text = header.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp).testTag("subtitle"),
            )
        }

        // Description
        if (header.description.isNotBlank()) {
            Text(
                text = header.description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp).testTag("description"),
            )
        }

        // Custom Content View
        CustomPromptContentView(
            contentView = header.contentView,
            onMoreOptionsPressed = onContentViewMoreOptionsButtonPressed,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
fun PromptHeaderLandscape(
    header: CredentialHeaderViewModel,
    modifier: Modifier = Modifier,
    onContentViewMoreOptionsButtonPressed: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        // Top Row: Logo + Description
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = rememberDrawablePainter(header.icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp).testTag("logo"),
            )

            if (header.logoDescription.isNotBlank()) {
                Text(
                    text = header.logoDescription,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("logo_description"),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (header.title.isNotBlank()) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.testTag("title").semantics { heading() },
            )
        }

        if (header.subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = header.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.testTag("subtitle"),
            )
        }

        if (header.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = header.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
                modifier = Modifier.testTag("description"),
            )
        }

        // Custom Content View
        CustomPromptContentView(
            contentView = header.contentView,
            onMoreOptionsPressed = onContentViewMoreOptionsButtonPressed,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
