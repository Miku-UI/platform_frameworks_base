/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.media.dialog.ui.model

/**
 * Represents the full UI state for the Compose-based Output Switcher dialog. This is intended to be
 * the single source of truth from the ViewModel to the UI.
 */
data class MediaOutputUiState(
    /**
     * Data class containing all of the metadata required for the header part of the dialog based on
     * the currently playing media. Can be null when the it's not loaded yet.
     */
    val metadata: MediaMetadata?,
    /** Color scheme to render the Output Switcher dialog background in. */
    val colorScheme: DialogColorScheme,
    /** List of media related items in the Output Switcher. */
    val mediaItemList: List<MediaItem>,
    /**
     * List of data of all the buttons in the quick access shelf section. Should be rendered in the
     * same order as the list.
     */
    val quickAccessShelfButtonList: List<QuickAccessShelfButton>,
    /** The current state of the stop button. */
    val stopActionState: StopActionState,
)
