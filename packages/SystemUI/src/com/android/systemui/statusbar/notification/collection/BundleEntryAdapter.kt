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

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifLifetimeExtender
import com.android.systemui.statusbar.notification.collection.provider.HighPriorityProvider
import com.android.systemui.statusbar.notification.icon.IconPack
import com.android.systemui.statusbar.notification.people.PeopleNotificationIdentifier.Companion.TYPE_NON_PERSON
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotifBindPipeline
import com.android.systemui.statusbar.notification.row.OnUserInteractionCallback
import com.android.systemui.statusbar.notification.row.RowContentBindStage
import kotlinx.coroutines.flow.StateFlow

class BundleEntryAdapter(
    private val highPriorityProvider: HighPriorityProvider,
    private val onUserInteractionCallback: OnUserInteractionCallback,
    val entry: BundleEntry,
) : EntryAdapter {

    override fun getBackingHashCode(): Int {
        return entry.hashCode()
    }

    /** TODO (b/394483200): convert to PipelineEntry.ROOT_ENTRY when pipeline is migrated? */
    override fun getParent(): GroupEntry {
        return GroupEntry.ROOT_ENTRY
    }

    override fun isTopLevelEntry(): Boolean {
        return true
    }

    override fun getKey(): String {
        return entry.key
    }

    override fun getRow(): ExpandableNotificationRow? {
        return entry.row
    }

    override fun isGroupRoot(): Boolean {
        return true
    }

    override fun isSensitive(): StateFlow<Boolean> {
        return entry.isSensitive
    }

    override fun isClearable(): Boolean {
        return entry.isClearable
    }

    override fun getTargetSdk(): Int {
        return Build.VERSION_CODES.CUR_DEVELOPMENT
    }

    override fun getSummarization(): String? {
        return null
    }

    override fun getContrastedColor(
        context: Context?,
        isLowPriority: Boolean,
        backgroundColor: Int,
    ): Int {
        return Notification.COLOR_DEFAULT
    }

    override fun canPeek(): Boolean {
        return false
    }

    override fun getWhen(): Long {
        return 0
    }

    override fun getIcons(): IconPack {
        return entry.icons
    }

    override fun isColorized(): Boolean {
        return false
    }

    override fun getSbn(): StatusBarNotification? {
        return null
    }

    override fun getRanking(): NotificationListenerService.Ranking? {
        return null
    }

    override fun endLifetimeExtension(
        callback: NotifLifetimeExtender.OnEndLifetimeExtensionCallback?,
        extender: NotifLifetimeExtender,
    ) {
        Log.wtf(TAG, "endLifetimeExtension() called")
    }

    override fun onImportanceChanged() {
        Log.wtf(TAG, "onImportanceChanged() called")
    }

    override fun markForUserTriggeredMovement(marked: Boolean) {
        Log.wtf(TAG, "markForUserTriggeredMovement() called")
    }

    override fun isMarkedForUserTriggeredMovement(): Boolean {
        return false
    }

    override fun isHighPriority(): Boolean {
        return highPriorityProvider.isHighPriority(entry)
    }

    override fun setInlineControlsShown(currentlyVisible: Boolean) {
        // nothing to do, yet
    }

    override fun isBlockable(): Boolean {
        return false
    }

    override fun canDragAndDrop(): Boolean {
        return false
    }

    override fun isBubble(): Boolean {
        return false
    }

    override fun getStyle(): String? {
        return null
    }

    override fun getSectionBucket(): Int {
        return entry.bucket
    }

    override fun isAmbient(): Boolean {
        return false
    }

    override fun getPeopleNotificationType(): Int {
        return TYPE_NON_PERSON
    }

    override fun isPromotedOngoing(): Boolean {
        return false
    }

    override fun isFullScreenCapable(): Boolean {
        return false
    }

    override fun onDragSuccess() {
        // do nothing. these should not be draggable
        Log.wtf(TAG, "onDragSuccess() called")
    }

    override fun onNotificationBubbleIconClicked() {
        // do nothing. these cannot be a bubble
        Log.wtf(TAG, "onNotificationBubbleIconClicked() called")
    }

    override fun onNotificationActionClicked() {
        // do nothing. these have no actions
        Log.wtf(TAG, "onNotificationActionClicked() called")
    }

    override fun isParentDismissed(): Boolean {
        return false
    }

    override fun onEntryClicked(row: ExpandableNotificationRow) {
        // TODO(b/396446620): should anything happen when you click on a bundle?
    }

    override fun getRemoteInputEntryAdapter(): RemoteInputEntryAdapter? {
        return null
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
        Log.wtf(TAG, "onEntryAnimatingAwayEnded() called")
    }

    override fun registerFutureDismissal(): Runnable {
        return onUserInteractionCallback.registerFutureDismissal(entry)
    }

    override fun markForReinflation(stage: RowContentBindStage) {
        Log.wtf(TAG, "markForReinflation() called")
    }

    override fun isViewBacked(): Boolean {
        return false
    }

    override fun requestRebind(
        stage: RowContentBindStage,
        callback: NotifBindPipeline.BindCallback,
    ) {
        Log.wtf(TAG, "requestRebind() called")
    }

    override fun isBundled(): Boolean {
        return false
    }

    override fun isBundle(): Boolean {
        return true
    }

    override fun onBundleDisabledForEntry() {
        // do nothing. it should not be possible for a bundle to be contained within a bundle
        Log.wtf(TAG, "onBundleDisabledForEntry() called")
    }

    override fun onBundleDisabledForApp() {
        // do nothing. it should not be possible for a bundle to be contained within a bundle
        Log.wtf(TAG, "onBundleDisabledForApp() called")
    }

    override fun getBundleType(): Int {
        return entry.bundleRepository.bundleType
    }
}

private const val TAG = "BundleEntryAdapter"
