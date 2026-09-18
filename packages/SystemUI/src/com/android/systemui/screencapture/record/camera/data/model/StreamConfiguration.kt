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

package com.android.systemui.screencapture.record.camera.data.model

import android.util.Size
import com.android.systemui.util.isEmpty

data class StreamConfiguration(val cameraStreamSize: Size, val outputStreamSize: Size)

/**
 * @return true when both [StreamConfiguration.cameraStreamSize] and
 *   [StreamConfiguration.outputStreamSize] are not empty and false otherwise.
 */
fun StreamConfiguration.isValid(): Boolean =
    !cameraStreamSize.isEmpty() && !outputStreamSize.isEmpty()
