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

package com.android.systemui.screenrecord.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.projection.StopReason
import android.os.Bundle
import androidx.media3.common.MimeTypes
import com.android.systemui.res.R
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.screencapture.record.smallscreen.ui.SmallScreenPostRecordingActivity
import com.android.systemui.screenrecord.RecordingServiceStrings
import com.android.systemui.screenrecord.ScreenMediaRecorder.SavedRecording
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.service.ScreenRecordingService

private const val REQUEST_CODE = 2

private const val GROUP_KEY_SAVED = "screen_record_saved"
private const val GROUP_KEY_ERROR_STARTING = "screen_record_error_starting"
private const val GROUP_KEY_ERROR_SAVING = "screen_record_error_saving"

private const val NOTIF_BASE_ID = 4273
private const val NOTIF_GROUP_ID_SAVED = NOTIF_BASE_ID + 1
private const val NOTIF_GROUP_ID_ERROR_SAVING = NOTIF_BASE_ID + 2
private const val NOTIF_GROUP_ID_ERROR_STARTING = NOTIF_BASE_ID + 3

class ScreenRecordingServiceNotificationInteractor(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val strings: RecordingServiceStrings,
    private val channelId: String,
    private val tag: String,
    private val serviceClass: Class<out Service>,
) : NotificationInteractor {

    override fun notifyProcessing(notificationId: Int, audioSource: ScreenRecordingAudioSource) {
        val notificationTitle: String =
            if (audioSource == ScreenRecordingAudioSource.NONE) {
                strings.ongoingRecording
            } else {
                strings.ongoingRecordingWithAudio
            }

        val builder: Notification.Builder =
            Notification.Builder(context, channelId)
                .setContentTitle(notificationTitle)
                .setContentText(strings.backgroundProcessingLabel)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setGroup(GROUP_KEY_SAVED)
                .addExtras(
                    Bundle().apply {
                        putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, strings.title)
                    }
                )
        notificationManager.notify(notificationId, builder.build())
    }

    override fun notifyRecording(notificationId: Int, audioSource: ScreenRecordingAudioSource) {
        val notificationTitle: String =
            if (audioSource == ScreenRecordingAudioSource.NONE) {
                strings.ongoingRecording
            } else {
                strings.ongoingRecordingWithAudio
            }

        val stopAction: Notification.Action =
            Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_screenrecord),
                    strings.stopLabel,
                    PendingIntent.getService(
                        context,
                        REQUEST_CODE,
                        Intent(context, serviceClass)
                            .setAction(ScreenRecordingService.ACTION_STOP)
                            .putExtra(
                                ScreenRecordingService.EXTRA_STOP_REASON,
                                StopReason.STOP_HOST_APP,
                            ),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build()
        val builder =
            Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setContentTitle(notificationTitle)
                .setUsesChronometer(true)
                .setColorized(true)
                .setColor(context.getColor(R.color.GM2_red_700))
                .setOngoing(true)
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(stopAction)
                .addExtras(
                    Bundle().apply {
                        putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, strings.title)
                    }
                )
        notificationManager.notify(notificationId, builder.build())
    }

    override fun notifySaved(
        notificationId: Int,
        audioSource: ScreenRecordingAudioSource,
        savedRecording: SavedRecording,
    ) {
        notifyGroupSummary(
            notificationContentTitle = strings.saveTitle,
            groupKey = GROUP_KEY_SAVED,
            notificationIdForGroup = NOTIF_GROUP_ID_SAVED,
        )

        val viewIntent =
            if (ScreenCaptureRecordFeaturesInteractor.isNewScreenRecordToolbarEnabled) {
                SmallScreenPostRecordingActivity.getStartingIntent(
                    context = context,
                    videoUri = savedRecording.uri,
                    shouldShowVideoSaved = true,
                )
            } else {
                Intent(Intent.ACTION_VIEW)
                    .setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    .setDataAndType(savedRecording.uri, MimeTypes.VIDEO_MP4)
            }

        val shareAction: Notification.Action =
            Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_screenrecord),
                    strings.shareLabel,
                    PendingIntent.getService(
                        context,
                        REQUEST_CODE,
                        Intent(context, serviceClass)
                            .setAction(ScreenRecordingService.ACTION_SHARE)
                            .setDataAndType(savedRecording.uri, MimeTypes.VIDEO_MP4),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                .build()

        val builder: Notification.Builder =
            Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setContentTitle(strings.saveTitle)
                .setContentText(strings.saveText)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        REQUEST_CODE,
                        viewIntent,
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
                .addAction(shareAction)
                .setAutoCancel(true)
                .setGroup(GROUP_KEY_SAVED)
                .addExtras(
                    Bundle().apply {
                        putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, strings.title)
                    }
                )

        // Add a thumbnail if available
        savedRecording.thumbnail?.let { thumbnail ->
            val pictureStyle: Notification.BigPictureStyle =
                Notification.BigPictureStyle()
                    .bigPicture(thumbnail)
                    .showBigPictureWhenCollapsed(true)
            builder.setStyle(pictureStyle)
        }
        notificationManager.notify(notificationId, builder.build())
    }

    override fun notifyErrorSaving(notificationId: Int) {
        notifyGroupSummary(
            notificationContentTitle = strings.saveError,
            groupKey = GROUP_KEY_ERROR_SAVING,
            notificationIdForGroup = NOTIF_GROUP_ID_ERROR_SAVING,
        )
        val notification =
            createErrorNotification(
                notificationContentTitle = strings.saveError,
                groupKey = GROUP_KEY_ERROR_SAVING,
            )
        notificationManager.notify(tag, notificationId, notification)
    }

    override fun notifyErrorStarting(notificationId: Int) {
        notifyGroupSummary(
            notificationContentTitle = strings.startError,
            groupKey = GROUP_KEY_ERROR_STARTING,
            notificationIdForGroup = NOTIF_GROUP_ID_ERROR_STARTING,
        )
        val notification = createErrorNotification(strings.startError, GROUP_KEY_ERROR_STARTING)
        notificationManager.notify(tag, notificationId, notification)
    }

    private fun createErrorNotification(
        notificationContentTitle: String,
        groupKey: String,
    ): Notification =
        Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_screenrecord)
            .setContentTitle(notificationContentTitle)
            .setGroup(groupKey)
            .addExtras(
                Bundle().apply { putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, strings.title) }
            )
            .build()

    /**
     * Posts a group summary notification for the given group.
     *
     * Notifications that should be grouped:
     * - Save notifications
     * - Error saving notifications
     * - Error starting notifications
     */
    private fun notifyGroupSummary(
        notificationContentTitle: String,
        groupKey: String,
        notificationIdForGroup: Int,
    ) {
        val builder =
            Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_screenrecord)
                .setContentTitle(notificationContentTitle)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setExtras(
                    Bundle().apply {
                        putString(Notification.EXTRA_SUBSTITUTE_APP_NAME, strings.title)
                    }
                )
        notificationManager.notify(tag, notificationIdForGroup, builder.build())
    }
}
