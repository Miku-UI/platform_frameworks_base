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

package com.android.systemui.scene.shared.model

import com.android.compose.animation.scene.SceneKey

/**
 * Returns `true` if the scene is part of the keyguard, `false` if the scene is not, and throws an
 * exception if the scene is unknown.
 */
fun SceneKey.isKeyguardScene(): Boolean {
    return if (keyguardScenes.contains(this)) {
        true
    } else if (nonKeyguardScenes.contains(this)) {
        false
    } else {
        throw IllegalArgumentException("Unknown scene: $this")
    }
}

/**
 * Content that is part of the keyguard and are shown when the device is locked or when the keyguard
 * still needs to be dismissed.
 */
private val keyguardScenes: Set<SceneKey> =
    setOf(Scenes.Lockscreen, Scenes.Communal, Scenes.Dream, Scenes.Occluded)

/**
 * Scenes that are not considered part of keyguard.
 *
 * This set is explicitly defined to enforce the "keyguardness" consideration when a new scene is
 * introduced.
 */
private val nonKeyguardScenes: Set<SceneKey> =
    setOf(Scenes.Gone, Scenes.QuickSettings, Scenes.Shade)
