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

package com.android.systemui.statusbar.quickactions.av.ui.viewmodel

import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.statusbar.quickactions.av.domain.interactor.DesktopEffectInteractor
import com.android.systemui.statusbar.quickactions.av.shared.model.BlurLevel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BlurDrillInViewModel
@AssistedInject
constructor(
    private val desktopEffectInteractor: DesktopEffectInteractor,
    @Assisted val returnToMainPage: () -> Unit,
) : HydratedActivatable() {

    val drillInTitle = com.android.systemui.res.R.string.av_camera_blur_drill_in_title

    val blurOffButton =
        Button(targetBlur = BlurLevel.OFF, desktopEffectInteractor = desktopEffectInteractor)
    val blurLightButton =
        Button(targetBlur = BlurLevel.LIGHT, desktopEffectInteractor = desktopEffectInteractor)
    val blurFullButton =
        Button(targetBlur = BlurLevel.FULL, desktopEffectInteractor = desktopEffectInteractor)

    override suspend fun onActivated() {
        coroutineScope {
            for (activatable in listOf(blurOffButton, blurLightButton, blurFullButton)) {
                launch { activatable.activate() }
            }
            awaitCancellation()
        }
    }

    class Button(
        private val targetBlur: BlurLevel,
        private val desktopEffectInteractor: DesktopEffectInteractor,
    ) : ButtonViewModel, HydratedActivatable() {

        override suspend fun onClick() {
            desktopEffectInteractor.setBlurLevel(newValue = targetBlur)
        }

        override val state by
            desktopEffectInteractor.model
                .map { buttonState(targetBlur = targetBlur, currentBlur = it.blurLevel) }
                .hydratedStateOf(
                    initialValue = buttonState(targetBlur = targetBlur, currentBlur = BlurLevel.OFF)
                )

        private fun buttonState(targetBlur: BlurLevel, currentBlur: BlurLevel): ButtonUiState =
            ButtonUiState(
                isEnabled = (targetBlur == currentBlur),
                image =
                    when (targetBlur) {
                        BlurLevel.OFF ->
                            com.android.systemui.res.R.drawable.gs_background_blur_full_off
                        BlurLevel.LIGHT ->
                            com.android.systemui.res.R.drawable.gs_background_blur_light
                        BlurLevel.FULL ->
                            com.android.systemui.res.R.drawable.gs_background_blur_full
                    },
                mainTitle =
                    when (targetBlur) {
                        BlurLevel.OFF -> com.android.systemui.res.R.string.av_camera_blur_off
                        BlurLevel.LIGHT -> com.android.systemui.res.R.string.av_camera_blur_light
                        BlurLevel.FULL -> com.android.systemui.res.R.string.av_camera_blur_full
                    },
            )
    }

    /** A factory to be used to create view model instances. */
    @AssistedFactory
    interface Factory {
        fun create(returnToMainPage: () -> Unit): BlurDrillInViewModel
    }
}
