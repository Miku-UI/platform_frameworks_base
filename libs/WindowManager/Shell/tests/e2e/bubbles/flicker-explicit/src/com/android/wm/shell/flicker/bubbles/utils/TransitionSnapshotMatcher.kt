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

package com.android.wm.shell.flicker.bubbles.utils

import android.tools.traces.component.IComponentNameMatcher
import android.tools.traces.surfaceflinger.Layer

/**
 * Matcher for transition snapshot layers.
 *
 * @param snapShotName the transition snapshot name.
 */
class TransitionSnapshotMatcher(private val snapShotName: String) : LayerMatcher() {

    /**
     * Matcher for transition snapshot layers.
     *
     * @param activityMatcher the activity that involves the transition snapshot.
     */
    constructor(activityMatcher: IComponentNameMatcher) : this(activityMatcher.className)

    override fun layerMatchesAnyOf(layers: Collection<Layer>): Boolean =
        layers.any {
            // Use regular expression here because we may want to match only the package name for
            // the transition snapshot of trampoline activity. Using contains() may also match
            // activity with the same package name unexpectedly.
            val escapedSnapShotName = Regex.escape(snapShotName)
            return@any """^transition snapshot: Task\{.*?$escapedSnapShotName\}.*$"""
                .toRegex()
                .matches(it.name)
        }

    override fun toLayerIdentifier(): String = "transition snapshot:[$snapShotName]"
}
