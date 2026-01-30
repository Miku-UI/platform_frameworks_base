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

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.systemui.biometrics.shared.model.IconType
import com.android.systemui.biometrics.ui.binder.Spaghetti
import com.android.systemui.biometrics.ui.viewmodel.PromptViewModel
import com.android.systemui.res.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiometricPromptFallbackView(promptViewModel: PromptViewModel, callback: Spaghetti.Callback) {
    val fallbackViewModel = promptViewModel.promptFallbackViewModel
    val promptContent by fallbackViewModel.fallbackOptions.collectAsStateWithLifecycle(emptyList())
    val showCredential by fallbackViewModel.showCredential.collectAsStateWithLifecycle(false)
    val credentialText by fallbackViewModel.credentialKindText.collectAsStateWithLifecycle(-1)
    val credentialIcon by
        fallbackViewModel.credentialKindIcon.collectAsStateWithLifecycle(Icons.Filled.Password)

    val showManageIdentityCheck by
        fallbackViewModel.showManageIdentityCheck.collectAsStateWithLifecycle(false)
    val icCredentialButtonEnabled by
        fallbackViewModel.icCredentialButtonEnabled.collectAsStateWithLifecycle(false)
    val icCredentialSubtitle by
        fallbackViewModel.icCredentialSubtitle.collectAsStateWithLifecycle(null)
    val icShowFooter by fallbackViewModel.icShowFooter.collectAsStateWithLifecycle(false)

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    PlatformTheme {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = {
                        promptViewModel.onSwitchToAuth()
                        callback.onResumeAuthentication()
                    },
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.accessibility_back),
                    )
                }
                Text(
                    text = stringResource(R.string.biometric_dialog_fallback_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier =
                        Modifier.padding(start = 16.dp).semantics {
                            heading()
                            // TODO(391644182): Use paneTitle once prompt is moved to compose
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val options = mutableListOf<@Composable (Int, Int) -> Unit>()

                if (showCredential) {
                    options.add { index, total ->
                        OptionItem(
                            icon = credentialIcon,
                            text = stringResource(credentialText),
                            index = index,
                            total = total,
                            modifier = Modifier.testTag("fallback_credential_button"),
                            onClick = {
                                promptViewModel.onSwitchToCredential()
                                callback.onUseDeviceCredential()
                            },
                        )
                    }
                }
                if (showManageIdentityCheck) {
                    options.add { index, total ->
                        OptionItem(
                            icon = Icons.Outlined.Settings,
                            text = stringResource(R.string.biometric_dialog_manage_identity_check),
                            index = index,
                            total = total,
                            onClick = {
                                fallbackViewModel.manageIdentityCheck(context)
                                callback.onUserCanceled()
                            },
                        )
                    }
                }
                for ((optionIndex, option) in promptContent.withIndex()) {
                    options.add { index, total ->
                        OptionItem(
                            icon = getIcon(option.iconType),
                            text = option.text.toString(),
                            index = index,
                            total = total,
                            onClick = { callback.onFallbackOptionPressed(optionIndex) },
                        )
                    }
                }
                if (showManageIdentityCheck) {
                    options.add { index, total ->
                        OptionItem(
                            icon = credentialIcon,
                            text = stringResource(credentialText),
                            index = index,
                            total = total,
                            subtitle = icCredentialSubtitle,
                            enabled = icCredentialButtonEnabled,
                            onClick = {
                                promptViewModel.onSwitchToCredential()
                                callback.onUseDeviceCredential()
                            },
                        )
                    }
                }

                val total = options.size
                options.forEachIndexed { index, optionComposable -> optionComposable(index, total) }

                if (showManageIdentityCheck && icShowFooter) {
                    IdentityCheckFooter(callback, context)
                }
            }
        }
    }
}

@Composable
private fun IdentityCheckFooter(callback: Spaghetti.Callback, context: Context) {
    val linkListener = LinkInteractionListener { linkAnnotation ->
        if (linkAnnotation is LinkAnnotation.Url) {
            callback.onUserCanceled()
            val intent =
                Intent(Intent.ACTION_VIEW, linkAnnotation.url.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
        }
    }

    val url = stringResource(R.string.biometric_dialog_identity_check_learn_more_link)
    val linkText = stringResource(R.string.biometric_dialog_identity_check_footer_link_text)
    val formatString = stringResource(R.string.biometric_dialog_identity_check_footer)

    val annotatedString = buildAnnotatedString {
        // TODO: Has to be a better way to handle this
        val placeholderIndex = formatString.indexOf("%1\$s")
        append(formatString.substring(0, placeholderIndex))

        val link = LinkAnnotation.Url(url, linkInteractionListener = linkListener)
        withLink(link) { append(linkText) }
    }

    Text(
        annotatedString,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    text: String,
    index: Int,
    total: Int,
    subtitle: Int? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape =
        when (index) {
            0 -> RoundedCornerShape(28.dp, 28.dp, 4.dp, 4.dp)
            total - 1 -> RoundedCornerShape(4.dp, 4.dp, 28.dp, 28.dp)
            else -> RoundedCornerShape(4.dp)
        }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { testTagsAsResourceId = true }
                .clickable(onClick = onClick, enabled = enabled)
                .alpha(if (enabled) 1f else 0.4f),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = text, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = stringResource(subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private fun getIcon(iconType: IconType): ImageVector {
    return when (iconType) {
        IconType.ACCOUNT -> Icons.Outlined.AccountCircle
        IconType.SETTING -> Icons.Outlined.Settings
        IconType.QR_CODE -> Icons.Outlined.QrCode2
        IconType.PASSWORD -> Icons.Outlined.Password
        else -> Icons.Outlined.ViewStream // Generic Icon //TODO: This one is a placeholder
    }
}
