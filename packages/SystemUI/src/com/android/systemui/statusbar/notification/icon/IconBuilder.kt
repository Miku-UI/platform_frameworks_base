/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.icon

import android.app.Notification
import android.content.Context
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.dagger.StatusBarMain
import com.android.systemui.statusbar.notification.collection.BundleEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.contentDescForNotification
import javax.inject.Inject

/** Testable wrapper around Context. */
class IconBuilder @Inject constructor(@StatusBarMain private val context: Context) {

    fun createIconView(entry: NotificationEntry, context: Context): StatusBarIconView {
        return StatusBarIconView(
            context,
            "${entry.sbn.packageName}/0x${Integer.toHexString(entry.sbn.id)}",
            entry.sbn,
        )
    }

    fun createIconView(entry: BundleEntry, context: Context): StatusBarIconView {
        return StatusBarIconView(context, entry.key, null, entry)
    }

    // TODO b/362720336: remove all usages of this without a provided context.
    @Deprecated("Use createIconView(entry, context), providing the correct context.")
    fun createIconView(entry: NotificationEntry): StatusBarIconView {
        return createIconView(entry, context)
    }

    // TODO b/362720336: remove all usages of this without a provided context.
    @Deprecated("Use createIconView(entry, context), providing the correct context.")
    fun createIconView(entry: BundleEntry): StatusBarIconView {
        return createIconView(entry, context)
    }

    fun getIconContentDescription(n: Notification): CharSequence {
        return contentDescForNotification(context, n)
    }
}
