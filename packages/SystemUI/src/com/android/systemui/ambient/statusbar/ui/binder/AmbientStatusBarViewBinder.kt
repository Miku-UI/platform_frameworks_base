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

package com.android.systemui.ambient.statusbar.ui.binder

import android.content.Context
import android.util.Log
import android.view.Display
import android.widget.LinearLayout
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.displaylib.PerDisplayRepository
import com.android.systemui.ambient.statusbar.ui.AmbientStatusBarViewModel
import com.android.systemui.compose.modifiers.sysUiResTagContainer
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.statusbar.chips.ui.compose.OngoingActivityChips
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.ConnectedDisplaysStatusBarNotificationIconViewStore

object AmbientStatusBarViewBinder {

    /**
     * Binds the ongoing activity chips view to the view model, and sets content for the compose
     * view.
     */
    @JvmStatic
    fun bindOngoingActivityChipsView(
        context: Context,
        ongoingActivityChipsView: ComposeView,
        ambientStatusBarViewModelFactory: AmbientStatusBarViewModel.Factory,
        iconViewStoreFactory: ConnectedDisplaysStatusBarNotificationIconViewStore.Factory,
        perDisplayDisplaySubcomponentRepository: PerDisplayRepository<SystemUIDisplaySubcomponent>,
    ) {
        ongoingActivityChipsView.repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                with(ongoingActivityChipsView) {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )

                    setContent {
                        val viewModel =
                            rememberViewModel("DreamStatusBar.AmbientStatusBarViewModel") {
                                val ongoingActivityChipsViewModel =
                                    getOngoingActivityChipsViewModel(
                                        perDisplayDisplaySubcomponentRepository,
                                        context,
                                    )
                                ambientStatusBarViewModelFactory.create(
                                    ongoingActivityChipsViewModel
                                )
                            }
                        val iconViewStore =
                            if (StatusBarConnectedDisplays.isEnabled) {
                                rememberViewModel("DreamStatusBar.IconViewStore") {
                                    iconViewStoreFactory.create(context.displayId)
                                }
                            } else {
                                null
                            }
                        val chips by viewModel.ongoingActivityChips.collectAsStateWithLifecycle()

                        OngoingActivityChips(
                            chips = chips,
                            iconViewStore = iconViewStore,
                            onChipBoundsChanged = viewModel::onChipBoundsChanged,
                            modifier = Modifier.sysUiResTagContainer(),
                        )
                    }
                }
            }
        }
    }

    private fun getOngoingActivityChipsViewModel(
        perDisplayDisplaySubcomponentRepository: PerDisplayRepository<SystemUIDisplaySubcomponent>,
        context: Context,
    ): OngoingActivityChipsViewModel {
        // TODO:b/425316868 - Make AmbientStatusBarComponent a Subcomponent of
        //  SystemUIDisplaySubcomponent so that we can directly inject display specific dependencies
        var displaySubcomponent = perDisplayDisplaySubcomponentRepository[context.displayId]
        if (displaySubcomponent == null) {
            Log.e(TAG, "No display subcomponent for display ${context.displayId}")
            displaySubcomponent = perDisplayDisplaySubcomponentRepository[Display.DEFAULT_DISPLAY]!!
        }
        val ongoingActivityChipsViewModel = displaySubcomponent.ongoingActivityChipsViewModel
        return ongoingActivityChipsViewModel
    }

    private const val TAG = "AmbientStatusBarViewBinder"
}
