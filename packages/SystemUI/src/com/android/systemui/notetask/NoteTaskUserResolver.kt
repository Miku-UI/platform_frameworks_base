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

import android.app.admin.DevicePolicyManager
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.notetask.NoteTaskEntryPoint.QUICK_AFFORDANCE
import com.android.systemui.notetask.NoteTaskEntryPoint.TAIL_BUTTON
import com.android.systemui.settings.UserTracker
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A utility class providing methods to resolve the correct [UserHandle] for note-taking tasks. */
@SysUISingleton
class NoteTaskUserResolver
@Inject
constructor(
    private val userManager: UserManager,
    private val devicePolicyManager: DevicePolicyManager,
    private val userTracker: UserTracker,
    private val secureSettings: SecureSettings,
    @Background private val bgDispatcher: CoroutineDispatcher,
) {

    /**
     * Returns the parent [UserHandle] if the given [UserHandle] is a managed profile, otherwise
     * returns the [UserHandle] itself.
     */
    suspend fun resolveParentUserIfManaged(userHandle: UserHandle): UserHandle? =
        withContext(bgDispatcher) {
            if (userManager.isManagedProfile(userHandle.identifier)) {
                userManager.getProfileParent(userHandle)
            } else {
                userHandle
            }
        }

    /**
     * Note task can be opened as a different user depending on the [entryPoint]:
     * 1. stylus tail button: the user selected in the notes role setting.
     * 2. lock screen quick affordance: since there is no user setting, the main profile notes app
     *    is used as default for work profile devices while the work profile notes app is used for
     *    COPE devices.
     * 3. Other entry point: the current user from [UserTracker.userHandle].
     */
    suspend fun getUserForHandlingNoteTaking(entryPoint: NoteTaskEntryPoint): UserHandle =
        withContext(bgDispatcher) {
            when {
                entryPoint == TAIL_BUTTON -> preferredUserForTailButton()
                devicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile &&
                    entryPoint == QUICK_AFFORDANCE -> {
                    userTracker.userProfiles
                        .firstOrNull { userManager.isManagedProfile(it.id) }
                        ?.userHandle ?: userTracker.userHandle
                }
                // On work profile devices, SysUI always run in the main user.
                else -> userTracker.userHandle
            }
        }

    private suspend fun preferredUserForTailButton(): UserHandle {
        val trackingUserId = userTracker.userHandle.identifier
        val userId =
            secureSettings.getIntForUser(
                /* name= */ Settings.Secure.DEFAULT_NOTE_TASK_PROFILE,
                /* def= */ trackingUserId,
                /* userHandle= */ trackingUserId,
            )
        return UserHandle.of(userId)
    }
}
