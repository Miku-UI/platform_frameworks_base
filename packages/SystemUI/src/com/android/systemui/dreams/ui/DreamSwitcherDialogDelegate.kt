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

package com.android.systemui.dreams.ui

import android.content.Context
import com.android.compose.theme.PlatformTheme
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dream.ui.composable.DreamSwitcherDialog
import com.android.systemui.dreams.ui.viewmodel.DreamDialogController
import com.android.systemui.dreams.ui.viewmodel.DreamSwitcherDialogViewModel
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class DreamSwitcherDialogDelegate
@AssistedInject
constructor(
    private val dialogFactory: SystemUIDialogFactory,
    private val viewModelFactory: DreamSwitcherDialogViewModel.Factory,
    @param:Application private val context: Context,
    @Assisted private val dialogController: DreamDialogController,
) : SystemUIDialog.Delegate {
    override fun createDialog(): SystemUIDialog {
        return dialogFactory
            .create(
                context,
                theme = R.style.Theme_SystemUI_Dialog_SelectDream,
                dismissOnDeviceLock = false,
            ) {
                PlatformTheme { DreamSwitcherDialog(viewModelFactory, dialogController) }
            }
            .also { dialog ->
                val title = context.getString(R.string.dream_switcher_choose_screensaver)
                dialog.window?.setTitle(title)
            }
    }

    @AssistedFactory
    fun interface Factory {
        fun create(dialogController: DreamDialogController): DreamSwitcherDialogDelegate
    }
}
