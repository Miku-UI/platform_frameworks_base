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
package com.android.systemui.media.dialog

import android.content.Context
import com.android.settingslib.media.MediaDevice
import com.android.systemui.media.dialog.MediaItem.DeviceMediaItem
import com.android.systemui.media.dialog.MediaItem.GroupDividerMediaItem
import com.android.systemui.res.R
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Collectors

/** A proxy of holding the list of Output Switcher's output media items. */
class OutputMediaItemListProxy(private val mContext: Context) {
    private val mOutputMediaItemList: MutableList<MediaItem> = CopyOnWriteArrayList()

    // Use separated lists to hold different media items and create the list of output media items
    // by using those separated lists and group dividers.
    private val mSelectedMediaItems: MutableList<MediaItem> = CopyOnWriteArrayList()
    private val mSuggestedMediaItems: MutableList<MediaItem> = CopyOnWriteArrayList()
    private val mSpeakersAndDisplaysMediaItems: MutableList<MediaItem> = CopyOnWriteArrayList()

    /** Returns the list of output media items. */
    fun getOutputMediaItemList(): MutableList<MediaItem> {
        if (isEmpty() && !mOutputMediaItemList.isEmpty()) {
            // Ensures mOutputMediaItemList is empty when all individual media item lists are empty,
            // preventing unexpected state issues.
            mOutputMediaItemList.clear()
        } else if (!isEmpty() && mOutputMediaItemList.isEmpty()) {
            // When any individual media item list is modified, the cached mOutputMediaItemList is
            // emptied. On the next request for the output media item list, a fresh list is created
            // and stored in the cache.
            mOutputMediaItemList.addAll(createOutputMediaItemList())
        }
        return mOutputMediaItemList
    }

    private fun createOutputMediaItemList(): MutableList<MediaItem> {
        val finalMediaItems: MutableList<MediaItem> = CopyOnWriteArrayList()
        finalMediaItems.addAll(mSelectedMediaItems)
        if (!mSuggestedMediaItems.isEmpty()) {
            finalMediaItems.add(
                GroupDividerMediaItem(
                    title = mContext.getString(R.string.media_output_group_title_suggested)
                )
            )
            finalMediaItems.addAll(mSuggestedMediaItems)
        }
        if (!mSpeakersAndDisplaysMediaItems.isEmpty()) {
            finalMediaItems.add(
                GroupDividerMediaItem(
                    title =
                        mContext.getString(R.string.media_output_group_title_speakers_and_displays)
                )
            )
            finalMediaItems.addAll(mSpeakersAndDisplaysMediaItems)
        }
        return finalMediaItems
    }

    /** Updates the list of output media items with a given list of media devices. */
    fun updateMediaDevices(
        devices: List<MediaDevice>,
        connectedMediaDevice: MediaDevice?,
        needToHandleMutingExpectedDevice: Boolean,
    ) {
        val selectedOrConnectedMediaDeviceIds =
            devices.stream().filter { it.isSelected }.map { it.id }.collect(Collectors.toSet())
        connectedMediaDevice?.let { selectedOrConnectedMediaDeviceIds.add(it.id) }

        val selectedMediaItems = mutableListOf<MediaItem>()
        val suggestedMediaItems = mutableListOf<MediaItem>()
        val speakersAndDisplaysMediaItems = mutableListOf<MediaItem>()
        val deviceIdToMediaItemMap: MutableMap<String, MediaItem> = HashMap()
        buildMediaItems(
            devices,
            selectedOrConnectedMediaDeviceIds,
            needToHandleMutingExpectedDevice,
            selectedMediaItems,
            suggestedMediaItems,
            speakersAndDisplaysMediaItems,
            deviceIdToMediaItemMap,
        )

        val updatedSelectedMediaItems: MutableList<MediaItem> = CopyOnWriteArrayList()
        val updatedSuggestedMediaItems: MutableList<MediaItem> = CopyOnWriteArrayList()
        val updatedSpeakersAndDisplaysMediaItems: MutableList<MediaItem> = CopyOnWriteArrayList()
        if (isEmpty()) {
            updatedSelectedMediaItems.addAll(selectedMediaItems)
            updatedSuggestedMediaItems.addAll(suggestedMediaItems)
            updatedSpeakersAndDisplaysMediaItems.addAll(speakersAndDisplaysMediaItems)
        } else {
            val updatedDeviceIds: MutableSet<String> = HashSet()
            // Preserve the existing media item order while updating with the latest device
            // information. Some items may retain their original group (suggested, speakers and
            // displays) to maintain this order.
            updateMediaItems(
                mSelectedMediaItems,
                updatedSelectedMediaItems,
                deviceIdToMediaItemMap,
                updatedDeviceIds,
            )
            updateMediaItems(
                mSuggestedMediaItems,
                updatedSuggestedMediaItems,
                deviceIdToMediaItemMap,
                updatedDeviceIds,
            )
            updateMediaItems(
                mSpeakersAndDisplaysMediaItems,
                updatedSpeakersAndDisplaysMediaItems,
                deviceIdToMediaItemMap,
                updatedDeviceIds,
            )

            // Append new media items that are not already in the existing lists to the output list.
            val remainingMediaItems = mutableListOf<MediaItem>()
            remainingMediaItems.addAll(getRemainingMediaItems(selectedMediaItems, updatedDeviceIds))
            remainingMediaItems.addAll(
                getRemainingMediaItems(suggestedMediaItems, updatedDeviceIds)
            )
            remainingMediaItems.addAll(
                getRemainingMediaItems(speakersAndDisplaysMediaItems, updatedDeviceIds)
            )
            updatedSpeakersAndDisplaysMediaItems.addAll(remainingMediaItems)
        }

        mSelectedMediaItems.clear()
        mSelectedMediaItems.addAll(updatedSelectedMediaItems)
        mSuggestedMediaItems.clear()
        mSuggestedMediaItems.addAll(updatedSuggestedMediaItems)
        mSpeakersAndDisplaysMediaItems.clear()
        mSpeakersAndDisplaysMediaItems.addAll(updatedSpeakersAndDisplaysMediaItems)

        // The cached mOutputMediaItemList is cleared upon any update to individual media item
        // lists. This ensures getOutputMediaItemList() computes and caches a fresh list on the next
        // invocation.
        mOutputMediaItemList.clear()
    }

    /** Removes the media items with muting expected devices. */
    fun removeMutingExpectedDevices() {
        removeMutingExpectedDevicesFromList(mSelectedMediaItems)
        removeMutingExpectedDevicesFromList(mSuggestedMediaItems)
        removeMutingExpectedDevicesFromList(mSpeakersAndDisplaysMediaItems)
        removeMutingExpectedDevicesFromList(mOutputMediaItemList)
    }

    private fun removeMutingExpectedDevicesFromList(list: MutableList<MediaItem>) {
        list.removeIf { it is DeviceMediaItem && it.mediaDevice.isMutingExpectedDevice }
    }

    /** Clears the output media item list. */
    fun clear() {
        mSelectedMediaItems.clear()
        mSuggestedMediaItems.clear()
        mSpeakersAndDisplaysMediaItems.clear()
        mOutputMediaItemList.clear()
    }

    /** Returns whether the output media item list is empty. */
    fun isEmpty(): Boolean =
        mSelectedMediaItems.isEmpty() &&
            mSuggestedMediaItems.isEmpty() &&
            mSpeakersAndDisplaysMediaItems.isEmpty()

    private fun buildMediaItems(
        devices: List<MediaDevice>,
        selectedOrConnectedMediaDeviceIds: MutableSet<String>,
        needToHandleMutingExpectedDevice: Boolean,
        selectedMediaItems: MutableList<MediaItem>,
        suggestedMediaItems: MutableList<MediaItem>,
        speakersAndDisplaysMediaItems: MutableList<MediaItem>,
        deviceIdToMediaItemMap: MutableMap<String, MediaItem>,
    ) {
        for (device in devices) {
            val deviceId = device.id
            val mediaItem = DeviceMediaItem(device)
            if (needToHandleMutingExpectedDevice && device.isMutingExpectedDevice) {
                selectedMediaItems.add(0, mediaItem)
            } else if (
                !needToHandleMutingExpectedDevice &&
                    selectedOrConnectedMediaDeviceIds.contains(device.id)
            ) {
                selectedMediaItems.add(mediaItem)
            } else if (
                device.isSuggestedDevice && suggestedMediaItems.size < MAX_SUGGESTED_DEVICE_COUNT
            ) {
                suggestedMediaItems.add(mediaItem)
            } else {
                speakersAndDisplaysMediaItems.add(mediaItem)
            }
            deviceIdToMediaItemMap.put(deviceId, mediaItem)
        }
    }

    /** Updates the input media items in-place based on the latest existing media items. */
    private fun updateMediaItems(
        existingMediaItems: MutableList<MediaItem>,
        updatedMediaItems: MutableList<MediaItem>,
        deviceIdToMediaItemMap: MutableMap<String, MediaItem>,
        updatedDeviceIds: MutableSet<String>,
    ) {
        val existingDeviceIds = getDeviceIds(existingMediaItems)
        for (deviceId in existingDeviceIds) {
            deviceIdToMediaItemMap[deviceId]?.let {
                updatedMediaItems.add(it)
                updatedDeviceIds.add(deviceId)
            }
        }
    }

    /**
     * Returns media items from the input list that are not associated with the given device IDs.
     */
    private fun getRemainingMediaItems(
        mediaItems: List<MediaItem>,
        deviceIds: Set<String>,
    ): List<MediaItem> {
        return mediaItems.filterIsInstance<DeviceMediaItem>().filter {
            it.mediaDevice.id !in deviceIds
        }
    }

    /** Returns a list of media device IDs for the given list of media items. */
    private fun getDeviceIds(mediaItems: List<MediaItem>): List<String> {
        return mediaItems.filterIsInstance<DeviceMediaItem>().map { it.mediaDevice.id }
    }

    companion object {
        private const val MAX_SUGGESTED_DEVICE_COUNT = 2
    }
}
