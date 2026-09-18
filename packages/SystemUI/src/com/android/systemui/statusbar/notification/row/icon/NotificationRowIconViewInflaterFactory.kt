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

package com.android.systemui.statusbar.notification.row.icon

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.android.internal.widget.NotificationRowIconView
import com.android.internal.widget.NotificationRowIconView.ICON_TYPE_BRIDGED_ICON
import com.android.internal.widget.NotificationRowIconView.ICON_TYPE_LAUNCHER_ICON
import com.android.internal.widget.NotificationRowIconView.ICON_TYPE_SMALL_ICON
import com.android.internal.widget.NotificationRowIconView.IconType
import com.android.internal.widget.NotificationRowIconView.NotificationIconProvider
import com.android.systemui.notifications.content.icon.AppIconProvider
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
import com.android.systemui.statusbar.notification.row.NotifRemoteViewsFactory
import com.android.systemui.statusbar.notification.row.NotificationRowContentBinder
import javax.inject.Inject

/**
 * A factory which owns the construction of any NotificationRowIconView inside of Notifications in
 * SystemUI. This allows overriding the small icon with the app icon in notifications.
 */
class NotificationRowIconViewInflaterFactory
@Inject
constructor(
    private val appIconProvider: AppIconProvider,
    private val iconStyleProvider: NotificationIconStyleProvider,
    private val bridgedIconProvider: BridgedIconProvider,
) : NotifRemoteViewsFactory {
    override fun instantiate(
        row: ExpandableNotificationRow,
        @NotificationRowContentBinder.InflationFlag layoutType: Int,
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet,
    ): View? {
        return when (name) {
            NotificationRowIconView::class.java.name ->
                NotificationRowIconView(context, attrs).also { view ->
                    view.setIconProvider(createIconProvider(row, context))
                }

            else -> null
        }
    }

    private fun createIconProvider(
        row: ExpandableNotificationRow,
        context: Context,
    ): NotificationIconProvider {
        val sbn = row.entryAdapter.sbn
        if (sbn == null) {
            return object : NotificationIconProvider {
                @IconType
                override fun getIconType(): Int {
                    return ICON_TYPE_SMALL_ICON
                }

                override fun getBridgedIcon(): Drawable? {
                    return null
                }

                override fun getLauncherIcon(): Drawable? {
                    return null
                }
            }
        }
        return object : NotificationIconProvider {
            @IconType
            override fun getIconType(): Int {
                var iconType =
                    if (iconStyleProvider.shouldShowAppIcon(sbn, context)) {
                        ICON_TYPE_LAUNCHER_ICON
                    } else {
                        ICON_TYPE_SMALL_ICON
                    }
                if (
                    android.app.Flags.bridgedNotifications() &&
                        (sbn.getNotification().getBridgedNotificationMetadata() != null)
                ) {
                    iconType = ICON_TYPE_BRIDGED_ICON
                }
                if (iconType == ICON_TYPE_LAUNCHER_ICON || iconType == ICON_TYPE_BRIDGED_ICON) {
                    row.setIsShowingAppIcon(true)
                }
                return iconType
            }

            override fun getBridgedIcon(): Drawable? {
                val bridgedMetadata = sbn.notification.bridgedNotificationMetadata ?: return null
                return bridgedIconProvider.getBridgedIcon(context, bridgedMetadata)
            }

            override fun getLauncherIcon(): Drawable {
                return appIconProvider.getOrFetchAppIcon(
                    packageName = sbn.packageName,
                    userHandle = context.user,
                    instanceKey = "LEGACY",
                )
            }
        }
    }
}
