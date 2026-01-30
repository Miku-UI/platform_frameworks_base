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

package com.android.systemui.flashlight.shared.model

sealed interface FlashlightModel {
    sealed interface Unavailable : FlashlightModel {

        /** The device does not have flashlight and that is not going to change. */
        sealed interface Permanently : Unavailable {
            data object NotSupported : Permanently
            // TODO(b/412982015) add fault tolerance and move this to Temp
        }

        sealed interface Temporarily : Unavailable {
            data object CameraInUse : Temporarily

            data object Loading : Temporarily

            data object NotFound : Temporarily
        }
    }

    sealed interface Available : FlashlightModel {
        val enabled: Boolean

        data class Binary(override val enabled: Boolean) : Available

        data class Level(override val enabled: Boolean, val level: Int, val max: Int) : Available
    }
}
