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
package com.android.systemui.media.dialog

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.android.media.flags.Flags
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_GROUPING
import com.android.settingslib.media.MediaDevice
import com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_GO_TO_APP
import com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_NONE
import com.android.settingslib.media.MediaDevice.SelectionBehavior.SELECTION_BEHAVIOR_TRANSFER
import com.android.systemui.media.dialog.MediaItem.DeviceMediaItem
import com.android.systemui.media.dialog.MediaOutputAdapterBase.ConnectionState.CONNECTED
import com.android.systemui.media.dialog.MediaOutputAdapterBase.ConnectionState.CONNECTING
import com.android.systemui.media.dialog.MediaOutputAdapterBase.ConnectionState.DISCONNECTED
import com.android.systemui.res.R
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A parent RecyclerView adapter for the media output dialog device list. This class doesn't
 * manipulate the layout directly.
 */
abstract class MediaOutputAdapterBase(protected val mController: MediaSwitchingController) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    data class OngoingSessionStatus(val host: Boolean)

    data class GroupStatus(val selected: Boolean, val deselectable: Boolean)

    enum class ConnectionState {
        CONNECTED,
        CONNECTING,
        DISCONNECTED,
    }

    private var mCurrentActivePosition: Int
    private var mIsDragging: Boolean = false

    protected val mMediaItemList: MutableList<MediaItem> = CopyOnWriteArrayList()

    init {
        mCurrentActivePosition = -1
        setHasStableIds(true)
    }

    fun isDragging(): Boolean = mIsDragging

    fun setIsDragging(isDragging: Boolean) {
        mIsDragging = isDragging
    }

    fun getCurrentActivePosition(): Int = mCurrentActivePosition

    override fun getItemCount(): Int {
        return mMediaItemList.size
    }

    abstract inner class MediaDeviceViewHolderBase
    internal constructor(view: View, var mContext: Context) : RecyclerView.ViewHolder(view) {
        fun renderItem(mediaItem: DeviceMediaItem, position: Int) {
            val device = mediaItem.mediaDevice
            val isMutingExpectedDeviceExist = mController.hasMutingExpectedDevice()
            val currentlyConnected = mController.isSingleConnectedDevice(device)
            val isCurrentConnectedDeviceRemote = mController.isCurrentConnectedDeviceRemote()

            if (DEBUG) {
                Log.d(TAG, "#$position: $device")
            }

            var groupStatus: GroupStatus? = null
            var ongoingSessionStatus: OngoingSessionStatus? = null
            var connectionState = DISCONNECTED
            var restrictVolumeAdjustment = mController.hasAdjustVolumeUserRestriction()
            var subtitle: String? = null
            var deviceStatusIcon: Drawable? = null
            var deviceDisabled = false
            var clickListener: View.OnClickListener? = null

            if (mCurrentActivePosition == position) {
                mCurrentActivePosition = -1
            }

            if (mController.isAnyDeviceTransferring()) {
                if (device.state == STATE_CONNECTING) {
                    connectionState = CONNECTING
                }
            } else {
                // Set different layout for each device
                if (device.isMutingExpectedDevice && !isCurrentConnectedDeviceRemote) {
                    connectionState = CONNECTED
                    restrictVolumeAdjustment = true
                    clickListener = View.OnClickListener { transferOutput(device) }
                } else if (
                    currentlyConnected &&
                        isMutingExpectedDeviceExist &&
                        !isCurrentConnectedDeviceRemote
                ) {
                    // mark as disconnected and set special click listener
                    clickListener = View.OnClickListener { cancelMuteAwaitConnection() }
                } else if (device.state == STATE_GROUPING) {
                    connectionState = CONNECTING
                } else { // A connected or disconnected device.
                    if (Flags.makeDeviceSelectionBehaviourRespectRouteListingPreference()) {
                        subtitle = getSubtitle(device)
                    } else {
                        subtitle = if (device.hasSubtext()) device.subtextString else null
                    }
                    ongoingSessionStatus = getOngoingSessionStatus(device)
                    groupStatus = getGroupStatus(device)

                    if (Flags.makeDeviceSelectionBehaviourRespectRouteListingPreference()) {
                        if (currentlyConnected || device.isSelected()) {
                            connectionState = CONNECTED
                        } else { // disconnected
                            deviceStatusIcon = getDeviceStatusIcon(device)
                            clickListener = getClickListenerBasedOnSelectionBehavior(device)
                            deviceDisabled = clickListener == null
                        }
                    } else {
                        if (device.state == STATE_CONNECTING_FAILED) {
                            deviceStatusIcon =
                                mContext.getDrawable(R.drawable.media_output_status_failed)
                            subtitle =
                                mContext.getString(R.string.media_output_dialog_connect_failed)
                            clickListener = View.OnClickListener { transferOutput(device) }
                        } else if (currentlyConnected || device.isSelected()) {
                            connectionState = CONNECTED
                        } else { // disconnected
                            if (device.isSelectable()) { // groupable device
                                if (
                                    device.isTransferable() ||
                                        device.hasRouteListingPreferenceItem()
                                ) {
                                    clickListener = View.OnClickListener { transferOutput(device) }
                                }
                            } else {
                                deviceStatusIcon = getDeviceStatusIcon(device)
                                clickListener = getClickListenerBasedOnSelectionBehavior(device)
                            }

                            deviceDisabled = clickListener == null
                        }
                    }
                }
            }

            if (connectionState == CONNECTED) {
                mCurrentActivePosition = position
            }

            if (device.isInputDevice) {
                restrictVolumeAdjustment = true
            }

            renderDeviceItem(
                device,
                connectionState,
                restrictVolumeAdjustment,
                groupStatus,
                ongoingSessionStatus,
                clickListener,
                deviceDisabled,
                subtitle,
                deviceStatusIcon,
            )
        }

        protected abstract fun renderDeviceItem(
            device: MediaDevice,
            connectionState: ConnectionState,
            restrictVolumeAdjustment: Boolean,
            groupStatus: GroupStatus?,
            ongoingSessionStatus: OngoingSessionStatus?,
            clickListener: View.OnClickListener?,
            deviceDisabled: Boolean,
            subtitle: String?,
            deviceStatusIcon: Drawable?,
        )

        protected abstract fun renderDeviceGroupItem()

        protected abstract fun disableSeekBar()

        private fun getOngoingSessionStatus(device: MediaDevice): OngoingSessionStatus? {
            return if (device.hasOngoingSession())
                OngoingSessionStatus(device.isHostForOngoingSession)
            else null
        }

        private fun getGroupStatus(device: MediaDevice): GroupStatus? {
            if (device.isInputDevice) {
                return null
            }
            // A device should either be selectable or, when the device selected, the list should
            // have other selectable or selected devices.
            val selectedWithOtherGroupDevices =
                device.isSelected() && (mController.hasGroupPlayback() || hasSelectableDevices())
            if (device.isSelectable() || selectedWithOtherGroupDevices) {
                return GroupStatus(device.isSelected(), device.isDeselectable())
            }
            return null
        }

        private fun hasSelectableDevices(): Boolean {
            return mMediaItemList.any { it is DeviceMediaItem && it.mediaDevice.isSelectable }
        }

        private fun getClickListenerBasedOnSelectionBehavior(
            device: MediaDevice
        ): View.OnClickListener? {
            return Api34Impl.getClickListenerBasedOnSelectionBehavior(
                device,
                mController,
                defaultTransferListener = { transferOutput(device) },
            )
        }

        private fun getDeviceStatusIcon(device: MediaDevice): Drawable? {
            if (Flags.makeDeviceSelectionBehaviourRespectRouteListingPreference()) {
                return if (device.state == STATE_CONNECTING_FAILED) {
                    mContext.getDrawable(R.drawable.media_output_status_failed)
                } else {
                    Api34Impl.getDeviceStatusIconBasedOnSelectionBehavior(device, mContext)
                }
            } else {
                return Api34Impl.getDeviceStatusIconBasedOnSelectionBehavior(device, mContext)
            }
        }

        private fun getSubtitle(device: MediaDevice): String? {
            return if (device.state == STATE_CONNECTING_FAILED) {
                mContext.getString(R.string.media_output_dialog_connect_failed)
            } else if (device.hasSubtext()) {
                device.subtextString
            } else null
        }

        protected fun onGroupActionTriggered(isChecked: Boolean, device: MediaDevice) {
            disableSeekBar()
            if (isChecked && device.isSelectable()) {
                mController.addDeviceToPlayMedia(device)
            } else if (!isChecked && device.isDeselectable()) {
                mController.removeDeviceFromPlayMedia(device)
            }
            notifyDataSetChanged()
        }

        private fun transferOutput(device: MediaDevice) {
            if (mController.isAnyDeviceTransferring()) {
                return
            }
            if (mController.isSingleConnectedDevice(device)) {
                Log.d(TAG, "This device is already connected! : ${device.getName()}")
                return
            }
            mController.setTemporaryAllowListExceptionIfNeeded()
            mCurrentActivePosition = -1
            mController.connectDevice(device)
            notifyDataSetChanged()
        }

        private fun cancelMuteAwaitConnection() {
            mController.cancelMuteAwaitConnection()
            notifyDataSetChanged()
        }
    }

    @RequiresApi(34)
    private object Api34Impl {
        @DoNotInline
        fun getClickListenerBasedOnSelectionBehavior(
            device: MediaDevice,
            controller: MediaSwitchingController,
            defaultTransferListener: View.OnClickListener,
        ): View.OnClickListener? {
            return when (device.selectionBehavior) {
                SELECTION_BEHAVIOR_NONE -> null
                SELECTION_BEHAVIOR_TRANSFER -> defaultTransferListener
                SELECTION_BEHAVIOR_GO_TO_APP ->
                    View.OnClickListener { v: View ->
                        controller.tryToLaunchInAppRoutingIntent(device.getId(), v)
                    }
                else -> defaultTransferListener
            }
        }

        @DoNotInline
        fun getDeviceStatusIconBasedOnSelectionBehavior(
            device: MediaDevice,
            context: Context,
        ): Drawable? {
            return when (device.selectionBehavior) {
                SELECTION_BEHAVIOR_NONE ->
                    context.getDrawable(R.drawable.media_output_status_failed)

                SELECTION_BEHAVIOR_TRANSFER -> null
                SELECTION_BEHAVIOR_GO_TO_APP ->
                    context.getDrawable(R.drawable.media_output_status_help)
                else -> null
            }
        }
    }

    companion object {
        private const val TAG = "MediaOutputAdapterBase"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }
}
