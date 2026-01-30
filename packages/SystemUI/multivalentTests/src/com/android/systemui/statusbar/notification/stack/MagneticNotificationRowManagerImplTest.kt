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

import android.os.VibrationEffect
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import androidx.dynamicanimation.animation.SpringForce
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.haptics.fakeVibratorHelper
import com.android.systemui.haptics.msdl.fakeMSDLPlayer
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.notification.Roundable
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.stack.MagneticNotificationRowManagerImpl.State
import com.android.systemui.statusbar.phone.fake
import com.android.systemui.statusbar.phone.notificationTargetsHelper
import com.android.systemui.testKosmos
import com.google.android.msdl.data.model.MSDLToken
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class MagneticNotificationRowManagerImplTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val childrenCount = 5
    private val stackScrollLayout = mock<NotificationStackScrollLayout>()
    private val sectionsManager = mock<NotificationSectionsManager>()
    private val msdlPlayer = kosmos.fakeMSDLPlayer
    private val vibratorHelper = kosmos.fakeVibratorHelper

    private val underTest = kosmos.magneticNotificationRowManagerImpl

    private lateinit var magneticRowListeners: MutableList<TestableMagneticRowListener>
    private lateinit var children: MutableList<ExpandableNotificationRow>
    private lateinit var swipedRow: ExpandableNotificationRow

    @Before
    fun setUp() {
        magneticRowListeners = mutableListOf()
        children = mutableListOf()
        val magneticRoundableTargets = mutableListOf<MagneticRoundableTarget>()

        repeat(childrenCount) {
            val row = mock<ExpandableNotificationRow>()
            val listener = TestableMagneticRowListener()
            whenever(row.magneticRowListener).thenReturn(listener)
            val magneticRoundableTarget = MagneticRoundableTarget(mock<Roundable>(), listener)

            magneticRowListeners.add(listener)
            children.add(row)
            magneticRoundableTargets.add(magneticRoundableTarget)
        }

        swipedRow = children[childrenCount / 2]
        kosmos.notificationTargetsHelper.fake.magneticRoundableTargets = magneticRoundableTargets
    }

    @Test
    fun setMagneticAndRoundableTargets_onIdle_targetsGetSet() =
        kosmos.testScope.runTest {
            // WHEN the targets are set for a row
            setTargets()

            // THEN the magnetic and roundable targets are defined and the state is TARGETS_SET
            assertThat(underTest.currentState).isEqualTo(State.TARGETS_SET)
            assertThat(underTest.currentMagneticListeners.isNotEmpty()).isTrue()
            assertThat(underTest.isSwipedViewRoundableSet).isTrue()
        }

    @Test
    fun setMagneticRowTranslation_whenTargetsAreSet_startsPulling() =
        kosmos.testScope.runTest {
            // GIVEN targets are set
            setTargets()

            // WHEN setting a translation for the swiped row
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // THEN the state moves to PULLING
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
        }

    @Test
    fun setMagneticRowTranslation_whenIdle_doesNotSetMagneticTranslation() =
        kosmos.testScope.runTest {
            // GIVEN an IDLE state
            // WHEN setting a translation for the swiped row
            val row = children[childrenCount / 2]
            underTest.setMagneticRowTranslation(row, translation = 100f)

            // THEN no magnetic translations are set
            val canSetMagneticTranslation =
                underTest.setMagneticRowTranslation(row, translation = 100f)
            assertThat(canSetMagneticTranslation).isFalse()
        }

    @Test
    fun setMagneticRowTranslation_whenRowIsNotSwiped_doesNotSetMagneticTranslation() =
        kosmos.testScope.runTest {
            // GIVEN that targets are set
            setTargets()

            // WHEN setting a translation for a row that is not being swiped
            val differentRow = children[childrenCount / 2 - 1]
            val canSetMagneticTranslation =
                underTest.setMagneticRowTranslation(differentRow, translation = 100f)

            // THEN no magnetic translations are set
            assertThat(canSetMagneticTranslation).isFalse()
        }

    @Test
    fun setMagneticRowTranslation_whenDismissible_belowThreshold_whenPulling_setsTranslations() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the rows are being pulled
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN setting a translation that will fall below the threshold
            val translation = 50f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the targets continue to be pulled and translations are set
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
            assertThat(swipedRow.testableTranslation())
                .isEqualTo(underTest.swipedRowMultiplier * translation)
        }

    @Test
    fun setMagneticRowTranslation_whenNotDismissible_belowThreshold_whenPulling_setsTranslations() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the rows are being pulled
            magneticRowListeners[childrenCount / 2].canBeDismissed = false
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN setting a translation that will fall below the threshold
            val translation = 50f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the targets continue to be pulled and reduced translations are set
            val expectedTranslation = getReducedTranslation(translation)
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
            assertThat(swipedRow.testableTranslation()).isEqualTo(expectedTranslation)
        }

    @Test
    fun setMagneticRowTranslation_whenDismissible_aboveThreshold_whilePulling_detaches() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the rows are being pulled
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN setting a translation that will fall above the threshold
            val translation = 150f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the swiped view detaches and the correct detach haptics play
            assertThat(underTest.currentState).isEqualTo(State.DETACHED)
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
        }

    @Test
    fun setMagneticRowTranslation_whenNotDismissible_aboveThreshold_whilePulling_doesNotDetach() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the rows are being pulled
            magneticRowListeners[childrenCount / 2].canBeDismissed = false
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN setting a translation that will fall above the threshold
            val translation = 150f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the swiped view does not detach and the reduced translation is set
            val expectedTranslation = getReducedTranslation(translation)
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
            assertThat(swipedRow.testableTranslation()).isEqualTo(expectedTranslation)
        }

    @Test
    fun setMagneticRowTranslation_whileDetached_setsTranslationAndStaysDetached() =
        kosmos.testScope.runTest {
            // GIVEN that the swiped view has been detached
            setDetachedState()

            // WHEN setting a new translation
            val translation = 300f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the swiped view continues to be detached
            assertThat(underTest.currentState).isEqualTo(State.DETACHED)
        }

    @Test
    fun onMagneticInteractionEnd_whilePulling_goesToIdle() =
        kosmos.testScope.runTest {
            // GIVEN targets are set
            setTargets()

            // WHEN setting a translation for the swiped row
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN the interaction ends on the row
            underTest.onMagneticInteractionEnd(swipedRow, dismissing = false, velocity = null)

            // THEN the state resets
            assertThat(underTest.currentState).isEqualTo(State.IDLE)
        }

    @Test
    fun onMagneticInteractionEnd_whileTargetsSet_goesToIdle() =
        kosmos.testScope.runTest {
            // GIVEN that targets are set
            setTargets()

            // WHEN the interaction ends on the row
            underTest.onMagneticInteractionEnd(swipedRow, dismissing = false, velocity = null)

            // THEN the state resets
            assertThat(underTest.currentState).isEqualTo(State.IDLE)
        }

    @Test
    fun onMagneticInteractionEnd_whileDetached_goesToIdle() =
        kosmos.testScope.runTest {
            // GIVEN the swiped row is detached
            setDetachedState()

            // WHEN the interaction ends on the row
            underTest.onMagneticInteractionEnd(swipedRow, dismissing = false, velocity = null)

            // THEN the state resets
            assertThat(underTest.currentState).isEqualTo(State.IDLE)
        }

    @Test
    fun onMagneticInteractionEnd_whenDetached_cancelsMagneticAnimations() =
        kosmos.testScope.runTest {
            // GIVEN the swiped row is detached
            setDetachedState()

            // WHEN the interaction ends on the row
            underTest.onMagneticInteractionEnd(swipedRow, dismissing = false, velocity = null)

            // THEN magnetic animations are cancelled
            assertThat(magneticRowListeners[childrenCount / 2].magneticAnimationCancelled).isTrue()
        }

    @Test
    fun onMagneticInteractionEnd_forMagneticNeighbor_cancelsMagneticAnimations() =
        kosmos.testScope.runTest {
            val neighborIndex = childrenCount / 2 - 1
            val neighborRow = children[neighborIndex]

            // GIVEN that targets are set
            setTargets()

            // WHEN the interactionEnd is called on a target different from the swiped row
            underTest.onMagneticInteractionEnd(neighborRow, dismissing = false, velocity = null)

            // THEN magnetic animations are cancelled
            assertThat(magneticRowListeners[neighborIndex].magneticAnimationCancelled).isTrue()
        }

    @Test
    @EnableFlags(Flags.FLAG_MSDL_FEEDBACK)
    fun onMagneticInteractionEnd_whenPulling_fromDismiss_playsMSDLThresholdHaptics() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the swiped row is being pulled
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN the interaction ends on the row because it was dismissed
            underTest.onMagneticInteractionEnd(swipedRow, dismissing = true, velocity = null)

            // THEN threshold haptics play to indicate the dismissal
            assertThat(msdlPlayer.latestTokenPlayed).isEqualTo(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
        }

    @Test
    @DisableFlags(Flags.FLAG_MSDL_FEEDBACK)
    fun onMagneticInteractionEnd_whenPulling_fromDismiss_playsThresholdVibration() =
        kosmos.testScope.runTest {
            // GIVEN a threshold of 100 px
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN that targets are set and the swiped row is being pulled
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // WHEN the interaction ends on the row because it was dismissed
            underTest.onMagneticInteractionEnd(swipedRow, dismissing = true, velocity = null)

            // THEN threshold haptics play to indicate the dismissal
            val composition =
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .compose()
            assertThat(vibratorHelper.hasVibratedWithEffects(composition)).isTrue()
        }

    @Test
    fun onResetRoundness_swipedRoundableGetsCleared() =
        kosmos.testScope.runTest {
            // GIVEN targets are set
            setTargets()

            // WHEN we reset the roundness
            underTest.resetRoundness()

            // THEN the swiped roundable gets cleared
            assertThat(underTest.isSwipedViewRoundableSet).isFalse()
        }

    @Test
    fun isMagneticRowDismissible_whenDetached_isDismissibleWithCorrectDirection_andFastFling() =
        kosmos.testScope.runTest {
            setDetachedState()

            // With a fast enough velocity in the direction of the detachment
            val velocity = Float.POSITIVE_INFINITY

            // The row is dismissible
            val isDismissible = underTest.isMagneticRowSwipedDismissible(swipedRow, velocity)
            assertThat(isDismissible).isTrue()
        }

    @Test
    fun isMagneticRowDismissible_whenDetached_isDismissibleWithCorrectDirection_andSlowFling() =
        kosmos.testScope.runTest {
            setDetachedState()

            // With a very low velocity in the direction of the detachment
            val velocity = 0.01f

            // The row is dismissible
            val isDismissible = underTest.isMagneticRowSwipedDismissible(swipedRow, velocity)
            assertThat(isDismissible).isTrue()
        }

    @Test
    fun isMagneticRowDismissible_whenDetached_isDismissibleWithOppositeDirection_andSlowFling() =
        kosmos.testScope.runTest {
            setDetachedState()

            // With a very low velocity in the opposite direction relative to the detachment
            val velocity = -0.01f

            // The row is dismissible
            val isDismissible = underTest.isMagneticRowSwipedDismissible(swipedRow, velocity)
            assertThat(isDismissible).isTrue()
        }

    @Test
    fun isMagneticRowDismissible_whenDetached_isNotDismissibleWithOppositeDirection_andFastFling() =
        kosmos.testScope.runTest {
            setDetachedState()

            // With a high enough velocity in the opposite direction relative to the detachment
            val velocity = Float.NEGATIVE_INFINITY

            // The row is not dismissible
            val isDismissible = underTest.isMagneticRowSwipedDismissible(swipedRow, velocity)
            assertThat(isDismissible).isFalse()
        }

    @Test
    fun setMagneticRowTranslation_whenDetached_belowAttachThreshold_reattaches() =
        kosmos.testScope.runTest {
            // GIVEN that the swiped view has been detached
            setDetachedState()

            // WHEN setting a new translation above the attach threshold
            val translation = 50f
            underTest.setMagneticRowTranslation(swipedRow, translation)

            // THEN the swiped view reattaches magnetically and the state becomes PULLING
            assertThat(underTest.currentState).isEqualTo(State.PULLING)
        }

    @Test
    fun getDetachDirection_whilePulling_returnsZero() =
        kosmos.testScope.runTest {
            // GIVEN a detach threshold
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN the swiped row is being pulled
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // THEN the detach direction is zero
            val detachDirection = underTest.getDetachDirection(swipedRow)
            assertThat(detachDirection).isEqualTo(0)
        }

    @Test
    fun getDetachDirection_withoutSwipedRow_returnsZero() =
        kosmos.testScope.runTest {
            // GIVEN a detach threshold
            val threshold = 100f
            underTest.onDensityChange(
                threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
            )

            // GIVEN the swiped row is being pulled
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // THEN the detach direction for a non-swiped row is zero
            val neighborIndex = childrenCount / 2 - 1
            val neighborRow = children[neighborIndex]
            val detachDirection = underTest.getDetachDirection(neighborRow)
            assertThat(detachDirection).isEqualTo(0)
        }

    @Test
    fun getDetachDirection_whenDetachedToTheRight_returnsCorrectDirection() =
        kosmos.testScope.runTest {
            // GIVEN that the swiped row is detached to the right
            setDetachedState()

            // THEN the detach direction is 1
            val detachDirection = underTest.getDetachDirection(swipedRow)
            assertThat(detachDirection).isEqualTo(1)
        }

    @Test
    fun getDetachDirection_whenDetachedToTheLeft_returnsCorrectDirection() =
        kosmos.testScope.runTest {
            // GIVEN that the swiped row is detached to the left
            setDetachedState(direction = -1)

            // THEN the detach direction is -1
            val detachDirection = underTest.getDetachDirection(swipedRow)
            assertThat(detachDirection).isEqualTo(-1)
        }

    @Test
    fun getDetachDirection_afterADismissal_returnsCorrectDirection() =
        kosmos.testScope.runTest {
            // GIVEN that the swiped row is detached to the right
            setDetachedState()
            assertThat(underTest.getDetachDirection(swipedRow)).isEqualTo(1)

            // GIVEN that the notification is dismissed
            underTest.onMagneticInteractionEnd(swipedRow, dismissing = true, velocity = 5000f)

            // WHEN we begin interacting with another row
            swipedRow = children.first()
            setTargets()
            underTest.setMagneticRowTranslation(swipedRow, translation = 100f)

            // THEN the detach direction is 0
            assertThat(underTest.getDetachDirection(swipedRow)).isEqualTo(0)
        }

    @After
    fun tearDown() {
        // We reset the manager so that all MagneticRowListener can cancel all animations
        underTest.reset()
    }

    /**
     * Set the detached state towards a specific direction:
     *
     * 1 -> detached to the right, -1 -> detached to the left
     */
    private fun setDetachedState(direction: Int = 1) {
        val threshold = 100f
        underTest.onDensityChange(
            threshold / MagneticNotificationRowManager.MAGNETIC_DETACH_THRESHOLD_DP
        )

        // Set the pulling state
        setTargets()
        underTest.setMagneticRowTranslation(swipedRow, translation = direction * 100f)

        // Set a translation that will fall above the threshold
        val translation = direction * 150f
        underTest.setMagneticRowTranslation(swipedRow, translation)

        assertThat(underTest.currentState).isEqualTo(State.DETACHED)
    }

    private fun setTargets() {
        underTest.setMagneticAndRoundableTargets(swipedRow, stackScrollLayout, sectionsManager)
    }

    private fun getReducedTranslation(originalTranslation: Float) =
        underTest.swipedRowMultiplier *
            originalTranslation *
            MagneticNotificationRowManagerImpl.MAGNETIC_REDUCTION

    private fun ExpandableNotificationRow.testableTranslation(): Float =
        (magneticRowListener as TestableMagneticRowListener).currentTranslation

    private inner class TestableMagneticRowListener : MagneticRowListener {

        var currentTranslation = 0f
            private set

        var magneticAnimationCancelled = false
            private set

        var canBeDismissed = true

        override fun setMagneticTranslation(translation: Float) {
            currentTranslation = translation
        }

        override fun triggerMagneticForce(
            endTranslation: Float,
            springForce: SpringForce,
            startVelocity: Float,
        ) {}

        override fun cancelMagneticAnimations() {
            magneticAnimationCancelled = true
        }

        override fun cancelTranslationAnimations() {}

        override fun canRowBeDismissed(): Boolean = canBeDismissed

        override fun getRowLoggingKey(): String = "testable listener"
    }
}
