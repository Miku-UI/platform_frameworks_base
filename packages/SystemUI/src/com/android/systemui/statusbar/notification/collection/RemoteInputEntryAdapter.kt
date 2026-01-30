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

import android.net.Uri
import android.view.ContentInfo
import com.android.systemui.statusbar.notification.headsup.HeadsUpManager
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow

class RemoteInputEntryAdapter(
    private val entry: NotificationEntry,
) {
    val key: String get() = entry.key

    val style: String get() = entry.notificationStyle

    val rowExists: Boolean get() = entry.rowExists()

    val row: ExpandableNotificationRow? get() = entry.row

    var remoteInputAnimatingAway: Boolean get() = entry.mRemoteEditImeAnimatingAway
        set(value) {
            entry.mRemoteEditImeAnimatingAway = value
        }

    var remoteInputImeVisible: Boolean get() = entry.mRemoteEditImeVisible
        set(value) {
            entry.mRemoteEditImeVisible = value
        }

    var remoteInputAttachment: ContentInfo? get() = entry.remoteInputAttachment
        set(value) {
            entry.remoteInputAttachment = value
        }

    var remoteInputUri: Uri? get() = entry.remoteInputUri
        set(value) {
            entry.remoteInputUri = value
        }

    var remoteInputMimeType: String? get() = entry.remoteInputMimeType
        set(value) {
            entry.remoteInputMimeType = value
        }

    var remoteInputText: CharSequence? get() = entry.remoteInputText
        set(value) {
            entry.remoteInputText = value
        }

    var remoteInputTextWhenReset: CharSequence? get() = entry.remoteInputTextWhenReset
        set(value) {
            entry.remoteInputTextWhenReset = value
        }

    fun isSameEntryAs(entry: NotificationEntry) : Boolean = this.entry === entry

    fun closeRemoteInput() {
        entry.closeRemoteInput()
    }

    fun notifyHeightChanged(animate : Boolean) {
        entry.notifyHeightChanged(animate)
    }

    fun setRemoteInputActive(headsUpManager: HeadsUpManager, isActive: Boolean) {
        headsUpManager.setRemoteInputActive(entry, isActive)
    }
}

