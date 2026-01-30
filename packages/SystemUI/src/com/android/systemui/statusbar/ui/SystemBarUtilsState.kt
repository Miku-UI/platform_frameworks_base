/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.ui

import android.content.Context
import com.android.internal.policy.SystemBarUtils
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.onConfigChanged
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Tracks state from [SystemBarUtils]. Using this is both more efficient and more testable than
 * using [SystemBarUtils] directly.
 */
class SystemBarUtilsState
@AssistedInject
constructor(
    @Assisted context: Context,
    @Background bgContext: CoroutineContext,
    @Main mainContext: CoroutineContext,
    @Assisted configurationController: ConfigurationController,
    proxy: SystemBarUtilsProxy,
) {
    /** @see SystemBarUtils.getStatusBarHeight */
    val statusBarHeight: Flow<Int> =
        configurationController.onConfigChanged
            .onStart<Any> { emit(Unit) }
            .flowOn(mainContext)
            .conflate()
            .map { proxy.getStatusBarHeight(context) }
            .distinctUntilChanged()
            .flowOn(bgContext)
            .conflate()

    val statusBarHeaderHeightKeyguard: Flow<Int> =
        configurationController.onConfigChanged
            .onStart<Any> { emit(Unit) }
            .flowOn(mainContext)
            .conflate()
            .map { proxy.getStatusBarHeaderHeightKeyguard(context) }
            .distinctUntilChanged()
            .flowOn(bgContext)
            .conflate()

    @AssistedFactory
    interface Factory {
        fun create(
            context: Context,
            configurationController: ConfigurationController,
        ): SystemBarUtilsState
    }
}
