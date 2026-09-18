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

package com.android.systemui.statusbar.quickactions.av.domain.interactor

import androidx.annotation.VisibleForTesting
import com.android.systemui.accessibility.domain.interactor.CaptioningInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.quickactions.av.shared.model.BlurLevel
import com.android.systemui.statusbar.quickactions.av.shared.model.DesktopEffectModel
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.kotlin.combine
import com.android.systemui.util.settings.repository.SecureSettingsForUserRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Interactor for desktop effects.
 *
 * This class is responsible for managing the state of desktop effects, such as portrait relight,
 * face retouch, studio mic, and blur level. It provides methods to set the values of these effects
 * and exposes a [StateFlow] of [DesktopEffectModel] that represents the current state of the
 * effects.
 */
@SysUISingleton
class DesktopEffectInteractor
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val secureSettingsForUserRepository: SecureSettingsForUserRepository,
    private val captioningInteractor: CaptioningInteractor,
) {
    /** The current state of the desktop effects. */
    val model: StateFlow<DesktopEffectModel> =
        selectedUserInteractor.selectedUser
            .flatMapLatest { effectsForUser(it) }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = DesktopEffectModel(),
            )

    /**
     * Sets the value of the studio mic effect.
     *
     * @param newValue The new value of the effect.
     * @param userId The ID of the user for which to set the effect. If `null`, the effect is set
     *   for the currently selected user.
     */
    suspend fun setStudioMic(newValue: Boolean, userId: Int? = null) =
        withContext(bgDispatcher) {
            secureSettingsForUserRepository.setBoolForUser(
                userId.asValidUserId(),
                DESKTOP_EFFECTS_STUDIO_MIC_KEY,
                newValue,
            )
        }

    /**
     * Sets the value of the portrait relight effect.
     *
     * @param newValue The new value of the effect.
     * @param userId The ID of the user for which to set the effect. If `null`, the effect is set
     *   for the currently selected user.
     */
    suspend fun setPortraitRelight(newValue: Boolean, userId: Int? = null) =
        withContext(bgDispatcher) {
            secureSettingsForUserRepository.setBoolForUser(
                userId.asValidUserId(),
                DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY,
                newValue,
            )
        }

    /**
     * Sets the value of the face retouch effect.
     *
     * @param newValue The new value of the effect.
     * @param userId The ID of the user for which to set the effect. If `null`, the effect is set
     *   for the currently selected user.
     */
    suspend fun setFaceRetouch(newValue: Boolean, userId: Int? = null) =
        withContext(bgDispatcher) {
            secureSettingsForUserRepository.setBoolForUser(
                userId.asValidUserId(),
                DESKTOP_EFFECTS_FACE_RETOUCH_KEY,
                newValue,
            )
        }

    /**
     * Sets the value of the blur level effect.
     *
     * @param newValue The new value of the effect.
     * @param userId The ID of the user for which to set the effect. If `null`, the effect is set
     *   for the currently selected user.
     */
    suspend fun setBlurLevel(newValue: BlurLevel, userId: Int? = null) =
        withContext(bgDispatcher) {
            secureSettingsForUserRepository.setIntForUser(
                userId.asValidUserId(),
                DESKTOP_EFFECTS_BLUR_LEVEL_KEY,
                newValue.code,
            )
        }

    /**
     * Sets the value of the camera framing effect.
     *
     * @param newValue The new value of the effect.
     * @param userId The ID of the user for which to set the effect. If `null`, the effect is set
     *   for the currently selected user.
     */
    suspend fun setCameraFraming(newValue: Boolean, userId: Int? = null) =
        withContext(bgDispatcher) {
            secureSettingsForUserRepository.setBoolForUser(
                userId.asValidUserId(),
                DESKTOP_EFFECTS_CAMERA_FRAMING_KEY,
                newValue,
            )
        }

    /**
     * Sets the value of the live captions effect.
     *
     * @param newValue The new value of the effect.
     * @param userId The ID of the user for which to set the effect. If `null`, the effect is set
     *   for the currently selected user.
     */
    suspend fun setLiveCaptions(newValue: Boolean, userId: Int? = null) =
        withContext(bgDispatcher) {
            captioningInteractor.setIsSystemAudioCaptioningEnabled(newValue)
        }

    /**
     * Returns a [Flow] of [DesktopEffectModel] for the given user.
     *
     * @param userId The ID of the user for which to get the effects.
     */
    fun effectsForUser(userId: Int): Flow<DesktopEffectModel> =
        combine(
            portraitRelightFlowForUser(userId),
            faceRetouchFlowForUser(userId),
            studioMicFlowForUser(userId),
            blurLevelFlowForUser(userId),
            liveCaptionsFlowForUser(userId),
            cameraFramingFlowForUser(userId),
        ) {
            portraitRelight: Boolean,
            faceRetouch: Boolean,
            studioMic: Boolean,
            blurLevel: BlurLevel,
            liveCaptions: Boolean,
            cameraFraming: Boolean ->
            DesktopEffectModel(
                portraitRelight = portraitRelight,
                faceRetouch = faceRetouch,
                studioMic = studioMic,
                blurLevel = blurLevel,
                liveCaptions = liveCaptions,
                cameraFraming = cameraFraming,
            )
        }

    private fun Int?.asValidUserId(): Int {
        return this ?: selectedUserInteractor.getSelectedUserId()
    }

    private fun portraitRelightFlowForUser(userId: Int): Flow<Boolean> =
        secureSettingsForUserRepository.boolSettingForUser(
            userId,
            DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY,
            false,
        )

    private fun faceRetouchFlowForUser(userId: Int): Flow<Boolean> =
        secureSettingsForUserRepository.boolSettingForUser(
            userId,
            DESKTOP_EFFECTS_FACE_RETOUCH_KEY,
            false,
        )

    private fun studioMicFlowForUser(userId: Int): Flow<Boolean> =
        secureSettingsForUserRepository.boolSettingForUser(
            userId,
            DESKTOP_EFFECTS_STUDIO_MIC_KEY,
            false,
        )

    private fun blurLevelFlowForUser(userId: Int): Flow<BlurLevel> =
        secureSettingsForUserRepository
            .intSettingForUser(userId, DESKTOP_EFFECTS_BLUR_LEVEL_KEY, 0)
            .map { blurLevel -> BlurLevel.entries.find { it.code == blurLevel } ?: BlurLevel.OFF }

    private fun cameraFramingFlowForUser(userId: Int): Flow<Boolean> =
        secureSettingsForUserRepository.boolSettingForUser(
            userId,
            DESKTOP_EFFECTS_CAMERA_FRAMING_KEY,
            false,
        )

    private fun liveCaptionsFlowForUser(userId: Int): Flow<Boolean> =
        captioningInteractor.isSystemAudioCaptioningEnabled.onStart {
            // Emitting the initial default value.
            emit(false)
        }

    companion object {
        @VisibleForTesting
        const val DESKTOP_EFFECTS_PORTRAIT_RELIGHT_KEY = "desktop-effects-portrait-relight"
        @VisibleForTesting
        const val DESKTOP_EFFECTS_FACE_RETOUCH_KEY = "desktop-effects-face-retouch"
        @VisibleForTesting const val DESKTOP_EFFECTS_STUDIO_MIC_KEY = "desktop-effects-studio-mic"
        @VisibleForTesting const val DESKTOP_EFFECTS_BLUR_LEVEL_KEY = "desktop-effects-blur-level"
        @VisibleForTesting
        const val DESKTOP_EFFECTS_CAMERA_FRAMING_KEY = "desktop-effects-camera-framing"
    }
}
