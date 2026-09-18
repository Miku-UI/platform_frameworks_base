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
import android.animation.AnimatorSet
import android.graphics.Rect

/**
 * Creates animations to transition between two LayoutNode tree states.
 *
 * It "diffs" the before and after trees to determine which animatable objects are entering,
 * exiting, or changing bounds. This class is generic and relies on an [Animatable]
 * adapter to handle the specifics of animating different types of objects (e.g., Views, Surfaces).
 *
 * @param T The type of the object to be animated.
 * @param animatable An adapter that provides the actual animation logic for objects of type T.
 */
class LayoutAnimator<T>(private val animatable: Animatable<T>) {

    /**
     * Creates and returns an AnimatorSet to transition from a previous layout to a new one.
     *
     * @param beforeTree The root of the old LayoutNode tree.
     * @param afterTree The root of the new LayoutNode tree.
     * @param targetMap A map of all animatable objects, keyed by their taskId.
     * @return An AnimatorSet that will perform the transition.
     */
    fun createTransition(
        beforeTree: LayoutNode,
        afterTree: LayoutNode,
        targetMap: Map<Int, T>,
    ): AnimatorSet {
        // Step 1: Flatten both trees to easily find nodes and their bounds.
        val beforeBounds = flattenTree(beforeTree)
        val afterBounds = flattenTree(afterTree)

        // Step 2: Identify which tasks are staying, exiting, or entering.
        val stayingTaskIds = beforeBounds.keys.intersect(afterBounds.keys)
        val exitingTaskIds = beforeBounds.keys - afterBounds.keys
        // Note: Entering tasks are handled by a higher-level controller.

        val animations = mutableListOf<Animator>()

        // Step 3: Create animations for tasks that are staying but changing bounds.
        for (taskId in stayingTaskIds) {
            val target = targetMap[taskId] ?: continue
            val oldBounds = beforeBounds[taskId]!!
            val newBounds = afterBounds[taskId]!!

            if (oldBounds != newBounds) {
                // Animate the bounds of the target from old to new.
                animations.add(animatable.createBoundsAnimator(target, oldBounds, newBounds))
            }
        }

        // Step 4: Create fade-out animations for tasks that are exiting.
        for (taskId in exitingTaskIds) {
            val target = targetMap[taskId] ?: continue
            animations.add(animatable.createFadeOutAnimator(target))
        }

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animations)
        return animatorSet
    }

    /** Helper to recursively traverse a tree and create a map of taskId to its final bounds. */
    private fun flattenTree(
        node: LayoutNode,
        map: MutableMap<Int, Rect> = mutableMapOf(),
    ): Map<Int, Rect> {
        when (node) {
            is LeafNode -> map[node.taskInfo.taskId] = node.bounds
            is BranchNode -> node.children.forEach { flattenTree(it, map) }
        }
        return map
    }
}
