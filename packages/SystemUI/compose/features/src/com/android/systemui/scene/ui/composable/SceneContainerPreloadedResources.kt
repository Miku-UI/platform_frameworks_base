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

package com.android.systemui.scene.ui.composable

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import com.android.systemui.res.R

/**
 * Provides an instance of [SceneContainerPreloadedResources] that can be used to preload resources
 * from a [Resources] instance and keep them cached in memory as to not take on the repeated
 * overhead or suffer from any lock contention related to loading resources.
 */
val LocalSceneContainerPreloadedResources =
    staticCompositionLocalOf<SceneContainerPreloadedResources> {
        error(
            "LocalSceneContainerPreloadedResources not set yet, please use WithSceneContainerPreloadedResources or associate LocalSceneContainerPreloadedResources with rememberSceneContainerPreloadedResources!"
        )
    }

/**
 * Defines interface for classes that can provide properties backed by preloaded resources.
 *
 * There is a problem in Android where all threads accessing resources from the [Resources] API have
 * to hold a lock/mutex that's global. This means that if resources are being accessed/read from
 * multiple background threads, the main thread might get blocked awaiting the release of the global
 * mutex (e.g. "lock contention"). Since Compose runs on the main thread, this can lead to long
 * frames and jank.
 *
 * [SceneContainerPreloadedResources] is a solution to minimize this where a certain collection of
 * resources can be loaded once, in a common ancestor and multiple child composable functions can
 * then access the preloaded resources without hitting the contentious lock in the [Resources] API.
 */
@Stable
interface SceneContainerPreloadedResources {

    /**
     * Whether the shade layout should be full width (true) or floating (false).
     *
     * In a floating layout, notifications and quick settings each take up only up to half the
     * screen width (whether they are shown at the same time or not). In a full width layout, they
     * can each be as wide as the entire screen.
     */
    val isFullWidthShade: Boolean
}

/**
 * Returns a remembered [SceneContainerPreloadedResources] that will preload all of its resources
 * just once, each time that the resources change.
 */
@Composable
fun rememberSceneContainerPreloadedResources(): SceneContainerPreloadedResources {
    val resources: Resources = LocalResources.current

    val isFullWidthShade by
        rememberUpdatedState(resources.getBoolean(R.bool.config_isFullWidthShade))

    return remember {
        object : SceneContainerPreloadedResources {
            override val isFullWidthShade: Boolean
                get() = isFullWidthShade
        }
    }
}

/**
 * Convenience wrapper around the [LocalSceneContainerPreloadedResources] composition local.
 *
 * Code in [content] can access preloaded resources via [LocalSceneContainerPreloadedResources].
 */
@Composable
fun WithSceneContainerPreloadedResources(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSceneContainerPreloadedResources provides rememberSceneContainerPreloadedResources(),
        content,
    )
}
