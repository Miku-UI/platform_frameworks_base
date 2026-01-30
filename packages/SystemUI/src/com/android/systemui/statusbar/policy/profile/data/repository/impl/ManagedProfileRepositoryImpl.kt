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

package com.android.systemui.statusbar.policy.profile.data.repository.impl

import android.app.IActivityTaskManager
import android.content.IntentFilter
import android.content.res.Resources
import android.os.RemoteException
import android.os.UserManager
import android.util.Log
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.common.coroutine.ChannelExt.trySendWithFailureLogging
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.policy.profile.data.repository.ManagedProfileRepository
import com.android.systemui.statusbar.policy.profile.shared.model.ProfileInfo
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Implementation of [ManagedProfileRepository]. */
@SysUISingleton
class ManagedProfileRepositoryImpl
@Inject
constructor(
    @Background backgroundScope: CoroutineScope,
    @Background backgroundDispatcher: CoroutineDispatcher,
    broadcastDispatcher: BroadcastDispatcher,
    private val userManager: UserManager,
    commandQueue: CommandQueue,
    activityTaskManager: IActivityTaskManager,
    userRepository: UserRepository,
) : ManagedProfileRepository {

    /** Flow that emits Unit whenever the state or availability of any profile changes. */
    private val profileChangeTrigger: Flow<Unit> =
        broadcastDispatcher
            .broadcastFlow(
                filter =
                    IntentFilter().apply {
                        addAction(android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                        addAction(android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                        addAction(android.content.Intent.ACTION_PROFILE_REMOVED)
                        addAction(android.content.Intent.ACTION_PROFILE_ACCESSIBLE)
                        addAction(android.content.Intent.ACTION_PROFILE_INACCESSIBLE)
                    }
            )
            .onStart { emit(Unit) }

    /**
     * Flow that emits whenever an app transition occurs. This is used to re-check which user's
     * activity is in the foreground.
     */
    private val appTransitionTrigger: Flow<Unit> =
        conflatedCallbackFlow {
                val appTransitionCallback =
                    object : CommandQueue.Callbacks {
                        override fun appTransitionStarting(
                            displayId: Int,
                            startTime: Long,
                            duration: Long,
                            forced: Boolean,
                        ) {
                            trySendWithFailureLogging(Unit, TAG)
                        }

                        override fun appTransitionFinished(displayId: Int) {
                            trySendWithFailureLogging(Unit, TAG)
                        }
                    }

                commandQueue.addCallback(appTransitionCallback)
                awaitClose { commandQueue.removeCallback(appTransitionCallback) }
            }
            .onStart { emit(Unit) }

    override val currentProfileInfo: StateFlow<ProfileInfo?> =
        combine(appTransitionTrigger, profileChangeTrigger, userRepository.userInfos) { _, _, _ ->
                try {
                    getProfileInfo(activityTaskManager.lastResumedActivityUserId)
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to get last resumed activity user id", e)
                    null
                }
            }
            .flowOn(backgroundDispatcher)
            .stateIn(
                backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = null,
            )

    private fun getProfileInfo(userId: Int): ProfileInfo? {
        val iconResId = userManager.getUserStatusBarIconResId(userId)
        return if (userManager.isProfile(userId) && iconResId != Resources.ID_NULL) {
            val accessibilityString = getContentDescriptionString(userId)
            ProfileInfo(userId, iconResId, accessibilityString)
        } else {
            null
        }
    }

    private fun getContentDescriptionString(userId: Int): String? {
        return try {
            userManager.getProfileAccessibilityString(userId)
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Accessibility string not found for userId: $userId")
            null
        }
    }

    companion object {
        private const val TAG = "ManagedProfileRepository"
    }
}
