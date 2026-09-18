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

package com.android.compose.animation.scene.transformation

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.content.state.TransitionState
import kotlinx.coroutines.CoroutineScope

private const val TAG = "OffsetSharedElementWithAnchor"

/**
 * Drives the offset animation of one element relative to another. Allows the caller to define
 * custom offset transformation by seeking another element's start/end positions.
 *
 * @param matcher The [ElementKey] of the element to be transformed.
 * @param anchor The [ElementKey] of the element whose start/end position can be used as an anchor.
 * @param offset A lambda that returns the desired [Offset] for the element. It provides the
 *   start/end offsets of both the element (`from`, `to`) and the anchor (`fromAnchor`, `toAnchor`).
 */
fun TransitionBuilder.offsetSharedElementWithAnchor(
    matcher: ElementKey,
    anchor: ElementKey,
    offset:
        PropertyTransformationScope.(
            from: Offset, to: Offset, fromAnchor: Offset, toAnchor: Offset,
        ) -> Offset,
) {
    transformation(matcher) {
        object : CustomSharedPropertyTransformation<Offset> {
            override fun PropertyTransformationScope.transform(
                element: ElementKey,
                transition: TransitionState.Transition,
                transitionScope: CoroutineScope,
                fromValue: Offset,
                toValue: Offset,
            ): Offset {
                val fromAnchor =
                    anchor.targetOffset(transition.fromContent)
                        ?: run {
                            Log.w(
                                TAG,
                                "Anchor ${anchor.debugName} is missing from the starting content.",
                            )
                            return fromValue
                        }
                val toAnchor =
                    anchor.targetOffset(transition.toContent)
                        ?: run {
                            Log.w(
                                TAG,
                                "Anchor ${anchor.debugName} is missing from the target content.",
                            )
                            return toValue
                        }
                return offset(fromValue, toValue, fromAnchor, toAnchor)
            }

            override val property: PropertyTransformation.Property<Offset> =
                PropertyTransformation.Property.Offset
        }
    }
}
