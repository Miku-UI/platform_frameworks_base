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

package com.android.systemui.screenshot

import android.hardware.camera2.CameraManager
import android.media.MediaActionSound
import com.android.systemui.Flags.screenshotForceShutterSound
import com.android.systemui.dagger.qualifiers.Main
import java.util.concurrent.Executor
import javax.inject.Inject

/** Policy class to determine if a shutter sound should be forced when taking a screenshot. */
open class ScreenshotSoundPolicy
@Inject
constructor(
    cameraManager: CameraManager,
    private val shutterPolicy: MediaShutterSoundPolicy,
    @Main private val mainExecutor: Executor,
) {
    private var cameraOpen: Boolean = false

    init {
        if (screenshotForceShutterSound()) {
            cameraManager.registerAvailabilityCallback(
                mainExecutor,
                object : CameraManager.AvailabilityCallback() {
                    override fun onCameraOpened(cameraId: String, packageId: String) {
                        cameraOpen = true
                    }

                    override fun onCameraClosed(cameraId: String) {
                        cameraOpen = false
                    }
                },
            )
        }
    }

    fun shouldForceShutterSound(): Boolean {
        return screenshotForceShutterSound() && shutterPolicy.mustPlayShutterSound() && cameraOpen
    }
}

class MediaShutterSoundPolicy @Inject constructor() {
    fun mustPlayShutterSound(): Boolean {
        return MediaActionSound.mustPlayShutterSound()
    }
}
