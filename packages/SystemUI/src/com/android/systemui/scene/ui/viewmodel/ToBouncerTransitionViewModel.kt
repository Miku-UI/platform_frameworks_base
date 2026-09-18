/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.scene.ui.viewmodel

import androidx.compose.runtime.Stable
import com.android.systemui.bouncer.domain.interactor.BouncerInteractor
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.time.Duration.Companion.milliseconds

private const val BOUNCER_CONTENTS_PASSIVE_AUTH_DELAY = 500

@Stable
class ToBouncerTransitionViewModel
@AssistedInject
constructor(private val bouncerInteractor: BouncerInteractor) {
    /**
     * Call this method to determine if Bouncer contents should delay showing on initial transition
     * to the bouncer. We have this delay to give an opportunity for passive authentication methods
     * (such as face auth and watch unlock) to succeed first before showing the bouncer contents UI
     * to avoid a flicker of the UI. However, we do not want to delay the entire Bouncer scene (with
     * the bouncer background) because we still want to give the user a visual indication that their
     * request for the bouncer is being processed.
     *
     * Returns `true` if a passive authentication method (such as face authentication or watch
     * unlock) may authenticate the device before the user has the opportunity to enter their
     * pin/pattern/password. Else, `false`.
     */
    fun shouldDelayBouncerContent(): Boolean {
        return bouncerInteractor.passiveAuthMaySucceedBeforeFullyShowingBouncer()
    }

    /**
     * Delay to be introduced when transitioning to bouncer when passive auth (face/active unlock)
     * can run.
     */
    val delayForPassiveAuth = BOUNCER_CONTENTS_PASSIVE_AUTH_DELAY.milliseconds

    @AssistedFactory
    interface Factory {
        fun create(): ToBouncerTransitionViewModel
    }
}
