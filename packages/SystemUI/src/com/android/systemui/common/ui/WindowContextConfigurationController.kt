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

package com.android.systemui.common.ui

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.window.WindowContext
import com.android.systemui.statusbar.phone.ConfigurationControllerImpl
import com.android.systemui.statusbar.phone.ConfigurationForwarder
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.statusbar.policy.ConfigurationControllerDelegate
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * Simple proxy class that acts as a [ConfigurationController] using [ComponentCallbacks] for a
 * [WindowContext].
 *
 * This is usually needed if we want to receive configuration changes associated to a specific
 * window.
 */
interface WindowContextConfigurationController : ConfigurationController {
    /** Starts listening and propagating config changes. */
    fun start()

    /** Stops listening and propagating config changes. */
    fun stop()
}

class WindowContextConfigurationControllerImpl
@AssistedInject
constructor(
    @Assisted private val windowContext: WindowContext,
    @Assisted private val delegate: ConfigurationControllerDelegate,
    configurationControllerFactory: ConfigurationControllerImpl.Factory,
) : WindowContextConfigurationController, ConfigurationController by delegate {

    init {
        delegate.setDelegate(configurationControllerFactory.create(windowContext))
    }

    private val configurationForwarder: ConfigurationForwarder = delegate

    private val componentCallback =
        object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                configurationForwarder.onConfigurationChanged(newConfig)
            }

            @Deprecated("See ComponentCallbacks") override fun onLowMemory() {}
        }

    override fun start() {
        windowContext.registerComponentCallbacks(componentCallback)
    }

    override fun stop() {
        windowContext.unregisterComponentCallbacks(componentCallback)
    }

    @AssistedFactory
    interface Factory {
        /**
         * Creates a [com.android.systemui.common.ui.WindowContextConfigurationControllerImpl] that
         * gets config changes using [WindowContext.registerComponentCallbacks].
         */
        fun create(
            windowContext: WindowContext,
            delegate: ConfigurationControllerDelegate = ConfigurationControllerDelegate(),
        ): WindowContextConfigurationControllerImpl
    }
}
