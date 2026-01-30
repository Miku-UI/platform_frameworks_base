/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.utils.leaks

import android.testing.LeakCheck
import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.PluginListener
import com.android.systemui.plugins.PluginManager

class FakePluginManager(test: LeakCheck) : PluginManager {
    private val mLeakChecker = BaseLeakChecker<PluginListener<*>>(test, "Plugin")

    override fun <T : Plugin> addPluginListener(
        listener: PluginListener<T>,
        cls: Class<T>,
        allowMultiple: Boolean,
    ) {
        mLeakChecker.addCallback(listener)
    }

    override val config = PluginManager.Config()

    override fun removePluginListener(listener: PluginListener<*>) {
        mLeakChecker.removeCallback(listener)
    }

    override fun <T> dependsOn(p: Plugin, cls: Class<T>): Boolean {
        return false
    }
}
