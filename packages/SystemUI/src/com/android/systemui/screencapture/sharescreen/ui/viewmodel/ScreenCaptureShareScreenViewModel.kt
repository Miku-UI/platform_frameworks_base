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

package com.android.systemui.screencapture.sharescreen.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_APP
import android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_APP_CONTENT
import android.media.projection.MediaProjectionConfig.PROJECTION_SOURCE_DISPLAY
import android.os.UserHandle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureTarget
import com.android.systemui.screencapture.common.ui.viewmodel.AppContentsViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.AudioSwitchViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DisplaysViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.common.ui.viewmodel.RecentTasksViewModel
import com.android.systemui.screencapture.sharescreen.domain.interactor.ShareScreenUiInteractor
import com.android.systemui.statusbar.quickactions.sharescreen.domain.interactor.ShareScreenPrivacyIndicatorInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.coroutineScope

class ScreenCaptureShareScreenViewModel
@AssistedInject
constructor(
    private val packageManager: PackageManager,
    private val drawableLoaderViewModel: DrawableLoaderViewModel,
    private val shareScreenUiInteractor: ShareScreenUiInteractor,
    private val mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
    @Assisted("thumbnailWidthPx") private val thumbnailWidthPx: Int,
    @Assisted("thumbnailHeightPx") private val thumbnailHeightPx: Int,
    @Assisted("iconSizePx") private val iconSizePx: Int,
    appContentsViewModelFactory: AppContentsViewModel.Factory,
    private val recentTasksViewModel: RecentTasksViewModel,
    private val displaysViewModel: DisplaysViewModel,
    private val accessibilityManager: AccessibilityManager,
    private val context: Context,
    private val shareScreenPrivacyIndicatorInteractor: ShareScreenPrivacyIndicatorInteractor,
) :
    HydratedActivatable(enableEnqueuedActivations = true),
    DrawableLoaderViewModel by drawableLoaderViewModel {
    private val appContentsViewModel =
        appContentsViewModelFactory.create(
            shareScreenUiInteractor.packageName,
            thumbnailWidthPx,
            thumbnailHeightPx,
            iconSizePx,
        )

    val isAppContentSharingEnabled: Boolean
        get() =
            shareScreenUiInteractor.config?.isSourceEnabled(PROJECTION_SOURCE_APP_CONTENT) ?: true

    val isAppSharingEnabled: Boolean
        get() = shareScreenUiInteractor.config?.isSourceEnabled(PROJECTION_SOURCE_APP) ?: true

    val isEntireScreenSharingEnabled: Boolean
        get() = shareScreenUiInteractor.config?.isSourceEnabled(PROJECTION_SOURCE_DISPLAY) ?: true

    val isAudioRequested: Boolean
        get() = shareScreenUiInteractor.config?.isAudioRequested ?: false

    private val initialSource = shareScreenUiInteractor.config?.initiallySelectedSource ?: -1

    var currentTargetsModel by
        mutableStateOf(
            when {
                // Prioritize initialSource if it's enabled.
                initialSource == PROJECTION_SOURCE_APP_CONTENT && isAppContentSharingEnabled ->
                    appContentsViewModel
                initialSource == PROJECTION_SOURCE_APP && isAppSharingEnabled ->
                    recentTasksViewModel
                initialSource == PROJECTION_SOURCE_DISPLAY && isEntireScreenSharingEnabled ->
                    displaysViewModel
                // Fallback to the first available enabled source in order: AppContent, App, Display
                isAppContentSharingEnabled -> appContentsViewModel
                isAppSharingEnabled -> recentTasksViewModel
                isEntireScreenSharingEnabled -> displaysViewModel
                // Ultimate fallback if nothing is set.
                else -> appContentsViewModel
            }
        )
        private set

    var isUiVisible by mutableStateOf(true)
        private set

    val requestingAppName: String by lazy {
        try {
            val appInfo =
                packageManager.getApplicationInfoAsUser(
                    shareScreenUiInteractor.packageName,
                    /* flags= */ 0,
                    UserHandle.getUserId(shareScreenUiInteractor.uid),
                )
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(
                TAG,
                "Could not find app name for package ${shareScreenUiInteractor.packageName}",
                e,
            )
            shareScreenUiInteractor.packageName
        }
    }

    fun setTargetViewModel(type: ScreenCaptureTarget) {
        currentTargetsModel =
            when (type) {
                is ScreenCaptureTarget.App -> {
                    mediaProjectionMetricsLogger.notifyAppSelectorDisplayed(
                        shareScreenUiInteractor.uid
                    )
                    recentTasksViewModel
                }
                // TODO(b/471059930) Extend metrics for large screen sharing.
                is ScreenCaptureTarget.AppContent -> appContentsViewModel
                is ScreenCaptureTarget.Fullscreen -> displaysViewModel
                else ->
                    throw IllegalArgumentException("Unsupported ScreenCaptureTarget type: $type")
            }
        // Reset the audio state on the newly selected view model.
        (currentTargetsModel as AudioSwitchViewModel).setCaptureAudio(false)
    }

    fun onShareClicked() {
        // Hide the UI immediately for responsive feedback.
        isUiVisible = false
        var announcement: String? = null
        var sharingLabel = ""
        var sharingType: ShareScreenPrivacyIndicatorInteractor.SharingType? = null

        when (val currentModel = currentTargetsModel) {
            is RecentTasksViewModel -> {
                currentModel.selectedTarget.value?.let {
                    enqueueOnActivatedScope {
                        shareScreenUiInteractor.onAppSharingApproved(it.model.taskId)
                    }
                    sharingLabel = it.label?.getOrNull()?.toString() ?: ""
                    sharingType = ShareScreenPrivacyIndicatorInteractor.SharingType.APP
                    announcement =
                        context.getString(R.string.screen_share_a11y_sharing_app, sharingLabel)
                }
            }
            is AppContentsViewModel -> {
                currentModel.selectedTarget.value?.let {
                    val callback = currentModel.projectionCallback.value?.get()
                    // The callback is retrieved from a [WeakReference] and may be null if it was
                    // garbage collected.
                    if (callback == null) {
                        Log.e(TAG, "Projection callback is not available")
                        return@let
                    }
                    shareScreenUiInteractor.onAppContentSharingApproved(
                        it.model.contentId,
                        callback,
                        currentModel.captureAudio.value,
                    )
                    sharingLabel = it.label?.getOrNull()?.toString() ?: ""
                    sharingType = ShareScreenPrivacyIndicatorInteractor.SharingType.TAB
                    announcement =
                        context.getString(R.string.screen_share_a11y_sharing_tab, sharingLabel)
                }
            }
            is DisplaysViewModel -> {
                currentModel.selectedTarget.value?.let {
                    shareScreenUiInteractor.onDisplaySharingApproved(it.model.displayId)
                    sharingLabel = it.label?.getOrNull()?.toString() ?: ""
                    sharingType = ShareScreenPrivacyIndicatorInteractor.SharingType.DISPLAY
                    announcement =
                        context.getString(R.string.screen_share_a11y_sharing_display, sharingLabel)
                }
            }
            else -> throw IllegalStateException("Unsupported TargetsViewModel type: $currentModel")
        }

        sharingType?.let { type ->
            shareScreenPrivacyIndicatorInteractor.assignSharingInfo(type, sharingLabel)
        }

        announcement?.let {
            if (accessibilityManager.isEnabled) {
                accessibilityManager.sendAccessibilityEvent(
                    AccessibilityEvent(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED).apply {
                        packageName = context.packageName
                        className = Toast::class.java.name
                        text.add(it)
                    }
                )
            }
        }
    }

    fun onCloseClicked() {
        shareScreenUiInteractor.onClose()
    }

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("AppContentsViewModel") { appContentsViewModel.activate() }
            launchTraced("RecentTasksViewModel") { recentTasksViewModel.activate() }
            launchTraced("DisplaysViewModel") { displaysViewModel.activate() }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("thumbnailWidthPx") thumbnailWidthPx: Int,
            @Assisted("thumbnailHeightPx") thumbnailHeightPx: Int,
            @Assisted("iconSizePx") iconSizePx: Int,
        ): ScreenCaptureShareScreenViewModel
    }

    companion object {
        private const val TAG = "ScreenCaptureShareScreenViewModel"
    }
}
