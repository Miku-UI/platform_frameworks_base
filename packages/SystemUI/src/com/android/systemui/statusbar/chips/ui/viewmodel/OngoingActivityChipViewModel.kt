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

package com.android.systemui.statusbar.chips.ui.viewmodel

import android.content.Context
import android.view.View
import com.android.internal.logging.InstanceId
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.core.Logger
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.notification.domain.interactor.StatusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.view.ChipBackgroundContainer
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import com.android.systemui.statusbar.notification.domain.model.TopPinnedState
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.ongoingcall.StatusBarChipsModernization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Interface for a view model that knows the display requirements for a single type of ongoing
 * activity chip.
 */
interface OngoingActivityChipViewModel {
    /** A flow modeling the chip that should be shown (or not shown). */
    val chip: StateFlow<OngoingActivityChipModel>

    companion object {
        /** Creates a chip click listener that launches a dialog created by [dialogDelegate]. */
        fun createDialogLaunchOnClickListener(
            dialogDelegateCreator: (Context) -> SystemUIDialog.Delegate,
            dialogTransitionAnimator: DialogTransitionAnimator,
            cuj: DialogCuj,
            instanceId: InstanceId,
            uiEventLogger: StatusBarChipsUiEventLogger,
            @StatusBarChipsLog logger: LogBuffer,
            key: String,
            tag: String,
        ): View.OnClickListener {
            return View.OnClickListener { view ->
                StatusBarChipsModernization.assertInLegacyMode()

                logger.log(tag, LogLevel.INFO, {}, { "Chip clicked" })
                uiEventLogger.logChipTapToShow(key, instanceId)

                val dialog = dialogDelegateCreator(view.context).createDialog()
                val launchableView =
                    view.requireViewById<ChipBackgroundContainer>(
                        R.id.ongoing_activity_chip_background
                    )
                dialogTransitionAnimator.showFromView(dialog, launchableView, cuj)
            }
        }

        /**
         * Creates a chip click callback with an [Expandable] parameter that launches a dialog
         * created by [dialogDelegate].
         */
        fun createDialogLaunchOnClickCallback(
            dialogDelegateCreator: (Context) -> SystemUIDialog.Delegate,
            dialogTransitionAnimator: DialogTransitionAnimator,
            cuj: DialogCuj,
            key: String,
            instanceId: InstanceId,
            uiEventLogger: StatusBarChipsUiEventLogger,
            @StatusBarChipsLog logger: LogBuffer,
            tag: String,
        ): (Expandable) -> Unit {
            return { expandable ->
                StatusBarChipsModernization.unsafeAssertInNewMode()

                logger.log(tag, LogLevel.INFO, {}, { "Chip clicked" })
                uiEventLogger.logChipTapToShow(key, instanceId)

                val controller = expandable.dialogTransitionController(cuj)
                val viewContext = controller?.viewRoot?.view?.context
                if (viewContext != null) {
                    val dialog = dialogDelegateCreator(viewContext).createDialog()
                    dialogTransitionAnimator.show(dialog, controller)
                }
            }
        }

        private fun createNotificationToggleClickListener(
            @Application applicationScope: CoroutineScope,
            notifChipsInteractor: StatusBarNotificationChipsInteractor,
            logger: Logger,
            notificationKey: String,
        ): () -> Unit {
            return {
                logger.i({ "Chip clicked: $str1" }) { str1 = notificationKey }
                // The notification pipeline needs everything to run on the main thread, so keep
                // this event on the main thread.
                applicationScope.launch {
                    notifChipsInteractor.onPromotedNotificationChipTapped(notificationKey)
                }
            }
        }

        /**
         * Creates a click listener that will show or hide this chip's HUN depending on the current
         * state.
         *
         * Only used if [StatusBarChipsModernization] is disabled.
         */
        fun createNotificationToggleClickListenerLegacy(
            @Application applicationScope: CoroutineScope,
            notifChipsInteractor: StatusBarNotificationChipsInteractor,
            logger: Logger,
            notificationKey: String,
        ): View.OnClickListener {
            val clickListener =
                createNotificationToggleClickListener(
                    applicationScope = applicationScope,
                    notifChipsInteractor = notifChipsInteractor,
                    logger = logger,
                    notificationKey = notificationKey,
                )
            return View.OnClickListener {
                StatusBarChipsModernization.assertInLegacyMode()
                clickListener.invoke()
            }
        }

        /**
         * Creates a click listener that will show or hide this chip's HUN depending on the current
         * state.
         *
         * Only used if [StatusBarChipsModernization] is enabled.
         */
        fun createNotificationToggleClickBehavior(
            @Application applicationScope: CoroutineScope,
            notifChipsInteractor: StatusBarNotificationChipsInteractor,
            logger: Logger,
            notificationKey: String,
            isShowingHeadsUpFromChipTap: Boolean,
        ): OngoingActivityChipModel.ClickBehavior {
            val clickListener =
                createNotificationToggleClickListener(
                    applicationScope = applicationScope,
                    notifChipsInteractor = notifChipsInteractor,
                    logger = logger,
                    notificationKey = notificationKey,
                )
            // Using the correct model here ensures that our custom content descriptions in
            // [OngoingActivityChip] work correctly.
            return if (isShowingHeadsUpFromChipTap) {
                OngoingActivityChipModel.ClickBehavior.HideHeadsUpNotification {
                    /* check if */ StatusBarChipsModernization.isUnexpectedlyInLegacyMode()
                    clickListener.invoke()
                }
            } else {
                OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification {
                    /* check if */ StatusBarChipsModernization.isUnexpectedlyInLegacyMode()
                    clickListener.invoke()
                }
            }
        }

        /**
         * Returns true if this [TopPinnedState] means that the notification with the given key is
         * currently pinned because the user tapped the status bar chip for it.
         */
        fun TopPinnedState.isShowingHeadsUpFromChipTap(notificationKey: String): Boolean {
            return this is TopPinnedState.Pinned &&
                this.status == PinnedStatus.PinnedByUser &&
                this.key == notificationKey
        }
    }
}
