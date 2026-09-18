/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.compose.animation.scene.TransitionKey

/**
 * Defines all known named transitions.
 *
 * This is the subset of transitions that can be referenced by key when a scene change is requested.
 */
object TransitionKeys {
    /** A scene transition to the Lockscreen with Always-on-Display enabled. */
    val ToAlwaysOnDisplay = TransitionKey("ToAlwaysOnDisplay")

    /** The Gone/Lockscreen-to-Shade transition with Split Shade enabled. */
    val ToSplitShade = TransitionKey("ToSplitShade")

    /** A scene transition that can collapse the Shade slightly faster than a normal collapse. */
    val SlightlyFasterShadeTransition = TransitionKey("SlightlyFasterShadeTransition")

    /** A scene transition that should happen instantly, i.e. without animation. */
    val Instant = TransitionKey("Instant")

    /** Reference to a transition in or out of communal scene triggered by the system. */
    val SystemCommunalTransition = TransitionKey("SystemCommunalTransition")

    /** A scene transition for swiping up to the gone scene. */
    val SwipeUpToGone = TransitionKey("SwipeUpToGone")
}
