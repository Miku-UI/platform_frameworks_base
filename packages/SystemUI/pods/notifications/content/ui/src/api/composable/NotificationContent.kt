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

package com.android.systemui.notifications.content.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.systemui.notifications.content.ui.viewmodel.NotificationContentViewModel

/** Interface providing a composable notification. */
public interface NotificationContent {
    /** The content of an expanded notification. */
    @Composable
    public fun Expanded(viewModelFactory: NotificationContentViewModel.Factory, modifier: Modifier)

    /** The content of a collapsed notification. */
    @Composable
    public fun Collapsed(viewModelFactory: NotificationContentViewModel.Factory, modifier: Modifier)

    /**
     * The content of a notification preview. This is a non-interactive version of the collapsed
     * notification, that doesn't have an expand button and cannot be tapped.
     */
    @Composable
    public fun Preview(viewModelFactory: NotificationContentViewModel.Factory, modifier: Modifier)
}
