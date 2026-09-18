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

package com.android.systemui.headline.ui.compose

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.DefaultElementContentPicker
import com.android.compose.animation.scene.ElementContentPicker
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.ElementMatcher
import com.android.compose.animation.scene.FixedDistance
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.animation.scene.and
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.inContent
import com.android.compose.animation.scene.or
import com.android.compose.animation.scene.transformation.CustomSharedPropertyTransformation
import com.android.compose.animation.scene.transformation.InterpolatedPropertyTransformation
import com.android.compose.animation.scene.transformation.InterpolatedSharedPropertyTransformation
import com.android.compose.animation.scene.transformation.PropertyTransformation
import com.android.compose.animation.scene.transformation.PropertyTransformationScope
import com.android.compose.animation.scene.transformation.SharedElementPropertyTransformation
import com.android.compose.animation.scene.transitions
import com.android.systemui.headline.ui.viewmodel.HeadlineItemKey
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel.Companion.GoneScene
import com.android.systemui.headline.ui.viewmodel.toHeadlineItemKey
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope

/**
 * All the transition definitions for Headline.
 *
 * There are currently 3 different definitions:
 * - from/to the Gone scene, i.e. when the first item appears or the last disappears.
 * - a triggered transition between two items, e.g. when clicking the dot indicator of the
 *   next/previous item.
 * - a swipe transition between two items.
 *
 * The swipe transition is done in 2-steps, using the [preview](go/sysui-transition-layout#previews)
 * mechanism from STL.
 *
 * All transitions make heavy use of STL custom [InterpolatedPropertyTransformation] and
 * [SharedElementPropertyTransformation] to drive the size and offset of the different elements.
 *
 * @see com.android.systemui.headline.ui.compose.HeadlineGoneTransitionPreview
 * @see com.android.systemui.headline.ui.compose.HeadlineTriggeredTransitionPreview
 * @see com.android.systemui.headline.ui.compose.HeadlineSwipeTransitionPreview
 *
 * TODO(b/449675581): Add documentation about custom transformations.
 */
internal val HeadlineTransitions = transitions {
    default(
        preview = {
            if (!transition.isGoneTransition() && transition.isInitiatedByUserInput) {
                headlineSwipeTransitionPreview()
            }
        }
    ) {
        if (transition.isGoneTransition()) {
            goneTransition()
        } else if (transition.isInitiatedByUserInput) {
            headlineSwipeTransitionAfterRelease()
        } else {
            headlineTransition()
        }
    }
}

// ===========================
// ===== Shared elements =====
// ===========================

/** The disappearing pill that morphs into a dot indicator during the transition. */
private fun TransitionBuilder.pillToDot(): ElementKey {
    return (transition.fromContent as SceneKey).toHeadlineItemKey().toPillElementKey()
}

/** The dot indicator that morphs into the appearing pill during the transition. */
private fun TransitionBuilder.dotToPill(): ElementKey {
    return (transition.toContent as SceneKey).toHeadlineItemKey().toPillElementKey()
}

// =============================================
// ===== Transformed (non-shared) elements =====
// =============================================

/** The dot indicator that appears during the transition. */
private fun TransitionBuilder.appearingDot(): ElementMatcher {
    return ElementKey.withIdentity { it is SharedPillElementIdentity } and
        inContent(transition.toContent)
}

/** The dot indicator that disappears during the transition. */
private fun TransitionBuilder.disappearingDot(): ElementMatcher {
    return ElementKey.withIdentity { it is SharedPillElementIdentity } and
        inContent(transition.fromContent)
}

/** The contents (start and end) inside a pill (appearing or disappearing). */
private fun pillContent(): ElementMatcher {
    return ElementKey.withIdentity {
        it is StartContentElementIdentity || it is EndContentElementIdentity
    }
}

/** The contents (start and end) inside the appearing pill. */
private fun TransitionBuilder.appearingPillContent(): ElementMatcher {
    return pillContent() and inContent(transition.toContent)
}

/** The contents (start and end) inside the disappearing pill. */
private fun TransitionBuilder.disappearingPillContent(): ElementMatcher {
    return pillContent() and inContent(transition.fromContent)
}

// ===========================
// ===== Gone transition =====
// ===========================

/** Whether this transition is a transition from/to the Gone scene. */
internal fun TransitionState.Transition.isGoneTransition(): Boolean {
    return fromContent == GoneScene || toContent == GoneScene
}

/** The definition of the transition from/to the Gone scene. */
private fun TransitionBuilder.goneTransition() {
    // TODO(b/449675581): Use bouncy springs once there is overscroll.
    // TODO(b/449675581): Use MotionScheme springs.
    spec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)

    val dots = appearingDot() or disappearingDot()
    fade(dots)
    scaleDraw(dots, scaleX = 0f, scaleY = 0f)

    val disappearingPillContent = disappearingPillContent()
    fractionRange(end = 0.5f) { fade(disappearingPillContent) }

    val appearingPillContent = appearingPillContent()
    fractionRange(start = 0.5f) { fade(appearingPillContent) }
}

// ================================
// ===== Triggered transition =====
// ================================

/** The definition of the transition between 2 items that is triggered, i.e. *not* swipe-based. */
private fun TransitionBuilder.headlineTransition() {
    // TODO(b/449675581): Use bouncy springs once there is overscroll.
    // TODO(b/449675581): Use MotionScheme springs.
    spec = tween(500)

    // Fade in the pill content that disappears. We make sure that the contents are faded out before
    // we reach 50% of the transition, otherwise the start/end content might overlap a bit, which
    // looks bad.
    val disappearingPillContent = disappearingPillContent()
    fractionRange(end = 0.35f) { fade(disappearingPillContent) }

    // Fade in the pill content that appears.
    val appearingPillContent = appearingPillContent()
    fractionRange(start = 0.65f) { fade(appearingPillContent) }

    // The dot that appears starts exactly where the dot that morphs into the pill is at the start
    // of the transition.
    val appearingDot = appearingDot()
    transformation(appearingDot) {
        object : InterpolatedPropertyTransformation<Offset> {
            override val property = PropertyTransformation.Property.Offset

            override fun PropertyTransformationScope.transform(
                content: ContentKey,
                element: ElementKey,
                transition: TransitionState.Transition,
                idleValue: Offset,
            ): Offset = dotToPill().targetOffset(transition.fromContent)!!
        }
    }
    fractionRange(start = 0.5f) {
        fade(appearingDot)
        scaleDraw(appearingDot, scaleX = 0f, scaleY = 0f)
    }

    // The dot that disappears ends exactly where the pill that morphs into the dot is at the end
    // of the transition.
    val disappearingDot = disappearingDot()
    transformation(disappearingDot) {
        object : InterpolatedPropertyTransformation<Offset> {
            override val property = PropertyTransformation.Property.Offset

            override fun PropertyTransformationScope.transform(
                content: ContentKey,
                element: ElementKey,
                transition: TransitionState.Transition,
                idleValue: Offset,
            ): Offset = pillToDot().targetOffset(transition.toContent)!!
        }
    }
    fractionRange(end = 0.5f) {
        fade(disappearingDot)
        scaleDraw(disappearingDot, scaleX = 0f, scaleY = 0f)
    }

    interpolatePillSizeAndOffset(dotToPill())
    interpolatePillSizeAndOffset(pillToDot())
}

/**
 * Interpolates the pill matched by [matcher] so that the height of the pill is at least as big as
 * the width, to avoid having a thin a long pill when interpolating between the from/to states.
 * Without doing that, the pill will have a long width and a short height, which looks bad.
 */
private fun TransitionBuilder.interpolatePillSizeAndOffset(matcher: ElementMatcher) {
    var lastSize = IntSize.Zero
    transformation(matcher) {
        object : CustomSharedPropertyTransformation<IntSize> {
            override val property = PropertyTransformation.Property.Size

            override fun PropertyTransformationScope.transform(
                element: ElementKey,
                transition: TransitionState.Transition,
                transitionScope: CoroutineScope,
                fromValue: IntSize,
                toValue: IntSize,
            ): IntSize {
                val width = lerp(fromValue.width, toValue.width, transition.progress)
                val maxHeight = max(fromValue.height, toValue.height)
                val height = min(width, maxHeight)
                return IntSize(width, height).also { lastSize = it }
            }
        }
    }

    transformation(matcher) {
        object : CustomSharedPropertyTransformation<Offset> {
            override val property = PropertyTransformation.Property.Offset

            override fun PropertyTransformationScope.transform(
                element: ElementKey,
                transition: TransitionState.Transition,
                transitionScope: CoroutineScope,
                fromValue: Offset,
                toValue: Offset,
            ): Offset {
                // Make sure the pill is always centered vertically.
                val centerY =
                    element.targetOffset(transition.fromContent)!!.y +
                        element.targetSize(transition.fromContent)!!.height / 2
                return Offset(
                    lerp(fromValue.x, toValue.x, transition.progress),
                    centerY - lastSize.height / 2f,
                )
            }
        }
    }
}

// ============================
// ===== Swipe transition =====
// ============================

/** The transformations that happen when the user release their finger after a swipe. */
private fun TransitionBuilder.headlineSwipeTransitionAfterRelease() {
    // TODO(b/449675581): Use bouncy springs once there is overscroll.
    // TODO(b/449675581): Use MotionScheme springs.
    spec = tween(500)

    // TODO(b/449675581): Find out what the actual swipe distance should be.
    distance = FixedDistance(200.dp)

    val fromContent = transition.fromContent as SceneKey
    val toContent = transition.toContent as SceneKey
    val dotToPill = toContent.toHeadlineItemKey().toPillElementKey()

    // The pill that appears starts exactly where the dot that morphs into the pill is at the start
    // of the transition.
    transformation(appearingDot()) {
        object : InterpolatedPropertyTransformation<Offset> {
            override val property = PropertyTransformation.Property.Offset

            override fun PropertyTransformationScope.transform(
                content: ContentKey,
                element: ElementKey,
                transition: TransitionState.Transition,
                idleValue: Offset,
            ): Offset = dotToPill.targetOffset(fromContent)!!
        }
    }

    val appearingPillContent = appearingPillContent()
    fractionRange(start = 0.5f) { fade(appearingPillContent) }

    interpolatePillSizeAndOffset(dotToPill())
    interpolatePillSizeAndOffset(pillToDot())
}

/** The transformations that happen while the user is swiping/previewing. */
private fun TransitionBuilder.headlineSwipeTransitionPreview() {
    val fromContent = transition.fromContent as SceneKey
    val toContent = transition.toContent as SceneKey
    val dotToPill = dotToPill()
    val pillToDot = pillToDot()

    /** Whether the swipe is from left to right. */
    fun PropertyTransformationScope.isSwipingLeftToRight(): Boolean {
        val dotToPillFromOffset = dotToPill.targetOffset(fromContent)!!
        val pillToDotFromOffset = pillToDot.targetOffset(fromContent)!!
        return dotToPillFromOffset.x < pillToDotFromOffset.x
    }

    // =============================================================
    // ===== The small dot indicator that morphs into the pill =====
    // =============================================================

    // The target preview size of the dot is the square/circle with the same size as the target
    // height.
    transformation(dotToPill) {
        object : InterpolatedSharedPropertyTransformation<IntSize> {
            override val property = PropertyTransformation.Property.Size

            override fun PropertyTransformationScope.targetPreviewValue(
                element: ElementKey,
                transition: TransitionState.Transition,
                fromValue: IntSize,
                toValue: IntSize,
            ): IntSize = IntSize(toValue.height, toValue.height)
        }
    }

    // The target preview offset of the dot is such that the dot is perfectly aligned with the pill.
    fun PropertyTransformationScope.dotToPillPreviewTargetOffset(): Offset {
        val dotToPillToSize = dotToPill.targetSize(toContent)!!
        val pillToDotFromOffset = pillToDot.targetOffset(fromContent)!!
        val pillToDotFromSize = pillToDot.targetSize(fromContent)!!
        return if (isSwipingLeftToRight()) {
            // When swiping left-to-right, this is the same offset as the pill that will disappear
            // into a dot.
            pillToDotFromOffset
        } else {
            // When swiping right-to-left, this is the top-right corner of the pill minus the target
            // preview size of the dot.
            Offset(
                x = pillToDotFromOffset.x + pillToDotFromSize.width - dotToPillToSize.height,
                y = pillToDotFromOffset.y,
            )
        }
    }

    transformation(dotToPill) {
        object : InterpolatedSharedPropertyTransformation<Offset> {
            override val property = PropertyTransformation.Property.Offset

            override fun PropertyTransformationScope.targetPreviewValue(
                element: ElementKey,
                transition: TransitionState.Transition,
                fromValue: Offset,
                toValue: Offset,
            ): Offset = dotToPillPreviewTargetOffset()
        }
    }

    // =====================================================
    // ===== The pill that morphs into a dot indicator =====
    // =====================================================

    /** The horizontal preview distance of the dot that morphs into the pill. */
    fun PropertyTransformationScope.dotToPillPreviewOffsetDelta(): Float {
        return dotToPillPreviewTargetOffset().x - dotToPill.targetOffset(fromContent)!!.x
    }

    /**
     * The horizontal preview distance of the pill that morphs into the dot when swiping
     * left-to-right.
     */
    fun PropertyTransformationScope.pillToDotLtrPreviewOffsetDelta(): Float {
        val dotToPillToSize = dotToPill.targetSize(toContent)!!
        val dotToPillFromSize = dotToPill.targetSize(fromContent)!!
        val dotToPillHeightDelta = dotToPillToSize.height - dotToPillFromSize.height
        return dotToPillPreviewOffsetDelta() + dotToPillHeightDelta
    }

    // The size of the pill during the preview is reduced to compensate the translation of the pill
    // (left-to-right) or the translation of the dot (right to left).
    transformation(pillToDot) {
        object : InterpolatedSharedPropertyTransformation<IntSize> {
            override val property = PropertyTransformation.Property.Size

            override fun PropertyTransformationScope.targetPreviewValue(
                element: ElementKey,
                transition: TransitionState.Transition,
                fromValue: IntSize,
                toValue: IntSize,
            ): IntSize {
                return IntSize(
                    if (isSwipingLeftToRight()) {
                        fromValue.width - pillToDotLtrPreviewOffsetDelta().roundToInt()
                    } else {
                        fromValue.width - dotToPillPreviewOffsetDelta().roundToInt().absoluteValue
                    },
                    fromValue.height,
                )
            }
        }
    }

    transformation(pillToDot) {
        object : InterpolatedSharedPropertyTransformation<Offset> {
            override val property = PropertyTransformation.Property.Offset

            override fun PropertyTransformationScope.targetPreviewValue(
                element: ElementKey,
                transition: TransitionState.Transition,
                fromValue: Offset,
                toValue: Offset,
            ): Offset {
                val pillToDotFromOffset = pillToDot.targetOffset(fromContent)!!
                return if (isSwipingLeftToRight()) {
                    pillToDotFromOffset.copy(
                        x = pillToDotFromOffset.x + pillToDotLtrPreviewOffsetDelta()
                    )
                } else {
                    // No translation of the pill during preview when swiping right-to-left.
                    pillToDotFromOffset
                }
            }
        }
    }

    // =======================================================
    // ===== The dots that are appearing or disappearing =====
    // =======================================================

    // The dot indicator that appears when swiping.
    val appearingDot = appearingDot()
    fractionRange(start = 0.5f) {
        fade(appearingDot)
        scaleDraw(appearingDot, scaleX = 0f, scaleY = 0f)
    }

    val disappearingDot = disappearingDot()
    translate(disappearingDot, x = 0.dp, y = 0.dp)
    fractionRange(end = 0.5f) {
        fade(disappearingDot)
        scaleDraw(disappearingDot, scaleX = 0f, scaleY = 0f)
    }

    // The content of the pill that disappears when swiping.
    val disappearingPillContent = disappearingPillContent()
    fade(disappearingPillContent)
}

/** The key for the pill element, i.e. the central content container with the black background. */
internal fun HeadlineItemKey.toPillElementKey(): ElementKey {
    return ElementKey(
        debugName = "${key}_pill",
        identity = SharedPillElementIdentity(this),
        contentPicker = SharedPillElementContentPicker,
    )
}

/** The key for the start content of the pill. */
internal fun HeadlineItemKey.toStartContentElementKey(): ElementKey {
    return ElementKey(debugName = "${key}_start", identity = StartContentElementIdentity(this))
}

/** The key for the end content of the pill. */
internal fun HeadlineItemKey.toEndContentElementKey(): ElementKey {
    return ElementKey(debugName = "${key}_end", identity = EndContentElementIdentity(this))
}

private data class SharedPillElementIdentity(val item: HeadlineItemKey)

private data class StartContentElementIdentity(val item: HeadlineItemKey)

private data class EndContentElementIdentity(val item: HeadlineItemKey)

/** The [ElementContentPicker] for the pill, when it is shared. */
private object SharedPillElementContentPicker : ElementContentPicker {
    override fun contentDuringTransition(
        element: ElementKey,
        transition: TransitionState.Transition,
        fromContentZIndex: Long,
        toContentZIndex: Long,
    ): ContentKey {
        val identity = element.identity as SharedPillElementIdentity
        val itemScene = identity.item.toSceneKey()
        // Always pick the scene that contains the item and its content if possible, otherwise use
        // STL default.
        return if (transition.isTransitioningFromOrTo(itemScene)) {
            itemScene
        } else {
            DefaultElementContentPicker.contentDuringTransition(
                element,
                transition,
                fromContentZIndex,
                toContentZIndex,
            )
        }
    }
}
