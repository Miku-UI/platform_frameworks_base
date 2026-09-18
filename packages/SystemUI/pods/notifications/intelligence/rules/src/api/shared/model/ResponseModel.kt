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

package com.android.systemui.notifications.intelligence.rules.shared.model

/** Represents possible responses from system_server, where the desired response is of type [T]. */
sealed interface ResponseModel<out T> {
    /** SysUI is waiting for a response from system_server. */
    data object Loading : ResponseModel<Nothing>

    /** SysUI received a successful response. */
    data class Success<T>(val draftRule: T) : ResponseModel<T>

    /** system_server had some sort of error. */
    data object Error : ResponseModel<Nothing>
}
