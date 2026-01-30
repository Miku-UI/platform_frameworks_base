/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyboard.stickykeys.ui

import android.app.Dialog
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyboard.stickykeys.StickyKeysLogger
import com.android.systemui.keyboard.stickykeys.ui.viewmodel.StickyKeysIndicatorViewModel
import com.android.systemui.settings.DisplayTracker
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@SysUISingleton
class StickyKeysIndicatorCoordinator
@Inject
constructor(
    @Application private val applicationScope: CoroutineScope,
    private val stickyKeyDialogFactory: StickyKeyDialogFactory,
    private val viewModel: StickyKeysIndicatorViewModel,
    private val stickyKeysLogger: StickyKeysLogger,
    private val displayTracker: DisplayTracker,
) {

    private var dialogs = mutableMapOf<Int, Dialog?>()

    fun startListening() {
        applicationScope.launch {
            viewModel.indicatorContent.collect { stickyKeys ->
                stickyKeysLogger.logNewUiState(stickyKeys)
                if (stickyKeys.isEmpty()) {
                    displayTracker.allDisplays.forEach {
                        dialogs[it.displayId]?.dismiss()
                        dialogs[it.displayId] = null
                    }
                } else {
                    displayTracker.allDisplays
                        .filter { dialogs[it.displayId] == null }
                        .forEach { display ->
                            val newDialog = stickyKeyDialogFactory.create(display, viewModel)
                            dialogs[display.displayId] = newDialog
                            newDialog.show()
                        }
                }
            }
        }
    }
}
