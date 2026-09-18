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

package com.android.systemui.screencapture

import android.content.Context
import android.os.UserHandle
import android.view.Display
import com.android.app.tracing.coroutines.launchInTraced
import com.android.systemui.CoreStartable
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.screencapture.common.ScreenCaptureComponent
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureType
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiState
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureComponentInteractor
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureTargetDisplayInteractor
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureTracingInteractor
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.screencapture.record.smallscreen.ui.SmallScreenPostRecordingActivity
import com.android.systemui.screencapture.ui.PostRecordingShelf
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecording
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "ScreenCapture"

@SysUISingleton
class ScreenCaptureCoreStartable
@Inject
constructor(
    @Application private val appScope: CoroutineScope,
    @Application private val context: Context,
    private val screenCaptureComponentInteractor: ScreenCaptureComponentInteractor,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    private val screenCaptureTargetDisplayInteractor: ScreenCaptureTargetDisplayInteractor,
    private val screenRecordingServiceInteractor: ScreenRecordingServiceInteractor,
    private val postRecordingShelfFactory: PostRecordingShelf.Factory,
    private val activityStarter: ActivityStarter,
    private val screenCaptureRecordFeaturesInteractor: ScreenCaptureRecordFeaturesInteractor,
    private val screenCaptureTracingInteractor: ScreenCaptureTracingInteractor,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val screenCaptureUiReceiver: ScreenCaptureUiReceiver,
) : CoreStartable {

    override fun start() {
        appScope.launch { screenCaptureComponentInteractor.initialize() }
        ScreenCaptureType.entries.forEach { observeUiState(it) }
        setupSmallScreenPostRecordings()
        setupLargeScreenPostRecordings()
        broadcastDispatcher.registerReceiver(
            receiver = screenCaptureUiReceiver,
            filter = ScreenCaptureUiReceiver.intentFilter(),
            user = UserHandle.ALL,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeUiState(type: ScreenCaptureType) {
        val uiStateFlow =
            screenCaptureUiInteractor.uiState(type).onEach {
                screenCaptureTracingInteractor.beginVisibilityChangeSection(it)
            }

        val componentFlow =
            screenCaptureComponentInteractor.screenCaptureComponent(type).onEach { it?.start() }

        combine(uiStateFlow, componentFlow) { state, component ->
                if (state is ScreenCaptureUiState.Visible) component else null
            }
            .distinctUntilChanged()
            .flatMapLatest { component ->
                if (component == null) return@flatMapLatest emptyFlow<Unit>()

                screenCaptureTargetDisplayInteractor.targetDisplay.mapLatest { display ->
                    showUi(type, component, display)
                    screenCaptureUiInteractor.hide(type)
                }
            }
            .launchIn(appScope)
    }

    /**
     * Shows the UI and suspends until it's dismissed. Cancelling the suspension dismisses the UI
     */
    private suspend fun showUi(
        type: ScreenCaptureType,
        screenCaptureComponent: ScreenCaptureComponent,
        display: Display,
    ) {
        val factory = screenCaptureComponent.screenCaptureUiDialogFactory()
        val dialog = factory.create(display = display, type = type)
        suspendCancellableCoroutine { invocation ->
            dialog.setOnDismissListener {
                if (invocation.isActive) {
                    invocation.resume(Unit)
                }
            }
            dialog.show()
            invocation.invokeOnCancellation { dialog.dismiss() }
        }
    }

    private fun setupSmallScreenPostRecordings() {
        if (!screenCaptureRecordFeaturesInteractor.isSmallScreenRecordingEnabled) return

        screenRecordingServiceInteractor.screenRecordings
            .filterIsInstance(ScreenRecording.Saving::class)
            .onEach { recording ->
                activityStarter.startActivityDismissingKeyguard(
                    /* intent = */ SmallScreenPostRecordingActivity.waitForRecording(
                        context = context,
                        videoUri = recording.uri,
                        notificationId = recording.notificationId,
                    ),
                    /* onlyProvisioned = */ true,
                    /* dismissShade = */ true,
                    /* customMessage = */ null,
                )
            }
            .launchIn(appScope)
    }

    private fun setupLargeScreenPostRecordings() {
        if (!screenCaptureRecordFeaturesInteractor.isLargeScreenRecordingEnabled) return

        screenRecordingServiceInteractor.screenRecordings
            .filterIsInstance<ScreenRecording.Saved>()
            .onEach { recording ->
                val display = screenCaptureTargetDisplayInteractor.targetDisplay.first()
                val shelf =
                    postRecordingShelfFactory.create(
                        uri = recording.uri,
                        thumbnail = recording.thumbnail,
                        display = display,
                        notificationId = recording.notificationId,
                    )
                shelf.show()
            }
            .launchIn(appScope)
    }

    private fun ScreenCaptureComponent.start() {
        screenCaptureOverlayStateInteractor()
            .isVisible
            .mapLatest { visible ->
                if (visible) {
                    screenRecordOverlayUi().show()
                }
            }
            .launchInTraced("ScreenCaptureOverlayStateInteractor#show", coroutineScope())
    }
}
