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

package com.android.systemui.shade.domain.interactor

import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.util.kotlin.pairwise
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Dismisses dialogs from the previous display when the shade moves.
 *
 * This is needed as dialogs don't receive configuration changes and therefore can't handle the
 * shade display change gracefully. Now, every time the shade moves away from a display, we're
 * dismissing all dialogs from that display.
 */
@SysUISingleton
class ShadeDisplaysDialogInteractor
@Inject
constructor(
    private val dialogManager: SystemUIDialogManager,
    private val shadeDisplaysRepository: ShadeDisplaysRepository,
    @Background private val coroutineScope: CoroutineScope,
) : CoreStartable {
    override fun start() {
        coroutineScope.launchTraced("ShadeDisplayDialogInteractor") {
            shadeDisplaysRepository.pendingDisplayId.pairwise().collect { (previousDisplayId, _) ->
                dialogManager.dismissDialogsForDisplayId(previousDisplayId)
            }
        }
    }
}
