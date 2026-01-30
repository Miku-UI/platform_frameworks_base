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

package com.android.systemui.user.domain.interactor

import android.annotation.UserIdInt
import android.os.UserHandle
import android.os.UserManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Encapsulates logic related to Headless System User Mode (`HSUM`). */
interface HeadlessSystemUserMode {

    /** Returns `true` if the device is `HSUM`. */
    fun isHeadlessSystemUserMode(): Boolean

    /**
     * Returns `true` if the given `userId` is the Headless System User (i.e., it's the system user
     * in a HSUM device)
     */
    suspend fun isHeadlessSystemUser(@UserIdInt userId: Int): Boolean
}

@SysUISingleton
class HeadlessSystemUserModeImpl
@Inject
constructor(@Background private val backgroundDispatcher: CoroutineDispatcher) :
    HeadlessSystemUserMode {

    override fun isHeadlessSystemUserMode(): Boolean {
        return UserManager.isHeadlessSystemUserMode()
    }

    override suspend fun isHeadlessSystemUser(@UserIdInt userId: Int): Boolean {
        return withContext(backgroundDispatcher) {
            // NOTE: ideally it should use UserManager.isSystem() instead of checking the userId
            // directly, but it would overcomplicate it (for example, it would require callers to
            // use a UserContextProvider to get the proper user context, and that would just work
            // for the current user). And pragmatically speaking, the system user is always 0.
            isHeadlessSystemUserMode() && userId == UserHandle.USER_SYSTEM
        }
    }
}
