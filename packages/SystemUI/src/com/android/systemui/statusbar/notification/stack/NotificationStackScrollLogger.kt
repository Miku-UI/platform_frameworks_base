package com.android.systemui.statusbar.notification.stack

import android.view.ViewGroup
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel.DEBUG
import com.android.systemui.log.core.LogLevel.ERROR
import com.android.systemui.log.core.LogLevel.INFO
import com.android.systemui.log.dagger.NotificationHeadsUpLog
import com.android.systemui.log.dagger.NotificationRenderLog
import com.android.systemui.log.dagger.ShadeLog
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_ADD
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_APPEAR
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_DISAPPEAR
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_DISAPPEAR_CLICK
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout.AnimationEvent.ANIMATION_TYPE_HEADS_UP_OTHER
import com.google.errorprone.annotations.CompileTimeConstant
import javax.inject.Inject

class NotificationStackScrollLogger
@Inject
constructor(
    @NotificationHeadsUpLog private val buffer: LogBuffer,
    @NotificationRenderLog private val notificationRenderBuffer: LogBuffer,
    @ShadeLog private val shadeLogBuffer: LogBuffer,
) {
    fun hunAnimationSkipped(entry: String, reason: String) {
        buffer.log(
            TAG,
            INFO,
            {
                str1 = entry
                str2 = reason
            },
            { "heads up animation skipped: key: $str1 reason: $str2" },
        )
    }

    fun hunAnimationEventAdded(entry: String, type: Int) {
        val reason: String
        reason =
            if (type == ANIMATION_TYPE_HEADS_UP_DISAPPEAR) {
                "HEADS_UP_DISAPPEAR"
            } else if (type == ANIMATION_TYPE_HEADS_UP_DISAPPEAR_CLICK) {
                "HEADS_UP_DISAPPEAR_CLICK"
            } else if (type == ANIMATION_TYPE_HEADS_UP_APPEAR) {
                "HEADS_UP_APPEAR"
            } else if (type == ANIMATION_TYPE_HEADS_UP_OTHER) {
                "HEADS_UP_OTHER"
            } else if (type == ANIMATION_TYPE_ADD) {
                "ADD"
            } else {
                type.toString()
            }
        buffer.log(
            TAG,
            INFO,
            {
                str1 = entry
                str2 = reason
            },
            { "heads up animation added: $str1 with type $str2" },
        )
    }

    fun hunSkippedForUnexpectedState(entry: String, expected: Boolean, actual: Boolean) {
        buffer.log(
            TAG,
            INFO,
            {
                str1 = entry
                bool1 = expected
                bool2 = actual
            },
            {
                "HUN animation skipped for unexpected hun state: " +
                    "key: $str1 expected: $bool1 actual: $bool2"
            },
        )
    }

    fun logShadeDebugEvent(@CompileTimeConstant msg: String) = shadeLogBuffer.log(TAG, DEBUG, msg)

    fun logEmptySpaceClick(
        isBelowLastNotification: Boolean,
        statusBarState: Int,
        touchIsClick: Boolean,
        motionEventDesc: String,
    ) {
        shadeLogBuffer.log(
            TAG,
            DEBUG,
            {
                int1 = statusBarState
                bool1 = touchIsClick
                bool2 = isBelowLastNotification
                str1 = motionEventDesc
            },
            {
                "handleEmptySpaceClick: statusBarState: $int1 isTouchAClick: $bool1 " +
                    "isTouchBelowNotification: $bool2 motionEvent: $str1"
            },
        )
    }

    fun transientNotificationRowTraversalCleaned(entry: String, reason: String) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = entry
                str2 = reason
            },
            { "transientNotificationRowTraversalCleaned: key: $str1 reason: $str2" },
        )
    }

    fun addTransientChildNotificationToChildContainer(childEntry: String, containerEntry: String) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = childEntry
                str2 = containerEntry
            },
            {
                "addTransientChildToContainer from onViewRemovedInternal: childKey: $str1 " +
                    "-- containerKey: $str2"
            },
        )
    }

    fun addTransientChildNotificationToNssl(childEntry: String) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            { str1 = childEntry },
            { "addTransientRowToNssl from onViewRemovedInternal: childKey: $str1" },
        )
    }

    fun addTransientChildNotificationToViewGroup(childEntry: String, container: ViewGroup) {
        notificationRenderBuffer.log(
            TAG,
            ERROR,
            {
                str1 = childEntry
                str2 = container.toString()
            },
            {
                "addTransientRowTo unhandled ViewGroup from onViewRemovedInternal: childKey: $str1 " +
                    "-- ViewGroup: $str2"
            },
        )
    }

    fun addTransientRow(childEntry: String, index: Int) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = childEntry
                int1 = index
            },
            { "addTransientRow to NSSL: childKey: $str1 -- index: $int1" },
        )
    }

    fun removeTransientRow(childEntry: String) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            { str1 = childEntry },
            { "removeTransientRow from NSSL: childKey: $str1" },
        )
    }

    fun logUpdateSensitivenessWithAnimation(
        shouldAnimate: Boolean,
        isSensitive: Boolean,
        isSensitiveContentProtectionActive: Boolean,
        isAnyProfilePublic: Boolean,
    ) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                bool1 = shouldAnimate
                bool2 = isSensitive
                bool3 = isSensitiveContentProtectionActive
                bool4 = isAnyProfilePublic
            },
            {
                "updateSensitivenessWithAnimation from NSSL: shouldAnimate=$bool1 " +
                    "isSensitive(hideSensitive)=$bool2 isSensitiveContentProtectionActive=$bool3 " +
                    "isAnyProfilePublic=$bool4"
            },
        )
    }

    fun logUpdateSensitivenessWithAnimation(animate: Boolean, anyProfilePublicMode: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                bool1 = animate
                bool2 = anyProfilePublicMode
            },
            {
                "updateSensitivenessWithAnimation from NSSL: animate=$bool1 " +
                    "anyProfilePublicMode(hideSensitive)=$bool2"
            },
        )
    }

    fun childHeightUpdated(row: ExpandableNotificationRow, needsAnimation: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = row.key
                bool1 = needsAnimation
            },
            { "childHeightUpdated: childKey: $str1 -- needsAnimation: $bool1" },
        )
    }

    fun setMaxDisplayedNotifications(maxDisplayedNotifications: Int) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            { int1 = maxDisplayedNotifications },
            { "setMaxDisplayedNotifications: $int1" },
        )
    }

    fun logAddSwipedOutView(loggingKey: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                bool1 = clearAllInProgress
            },
            { "addSwipedOutView from NSSL: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logRemoveSwipedOutView(loggingKey: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                bool1 = clearAllInProgress
            },
            { "removeSwipedOutView from NSSL: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logOnChildDismissed(loggingKey: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                bool1 = clearAllInProgress
            },
            { "onChildDismissed from NSSL: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logOnSwipeBegin(loggingKey: String, reason: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                str2 = reason
                bool1 = clearAllInProgress
            },
            { "onSwipeBegin from $str2: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logOnSwipeEnd(loggingKey: String, reason: String, clearAllInProgress: Boolean) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                str2 = reason
                bool1 = clearAllInProgress
            },
            { "onSwipeEnd from $str2: childKey = $str1 -- clearAllInProgress:$bool1" },
        )
    }

    fun logOnChildNotDismissed(
        loggingKey: String,
        animationCancelled: Boolean,
        viewWasRemoved: Boolean,
    ) {
        notificationRenderBuffer.log(
            TAG,
            INFO,
            {
                str1 = loggingKey
                bool1 = animationCancelled
                bool2 = viewWasRemoved
            },
            {
                "onChildNotDismissed (ERROR) childKey = $str1 " +
                    "-- animationCancelled:$bool1 -- viewWasRemoved:$bool2"
            },
        )
    }
}

private const val TAG = "NotificationStackScroll"
