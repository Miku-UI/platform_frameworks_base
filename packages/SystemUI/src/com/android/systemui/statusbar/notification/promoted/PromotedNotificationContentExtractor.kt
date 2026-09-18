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
package com.android.systemui.statusbar.notification.promoted

import android.annotation.WorkerThread
import android.app.Flags.apiNotificationActionCustom
import android.app.Notification
import android.app.Notification.BigTextStyle
import android.app.Notification.CallStyle
import android.app.Notification.EXTRA_PROGRESS
import android.app.Notification.EXTRA_PROGRESS_INDETERMINATE
import android.app.Notification.EXTRA_PROGRESS_MAX
import android.app.Notification.MetricStyle
import android.app.Notification.ProgressStyle
import android.content.Context
import android.os.UserHandle
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import androidx.compose.ui.util.trace
import com.android.internal.R
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.notifications.content.chronometerCountDown
import com.android.systemui.notifications.content.icon.AppIconProvider
import com.android.systemui.notifications.content.preferSmallIcon
import com.android.systemui.notifications.content.subText
import com.android.systemui.notifications.content.text
import com.android.systemui.notifications.content.title
import com.android.systemui.notifications.content.verificationIcon
import com.android.systemui.notifications.content.verificationText
import com.android.systemui.statusbar.NotificationLockscreenUserManager.REDACTION_TYPE_NONE
import com.android.systemui.statusbar.NotificationLockscreenUserManager.RedactionType
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.NotifIcon
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.OldProgress
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.When
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.row.icon.NotificationIconStyleProvider
import com.android.systemui.statusbar.notification.row.shared.ImageModel
import com.android.systemui.statusbar.notification.row.shared.ImageModelProvider
import com.android.systemui.statusbar.notification.row.shared.ImageModelProvider.ImageSizeClass.MediumSquare
import com.android.systemui.statusbar.notification.row.shared.ImageModelProvider.ImageSizeClass.SmallSquare
import com.android.systemui.statusbar.notification.row.shared.SkeletonImageTransform
import com.android.systemui.statusbar.notification.shared.NotificationChipFromCompactContent
import com.android.systemui.statusbar.notification.shared.extractMetrics
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject

interface PromotedNotificationContentExtractor {
    @WorkerThread
    fun extractContent(
        entry: NotificationEntry,
        recoveredBuilder: Notification.Builder,
        @RedactionType redactionType: Int,
        imageModelProvider: ImageModelProvider,
        packageContext: Context,
        systemUiContext: Context,
    ): PromotedNotificationContentModels?
}

@SysUISingleton
class PromotedNotificationContentExtractorImpl
@Inject
constructor(
    private val notificationIconStyleProvider: NotificationIconStyleProvider,
    private val appIconProvider: AppIconProvider,
    private val skeletonImageTransform: SkeletonImageTransform,
    private val systemClock: SystemClock,
    private val logger: PromotedNotificationLogger,
) : PromotedNotificationContentExtractor {
    @WorkerThread
    override fun extractContent(
        entry: NotificationEntry,
        recoveredBuilder: Notification.Builder,
        @RedactionType redactionType: Int,
        imageModelProvider: ImageModelProvider,
        packageContext: Context,
        systemUiContext: Context,
    ): PromotedNotificationContentModels? {
        val notification = entry.sbn.notification
        if (notification == null) {
            if (LOG_NOT_EXTRACTED) {
                logger.logExtractionFailed(entry, "entry.sbn.notification is null")
            }
            return null
        }
        if (!notification.isPromotedOngoing()) {
            if (LOG_NOT_EXTRACTED) {
                logger.logExtractionSkipped(entry, "isPromotedOngoing returned false")
            }
            return null
        }
        val privateVersion =
            extractPrivateContent(
                key = entry.key,
                sbn = entry.sbn,
                recoveredBuilder = recoveredBuilder,
                lastAudiblyAlertedMs = entry.lastAudiblyAlertedMs,
                imageModelProvider = imageModelProvider,
                packageContext = packageContext,
                systemUiContext = systemUiContext,
            )
        if (privateVersion.notificationView == null) {
            logger.logSkeletonInflationFailed(entry, "Private View inflation failed")
        }
        val publicVersion =
            if (redactionType == REDACTION_TYPE_NONE) {
                privateVersion
            } else {
                notification.publicVersion?.let { publicNotification ->
                    createAppDefinedPublicVersion(
                        privateModel = privateVersion,
                        publicNotification = publicNotification,
                        imageModelProvider = imageModelProvider,
                        systemUiContext = systemUiContext,
                    )
                }
                    ?: createDefaultPublicVersion(
                        privateModel = privateVersion,
                        systemUiContext = systemUiContext,
                    )
            }
        if (redactionType != REDACTION_TYPE_NONE && publicVersion.notificationView == null) {
            logger.logSkeletonInflationFailed(entry, "Public View inflation failed")
        }
        return PromotedNotificationContentModels(
                privateVersion = privateVersion,
                publicVersion = publicVersion,
            )
            .also { logger.logExtractionSucceeded(entry, it) }
    }

    private fun copyNonSensitiveFields(
        privateModel: PromotedNotificationContentModel,
        publicBuilder: PromotedNotificationContentModel.Builder,
    ) {
        publicBuilder.preferSmallIcon = privateModel.preferSmallIcon
        publicBuilder.skeletonNotifIcon = privateModel.skeletonNotifIcon
        publicBuilder.iconLevel = privateModel.iconLevel
        publicBuilder.appName = privateModel.appName
        publicBuilder.time = privateModel.time
        publicBuilder.lastAudiblyAlertedMs = privateModel.lastAudiblyAlertedMs
        publicBuilder.profileBadgeBitmap = privateModel.profileBadgeBitmap
        publicBuilder.colors = privateModel.colors
    }

    private fun createDefaultPublicVersion(
        privateModel: PromotedNotificationContentModel,
        systemUiContext: Context,
    ): PromotedNotificationContentModel =
        PromotedNotificationContentModel.Builder(key = privateModel.identity.key)
            .also {
                it.style =
                    if (privateModel.style == Style.Ineligible) Style.Ineligible else Style.Base
                copyNonSensitiveFields(privateModel, it)
                inflateNotificationView(it, systemUiContext)
            }
            .build()

    private fun createAppDefinedPublicVersion(
        privateModel: PromotedNotificationContentModel,
        publicNotification: Notification,
        imageModelProvider: ImageModelProvider,
        systemUiContext: Context,
    ): PromotedNotificationContentModel =
        PromotedNotificationContentModel.Builder(key = privateModel.identity.key)
            .also { publicBuilder ->
                val notificationStyle = publicNotification.notificationStyle
                publicBuilder.style =
                    when {
                        privateModel.style == Style.Ineligible -> Style.Ineligible
                        notificationStyle == CallStyle::class.java -> Style.CollapsedCall
                        else -> Style.CollapsedBase
                    }
                copyNonSensitiveFields(privateModel = privateModel, publicBuilder = publicBuilder)
                publicBuilder.shortCriticalText = publicNotification.shortCriticalText
                publicBuilder.subText = publicNotification.subText()
                // The standard public version is extracted as a collapsed notification,
                //  so avoid using bigTitle or bigText, and instead get the collapsed versions.
                publicBuilder.title = publicNotification.title(expanded = false)
                publicBuilder.text = publicNotification.text(expanded = false)
                publicBuilder.skeletonLargeIcon =
                    publicNotification.skeletonLargeIcon(imageModelProvider)
                // Only CallStyle has styled content that shows in the collapsed version.
                if (publicBuilder.style == Style.Call) {
                    extractCallStyleContent(publicNotification, publicBuilder, imageModelProvider)
                }
                inflateNotificationView(publicBuilder, systemUiContext)
            }
            .build()

    private fun extractPrivateContent(
        key: String,
        sbn: StatusBarNotification,
        recoveredBuilder: Notification.Builder,
        lastAudiblyAlertedMs: Long,
        imageModelProvider: ImageModelProvider,
        packageContext: Context,
        systemUiContext: Context,
    ): PromotedNotificationContentModel {
        val notification = sbn.notification
        val contentBuilder = PromotedNotificationContentModel.Builder(key)
        // TODO: Pitch a fit if style is unsupported or mandatory fields are missing once
        // FLAG_PROMOTED_ONGOING is set reliably and we're not testing status bar chips.
        contentBuilder.preferSmallIcon = notification.preferSmallIcon()
        contentBuilder.skeletonNotifIcon =
            sbn.skeletonAppIcon(packageContext)
                ?: notification.skeletonSmallIcon(imageModelProvider)
        contentBuilder.iconLevel = notification.iconLevel
        contentBuilder.appName = notification.loadHeaderAppName(packageContext)
        contentBuilder.subText = notification.subText()
        contentBuilder.time = notification.extractWhen()
        if (NotificationChipFromCompactContent.isEnabled) {
            contentBuilder.compactContent = notification.resolveCompactContent(packageContext)
        } else {
            contentBuilder.shortCriticalText = notification.shortCriticalText
        }
        contentBuilder.lastAudiblyAlertedMs = lastAudiblyAlertedMs
        contentBuilder.profileBadgeBitmap = recoveredBuilder.profileBadge
        contentBuilder.title = notification.title(expanded = true)
        contentBuilder.text = notification.text(expanded = true)
        contentBuilder.skeletonLargeIcon = notification.skeletonLargeIcon(imageModelProvider)
        contentBuilder.oldProgress = recoveredBuilder.oldProgress(notification)
        val colorsFromNotif = recoveredBuilder.getColors(/* isHeader= */ false)
        contentBuilder.colors =
            PromotedNotificationContentModel.Colors(
                backgroundColor = colorsFromNotif.backgroundColor,
                textColor = colorsFromNotif.textColor,
            )
        recoveredBuilder.extractStyleContent(
            notification,
            contentBuilder,
            imageModelProvider,
            systemUiContext,
        )
        inflateNotificationView(contentBuilder, systemUiContext)
        return contentBuilder.build()
    }

    private data class InflationIdentity(val layout: Int, val density: Float, val scale: Float)

    private fun inflateNotificationView(
        contentBuilder: PromotedNotificationContentModel.Builder,
        systemUiContext: Context,
    ) {
        val style = contentBuilder.style ?: return
        val res = getLayoutSource(style) ?: return
        // Inflating with `sysuiContext` is intentional here.
        // As we transition to Jetpack Compose, the view layer will no longer have direct
        // access to the application's context. Using `sysuiContext` ensures we can
        // properly inflate this view while adhering to upcoming architectural constraints.
        trace("AODPromotedNotification#inflate") {
            contentBuilder.notificationView =
                try {
                    LayoutInflater.from(systemUiContext).inflate(res, /* root= */ null)
                } catch (_: Throwable) {
                    null
                }
            contentBuilder.notificationView?.setTag(
                com.android.systemui.res.R.id.aod_promoted_notification_inflation_identity,
                InflationIdentity(
                    layout = res,
                    density = systemUiContext.resources.displayMetrics.density,
                    scale = systemUiContext.resources.displayMetrics.scaledDensity,
                ),
            )
        }
    }

    private fun getLayoutSource(style: Style): Int? {
        return when (style) {
            Style.Base -> R.layout.notification_2025_template_expanded_base
            Style.CollapsedBase -> R.layout.notification_2025_template_collapsed_base
            Style.BigText -> R.layout.notification_2025_template_expanded_big_text
            Style.Call -> R.layout.notification_2025_template_expanded_call
            Style.CollapsedCall -> R.layout.notification_2025_template_collapsed_call
            Style.Progress ->
                if (apiNotificationActionCustom())
                    R.layout.notification_2025_template_promoted_progress
                else R.layout.notification_2025_template_expanded_progress

            Style.Metric -> R.layout.notification_2025_template_expanded_metric
            Style.MetricSingle -> R.layout.notification_2025_template_promoted_single_metric
            Style.Ineligible -> null
        }
    }

    private fun Notification.skeletonSmallIcon(
        imageModelProvider: ImageModelProvider
    ): NotifIcon.SmallIcon? =
        imageModelProvider.getImageModel(smallIcon, SmallSquare)?.let { NotifIcon.SmallIcon(it) }

    private fun StatusBarNotification.skeletonAppIcon(packageContext: Context): NotifIcon.AppIcon? {
        if (!notificationIconStyleProvider.shouldShowAppIcon(this, packageContext)) return null
        val userHandle = UserHandle.of(normalizedUserId)
        return NotifIcon.AppIcon(appIconProvider.getOrFetchSkeletonAppIcon(packageName, userHandle))
    }

    private fun Notification.skeletonLargeIcon(
        imageModelProvider: ImageModelProvider
    ): ImageModel? {
        if (notificationStyle == MetricStyle::class.java) return null
        return getLargeIcon()?.let {
            imageModelProvider.getImageModel(it, MediumSquare, skeletonImageTransform)
        }
    }

    private fun Notification.Builder.oldProgress(notification: Notification): OldProgress? {
        if (style is ProgressStyle || style is CallStyle || style is MetricStyle) {
            return null
        }
        val progress = notification.progress() ?: return null
        val max = notification.progressMax() ?: return null
        val isIndeterminate = notification.progressIndeterminate() ?: return null
        if (progress == 0 && max == 0) return null
        return OldProgress(progress = progress, max = max, isIndeterminate = isIndeterminate)
    }

    private fun Notification.progress(): Int? = extras?.getInt(EXTRA_PROGRESS)

    private fun Notification.progressMax(): Int? = extras?.getInt(EXTRA_PROGRESS_MAX)

    private fun Notification.progressIndeterminate(): Boolean? =
        extras?.getBoolean(EXTRA_PROGRESS_INDETERMINATE)

    private fun Notification.extractWhen(): When? {
        val whenTime = getWhen()
        return when {
            showsChronometer() -> {
                When.Chronometer(
                    elapsedRealtimeMillis =
                        whenTime + systemClock.elapsedRealtime() - systemClock.currentTimeMillis(),
                    isCountDown = chronometerCountDown(),
                )
            }
            showsTime() -> When.Time(currentTimeMillis = whenTime)
            else -> null
        }
    }

    private fun Notification.skeletonVerificationIcon(
        imageModelProvider: ImageModelProvider
    ): ImageModel? =
        verificationIcon()?.let {
            imageModelProvider.getImageModel(it, SmallSquare, skeletonImageTransform)
        }

    private fun Notification.Builder.extractStyleContent(
        notification: Notification,
        contentBuilder: PromotedNotificationContentModel.Builder,
        imageModelProvider: ImageModelProvider,
        systemUiContext: Context,
    ) {
        val style = this.style
        contentBuilder.style =
            when (style) {
                null -> Style.Base
                is BigTextStyle -> {
                    Style.BigText
                }
                is CallStyle -> {
                    extractCallStyleContent(notification, contentBuilder, imageModelProvider)
                    Style.Call
                }
                is ProgressStyle -> {
                    style.extractContent(contentBuilder)
                    Style.Progress
                }
                is MetricStyle -> {
                    contentBuilder.metrics = style.extractMetrics(systemUiContext).toList()
                    if (style.metrics.size == 1) Style.MetricSingle else Style.Metric
                }
                else -> {
                    Style.Ineligible
                }
            }
    }

    private fun extractCallStyleContent(
        notification: Notification,
        contentBuilder: PromotedNotificationContentModel.Builder,
        imageModelProvider: ImageModelProvider,
    ) {
        contentBuilder.verificationIcon = notification.skeletonVerificationIcon(imageModelProvider)
        contentBuilder.verificationText = notification.verificationText()
    }

    private fun ProgressStyle.extractContent(
        contentBuilder: PromotedNotificationContentModel.Builder
    ) {
        // TODO: Create NotificationProgressModel.toSkeleton, or something similar.
        contentBuilder.newProgress =
            createProgressModel(
                0xffffffff.toInt(),
                0xff000000.toInt(),
                { Notification.COLOR_DEFAULT },
            )
    }

    companion object {
        private const val LOG_NOT_EXTRACTED = false
    }
}
