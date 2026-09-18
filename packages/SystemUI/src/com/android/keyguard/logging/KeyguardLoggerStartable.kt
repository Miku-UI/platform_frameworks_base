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

package com.android.keyguard.logging

import android.app.StatusBarManager.SESSION_KEYGUARD
import com.android.internal.logging.UiEventLogger
import com.android.systemui.CoreStartable
import com.android.systemui.authentication.domain.interactor.AuthenticationInteractor
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.KeyguardUiEvent
import com.android.systemui.log.SessionTracker
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** A [CoreStartable] responsible for logging metrics for keyguard. */
@SysUISingleton
class KeyguardLoggerStartable
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val sceneInteractor: SceneInteractor,
    private val uiEventLogger: UiEventLogger,
    private val sessionTracker: SessionTracker,
    private val authenticationInteractor: AuthenticationInteractor,
) : CoreStartable {

    override fun start() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }
        scope.launch {
            sceneInteractor.transitionStateFlow
                .filter { it.isIdle() }
                .distinctUntilChanged()
                .collect {
                    val uiEvent =
                        if (it.isIdle(Overlays.Bouncer)) {
                            if (isSecure()) {
                                KeyguardUiEvent.BOUNCER_OPEN_SECURE
                            } else {
                                KeyguardUiEvent.BOUNCER_OPEN_INSECURE
                            }
                        } else if (it.isIdle(Scenes.Lockscreen)) {
                            if (isSecure()) {
                                KeyguardUiEvent.LOCKSCREEN_OPEN_SECURE
                            } else {
                                KeyguardUiEvent.LOCKSCREEN_OPEN_INSECURE
                            }
                        } else {
                            null
                        }

                    if (uiEvent != null) {
                        uiEventLogger.logWithInstanceId(
                            uiEvent,
                            0,
                            null,
                            sessionTracker.getSessionId(SESSION_KEYGUARD),
                        )
                    }
                }
        }
    }

    private fun isSecure(): Boolean {
        return authenticationInteractor.authenticationMethod.value.isSecure
    }
}
