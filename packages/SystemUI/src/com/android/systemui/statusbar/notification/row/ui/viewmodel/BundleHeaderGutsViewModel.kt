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

package com.android.systemui.statusbar.notification.row.ui.viewmodel

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.res.R.string

class BundleHeaderGutsViewModel(
    @StringRes val titleText: Int,
    @StringRes val summaryText: Int,
    @DrawableRes val bundleIcon: Int,

    /** Opens the settings page for this bundle. */
    val onSettingsClicked: () -> Unit = {},

    /** Disables this bundle type in settings. */
    private val disableBundle: () -> Unit = {},

    /** Dismisses all bundle children which makes the bundle disappear. */
    private val onDismissClicked: () -> Unit = {},

    /** Closes the guts. This makes the bundle show in its normal state. */
    private val closeGuts: () -> Unit = {},
) {
    var switchState by mutableStateOf(true)

    fun getDoneOrApplyButtonText() =
        if (switchState) string.inline_done_button else string.inline_ok_button

    fun onDismissClicked() {
        closeGuts.invoke()
        onDismissClicked.invoke()
    }

    fun onDoneOrApplyClicked() {
        closeGuts.invoke()
        if (!switchState) {
            // The bundle always starts enabled otherwise the guts would not have been visible. Thus
            // we only need to update settings when the switch was toggled to false. Notifications
            // that were previously in the bundle will be re-sorted into their original locations.
            disableBundle.invoke()
        }
    }

    // Long click action for accessibility action on BundleHeaderGuts only. Touch-based long click
    // handling is done through NotificationStackScrollLayoutController.
    fun onAllyLongClicked() {
        closeGuts.invoke()
    }
}
