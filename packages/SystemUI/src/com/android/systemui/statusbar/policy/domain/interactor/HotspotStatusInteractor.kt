/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.policy.HotspotController
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Interactor responsible for providing hotspot status. */
@SysUISingleton
class HotspotStatusInteractor
@Inject
constructor(
    @Background private val bgDispatcher: CoroutineDispatcher,
    @Background private val scope: CoroutineScope,
    private val controller: HotspotController,
) {

    /** Flow emitting the current hotspot enabled status. */
    val isEnabled: StateFlow<Boolean> =
        conflatedCallbackFlow {
                val callback = HotspotController.Callback { enabled, _ -> trySend(enabled) }
                controller.addCallback(callback)
                awaitClose { controller.removeCallback(callback) }
            }
            .flowOn(bgDispatcher)
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = controller.isHotspotEnabled(),
            )
}
