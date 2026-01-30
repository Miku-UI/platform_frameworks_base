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

package com.android.systemui.statusbar.notification.row

import android.content.Context
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.notifications.ui.composable.row.BundleHeaderGuts
import com.android.systemui.notifications.ui.composable.row.createBundleHeaderGutsComposeView
import com.android.systemui.statusbar.notification.collection.BundleEntryAdapter
import com.android.systemui.statusbar.notification.row.NotificationGuts.GutsContent
import com.android.systemui.statusbar.notification.row.ui.viewmodel.BundleHeaderGutsViewModel

/** The guts content that provides the view to be displayed when a bundle header is long pressed. */
class BundleHeaderGutsContent(context: Context) : GutsContent {

    private var composeView: ComposeView = createBundleHeaderGutsComposeView(context)
    private var gutsParent: NotificationGuts? = null

    fun bindNotification(
        row: ExpandableNotificationRow,
        onSettingsClicked: () -> Unit = {},
        onDismissClicked: () -> Unit = {},
        enableBundle: (type: Int, enable: Boolean) -> Unit,
    ) {
        val repository = (row.entryAdapter as BundleEntryAdapter).entry.bundleRepository

        val viewModel =
            BundleHeaderGutsViewModel(
                titleText = repository.titleText,
                bundleIcon = repository.bundleIcon,
                summaryText = repository.summaryText,
                disableBundle = { enableBundle(repository.bundleType, false) },
                onSettingsClicked = onSettingsClicked,
                closeGuts = { gutsParent?.closeControls(composeView, true) },
                onDismissClicked = onDismissClicked,
            )

        composeView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                // if we have an attached (visible) bundle, the BE setting and thus the switch must
                // be 'enabled'
                viewModel.switchState = true
                composeView.setContent {
                    // TODO(b/399588047): Check if we can init PlatformTheme once instead of once
                    //  per ComposeView
                    PlatformTheme {
                        BundleHeaderGuts(
                            viewModel,
                            modifier =
                                Modifier.semantics(mergeDescendants = true) {
                                    onLongClick(
                                        action = {
                                            viewModel.onAllyLongClicked()
                                            true
                                        }
                                    )
                                },
                        )
                    }
                }
            }
        }
    }

    override fun setGutsParent(listener: NotificationGuts?) {
        gutsParent = listener
    }

    override fun getContentView(): View {
        return composeView
    }

    override fun getActualHeight(): Int {
        return composeView.measuredHeight
    }

    override fun handleCloseControls(save: Boolean, force: Boolean): Boolean {
        return false
    }

    override fun willBeRemoved(): Boolean {
        return false
    }

    override fun shouldBeSavedOnClose(): Boolean {
        return false
    }

    override fun needsFalsingProtection(): Boolean {
        return true
    }
}
