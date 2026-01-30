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

package com.android.systemui.statusbar.pipeline.shared.ui.model

/** Represents the user's latest toggle action for Wi-Fi, for optimistic UI updates. */
enum class WifiToggleState {
    /** The default state, no toggle action is in progress. */
    Normal,

    /** The user has just requested to pause Wi-Fi */
    Pausing,

    /** The user has just requested to scan for Wi-Fi */
    Scanning,
}
