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

package com.android.systemui.media.remedia.data.repository

import android.content.Context
import android.os.UserHandle
import android.provider.Settings
import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.media.controls.data.model.MediaSortKeyModel
import com.android.systemui.media.controls.shared.areIconsEqual
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.model.UpdateArtInfoModel
import com.android.systemui.util.settings.SecureSettings
import com.android.systemui.util.settings.SettingsProxyExt.observerFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An abstract repository class that holds fields and functions called by media pipeline. */
abstract class MediaPipelineRepository(
    @Application private val applicationContext: Context,
    @Application applicationScope: CoroutineScope,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    private val secureSettings: SecureSettings,
) {

    protected val mutableUserEntries: MutableStateFlow<Map<InstanceId, MediaData>> =
        MutableStateFlow(LinkedHashMap())
    val currentUserEntries: StateFlow<Map<InstanceId, MediaData>> = mutableUserEntries.asStateFlow()

    private val mutableAllEntries: MutableStateFlow<Map<String, MediaData>> =
        MutableStateFlow(LinkedHashMap())
    val allMediaEntries: StateFlow<Map<String, MediaData>> = mutableAllEntries.asStateFlow()

    private val mutableAllowMediaPlayerOnLockscreen = MutableStateFlow(true)
    val allowMediaPlayerOnLockscreen: StateFlow<Boolean> =
        mutableAllowMediaPlayerOnLockscreen.asStateFlow()

    protected val comparator =
        compareByDescending<MediaSortKeyModel> {
                it.isPlaying == true && it.playbackLocation == MediaData.PLAYBACK_LOCAL
            }
            .thenByDescending {
                it.isPlaying == true && it.playbackLocation == MediaData.PLAYBACK_CAST_LOCAL
            }
            .thenByDescending { it.active }
            .thenByDescending { !it.isResume }
            .thenByDescending { it.playbackLocation != MediaData.PLAYBACK_CAST_REMOTE }
            .thenByDescending { it.lastActive }
            .thenByDescending { it.updateTime }
            .thenByDescending { it.notificationKey }

    init {
        listenForLockscreenSettingChanges(applicationScope)
    }

    fun addMediaEntry(key: String, data: MediaData) {
        val entries = LinkedHashMap<String, MediaData>(mutableAllEntries.value)
        entries[key] = data
        mutableAllEntries.value = entries
    }

    /**
     * Removes the media entry corresponding to the given [key].
     *
     * @return media data if an entry is actually removed, `null` otherwise.
     */
    fun removeMediaEntry(key: String): MediaData? {
        val entries = LinkedHashMap<String, MediaData>(mutableAllEntries.value)
        val mediaData = entries.remove(key)
        mutableAllEntries.value = entries
        return mediaData
    }

    /** @return the old existing media data. */
    open fun addCurrentUserMediaEntry(data: MediaData): UpdateArtInfoModel? {
        val entries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        val existing = mutableUserEntries.value[data.instanceId]
        entries[data.instanceId] = data
        mutableUserEntries.value = entries
        return existing?.let { getUpdateInfoModel(it, data) }
    }

    /**
     * Removes current user media entry given the corresponding key.
     *
     * @return media data if an entry is actually removed, `null` otherwise.
     */
    open fun removeCurrentUserMediaEntry(key: InstanceId): MediaData? {
        val entries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        val mediaData = entries.remove(key)
        mutableUserEntries.value = entries
        return mediaData
    }

    /**
     * Removes current user media entry given a key and media data.
     *
     * @return true if media data is removed, false otherwise.
     */
    open fun removeCurrentUserMediaEntry(key: InstanceId, data: MediaData): Boolean {
        val entries = LinkedHashMap<InstanceId, MediaData>(mutableUserEntries.value)
        val succeed = entries.remove(key, data)
        if (!succeed) {
            return false
        }
        mutableUserEntries.value = entries
        return true
    }

    fun hasAnyMedia() = mutableUserEntries.value.entries.isNotEmpty()

    fun hasActiveMedia() = mutableUserEntries.value.any { it.value.active }

    abstract fun clearCurrentUserMedia()

    private fun getUpdateInfoModel(old: MediaData, new: MediaData): UpdateArtInfoModel {
        return UpdateArtInfoModel(
            isBackgroundUpdated = !areIconsEqual(applicationContext, new.artwork, old.artwork),
            isAppIconUpdated = !areIconsEqual(applicationContext, new.appIcon, old.appIcon),
        )
    }

    private fun listenForLockscreenSettingChanges(scope: CoroutineScope): Job {
        return scope.launch {
            secureSettings
                .observerFlow(UserHandle.USER_ALL, Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN)
                .onStart { emit(Unit) }
                .map { getMediaLockScreenSetting() }
                .distinctUntilChanged()
                .flowOn(backgroundDispatcher)
                .collectLatest { mutableAllowMediaPlayerOnLockscreen.value = it }
        }
    }

    private suspend fun getMediaLockScreenSetting(): Boolean {
        return withContext(backgroundDispatcher) {
            secureSettings.getBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                true,
                UserHandle.USER_CURRENT,
            )
        }
    }
}
