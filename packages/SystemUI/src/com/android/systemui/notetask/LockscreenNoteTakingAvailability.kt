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

package com.android.systemui.notetask

import android.content.Context
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Encapsulates the logic to determine if note taking on the lock screen is available. */
@SysUISingleton
class LockscreenNoteTakingAvailability
@Inject
constructor(
    private val context: Context,
    @Background private val bgDispatcher: CoroutineDispatcher,
) {

    /** Whether note taking on the lock screen is supported without user consent flow. */
    private val isLegacyUnconsentedLockScreenNoteTakingSupported: Boolean by lazy {
        context.resources.getBoolean(R.bool.config_supportLegacyUnconsentedLockScreenNoteTaking)
    }

    /** Returns true if note taking on lock screen is enabled. */
    suspend fun isLockscreenNoteTakingEnabled(): Boolean =
        withContext(bgDispatcher) {
            // TODO(b/491809338): If consent flow flag is enabled check consent instead of
            //  isLegacyUnconsentedLockScreenNoteTakingSupported.
            isLegacyUnconsentedLockScreenNoteTakingSupported
        }

    /** Returns true if notes should show in lockscreen shortcut picker. */
    suspend fun shouldShowNotesInLockscreenShortcutPicker(): Boolean =
        withContext(bgDispatcher) {
            // TODO(b/491809338):If consent flow flag is enabled don't check
            //  isLegacyUnconsentedLockScreenNoteTakingSupported, just return true.
            isLegacyUnconsentedLockScreenNoteTakingSupported
        }
}
