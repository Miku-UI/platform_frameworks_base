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

package com.android.systemui.statusbar.quickactions.av.shared.model

/**
 * Represents the different levels of blur that can be applied as a desktop effect.
 *
 * @property code The integer code associated with each blur level.
 */
enum class BlurLevel(val code: Int) {
    /** No blur applied. */
    OFF(0),

    /** A light level of blur applied. */
    LIGHT(1),

    /** A full level of blur applied. */
    FULL(2),
}

/**
 * Data class representing the current state of various desktop effects for the camera and
 * microphone.
 *
 * @property portraitRelight True if portrait relight effect is enabled, false otherwise.
 * @property faceRetouch True if face retouch effect is enabled, false otherwise.
 * @property studioMic True if studio mic effect is enabled, false otherwise.
 * @property blurLevel The current blur level applied, represented by a [BlurLevel] enum.
 * @property cameraFraming True if automatic camera framing is active.
 * @property liveCaptions True if live captions are active.
 * @property portraitRelightSupported True if supported by the device
 * @property faceRetouchSupported True if supported by the device
 * @property studioMicSupported True if supported by the device
 * @property blurSupported True if supported by the device
 * @property cameraFramingSupported True if supported by the device
 * @property liveCaptionsSupported True if supported by the device
 */
data class DesktopEffectModel(
    val portraitRelight: Boolean = false,
    val portraitRelightSupported: Boolean = true,
    val faceRetouch: Boolean = false,
    val faceRetouchSupported: Boolean = true,
    val studioMic: Boolean = false,
    val studioMicSupported: Boolean = true,
    val blurLevel: BlurLevel = BlurLevel.OFF,
    val blurSupported: Boolean = true,
    val cameraFraming: Boolean = false,
    val cameraFramingSupported: Boolean = true,
    val liveCaptions: Boolean = false,
    val liveCaptionsSupported: Boolean = true,
)
