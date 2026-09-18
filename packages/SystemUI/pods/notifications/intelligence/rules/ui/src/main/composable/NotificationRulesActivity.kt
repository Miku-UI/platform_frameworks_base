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

import android.os.Bundle
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.compose.theme.PlatformTheme
import com.android.systemui.notifications.intelligence.rules.shared.NmContextualDisplayLaunch
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleEditViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRuleFreeformTextCreationViewModel
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.NotificationRulesScreenViewModel
import javax.inject.Inject

/**
 * An activity that handles everything related to notification rules. This includes displaying the
 * current list of rules as well as creating, editing, & deleting rules.
 */
public class NotificationRulesActivity
@Inject
constructor(
    private val viewModelFactory: NotificationRulesScreenViewModel.Factory,
    private val editViewModelFactory: NotificationRuleEditViewModel.Factory,
    private val freeformTextViewModelFactory: NotificationRuleFreeformTextCreationViewModel.Factory,
    private val notificationRulesScreen: NotificationRulesScreen,
) : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!NmContextualDisplayLaunch.isEnabled) {
            finish()
        }
        if (userId != UserHandle.USER_SYSTEM) {
            finish()
        }

        setContent {
            PlatformTheme {
                notificationRulesScreen.Content(
                    viewModelFactory = viewModelFactory,
                    editViewModelFactory = editViewModelFactory,
                    freeformTextViewModelFactory = freeformTextViewModelFactory,
                    dismissRulesScreen = { finish() },
                    modifier =
                        Modifier.background(MaterialTheme.colorScheme.background)
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .fillMaxSize(),
                )
            }
        }
    }
}
