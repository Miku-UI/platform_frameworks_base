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
package com.android.systemui.statusbar.notification.row

import android.app.INotificationManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.UiEventLogger
import com.android.systemui.notifications.content.icon.AppIconProvider
import com.android.systemui.res.R
import com.android.systemui.statusbar.notification.collection.EntryAdapter
import com.android.systemui.statusbar.notification.promoted.domain.interactor.PackageDemotionInteractor
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider

class BridgedNotificationInfo(context: Context?, attrs: AttributeSet?) :
    NotificationInfo(context, attrs) {

    override fun bindInlineControls() {
        val done = findViewById<TextView>(R.id.done)
        done.setOnClickListener { mOnDismissSettings.onClick(done) }

        val dismissButton = findViewById<TextView>(R.id.inline_dismiss)
        dismissButton.setOnClickListener(mOnCloseClickListener)

        val openAssociatedDeviceSettingsButton =
            findViewById<TextView>(R.id.bridged_open_associated_device_settings)
        openAssociatedDeviceSettingsButton.setOnClickListener {
            var intent = Intent(Notification.ACTION_BRIDGED_NOTIFICATION_PREFERENCES)
            intent.setPackage(mSbn.packageName)
            context.sendBroadcastAsUser(intent, UserHandle.CURRENT)
        }
    }

    override fun bindNotification(
        pm: PackageManager,
        iNotificationManager: INotificationManager,
        appIconProvider: AppIconProvider,
        iconStyleProvider: NotificationIconStyleProvider,
        onUserInteractionCallback: OnUserInteractionCallback,
        channelEditorDialogController: ChannelEditorDialogController,
        packageDemotionInteractor: PackageDemotionInteractor,
        pkg: String,
        ranking: NotificationListenerService.Ranking,
        sbn: StatusBarNotification,
        entryAdapter: EntryAdapter?,
        onSettingsClick: OnSettingsClickListener?,
        onAppSettingsClick: OnAppSettingsClickListener?,
        feedbackClickListener: OnFeedbackClickListener?,
        uiEventLogger: UiEventLogger,
        isDeviceProvisioned: Boolean,
        isNonblockable: Boolean,
        isDismissable: Boolean,
        wasShownHighPriority: Boolean,
        metricsLogger: MetricsLogger,
        onCloseClick: OnClickListener?,
    ) {
        super.bindNotification(
            pm,
            iNotificationManager,
            appIconProvider,
            iconStyleProvider,
            onUserInteractionCallback,
            channelEditorDialogController,
            packageDemotionInteractor,
            pkg,
            ranking,
            sbn,
            entryAdapter,
            onSettingsClick,
            onAppSettingsClick,
            feedbackClickListener,
            uiEventLogger,
            isDeviceProvisioned,
            isNonblockable,
            isDismissable,
            wasShownHighPriority,
            metricsLogger,
            onCloseClick,
        )
        val bridgedOpenSettingsButton =
            findViewById<TextView>(R.id.bridged_open_associated_device_settings)
        var notification = sbn.notification
        if (notification?.bridgedNotificationMetadata == null) {
            // This view is only used for bridged notifications, which must have this metadata.
            Log.wtf(TAG, "BridgedNotificationMetadata is null in BridgedNotificationInfo")
            // Something has gone very wrong and we should just stop before we make it worse.
            return
        }

        var deviceTypeString = context.getString(R.string.bridged_source_device)
        bridgedOpenSettingsButton.setText(
            mContext.getString(
                R.string.inline_bridged_open_associated_device_settings,
                deviceTypeString,
            )
        )
        val deviceName =
            notification?.bridgedNotificationMetadata?.originDeviceName ?: deviceTypeString
        val bridgedLabelTextView = findViewById<TextView>(R.id.bridged_label)
        bridgedLabelTextView.setText(
            mContext.getString(R.string.notification_bridged_title, deviceName)
        )

        val bridgedSummaryTextView = findViewById<TextView>(R.id.bridged_summary)
        bridgedSummaryTextView.setText(
            mContext.getString(R.string.notification_channel_summary_bridged, deviceName)
        )
    }

    companion object {
        private const val TAG = "BridgedNotificationInfo"
    }
}
