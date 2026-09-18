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

package com.android.systemui.screencapture.sharescreen.domain.interactor

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.media.projection.IAppContentProjectionCallback
import android.media.projection.IAppContentProjectionSession
import android.media.projection.IMediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.media.projection.ReviewGrantedConsentResult
import android.media.projection.StopReason
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.domain.interactor.ScreenCaptureRecentTaskInteractor
import com.android.systemui.util.AsyncActivityLauncher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

@ScreenCaptureUiScope
class ShareScreenUiInteractor
@Inject
constructor(
    private val recentTaskInteractor: ScreenCaptureRecentTaskInteractor,
    private val asyncActivityLauncher: AsyncActivityLauncher,
    private val mediaProjectionHelper: MediaProjectionServiceHelperWrapper,
    @param:Application private val context: Context,
) {

    sealed class SharingState {
        object NotStarted : SharingState()

        data class Approved(val projection: IMediaProjection) : SharingState()

        object Denied : SharingState()
    }

    private val _sharingState = MutableStateFlow<SharingState>(SharingState.NotStarted)
    val sharingState = _sharingState.asStateFlow()

    private var projection: IMediaProjection? = null
    /** Tracks which display the current [projection] token is authorized for. */
    private var authorizedDisplayId: Int? = null

    private var reviewGrantedConsentRequired: Boolean = false
    private lateinit var hostUserHandle: UserHandle
    var uid: Int = -1
        private set

    var packageName: String = ""
        private set

    private var initialDisplayId: Int = -1

    var config: MediaProjectionConfig? = null
        private set

    fun initialize(
        projection: IMediaProjection?,
        reviewGrantedConsentRequired: Boolean,
        hostUserHandle: UserHandle,
        uid: Int,
        packageName: String,
        initialDisplayId: Int,
        config: MediaProjectionConfig?,
    ) {
        this.projection = projection
        // If a projection was provided upfront, we assume it's for the initial display.
        this.authorizedDisplayId = if (projection != null) initialDisplayId else null
        this.reviewGrantedConsentRequired = reviewGrantedConsentRequired
        this.hostUserHandle = hostUserHandle
        this.uid = uid
        this.packageName = packageName
        this.initialDisplayId = initialDisplayId
        this.config = config
    }

    /**
     * Returns the current [IMediaProjection] or creates a new one for the given [displayId] if it
     * doesn't exist or is authorized for a different display.
     */
    private fun getOrCreateProjection(displayId: Int, mediaProjectionType: Int): IMediaProjection {
        val currentProjection = projection
        // Reuse the existing projection if it exists and matches the requested display.
        if (currentProjection != null && displayId == authorizedDisplayId) {
            return currentProjection
        }

        val newProjection =
            mediaProjectionHelper.createOrReuseProjection(
                uid,
                packageName,
                reviewGrantedConsentRequired,
                displayId,
                mediaProjectionType,
            )

        projection = newProjection
        authorizedDisplayId = displayId
        return newProjection
    }

    /**
     * Called when the user approves sharing of a specific piece of app content (e.g. a browser
     * tab).
     */
    fun onAppContentSharingApproved(
        contentId: Int,
        appContentCallback: IAppContentProjectionCallback,
        isAudioRequested: Boolean,
    ) {
        try {
            val appContentSession =
                object : IAppContentProjectionSession.Stub() {
                    // This is an anonymous implementation of IAppContentProjectionSession.Stub.
                    // It serves as the local Binder object for the projection session, allowing
                    // the remote app (the app being projected) to notify the SystemUI service
                    // when the projection session stops.
                    override fun notifySessionStop() {
                        Log.d(TAG, "App content projection session stopped by remote app.")
                        try {
                            projection?.stop(StopReason.STOP_HOST_APP)
                        } catch (e: RemoteException) {
                            Log.e(TAG, "Failed to stop projection on session stop", e)
                        }
                        _sharingState.value = SharingState.Denied
                    }
                }

            val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
            val projectionToken =
                projectionManager.createProjectionForAppContent(
                    uid,
                    packageName,
                    appContentSession,
                    contentId,
                    isAudioRequested,
                    appContentCallback,
                )!!
            projection = projectionToken
            authorizedDisplayId = initialDisplayId

            mediaProjectionHelper.setReviewedConsentIfNeeded(
                ReviewGrantedConsentResult.RECORD_CONTENT_TASK,
                reviewGrantedConsentRequired,
                projectionToken,
            )
            _sharingState.value = SharingState.Approved(projectionToken)
        } catch (e: Exception) {
            Log.e(TAG, "Error granting projection permission for app content", e)
            _sharingState.value = SharingState.Denied
        }
    }

    /** Called when the user approves sharing of a single application, identified by its task ID. */
    suspend fun onAppSharingApproved(taskId: Int) {
        try {
            val recentTasks = recentTaskInteractor.recentTasks.first()
            val task = recentTasks.firstOrNull { it.taskId == taskId }

            if (task == null || task.baseIntent == null) {
                // The task is no longer running, so we can't share it.
                Log.w(TAG, "Task info not found for taskId: $taskId")
                _sharingState.value = SharingState.Denied
                return
            }

            // Create a new LaunchCookie and ActivityOptions to perform the security handshake.
            val launchCookie = ActivityOptions.LaunchCookie(MEDIA_PROJECTION_LAUNCH_TOKEN)

            val projection =
                getOrCreateProjection(initialDisplayId, MediaProjectionManager.TYPE_SCREEN_CAPTURE)

            if (task.isForegroundTask && task.component?.packageName == packageName) {
                // The task is already in the foreground and belongs to the host app, so we don't
                // need to launch it again. Directly approve the projection for this taskId.
                try {
                    projection.setLaunchCookie(launchCookie)
                    projection.taskId = taskId
                    mediaProjectionHelper.setReviewedConsentIfNeeded(
                        ReviewGrantedConsentResult.RECORD_CONTENT_TASK,
                        reviewGrantedConsentRequired,
                        projection,
                    )
                    _sharingState.value = SharingState.Approved(projection)
                } catch (e: Exception) {
                    Log.e(TAG, "Error granting projection permission for task", e)
                    _sharingState.value = SharingState.Denied
                }
                return
            }

            val options = ActivityOptions.makeBasic()
            options.launchCookie = launchCookie.binder

            val launchIntent =
                if (task.component?.packageName == packageName) {
                    // If it's the host app, use the standard launch intent to ensure it brings the
                    // existing task to the front correctly without triggering new window logic.
                    val userContext = context.createContextAsUser(hostUserHandle, /* flags= */ 0)
                    userContext.packageManager.getLaunchIntentForPackage(packageName)
                } else {
                    task.baseIntent
                }

            if (launchIntent == null) {
                Log.w(TAG, "Launch intent not found for task: taskId=$taskId")
                _sharingState.value = SharingState.Denied
                return
            }

            // Bring the task to be captured to the front using the new cookie, and finish this
            // activity in the callback once the app is started.
            asyncActivityLauncher.startActivityAsUser(
                launchIntent,
                hostUserHandle,
                options.toBundle(),
            ) { waitResult ->
                if (
                    waitResult.result == ActivityManager.START_SUCCESS ||
                        waitResult.result == ActivityManager.START_TASK_TO_FRONT ||
                        waitResult.result == ActivityManager.START_DELIVERED_TO_TOP
                ) {
                    try {
                        // Configure the projection to capture a specific task using the same
                        // cookie.
                        projection.setLaunchCookie(launchCookie)
                        projection.taskId = taskId
                        mediaProjectionHelper.setReviewedConsentIfNeeded(
                            ReviewGrantedConsentResult.RECORD_CONTENT_TASK,
                            reviewGrantedConsentRequired,
                            projection,
                        )
                        _sharingState.value = SharingState.Approved(projection)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error granting projection permission for task", e)
                        _sharingState.value = SharingState.Denied
                    }
                } else {
                    Log.w(
                        TAG,
                        "Failed to launch activity for task: taskId=$taskId, result=" +
                            "${waitResult.result}",
                    )
                    _sharingState.value = SharingState.Denied
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error granting projection permission for task", e)
            _sharingState.value = SharingState.Denied
        }
    }

    /** Called when the user approves sharing of an entire display. */
    fun onDisplaySharingApproved(displayId: Int) {
        try {
            val projectionToUse =
                getOrCreateProjection(displayId, MediaProjectionManager.TYPE_SCREEN_CAPTURE)

            mediaProjectionHelper.setReviewedConsentIfNeeded(
                ReviewGrantedConsentResult.RECORD_CONTENT_DISPLAY,
                reviewGrantedConsentRequired,
                projectionToUse,
            )
            _sharingState.value = SharingState.Approved(projectionToUse)
        } catch (e: RemoteException) {
            Log.e(TAG, "Error granting projection permission for display $displayId", e)
            _sharingState.value = SharingState.Denied
        }
    }

    fun onClose() {
        mediaProjectionHelper.setReviewedConsentIfNeeded(
            ReviewGrantedConsentResult.RECORD_CANCEL,
            reviewGrantedConsentRequired,
            projection,
        )
        _sharingState.value = SharingState.Denied
    }

    companion object {
        private const val TAG = "ShareScreenUiInteractor"
        private const val MEDIA_PROJECTION_LAUNCH_TOKEN = "media_projection_launch_token"
    }
}
