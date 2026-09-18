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

package com.android.systemui.screencapture.ui

import android.content.Intent
import android.media.projection.IMediaProjection
import android.media.projection.IMediaProjectionManager.EXTRA_USER_REVIEW_GRANTED_CONSENT
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager.EXTRA_MEDIA_PROJECTION
import android.media.projection.MediaProjectionManager.EXTRA_MEDIA_PROJECTION_CONFIG
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.sharescreen.ScreenCaptureShareScreenUiComponent
import com.android.systemui.screencapture.sharescreen.domain.interactor.ShareScreenUiInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * An activity that hosts the pre screen share UI, started from MediaProjectionPermissionActivity.
 */
class ShareScreenActivity
@Inject
constructor(
    private val builder: ScreenCaptureComponent.Builder,
    private val mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
    private val accessibilityManager: AccessibilityManager,
) : ComponentActivity() {

    // Controls the visibility and animation state of the Compose UI.
    private val visibleState = MutableTransitionState(false)
    private var interactor: ShareScreenUiInteractor? = null
    private var hasAnnouncedCancel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        visibleState.targetState = true

        val uid = intent.getIntExtra(EXTRA_HOST_APP_UID, -1)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val reviewGrantedConsentRequired =
            intent.getBooleanExtra(EXTRA_USER_REVIEW_GRANTED_CONSENT, false)
        val projectionBinder = intent.extras?.getBinder(EXTRA_MEDIA_PROJECTION)
        val hostUserHandle: UserHandle? =
            intent.getParcelableExtra(EXTRA_HOST_APP_USER_HANDLE, UserHandle::class.java)
        val config: MediaProjectionConfig? =
            intent.getParcelableExtra(
                EXTRA_MEDIA_PROJECTION_CONFIG,
                MediaProjectionConfig::class.java,
            )

        if (uid == -1 || hostUserHandle == null || packageName == null) {
            Log.d(
                TAG,
                "Invalid intent extras: uid=$uid, hostUserHandle=$hostUserHandle, packageName=$packageName",
            )
            finish()
            return
        }
        val projection = projectionBinder?.let { IMediaProjection.Stub.asInterface(it) }

        val parameters = ScreenCaptureUiParameters.ShareScreen(hostAppUserHandle = hostUserHandle)
        val component = builder.setScope(lifecycleScope).setParameters(parameters).build()

        setContent {
            PlatformTheme {
                val scope = rememberCoroutineScope()
                val transition = rememberTransition(visibleState, label = "ShareScreenActivity")
                val uiComponent: ScreenCaptureShareScreenUiComponent =
                    remember(scope, parameters, transition) {
                        val ui =
                            (component
                                    .uiComponentBuilders()[ScreenCaptureType.SHARE_SCREEN]
                                    ?.setScope(scope)
                                    ?.setDisplay(display)
                                    ?.setWindow(window)
                                    ?.setVisibilityTransition(transition)
                                    ?.build() as ScreenCaptureShareScreenUiComponent)
                                .apply {
                                    shareScreenUiInteractor.initialize(
                                        projection,
                                        reviewGrantedConsentRequired,
                                        hostUserHandle,
                                        uid,
                                        packageName,
                                        display!!.displayId,
                                        config,
                                    )
                                }
                        ui
                    }

                val shareScreenUiInteractor = uiComponent.shareScreenUiInteractor
                interactor = shareScreenUiInteractor
                BackHandler { shareScreenUiInteractor.onClose() }
                LaunchedEffect(shareScreenUiInteractor) {
                    shareScreenUiInteractor.sharingState
                        .onEach { state ->
                            when (state) {
                                is ShareScreenUiInteractor.SharingState.Approved -> {
                                    setResult(RESULT_OK, createSuccessIntent(state.projection))
                                    setForceSendResultForMediaProjection()
                                    hide()
                                }
                                is ShareScreenUiInteractor.SharingState.Denied -> {
                                    setResult(RESULT_CANCELED)
                                    mediaProjectionMetricsLogger.notifyProjectionRequestCancelled(
                                        uid
                                    )
                                    announceCancellationIfNeeded()
                                    hide()
                                }
                                is ShareScreenUiInteractor.SharingState.NotStarted -> {
                                    // Do nothing
                                }
                            }
                        }
                        .launchIn(this)
                }

                if (!visibleState.targetState && visibleState.isIdle) {
                    // Dismiss the activity only after the exit animation has completed.
                    SideEffect { finishAndRemoveTask() }
                }

                val density = LocalDensity.current
                val emphasizedDecelerate = remember { CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f) }
                val standardEasing = remember { CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) }
                val initialOffsetPx = with(density) { 40.dp.roundToPx() }
                val standardAccelerate = remember { CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f) }
                val targetOffsetPx = with(density) { 20.dp.roundToPx() }

                AnimatedVisibility(
                    visibleState = visibleState,
                    enter =
                        slideInVertically(
                            animationSpec =
                                tween(durationMillis = 300, easing = emphasizedDecelerate),
                            initialOffsetY = { initialOffsetPx },
                        ) +
                            fadeIn(
                                animationSpec = tween(durationMillis = 300, easing = standardEasing)
                            ),
                    exit =
                        slideOutVertically(
                            animationSpec =
                                tween(durationMillis = 150, easing = standardAccelerate),
                            targetOffsetY = { targetOffsetPx },
                        ) +
                            fadeOut(
                                animationSpec =
                                    tween(durationMillis = 150, easing = standardAccelerate)
                            ),
                ) {
                    Box(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                        uiComponent.screenCaptureContent.Content()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (interactor?.sharingState?.value is ShareScreenUiInteractor.SharingState.Approved) {
            return
        }

        if (isFinishing) {
            // Ensure cancellation state and accessibility announcement are triggered on dismissal
            // (e.g., entering Overview).
            interactor?.onClose()
            announceCancellationIfNeeded()
        }
    }

    private fun announceCancellationIfNeeded() {
        if (hasAnnouncedCancel || !accessibilityManager.isEnabled) {
            return
        }

        val announcement = getString(R.string.screen_share_cancel_announcement)
        val event =
            AccessibilityEvent(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED).apply {
                className = Toast::class.java.name
                packageName = this@ShareScreenActivity.packageName
                text.add(announcement)
            }
        accessibilityManager.sendAccessibilityEvent(event)

        hasAnnouncedCancel = true
    }

    private fun createSuccessIntent(projection: IMediaProjection): Intent {
        val extras = Bundle()
        extras.putBinder(EXTRA_MEDIA_PROJECTION, projection.asBinder())
        val intent = Intent()
        intent.putExtras(extras)
        return intent
    }

    private fun setupWindow() {
        window.attributes =
            window.attributes.apply {
                title = "ShareScreenActivity" // Not the same as Window#setTitle
            }
        window.attributes.privateFlags =
            window.attributes.privateFlags or
                WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS
        with(window) {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            // This is a regular activity and should not be a system overlay.
            // By not setting a type, it will remain a standard application window.
            setWindowAnimations(-1)
        }
    }

    private fun hide() {
        visibleState.targetState = false
    }

    companion object {
        const val EXTRA_HOST_APP_UID = "launched_from_host_uid"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_HOST_APP_USER_HANDLE = "launched_from_user_handle"

        private const val TAG = "ShareScreenActivity"
    }
}
