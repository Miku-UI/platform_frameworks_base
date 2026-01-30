/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.phone.ongoingcall.shared.model

import android.app.PendingIntent
import com.android.internal.logging.InstanceId
import com.android.systemui.activity.data.repository.activityManagerRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.addNotif
import com.android.systemui.statusbar.notification.data.repository.removeNotif
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.shared.CallType
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import com.android.systemui.statusbar.phone.ongoingcall.data.repository.ongoingCallRepository
import org.mockito.kotlin.mock

/** Helper for building [OngoingCallModel.InCall] instances in tests. */
fun inCallModel(
    startTimeMs: Long,
    notificationIcon: StatusBarIconView? = null,
    intent: PendingIntent? = null,
    notificationKey: String = "test",
    appName: String = "",
    requestedPromotion: Boolean = false,
    promotedContent: PromotedNotificationContentModels? = null,
    isAppVisible: Boolean = false,
    instanceId: InstanceId? = null,
    packageName: String = "fake.package.name",
) =
    OngoingCallModel.InCall(
        startTimeMs,
        notificationIcon,
        intent,
        notificationKey,
        appName,
        requestedPromotion,
        promotedContent,
        isAppVisible,
        instanceId,
        packageName,
    )

object OngoingCallTestHelper {
    /**
     * Removes any ongoing call state and removes any call notification associated with [key]. Does
     * it correctly based on whether [StatusBarChipsModernization] is enabled or not.
     *
     * @param key the notification key associated with the call notification.
     */
    fun Kosmos.removeOngoingCallState(key: String) {
        if (StatusBarChipsModernization.isEnabled) {
            activeNotificationListRepository.removeNotif(key)
        } else {
            ongoingCallRepository.setOngoingCallState(OngoingCallModel.NoCall)
        }
    }

    /**
     * Sets SysUI to have an ongoing call state. Does it correctly based on whether
     * [StatusBarChipsModernization] is enabled or not.
     *
     * @param key the notification key to be associated with the call notification
     */
    fun Kosmos.addOngoingCallState(
        key: String = DEFAULT_KEY,
        startTimeMs: Long = 1000L,
        statusBarChipIconView: StatusBarIconView? = createStatusBarIconViewOrNull(),
        requestedPromotion: Boolean = false,
        promotedContent: PromotedContentInput = PromotedContentInput.Default,
        contentIntent: PendingIntent? = null,
        uid: Int = DEFAULT_UID,
        appName: String = "Fake name",
        isAppVisible: Boolean = false,
        instanceId: InstanceId? = null,
        packageName: String = "fake.package.name",
    ) {
        val actualPromotedContent =
            when (promotedContent) {
                is PromotedContentInput.Default -> null
                is PromotedContentInput.OverrideToNull -> null
                is PromotedContentInput.OverrideToValue -> {
                    promotedContent.value
                }
            }
        if (StatusBarChipsModernization.isEnabled) {
            activityManagerRepository.fake.startingIsAppVisibleValue = isAppVisible
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = key,
                    whenTime = startTimeMs,
                    callType = CallType.Ongoing,
                    statusBarChipIcon = statusBarChipIconView,
                    contentIntent = contentIntent,
                    requestedPromotion = requestedPromotion,
                    promotedContent = actualPromotedContent,
                    uid = uid,
                    appName = appName,
                    instanceId = instanceId,
                    packageName = packageName,
                )
            )
        } else {
            ongoingCallRepository.setOngoingCallState(
                inCallModel(
                    startTimeMs = startTimeMs,
                    notificationIcon = statusBarChipIconView,
                    intent = contentIntent,
                    notificationKey = key,
                    appName = appName,
                    requestedPromotion = requestedPromotion,
                    promotedContent = actualPromotedContent,
                    isAppVisible = isAppVisible,
                    instanceId = instanceId,
                    packageName = packageName,
                )
            )
        }
    }

    sealed interface PromotedContentInput {
        data object Default : PromotedContentInput

        data object OverrideToNull : PromotedContentInput

        data class OverrideToValue(val value: PromotedNotificationContentModels) :
            PromotedContentInput
    }

    private fun createStatusBarIconViewOrNull(): StatusBarIconView? =
        if (StatusBarConnectedDisplays.isEnabled) {
            null
        } else {
            mock<StatusBarIconView>()
        }

    /**
     * Creates a starting point for [PromotedNotificationContentBuilder] that should be used
     * whenever a call notification is promoted.
     */
    fun callPromotedContentBuilder(key: String = DEFAULT_KEY): PromotedNotificationContentBuilder {
        return PromotedNotificationContentBuilder(key).applyToShared {
            this.style = PromotedNotificationContentModel.Style.Call
        }
    }

    private const val DEFAULT_UID = 886
    private const val DEFAULT_KEY = "notif"
}
