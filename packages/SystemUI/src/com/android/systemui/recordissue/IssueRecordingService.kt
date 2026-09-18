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

package com.android.systemui.recordissue

import android.app.IActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.media.projection.StopReason
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.pipeline.domain.interactor.PanelInteractor
import com.android.systemui.res.R
import com.android.systemui.screenrecord.RecordingServiceStrings
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.service.ComponentService
import com.android.systemui.screenrecord.shared.model.ScreenRecording
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import com.android.systemui.settings.UserContextProvider
import com.android.traceur.MessageConstants.INTENT_EXTRA_TRACE_TYPE
import com.android.traceur.PresetTraceConfigs
import com.android.traceur.TraceConfig
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class IssueRecordingService
@Inject
constructor(
    @param:Application private val mainCoroutineScope: CoroutineScope,
    @param:Background private val backgroundContext: CoroutineContext,
    notificationManager: NotificationManager,
    userContextProvider: UserContextProvider,
    dialogTransitionAnimator: DialogTransitionAnimator,
    panelInteractor: PanelInteractor,
    private val issueRecordingState: IssueRecordingState,
    traceurConnectionProvider: TraceurConnection.Provider,
    iActivityManager: IActivityManager,
    screenRecordingStartTimeStore: ScreenRecordingStartTimeStore,
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
) : ComponentService() {

    private val traceurConnection: TraceurConnection = traceurConnectionProvider.create()
    private var screenRecordingNotificationId: Int? = null

    private val session =
        IssueRecordingServiceSession(
            coroutineScope,
            backgroundContext,
            dialogTransitionAnimator,
            panelInteractor,
            traceurConnection,
            issueRecordingState,
            iActivityManager,
            notificationManager,
            userContextProvider,
            screenRecordingStartTimeStore,
            screenRecordingServiceInteractor,
        )

    /**
     * It is necessary to bind to IssueRecordingService from the Record Issue Tile because there are
     * instances where this service is not created in the same user profile as the record issue tile
     * aka, headless system user mode. In those instances, the TraceurConnection will be considered
     * a leak in between notification actions unless the tile is bound to this service to keep it
     * alive.
     */
    override fun onBind(intent: Intent): IBinder? {
        traceurConnection.doBind()
        return null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        traceurConnection.doUnBind()
        return super.onUnbind(intent)
    }

    override fun onCreate() {
        super.onCreate()

        mainCoroutineScope.launch {
            screenRecordingServiceInteractor.screenRecordings.collect {
                savedRecording: ScreenRecording ->
                issueRecordingState.isRecording = false
                val uri = savedRecording.uri
                if (uri != null) {
                    onRecordingSaved(uri)
                } else {
                    stopSelf()
                }
            }
        }

        mainCoroutineScope.launch {
            screenRecordingServiceInteractor.status.collect { status ->
                when (status) {
                    is ScreenRecordingStatus.Stopped -> {
                        if (status.reason == StopReason.STOP_ERROR) {
                            issueRecordingState.isRecording = false
                            stopSelf()
                        }
                    }
                    // Non-error stops are handled when the file is saved and screenRecordings
                    // are collected.
                    else -> {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "handling action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val notificationId = UUID.randomUUID().mostSignificantBits.toInt()
                screenRecordingNotificationId = notificationId
                val screenRecord = intent.getBooleanExtra(EXTRA_SCREEN_RECORD, false)
                with(session) {
                    traceConfig =
                        intent.getParcelableExtra(INTENT_EXTRA_TRACE_TYPE, TraceConfig::class.java)
                            ?: PresetTraceConfigs.getDefaultConfig()
                    takeBugReport = intent.getBooleanExtra(EXTRA_BUG_REPORT, false)
                    this.screenRecord = screenRecord
                    start(notificationId)
                }
                if (!screenRecord) {
                    // If we don't want to record the screen, the ACTION_SHOW_START_NOTIF action
                    // will circumvent the ScreenRecordingService's screen recording start code.
                    return super.onStartCommand(Intent(ACTION_SHOW_START_NOTIF), flags, startId)
                }
            }
            ACTION_STOP -> {
                session.stop(intent.getIntExtra(EXTRA_STOP_REASON, StopReason.STOP_UNKNOWN))
                stopSelf()
            }
            else -> {}
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * If the user chooses to create a bugreport, we do not want to make them click share twice. To
     * avoid that, the code immediately triggers the bugreport flow which will handle the rest.
     */
    private fun onRecordingSaved(recordingUri: Uri) {
        val notificationId = screenRecordingNotificationId

        if (notificationId != null) {
            if (session.takeBugReport) {
                session.share(notificationId, recordingUri)
            }
        }
        stopSelf()
    }

    companion object {
        private const val TAG = "IssueRecordingService"
        private const val CHANNEL_ID = "issue_record"
        const val EXTRA_SCREEN_RECORD = "extra_screenRecord"
        const val EXTRA_BUG_REPORT = "extra_bugReport"
        const val EXTRA_SHOW_SECONDS = "extra_showSeconds"
        const val ACTION_START = "com.android.systemui.recordissue.ACTION_START"
        const val ACTION_STOP = "com.android.systemui.recordissue.ACTION_STOP"
        const val ACTION_SHOW_START_NOTIF = "com.android.systemui.recordissue.START_NOTIF"
        const val ACTION_SHARE = "com.android.systemui.recordissue.ACTION_SHARE"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_STOP_REASON =
            "com.android.systemui.recordissue.IssueRecordingService.EXTRA_STOP_REASON"

        /**
         * Get an intent to stop the issue recording service.
         *
         * @param context Context from the requesting activity
         * @return
         */
        fun getStopIntent(context: Context): Intent =
            Intent(context, IssueRecordingService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(Intent.EXTRA_USER_HANDLE, context.userId)

        /**
         * Get an intent to start the issue recording service.
         *
         * @param context Context from the requesting activity
         */
        fun getStartIntent(
            context: Context,
            traceConfig: TraceConfig,
            screenRecord: Boolean,
            bugReport: Boolean,
        ): Intent =
            Intent(context, IssueRecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(INTENT_EXTRA_TRACE_TYPE, traceConfig)
                .putExtra(EXTRA_SCREEN_RECORD, screenRecord)
                .putExtra(EXTRA_BUG_REPORT, bugReport)
                .putExtra(EXTRA_SHOW_SECONDS, true)
    }
}

private class IrsStrings(private val res: Resources) : RecordingServiceStrings(res) {
    override val title
        get() = res.getString(R.string.issuerecord_title)

    override val notificationChannelDescription
        get() = res.getString(R.string.issuerecord_channel_description)

    override val startErrorResId
        get() = R.string.issuerecord_start_error

    override val startError
        get() = res.getString(R.string.issuerecord_start_error)

    override val saveErrorResId
        get() = R.string.issuerecord_save_error

    override val saveError
        get() = res.getString(R.string.issuerecord_save_error)

    override val ongoingRecording
        get() = res.getString(R.string.issuerecord_ongoing_screen_only)

    override val backgroundProcessingLabel
        get() = res.getString(R.string.issuerecord_background_processing_label)

    override val saveTitle
        get() = res.getString(R.string.issuerecord_save_title)
}
