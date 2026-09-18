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

package com.android.systemui.accessibility.shortcutchooser.ui.composable

import android.accessibilityservice.AccessibilityServiceInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.theme.PlatformTheme
import com.android.internal.accessibility.dialog.AccessibilityServiceWarning

/**
 * Dialog content for warning the user about enabling an untrusted accessibility service. Used by
 * `SystemUIDialogFactory.create`.
 *
 * @param info The info of the accessibility service to warn about in the dialog.
 * @param onAllowClick The callback for clicking the allow button
 * @param onDenyClick The callback for clicking the deny button
 */
@Composable
fun ServiceWarningDialogContent(
    info: AccessibilityServiceInfo,
    onAllowClick: () -> Unit,
    onDenyClick: () -> Unit,
) {
    PlatformTheme {
        AndroidView(
            { context ->
                AccessibilityServiceWarning.createAccessibilityServiceWarningDialogContentView(
                    context,
                    info,
                    /* allowListener = */ { onAllowClick() },
                    /* denyListener = */ { onDenyClick() },
                    /* uninstallListener = */ {},
                )
            },
            modifier = Modifier.testTag("service_warning_dialog"),
        )
    }
}
