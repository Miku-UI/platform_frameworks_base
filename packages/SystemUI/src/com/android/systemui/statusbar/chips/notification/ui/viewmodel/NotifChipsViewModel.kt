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

package com.android.systemui.statusbar.chips.notification.ui.viewmodel

import android.content.Context
import com.android.internal.logging.InstanceId
import com.android.systemui.Flags
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.notification.domain.interactor.StatusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.notification.domain.model.NotificationChipModel
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel.Companion.createNotificationToggleClickBehavior
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel.Companion.createNotificationToggleClickListenerLegacy
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel.Companion.isShowingHeadsUpFromChipTap
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.statusbar.notification.domain.model.TopPinnedState
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A view model for status bar chips for promoted ongoing notifications. */
@SysUISingleton
class NotifChipsViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    private val notifChipsInteractor: StatusBarNotificationChipsInteractor,
    headsUpNotificationInteractor: HeadsUpNotificationInteractor,
    private val systemClock: SystemClock,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "NotifChipVM".pad())

    /**
     * A flow that prunes the incoming [NotificationChipModel] instances to just the information
     * each status bar chip needs.
     */
    private val notificationChipsWithPrunedContent: Flow<List<PrunedNotificationChipModel>> =
        notifChipsInteractor.allNotificationChips
            .map { chips -> chips.filterByPackage().map { it.toPrunedModel() } }
            .distinctUntilChanged()

    /**
     * Filters all the chips down to just the most important chip per package so we don't show
     * multiple chips for the same app.
     */
    private fun List<NotificationChipModel>.filterByPackage(): List<NotificationChipModel> {
        return this.groupBy { it.packageName }.map { (_, chips) -> chips[0] }
    }

    private fun NotificationChipModel.toPrunedModel(): PrunedNotificationChipModel {
        // Chips are never shown when locked, so it's safe to use the version with sensitive content
        val content = promotedContent.privateVersion

        val time =
            when (val rawTime = content.time) {
                null -> null
                is PromotedNotificationContentModel.When.Time -> {
                    if (
                        rawTime.currentTimeMillis >=
                            systemClock.currentTimeMillis() + FUTURE_TIME_THRESHOLD_MILLIS
                    ) {
                        rawTime
                    } else {
                        // Don't show a `when` time that's close to now or in the past because it's
                        // likely that the app didn't intentionally set the `when` time to be shown
                        // in the status bar chip.
                        // TODO(b/393369213): If a notification sets a `when` time in the future and
                        // then that time comes and goes, the chip *will* start showing times in the
                        // past. Not going to fix this right now because the Compose implementation
                        // automatically handles this for us and we're hoping to launch the
                        // notification chips at the same time as the Compose chips.
                        null
                    }
                }
                is PromotedNotificationContentModel.When.Chronometer -> rawTime
            }
        return PrunedNotificationChipModel(
            key = key,
            packageName = packageName,
            appName = appName,
            statusBarChipIconView = statusBarChipIconView,
            text = content.shortCriticalText,
            time = time,
            wasPromotedAutomatically = content.wasPromotedAutomatically,
            isAppVisible = isAppVisible,
            instanceId = instanceId,
        )
    }

    /**
     * A flow modeling the current notification chips. Emits an empty list if there are no
     * notifications that are eligible to show a status bar chip.
     */
    val chips: Flow<List<OngoingActivityChipModel.Active>> =
        combine(
                notificationChipsWithPrunedContent,
                headsUpNotificationInteractor.statusBarHeadsUpState,
            ) { notifications, headsUpState ->
                notifications.map { it.toActivityChipModel(headsUpState) }
            }
            .distinctUntilChanged()

    /** Converts the notification to the [OngoingActivityChipModel] object. */
    private fun PrunedNotificationChipModel.toActivityChipModel(
        headsUpState: TopPinnedState
    ): OngoingActivityChipModel.Active {
        PromotedNotificationUi.unsafeAssertInNewMode()

        val contentDescription = getContentDescription(this.appName)
        val icon =
            if (this.statusBarChipIconView != null) {
                StatusBarConnectedDisplays.assertInLegacyMode()
                OngoingActivityChipModel.ChipIcon.StatusBarView(
                    this.statusBarChipIconView,
                    contentDescription,
                )
            } else {
                StatusBarConnectedDisplays.unsafeAssertInNewMode()
                OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon(
                    this.key,
                    contentDescription,
                )
            }
        val colors = ColorsModel.SystemThemed
        // If the app that posted this notification is visible, we want to hide the chip
        // because information between the status bar chip and the app itself could be
        // out-of-sync (like a timer that's slightly off)
        val isHidden = this.isAppVisible

        val isShowingHeadsUpFromChipTap = headsUpState.isShowingHeadsUpFromChipTap(this.key)
        val onClickListenerLegacy =
            createNotificationToggleClickListenerLegacy(
                applicationScope = applicationScope,
                notifChipsInteractor = notifChipsInteractor,
                logger = logger,
                notificationKey = this.key,
            )
        val clickBehavior =
            createNotificationToggleClickBehavior(
                applicationScope = applicationScope,
                notifChipsInteractor = notifChipsInteractor,
                logger = logger,
                notificationKey = this.key,
                isShowingHeadsUpFromChipTap = isShowingHeadsUpFromChipTap,
            )

        val content: OngoingActivityChipModel.Content =
            when {
                isShowingHeadsUpFromChipTap -> {
                    // If the user tapped this chip to show the HUN, we want to just show the icon
                    // because the HUN will show the rest of the information.
                    // Similar behavior to [CallChipViewModel].
                    OngoingActivityChipModel.Content.IconOnly
                }
                text != null -> OngoingActivityChipModel.Content.Text(text = text)
                Flags.promoteNotificationsAutomatically() && wasPromotedAutomatically -> {
                    // When we're promoting notifications automatically, the `when` time set on the
                    // notification will likely just be set to the current time, which would cause
                    // the chip to always show "now". We don't want early testers to get that
                    // experience since it's not what will happen at launch, so just don't show any
                    // time.
                    OngoingActivityChipModel.Content.IconOnly
                }
                else -> {
                    when (time) {
                        null -> OngoingActivityChipModel.Content.IconOnly
                        is PromotedNotificationContentModel.When.Time -> {
                            OngoingActivityChipModel.Content.ShortTimeDelta(
                                time = time.currentTimeMillis
                            )
                        }
                        is PromotedNotificationContentModel.When.Chronometer -> {
                            OngoingActivityChipModel.Content.Timer(
                                startTimeMs = time.elapsedRealtimeMillis,
                                isEventInFuture = time.isCountDown,
                            )
                        }
                    }
                }
            }

        return OngoingActivityChipModel.Active(
            key = key,
            managingPackageName = packageName,
            isImportantForPrivacy = false,
            icon = icon,
            content = content,
            colors = colors,
            onClickListenerLegacy = onClickListenerLegacy,
            clickBehavior = clickBehavior,
            isHidden = isHidden,
            instanceId = instanceId,
        )
    }

    private fun getContentDescription(appName: String): ContentDescription {
        val ongoingDescription =
            context.getString(R.string.ongoing_notification_extra_content_description)
        return ContentDescription.Loaded(
            context.getString(
                R.string.accessibility_desc_notification_icon,
                appName,
                ongoingDescription,
            )
        )
    }

    /**
     * Model that prunes data from [NotificationChipModel] to just the information the status bar
     * chip needs.
     *
     * Used so that we don't re-create the chip [OngoingActivityChipModel] classes with new click
     * listeners unless absolutely necessary, which helps the chips re-compose less frequently. See
     * b/393456147.
     */
    private data class PrunedNotificationChipModel(
        val key: String,
        val packageName: String,
        val appName: String,
        val statusBarChipIconView: StatusBarIconView?,
        /**
         * The text to show in the chip, or null if text shouldn't be shown. Text takes precedence
         * over [time].
         */
        val text: String?,
        /** The time to show in the chip, or null if the time shouldn't be shown. */
        val time: PromotedNotificationContentModel.When?,
        /** See [PromotedNotificationContentModel.wasPromotedAutomatically]. */
        val wasPromotedAutomatically: Boolean,
        val isAppVisible: Boolean,
        val instanceId: InstanceId?,
    )

    companion object {
        /**
         * Notifications must have a `when` time of at least 1 minute in the future in order for the
         * status bar chip to show the time.
         */
        private const val FUTURE_TIME_THRESHOLD_MILLIS = 60 * 1000
    }
}
