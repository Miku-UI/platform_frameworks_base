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
import android.content.pm.ApplicationInfo
import android.media.MediaRoute2ProviderService.REASON_INVALID_COMMAND
import android.media.MediaRoute2ProviderService.REASON_NETWORK_ERROR
import android.media.MediaRoute2ProviderService.REASON_REJECTED
import android.media.MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE
import android.media.MediaRoute2ProviderService.REASON_UNKNOWN_ERROR
import android.util.Log
import com.android.settingslib.media.MediaDevice
import com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_3POINT5_MM_AUDIO_DEVICE
import com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_BLUETOOTH_DEVICE
import com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_CAST_DEVICE
import com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_CAST_GROUP_DEVICE
import com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_PHONE_DEVICE
import com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER
import com.android.settingslib.media.MediaDevice.MediaDeviceType.TYPE_USB_C_AUDIO_DEVICE
import com.android.settingslib.media.MediaDevice.SUGGESTION_PROVIDER_DEVICE_SUGGESTION_APP
import com.android.settingslib.media.MediaDevice.SUGGESTION_PROVIDER_DEVICE_SUGGESTION_OTHER
import com.android.settingslib.media.MediaDevice.SUGGESTION_PROVIDER_RLP
import com.android.settingslib.media.MediaDevice.SUGGESTION_PROVIDER_UNSPECIFIED
import com.android.systemui.media.dialog.MediaItem.DeviceMediaItem
import com.android.systemui.shared.system.SysUiStatsLog

/** Metric logger for media output features. */
class MediaOutputMetricLogger(private val mContext: Context, private val mPackageName: String?) {
    private var mSourceDevice: MediaDevice? = null
    private var mTargetDevice: MediaDevice? = null
    private var mWiredDeviceCount = 0
    private var mConnectedBluetoothDeviceCount = 0
    private var mRemoteDeviceCount = 0
    private var mAppliedDeviceCountWithinRemoteGroup = 0

    /**
     * Update the endpoints of a content switching operation. This method should be called before a
     * switching operation, so the metric logger can track source and target devices.
     *
     * @param source the current connected media device, might be null is nothing is connected
     * @param target the target media device for content switching to
     */
    fun updateOutputEndPoints(source: MediaDevice?, target: MediaDevice) {
        mSourceDevice = source
        mTargetDevice = target

        if (DEBUG) {
            Log.d(TAG, "updateOutputEndPoints - source: $source, target: $target")
        }
    }

    /**
     * Do the metric logging of content switching success.
     *
     * @param selectedDeviceType string representation of the target media device
     * @param deviceItemList media item list for device count updating
     */
    fun logOutputItemSuccess(selectedDeviceType: String, deviceItemList: List<MediaItem>) {
        if (DEBUG) {
            Log.d(TAG, "logOutputSuccess - selected device: $selectedDeviceType")
        }

        if (mSourceDevice == null && mTargetDevice == null) {
            return
        }

        updateLoggingMediaItemCount(deviceItemList)

        SysUiStatsLog.write(
            SysUiStatsLog.MEDIAOUTPUT_OP_SWITCH_REPORTED,
            getLoggingDeviceType(mSourceDevice, true),
            getLoggingDeviceType(mTargetDevice, false),
            SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__RESULT__OK,
            SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUBRESULT__NO_ERROR,
            getLoggingPackageName(),
            mWiredDeviceCount,
            mConnectedBluetoothDeviceCount,
            mRemoteDeviceCount,
            mAppliedDeviceCountWithinRemoteGroup,
            mTargetDevice?.isSuggestedDevice ?: false,
            mTargetDevice?.hasOngoingSession() ?: false,
            getLoggingSuggestionProvider(mTargetDevice?.getSuggestionProvider()),
        )
    }

    /**
     * Do the metric logging of volume adjustment.
     *
     * @param source the device been adjusted
     */
    fun logInteractionAdjustVolume(source: MediaDevice) {
        if (DEBUG) {
            Log.d(TAG, "logInteraction - AdjustVolume")
        }

        SysUiStatsLog.write(
            SysUiStatsLog.MEDIAOUTPUT_OP_INTERACTION_REPORT,
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__INTERACTION_TYPE__ADJUST_VOLUME,
            getInteractionDeviceType(source),
            getLoggingPackageName(),
            source.isSuggestedDevice,
            getLoggingSuggestionProvider(source.getSuggestionProvider()),
        )
    }

    /** Do the metric logging of stop sharing. */
    fun logInteractionStopSharing() {
        if (DEBUG) {
            Log.d(TAG, "logInteraction - Stop sharing")
        }

        SysUiStatsLog.write(
            SysUiStatsLog.MEDIAOUTPUT_OP_INTERACTION_REPORT,
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__INTERACTION_TYPE__STOP_SHARING,
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__UNKNOWN_TYPE,
            getLoggingPackageName(),
            false, /* isSuggestedDevice */
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__SUGGESTION_PROVIDER__UNSPECIFIED,
        )
    }

    /** Do the metric logging of stop casting. */
    fun logInteractionStopCasting() {
        if (DEBUG) {
            Log.d(TAG, "logInteraction - Stop casting")
        }

        SysUiStatsLog.write(
            SysUiStatsLog.MEDIAOUTPUT_OP_INTERACTION_REPORT,
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__INTERACTION_TYPE__STOP_CASTING,
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__UNKNOWN_TYPE,
            getLoggingPackageName(),
            false, /* isSuggestedDevice */
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__SUGGESTION_PROVIDER__UNSPECIFIED,
        )
    }

    /** Do the metric logging of device expansion. */
    fun logInteractionExpansion(source: MediaDevice) {
        if (DEBUG) {
            Log.d(TAG, "logInteraction - Expansion")
        }

        SysUiStatsLog.write(
            SysUiStatsLog.MEDIAOUTPUT_OP_INTERACTION_REPORT,
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__INTERACTION_TYPE__EXPANSION,
            getInteractionDeviceType(source),
            getLoggingPackageName(),
            source.isSuggestedDevice,
            getLoggingSuggestionProvider(source.getSuggestionProvider()),
        )
    }

    /** Do the metric logging of device contraction. */
    fun logInteractionContraction(source: MediaDevice) {
        if (DEBUG) {
            Log.d(TAG, "logInteraction - Contraction")
        }

        SysUiStatsLog.write(
            SysUiStatsLog.MEDIAOUTPUT_OP_INTERACTION_REPORT,
            SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__INTERACTION_TYPE__CONTRACTION,
            getInteractionDeviceType(source),
            getLoggingPackageName(),
            source.isSuggestedDevice,
            getLoggingSuggestionProvider(source.getSuggestionProvider()),
        )
    }

    /**
     * Do the metric logging of content switching failure.
     *
     * @param deviceItemList media item list for device count updating
     * @param reason the reason of content switching failure
     */
    fun logOutputItemFailure(deviceItemList: List<MediaItem>, reason: Int) {
        if (DEBUG) {
            Log.e(TAG, "logRequestFailed - $reason")
        }

        if (mSourceDevice == null && mTargetDevice == null) {
            return
        }

        updateLoggingMediaItemCount(deviceItemList)

        SysUiStatsLog.write(
            SysUiStatsLog.MEDIAOUTPUT_OP_SWITCH_REPORTED,
            getLoggingDeviceType(mSourceDevice, true),
            getLoggingDeviceType(mTargetDevice, false),
            SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__RESULT__ERROR,
            getLoggingSwitchOpSubResult(reason),
            getLoggingPackageName(),
            mWiredDeviceCount,
            mConnectedBluetoothDeviceCount,
            mRemoteDeviceCount,
            mAppliedDeviceCountWithinRemoteGroup,
            mTargetDevice?.isSuggestedDevice ?: false,
            mTargetDevice?.hasOngoingSession() ?: false,
            getLoggingSuggestionProvider(mTargetDevice?.getSuggestionProvider()),
        )
    }

    private fun updateLoggingMediaItemCount(deviceItemList: List<MediaItem>) {
        mRemoteDeviceCount = 0
        mConnectedBluetoothDeviceCount = 0
        mWiredDeviceCount = 0
        mAppliedDeviceCountWithinRemoteGroup = 0

        val connectedDeviceItems =
            deviceItemList.filterIsInstance<DeviceMediaItem>().filter {
                it.mediaDevice.isConnected()
            }

        for (mediaItem in connectedDeviceItems) {
            when (mediaItem.mediaDevice.deviceType) {
                TYPE_3POINT5_MM_AUDIO_DEVICE,
                TYPE_USB_C_AUDIO_DEVICE -> mWiredDeviceCount++
                TYPE_BLUETOOTH_DEVICE -> mConnectedBluetoothDeviceCount++
                TYPE_CAST_DEVICE,
                TYPE_CAST_GROUP_DEVICE -> mRemoteDeviceCount++
                else -> {}
            }
        }

        if (DEBUG) {
            Log.d(
                TAG,
                "connected devices: wired: $mWiredDeviceCount" +
                    " bluetooth: $mConnectedBluetoothDeviceCount" +
                    " remote: $mRemoteDeviceCount",
            )
        }
    }

    private fun getLoggingDeviceType(device: MediaDevice?, isSourceDevice: Boolean): Int {
        if (device == null) {
            return if (isSourceDevice)
                SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__UNKNOWN_TYPE
            else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__UNKNOWN_TYPE
        }
        return when (device.deviceType) {
            TYPE_PHONE_DEVICE ->
                if (isSourceDevice)
                    SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__BUILTIN_SPEAKER
                else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__BUILTIN_SPEAKER

            TYPE_3POINT5_MM_AUDIO_DEVICE ->
                if (isSourceDevice)
                    SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__WIRED_3POINT5_MM_AUDIO
                else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__WIRED_3POINT5_MM_AUDIO

            TYPE_USB_C_AUDIO_DEVICE ->
                if (isSourceDevice)
                    SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__USB_C_AUDIO
                else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__USB_C_AUDIO

            TYPE_BLUETOOTH_DEVICE ->
                if (isSourceDevice) SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__BLUETOOTH
                else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__BLUETOOTH

            TYPE_CAST_DEVICE ->
                if (isSourceDevice)
                    SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__REMOTE_SINGLE
                else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__REMOTE_SINGLE

            TYPE_CAST_GROUP_DEVICE ->
                if (isSourceDevice)
                    SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__REMOTE_GROUP
                else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__REMOTE_GROUP

            TYPE_REMOTE_AUDIO_VIDEO_RECEIVER ->
                if (isSourceDevice) SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__AVR
                else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__AVR

            else ->
                if (isSourceDevice)
                    SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SOURCE__UNKNOWN_TYPE
                else SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__TARGET__UNKNOWN_TYPE
        }
    }

    private fun getInteractionDeviceType(device: MediaDevice?): Int {
        if (device == null) {
            return SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__UNKNOWN_TYPE
        }
        return when (device.deviceType) {
            TYPE_PHONE_DEVICE ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__BUILTIN_SPEAKER
            TYPE_3POINT5_MM_AUDIO_DEVICE ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__WIRED_3POINT5_MM_AUDIO

            TYPE_USB_C_AUDIO_DEVICE ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__USB_C_AUDIO
            TYPE_BLUETOOTH_DEVICE ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__BLUETOOTH
            TYPE_CAST_DEVICE ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__REMOTE_SINGLE
            TYPE_CAST_GROUP_DEVICE ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__REMOTE_GROUP
            else -> SysUiStatsLog.MEDIA_OUTPUT_OP_INTERACTION_REPORTED__TARGET__UNKNOWN_TYPE
        }
    }

    private fun getLoggingSwitchOpSubResult(reason: Int): Int {
        return when (reason) {
            REASON_REJECTED -> SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUBRESULT__REJECTED
            REASON_NETWORK_ERROR ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUBRESULT__NETWORK_ERROR
            REASON_ROUTE_NOT_AVAILABLE ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUBRESULT__ROUTE_NOT_AVAILABLE

            REASON_INVALID_COMMAND ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUBRESULT__INVALID_COMMAND
            REASON_UNKNOWN_ERROR ->
                SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUBRESULT__UNKNOWN_ERROR
            else -> SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUBRESULT__UNKNOWN_ERROR
        }
    }

    private fun getLoggingPackageName(): String {
        if (mPackageName == null || mPackageName.isEmpty()) return ""

        try {
            val applicationInfo =
                mContext.getPackageManager().getApplicationInfo(mPackageName, /* flags= */ 0)
            if (
                (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            ) {
                return mPackageName
            }
        } catch (_: Exception) {
            Log.e(TAG, "$mPackageName is invalid.")
        }
        return ""
    }

    companion object {
        private const val TAG = "MediaOutputMetricLogger"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        private fun getLoggingSuggestionProvider(
            @MediaDevice.SuggestionProvider suggestionProvider: Int?
        ): Int {
            if (suggestionProvider == null) {
                return SysUiStatsLog
                    .MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUGGESTION_PROVIDER__UNSPECIFIED
            }

            return when (suggestionProvider) {
                SUGGESTION_PROVIDER_UNSPECIFIED ->
                    SysUiStatsLog.MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUGGESTION_PROVIDER__UNSPECIFIED
                SUGGESTION_PROVIDER_RLP ->
                    SysUiStatsLog
                        .MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUGGESTION_PROVIDER__ROUTE_LISTING_PREFERENCE

                SUGGESTION_PROVIDER_DEVICE_SUGGESTION_APP ->
                    SysUiStatsLog
                        .MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUGGESTION_PROVIDER__DEVICE_SUGGESTION_APP

                SUGGESTION_PROVIDER_DEVICE_SUGGESTION_OTHER ->
                    SysUiStatsLog
                        .MEDIA_OUTPUT_OP_SWITCH_REPORTED__SUGGESTION_PROVIDER__DEVICE_SUGGESTION_OTHER

                else ->
                    throw IllegalArgumentException(
                        "Unknown suggestion provider: $suggestionProvider"
                    )
            }
        }
    }
}
