/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.systemui.personalcontext.attribution

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.service.personalcontext.insight.interaction.AttributionDetails
import android.service.personalcontext.insight.interaction.AttributionDetails.AttributionLine
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.systemui.res.R

private const val VENDOR_INTELLIGENCE_ROLE = "android.app.role.SYSTEM_VENDOR_INTELLIGENCE"
private const val TAG = "AttributionDialogCmp"
private const val SETTINGS_INTENT_ACTION = "android.settings.PERSONAL_CONTEXT_SETTINGS"

@Composable
fun AttributionDialogComposable(attributionDetails: AttributionDetails) {
    MainTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                AttributionHeader()
                Attributions(attributionDetails)
                AttributionFooter()
            }
        }
    }
}

@Composable
fun AttributionHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.ace_attribution_dialog_title),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(R.drawable.magic_cue),
            tint = MaterialTheme.colorScheme.onSurface,
            contentDescription =
                stringResource(R.string.ace_attribution_header_icon_accessibility_description),
        )

    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Attributions(attributionDetails: AttributionDetails) {
    Column(
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.ace_attribution_dialog_description),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        for (attributionLine: AttributionLine in attributionDetails.lines) {
            SingleSourceAttribution(attributionLine)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SingleSourceAttribution(attributionLine: AttributionLine) {
    val ctx = LocalContext.current
    var attributionSourceBitmap =
        remember(ctx) {
            val attributionSourceIcon = attributionLine.icon?.loadDrawable(ctx)
            attributionSourceIcon
                ?.toBitmap(
                    attributionSourceIcon.intrinsicWidth,
                    attributionSourceIcon.intrinsicHeight,
                )
                ?.asImageBitmap()
        }
    if (attributionSourceBitmap == null) {
        return
    }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .semantics {
                    this.contentDescription = attributionLine.contentDescription.toString()
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            modifier = Modifier.size(40.dp),
            bitmap = attributionSourceBitmap,
            contentDescription = null,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attributionLine.title.toString(),
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = attributionLine.subtitle.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// TODO: b/457388639 - support feedback bottomsheet entrypoint.
@Composable
fun AttributionFooter() {
    Row(
        modifier = Modifier.padding(8.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ctx = LocalContext.current
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier.clickable(
                        onClick = {
                            ctx.startActivity(Intent(SETTINGS_INTENT_ACTION))
                            ctx.getActivity()?.finish()
                        }
                    )
                    .semantics {
                        contentDescription =
                            ctx.getString(
                                R.string.ace_attribution_settings_button_accessibility_description
                            )
                    },
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.ic_settings_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.ace_attribution_dialog_settings_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = { ctx.getActivity()?.finish() },
            modifier =
                Modifier.size(48.dp).semantics {
                    contentDescription =
                        ctx.getString(
                            R.string.ace_attribution_thumbs_up_button_accessibility_description
                        )
                },
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.thumbs_up),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(
            onClick = { ctx.getActivity()?.finish() },
            modifier =
                Modifier.size(48.dp).semantics {
                    contentDescription =
                        ctx.getString(
                            R.string.ace_attribution_thumbs_down_button_accessibility_description
                        )
                },
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                painter = painterResource(R.drawable.thumbs_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }

    MaterialTheme(colorScheme = colorScheme) { content() }
}

fun Context.getActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> null
    }
