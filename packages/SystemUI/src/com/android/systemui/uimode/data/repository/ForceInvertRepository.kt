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

package com.android.systemui.uimode.data.repository

import android.app.UiModeManager
import android.app.UiModeManager.FORCE_INVERT_TYPE_DARK
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.util.kotlin.emitOnStart
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import dagger.Binds
import dagger.Module
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Exposes state related to the force invert dark theme accessibility feature. */
interface ForceInvertRepository {
    /** Flow is `true` if the user has enabled the dark theme feature, otherwise `false`. */
    val isForceInvertDark: Flow<Boolean>
}

@SysUISingleton
class ForceInvertRepositoryImpl
@Inject
constructor(
    private val uiModeManager: UiModeManager,
    @Background private val bgDispatcher: CoroutineDispatcher,
) : ForceInvertRepository {
    override val isForceInvertDark: Flow<Boolean> =
        conflatedCallbackFlow {
                val listener = UiModeManager.ForceInvertStateChangeListener { _ -> trySend(Unit) }
                uiModeManager.addForceInvertStateChangeListener(bgDispatcher.asExecutor(), listener)
                awaitClose { uiModeManager.removeForceInvertStateChangeListener(listener) }
            }
            .emitOnStart()
            .map { isForceInvertDark() }
            .distinctUntilChanged()
            .flowOn(bgDispatcher)

    private suspend fun isForceInvertDark(): Boolean =
        withContext(bgDispatcher) {
            (uiModeManager.forceInvertState and FORCE_INVERT_TYPE_DARK) == FORCE_INVERT_TYPE_DARK
        }
}

@Module
interface ForceInvertRepositoryModule {
    @Binds fun bindImpl(impl: ForceInvertRepositoryImpl): ForceInvertRepository
}
