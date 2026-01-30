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

package com.android.systemui.media.controls.domain.pipeline

import android.content.Context
import android.content.pm.UserInfo
import android.util.Log
import com.android.internal.annotations.KeepForWeakReference
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.media.controls.shared.MediaLogger
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.MediaPipelineRepository
import com.android.systemui.settings.UserTracker
import com.android.systemui.statusbar.NotificationLockscreenUserManager
import java.util.concurrent.Executor
import javax.inject.Inject

private const val TAG = "MediaDataFilter"
private const val DEBUG = true

/**
 * Filters data updates from [MediaDataCombineLatest] based on the current user ID, and handles user
 * switches (removing entries for the previous user, adding back entries for the current user).
 *
 * This is added at the end of the pipeline since we may still need to handle callbacks from
 * background users (e.g. timeouts).
 */
@SysUISingleton
class MediaDataFilterImpl
@Inject
constructor(
    userTracker: UserTracker,
    private val lockscreenUserManager: NotificationLockscreenUserManager,
    @Main private val executor: Executor,
    private val mediaPipelineRepository: MediaPipelineRepository,
    private val mediaLogger: MediaLogger,
) : MediaDataManager.Listener {
    /** Non-UI listeners to media changes. */
    private val _listeners: MutableSet<MediaDataProcessor.Listener> = mutableSetOf()
    val listeners: Set<MediaDataProcessor.Listener>
        get() = _listeners.toSet()

    lateinit var mediaDataProcessor: MediaDataProcessor

    // Ensure the field (and associated reference) isn't removed during optimization.
    @KeepForWeakReference
    private val userTrackerCallback =
        object : UserTracker.Callback {
            override fun onUserChanged(newUser: Int, userContext: Context) {
                handleUserSwitched()
            }

            override fun onProfilesChanged(profiles: List<UserInfo>) {
                handleProfileChanged()
            }
        }

    init {
        userTracker.addCallback(userTrackerCallback, executor)
    }

    override fun onMediaDataLoaded(
        key: String,
        oldKey: String?,
        data: MediaData,
        immediately: Boolean,
    ) {
        if (oldKey != null && oldKey != key) {
            mediaPipelineRepository.removeMediaEntry(oldKey)
        }
        mediaPipelineRepository.addMediaEntry(key, data)

        if (
            !lockscreenUserManager.isCurrentProfile(data.userId) ||
                !lockscreenUserManager.isProfileAvailable(data.userId)
        ) {
            return
        }

        mediaPipelineRepository.addCurrentUserMediaEntry(data)

        mediaLogger.logMediaLoaded(data.instanceId, data.active, "loading media")

        // Notify listeners
        listeners.forEach { it.onMediaDataLoaded(key, oldKey, data) }
    }

    override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
        mediaPipelineRepository.removeMediaEntry(key)?.let { mediaData ->
            val instanceId = mediaData.instanceId
            mediaPipelineRepository.removeCurrentUserMediaEntry(instanceId)?.let {
                mediaLogger.logMediaRemoved(instanceId, "removing media card")
                // Only notify listeners if something actually changed
                listeners.forEach { it.onMediaDataRemoved(key, userInitiated) }
            }
        }
    }

    @VisibleForTesting
    internal fun handleProfileChanged() {
        // TODO(b/317221348) re-add media removed when profile is available.
        mediaPipelineRepository.allMediaEntries.value.forEach { (key, data) ->
            if (!lockscreenUserManager.isProfileAvailable(data.userId)) {
                // Only remove media when the profile is unavailable.
                mediaPipelineRepository.removeCurrentUserMediaEntry(data.instanceId, data)
                mediaLogger.logMediaRemoved(data.instanceId, "Removing $key after profile change")
                listeners.forEach { listener -> listener.onMediaDataRemoved(key, false) }
            }
        }
    }

    @VisibleForTesting
    internal fun handleUserSwitched() {
        // If the user changes, remove all current MediaData objects.
        val listenersCopy = listeners
        val keyCopy = mediaPipelineRepository.currentUserEntries.value.keys.toMutableList()
        // Clear the list first and update loading state to remove media from UI.
        mediaPipelineRepository.clearCurrentUserMedia()
        keyCopy.forEach { instanceId ->
            mediaLogger.logMediaRemoved(instanceId, "Removing media after user change")
            getKey(instanceId)?.let {
                listenersCopy.forEach { listener -> listener.onMediaDataRemoved(it, false) }
            }
        }

        mediaPipelineRepository.allMediaEntries.value.forEach { (key, data) ->
            if (lockscreenUserManager.isCurrentProfile(data.userId)) {
                mediaPipelineRepository.addCurrentUserMediaEntry(data)
                mediaLogger.logMediaLoaded(
                    data.instanceId,
                    data.active,
                    "Re-adding $key after user change",
                )
                listenersCopy.forEach { listener -> listener.onMediaDataLoaded(key, null, data) }
            }
        }
    }

    /** Invoked when the user has dismissed the media carousel */
    fun onSwipeToDismiss() {
        if (DEBUG) Log.d(TAG, "Media carousel swiped away")
        val mediaEntries = mediaPipelineRepository.allMediaEntries.value.entries
        mediaEntries.forEach { (key, data) ->
            if (mediaPipelineRepository.currentUserEntries.value.containsKey(data.instanceId)) {
                // Force updates to listeners, needed for re-activated card
                mediaDataProcessor.setInactive(key, timedOut = true, forceUpdate = true)
            }
        }
    }

    /** Add a listener for filtered [MediaData] changes */
    fun addListener(listener: MediaDataProcessor.Listener) = _listeners.add(listener)

    /** Remove a listener that was registered with addListener */
    fun removeListener(listener: MediaDataProcessor.Listener) = _listeners.remove(listener)

    private fun getKey(instanceId: InstanceId): String? {
        val allEntries = mediaPipelineRepository.allMediaEntries.value
        val filteredEntries = allEntries.filter { (_, data) -> data.instanceId == instanceId }
        return if (filteredEntries.isNotEmpty()) {
            filteredEntries.keys.first()
        } else {
            null
        }
    }
}
