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
package com.android.wm.shell.hierarchy.properties

import android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
import android.window.TransitionInfo
import com.android.wm.shell.dagger.hierarchy.WmSyncedProperty

/**
 * Properties for a container that is associated with a display area in the WindowManager hierarchy.
 */
class DisplayAreaContainerProperties(
    @WmSyncedProperty val featureId: Int,
) : ContainerProperties() {

    /** @see ContainerProperties.updateFromWindowChange */
    override fun updateFromWindowChange(change: TransitionInfo.Change) {
        // TODO(b/491214137): Update configuration once we have display area info
        super.updateFromWindowChange(change)
    }

    /** @see ContainerProperties.copy */
    override fun copy(): DisplayAreaContainerProperties {
        return DisplayAreaContainerProperties(featureId).apply {
            copyFrom(this@DisplayAreaContainerProperties)
        }
    }

    /** @see ContainerProperties.propsToString */
    override fun propsToString(): String {
        return "featureId=$featureId | " + super.propsToString()
    }

    /** @see ContainerProperties.getTypeName */
    override fun getTypeName(): String {
        return if (featureId == FEATURE_DEFAULT_TASK_CONTAINER) "TDA" else "DA"
    }
}