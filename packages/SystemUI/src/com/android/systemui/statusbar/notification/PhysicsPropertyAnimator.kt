/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.systemui.statusbar.notification

import android.util.FloatProperty
import android.util.Log
import android.util.Property
import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.internal.dynamicanimation.animation.DynamicAnimation
import com.android.internal.dynamicanimation.animation.SpringAnimation
import com.android.internal.dynamicanimation.animation.SpringForce
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.PhysicsPropertyAnimator.Companion.TAG
import com.android.systemui.statusbar.notification.PhysicsPropertyAnimator.Companion.createDefaultSpring
import com.android.systemui.statusbar.notification.stack.AnimationProperties
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.asin
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * A physically animatable property of a view.
 *
 * @param tag the view tag to safe this property in
 * @param property the property to animate.
 * @param avoidDoubleOvershoot should this property avoid double overshoot when animated
 */
data class PhysicsProperty
@JvmOverloads
constructor(
    val tag: Int,
    val property: Property<View, Float>,
    val avoidDoubleOvershoot: Boolean = true,
) {
    val offsetProperty =
        object : FloatProperty<View>(property.name) {
            override fun get(view: View): Float {
                return property.get(view)
            }

            override fun setValue(view: View, offset: Float) {
                val propertyData = view.getTag(tag) as PropertyData? ?: return
                propertyData.offset = offset
                property.set(view, propertyData.finalValue + offset)
            }
        }

    fun setFinalValue(view: View, finalValue: Float) {
        val propertyData = obtainPropertyData(view, this)
        propertyData.finalValue = finalValue
        if (propertyData.finalValue + propertyData.offset != property.get(view)) {
            property.set(view, propertyData.finalValue + propertyData.offset)
        }
    }
}

/** The propertyData associated with each animation running */
data class PropertyData(
    var finalValue: Float = 0f,
    var offset: Float = 0f,
    var animator: SpringAnimation? = null,
    var delayRunnable: Runnable? = null,

    /**
     * A runnable that should be executed if the animation is skipped to end / cancelled before the
     * animation actually starts running.
     */
    var endedBeforeStartingCleanupHandler: ((Boolean) -> Unit)? = null,
    var startOffset: Float = 0f,
    var doubleOvershootAvoidingListener: DynamicAnimation.OnAnimationUpdateListener? = null,
)

/**
 * A utility that can run physics based animations in a simple way. It properly handles overlapping
 * calls where sometimes a property can be set without animation, while also having instances where
 * it's supposed to start animations.
 *
 * This overall helps making sure that physics based animations complete and don't constantly start
 * new transitions which can lead to a feeling of lagging behind.
 *
 * Overall it is achieved by starting offset animations to an end value as soon as an animation is
 * requested and updating the end value immediately when no animation is needed. With the offset
 * always going to 0, this ensures that animations complete within a short time after an animation
 * has been requested.
 */
class PhysicsPropertyAnimator {
    companion object {
        @JvmField val TAG_ANIMATOR_TRANSLATION_Y = R.id.translation_y_animator_tag

        @JvmField
        val Y_TRANSLATION: PhysicsProperty =
            PhysicsProperty(TAG_ANIMATOR_TRANSLATION_Y, View.TRANSLATION_Y)

        // Uses the standard spatial material spring by default
        @JvmStatic
        fun createDefaultSpring(): SpringForce {
            return SpringForce().setStiffness(380f).setDampingRatio(0.68f)
        }

        @JvmStatic
        @JvmOverloads
        /**
         * Set a property on a view, updating its value, even if it's already animating. The @param
         * animated can be used to request an animation. If the view isn't animated, this utility
         * will update the current animation if existent, such that the end value will point
         * to @param newEndValue or apply it directly if there's no animation.
         *
         * @param maxOvershoot limit the spring overshoot of the animation. If specified, must be a
         *   positive, finite distance.
         */
        fun setProperty(
            view: View,
            animatableProperty: PhysicsProperty,
            newEndValue: Float,
            properties: AnimationProperties? = null,
            animated: Boolean = false,
            endListener: DynamicAnimation.OnAnimationEndListener? = null,
            maxOvershoot: Float? = null,
        ) {
            if (animated) {
                startAnimation(
                    view,
                    animatableProperty,
                    newEndValue,
                    properties,
                    endListener,
                    maxOvershoot,
                )
            } else {
                animatableProperty.setFinalValue(view, newEndValue)
            }
        }

        fun isAnimating(view: View, property: PhysicsProperty): Boolean {
            val (_, _, animator, _) = obtainPropertyData(view, property)
            return animator?.isRunning ?: false
        }

        internal val TAG = "PhysicsPropertyAnimator"
    }
}

private fun startAnimation(
    view: View,
    animatableProperty: PhysicsProperty,
    newEndValue: Float,
    properties: AnimationProperties?,
    endListener: DynamicAnimation.OnAnimationEndListener?,
    maxOvershoot: Float?,
) {
    val property = animatableProperty.property
    val propertyData = obtainPropertyData(view, animatableProperty)
    val previousEndValue = propertyData.finalValue
    if (previousEndValue == newEndValue) {
        return
    }
    propertyData.finalValue = newEndValue
    var animator = propertyData.animator
    if (animator == null) {
        animator = SpringAnimation(view, animatableProperty.offsetProperty)
        propertyData.animator = animator
        val listener = properties?.getAnimationEndListener(animatableProperty.property)
        if (listener != null) {
            animator.addEndListener(listener)
        }
        // We always notify things as started even if we have a delay
        properties?.getAnimationStartListener(animatableProperty.property)?.accept(animator)
        // remove the tag when the animation is finished
        animator.addEndListener { _, _, _, _ ->
            propertyData.animator = null
            propertyData.doubleOvershootAvoidingListener = null
            // Let's make sure we never get stuck with an offset even when canceling
            // We never actually cancel running animations but keep it around, so this only
            // triggers if things really should end.
            propertyData.offset = 0f
        }
    }
    if (
        animatableProperty.avoidDoubleOvershoot &&
            propertyData.doubleOvershootAvoidingListener == null
    ) {
        propertyData.doubleOvershootAvoidingListener =
            DynamicAnimation.OnAnimationUpdateListener { _, offset: Float, velocity: Float ->
                val isOscillatingBackwards = velocity.sign == propertyData.startOffset.sign
                val didAlreadyRemoveBounciness =
                    animator.spring.dampingRatio == SpringForce.DAMPING_RATIO_NO_BOUNCY
                val isOvershooting = offset.sign != propertyData.startOffset.sign
                if (isOvershooting && isOscillatingBackwards && !didAlreadyRemoveBounciness) {
                    // our offset is starting to decrease, let's remove all overshoot
                    animator.spring.setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                } else if (
                    !isOvershooting && (didAlreadyRemoveBounciness || isOscillatingBackwards)
                ) {
                    // we already did overshoot, let's skip to the end to avoid oscillations.
                    // Usually we shouldn't hit this as setting the damping ratio avoid overshoots
                    // but it may still happen if we see jank
                    animator.skipToEnd()
                }
            }
        animator.addUpdateListener(propertyData.doubleOvershootAvoidingListener)
    } else if (
        !animatableProperty.avoidDoubleOvershoot &&
            propertyData.doubleOvershootAvoidingListener != null
    ) {
        animator.removeUpdateListener(propertyData.doubleOvershootAvoidingListener)
    }

    val startOffset = previousEndValue - newEndValue + propertyData.offset

    // reset a new spring as it may have been modified
    val spring = createDefaultSpring().setFinalPosition(0f)
    maxOvershoot
        ?.takeIf { it > 0f }
        ?.let {
            // The spring will animate from [startOffset] to 0. Modify the spring parameters to
            // guarantee the overshoot won't exceed [maxOvershoot].
            spring.limitOvershoot(initialDisplacement = startOffset, maxOvershoot = it)
        }
    animator.setSpring(spring)

    // TODO(b/393581344): look at custom spring
    endListener?.let { animator.addEndListener(it) }

    // Immediately set the new offset that compensates for the immediate end value change
    propertyData.offset = startOffset
    propertyData.startOffset = startOffset
    property.set(view, newEndValue + startOffset)

    // cancel previous starters still pending
    view.removeCallbacks(propertyData.delayRunnable)
    animator.setStartValue(startOffset)
    val startRunnable = Runnable {
        animator.animateToFinalPosition(0f)
        propertyData.delayRunnable = null
        // When setting a new spring on a running animation it doesn't properly set the finish
        // conditions and will never actually end them only calling start explicitly does that,
        // so let's start them again!
        animator.start()
        propertyData.endedBeforeStartingCleanupHandler = null
    }
    propertyData.endedBeforeStartingCleanupHandler = { cancelled ->
        val listener = properties?.getAnimationEndListener(animatableProperty.property)
        listener?.onAnimationEnd(
            propertyData.animator,
            cancelled,
            0f /* value */,
            0f, /* velocity */
        )
        endListener?.onAnimationEnd(
            propertyData.animator,
            cancelled,
            0f /* value */,
            0f, /* velocity */
        )
        propertyData.animator = null
        propertyData.doubleOvershootAvoidingListener = null
        propertyData.offset = 0f
        // We always reset the offset as we never want to get stuck with old values. This is
        // consistent with the end listener above.
        property.set(view, propertyData.finalValue)
        propertyData.endedBeforeStartingCleanupHandler = null
    }
    if (properties != null && properties.delay > 0 && !animator.isRunning) {
        propertyData.delayRunnable = startRunnable
        view.postDelayed(propertyData.delayRunnable, properties.delay)
    } else {
        startRunnable.run()
    }
}

private fun obtainPropertyData(view: View, animatableProperty: PhysicsProperty): PropertyData {
    var propertyData = view.getTag(animatableProperty.tag) as PropertyData?
    if (propertyData == null) {
        propertyData =
            PropertyData(finalValue = animatableProperty.property.get(view), offset = 0f, null)
        view.setTag(animatableProperty.tag, propertyData)
    }
    return propertyData
}

/**
 * Modifies this spring's parameters to guarantee it overshoots by at most [maxOvershoot], when
 * started with [initialDisplacement] and an initial velocity of 0.
 *
 * This requires the current spring parameters to be under-damped.
 */
@VisibleForTesting
fun SpringForce.limitOvershoot(initialDisplacement: Float, maxOvershoot: Float) {
    require(maxOvershoot > 0)
    val absoluteDisplacement = initialDisplacement.absoluteValue.toDouble()
    if (absoluteDisplacement == 0.0) {
        // Nothing to animate, cannot compute the constraint
        return
    }

    val originalStiffness = stiffness.toDouble()
    val originalDamping = dampingRatio.toDouble()
    if (originalDamping <= 0 || originalDamping >= 1) {
        Log.w(
            TAG,
            "limitOvershoot can be applied to under-damped springs only, but is $originalDamping",
        )
        return
    }

    if (maxOvershoot >= absoluteDisplacement) {
        // the overshoot is guaranteed to be less than absoluteDisplacement, so we don't need to
        // adjust
        return
    }

    // Calculate required damping to guarantee the overshoot won't exceed maxOvershoot.
    val lnOvershootRatio = ln(absoluteDisplacement / maxOvershoot)
    val requiredDamping = lnOvershootRatio / sqrt(PI.pow(2) + lnOvershootRatio.pow(2))
    if (requiredDamping < originalDamping) {
        // The current damping is already sufficient to not exceed the maxOvershoot. No need to
        // modify the spring
        return
    }

    if (requiredDamping >= 1) {
        // A critically / over-damped spring would never overshoot. Given the initial conditions
        // above, the branch should not be reached. Log a warning and don't modify the spring, this
        // is a state we did not expect.
        Log.w(
            TAG,
            "Unexpected required damping of $requiredDamping. " +
                "(original: $originalDamping, " +
                "displacement: $absoluteDisplacement, " +
                "maxOvershoot: $maxOvershoot)",
        )
        return
    }

    // The requiredDamping computed above guarantees that the overshoot won't exceed maxOvershoot
    // Now tweak the stiffness to compensate for the shift in frequency. We do this by aligning the
    // first 0-crossing with the original parameters.

    // Compute the time 0 is crossed, assuming the spring starts with 0 initial velocity.
    val omegaN = sqrt(originalStiffness) // Natural frequency
    val omegaD = omegaN * sqrt(1f - originalDamping.pow(2)) // Damped frequency
    val targetTime = (PI / 2f + asin(originalDamping)) / omegaD

    val numerator = PI / 2.0 + asin(requiredDamping)
    val denominator = targetTime * sqrt(1.0 - requiredDamping.pow(2))
    val requiredStiffness = (numerator / denominator).pow(2)

    setStiffness(requiredStiffness.toFloat())
    setDampingRatio(requiredDamping.toFloat())
}
