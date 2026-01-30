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

package com.android.systemui.media.remedia.data.model

import android.app.PendingIntent
import android.media.session.MediaSession
import com.android.internal.logging.InstanceId
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.media.controls.shared.model.MediaButton
import com.android.systemui.media.controls.shared.model.MediaDeviceData
import com.android.systemui.media.controls.shared.model.MediaNotificationAction
import com.android.systemui.media.controls.shared.model.SuggestionData
import com.android.systemui.media.remedia.shared.model.MediaColorScheme
import com.android.systemui.media.remedia.shared.model.MediaSessionState

/** Data model representing a media data. */
data class MediaDataModel(
    /** Instance ID for logging purposes */
    val instanceId: InstanceId,
    /** The UID of the app, used for logging. */
    val appUid: Int,
    /** Package name of the app that's posting the media, used for logging. */
    val packageName: String,
    val appName: String,
    val appIcon: Icon,
    val background: Icon?,
    val title: String,
    val subtitle: String,
    val colorScheme: MediaColorScheme?,
    /** List of generic action buttons for the media player, based on notification actions */
    val notificationActions: List<MediaNotificationAction>,
    /**
     * Semantic actions buttons, based on the PlaybackState of the media session. If present, these
     * actions will be preferred in the UI over [notificationActions]
     */
    val playbackStateActions: MediaButton?,
    /** Where the media is playing: phone, headphones, ear buds, remote session. */
    val outputDevice: MediaDeviceData?,
    /** Action to perform when the media player is tapped. */
    val clickIntent: PendingIntent?,
    val state: MediaSessionState,
    val durationMs: Long,
    val positionMs: Long,
    val canBeScrubbed: Boolean,
    val canBeDismissed: Boolean,
    /**
     * An active player represents a current media session that has not timed out or been swiped
     * away by the user.
     */
    val isActive: Boolean,
    /**
     * Indicates that this player is a resumption player (ie. It only shows a play actions which
     * will start the app and start playing).
     */
    val isResume: Boolean,
    /** Action that should be performed to restart a non active session. */
    val resumeAction: Runnable?,
    val isExplicit: Boolean,
    /** Device suggestions data */
    val suggestionData: SuggestionData?,
    val token: MediaSession.Token?,
)
