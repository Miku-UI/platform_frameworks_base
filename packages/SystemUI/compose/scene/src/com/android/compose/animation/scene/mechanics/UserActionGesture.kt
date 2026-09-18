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

package com.android.compose.animation.scene.mechanics

import androidx.compose.foundation.gestures.Orientation
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.DefaultSwipeDistance
import com.android.compose.animation.scene.UserActionDistance
import com.android.compose.animation.scene.UserActionDistanceScope
import com.android.mechanics.spec.MotionSpec
import com.android.mechanics.spec.SemanticKey
import com.android.mechanics.spec.builder.MotionBuilderContext

/**
 * Experimental API to support non-linear gestures.
 *
 * Implement this interface in lieu of simply setting the [UserActionDistance] function in
 * [BaseTransitionBuilder.distance].
 */
internal interface UserActionGesture : UserActionDistance {

    /**
     * Defines a transformation of the actual user's gesture, to allow simulating behavior such as
     * magnetic detach.
     *
     * The spec transforms the physical gesture drag offset (as observed by the pointer input) into
     * a effective gesture drag offset. Both, the input (physical) and output (effective) drag
     * offsets are in the range of `0..absoluteDistance`. The transition progress is then computed
     * based on the effective drag offset.
     *
     * This method is called immediately after [absoluteDistance] returned a non-zero result.
     */
    fun UserActionGestureScope.gestureSpec(
        fromContent: ContentKey,
        toContent: ContentKey,
        orientation: Orientation,
        absoluteDistance: Float,
    ): MotionSpec {
        return MotionSpec.Identity
    }

    companion object {
        /**
         * Boolean semantic on whether the transformed gesture should be committed.
         *
         * The [MotionSpec] created by [gestureSpec] must be annotated with this semantic value in
         * order to override the default commit behavior.
         *
         * If `UserActionResult.requiresFullDistanceSwipe` was set, this semantic won't have an
         * effect.
         */
        val ShouldCommit = SemanticKey<Boolean>(debugLabel = "Commit STL Gesture")
    }
}

/** A [UserActionDistanceScope] with an additional [MotionBuilderContext]. */
internal interface UserActionGestureScope : MotionBuilderContext, UserActionDistanceScope

/**
 * Signature of `UserActionGesture.gestureSpec`.
 *
 * Used to allow composing the UserActionDistance and UserActionGesture functions, since
 * UserActionGesture is not a `fun interface`
 */
internal typealias GestureSpecFn =
    (UserActionGestureScope).(
        fromContent: ContentKey, toContent: ContentKey, orientation: Orientation, distance: Float,
    ) -> MotionSpec

/**
 * Convenience helper to create a UserActionGesture.
 *
 * The [distance] is the same default as if [distance] is not specified at all in the transition.
 */
internal fun UserActionGesture(
    distance: UserActionDistance = DefaultSwipeDistance,
    gestureSpecFn: GestureSpecFn,
): UserActionGesture {
    return object : UserActionGesture, UserActionDistance by distance {
        override fun UserActionGestureScope.gestureSpec(
            fromContent: ContentKey,
            toContent: ContentKey,
            orientation: Orientation,
            absoluteDistance: Float,
        ): MotionSpec {
            return gestureSpecFn(fromContent, toContent, orientation, absoluteDistance)
        }
    }
}
