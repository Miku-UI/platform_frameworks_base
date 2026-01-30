/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.controls.shared.model.MediaDeviceData
import com.android.systemui.media.controls.shared.model.SuggestionData
import javax.inject.Inject

/** Combines [MediaDataManager.Listener] events with [MediaDeviceManager.Listener] events. */
class MediaDataCombineLatest @Inject constructor() :
    MediaDataManager.Listener, MediaDeviceManager.Listener {

    private val listeners: MutableSet<MediaDataManager.Listener> = mutableSetOf()
    private val entries: MutableMap<String, Triple<MediaData?, MediaDeviceData?, SuggestionData?>> =
        mutableMapOf()

    override fun onMediaDataLoaded(
        key: String,
        oldKey: String?,
        data: MediaData,
        immediately: Boolean,
    ) {
        if (oldKey != null && oldKey != key && entries.contains(oldKey)) {
            val previousEntry = entries.remove(oldKey)
            val (mediaDeviceData, suggestionData) = previousEntry?.second to previousEntry?.third
            entries[key] = Triple(data, mediaDeviceData, suggestionData)
            update(key, oldKey)
        } else {
            val previousEntry = entries[key]
            val (mediaDeviceData, suggestionData) = previousEntry?.second to previousEntry?.third
            entries[key] = Triple(data, mediaDeviceData, suggestionData)
            update(key, key)
        }
    }

    override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
        remove(key, userInitiated)
    }

    override fun onMediaDeviceChanged(key: String, oldKey: String?, data: MediaDeviceData?) {
        if (oldKey != null && oldKey != key && entries.contains(oldKey)) {
            val previousEntry = entries.remove(oldKey)
            val (mediaData, suggestionData) = previousEntry?.first to previousEntry?.third
            entries[key] = Triple(mediaData, data, suggestionData)
            update(key, oldKey)
        } else {
            val previousEntry = entries[key]
            val (mediaData, suggestionData) = previousEntry?.first to previousEntry?.third
            entries[key] = Triple(mediaData, data, suggestionData)
            update(key, key)
        }
    }

    override fun onSuggestionDataChanged(key: String, oldKey: String?, data: SuggestionData?) {
        if (oldKey != null && oldKey != key && entries.contains(oldKey)) {
            val previousEntry = entries.remove(oldKey)
            val (mediaData, mediaDeviceData) = previousEntry?.first to previousEntry?.second
            entries[key] = Triple(mediaData, mediaDeviceData, data)
            update(key, oldKey)
        } else {
            val previousEntry = entries[key]
            val (mediaData, mediaDeviceData) = previousEntry?.first to previousEntry?.second
            entries[key] = Triple(mediaData, mediaDeviceData, data)
            update(key, key)
        }
    }

    override fun onMediaDeviceAndSuggestionDataChanged(
        key: String,
        oldKey: String?,
        device: MediaDeviceData?,
        suggestion: SuggestionData?,
    ) {
        if (oldKey != null && oldKey != key && entries.contains(oldKey)) {
            val previousEntry = entries.remove(oldKey)
            val mediaData = previousEntry?.first
            entries[key] = Triple(mediaData, device, suggestion)
            update(key, oldKey)
        } else {
            val previousEntry = entries[key]
            val mediaData = previousEntry?.first
            entries[key] = Triple(mediaData, device, suggestion)
            update(key, key)
        }
    }

    override fun onKeyRemoved(key: String, userInitiated: Boolean) {
        remove(key, userInitiated)
    }

    /**
     * Add a listener for [MediaData] changes that has been combined with latest [MediaDeviceData].
     */
    fun addListener(listener: MediaDataManager.Listener) = listeners.add(listener)

    /** Remove a listener registered with addListener. */
    fun removeListener(listener: MediaDataManager.Listener) = listeners.remove(listener)

    private fun update(key: String, oldKey: String?) {
        val mediaData = entries[key]?.first
        val mediaDeviceData = entries[key]?.second
        val suggestionData = entries[key]?.third
        if (mediaData != null && mediaDeviceData != null) {
            val data = mediaData.copy(device = mediaDeviceData, suggestionData = suggestionData)
            val listenersCopy = listeners.toSet()
            listenersCopy.forEach { it.onMediaDataLoaded(key, oldKey, data) }
        }
    }

    private fun remove(key: String, userInitiated: Boolean) {
        entries.remove(key)?.let {
            val listenersCopy = listeners.toSet()
            listenersCopy.forEach { it.onMediaDataRemoved(key, userInitiated) }
        }
    }
}
