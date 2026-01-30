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

package com.android.systemui.statusbar.notification.collection

import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.systemui.statusbar.notification.NotificationActivityStarter
import com.android.systemui.statusbar.notification.collection.coordinator.BundleCoordinator.Companion.debugBundleAppName
import com.android.systemui.statusbar.notification.collection.coordinator.VisualStabilityCoordinator
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender
import com.android.systemui.statusbar.notification.collection.provider.HighPriorityProvider
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.icon.IconPack
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotifBindPipeline
import com.android.systemui.statusbar.notification.row.NotificationActionClickManager
import com.android.systemui.statusbar.notification.row.OnUserInteractionCallback
import com.android.systemui.statusbar.notification.row.RowContentBindParams
import com.android.systemui.statusbar.notification.row.RowContentBindStage
import kotlinx.coroutines.flow.StateFlow

class NotificationEntryAdapter(
    private val notificationActivityStarter: NotificationActivityStarter,
    private val peopleNotificationIdentifier: PeopleNotificationIdentifier,
    private val visualStabilityCoordinator: VisualStabilityCoordinator,
    private val notificationActionClickManager: NotificationActionClickManager,
    private val highPriorityProvider: HighPriorityProvider,
    private val headsUpManager: HeadsUpManager,
    private val onUserInteractionCallback: OnUserInteractionCallback,
    private val entry: NotificationEntry,
    private val notifPipeline: NotifPipeline,
) : EntryAdapter {
    override fun getBackingHashCode(): Int {
        return entry.hashCode()
    }

    override fun getParent(): PipelineEntry? {
        return entry.parent
    }

    override fun isTopLevelEntry(): Boolean {
        return parent != null && (parent === GroupEntry.ROOT_ENTRY || parent is BundleEntry)
    }

    override fun getKey(): String {
        return entry.key
    }

    override fun getRow(): ExpandableNotificationRow {
        return entry.row
    }

    override fun isGroupRoot(): Boolean {
        if (isTopLevelEntry || parent == null) {
            return false
        }
        return (entry.parent as? GroupEntry)?.summary == entry
    }

    override fun isSensitive(): StateFlow<Boolean> {
        return entry.isSensitive
    }

    override fun isClearable(): Boolean {
        return entry.isClearable
    }

    override fun getTargetSdk(): Int {
        return entry.targetSdk
    }

    override fun getSummarization(): String? {
        return entry.ranking?.summarization
    }

    override fun prepareForInflation() {
        entry.sbn.clearPackageContext()
    }

    override fun getContrastedColor(
        context: Context?,
        isLowPriority: Boolean,
        backgroundColor: Int,
    ): Int {
        return entry.getContrastedColor(context, isLowPriority, backgroundColor)
    }

    override fun canPeek(): Boolean {
        return entry.isStickyAndNotDemoted
    }

    override fun getWhen(): Long {
        return entry.sbn.notification.getWhen()
    }

    override fun getIcons(): IconPack {
        return entry.icons
    }

    override fun isColorized(): Boolean {
        return entry.sbn?.notification?.isColorized ?: false
    }

    override fun getSbn(): StatusBarNotification {
        return entry.sbn
    }

    override fun getRanking(): NotificationListenerService.Ranking? {
        return entry.ranking
    }

    override fun endLifetimeExtension(
        callback: NotifLifetimeExtender.OnEndLifetimeExtensionCallback?,
        extender: NotifLifetimeExtender,
    ) {
        callback?.onEndLifetimeExtension(extender, entry)
    }

    override fun onImportanceChanged() {
        visualStabilityCoordinator.temporarilyAllowSectionChanges(entry, SystemClock.uptimeMillis())
    }

    override fun markForUserTriggeredMovement(marked: Boolean) {
        entry.markForUserTriggeredMovement(marked)
    }

    override fun isMarkedForUserTriggeredMovement(): Boolean {
        return entry.isMarkedForUserTriggeredMovement
    }

    override fun isHighPriority(): Boolean {
        return highPriorityProvider.isHighPriority(entry)
    }

    override fun setInlineControlsShown(currentlyVisible: Boolean) {
        headsUpManager.setGutsShown(entry, currentlyVisible)
    }

    override fun isBlockable(): Boolean {
        return entry.isBlockable
    }

    override fun canDragAndDrop(): Boolean {
        val canBubble: Boolean = entry.canBubble()
        val notif = entry.sbn.notification
        val dragIntent =
            if (notif.contentIntent != null) notif.contentIntent else notif.fullScreenIntent
        if (dragIntent != null && dragIntent.isActivity && !canBubble) {
            return true
        }
        return false
    }

    override fun isBubble(): Boolean {
        return entry.isBubble
    }

    override fun getStyle(): String? {
        return entry.notificationStyle
    }

    override fun getSectionBucket(): Int {
        return entry.bucket
    }

    override fun isAmbient(): Boolean {
        return entry.ranking.isAmbient
    }

    override fun getPeopleNotificationType(): Int {
        return peopleNotificationIdentifier.getPeopleNotificationType(entry)
    }

    override fun isPromotedOngoing(): Boolean {
        return entry.isPromotedOngoing
    }

    override fun isFullScreenCapable(): Boolean {
        return entry.sbn.notification.fullScreenIntent != null
    }

    override fun onDragSuccess() {
        notificationActivityStarter.onDragSuccess(entry)
    }

    override fun onNotificationBubbleIconClicked() {
        notificationActivityStarter.onNotificationBubbleIconClicked(entry)
    }

    override fun onNotificationActionClicked() {
        notificationActionClickManager.onNotificationActionClicked(entry)
    }

    override fun isParentDismissed(): Boolean {
        return entry.dismissState == NotificationEntry.DismissState.PARENT_DISMISSED
    }

    override fun onEntryClicked(row: ExpandableNotificationRow) {
        notificationActivityStarter.onNotificationClicked(entry, row)
    }

    override fun getRemoteInputEntryAdapter(): RemoteInputEntryAdapter {
        return entry.getRemoteInputEntryAdapter()
    }

    override fun addOnSensitivityChangedListener(
        listener: PipelineEntry.OnSensitivityChangedListener
    ) {
        entry.addOnSensitivityChangedListener(listener)
    }

    override fun removeOnSensitivityChangedListener(
        listener: PipelineEntry.OnSensitivityChangedListener
    ) {
        entry.removeOnSensitivityChangedListener(listener)
    }

    override fun setSeenInShade(seen: Boolean) {
        entry.isSeenInShade = seen
    }

    override fun isSeenInShade(): Boolean {
        return entry.isSeenInShade
    }

    override fun onEntryAnimatingAwayEnded() {
        headsUpManager.onEntryAnimatingAwayEnded(entry)
    }

    override fun registerFutureDismissal(): Runnable {
        return onUserInteractionCallback.registerFutureDismissal(
            entry,
            NotificationListenerService.REASON_CANCEL,
        )
    }

    override fun markForReinflation(stage: RowContentBindStage) {
        val params: RowContentBindParams = stage.getStageParams(entry)
        params.setNeedsReinflation(true)
    }

    override fun isViewBacked(): Boolean {
        return true
    }

    override fun requestRebind(
        stage: RowContentBindStage,
        callback: NotifBindPipeline.BindCallback,
    ) {
        stage.requestRebind(entry, callback)
    }

    override fun isBundled(): Boolean {
        return entry.isBundled || entry.isDebugBundled
    }

    override fun isBundle(): Boolean {
        return false
    }

    override fun onBundleDisabledForEntry() {
        visualStabilityCoordinator.temporarilyAllowFreeMovement(entry, SystemClock.uptimeMillis())
        if (isGroupRoot()) {
            row.attachedChildren?.forEach { it.entryAdapter.onBundleDisabledForEntry() }
        }
    }

    override fun onBundleDisabledForApp() {
        val now = SystemClock.uptimeMillis()
        for (notif in notifPipeline.allNotifs) {
            if (
                notif.sbn.packageName == entry.sbn.packageName &&
                    notif.sbn.user == entry.sbn.user &&
                    (notif.isBundled || notif.isDebugBundled)
            ) {
                visualStabilityCoordinator.temporarilyAllowFreeMovement(notif, now)
            }
        }
    }

    override fun getBundleType(): Int {
        Log.wtf(TAG, "getBundleType() called on non-bundle entry")
        return -1
    }
}

private val NotificationEntry.isDebugBundled: Boolean
    get() = !debugBundleAppName.isNullOrEmpty() && hasBundleParent

private val NotificationEntry.hasBundleParent: Boolean
    get() {
        var parent: PipelineEntry? = parent
        while (parent != null) {
            if (parent is BundleEntry) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

private const val TAG = "NotifEntryAdapter"
