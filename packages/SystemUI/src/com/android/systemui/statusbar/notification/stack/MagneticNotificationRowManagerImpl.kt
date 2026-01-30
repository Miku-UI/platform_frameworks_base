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

package com.android.systemui.statusbar.notification.stack

import android.os.VibrationAttributes
import android.os.VibrationEffect
import androidx.dynamicanimation.animation.SpringForce
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.statusbar.notification.Roundable
import com.android.systemui.statusbar.notification.TopBottomRoundness
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotificationRowLogger
import com.android.systemui.util.time.SystemClock
import com.google.android.msdl.data.model.MSDLToken
import com.google.android.msdl.domain.InteractionProperties
import com.google.android.msdl.domain.MSDLPlayer
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import org.jetbrains.annotations.TestOnly

@SysUISingleton
class MagneticNotificationRowManagerImpl
@Inject
constructor(
    private val msdlPlayer: MSDLPlayer,
    private val vibratorHelper: VibratorHelper,
    private val notificationTargetsHelper: NotificationTargetsHelper,
    private val notificationRoundnessManager: NotificationRoundnessManager,
    private val logger: NotificationRowLogger,
    private val systemClock: SystemClock,
) : MagneticNotificationRowManager {

    var currentState = State.IDLE
        private set(value) {
            val swipedLoggingKey =
                currentMagneticListeners.swipedListener()?.getRowLoggingKey().toString()
            if (swipedLoggingKey == "null") {
                logger.logMagneticRowManagerInvalidStateChange(from = currentState, to = value)
            } else {
                logger.logMagneticRowManagerStateSet(
                    loggingKey = swipedLoggingKey,
                    from = currentState,
                    to = value,
                )
            }
            field = value
        }

    // Magnetic targets
    var currentMagneticListeners = listOf<MagneticRowListener?>()
        private set

    private var magneticDetachThreshold = Float.POSITIVE_INFINITY
    private var magneticAttachThreshold = 0f

    // Has the roundable target been set for the magnetic view that is being swiped.
    val isSwipedViewRoundableSet: Boolean
        @TestOnly get() = notificationRoundnessManager.isSwipedViewSet

    // Animation spring forces
    private val detachForce =
        SpringForce().setStiffness(DETACH_STIFFNESS).setDampingRatio(DETACH_DAMPING_RATIO)
    private val snapForce =
        SpringForce().setStiffness(SNAP_BACK_STIFFNESS).setDampingRatio(SNAP_BACK_DAMPING_RATIO)

    // Multiplier applied to the translation of a row while swiped
    val swipedRowMultiplier =
        MAGNETIC_TRANSLATION_MULTIPLIERS[MAGNETIC_TRANSLATION_MULTIPLIERS.size / 2]

    private var dismissVelocity = 0f

    private val detachDirectionEstimator = DirectionEstimator()

    private var magneticSwipeInfoProvider: MagneticNotificationRowManager.SwipeInfoProvider? = null

    // Last time pulling haptics played, in milliseconds since boot
    // (see SystemClock.elapsedRealtime)
    private var lastVibrationTime = 0L

    private val detachedRoundnessSet =
        List(ROUNDNESS_MULTIPLIERS.size) { index ->
            when (index) {
                ROUNDNESS_MULTIPLIERS.size / 2 - 1 ->
                    TopBottomRoundness(topRoundness = 0f, bottomRoundness = 1f)
                ROUNDNESS_MULTIPLIERS.size / 2 -> TopBottomRoundness(roundness = 1f)
                ROUNDNESS_MULTIPLIERS.size / 2 + 1 ->
                    TopBottomRoundness(topRoundness = 1f, bottomRoundness = 0f)
                else -> TopBottomRoundness(roundness = 0f)
            }
        }

    override fun setInfoProvider(
        swipeInfoProvider: MagneticNotificationRowManager.SwipeInfoProvider?
    ) {
        magneticSwipeInfoProvider = swipeInfoProvider
    }

    override fun onDensityChange(density: Float) {
        magneticDetachThreshold =
            density * MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
        magneticAttachThreshold =
            density * MagneticNotificationRowManager.MAGNETIC_ATTACH_THRESHOLD_DP
        dismissVelocity = density * DISMISS_VELOCITY
    }

    override fun setMagneticAndRoundableTargets(
        swipingRow: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
    ) {
        if (currentState == State.IDLE) {
            detachDirectionEstimator.reset()
            updateMagneticAndRoundableTargets(swipingRow, stackScrollLayout, sectionsManager)
            currentState = State.TARGETS_SET
        } else {
            logger.logMagneticAndRoundableTargetsNotSet(currentState, swipingRow.loggingKey)
        }
    }

    private fun updateMagneticAndRoundableTargets(
        expandableNotificationRow: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
    ) {
        // All targets
        val newTargets =
            notificationTargetsHelper.findMagneticRoundableTargets(
                expandableNotificationRow,
                stackScrollLayout,
                sectionsManager,
                MAGNETIC_TRANSLATION_MULTIPLIERS.size,
            )

        // Update roundable targets
        notificationRoundnessManager.clear()
        notificationRoundnessManager.setRoundableTargets(newTargets.map { it.roundable })

        val newListeners =
            newTargets
                // Remove the roundable boundaries
                .filterIndexed { i, _ -> i > 0 && i < newTargets.size - 1 }
                .map { it.magneticRowListener }
        newListeners.forEach {
            if (currentMagneticListeners.contains(it)) {
                it?.cancelMagneticAnimations()
                if (it == currentMagneticListeners.swipedListener()) {
                    it?.cancelTranslationAnimations()
                }
            }
        }
        currentMagneticListeners = newListeners
    }

    override fun setMagneticRowTranslation(
        row: ExpandableNotificationRow,
        translation: Float,
    ): Boolean {
        if (!row.isSwipedTarget()) return false

        val canTargetBeDismissed =
            currentMagneticListeners.swipedListener()?.canRowBeDismissed() ?: false
        when (currentState) {
            State.IDLE -> {
                logger.logMagneticRowTranslationNotSet(currentState, row.getLoggingKey())
                return false
            }
            State.TARGETS_SET -> {
                detachDirectionEstimator.recordTranslation(translation)
                pullTargets(translation, canTargetBeDismissed)
                currentState = State.PULLING
            }
            State.PULLING -> {
                detachDirectionEstimator.recordTranslation(translation)
                updateRoundness(translation)
                if (canTargetBeDismissed) {
                    pullDismissibleRow(translation)
                } else {
                    pullTargets(translation, canSwipedBeDismissed = false)
                }
            }
            State.DETACHED -> {
                detachDirectionEstimator.recordTranslation(translation)
                translateDetachedRow(translation)
            }
        }
        return true
    }

    private fun updateRoundness(translation: Float, animate: Boolean = false) {
        val normalizedTranslation = abs(swipedRowMultiplier * translation) / magneticDetachThreshold
        val cappedRoundness = normalizedTranslation.coerceIn(0f, MAX_PRE_DETACH_ROUNDNESS)
        val roundnessSet =
            ROUNDNESS_MULTIPLIERS.mapIndexed { i, multiplier ->
                val roundness = multiplier * cappedRoundness
                when (i) {
                    0 -> TopBottomRoundness(bottomRoundness = roundness)
                    ROUNDNESS_MULTIPLIERS.size - 1 -> TopBottomRoundness(topRoundness = roundness)
                    else -> TopBottomRoundness(roundness)
                }
            }
        notificationRoundnessManager.setRoundnessForAffectedViews(
            /* roundnessSet */ roundnessSet,
            animate,
        )
    }

    private fun pullDismissibleRow(translation: Float) {
        val crossedThreshold = abs(translation) >= magneticDetachThreshold
        if (crossedThreshold) {
            detachDirectionEstimator.halt()
            snapNeighborsBack()
            currentMagneticListeners.swipedListener()?.let { detach(it, translation) }
            currentState = State.DETACHED
        } else {
            pullTargets(translation, canSwipedBeDismissed = true)
        }
    }

    private fun pullTargets(translation: Float, canSwipedBeDismissed: Boolean) {
        var targetTranslation: Float
        currentMagneticListeners.forEachIndexed { i, listener ->
            listener?.let {
                if (!canSwipedBeDismissed || !it.canRowBeDismissed()) {
                    // Use a reduced translation if the target swiped can't be dismissed or if the
                    // target itself can't be dismissed
                    targetTranslation =
                        MAGNETIC_TRANSLATION_MULTIPLIERS[i] * translation * MAGNETIC_REDUCTION
                } else {
                    targetTranslation = MAGNETIC_TRANSLATION_MULTIPLIERS[i] * translation
                }
                it.setMagneticTranslation(targetTranslation)
            }
        }
        playPullHaptics(mappedTranslation = translation * swipedRowMultiplier, canSwipedBeDismissed)
    }

    private fun playPullHaptics(mappedTranslation: Float, canSwipedBeDismissed: Boolean) {
        val currentTime = systemClock.elapsedRealtime()
        if ((currentTime - lastVibrationTime) < VIBRATION_TIME_THRESHOLD) return
        lastVibrationTime = currentTime
        val normalizedTranslation = abs(mappedTranslation) / magneticDetachThreshold
        val scaleFactor =
            if (canSwipedBeDismissed) {
                WEAK_VIBRATION_SCALE
            } else {
                STRONG_VIBRATION_SCALE
            }
        val vibrationScale = scaleFactor * normalizedTranslation.pow(VIBRATION_SCALE_EXPONENT)
        val compensatedScale =
            vibrationScale.pow(VIBRATION_PERCEPTION_EXPONENT).coerceAtMost(maximumValue = 1f)
        if (Flags.msdlFeedback()) {
            msdlPlayer.playToken(
                MSDLToken.DRAG_INDICATOR_CONTINUOUS,
                InteractionProperties.DynamicVibrationScale(
                    scale = compensatedScale,
                    vibrationAttributes = VIBRATION_ATTRIBUTES_PIPELINING,
                ),
            )
        } else {
            val composition =
                VibrationEffect.startComposition().apply {
                    repeat(N_LOW_TICKS) {
                        addPrimitive(
                            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                            compensatedScale,
                        )
                    }
                }
            vibratorHelper.vibrate(composition.compose(), VIBRATION_ATTRIBUTES_PIPELINING)
        }
    }

    private fun playThresholdHaptics() {
        if (Flags.msdlFeedback()) {
            msdlPlayer.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
        } else {
            val composition =
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .compose()
            vibratorHelper.vibrate(composition)
        }
    }

    private fun snapNeighborsBack(velocity: Float? = null) {
        currentMagneticListeners.forEachIndexed { i, target ->
            target?.let {
                if (i != currentMagneticListeners.size / 2) {
                    val velocityMultiplier = MAGNETIC_TRANSLATION_MULTIPLIERS[i]
                    snapBack(it, velocity?.times(velocityMultiplier))
                }
            }
        }
    }

    private fun detach(listener: MagneticRowListener, toPosition: Float) {
        val direction = detachDirectionEstimator.direction
        val velocity = magneticSwipeInfoProvider?.getCurrentSwipeVelocity() ?: 0f
        listener.cancelMagneticAnimations()
        listener.triggerMagneticForce(
            toPosition,
            detachForce,
            startVelocity = direction * abs(velocity),
        )
        notificationRoundnessManager.setRoundnessForAffectedViews(
            /* roundnessSet */ detachedRoundnessSet,
            /* animate */ true,
        )
        playThresholdHaptics()
    }

    private fun snapBack(listener: MagneticRowListener, velocity: Float?) {
        listener.cancelMagneticAnimations()
        listener.triggerMagneticForce(
            endTranslation = 0f,
            snapForce,
            startVelocity = velocity ?: 0f,
        )
    }

    private fun translateDetachedRow(translation: Float) {
        val crossedThreshold = abs(translation) <= magneticAttachThreshold
        if (crossedThreshold) {
            updateRoundness(translation, animate = true)
            attach(translation)
            currentState = State.PULLING
        } else {
            val swiped = currentMagneticListeners.swipedListener()
            swiped?.setMagneticTranslation(translation)
        }
    }

    private fun attach(translation: Float) {
        val detachDirection = detachDirectionEstimator.direction
        val swipeVelocity = magneticSwipeInfoProvider?.getCurrentSwipeVelocity() ?: 0f
        playThresholdHaptics()
        currentMagneticListeners.forEachIndexed { i, listener ->
            val targetTranslation = MAGNETIC_TRANSLATION_MULTIPLIERS[i] * translation
            val attachForce =
                SpringForce().setStiffness(ATTACH_STIFFNESS).setDampingRatio(ATTACH_DAMPING_RATIO)
            val velocity =
                if (i == currentMagneticListeners.size / 2) {
                    -detachDirection * abs(swipeVelocity)
                } else {
                    0f
                }
            listener?.cancelMagneticAnimations()
            listener?.triggerMagneticForce(
                endTranslation = targetTranslation,
                springForce = attachForce,
                startVelocity = velocity,
            )
        }
        detachDirectionEstimator.reset()
    }

    override fun onMagneticInteractionEnd(
        row: ExpandableNotificationRow,
        dismissing: Boolean,
        velocity: Float?,
    ) {
        detachDirectionEstimator.reset()
        if (row.isSwipedTarget()) {
            when (currentState) {
                State.TARGETS_SET -> {
                    if (dismissing) {
                        playThresholdHaptics()
                    }
                    currentState = State.IDLE
                }
                State.PULLING -> {
                    if (dismissing) {
                        playThresholdHaptics()
                    }
                    snapNeighborsBack(velocity)
                    currentState = State.IDLE
                }
                State.DETACHED -> {
                    // Cancel any detaching animation that may be occurring
                    currentMagneticListeners.swipedListener()?.cancelMagneticAnimations()
                    currentState = State.IDLE
                }
                else -> {}
            }
        } else {
            // A magnetic neighbor may be dismissing. In this case, we need to cancel any snap back
            // magnetic animation to let the external dismiss animation proceed.
            val listener = currentMagneticListeners.find { it == row.magneticRowListener }
            listener?.cancelMagneticAnimations()
        }
    }

    override fun isMagneticRowSwipedDismissible(
        row: ExpandableNotificationRow,
        endVelocity: Float,
    ): Boolean {
        if (!row.isSwipedTarget()) return false
        val isEndVelocityLargeEnough = abs(endVelocity) >= dismissVelocity
        val shouldSnapBack =
            isEndVelocityLargeEnough && detachDirectionEstimator.direction != sign(endVelocity)

        return when (currentState) {
            State.IDLE,
            State.TARGETS_SET,
            State.PULLING -> isEndVelocityLargeEnough
            State.DETACHED -> !shouldSnapBack
        }
    }

    override fun getDetachDirection(row: ExpandableNotificationRow): Int =
        if (row.isSwipedTarget()) {
            detachDirectionEstimator.direction.toInt()
        } else {
            0
        }

    override fun resetRoundness() = notificationRoundnessManager.clear()

    override fun reset() {
        detachDirectionEstimator.reset()
        currentMagneticListeners.forEach {
            it?.cancelMagneticAnimations()
            it?.cancelTranslationAnimations()
        }
        currentState = State.IDLE
        currentMagneticListeners = listOf()
        notificationRoundnessManager.clear()
    }

    private fun List<MagneticRowListener?>.swipedListener(): MagneticRowListener? =
        getOrNull(index = size / 2)

    private fun ExpandableNotificationRow.isSwipedTarget(): Boolean =
        magneticRowListener == currentMagneticListeners.swipedListener()

    private fun NotificationRoundnessManager.clear() = setViewsAffectedBySwipe(listOf())

    private fun NotificationRoundnessManager.setRoundableTargets(targets: List<Roundable?>) =
        setViewsAffectedBySwipe(targets)

    /**
     * A class to estimate the direction of a gesture translations with a moving average.
     *
     * The class holds a buffer that stores translations. When requested, the direction of movement
     * is estimated as the sign of the average value from the buffer.
     */
    class DirectionEstimator {

        // A ring buffer to hold past translations. This is a FIFO structure with a fixed size.
        private val translationBuffer = FloatArray(size = TRANSLATION_BUFFER_SIZE)
        // The head points to the next available slot in the buffer
        private var bufferHead = 0

        /**
         * A kernel function that multiplies values in the translation buffer to derive a weighted
         * average.
         *
         * The kernel should give higher weights to most recent values in the buffer, and smaller
         * weights to past values.
         */
        private val kernel =
            FloatArray(size = TRANSLATION_BUFFER_SIZE) { i ->
                val x = (i + 1) / TRANSLATION_BUFFER_SIZE.toFloat()
                -0.45f * cos(kotlin.math.PI.toFloat() * x) + 0.55f
            }

        /**
         * The estimated direction of the translations. It will be estimated as the weighted average
         * of the values in the [translationBuffer] and set only once when the estimator is halted.
         */
        var direction = 0f
            private set

        private var acceptTranslations = true

        /**
         * Add a new translation to the [translationBuffer] if we are still accepting translations
         * (see [halt]). If the buffer is full, we remove the last value and add the new one to the
         * end.
         */
        fun recordTranslation(translation: Float) {
            if (!acceptTranslations) return

            translationBuffer[bufferHead] = translation
            // Move the head pointer, wrapping if necessary
            bufferHead = (bufferHead + 1) % TRANSLATION_BUFFER_SIZE
        }

        /**
         * Halt the operation of the estimator.
         *
         * This stops the estimator from receiving new translations and derives the estimated
         * direction. This is the sign of the weighted average value from the available data in the
         * [translationBuffer].
         */
        fun halt() {
            acceptTranslations = false
            direction = translationBuffer.kernelMean()
        }

        fun reset() {
            direction = 0f
            translationBuffer.fill(element = 0f)
            acceptTranslations = true
        }

        private fun FloatArray.kernelMean(): Float {
            // Unfold the ring buffer into a list with the most recent translations to the right
            val unfolded = mutableListOf<Float>()
            var i = bufferHead
            repeat(times = size) {
                unfolded.add(translationBuffer[i])
                i = (i + 1) % size
            }
            // Get a weighted average after applying the kernel function
            val weightedSum =
                kernel
                    .zip(other = unfolded) { kernelMultiplier, value -> value * kernelMultiplier }
                    .sum()
            return sign(weightedSum / size)
        }

        companion object {
            private const val TRANSLATION_BUFFER_SIZE = 10
        }
    }

    enum class State {
        IDLE,
        TARGETS_SET,
        PULLING,
        DETACHED,
    }

    companion object {
        /**
         * Multipliers applied to the translation of magnetically-coupled views. This list must be
         * symmetric with an odd size, where the center multiplier applies to the view that is
         * currently being swiped. From the center outwards, the multipliers apply to the neighbors
         * of the swiped view.
         */
        private val MAGNETIC_TRANSLATION_MULTIPLIERS = listOf(0.04f, 0.12f, 0.5f, 0.12f, 0.04f)

        /**
         * Multipliers applied to roundable targets. Their structure mimic that of
         * [MAGNETIC_TRANSLATION_MULTIPLIERS] but are only used to modify the roundness of current
         * targets.
         */
        private val ROUNDNESS_MULTIPLIERS = listOf(0.5f, 0.7f, 0.9f, 1.0f, 0.9f, 0.7f, 0.5f)

        const val MAGNETIC_REDUCTION = 0.65f

        /** Spring parameters for physics animators */
        private const val DETACH_STIFFNESS = 800f
        private const val DETACH_DAMPING_RATIO = 0.95f
        private const val SNAP_BACK_STIFFNESS = 550f
        private const val SNAP_BACK_DAMPING_RATIO = 0.6f
        private const val ATTACH_STIFFNESS = 850f
        private const val ATTACH_DAMPING_RATIO = 0.95f

        private const val DISMISS_VELOCITY = 500 // in dp/sec

        // Maximum value of corner roundness that gets applied during the pre-detach dragging
        private const val MAX_PRE_DETACH_ROUNDNESS = 0.8f

        private val VIBRATION_ATTRIBUTES_PIPELINING =
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .setFlags(VibrationAttributes.FLAG_PIPELINED_EFFECT)
                .build()
        private const val WEAK_VIBRATION_SCALE = 0.2f
        private const val STRONG_VIBRATION_SCALE = 0.4f
        // Exponent applied to a normalized translation to make the linear translation exponential
        private const val VIBRATION_SCALE_EXPONENT = 1.27f
        // Exponent applied to a vibration scale to compensate for human vibration perception
        private const val VIBRATION_PERCEPTION_EXPONENT = 1 / 0.89f
        // How much time we wait (in milliseconds) before we play a new pulling vibration
        private const val VIBRATION_TIME_THRESHOLD = 60
        // The number of LOW_TICK primitives in a pulling vibration
        private const val N_LOW_TICKS = 5
    }
}
