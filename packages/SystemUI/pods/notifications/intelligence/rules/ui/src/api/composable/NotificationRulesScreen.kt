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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleFreeformTextCreationViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.RulesScreenViewState

/** Interface providing a composable to render the notification rules screen. */
public interface NotificationRulesScreen {
    /**
     * Renders a notification rules screen.
     *
     * @param dismissRulesScreen a function to invoke to hide the rules screen and go back to the
     *   previous page.
     * @param startingBackStack the initial back stack to start with.
     */
    @Composable
    public fun Content(
        viewModelFactory: NotificationRulesScreenViewModel.Factory,
        editViewModelFactory: NotificationRuleEditViewModel.Factory,
        freeformTextViewModelFactory: NotificationRuleFreeformTextCreationViewModel.Factory,
        dismissRulesScreen: () -> Unit,
        startingBackStack: List<RulesScreenViewState> = listOf(RulesScreenViewState.CurrentRules),
        modifier: Modifier,
    )
}
