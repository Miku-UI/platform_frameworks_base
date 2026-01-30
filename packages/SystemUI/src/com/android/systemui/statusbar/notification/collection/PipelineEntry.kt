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

import com.android.systemui.statusbar.notification.collection.ListAttachState.Companion.create
import com.android.systemui.statusbar.notification.collection.listbuilder.NotifSection
import com.android.systemui.statusbar.notification.stack.PriorityBucket
import com.android.systemui.util.ListenerSet

/** Class to represent a notification, group, or bundle in the pipeline. */
sealed class PipelineEntry(
    /** Key uniquely identifying this entry. */
    val key: String
) {
    /** @return Current state that ShadeListBuilder assigned to this PipelineEntry. */
    val attachState: ListAttachState = create()

    /** @return Previous state that ShadeListBuilder assigned to this PipelineEntry. */
    val previousAttachState: ListAttachState = create()

    @get:PriorityBucket abstract val bucket: Int

    var isSeenInShade: Boolean = false

    @JvmField
    val mOnSensitivityChangedListeners: ListenerSet<OnSensitivityChangedListener> = ListenerSet()

    /** @return This [PipelineEntry] as a [ListEntry], or `null`. */
    abstract fun asListEntry(): ListEntry?

    /** @return NotifSection that ShadeListBuilder assigned to this PipelineEntry. */
    val section: NotifSection?
        get() = attachState.section

    /** @return True if this entry was attached in the last pass, else false. */
    open fun wasAttachedInPreviousPass(): Boolean {
        return previousAttachState.parent != null
    }

    /** @return Index of section assigned to this entry. */
    val sectionIndex: Int
        get() = if (attachState.section != null) attachState.section!!.index else -1

    /** @return Parent PipelineEntry */
    var parent: PipelineEntry?
        get() = attachState.parent
        set(parent) {
            attachState.parent = parent
        }

    /**
     * Stores the current attach state into [.getPreviousAttachState]} and then starts a fresh
     * attach state (all entries will be null/default-initialized).
     */
    fun beginNewAttachState() {
        previousAttachState.clone(attachState)
        attachState.reset()
    }

    /** Add a listener to be notified when the entry's sensitivity changes. */
    fun addOnSensitivityChangedListener(listener: OnSensitivityChangedListener) {
        mOnSensitivityChangedListeners.addIfAbsent(listener)
    }

    /** Remove a listener that was registered above. */
    fun removeOnSensitivityChangedListener(listener: OnSensitivityChangedListener) {
        mOnSensitivityChangedListeners.remove(listener)
    }

    /** Listener interface for [.addOnSensitivityChangedListener] */
    fun interface OnSensitivityChangedListener {
        /** Called when the sensitivity changes */
        fun onSensitivityChanged(entry: NotificationEntry)
    }
}

/** Performs an action on all group entries, even if they are in a bundle */
inline fun List<PipelineEntry>.forEachGroupEntry(action: (GroupEntry) -> Unit) {
    forEach { entry ->
        when (entry) {
            is GroupEntry -> {
                action(entry)
            }
            is BundleEntry -> {
                for (bundleChild in entry.children) {
                    if (bundleChild is GroupEntry) {
                        action(bundleChild)
                    }
                }
            }
            else -> {
                // Do nothing for leaf nodes
            }
        }
    }
}
