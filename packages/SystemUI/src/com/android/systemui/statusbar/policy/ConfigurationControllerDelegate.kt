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

package com.android.systemui.statusbar.policy

import android.content.res.Configuration

/** Delegates the implementation of a [ConfigurationController] to another. */
class ConfigurationControllerDelegate : ConfigurationController {
    private lateinit var delegate: ConfigurationController

    /** Sets the delegate. */
    fun setDelegate(delegate: ConfigurationController) {
        this.delegate = delegate
    }

    override fun addCallback(listener: ConfigurationController.ConfigurationListener) {
        delegate.addCallback(listener)
    }

    override fun removeCallback(listener: ConfigurationController.ConfigurationListener) {
        delegate.removeCallback(listener)
    }

    override fun onConfigurationChanged(newConfiguration: Configuration) {
        delegate.onConfigurationChanged(newConfiguration)
    }

    override fun dispatchOnMovedToDisplay(newDisplayId: Int, newConfiguration: Configuration) {
        delegate.dispatchOnMovedToDisplay(newDisplayId, newConfiguration)
    }

    override fun notifyThemeChanged() {
        delegate.notifyThemeChanged()
    }

    override fun isLayoutRtl(): Boolean {
        return delegate.isLayoutRtl
    }

    override fun getNightModeName(): String {
        return delegate.nightModeName
    }
}
