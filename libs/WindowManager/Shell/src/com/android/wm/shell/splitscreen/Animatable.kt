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

package com.android.wm.shell.splitscreen

import android.animation.Animator
import android.graphics.Rect

/**
 * An adapter interface that allows the [LayoutAnimator] to animate different types of objects
 * (e.g., Views, SurfaceControls) in a generic way.
 *
 * @param T The type of the object to be animated.
 */
interface Animatable<T> {
    /**
     * Creates an animator to change the bounds of a target object.
     *
     * @param target The object whose bounds are to be animated.
     * @param from The starting bounds.
     * @param to The ending bounds.
     * @return An [Animator] that performs the bounds change.
     */
    fun createBoundsAnimator(target: T, from: Rect, to: Rect): Animator

    /**
     * Creates an animator to fade out a target object.
     *
     * @param target The object to be faded out.
     * @return An [Animator] that performs the fade-out.
     */
    fun createFadeOutAnimator(target: T): Animator
}
