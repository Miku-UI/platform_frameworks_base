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
package com.android.wm.shell.hierarchy.containers

import android.content.res.Configuration
import android.graphics.Rect
import android.window.WindowContainerToken
import com.android.wm.shell.hierarchy.properties.ContainerProperties

/**
 * A test container in the ContainerHierarchy.
 */
class StubContainer(
    token: WindowContainerToken = WindowContainerToken.createProxy("test"),
    window: ContainerProperties = ContainerProperties(),
) : Container(token=token, props=window) {
    // Whether the parent config has changed
    var parentConfigChanged = false

    /** Convenience method to set the bounds of the container */
    fun setBounds(bounds: Rect) {
        props.config.windowConfiguration.setBounds(bounds)
    }

    override fun updateConfigurationFromParentIfNeeded(parentConfig: Configuration) {
        parentConfigChanged = true
    }
}