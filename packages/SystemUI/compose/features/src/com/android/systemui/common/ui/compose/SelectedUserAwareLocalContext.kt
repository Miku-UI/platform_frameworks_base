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

package com.android.systemui.common.ui.compose

import android.annotation.UserIdInt
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Wrapper for composables that need to be aware of the selected user as their [LocalContext] to
 * e.g. read settings for the correct user. SysUI always runs as user 0 and therefore would also
 * read settings from there by default via [LocalContext]. However, settings like [SHOW_PASSWORD]
 * are configured in the individual users (i.e. user 10, 11, 12) settings and therefore should be
 * read from their context instead.
 *
 * Usage:
 * ```
 * @Composable
 * fun YourFunction(viewModel: YourViewModel) {
 *     val selectedUserId = viewModel.selectedUserId
 *
 *     SelectedUserAwareLocalContext(selectedUserId) {
 *         TextField(...)
 *     }
 * }
 * ```
 */
@Composable
fun SelectedUserAwareLocalContext(@UserIdInt selectedUserId: Int, content: @Composable () -> Unit) {
    val selectedUserHandle = UserHandle.of(selectedUserId)
    val selectedUserContext =
        LocalContext.current.createContextAsUser(selectedUserHandle, /* flags= */ 0)

    CompositionLocalProvider(LocalContext provides selectedUserContext) { content() }
}
