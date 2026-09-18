/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.camera.data.repository

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.CameraLogger
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface CameraNotifyWarmUpRepository {
    /* notifies camera sub system to warm up back camera */
    fun notifyCameraWarmUp()
}

@SysUISingleton
class CameraNotifyWarmUpRepositoryImpl
@Inject
constructor(
    @Background private val bgCoroutineContext: CoroutineContext,
    @Application private val scope: CoroutineScope,
    private val cameraManager: CameraManager,
    private val logger: CameraLogger,
) : CameraNotifyWarmUpRepository {
    private val CAMERA_ID_BACK = "0"

    override fun notifyCameraWarmUp() {
        scope.launch(bgCoroutineContext) {
            try {
                cameraManager.warmUp(CAMERA_ID_BACK)
            } catch (e: CameraAccessException) {
                logger.logCameraAccessException()
            }
        }
    }
}
