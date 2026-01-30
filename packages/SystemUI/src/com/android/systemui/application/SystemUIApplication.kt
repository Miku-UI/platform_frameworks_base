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

import android.app.Application

/**
 * Application class for SystemUI.
 *
 * Split into interface and implementation so that SysUI initialization can be in a separate build
 * target from SysUI services that need to reference the application.
 */
abstract class SystemUIApplication : Application() {
    /**
     * Makes sure that all the CoreStartables are running. If they are already running, this is a
     * no-op. This is needed to conditionally start all the services, as we only need to have it in
     * the main process.
     *
     * This method must only be called from the main thread.
     */
    abstract fun startSystemUserServicesIfNeeded()

    /**
     * Ensures that all the Secondary user SystemUI services are running. If they are already
     * running, this is a no-op. This is needed to conditionally start all the services, as we only
     * need to have it in the main process.
     *
     * This method must only be called from the main thread.
     */
    abstract fun startSecondaryUserServicesIfNeeded()
}
