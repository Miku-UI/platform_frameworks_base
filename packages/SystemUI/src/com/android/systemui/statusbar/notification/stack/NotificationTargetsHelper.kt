package com.android.systemui.statusbar.notification.stack

import androidx.core.view.children
import androidx.core.view.isVisible
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.NotificationShelf
import com.android.systemui.statusbar.notification.Roundable
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.ExpandableView
import javax.inject.Inject

interface NotificationTargetsHelper {

    /**
     * This method looks for views that can be rounded (and implement [Roundable]) during a
     * notification swipe.
     *
     * @return The [Roundable] targets above/below the [viewSwiped] (if available). The
     *   [RoundableTargets.before] and [RoundableTargets.after] parameters can be `null` if there is
     *   no above/below notification or the notification is not part of the same section.
     */
    fun findRoundableTargets(
        viewSwiped: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
    ): RoundableTargets

    /**
     * This method looks for [MagneticRoundableTarget]s that can magnetically attach to a swiped
     * [ExpandableNotificationRow].
     *
     * The [MagneticRoundableTarget] for the swiped row is at the center of the list. From the
     * center towards the left, the list contains the closest notification targets above the swiped
     * row. From the center towards the right, the list contains the closest targets below the row.
     *
     * The list is filled from the center outwards, stopping at the first target that is not a valid
     * [MagneticRoundableTarget] (see [ExpandableView.isValidMagneticTargetForSwiped]). Positions
     * where the list halted could also be added if the target is a valid boundary (see
     * [ExpandableView.isValidMagneticBoundary]). If neither condition is met, the position is
     * filled with an empty [MagneticRoundableTarget]. In addition to the [numTargets] required, the
     * list contains two additional targets used as [Roundable] boundaries that are not affected by
     * a magnetic swipe. These will be returned at the beginning and end of the list.
     *
     * @param[viewSwiped] The [ExpandableNotificationRow] that is swiped.
     * @param[stackScrollLayout] [NotificationStackScrollLayout] container.
     * @param[sectionsManager] The [NotificationSectionsManager].
     * @param[numTargets] The number of targets in the resulting list, including the swiped view.
     * @return The list of [MagneticRoundableTarget]s above and below the swiped
     *   [ExpandableNotificationRow]. The list includes two [Roundable] targets as boundaries of the
     *   list. They are position at the beginning and end of the list.
     */
    fun findMagneticRoundableTargets(
        viewSwiped: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
        numTargets: Int,
    ): List<MagneticRoundableTarget>
}

/**
 * Utility class that helps us find the targets of an animation, often used to find the notification
 * ([Roundable]) above and below the current one (see [findRoundableTargets]).
 */
@SysUISingleton
class NotificationTargetsHelperImpl @Inject constructor() : NotificationTargetsHelper {

    override fun findRoundableTargets(
        viewSwiped: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
    ): RoundableTargets {
        val viewBefore: Roundable?
        val viewAfter: Roundable?

        val notificationParent = viewSwiped.notificationParent
        val childrenContainer = notificationParent?.childrenContainer
        val visibleStackChildren =
            stackScrollLayout.children
                .filterIsInstance<ExpandableView>()
                .filter { it.isVisible }
                .toList()

        if (notificationParent != null && childrenContainer != null) {
            // We are inside a notification group
            val visibleGroupChildren = childrenContainer.attachedChildren.filter { it.isVisible }
            val indexOfParentSwipedView = visibleGroupChildren.indexOf(viewSwiped)

            viewBefore =
                visibleGroupChildren.getOrNull(indexOfParentSwipedView - 1)
                    ?: childrenContainer.roundableHeaderWrapper

            viewAfter =
                visibleGroupChildren.getOrNull(indexOfParentSwipedView + 1)
                    ?: visibleStackChildren.indexOf(notificationParent).let {
                        visibleStackChildren.getOrNull(it + 1)
                    }
        } else {
            // Assumption: we are inside the NotificationStackScrollLayout

            val indexOfSwipedView = visibleStackChildren.indexOf(viewSwiped)

            viewBefore =
                visibleStackChildren.getOrNull(indexOfSwipedView - 1)?.takeIf {
                    !sectionsManager.beginsSection(viewSwiped, it)
                }

            viewAfter =
                visibleStackChildren.getOrNull(indexOfSwipedView + 1)?.takeIf {
                    !sectionsManager.beginsSection(it, viewSwiped)
                }
        }

        return RoundableTargets(before = viewBefore, swiped = viewSwiped, after = viewAfter)
    }

    override fun findMagneticRoundableTargets(
        viewSwiped: ExpandableNotificationRow,
        stackScrollLayout: NotificationStackScrollLayout,
        sectionsManager: NotificationSectionsManager,
        numTargets: Int,
    ): List<MagneticRoundableTarget> {
        val notificationParent = viewSwiped.notificationParent
        val childrenContainer = notificationParent?.childrenContainer
        val visibleStackChildren =
            stackScrollLayout.children
                .filterIsInstance<ExpandableView>()
                .filter { it.isVisible }
                .toList()

        var notificationHeaderWrapper: Roundable? = null
        val container: List<ExpandableView>
        if (notificationParent != null && childrenContainer != null) {
            // We are inside a notification group
            notificationHeaderWrapper = childrenContainer.roundableHeaderWrapper
            container = childrenContainer.attachedChildren.filter { it.isVisible }
        } else {
            container = visibleStackChildren
        }

        // Add two targets more to use as roundable boundaries. These will be at the beginning and
        // end of the list
        val totalTargets = numTargets + 2

        val targets = MutableList(totalTargets) { MagneticRoundableTarget.Empty }
        targets[totalTargets / 2] = viewSwiped.toMagneticRoundableTarget()

        // Fill the list outwards from the center
        val centerIndex = container.indexOf(viewSwiped)
        var leftIndex = totalTargets / 2 - 1
        var rightIndex = totalTargets / 2 + 1
        var canMoveLeft = true
        var canMoveRight = true
        for (distance in 1..totalTargets / 2) {
            if (canMoveLeft) {
                val leftElement = container.getOrNull(index = centerIndex - distance)
                val isValid =
                    leftElement?.isValidMagneticTargetForSwiped(
                        viewSwiped,
                        leftOfSwiped = true,
                        sectionsManager,
                    ) ?: false
                if (leftElement != null && isValid) {
                    targets[leftIndex] =
                        leftElement.toMagneticRoundableTarget(
                            // Don't include the magnetic listener for a roundable boundary
                            withMagneticRowListener = leftIndex > 0
                        )
                    leftIndex--
                } else {
                    // If the element is still a valid boundary, add it and stop iterating
                    if (leftElement != null && leftElement.isValidMagneticBoundary()) {
                        targets[leftIndex] =
                            leftElement.toMagneticRoundableTarget(
                                // Don't include the magnetic listener for a roundable boundary
                                withMagneticRowListener = leftIndex > 0
                            )
                    }
                    canMoveLeft = false
                }
            }
            if (canMoveRight) {
                val rightElement = container.getOrNull(index = centerIndex + distance)
                val isValid =
                    rightElement?.isValidMagneticTargetForSwiped(
                        viewSwiped,
                        leftOfSwiped = false,
                        sectionsManager,
                    ) ?: false
                if (rightElement != null && isValid) {
                    targets[rightIndex] =
                        rightElement.toMagneticRoundableTarget(
                            // Don't include the magnetic listener for a roundable boundary
                            withMagneticRowListener = rightIndex < targets.size - 1
                        )
                    rightIndex++
                } else {
                    // If the element is still a valid boundary, add it and stop iterating
                    if (rightElement != null && rightElement.isValidMagneticBoundary()) {
                        targets[rightIndex] =
                            rightElement.toMagneticRoundableTarget(
                                // Don't include the magnetic listener for a roundable boundary
                                withMagneticRowListener = rightIndex < targets.size - 1
                            )
                    }
                    canMoveRight = false
                }
            }
        }

        if (notificationHeaderWrapper != null) {
            // From the center towards the left, we need to add the header roundable to the first
            // non-empty target
            var i = totalTargets / 2
            while (i >= 0) {
                if (targets[i] == MagneticRoundableTarget.Empty) {
                    targets[i] =
                        MagneticRoundableTarget(
                            roundable = notificationHeaderWrapper,
                            magneticRowListener = null,
                        )
                    break
                } else {
                    i--
                }
            }
        }
        return targets
    }

    private fun ExpandableView.toMagneticRoundableTarget(withMagneticRowListener: Boolean = true) =
        MagneticRoundableTarget(
            roundable = this,
            magneticRowListener =
                if (withMagneticRowListener) {
                    magneticRowListener
                } else {
                    null
                },
        )

    private fun ExpandableView?.isValidMagneticBoundary(): Boolean = this is NotificationShelf

    private fun ExpandableView.isValidMagneticTargetForSwiped(
        viewSwiped: ExpandableView,
        leftOfSwiped: Boolean,
        sectionsManager: NotificationSectionsManager,
    ): Boolean {
        val beginsSection = sectionsManager.beginsSectionFromSwiped(this, viewSwiped, leftOfSwiped)
        return this is ExpandableNotificationRow && !beginsSection
    }

    private fun NotificationSectionsManager.beginsSectionFromSwiped(
        view: ExpandableView,
        viewSwiped: ExpandableView,
        leftOfSwiped: Boolean,
    ): Boolean =
        if (leftOfSwiped) {
            beginsSection(viewSwiped, previous = view)
        } else {
            beginsSection(view, previous = viewSwiped)
        }
}

/**
 * This object contains targets above/below the [swiped] (if available). The [before] and [after]
 * parameters can be `null` if there is no above/below notification or the notification is not part
 * of the same section.
 */
data class RoundableTargets(
    val before: Roundable?,
    val swiped: ExpandableNotificationRow?,
    val after: Roundable?,
)

/**
 * This object encapsulates a [Roundable] and an associated [MagneticRowListener] that are
 * manipulated during magnetic interactions. There could be instances in which a target includes a
 * [Roundable] but not a [MagneticRowListener], such as when a notification is used as a boundary of
 * the magnetic zone. The boundaries are rounded but are not affected by magnetic pulls.
 */
data class MagneticRoundableTarget(
    val roundable: Roundable?,
    val magneticRowListener: MagneticRowListener?,
) {
    companion object {
        val Empty = MagneticRoundableTarget(null, null)
    }
}
