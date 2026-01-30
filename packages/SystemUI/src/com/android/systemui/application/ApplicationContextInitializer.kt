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

package com.android.systemui.application

/**
 * Interface for classes that can be constructed by the system before a context is available.
 *
 * This is intended for [Application] implementations that may not have a Context until some point
 * after construction or are themselves a [Context].
 *
 * Implementers will be passed a [ApplicationContextAvailableCallback] that they should call as soon
 * as an Application Context is ready.
 */
interface ApplicationContextInitializer {
    /**
     * Called to supply the [ApplicationContextAvailableCallback] that should be called when an
     * Application [Context] is available.
     */
    fun setContextAvailableCallback(callback: ApplicationContextAvailableCallback)
}
