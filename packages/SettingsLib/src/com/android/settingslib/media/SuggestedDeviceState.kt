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

package com.android.settingslib.media

import android.content.Context
import android.graphics.drawable.Drawable
import android.media.SuggestedDeviceInfo
import com.android.settingslib.R
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_CONNECTING_FAILED
import com.android.settingslib.media.LocalMediaManager.MediaDeviceState.STATE_DISCONNECTED

/**
 * Wrapper class around [SuggestedDeviceInfo] and the corresponding connection state of the
 * suggestion.
 */
data class SuggestedDeviceState
@JvmOverloads
constructor(
    val suggestedDeviceInfo: SuggestedDeviceInfo,
    @MediaDeviceState val connectionState: Int = STATE_DISCONNECTED,
) {
  fun getIcon(context: Context): Drawable {
      val deviceType = suggestedDeviceInfo.type
      val resourceId = when {
          connectionState == STATE_CONNECTING_FAILED -> android.R.drawable.ic_info
          isInfoMediaDevice(deviceType) -> InfoMediaDevice.getDrawableResIdByType(deviceType)
          isPhoneMediaDevice(deviceType) ->
              DeviceIconUtil(context).getIconResIdFromMediaRouteType(deviceType)
          else -> R.drawable.ic_media_speaker_device
      }

      return context.getDrawable(resourceId)!!
  }
}
