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
import android.content.Intent
import android.media.projection.StopReason
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import android.view.Display
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.qs.pipeline.domain.interactor.PanelInteractor
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParameters
import com.android.systemui.settings.UserContextProvider
import com.android.traceur.PresetTraceConfigs
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHELL_PACKAGE = "com.android.shell"
private const val NOTIFY_SESSION_ENDED_SETTING = "should_notify_trace_session_ended"
private const val DISABLED = 0

/**
 * This class exists to unit test the business logic encapsulated in IssueRecordingService. Android
 * specifically calls out that there is no supported way to test IntentServices here:
 * https://developer.android.com/training/testing/other-components/services, and mentions that the
 * best way to add unit tests, is to introduce a separate class containing the business logic of
 * that service, and test the functionality via that class.
 */
class IssueRecordingServiceSession(
    private val coroutineScope: CoroutineScope,
    private val backgroundContext: CoroutineContext,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val panelInteractor: PanelInteractor,
    private val traceurConnection: TraceurConnection,
    private val issueRecordingState: IssueRecordingState,
    private val iActivityManager: IActivityManager,
    private val notificationManager: NotificationManager,
    private val userContextProvider: UserContextProvider,
    private val startTimeStore: ScreenRecordingStartTimeStore,
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
) {
    var takeBugReport = false
    var traceConfig = PresetTraceConfigs.getDefaultConfig()
    var screenRecord = false

    fun start(notificationId: Int) {
        coroutineScope.launch {
            withContext(backgroundContext) {
                traceurConnection.startTracing(traceConfig)
                issueRecordingState.isRecording = true

                val params =
                    ScreenRecordingParameters(
                        captureTarget = null,
                        displayId = Display.DEFAULT_DISPLAY,
                        audioSource = ScreenRecordingAudioSource.NONE,
                        shouldShowTaps = true,
                        shouldShowSeconds = true,
                        notificationId = notificationId,
                    )

                screenRecordingServiceInteractor.startRecording(params)
            }
        }
    }

    fun stop(@StopReason stopReason: Int) {
        coroutineScope.launch {
            withContext(backgroundContext) {
                if (traceConfig.longTrace) {
                    Settings.Global.putInt(
                        userContextProvider.userContext.contentResolver,
                        NOTIFY_SESSION_ENDED_SETTING,
                        DISABLED,
                    )
                }
                traceurConnection.stopTracing()
                issueRecordingState.isRecording = false

                screenRecordingServiceInteractor.stopRecording(stopReason)
            }
        }
    }

    fun share(notificationId: Int, screenRecording: Uri?) {
        coroutineScope.launch {
            withContext(backgroundContext) {
                notificationManager.cancelAsUser(
                    null,
                    notificationId,
                    UserHandle(userContextProvider.userContext.userId),
                )
                val screenRecordingUris: List<Uri> =
                    mutableListOf<Uri>().apply {
                        screenRecording?.let { add(it) }
                        if (traceConfig.winscope && screenRecord) {
                            startTimeStore.getFileUri(userContextProvider.userContext)?.let {
                                add(it)
                            }
                        }
                    }
                if (takeBugReport) {
                    screenRecordingUris.forEach {
                        userContextProvider.userContext.grantUriPermission(
                            SHELL_PACKAGE,
                            it,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    iActivityManager.requestBugReportWithExtraAttachments(screenRecordingUris)
                } else {
                    traceurConnection.shareTraces(screenRecordingUris)
                }
            }
        }

        dialogTransitionAnimator.disableAllCurrentDialogsExitAnimations()
        panelInteractor.collapsePanels()
    }
}
