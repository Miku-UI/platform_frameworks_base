package com.android.systemui.statusbar.notification.stack

import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.createRowGroup
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

/** Tests for {@link NotificationTargetsHelper}. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class NotificationTargetsHelperTest : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val sectionsManager: NotificationSectionsManager = mock()
    private val stackScrollLayout: NotificationStackScrollLayout = mock()

    @Before
    fun setUp() {
        allowTestableLooperAsMainThread()
    }

    private fun notificationTargetsHelper() = NotificationTargetsHelperImpl()

    @Test
    fun targetsForFirstNotificationInGroup() {
        val children = kosmos.createRowGroup(3).childrenContainer
        val swiped = children.attachedChildren[0]

        val actual =
            notificationTargetsHelper()
                .findRoundableTargets(
                    viewSwiped = swiped,
                    stackScrollLayout = stackScrollLayout,
                    sectionsManager = sectionsManager,
                )

        val expected =
            RoundableTargets(
                before = children.notificationHeaderWrapper, // group header
                swiped = swiped,
                after = children.attachedChildren[1],
            )
        assertEquals(expected, actual)
    }

    @Test
    fun targetsForMiddleNotificationInGroup() {
        val children = kosmos.createRowGroup(3).childrenContainer
        val swiped = children.attachedChildren[1]

        val actual =
            notificationTargetsHelper()
                .findRoundableTargets(
                    viewSwiped = swiped,
                    stackScrollLayout = stackScrollLayout,
                    sectionsManager = sectionsManager,
                )

        val expected =
            RoundableTargets(
                before = children.attachedChildren[0],
                swiped = swiped,
                after = children.attachedChildren[2],
            )
        assertEquals(expected, actual)
    }

    @Test
    fun targetsForLastNotificationInGroup() {
        val children = kosmos.createRowGroup(3).childrenContainer
        val swiped = children.attachedChildren[2]

        val actual =
            notificationTargetsHelper()
                .findRoundableTargets(
                    viewSwiped = swiped,
                    stackScrollLayout = stackScrollLayout,
                    sectionsManager = sectionsManager,
                )

        val expected =
            RoundableTargets(before = children.attachedChildren[1], swiped = swiped, after = null)
        assertEquals(expected, actual)
    }

    @Test
    fun findMagneticRoundableTargets_forMiddleChild_createsAllTargets() {
        val childrenNumber = 7
        val children = kosmos.createRowGroup(childrenNumber).childrenContainer
        val expectedTargets =
            children.attachedChildren.mapIndexed { i, child ->
                val useMagneticListener = i > 0 && i < childrenNumber - 1
                child.toMagneticRoundableTarget(useMagneticListener)
            }

        // WHEN the swiped view is the one at the middle of the container
        val swiped = children.attachedChildren[childrenNumber / 2]

        // THEN all the views that surround it become targets with the swiped view at the middle
        val actual =
            notificationTargetsHelper()
                .findMagneticRoundableTargets(
                    viewSwiped = swiped,
                    stackScrollLayout = stackScrollLayout,
                    sectionsManager,
                    numTargets = childrenNumber - 2, // Account for the roundable boundaries
                )
        assertMagneticRoundableTargetsForChildren(actual, expectedTargets)
    }

    @Test
    fun findMagneticRoundableTargets_forTopChild_createsEligibleTargets() {
        val childrenNumber = 7
        val children = kosmos.createRowGroup(childrenNumber).childrenContainer

        // WHEN the swiped view is the first one in the container
        val swiped = children.attachedChildren[0]

        // THEN the neighboring views become targets, with the swiped view at the middle and empty
        // targets to the left, except for the first left-most neighbor, which should be the
        // notification header wrapper as a roundable.
        val actual =
            notificationTargetsHelper()
                .findMagneticRoundableTargets(
                    viewSwiped = swiped,
                    stackScrollLayout = stackScrollLayout,
                    sectionsManager,
                    numTargets = childrenNumber - 2, // Account for the roundable boundaries
                )
        val expectedTargets =
            listOf(
                MagneticRoundableTarget.Empty,
                MagneticRoundableTarget.Empty,
                MagneticRoundableTarget(children.notificationHeaderWrapper, null),
                swiped.toMagneticRoundableTarget(),
                children.attachedChildren[1].toMagneticRoundableTarget(),
                children.attachedChildren[2].toMagneticRoundableTarget(),
                children.attachedChildren[3].toMagneticRoundableTarget(useMagneticListener = false),
            )
        assertMagneticRoundableTargetsForChildren(actual, expectedTargets)
    }

    @Test
    fun findMagneticRoundableTargets_forBottomChild_createsEligibleTargets() {
        val childrenNumber = 7
        val children = kosmos.createRowGroup(childrenNumber).childrenContainer

        // WHEN the view swiped is the last one in the container
        val swiped = children.attachedChildren[childrenNumber - 1]

        // THEN the neighboring views become targets, with the swiped view at the middle and empty
        // targets to the right
        val actual =
            notificationTargetsHelper()
                .findMagneticRoundableTargets(
                    viewSwiped = swiped,
                    stackScrollLayout = stackScrollLayout,
                    sectionsManager,
                    numTargets = childrenNumber - 2, // Account for the roundable boundaries
                )
        val expectedTargets =
            listOf(
                children.attachedChildren[childrenNumber - 4].toMagneticRoundableTarget(
                    useMagneticListener = false
                ),
                children.attachedChildren[childrenNumber - 3].toMagneticRoundableTarget(),
                children.attachedChildren[childrenNumber - 2].toMagneticRoundableTarget(),
                swiped.toMagneticRoundableTarget(),
                MagneticRoundableTarget.Empty,
                MagneticRoundableTarget.Empty,
                MagneticRoundableTarget.Empty,
            )
        assertMagneticRoundableTargetsForChildren(actual, expectedTargets)
    }

    @Test
    fun findMagneticRoundableTargets_doesNotCrossSectionAtTop() {
        val childrenNumber = 7
        val children = kosmos.createRowGroup(childrenNumber).childrenContainer

        // WHEN the second child is swiped and the first one begins a new section
        val swiped = children.attachedChildren[1]
        whenever(sectionsManager.beginsSection(swiped, children.attachedChildren[0])).then { true }

        // THEN the neighboring views become targets, with the swiped view at the middle and empty
        // targets to the left (since the top view relative to swiped begins a new section), except
        // For the notification header wrapper as a roundable
        val actual =
            notificationTargetsHelper()
                .findMagneticRoundableTargets(
                    viewSwiped = swiped,
                    stackScrollLayout = stackScrollLayout,
                    sectionsManager,
                    numTargets = childrenNumber - 2, // Account for roundable boundaries
                )
        val expectedTargets =
            listOf(
                MagneticRoundableTarget.Empty,
                MagneticRoundableTarget.Empty,
                MagneticRoundableTarget(children.notificationHeaderWrapper, null),
                swiped.toMagneticRoundableTarget(),
                children.attachedChildren[2].toMagneticRoundableTarget(),
                children.attachedChildren[3].toMagneticRoundableTarget(),
                children.attachedChildren[4].toMagneticRoundableTarget(useMagneticListener = false),
            )
        assertMagneticRoundableTargetsForChildren(actual, expectedTargets)
    }

    @Test
    fun findMagneticRoundableTargets_doesNotCrossSectionAtBottom() {
        val childrenNumber = 7
        val children = kosmos.createRowGroup(childrenNumber).childrenContainer

        // WHEN the fourth child is swiped and the last one begins a new section
        val swiped = children.attachedChildren[3]
        whenever(sectionsManager.beginsSection(children.attachedChildren[4], swiped)).then { true }

        // THEN the neighboring views become targets, with the swiped view at the middle and empty
        // targets to the right (since the bottom view relative to swiped begins a new section)
        val actual =
            notificationTargetsHelper()
                .findMagneticRoundableTargets(
                    viewSwiped = swiped,
                    stackScrollLayout = stackScrollLayout,
                    sectionsManager,
                    numTargets = childrenNumber - 2, // Account for roundable boundaries
                )
        val expectTargets =
            listOf(
                children.attachedChildren[0].toMagneticRoundableTarget(useMagneticListener = false),
                children.attachedChildren[1].toMagneticRoundableTarget(),
                children.attachedChildren[2].toMagneticRoundableTarget(),
                swiped.toMagneticRoundableTarget(),
                MagneticRoundableTarget.Empty,
                MagneticRoundableTarget.Empty,
                MagneticRoundableTarget.Empty,
            )
        assertMagneticRoundableTargetsForChildren(actual, expectTargets)
    }

    private fun assertMagneticRoundableTargetsForChildren(
        current: List<MagneticRoundableTarget>,
        expected: List<MagneticRoundableTarget>,
    ) {
        assertThat(current.size).isEqualTo(expected.size)
        current.zip(expected).forEach { (currentTarget, expectedTarget) ->
            assertThat(currentTarget.roundable).isEqualTo(expectedTarget.roundable)
            assertThat(currentTarget.magneticRowListener)
                .isEqualTo(expectedTarget.magneticRowListener)
        }
    }

    private fun ExpandableNotificationRow.toMagneticRoundableTarget(
        useMagneticListener: Boolean = true
    ): MagneticRoundableTarget =
        MagneticRoundableTarget(
            roundable = this,
            magneticRowListener =
                if (useMagneticListener) {
                    this.magneticRowListener
                } else {
                    null
                },
        )
}
