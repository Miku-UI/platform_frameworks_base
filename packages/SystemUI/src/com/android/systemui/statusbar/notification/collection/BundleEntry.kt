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

import com.android.systemui.statusbar.notification.icon.IconPack
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.data.repository.BundleRepository
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Class to represent notifications bundled by classification.
 *
 * This is the model used by the pipeline.
 */
class BundleEntry(spec: BundleSpec) : PipelineEntry(spec.key) {

    override val bucket: Int = spec.bucket

    /** The model used by UI. */
    val bundleRepository =
        BundleRepository(
            titleText = spec.titleText,
            bundleIcon = spec.icon,
            summaryText = spec.summaryText,
            bundleType = spec.bundleType,
        )

    // TODO(b/394483200): move NotificationEntry's implementation to PipelineEntry?
    val isSensitive: MutableStateFlow<Boolean> = MutableStateFlow(false)

    var row: ExpandableNotificationRow? = null

    var icons: IconPack = IconPack.buildEmptyPack(null)

    private val _children = ArrayList<ListEntry>()

    /**
     * Modifiable list of children for this bundle. You should prefer [children] to this property.
     */
    @InternalNotificationsApi val rawChildren: MutableList<ListEntry> = _children

    val children: List<ListEntry> = Collections.unmodifiableList(_children)

    @InternalNotificationsApi
    fun addChild(child: ListEntry) {
        _children.add(child)
    }

    @InternalNotificationsApi
    fun removeChild(child: ListEntry) {
        _children.remove(child)
    }

    @InternalNotificationsApi
    fun clearChildren() {
        _children.clear()
    }

    override fun asListEntry(): ListEntry? {
        return null
    }

    override fun wasAttachedInPreviousPass(): Boolean {
        return false
    }

    fun onDensityOrFontScaleChanged() {
        row?.onDensityOrFontScaleChanged()
    }

    fun onUiModeChanged() {
        row?.onUiModeChanged()
    }

    /**
     * Returns whether this bundle be cleared when the user wants to "clear all" notifications.
     *
     * This is `true` only if all children are clearable.
     */
    val isClearable: Boolean
        get() = _children.all { it.representativeEntry?.sbn?.isClearable != false }
}
