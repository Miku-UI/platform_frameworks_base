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

package com.android.systemui.statusbar.notification.promoted.shared.model

import android.annotation.CurrentTimeMillisLong
import android.annotation.ElapsedRealtimeLong
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.ColorInt
import com.android.internal.widget.NotificationProgressModel
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import com.android.systemui.statusbar.notification.row.ImageResult
import com.android.systemui.statusbar.notification.row.LazyImage
import com.android.systemui.statusbar.notification.row.shared.ImageModel

data class PromotedNotificationContentModels(
    /** The potentially redacted version of the content that will be exposed to the public */
    val publicVersion: PromotedNotificationContentModel,
    /** The unredacted version of the content that will be kept private */
    val privateVersion: PromotedNotificationContentModel,
) {
    val key: String
        get() = privateVersion.identity.key

    init {
        check(publicVersion.identity.key == privateVersion.identity.key) {
            "public and private models must have the same key"
        }
    }

    fun toRedactedString(): String {
        val publicVersionString =
            "==privateVersion".takeIf { privateVersion === publicVersion }
                ?: publicVersion.toRedactedString()
        return ("PromotedNotificationContentModels(" +
            "privateVersion=${privateVersion.toRedactedString()}, " +
            "publicVersion=$publicVersionString)")
    }
}

/**
 * The content needed to render a promoted notification to surfaces besides the notification stack,
 * like the skeleton view on AOD or the status bar chip.
 */
data class PromotedNotificationContentModel(
    val identity: Identity,

    // for all styles:
    /**
     * True if this notification was automatically promoted - see [AutomaticPromotionCoordinator].
     */
    val wasPromotedAutomatically: Boolean,
    val skeletonNotifIcon: NotifIcon?,
    val iconLevel: Int,
    val appName: CharSequence?,
    val subText: CharSequence?,
    val shortCriticalText: String?,
    /**
     * The timestamp associated with the notification. Null if the timestamp should not be
     * displayed.
     */
    val time: When?,
    val lastAudiblyAlertedMs: Long,
    val profileBadgeBitmap: Bitmap?,
    val title: CharSequence?,
    val text: CharSequence?,
    val skeletonLargeIcon: ImageModel?,
    val oldProgress: OldProgress?,
    val colors: Colors,
    val style: Style,

    // for CallStyle:
    val verificationIcon: ImageModel?,
    val verificationText: CharSequence?,

    // for ProgressStyle:
    val newProgress: NotificationProgressModel?,
    val notificationView: View?,
) {
    class Builder(val key: String) {
        var wasPromotedAutomatically: Boolean = false
        var skeletonNotifIcon: NotifIcon? = null
        var iconLevel: Int = 0
        var appName: CharSequence? = null
        var subText: CharSequence? = null
        var time: When? = null
        var shortCriticalText: String? = null
        var lastAudiblyAlertedMs: Long = 0L
        var profileBadgeBitmap: Bitmap? = null
        var title: CharSequence? = null
        var text: CharSequence? = null
        var skeletonLargeIcon: ImageModel? = null
        var oldProgress: OldProgress? = null
        var style: Style = Style.Ineligible
        var colors: Colors = Colors(backgroundColor = 0, primaryTextColor = 0)

        // for CallStyle:
        var verificationIcon: ImageModel? = null
        var verificationText: CharSequence? = null

        // for ProgressStyle:
        var newProgress: NotificationProgressModel? = null

        var notificationView: View? = null

        fun build() =
            PromotedNotificationContentModel(
                identity = Identity(key, style),
                wasPromotedAutomatically = wasPromotedAutomatically,
                skeletonNotifIcon = skeletonNotifIcon,
                iconLevel = iconLevel,
                appName = appName,
                subText = subText,
                shortCriticalText = shortCriticalText,
                time = time,
                lastAudiblyAlertedMs = lastAudiblyAlertedMs,
                profileBadgeBitmap = profileBadgeBitmap,
                title = title,
                text = text,
                skeletonLargeIcon = skeletonLargeIcon,
                oldProgress = oldProgress,
                colors = colors,
                style = style,
                verificationIcon = verificationIcon,
                verificationText = verificationText,
                newProgress = newProgress,
                notificationView = notificationView,
            )
    }

    data class Identity(val key: String, val style: Style)

    sealed class NotifIcon {
        data class SmallIcon(val imageModel: ImageModel) : NotifIcon()

        data class AppIcon(val drawable: Drawable) : NotifIcon()
    }

    /** The timestamp associated with a notification, along with the mode used to display it. */
    sealed class When {
        /** Show the notification's time as a timestamp. */
        data class Time(@CurrentTimeMillisLong val currentTimeMillis: Long) : When()

        /**
         * Show the notification's time as a chronometer that counts up or down (based on
         * [isCountDown]) to [elapsedRealtimeMillis].
         */
        data class Chronometer(
            @ElapsedRealtimeLong val elapsedRealtimeMillis: Long,
            val isCountDown: Boolean,
        ) : When()
    }

    /** The colors used to display the notification. */
    data class Colors(@ColorInt val backgroundColor: Int, @ColorInt val primaryTextColor: Int)

    /** The fields needed to render the old-style progress bar. */
    data class OldProgress(val progress: Int, val max: Int, val isIndeterminate: Boolean)

    /** The promotion-eligible style of a notification, or [Style.Ineligible] if not. */
    enum class Style {
        Base, // style == null
        CollapsedBase, // style == null
        BigPicture,
        BigText,
        Call,
        CollapsedCall,
        Progress,
        Ineligible,
    }

    fun toRedactedString(): String {
        return ("PromotedNotificationContentModel(" +
            "identity=$identity, " +
            "wasPromotedAutomatically=$wasPromotedAutomatically, " +
            "skeletonNotifIcon=${skeletonNotifIcon?.toRedactedString()}, " +
            "appName=$appName, " +
            "subText=${subText?.toRedactedString()}, " +
            "shortCriticalText=$shortCriticalText, " +
            "time=$time, " +
            "lastAudiblyAlertedMs=$lastAudiblyAlertedMs, " +
            "profileBadgeBitmap=$profileBadgeBitmap, " +
            "title=${title?.toRedactedString()}, " +
            "text=${text?.toRedactedString()}, " +
            "skeletonLargeIcon=${skeletonLargeIcon?.toRedactedString()}, " +
            "oldProgress=$oldProgress, " +
            "colors=$colors, " +
            "style=$style, " +
            "verificationIcon=$verificationIcon, " +
            "verificationText=$verificationText, " +
            "newProgress=$newProgress)")
    }

    private fun CharSequence.toRedactedString(): String = "[$length]"

    private fun NotifIcon.toRedactedString(): String =
        when (this) {
            is NotifIcon.SmallIcon -> "SmallIcon(${imageModel.toRedactedString()})"
            is NotifIcon.AppIcon -> "AppIcon([${drawable.javaClass.simpleName}])"
        }

    private fun ImageModel.toRedactedString(): String {
        return when (this) {
            is LazyImage -> this.toRedactedString()
            else -> this.toString()
        }
    }

    private fun LazyImage.toRedactedString(): String {
        return ("LazyImage(" +
            "icon=[${icon.javaClass.simpleName}], " +
            "sizeClass=$sizeClass, " +
            "transform=$transform, " +
            "result=${result?.toRedactedString()})")
    }

    private fun ImageResult.toRedactedString(): String {
        return when (this) {
            is ImageResult.Empty -> this.toString()
            is ImageResult.Image -> "Image(drawable=[${drawable.javaClass.simpleName}])"
        }
    }

    companion object {
        @JvmStatic fun featureFlagEnabled(): Boolean = PromotedNotificationUi.isEnabled
    }
}
