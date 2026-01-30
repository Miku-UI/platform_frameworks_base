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

import android.media.MediaRoute2Info

fun isInfoMediaDevice(deviceType: Int): Boolean {
  return when (deviceType) {
    MediaRoute2Info.TYPE_UNKNOWN,
    MediaRoute2Info.TYPE_REMOTE_TV,
    MediaRoute2Info.TYPE_REMOTE_SPEAKER,
    MediaRoute2Info.TYPE_GROUP,
    MediaRoute2Info.TYPE_REMOTE_TABLET,
    MediaRoute2Info.TYPE_REMOTE_TABLET_DOCKED,
    MediaRoute2Info.TYPE_REMOTE_COMPUTER,
    MediaRoute2Info.TYPE_REMOTE_GAME_CONSOLE,
    MediaRoute2Info.TYPE_REMOTE_CAR,
    MediaRoute2Info.TYPE_REMOTE_SMARTWATCH,
    MediaRoute2Info.TYPE_REMOTE_SMARTPHONE,
      -> true
    else -> false
  }
}

fun isPhoneMediaDevice(deviceType: Int): Boolean {
  return when (deviceType) {
    MediaRoute2Info.TYPE_BUILTIN_SPEAKER,
    MediaRoute2Info.TYPE_USB_DEVICE,
    MediaRoute2Info.TYPE_USB_HEADSET,
    MediaRoute2Info.TYPE_USB_ACCESSORY,
    MediaRoute2Info.TYPE_DOCK,
    MediaRoute2Info.TYPE_HDMI,
    MediaRoute2Info.TYPE_HDMI_ARC,
    MediaRoute2Info.TYPE_HDMI_EARC,
    MediaRoute2Info.TYPE_LINE_DIGITAL,
    MediaRoute2Info.TYPE_LINE_ANALOG,
    MediaRoute2Info.TYPE_AUX_LINE,
    MediaRoute2Info.TYPE_WIRED_HEADSET,
    MediaRoute2Info.TYPE_WIRED_HEADPHONES,
      -> true
    else -> false
  }
}

fun isBluetoothMediaDevice(deviceType: Int): Boolean {
  return when (deviceType) {
    MediaRoute2Info.TYPE_HEARING_AID,
    MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
    MediaRoute2Info.TYPE_BLE_HEADSET,
      -> true
    else -> false
  }
}

fun isComplexMediaDevice(deviceType: Int): Boolean {
  return deviceType == MediaRoute2Info.TYPE_REMOTE_AUDIO_VIDEO_RECEIVER
}
