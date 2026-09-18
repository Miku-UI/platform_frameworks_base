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

package com.android.systemui.statusbar.quickactions.sharescreen.domain.interactor

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.projection.StopReason
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import com.android.systemui.CoreStartable
import com.android.systemui.common.ui.data.repository.ConfigurationRepository
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.mediaprojection.MediaProjectionUtils
import com.android.systemui.mediaprojection.data.model.MediaProjectionState
import com.android.systemui.mediaprojection.data.repository.MediaProjectionRepository
import com.android.systemui.res.R
import com.android.systemui.screenrecord.data.model.ScreenRecordModel
import com.android.systemui.screenrecord.data.repository.ScreenRecordRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@SysUISingleton
class ShareScreenPrivacyIndicatorInteractor
@Inject
constructor(
    @Main private val resources: Resources,
    configurationRepository: ConfigurationRepository,
    @Background private val scope: CoroutineScope,
    private val mediaProjectionRepository: MediaProjectionRepository,
    private val screenRecordRepository: ScreenRecordRepository,
    private val packageManager: PackageManager,
    private val accessibilityManager: AccessibilityManager,
    @Application private val context: Context,
) : CoreStartable {
    enum class SharingType {
        APP,
        TAB,
        DISPLAY,
    }

    private data class SharingInfo(val type: SharingType, val label: String)

    private var lastSharingInfo: SharingInfo? = null

    // The projection is active if the state is any subtype of MediaProjectionState.Projecting.
    private val isScreenSharing: StateFlow<Boolean> =
        combine(
                mediaProjectionRepository.mediaProjectionState,
                screenRecordRepository.screenRecordState,
            ) { projectionState, recordState ->
                if (projectionState !is MediaProjectionState.Projecting) {
                    return@combine false
                }

                val isRecording = recordState is ScreenRecordModel.Recording
                // TODO(b/493642399) Thinking about remove this once the race condition being
                // resolved at [ScreenRecordingService].
                val hostPackage = projectionState.hostPackage
                // The Screen Recorder (SystemUI) manages its own indicators and UX.
                // We explicitly exclude any projection hosted by SystemUI to prevent the
                // generic "Share Screen" indicator from appearing during or after a
                // recording session, avoiding "ghost" indicators during teardown.
                val isSystemUIProjection = hostPackage == context.packageName

                // We identify "sharing" by excluding "recording", "casting", and
                // any system-internal projections.
                !isRecording &&
                    !isSystemUIProjection &&
                    !MediaProjectionUtils.packageHasCastingCapabilities(packageManager, hostPackage)
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    val isChipVisible: StateFlow<Boolean> =
        combine(
                configurationRepository.onAnyConfigurationChange.onStart { emit(Unit) },
                isScreenSharing,
            ) { _, isSharing ->
                resources.getBoolean(R.bool.config_largeScreenPrivacyIndicator) && isSharing
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = false,
            )

    override fun start() {
        scope.launch {
            var wasSharing = false
            isScreenSharing.collect { isSharing ->
                if (wasSharing && !isSharing) {
                    announceStoppedSharing()
                }
                wasSharing = isSharing
            }
        }
    }

    fun stopShare() {
        scope.launch { mediaProjectionRepository.stopProjecting(StopReason.STOP_PRIVACY_CHIP) }
    }

    fun assignSharingInfo(type: SharingType, label: String) {
        lastSharingInfo = SharingInfo(type, label)
    }

    private fun announceStoppedSharing() {
        val info = lastSharingInfo ?: return

        if (accessibilityManager.isEnabled) {
            val resId =
                when (info.type) {
                    SharingType.APP -> R.string.screen_share_a11y_stopped_sharing_app
                    SharingType.TAB -> R.string.screen_share_a11y_stopped_sharing_tab
                    SharingType.DISPLAY -> R.string.screen_share_a11y_stopped_sharing_display
                }

            accessibilityManager.sendAccessibilityEvent(
                AccessibilityEvent(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED).apply {
                    packageName = context.packageName
                    className = Toast::class.java.name
                    text.add(resources.getString(resId, info.label))
                }
            )
        }

        lastSharingInfo = null
    }
}
